#!/usr/bin/env bash
set -euo pipefail
mkdir -p "$(pwd)/lib"
cd "$(pwd)/lib"

PDFBOX_VER=2.0.29
PDFBOX_G2_VER=0.6.0
COMMONS_LOG_VER=1.2

echo "Downloading PDFBox $PDFBOX_VER, fontbox, commons-logging, pdfbox-graphics2d $PDFBOX_G2_VER into lib/"

curl -L -o pdfbox-${PDFBOX_VER}.jar \
  https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox/${PDFBOX_VER}/pdfbox-${PDFBOX_VER}.jar
curl -L -o fontbox-${PDFBOX_VER}.jar \
  https://repo1.maven.org/maven2/org/apache/pdfbox/fontbox/${PDFBOX_VER}/fontbox-${PDFBOX_VER}.jar
curl -L -o commons-logging-${COMMONS_LOG_VER}.jar \
  https://repo1.maven.org/maven2/commons-logging/commons-logging/${COMMONS_LOG_VER}/commons-logging-${COMMONS_LOG_VER}.jar

# pdfbox-graphics2d artifact coordinates
curl -L -o pdfbox-graphics2d-${PDFBOX_G2_VER}.jar \
  https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox-graphics2d/${PDFBOX_G2_VER}/pdfbox-graphics2d-${PDFBOX_G2_VER}.jar || true

echo "Downloads complete. Verify lib/ contains:
 - pdfbox-${PDFBOX_VER}.jar
 - fontbox-${PDFBOX_VER}.jar
 - commons-logging-${COMMONS_LOG_VER}.jar
 - pdfbox-graphics2d-${PDFBOX_G2_VER}.jar (optional)"
