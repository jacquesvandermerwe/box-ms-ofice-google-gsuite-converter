# OAuth Setup for Personal Google Accounts

This guide explains how to use the migration tool with a **personal Google account** (@gmail.com) using OAuth authentication.

## When to Use OAuth Mode

✅ **Use OAuth if:**
- You have a personal Google account (@gmail.com, @outlook.com, etc.)
- You're migrating files to **your own** Google Drive only
- You don't have Google Workspace
- You see "You need to be part of an organization" errors

❌ **Don't use OAuth if:**
- You need to migrate files to multiple users' Google Drives
- You have Google Workspace with custom domain
- You want to use domain-wide delegation

→ For multiple users, use Service Account mode (requires Google Workspace)

## How OAuth Mode Works

1. **First Run**: Browser opens automatically for you to grant permission
2. **You Authenticate**: Sign in with your Google account and click "Allow"
3. **Token Stored**: App receives and stores a refresh token locally
4. **Subsequent Runs**: App uses stored token, no browser needed
5. **All Files to Your Drive**: Regardless of `user_email` in CSV, everything goes to your Drive

## Step-by-Step Setup

### Step 1: Create OAuth Client ID in Google Cloud Console

1. Go to [Google Cloud Console](https://console.cloud.google.com/)

2. Select your project (or create one if you haven't)

3. Go to **APIs & Services** → **Credentials**

4. Click **+ CREATE CREDENTIALS** → **OAuth client ID**

5. If prompted, configure the OAuth consent screen:
   - Click **Configure Consent Screen**
   - Select **External** (for personal accounts)
   - Click **Create**
   
6. Fill in OAuth consent screen:
   - **App name**: Box-Google-Converter
   - **User support email**: Your email
   - **Developer contact information**: Your email
   - Click **Save and Continue**
   
7. Skip "Scopes" (click **Save and Continue**)

8. Add test users (click **+ ADD USERS**):
   - Add your own Gmail address
   - Click **Save and Continue**
   
9. Review and click **Back to Dashboard**

10. Go back to **Credentials** → **+ CREATE CREDENTIALS** → **OAuth client ID**

11. Application type: **Desktop app**

12. Name: `Box-Google-Converter OAuth Client`

13. Click **Create**

14. **Download the JSON** file (click the download icon)
    - Save it as `oauth-credentials.json` in your project folder

### Step 2: Configure Application

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

# Other settings...
thread.pool.size=100
```

**Key changes:**
- `google.auth.type=oauth` (instead of `service_account`)
- `google.credentials.file` points to your **OAuth client credentials** JSON (not service account)

### Step 3: Prepare CSV

Your CSV can include `user_email`, but it will be **ignored** in OAuth mode:

```csv
box_file_id,box_file_path,user_email
123456789,/Marketing/Report.docx,your.email@gmail.com
987654321,/Sales/Budget.xlsx,your.email@gmail.com
```

Or you can put any value (or leave blank):

```csv
box_file_id,box_file_path,user_email
123456789,/Marketing/Report.docx,ignored
987654321,/Sales/Budget.xlsx,ignored
```

All files will go to the authenticated user's Drive (you) regardless.

### Step 4: First Run - OAuth Consent

1. Build the application:
```bash
mvn clean package
```

2. Run the application:
```bash
java -jar target/box-google-converter-1.0-SNAPSHOT-jar-with-dependencies.jar
```

3. **Browser Opens Automatically**:
   - You'll see: "Box-Google-Converter wants to access your Google Account"
   - Click **your Google account**
   - You may see "Google hasn't verified this app" - click **Advanced** → **Go to Box-Google-Converter (unsafe)**
   - Click **Allow** to grant Drive access
   
4. Browser shows: "The authentication flow has completed. You may close this window."

5. Application continues with migration

### Step 5: Subsequent Runs

- Token is saved in `tokens/` folder
- No browser needed on subsequent runs
- App automatically uses stored refresh token
- Works until you revoke access

## File Structure

```
box-google-converter/
├── oauth-credentials.json          # OAuth client credentials (from Step 1)
├── tokens/                         # Created automatically
│   └── StoredCredential           # Refresh token (auto-saved)
├── migration-input.csv
└── ...
```

**Security Note**: Add `oauth-credentials.json` and `tokens/` to `.gitignore` (already done)

## OAuth vs Service Account Comparison

| Feature | OAuth (Personal) | Service Account (Workspace) |
|---------|------------------|----------------------------|
| Account Type | Personal (@gmail.com) | Google Workspace |
| Setup Complexity | Easier | More complex |
| Browser Required | First run only | Never |
| Multiple Users | ❌ No (only you) | ✅ Yes |
| Domain-Wide Delegation | ❌ Not possible | ✅ Required |
| Admin Console | ❌ Not needed | ✅ Required |
| user_email in CSV | Ignored | Used for impersonation |
| Cost | Free | ~$6-12/user/month |

## Troubleshooting OAuth Setup

### Issue: "This app isn't verified"

**Cause**: Your app isn't verified by Google (normal for personal projects)

**Solution**:
1. Click **Advanced**
2. Click **Go to Box-Google-Converter (unsafe)**
3. This is safe because it's YOUR app
4. For production, you can submit for verification (optional)

### Issue: "Access blocked: This app's request is invalid"

**Cause**: OAuth consent screen not configured or missing scopes

**Solution**:
1. Go back to Google Cloud Console
2. **APIs & Services** → **OAuth consent screen**
3. Make sure your email is added as a test user
4. Status should be "Testing" (not "In production")

### Issue: "Error: redirect_uri_mismatch"

**Cause**: OAuth client not configured for desktop app

**Solution**:
1. When creating OAuth client ID, select **Desktop app** (not Web application)
2. If you already created it wrong, delete and recreate with correct type

### Issue: "Browser doesn't open automatically"

**Cause**: No default browser configured or headless environment

**Solution**:
1. Check console output for the authorization URL
2. Manually copy URL and paste in browser
3. Complete the authorization flow
4. App will detect completion

### Issue: "Token expired" or "Invalid grant"

**Cause**: Refresh token expired or revoked

**Solution**:
1. Delete `tokens/` folder
2. Run application again
3. Complete OAuth flow again to get new token

### Issue: "Drive API not enabled"

**Cause**: Google Drive API not enabled for your project

**Solution**:
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. **APIs & Services** → **Library**
3. Search "Google Drive API"
4. Click **Enable**

## Switching Between OAuth and Service Account

You can switch modes by changing one line in `application.properties`:

**For OAuth (Personal Account):**
```properties
google.auth.type=oauth
google.credentials.file=/path/to/oauth-credentials.json
```

**For Service Account (Google Workspace):**
```properties
google.auth.type=service_account
google.credentials.file=/path/to/service-account-key.json
```

The application automatically detects and uses the correct authentication method.

## Security Best Practices

1. **Keep credentials private**:
   - Never commit `oauth-credentials.json` to git
   - Never commit `tokens/` folder to git
   - Already in `.gitignore`

2. **Revoke access when done**:
   - Go to [Google Account Permissions](https://myaccount.google.com/permissions)
   - Find "Box-Google-Converter"
   - Click **Remove Access** when migration is complete

3. **Token storage**:
   - Tokens stored in `tokens/` folder
   - Encrypted by Google OAuth library
   - Delete folder to force re-authentication

4. **OAuth Client credentials**:
   - Treat like a password
   - Don't share the JSON file
   - Regenerate if compromised (Cloud Console → Credentials)

## Testing OAuth Setup

Quick test to verify OAuth is working:

```bash
# 1. Set oauth mode in application.properties
google.auth.type=oauth

# 2. Create a test CSV with 1 file
echo "box_file_id,box_file_path,user_email" > test.csv
echo "YOUR_BOX_FILE_ID,/Test,ignored" >> test.csv

# 3. Update csv path
csv.input.path=./test.csv

# 4. Run
java -jar target/box-google-converter-1.0-SNAPSHOT-jar-with-dependencies.jar

# Expected output:
# "Authentication Mode: OAuth (Personal Google Account)"
# Browser opens for consent
# File uploads to your Drive
```

## FAQ

**Q: Can I use OAuth with Google Workspace?**  
A: Yes, but you'd only upload to your own Drive (not impersonate other users). Service Account with delegation is better for Workspace.

**Q: How long does the OAuth token last?**  
A: Refresh tokens typically last indefinitely unless revoked. Access tokens expire hourly but are auto-refreshed.

**Q: Can multiple people use the same OAuth credentials?**  
A: No. Each person needs their own OAuth flow. The credentials JSON can be shared, but each user authenticates separately.

**Q: What if I want files in different folder per user?**  
A: In OAuth mode, all files go to your Drive. Use folder paths in CSV to organize them.

**Q: Do I need to re-authenticate every time?**  
A: No. Only the first time. Subsequent runs use the stored refresh token.

## Next Steps

After OAuth setup:
1. ✅ Follow regular Box setup (SETUP_GUIDE.md Step 1)
2. ✅ Create your CSV with Box file IDs
3. ✅ Run the migration
4. ✅ Check your Google Drive for migrated files
5. ✅ Revoke access when done (optional)

For Google Workspace setup with Service Account, see main SETUP_GUIDE.md.
