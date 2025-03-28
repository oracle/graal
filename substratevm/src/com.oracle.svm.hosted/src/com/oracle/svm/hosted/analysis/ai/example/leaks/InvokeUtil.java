package com.oracle.svm.hosted.analysis.ai.example.leaks;

import com.oracle.svm.hosted.analysis.ai.example.leaks.set.ResourceId;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;

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

    public static ResourceId getInitResourceId(Invoke invoke) {
        return new ResourceId(invoke.callTarget().getNodeSourcePosition());
    }

    public static ResourceId getAllocatedObjResourceId(AllocatedObjectNode allocatedObjectNode) {
        return new ResourceId(allocatedObjectNode.getVirtualObject().getNodeSourcePosition());
    }
}
