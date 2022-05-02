package com.oracle.truffle.sl.parser.operations;

import java.util.Map;
import java.util.Objects;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;

public class SLSource {
    private final Source source;
    private Map<TruffleString, RootCallTarget> functions;

    public SLSource(Source source) {
        Objects.requireNonNull(source);
        this.source = source;
    }

    public Map<TruffleString, RootCallTarget> getFunctions() {
        return functions;
    }

    public void setFunctions(Map<TruffleString, RootCallTarget> functions) {
        this.functions = functions;
    }

    public Source getSource() {
        return source;
    }
}
