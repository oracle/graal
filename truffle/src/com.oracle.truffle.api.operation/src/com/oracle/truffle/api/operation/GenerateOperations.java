package com.oracle.truffle.api.operation;

public @interface GenerateOperations {

    boolean generateASTBuilder() default true;

    boolean generateBytecodeBuilder() default true;

    boolean generateContinueAt() default true;

    String statistics() default "";

}
