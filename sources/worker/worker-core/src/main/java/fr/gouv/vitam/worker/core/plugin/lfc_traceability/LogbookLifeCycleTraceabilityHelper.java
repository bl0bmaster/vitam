package fr.gouv.vitam.worker.core.plugin.lfc_traceability;

import com.fasterxml.jackson.databind.JsonNode;
import fr.gouv.vitam.common.LocalDateUtil;
import fr.gouv.vitam.common.collection.CloseableIterator;
import fr.gouv.vitam.common.database.builder.request.exception.InvalidCreateOperationException;
import fr.gouv.vitam.common.database.builder.request.single.Select;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.json.CanonicalJsonFormatter;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.SysErrLogger;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.security.merkletree.MerkleTreeAlgo;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.logbook.common.exception.LogbookClientException;
import fr.gouv.vitam.logbook.common.exception.TraceabilityException;
import fr.gouv.vitam.logbook.common.model.TraceabilityEvent;
import fr.gouv.vitam.logbook.common.model.TraceabilityFile;
import fr.gouv.vitam.logbook.common.model.TraceabilityStatistics;
import fr.gouv.vitam.logbook.common.parameters.LogbookParameterName;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookOperation;
import fr.gouv.vitam.logbook.common.traceability.LogbookTraceabilityHelper;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClient;
import fr.gouv.vitam.storage.engine.client.StorageClient;
import fr.gouv.vitam.storage.engine.client.StorageClientFactory;
import fr.gouv.vitam.storage.engine.client.exception.StorageAlreadyExistsClientException;
import fr.gouv.vitam.storage.engine.client.exception.StorageNotFoundClientException;
import fr.gouv.vitam.storage.engine.client.exception.StorageServerClientException;
import fr.gouv.vitam.storage.engine.common.model.DataCategory;
import fr.gouv.vitam.storage.engine.common.model.request.ObjectDescription;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.worker.core.distribution.JsonLineModel;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageAlreadyExistException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static fr.gouv.vitam.logbook.common.server.database.collections.LogbookMongoDbName.eventDetailData;
import static fr.gouv.vitam.logbook.common.server.database.collections.LogbookMongoDbName.eventIdentifier;

public abstract class LogbookLifeCycleTraceabilityHelper implements LogbookTraceabilityHelper {
    private static final VitamLogger LOGGER =
        VitamLoggerFactory.getInstance(LogbookLifeCycleTraceabilityHelper.class);

    private static final String LOGBOOK = "logbook";
    private static final String EVENT_ID = eventIdentifier.getDbname();
    private static final String EVENT_DETAIL_DATA = eventDetailData.getDbname();
    private static final String HANDLER_ID = "FINALIZE_LC_TRACEABILITY";
    private static final String HANDLER_SUB_ACTION_TIMESTAMP = "OP_SECURISATION_TIMESTAMP";
    private static final String HANDLER_SUB_ACTION_SECURISATION_STORAGE = "OP_SECURISATION_STORAGE";
    private static final int LAST_OPERATION_LIFECYCLES_RANK = 0;
    private static final int TRACEABILITY_INFORMATION_RANK = 1;
    private static final int TRACEABILITY_STATISTICS_RANK = 3;

    private final HandlerIO handlerIO;
    private final LogbookOperationsClient logbookOperationsClient;
    private final ItemStatus itemStatus;
    private final String operationID;

    private final WorkspaceClientFactory workspaceClientFactory;

    private LogbookOperation lastTraceabilityOperation = null;
    private List<String> expectedLogbookId = null;
    private JsonNode traceabilityInformation = null;
    private TraceabilityStatistics traceabilityStatistics;
    private LocalDateTime traceabilityStartDate;
    private LocalDateTime traceabilityEndDate;

    private Boolean isLastEventInit = false;
    private Boolean isLastMonthEventInit = false;
    private Boolean isLastYearEventInit = false;
    private String previousStartDate = null;
    private String previousMonthStartDate = null;
    private String previousYearStartDate = null;
    private byte[] previousTimestampToken = null;
    private byte[] previousMonthTimestampToken = null;
    private byte[] previousYearTimestampToken = null;
    private boolean maxEntriesReached;

