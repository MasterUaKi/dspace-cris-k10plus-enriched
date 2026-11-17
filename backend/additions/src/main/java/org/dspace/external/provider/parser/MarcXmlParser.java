/**
 * Version: 1.0.2 | Release Date: 2026-11-17
 */
package org.dspace.external.provider.parser;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dspace.external.provider.dto.K10plusRecordDTO;
import org.dspace.external.provider.dto.K10plusRecordDTO.AuthorEntry;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class MarcXmlParser {

    private static final Logger log = LogManager.getLogger(MarcXmlParser.class);
    private static final String MARC21_NS = "http://www.loc.gov/MARC21/slim";
    private static final String SRW_NS = "http://www.loc.gov/zing/srw/";
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(1[5-9]\\d{2}|20\\d{2})\\b");
    private static final Pattern GND_URL_PATTERN = Pattern.compile("d-nb\\.info/gnd/([0-9X-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GND_PREFIX_PATTERN = Pattern.compile("^\\(DE-588\\)([0-9X-]+)$", Pattern.CASE_INSENSITIVE);

    public int extractNumberOfRecords(String xmlContent) {
        if (StringUtils.isBlank(xmlContent)) {
            return 0;
        }
        try {
            Document doc = parseDocument(xmlContent);
            NodeList nodes = doc.getElementsByTagNameNS("*", "numberOfRecords");
            if (nodes.getLength() > 0) {
                return Integer.parseInt(nodes.item(0).getTextContent().trim());
            }
        } catch (Exception e) {
            log.error("Error extracting numberOfRecords from SRU XML: {}", e.getMessage(), e);
        }
        return 0;
    }

    public List<K10plusRecordDTO> parseRecords(String xmlContent) {
        List<K10plusRecordDTO> results = new ArrayList<>();
        if (StringUtils.isBlank(xmlContent)) {
            return results;
        }
        try {
            Document doc = parseDocument(xmlContent);
            NodeList recordNodes = doc.getElementsByTagNameNS(MARC21_NS, "record");
            if (recordNodes.getLength() == 0) {
                recordNodes = doc.getElementsByTagNameNS("*", "record");
            }
            Set<String> seenPpns = new HashSet<>();
            for (int i = 0; i < recordNodes.getLength(); i++) {
                Node node = recordNodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE && !SRW_NS.equals(node.getNamespaceURI())) {
                    K10plusRecordDTO dto = parseSingleMarcRecord((Element) node);
                    if (StringUtils.isNotBlank(dto.getTitle()) || StringUtils.isNotBlank(dto.getPpn())) {
                        String ppnKey = StringUtils.trimToNull(dto.getPpn());
                        if (ppnKey == null || seenPpns.add(ppnKey)) {
                            results.add(dto);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error parsing MARCXML from K10plus: {}", e.getMessage(), e);
        }
        return results;
    }

    private K10plusRecordDTO parseSingleMarcRecord(Element recordElement) {
        K10plusRecordDTO dto = new K10plusRecordDTO();
        NodeList controlFields = recordElement.getElementsByTagNameNS("*", "controlfield");
        for (int i = 0; i < controlFields.getLength(); i++) {
            Element cf = (Element) controlFields.item(i);
            if ("001".equals(cf.getAttribute("tag"))) {
                dto.setPpn(StringUtils.trimToNull(cf.getTextContent()));
            }
        }

        NodeList dataFields = recordElement.getElementsByTagNameNS("*", "datafield");
        boolean has264Publication = false;

        for (int i = 0; i < dataFields.getLength(); i++) {
            Element df = (Element) dataFields.item(i);
            String tag = df.getAttribute("tag");
            String ind1 = df.getAttribute("ind1");
            String ind2 = df.getAttribute("ind2");

            switch (tag) {
                case "245":
                    parseTitle245(df, dto);
                    break;
                case "100":
                case "700":
                    parseAuthor(df, dto);
                    break;
                case "264":
                    if ("1".equals(ind2) || !has264Publication) {
                        parsePublicationInfo(df, dto);
                        if ("1".equals(ind2)) {
                            has264Publication = true;
                        }
                    }
                    break;
                case "260":
                    if (!has264Publication && StringUtils.isBlank(dto.getPublisher()) && StringUtils.isBlank(dto.getIssuedYear())) {
                        parsePublicationInfo(df, dto);
                    }
                    break;
                case "020":
                    String isbn = getFirstSubfield(df, "a");
                    if (StringUtils.isNotBlank(isbn)) {
                        dto.getIsbns().add(isbn.split("\\s+")[0].trim());
                    }
                    break;
                case "022":
                    String issn = getFirstSubfield(df, "a");
                    if (StringUtils.isNotBlank(issn)) {
                        dto.getIssns().add(issn.split("\\s+")[0].trim());
                    }
                    break;
                case "024":
                    parseIdentifier024(df, ind1, dto);
                    break;
                case "041":
                    for (String lang : getSubfields(df, "a")) {
                        if (StringUtils.isNotBlank(lang)) {
                            dto.getLanguages().add(lang.trim().toLowerCase());
                        }
                    }
                    break;
                case "300":
                    String extent = cleanTrailingPunctuation(getFirstSubfield(df, "a"));
                    if (StringUtils.isNotBlank(extent)) {
                        dto.setExtent(extent);
                    }
                    break;
                case "520":
                    String abstractVal = getFirstSubfield(df, "a");
                    if (StringUtils.isNotBlank(abstractVal)) {
                        dto.setAbstractText(StringUtils.isBlank(dto.getAbstractText())
                                ? abstractVal.trim() : dto.getAbstractText() + "\n" + abstractVal.trim());
                    }
                    break;
                case "650":
                case "653":
                    for (String subj : getSubfields(df, "a")) {
                        String cleanSubj = cleanTrailingPunctuation(subj);
                        if (StringUtils.isNotBlank(cleanSubj)) {
                            dto.getSubjects().add(cleanSubj);
                        }
                    }
                    break;
                case "773":
                    String hostTitle = getFirstSubfield(df, "t");
                    if (StringUtils.isBlank(hostTitle)) {
                        hostTitle = getFirstSubfield(df, "a");
                    }
                    if (StringUtils.isNotBlank(hostTitle)) {
                        dto.setJournalTitle(cleanTrailingPunctuation(hostTitle));
                    }
                    break;
                case "856":
                    for (String uri : getSubfields(df, "u")) {
                        if (StringUtils.isNotBlank(uri)) {
                            dto.getUris().add(uri.trim());
                        }
                    }
                    break;
                default:
                    break;
            }
        }
        return dto;
    }

    private void parseTitle245(Element df, K10plusRecordDTO dto) {
        String mainTitle = cleanTrailingPunctuation(getFirstSubfield(df, "a"));
        String subTitle = cleanTrailingPunctuation(getFirstSubfield(df, "b"));
        if (StringUtils.isNotBlank(mainTitle)) {
            dto.setTitle(mainTitle);
        }
        if (StringUtils.isNotBlank(subTitle)) {
            dto.setSubTitle(subTitle);
        }
    }

    private void parseAuthor(Element df, K10plusRecordDTO dto) {
        String name = cleanTrailingPunctuation(getFirstSubfield(df, "a"));
        if (StringUtils.isBlank(name)) {
            return;
        }
        String normalizedGnd = null;
        for (String sub0 : getSubfields(df, "0")) {
            String candidate = extractNormalizedGnd(sub0);
            if (candidate != null) {
                normalizedGnd = candidate;
                break;
            }
        }
        dto.addAuthor(new AuthorEntry(name, normalizedGnd));
    }

    private void parsePublicationInfo(Element df, K10plusRecordDTO dto) {
        String place = cleanTrailingPunctuation(getFirstSubfield(df, "a"));
        String publisher = cleanTrailingPunctuation(getFirstSubfield(df, "b"));
        String rawDate = getFirstSubfield(df, "c");
        if (StringUtils.isNotBlank(place)) {
            dto.setPublicationPlace(place);
        }
        if (StringUtils.isNotBlank(publisher)) {
            dto.setPublisher(publisher);
        }
        if (StringUtils.isNotBlank(rawDate)) {
            Matcher matcher = YEAR_PATTERN.matcher(rawDate);
            dto.setIssuedYear(matcher.find() ? matcher.group(1) : cleanTrailingPunctuation(rawDate));
        }
    }

    private void parseIdentifier024(Element df, String ind1, K10plusRecordDTO dto) {
        String sourceType = getFirstSubfield(df, "2");
        String value = getFirstSubfield(df, "a");
        if ("7".equals(ind1) && "doi".equalsIgnoreCase(StringUtils.trimToEmpty(sourceType)) && StringUtils.isNotBlank(value)) {
            String cleanDoi = value.trim()
                    .replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", "")
                    .replaceFirst("(?i)^doi:\\s*", "");
            dto.setDoi(cleanDoi);
        }
    }

    public String extractNormalizedGnd(String rawValue) {
        if (StringUtils.isBlank(rawValue)) {
            return null;
        }
        String trimmed = rawValue.trim();
        Matcher prefixMatcher = GND_PREFIX_PATTERN.matcher(trimmed);
        if (prefixMatcher.matches()) {
            return "(DE-588)" + prefixMatcher.group(1).toUpperCase();
        }
        Matcher urlMatcher = GND_URL_PATTERN.matcher(trimmed);
        if (urlMatcher.find()) {
            return "(DE-588)" + urlMatcher.group(1).toUpperCase();
        }
        return null;
    }

    private String getFirstSubfield(Element datafield, String code) {
        List<String> values = getSubfields(datafield, code);
        return values.isEmpty() ? null : values.get(0);
    }

    private List<String> getSubfields(Element datafield, String code) {
        List<String> values = new ArrayList<>();
        NodeList subfields = datafield.getElementsByTagNameNS("*", "subfield");
        for (int i = 0; i < subfields.getLength(); i++) {
            Element sf = (Element) subfields.item(i);
            if (code.equals(sf.getAttribute("code"))) {
                String text = sf.getTextContent();
                if (StringUtils.isNotBlank(text)) {
                    values.add(text.trim());
                }
            }
        }
        return values;
    }

    private String cleanTrailingPunctuation(String input) {
        if (input == null) {
            return null;
        }
        return input.replaceAll("\\s*[:;/,\\.]\\s*$", "").trim();
    }

    private Document parseDocument(String xmlContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));
    }
}
