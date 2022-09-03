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

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.RestrictHeapAccess.Access;
import com.oracle.svm.core.heap.RestrictHeapAccessCallees;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Construct a list of all the methods that are, or are called from, methods annotated with
 * {@link RestrictHeapAccess} or {@link Uninterruptible}.
 */
public class RestrictHeapAccessCalleesImpl implements RestrictHeapAccessCallees {

    /**
     * A map from a callee to a caller on a path to an annotated caller. The keys are the set of
     * callees that must not allocate. The values are for printing a call path for error messages.
     */
    private Map<AnalysisMethod, RestrictionInfo> calleeToCallerMap;

    /** AssertionErrors are cut points, because their allocations are removed. */
    private List<ResolvedJavaMethod> assertionErrorConstructorList;

    /** Initialize the set of callees only once. */
    private boolean initialized;

    /** Constructor for the singleton instance. */
    public RestrictHeapAccessCalleesImpl() {
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

    public RestrictionInfo getRestrictionInfo(ResolvedJavaMethod method) {
        return calleeToCallerMap.get(methodToKey(method));
    }

    @Override
    public boolean mustNotAllocate(ResolvedJavaMethod method) {
        return isRestricted(method) || Uninterruptible.Utils.isUninterruptible(method);
    }

    private boolean isRestricted(ResolvedJavaMethod method) {
        RestrictionInfo info = getRestrictionInfo(method);
        return info != null && info.getAccess() != Access.UNRESTRICTED;
    }

    /** Get the map from a callee to a caller. */
    public Map<AnalysisMethod, RestrictionInfo> getCallerMap() {
        return calleeToCallerMap;
    }

    /**
     * Aggregate a set of methods that are annotated with {@link RestrictHeapAccess}, or methods
     * that are called from those methods.
     */
    public void aggregateMethods(Collection<AnalysisMethod> methods) {
        assert !initialized : "RestrictHeapAccessCallees.aggregateMethods: Should only initialize once.";
        Map<AnalysisMethod, RestrictionInfo> aggregation = new HashMap<>();
        for (AnalysisMethod method : methods) {
            if (method.isAnnotationPresent(RestrictHeapAccess.class)) {
                setMethodRestrictionInfo(method, aggregation);
            }
        }
        MethodAggregator visitor = new MethodAggregator(aggregation, assertionErrorConstructorList);
        AnalysisMethodCalleeWalker walker = new AnalysisMethodCalleeWalker();
        for (AnalysisMethod method : aggregation.keySet().toArray(new AnalysisMethod[0])) {
            walker.walkMethod(method, visitor);
        }
        calleeToCallerMap = Collections.unmodifiableMap(aggregation);
        initialized = true;
    }

    /**
     * Set RestrictionInfo for annotated method and its overrides.
     *
     * Unless the overrides themselves are annotated, treat them as if they were annotated with the
     * same annotation as the method.
     */
    private static void setMethodRestrictionInfo(AnalysisMethod method, Map<AnalysisMethod, RestrictionInfo> aggregation) {
        assert method.isAnnotationPresent(RestrictHeapAccess.class);
        if (aggregation.get(method) != null) {
            return;
        }
        for (AnalysisMethod impl : method.getImplementations()) {
            if (impl.isAnnotationPresent(RestrictHeapAccess.class) && !impl.equals(method)) {
                /* Annotated overrides take precedence, so process them first. */
                setMethodRestrictionInfo(impl, aggregation);
            }
        }
        assert aggregation.get(method) == null;
        Access access = method.getAnnotation(RestrictHeapAccess.class).access();
        aggregation.put(method, new RestrictionInfo(access, null, null, method));
        for (AnalysisMethod impl : method.getImplementations()) {
            aggregation.putIfAbsent(impl, new RestrictionInfo(access, null, null, impl));
        }
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
        private final Map<AnalysisMethod, RestrictionInfo> calleeToCallerMap;

        /** The constructor {@link AssertionError#AssertionError()}. */
        private final List<ResolvedJavaMethod> assertionErrorConstructorList;

        /** Constructor. */
        MethodAggregator(Map<AnalysisMethod, RestrictionInfo> calleeToCallerMap, List<ResolvedJavaMethod> assertionErrorConstructorList) {
            this.calleeToCallerMap = calleeToCallerMap;
            this.assertionErrorConstructorList = assertionErrorConstructorList;
        }

        /** Visit a method and add it to the set of methods that should not allocate. */
        @Override
        public VisitResult visitMethod(AnalysisMethod callee, AnalysisMethod caller, BytecodePosition invokePosition, int depth) {
            RestrictionInfo existingRestrictionInfo = calleeToCallerMap.get(callee);
            if (caller == null) {
                /* Start a new traversal. */
                assert existingRestrictionInfo != null && existingRestrictionInfo.isExplicit();
                return existingRestrictionInfo.getAccess() == Access.UNRESTRICTED ? VisitResult.CUT : VisitResult.CONTINUE;
            }

            /* Propagate caller restriction. */
            Access access = calleeToCallerMap.get(caller).getAccess();
            assert access != Access.UNRESTRICTED;

            if (existingRestrictionInfo != null && existingRestrictionInfo.isExplicit()) {
                /* No propagation to callees with explicit restrictions, so stop here. */
                return VisitResult.CUT;
            }
            if (existingRestrictionInfo != null && !access.isMoreRestrictiveThan(existingRestrictionInfo.getAccess())) {
                /* Earlier traversal with same or higher level of restriction, so stop here. */
                return VisitResult.CUT;
            }
            if (assertionErrorConstructorList != null && assertionErrorConstructorList.contains(callee)) {
                /* Ignore AssertionError allocations: ImplicitExceptionsPlugin will replace them */
                return VisitResult.CUT;
            }
            assert invokePosition != null;
            StackTraceElement callerStackTraceElement = invokePosition.getMethod().asStackTraceElement(invokePosition.getBCI());
            calleeToCallerMap.put(callee, new RestrictionInfo(access, caller, callerStackTraceElement, callee));
            return VisitResult.CONTINUE;
        }
    }

    /** Information about a restricted method, for error messages. */
    public static class RestrictionInfo {

        /** The transitively determined level of restricted access. */
        private final RestrictHeapAccess.Access access;
        /** The caller in the invocation, if any. */
        private final AnalysisMethod caller;
        /** The stack trace element of the invocation, if any. */
        private final StackTraceElement invocationStackTraceElement;
        /** The method to which the restriction applies. */
        private final AnalysisMethod method;

        RestrictionInfo(Access access, AnalysisMethod caller, StackTraceElement stackTraceElement, AnalysisMethod method) {
            this.access = access;
            this.caller = caller;
            this.invocationStackTraceElement = stackTraceElement;
            this.method = method;
        }

        /** Return whether the restriction is explicit or inferred. */
        boolean isExplicit() {
            return caller == null;
        }

        public Access getAccess() {
            return access;
        }

        public AnalysisMethod getCaller() {
            return caller;
        }

        public StackTraceElement getInvocationStackTraceElement() {
            return invocationStackTraceElement;
        }

        public AnalysisMethod getMethod() {
            return method;
        }
    }
}

@AutomaticallyRegisteredFeature
class RestrictHeapAccessCalleesFeature implements InternalFeature {

    /** This is called early, to register in the VMConfiguration. */
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(RestrictHeapAccessCallees.class, new RestrictHeapAccessCalleesImpl());
    }

    /** This is called during analysis, to find the AssertionError constructors. */
    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        List<ResolvedJavaMethod> assertionErrorConstructorList = initializeAssertionErrorConstructors(access);
        ((RestrictHeapAccessCalleesImpl) ImageSingletons.lookup(RestrictHeapAccessCallees.class)).setAssertionErrorConstructors(assertionErrorConstructorList);
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
