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
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.AbstractNewObjectNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.meta.HostedMethod;

public final class UninterruptibleAnnotationChecker {

    public static class Options {
        @Option(help = "Print (to stderr) a DOT graph of the @Uninterruptible annotations.")//
        public static final HostedOptionKey<Boolean> PrintUninterruptibleCalleeDOTGraph = new HostedOptionKey<>(false);
    }

    private final Collection<HostedMethod> methodCollection;
    private final Set<String> violations;

    public UninterruptibleAnnotationChecker(Collection<HostedMethod> methodCollection) {
        this.methodCollection = methodCollection;
        this.violations = new TreeSet<>();
    }

    /** Check that {@linkplain Uninterruptible} has been used consistently. */
    public void check() {
        checkUninterruptibleOverrides();
        checkUninterruptibleCallees();
        checkUninterruptibleCallers();
        checkUninterruptibleAllocations();

        if (!violations.isEmpty()) {
            String message = "Found " + violations.size() + " violations of @Uninterruptible usage:";
            for (String violation : violations) {
                message = message + System.lineSeparator() + violation;
            }
            throw UserError.abort(message);
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
     *
     * TODO: The check for the same values might be too strict.
     */
    @SuppressWarnings("try")
    private void checkUninterruptibleOverrides() {
        for (HostedMethod method : methodCollection) {
            Uninterruptible methodAnnotation = method.getAnnotation(Uninterruptible.class);
            if (methodAnnotation != null) {
                for (HostedMethod impl : method.getImplementations()) {
                    Uninterruptible implAnnotation = impl.getAnnotation(Uninterruptible.class);
                    if (implAnnotation != null) {
                        if (methodAnnotation.callerMustBe() != implAnnotation.callerMustBe()) {
                            violations.add("callerMustBe: " + method.format("%H.%n(%p)") + " != " + impl.format("%H.%n(%p)"));
                        }
                        if (methodAnnotation.calleeMustBe() != implAnnotation.calleeMustBe()) {
                            violations.add("calleeMustBe: " + method.format("%H.%n(%p)") + " != " + impl.format("%H.%n(%p)"));
                        }
                    } else {
                        violations.add("method " + method.format("%H.%n(%p)") + " is annotated but " + impl.format("%H.%n(%p)" + " is not"));
                    }
                }
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
    @SuppressWarnings("try")
    private void checkUninterruptibleCallees() {
        if (Options.PrintUninterruptibleCalleeDOTGraph.getValue()) {
            System.out.println("/* DOT */ digraph uninterruptible {");
        }
        for (HostedMethod caller : methodCollection) {
            Uninterruptible callerAnnotation = caller.getAnnotation(Uninterruptible.class);
            StructuredGraph graph = caller.compilationInfo.getGraph();
            if (callerAnnotation != null) {
                if (callerAnnotation.calleeMustBe()) {
                    if (graph != null) {
                        for (Invoke invoke : graph.getInvokes()) {
                            HostedMethod callee = (HostedMethod) invoke.callTarget().targetMethod();
                            if (Options.PrintUninterruptibleCalleeDOTGraph.getValue()) {
                                printDotGraphEdge(caller, callee);
                            }
                            if (!isNotInterruptible(callee)) {
                                violations.add("Unannotated callee: " + callee.format("%H.%n(%p)") + " called by annotated caller " + caller.format("%H.%n(%p)"));
                            }
                        }
                    }
                } else {
                    // Print DOT graph edge even if callee need not be annotated.
                    if (graph != null) {
                        for (Invoke invoke : graph.getInvokes()) {
                            HostedMethod callee = (HostedMethod) invoke.callTarget().targetMethod();
                            if (Options.PrintUninterruptibleCalleeDOTGraph.getValue()) {
                                printDotGraphEdge(caller, callee);
                            }
                        }
                    }
                }
            }
        }
        if (Options.PrintUninterruptibleCalleeDOTGraph.getValue()) {
            System.out.println("/* DOT */ }");
        }
    }

    /**
     * Check that each method that calls a method annotated with {@linkplain Uninterruptible} that
     * has "callerMustBeUninterrutible = true" is also annotated with {@linkplain Uninterruptible}.
     */
    @SuppressWarnings("try")
    private void checkUninterruptibleCallers() {
        for (HostedMethod caller : methodCollection) {
            Uninterruptible callerAnnotation = caller.getAnnotation(Uninterruptible.class);
            StructuredGraph graph = caller.compilationInfo.getGraph();
            if (callerAnnotation == null && graph != null) {
                for (Invoke invoke : graph.getInvokes()) {
                    HostedMethod callee = (HostedMethod) invoke.callTarget().targetMethod();
                    if (isCallerMustBe(callee)) {
                        violations.add("Unannotated caller: " + caller.format("%H.%n(%p)") + " calls annotated callee " + callee.format("%H.%n(%p)"));
                    }
                }
            }
        }
    }

    /**
     * Check that each method that is annotated with {@linkplain Uninterruptible} contains no
     * allocations.
     */
    @SuppressWarnings("try")
    private void checkUninterruptibleAllocations() {
        for (HostedMethod method : methodCollection) {
            Uninterruptible methodAnnotation = method.getAnnotation(Uninterruptible.class);
            StructuredGraph graph = method.compilationInfo.getGraph();
            if (methodAnnotation != null && graph != null) {
                for (Node node : graph.getNodes()) {
                    if (node instanceof AbstractNewObjectNode) {
                        violations.add("Annotated method: " + method.format("%H.%n(%p)") + " allocates.");
                    }
                }
            }
        }
    }

    private static boolean isNotInterruptible(HostedMethod method) {
        return (isUninterruptible(method) || isNoTransitionCFunction(method));
    }

    private static boolean isUninterruptible(HostedMethod method) {
        return (method.getAnnotation(Uninterruptible.class) != null);
    }

    private static boolean isCallerMustBe(HostedMethod method) {
        final Uninterruptible uninterruptibleAnnotation = method.getAnnotation(Uninterruptible.class);
        return ((uninterruptibleAnnotation != null) && uninterruptibleAnnotation.callerMustBe());
    }

    private static boolean isCalleeMustBe(HostedMethod method) {
        final Uninterruptible uninterruptibleAnnotation = method.getAnnotation(Uninterruptible.class);
        return ((uninterruptibleAnnotation != null) && uninterruptibleAnnotation.calleeMustBe());
    }

    private static boolean isNoTransitionCFunction(HostedMethod method) {
        final CFunction cfunctionAnnotation = method.getAnnotation(CFunction.class);
        final InvokeCFunctionPointer invokeCFunctionPointerAnnotation = method.getAnnotation(InvokeCFunctionPointer.class);
        return (cfunctionAnnotation != null && cfunctionAnnotation.transition() == Transition.NO_TRANSITION) ||
                        (invokeCFunctionPointerAnnotation != null && invokeCFunctionPointerAnnotation.transition() == Transition.NO_TRANSITION);
    }

    private static void printDotGraphEdge(HostedMethod caller, HostedMethod callee) {
        // The default color is black.
        String callerColor = " [color=black]";
        String calleeColor = " [color=black]";
        if (isUninterruptible(caller)) {
            callerColor = " [color=blue]";
            if (!isCalleeMustBe(caller)) {
                callerColor = " [color=orange]";
            }
        }
        if (isUninterruptible(callee)) {
            calleeColor = " [color=blue]";
            if (!isCalleeMustBe(callee)) {
                calleeColor = " [color=purple]";
            }
        } else {
            calleeColor = " [color=red]";
        }
        if (isNoTransitionCFunction(callee)) {
            calleeColor = " [color=green]";
        }
        System.out.println("/* DOT */    " + caller.format("<%h.%n>") + callerColor);
        System.out.println("/* DOT */    " + callee.format("<%h.%n>") + calleeColor);
        System.out.println("/* DOT */    " + caller.format("<%h.%n>") + " -> " + callee.format("<%h.%n>") + calleeColor);
    }
}
