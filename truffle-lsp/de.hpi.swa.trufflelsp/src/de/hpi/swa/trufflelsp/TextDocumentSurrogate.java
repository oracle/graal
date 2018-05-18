package de.hpi.swa.trufflelsp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.TextDocumentContentChangeEvent;

import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.source.SourceSection;

public class TextDocumentSurrogate {

    private final String uri;
    private final String langId;
    private final List<TextDocumentContentChangeEvent> changeEventsSinceLastSuccessfulParsing = new ArrayList<>();
    private final Map<SourceSection, MaterializedFrame> section2frame = new HashMap<>();
    private String currentText;
    private Boolean typeHarvestingDone = Boolean.FALSE;
    private SourceWrapper parsedSourceWrapper;

    public TextDocumentSurrogate(final String uri, final String langId) {
        this.uri = uri;
        this.langId = langId;
    }

    public TextDocumentSurrogate(final String uri, final String langId, final String currentText) {
        this(uri, langId);
        this.currentText = currentText;
    }

    public String getUri() {
        return uri;
    }

    public String getLangId() {
        return langId;
    }

    public String getCurrentText() {
        return currentText;
    }

    public void setCurrentText(String text) {
        this.currentText = text;
    }

    public Boolean getTypeHarvestingDone() {
        return typeHarvestingDone;
    }

    public void setTypeHarvestingDone(Boolean typeHarvestingDone) {
        this.typeHarvestingDone = typeHarvestingDone;
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

    public Map<SourceSection, MaterializedFrame> getSection2frame() {
        return section2frame;
    }
}
