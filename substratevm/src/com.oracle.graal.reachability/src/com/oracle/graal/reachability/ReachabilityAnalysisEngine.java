/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.reachability;

import java.lang.reflect.Executable;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;

import com.oracle.graal.pointsto.AbstractAnalysisEngine;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.CompletionExecutor;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.TimerCollection;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * Core class of the Reachability Analysis. Contains the crucial part: resolving virtual methods.
 * The resolving is done in two directions. Whenever a new method is marked as virtually invoked,
 * see {@link #onMethodInvoked(ReachabilityAnalysisMethod, Object)}, and whenever a new type is
 * marked as instantiated, see {@link #onTypeInstantiated(ReachabilityAnalysisType,Object)}.
 *
 * @see MethodSummary
 * @see MethodSummaryProvider
 * @see SimpleInMemoryMethodSummaryProvider
 * @see ReachabilityObjectScanner
 * @see ReachabilityAnalysisType
 * @see ReachabilityAnalysisMethod
 */
public abstract class ReachabilityAnalysisEngine extends AbstractAnalysisEngine {
    private final Timer reachabilityTimer;
    private final Set<AnalysisType> allInstantiatedTypes;

    private final ReachabilityMethodProcessingHandler reachabilityMethodProcessingHandler;

    public ReachabilityAnalysisEngine(OptionValues options, AnalysisUniverse universe, HostedProviders providers, HostVM hostVM, ForkJoinPool executorService, Runnable heartbeatCallback,
                    UnsupportedFeatures unsupportedFeatures, TimerCollection timerCollection,
                    ReachabilityMethodProcessingHandler reachabilityMethodProcessingHandler) {
        super(options, universe, providers, hostVM, executorService, heartbeatCallback, unsupportedFeatures, timerCollection);
        this.executor.init(getTiming());
        this.reachabilityTimer = timerCollection.createTimer("(reachability)");

        ReachabilityAnalysisType objectType = (ReachabilityAnalysisType) metaAccess.lookupJavaType(Object.class);
        this.allInstantiatedTypes = Collections.unmodifiableSet(objectType.getInstantiatedSubtypes());
        this.reachabilityMethodProcessingHandler = reachabilityMethodProcessingHandler;
    }

    /**
     * Timing is not implemented ATM.
     */
    @Override
    protected CompletionExecutor.Timing getTiming() {
        return null;
    }

    @Override
    public AnalysisType addRootClass(Class<?> clazz, boolean addFields, boolean addArrayClass) {
        AnalysisType type = metaAccess.lookupJavaType(clazz);
        return addRootClass(type, addFields, addArrayClass);
    }

    @Override
    public AnalysisMethod addRootMethod(Executable method, boolean invokeSpecial) {
        return addRootMethod(metaAccess.lookupJavaMethod(method), invokeSpecial);
    }

    @SuppressWarnings("try")
    @Override
    public AnalysisType addRootClass(AnalysisType type, boolean addFields, boolean addArrayClass) {
        type.registerAsReachable("root class");
        for (AnalysisField field : type.getInstanceFields(false)) {
            if (addFields) {
                field.registerAsAccessed("field of root class");
            }
        }

        if (type.getSuperclass() != null) {
            addRootClass(type.getSuperclass(), addFields, addArrayClass);
        }
        if (addArrayClass) {
            addRootClass(type.getArrayClass(), false, false);
        }
        return type;
    }

    @SuppressWarnings("try")
    @Override
    public AnalysisType addRootField(Class<?> clazz, String fieldName) {
        AnalysisType type = addRootClass(clazz, false, false);
        for (AnalysisField field : type.getInstanceFields(true)) {
            if (field.getName().equals(fieldName)) {
                field.registerAsAccessed("root field");
                return field.getType();
            }
        }
        throw AnalysisError.userError("Field not found: " + fieldName);
    }

    @Override
    public AnalysisMethod addRootMethod(AnalysisMethod m, boolean invokeSpecial) {
        ReachabilityAnalysisMethod method = (ReachabilityAnalysisMethod) m;
        if (m.isStatic()) {
            if (!method.registerAsDirectRootMethod()) {
                return method;
            }
            markMethodImplementationInvoked(method, "root method");
        } else if (invokeSpecial) {
            AnalysisError.guarantee(!method.isAbstract(), "Abstract methods cannot be registered as special invoke entry point.");
            if (!method.registerAsDirectRootMethod()) {
                return method;
            }
            markMethodImplementationInvoked(method, "root method");
        } else {
            if (!method.registerAsVirtualRootMethod()) {
                return method;
            }
            markMethodInvoked(method, "root method");
        }
        return method;
    }

    public void markMethodImplementationInvoked(ReachabilityAnalysisMethod method, Object reason) {
        // Unlinked methods cannot be parsed
        if (!method.getWrapped().getDeclaringClass().isLinked()) {
            return;
        }
        if (!method.registerAsImplementationInvoked(reason)) {
            return;
        }
        schedule(() -> onMethodImplementationInvoked(method));
    }

    @SuppressWarnings("try")
    private void onMethodImplementationInvoked(ReachabilityAnalysisMethod method) {
        try {
            reachabilityMethodProcessingHandler.onMethodReachable(this, method);
        } catch (Throwable ex) {
            getUnsupportedFeatures().addMessage(method.format("%H.%n(%p)"), method, ex.getLocalizedMessage(), null, ex);
        }
    }

    @Override
    public boolean registerTypeAsInHeap(AnalysisType t, Object reason) {
        ReachabilityAnalysisType type = (ReachabilityAnalysisType) t;
        if (!type.registerAsInHeap(reason)) {
            return false;
        }
        if (type.registerAsInstantiated()) {
            schedule(() -> onTypeInstantiated(type, reason));
        }
        return true;
    }

    @Override
    public boolean registerTypeAsAllocated(AnalysisType t, Object reason) {
        ReachabilityAnalysisType type = (ReachabilityAnalysisType) t;
        if (!type.registerAsAllocated(reason)) {
            return false;
        }
        if (type.registerAsInstantiated()) {
            schedule(() -> onTypeInstantiated(type, reason));
        }
        return true;
    }

    /**
     * Processes an embedded constant found in a method graph/summary.
     */
    public void handleEmbeddedConstant(ReachabilityAnalysisMethod method, JavaConstant constant, Object reason) {
        if (constant.getJavaKind() == JavaKind.Object && constant.isNonNull()) {
            if (scanningPolicy().trackConstant(this, constant)) {
                BytecodePosition position = new BytecodePosition(null, method, 0);
                getUniverse().registerEmbeddedRoot(constant, position);

                Object obj = getSnippetReflectionProvider().asObject(Object.class, constant);
                AnalysisType type = getMetaAccess().lookupJavaType(obj.getClass());
                registerTypeAsInHeap(type, reason);
            }
        }
    }

    /**
     * We collect all instantiated subtypes of each type and then use this set to resolve the
     * virtual call.
     */
    private void onMethodInvoked(ReachabilityAnalysisMethod method, Object reason) {
        ReachabilityAnalysisType clazz = method.getDeclaringClass();

        if (method.isStatic()) {
            markMethodImplementationInvoked(method, reason);
            return;
        }

        Set<ReachabilityAnalysisType> instantiatedSubtypes = clazz.getInstantiatedSubtypes();
        for (ReachabilityAnalysisType subtype : instantiatedSubtypes) {
            ReachabilityAnalysisMethod resolvedMethod = subtype.resolveConcreteMethod(method, clazz);
            if (resolvedMethod == null) {
                continue;
            }
            markMethodImplementationInvoked(resolvedMethod, reason);
        }
    }

    /**
     * Whenever a type is instantiated, it can potentially contain implementations for virtual
     * methods that are already marked as invoked. That's why we memorize for each class its invoked
     * virtual methods and then, when a new type is instantiated, we walk the type hierarchy upwards
     * and try to resolve invoked virtual methods on our new instantiated type.
     *
     * This is expensive, essentially quadratic O(DEPTH_OF_TYPE_HIERARCHY *
     * NUMBER_OF_INVOKED_METHODS_ON_TYPE). and is one of the places that we should try to optimize
     * in near future.
     */
    private void onTypeInstantiated(ReachabilityAnalysisType type, Object reason) {
        type.forAllSuperTypes(current -> {
            Set<ReachabilityAnalysisMethod> invokedMethods = ((ReachabilityAnalysisType) current).getInvokedVirtualMethods();
            for (ReachabilityAnalysisMethod curr : invokedMethods) {
                ReachabilityAnalysisMethod method = type.resolveConcreteMethod(curr, current);
                if (method != null) {
                    markMethodImplementationInvoked(method, reason);
                }
            }
        });
    }

    public void markMethodInvoked(ReachabilityAnalysisMethod method, Object reason) {
        if (!method.registerAsInvoked(reason)) {
            return;
        }
        schedule(() -> onMethodInvoked(method, reason));
    }

    @Override
    public boolean finish() throws InterruptedException {
        universe.setAnalysisDataValid(false);
        runReachability();
        assert executor.getPostedOperations() == 0;
        universe.setAnalysisDataValid(true);
        return true;
    }

    @SuppressWarnings("try")
    private void runReachability() throws InterruptedException {
        try (Timer.StopTimer t = reachabilityTimer.start()) {
            executor.start();
            executor.complete();
            executor.shutdown();
            executor.init(getTiming());
        }
    }

    @Override
    public void afterAnalysis() {
        computeCallers();
    }

    /**
     * Performs a single traversal over the call graph from roots to compute the callers of each
     * method.
     */
    private void computeCallers() {
        Set<ReachabilityAnalysisMethod> seen = new HashSet<>();
        Deque<ReachabilityAnalysisMethod> queue = new ArrayDeque<>();

        for (AnalysisMethod m : universe.getMethods()) {
            ReachabilityAnalysisMethod method = ((ReachabilityAnalysisMethod) m);
            if (method.isDirectRootMethod() || method.isEntryPoint()) {
                if (seen.add(method)) {
                    queue.add(method);
                }
            }
            if (method.isVirtualRootMethod()) {
                for (ReachabilityAnalysisType subtype : method.getDeclaringClass().getInstantiatedSubtypes()) {
                    ReachabilityAnalysisMethod resolved = subtype.resolveConcreteMethod(method, subtype);
                    if (resolved != null) {
                        if (seen.add(resolved)) {
                            queue.add(resolved);
                        }
                    }
                }
            }
        }

        while (!queue.isEmpty()) {
            ReachabilityAnalysisMethod method = queue.removeFirst();
            for (InvokeInfo invoke : method.getInvokes()) {
                for (AnalysisMethod c : invoke.getCallees()) {
                    ReachabilityAnalysisMethod callee = (ReachabilityAnalysisMethod) c;
                    callee.addCaller(invoke.getPosition());
                    if (seen.add(callee)) {
                        callee.setReason(invoke.getPosition());
                        queue.add(callee);
                    }
                }
            }
        }

    }

    @Override
    public void forceUnsafeUpdate(AnalysisField field) {
    }

    @Override
    public void registerAsJNIAccessed(AnalysisField field, boolean writable) {
    }

    /**
     * We cannot resolve all the objects used in synchronized blocks, so we have to overapproximate
     * and consider all instantiated types.
     */
    @Override
    public Iterable<AnalysisType> getAllSynchronizedTypes() {
        return getAllInstantiatedTypes();
    }

    @Override
    public Iterable<AnalysisType> getAllInstantiatedTypes() {
        return allInstantiatedTypes;
    }

    @SuppressWarnings("try")
    public void processGraph(StructuredGraph graph) {
        reachabilityMethodProcessingHandler.processGraph(this, graph);
    }

}
