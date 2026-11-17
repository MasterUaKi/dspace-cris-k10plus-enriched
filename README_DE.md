<!-- Version: 1.2.0 | Release Date: 2026-11-17 -->
# K10plus + OpenAlex Enriched Live-Import-Provider für DSpace-CRIS 9

Ein hybrider External-Data-Provider (`ExternalDataProvider`) für **DSpace-CRIS 9** (`2025.02.00`), der die bibliografische Suche im **K10plus-Verbundkatalog (SRU / MARC21 XML)** mit automatischer Metadaten-Anreicherung über die **OpenAlex Works API**, lokaler CRIS-Entitätsverknüpfung (`Person`, `OrganizationalUnit`) via GND/ORCID sowie flexibler `.cfg`-Konfiguration kombiniert.

---

## Architekturübersicht

    [DSpace-CRIS 9 UI / REST API]
                 │  GET /server/api/integration/externalsources/K10plusEnriched/entries?query=...
                 ▼
    [K10plusEnrichedExternalDataProvider]  ──► Verknüpfung von Authority-UUIDs & Confidence (CF_ACCEPTED)
                 │
                 ▼
    [K10plusEnrichedDataProvider]
       ├──► 1. K10plus SRU API (MARC21 XML, Namespace http://www.loc.gov/MARC21/slim)
       ├──► 2. OpenAlex Works API (DOI / Titel+Jahr-Fallback -> Abstract, ORCID, OA-PDF, Affiliationen)
       ├──► 3. Lokale Solr-Suche (Abgleich von Person über GND/ORCID & OrganizationalUnit über Titel)
       └──► 4. ConfigurationService (/dspace/config/modules/k10plus-enriched.cfg)

### Hauptfunktionen
* **Intelligentes CQL-Query-Routing:** Automatische Erkennung von DOI, ISBN, PPN, nativen `pica.*`-Ausdrücken sowie Freitext-Anfragen.
* **Duplikatfreie SRU-Verarbeitung:** `MarcXmlParser` filtert Datensätze strikt anhand des MARC21-Namespaces (`http://www.loc.gov/MARC21/slim`) und verhindert eine doppelte Auswertung des äußeren `<zs:record>`-Wrappers.
* **OpenAlex-Anreicherung:** Automatische Rekonstruktion fehlender Abstracts (`abstract_inverted_index`), Ergänzung von Co-Autoren-ORCIDs, Open-Access-PDF-Links (`best_oa_location`) und Institutionen.
* **Hot-Reload-fähiges Mapping via `.cfg`:** Anpassung aller Ziel-Metadatenfelder (`dc.*`, `vero.*`, `veroPU.*`, `kdsf.*`) in `/dspace/config/modules/k10plus-enriched.cfg` ohne Neukompilierung der JAR-Dateien.

---

## Installationsmöglichkeiten

Für das Backend stehen **zwei gleichwertige Installationswege** zur Verfügung:

### Option A: Vollständiger Standard-Build mit Maven & Ant (Empfohlen für reguläre Deployments)
Kompiliert den gesamten DSpace-CRIS-9-Quellbaum (`mvn clean package`), erzeugt eine komplett neue `server-boot.jar` und aktualisiert `/dspace` über `sudo ant update`.  
➡️ **Vollständige Schritt-für-Schritt-Anleitung:** **[docs/FULL_BUILD_DE.md](docs/FULL_BUILD_DE.md)**

### Option B: Schnelle modulare Installation (`scripts/install.sh`)
Kompiliert ausschließlich das Modul `dspace/modules/additions` (~30 KB) und aktualisiert `BOOT-INF/lib/additions-cris-*.jar` innerhalb der bestehenden `server-boot.jar` unkomprimiert (`ZIP_STORED`):

    git clone [https://github.com/MasterUaKi/dspace-cris-k10plus-enriched.git](https://github.com/MasterUaKi/dspace-cris-k10plus-enriched.git)
    cd dspace-cris-k10plus-enriched
    chmod +x scripts/install.sh
    ./scripts/install.sh

---

## Frontend-Lokalisierung & Überprüfung (für Option A & B)

### 1. REST-API überprüfen

    curl -s "http://localhost:8080/server/api/integration/externalsources/K10plusEnriched" | jq .
    curl -s "http://localhost:8080/server/api/integration/externalsources/K10plusEnriched/entries?query=Vechta&size=2" | jq '._embedded.externalSourceEntries | length'

### 2. Lokalisierung hinzufügen und Angular-Frontend neu bauen

    python3 scripts/inject_i18n.py /opt/dspace-cris-9-angular/src/assets/i18n

    export NVM_DIR="$HOME/.nvm"
    [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"
    nvm use 22 || nvm use 20

    cd /opt/dspace-cris-9-angular
    npm run build:prod
    pm2 reload dspace-ui

---

## Anwendung und Konfiguration

Eine detaillierte Beschreibung aller `.cfg`-Schlüssel, der Metadaten-Mappings sowie der CRIS-Authority-Verknüpfung finden Sie unter **[docs/CONFIGURATION_DE.md](docs/CONFIGURATION_DE.md)**.

### Suchbeispiele in der Publikationseinreichung
* **Freitext / Autor:** `Vechta` oder `Riabtsev`
* **DOI:** `10.1007/978-3-031-96081-9_2`
* **ISBN:** `978-3-658-12345-6`
* **K10plus PPN:** `198853741X`
* **Direkte CQL-Abfrage:** `pica.tit="Digitalisierung" and pica.jah=2025`

---

## Fehlerbehebung (Troubleshooting)

* **`NoUniqueBeanDefinitionException: LiveImportClientImpl` beim Start:** Stellen Sie sicher, dass unter `/dspace/config/spring/api/` (und bei vollständigem Maven-Build auch unter `${DSPACE_SRC}/dspace/config/spring/api/`) keine Backup-Dateien mit der Endung `.xml` (z. B. `external-services.old.xml`) liegen.
* **Fehler bei verschachtelten JARs in `server-boot.jar`:** Bibliotheken unter `BOOT-INF/lib/` müssen zwingend im Modus `ZIP_STORED` gespeichert werden (`scripts/install.sh` übernimmt dies automatisch; beim vollständigen Build erledigt dies das Spring Boot Maven Plugin).
* **Im Frontend erscheint der unübersetzte Schlüssel `submission.import-external.source.K10plusEnriched`:** Stellen Sie sicher, dass `npm run build:prod` unter Node.js v20.19+/v22.12+ ausgeführt und der Cluster `dspace-ui` mittels `pm2 reload dspace-ui` neu geladen wurde.