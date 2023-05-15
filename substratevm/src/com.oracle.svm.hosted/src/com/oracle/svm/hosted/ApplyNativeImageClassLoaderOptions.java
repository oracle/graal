package com.oracle.svm.hosted;

public class ApplyNativeImageClassLoaderOptions implements NativeImageClassLoaderPostProcessing {
    @Override
    public void apply(NativeImageClassLoaderSupport support) {
        support.processClassLoaderOptions();
    }
}