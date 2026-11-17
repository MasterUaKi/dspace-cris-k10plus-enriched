<!-- Version: 1.2.0 | Release Date: 2026-11-17 -->
# Full Build and Deployment via Maven & Ant (Standard Method)

This guide describes how to perform a complete recompilation of the entire DSpace-CRIS 9 application—producing a freshly built `server-boot.jar`—using **Apache Maven** and deploying it via **Apache Ant (`ant update`)**.

---

## When to Use This Method
* For initial deployments or scheduled DSpace-CRIS 9 system upgrades.
* When you are already building other customizations in your DSpace source tree (`/build/DSpace-CRIS-9`).
* When you prefer a full Maven-packaged `server-boot.jar` without in-place archive patching.

---

## Prerequisites
* DSpace-CRIS `9.x` (`2025.02.00` or later) source tree (e.g., `/build/DSpace-CRIS-9`).
* Deployed runtime directory at `/dspace`.
* Java 17 or Java 21, Apache Maven 3.8+, Apache Ant 1.10+.

---

## Step-by-Step Instructions

### 1. Create a Backup
Back up your running `/dspace` directory (especially `webapps/` and `config/`) before building:

    BACKUP_DIR="${HOME}/dspace_backups/full_build_$(date +%Y%m%d_%H%M%S)"
    mkdir -p "${BACKUP_DIR}"
    sudo cp -a /dspace/webapps "${BACKUP_DIR}/webapps"
    sudo cp -a /dspace/config "${BACKUP_DIR}/config"

### 2. Copy Source Code and Configurations into the DSpace Source Tree
Copy the Java classes along with the Spring XML and `.cfg` files from this repository into your DSpace-CRIS 9 source tree (`/build/DSpace-CRIS-9`):

    DSPACE_SRC="/build/DSpace-CRIS-9"
    REPO_ROOT="$(pwd)"

    TARGET_JAVA="${DSPACE_SRC}/dspace/modules/additions/src/main/java/org/dspace/external/provider"
    mkdir -p "${TARGET_JAVA}"/{impl,parser,client,dto}

    cp -f "${REPO_ROOT}/backend/additions/src/main/java/org/dspace/external/provider/impl/"*.java "${TARGET_JAVA}/impl/"
    cp -f "${REPO_ROOT}/backend/additions/src/main/java/org/dspace/external/provider/parser/"*.java "${TARGET_JAVA}/parser/"
    cp -f "${REPO_ROOT}/backend/additions/src/main/java/org/dspace/external/provider/client/"*.java "${TARGET_JAVA}/client/"
    cp -f "${REPO_ROOT}/backend/additions/src/main/java/org/dspace/external/provider/dto/"*.java "${TARGET_JAVA}/dto/"

    cp -f "${REPO_ROOT}/config/spring/api/k10plus-external-services.xml" "${DSPACE_SRC}/dspace/config/spring/api/k10plus-external-services.xml"
    cp -f "${REPO_ROOT}/config/modules/k10plus-enriched.cfg" "${DSPACE_SRC}/dspace/config/modules/k10plus-enriched.cfg"

> **Important Note on Spring Boot Packaging (`server-boot.jar`):**  
> During a full `mvn package` build, Spring Boot embeds `${DSPACE_SRC}/dspace/config/spring/` inside `BOOT-INF/classes/spring/` within `server-boot.jar`. Ensure that neither `${DSPACE_SRC}/dspace/config/spring/api/` nor `/dspace/config/spring/api/` contains renamed backup XML files (such as `external-services.old.xml`) and that both directories are kept in sync to prevent `NoUniqueBeanDefinitionException` at startup.

### 3. Run the Full Maven Build
Compile the entire DSpace-CRIS project and generate the installer package including the new `server-boot.jar`:

    cd "${DSPACE_SRC}"
    mvn -U clean package -DskipTests -Dcheckstyle.skip=true -Dlicense.skip=true

*(The resulting `server-boot.jar` is generated at `${DSPACE_SRC}/dspace/target/dspace-installer/webapps/server-boot.jar`.)*

### 4. Deploy with Apache Ant (`ant update`)
Navigate to the generated `dspace-installer` directory and update your runtime installation at `/dspace`:

    cd "${DSPACE_SRC}/dspace/target/dspace-installer"
    sudo ant update

If `ant update` creates `.new` configuration files, move them into place:

    [ -f /dspace/config/modules/k10plus-enriched.cfg.new ] && sudo mv /dspace/config/modules/k10plus-enriched.cfg.new /dspace/config/modules/k10plus-enriched.cfg
    [ -f /dspace/config/spring/api/k10plus-external-services.xml.new ] && sudo mv /dspace/config/spring/api/k10plus-external-services.xml.new /dspace/config/spring/api/k10plus-external-services.xml

### 5. Restart the Backend Service and Verify the REST API

    sudo systemctl restart dspace-backend.service
    curl -s "http://localhost:8080/server/api/integration/externalsources/K10plusEnriched" | jq .
    curl -s "http://localhost:8080/server/api/integration/externalsources/K10plusEnriched/entries?query=Vechta&size=2" | jq '._embedded.externalSourceEntries | length'

### 6. Inject Angular i18n Keys and Rebuild the Frontend

    python3 "${REPO_ROOT}/scripts/inject_i18n.py" /opt/dspace-cris-9-angular/src/assets/i18n

    export NVM_DIR="$HOME/.nvm"
    [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"
    nvm use 22 || nvm use 20

    cd /opt/dspace-cris-9-angular
    npm run build:prod
    pm2 reload dspace-ui