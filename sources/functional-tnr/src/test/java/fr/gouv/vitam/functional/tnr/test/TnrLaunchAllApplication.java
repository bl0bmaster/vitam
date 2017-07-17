/**
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019)
 *
 * contact.vitam@culture.gouv.fr
 * 
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 * users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 * successive licensors have only limited liability.
 *
 * In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 * accept its terms.
 */
package fr.gouv.vitam.functional.tnr.test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import fr.gouv.vitam.access.external.rest.AccessExternalApplication;
import fr.gouv.vitam.access.internal.rest.AccessInternalApplication;
import fr.gouv.vitam.common.FileUtil;
import fr.gouv.vitam.common.PropertiesUtils;
import fr.gouv.vitam.common.SystemPropertyUtil;
import fr.gouv.vitam.common.client.configuration.ClientConfigurationImpl;
import fr.gouv.vitam.common.database.server.elasticsearch.ElasticsearchNode;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamApplicationServerException;
import fr.gouv.vitam.common.exception.VitamException;
import fr.gouv.vitam.common.junit.JunitHelper;
import fr.gouv.vitam.common.junit.JunitHelper.ElasticsearchTestConfiguration;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.functional.administration.rest.AdminManagementApplication;
import fr.gouv.vitam.ingest.external.rest.IngestExternalApplication;
import fr.gouv.vitam.ingest.internal.upload.rest.IngestInternalApplication;
import fr.gouv.vitam.logbook.common.server.database.collections.LogbookElasticsearchAccess;
import fr.gouv.vitam.logbook.lifecycles.client.LogbookLifeCyclesClientFactory;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.logbook.rest.LogbookApplication;
import fr.gouv.vitam.metadata.rest.MetaDataApplication;
import fr.gouv.vitam.processing.common.exception.PluginException;
import fr.gouv.vitam.processing.management.rest.ProcessManagementApplication;
import fr.gouv.vitam.storage.engine.server.rest.StorageApplication;
import fr.gouv.vitam.storage.engine.server.rest.StorageConfiguration;
import fr.gouv.vitam.storage.offers.common.rest.DefaultOfferApplication;
import fr.gouv.vitam.worker.server.rest.WorkerApplication;
import fr.gouv.vitam.workspace.rest.WorkspaceApplication;

/**
 * This class aims to help to launch locally the TNR by launching all necessary components
 */
public class TnrLaunchAllApplication {
    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(TnrLaunchAllApplication.class);

    /**
     * Java -D argument
     */
    private static final String JAVA_ARG_TNR_JUNIT_CONF = "tnrJunitConf";
    /**
     * Default tnr-config.conf filename
     */
    private static final String TNR_CONFIG_CONF = "tnr-config.conf";

    /**
     * Still not possible to launch 2 offers, when possible change the static-strategy.json to reflect the changes
     */
    private static final boolean START_OFFER2 = false;

