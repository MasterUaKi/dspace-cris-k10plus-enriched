/**
 * Version: 1.0.1 | Release Date: 2026-11-16
 */
package org.dspace.external.provider.dto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class OpenAlexEnrichmentDTO {

    public static class EnrichedAuthor {
        private String displayName;
        private String orcid;

        public EnrichedAuthor() {}

        public EnrichedAuthor(String displayName, String orcid) {
            this.displayName = displayName;
            this.orcid = orcid;
        }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getOrcid() { return orcid; }
        public void setOrcid(String orcid) { this.orcid = orcid; }
    }

    private String openAlexId;
    private String resolvedDoi;
    private String reconstructedAbstract;
    private final List<EnrichedAuthor> authors = new ArrayList<>();
    private final Set<String> institutions = new LinkedHashSet<>();
    private String oaPdfUrl;

    public String getOpenAlexId() { return openAlexId; }
    public void setOpenAlexId(String openAlexId) { this.openAlexId = openAlexId; }
    public String getResolvedDoi() { return resolvedDoi; }
    public void setResolvedDoi(String resolvedDoi) { this.resolvedDoi = resolvedDoi; }
    public String getReconstructedAbstract() { return reconstructedAbstract; }
    public void setReconstructedAbstract(String reconstructedAbstract) { this.reconstructedAbstract = reconstructedAbstract; }
    public List<EnrichedAuthor> getAuthors() { return authors; }
    public Set<String> getInstitutions() { return institutions; }
    public String getOaPdfUrl() { return oaPdfUrl; }
    public void setOaPdfUrl(String oaPdfUrl) { this.oaPdfUrl = oaPdfUrl; }
}
