package com.oracle.svm.hosted.agent;

import java.lang.instrument.Instrumentation;

public interface NativeImageBytecodeInstrumentationAgentExtension {

    void addClassInstrumentationTransformer(Instrumentation inst);
}
