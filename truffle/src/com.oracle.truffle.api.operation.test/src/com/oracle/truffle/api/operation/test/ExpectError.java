package com.oracle.truffle.api.operation.test;

public @interface ExpectError {
    public String[] value() default {};
}