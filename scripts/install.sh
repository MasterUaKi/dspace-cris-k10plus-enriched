#!/usr/bin/env bash
# Version: 1.1.0 | Release Date: 2026-11-17
# [DE] Automatisierter Installer für den K10plusEnriched External Data Provider in DSpace-CRIS 9
# [EN] Automated installer for K10plusEnriched External Data Provider in DSpace-CRIS 9

set -euo pipefail

DSPACE_SRC="${DSPACE_SRC:-/build/DSpace-CRIS-9}"
DSPACE_DIR="${DSPACE_DIR:-/dspace}"
SERVICE_NAME="${SERVICE_NAME:-dspace-backend.service}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="${HOME}/dspace_backups/k10plus_$(date +%Y%m%d_%H%M%S)"

echo "=== [1/6] Erstelle Backups / Creating backups in ${BACKUP_DIR} ==="
mkdir -p "${BACKUP_DIR}"
cp -f "${DSPACE_DIR}/webapps/server-boot.jar" "${BACKUP_DIR}/server-boot.jar.bak"
[ -f "${DSPACE_DIR}/config/modules/k10plus-enriched.cfg" ] && cp -f "${DSPACE_DIR}/config/modules/k10plus-enriched.cfg" "${BACKUP_DIR}/"
[ -f "${DSPACE_DIR}/config/spring/api/k10plus-external-services.xml" ] && cp -f "${DSPACE_DIR}/config/spring/api/k10plus-external-services.xml" "${BACKUP_DIR}/"

echo "=== [2/6] Kopiere Java-Quellen / Copying Java sources to ${DSPACE_SRC}/dspace/modules/additions ==="
TARGET_JAVA="${DSPACE_SRC}/dspace/modules/additions/src/main/java/org/dspace/external/provider"
mkdir -p "${TARGET_JAVA}"/{impl,parser,client,dto}
cp -f "${REPO_ROOT}/backend/additions/src/main/java/org/dspace/external/provider/impl/"*.java "${TARGET_JAVA}/impl/"
cp -f "${REPO_ROOT}/backend/additions/src/main/java/org/dspace/external/provider/parser/"*.java "${TARGET_JAVA}/parser/"
cp -f "${REPO_ROOT}/backend/additions/src/main/java/org/dspace/external/provider/client/"*.java "${TARGET_JAVA}/client/"
cp -f "${REPO_ROOT}/backend/additions/src/main/java/org/dspace/external/provider/dto/"*.java "${TARGET_JAVA}/dto/"

echo "=== [3/6] Baue additions-Modul via Maven / Building additions module via Maven ==="
mvn -f "${DSPACE_SRC}/dspace/modules/additions/pom.xml" clean install -DskipTests -Dcheckstyle.skip=true -Dlicense.skip=true

ADDITIONS_JAR=$(find "${DSPACE_SRC}/dspace/modules/additions/target" -maxdepth 1 -name "additions-cris-*.jar" ! -name "*-sources.jar" | head -n 1)
if [ -z "${ADDITIONS_JAR}" ]; then
    echo "ERROR: additions-cris-*.jar wurde nicht gefunden / could not be found"
    exit 1
fi
JAR_BASENAME="$(basename "${ADDITIONS_JAR}")"
echo "JAR: ${ADDITIONS_JAR}"

echo "=== [4/6] Installiere Spring XML & .cfg / Installing Spring XML & .cfg module ==="
cp -f "${REPO_ROOT}/config/spring/api/k10plus-external-services.xml" "${DSPACE_SRC}/dspace/config/spring/api/k10plus-external-services.xml"
sudo install -o root -g root -m 644 "${REPO_ROOT}/config/spring/api/k10plus-external-services.xml" "${DSPACE_DIR}/config/spring/api/k10plus-external-services.xml"

if [ ! -f "${DSPACE_DIR}/config/modules/k10plus-enriched.cfg" ]; then
    cp -f "${REPO_ROOT}/config/modules/k10plus-enriched.cfg" "${DSPACE_SRC}/dspace/config/modules/k10plus-enriched.cfg"
    sudo install -o root -g root -m 644 "${REPO_ROOT}/config/modules/k10plus-enriched.cfg" "${DSPACE_DIR}/config/modules/k10plus-enriched.cfg"
    echo "Neue k10plus-enriched.cfg installiert / Installed new k10plus-enriched.cfg"
else
    echo "Bestehende k10plus-enriched.cfg beibehalten / Preserved existing ${DSPACE_DIR}/config/modules/k10plus-enriched.cfg"
fi

# [DE] Entferne versehentliche Backup-XMLs aus spring/api / [EN] Move stray backup XMLs out of spring/api
find "${DSPACE_DIR}/config/spring" -type f \( -name "*old*.xml" -o -name "*bak*.xml" -o -name "*copy*.xml" \) -exec sudo mv -v {} "${BACKUP_DIR}/" \;

echo "=== [5/6] Aktualisiere server-boot.jar (ZIP_STORED) / Patching server-boot.jar (ZIP_STORED) ==="
sudo install -o root -g root -m 644 "${ADDITIONS_JAR}" "${DSPACE_DIR}/lib/${JAR_BASENAME}"

python3 - "${DSPACE_DIR}/webapps/server-boot.jar" "${ADDITIONS_JAR}" "BOOT-INF/lib/${JAR_BASENAME}" "/tmp/server-boot-patched.jar" << 'PYEOF'
import sys, zipfile

src_jar, add_jar, entry_name, out_jar = sys.argv[1:5]
with open(add_jar, "rb") as f:
    add_bytes = f.read()

replaced = False
with zipfile.ZipFile(src_jar, "r") as zin, zipfile.ZipFile(out_jar, "w") as zout:
    for item in zin.infolist():
        if item.filename == entry_name:
            zi = zipfile.ZipInfo(entry_name)
            zi.compress_type = zipfile.ZIP_STORED
            zout.writestr(zi, add_bytes)
            replaced = True
        else:
            zout.writestr(item, zin.read(item.filename))

if not replaced:
    raise RuntimeError(f"Entry {entry_name} not found inside {src_jar}")
print(f"Updated {entry_name} ({len(add_bytes)} bytes, ZIP_STORED)")
PYEOF

sudo install -o root -g root -m 644 /tmp/server-boot-patched.jar "${DSPACE_DIR}/webapps/server-boot.jar"
rm -f /tmp/server-boot-patched.jar

echo "=== [6/6] Starte ${SERVICE_NAME} neu / Restarting${SERVICE_NAME} ==="
sudo systemctl restart "${SERVICE_NAME}"
echo "Fertig / Done! Test-URL: http://localhost:8080/server/api/integration/externalsources/K10plusEnriched"
