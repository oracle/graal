package com.oracle.truffle.api.bytecode.test;

public @interface ExpectError {
    String[] value() default {};
}
