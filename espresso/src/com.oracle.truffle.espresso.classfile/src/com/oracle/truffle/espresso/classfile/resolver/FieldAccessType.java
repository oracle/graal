package com.oracle.truffle.espresso.shared.resolver;

public enum FieldAccessType {
    GetStatic(true, false),
    PutStatic(true, true),
    GetInstance(false, false),
    PutInstance(false, true);

    private final boolean isStatic;
    private final boolean isPut;

    FieldAccessType(boolean isStatic, boolean isPut) {
        this.isStatic = isStatic;
        this.isPut = isPut;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public boolean isPut() {
        return isPut;
    }
}
