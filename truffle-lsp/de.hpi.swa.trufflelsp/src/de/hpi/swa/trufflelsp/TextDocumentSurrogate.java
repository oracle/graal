package de.hpi.swa.trufflelsp;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.lsp4j.TextDocumentContentChangeEvent;

import com.oracle.truffle.api.source.SourceSection;

public class TextDocumentSurrogate {

    private final URI uri;
    private final String langId;
    private final List<TextDocumentContentChangeEvent> changeEventsSinceLastSuccessfulParsing = new ArrayList<>();
    private final Map<SourceLocation, Set<URI>> location2coverageUri = new HashMap<>();
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

    public Map<SourceLocation, Set<URI>> getLocation2coverageUri() {
        return location2coverageUri;
    }

    public Set<URI> getCoverageUri(SourceSection section) {
        return location2coverageUri.get(SourceLocation.from(section));
    }

    public void addLocationCoverage(SourceLocation location, URI coverageUri) {
        if (!location2coverageUri.containsKey(location)) {
            location2coverageUri.put(location, new HashSet<>());
        }
        location2coverageUri.get(location).add(coverageUri);
    }
}
