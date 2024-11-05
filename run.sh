#!/usr/bin/env bash
set -eo pipefail

mkdir -p $MNT_DIR

echo "Mounting Cloud Filestore."
mount -o nolock $FILESTORE_IP_ADDRESS:/$FILE_SHARE_NAME $MNT_DIR
echo "Mounting completed."

java -jar ComposerBasedEscIndexer.jar $BASE_PATH $INDEX_NAME

wait -n