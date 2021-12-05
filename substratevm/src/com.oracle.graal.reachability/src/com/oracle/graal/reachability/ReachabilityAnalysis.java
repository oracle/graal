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
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.Timer;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallSignature;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.ForeignCall;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import org.graalvm.compiler.replacements.nodes.UnaryMathIntrinsicNode;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

import static jdk.vm.ci.common.JVMCIError.shouldNotReachHere;

public abstract class ReachabilityAnalysis extends AbstractReachabilityAnalysis {

    private final MethodSummaryProvider methodSummaryProvider;
    private final AnalysisType objectType;
    private final Timer summaryTimer;

    public ReachabilityAnalysis(OptionValues options, AnalysisUniverse universe, HostedProviders providers, HostVM hostVM, ForkJoinPool executorService, Runnable heartbeatCallback,
                    UnsupportedFeatures unsupportedFeatures, MethodSummaryProvider methodSummaryProvider) {
        super(options, universe, providers, hostVM, executorService, heartbeatCallback, unsupportedFeatures);
        this.methodSummaryProvider = methodSummaryProvider;
        this.objectType = metaAccess.lookupJavaType(Object.class);
        this.summaryTimer = new Timer(hostVM.getImageName(), "((summaries))", false);
    }

    @SuppressWarnings("try")
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

