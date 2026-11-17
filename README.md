<!-- Version: 1.1.0 | Release Date: 2026-11-17 -->
# K10plus + OpenAlex Enriched Live Import Provider (DSpace-CRIS 9)

**Sprachen / Languages:** [🇩🇪 Deutsch (README_DE.md)](README_DE.md) | [🇬🇧 English (README_EN.md)](README_EN.md)

---

## 🇩🇪 Kurzübersicht (Deutsch)

Hybrider External-Data-Provider für **DSpace-CRIS 9** (`2025.02.00`), der die SRU/MARC21-Suche im **K10plus-Katalog** mit automatischer Metadaten-Anreicherung über **OpenAlex**, lokaler CRIS-Entitätsverknüpfung (`Person` via GND/ORCID, `OrganizationalUnit`) und flexibler `.cfg`-Konfiguration (`/dspace/config/modules/k10plus-enriched.cfg`) verbindet.

* **Vollständige Installations- und Betriebsanleitung (DE):** [README_DE.md](README_DE.md)
* **Konfigurationsleitfaden `.cfg` & Metadaten-Mapping (DE):** [docs/CONFIGURATION_DE.md](docs/CONFIGURATION_DE.md)

### Schnellstart (Backend & Frontend)
```bash
chmod +x scripts/install.sh
./scripts/install.sh
python3 scripts/inject_i18n.py /opt/dspace-cris-9-angular/src/assets/i18n
```

---

## 🇬🇧 Overview (English)

Hybrid External Data Provider for **DSpace-CRIS 9** (`2025.02.00`) integrating **K10plus SRU/MARC21** catalog retrieval with automated **OpenAlex Works API** enrichment, local CRIS entity authority linking (`Person` via GND/ORCID, `OrganizationalUnit`), and hot-configurable `.cfg` field mappings (`/dspace/config/modules/k10plus-enriched.cfg`).

* **Full Installation & Operations Guide (EN):** [README_EN.md](README_EN.md)
* **Configuration Guide `.cfg` & Metadata Mapping (EN):** [docs/CONFIGURATION_EN.md](docs/CONFIGURATION_EN.md)

### Quick Start (Backend & Frontend)
```bash
chmod +x scripts/install.sh
./scripts/install.sh
python3 scripts/inject_i18n.py /opt/dspace-cris-9-angular/src/assets/i18n
```

---

## 📁 Repository Structure / Verzeichnisstruktur

```text
dspace-cris-k10plus-enriched/
├── README.md                          # Bilingual entry point (DE / EN)
├── README_DE.md                       # Vollständige Dokumentation (Deutsch)
├── README_EN.md                       # Complete documentation (English)
├── LICENSE                            # MIT License
├── docs/
│   ├── CONFIGURATION_DE.md            # Konfigurationshandbuch (Deutsch)
│   └── CONFIGURATION_EN.md            # Configuration reference (English)
├── config/
│   ├── modules/
│   │   └── k10plus-enriched.cfg       # Hot-configurable metadata mappings
│   └── spring/
│       └── api/
│           └── k10plus-external-services.xml
├── backend/
│   └── additions/src/main/java/org/dspace/external/provider/
│       ├── impl/
│       ├── parser/
│       ├── client/
│       └── dto/
└── scripts/
    ├── install.sh                     # Automated backend installer
    └── inject_i18n.py                 # Angular i18n injector (de.json5, en.json5)
```
