package com.oracle.svm.hosted.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;

/**
 * Defines a service for specifying additional {@link ClassFileTransformer}s.
 *
 * {@link NativeImageBytecodeInstrumentationAgent} uses
 * {@code ServiceLoader.load(NativeImageBytecodeInstrumentationAgentExtension.class)} to register
 * additional transformers.
 */
public interface NativeImageBytecodeInstrumentationAgentExtension {

    void addClassFileTransformers(Instrumentation inst);
}
