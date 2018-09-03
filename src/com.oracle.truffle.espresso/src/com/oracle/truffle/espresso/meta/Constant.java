package com.oracle.truffle.espresso.meta;

/**
 * Represents a compile-time constant (boxed) value within the compiler.
 */
public interface Constant {

    boolean isDefaultForKind();

    String toValueString();
}
