package org.graalvm.tools.lsp.server.utils;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.source.Source;

public class SourceWrapper {
    private Source source;
    private boolean parsingSuccessful = false;
    /**
     * Needed to have a strong reference to the RootNode so that it and its children will not be
     * garbage collected.
     */
    @SuppressWarnings("unused") private CallTarget callTarget;

    public SourceWrapper(Source source) {
        this.setSource(source);
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public boolean isParsingSuccessful() {
        return parsingSuccessful;
    }

    public void setParsingSuccessful(boolean parsingSuccessful) {
        this.parsingSuccessful = parsingSuccessful;
    }

    public void setCallTarget(CallTarget callTarget) {
        this.callTarget = callTarget;
    }
}
