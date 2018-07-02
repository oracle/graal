package de.hpi.swa.trufflelsp;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.TextDocumentContentChangeEvent;

import com.oracle.truffle.api.source.SourceSection;

public final class TextDocumentSurrogate {

    private final URI uri;
    private final String langId;
    private final List<TextDocumentContentChangeEvent> changeEventsSinceLastSuccessfulParsing = new ArrayList<>();
    private final Map<SourceLocation, List<CoverageData>> location2coverageData = new HashMap<>();
    private String editorText;
    private String fixedText;
    private Boolean coverageAnalysisDone = Boolean.FALSE;
    private SourceWrapper parsedSourceWrapper;

    public TextDocumentSurrogate(final URI uri, final String langId) {
        this.uri = uri;
        this.langId = langId;
    }

    public TextDocumentSurrogate(final URI uri, final String langId, final String editorText) {
        this(uri, langId);
        this.editorText = editorText;
    }

    public URI getUri() {
        return uri;
    }

    public String getLangId() {
        return langId;
    }

    public String getCurrentText() {
        return fixedText != null ? fixedText : editorText;
    }

    public String getEditorText() {
        return editorText;
    }

    public void setEditorText(String editorText) {
        this.editorText = editorText;
    }

    public String getFixedText() {
        return fixedText;
    }

    public void setFixedText(String currentFixedText) {
        this.fixedText = currentFixedText;
    }

    public Boolean getTypeHarvestingDone() {
        return coverageAnalysisDone;
    }

    public void setCoverageAnalysisDone(Boolean coverageAnalysisDone) {
        this.coverageAnalysisDone = coverageAnalysisDone;
    }

    public SourceWrapper getParsedSourceWrapper() {
        return parsedSourceWrapper;
    }

    public void setParsedSourceWrapper(SourceWrapper parsedSourceWrapper) {
        this.parsedSourceWrapper = parsedSourceWrapper;
    }

    @Override
    public int hashCode() {
        return this.uri.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextDocumentSurrogate) {
            return this.uri.equals(((TextDocumentSurrogate) obj).uri);
        }
        return false;
    }

    public List<TextDocumentContentChangeEvent> getChangeEventsSinceLastSuccessfulParsing() {
        return changeEventsSinceLastSuccessfulParsing;
    }

    public List<CoverageData> getCoverageData(SourceSection section) {
        return location2coverageData.get(SourceLocation.from(section));
    }

    public List<CoverageData> getCoverageData(SourceLocation location) {
        return location2coverageData.get(location);
    }

    public Set<URI> getCoverageUris(SourceSection section) {
        List<CoverageData> coverageDataObjects = location2coverageData.get(SourceLocation.from(section));
        return coverageDataObjects == null ? null : coverageDataObjects.stream().map(coverageData -> coverageData.getCovarageUri()).collect(Collectors.toSet());
    }

    public void addLocationCoverage(SourceLocation location, CoverageData coverageData) {
        if (!location2coverageData.containsKey(location)) {
            location2coverageData.put(location, new ArrayList<>());
        }
        location2coverageData.get(location).add(coverageData);
    }

    public boolean isLocationCovered(SourceLocation location) {
        return location2coverageData.containsKey(location);
    }

    public boolean hasCoverageData() {
        return !location2coverageData.isEmpty();
    }

    public void clearCoverage() {
        location2coverageData.clear();
    }

    public List<SourceLocation> getCoverageLocations() {
        return new ArrayList<>(location2coverageData.keySet());
    }

    public void replace(SourceLocation oldLocation, SourceLocation newLocation) {
        List<CoverageData> removedCoverageData = location2coverageData.remove(oldLocation);
        assert removedCoverageData != null;
        location2coverageData.put(newLocation, removedCoverageData);
    }
}
