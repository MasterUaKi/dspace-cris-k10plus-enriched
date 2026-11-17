/**
 * Version: 1.1.0 | Release Date: 2026-11-17
 */
package org.dspace.external.provider.impl;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.content.Item;
import org.dspace.content.authority.Choices;
import org.dspace.core.Context;
import org.dspace.discovery.DiscoverQuery;
import org.dspace.discovery.DiscoverResult;
import org.dspace.discovery.IndexableObject;
import org.dspace.discovery.SearchService;
import org.dspace.discovery.indexobject.IndexableItem;
import org.dspace.external.provider.client.OpenAlexEnrichmentClient;
import org.dspace.external.provider.dto.K10plusRecordDTO;
import org.dspace.external.provider.dto.K10plusRecordDTO.AuthorEntry;
import org.dspace.external.provider.dto.OpenAlexEnrichmentDTO;
import org.dspace.external.provider.dto.OpenAlexEnrichmentDTO.EnrichedAuthor;
import org.dspace.external.provider.parser.MarcXmlParser;
import org.dspace.importer.external.datamodel.ImportRecord;
import org.dspace.importer.external.datamodel.Query;
import org.dspace.importer.external.exception.MetadataSourceException;
import org.dspace.importer.external.metadatamapping.MetadatumDTO;
import org.dspace.importer.external.service.AbstractImportMetadataSourceService;
import org.dspace.importer.external.service.components.QuerySource;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

public class K10plusEnrichedDataProvider extends AbstractImportMetadataSourceService<K10plusRecordDTO> implements QuerySource {

    private static final Logger log = LogManager.getLogger(K10plusEnrichedDataProvider.class);

    private static final String CFG_PREFIX = "k10plus-enriched.";
    private static final Pattern DOI_PATTERN = Pattern.compile("^10\\.\\d{4,9}/[-._;()/:A-Za-z0-9]+$");
    private static final Pattern ISBN_PATTERN = Pattern.compile("^(97[89][- ]?)?\\d{1,5}[- ]?\\d{1,7}[- ]?\\d{1,7}[- ]?[0-9Xx]$");
    private static final Pattern PPN_PATTERN = Pattern.compile("^\\d{8,10}[0-9Xx]$");

    public static class AuthorityBinding {
        private final String authority;
        private final int confidence;

        public AuthorityBinding(String authority, int confidence) {
            this.authority = authority;
            this.confidence = confidence;
        }

        public String getAuthority() { return authority; }
        public int getConfidence() { return confidence; }
    }

    private String sruBaseUrl = "https://sru.k10plus.de/opac-de-627";
    private String importSourceName = "K10plusEnriched";
    private int timeoutSeconds = 12;

    private MarcXmlParser marcXmlParser;
    private OpenAlexEnrichmentClient openAlexEnrichmentClient;

    @Autowired
    private SearchService searchService;

    @Autowired
    private ConfigurationService configurationService;

    private final HttpClient httpClient;
    private final Map<String, AuthorityBinding> authorAuthorityCache = new ConcurrentHashMap<>();
    private final Map<String, AuthorityBinding> orgUnitAuthorityCache = new ConcurrentHashMap<>();

    public K10plusEnrichedDataProvider() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public void init() throws Exception {
        // Stateless HTTP client initialization
    }

    @Override
    public String getImportSource() {
        return importSourceName;
    }

    @Override
    public int getRecordsCount(String query) throws MetadataSourceException {
        if (StringUtils.isBlank(query)) {
            return 0;
        }
        String xmlResponse = executeSruSearch(query, 1, 1);
        return marcXmlParser.extractNumberOfRecords(xmlResponse);
    }

    @Override
    public int getRecordsCount(Query query) throws MetadataSourceException {
        return query == null ? 0 : getRecordsCount(query.toString());
    }

    @Override
    public ImportRecord getRecord(String id) throws MetadataSourceException {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        String cqlQuery = "pica.ppn=\"" + escapeCql(id.trim()) + "\"";
        String xmlResponse = executeSruSearchWithRawCql(cqlQuery, 1, 1);
        List<K10plusRecordDTO> parsed = marcXmlParser.parseRecords(xmlResponse);
        return parsed.isEmpty() ? null : buildEnrichedImportRecord(parsed.get(0));
    }

