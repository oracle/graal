/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.RestrictHeapAccess.Access;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Construct a list of all the methods that are, or are called from, methods annotated with
 * {@link RestrictHeapAccess} or {@link Uninterruptible}.
 */
public class RestrictHeapAccessCallees {

    /**
     * A map from a callee to a caller on a path to an annotated caller. The keys are the set of
     * callees that must not allocate. The values are for printing a call path for error messages.
     */
    private Map<AnalysisMethod, InvocationInfo> calleeToCallerMap;

    /** AssertionErrors are cut points, because their allocations are removed. */
    private List<ResolvedJavaMethod> assertionErrorConstructorList;

    /** Initialize the set of callees only once. */
    private boolean initialized;

    /** Constructor for the singleton instance. */
    public RestrictHeapAccessCallees() {
        calleeToCallerMap = Collections.emptyMap();
        this.assertionErrorConstructorList = Collections.emptyList();
        initialized = false;
    }

    /** This gets called multiple times, but I only need one AnalysisMethod to be happy. */
    public void setAssertionErrorConstructors(List<ResolvedJavaMethod> resolvedConstructorList) {
        if (assertionErrorConstructorList.isEmpty()) {
            assertionErrorConstructorList = resolvedConstructorList;
        }
    }

    /** Is the method on the list of methods that must not allocate? */
    public boolean mustNotAllocate(ResolvedJavaMethod method) {
        return calleeToCallerMap.containsKey(methodToKey(method));
    }

    /** Get the map from a callee to a caller. */
    public Map<AnalysisMethod, InvocationInfo> getCallerMap() {
        return calleeToCallerMap;
    }

    /**
     * Aggregate a set of methods that are annotated with {@link RestrictHeapAccess}, or are called
     * from those methods.
     */
    public Map<AnalysisMethod, InvocationInfo> aggregateMethods(Collection<AnalysisMethod> methods) {
        /* Build the list of allocating methods. */
        assert !initialized : "RestrictHeapAccessCallees.aggregateMethods: Should only initialize once.";
        final Map<AnalysisMethod, InvocationInfo> aggregation = new HashMap<>();
        final MethodAggregator visitor = new MethodAggregator(aggregation, assertionErrorConstructorList);
        final AnalysisMethodCalleeWalker walker = new AnalysisMethodCalleeWalker();
        for (AnalysisMethod method : methods) {
            /*
             * Find methods annotated with with either RestrictHeapAccess(access = NO_ALLOCATION) or
             * Uninterruptible.
             */
            final RestrictHeapAccess restrictHeapAccessAnnotation = method.getAnnotation(RestrictHeapAccess.class);
            final Uninterruptible uninterruptibleAnnotation = method.getAnnotation(Uninterruptible.class);
            if ((restrictHeapAccessAnnotation != null && restrictHeapAccessAnnotation.access() == Access.NO_ALLOCATION) || uninterruptibleAnnotation != null) {
                /* Walk all the implementations of the annotated method. */
                for (AnalysisMethod calleeImpl : method.getImplementations()) {
                    walker.walkMethod(calleeImpl, visitor);
                }
            }
        }
        /* Assign the set to the visible state. */
        calleeToCallerMap = Collections.unmodifiableMap(aggregation);
        initialized = true;
        return calleeToCallerMap;
    }

    /**
     * During analysis, the ResolvedJavaMethod parameter will be an AnalysisMethod, but during
     * compilation it will be a HostedMethod. Since I am using the AnalysisMethod as the key to the
     * map, I get an AnalysisMethod to use as the key.
     */
    private static AnalysisMethod methodToKey(ResolvedJavaMethod method) {
        final AnalysisMethod result;
        if (method instanceof AnalysisMethod) {
            result = (AnalysisMethod) method;
        } else if (method instanceof HostedMethod) {
            result = ((HostedMethod) method).getWrapped();
        } else {
            throw VMError.shouldNotReachHere("RestrictHeapAccessCallees.methodToKey: ResolvedJavaMethod is neither an AnalysisMethod nor a HostedMethod: " + method);
        }
        return result;
    }

    /** A visitor that aggregates callees and a callee-to-caller edge in the call graph. */
    static class MethodAggregator extends AnalysisMethodCalleeWalker.CallPathVisitor {

        /** The map from a callee to a caller that is being constructed. */
        private final Map<AnalysisMethod, InvocationInfo> calleeToCallerMap;

        /** The constructor {@link AssertionError#AssertionError()}. */
        private final List<ResolvedJavaMethod> assertionErrorConstructorList;

        /** Constructor. */
        MethodAggregator(Map<AnalysisMethod, InvocationInfo> calleeToCallerMap, List<ResolvedJavaMethod> assertionErrorConstructorList) {
            this.calleeToCallerMap = calleeToCallerMap;
            this.assertionErrorConstructorList = assertionErrorConstructorList;
        }

