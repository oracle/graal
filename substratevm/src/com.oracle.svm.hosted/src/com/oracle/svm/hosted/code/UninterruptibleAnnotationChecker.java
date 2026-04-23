/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CFunction;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.UninterruptibleAnnotationUtils;
import com.oracle.svm.core.UninterruptibleAnnotationUtils.UninterruptibleGuestValue;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.shared.AlwaysInline;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.AnnotationUtil;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.graal.compiler.nodes.java.MonitorEnterNode;
import jdk.graal.compiler.nodes.java.NewMultiArrayNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionsParser;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/** Checks that {@linkplain Uninterruptible} has been used consistently. */
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
@AutomaticallyRegisteredImageSingleton
public final class UninterruptibleAnnotationChecker {

    public static class Options {
        @Option(help = "Print (to stderr) a DOT graph of the @Uninterruptible annotations.")//
        public static final HostedOptionKey<Boolean> PrintUninterruptibleCalleeDOTGraph = new HostedOptionKey<>(false);
    }

    private static UninterruptibleAnnotationChecker singleton() {
        return ImageSingletons.lookup(UninterruptibleAnnotationChecker.class);
    }

    private final Set<String> violations = new TreeSet<>();

    UninterruptibleAnnotationChecker() {
    }

    public static void checkAfterParsing(ResolvedJavaMethod method, StructuredGraph graph, ConstantReflectionProvider constantReflectionProvider) {
        if (UninterruptibleAnnotationUtils.isUninterruptible(method) && graph != null) {
            singleton().checkGraph(method, graph, constantReflectionProvider);
        }
    }

    public static void checkBeforeCompilation(Collection<HostedMethod> methods) {
        if (Options.PrintUninterruptibleCalleeDOTGraph.getValue()) {
            System.out.println("/* DOT */ digraph uninterruptible {");
        }

        UninterruptibleAnnotationChecker c = singleton();
        for (HostedMethod method : methods) {
            UninterruptibleGuestValue annotation = UninterruptibleAnnotationUtils.getAnnotation(method);
            CompilationGraph graph = method.compilationInfo.getCompilationGraph();
            if (annotation != null) {
                c.checkSpecifiedOptions(method, annotation);
                c.checkOverrides(method, annotation);
                c.checkCallees(method, graph);
            } else {
                c.checkCallers(method, graph);
            }
        }

        if (Options.PrintUninterruptibleCalleeDOTGraph.getValue()) {
            System.out.println("/* DOT */ }");
        }

        c.reportViolations();
    }

    private void reportViolations() {
        if (!violations.isEmpty()) {
            String message = "Found " + violations.size() + " violations of @Uninterruptible usage:";
            for (String violation : violations) {
                message = message + System.lineSeparator() + "- " + violation;
            }
            throw VMError.shouldNotReachHere("%s", message);
        }
    }