    @Override
    public ImportRecord getRecord(Query query) throws MetadataSourceException {
        if (query == null) {
            return null;
        }
        Collection<ImportRecord> records = getRecords(query.toString(), 0, 1);
        return records.isEmpty() ? null : records.iterator().next();
    }

    @Override
    public Collection<ImportRecord> getRecords(String query, int start, int count) throws MetadataSourceException {
        if (StringUtils.isBlank(query)) {
            return Collections.emptyList();
        }
        int sruStartRecord = Math.max(1, start + 1);
        int maxRecords = Math.max(1, count);

        String xmlResponse = executeSruSearch(query, sruStartRecord, maxRecords);
        List<K10plusRecordDTO> k10Records = marcXmlParser.parseRecords(xmlResponse);

        List<ImportRecord> importRecords = new ArrayList<>(k10Records.size());
        for (K10plusRecordDTO k10Dto : k10Records) {
            importRecords.add(buildEnrichedImportRecord(k10Dto));
        }
        return importRecords;
    }

    @Override
    public Collection<ImportRecord> getRecords(Query query) throws MetadataSourceException {
        return query == null ? Collections.emptyList() : getRecords(query.toString(), 0, 10);
    }

    @Override
    public Collection<ImportRecord> findMatchingRecords(Query query) throws MetadataSourceException {
        return getRecords(query);
    }

    @Override
    public Collection<ImportRecord> findMatchingRecords(Item item) throws MetadataSourceException {
        if (item == null || StringUtils.isBlank(item.getName())) {
            return Collections.emptyList();
        }
        return getRecords(item.getName(), 0, 5);
    }

    public AuthorityBinding getCachedAuthorBinding(String authorName) {
        return authorName == null ? null : authorAuthorityCache.get(authorName);
    }

    public AuthorityBinding getCachedOrgUnitBinding(String orgUnitName) {
        return orgUnitName == null ? null : orgUnitAuthorityCache.get(orgUnitName);
    }

    private String executeSruSearch(String userQuery, int startRecord, int maximumRecords) throws MetadataSourceException {
        String cqlQuery = buildCqlQuery(userQuery);
        return executeSruSearchWithRawCql(cqlQuery, startRecord, maximumRecords);
    }

    private String executeSruSearchWithRawCql(String cqlQuery, int startRecord, int maximumRecords) throws MetadataSourceException {
        try {
            String effectiveUrl = getCfgString("sru.url", sruBaseUrl);
            int effectiveTimeout = getCfgInt("sru.timeout", timeoutSeconds);
            String encodedCql = URLEncoder.encode(cqlQuery, StandardCharsets.UTF_8);
            String requestUrl = String.format(
                    "%s?version=1.1&operation=searchRetrieve&recordSchema=marcxml&startRecord=%d&maximumRecords=%d&query=%s",
                    effectiveUrl,
                    startRecord,
                    maximumRecords,
                    encodedCql
            );

            log.debug("K10plus SRU request: {}", requestUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .timeout(Duration.ofSeconds(effectiveTimeout))
                    .header("Accept", "application/xml, text/xml")
                    .header("User-Agent", "DSpace-CRIS-9-K10plus-Provider/1.1")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new MetadataSourceException("K10plus SRU HTTP status: " + response.statusCode());
            }
            return response.body();
        } catch (MetadataSourceException e) {
            throw e;
        } catch (Exception e) {
            throw new MetadataSourceException("Error connecting to K10plus SRU API: " + e.getMessage(), e);
        }
    }

