<!-- Version: 1.1.0 | Release Date: 2026-11-17 -->
# K10plus + OpenAlex Enriched Live Import Provider for DSpace-CRIS 9

A hybrid External Data Provider (`ExternalDataProvider`) for **DSpace-CRIS 9** (`2025.02.00`) combining bibliographic search across the **K10plus Union Catalog (SRU / MARC21 XML)** with automated metadata enrichment via the **OpenAlex Works API**, local CRIS entity authority linking (`Person`, `OrganizationalUnit`) via GND/ORCID, and hot-configurable `.cfg` field mappings.

---

## Architecture Overview

```text
[DSpace-CRIS 9 UI / REST API]
             │  GET /server/api/integration/externalsources/K10plusEnriched/entries?query=...
             ▼
[K10plusEnrichedExternalDataProvider]  ──► Binds Authority UUIDs & Confidence (CF_ACCEPTED)
             │
             ▼
[K10plusEnrichedDataProvider]
   ├──► 1. K10plus SRU API (MARC21 XML, namespace [http://www.loc.gov/MARC21/slim](http://www.loc.gov/MARC21/slim))
   ├──► 2. OpenAlex Works API (DOI / Title+Year fallback -> Abstract, ORCID, OA PDF, Affiliations)
   ├──► 3. Local Solr Discovery (Matches Person via GND/ORCID & OrganizationalUnit via title)
   └──► 4. ConfigurationService (/dspace/config/modules/k10plus-enriched.cfg)
```

### Key Features
* **Smart CQL Query Routing:** Automatically detects DOIs, ISBNs, PPNs, native `pica.*` expressions, and free-text queries.
* **Duplicate-Free SRU Parsing:** `MarcXmlParser` selects records strictly by the MARC21 namespace (`http://www.loc.gov/MARC21/slim`), avoiding double-parsing of outer `<zs:record>` wrappers.
* **OpenAlex Enrichment:** Reconstructs missing abstracts from `abstract_inverted_index`, enriches co-authors with ORCID iDs, adds Open Access PDF links (`best_oa_location`), and extracts institutional affiliations.
* **Hot-Configurable `.cfg` Mappings:** Map logical DTO attributes to any local metadata schema (`dc.*`, `vero.*`, `veroPU.*`, `kdsf.*`) in `/dspace/config/modules/k10plus-enriched.cfg` without recompiling JAR archives.

---

## Installation Guide

### Prerequisites
* DSpace-CRIS `9.x` (`2025.02.00` or later) with source tree at `/build/DSpace-CRIS-9` and runtime installation at `/dspace`.
* Java 17/21, Maven 3.8+, Python 3.8+.
* For the Angular frontend: Node.js `v20.19+` or `v22.12+`.

### Step 1: Install Backend Module and Configurations

```bash
git clone <REPOSITORY-URL> dspace-cris-k10plus-enriched
cd dspace-cris-k10plus-enriched
chmod +x scripts/install.sh
./scripts/install.sh
```

The `scripts/install.sh` script automatically:
1. Backs up `server-boot.jar` and any existing configuration files to `~/dspace_backups/`.
2. Copies the Java classes into `dspace/modules/additions` and builds `additions-cris-*.jar`.
3. Installs `k10plus-external-services.xml` into `/dspace/config/spring/api/` and `k10plus-enriched.cfg` into `/dspace/config/modules/`.
4. Replaces `BOOT-INF/lib/additions-cris-*.jar` inside `/dspace/webapps/server-boot.jar` using `ZIP_STORED` (uncompressed), as required by Spring Boot's `JarLauncher`.
5. Restarts `dspace-backend.service`.

### Step 2: Verify the REST API

```bash
curl -s "http://localhost:8080/server/api/integration/externalsources/K10plusEnriched" | jq .
curl -s "http://localhost:8080/server/api/integration/externalsources/K10plusEnriched/entries?query=Vechta&size=2" | jq '._embedded.externalSourceEntries | length'
```

### Step 3: Inject i18n Keys and Rebuild the Angular Frontend

```bash
python3 scripts/inject_i18n.py /opt/dspace-cris-9-angular/src/assets/i18n

export NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"
nvm use 22 || nvm use 20

cd /opt/dspace-cris-9-angular
npm run build:prod
pm2 reload dspace-ui
```

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

* **`NoUniqueBeanDefinitionException: LiveImportClientImpl` on startup:** Ensure no backup files ending in `.xml` (such as `external-services.old.xml`) remain inside `/dspace/config/spring/api/`, as DSpace loads all `*.xml` files in that folder.
* **Nested JAR errors in `server-boot.jar`:** Libraries inside `BOOT-INF/lib/` must be stored uncompressed (`ZIP_STORED`); `scripts/install.sh` handles this automatically.
* **Raw translation key `submission.import-external.source.K10plusEnriched` displayed in UI:** Verify that `npm run build:prod` ran with Node.js v20.19+/v22.12+ and that PM2 reloaded the UI cluster (`pm2 reload dspace-ui`).
