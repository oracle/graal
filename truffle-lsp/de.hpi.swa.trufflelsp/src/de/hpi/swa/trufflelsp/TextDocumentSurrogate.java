package de.hpi.swa.trufflelsp;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.TextDocumentContentChangeEvent;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class TextDocumentSurrogate {

    private final URI uri;
    private final String langId;
    private final List<TextDocumentContentChangeEvent> changeEventsSinceLastSuccessfulParsing;
    private final Map<SourceLocation, List<CoverageData>> location2coverageData;
    private String editorText;
    private Boolean coverageAnalysisDone = Boolean.FALSE;
    private SourceWrapper sourceWrapper;
    private TextDocumentContentChangeEvent lastChange = null;

    private TextDocumentSurrogate(TextDocumentSurrogate blueprint) {
        this.uri = blueprint.uri;
        this.langId = blueprint.langId;
        this.location2coverageData = blueprint.location2coverageData;
        this.changeEventsSinceLastSuccessfulParsing = blueprint.changeEventsSinceLastSuccessfulParsing;
        this.editorText = blueprint.editorText;
        this.sourceWrapper = blueprint.sourceWrapper;
        this.lastChange = blueprint.lastChange;
    }

    public TextDocumentSurrogate(final URI uri, final String langId) {
        this.uri = uri;
        String actualLangId = org.graalvm.polyglot.Source.findLanguage(langId);
        if (actualLangId == null) {
            try {
                actualLangId = org.graalvm.polyglot.Source.findLanguage(new File(uri));
            } catch (IOException e) {
            }

            if (actualLangId == null) {
                actualLangId = langId;
            }
        }
        this.langId = actualLangId;
        this.location2coverageData = new HashMap<>();
        this.changeEventsSinceLastSuccessfulParsing = new ArrayList<>();
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

    public String getEditorText() {
        return editorText;
    }

    public void setEditorText(String editorText) {
        this.editorText = editorText;
    }

    public Boolean getTypeHarvestingDone() {
        return coverageAnalysisDone;
    }

    public void setCoverageAnalysisDone(Boolean coverageAnalysisDone) {
        this.coverageAnalysisDone = coverageAnalysisDone;
    }

    public SourceWrapper getSourceWrapper() {
        return sourceWrapper;
    }

    public void setSourceWrapper(SourceWrapper sourceWrapper) {
        this.sourceWrapper = sourceWrapper;
    }

    @Override
    public int hashCode() {
        return this.uri.hashCode();
    }

    public TextDocumentContentChangeEvent getLastChange() {
        return lastChange;
    }

    public void setLastChange(TextDocumentContentChangeEvent lastChange) {
        this.lastChange = lastChange;
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

    public Source buildSource() {
        return Source.newBuilder(new File(uri)).name(uri.toString()).language(langId).cached(false).content(getEditorText()).build();
    }

    public SourceWrapper prepareParsing() {
        sourceWrapper = new SourceWrapper(buildSource());
        return sourceWrapper;
    }

    public void notifyParsingSuccessful(CallTarget callTarget) {
        sourceWrapper.setParsingSuccessful(true);
        sourceWrapper.setCallTarget(callTarget);
        changeEventsSinceLastSuccessfulParsing.clear();
    }

    public boolean isSourceCodeReadyForCodeCompletion() {
        return sourceWrapper.isParsingSuccessful();
    }

    public Source getSource() {
        return sourceWrapper.getSource();
    }

    public TextDocumentSurrogate copy() {
        return new TextDocumentSurrogate(this);
    }
}
