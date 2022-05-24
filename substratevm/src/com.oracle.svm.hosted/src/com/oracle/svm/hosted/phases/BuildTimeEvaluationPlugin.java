package com.oracle.svm.hosted.phases;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.hosted.snippets.FoldInvocationUsingReflectionPlugin;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;
import com.oracle.svm.util.GuardedAnnotationAccess;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class BuildTimeEvaluationPlugin extends FoldInvocationUsingReflectionPlugin implements NodePlugin {
    public BuildTimeEvaluationPlugin(AnnotationSubstitutionProcessor annotationSubstitutions, AnalysisUniverse aUniverse, ParsingReason reason,
                    Set<Class<?>> allowedConstantClasses) {
        super(aUniverse.getSnippetReflection(), annotationSubstitutions, aUniverse, reason, allowedConstantClasses);
    }

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        Annotation matchedBuildTimeAnnotation = findBuildTimeAnnotation(GuardedAnnotationAccess.getAnnotations(method));
        if (matchedBuildTimeAnnotation == null) {
            return false;
        }
        if (!method.isStatic() && !method.isFinal()) {
            /* We don't do non-final methods */
            return false;
        }

        ValueNode[] newArgs;
        final ValueNode receiver = method.isStatic() ? null : args[0];
        if (method.isStatic()) {
            newArgs = args;
        } else {
            newArgs = Arrays.copyOfRange(args, 1, args.length);
        }

        return foldInvocationUsingReflection(b, method, reflectionMethod(method), () -> receiver, newArgs, true);
    }

    @Override
    public boolean handleNewInstance(GraphBuilderContext b, ResolvedJavaType type) {
        return NodePlugin.super.handleNewInstance(b, type);
    }

    private Method reflectionMethod(ResolvedJavaMethod targetMethod) {
        AnalysisMethod m = (AnalysisMethod) targetMethod;
        return (Method) m.getJavaMethod();
    }

    private static Annotation findBuildTimeAnnotation(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().getTypeName().endsWith("nativeimage.meta.Constant")) {
                return annotation;
            }
        }
        return null;
    }
}
