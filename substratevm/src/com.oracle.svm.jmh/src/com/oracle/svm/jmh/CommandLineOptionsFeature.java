package com.oracle.svm.jmh;

import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.RuntimeReflection;
import org.openjdk.jmh.runner.options.TimeValue;

import com.oracle.svm.core.annotate.AutomaticFeature;

/**
 * Registers classes used in command line options to be usable by JMH during runtime via reflection.
 */
@AutomaticFeature
public final class CommandLineOptionsFeature implements Feature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        RuntimeReflection.register(TimeValue.class);
        RuntimeReflection.register(TimeValue.class.getDeclaredConstructors());
        RuntimeReflection.register(TimeValue.class.getDeclaredMethods());
        RuntimeReflection.register(TimeValue.class.getDeclaredFields());

        RuntimeReflection.register(String.class);
        RuntimeReflection.register(String.class.getDeclaredConstructors());
        RuntimeReflection.register(String.class.getDeclaredMethods());
        RuntimeReflection.register(String.class.getDeclaredFields());

        RuntimeReflection.register(Boolean.class);
        RuntimeReflection.register(Boolean.class.getDeclaredConstructors());
        RuntimeReflection.register(Boolean.class.getDeclaredMethods());
        RuntimeReflection.register(Boolean.class.getDeclaredFields());

        RuntimeReflection.register(Integer.class);
        RuntimeReflection.register(Integer.class.getDeclaredConstructors());
        RuntimeReflection.register(Integer.class.getDeclaredMethods());
        RuntimeReflection.register(Integer.class.getDeclaredFields());
    }

}
