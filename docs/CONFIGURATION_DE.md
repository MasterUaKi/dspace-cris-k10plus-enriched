<!-- Version: 1.1.0 | Release Date: 2026-11-17 -->
# Konfigurations- und Anwendungsleitfaden (`k10plus-enriched.cfg`)

Der Provider `K10plusEnriched` ist so konzipiert, dass sämtliche Metadaten-Mappings, SRU-Verbindungsparameter und Solr-Suchfelder für CRIS-Entitäten über die externe Konfigurationsdatei **`/dspace/config/modules/k10plus-enriched.cfg`** ohne Neukompilierung der Java-JAR-Dateien angepasst werden können.

---

## 1. Verbindungs- und CQL-Abfrageparameter

| Schlüssel in `k10plus-enriched.cfg` | Standardwert | Beschreibung |
|---|---|---|
| `k10plus-enriched.sru.url` | `https://sru.k10plus.de/opac-de-627` | Basis-URL der K10plus-SRU-Schnittstelle (anpassbar für lokale Proxys oder spezifische Verbund-Profile) |
| `k10plus-enriched.sru.timeout` | `12` | HTTP-Timeout für SRU-Anfragen in Sekunden |
| `k10plus-enriched.sru.cql.template` | `(pica.tit="%s" or pica.per="%s")` | CQL-Abfragevorlage für Freitext-Suchen (unterstützt 1 oder 2 `%s`-Platzhalter) |

### Automatische Abfrage-Erkennung (Query Routing)
Vor Anwendung von `sru.cql.template` analysiert der Provider die Eingabe des Nutzers:
* Enthält die Suchanfrage `pica.` — direkte Übergabe als native CQL-Abfrage (z. B. `pica.tit="Vechta" and pica.jah=2025`).
* Erkannter **DOI** (`10.xxxx/...`) — Abfrage über `pica.doi="..."`.
* Erkannte **ISBN** (10- oder 13-stellig) — Bindestriche werden entfernt, Abfrage über `pica.isb="..."`.
* Erkannte **PPN** (8–10 Ziffern + Prüfziffer) — Abfrage über `pica.ppn="..."`.

---

## 2. Metadaten-Mapping (`k10plus-enriched.map.*`)

Jeder logische Schlüssel unterstützt folgende Notation:
* **Einzelnes Zielfeld:** `k10plus-enriched.map.title = dc.title`
* **Mehrere Zielfelder (kommagetrennt):** `k10plus-enriched.map.ppn = dc.identifier.other, cris.sourceId`
* **Deaktivieren eines Feldes:** `k10plus-enriched.map.extent = none`

| `.cfg`-Schlüssel | Standard-Zielfeld(er) | Datenquelle (MARC21 / OpenAlex) |
|---|---|---|
| `map.ppn` | `dc.identifier.other, cris.sourceId` | MARC `001` (K10plus-PPN) |
| `map.title` | `dc.title` | MARC `245 $a` (Haupttitel) |
| `map.subTitle` | `dc.title.subtitle` | MARC `245 $b` (Untertitel) |
| `map.author` | `dc.contributor.author` | MARC `100 $a` und `700 $a` (+ GND/ORCID-Authority) |
| `map.authorOrcid` | `cris.author.orcid` | OpenAlex Works API (Nachnamen-Abgleich) |
| `map.publisher` | `dc.publisher` | MARC `264 $b` (prioritär) oder `260 $b` |
| `map.publicationPlace` | `veroPU.bibliographicCitation.publisherPlace` | MARC `264 $a` oder `260 $a` (Verlagsort) |
| `map.issuedYear` | `dc.date.issued` | MARC `264 $c` oder `260 $c` (normalisiertes Jahr) |
| `map.isbn` | `dc.identifier.isbn` | MARC `020 $a` |
| `map.issn` | `dc.identifier.issn` | MARC `022 $a` |
| `map.doi` | `dc.identifier.doi` | MARC `024 7 $a` (`$2=doi`) oder OpenAlex-Fallback |
| `map.language` | `dc.language.iso` | MARC `041 $a` (ISO 639-2/B) |
| `map.extent` | `dc.format.extent` | MARC `300 $a` (Umfang / Seitenanzahl) |
| `map.abstract` | `dc.description.abstract` | MARC `520 $a` oder rekonstruierter Abstract aus OpenAlex |
| `map.subject` | `dc.subject` | MARC `650 $a` und `653 $a` |
| `map.journalTitle` | `dc.relation.ispartof` | MARC `773 $t` oder `$a` (Zeitschriften-/Sammelbandtitel) |
| `map.uri` | `dc.identifier.uri` | MARC `856 $u` + Open-Access-PDF-URL aus OpenAlex |
| `map.affiliation` | `oairecerif.author.affiliation` | OpenAlex `authorships[].institutions[]` |
| `map.orgUnit` | `vero.OrgUnit` | OpenAlex Institutions bei Übereinstimmung mit lokaler `OrganizationalUnit` |

---

## 3. Automatische Verknüpfung von CRIS-Entitäten (Authority Binding)

1. **Personen (`Person`):**
   * Aus MARC `100/700 $0` wird die GND-ID extrahiert und auf `(DE-588)<ID>` normalisiert.
   * Aus OpenAlex wird die ORCID des Autors ergänzt.
   * Im lokalen Solr-Index wird über `k10plus-enriched.authority.person.gnd-field` und `k10plus-enriched.authority.person.orcid-field` gesucht.
   * Bei einem Treffer erhält `dc.contributor.author` die interne UUID mit Status `Choices.CF_ACCEPTED` (`600`). Ohne lokalen Treffer, aber mit vorhandener GND, wird die GND mit `CF_UNCERTAIN` (`200`) hinterlegt.
2. **Organisationseinheiten (`OrganizationalUnit`):**
   * Über die Institutionsnamen aus OpenAlex wird im Feld `k10plus-enriched.authority.orgunit.title-field` gesucht.
   * Bei einem Treffer wird das unter `k10plus-enriched.map.orgUnit` konfigurierte Feld inkl. UUID (`CF_ACCEPTED`) hinzugefügt.

---

## 4. Änderungen übernehmen

Nach Anpassungen an `/dspace/config/modules/k10plus-enriched.cfg` genügt ein Neustart des Backends:

```bash
sudo systemctl restart dspace-backend.service
