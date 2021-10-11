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

import com.oracle.graal.pointsto.AbstractReachabilityAnalysis;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.typestate.TypeState;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

import static jdk.vm.ci.common.JVMCIError.shouldNotReachHere;

public abstract class ReachabilityAnalysis extends AbstractReachabilityAnalysis {

    private final MethodSummaryProvider methodSummaryProvider;
    private final AnalysisType objectType;

    public ReachabilityAnalysis(OptionValues options, AnalysisUniverse universe, HostedProviders providers, HostVM hostVM, ForkJoinPool executorService, Runnable heartbeatCallback,
                    UnsupportedFeatures unsupportedFeatures, MethodSummaryProvider methodSummaryProvider) {
        super(options, universe, providers, hostVM, executorService, heartbeatCallback, unsupportedFeatures);
        this.methodSummaryProvider = methodSummaryProvider;
        this.objectType = metaAccess.lookupJavaType(Object.class);
    }

    @Override
    public AnalysisType addRootClass(AnalysisType type, boolean addFields, boolean addArrayClass) {
        try (Indent indent = debug.logAndIndent("add root class %s", type.getName())) {
            for (AnalysisField field : type.getInstanceFields(false)) {
                if (addFields) {
                    field.registerAsAccessed();
                }
            }

            markTypeReachable(type);

            if (type.getSuperclass() != null) {
                addRootClass(type.getSuperclass(), addFields, addArrayClass);
            }
            if (addArrayClass) {
                addRootClass(type.getArrayClass(), false, false);
            }
        }
        return type;
    }

    @Override
    public AnalysisType addRootField(Class<?> clazz, String fieldName) {
        AnalysisType type = addRootClass(clazz, false, false);
        for (AnalysisField field : type.getInstanceFields(true)) {
            if (field.getName().equals(fieldName)) {
                try (Indent indent = debug.logAndIndent("add root field %s in class %s", fieldName, clazz.getName())) {
                    field.registerAsAccessed();
                }
                return field.getType();
            }
        }
        throw shouldNotReachHere("field not found: " + fieldName);
    }

    @Override
    public AnalysisMethod addRootMethod(AnalysisMethod method) {
        if (!method.registerAsRootMethod()) {
            return method;
        }
        if (!method.isStatic()) {
            markTypeInstantiated(method.getDeclaringClass());
        }
        method.registerAsInvoked(null);
        markMethodImplementationInvoked(method, new RuntimeException().getStackTrace());
        return method;
    }

    @Override
    public void markMethodImplementationInvoked(AnalysisMethod method, Object reason) {
        if (method == null) {
            System.err.println("Null method received");
            System.out.println("reson: " + reason);
            new RuntimeException().printStackTrace();
            return;
        }
        if (!method.registerAsImplementationInvoked(null)) {
            return;
        }
        schedule(() -> onMethodImplementationInvoked(method));
    }

    private static final Set<AnalysisMethod> processed = ConcurrentHashMap.newKeySet();
    private static final Set<String> processed2 = ConcurrentHashMap.newKeySet();

    private void onMethodImplementationInvoked(AnalysisMethod method) {
        if (!processed.add(method)) {
            System.err.println("Method " + method + " has already been processed");
// return;
        }
        if (!processed2.add(method.getQualifiedName())) {
            System.err.println("Method " + method + " has already been processed");
// return;
        }
        if (method.isNative()) {
            System.err.println("native method " + method);
// return;
        }
        try {
            MethodSummary summary = methodSummaryProvider.getSummary(this, method);
            processSummary(method, summary);
        } catch (Throwable ex) {
            System.err.println("Failed to provide a summary for " + method.format("%H.%n(%p)"));
            System.err.println(ex + " " + ex.getMessage());
            System.err.println("Parsing reason: " + method.getReason());
            ex.printStackTrace();
        }
    }

    private void processSummary(AnalysisMethod method, MethodSummary summary) {
        for (AnalysisMethod invokedMethod : summary.invokedMethods) {
            markMethodInvoked(invokedMethod);
        }
        for (AnalysisMethod invokedMethod : summary.implementationInvokedMethods) {
// markMethodInvoked(invokedMethod);
            markMethodImplementationInvoked(invokedMethod, method);
        }
        for (AnalysisType type : summary.accessedTypes) {
            markTypeReachable(type);
        }
        for (AnalysisType type : summary.instantiatedTypes) {
            markTypeInstantiated(type);
        }
        for (AnalysisField field : summary.readFields) {
            markFieldRead(field);
        }
        for (AnalysisField field : summary.writtenFields) {
            markFieldWritten(field);
        }
        for (JavaConstant constant : summary.embeddedConstants) {
            if (constant.getJavaKind() == JavaKind.Object && constant.isNonNull()) {
                // todo heap initiate scanning
                // track the constant
                if (this.scanningPolicy().trackConstant(this, constant)) {
                    BytecodePosition position = new BytecodePosition(null, method, 0);
                    getUniverse().registerEmbeddedRoot(constant, position);

                    Object obj = getSnippetReflectionProvider().asObject(Object.class, constant);
                    AnalysisType type = getMetaAccess().lookupJavaType(obj.getClass());
                    markTypeInHeap(type);
                }
            }
        }
    }

