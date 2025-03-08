package com.oracle.svm.hosted.analysis.ai.util;

import jdk.graal.compiler.nodes.Invoke;

public class InvokeUtil {

    public static boolean opensResource(Invoke invoke) {
        String declaringClass = invoke.getTargetMethod().getDeclaringClass().toJavaName();
        if (!(declaringClass.equals("java.io.FileInputStream"))) {
            return false;
        }
        return invoke.getTargetMethod().getName().equals("<init>");
    }

    public static boolean closesResource(Invoke invoke) {
        String declaringClass = invoke.getTargetMethod().getDeclaringClass().toJavaName();
        if (!(declaringClass.equals("java.io.FileInputStream"))) {
            return false;
        }
        return invoke.getTargetMethod().getName().equals("close");
    }
}
