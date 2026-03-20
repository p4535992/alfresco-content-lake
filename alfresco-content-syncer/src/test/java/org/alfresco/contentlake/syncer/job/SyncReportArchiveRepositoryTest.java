package org.alfresco.contentlake.syncer.job;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncReportArchiveRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsArchivedReports() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:file:" + tempDir.resolve("archive-db").toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");

        SyncReportArchiveRepository repository = new SyncReportArchiveRepository();
        repository.dataSource = dataSource;
        repository.init();

        repository.save("job-1", "{\"status\":\"ok\"}", "summary,\"filesUploaded\",\"1\"");

        assertEquals("{\"status\":\"ok\"}", repository.findJson("job-1").orElseThrow());
        assertEquals("summary,\"filesUploaded\",\"1\"", repository.findCsv("job-1").orElseThrow());
        assertTrue(repository.findJson("missing").isEmpty());
        assertTrue(repository.findCsv("missing").isEmpty());
    }
}
