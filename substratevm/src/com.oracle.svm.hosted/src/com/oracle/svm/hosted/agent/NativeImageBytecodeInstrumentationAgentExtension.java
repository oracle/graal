package com.oracle.svm.hosted.agent;

import java.lang.instrument.Instrumentation;

/**
 * Defines a service for specifying additional ClassInstrumentationTransformers.
 *
 * {@link NativeImageBytecodeInstrumentationAgent} uses
 * {@code ServiceLoader.load(NativeImageBytecodeInstrumentationAgentExtension.class)} to register
 * additional transformers. This is used to specify transformers that should only be used when
 * running the agent on JDK8.
 */
public interface NativeImageBytecodeInstrumentationAgentExtension {

    void addClassInstrumentationTransformer(Instrumentation inst);
}