    @Override
    public void markFieldAccessed(AnalysisField field) {
        field.registerAsAccessed();
    }

    @Override
    public void markFieldRead(AnalysisField field) {
        field.registerAsRead(null);
    }

    @Override
    public void markFieldWritten(AnalysisField field) {
        field.registerAsWritten(null);
    }

    @Override
    public void markTypeReachable(AnalysisType type) {
        // todo double check whether all necessary logic is in
        type.registerAsReachable();
    }

    @Override
    public void markTypeInHeap(AnalysisType type) {
        markTypeInstantiated(type);
        type.registerAsInHeap();
    }

    public void markTypeInstantiated(AnalysisType type) {
        if (!type.registerAsAllocated(null)) {
            return;
        }
        AnalysisType current = type;
        while (current != null) {
            Set<AnalysisMethod> invokedMethods = current.getInvokedMethods();
            for (AnalysisMethod method : invokedMethods) {
                AnalysisMethod implementationInvokedMethod = type.resolveConcreteMethod(method, current);
                if (implementationInvokedMethod == null) {
                    System.out.println("onMethodInvoked: method " + method + " on type " + current + " is null");
                    continue;
                }
                markMethodImplementationInvoked(implementationInvokedMethod, type); // todo better
                                                                                    // reason
            }
            current = current.getSuperclass();
        }
    }

    private void markMethodInvoked(AnalysisMethod method) {
        if (!method.registerAsInvoked(null)) {
            return;
        }
        schedule(() -> onMethodInvoked(method));
    }

    private void onMethodInvoked(AnalysisMethod method) {
        AnalysisType clazz = method.getDeclaringClass();
        Set<AnalysisType> instantiatedSubtypes = clazz.getInstantiatedSubtypes();
        for (AnalysisType subtype : instantiatedSubtypes) {
            AnalysisMethod resolvedMethod = subtype.resolveConcreteMethod(method, clazz);
            if (resolvedMethod == null) {
                System.out.println("onMethodInvoked: method " + method + " on type " + subtype + " is null");
                continue;
            }
            markMethodImplementationInvoked(resolvedMethod, method); // todo better reason
        }
    }

    @Override
    public boolean finish() throws InterruptedException {
        universe.setAnalysisDataValid(false);
        // todo transform into a real 'run until fixpoint' loop
        for (int i = 0; i < 10; i++) {
            if (!executor.isStarted()) {
                executor.start();
            }
            executor.complete();
            executor.shutdown();
            executor.init(timing);

            checkObjectGraph();
        }
        universe.setAnalysisDataValid(true);
        return true;
    }

    private ObjectScanner.ReusableSet scannedObjects = new ObjectScanner.ReusableSet();

    @SuppressWarnings("try")
    private void checkObjectGraph() throws InterruptedException {
        scannedObjects.reset();
        // scan constants
        boolean isParallel = PointstoOptions.ScanObjectsParallel.getValue(options);
        ObjectScanner objectScanner = new ReachabilityObjectScanner(this, isParallel ? executor : null, scannedObjects, metaAccess);
        checkObjectGraph(objectScanner);
        if (isParallel) {
            executor.start();
            objectScanner.scanBootImageHeapRoots(null, null);
            executor.complete();
            executor.shutdown();
            executor.init(null);
        } else {
            objectScanner.scanBootImageHeapRoots(null, null);
        }
    }

    protected abstract void checkObjectGraph(ObjectScanner objectScanner);

    @Override
    public void cleanupAfterAnalysis() {
        super.cleanupAfterAnalysis();
    }

    @Override
    public void forceUnsafeUpdate(AnalysisField field) {
        // todo what to do?
    }

    @Override
    public void registerAsJNIAccessed(AnalysisField field, boolean writable) {
        // todo what to do?
    }

    @Override
    public TypeState getAllSynchronizedTypeState() {
        // todo don't overapproximate so much
        return objectType.getTypeFlow(this, true).getState();
    }

    public void processGraph(StructuredGraph graph) {
        MethodSummary summary = methodSummaryProvider.getSummary(this, graph);
        // todo figure out what to pass
        processSummary(null, summary);
    }
}
