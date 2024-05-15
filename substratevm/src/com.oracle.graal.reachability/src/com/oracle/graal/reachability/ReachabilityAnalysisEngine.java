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
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import com.oracle.graal.pointsto.AbstractAnalysisEngine;
import com.oracle.graal.pointsto.ClassInclusionPolicy;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.heap.TypedConstant;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.CompletionExecutor;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.svm.common.meta.MultiMethod;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Core class of the Reachability Analysis. Contains the crucial part: resolving virtual methods.
 * The resolving is done in two directions. Whenever a new method is marked as virtually invoked,
 * see {@link #onMethodInvoked(ReachabilityAnalysisMethod, Object)}, and whenever a new type is
 * marked as instantiated, see {@link #onTypeInstantiated(AnalysisType)}.
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

    @SuppressWarnings("this-escape")
    public ReachabilityAnalysisEngine(OptionValues options, AnalysisUniverse universe, HostVM hostVM, AnalysisMetaAccess metaAccess, SnippetReflectionProvider snippetReflectionProvider,
                    ConstantReflectionProvider constantReflectionProvider, WordTypes wordTypes, UnsupportedFeatures unsupportedFeatures, DebugContext debugContext, TimerCollection timerCollection,
                    ReachabilityMethodProcessingHandler reachabilityMethodProcessingHandler, ClassInclusionPolicy classInclusionPolicy) {
        super(options, universe, hostVM, metaAccess, snippetReflectionProvider, constantReflectionProvider, wordTypes, unsupportedFeatures, debugContext, timerCollection, classInclusionPolicy);
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
    public AnalysisMethod addRootMethod(Executable method, boolean invokeSpecial, Object reason, MultiMethod.MultiMethodKey... otherRoots) {
        return addRootMethod(metaAccess.lookupJavaMethod(method), invokeSpecial, reason, otherRoots);
    }

    @Override
    public AnalysisMethod forcedAddRootMethod(Executable method, boolean invokeSpecial, Object reason, MultiMethod.MultiMethodKey... otherRoots) {
        return addRootMethod(method, invokeSpecial, reason, otherRoots);
    }

    @SuppressWarnings("try")
    @Override
    public AnalysisType addRootClass(AnalysisType type, boolean addFields, boolean addArrayClass) {
        type.registerAsReachable("root class");
        for (ResolvedJavaField javaField : type.getInstanceFields(false)) {
            AnalysisField field = (AnalysisField) javaField;
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
        for (ResolvedJavaField javaField : type.getInstanceFields(true)) {
            AnalysisField field = (AnalysisField) javaField;
            if (field.getName().equals(fieldName)) {
                field.registerAsAccessed("root field");
                return field.getType();
            }
        }
        throw AnalysisError.userError("Field not found: " + fieldName);
    }

    @Override
    public AnalysisType addRootField(Field field) {
        AnalysisField analysisField = getMetaAccess().lookupJavaField(field);
        analysisField.registerAsAccessed("root field");
        return analysisField.getType();
    }

    @Override
    public AnalysisMethod addRootMethod(AnalysisMethod m, boolean invokeSpecial, Object reason, MultiMethod.MultiMethodKey... otherRoots) {
        assert otherRoots.length == 0 : otherRoots;
        ReachabilityAnalysisMethod method = (ReachabilityAnalysisMethod) m;
        if (m.isStatic()) {
            postTask(() -> {
                if (method.registerAsDirectRootMethod(reason)) {
                    markMethodImplementationInvoked(method, reason);
                }
            });
        } else if (invokeSpecial) {
            AnalysisError.guarantee(!method.isAbstract(), "Abstract methods cannot be registered as special invoke entry point.");
            postTask(() -> {
                if (method.registerAsDirectRootMethod(reason)) {
                    markMethodImplementationInvoked(method, reason);
                }
            });
        } else {
            postTask(() -> {
                if (method.registerAsVirtualRootMethod(reason)) {
                    markMethodInvoked(method, reason);
                }
            });
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

    public void markMethodSpecialInvoked(ReachabilityAnalysisMethod targetMethod, Object reason) {
        ReachabilityAnalysisType declaringClass = targetMethod.getDeclaringClass();
        declaringClass.addSpecialInvokedMethod(targetMethod);
        if (!declaringClass.getInstantiatedSubtypes().isEmpty()) {
            markMethodImplementationInvoked(targetMethod, reason);
        }
    }

    /* Method is overwritten so that other classes in this package can invoke it. */
    @Override
    protected void schedule(Runnable task) {
        super.schedule(task);
    }

    /**
     * Processes an embedded constant found in a method graph/summary.
     */
    public void handleEmbeddedConstant(ReachabilityAnalysisMethod method, JavaConstant constant, Object reason) {
        if (constant.getJavaKind() == JavaKind.Object && constant.isNonNull()) {
            BytecodePosition position = new BytecodePosition(null, method, 0);
            getUniverse().registerEmbeddedRoot(constant, position);

            AnalysisType type = ((TypedConstant) constant).getType();
            type.registerAsInstantiated(reason);
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
    @Override
    public void onTypeInstantiated(AnalysisType type) {
        ReachabilityAnalysisEngine bb = (ReachabilityAnalysisEngine) universe.getBigbang();
        bb.schedule(() -> {
            type.forAllSuperTypes(current -> {
                Set<ReachabilityAnalysisMethod> invokedMethods = ((ReachabilityAnalysisType) current).getInvokedVirtualMethods();
                for (ReachabilityAnalysisMethod curr : invokedMethods) {
                    ReachabilityAnalysisMethod method = (ReachabilityAnalysisMethod) type.resolveConcreteMethod(curr, current);
                    if (method != null) {
                        markMethodImplementationInvoked(method, type.getInstantiatedReason());
                    }
                }

                for (ReachabilityAnalysisMethod method : ((ReachabilityAnalysisType) current).getInvokedSpecialMethods()) {
                    markMethodImplementationInvoked(method, type.getInstantiatedReason());
                }
            });
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
        assert executor.getPostedOperations() == 0 : executor.getPostedOperations();
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
                for (AnalysisMethod c : invoke.getAllCallees()) {
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

    @Override
    public boolean trackPrimitiveValues() {
        return false;
    }
}