    private void checkSpecifiedOptions(HostedMethod method, UninterruptibleGuestValue annotation) {
        if (annotation.reason().equals(CALLED_FROM_UNINTERRUPTIBLE_CODE)) {
            if (!annotation.mayBeInlined() && !AnnotationUtil.isAnnotationPresent(method, NeverInline.class)) {
                addViolation("Inconsistent @Uninterruptible annotation on %s: reason '%s' is too generic for a method that is annotated with 'mayBeInlined = false'. " +
                                "If the method has an inherent reason for being uninterruptible, besides being called from uninterruptible code, then please improve the reason. " +
                                "Otherwise, use 'mayBeInlined = true' to allow inlining into interruptible code.",
                                formatMethod(method), CALLED_FROM_UNINTERRUPTIBLE_CODE);
            }

            if (annotation.callerMustBe()) {
                addViolation("Inconsistent @Uninterruptible annotation on %s: reason '%s' is too generic for a method that is annotated with 'callerMustBe = true'. " +
                                "Please explain in the reason why callers must be uninterruptible.",
                                formatMethod(method), CALLED_FROM_UNINTERRUPTIBLE_CODE);
            }

            if (!annotation.calleeMustBe()) {
                addViolation("Inconsistent @Uninterruptible annotation on %s: reason '%s' is too generic for a method that is annotated with 'calleeMustBe = false'. " +
                                "Please explain in the reason why calling interruptible code is safe.",
                                formatMethod(method), CALLED_FROM_UNINTERRUPTIBLE_CODE);
            }
        } else if (isSimilarToUnspecificReason(annotation.reason())) {
            addViolation("Inconsistent @Uninterruptible annotation on %s: reason '%s' is too similar to '%s'. " +
                            "If the method has an inherent reason for being uninterruptible, besides being called from uninterruptible code, then please improve the reason. " +
                            "Otherwise, use 'CALLED_FROM_UNINTERRUPTIBLE_CODE' as the reason.",
                            formatMethod(method), annotation.reason(), CALLED_FROM_UNINTERRUPTIBLE_CODE);
        }

        if (annotation.mayBeInlined()) {
            if (AnnotationUtil.isAnnotationPresent(method, NeverInline.class)) {
                addViolation("Inconsistent @Uninterruptible annotation on %s: 'mayBeInlined = true' conflicts with @NeverInline.",
                                formatMethod(method));
            }

            if (annotation.callerMustBe()) {
                addViolation("Inconsistent @Uninterruptible annotation on %s: 'mayBeInlined = true' conflicts with 'callerMustBe = true'. " +
                                "If it is safe to inline the method into interruptible code, please remove 'callerMustBe = true'. " +
                                "Otherwise, please remove 'mayBeInlined = true'.",
                                formatMethod(method));
            }
        }

        if (annotation.mayBeInlined() && annotation.calleeMustBe()) {
            if (!annotation.reason().equals(CALLED_FROM_UNINTERRUPTIBLE_CODE) && !AnnotationUtil.isAnnotationPresent(method, AlwaysInline.class)) {
                addViolation("Inconsistent @Uninterruptible annotation on %s: 'mayBeInlined = true' can only be used with '%s'. " +
                                "If the method has an inherent reason for being uninterruptible, besides being called from uninterruptible code, then please remove 'mayBeInlined = true'. " +
                                "Otherwise, use 'CALLED_FROM_UNINTERRUPTIBLE_CODE' as the reason.",
                                formatMethod(method), CALLED_FROM_UNINTERRUPTIBLE_CODE);
            }
        }

        if (!annotation.mayBeInlined() && !annotation.callerMustBe() && AnnotationUtil.isAnnotationPresent(method, AlwaysInline.class)) {
            addViolation("Inconsistent @Uninterruptible annotation on %s: @AlwaysInline requires either 'mayBeInlined = true' or 'callerMustBe = true'. " +
                            "If the method may be inlined into interruptible code, please use 'mayBeInlined = true'. " +
                            "Otherwise, use 'callerMustBe = true'.",
                            formatMethod(method));
        }
    }

    private static boolean isSimilarToUnspecificReason(String reason) {
        return OptionsParser.stringSimilarity(CALLED_FROM_UNINTERRUPTIBLE_CODE, reason) > 0.75;
    }

    /**
     * Check that each method annotated with {@linkplain Uninterruptible} is overridden with
     * implementations that are also annotated with {@linkplain Uninterruptible}, with the same
     * values.
     *
     * The reverse need not be true: An overriding method can be annotated with
     * {@linkplain Uninterruptible} even though the overridden method is not annotated with
     * {@linkplain Uninterruptible}.
     */
    private void checkOverrides(HostedMethod method, UninterruptibleGuestValue methodAnnotation) {
        for (HostedMethod impl : method.getImplementations()) {
            UninterruptibleGuestValue implAnnotation = UninterruptibleAnnotationUtils.getAnnotation(impl);
            if (implAnnotation != null) {
                if (methodAnnotation.callerMustBe() != implAnnotation.callerMustBe()) {
                    addViolation("Inconsistent @Uninterruptible annotations: %s overrides %s but differs in 'callerMustBe' (base=%s, override=%s).",
                                    formatMethod(impl), formatMethod(method), methodAnnotation.callerMustBe(), implAnnotation.callerMustBe());
                }
                if (methodAnnotation.calleeMustBe() != implAnnotation.calleeMustBe()) {
                    addViolation("Inconsistent @Uninterruptible annotations: %s overrides %s but differs in 'calleeMustBe' (base=%s, override=%s).",
                                    formatMethod(impl), formatMethod(method), methodAnnotation.calleeMustBe(), implAnnotation.calleeMustBe());
                }
            } else {
                addViolation("Missing @Uninterruptible annotation: %s is not annotated but overrides %s.", formatMethod(impl), formatMethod(method));
            }
        }
    }