    /**
     * Jetty port setters
     */
    private static final String JETTY_INGEST_EXTERNAL_PORT = "jetty.ingest-external.port";
    private static final String JETTY_INGEST_INTERNAL_PORT = "jetty.ingest-internal.port";
    private static final String JETTY_ACCESS_EXTERNAL_PORT = "jetty.access-external.port";
    private static final String JETTY_ACCESS_INTERNAL_PORT = "jetty.access-internal.port";
    private static final String JETTY_WORKER_PORT = "jetty.worker.port";
    private static final String JETTY_FUNCTIONAL_ADMIN_PORT = "jetty.functional-admin.port";
    private static final String JETTY_STORAGE_PORT = "jetty.storage.port";
    private static final String JETTY_OFFER2_PORT = "jetty.offer2.port";
    private static final String JETTY_OFFER_PORT = "jetty.offer.port";
    /**
     * Vitam TMP Folder
     */
    private static final String VITAM_TMP_FOLDER = "vitam.tmp.folder";
    /**
     * Configuration files for applications
     */
    private static final String FUNCTIONAL_TNR_ACCESS_EXTERNAL_CONF = "functional-tnr/access-external.conf";
    private static final String FUNCTIONAL_TNR_INGEST_EXTERNAL_CONF = "functional-tnr/ingest-external.conf";
    private static final String FUNCTIONAL_TNR_ACCESS_INTERNAL_CONF = "functional-tnr/access-internal.conf";
    private static final String FUNCTIONAL_TNR_INGEST_INTERNAL_CONF = "functional-tnr/ingest-internal.conf";
    private static final String FUNCTIONAL_TNR_LOGBOOK_CONF = "functional-tnr/logbook.conf";
    private static final String FUNCTIONAL_TNR_FUNCTIONAL_ADMINISTRATION_CONF =
        "functional-tnr/functional-administration.conf";
    private static final String FUNCTIONAL_TNR_PROCESSING_CONF = "functional-tnr/processing.conf";
    private static final String FUNCTIONAL_TNR_WORKSPACE_CONF = "functional-tnr/workspace.conf";
    private static final String FUNCTIONAL_TNR_METADATA_CONF = "functional-tnr/metadata.conf";
    private static final String FUNCTIONAL_TNR_STORAGE_DEFAULT_OFFER_NOSSL2_CONF =
        "functional-tnr/storage-default-offer-nossl2.conf";
    private static final String FUNCTIONAL_TNR_STORAGE_DEFAULT_OFFER_NOSSL_CONF =
        "functional-tnr/storage-default-offer-nossl.conf";
    private static final String FUNCTIONAL_TNR_STORAGE_ENGINE_CONF = "functional-tnr/storage-engine.conf";
    /**
     * OWASP setter
     */
    private static final String ORG_OWASP_ESAPI_RESOURCES = "org.owasp.esapi.resources";

    private static final String LOCALHOST = "localhost";

    /**
     * SUB_PATHES
     */
    private static final String PROCESS_SUBPATH = "process";
    private static final String VERSION_FOLDER_SUBPATH = "version/folder";
    private static final String STORAGELOG_SUBPATH = "storagelog";
    private static final String STORAGEZIP_SUBPATH = "storagezip";
    private static final String WORKER_DB_SUBPATH = "worker.db";
    private static final String WKSINGEST_SUBPATH = "wksingest";
    private static final String OFFER2_SUBPATH = "offer2";
    private static final String OFFER_SUBPATH = "offer";
    private static final String WORKSPACE_SUBPATH = "workspace";

    /**
     * MongoDB
     */
    private static final int DATABASE_PORT = 12346;
    private static MongodExecutable mongodExecutable;
    private static MongodProcess mongod;
    /**
     * Elasticsearch
     */
    private static LogbookElasticsearchAccess esClient;
    private static final int TCP_PORT = 54321;
    private static final int HTTP_PORT = 54320;
    private static final String CLUSTER_NAME = "vitam-cluster";

    /**
     * Temporary folder for database
     */
    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final int PORT_SERVICE_WORKER = 8098;
    private static final int PORT_SERVICE_WORKSPACE = 8094;
    private static final int PORT_SERVICE_METADATA = 8096;
    private static final int PORT_SERVICE_PROCESSING = 8097;
    private static final int PORT_SERVICE_FUNCTIONAL_ADMIN = 8093;
    private static final int PORT_SERVICE_LOGBOOK = 8099;
    private static final int PORT_SERVICE_STORAGE = 8100;
    private static final int PORT_SERVICE_STORAGE_OFFER = 8101;
    private static final int PORT_SERVICE_STORAGE_OFFER2 = 8103;
    private static final int PORT_SERVICE_SIEGFRIED = 8102;
    private static final int PORT_SERVICE_INGEST_INTERNAL = 8095;
    private static final int PORT_SERVICE_INGEST_EXTERNAL = 8090;
    private static final int PORT_SERVICE_ACCESS_INTERNAL = 8092;
    private static final int PORT_SERVICE_ACCESS_EXTERNAL = 8091;

