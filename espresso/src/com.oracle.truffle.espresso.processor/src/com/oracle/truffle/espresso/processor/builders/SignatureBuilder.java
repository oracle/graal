package com.oracle.truffle.espresso.processor.builders;

import java.util.LinkedList;
import java.util.List;

public final class SignatureBuilder extends AbstractCodeBuilder {
    private final String name;
    private final List<String> params = new LinkedList<>();

    public SignatureBuilder(String name) {
        this.name = name;
    }

    public SignatureBuilder addParam(String param) {
        params.add(param);
        return this;
    }

    @Override
    String build() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        sb.append(PAREN_OPEN).append(String.join(", ", params)).append(PAREN_CLOSE);
        return sb.toString();
    }
}