    public String buildCqlQuery(String rawQuery) {
        String trimmed = rawQuery.trim();
        if (trimmed.contains("pica.")) {
            return trimmed;
        }
        String normalizedDoi = trimmed.replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", "");
        if (DOI_PATTERN.matcher(normalizedDoi).matches()) {
            return "pica.doi=\"" + escapeCql(normalizedDoi) + "\"";
        }
        if (ISBN_PATTERN.matcher(trimmed).matches()) {
            String isbnWithoutHyphens = trimmed.replaceAll("[-\\s]", "");
            return "pica.isb=\"" + escapeCql(isbnWithoutHyphens) + "\"";
        }
        if (PPN_PATTERN.matcher(trimmed).matches()) {
            return "pica.ppn=\"" + escapeCql(trimmed) + "\"";
        }
        String escaped = escapeCql(trimmed);
        String template = getCfgString("sru.cql.template", "(pica.tit=\"%s\" or pica.per=\"%s\")");
        int placeholders = StringUtils.countMatches(template, "%s");
        if (placeholders == 1) {
            return String.format(template, escaped);
        } else if (placeholders >= 2) {
            return String.format(template, escaped, escaped);
        }
        return String.format("(pica.tit=\"%s\" or pica.per=\"%s\")", escaped, escaped);
    }

