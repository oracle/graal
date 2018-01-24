/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Map;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.AbstractNewObjectNode;
import org.graalvm.compiler.nodes.java.NewMultiArrayNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;

public final class RestrictHeapAccessAnnotationChecker {

    /*
     * Command line options so errors are not fatal to the build.
     */
    public static class Options {
        @Option(help = "Print warnings for @RestrictHeapAccess annotations.")//
        public static final HostedOptionKey<Boolean> PrintRestrictHeapAccessWarnings = new HostedOptionKey<>(true);

        @Option(help = "Print path for @RestrictHeapAccess warnings.")//
        public static final HostedOptionKey<Boolean> PrintRestrictHeapAccessPath = new HostedOptionKey<>(true);
    }

    /** Entry point method. */
    public static void check(DebugContext debug, HostedUniverse universe, Collection<HostedMethod> methods) {
        final AllocationWarningVisitor visitor = new AllocationWarningVisitor(universe);
        for (HostedMethod method : methods) {
            visitor.visitMethod(debug, method);
        }
    }

    /** Does this graph contain an allocation node? */
    static boolean hasAllocation(StructuredGraph graph) {
        if (graph == null) {
            return false;
        }
        for (Node node : graph.getNodes()) {
            if (isAllocationNode(node)) {
                return true;
            }
        }
        return false;
    }

    /** Is this node an allocation node? */
    static boolean isAllocationNode(Node node) {
        return ((node instanceof AbstractNewObjectNode) || (node instanceof NewMultiArrayNode));
    }

    /** A HostedMethod visitor that checks for allocation. */
    static class AllocationWarningVisitor {

        private final HostedUniverse universe;
        private final RestrictHeapAccessCallees restrictHeapAccessCallees;

        AllocationWarningVisitor(HostedUniverse universe) {
            this.universe = universe;
            this.restrictHeapAccessCallees = ImageSingletons.lookup(RestrictHeapAccessCallees.class);
        }

        @SuppressWarnings("try")
        public void visitMethod(DebugContext debug, HostedMethod method) {
            /* If this is not a method that must not allocate, then everything is fine. */
            if (!restrictHeapAccessCallees.mustNotAllocate(method)) {
                return;
            }
            /* Look through the graph for this method and see if it allocates. */
            final StructuredGraph graph = method.compilationInfo.getGraph();
            if (RestrictHeapAccessAnnotationChecker.hasAllocation(graph)) {
                try (DebugContext.Scope s = debug.scope("RestrictHeapAccessAnnotationChecker", graph, method, this)) {
                    postRestrictHeapAccessWarning(method.getWrapped(), restrictHeapAccessCallees.getCallerMap());
                } catch (Throwable t) {
                    throw debug.handle(t);
                }
            }
        }

        private void postRestrictHeapAccessWarning(AnalysisMethod allocatingCallee, Map<AnalysisMethod, RestrictHeapAccessCallees.InvocationInfo> callerMap) {
            if (Options.PrintRestrictHeapAccessWarnings.getValue()) {
                String message = "@RestrictHeapAccess warning: ";

                /* Walk from callee to caller building a list I can walk from caller to callee. */
                final Deque<RestrictHeapAccessCallees.InvocationInfo> invocationList = new ArrayDeque<>();
                walkInvocationPath(allocatingCallee, callerMap, (AnalysisMethod element) -> {
                    final RestrictHeapAccessCallees.InvocationInfo invocationInfo = callerMap.get(element);
                    if (!invocationInfo.isNullInstance()) {
                        invocationList.addFirst(invocationInfo);
                    }
                });

                /* Walk from caller to callee building a list to the nearest allocating method. */
                final Deque<RestrictHeapAccessCallees.InvocationInfo> allocationList = new ArrayDeque<>();
                for (RestrictHeapAccessCallees.InvocationInfo element : invocationList) {
                    allocationList.addLast(element);
                    if (hasHostedAllocation(element.getCallee())) {
                        break;
                    }
                }
                if (allocationList.size() == 0) {
                    final StackTraceElement allocationStackTraceElement = getAllocationStackTraceElement(allocatingCallee);
                    if (allocationStackTraceElement != null) {
                        message += "Blacklisted method allocates directly: '" + allocationStackTraceElement.toString() + "'.";
                    } else {
                        message += "Blacklisted method allocates directly: '" + allocatingCallee.format("%H.%n(%p)") + "'";
                    }
                } else {
                    final AnalysisMethod first = allocationList.getFirst().getCaller();
                    final AnalysisMethod last = allocationList.getLast().getCallee();
                    message += "Blacklisted method: '" + first.format("%h.%n(%p)") + "' calls '" +
                                    last.format("%h.%n(%p)") + "' that allocates.";
                    if (Options.PrintRestrictHeapAccessPath.getValue()) {
                        message += "\n" + "  [Path:";
                        for (RestrictHeapAccessCallees.InvocationInfo element : allocationList) {
                            message += "\n" + "    " + element.getInvocationStackTraceElement().toString();
                        }
                        final StackTraceElement allocationStackTraceElement = getAllocationStackTraceElement(last);
                        if (allocationStackTraceElement != null) {
                            message += "\n" + "    " + allocationStackTraceElement.toString();
                        } else {
                            message += "\n" + "    " + last.format("%H.%n(%p)");
                        }
                        message += "]";
                    }
                }
                throw UserError.abort(message);
            }
        }

        /**
         * Walk up the invocation path and visit each callee.
         */
        private static void walkInvocationPath(AnalysisMethod callee, Map<AnalysisMethod, RestrictHeapAccessCallees.InvocationInfo> callerMap, AnalysisMethodVisitor visitor) {
            for (AnalysisMethod current = callee; current != null; current = callerMap.get(current).getCaller()) {
                visitor.visitAnalysisMethod(current);
            }
        }

        boolean hasHostedAllocation(AnalysisMethod method) {
            boolean result = false;
            final HostedMethod hostedMethod = universe.optionalLookup(method);
            if (hostedMethod != null) {
                final StructuredGraph graph = hostedMethod.compilationInfo.getGraph();
                if (graph != null) {
                    for (Node node : graph.getNodes()) {
                        if (RestrictHeapAccessAnnotationChecker.isAllocationNode(node)) {
                            result = true;
                            break;
                        }
                    }
                }
            }
            return result;
        }

        /** Look through the graph of the corresponding HostedMethod to see if it allocates. */
        private StackTraceElement getAllocationStackTraceElement(AnalysisMethod method) {
            StackTraceElement result = null;
            final HostedMethod hostedMethod = universe.optionalLookup(method);
            /* This level of paranoia about nulls is ugly, but necessary. */
            if (hostedMethod != null) {
                final StructuredGraph graph = hostedMethod.compilationInfo.getGraph();
                if (graph != null) {
                    for (Node node : graph.getNodes()) {
                        if (RestrictHeapAccessAnnotationChecker.isAllocationNode(node)) {
                            final NodeSourcePosition sourcePosition = node.getNodeSourcePosition();
                            if (sourcePosition != null) {
                                final int bci = sourcePosition.getBCI();
                                if (bci != -1) {
                                    result = method.asStackTraceElement(bci);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            return result;
        }

        @FunctionalInterface
        interface AnalysisMethodVisitor {
            void visitAnalysisMethod(AnalysisMethod method);
        }
    }
}
