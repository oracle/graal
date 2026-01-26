package com.oracle.svm.core.c.libc;

public class CosmoLibC implements LibCBase {

    public static final String NAME = "cosmo";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean hasIsolatedNamespaces() {
        return false;
    }
}