/**
 * Version: 1.0.2 | Release Date: 2026-11-16
 */
package org.dspace.external.provider.impl;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.dspace.content.dto.MetadataValueDTO;
import org.dspace.external.model.ExternalDataObject;
import org.dspace.external.provider.impl.K10plusEnrichedDataProvider.AuthorityBinding;
import org.dspace.importer.external.datamodel.ImportRecord;
import org.dspace.importer.external.exception.MetadataSourceException;
import org.dspace.importer.external.metadatamapping.MetadatumDTO;

public class K10plusEnrichedExternalDataProvider extends LiveImportDataProvider {

    private String localRecordIdMetadata = "dc.identifier.other";
    private String localDisplayMetadata = "dc.title";

    @Override
    public void setRecordIdMetadata(String recordIdMetadata) {
        this.localRecordIdMetadata = recordIdMetadata;
        super.setRecordIdMetadata(recordIdMetadata);
    }

    @Override
    public void setDisplayMetadata(String displayMetadata) {
        this.localDisplayMetadata = displayMetadata;
        super.setDisplayMetadata(displayMetadata);
    }

    @Override
    public Optional<ExternalDataObject> getExternalDataObject(String id) {
        try {
            ExternalDataObject obj = convertRecordWithAuthorities(getQuerySource().getRecord(id));
            return Optional.ofNullable(obj);
        } catch (MetadataSourceException e) {
            throw new RuntimeException("K10plusEnriched provider error", e);
        }
    }

    @Override
    public List<ExternalDataObject> searchExternalDataObjects(String query, int start, int limit) {
        try {
            Collection<ImportRecord> records = getQuerySource().getRecords(query, start, limit);
            return records.stream()
                    .map(this::convertRecordWithAuthorities)
                    .collect(Collectors.toList());
        } catch (MetadataSourceException e) {
            throw new RuntimeException("K10plusEnriched provider error", e);
        }
    }

    private ExternalDataObject convertRecordWithAuthorities(ImportRecord record) {
        if (Objects.isNull(record)) {
            return null;
        }
        ExternalDataObject externalDataObject = new ExternalDataObject(getSourceIdentifier());
        String id = extractFirstValue(record, localRecordIdMetadata);
        String display = extractFirstValue(record, localDisplayMetadata);
        externalDataObject.setId(id);
        externalDataObject.setDisplayValue(display);
        externalDataObject.setValue(display);

        K10plusEnrichedDataProvider k10Provider = (getQuerySource() instanceof K10plusEnrichedDataProvider)
                ? (K10plusEnrichedDataProvider) getQuerySource()
                : null;

        for (MetadatumDTO dto : record.getValueList()) {
            MetadataValueDTO mvDTO = new MetadataValueDTO();
            mvDTO.setSchema(dto.getSchema());
            mvDTO.setElement(dto.getElement());
            mvDTO.setQualifier(dto.getQualifier());
            mvDTO.setValue(dto.getValue());

            if (k10Provider != null) {
                if ("dc".equals(dto.getSchema()) && "contributor".equals(dto.getElement()) && "author".equals(dto.getQualifier())) {
                    AuthorityBinding binding = k10Provider.getCachedAuthorBinding(dto.getValue());
                    if (binding != null) {
                        mvDTO.setAuthority(binding.getAuthority());
                        mvDTO.setConfidence(binding.getConfidence());
                    }
                } else if ("vero".equals(dto.getSchema()) && "OrgUnit".equals(dto.getElement()) && dto.getQualifier() == null) {
                    AuthorityBinding binding = k10Provider.getCachedOrgUnitBinding(dto.getValue());
                    if (binding != null) {
                        mvDTO.setAuthority(binding.getAuthority());
                        mvDTO.setConfidence(binding.getConfidence());
                    }
                }
            }
            externalDataObject.addMetadata(mvDTO);
        }
        return externalDataObject;
    }

    private String extractFirstValue(ImportRecord record, String metadata) {
        String[] split = StringUtils.split(metadata, ".", 3);
        Collection<MetadatumDTO> values = record.getValue(split[0], split[1], split.length == 3 ? split[2] : null);
        return values.isEmpty() ? null : values.iterator().next().getValue();
    }
}
