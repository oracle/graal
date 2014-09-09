package com.oracle.graal.api.meta;

public class RawConstant extends PrimitiveConstant {
    private static final long serialVersionUID = -242269518888560348L;

    public RawConstant(long rawValue) {
        super(Kind.Int, rawValue);
    }
}
