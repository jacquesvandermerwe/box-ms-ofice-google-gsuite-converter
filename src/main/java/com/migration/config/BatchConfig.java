package com.migration.config;

import com.migration.model.MigrationRecord;
import com.migration.processor.MigrationItemProcessor;
import com.migration.repository.MigrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.boot.autoconfigure.batch.BatchDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;

import javax.sql.DataSource;
import java.util.List;

@Configuration
public class BatchConfig {
    private static final Logger logger = LoggerFactory.getLogger(BatchConfig.class);

    @Bean
    @Primary
    public DataSource dataSource(AppConfig config) {
        logger.info("Initializing primary SQLite database datasource for: {}", config.getDbPath());
        com.zaxxer.hikari.HikariDataSource dataSource = new com.zaxxer.hikari.HikariDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setJdbcUrl("jdbc:sqlite:" + config.getDbPath() + "?journal_mode=WAL&busy_timeout=5000");
        dataSource.setMaximumPoolSize(1);
        dataSource.setConnectionTimeout(30000);
        return dataSource;
    }

    @Bean
    @BatchDataSource
    public DataSource batchDataSource() {
        logger.info("Initializing in-memory H2 database for Spring Batch metadata");
        com.zaxxer.hikari.HikariDataSource dataSource = new com.zaxxer.hikari.HikariDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setJdbcUrl("jdbc:h2:mem:batchdb;DB_CLOSE_DELAY=-1;MODE=MySQL");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        dataSource.setMaximumPoolSize(10);
        dataSource.setConnectionTimeout(30000);
        return dataSource;
    }

    @Bean
    @StepScope
    public ItemReader<MigrationRecord> itemReader(MigrationRepository repository) {
        return new ItemReader<>() {
            private List<MigrationRecord> records;
            private int nextIndex = 0;

            @Override
            public synchronized MigrationRecord read() {
                if (records == null) {
                    records = repository.getPendingRecords();
                    nextIndex = 0;
                    logger.info("Spring Batch Reader loaded {} pending/failed migration records from SQLite", records.size());
                }
                if (nextIndex < records.size()) {
                    return records.get(nextIndex++);
                }
                return null; // Signals end of data to Spring Batch
            }
        };
    }

    @Bean
    public ItemWriter<MigrationRecord> itemWriter(MigrationRepository repository) {
        return chunk -> {
            logger.debug("Spring Batch Writer updating chunk of {} records in SQLite", chunk.size());
            for (MigrationRecord record : chunk) {
                repository.insertOrUpdateRecord(record);
            }
        };
    }

    @Bean
    public TaskExecutor taskExecutor() {
        logger.info("Configuring TaskExecutor with virtual threads for concurrent Spring Batch steps");
        SimpleAsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor("batch-virtual-");
        taskExecutor.setVirtualThreads(true);
        return taskExecutor;
    }

    @Bean
    public Step migrationStep(JobRepository jobRepository,
                             ItemReader<MigrationRecord> reader, MigrationItemProcessor processor,
                             ItemWriter<MigrationRecord> writer, TaskExecutor taskExecutor,
                             AppConfig config) {
        // Use ResourcelessTransactionManager to avoid deadlock with HikariCP pool size 1
        // This allows the repository to manage its own short-lived connections
        // enabling true parallel processing with virtual threads
        return new StepBuilder("migrationStep", jobRepository)
                .<MigrationRecord, MigrationRecord>chunk(1, new ResourcelessTransactionManager())
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .taskExecutor(taskExecutor)
                .throttleLimit(config.getThreadPoolSize())
                .build();
    }

    @Bean
    public Job migrationJob(JobRepository jobRepository, Step step) {
        return new JobBuilder("migrationJob", jobRepository)
                .start(step)
                .build();
    }
}
