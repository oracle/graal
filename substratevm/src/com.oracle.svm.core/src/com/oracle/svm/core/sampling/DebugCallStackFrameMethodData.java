package com.oracle.svm.core.sampling;

import jdk.vm.ci.meta.ResolvedJavaMethod;

import java.util.HashMap;
import java.util.Map;

//Debug purposes only.
public class DebugCallStackFrameMethodData {

    private Map<Integer, String> samplingMethods = new HashMap<>();

    public void addMethodInfo(ResolvedJavaMethod method, int methodId) {
        samplingMethods.put(methodId, method.format("%H.%n"));
    }

    public String methodInfo(int methodId) {
        return samplingMethods.get(methodId);
    }
}
