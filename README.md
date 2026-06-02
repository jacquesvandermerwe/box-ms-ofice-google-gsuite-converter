# Box to Google Drive Migration Tool

A Java application for migrating files from Box.com to Google Drive with automatic format conversion from Microsoft Office formats to Google Workspace formats.

## Features

### Core Features
- **Selective Migration**: Process only specific files defined in CSV input
- **Format Conversion**: Automatically converts Office files to Google Workspace formats
  - .docx, .doc → Google Docs
  - .xlsx, .xls → Google Sheets
  - .pptx, .ppt → Google Slides
- **Folder Structure Preservation**: Maintains Box folder hierarchy in Google Drive automatically
- **🚀 Virtual Threads**: Lightweight, scalable concurrency (100-500+ concurrent migrations)
- **High Performance**: 10x faster than traditional threading with minimal resource usage
- **Resume Capability**: SQLite database tracks state, allowing safe restarts
- **Duplicate Detection**: Prevents re-uploading files that already exist
- **Comprehensive Reporting**: Detailed logging and database reporting
- **Error Handling**: Robust error handling with detailed error messages

### Authentication Options
- **Box Authentication**:
  - JWT Config (production - auto-refreshing tokens) ✅ Recommended
  - Developer Token (testing - expires in 60 minutes)
  - As-User header support for service account impersonation
- **Google Authentication**:
  - OAuth 2.0 (personal Google accounts) ✅ No Google Workspace required
  - Service Account with domain-wide delegation (Google Workspace)

### CSV Flexibility
- **Simplified format**: Only `box_file_id` and `user_email` required
- **Automatic path detection**: File paths fetched from Box API (optional in CSV)
- **Smart duplicate checking**: Strips extensions when checking for converted files

## Prerequisites

- **Java 21 or higher** (required for virtual threads)
- Maven 3.6+
- Box.com account with API access
- Google Cloud project with Drive API enabled
- **One of:**
  - Personal Google account (for OAuth mode - uploads to your Drive)
  - Google Workspace with admin access (for Service Account mode - multi-user)

> **Note**: Virtual threads require Java 21+. See [VIRTUAL_THREADS.md](VIRTUAL_THREADS.md) for installation and performance details.

## Quick Start

**Choose your setup path:**

- **Personal Google Account?** → See [OAUTH_SETUP.md](OAUTH_SETUP.md) (5 minute setup)
- **Google Workspace?** → See [SETUP_GUIDE.md](SETUP_GUIDE.md) (full setup guide)
- **Compare both options?** → See [GOOGLE_CLOUD_SETUP_COMPARISON.md](GOOGLE_CLOUD_SETUP_COMPARISON.md)

## Detailed Setup

See [SETUP_GUIDE.md](SETUP_GUIDE.md) for comprehensive setup instructions covering:
- Box JWT authentication and developer tokens
- Google OAuth (personal accounts) and Service Account (Workspace)
- Box As-User header configuration
- CSV format options (simplified vs. full)
- Troubleshooting common issues

### Quick Configuration Example

```properties
# Box Configuration - Choose ONE:
box.config.file=/path/to/box_config.json  # JWT (recommended)
# OR
box.developer.token=YOUR_TOKEN  # Testing only (expires 60 min)

# Box As-User (optional but often required for JWT)
box.as.user.id=YOUR_BOX_USER_ID

# Google Drive - OAuth Mode (Personal Account)
google.auth.type=oauth
google.credentials.file=/path/to/oauth-credentials.json

# OR Google Drive - Service Account Mode (Workspace)
# google.auth.type=service_account
# google.credentials.file=/path/to/service-account-key.json

# Database & CSV
db.path=./migration-results.db
csv.input.path=./migration-input.csv

# Virtual Threads (lightweight, scalable)
thread.pool.size=100

# CSV Input
csv.input.path=./migration-input.csv

# Retry Configuration
retry.max.attempts=3
retry.delay.seconds=5
```

### CSV Input Format

**Simplified format (recommended):**
```csv
box_file_id,user_email
123456789,user@example.com
987654321,user@example.com
```

The application automatically fetches file paths and names from Box API.

**Legacy format (with explicit paths):**
```csv
box_file_id,box_file_path,user_email
123456789,/Marketing/Q1,user@example.com
```

**Columns:**
- `box_file_id`: **Required** - The Box file ID (from Box URL or API)
- `user_email`: **Required** - Target user email (ignored in OAuth mode)
- `box_file_path`: **Optional** - Custom folder path (auto-detected if omitted)

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