    /**
     * @param handlerIO Workflow Input/Output of the traceability event
     * @param logbookOperationsClient used to search the operation to secure
     * @param itemStatus used by workflow, event must be updated here
     * @param operationID of the current traceability process
     * @param workspaceClientFactory
     */
    public LogbookLifeCycleTraceabilityHelper(HandlerIO handlerIO, LogbookOperationsClient logbookOperationsClient,
        ItemStatus itemStatus, String operationID,
        WorkspaceClientFactory workspaceClientFactory) {
        this.handlerIO = handlerIO;
        this.logbookOperationsClient = logbookOperationsClient;
        this.itemStatus = itemStatus;
        this.operationID = operationID;
        this.workspaceClientFactory = workspaceClientFactory;
    }

    @Override
    public void initialize()
        throws TraceabilityException {

        try {
            File operationFile = (File) handlerIO.getInput(LAST_OPERATION_LIFECYCLES_RANK);
            JsonNode operationJson = null;
            if (operationFile != null) {
                operationJson = JsonHandler.getFromFile(operationFile);
            }

            if (operationJson != null && operationJson.isObject() && operationJson.get("evId") != null) {
                lastTraceabilityOperation = new LogbookOperation(operationJson);
            }
            File traceabilityInformationFile = (File) handlerIO.getInput(TRACEABILITY_INFORMATION_RANK);
            if (traceabilityInformationFile != null) {
                traceabilityInformation = JsonHandler.getFromFile(traceabilityInformationFile);
            }

            File statsFile = (File) handlerIO.getInput(TRACEABILITY_STATISTICS_RANK);
            if (operationFile != null) {
                traceabilityStatistics = JsonHandler.getFromFile(statsFile, TraceabilityStatistics.class);
            }

        } catch (InvalidParseOperationException e) {
            throw new TraceabilityException("Cannot parse logbook operation", e);
        }

        expectedLogbookId = newArrayList(operationID);
        if (lastTraceabilityOperation != null) {
            expectedLogbookId.add(lastTraceabilityOperation.getString(EVENT_ID));
        }

        this.traceabilityStartDate =
            LocalDateUtil.parseMongoFormattedDate(traceabilityInformation.get("startDate").asText());
        this.traceabilityEndDate =
            LocalDateUtil.parseMongoFormattedDate(traceabilityInformation.get("endDate").asText());
        this.maxEntriesReached
            = traceabilityInformation.get("maxEntriesReached").asBoolean();
    }

    @Override
    public void createLogbookOperationStructure()
        throws TraceabilityException {
        // Nothing to do: Master event is make and updated by workflow
    }

    @Override
    public void createLogbookOperationEvent(Integer tenantId,
        String eventType, StatusCode status, TraceabilityEvent event)
        throws TraceabilityException {

        if (!getStepName().equals(eventType)) {
            final ItemStatus subItemStatusTimestamp = new ItemStatus(eventType);
            itemStatus.setItemsStatus(eventType, subItemStatusTimestamp.increment(status));
        }
    }

    @Override
    public void saveEmpty(Integer tenantId) {
        // Nothing to do. Empty master event will be close by workflow
    }

    @Override
    public boolean getMaxEntriesReached() {
        return maxEntriesReached;
    }

    @Override
    public TraceabilityStatistics getTraceabilityStatistics() {
        return traceabilityStatistics;
    }

