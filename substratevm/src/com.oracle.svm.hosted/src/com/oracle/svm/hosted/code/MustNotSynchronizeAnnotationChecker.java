/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Iterator;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.MonitorEnterNode;
import org.graalvm.compiler.options.Option;

import com.oracle.svm.core.annotate.MustNotSynchronize;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.hosted.meta.HostedMethod;

public final class MustNotSynchronizeAnnotationChecker {

    /*
     * Command line options so errors are not fatal to the build.
     */
    public static class Options {
        @Option(help = "Print warnings for @MustNotSynchronize annotations.")//
        public static final HostedOptionKey<Boolean> PrintMustNotSynchronizeWarnings = new HostedOptionKey<>(true);

        @Option(help = "Print path for @MustNotSynchronize warnings.")//
        public static final HostedOptionKey<Boolean> PrintMustNotSynchronizePath = new HostedOptionKey<>(true);

        @Option(help = "Warnings for @MustNotSynchronize annotations are fatal.")//
        public static final HostedOptionKey<Boolean> MustNotSynchronizeWarningsAreFatal = new HostedOptionKey<>(true);
    }

    /** A collection of methods from the universe. */
    private final Collection<HostedMethod> methods;

    /**
     * Stacks of methods and their implementations that are currently being examined, to detect
     * cycles in the call graph.
     */
    private final Deque<HostedMethod> methodPath;
    private final Deque<HostedMethod> methodImplPath;

    /** Private constructor for {@link #check}. */
    private MustNotSynchronizeAnnotationChecker(Collection<HostedMethod> methods) {
        this.methods = methods;
        this.methodPath = new ArrayDeque<>();
        this.methodImplPath = new ArrayDeque<>();
    }

    /** Entry point method. */
    public static void check(DebugContext debug, Collection<HostedMethod> methods) {
        final MustNotSynchronizeAnnotationChecker checker = new MustNotSynchronizeAnnotationChecker(methods);
        checker.checkMethods(debug);
    }

    /** Check methods with the {@link MustNotSynchronize} annotation. */
    @SuppressWarnings("try")
    public void checkMethods(DebugContext debug) {
        for (HostedMethod method : methods) {
            try (DebugContext.Scope s = debug.scope("MustNotSynchronizeAnnotationChecker", method.compilationInfo.graph, method, this)) {
                MustNotSynchronize annotation = method.getAnnotation(MustNotSynchronize.class);
                if ((annotation != null) && (annotation.list() == MustNotSynchronize.BLACKLIST)) {
                    methodPath.clear();
                    methodImplPath.clear();
                    try {
                        checkMethod(method, method);
                    } catch (WarningException we) {
                        // Clean up the recursive stack trace for Debug.scope.
                        throw new WarningException(we.getMessage());
                    }
                }
            } catch (Throwable t) {
                throw debug.handle(t);
            }
        }
    }

    /** Check this method for direct synchronizations or calls to methods that synchronize. */
    protected boolean checkMethod(HostedMethod method, HostedMethod methodImpl) throws WarningException {
        if (methodImplPath.contains(methodImpl)) {
            // If the method is already on the path then avoid recursion.
            return false;
        }
        MustNotSynchronize annotation = methodImpl.getAnnotation(MustNotSynchronize.class);
        if ((annotation != null) && (annotation.list() == MustNotSynchronize.WHITELIST)) {
            // The method is on the whitelist, so I do not care if it synchronizes.
            return false;
        }
        methodPath.push(method);
        methodImplPath.push(methodImpl);
        try {
            // Check for direct synchronizations.
            if (synchronizesDirectly(methodImpl)) {
                return true;
            }
            if (synchronizesIndirectly(methodImpl)) {
                return true;
            }
            return false;
        } finally {
            methodPath.pop();
            methodImplPath.pop();
        }
    }

    /** Does this method synchronize directly? */
    protected boolean synchronizesDirectly(HostedMethod methodImpl) throws WarningException {
        final StructuredGraph graph = methodImpl.compilationInfo.getGraph();
        if (graph != null) {
            for (Node node : graph.getNodes()) {
                if (node instanceof MonitorEnterNode) {
                    postMustNotSynchronizeWarning();
                    return true;
                }
            }
        }
        return false;
    }

    /** Does this method call a method that synchronizes? */
    protected boolean synchronizesIndirectly(HostedMethod methodImpl) throws WarningException {
        boolean result = false;
        final StructuredGraph graph = methodImpl.compilationInfo.getGraph();
        if (graph != null) {
            for (Invoke invoke : graph.getInvokes()) {
                final HostedMethod callee = (HostedMethod) invoke.callTarget().targetMethod();
                if (invoke.callTarget().invokeKind().isDirect()) {
                    result |= checkMethod(callee, callee);
                    if (result) {
                        return result;
                    }
                } else {
                    for (HostedMethod calleeImpl : callee.getImplementations()) {
                        result |= checkMethod(callee, calleeImpl);
                        /* One violation is too many. */
                        if (result) {
                            return result;
                        }
                    }
                }
            }
        }
        return result;
    }

    private void postMustNotSynchronizeWarning() throws WarningException {
        final HostedMethod blacklistMethod = methodPath.getLast();
        String message = "@MustNotSynchronize warning: ";
        if (methodPath.size() == 1) {
            message += "Blacklisted method: " + blacklistMethod.format("%h.%n(%p)") + " synchronizes.";
        } else {
            final HostedMethod witness = methodPath.getFirst();
            message += "Blacklisted method: " + blacklistMethod.format("%h.%n(%p)") + " calls " + witness.format("%h.%n(%p)") + " that synchronizes.";
        }
        if (Options.PrintMustNotSynchronizeWarnings.getValue()) {
            System.err.println(message);
            if (Options.PrintMustNotSynchronizePath.getValue() && (1 < methodPath.size())) {
                printPath();
            }
        }
        if (Options.MustNotSynchronizeWarningsAreFatal.getValue()) {
            throw new WarningException(message);
        }
    }

    private void printPath() {
        System.out.print("  [Path: ");
        final Iterator<HostedMethod> methodIterator = methodPath.iterator();
        final Iterator<HostedMethod> methodImplIterator = methodImplPath.iterator();
        while (methodIterator.hasNext()) {
            final HostedMethod method = methodIterator.next();
            final HostedMethod methodImpl = methodImplIterator.next();
            System.err.println();
            if (method.equals(methodImpl)) {
                /* If the method and the implementation are the same, give a short message. */
                System.err.print("     " + method.format("%h.%n(%p)"));
            } else {
                /* Else give a longer message to help people follow virtual calls. */
                System.err.print("     " + method.format("%f %h.%n(%p)") + " implemented by " + methodImpl.format("%h.%n(%p)"));
            }
        }
        System.err.println("]");
    }

    public static class WarningException extends Exception {

        public WarningException(String message) {
            super(message);
        }

        /** Every exception needs a generated serialVersionUID. */
        private static final long serialVersionUID = 5793144021924912791L;
    }
}
