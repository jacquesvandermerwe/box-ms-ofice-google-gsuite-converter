# Box to Google Drive Migration Tool - Setup Guide

## Quick Start

This guide will walk you through setting up and running the Box to Google Drive migration tool.

## System Requirements

- **Java**: Java 21 or higher (required for virtual threads)
- **Maven**: Maven 3.6+ (for building from source)
- **Box.com**: Account with API access
- **Google Workspace**: Domain with admin access for service account setup

> **Note**: This application uses Java 21 virtual threads for high-performance, lightweight concurrency. See [VIRTUAL_THREADS.md](VIRTUAL_THREADS.md) for details.

### Verify Java Version

```bash
java -version
# Should show version 21 or higher
```

If you need to install Java 21, download from [Adoptium](https://adoptium.net/) or use:

```bash
# macOS (using Homebrew)
brew install openjdk@21

# Ubuntu/Debian
sudo apt install openjdk-21-jdk

# Windows - Download from Oracle or Adoptium
```

## Step 1: Box.com Setup

### Option A: Using Developer Token (Quick Testing)

1. Log in to [Box Developer Console](https://app.box.com/developers/console)
2. Click **Create New App**
3. Select **Custom App**
4. Choose **Server Authentication (with JWT)** or **OAuth 2.0 with Client Credentials**
5. Name your app (e.g., "Box-Google-Migration")
6. Click **View Your App**
7. Go to **Configuration** tab
8. Scroll down to **Developer Token** section
9. Click **Generate Developer Token**
10. **Copy the token** (valid for 60 minutes - for testing only)

### Option B: Using JWT (Production)

1. Follow steps 1-6 from Option A
2. Go to **Configuration** → **Add and Manage Public Keys**
3. Generate a keypair or upload your public key
4. Download the JSON config file (contains your private key)
5. Go to **Authorization** tab
6. Click **Review and Submit** to request admin approval
7. Wait for admin approval

**Important**: For this tool, we'll use the **Developer Token** approach for simplicity.

### Required Box Permissions

In the Box Developer Console, ensure your app has:
- ✅ **Read all files and folders**
- ✅ **Write all files and folders** (optional, for future features)

## Step 2: Google Cloud & Drive API Setup

### 2.1 Create Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Click on the project dropdown → **New Project**
3. Name: "Box-Google-Migration"
4. Click **Create**
5. Wait for project creation (1-2 minutes)

### 2.2 Enable Google Drive API

1. In Google Cloud Console, select your new project
2. Go to **APIs & Services** → **Library**
3. Search for "Google Drive API"
4. Click **Google Drive API**
5. Click **Enable**

### 2.3 Create Service Account

1. Go to **APIs & Services** → **Credentials**
2. Click **+ CREATE CREDENTIALS** → **Service Account**
3. Service account details:
   - **Name**: `box-migration-service`
   - **Service account ID**: (auto-generated)
   - **Description**: "Service account for Box to Google Drive migration"
4. Click **Create and Continue**
5. Grant role: **None needed** (we'll use domain-wide delegation)
6. Click **Continue** → **Done**

### 2.4 Create Service Account Key

1. Click on the service account you just created
2. Go to **Keys** tab
3. Click **Add Key** → **Create new key**
4. Select **JSON**
5. Click **Create**
6. **Save the downloaded JSON file** securely (e.g., `google-credentials.json`)
7. **Important**: Store this file safely - it contains private keys!

### 2.5 Enable Domain-Wide Delegation

1. Still in the service account page, click **Show Advanced Settings**
2. Under "Domain-wide delegation", click **Enable G Suite Domain-wide Delegation**
3. **Copy the Client ID** (you'll need this next)

### 2.6 Authorize in Google Workspace Admin Console

1. Log in to [Google Admin Console](https://admin.google.com/)
2. Go to **Security** → **Access and data control** → **API Controls**
3. Scroll to **Domain-wide Delegation**
4. Click **Add new**
5. Paste the **Client ID** from step 2.5
6. Add OAuth Scopes:
   ```
   https://www.googleapis.com/auth/drive
   https://www.googleapis.com/auth/drive.file
   ```
7. Click **Authorize**

## Step 3: Get Box File IDs

You need the Box file IDs for files you want to migrate. Here are several ways to get them:

### Method 1: From Box Web Interface

1. Navigate to the file in Box web interface
2. Click on the file to view details
3. Look at the URL: `https://app.box.com/file/123456789`
4. The number `123456789` is the file ID

### Method 2: Using Box API Explorer

1. Go to [Box API Reference](https://developer.box.com/reference/)
2. Try the "Get folder items" endpoint
3. Authenticate with your Developer Token
4. Browse folders to find file IDs

### Method 3: Using Box CLI

```bash
# Install Box CLI
npm install -g @box/cli

# Login
box login

# List folder contents
box folders:items 0  # 0 = root folder
```

## Step 4: Prepare Configuration Files

### 4.1 Copy and Configure application.properties

1. Navigate to your project directory:
   ```bash
   cd /path/to/box-google-converter
   ```

2. Edit `src/main/resources/application.properties`:
   ```properties
   # Box Configuration
   box.client.id=your_box_client_id_here
   box.client.secret=your_box_client_secret_here
   box.developer.token=YOUR_DEVELOPER_TOKEN_HERE
   box.enterprise.id=your_enterprise_id_here
   
   # Google Drive Configuration
   google.credentials.file=/absolute/path/to/google-credentials.json
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

3. **Replace placeholders**:
   - `YOUR_DEVELOPER_TOKEN_HERE`: Your Box developer token (from Step 1)
   - `/absolute/path/to/google-credentials.json`: Full path to your service account JSON file (from Step 2.4)

### 4.2 Create Migration CSV File

Create `migration-input.csv` in your project root:

```csv
box_file_id,box_file_path,user_email
123456789,/Marketing/Q1/Report.docx,user1@yourcompany.com
987654321,/Sales/Budget.xlsx,user2@yourcompany.com
456789123,/HR/Presentation.pptx,user3@yourcompany.com
```

**Column descriptions**:
- `box_file_id`: The Box file ID (see Step 3)
- `box_file_path`: The target folder path in Google Drive (e.g., `/Marketing/Q1`)
- `user_email`: The target Google Workspace user email (must be in your domain)

## Step 5: Build the Application

```bash
# Navigate to project directory
cd /path/to/box-google-converter

# Build with Maven
mvn clean package

# You should see: BUILD SUCCESS
```

This creates: `target/box-google-converter-1.0-SNAPSHOT-jar-with-dependencies.jar`

## Step 6: Run the Migration

### Test Run (Dry Run)

First, test with 1-2 files:

```bash
java -jar target/box-google-converter-1.0-SNAPSHOT-jar-with-dependencies.jar
```

### Monitor Progress

Watch the console output:
```
2026-06-02 16:30:00 [main] INFO  com.migration.Main - Box to Google Drive Migration Tool - Starting...
2026-06-02 16:30:01 [main] INFO  com.migration.Main - Loading configuration...
2026-06-02 16:30:02 [main] INFO  com.migration.service.MigrationOrchestrator - Starting Box to Google Drive Migration
...
```

### Check Logs

Logs are written to `logs/migration-{date}.log`:

```bash
tail -f logs/migration-2026-06-02.log
```

## Step 7: Verify Results

### Option 1: Check Database

```bash
sqlite3 migration-results.db

# Summary
SELECT status, COUNT(*) as count FROM migration_records GROUP BY status;

# Successful migrations
SELECT box_file_id, box_file_name, google_drive_file_id, google_drive_web_view_link 
FROM migration_records 
WHERE status = 'COMPLETED';

# Failed migrations
SELECT box_file_id, box_file_name, error_message 
FROM migration_records 
WHERE status = 'FAILED';
```

### Option 2: Check Google Drive

1. Log in to Google Drive as the target user
2. Navigate to the folder path specified in your CSV
3. Verify files are present and in Google Workspace format:
   - **.docx** → Google Docs icon
   - **.xlsx** → Google Sheets icon
   - **.pptx** → Google Slides icon

## Step 8: Resume After Failures

If the migration fails or is interrupted:

1. **Don't modify the database** (`migration-results.db`)
2. **Fix any errors** (check logs for details)
3. **Re-run the same command**:
   ```bash
   java -jar target/box-google-converter-1.0-SNAPSHOT-jar-with-dependencies.jar
   ```

The tool automatically:
- Skips COMPLETED files
- Retries PENDING and FAILED files
- Maintains all previous state

## Troubleshooting

### Issue: "Box credentials not configured properly"

**Solution**:
- Verify `box.developer.token` is set in `application.properties`
- Generate a new developer token (they expire after 60 minutes)
- For production, switch to JWT authentication

### Issue: "Google credentials file not configured"

**Solution**:
- Check that `google.credentials.file` points to your downloaded JSON file
- Use **absolute path** (e.g., `/Users/yourname/google-credentials.json`)
- Verify the file exists: `ls -l /path/to/credentials.json`

### Issue: "File already exists at destination"

**Solution**:
- The tool is designed to **not overwrite** existing files
- Options:
  1. Remove the duplicate file from Google Drive
  2. Remove the entry from your CSV file
  3. Change the target path in the CSV

### Issue: "Failed to download file from Box"

**Solution**:
- Verify the Box file ID is correct
- Check the file still exists in Box
- Ensure your Box token/app has **read permissions**

### Issue: "Security error while uploading file"

**Solution**:
- Verify domain-wide delegation is configured (Step 2.6)
- Ensure the OAuth scopes are correct in Admin Console
- Check that user emails in CSV are valid in your domain
- Verify the service account Client ID matches in Admin Console

### Issue: "Java version error"

**Solution**:
```bash
# Check version
java -version

# Should be 21+, if not, install Java 21:
# macOS
brew install openjdk@21

# Ubuntu/Debian
sudo apt install openjdk-21-jdk

# Set JAVA_HOME
export JAVA_HOME=/path/to/java21
```

## Production Deployment

For production use:

1. **Switch to JWT authentication** instead of developer token
2. **Tune virtual thread concurrency** for your workload:
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
3. **Monitor API rate limits** (higher concurrency = more API requests)
4. **Set up log rotation** for production logging
5. **Backup the database** regularly during migration
6. **Monitor disk space** (SQLite database grows with records)

> **Performance Note**: Virtual threads enable 100-500 concurrent migrations with minimal resource usage. See [VIRTUAL_THREADS.md](VIRTUAL_THREADS.md) for tuning guidance.

## Advanced: Running as a Service

### Linux (systemd)

Create `/etc/systemd/system/box-migration.service`:

```ini
[Unit]
Description=Box to Google Drive Migration
After=network.target

[Service]
Type=simple
User=migration
WorkingDirectory=/opt/box-google-converter
ExecStart=/usr/bin/java -jar target/box-google-converter-1.0-SNAPSHOT-jar-with-dependencies.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

Enable and start:
```bash
sudo systemctl enable box-migration
sudo systemctl start box-migration
sudo systemctl status box-migration
```

## Support & Next Steps

### Useful Commands

```bash
# Check migration summary
sqlite3 migration-results.db "SELECT status, COUNT(*) FROM migration_records GROUP BY status;"

# Export all results to CSV
sqlite3 -header -csv migration-results.db "SELECT * FROM migration_records;" > migration-report.csv

# Find all failed migrations
sqlite3 -header -csv migration-results.db "SELECT box_file_id, box_file_name, error_message FROM migration_records WHERE status='FAILED';" > failures.csv
```

### Database Schema

Full schema documentation is in `README.md`.

### Logs

- Console: Real-time progress
- File: `logs/migration-{date}.log`
- Database: `migration-results.db`

## Security Best Practices

1. **Never commit credentials** to version control
2. **Store service account JSON** securely (use secrets manager in production)
3. **Rotate developer tokens** regularly
4. **Limit service account permissions** to only what's needed
5. **Audit migration logs** regularly
6. **Delete or secure** the database after migration completes

## Maintenance

### After Migration Completes

1. Review all FAILED records and resolve issues
2. Re-run migration to retry failures
3. Verify files in Google Drive
4. Archive or delete migration database
5. Revoke Box API access if no longer needed
6. Consider removing service account domain-wide delegation

---

**Need Help?**

Check the comprehensive `README.md` for architecture details and troubleshooting.