    @Override
    public void storeAndDeleteZip(Integer tenant, String strategyId, File zipFile,
        String fileName, String uri, TraceabilityEvent event)
        throws TraceabilityException {

        String evDetailData = JsonHandler.unprettyPrint(event);
        itemStatus.setEvDetailData(evDetailData);
        itemStatus.setMasterData(LogbookParameterName.eventDetailData.name(),
            evDetailData);

        final ItemStatus subItemStatusSecurisationStorage = new ItemStatus(HANDLER_SUB_ACTION_SECURISATION_STORAGE);
        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(zipFile));
            final WorkspaceClient workspaceClient = workspaceClientFactory.getClient()) {

            String containerName = VitamThreadUtils.getVitamSession().getRequestId() + "-Traceability";
            try {
                workspaceClient.createContainer(containerName);
            } catch (ContentAddressableStorageAlreadyExistException e) {
                // Already exists
                SysErrLogger.FAKE_LOGGER.ignoreLog(e);
            }
            workspaceClient.putObject(containerName, uri, inputStream);

            final StorageClientFactory storageClientFactory = StorageClientFactory.getInstance();

            final ObjectDescription description = new ObjectDescription();
            description.setWorkspaceContainerGUID(containerName);
            description.setWorkspaceObjectURI(uri);

            try (final StorageClient storageClient = storageClientFactory.getClient()) {

                storageClient.storeFileFromWorkspace(
                        strategyId, DataCategory.LOGBOOK, fileName, description);
                workspaceClient.deleteContainer(containerName, true);
                subItemStatusSecurisationStorage.setEvDetailData(JsonHandler.unprettyPrint(event));
                itemStatus.setItemsStatus(HANDLER_SUB_ACTION_SECURISATION_STORAGE,
                    subItemStatusSecurisationStorage.increment(StatusCode.OK));
            } catch (StorageAlreadyExistsClientException | StorageNotFoundClientException |
                StorageServerClientException | ContentAddressableStorageNotFoundException e) {
                itemStatus.setItemsStatus(HANDLER_SUB_ACTION_SECURISATION_STORAGE,
                    subItemStatusSecurisationStorage.increment(StatusCode.FATAL));
                throw new TraceabilityException("unable to store zip file", e);
            }
        } catch (ContentAddressableStorageServerException | IOException e) {
            itemStatus.setItemsStatus(HANDLER_SUB_ACTION_SECURISATION_STORAGE,
                subItemStatusSecurisationStorage.increment(StatusCode.FATAL));
            throw new TraceabilityException("unable to create container", e);
        } finally {
            zipFile.delete();
        }
    }

    @Override
    public String getStepName() {
        return HANDLER_ID;
    }

    @Override
    public String getTimestampStepName() {
        return HANDLER_SUB_ACTION_TIMESTAMP;
    }

    @Override
    public String getUriName() {
        return LOGBOOK;
    }

    @Override
    public String getTraceabilityStartDate() {
        return LocalDateUtil.getFormattedDateForMongo(this.traceabilityStartDate);
    }

    @Override
    public String getTraceabilityEndDate() {
        return LocalDateUtil.getFormattedDateForMongo(this.traceabilityEndDate);
    }

    @Override
    public long getDataSize() {
        return traceabilityInformation.get("nbEntries").asLong();
    }

    @Override
    public byte[] getPreviousTimestampToken()
        throws InvalidParseOperationException {
        if (!isLastEventInit) {
            extractPreviousEvent();
        }
        return previousTimestampToken;
    }

    @Override
    public byte[] getPreviousMonthTimestampToken()
        throws InvalidParseOperationException, TraceabilityException {
        if (!isLastMonthEventInit) {
            extractPreviousEvent(this.traceabilityEndDate.minusMonths(1), true);
        }
        return previousMonthTimestampToken;
    }

    @Override
    public byte[] getPreviousYearTimestampToken()
        throws InvalidParseOperationException, TraceabilityException {
        if (!isLastYearEventInit) {
            extractPreviousEvent(this.traceabilityEndDate.minusYears(1), false);
        }
        return previousYearTimestampToken;
    }

    @Override
    public String getPreviousStartDate() throws InvalidParseOperationException {
        if (!isLastEventInit) {
            extractPreviousEvent();
        }
        return previousStartDate;
    }

    @Override
    public String getPreviousMonthStartDate()
        throws InvalidParseOperationException, TraceabilityException {
        if (!isLastMonthEventInit) {
            extractPreviousEvent(this.traceabilityEndDate.minusMonths(1), true);
        }
        return previousMonthStartDate;
    }

    @Override
    public String getPreviousYearStartDate()
        throws InvalidParseOperationException, TraceabilityException {
        if (!isLastYearEventInit) {
            extractPreviousEvent(this.traceabilityEndDate.minusYears(1), false);
        }
        return previousYearStartDate;
    }

    /**
     * Reduce part of the process : lets merge in one only file the disting lifecycles ones
     *
     * @param jsonLineIterator
     * @param traceabilityFile
     * @param algo
     * @return
     * @throws TraceabilityException
     */
    protected void extractAppendToFinalFile(CloseableIterator<JsonLineModel> jsonLineIterator, TraceabilityFile traceabilityFile,
        MerkleTreeAlgo algo)
        throws TraceabilityException {
        try {

            while (jsonLineIterator.hasNext()) {
                JsonNode entry = jsonLineIterator.next().getParams();

                byte[] bytes = CanonicalJsonFormatter.serializeToByteArray(entry);

                traceabilityFile.storeLog(bytes);
                algo.addLeaf(bytes);
            }

        } catch (IOException e) {
            LOGGER.error("Error while storing files in ZIP");
            throw new TraceabilityException("Error while storing files in ZIP", e);
        }
    }

    private void extractPreviousEvent() throws InvalidParseOperationException {
        previousTimestampToken = extractTimestampToken(lastTraceabilityOperation);
        if (lastTraceabilityOperation != null) {
            TraceabilityEvent lastTraceabilityEvent = extractEventDetData(lastTraceabilityOperation);
            if (lastTraceabilityEvent != null) {
                previousStartDate = lastTraceabilityEvent.getStartDate();
            }
        }
        isLastEventInit = true;
    }

    private void extractPreviousEvent(LocalDateTime specificDate, Boolean isMonth)
        throws InvalidParseOperationException, TraceabilityException {
        try {
            previousMonthTimestampToken =
                findHashByTraceabilityEventExpect(specificDate);
            final LogbookOperation oneMounthBeforeTraceabilityOperation =
                findFirstTraceabilityOperationOKAfterDate(specificDate);
            if (oneMounthBeforeTraceabilityOperation != null) {
                TraceabilityEvent oneMonthBeforeTraceabilityEvent =
                    extractEventDetData(oneMounthBeforeTraceabilityOperation);
                if (oneMonthBeforeTraceabilityEvent != null) {
                    previousMonthStartDate = oneMonthBeforeTraceabilityEvent.getStartDate();
                }
            }
        } catch (InvalidCreateOperationException | LogbookClientException e) {
            throw new TraceabilityException(e);
        }

        if (isMonth) {
            isLastMonthEventInit = true;
        } else {
            isLastYearEventInit = true;
        }
    }

    byte[] extractTimestampToken(LogbookOperation logbookOperation) throws InvalidParseOperationException {
        TraceabilityEvent traceabilityEvent = extractEventDetData(logbookOperation);
        if (traceabilityEvent == null) {
            return null;
        }
        return traceabilityEvent.getTimeStampToken();
    }

    private TraceabilityEvent extractEventDetData(LogbookOperation logbookOperation)
        throws InvalidParseOperationException {
        if (logbookOperation == null) {
            return null;
        }
        return JsonHandler.getFromString((String) logbookOperation.get(EVENT_DETAIL_DATA), TraceabilityEvent.class);
    }

    private byte[] findHashByTraceabilityEventExpect(LocalDateTime date)
        throws InvalidCreateOperationException, InvalidParseOperationException, LogbookClientException {
        RequestResponseOK<JsonNode> requestResponseOK =
            RequestResponseOK.getFromJsonNode(
                logbookOperationsClient.selectOperation(generateSelectLogbookOperation(date).getFinalSelect()));
        List<JsonNode> foundOperation = requestResponseOK.getResults();
        if (foundOperation != null && !foundOperation.isEmpty()) {
            LogbookOperation lastOperationTraceabilityLifecycle = new LogbookOperation(foundOperation.get(0));
            if (!expectedLogbookId.contains(lastOperationTraceabilityLifecycle.getString(EVENT_ID))) {
                expectedLogbookId.add(lastOperationTraceabilityLifecycle.getString(EVENT_ID));
                return extractTimestampToken(lastOperationTraceabilityLifecycle);
            }
        }

        LOGGER.warn("Logbook operation not found, there is no Operation");
        return null;
    }

    private LogbookOperation findFirstTraceabilityOperationOKAfterDate(LocalDateTime date)
        throws InvalidCreateOperationException, InvalidParseOperationException, LogbookClientException {
        RequestResponseOK<JsonNode> requestResponseOK =
            RequestResponseOK.getFromJsonNode(
                logbookOperationsClient.selectOperation(generateSelectLogbookOperation(date).getFinalSelect()));
        List<JsonNode> foundOperation = requestResponseOK.getResults();
        if (foundOperation != null && foundOperation.size() == 1) {
            return new LogbookOperation(foundOperation.get(0));
        }
        LOGGER.warn("Logbook operation not found, there is no Operation");
        return null;
    }

    protected abstract Select generateSelectLogbookOperation(LocalDateTime date) throws InvalidCreateOperationException;

}
