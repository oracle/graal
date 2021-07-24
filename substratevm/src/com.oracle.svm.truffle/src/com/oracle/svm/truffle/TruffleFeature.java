package com.oracle.svm.truffle;

import org.graalvm.nativeimage.hosted.Feature;

import java.util.Arrays;
import java.util.List;

/**
 * Feature that includes both {@link TruffleBaseFeature} and {@link TruffleCompilationFeature} because some downstream projects
 * depend on the old TruffleFeature.
 */
public class TruffleFeature implements Feature {
    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(TruffleBaseFeature.class, TruffleCompilationFeature.class);
    }
}
