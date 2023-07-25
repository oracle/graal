package com.oracle.truffle.api.operation.test;

public @interface ExpectError {
    String[] value() default {};
}
