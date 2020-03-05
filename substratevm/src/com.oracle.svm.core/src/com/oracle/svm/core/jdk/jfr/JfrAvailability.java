package com.oracle.svm.core.jdk.jfr;

import java.util.function.BooleanSupplier;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

public class JfrAvailability {
    public static boolean withJfr = false;
    public static class WithJfr implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return JavaVersionUtil.JAVA_SPEC >= 11 && withJfr;
        }
    }
}