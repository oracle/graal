package com.oracle.svm.core.sampling;

import java.util.HashMap;
import java.util.Map;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class CallStackFrameMethodInfo {
    private final Map<Integer, String> sampledMethods = new HashMap<>();

    private int enterSamplingCodeMethodId = ENTER_SAMPLING_CODE_METHOD_ID_INTIAL;
    private static final int ENTER_SAMPLING_CODE_METHOD_ID_INTIAL = -1;
    private static final String enterSamplingCodeMethod = "Safepoint.enterSlowPathSafepointCheck";

    public void addMethodInfo(ResolvedJavaMethod method, int methodId) {
        sampledMethods.put(methodId, method.format("%H.%n"));
        if (enterSamplingCodeMethodId == ENTER_SAMPLING_CODE_METHOD_ID_INTIAL && method.format("%H.%n").contains(enterSamplingCodeMethod)) {
            enterSamplingCodeMethodId = methodId;
        }
    }

    public String methodFor(int methodId) {
        return sampledMethods.get(methodId);
    }

    boolean isSamplingCodeEntry(int methodId) {
        return enterSamplingCodeMethodId == methodId;
    }

    public void setEnterSamplingCodeMethodId(int enterSamplingCodeMethodId) {
        this.enterSamplingCodeMethodId = enterSamplingCodeMethodId;
    }

    public int getEnterSamplingCodeMethodId() {
        return enterSamplingCodeMethodId;
    }
}
