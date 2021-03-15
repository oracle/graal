package com.oracle.svm.core.sampling;

import com.oracle.svm.core.code.FrameInfoQueryResult;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Encode method data to identify methods in {@link FrameInfoQueryResult}.
 */
public interface CallStackFrameMethodData {

    int setMethodId(ResolvedJavaMethod method);
}
