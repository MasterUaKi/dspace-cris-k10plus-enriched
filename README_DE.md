<!-- Version: 1.1.0 | Release Date: 2026-11-17 -->
# K10plus + OpenAlex Enriched Live-Import-Provider für DSpace-CRIS 9

Ein hybrider External-Data-Provider (`ExternalDataProvider`) für **DSpace-CRIS 9** (`2025.02.00`), der die bibliografische Suche im **K10plus-Verbundkatalog (SRU / MARC21 XML)** mit automatischer Metadaten-Anreicherung über die **OpenAlex Works API**, lokaler CRIS-Entitätsverknüpfung (`Person`, `OrganizationalUnit`) via GND/ORCID sowie flexibler `.cfg`-Konfiguration kombiniert.

---

## Architekturübersicht

```text
[DSpace-CRIS 9 UI / REST API]
             │  GET /server/api/integration/externalsources/K10plusEnriched/entries?query=...
             ▼
[K10plusEnrichedExternalDataProvider]  ──► Verknüpfung von Authority-UUIDs & Confidence (CF_ACCEPTED)
             │
             ▼
[K10plusEnrichedDataProvider]
   ├──► 1. K10plus SRU API (MARC21 XML, Namespace [http://www.loc.gov/MARC21/slim](http://www.loc.gov/MARC21/slim))
   ├──► 2. OpenAlex Works API (DOI / Titel+Jahr-Fallback -> Abstract, ORCID, OA-PDF, Affiliationen)
   ├──► 3. Lokale Solr-Suche (Abgleich von Person über GND/ORCID & OrganizationalUnit über Titel)
   └──► 4. ConfigurationService (/dspace/config/modules/k10plus-enriched.cfg)
```

### Hauptfunktionen
* **Intelligentes CQL-Query-Routing:** Automatische Erkennung von DOI, ISBN, PPN, nativen `pica.*`-Ausdrücken sowie Freitext-Anfragen.
* **Duplikatfreie SRU-Verarbeitung:** `MarcXmlParser` filtert Datensätze strikt anhand des MARC21-Namespaces (`http://www.loc.gov/MARC21/slim`) und verhindert eine doppelte Auswertung des äußeren `<zs:record>`-Wrappers.
* **OpenAlex-Anreicherung:** Automatische Rekonstruktion fehlender Abstracts (`abstract_inverted_index`), Ergänzung von Co-Autoren-ORCIDs, Open-Access-PDF-Links (`best_oa_location`) und Institutionen.
* **Hot-Reload-fähiges Mapping via `.cfg`:** Anpassung aller Ziel-Metadatenfelder (`dc.*`, `vero.*`, `veroPU.*`, `kdsf.*`) in `/dspace/config/modules/k10plus-enriched.cfg` ohne Neukompilierung der JAR-Dateien.

---

## Installationsanleitung

### Voraussetzungen
* DSpace-CRIS `9.x` (`2025.02.00` oder neuer) mit Quellcode unter `/build/DSpace-CRIS-9` und Laufzeitumgebung unter `/dspace`.
* Java 17/21, Maven 3.8+, Python 3.8+.
* Für das Angular-Frontend: Node.js `v20.19+` oder `v22.12+`.

### Schritt 1: Backend-Modul und Konfigurationen installieren

```bash
git clone <REPOSITORY-URL> dspace-cris-k10plus-enriched
cd dspace-cris-k10plus-enriched
chmod +x scripts/install.sh
./scripts/install.sh
```

Das Skript `scripts/install.sh` führt automatisiert folgende Schritte aus:
1. Erstellt Sicherheitskopien von `server-boot.jar` und bestehenden Konfigurationen unter `~/dspace_backups/`.
2. Kopiert die Java-Klassen nach `dspace/modules/additions` und kompiliert `additions-cris-*.jar`.
3. Installiert `k10plus-external-services.xml` nach `/dspace/config/spring/api/` sowie `k10plus-enriched.cfg` nach `/dspace/config/modules/`.
4. Aktualisiert `BOOT-INF/lib/additions-cris-*.jar` innerhalb von `/dspace/webapps/server-boot.jar` unkomprimiert (`ZIP_STORED`), wie es der Spring Boot `JarLauncher` erfordert.
5. Startet den Dienst `dspace-backend.service` neu.

### Schritt 2: REST-API überprüfen

```bash
curl -s "http://localhost:8080/server/api/integration/externalsources/K10plusEnriched" | jq .
curl -s "http://localhost:8080/server/api/integration/externalsources/K10plusEnriched/entries?query=Vechta&size=2" | jq '._embedded.externalSourceEntries | length'
```

### Schritt 3: Lokalisierung hinzufügen und Angular-Frontend neu bauen

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

* **`NoUniqueBeanDefinitionException: LiveImportClientImpl` beim Start:** Stellen Sie sicher, dass unter `/dspace/config/spring/api/` keine Backup-Dateien mit der Endung `.xml` (z. B. `external-services.old.xml`) liegen. DSpace lädt automatisch alle `*.xml`-Dateien aus diesem Verzeichnis.
* **Fehler bei verschachtelten JARs in `server-boot.jar`:** Bibliotheken unter `BOOT-INF/lib/` müssen zwingend im Modus `ZIP_STORED` gespeichert werden (`scripts/install.sh` übernimmt dies automatisch).
* **Im Frontend erscheint der unübersetzte Schlüssel `submission.import-external.source.K10plusEnriched`:** Stellen Sie sicher, dass `npm run build:prod` unter Node.js v20.19+/v22.12+ ausgeführt und der Cluster `dspace-ui` mittels `pm2 reload dspace-ui` neu geladen wurde.
