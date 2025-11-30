package com.oracle.svm.hosted.analysis.ai.checker.applier;

public abstract class BaseApplier implements FactApplier {

    @Override
    public String getDescription() {
        return "Base applier";
    }

    @Override
    public boolean shouldApply() {
        return false;
    }
}
