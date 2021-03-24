package com.oracle.svm.core.sampling;

import java.util.HashMap;
import java.util.Map;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class DebugCallStackFrameMethodData {

    // Debug purpose only.
    private final Map<Integer, String> sampledMethods = new HashMap<>();

    public int samplingCodeStartId = -1;

    public void addMethodInfo(ResolvedJavaMethod method, int methodId) {
        sampledMethods.put(methodId, method.format("%H.%n"));
        if (samplingCodeStartId == -1 && method.format("%H.%n").contains("Safepoint.enterSlowPathSafepointCheck")) {
            samplingCodeStartId = methodId;
        }
    }

    public String methodInfo(int methodId) {
        return sampledMethods.get(methodId);
    }

    boolean isSamplingCode(int methodId) {
        return samplingCodeStartId == methodId;
    }
}
