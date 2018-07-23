package de.hpi.swa.trufflelsp;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.source.Source;

public class SourceWrapper {
    private Source source;
    private boolean parsingSuccessful = false;
    private String text;
    private CallTarget callTarget;

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

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public CallTarget getCallTarget() {
        return callTarget;
    }

    public void setCallTarget(CallTarget callTarget) {
        this.callTarget = callTarget;
    }
}
