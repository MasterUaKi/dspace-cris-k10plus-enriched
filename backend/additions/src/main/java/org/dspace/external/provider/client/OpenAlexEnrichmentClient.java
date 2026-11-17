/**
 * Version: 1.0.1 | Release Date: 2026-11-16
 */
package org.dspace.external.provider.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.external.provider.dto.K10plusRecordDTO;
import org.dspace.external.provider.dto.OpenAlexEnrichmentDTO;
import org.dspace.external.provider.dto.OpenAlexEnrichmentDTO.EnrichedAuthor;

public class OpenAlexEnrichmentClient {

    private static final Logger log = LogManager.getLogger(OpenAlexEnrichmentClient.class);

    private String baseUrl = "https://api.openalex.org/works";
    private String politePoolEmail = "oleh.riabtsev@uni-vechta.de";
    private int timeoutSeconds = 8;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAlexEnrichmentClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public OpenAlexEnrichmentDTO enrichRecord(K10plusRecordDTO k10Record) {
        if (k10Record == null) {
            return null;
        }
        try {
            JsonNode matchedWork = null;
            if (StringUtils.isNotBlank(k10Record.getDoi())) {
                matchedWork = fetchWorkByDoi(k10Record.getDoi());
            }
            if (matchedWork == null && StringUtils.isNotBlank(k10Record.getTitle())) {
                matchedWork = fetchAndValidateWorkByTitle(k10Record);
            }
            if (matchedWork == null || matchedWork.isMissingNode()) {
                return null;
            }
            return mapJsonToEnrichmentDto(matchedWork);
        } catch (Exception e) {
            log.warn("OpenAlex API error for PPN {}: {}", k10Record.getPpn(), e.getMessage());
            return null;
        }
    }

    private JsonNode fetchWorkByDoi(String rawDoi) throws Exception {
        String cleanDoi = rawDoi.trim().replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", "");
        String encodedDoi = URLEncoder.encode(cleanDoi, StandardCharsets.UTF_8).replace("%2F", "/");
        String endpoint = normalizeWorksEndpoint(baseUrl);
        String requestUrl = buildUrlWithMailto(endpoint + "/https://doi.org/" + encodedDoi, false);
        String responseBody = executeGet(requestUrl);
        if (responseBody == null) {
            return null;
        }
        JsonNode root = objectMapper.readTree(responseBody);
        return root.has("id") ? root : null;
    }

