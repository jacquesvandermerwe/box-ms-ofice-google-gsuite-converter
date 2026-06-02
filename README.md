# Box to Google Drive Migration Tool

A Java application for migrating files from Box.com to Google Drive with automatic format conversion from Microsoft Office formats to Google Workspace formats.

## Features

- **Selective Migration**: Process only specific files defined in CSV input
- **Format Conversion**: Automatically converts Office files to Google Workspace formats
  - .docx → Google Docs
  - .xlsx → Google Sheets
  - .pptx → Google Slides
- **Folder Structure Preservation**: Maintains Box folder hierarchy in Google Drive
- **🚀 Virtual Threads**: Lightweight, scalable concurrency (100-500+ concurrent migrations)
- **High Performance**: 10x faster than traditional threading with minimal resource usage
- **Resume Capability**: SQLite database tracks state, allowing safe restarts
- **Comprehensive Reporting**: Detailed logging and database reporting
- **Error Handling**: Robust error handling with detailed error messages

## Prerequisites

- **Java 21 or higher** (required for virtual threads)
- Maven 3.6+
- Box.com account with API access
- Google Cloud project with Drive API enabled
- Service account credentials for Google Drive

> **Note**: Virtual threads require Java 21+. See [VIRTUAL_THREADS.md](VIRTUAL_THREADS.md) for installation and performance details.

## Setup

### 1. Box Setup

