package com.oracle.svm.jmh.feature;

import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.RuntimeReflection;
import org.openjdk.jmh.runner.BenchmarkListEntry;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.jmh.Benchmarks;

/**
 * Registers the generated benchmark classes to be usable by JMH during runtime via reflection.
 */
@AutomaticFeature
public final class BenchmarkReflectionFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        Benchmarks.strings().stream().map(BenchmarkListEntry::new).forEach(entry -> {
            // parse generated benchmark class name from entry
            String target = entry.generatedTarget();
            int lastDot = target.lastIndexOf('.');
            String className = target.substring(0, lastDot);

            // register generated benchmark class for reflection
            Class<?> cls = access.findClassByName(className);
            RuntimeReflection.register(cls);
            RuntimeReflection.register(cls.getDeclaredConstructors());
            RuntimeReflection.register(cls.getDeclaredMethods());
        });
    }

}