    private ImportRecord buildEnrichedImportRecord(K10plusRecordDTO k10Dto) {
        OpenAlexEnrichmentDTO openAlexDto = null;
        if (openAlexEnrichmentClient != null) {
            openAlexDto = openAlexEnrichmentClient.enrichRecord(k10Dto);
        }

        List<MetadatumDTO> metadataList = new ArrayList<>();

        addMappedMetadata(metadataList, "ppn", k10Dto.getPpn(), "dc.identifier.other", "cris.sourceId");
        addMappedMetadata(metadataList, "title", k10Dto.getTitle(), "dc.title");
        addMappedMetadata(metadataList, "subTitle", k10Dto.getSubTitle(), "dc.title.subtitle");

        mergeAuthorIdentifiers(k10Dto, openAlexDto);

        Context context = null;
        try {
            context = new Context();
            context.turnOffAuthorisationSystem();

            for (AuthorEntry author : k10Dto.getAuthors()) {
                addMappedMetadata(metadataList, "author", author.getFullName(), "dc.contributor.author");

                String matchedPersonUuid = resolveExistingPersonUuid(context, author.getGndId(), author.getOrcid());
                if (matchedPersonUuid != null) {
                    authorAuthorityCache.put(author.getFullName(), new AuthorityBinding(matchedPersonUuid, Choices.CF_ACCEPTED));
                } else if (StringUtils.isNotBlank(author.getGndId())) {
                    authorAuthorityCache.put(author.getFullName(), new AuthorityBinding(author.getGndId(), Choices.CF_UNCERTAIN));
                }

                addMappedMetadata(metadataList, "authorOrcid", author.getOrcid(), "cris.author.orcid");
            }

            addMappedMetadata(metadataList, "publisher", k10Dto.getPublisher(), "dc.publisher");
            addMappedMetadata(metadataList, "publicationPlace", k10Dto.getPublicationPlace(), "veroPU.bibliographicCitation.publisherPlace");
            addMappedMetadata(metadataList, "issuedYear", k10Dto.getIssuedYear(), "dc.date.issued");

            for (String isbn : k10Dto.getIsbns()) {
                addMappedMetadata(metadataList, "isbn", isbn, "dc.identifier.isbn");
            }
            for (String issn : k10Dto.getIssns()) {
                addMappedMetadata(metadataList, "issn", issn, "dc.identifier.issn");
            }

            String effectiveDoi = StringUtils.isNotBlank(k10Dto.getDoi())
                    ? k10Dto.getDoi()
                    : (openAlexDto != null ? openAlexDto.getResolvedDoi() : null);
            addMappedMetadata(metadataList, "doi", effectiveDoi, "dc.identifier.doi");

            for (String lang : k10Dto.getLanguages()) {
                addMappedMetadata(metadataList, "language", lang, "dc.language.iso");
            }
            addMappedMetadata(metadataList, "extent", k10Dto.getExtent(), "dc.format.extent");

            String effectiveAbstract = StringUtils.isNotBlank(k10Dto.getAbstractText())
                    ? k10Dto.getAbstractText()
                    : (openAlexDto != null ? openAlexDto.getReconstructedAbstract() : null);
            addMappedMetadata(metadataList, "abstract", effectiveAbstract, "dc.description.abstract");

            for (String subject : k10Dto.getSubjects()) {
                addMappedMetadata(metadataList, "subject", subject, "dc.subject");
            }

            addMappedMetadata(metadataList, "journalTitle", k10Dto.getJournalTitle(), "dc.relation.ispartof");

            Set<String> allUris = new LinkedHashSet<>(k10Dto.getUris());
            if (openAlexDto != null && StringUtils.isNotBlank(openAlexDto.getOaPdfUrl())) {
                allUris.add(openAlexDto.getOaPdfUrl());
            }
            for (String uri : allUris) {
                addMappedMetadata(metadataList, "uri", uri, "dc.identifier.uri");
            }

            if (openAlexDto != null) {
                boolean orgUnitLinked = false;
                for (String institutionName : openAlexDto.getInstitutions()) {
                    addMappedMetadata(metadataList, "affiliation", institutionName, "oairecerif.author.affiliation");

                    if (!orgUnitLinked) {
                        String matchedOrgUnitUuid = resolveExistingOrgUnitUuid(context, institutionName);
                        if (matchedOrgUnitUuid != null) {
                            addMappedMetadata(metadataList, "orgUnit", institutionName, "vero.OrgUnit");
                            orgUnitAuthorityCache.put(institutionName, new AuthorityBinding(matchedOrgUnitUuid, Choices.CF_ACCEPTED));
                            orgUnitLinked = true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error building ImportRecord for PPN {}: {}", k10Dto.getPpn(), e.getMessage(), e);
        } finally {
            if (context != null) {
                context.restoreAuthSystemState();
                if (context.isValid()) {
                    context.abort();
                }
            }
        }

        return new ImportRecord(metadataList);
    }

    private void addMappedMetadata(List<MetadatumDTO> metadataList, String logicalKey, String value, String... defaultFields) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        for (String targetField : resolveTargetFields(logicalKey, defaultFields)) {
            String[] parts = StringUtils.split(targetField, ".", 3);
            if (parts.length >= 2) {
                metadataList.add(createMetadatum(parts[0].trim(), parts[1].trim(), parts.length == 3 ? parts[2].trim() : null, value));
            }
        }
    }

    private List<String> resolveTargetFields(String logicalKey, String... defaultFields) {
        if (configurationService != null) {
            String[] configured = configurationService.getArrayProperty(CFG_PREFIX + "map." + logicalKey);
            if (configured != null && configured.length > 0) {
                List<String> fields = new ArrayList<>(configured.length);
                for (String entry : configured) {
                    String trimmed = StringUtils.trimToNull(entry);
                    if (trimmed != null && !"-".equals(trimmed) && !"none".equalsIgnoreCase(trimmed)) {
                        fields.add(trimmed);
                    }
                }
                return fields;
            }
        }
        return Arrays.asList(defaultFields);
    }

    private String getCfgString(String key, String fallback) {
        if (configurationService != null) {
            String val = configurationService.getProperty(CFG_PREFIX + key);
            if (StringUtils.isNotBlank(val)) {
                return val.trim();
            }
        }
        return fallback;
    }

    private int getCfgInt(String key, int fallback) {
        if (configurationService != null) {
            return configurationService.getIntProperty(CFG_PREFIX + key, fallback);
        }
        return fallback;
    }

    private void mergeAuthorIdentifiers(K10plusRecordDTO k10Dto, OpenAlexEnrichmentDTO openAlexDto) {
        if (openAlexDto == null || openAlexDto.getAuthors().isEmpty() || k10Dto.getAuthors().isEmpty()) {
            return;
        }
        for (AuthorEntry k10Author : k10Dto.getAuthors()) {
            String k10Surname = openAlexEnrichmentClient.extractNormalizedSurname(k10Author.getFullName());
            if (StringUtils.isBlank(k10Surname)) {
                continue;
            }
            for (EnrichedAuthor oaAuthor : openAlexDto.getAuthors()) {
                if (StringUtils.isBlank(oaAuthor.getOrcid())) {
                    continue;
                }
                String oaSurname = openAlexEnrichmentClient.extractNormalizedSurname(oaAuthor.getDisplayName());
                if (k10Surname.equals(oaSurname)) {
                    k10Author.setOrcid(oaAuthor.getOrcid());
                    break;
                }
            }
        }
    }

    private String resolveExistingPersonUuid(Context context, String gndId, String orcid) {
        if (searchService == null || (StringUtils.isBlank(gndId) && StringUtils.isBlank(orcid))) {
            return null;
        }
        try {
            String gndField = getCfgString("authority.person.gnd-field", "veroPE.identifier.gnd");
            String orcidField = getCfgString("authority.person.orcid-field", "person.identifier.orcid");
            List<String> clauses = new ArrayList<>();
            if (StringUtils.isNotBlank(gndId)) {
                clauses.add(gndField + ":\"" + escapeSolr(gndId) + "\"");
            }
            if (StringUtils.isNotBlank(orcid)) {
                clauses.add(orcidField + ":\"" + escapeSolr(orcid) + "\"");
            }
            DiscoverQuery query = new DiscoverQuery();
            query.setQuery(String.join(" OR ", clauses));
            query.addFilterQueries("search.resourcetype:" + IndexableItem.TYPE);
            query.addFilterQueries("dspace.entity.type:Person");
            query.setMaxResults(1);

            DiscoverResult result = searchService.search(context, query);
            for (IndexableObject obj : result.getIndexableObjects()) {
                if (obj.getIndexedObject() instanceof Item) {
                    return ((Item) obj.getIndexedObject()).getID().toString();
                }
            }
        } catch (Exception e) {
            log.debug("Solr search for CRIS Person failed: {}", e.getMessage());
        }
        return null;
    }

    private String resolveExistingOrgUnitUuid(Context context, String institutionName) {
        if (searchService == null || StringUtils.isBlank(institutionName)) {
            return null;
        }
        try {
            String orgTitleField = getCfgString("authority.orgunit.title-field", "dc.title");
            DiscoverQuery query = new DiscoverQuery();
            query.setQuery(orgTitleField + ":\"" + escapeSolr(institutionName) + "\"");
            query.addFilterQueries("search.resourcetype:" + IndexableItem.TYPE);
            query.addFilterQueries("dspace.entity.type:OrganizationalUnit");
            query.setMaxResults(1);

            DiscoverResult result = searchService.search(context, query);
            for (IndexableObject obj : result.getIndexableObjects()) {
                if (obj.getIndexedObject() instanceof Item) {
                    return ((Item) obj.getIndexedObject()).getID().toString();
                }
            }
        } catch (Exception e) {
            log.debug("Solr search for CRIS OrganizationalUnit failed: {}", e.getMessage());
        }
        return null;
    }

    private MetadatumDTO createMetadatum(String schema, String element, String qualifier, String value) {
        MetadatumDTO dto = new MetadatumDTO();
        dto.setSchema(schema);
        dto.setElement(element);
        dto.setQualifier(qualifier);
        dto.setValue(value);
        return dto;
    }

    private String escapeCql(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String escapeSolr(String value) {
        return value.replaceAll("([+\\-!(){}\\[\\]^\"~*?:\\\\/])", "\\\\$1");
    }

    public String getSruBaseUrl() { return sruBaseUrl; }
    public void setSruBaseUrl(String sruBaseUrl) { this.sruBaseUrl = sruBaseUrl; }
    public String getImportSourceName() { return importSourceName; }
    public void setImportSourceName(String importSourceName) { this.importSourceName = importSourceName; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public MarcXmlParser getMarcXmlParser() { return marcXmlParser; }
    public void setMarcXmlParser(MarcXmlParser marcXmlParser) { this.marcXmlParser = marcXmlParser; }
    public OpenAlexEnrichmentClient getOpenAlexEnrichmentClient() { return openAlexEnrichmentClient; }
    public void setOpenAlexEnrichmentClient(OpenAlexEnrichmentClient openAlexEnrichmentClient) { this.openAlexEnrichmentClient = openAlexEnrichmentClient; }
}