1. Go to [Box Developer Console](https://app.box.com/developers/console)
2. Create a new Custom App
3. Choose **Server Authentication (with JWT)** or use **Developer Token** for testing
4. Grant the app **Read all files and folders** scope
5. Note your:
   - Client ID
   - Client Secret
   - Developer Token (for testing)

### 2. Google Cloud Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable **Google Drive API**
4. Create a **Service Account**:
   - Go to IAM & Admin → Service Accounts
   - Create Service Account
   - Download JSON key file
5. Enable **Domain-Wide Delegation**:
   - Edit the service account
   - Check "Enable G Suite Domain-wide Delegation"
   - Note the Client ID
6. In Google Workspace Admin Console:
   - Navigate to Security → API Controls → Domain-wide Delegation
   - Add new API client with the service account Client ID
   - Add OAuth Scopes:
     - `https://www.googleapis.com/auth/drive`
     - `https://www.googleapis.com/auth/drive.file`

### 3. Configuration

1. Copy `src/main/resources/application.properties` and update:

```properties
# Box Configuration
box.client.id=YOUR_BOX_CLIENT_ID
box.client.secret=YOUR_BOX_CLIENT_SECRET
box.developer.token=YOUR_BOX_DEV_TOKEN
box.enterprise.id=YOUR_ENTERPRISE_ID

# Google Drive Configuration (use absolute path)
google.credentials.file=/path/to/your/service-account-key.json
google.application.name=Box-Google-Converter

# Database Configuration
db.path=./migration-results.db

# Threading Configuration (Virtual Threads)
# Virtual threads are lightweight - can handle 100-500+ concurrent migrations
# Recommended: 100 for most use cases, 200-500 for large-scale migrations
thread.pool.size=100
thread.pool.max.size=500

# CSV Input
csv.input.path=./migration-input.csv

# Retry Configuration
retry.max.attempts=3
retry.delay.seconds=5
```

### 4. Prepare CSV Input

Create `migration-input.csv` with the following format:

```csv
box_file_id,box_file_path,user_email
123456789,/Marketing/Q1/Report.docx,user1@example.com
987654321,/Sales/Budget.xlsx,user2@example.com
456789123,/HR/Presentation.pptx,user3@example.com
```

**Columns:**
- `box_file_id`: The Box file ID (found in Box URL or via API)
- `box_file_path`: The folder path in Box where the file is located
- `user_email`: The target Google Drive user email (for domain-wide delegation)

## Building

```bash
mvn clean package
```

This creates an executable JAR: `target/box-google-converter-1.0-SNAPSHOT-jar-with-dependencies.jar`

## Running

```bash
java -jar target/box-google-converter-1.0-SNAPSHOT-jar-with-dependencies.jar
```

Or run directly with Maven:

```bash
mvn exec:java -Dexec.mainClass="com.migration.Main"
```

## How It Works

1. **CSV Loading**: Reads the input CSV and creates PENDING records in SQLite database
2. **Resume Support**: Checks database for existing records (allows safe restart)
3. **Parallel Processing**: Creates thread pool and processes files concurrently
4. **Per-File Migration**:
   - Downloads file from Box
   - Creates folder structure in Google Drive
   - Checks if file already exists (fails if exists)
   - Uploads file to Google Drive
   - Converts to Google Workspace format
   - Updates database with results
5. **Summary Report**: Displays statistics and success/failure counts

## Database Schema

The SQLite database (`migration-results.db`) contains:

```sql
CREATE TABLE migration_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    box_file_id TEXT NOT NULL UNIQUE,
    box_file_path TEXT NOT NULL,
    box_file_name TEXT NOT NULL,
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
);
```

## Querying Results

Query the database to check migration status:

```bash
sqlite3 migration-results.db
```

```sql
-- Summary by status
SELECT status, COUNT(*) FROM migration_records GROUP BY status;

-- Failed migrations
SELECT box_file_id, box_file_name, error_message 
FROM migration_records 
WHERE status = 'FAILED';

-- Completed migrations
SELECT box_file_id, box_file_name, google_drive_file_id, google_drive_web_view_link
FROM migration_records 
WHERE status = 'COMPLETED';
```

## Resume After Failure

The application automatically resumes from where it left off:

1. If the application crashes or is stopped, simply restart it
2. The database tracks which files are PENDING, IN_PROGRESS, COMPLETED, or FAILED
3. Only PENDING and FAILED records will be retried

## Logging

Logs are written to:
- **Console**: Real-time progress
- **File**: `logs/migration-{date}.log` (rolling daily, 30-day retention)

## Virtual Threads Performance

This application uses **Java 21 Virtual Threads** for exceptional performance with minimal resource usage.

### Performance Comparison

| Metric | Platform Threads (Java 17) | Virtual Threads (Java 21) |
|--------|---------------------------|---------------------------|
| Max Concurrent | 5-10 | 100-500+ |
| Memory per thread | ~1-2 MB | ~Few KB |
| Throughput | 5-10 files/min | **50-200+ files/min** |
| Resource Usage | High | Low |

### Why Virtual Threads?

Migration is **I/O-bound** (network operations waiting on Box and Google Drive APIs). Virtual threads:
- Automatically park when waiting for I/O
- Allow thousands of concurrent operations
- Use minimal memory
- Provide 10x+ performance improvement

### Configuration Recommendations

```properties
# Small migrations (< 100 files)
thread.pool.size=50

# Medium migrations (100-1000 files) - RECOMMENDED
thread.pool.size=100

# Large migrations (1000+ files)
thread.pool.size=200

# Massive migrations (10,000+ files)
thread.pool.size=500
```

**See [VIRTUAL_THREADS.md](VIRTUAL_THREADS.md) for detailed performance analysis and tuning guide.**

## Troubleshooting

### "Box credentials not configured properly"
- Ensure `box.developer.token` is set in `application.properties`
- Verify the token is valid (test in Box API Explorer)

### "Google credentials file not configured"
- Check that `google.credentials.file` points to a valid service account JSON file
- Use absolute path to the credentials file

### "File already exists at destination"
- The application fails if a file with the same name exists in the target folder
- This is by design (no overwrite per requirements)
- Manually remove the file or update the CSV to skip it

### "Failed to download file from Box"
- Verify the Box file ID is correct
- Check that the application has read permissions
- Ensure the file hasn't been deleted from Box

### "Security error while uploading file"
- Verify domain-wide delegation is configured correctly
- Ensure the service account has the correct OAuth scopes
- Check that the user email is valid in your Google Workspace domain

## Testing

Run unit tests:

```bash
mvn test
```

## Architecture

```
┌─────────────┐
│    Main     │
└──────┬──────┘
       │
       ├──────────────┐
       │              │
┌──────▼──────┐  ┌───▼────────────┐
│ AppConfig   │  │ Credentials    │
│             │  │ Manager        │
└─────────────┘  └────────────────┘
       │
       │
┌──────▼──────────────────────┐
│ MigrationOrchestrator       │
│  - Loads CSV                │
│  - Creates thread pool      │
│  - Coordinates tasks        │
└──────┬──────────────────────┘
       │
       ├─────────────┬─────────────┬──────────────┐
       │             │             │              │
┌──────▼──────┐ ┌───▼────────┐ ┌─▼─────────┐ ┌──▼──────────┐
│ BoxService  │ │ GoogleDrive│ │Conversion │ │ Migration   │
│             │ │ Service    │ │ Service   │ │ Repository  │
└─────────────┘ └────────────┘ └───────────┘ └─────────────┘
       │             │             │              │
       └─────────────┴─────────────┴──────────────┘
                      │
              ┌───────▼────────┐
              │ Migration      │
              │ TaskProcessor  │
              │ (per file)     │
              └────────────────┘
```

## License

This is a proprietary migration tool. All rights reserved.

## Support

For issues or questions:
- Check the logs in `logs/migration-{date}.log`
- Query the database for detailed error messages
- Review Box and Google API documentation
