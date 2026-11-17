<!-- Version: 1.1.0 | Release Date: 2026-11-17 -->
# Configuration and Usage Guide (`k10plus-enriched.cfg`)

The `K10plusEnriched` provider is designed so that all metadata field mappings, SRU connection parameters, and CRIS entity lookup fields can be customized via the external configuration module **`/dspace/config/modules/k10plus-enriched.cfg`** without recompiling any Java JAR files.

---

## 1. Connection and CQL Query Parameters

| Key in `k10plus-enriched.cfg` | Default Value | Description |
|---|---|---|
| `k10plus-enriched.sru.url` | `https://sru.k10plus.de/opac-de-627` | Base URL of the K10plus SRU endpoint (can be pointed to a local proxy or another SRU target) |
| `k10plus-enriched.sru.timeout` | `12` | HTTP timeout for SRU requests in seconds |
| `k10plus-enriched.sru.cql.template` | `(pica.tit="%s" or pica.per="%s")` | CQL query template for free-text searches (supports 1 or 2 `%s` placeholders) |

### Automatic Query Routing
Before applying `sru.cql.template`, the provider inspects the user query:
* Contains `pica.` — passed directly as a raw CQL query (e.g., `pica.tit="Vechta" and pica.jah=2025`).
* Matches **DOI** (`10.xxxx/...`) — queries `pica.doi="..."`.
* Matches **ISBN** (10 or 13 digits) — strips hyphens and queries `pica.isb="..."`.
* Matches **PPN** (8–10 digits + check digit) — queries `pica.ppn="..."`.

---

## 2. Metadata Field Mappings (`k10plus-enriched.map.*`)

Each logical key supports:
* **Single target field:** `k10plus-enriched.map.title = dc.title`
* **Multiple target fields (comma-separated):** `k10plus-enriched.map.ppn = dc.identifier.other, cris.sourceId`
* **Disabling a field:** `k10plus-enriched.map.extent = none`

| `.cfg` Key | Default Target Field(s) | Data Source (MARC21 / OpenAlex) |
|---|---|---|
| `map.ppn` | `dc.identifier.other, cris.sourceId` | MARC `001` (K10plus PPN identifier) |
| `map.title` | `dc.title` | MARC `245 $a` (Main title) |
| `map.subTitle` | `dc.title.subtitle` | MARC `245 $b` (Subtitle) |
| `map.author` | `dc.contributor.author` | MARC `100 $a` and `700 $a` (+ GND/ORCID authority) |
| `map.authorOrcid` | `cris.author.orcid` | OpenAlex Works API (matched by author surname) |
| `map.publisher` | `dc.publisher` | MARC `264 $b` (preferred) or `260 $b` |
| `map.publicationPlace` | `veroPU.bibliographicCitation.publisherPlace` | MARC `264 $a` or `260 $a` (Place of publication) |
| `map.issuedYear` | `dc.date.issued` | MARC `264 $c` or `260 $c` (Normalized year) |
| `map.isbn` | `dc.identifier.isbn` | MARC `020 $a` |
| `map.issn` | `dc.identifier.issn` | MARC `022 $a` |
| `map.doi` | `dc.identifier.doi` | MARC `024 7 $a` (`$2=doi`) or OpenAlex fallback |
| `map.language` | `dc.language.iso` | MARC `041 $a` (ISO 639-2/B) |
| `map.extent` | `dc.format.extent` | MARC `300 $a` (Physical extent / pages) |
| `map.abstract` | `dc.description.abstract` | MARC `520 $a` or reconstructed abstract from OpenAlex |
| `map.subject` | `dc.subject` | MARC `650 $a` and `653 $a` |
| `map.journalTitle` | `dc.relation.ispartof` | MARC `773 $t` or `$a` (Journal / host item title) |
| `map.uri` | `dc.identifier.uri` | MARC `856 $u` + Open Access PDF URL from OpenAlex |
| `map.affiliation` | `oairecerif.author.affiliation` | OpenAlex `authorships[].institutions[]` |
| `map.orgUnit` | `vero.OrgUnit` | OpenAlex Institutions when matched to a local `OrganizationalUnit` |

---

## 3. Automatic CRIS Entity Linking (Authority Binding)

1. **Persons (`Person`):**
   * Extracts and normalizes GND identifiers from MARC `100/700 $0` into `(DE-588)<ID>`.
   * Enriches author entries with ORCID identifiers from OpenAlex.
   * Queries local Solr using `k10plus-enriched.authority.person.gnd-field` and `k10plus-enriched.authority.person.orcid-field`.
   * On a match, `dc.contributor.author` receives the local Person UUID with `Choices.CF_ACCEPTED` (`600`). If unmatched locally but a GND ID exists, the normalized GND is attached with `CF_UNCERTAIN` (`200`).
2. **Organizational Units (`OrganizationalUnit`):**
   * Searches local Solr by institution name against `k10plus-enriched.authority.orgunit.title-field`.
   * On a match, populates the field configured in `k10plus-enriched.map.orgUnit` with the local OrgUnit UUID (`CF_ACCEPTED`).

---

## 4. Applying Configuration Changes

After editing `/dspace/config/modules/k10plus-enriched.cfg`, restart the backend service:

```bash
sudo systemctl restart dspace-backend.service
```