    /**
     * Check that each method annotated with {@link Uninterruptible} calls only methods that are
     * also annotated with {@link Uninterruptible}, or methods annotated with {@link CFunction} that
     * specify "Transition = NO_TRANSITION".
     *
     * A caller can be annotated with "calleeMustBe = false" to allow calls to methods that are not
     * annotated with {@link Uninterruptible}, to allow the few cases where that should be allowed.
     */
    private void checkCallees(HostedMethod method, CompilationGraph graph) {
        if (graph == null) {
            return;
        }

        for (CompilationGraph.InvokeInfo invoke : graph.getInvokeInfos()) {
            HostedMethod directCaller = invoke.getDirectCaller();
            HostedMethod callee = invoke.getTargetMethod();

            if (Options.PrintUninterruptibleCalleeDOTGraph.getValue()) {
                printDotGraphEdge(method, callee);
            }

            UninterruptibleGuestValue directCallerAnnotation = UninterruptibleAnnotationUtils.getAnnotation(directCaller);
            if (directCallerAnnotation == null) {
                addViolation("Missing @Uninterruptible annotation: %s is annotated with @Uninterruptible, but inlines %s which is not annotated.%s",
                                formatMethod(method), formatMethod(directCaller), formatSourcePosition(invoke));
                continue;
            }

            /*
             * Code that is annotated with 'mayBeInlined = true' must not call any code that has a
             * good reason for being uninterruptible (i.e., 'mayBeInlined = false'). Otherwise, we
             * could accidentally inline uninterruptible code into an interruptible caller.
             *
             * Note that we tolerate such calls if the caller itself is annotated
             * with @AlwaysInline. This is primarily relevant for GC code, where we do some forced
             * inlining for performance reasons.
             */
            if (directCallerAnnotation.mayBeInlined() && !callee.isNative()) {
                UninterruptibleGuestValue calleeAnnotation = UninterruptibleAnnotationUtils.getAnnotation(callee);
                if (calleeAnnotation != null && !calleeAnnotation.mayBeInlined() && !AnnotationUtil.isAnnotationPresent(callee, NeverInline.class) &&
                                !AnnotationUtil.isAnnotationPresent(directCaller, AlwaysInline.class)) {
                    addViolation("Inconsistent @Uninterruptible annotations: %s is annotated with 'mayBeInlined = true', but calls %s which is annotated with 'mayBeInlined = false'. " +
                                    "This can unexpectedly inline the callee into interruptible code. " +
                                    "To prevent such inlining, either remove 'mayBeInlined = true' from the caller or annotate the callee with @NeverInline. " +
                                    "Alternatively, if inlining into interruptible code is safe, add 'mayBeInlined = true' to the callee.%s",
                                    formatMethod(directCaller), formatMethod(callee), formatSourcePosition(invoke));
                }
            }

            if (directCallerAnnotation.calleeMustBe()) {
                if (!UninterruptibleAnnotationUtils.isUninterruptible(callee)) {
                    addViolation("Missing @Uninterruptible annotation: %s is annotated with @Uninterruptible, but calls %s which is not annotated.%s",
                                    formatMethod(directCaller), formatMethod(callee), formatSourcePosition(invoke));
                }
            } else {
                if (callee.isSynthetic()) {
                    /*
                     * Synthetic callees are dangerous because they may slip in accidentally. This
                     * can cause issues in callers that are annotated with 'calleeMustBe = false'
                     * because the synthetic callee may introduce unexpected safepoints in code
                     * parts that need to be fully uninterruptible.
                     */
                    addViolation("Potentially unexpected call of interruptible code: %s is annotated with @Uninterruptible(calleeMustBe = false), but calls synthetic method %s. " +
                                    "If calling interruptible code is intended, please call the synthetic method from an unannotated method.%s",
                                    formatMethod(directCaller), formatMethod(callee), formatSourcePosition(invoke));
                }
            }
        }
    }