    private static String CONFIG_WORKER_PATH = "";
    private static String CONFIG_WORKSPACE_PATH = "";
    private static String CONFIG_METADATA_PATH = "";
    private static String CONFIG_PROCESSING_PATH = "";
    private static String CONFIG_FUNCTIONAL_ADMIN_PATH = "";
    private static String CONFIG_LOGBOOK_PATH = "";
    private static String CONFIG_INGEST_INTERNAL_PATH = "";
    private static String CONFIG_ACCESS_INTERNAL_PATH = "";
    private static String CONFIG_INGEST_EXTERNAL_PATH = "";
    private static String CONFIG_ACCESS_EXTERNAL_PATH = "";
    private static String DEFAULT_OFFER_CONF = "";
    private static String DEFAULT_OFFER_CONF2 = "";
    private static String STORAGE_CONF = "";

    // private static VitamServer workerApplication;
    private static MetaDataApplication medtadataApplication;
    private static WorkerApplication wkrapplication;
    private static AdminManagementApplication adminApplication;
    private static LogbookApplication lgbapplication;
    private static WorkspaceApplication workspaceApplication;
    private static ProcessManagementApplication processManagementApplication;
    private static IngestInternalApplication ingestInternalApplication;
    private static AccessInternalApplication accessInternalApplication;
    private static IngestExternalApplication ingestExternalApplication;
    private static AccessExternalApplication accessExternalApplication;
    private static StorageApplication storageApplication;
    private static DefaultOfferApplication defaultOfferApplication;
    private static DefaultOfferApplication defaultOfferApplication2;
    private static Process siegfried;
    private static ElasticsearchTestConfiguration config = null;


    private static JunitTnrConfiguration buildConfiguration;

