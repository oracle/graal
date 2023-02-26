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

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.AbstractNewObjectNode;
import org.graalvm.compiler.nodes.java.NewMultiArrayNode;
import org.graalvm.compiler.nodes.virtual.CommitAllocationNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CFunction;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/** Checks that {@linkplain Uninterruptible} has been used consistently. */
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

    public static void checkAfterParsing(ResolvedJavaMethod method, StructuredGraph graph) {
        singleton().checkAllocations(method, graph);
    }

    public static void checkBeforeCompilation(Collection<HostedMethod> methods) {
        if (Options.PrintUninterruptibleCalleeDOTGraph.getValue()) {
            System.out.println("/* DOT */ digraph uninterruptible {");
        }

        UninterruptibleAnnotationChecker c = singleton();
        for (HostedMethod method : methods) {
            Uninterruptible annotation = Uninterruptible.Utils.getAnnotation(method);
            CompilationGraph graph = method.compilationInfo.getCompilationGraph();
            c.checkSpecifiedOptions(method, annotation);
            c.checkOverrides(method, annotation);
            c.checkCallees(method, annotation, graph);
            c.checkCallers(method, annotation, graph);
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
                message = message + System.lineSeparator() + violation;
            }
            throw UserError.abort("%s", message);
        }
    }

    /**
     * Check that each method annotated with {@linkplain Uninterruptible} doesn't contain
     * "calleeMustBe == false" and "mayBeInlined == true" at the same time.
     *
     * The combination does not make much sense because "mayBeInlined = true" means that the
     * annotated method itself actually has no need to be uninterruptible, it is just a "benign
     * utility method" that is used from within and outside uninterruptible code. But then
     * "calleeMustBe = false" actually means that the method is actually doing something to switch
     * from uninterruptible to "normal" code.
     */
    private void checkSpecifiedOptions(HostedMethod method, Uninterruptible methodAnnotation) {
        if (methodAnnotation == null) {
            return;
        }
        if (methodAnnotation.mayBeInlined() && !methodAnnotation.calleeMustBe()) {
            violations.add("method " + method.format("%H.%n(%p)") + " is annotated with 'calleeMustBe == false' " +
                            "and 'mayBeInlined == true' at the same time.");
        }
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
    private void checkOverrides(HostedMethod method, Uninterruptible methodAnnotation) {
        if (methodAnnotation == null) {
            return;
        }
        for (HostedMethod impl : method.getImplementations()) {
            Uninterruptible implAnnotation = Uninterruptible.Utils.getAnnotation(impl);
            if (implAnnotation != null) {
                if (methodAnnotation.callerMustBe() != implAnnotation.callerMustBe()) {
                    violations.add("callerMustBe: " + method.format("%H.%n(%p):%r") + " != " + impl.format("%H.%n(%p):%r"));
                }
                if (methodAnnotation.calleeMustBe() != implAnnotation.calleeMustBe()) {
                    violations.add("calleeMustBe: " + method.format("%H.%n(%p):%r") + " != " + impl.format("%H.%n(%p):%r"));
                }
            } else {
                violations.add("method " + method.format("%H.%n(%p):%r") + " is annotated but " + impl.format("%H.%n(%p):%r" + " is not"));
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
    private void checkCallees(HostedMethod caller, Uninterruptible callerAnnotation, CompilationGraph graph) {
        if (callerAnnotation == null || graph == null) {
            return;
        }
        for (CompilationGraph.InvokeInfo invoke : graph.getInvokeInfos()) {
            HostedMethod callee = invoke.getTargetMethod();
            if (Options.PrintUninterruptibleCalleeDOTGraph.getValue()) {
                printDotGraphEdge(caller, callee);
            }

            Uninterruptible directCallerAnnotation = Uninterruptible.Utils.getAnnotation(invoke.getDirectCaller());
            if (directCallerAnnotation == null) {
                violations.add("Unannotated callee: " + invoke.getDirectCaller().format("%H.%n(%p):%r") + " inlined into annotated caller " + caller.format("%H.%n(%p):%r") +
                                System.lineSeparator() + invoke.getNodeSourcePosition());
            } else if (directCallerAnnotation.calleeMustBe() && !Uninterruptible.Utils.isUninterruptible(callee)) {
                violations.add("Unannotated callee: " + callee.format("%H.%n(%p):%r") + " called by annotated caller " + caller.format("%H.%n(%p):%r") +
                                System.lineSeparator() + invoke.getNodeSourcePosition());
            }
        }
    }

    /**
     * Check that each method that calls a method annotated with {@linkplain Uninterruptible} that
     * has "callerMustBe = true" is also annotated with {@linkplain Uninterruptible}.
     */
    private void checkCallers(HostedMethod caller, Uninterruptible callerAnnotation, CompilationGraph graph) {
        if (callerAnnotation != null || graph == null) {
            return;
        }
        for (CompilationGraph.InvokeInfo invoke : graph.getInvokeInfos()) {
            HostedMethod callee = invoke.getTargetMethod();
            if (isCallerMustBe(callee)) {
                violations.add("Unannotated caller: " + caller.format("%H.%n(%p)") + " calls annotated callee " + callee.format("%H.%n(%p)"));
            }
        }
    }

    /**
     * Check that each method that is annotated with {@linkplain Uninterruptible} contains no
     * allocations.
     */
    private void checkAllocations(ResolvedJavaMethod method, StructuredGraph graph) {
        if (Uninterruptible.Utils.isUninterruptible(method) && graph != null) {
            for (Node node : graph.getNodes()) {
                if (isAllocationNode(node)) {
                    violations.add("Annotated method: " + method.format("%H.%n(%p)") + " allocates.");
                }
            }
        }
    }

    public static boolean isAllocationNode(Node node) {
        return node instanceof CommitAllocationNode || node instanceof AbstractNewObjectNode || node instanceof NewMultiArrayNode;
    }

    private static boolean isCallerMustBe(HostedMethod method) {
        Uninterruptible uninterruptibleAnnotation = Uninterruptible.Utils.getAnnotation(method);
        return uninterruptibleAnnotation != null && uninterruptibleAnnotation.callerMustBe();
    }

    private static boolean isCalleeMustBe(HostedMethod method) {
        Uninterruptible uninterruptibleAnnotation = Uninterruptible.Utils.getAnnotation(method);
        return uninterruptibleAnnotation != null && uninterruptibleAnnotation.calleeMustBe();
    }

    private static void printDotGraphEdge(HostedMethod caller, HostedMethod callee) {
        String callerColor = " [color=black]";
        String calleeColor;
        if (Uninterruptible.Utils.isUninterruptible(caller)) {
            callerColor = " [color=blue]";
            if (!isCalleeMustBe(caller)) {
                callerColor = " [color=orange]";
            }
        }
        if (Uninterruptible.Utils.isUninterruptible(callee)) {
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
