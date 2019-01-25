package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.mudball.impl.Klass;

public final class Field {

    public static final Field[] EMPTY_ARRAY = new Field[0];

    private final LinkedField linkedField;
    private final Klass declaringKlass;
    private final Klass type;

    public Klass getType() {
        return type;
    }

    public Klass getKind() {
        return type.getJavaKind();
    }

    public Klass getDeclaringKlass() {
        return declaringKlass;
    }

    public Field(LinkedField linkedField, Klass declaringKlass, Klass type) {
        this.linkedField = linkedField;
        this.declaringKlass = declaringKlass;
        this.type = type;
    }
}
