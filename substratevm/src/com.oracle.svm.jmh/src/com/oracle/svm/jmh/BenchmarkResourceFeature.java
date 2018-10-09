package com.oracle.svm.jmh;

import java.io.IOException;
import java.io.InputStream;

import org.graalvm.nativeimage.Feature;

import com.oracle.svm.core.jdk.Resources;

/**
 * Registers the generated benchmark resource file to be usable by JMH during runtime.
 */
public final class BenchmarkResourceFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        try (InputStream stream = Benchmarks.inputStream()) {
            Resources.registerResource(Benchmarks.RESOURCE, stream);
        } catch (IOException e) {
            throw new RuntimeException("error reading benchmark list", e);
        }
    }

}
