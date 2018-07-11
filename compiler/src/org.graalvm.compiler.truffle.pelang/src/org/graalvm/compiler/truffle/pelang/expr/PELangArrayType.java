package org.graalvm.compiler.truffle.pelang.expr;

import org.graalvm.compiler.truffle.pelang.PELangFunction;

import com.oracle.truffle.api.object.DynamicObject;

public enum PELangArrayType {

    Long(Long.class),
    String(String.class),
    Function(PELangFunction.class),
    Object(DynamicObject.class);

    private final Class<?> javaClass;

    private PELangArrayType(Class<?> javaClass) {
        this.javaClass = javaClass;
    }

    public Class<?> getJavaClass() {
        return javaClass;
    }

}
