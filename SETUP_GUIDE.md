# Box to Google Drive Migration Tool - Setup Guide

## Quick Start

This guide will walk you through setting up and running the Box to Google Drive migration tool.

### Common Setup Issues (Quick Reference)

**Most common setup problems and solutions:**

1. **"You need to be part of an organization"** → You have a personal Google account, not Google Workspace. See [Alternative for Personal Accounts](#alternative-for-personal-accounts-single-user)
2. **"Security error while uploading"** → Domain-wide delegation not authorized in Admin Console
3. **"Invalid Client ID"** → Used service account email instead of numeric Client ID
4. **"Subject not found"** → User email not in your Workspace domain or delegation not propagated
5. **"Application data vs User data"** → Always choose "Application data" for service account
6. **Can't find Admin Console** → Personal accounts don't have Admin Console - need Google Workspace

See the [Troubleshooting](#troubleshooting) section for detailed solutions.

## System Requirements

- **Java**: Java 21 or higher (required for virtual threads)
- **Maven**: Maven 3.6+ (for building from source)
- **Box.com**: Account with API access
- **Google Workspace**: Domain with admin access for service account setup
  - ⚠️ **Google Workspace (paid) REQUIRED** - Personal Google accounts (@gmail.com) cannot use domain-wide delegation
  - See [Alternative for Personal Accounts](#alternative-for-personal-accounts-single-user) if you have a personal account

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

> **Choose ONE authentication method for Box:**
> - **Option A (Developer Token)**: Quick testing, expires in 60 minutes
> - **Option B (JWT Config)**: Production use, auto-refreshing tokens ✅ RECOMMENDED

### Option A: Using Developer Token (Quick Testing)

**Use this for:** Quick testing, proof of concept

⚠️ **Limitations:**
- Token expires after 60 minutes
- Must regenerate manually
- Not suitable for production

**Steps:**

1. Log in to [Box Developer Console](https://app.box.com/developers/console)
2. Click **Create New App**
3. Select **Custom App**
4. Choose **Server Authentication (with JWT)**
5. Name your app (e.g., "Box-Google-Migration")
6. Click **Create App**
7. Click **View Your App**
8. Go to **Configuration** tab
9. Scroll down to **Developer Token** section
10. Click **Generate Developer Token**
11. **Copy the token** (valid for 60 minutes)
12. Set in `application.properties`:
    ```properties
    box.developer.token=YOUR_COPIED_TOKEN_HERE
    ```

### Option B: Using JWT Config File (Production) ✅ RECOMMENDED

**Use this for:** Production migrations, long-running processes

✅ **Benefits:**
- Tokens auto-refresh (no expiration)
- More secure than developer tokens
- Supports enterprise and app users
- Required for production use

**Steps:**

1. Log in to [Box Developer Console](https://app.box.com/developers/console)

2. If you haven't created an app yet:
   - Click **Create New App**
   - Select **Custom App**
   - Choose **Server Authentication (with JWT)**
   - Name your app (e.g., "Box-Google-Migration")
   - Click **Create App**

3. Go to your app's **Configuration** tab

4. Scroll to **Add and Manage Public Keys** section

5. Click **Generate a Public/Private Keypair**
   - Box will generate a keypair and download `box_config.json`
   - This file contains your private key - keep it secure!

6. Save the downloaded `box_config.json` file securely (e.g., in a `credentials/` folder)

7. Scroll to **Application Scopes** section and ensure these are checked:
   - ✅ **Read all files and folders stored in Box**
   - ✅ **Write all files and folders stored in Box** (optional, for future features)

8. Go to **Authorization** tab

9. Click **Review and Submit**
   - This requests admin approval for your app

10. Wait for Box admin approval (notification via email)

11. Once approved, set in `application.properties`:
    ```properties
    box.config.file=/absolute/path/to/box_config.json
    ```

**File structure example:**
```
box-google-converter/
├── credentials/
│   ├── box_config.json              ← Box JWT config
│   └── oauth-credentials.json       ← Google OAuth credentials
└── src/
    └── main/
        └── resources/
            └── application.properties
```

### Required Box Permissions

In the Box Developer Console, ensure your app has:
- ✅ **Read all files and folders** (required)
- ✅ **Write all files and folders** (optional, for future features)
- ✅ **Application Scopes** enabled (for JWT)
- ✅ **Authorized** by Box admin (for JWT)

## Step 2: Choose Your Authentication Method

> ⚠️ **IMPORTANT: Choose Based on Your Google Account Type!**
>
> This migration tool supports **two authentication modes**:
>
> **Option A: OAuth (Personal Google Account)** ✅ RECOMMENDED for individuals
> - For personal accounts (@gmail.com, @outlook.com, etc.)
> - Migrates files to **your own** Google Drive only
> - Browser-based authentication (one-time consent)
> - **No Google Workspace required**
> - **Go to: [Setup for Personal Accounts (OAuth)](#step-2a-setup-for-personal-accounts-oauth)**
>
> **Option B: Service Account (Google Workspace)** ✅ RECOMMENDED for organizations
> - For Google Workspace (business accounts with custom domains like @yourcompany.com)
> - Migrates files to **multiple users'** Google Drives
> - Requires Admin Console access and domain-wide delegation
> - **Google Workspace subscription required** (~$6-12/user/month)
> - **Go to: [Setup for Google Workspace (Service Account)](#step-2b-setup-for-google-workspace-service-account)**
>
> **How to tell which you have:**
> - Personal: Your email is @gmail.com, @outlook.com, etc. → Use **Option A (OAuth)**
> - Workspace: Your email is @yourcompany.com (custom domain) → Use **Option B (Service Account)**

### Quick Comparison

| Feature | OAuth (Personal) | Service Account (Workspace) |
|---------|------------------|----------------------------|
| **Account Type** | Personal (@gmail.com) | Google Workspace (custom domain) |
| **Setup Complexity** | Easy (5 steps) | More complex (9 steps) |
| **Browser Required** | First run only | Never |
| **Multiple Users** | ❌ No (only you) | ✅ Yes (any user in domain) |
| **Admin Console** | ❌ Not needed | ✅ Required |
| **Domain-Wide Delegation** | ❌ Not needed | ✅ Required |
| **CSV user_email** | Ignored | Used for impersonation |
| **Cost** | Free | ~$6-12/user/month |
| **Google Cloud Setup** | OAuth Client ID | Service Account + Key |
| **Best For** | Personal migrations | Organization-wide migrations |

---

## Step 2A: Setup for Personal Accounts (OAuth)

> ✅ **Use this section if you have a personal Google account (@gmail.com) and want to migrate files to your own Drive.**
>
> This setup uses OAuth 2.0 user consent. You'll grant permission via your browser on first run.

### 2A.1 Create Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Click on the project dropdown → **New Project**
3. Name: "Box-Google-Migration"
4. Click **Create**
5. Wait for project creation (1-2 minutes)

### 2A.2 Enable Google Drive API

1. In Google Cloud Console, select your new project
2. Go to **APIs & Services** → **Library**
3. Search for "Google Drive API"
4. Click **Google Drive API**
5. Click **Enable**

### 2A.3 Create OAuth 2.0 Client ID

1. Go to **APIs & Services** → **Credentials**

2. If this is your first time, you'll need to **Configure the OAuth consent screen**:
   - Click **Configure Consent Screen**
   - Select **External** (for personal accounts)
   - Click **Create**

3. Fill in OAuth consent screen (Basic Info):
   - **App name**: Box-Google-Converter
   - **User support email**: Your email address
   - **Developer contact information**: Your email address
   - Click **Save and Continue**

4. **Scopes** page: Click **Save and Continue** (no need to add scopes manually - the app requests them at runtime)

5. **Test users** page:
   - Click **+ ADD USERS**
   - Add your own Gmail address
   - Click **Save and Continue**

6. Review summary and click **Back to Dashboard**

7. Go back to **Credentials** tab → Click **+ CREATE CREDENTIALS** → **OAuth client ID**

8. Configure OAuth client:
   - **Application type**: **Desktop app**
   - **Name**: `Box-Google-Converter OAuth Client`
   - Click **Create**

9. **Download the JSON file**:
   - Click the **download icon** next to your newly created OAuth client
   - Save it as `oauth-credentials.json` in your project root folder

### 2A.4 Configure Application for OAuth Mode

Edit `src/main/resources/application.properties`:

```properties
# Box Configuration (same as before)
box.developer.token=YOUR_BOX_DEV_TOKEN

# Google Drive Configuration - OAuth Mode
google.auth.type=oauth
google.credentials.file=/absolute/path/to/oauth-credentials.json
google.application.name=Box-Google-Converter

# CSV Input
csv.input.path=./migration-input.csv

# Threading Configuration
thread.pool.size=100
```

**Key settings for OAuth:**
- `google.auth.type=oauth` ← This enables OAuth mode
- `google.credentials.file` should point to your `oauth-credentials.json` (the OAuth client credentials, NOT a service account key)

### 2A.5 Prepare CSV (User Email Ignored in OAuth Mode)

Create a simple CSV with just Box file IDs. The application automatically fetches file paths and names from Box:

```csv
box_file_id,user_email
123456789,your.email@gmail.com
987654321,your.email@gmail.com
```

*Note: The `user_email` column is required but ignored in OAuth mode - all files go to your authenticated Google Drive account.*

### 2A.6 First Run - OAuth Consent Flow

When you run the application for the first time:

1. Build and run:
   ```bash
   mvn clean package
   java -jar target/box-google-converter-1.0-SNAPSHOT-jar-with-dependencies.jar
   ```

2. **Browser opens automatically** with Google sign-in page

3. Sign in with your Google account

4. You may see "Google hasn't verified this app":
   - Click **Advanced**
   - Click **Go to Box-Google-Converter (unsafe)**
   - This is safe because it's YOUR app

5. Click **Allow** to grant Drive access

6. Browser shows: "The authentication flow has completed. You may close this window."

7. Application continues with migration

**Subsequent runs**: The token is stored in `tokens/` folder, so you won't need to authenticate again unless you revoke access or delete the tokens folder.

✅ **OAuth setup complete!** Skip to [Step 3: Get Box File IDs](#step-3-get-box-file-ids)

For detailed OAuth troubleshooting and additional information, see [OAUTH_SETUP.md](OAUTH_SETUP.md).

---

## Step 2B: Setup for Google Workspace (Service Account)

> ✅ **Use this section if you have Google Workspace and want to migrate files to multiple users' Google Drives.**
>
> This setup uses a service account with domain-wide delegation to impersonate users.

### 2B.1 Create Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Click on the project dropdown → **New Project**
3. Name: "Box-Google-Migration"
4. Click **Create**
5. Wait for project creation (1-2 minutes)

### 2B.2 Enable Google Drive API

1. In Google Cloud Console, select your new project
2. Go to **APIs & Services** → **Library**
3. Search for "Google Drive API"
4. Click **Google Drive API**
5. Click **Enable**

### 2B.3 Create Service Account

**Important**: This migration tool uses a **service account** (not OAuth user login) so it can upload files to multiple users' Google Drives based on the `user_email` in your CSV, without requiring interactive sign-in each time.

#### Option A: Using the Credentials Wizard (Recommended for First-Time Users)

1. Go to **APIs & Services** → **Credentials**
2. Click **+ CREATE CREDENTIALS** → Select **Help me choose**
3. On the "Create credentials" wizard:
   - **Which API are you using?** → Select **Google Drive API**
   - **What data will you be accessing?** → Select **Application data** ✅
     - ⚠️ **Do NOT select "User data"** - that creates OAuth, which requires browser sign-in
   - Click **Next**
4. Fill in service account details:
   - **Service account name**: `box-migration-service`
   - **Service account ID**: (auto-generated)
   - **Service account description**: "Service account for Box to Google Drive migration"
5. Click **Create and Continue**
6. Grant role: **None needed** (we'll use domain-wide delegation instead)
7. Click **Continue** → **Done**

#### Option B: Direct Service Account Creation (Faster if You Know What You're Doing)

1. Go to **APIs & Services** → **Credentials**
2. Click **+ CREATE CREDENTIALS** → **Service Account** (skip the wizard)
3. Service account details:
   - **Name**: `box-migration-service`
   - **Service account ID**: (auto-generated)
   - **Description**: "Service account for Box to Google Drive migration"
4. Click **Create and Continue**
5. Grant role: **None needed** (we'll use domain-wide delegation)
6. Click **Continue** → **Done**

> **Why Application Data / Service Account?**
> - Service accounts act on behalf of users without interactive login
> - Perfect for automated migrations across multiple user accounts
> - Works with domain-wide delegation to impersonate users
> - "User data" OAuth is for apps where users click "Allow" in browser - not needed here

### 2B.4 Create Service Account Key

1. Click on the service account you just created
2. Go to **Keys** tab
3. Click **Add Key** → **Create new key**
4. Select **JSON**
5. Click **Create**
6. **Save the downloaded JSON file** securely (e.g., `google-credentials.json`)
7. **Important**: Store this file safely - it contains private keys!

### 2B.5 Get Service Account Client ID

Before you can enable domain-wide delegation, you need to get the service account's Client ID.

**Official Google Documentation**: [Delegating domain-wide authority](https://developers.google.com/identity/protocols/oauth2/service-account#delegatingauthority)

1. Go to [**Service accounts** page](https://console.developers.google.com/iam-admin/serviceaccounts) in Google Cloud Console
   - Or navigate: **IAM & Admin** → **Service Accounts**
2. In the list, click on the **email address** of your service account
   - (e.g., `box-migration-service@your-project.iam.gserviceaccount.com`)
3. Look at the service account details page
4. Find and **copy the Client ID** (numeric value)
   - It looks like: `123456789012345678901`
   - This is **NOT** the email address
   - You'll need this for the next step

> **Important Notes**:
> - The Client ID is a **numeric string** (usually 21 digits)
> - Do NOT use the service account email address for delegation
> - Using the email will cause an `unauthorized_client` error
> - Keep this Client ID handy - you'll paste it into Admin Console next

> **Optional**: Some service accounts have a checkbox for "Enable Google Workspace Domain-wide Delegation" on this page. You can check it if you see it, but **the critical step is authorizing in Admin Console** (next section).

### 2B.6 Authorize Service Account in Google Workspace Admin Console

> ⚠️ **Google Workspace Required**: This step ONLY works with Google Workspace (custom domain). 
> Personal Google accounts (@gmail.com) cannot access Admin Console.
> See [Alternative for Personal Accounts](#alternative-for-personal-accounts-single-user) if you have a personal account.

**This is the critical step** that allows the service account to impersonate users and upload files to their Google Drives.

> **Requirement**: You must be a **Super Administrator** of your Google Workspace domain to complete this step.

#### Steps to Authorize Domain-Wide Delegation

1. Log in to [Google Admin Console](https://admin.google.com/) as a **Super Admin**

2. Navigate to: **Main menu** → **Security** → **Access and data control** → **API Controls**

3. In the **Domain wide delegation** pane, click **Manage Domain Wide Delegation**

4. Click **Add new**

5. Fill in the authorization form:

   **Client ID field**:
   - Paste the **numeric Client ID** from step 2.5
   - Example: `123456789012345678901`
   - ⚠️ **Do NOT use the service account email address**
   - Using the email will cause `unauthorized_client` error
   
   **OAuth scopes (comma-delimited) field**:
   - Enter both of these scopes, separated by a comma:
   ```
   https://www.googleapis.com/auth/drive,https://www.googleapis.com/auth/drive.file
   ```
   - You can also use spaces: `https://www.googleapis.com/auth/drive, https://www.googleapis.com/auth/drive.file`

6. Click **Authorize**

7. Verify the entry appears in the **Domain wide delegation** list with your Client ID

> **Important**: According to Google documentation:
> - "It usually takes a few minutes for impersonation access to be granted"
> - "In some cases, it might take up to **24 hours**"
> - Wait at least **5-10 minutes** before testing the migration

> **What These Scopes Mean**:
> - `https://www.googleapis.com/auth/drive` - Full Drive access (create folders, upload files)
> - `https://www.googleapis.com/auth/drive.file` - Access to files created/opened by this app
> - Both are required for this migration tool to work properly

### 2B.7 Configure Application for Service Account Mode

Edit `src/main/resources/application.properties`:

```properties
# Box Configuration
box.developer.token=YOUR_BOX_DEV_TOKEN

# Google Drive Configuration - Service Account Mode
google.auth.type=service_account
google.credentials.file=/absolute/path/to/service-account-key.json
google.application.name=Box-Google-Converter

# CSV Input
csv.input.path=./migration-input.csv

# Threading Configuration
thread.pool.size=100
```

**Key settings for Service Account:**
- `google.auth.type=service_account` ← This enables Service Account mode (default)
- `google.credentials.file` should point to your service account JSON key (from Step 2B.4)

### 2B.8 Prepare CSV (User Email Required in Service Account Mode)

Create a simple CSV with Box file IDs and target users. The application automatically fetches file paths and names from Box:

```csv
box_file_id,user_email
123456789,user1@yourcompany.com
987654321,user2@yourcompany.com
456789123,user3@yourcompany.com
```

*Note: User emails must be valid in your Google Workspace domain. The application will impersonate each user and upload files to their respective Google Drives.*

### 2B.9 Verify Domain-Wide Delegation Setup

Before proceeding, verify your setup:

1. ✅ Service account created in Google Cloud Console
2. ✅ JSON key downloaded
3. ✅ Domain-wide delegation enabled on the service account
4. ✅ Client ID authorized in Google Workspace Admin Console with both Drive scopes
5. ✅ You have the service account email and Client ID noted down
6. ✅ `google.auth.type=service_account` set in application.properties

**Test Checklist**:
- [ ] Can you see the service account in Google Cloud Console → IAM & Admin → Service Accounts?
- [ ] Does the service account have domain-wide delegation enabled (check Details tab)?
- [ ] Is the Client ID listed in Admin Console → Security → API Controls → Domain-wide Delegation?
- [ ] Are both Drive scopes listed next to the Client ID?

✅ **Service Account setup complete!** Continue to [Step 3: Get Box File IDs](#step-3-get-box-file-ids)

---

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

2. Copy the template to create your configuration file:
   ```bash
   cp src/main/resources/application.properties.template \
      src/main/resources/application.properties
   ```

3. Edit `src/main/resources/application.properties` based on your authentication mode:

**For OAuth (Personal Account):**
```properties
# Box Configuration - Choose ONE:
# Option 1: JWT Config (recommended for production)
box.config.file=/absolute/path/to/box_config.json

# Option 2: Developer Token (for quick testing, expires in 60 min)
# box.developer.token=YOUR_DEVELOPER_TOKEN_HERE

# Google Drive Configuration - OAuth Mode
google.auth.type=oauth
google.credentials.file=/absolute/path/to/oauth-credentials.json
google.application.name=Box-Google-Converter

# Database Configuration
db.path=./migration-results.db

# Threading Configuration (Virtual Threads)
thread.pool.size=100

# CSV Input
csv.input.path=./migration-input.csv

# Retry Configuration
retry.max.attempts=3
retry.delay.seconds=5
```

**For Service Account (Google Workspace):**
```properties
# Box Configuration - Choose ONE:
# Option 1: JWT Config (recommended for production)
box.config.file=/absolute/path/to/box_config.json

# Option 2: Developer Token (for quick testing, expires in 60 min)
# box.developer.token=YOUR_DEVELOPER_TOKEN_HERE

# Google Drive Configuration - Service Account Mode
google.auth.type=service_account
google.credentials.file=/absolute/path/to/service-account-key.json
google.application.name=Box-Google-Converter

# Database Configuration
db.path=./migration-results.db

# Threading Configuration (Virtual Threads)
thread.pool.size=100

# CSV Input
csv.input.path=./migration-input.csv

# Retry Configuration
retry.max.attempts=3
retry.delay.seconds=5
```

4. **Replace placeholders:**

   **Box Configuration (choose one):**
   - `box.config.file`: Path to Box JWT config JSON (from Step 1 Option B) ✅ RECOMMENDED
   - OR `box.developer.token`: Your Box developer token (from Step 1 Option A)
   
   **Google Configuration:**
   - `google.credentials.file`: 
     - OAuth: Path to `oauth-credentials.json` (from Step 2A.3)
     - Service Account: Path to service account JSON key (from Step 2B.4)

### 4.2 Create Migration CSV File

Create `migration-input.csv` in your project root.

**Simplified CSV format (RECOMMENDED):**

The application automatically retrieves file paths and names from the Box API, so you only need to specify the file IDs and target users:

**For OAuth (Personal Account):**
```csv
box_file_id,user_email
123456789,your.email@gmail.com
987654321,your.email@gmail.com
456789123,your.email@gmail.com
```
*Note: The `user_email` column is ignored in OAuth mode - all files go to your Drive.*

**For Service Account (Google Workspace):**
```csv
box_file_id,user_email
123456789,user1@yourcompany.com
987654321,user2@yourcompany.com
456789123,user3@yourcompany.com
```
*Note: The `user_email` must be valid users in your Google Workspace domain.*

**Legacy format (optional, with explicit paths):**

You can optionally include `box_file_path` if you want to override the automatic path detection:

```csv
box_file_id,box_file_path,user_email
123456789,/Marketing/Q1,user1@yourcompany.com
987654321,/Sales,user2@yourcompany.com
```

**Column descriptions**:
- `box_file_id`: **Required** - The Box file ID (see Step 3)
- `user_email`: **Required** - Target user email
  - OAuth mode: Ignored (all files → your Drive)
  - Service Account mode: User to impersonate (must be in your Workspace domain)
- `box_file_path`: **Optional** - Custom folder path in Google Drive
  - If omitted, the application fetches the original Box folder path automatically
  - If provided, files are placed in this folder instead of the Box path
  - Example: `/Marketing/Q1` or `/Custom/Folder/Path`

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

Watch the console output. The application will show which authentication mode it's using:

**OAuth Mode:**
```
2026-06-02 16:30:00 [main] INFO  - Starting Box to Google Drive Migration
2026-06-02 16:30:00 [main] INFO  - Authentication Mode: OAuth (Personal Google Account)
2026-06-02 16:30:00 [main] INFO  - All files will be uploaded to the authenticated user's Google Drive
2026-06-02 16:30:00 [main] INFO  - user_email column in CSV will be ignored
```

**Service Account Mode:**
```
2026-06-02 16:30:00 [main] INFO  - Starting Box to Google Drive Migration
2026-06-02 16:30:00 [main] INFO  - Authentication Mode: Service Account (Google Workspace)
2026-06-02 16:30:00 [main] INFO  - Files will be uploaded to users specified in CSV user_email column
2026-06-02 16:30:00 [main] INFO  - Domain-wide delegation must be configured in Admin Console
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

### Issue: "You need to be part of an organization" or "Security Command Centre" message

**Cause**: You're using a **personal Google account** (@gmail.com), not Google Workspace

**Solution**: Use **OAuth mode** instead of Service Account mode!

1. Go back to [Step 2A: Setup for Personal Accounts (OAuth)](#step-2a-setup-for-personal-accounts-oauth)
2. Create OAuth 2.0 Client ID (not Service Account)
3. Set `google.auth.type=oauth` in `application.properties`
4. Download `oauth-credentials.json` and configure the path
5. Run the application - browser will open for consent on first run

**Note**: OAuth mode uploads files to **your own** Google Drive only. If you need to migrate to multiple users, you need Google Workspace.

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

### Issue: "Invalid grant: Not a valid email" or "Subject not found"

**Cause**: The service account cannot impersonate the user email

**Solution**:
1. Verify the user email exists in your Google Workspace domain
2. Check that domain-wide delegation is properly authorized in Admin Console
3. Ensure you used the **numeric Client ID** (not email) when authorizing in Admin Console
4. **Wait for propagation**: Google states this can take:
   - Usually: A few minutes
   - Sometimes: **Up to 24 hours**
   - Recommendation: Wait at least 10-15 minutes, then retry
5. Verify both Drive scopes are listed in Admin Console:
   - `https://www.googleapis.com/auth/drive`
   - `https://www.googleapis.com/auth/drive.file`
6. Check the service account Client ID in Admin Console matches the one from IAM & Admin → Service Accounts

### Issue: "Credentials not found" or "Service account key invalid"

**Cause**: The JSON key file path is incorrect or the key is invalid

**Solution**:
1. Use **absolute path** in `google.credentials.file` setting:
   ```properties
   # Good
   google.credentials.file=/Users/yourname/box-migration/credentials.json
   
   # Bad (relative paths may not work)
   google.credentials.file=./credentials.json
   ```
2. Verify the JSON file is valid:
   ```bash
   cat /path/to/credentials.json | jq .
   # Should show valid JSON with type: "service_account"
   ```
3. Check file permissions:
   ```bash
   ls -l /path/to/credentials.json
   # Should be readable
   ```

### Issue: "Cannot find service account option in Google Cloud Console"

**Cause**: Wrong section or project not properly set up

**Solution**:
1. Make sure you're in the correct Google Cloud project
2. Go to **APIs & Services** → **Credentials** (not IAM & Admin)
3. Click **+ CREATE CREDENTIALS** at the top
4. Look for **Service Account** in the dropdown
5. If using wizard, select **Help me choose** and pick **Application data**

### Additional Resources

**Official Google Documentation**:
- [Service Account Domain-Wide Delegation](https://developers.google.com/identity/protocols/oauth2/service-account#delegatingauthority)
- [Google Drive API Scopes](https://developers.google.com/identity/protocols/oauth2/scopes#drive)
- [Service Account Credentials](https://cloud.google.com/iam/docs/service-account-creds)

**Key Points from Google**:
- Always use the **numeric Client ID** for domain-wide delegation (not the email)
- Propagation can take up to 24 hours (usually much faster)
- You must be a Super Administrator to authorize delegation
- The service account only accesses what the impersonated user can access

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