    /**
     * Check that each method that calls a method annotated with {@linkplain Uninterruptible} that
     * has "callerMustBe = true" is also annotated with {@linkplain Uninterruptible}.
     */
    private void checkCallers(HostedMethod caller, CompilationGraph graph) {
        if (graph == null) {
            return;
        }

        for (CompilationGraph.InvokeInfo invoke : graph.getInvokeInfos()) {
            HostedMethod callee = invoke.getTargetMethod();
            if (isCallerMustBe(callee)) {
                addViolation("Missing @Uninterruptible annotation: %s is not annotated, but calls %s which is annotated with 'callerMustBe = true'.%s",
                                formatMethod(caller), formatMethod(callee), formatSourcePosition(invoke));
            }
        }
    }

    private void checkGraph(ResolvedJavaMethod method, StructuredGraph graph, ConstantReflectionProvider constantReflectionProvider) {
        UninterruptibleGuestValue annotation = UninterruptibleAnnotationUtils.getAnnotation(method);
        for (Node node : graph.getNodes()) {
            if (isAllocationNode(node)) {
                addViolation("Unexpected allocation: %s is annotated with @Uninterruptible and therefore must not allocate.", formatMethod(method));
            } else if (node instanceof MonitorEnterNode) {
                addViolation("Unexpected synchronization: %s is annotated with @Uninterruptible and therefore must not use synchronization.", formatMethod(method));
            } else if (node instanceof EnsureClassInitializedNode && annotation.calleeMustBe()) {
                /*
                 * Class initialization nodes are lowered to some simple nodes and a foreign call.
                 * It is therefore safe to have class initialization nodes in methods that are
                 * annotated with calleeMustBe = false.
                 */
                ValueNode hub = ((EnsureClassInitializedNode) node).getHub();

                var culprit = hub.isConstant() ? constantReflectionProvider.asJavaType(hub.asConstant()).toClassName() : "unknown";
                addViolation("Unexpected class initialization: %s is annotated with @Uninterruptible and therefore must not trigger class initialization. Initialized type: %s.",
                                formatMethod(method), culprit);
            }
        }
    }

    private void addViolation(String format, Object... args) {
        violations.add(String.format(format, args));
    }

    private static String formatMethod(ResolvedJavaMethod method) {
        return method.format("%H.%n(%p):%r");
    }

    private static String formatSourcePosition(CompilationGraph.InvokeInfo invoke) {
        if (invoke.getNodeSourcePosition() == null) {
            return "";
        }
        return System.lineSeparator() + "  at " + invoke.getNodeSourcePosition();
    }

    public static boolean isAllocationNode(Node node) {
        return node instanceof CommitAllocationNode || node instanceof AbstractNewObjectNode || node instanceof NewMultiArrayNode;
    }

    private static boolean isCallerMustBe(HostedMethod method) {
        UninterruptibleGuestValue annotation = UninterruptibleAnnotationUtils.getAnnotation(method);
        return annotation != null && annotation.callerMustBe();
    }

    private static boolean isCalleeMustBe(HostedMethod method) {
        UninterruptibleGuestValue annotation = UninterruptibleAnnotationUtils.getAnnotation(method);
        return annotation != null && annotation.calleeMustBe();
    }

    private static void printDotGraphEdge(HostedMethod caller, HostedMethod callee) {
        String callerColor = " [color=black]";
        String calleeColor;
        if (UninterruptibleAnnotationUtils.isUninterruptible(caller)) {
            callerColor = " [color=blue]";
            if (!isCalleeMustBe(caller)) {
                callerColor = " [color=orange]";
            }
        }
        if (UninterruptibleAnnotationUtils.isUninterruptible(callee)) {
            calleeColor = " [color=blue]";
            if (!isCalleeMustBe(callee)) {
                calleeColor = " [color=purple]";
            }
        } else {
            calleeColor = " [color=red]";
        }
        System.out.println("/* DOT */    " + caller.format("<%h.%n>") + callerColor);
        System.out.println("/* DOT */    " + callee.format("<%h.%n>") + calleeColor);
        System.out.println("/* DOT */    " + caller.format("<%h.%n>") + " -> " + callee.format("<%h.%n>") + calleeColor);
    }
}
