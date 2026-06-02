package com.migration.repository;

import com.migration.model.MigrationRecord;
import com.migration.model.MigrationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MigrationRepository {
    private static final Logger logger = LoggerFactory.getLogger(MigrationRepository.class);
    private final String dbPath;

    public MigrationRepository(String dbPath) {
        this.dbPath = dbPath;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    public void initialize() {
        logger.info("Initializing database at: {}", dbPath);
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS migration_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                box_file_id TEXT NOT NULL UNIQUE,
                box_file_path TEXT,
                box_file_name TEXT,
                user_email TEXT NOT NULL,
                google_drive_file_id TEXT,
                google_drive_path TEXT,
                google_drive_web_view_link TEXT,
                status TEXT NOT NULL,
                error_message TEXT,
                original_format TEXT,
                converted_format TEXT,
                file_size_bytes BIGINT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                completed_at TIMESTAMP
            )
        """;

        String createIndexStatus = "CREATE INDEX IF NOT EXISTS idx_status ON migration_records(status)";
        String createIndexBoxFileId = "CREATE INDEX IF NOT EXISTS idx_box_file_id ON migration_records(box_file_id)";
        String createIndexUserEmail = "CREATE INDEX IF NOT EXISTS idx_user_email ON migration_records(user_email)";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            stmt.execute(createIndexStatus);
            stmt.execute(createIndexBoxFileId);
            stmt.execute(createIndexUserEmail);
            logger.info("Database initialized successfully");
        } catch (SQLException e) {
            logger.error("Failed to initialize database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    public void insertOrUpdateRecord(MigrationRecord record) {
        String sql = """
            INSERT INTO migration_records (
                box_file_id, box_file_path, box_file_name, user_email,
                google_drive_file_id, google_drive_path, google_drive_web_view_link,
                status, error_message, original_format, converted_format,
                file_size_bytes, updated_at, completed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
            ON CONFLICT(box_file_id) DO UPDATE SET
                google_drive_file_id = excluded.google_drive_file_id,
                google_drive_path = excluded.google_drive_path,
                google_drive_web_view_link = excluded.google_drive_web_view_link,
                status = excluded.status,
                error_message = excluded.error_message,
                original_format = excluded.original_format,
                converted_format = excluded.converted_format,
                file_size_bytes = excluded.file_size_bytes,
                updated_at = CURRENT_TIMESTAMP,
                completed_at = excluded.completed_at
        """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, record.getBoxFileId());
            pstmt.setString(2, record.getBoxFilePath());
            pstmt.setString(3, record.getBoxFileName());
            pstmt.setString(4, record.getUserEmail());
            pstmt.setString(5, record.getGoogleDriveFileId());
            pstmt.setString(6, record.getGoogleDrivePath());
            pstmt.setString(7, record.getGoogleDriveWebViewLink());
            pstmt.setString(8, record.getStatus().name());
            pstmt.setString(9, record.getErrorMessage());
            pstmt.setString(10, record.getOriginalFormat());
            pstmt.setString(11, record.getConvertedFormat());
            if (record.getFileSizeBytes() != null) {
                pstmt.setLong(12, record.getFileSizeBytes());
            } else {
                pstmt.setNull(12, Types.BIGINT);
            }
            pstmt.setTimestamp(13, record.getCompletedAt());

            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to insert/update record for box file ID: {}", record.getBoxFileId(), e);
            throw new RuntimeException("Database operation failed", e);
        }
    }

    public void updateStatus(String boxFileId, MigrationStatus status, String errorMessage) {
        String sql = "UPDATE migration_records SET status = ?, error_message = ?, updated_at = CURRENT_TIMESTAMP WHERE box_file_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status.name());
            pstmt.setString(2, errorMessage);
            pstmt.setString(3, boxFileId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to update status for box file ID: {}", boxFileId, e);
        }
    }

    public List<MigrationRecord> getPendingRecords() {
        String sql = "SELECT * FROM migration_records WHERE status IN ('PENDING', 'FAILED') ORDER BY id";
        List<MigrationRecord> records = new ArrayList<>();

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                records.add(mapResultSetToRecord(rs));
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve pending records", e);
            throw new RuntimeException("Database query failed", e);
        }

        return records;
    }

    public MigrationRecord getRecordByBoxFileId(String boxFileId) {
        String sql = "SELECT * FROM migration_records WHERE box_file_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, boxFileId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToRecord(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve record for box file ID: {}", boxFileId, e);
        }

        return null;
    }

    public List<MigrationRecord> getAllRecords() {
        String sql = "SELECT * FROM migration_records ORDER BY id";
        List<MigrationRecord> records = new ArrayList<>();

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                records.add(mapResultSetToRecord(rs));
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve all records", e);
            throw new RuntimeException("Database query failed", e);
        }

        return records;
    }

    public Map<MigrationStatus, Long> getStatistics() {
        String sql = "SELECT status, COUNT(*) as count FROM migration_records GROUP BY status";
        Map<MigrationStatus, Long> stats = new HashMap<>();

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String statusStr = rs.getString("status");
                long count = rs.getLong("count");
                try {
                    MigrationStatus status = MigrationStatus.valueOf(statusStr);
                    stats.put(status, count);
                } catch (IllegalArgumentException e) {
                    logger.warn("Unknown status in database: {}", statusStr);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve statistics", e);
        }

        return stats;
    }

    private MigrationRecord mapResultSetToRecord(ResultSet rs) throws SQLException {
        MigrationRecord record = new MigrationRecord();
        record.setId(rs.getLong("id"));
        record.setBoxFileId(rs.getString("box_file_id"));
        record.setBoxFilePath(rs.getString("box_file_path"));
        record.setBoxFileName(rs.getString("box_file_name"));
        record.setUserEmail(rs.getString("user_email"));
        record.setGoogleDriveFileId(rs.getString("google_drive_file_id"));
        record.setGoogleDrivePath(rs.getString("google_drive_path"));
        record.setGoogleDriveWebViewLink(rs.getString("google_drive_web_view_link"));
        record.setStatus(MigrationStatus.valueOf(rs.getString("status")));
        record.setErrorMessage(rs.getString("error_message"));
        record.setOriginalFormat(rs.getString("original_format"));
        record.setConvertedFormat(rs.getString("converted_format"));
        long fileSize = rs.getLong("file_size_bytes");
        if (!rs.wasNull()) {
            record.setFileSizeBytes(fileSize);
        }
        record.setCreatedAt(rs.getTimestamp("created_at"));
        record.setUpdatedAt(rs.getTimestamp("updated_at"));
        record.setCompletedAt(rs.getTimestamp("completed_at"));
        return record;
    }
}
