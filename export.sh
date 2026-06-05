#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

# Help message
usage() {
    echo "Usage: $0 <sheet|doc> <Google_File_ID> <Google_Access_Token> <Box_Dev_Token> <Box_Folder_ID>"
    echo "  Example Sheet: $0 sheet 1sOmE_ShEeT_Id_HeRe ya29.a0Af... 1\!AbCd... 0"
    echo "  Example Doc:   $0 doc   1sOmE_DoC_Id_HeRe   ya29.a0Af... 1\!AbCd... 0"
    exit 1
}

# Validate argument count
if [ "$#" -ne 5 ]; then
    usage
fi

TYPE=$(echo "$1" | tr '[:upper:]' '[:lower:]')
FILE_ID=$2
GOOGLE_TOKEN=$3
BOX_TOKEN=$4
BOX_FOLDER_ID=$5

TEMP_FILE="google_raw_temp.json"
FILE_NAME=""

# Ensure temp file is cleaned up even if script fails
cleanup() {
    if [ -f "$TEMP_FILE" ]; then
        rm -f "$TEMP_FILE"
    fi
}
trap cleanup EXIT

# --- STEP 1: Fetch Raw JSON from Google ---
if [ "$TYPE" = "sheet" ]; then
    echo "--> Fetching raw Google Sheet..."
    FILE_NAME="GoogleSheet_${FILE_ID}.gsheet"
    
    HTTP_STATUS=$(curl -s -o "$TEMP_FILE" -w "%{http_code}" \
        -H "Authorization: Bearer $GOOGLE_TOKEN" \
        "https://sheets.googleapis.com/v4/spreadsheets/${FILE_ID}?includeGridData=true")

elif [ "$TYPE" = "doc" ]; then
    echo "--> Fetching raw Google Doc..."
    FILE_NAME="GoogleDoc_${FILE_ID}.gdoc"
    
    HTTP_STATUS=$(curl -s -o "$TEMP_FILE" -w "%{http_code}" \
        -H "Authorization: Bearer $GOOGLE_TOKEN" \
        "https://docs.googleapis.com/v1/documents/${FILE_ID}")
else
    echo "Error: Invalid type '$TYPE'. Must be 'sheet' or 'doc'."
    usage
fi

# Check if Google fetch succeeded
if [ "$HTTP_STATUS" -ne 200 ]; then
    echo "Error: Google API returned HTTP status $HTTP_STATUS"
    cat "$TEMP_FILE"
    exit 1
fi

echo "--> Successfully fetched raw JSON from Google."

# --- STEP 2: Upload to Box ---
echo "--> Uploading raw JSON to Box as exactly: '$FILE_NAME'..."

# Box API requires metadata sent as a JSON string under the 'attributes' parameter
BOX_METADATA="{\"name\":\"${FILE_NAME}\", \"parent\":{\"id\":\"${BOX_FOLDER_ID}\"}}"

BOX_RESPONSE=$(curl -s -w "\n%{http_code}" \
    -X POST "https://upload.box.com/api/2.0/files/content" \
    -H "Authorization: Bearer $BOX_TOKEN" \
    -F "attributes=${BOX_METADATA}" \
    -F "file=@${TEMP_FILE}")

# Extract status code (last line of output) and response body
BOX_HTTP_STATUS=$(echo "$BOX_RESPONSE" | tail -n1)
BOX_BODY=$(echo "$BOX_RESPONSE" | sed '$d')

if [ "$BOX_HTTP_STATUS" -eq 201 ]; then
    echo "--> SUCCESS!"
    echo "File successfully uploaded to Box."
    echo "Box details: $(echo "$BOX_BODY" | grep -o '"id":"[0-9]*"' | head -1)"
else
    echo "Error: Box API upload failed with HTTP status $BOX_HTTP_STATUS"
    echo "$BOX_BODY"
    exit 1
fi