    /**
     * Start Vitam
     */
    @BeforeClass
    public static void setUpBeforeClass() {
        LOGGER.warn("Start TNR configuration");
        String tnrconfigFile = SystemPropertyUtil.get(JAVA_ARG_TNR_JUNIT_CONF, TNR_CONFIG_CONF);
        LOGGER.warn("Try to load config : " + tnrconfigFile);
        try (final InputStream yamlIS = PropertiesUtils.getConfigAsStream(tnrconfigFile)) {
            buildConfiguration = PropertiesUtils.readYaml(yamlIS,
                JunitTnrConfiguration.class);
        } catch (final IOException e) {
            LOGGER.error("Cannot load the configuration file: " + tnrconfigFile, e);
            System.exit(1);
        }
        deleteDirectories();
        createDirectories();
        boolean launchSiegfried = buildConfiguration.isLaunchSiegfried();
        String SIEGFRIED_PATH = buildConfiguration.getSiegfriedPath();
        String VITAM_APP_SIEGFRIED = buildConfiguration.getDataSiegfried();

        try {
            STORAGE_CONF = PropertiesUtils.getResourcePath(FUNCTIONAL_TNR_STORAGE_ENGINE_CONF).toString();
            DEFAULT_OFFER_CONF =
                PropertiesUtils.getResourcePath(FUNCTIONAL_TNR_STORAGE_DEFAULT_OFFER_NOSSL_CONF).toString();
            DEFAULT_OFFER_CONF2 =
                PropertiesUtils.getResourcePath(FUNCTIONAL_TNR_STORAGE_DEFAULT_OFFER_NOSSL2_CONF).toString();
            CONFIG_METADATA_PATH = PropertiesUtils.getResourcePath(FUNCTIONAL_TNR_METADATA_CONF).toString();
            CONFIG_WORKER_PATH = PropertiesUtils.getResourcePath("functional-tnr/worker.conf").toString();
            CONFIG_WORKSPACE_PATH =
                PropertiesUtils.getResourcePath(FUNCTIONAL_TNR_WORKSPACE_CONF).toString();
            CONFIG_PROCESSING_PATH =
                PropertiesUtils.getResourcePath(FUNCTIONAL_TNR_PROCESSING_CONF).toString();
            CONFIG_FUNCTIONAL_ADMIN_PATH =
                PropertiesUtils.getResourcePath(FUNCTIONAL_TNR_FUNCTIONAL_ADMINISTRATION_CONF).toString();
            CONFIG_LOGBOOK_PATH = PropertiesUtils.getResourcePath(FUNCTIONAL_TNR_LOGBOOK_CONF).toString();
            CONFIG_INGEST_INTERNAL_PATH =
                PropertiesUtils.getResourcePath(FUNCTIONAL_TNR_INGEST_INTERNAL_CONF).toString();
            CONFIG_ACCESS_INTERNAL_PATH =
                PropertiesUtils.getResourcePath(FUNCTIONAL_TNR_ACCESS_INTERNAL_CONF).toString();

            CONFIG_INGEST_EXTERNAL_PATH =
                PropertiesUtils.getResourcePath(FUNCTIONAL_TNR_INGEST_EXTERNAL_CONF).toString();
            CONFIG_ACCESS_EXTERNAL_PATH =
                PropertiesUtils.getResourcePath(FUNCTIONAL_TNR_ACCESS_EXTERNAL_CONF).toString();
        } catch (FileNotFoundException e) {
            LOGGER.error(e);
            System.exit(1);
        }

        // MoongoDB and ES
        LOGGER.warn("Start MongoDb and Elasticsearch");
        try {
            config = JunitHelper.startElasticsearchForTest(temporaryFolder, CLUSTER_NAME, TCP_PORT, HTTP_PORT);
        } catch (VitamApplicationServerException e1) {
            LOGGER.error(e1);
            earlyShutdown();
        }

        final MongodStarter starter = MongodStarter.getDefaultInstance();

        try {
            mongodExecutable = starter.prepare(new MongodConfigBuilder()
                .version(Version.Main.PRODUCTION)
                .net(new Net(DATABASE_PORT, Network.localhostIsIPv6()))
                .build());
            mongod = mongodExecutable.start();
        } catch (IOException e1) {
            LOGGER.error(e1);
            earlyShutdown();
        }

        // ES client
        final List<ElasticsearchNode> nodes = new ArrayList<>();
        nodes.add(new ElasticsearchNode(LOCALHOST, config.getTcpPort()));
        try {
            esClient = new LogbookElasticsearchAccess(CLUSTER_NAME, nodes);
        } catch (VitamException e1) {
            LOGGER.error(e1);
            earlyShutdown();
        }

        // launch metadata
        LOGGER.warn("Start Metadata");
        SystemPropertyUtil.set(MetaDataApplication.PARAMETER_JETTY_SERVER_PORT,
            Integer.toString(PORT_SERVICE_METADATA));
        medtadataApplication = new MetaDataApplication(CONFIG_METADATA_PATH);
        try {
            medtadataApplication.start();
        } catch (VitamApplicationServerException e1) {
            LOGGER.error(e1);
            earlyShutdown();
        }
        SystemPropertyUtil.clear(MetaDataApplication.PARAMETER_JETTY_SERVER_PORT);

        // launch workspace
        LOGGER.warn("Start Workspace");
        SystemPropertyUtil.set(WorkspaceApplication.PARAMETER_JETTY_SERVER_PORT,
            Integer.toString(PORT_SERVICE_WORKSPACE));
        workspaceApplication = new WorkspaceApplication(CONFIG_WORKSPACE_PATH);
        try {
            workspaceApplication.start();
        } catch (VitamApplicationServerException e1) {
            LOGGER.error(e1);
            earlyShutdown();
        }
        SystemPropertyUtil.clear(WorkspaceApplication.PARAMETER_JETTY_SERVER_PORT);

        // launch storage

        // first offer
        LOGGER.warn("Start Offer1");
        SystemPropertyUtil
            .set(JETTY_OFFER_PORT, Integer.toString(PORT_SERVICE_STORAGE_OFFER));
        fr.gouv.vitam.common.storage.StorageConfiguration offerConfiguration;
        try {
            offerConfiguration = PropertiesUtils
                .readYaml(PropertiesUtils.findFile(DEFAULT_OFFER_CONF),
                    fr.gouv.vitam.common.storage.StorageConfiguration.class);
            defaultOfferApplication = new DefaultOfferApplication(offerConfiguration);
            defaultOfferApplication.start();
        } catch (IOException | VitamApplicationServerException e1) {
            LOGGER.error(e1);
            earlyShutdown();
        }
        SystemPropertyUtil.clear(JETTY_OFFER_PORT);


        // second offer
        if (START_OFFER2) {
            LOGGER.warn("Start Offer2");
            SystemPropertyUtil
                .set(JETTY_OFFER2_PORT, Integer.toString(PORT_SERVICE_STORAGE_OFFER2));
            fr.gouv.vitam.common.storage.StorageConfiguration offerConfiguration2;
            try {
                offerConfiguration2 = PropertiesUtils
                    .readYaml(PropertiesUtils.findFile(DEFAULT_OFFER_CONF2),
                        fr.gouv.vitam.common.storage.StorageConfiguration.class);
                defaultOfferApplication2 = new DefaultOfferApplication(offerConfiguration2);
                defaultOfferApplication2.start();
            } catch (IOException | VitamApplicationServerException e1) {
                LOGGER.error(e1);
                earlyShutdown();
            }
            SystemPropertyUtil.clear(JETTY_OFFER2_PORT);
        }

        // storage engine
        LOGGER.warn("Start Storage");
        try {
            final StorageConfiguration serverConfiguration =
                PropertiesUtils.readYaml(PropertiesUtils.findFile(STORAGE_CONF),
                    StorageConfiguration.class);
            SystemPropertyUtil
                .set(JETTY_STORAGE_PORT, Integer.toString(PORT_SERVICE_STORAGE));
            storageApplication = new StorageApplication(serverConfiguration);
            storageApplication.start();
            SystemPropertyUtil.clear(JETTY_STORAGE_PORT);
        } catch (final VitamApplicationServerException | IOException e) {
            LOGGER.error(e);
            earlyShutdown();
        }

        // launch logbook
        LOGGER.warn("Start Logbook");
        SystemPropertyUtil
            .set(LogbookApplication.PARAMETER_JETTY_SERVER_PORT, Integer.toString(PORT_SERVICE_LOGBOOK));
        lgbapplication = new LogbookApplication(CONFIG_LOGBOOK_PATH);
        try {
            lgbapplication.start();
        } catch (VitamApplicationServerException e) {
            LOGGER.error(e);
            earlyShutdown();
        }
        SystemPropertyUtil.clear(LogbookApplication.PARAMETER_JETTY_SERVER_PORT);
        LogbookOperationsClientFactory.changeMode(new ClientConfigurationImpl(LOCALHOST, PORT_SERVICE_LOGBOOK));
        LogbookLifeCyclesClientFactory.changeMode(new ClientConfigurationImpl(LOCALHOST, PORT_SERVICE_LOGBOOK));

        // launch functional Admin server
        LOGGER.warn("Start Functional Admin");
        SystemPropertyUtil.set(JETTY_FUNCTIONAL_ADMIN_PORT, Integer.toString(PORT_SERVICE_FUNCTIONAL_ADMIN));
        adminApplication = new AdminManagementApplication(CONFIG_FUNCTIONAL_ADMIN_PATH);
        try {
            adminApplication.start();
        } catch (VitamApplicationServerException e) {
            LOGGER.error(e);
            earlyShutdown();
        }
        SystemPropertyUtil.clear(JETTY_FUNCTIONAL_ADMIN_PORT);

        // launch processing
        LOGGER.warn("Start Processing");
        SystemPropertyUtil.set(ProcessManagementApplication.PARAMETER_JETTY_SERVER_PORT,
            Integer.toString(PORT_SERVICE_PROCESSING));
        processManagementApplication = new ProcessManagementApplication(CONFIG_PROCESSING_PATH);
        try {
            processManagementApplication.start();
        } catch (VitamApplicationServerException e) {
            LOGGER.error(e);
            earlyShutdown();
        }
        SystemPropertyUtil.clear(ProcessManagementApplication.PARAMETER_JETTY_SERVER_PORT);

        // Launch Siegfried
        if (launchSiegfried) {
            LOGGER.warn("Start Siegfried");
            ProcessBuilder pb = new ProcessBuilder(SIEGFRIED_PATH, "-home", VITAM_APP_SIEGFRIED, "-serve",
                "localhost:" + PORT_SERVICE_SIEGFRIED);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            try {
                siegfried = pb.start();
                Thread.sleep(100);
            } catch (IOException | InterruptedException e) {
                LOGGER.error(e);
                earlyShutdown();
            }
            LOGGER.warn("Siegfried alive ? : " + siegfried.isAlive());
        }

        // launch worker
        LOGGER.warn("Start Worker");
        SystemPropertyUtil.set(JETTY_WORKER_PORT, Integer.toString(PORT_SERVICE_WORKER));
        try {
            wkrapplication = new WorkerApplication(CONFIG_WORKER_PATH);
            wkrapplication.start();
        } catch (FileNotFoundException | PluginException | InvalidParseOperationException |
            VitamApplicationServerException e) {
            LOGGER.error(e);
            shutdownSiegfried();
            earlyShutdown();
        }
        SystemPropertyUtil.clear(JETTY_WORKER_PORT);

        // launch access-internal
        LOGGER.warn("Start Access Internal");
        SystemPropertyUtil.set(JETTY_ACCESS_INTERNAL_PORT, Integer.toString(PORT_SERVICE_ACCESS_INTERNAL));
        accessInternalApplication =
            new AccessInternalApplication(CONFIG_ACCESS_INTERNAL_PATH);
        try {
            accessInternalApplication.start();
        } catch (VitamApplicationServerException e) {
            LOGGER.error(e);
            shutdownSiegfried();
            earlyShutdown();
        }
        SystemPropertyUtil.clear(JETTY_ACCESS_INTERNAL_PORT);

        // launch access-external
        LOGGER.warn("Start Access External");
        SystemPropertyUtil.set(JETTY_ACCESS_EXTERNAL_PORT, Integer.toString(PORT_SERVICE_ACCESS_EXTERNAL));
        accessExternalApplication =
            new AccessExternalApplication(CONFIG_ACCESS_EXTERNAL_PATH);
        try {
            accessExternalApplication.start();
        } catch (VitamApplicationServerException e) {
            LOGGER.error(e);
            shutdownSiegfried();
            earlyShutdown();
        }
        SystemPropertyUtil.clear(JETTY_ACCESS_EXTERNAL_PORT);

        // launch ingest-internal
        LOGGER.warn("Start Ingest Internal");
        SystemPropertyUtil.set(JETTY_INGEST_INTERNAL_PORT, Integer.toString(PORT_SERVICE_INGEST_INTERNAL));
        ingestInternalApplication = new IngestInternalApplication(CONFIG_INGEST_INTERNAL_PATH);
        try {
            ingestInternalApplication.start();
        } catch (VitamApplicationServerException e) {
            LOGGER.error(e);
            shutdownSiegfried();
            earlyShutdown();
        }
        SystemPropertyUtil.clear(JETTY_INGEST_INTERNAL_PORT);

        // launch ingest-external
        LOGGER.warn("Start Ingest external");
        SystemPropertyUtil.set(JETTY_INGEST_EXTERNAL_PORT, Integer.toString(PORT_SERVICE_INGEST_EXTERNAL));
        ingestExternalApplication = new IngestExternalApplication(CONFIG_INGEST_EXTERNAL_PATH);
        try {
            ingestExternalApplication.start();
        } catch (VitamApplicationServerException e) {
            LOGGER.error(e);
            shutdownSiegfried();
            earlyShutdown();
        }
        SystemPropertyUtil.clear(JETTY_INGEST_EXTERNAL_PORT);

        LOGGER.warn("ALL STARTED");

    }

