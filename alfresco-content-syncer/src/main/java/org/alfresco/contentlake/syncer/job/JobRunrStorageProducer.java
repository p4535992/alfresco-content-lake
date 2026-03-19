package org.alfresco.contentlake.syncer.job;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.sql.h2.H2StorageProvider;

import javax.sql.DataSource;

@ApplicationScoped
public class JobRunrStorageProducer {

    @Inject
    DataSource dataSource;

    @Produces
    @ApplicationScoped
    StorageProvider storageProvider() {
        return new H2StorageProvider(dataSource);
    }
}
