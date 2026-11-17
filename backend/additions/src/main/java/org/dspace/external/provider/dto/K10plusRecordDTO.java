/**
 * Version: 1.0.1 | Release Date: 2026-11-16
 */
package org.dspace.external.provider.dto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class K10plusRecordDTO {

    public static class AuthorEntry {
        private String fullName;
        private String gndId;
        private String orcid;

        public AuthorEntry() {}

        public AuthorEntry(String fullName, String gndId) {
            this.fullName = fullName;
            this.gndId = gndId;
        }

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public String getGndId() { return gndId; }
        public void setGndId(String gndId) { this.gndId = gndId; }
        public String getOrcid() { return orcid; }
        public void setOrcid(String orcid) { this.orcid = orcid; }
    }

    private String ppn;
    private String title;
    private String subTitle;
    private final List<AuthorEntry> authors = new ArrayList<>();
    private String publicationPlace;
    private String publisher;
    private String issuedYear;
    private final Set<String> isbns = new LinkedHashSet<>();
    private final Set<String> issns = new LinkedHashSet<>();
    private String doi;
    private final Set<String> languages = new LinkedHashSet<>();
    private String extent;
    private String abstractText;
    private final Set<String> subjects = new LinkedHashSet<>();
    private String journalTitle;
    private final Set<String> uris = new LinkedHashSet<>();

    public String getPpn() { return ppn; }
    public void setPpn(String ppn) { this.ppn = ppn; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSubTitle() { return subTitle; }
    public void setSubTitle(String subTitle) { this.subTitle = subTitle; }
    public List<AuthorEntry> getAuthors() { return authors; }

    public void addAuthor(AuthorEntry author) {
        if (author != null && author.getFullName() != null && !author.getFullName().isBlank()) {
            this.authors.add(author);
        }
    }

    public String getPublicationPlace() { return publicationPlace; }
    public void setPublicationPlace(String publicationPlace) { this.publicationPlace = publicationPlace; }
    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public String getIssuedYear() { return issuedYear; }
    public void setIssuedYear(String issuedYear) { this.issuedYear = issuedYear; }
    public Set<String> getIsbns() { return isbns; }
    public Set<String> getIssns() { return issns; }
    public String getDoi() { return doi; }
    public void setDoi(String doi) { this.doi = doi; }
    public Set<String> getLanguages() { return languages; }
    public String getExtent() { return extent; }
    public void setExtent(String extent) { this.extent = extent; }
    public String getAbstractText() { return abstractText; }
    public void setAbstractText(String abstractText) { this.abstractText = abstractText; }
    public Set<String> getSubjects() { return subjects; }
    public String getJournalTitle() { return journalTitle; }
    public void setJournalTitle(String journalTitle) { this.journalTitle = journalTitle; }
    public Set<String> getUris() { return uris; }
}