    /**
     * Stop Vitam
     */
    @AfterClass
    public static void tearDownAfterClass() {
        LOGGER.error("try to shutdown ALL");
        try {
            if (ingestExternalApplication != null) {
                LOGGER.error("try to shutdown INGEST_EXTERNAL");
                ingestExternalApplication.stop();
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        try {
            if (ingestInternalApplication != null) {
                LOGGER.error("try to shutdown INGEST_INTERNAL");
                ingestInternalApplication.stop();
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        try {
            if (accessExternalApplication != null) {
                LOGGER.error("try to shutdown ACCESS_EXTERNAL");
                accessExternalApplication.stop();
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        try {
            if (accessInternalApplication != null) {
                LOGGER.error("try to shutdown ACCESS_INTERNAL");
                accessInternalApplication.stop();
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        try {
            if (wkrapplication != null) {
                LOGGER.error("try to shutdown WORKER");
                wkrapplication.stop();
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        try {
            if (processManagementApplication != null) {
                LOGGER.error("try to shutdown PROCESSING");
                processManagementApplication.stop();
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        try {
            if (adminApplication != null) {
                LOGGER.error("try to shutdown ADMIN_FUNCTIONAL");
                adminApplication.stop();
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        try {
            if (lgbapplication != null) {
                LOGGER.warn("try to shutdown LOGBOOK");
                lgbapplication.stop();
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        try {
            if (storageApplication != null) {
                LOGGER.warn("try to shutdown STORAGE");
                storageApplication.stop();
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        try {
            if (defaultOfferApplication != null) {
                LOGGER.warn("try to shutdown DEFAULT_OFFER");
                defaultOfferApplication.stop();
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        try {
            if (defaultOfferApplication2 != null) {
                LOGGER.warn("try to shutdown DEFAULT_OFFER2");
                defaultOfferApplication2.stop();
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        try {
            if (workspaceApplication != null) {
                LOGGER.warn("try to shutdown WORKSPACE");
                workspaceApplication.stop();
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        try {
            if (medtadataApplication != null) {
                LOGGER.warn("try to shutdown METADATA");
                medtadataApplication.stop();
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }

        earlyShutdown();
        LOGGER.error("ALL shutdown");
    }

    private static void earlyShutdown() {
        LOGGER.warn("try to shutdown ELEASTICSEARCH");
        if (esClient != null) {
            esClient.close();
        }
        if (config != null) {
            JunitHelper.stopElasticsearchForTest(config);
        }
        LOGGER.warn("try to shutdown MONGODB");
        if (mongod != null) {
            mongod.stop();
        }
        if (mongodExecutable != null) {
            mongodExecutable.stop();
        }
        // Clean directrories
        LOGGER.warn("try to CLEAN");
        if (temporaryFolder != null) {
            temporaryFolder.delete();
        }
        deleteDirectories();
        shutdownSiegfried();
    }

    private static void deleteDirectories() {
        String VITAM_DATA = buildConfiguration.getVitamData();
        if (VITAM_DATA != null && !VITAM_DATA.trim().isEmpty()) {
            LOGGER.warn("try to CLEAN DATA");
            String WORKSPACE = VITAM_DATA + WORKSPACE_SUBPATH;
            String OFFER = VITAM_DATA + OFFER_SUBPATH;
            String OFFER2 = VITAM_DATA + OFFER2_SUBPATH;
            String OFFER_INGEST = VITAM_DATA + WKSINGEST_SUBPATH;

            File workspaceRoot = new File(WORKSPACE);
            File storageRoot = new File(OFFER);
            File storageRoot2 = new File(OFFER2);
            File workspaceIngest = new File(OFFER_INGEST);
            FileUtil.deleteRecursive(workspaceRoot);
            FileUtil.deleteRecursive(storageRoot);
            FileUtil.deleteRecursive(storageRoot2);
            FileUtil.deleteRecursive(workspaceIngest);
            new File(VITAM_DATA + WORKER_DB_SUBPATH).delete();
        }
    }

    private static void createDirectories() {
        try {
            File tempFolder = temporaryFolder.newFolder();
            SystemPropertyUtil.set(VITAM_TMP_FOLDER, tempFolder.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.error(e);
            earlyShutdown();
        }

        String VITAM_DATA = buildConfiguration.getVitamData();
        if (VITAM_DATA == null || VITAM_DATA.trim().isEmpty()) {
            LOGGER.error("VITAM_DATA not defined");
            System.exit(1);
        }
        String WORKSPACE = VITAM_DATA + WORKSPACE_SUBPATH;
        String OFFER = VITAM_DATA + OFFER_SUBPATH;
        String OFFER2 = VITAM_DATA + OFFER2_SUBPATH;
        String OFFER_INGEST = VITAM_DATA + WKSINGEST_SUBPATH;
        SystemPropertyUtil.set("tnrBaseDirectory", buildConfiguration.getHomeItest());
        File dir = null;
        try {
            dir = PropertiesUtils.getResourceFile(TNR_CONFIG_CONF);
        } catch (FileNotFoundException e) {
            LOGGER.error(e);
            System.exit(1);
        }
        dir = dir.getParentFile();
        SystemPropertyUtil.set(ORG_OWASP_ESAPI_RESOURCES,
            dir.getAbsolutePath());


        File workspaceRoot = new File(WORKSPACE);
        new File(workspaceRoot, PROCESS_SUBPATH).mkdirs();
        File storageRoot = new File(OFFER);
        storageRoot.mkdirs();
        File storageRoot2 = new File(OFFER2);
        storageRoot2.mkdirs();
        new File(storageRoot, STORAGEZIP_SUBPATH).mkdirs();
        new File(storageRoot, STORAGELOG_SUBPATH).mkdirs();
        File workspaceIngest = new File(OFFER_INGEST);
        workspaceIngest.mkdirs();
        new File(VITAM_DATA + VERSION_FOLDER_SUBPATH).mkdirs();
    }

    private static void shutdownSiegfried() {
        String SIEGFRIED_PATH = buildConfiguration.getSiegfriedPath();
        boolean launchSiegfried = buildConfiguration.isLaunchSiegfried();
        if (launchSiegfried && siegfried != null) {
            LOGGER.warn("try to shutdown SIEGFRIED");
            // Try to kill Siegfried
            try {
                Process kill = Runtime.getRuntime().exec(new String[] {"killall", "-SIGKILL", SIEGFRIED_PATH});
                Thread.sleep(100);
                kill.destroy();
                // does not work
                siegfried.destroyForcibly();
                Thread.sleep(100);
            } catch (Exception e) {
                LOGGER.error(e);
            }
        }
    }

    /**
     * Run TNR
     */
    @Test
    public void runTestTNR() {
        try {
            String HOME_ITEST = buildConfiguration.getHomeItest();
            String specificTest = buildConfiguration.getSpecificItest();
            if (specificTest != null && !specificTest.trim().isEmpty()) {
                HOME_ITEST = specificTest;
            }
            LOGGER.warn("START TEST: {}", HOME_ITEST);
            long start = System.nanoTime();
            cucumber.api.cli.Main.run(new String[] {"-g", "fr.gouv.vitam.functionaltest.cucumber",
                "-p", "fr.gouv.vitam.functionaltest.cucumber.report.VitamReporter:report.json",
                HOME_ITEST}, Thread.currentThread().getContextClassLoader());
            long end = System.nanoTime();
            Thread.sleep(100);
            LOGGER.warn("ENDING TEST: {} in {} ms", HOME_ITEST, (end-start)/1000000);
        } catch (Throwable e) {
            LOGGER.error(e);
        }
    }

}
