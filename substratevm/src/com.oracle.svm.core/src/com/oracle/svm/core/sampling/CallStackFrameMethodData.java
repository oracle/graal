package com.oracle.svm.core.sampling;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public interface CallStackFrameMethodData {

    int addMethodId(ResolvedJavaMethod method);
}
