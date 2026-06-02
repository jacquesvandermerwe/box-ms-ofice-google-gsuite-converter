package com.migration.util;

import com.migration.model.MigrationRecord;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class CsvReader {
    private static final Logger logger = LoggerFactory.getLogger(CsvReader.class);

    public static List<MigrationRecord> readCsv(String csvPath) {
        logger.info("Reading CSV file: {}", csvPath);
        List<MigrationRecord> records = new ArrayList<>();

        try (Reader reader = Files.newBufferedReader(Paths.get(csvPath));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build())) {

            for (CSVRecord csvRecord : csvParser) {
                try {
                    String boxFileId = csvRecord.get("box_file_id");
                    String boxFilePath = csvRecord.get("box_file_path");
                    String userEmail = csvRecord.get("user_email");

                    String boxFileName = extractFileName(boxFilePath);

                    MigrationRecord record = new MigrationRecord(boxFileId, boxFilePath, boxFileName, userEmail);
                    records.add(record);

                } catch (IllegalArgumentException e) {
                    logger.error("Error parsing CSV record at line {}: {}",
                                csvRecord.getRecordNumber(), e.getMessage());
                }
            }

            logger.info("Successfully read {} records from CSV", records.size());
        } catch (IOException e) {
            logger.error("Failed to read CSV file: {}", csvPath, e);
            throw new RuntimeException("Failed to read CSV file", e);
        }

        return records;
    }

    private static String extractFileName(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }

        path = path.replace('\\', '/');

        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return path.substring(lastSlash + 1);
        }

        return path;
    }
}
