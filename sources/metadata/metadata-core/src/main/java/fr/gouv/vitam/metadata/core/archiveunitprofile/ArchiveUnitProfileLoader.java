/*******************************************************************************
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
 *******************************************************************************/
package fr.gouv.vitam.metadata.core.archiveunitprofile;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.exception.VitamRuntimeException;
import fr.gouv.vitam.common.model.RequestResponse;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.administration.ArchiveUnitProfileModel;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.functional.administration.client.AdminManagementClient;
import fr.gouv.vitam.functional.administration.client.AdminManagementClientFactory;
import fr.gouv.vitam.functional.administration.common.exception.AdminManagementClientServerException;
import fr.gouv.vitam.functional.administration.common.exception.ReferentialNotFoundException;

import java.util.concurrent.TimeUnit;

public class ArchiveUnitProfileLoader {

    private final AdminManagementClientFactory adminManagementClientFactory;
    private final LoadingCache<String, ArchiveUnitProfileModel> archiveUnitProfileCache;

    public ArchiveUnitProfileLoader(
        AdminManagementClientFactory adminManagementClientFactory, int maxEntriesInCache, int cacheTimeoutInSeconds) {

        this.adminManagementClientFactory = adminManagementClientFactory;
        CacheBuilder<Object, Object> objectObjectCacheBuilder = CacheBuilder.newBuilder();
        // Max entries in cache
        objectObjectCacheBuilder.maximumSize(maxEntriesInCache);
        // Access timeout
        objectObjectCacheBuilder.expireAfterAccess(cacheTimeoutInSeconds, TimeUnit.SECONDS);
        // Okay to GC
        objectObjectCacheBuilder.weakValues();
        this.archiveUnitProfileCache = objectObjectCacheBuilder
            .build(new CacheLoader<String, ArchiveUnitProfileModel>() {
                @Override
                public ArchiveUnitProfileModel load(String key) {
                    String aupId = key.substring(key.indexOf('/') + 1);
                    return loadArchiveUnitProfileFromAdminManagement(aupId);
                }
            });
    }

    public ArchiveUnitProfileModel loadArchiveUnitProfile(String aupId) {
        String requestId = VitamThreadUtils.getVitamSession().getRequestId();
        return this.archiveUnitProfileCache.getUnchecked(requestId + "/" + aupId);
    }

    private ArchiveUnitProfileModel loadArchiveUnitProfileFromAdminManagement(String aupId) {

        try (AdminManagementClient adminClient = adminManagementClientFactory.getClient()) {
            RequestResponse<ArchiveUnitProfileModel> aup = adminClient.findArchiveUnitProfilesByID(aupId);
            if (!aup.isOk()) {
                throw new VitamRuntimeException("Could not load ArchiveUnitProfile");
            }

            return ((RequestResponseOK<ArchiveUnitProfileModel>) aup).getFirstResult();

        } catch (ReferentialNotFoundException e) {
            throw new ArchiveUnitProfileException("ArchiveUnitProfile " + aupId + " not found", e);
        } catch (AdminManagementClientServerException e) {
            throw new VitamRuntimeException("Could not load ArchiveUnitProfile", e);
        } catch (InvalidParseOperationException e) {
            throw new IllegalStateException("Invalid ArchiveUnitProfile", e);
        }
    }
}
