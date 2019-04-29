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

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Map;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.UnsafeAccessNode;
import org.graalvm.compiler.nodes.java.AbstractNewObjectNode;
import org.graalvm.compiler.nodes.java.AccessArrayNode;
import org.graalvm.compiler.nodes.java.AccessFieldNode;
import org.graalvm.compiler.nodes.java.NewMultiArrayNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.annotate.RestrictHeapAccess.Access;
import com.oracle.svm.core.heap.RestrictHeapAccessCallees;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.code.RestrictHeapAccessCalleesImpl.RestrictionInfo;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

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
        final RestrictHeapAccessWarningVisitor visitor = new RestrictHeapAccessWarningVisitor(universe);
        for (HostedMethod method : methods) {
            visitor.visitMethod(debug, method);
        }
    }

    static Node checkViolatingNode(StructuredGraph graph, Access access) {
        if (graph != null) {
            for (Node node : graph.getNodes()) {
                if (!isViolatingNode(node, access)) {
                    return node;
                }
            }
        }
        return null;
    }

    private static boolean isViolatingNode(Node node, Access access) {
        assert access != Access.UNRESTRICTED : "does not require checks";
        return !isAllocationNode(node) && (access != Access.NO_HEAP_ACCESS || !isHeapAccess(node));
    }

    private static boolean isAllocationNode(Node node) {
        return (node instanceof AbstractNewObjectNode || node instanceof NewMultiArrayNode);
    }

    private static boolean isHeapAccess(Node node) {
        if (node instanceof AccessFieldNode || node instanceof AccessArrayNode || node instanceof UnsafeAccessNode) {
            return true;
        } else if (node instanceof ConstantNode) {
            Constant constant = ((ConstantNode) node).getValue();
            if (constant instanceof JavaConstant) {
                /*
                 * Loading an object constant other than null is suspicious and can cause pointer
                 * compression or uncompression which can be undesirable in some contexts, for
                 * example when the heap is not set up.
                 */
                JavaConstant javaConstant = (JavaConstant) constant;
                return javaConstant.getJavaKind() == JavaKind.Object && javaConstant.isNonNull();
            }
        } else if (node instanceof Invoke) {
            /* Virtual invokes do type checks and vtable lookups that access the heap */
            return ((Invoke) node).callTarget().invokeKind() == InvokeKind.Virtual;
        }
        return false;
    }

    /** A HostedMethod visitor that checks for violations of heap access restrictions. */
    static class RestrictHeapAccessWarningVisitor {

        private final HostedUniverse universe;
        private final RestrictHeapAccessCalleesImpl restrictHeapAccessCallees;

        RestrictHeapAccessWarningVisitor(HostedUniverse universe) {
            this.universe = universe;
            this.restrictHeapAccessCallees = (RestrictHeapAccessCalleesImpl) ImageSingletons.lookup(RestrictHeapAccessCallees.class);
        }

        @SuppressWarnings("try")
        public void visitMethod(DebugContext debug, HostedMethod method) {
            /* If this is not a method that must not allocate, then everything is fine. */
            RestrictionInfo info = restrictHeapAccessCallees.getRestrictionInfo(method);
            if (info == null || info.getAccess() == Access.UNRESTRICTED) {
                return;
            }
            /* Look through the graph for this method and see if it allocates. */
            final StructuredGraph graph = method.compilationInfo.getGraph();
            if (RestrictHeapAccessAnnotationChecker.checkViolatingNode(graph, info.getAccess()) != null) {
                try (DebugContext.Scope s = debug.scope("RestrictHeapAccessAnnotationChecker", graph, method, this)) {
                    postRestrictHeapAccessWarning(method.getWrapped(), restrictHeapAccessCallees.getCallerMap());
                } catch (Throwable t) {
                    throw debug.handle(t);
                }
            }
        }

        private void postRestrictHeapAccessWarning(AnalysisMethod violatingCallee, Map<AnalysisMethod, RestrictionInfo> callerMap) {
            if (Options.PrintRestrictHeapAccessWarnings.getValue()) {
                Access violatedAccess = callerMap.get(violatingCallee).getAccess();
                String message = "@RestrictHeapAccess warning: ";

                /* Walk from callee to caller building a list I can walk from caller to callee. */
                final Deque<RestrictionInfo> callChain = new ArrayDeque<>();
                AnalysisMethod current = violatingCallee;
                while (current != null) {
                    final RestrictionInfo info = callerMap.get(current);
                    callChain.addFirst(info);
                    current = info.getCaller();
                }
                /* Walk from caller to callee building a list to the nearest violating method. */
                final Deque<RestrictionInfo> allocationList = new ArrayDeque<>();
                for (RestrictionInfo element : callChain) {
                    allocationList.addLast(element);
                    if (checkHostedViolatingNode(element.getMethod(), element.getAccess()) != null) {
                        break;
                    }
                }
                assert !allocationList.isEmpty();
                if (allocationList.size() == 1) {
                    final StackTraceElement allocationStackTraceElement = getViolatingStackTraceElement(violatingCallee, violatedAccess);
                    if (allocationStackTraceElement != null) {
                        message += "Restricted method '" + allocationStackTraceElement.toString() + "' directly violates restriction " + violatedAccess + ".";
                    } else {
                        message += "Restricted method '" + violatingCallee.format("%H.%n(%p)") + "' directly violates restriction " + violatedAccess + ".";
                    }
                } else {
                    final RestrictionInfo first = allocationList.getFirst();
                    final RestrictionInfo last = allocationList.getLast();
                    message += "Restricted method: '" + first.getMethod().format("%h.%n(%p)") + "' calls '" +
                                    last.getMethod().format("%h.%n(%p)") + "' that violates restriction " + violatedAccess + ".";
                    if (Options.PrintRestrictHeapAccessPath.getValue()) {
                        message += "\n" + "  [Path:";
                        for (RestrictionInfo element : allocationList) {
                            if (element != first) { // first element has no caller
                                message += "\n" + "    " + element.getInvocationStackTraceElement().toString();
                            }
                        }
                        final StackTraceElement allocationStackTraceElement = getViolatingStackTraceElement(last.getMethod(), last.getAccess());
                        if (allocationStackTraceElement != null) {
                            message += "\n" + "    " + allocationStackTraceElement.toString();
                        } else {
                            message += "\n" + "    " + last.getMethod().format("%H.%n(%p)");
                        }
                        message += "]";
                    }
                }
                throw UserError.abort(message);
            }
        }

        Node checkHostedViolatingNode(AnalysisMethod method, Access access) {
            final HostedMethod hostedMethod = universe.optionalLookup(method);
            if (hostedMethod != null) {
                final StructuredGraph graph = hostedMethod.compilationInfo.getGraph();
                return checkViolatingNode(graph, access);
            }
            return null;
        }

        /**
         * Look through the graph of the corresponding HostedMethod to see if it allocates.
         */
        private StackTraceElement getViolatingStackTraceElement(AnalysisMethod method, Access access) {
            final HostedMethod hostedMethod = universe.optionalLookup(method);
            if (hostedMethod != null) {
                final StructuredGraph graph = hostedMethod.compilationInfo.getGraph();
                Node node = checkViolatingNode(graph, access);
                if (node != null) {
                    final NodeSourcePosition sourcePosition = node.getNodeSourcePosition();
                    if (sourcePosition != null && sourcePosition.getBCI() != -1) {
                        return method.asStackTraceElement(sourcePosition.getBCI());
                    }
                }
            }
            return null;
        }
    }
}
