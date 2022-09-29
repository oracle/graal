/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.hosted.snippets;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.GraalAccess;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ExceptionSynthesizer;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;
import com.oracle.svm.hosted.substitute.DeletedElementException;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class FoldInvocationUsingReflectionPlugin {

    /**
     * Marker value for parameters that are null, to distinguish from "not able to {@link #unbox}".
     */
    private static final Object NULL_MARKER = new Object() {
    };
    protected final SnippetReflectionProvider snippetReflection;
    protected final AnnotationSubstitutionProcessor annotationSubstitutions;
    protected final AnalysisUniverse aUniverse;
    protected final ParsingReason reason;
    private Set<Class<?>> allowedConstantClasses;
    private final boolean parseOnce = SubstrateOptions.parseOnce();

    public FoldInvocationUsingReflectionPlugin(SnippetReflectionProvider snippetReflectionProvider, AnnotationSubstitutionProcessor annotationSubstitutions, AnalysisUniverse aUniverse,
                    ParsingReason reason, Set<Class<?>> allowedConstantClasses) {
        this.annotationSubstitutions = annotationSubstitutions;
        this.aUniverse = aUniverse;
        this.snippetReflection = snippetReflectionProvider;
        this.reason = reason;
        this.allowedConstantClasses = allowedConstantClasses;
    }

    private static <T> boolean isDeleted(T element, MetaAccessProvider metaAccess) {
        AnnotatedElement annotated = null;
        try {
            if (element instanceof Executable) {
                annotated = metaAccess.lookupJavaMethod((Executable) element);
            } else if (element instanceof Field) {
                annotated = metaAccess.lookupJavaField((Field) element);
            }
        } catch (DeletedElementException ex) {
            /*
             * If ReportUnsupportedElementsAtRuntime is *not* set looking up a @Delete-ed element
             * will result in a DeletedElementException.
             */
            return true;
        }
        /*
         * If ReportUnsupportedElementsAtRuntime is set looking up a @Delete-ed element will return
         * a substitution method that has the @Delete annotation.
         */
        if (annotated != null && annotated.isAnnotationPresent(Delete.class)) {
            return true;
        }
        return false;
    }

    private static void traceConstant(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Supplier<String> targetParameters, Object value) {
        if (ReflectionPlugins.Options.ReflectionPluginTracing.getValue()) {
            System.out.println("Call to " + targetMethod.format("%H.%n(%p)") +
                            " reached in " + b.getMethod().format("%H.%n(%p)") +
                            " with parameters (" + targetParameters.get() + ")" +
                            " was reduced to the constant " + value);
        }
    }

    private static void traceException(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Supplier<String> targetParameters, Class<? extends Throwable> exceptionClass) {
        if (ReflectionPlugins.Options.ReflectionPluginTracing.getValue()) {
            System.out.println("Call to " + targetMethod.format("%H.%n(%p)") +
                            " reached in " + b.getMethod().format("%H.%n(%p)") +
                            " with parameters (" + targetParameters.get() + ")" +
                            " was reduced to a \"throw new " + exceptionClass.getName() + "(...)\"");
        }
    }

    protected boolean foldInvocationUsingReflection(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Method reflectionMethod, Supplier<ValueNode> receiver, ValueNode[] args,
                    boolean allowNullReturnValue) {
        assert b.getMetaAccess().lookupJavaMethod(reflectionMethod).equals(targetMethod) : "Fold method mismatch: " + reflectionMethod + " != " + targetMethod;

        Object receiverValue;
        if (targetMethod.isStatic()) {
            receiverValue = null;
        } else {
            /*
             * Calling receiver.get(true) can add a null check guard, i.e., modifying the graph in
             * the process. It is an error for invocation plugins that do not replace the call to
             * modify the graph.
             */
            receiverValue = unbox(b, receiver.get(), JavaKind.Object);
            if (receiverValue == null || receiverValue == NULL_MARKER) {
                return false;
            }
        }

        Object[] argValues = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object argValue = unbox(b, args[i], targetMethod.getSignature().getParameterKind(i));
            if (argValue == null) {
                return false;
            } else if (argValue == NULL_MARKER) {
                argValues[i] = null;
            } else {
                argValues[i] = argValue;
            }
        }

        /* String representation of the parameters for debug printing. */
        Supplier<String> targetParameters = () -> (receiverValue == null ? "" : receiverValue.toString() + "; ") +
                        Stream.of(argValues).map(arg -> arg instanceof Object[] ? Arrays.toString((Object[]) arg) : Objects.toString(arg)).collect(Collectors.joining(", "));

        Object returnValue;
        try {
            reflectionMethod.setAccessible(true);
            returnValue = reflectionMethod.invoke(receiverValue, argValues);
        } catch (InvocationTargetException ex) {
            return throwException(b, targetMethod, targetParameters, ex.getTargetException().getClass(), ex.getTargetException().getMessage());
        } catch (Throwable ex) {
            return throwException(b, targetMethod, targetParameters, ex.getClass(), ex.getMessage());
        }

        JavaKind returnKind = targetMethod.getSignature().getReturnKind();
        if (returnKind == JavaKind.Void) {
            /*
             * The target method is a side-effect free void method that did not throw an exception.
             */
            FoldInvocationUsingReflectionPlugin.traceConstant(b, targetMethod, targetParameters, JavaKind.Void);
            return true;
        }

        return pushConstant(b, targetMethod, targetParameters, returnKind, returnValue, allowNullReturnValue) != null;
    }

    protected Object unbox(GraphBuilderContext b, ValueNode arg, JavaKind argKind) {
        if (!arg.isJavaConstant()) {
            /*
             * If the argument is not a constant, we try to extract a varargs-parameter list for
             * Class[] arrays. This is used in many reflective lookup methods.
             */
            return SubstrateGraphBuilderPlugins.extractClassArray(annotationSubstitutions, snippetReflection, arg, true);
        }

        JavaConstant argConstant = arg.asJavaConstant();
        if (argConstant.isNull()) {
            return NULL_MARKER;
        }
        switch (argKind) {
            case Boolean:
                return argConstant.asInt() != 0L;
            case Byte:
                return (byte) argConstant.asInt();
            case Short:
                return (short) argConstant.asInt();
            case Char:
                return (char) argConstant.asInt();
            case Int:
                return argConstant.asInt();
            case Long:
                return argConstant.asLong();
            case Float:
                return argConstant.asFloat();
            case Double:
                return argConstant.asDouble();
            case Object:
                return unboxObjectConstant(b, argConstant);
            default:
                throw VMError.shouldNotReachHere();
        }
    }

    private Object unboxObjectConstant(GraphBuilderContext b, JavaConstant argConstant) {
        ResolvedJavaType javaType = b.getConstantReflection().asJavaType(argConstant);
        if (javaType != null) {
            /*
             * Get the Class object corresponding to the receiver of the reflective call. If the
             * class is substituted we want the original class, and not the substitution. The
             * reflective call will yield the original member, which will be intrinsified, and
             * subsequent phases are responsible for getting the right substitution.
             */
            return OriginalClassProvider.getJavaClass(GraalAccess.getOriginalSnippetReflection(), javaType);
        }

        /* Any other object that is not a Class. */
        Object result = snippetReflection.asObject(Object.class, argConstant);
        if (allowedConstantClasses == null || allowedConstantClasses.contains(result.getClass())) {
            return result;
        }
        return null;
    }

    /**
     * This method checks if the element should be intrinsified and returns the cached intrinsic
     * element if found. Caching intrinsic elements during analysis and reusing the same element
     * during compilation is important! For each call to Class.getMethod/Class.getField the JDK
     * returns a copy of the original object. Many of the reflection metadata fields are lazily
     * initialized, therefore the copy is partial. During analysis we use the
     * ReflectionMetadataFeature::replacer to ensure that the reflection metadata is eagerly
     * initialized. Therefore, we want to intrinsify the same, eagerly initialized object during
     * compilation, not a lossy copy of it.
     */
    @SuppressWarnings("unchecked")
    private <T> T getIntrinsic(GraphBuilderContext context, T element) {
        if (reason == ParsingReason.UnsafeSubstitutionAnalysis || reason == ParsingReason.EarlyClassInitializerAnalysis) {
            /* We are analyzing the static initializers and should always intrinsify. */
            return element;
        }
        /* We don't intrinsify if bci is not unique. */
        if (context.bciCanBeDuplicated()) {
            return null;
        }
        if (parseOnce || reason == ParsingReason.PointsToAnalysis) {
            if (FoldInvocationUsingReflectionPlugin.isDeleted(element, context.getMetaAccess())) {
                /*
                 * Should not intrinsify. Will fail during the reflective lookup at
                 * runtime. @Delete-ed elements are ignored by the reflection plugins regardless of
                 * the value of ReportUnsupportedElementsAtRuntime.
                 */
                return null;
            }

            Object replaced = aUniverse.replaceObject(element);

            if (parseOnce) {
                /* No separate parsing for compilation, so no need to cache the result. */
                return (T) replaced;
            }

            /* During parsing for analysis we intrinsify and cache the result for compilation. */
            ImageSingletons.lookup(ReflectionPlugins.ReflectionPluginRegistry.class).add(context.getMethod(), context.bci(), replaced);
        }
        /* During parsing for compilation we only intrinsify if intrinsified during analysis. */
        return ImageSingletons.lookup(ReflectionPlugins.ReflectionPluginRegistry.class).get(context.getMethod(), context.bci());
    }

    protected JavaConstant pushConstant(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Supplier<String> targetParameters, JavaKind returnKind, Object returnValue,
                    boolean allowNullReturnValue) {
        Object intrinsicValue = getIntrinsic(b, returnValue == null && allowNullReturnValue ? NULL_MARKER : returnValue);

        if (intrinsicValue == null) {
            return null;
        }

        JavaConstant intrinsicConstant;
        if (returnKind.isPrimitive()) {
            intrinsicConstant = JavaConstant.forBoxedPrimitive(intrinsicValue);
        } else if (intrinsicValue == NULL_MARKER) {
            intrinsicConstant = JavaConstant.NULL_POINTER;
        } else {
            intrinsicConstant = snippetReflection.forObject(intrinsicValue);
        }

        b.addPush(returnKind, ConstantNode.forConstant(intrinsicConstant, b.getMetaAccess()));
        FoldInvocationUsingReflectionPlugin.traceConstant(b, targetMethod, targetParameters, intrinsicValue);
        return intrinsicConstant;
    }

    protected boolean throwException(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Supplier<String> targetParameters, Class<? extends Throwable> exceptionClass, String originalMessage) {
        /* Get the exception throwing method that has a message parameter. */
        Method exceptionMethod = ExceptionSynthesizer.throwExceptionMethodOrNull(exceptionClass, String.class);
        if (exceptionMethod == null) {
            return false;
        }
        Method intrinsic = getIntrinsic(b, exceptionMethod);
        if (intrinsic == null) {
            return false;
        }

        String message = originalMessage + ". This exception was synthesized during native image building from a call to " + targetMethod.format("%H.%n(%p)") +
                        " with constant arguments.";
        ExceptionSynthesizer.throwException(b, exceptionMethod, message);
        FoldInvocationUsingReflectionPlugin.traceException(b, targetMethod, targetParameters, exceptionClass);
        return true;
    }
}
