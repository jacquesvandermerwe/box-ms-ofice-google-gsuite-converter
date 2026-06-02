# Google Cloud Console Setup - OAuth vs Service Account

This guide provides a side-by-side comparison of the Google Cloud Console setup steps for both authentication methods.

## Quick Decision Guide

**Use OAuth if:**
- ✅ You have a personal Google account (@gmail.com)
- ✅ Migrating files to YOUR OWN Drive only
- ✅ You want simple setup with no Admin Console
- ✅ You don't have Google Workspace

**Use Service Account if:**
- ✅ You have Google Workspace (custom domain)
- ✅ Migrating files to MULTIPLE users' Drives
- ✅ You have Admin Console access (Super Admin)
- ✅ You want automated, headless operation

---

## Setup Steps Comparison

### Common Steps (Both Methods)

| Step | Action | Details |
|------|--------|---------|
| 1 | Create Google Cloud Project | [console.cloud.google.com](https://console.cloud.google.com/) → New Project → "Box-Google-Migration" |
| 2 | Enable Google Drive API | APIs & Services → Library → Search "Google Drive API" → Enable |

---

### OAuth Setup (Personal Account)

#### Step 1: Configure OAuth Consent Screen

**Location:** APIs & Services → Credentials → Configure Consent Screen

| Field | Value |
|-------|-------|
| User Type | **External** |
| App name | Box-Google-Converter |
| User support email | Your email |
| Developer contact | Your email |
| Scopes | (Skip - app requests at runtime) |
| Test users | Add your Gmail address |

**Important Notes:**
- Must add yourself as a test user
- App stays in "Testing" mode (no verification needed for personal use)
- OAuth consent screen is required before creating OAuth client

#### Step 2: Create OAuth 2.0 Client ID

**Location:** APIs & Services → Credentials → + CREATE CREDENTIALS → OAuth client ID

| Field | Value |
|-------|-------|
| Application type | **Desktop app** ⚠️ (NOT Web application) |
| Name | Box-Google-Converter OAuth Client |
| Authorized redirect URIs | (Auto-configured for desktop app) |

**Download:** Click download icon → Save as `oauth-credentials.json`

**What you get:**
```json
{
  "installed": {
    "client_id": "123456789-abcdef.apps.googleusercontent.com",
    "client_secret": "GOCSPX-...",
    "auth_uri": "https://accounts.google.com/o/oauth2/auth",
    "token_uri": "https://oauth2.googleapis.com/token",
    "redirect_uris": ["http://localhost"]
  }
}
```

#### Step 3: Configure Application

**application.properties:**
```properties
google.auth.type=oauth
google.credentials.file=/path/to/oauth-credentials.json
```

#### Step 4: First Run (One-Time Browser Auth)

```bash
java -jar target/box-google-converter-1.0-SNAPSHOT-jar-with-dependencies.jar
```

1. Browser opens automatically
2. Sign in with your Google account
3. May see "Google hasn't verified this app" → Click Advanced → Go to Box-Google-Converter (safe - it's your app)
4. Click **Allow**
5. Browser shows "The authentication flow has completed"
6. Token stored in `tokens/` folder

**Subsequent runs:** No browser needed - uses stored refresh token

---

### Service Account Setup (Google Workspace)

#### Step 1: Create Service Account

**Location:** APIs & Services → Credentials → + CREATE CREDENTIALS → Service Account

**OR use the wizard:** Help me choose → Google Drive API → Application data

| Field | Value |
|-------|-------|
| Service account name | box-migration-service |
| Service account ID | (auto-generated) |
| Description | Service account for Box to Google Drive migration |
| Grant role | None (using domain-wide delegation) |

**Important:** Choose "Application data" NOT "User data" in wizard

#### Step 2: Create Service Account Key

**Location:** IAM & Admin → Service Accounts → Click on service account → Keys tab

1. Add Key → Create new key
2. Key type: **JSON**
3. Create
4. Save as `service-account-key.json`

**What you get:**
```json
{
  "type": "service_account",
  "project_id": "box-google-migration",
  "private_key_id": "...",
  "private_key": "-----BEGIN PRIVATE KEY-----\n...",
  "client_email": "box-migration-service@project.iam.gserviceaccount.com",
  "client_id": "123456789012345678901",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://oauth2.googleapis.com/token"
}
```

#### Step 3: Get Service Account Client ID

**Location:** IAM & Admin → Service Accounts → Click on service account email

Find the **Client ID** (numeric, 21 digits):
- Example: `123456789012345678901`
- ⚠️ This is NOT the email address
- ⚠️ This is NOT the private_key_id
- You'll need this for Admin Console authorization

#### Step 4: Authorize in Google Workspace Admin Console

**Requires:** Super Administrator access

**Location:** [admin.google.com](https://admin.google.com/) → Security → Access and data control → API Controls → Domain wide delegation

Click **Manage Domain Wide Delegation** → **Add new**

| Field | Value |
|-------|-------|
| Client ID | `123456789012345678901` (from Step 3) |
| OAuth scopes | `https://www.googleapis.com/auth/drive,https://www.googleapis.com/auth/drive.file` |

Click **Authorize**

**Propagation time:** Usually a few minutes, can take up to 24 hours

#### Step 5: Configure Application

**application.properties:**
```properties
google.auth.type=service_account
google.credentials.file=/path/to/service-account-key.json
```

#### Step 6: Run (No Browser Needed)

```bash
java -jar target/box-google-converter-1.0-SNAPSHOT-jar-with-dependencies.jar
```

No browser interaction - service account impersonates users automatically based on CSV `user_email` column.

---

## Credential Files Comparison

### OAuth Credentials (`oauth-credentials.json`)

```json
{
  "installed": {
    "client_id": "123...apps.googleusercontent.com",
    "client_secret": "GOCSPX-...",
    "auth_uri": "https://accounts.google.com/o/oauth2/auth",
    "token_uri": "https://oauth2.googleapis.com/token",
    "redirect_uris": ["http://localhost"]
  }
}
```

**Usage:**
- Identifies your app to Google
- Used during browser consent flow
- Not sensitive (can be embedded in public apps)
- Client secret is moderately sensitive

**Tokens directory (`tokens/`):**
```
tokens/
└── StoredCredential  # Encrypted refresh token (highly sensitive)
```

### Service Account Key (`service-account-key.json`)

```json
{
  "type": "service_account",
  "project_id": "box-google-migration",
  "private_key_id": "...",
  "private_key": "-----BEGIN PRIVATE KEY-----\n...",
  "client_email": "box-migration-service@....iam.gserviceaccount.com",
  "client_id": "123456789012345678901",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://oauth2.googleapis.com/token"
}
```

**Usage:**
- Contains private key for service account
- Used to generate access tokens
- **Highly sensitive** - treat like a password
- Allows impersonation of domain users (if delegated)

---

## Application Behavior Comparison

### Console Output

**OAuth Mode:**
```
========================================
Starting Box to Google Drive Migration
========================================
Authentication Mode: OAuth (Personal Google Account)
All files will be uploaded to the authenticated user's Google Drive
user_email column in CSV will be ignored
========================================
```

**Service Account Mode:**
```
========================================
Starting Box to Google Drive Migration
========================================
Authentication Mode: Service Account (Google Workspace)
Files will be uploaded to users specified in CSV user_email column
Domain-wide delegation must be configured in Admin Console
========================================
```

### CSV Handling

**OAuth Mode:**
```csv
box_file_id,box_file_path,user_email
123456789,/Marketing/Report.docx,ignored@example.com  ← Ignored
987654321,/Sales/Budget.xlsx,anything  ← Ignored
```
All files → Authenticated user's Drive

**Service Account Mode:**
```csv
box_file_id,box_file_path,user_email
123456789,/Marketing/Report.docx,user1@company.com  ← Impersonates user1
987654321,/Sales/Budget.xlsx,user2@company.com  ← Impersonates user2
```
Files → Respective users' Drives

---

## Switching Between Modes

You can switch authentication modes by changing one line in `application.properties`:

**Switch to OAuth:**
```properties
google.auth.type=oauth
google.credentials.file=/path/to/oauth-credentials.json
```

**Switch to Service Account:**
```properties
google.auth.type=service_account
google.credentials.file=/path/to/service-account-key.json
```

No code changes required - the application detects the mode automatically.

---

## Security Considerations

### OAuth Security

**What's stored locally:**
- `oauth-credentials.json` - Client ID and secret (moderately sensitive)
- `tokens/StoredCredential` - Refresh token (highly sensitive)

**Best practices:**
1. Add both to `.gitignore` (already done)
2. Revoke access after migration: [myaccount.google.com/permissions](https://myaccount.google.com/permissions)
3. Delete `tokens/` folder when done
4. OAuth credentials can be regenerated in Cloud Console

**Risk if compromised:**
- OAuth credentials alone: Attacker needs user to click "Allow"
- Refresh token: Full access to your Drive until revoked

### Service Account Security

**What's stored locally:**
- `service-account-key.json` - Private key (highly sensitive)

**Best practices:**
1. Add to `.gitignore` (already done)
2. Store in secrets manager in production (AWS Secrets Manager, GCP Secret Manager)
3. Use least-privilege scopes (only Drive access)
4. Rotate keys regularly (create new, delete old)
5. Monitor usage in Cloud Console audit logs
6. Remove domain-wide delegation when migration complete

**Risk if compromised:**
- Full access to all domain users' Drives (within authorized scopes)
- Can impersonate any user in the domain
- No user consent required

**Mitigation:**
- Service account only has access granted via domain-wide delegation
- Revoke delegation in Admin Console to immediately disable access
- Monitor service account activity in Admin Console audit logs

---

## Troubleshooting

### OAuth Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| "This app isn't verified" | App not verified by Google | Click Advanced → Go to Box-Google-Converter (safe for personal apps) |
| "redirect_uri_mismatch" | Wrong application type | Use **Desktop app** not Web application |
| "Access blocked" | OAuth consent screen not configured | Add yourself as test user in consent screen |
| Browser doesn't open | No default browser | Check console for URL, open manually |
| Token expired | Refresh token revoked | Delete `tokens/` folder, re-authenticate |

### Service Account Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| "Subject not found" | Domain-wide delegation not authorized | Check Admin Console → API Controls |
| "Invalid grant" | Client ID mismatch | Use numeric Client ID (not email) in Admin Console |
| "unauthorized_client" | Wrong Client ID | Verify Client ID matches in Admin Console |
| "Security error" | Delegation not propagated | Wait 5-10 minutes (can take up to 24 hours) |
| "You need to be part of organization" | Using personal account | Switch to OAuth mode |

---

## Next Steps

After completing Google Cloud setup:

1. ✅ Complete [Box Setup](SETUP_GUIDE.md#step-1-boxcom-setup)
2. ✅ [Prepare CSV file](SETUP_GUIDE.md#step-3-get-box-file-ids)
3. ✅ [Configure application.properties](SETUP_GUIDE.md#step-4-prepare-configuration-files)
4. ✅ [Build and run](SETUP_GUIDE.md#step-5-build-the-application)
5. ✅ [Verify results](SETUP_GUIDE.md#step-7-verify-results)

---

## Additional Resources

**OAuth Documentation:**
- [Google OAuth 2.0 for Desktop Apps](https://developers.google.com/identity/protocols/oauth2/native-app)
- [OAuth 2.0 Scopes for Google APIs](https://developers.google.com/identity/protocols/oauth2/scopes#drive)

**Service Account Documentation:**
- [Service Account Domain-Wide Delegation](https://developers.google.com/identity/protocols/oauth2/service-account#delegatingauthority)
- [Service Account Credentials](https://cloud.google.com/iam/docs/service-account-creds)

**This Project:**
- [OAUTH_SETUP.md](OAUTH_SETUP.md) - Detailed OAuth setup guide
- [SETUP_GUIDE.md](SETUP_GUIDE.md) - Complete setup guide for both modes
- [VIRTUAL_THREADS.md](VIRTUAL_THREADS.md) - Performance tuning guide
