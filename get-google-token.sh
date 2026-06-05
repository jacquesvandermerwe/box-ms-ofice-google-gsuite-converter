#!/bin/bash
# Print a Google OAuth access token using credentials from application.properties.
# First run opens a browser; later runs reuse the refresh token in tokens/.
#
# Usage:
#   ./get-google-token.sh
#   ./test-export-gdoc.sh doc FILE_ID "$(./get-google-token.sh)"

set -euo pipefail
cd "$(dirname "$0")"

mvn -q compile exec:java -Dexec.mainClass=com.migration.GoogleTokenCli