    private JsonNode fetchAndValidateWorkByTitle(K10plusRecordDTO k10Record) throws Exception {
        if (StringUtils.isBlank(k10Record.getIssuedYear()) || k10Record.getAuthors().isEmpty()) {
            return null;
        }
        Integer expectedYear = parseYearSafe(k10Record.getIssuedYear());
        String expectedFirstAuthorSurname = extractNormalizedSurname(k10Record.getAuthors().get(0).getFullName());
        if (expectedYear == null || StringUtils.isBlank(expectedFirstAuthorSurname)) {
            return null;
        }
        String fullTitleQuery = StringUtils.isNotBlank(k10Record.getSubTitle())
                ? k10Record.getTitle() + " " + k10Record.getSubTitle()
                : k10Record.getTitle();
        String encodedTitle = URLEncoder.encode(fullTitleQuery, StandardCharsets.UTF_8);
        String endpoint = normalizeWorksEndpoint(baseUrl);
        String requestUrl = buildUrlWithMailto(endpoint + "?search=" + encodedTitle + "&per-page=3", true);
        String responseBody = executeGet(requestUrl);
        if (responseBody == null) {
            return null;
        }
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode results = root.path("results");
        if (!results.isArray()) {
            return null;
        }
        for (JsonNode candidate : results) {
            if (isValidMatch(candidate, expectedYear, expectedFirstAuthorSurname)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isValidMatch(JsonNode candidate, int expectedYear, String expectedFirstAuthorSurname) {
        int candidateYear = candidate.path("publication_year").asInt(-1);
        if (candidateYear != expectedYear) {
            return false;
        }
        JsonNode authorships = candidate.path("authorships");
        if (!authorships.isArray() || authorships.isEmpty()) {
            return false;
        }
        String candidateFirstAuthorName = authorships.get(0).path("author").path("display_name").asText("");
        String candidateSurname = extractNormalizedSurname(candidateFirstAuthorName);
        return StringUtils.isNotBlank(candidateSurname) && candidateSurname.equals(expectedFirstAuthorSurname);
    }

    private OpenAlexEnrichmentDTO mapJsonToEnrichmentDto(JsonNode workNode) {
        OpenAlexEnrichmentDTO dto = new OpenAlexEnrichmentDTO();
        dto.setOpenAlexId(StringUtils.trimToNull(workNode.path("id").asText(null)));
        String doiUrl = workNode.path("doi").asText(null);
        if (StringUtils.isNotBlank(doiUrl)) {
            dto.setResolvedDoi(doiUrl.replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", ""));
        }
        JsonNode invertedIndexNode = workNode.path("abstract_inverted_index");
        if (invertedIndexNode.isObject() && !invertedIndexNode.isEmpty()) {
            dto.setReconstructedAbstract(reconstructAbstractFromInvertedIndex(invertedIndexNode));
        }
        JsonNode authorships = workNode.path("authorships");
        if (authorships.isArray()) {
            for (JsonNode authorship : authorships) {
                JsonNode authorNode = authorship.path("author");
                String displayName = StringUtils.trimToNull(authorNode.path("display_name").asText(null));
                String rawOrcid = StringUtils.trimToNull(authorNode.path("orcid").asText(null));
                String cleanOrcid = rawOrcid != null
                        ? rawOrcid.replaceFirst("(?i)^https?://orcid\\.org/", "")
                        : null;
                if (displayName != null) {
                    dto.getAuthors().add(new EnrichedAuthor(displayName, cleanOrcid));
                }
                JsonNode institutions = authorship.path("institutions");
                if (institutions.isArray()) {
                    for (JsonNode inst : institutions) {
                        String instName = StringUtils.trimToNull(inst.path("display_name").asText(null));
                        if (instName != null) {
                            dto.getInstitutions().add(instName);
                        }
                    }
                }
            }
        }
        String oaUrl = StringUtils.trimToNull(workNode.path("open_access").path("oa_url").asText(null));
        if (oaUrl == null) {
            oaUrl = StringUtils.trimToNull(workNode.path("best_oa_location").path("pdf_url").asText(null));
        }
        dto.setOaPdfUrl(oaUrl);
        return dto;
    }

    public String reconstructAbstractFromInvertedIndex(JsonNode invertedIndexNode) {
        if (invertedIndexNode == null || !invertedIndexNode.isObject()) {
            return null;
        }
        TreeMap<Integer, String> orderedWords = new TreeMap<>();
        Iterator<String> fieldNames = invertedIndexNode.fieldNames();
        while (fieldNames.hasNext()) {
            String word = fieldNames.next();
            JsonNode positions = invertedIndexNode.get(word);
            if (positions != null && positions.isArray()) {
                for (JsonNode posNode : positions) {
                    if (posNode.isInt()) {
                        orderedWords.put(posNode.asInt(), word);
                    }
                }
            }
        }
        return orderedWords.isEmpty() ? null : String.join(" ", orderedWords.values()).trim();
    }

    public String extractNormalizedSurname(String fullName) {
        if (StringUtils.isBlank(fullName)) {
            return null;
        }
        String rawSurname;
        if (fullName.contains(",")) {
            rawSurname = fullName.split(",", 2)[0].trim();
        } else {
            String[] parts = fullName.trim().split("\\s+");
            rawSurname = parts[parts.length - 1];
        }
        String normalized = Normalizer.normalize(rawSurname, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^a-zA-Z0-9-]", "")
                .toLowerCase();
        return StringUtils.trimToNull(normalized);
    }

    private String normalizeWorksEndpoint(String url) {
        String trimmed = StringUtils.trimToEmpty(url).replaceAll("/+$", "");
        if (trimmed.equals("https://openalex.org") || trimmed.equals("https://api.openalex.org")) {
            return "https://api.openalex.org/works";
        }
        return trimmed;
    }

    private Integer parseYearSafe(String yearStr) {
        try {
            return Integer.parseInt(yearStr.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String buildUrlWithMailto(String url, boolean hasQueryParams) {
        if (StringUtils.isBlank(politePoolEmail)) {
            return url;
        }
        return url + (hasQueryParams ? "&" : "?") + "mailto=" + URLEncoder.encode(politePoolEmail, StandardCharsets.UTF_8);
    }

    private String executeGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Accept", "application/json")
                .header("User-Agent", "DSpace-CRIS-9-K10plus-Importer/1.0")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response.statusCode() == 200 ? response.body() : null;
    }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getPolitePoolEmail() { return politePoolEmail; }
    public void setPolitePoolEmail(String politePoolEmail) { this.politePoolEmail = politePoolEmail; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
