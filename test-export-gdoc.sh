#!/bin/bash
# Probe whether the Decoupled Docs Export API (exportGDoc) is enabled for your OAuth client.
# See: [External - Box] Decoupled Docs Export API.pdf
#
# Usage: ./test-export-gdoc.sh <doc|sheet|slide> <google_file_id> <access_token>
#
# access_token: OAuth bearer with https://www.googleapis.com/auth/drive.readonly (or drive / drive.file)
# file_id: ID of a native Google Doc, Sheet, or Slide you can read (prefer one you own)

set -euo pipefail

usage() {
    echo "Usage: $0 <doc|sheet|slide> <Google_File_ID> <Google_Access_Token>"
    echo ""
    echo "Examples:"
    echo "  $0 doc   1abc...DocId...xyz ya29.a0Af..."
    echo "  $0 sheet 1abc...SheetId... ya29.a0Af..."
    echo "  $0 slide 1abc...SlideId... ya29.a0Af..."
    exit 1
}

if [ "$#" -ne 3 ]; then
    usage
fi

TYPE=$(echo "$1" | tr '[:upper:]' '[:lower:]')
FILE_ID=$2
TOKEN=$3

case "$TYPE" in
    doc)
        MIME="application/vnd.google-apps.document.internal"
        ;;
    sheet)
        MIME="application/vnd.google-apps.spreadsheet.internal"
        ;;
    slide)
        MIME="application/vnd.google-apps.presentation.internal"
        ;;
    *)
        echo "Error: type must be doc, sheet, or slide (got '$TYPE')"
        usage
        ;;
esac

URL="https://www.googleapis.com/drive/v2beta/files/${FILE_ID}/exportGDoc?mimeType=${MIME}"

OUT=$(mktemp)
trap 'rm -f "$OUT"' EXIT

echo "==> Decoupled Docs Export API probe"
echo "    Endpoint: GET .../drive/v2beta/files/${FILE_ID}/exportGDoc"
echo "    mimeType: ${MIME}"
echo ""

HTTP_STATUS=$(curl -sS -o "$OUT" -w "%{http_code}" \
    -H "Authorization: Bearer ${TOKEN}" \
    "$URL")

BYTES=$(wc -c < "$OUT" | tr -d ' ')

echo "HTTP status: ${HTTP_STATUS}"
echo "Response size: ${BYTES} bytes"
echo ""

case "$HTTP_STATUS" in
    200)
        echo "RESULT: API appears AVAILABLE — export succeeded."
        echo "        (Saved response body to temp file during probe; not written to disk.)"
        head -c 200 "$OUT" | xxd 2>/dev/null | head -3 || head -c 120 "$OUT"
        echo ""
        ;;
    403)
        echo "RESULT: Request reached Google but was denied (403)."
        echo "        Common causes: OAuth client not whitelisted for exportGDoc,"
        echo "        missing Drive scope, file download disabled, or not file owner."
        cat "$OUT"
        echo ""
        ;;
    404)
        echo "RESULT: Not found (404)."
        echo "        Either the file ID is wrong, or exportGDoc is not exposed to your client."
        cat "$OUT"
        echo ""
        ;;
    400)
        echo "RESULT: Bad request (400) — endpoint likely exists; check mimeType/file type/size."
        cat "$OUT"
        echo ""
        ;;
    401)
        echo "RESULT: Unauthorized (401) — token missing, expired, or wrong scope."
        cat "$OUT"
        echo ""
        ;;
    429|503|500)
        echo "RESULT: Transient/server error (${HTTP_STATUS}) — API may be available; retry later."
        cat "$OUT"
        echo ""
        ;;
    *)
        echo "RESULT: Unexpected status ${HTTP_STATUS}."
        cat "$OUT"
        echo ""
        ;;
esac

if [ "$HTTP_STATUS" -eq 200 ]; then
    exit 0
fi
exit 1