    @SuppressWarnings("try")
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
        method.registerAsInvoked();
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
        if (!method.registerAsImplementationInvoked()) {
            return;
        }
        schedule(() -> onMethodImplementationInvoked(method));
    }

    public final Map<AnalysisMethod, MethodSummary> summaries = new ConcurrentHashMap<>();

    @SuppressWarnings("try")
    private void onMethodImplementationInvoked(AnalysisMethod method) {
        try {
            MethodSummary summary;
            try (Timer.StopTimer t = summaryTimer.start()) {
                summary = methodSummaryProvider.getSummary(this, method);
            }
            processSummary(method, summary);
            summaries.put(method, summary);
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
            markMethodInvoked(invokedMethod);
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
        for (ForeignCallDescriptor descriptor : summary.foreignCallDescriptors) {
            registerForeignCall(descriptor);
        }
        for (ForeignCallSignature signature : summary.foreignCallSignatures) {
            registerForeignCall(getProviders().getForeignCalls().getDescriptor(signature));
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
        type.forAllSuperTypes(current -> {
            Set<AnalysisMethod> invokedMethods = current.getInvokedMethods();
            for (AnalysisMethod method : invokedMethods) {
                if (method.isStatic()) {
                    continue;
                }
                AnalysisMethod implementationInvokedMethod = type.resolveConcreteMethod(method, current);
                if (implementationInvokedMethod == null) {
// System.out.println("onMethodInvoked: method " + method + " on type " + current + " is null");
                    continue;
                }
                markMethodImplementationInvoked(implementationInvokedMethod, type); // todo better
                // reason
            }
        });
    }

    private void markMethodInvoked(AnalysisMethod method) {
        if (!method.registerAsInvoked()) {
            return;
        }
        schedule(() -> onMethodInvoked(method));
    }

    private void onMethodInvoked(AnalysisMethod method) {
        AnalysisType clazz = method.getDeclaringClass();
        Set<AnalysisType> instantiatedSubtypes = clazz.getInstantiatedSubtypes();
        if (method.isStatic()) {
            // todo better reason
            markMethodImplementationInvoked(method, null);
            return;
        }
        for (AnalysisType subtype : instantiatedSubtypes) {
            AnalysisMethod resolvedMethod = subtype.resolveConcreteMethod(method, clazz);
            if (resolvedMethod == null) {
// System.out.println("onMethodInvoked: method " + method + " on type " + subtype + " is null");
                continue;
            }
            markMethodImplementationInvoked(resolvedMethod, method); // todo better reason
        }
    }

    @Override
    public boolean finish() throws InterruptedException {
        universe.setAnalysisDataValid(false);

        int numTypes;
        do {
            runReachability();

            assert executor.getPostedOperations() == 0;
            numTypes = universe.getTypes().size();

            checkObjectGraph();

        } while (executor.getPostedOperations() != 0 || numTypes != universe.getTypes().size());

        universe.setAnalysisDataValid(true);

        return true;
    }

    @SuppressWarnings("try")
    @Override
    public void runAnalysis(DebugContext debugContext, Function<AnalysisUniverse, Boolean> analysisEndCondition) throws InterruptedException {
        // todo this is ugly copy paste from points-to
        int numIterations = 0;
        while (true) {
            try (Indent indent2 = debugContext.logAndIndent("new analysis iteration")) {
                /*
                 * Do the analysis (which itself is done in a similar iterative process)
                 */
                boolean analysisChanged = finish();

                numIterations++;
                if (numIterations > 1000) {
                    /*
                     * Usually there are < 10 iterations. If we have so many iterations, we probably
                     * have an endless loop (but at least we have a performance problem because we
                     * re-start the analysis so often).
                     */
                    throw AnalysisError.shouldNotReachHere(String.format("Static analysis did not reach a fix point after %d iterations because a Feature keeps requesting new analysis iterations. " +
                                    "The analysis itself %s find a change in type states in the last iteration.",
                                    numIterations, analysisChanged ? "DID" : "DID NOT"));
                }

                /*
                 * Allow features to change the universe.
                 */
                try (Timer.StopTimer t2 = getProcessFeaturesTimer().start()) {
                    int numTypes = universe.getTypes().size();
                    int numMethods = universe.getMethods().size();
                    int numFields = universe.getFields().size();
                    if (analysisEndCondition.apply(universe)) {
                        if (numTypes != universe.getTypes().size() || numMethods != universe.getMethods().size() || numFields != universe.getFields().size()) {
                            throw AnalysisError.shouldNotReachHere(
                                            "When a feature makes more types, methods, or fields reachable, it must require another analysis iteration via DuringAnalysisAccess.requireAnalysisIteration()");
                        }
                        return;
                    }
                }
            }
        }
    }

    @SuppressWarnings("try")
    private void runReachability() throws InterruptedException {
        try (Timer.StopTimer t = reachabilityTimer.start()) {
            if (!executor.isStarted()) {
                executor.start();
            }
            executor.complete();
            executor.shutdown();
            executor.init(timing);
        }
    }

    private final ObjectScanner.ReusableSet scannedObjects = new ObjectScanner.ReusableSet();

    @SuppressWarnings("try")
    private void checkObjectGraph() throws InterruptedException {
        try (Timer.StopTimer t = checkObjectsTimer.start()) {
            scannedObjects.reset();
            // scan constants
            boolean isParallel = PointstoOptions.ScanObjectsParallel.getValue(options);
            ObjectScanner objectScanner = new ObjectScanner(this, isParallel ? executor : null, scannedObjects, new ReachabilityObjectScanner(this, metaAccess)) {
            };
            checkObjectGraph(objectScanner);
            if (isParallel) {
                executor.start();
                objectScanner.scanBootImageHeapRoots(null, null);
                executor.complete();
                executor.shutdown();
                executor.init(timing);
            } else {
                objectScanner.scanBootImageHeapRoots(null, null);
            }
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

    @SuppressWarnings("try")
    public void processGraph(StructuredGraph graph) {
        MethodSummary summary;
        try (Timer.StopTimer t = summaryTimer.start()) {
            summary = methodSummaryProvider.getSummary(this, graph);
        }
        AnalysisMethod method = analysisMethod(graph.method());
        method.registerAsInvoked();
        method.registerAsImplementationInvoked();
        processSummary(method, summary.withoutMethods());

        registerForeignCalls(graph);
    }

    private void registerForeignCalls(StructuredGraph graph) {
        for (Node n : graph.getNodes()) {
            if (n instanceof ForeignCall) {
                ForeignCall node = (ForeignCall) n;
                registerForeignCall(node.getDescriptor());
            } else if (n instanceof UnaryMathIntrinsicNode) {
                UnaryMathIntrinsicNode node = (UnaryMathIntrinsicNode) n;
                registerForeignCall(getProviders().getForeignCalls().getDescriptor(node.getOperation().foreignCallSignature));
            } else if (n instanceof BinaryMathIntrinsicNode) {
                BinaryMathIntrinsicNode node = (BinaryMathIntrinsicNode) n;
                registerForeignCall(getProviders().getForeignCalls().getDescriptor(node.getOperation().foreignCallSignature));
            } else if (n instanceof FrameState) {
                FrameState node = (FrameState) n;
                AnalysisMethod method = (AnalysisMethod) node.getMethod();
                if (method != null) {
                    markTypeReachable(method.getDeclaringClass());
                }
            }
        }
    }

    private void registerForeignCall(ForeignCallDescriptor descriptor) {
        Optional<AnalysisMethod> targetMethod = getHostVM().handleForeignCall(descriptor, getProviders().getForeignCalls());
        targetMethod.ifPresent(this::addRootMethod);
    }

    private AnalysisMethod analysisMethod(ResolvedJavaMethod method) {
        return method instanceof AnalysisMethod ? ((AnalysisMethod) method) : universe.lookup(method);
    }

    @Override
    public void printTimers() {
        summaryTimer.print();
        super.printTimers();
    }
}