        /** Visit a method and add it to the set of methods that should not allocate. */
        @Override
        public VisitResult visitMethod(AnalysisMethod callee, AnalysisMethod caller, Invoke invoke, int depth) {
            if ((assertionErrorConstructorList != null) && assertionErrorConstructorList.contains(callee)) {
                /*
                 * Pretend that an AssertionError constructor is annotated
                 * with @RestrictHeapAccess(access = Access.UNRESTRICTED, overridesCallers = true).
                 * Allocations of AssertionError instances are taken out later, in
                 * ImplicitExceptionsPlugin.handleNewInstance(GraphBuilderContext, ResolvedJavaType)
                 */
                return VisitResult.CUT;
            }
            final RestrictHeapAccess restrictHeapAccessAnnotation = callee.getAnnotation(RestrictHeapAccess.class);
            if (restrictHeapAccessAnnotation != null && restrictHeapAccessAnnotation.access() == Access.UNRESTRICTED && restrictHeapAccessAnnotation.overridesCallers()) {
                /* The method is annotated as being on the white list, so cut the traversal. */
                return VisitResult.CUT;
            }
            if (calleeToCallerMap.containsKey(callee)) {
                /* The method is already a known callee, so cut the traversal. */
                return VisitResult.CUT;
            }
            /* A new callee: link it on to the map of callers. */
            final InvocationInfo invocationInfo = (caller != null
                            ? new InvocationInfo(caller, caller.asStackTraceElement(invoke.bci()), callee)
                            : InvocationInfo.nullInstance());
            calleeToCallerMap.put(callee, invocationInfo);
            return VisitResult.CONTINUE;
        }
    }

    /** Information about an invocation, for error messages. */
    public static class InvocationInfo {

        /** The caller in the invocation. */
        private final AnalysisMethod caller;
        /** The stack trace element of the invocation. */
        private final StackTraceElement invocationStackTraceElement;
        /* The callee in the invocation. */
        private final AnalysisMethod callee;

        /** The singleton null instance. */
        private static final InvocationInfo nullCallerInfo = new InvocationInfo(null, null, null);

        InvocationInfo(AnalysisMethod caller, StackTraceElement stackTraceElement, AnalysisMethod callee) {
            this.caller = caller;
            this.invocationStackTraceElement = stackTraceElement;
            this.callee = callee;
        }

        public AnalysisMethod getCaller() {
            return caller;
        }

        public StackTraceElement getInvocationStackTraceElement() {
            return invocationStackTraceElement;
        }

        public AnalysisMethod getCallee() {
            return callee;
        }

        public static InvocationInfo nullInstance() {
            return nullCallerInfo;
        }

        public boolean isNullInstance() {
            return (this == nullCallerInfo);
        }
    }
}

@AutomaticFeature
class RestrictHeapAccessCalleesFeature implements Feature {

    /** This is called early, to register in the VMConfiguration. */
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(RestrictHeapAccessCallees.class, new RestrictHeapAccessCallees());
    }

    /** This is called during analysis, to find the AssertionError constructors. */
    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        List<ResolvedJavaMethod> assertionErrorConstructorList = initializeAssertionErrorConstructors(access);
        ImageSingletons.lookup(RestrictHeapAccessCallees.class).setAssertionErrorConstructors(assertionErrorConstructorList);
    }

    private static List<ResolvedJavaMethod> initializeAssertionErrorConstructors(DuringAnalysisAccess access) {
        final List<ResolvedJavaMethod> result = new ArrayList<>();
        result.add(findAssertionConstructor(access));
        result.add(findAssertionConstructor(access, boolean.class));
        result.add(findAssertionConstructor(access, char.class));
        result.add(findAssertionConstructor(access, int.class));
        result.add(findAssertionConstructor(access, long.class));
        result.add(findAssertionConstructor(access, float.class));
        result.add(findAssertionConstructor(access, double.class));
        result.add(findAssertionConstructor(access, Object.class));
        result.add(findAssertionConstructor(access, String.class, Throwable.class));
        return result;
    }

    /** Look up an AssertionError constructor. */
    private static ResolvedJavaMethod findAssertionConstructor(DuringAnalysisAccess access, Class<?>... parameterTypes) {
        try {
            final Constructor<AssertionError> reflectiveConstructor = AssertionError.class.getConstructor(parameterTypes);
            final ResolvedJavaMethod resolvedConstructor = ((DuringAnalysisAccessImpl) access).getMetaAccess().lookupJavaMethod(reflectiveConstructor);
            return resolvedConstructor;
        } catch (NoSuchMethodException | SecurityException ex) {
            throw VMError.shouldNotReachHere("Should have found AssertionError constructor." + ex);
        }
    }
}
