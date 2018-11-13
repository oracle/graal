package org.graalvm.tools.lsp.server.utils;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.TextDocumentContentChangeEvent;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class TextDocumentSurrogate {

    private final URI uri;
    private final List<TextDocumentContentChangeEvent> changeEventsSinceLastSuccessfulParsing;
    private final Map<SourceLocation, List<CoverageData>> location2coverageData;
    private String editorText;
    private Boolean coverageAnalysisDone = Boolean.FALSE;
    private SourceWrapper sourceWrapper;
    private TextDocumentContentChangeEvent lastChange = null;
    private final List<String> completionTriggerCharacters;
    private final LanguageInfo languageInfo;

    private TextDocumentSurrogate(TextDocumentSurrogate blueprint) {
        this.uri = blueprint.uri;
        this.location2coverageData = blueprint.location2coverageData;
        this.changeEventsSinceLastSuccessfulParsing = blueprint.changeEventsSinceLastSuccessfulParsing;
        this.editorText = blueprint.editorText;
        this.sourceWrapper = blueprint.sourceWrapper;
        this.lastChange = blueprint.lastChange;
        this.completionTriggerCharacters = blueprint.completionTriggerCharacters;
        this.languageInfo = blueprint.languageInfo;
    }

    public TextDocumentSurrogate(final URI uri, final LanguageInfo languageInfo, final List<String> completionTriggerCharacters) {
        this.uri = uri;
        this.completionTriggerCharacters = completionTriggerCharacters;
        this.location2coverageData = new HashMap<>();
        this.changeEventsSinceLastSuccessfulParsing = new ArrayList<>();
        this.languageInfo = languageInfo;
    }

    public URI getUri() {
        return uri;
    }

    public String getLangId() {
        return languageInfo.getId();
    }

    public String getEditorText() {
        return editorText != null ? editorText : (editorText = buildSource().getCharacters().toString());
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

    public List<String> getCompletionTriggerCharacters() {
        return completionTriggerCharacters;
    }

    public LanguageInfo getLanguageInfo() {
        return languageInfo;
    }

    @Override
    public int hashCode() {
        return uri.hashCode();
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
            return uri.equals(((TextDocumentSurrogate) obj).uri);
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

    public void clearCoverage(URI runScriptUri) {
        for (Iterator<Entry<SourceLocation, List<CoverageData>>> iterator = location2coverageData.entrySet().iterator(); iterator.hasNext();) {
            Entry<SourceLocation, List<CoverageData>> entry = iterator.next();
            for (Iterator<CoverageData> iteratorData = entry.getValue().iterator(); iteratorData.hasNext();) {
                CoverageData coverageData = iteratorData.next();
                if (coverageData.getCovarageUri().equals(runScriptUri)) {
                    iteratorData.remove();
                }
            }
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
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
        try {
            return Source.newBuilder(languageInfo.getId(), uri.toURL()).name(uri.toString()).cached(false).content(editorText).build();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
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
        return sourceWrapper != null ? sourceWrapper.getSource() : null;
    }

    public TextDocumentSurrogate copy() {
        return new TextDocumentSurrogate(this);
    }
}
