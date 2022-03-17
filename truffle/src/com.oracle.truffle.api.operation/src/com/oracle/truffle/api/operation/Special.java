package com.oracle.truffle.api.operation;

public @interface Special {
    public static enum SpecialKind {
        NODE,
        ARGUMENTS,
    }

    public SpecialKind value();
}
