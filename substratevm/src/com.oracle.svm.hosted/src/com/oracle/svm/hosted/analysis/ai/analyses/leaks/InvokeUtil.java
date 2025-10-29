package com.oracle.svm.hosted.analysis.ai.analyses.leaks;

import com.oracle.svm.hosted.analysis.ai.util.SvmUtility;
import jdk.graal.compiler.nodes.Invoke;
import jdk.vm.ci.meta.ResolvedJavaType;

public class InvokeUtil {

    /* Checks if invoke is a constructor of a class that implements AutoCloseable */
    public static boolean opensResource(Invoke invoke) {
        return isResourceMethod(invoke, "<init>");
    }

    public static boolean closesResource(Invoke invoke) {
        return isResourceMethod(invoke, "close");
    }

    private static boolean isResourceMethod(Invoke invoke, String methodName) {
        if (invoke.getTargetMethod() == null) {
            return false;
        }
        ResolvedJavaType classType = invoke.getTargetMethod().getDeclaringClass();
        ResolvedJavaType autoCloseableType = SvmUtility.getInstance().lookUpType(AutoCloseable.class);

        if (!autoCloseableType.isAssignableFrom(classType)) {
            return false;
        }
        return invoke.getTargetMethod().getName().equals(methodName);
    }
}
