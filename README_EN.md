<!-- Version: 1.2.0 | Release Date: 2026-11-17 -->
# K10plus + OpenAlex Enriched Live Import Provider for DSpace-CRIS 9

A hybrid External Data Provider (`ExternalDataProvider`) for **DSpace-CRIS 9** (`2025.02.00`) combining bibliographic search across the **K10plus Union Catalog (SRU / MARC21 XML)** with automated metadata enrichment via the **OpenAlex Works API**, local CRIS entity authority linking (`Person`, `OrganizationalUnit`) via GND/ORCID, and hot-configurable `.cfg` field mappings.

---

## Architecture Overview

    [DSpace-CRIS 9 UI / REST API]
                 │  GET /server/api/integration/externalsources/K10plusEnriched/entries?query=...
                 ▼
    [K10plusEnrichedExternalDataProvider]  ──► Binds Authority UUIDs & Confidence (CF_ACCEPTED)
                 │
                 ▼
    [K10plusEnrichedDataProvider]
       ├──► 1. K10plus SRU API (MARC21 XML, namespace http://www.loc.gov/MARC21/slim)
       ├──► 2. OpenAlex Works API (DOI / Title+Year fallback -> Abstract, ORCID, OA PDF, Affiliations)
       ├──► 3. Local Solr Discovery (Matches Person via GND/ORCID & OrganizationalUnit via title)
       └──► 4. ConfigurationService (/dspace/config/modules/k10plus-enriched.cfg)

### Key Features
* **Smart CQL Query Routing:** Automatically detects DOIs, ISBNs, PPNs, native `pica.*` expressions, and free-text queries.
* **Duplicate-Free SRU Parsing:** `MarcXmlParser` selects records strictly by the MARC21 namespace (`http://www.loc.gov/MARC21/slim`), avoiding double-parsing of outer `<zs:record>` wrappers.
* **OpenAlex Enrichment:** Reconstructs missing abstracts from `abstract_inverted_index`, enriches co-authors with ORCID iDs, adds Open Access PDF links (`best_oa_location`), and extracts institutional affiliations.
* **Hot-Configurable `.cfg` Mappings:** Map logical DTO attributes to any local metadata schema (`dc.*`, `vero.*`, `veroPU.*`, `kdsf.*`) in `/dspace/config/modules/k10plus-enriched.cfg` without recompiling JAR archives.

---

## Installation Options

Two backend installation workflows are supported:

### Option A: Full Standard Build with Maven & Ant (Recommended for standard deployments)
Compiles the entire DSpace-CRIS 9 source tree (`mvn clean package`), builds a brand-new `server-boot.jar`, and deploys via `sudo ant update`.  
➡️ **Complete Step-by-Step Guide:** **[docs/FULL_BUILD_EN.md](docs/FULL_BUILD_EN.md)**

### Option B: Fast Modular Installation (`scripts/install.sh`)
Compiles only the `dspace/modules/additions` module (~30 KB) and replaces `BOOT-INF/lib/additions-cris-*.jar` inside the existing `server-boot.jar` uncompressed (`ZIP_STORED`):

    git clone [https://github.com/MasterUaKi/dspace-cris-k10plus-enriched.git](https://github.com/MasterUaKi/dspace-cris-k10plus-enriched.git)
    cd dspace-cris-k10plus-enriched
    chmod +x scripts/install.sh
    ./scripts/install.sh

---

## Frontend Localization & Verification (Applies to Options A & B)

### 1. Verify the REST API

    curl -s "http://localhost:8080/server/api/integration/externalsources/K10plusEnriched" | jq .
    curl -s "http://localhost:8080/server/api/integration/externalsources/K10plusEnriched/entries?query=Vechta&size=2" | jq '._embedded.externalSourceEntries | length'

### 2. Inject i18n Keys and Rebuild the Angular Frontend

    python3 scripts/inject_i18n.py /opt/dspace-cris-9-angular/src/assets/i18n

    export NVM_DIR="$HOME/.nvm"
    [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"
    nvm use 22 || nvm use 20

    cd /opt/dspace-cris-9-angular
    npm run build:prod
    pm2 reload dspace-ui

---

## Configuration and Usage

See **[docs/CONFIGURATION_EN.md](docs/CONFIGURATION_EN.md)** for a complete reference of `.cfg` properties, custom schema mappings, and CRIS authority linking rules.

### Example Queries in Publication Submission
* **Free-text / Author:** `Vechta` or `Riabtsev`
* **DOI:** `10.1007/978-3-031-96081-9_2`
* **ISBN:** `978-3-658-12345-6`
* **K10plus PPN:** `198853741X`
* **Raw CQL Query:** `pica.tit="Digitalisierung" and pica.jah=2025`

---

## Troubleshooting

* **`NoUniqueBeanDefinitionException: LiveImportClientImpl` on startup:** Ensure no backup files ending in `.xml` (such as `external-services.old.xml`) remain inside `/dspace/config/spring/api/` (and in `${DSPACE_SRC}/dspace/config/spring/api/` when performing a full Maven build).
* **Nested JAR errors in `server-boot.jar`:** Libraries inside `BOOT-INF/lib/` must be stored uncompressed (`ZIP_STORED`); `scripts/install.sh` handles this automatically, while a full build uses the Spring Boot Maven Plugin.
* **Raw translation key `submission.import-external.source.K10plusEnriched` displayed in UI:** Verify that `npm run build:prod` ran with Node.js v20.19+/v22.12+ and that PM2 reloaded the UI cluster (`pm2 reload dspace-ui`).