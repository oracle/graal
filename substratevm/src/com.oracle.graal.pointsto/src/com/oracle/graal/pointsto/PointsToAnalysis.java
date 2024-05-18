/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto;

import static com.oracle.graal.pointsto.meta.AnalysisUniverse.ESTIMATED_NUMBER_OF_TYPES;
import static jdk.vm.ci.common.JVMCIError.shouldNotReachHere;

import java.io.PrintWriter;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.flow.AllSynchronizedTypeFlow;
import com.oracle.graal.pointsto.flow.AnyPrimitiveSourceTypeFlow;
import com.oracle.graal.pointsto.flow.FieldTypeFlow;
import com.oracle.graal.pointsto.flow.FormalParamTypeFlow;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodFlowsGraphInfo;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.MethodTypeFlowBuilder;
import com.oracle.graal.pointsto.flow.OffsetLoadTypeFlow.AbstractUnsafeLoadTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetStoreTypeFlow.AbstractUnsafeStoreTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.PointsToAnalysisField;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.reports.StatisticsPrinter;
import com.oracle.graal.pointsto.typestate.AnyPrimitiveTypeState;
import com.oracle.graal.pointsto.typestate.PointsToStats;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.CompletionExecutor;
import com.oracle.graal.pointsto.util.CompletionExecutor.DebugContextRunnable;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.Timer.StopTimer;
import com.oracle.graal.pointsto.util.TimerCollection;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.util.ClassUtil;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;

public abstract class PointsToAnalysis extends AbstractAnalysisEngine {
    /** The type of {@link java.lang.Object}. */
    private final AnalysisType objectType;
    /**
     * Enables propagating primitive values interproceduraly using the typeflow graph. Only simple
     * constants are propagated. Arithmetic operations and merges of different constants result in a
     * special {@link AnyPrimitiveTypeState } state that leads to immediate saturation.
     * <p>
     * This optimization also handles word types, which are essentially primitive values.
     * <p>
     * Unsafe loads and stores are NOT modeled, because it would lead to merging of primitive and
     * objects states (all unsafe fields are merged into a single flow). Instead, all unsafe
     * accessed primitive fields are assigned the PrimitiveTypeState state and any unsafe read is
     * immediately represented as {@link com.oracle.graal.pointsto.flow.AnyPrimitiveSourceTypeFlow}.
     */
    private final boolean trackPrimitiveValues;
    private AnyPrimitiveSourceTypeFlow anyPrimitiveSourceTypeFlow;
    private TypeFlow<?> allSynchronizedTypeFlow;

    protected final boolean trackTypeFlowInputs;
    protected final boolean reportAnalysisStatistics;

    private ConcurrentMap<AbstractUnsafeLoadTypeFlow, Boolean> unsafeLoads;
    private ConcurrentMap<AbstractUnsafeStoreTypeFlow, Boolean> unsafeStores;

    public final AtomicLong numParsedGraphs = new AtomicLong();
    private final CompletionExecutor.Timing timing;

    public final Timer typeFlowTimer;

    @SuppressWarnings("this-escape")
    public PointsToAnalysis(OptionValues options, AnalysisUniverse universe, HostVM hostVM, AnalysisMetaAccess metaAccess, SnippetReflectionProvider snippetReflectionProvider,
                    ConstantReflectionProvider constantReflectionProvider, WordTypes wordTypes, UnsupportedFeatures unsupportedFeatures, DebugContext debugContext, TimerCollection timerCollection,
                    ClassInclusionPolicy classInclusionPolicy) {
        super(options, universe, hostVM, metaAccess, snippetReflectionProvider, constantReflectionProvider, wordTypes, unsupportedFeatures, debugContext, timerCollection, classInclusionPolicy);
        this.typeFlowTimer = timerCollection.createTimer("(typeflow)");
        this.trackPrimitiveValues = PointstoOptions.TrackPrimitiveValues.getValue(options);
        this.anyPrimitiveSourceTypeFlow = new AnyPrimitiveSourceTypeFlow(null, null);

        this.objectType = metaAccess.lookupJavaType(Object.class);
        /*
         * Make sure the all-instantiated type flow is created early. We do not have any
         * instantiated types yet, so the state is empty at first.
         */
        objectType.getTypeFlow(this, true);
        allSynchronizedTypeFlow = new AllSynchronizedTypeFlow();

        trackTypeFlowInputs = PointstoOptions.TrackInputFlows.getValue(options);
        reportAnalysisStatistics = PointstoOptions.PrintPointsToStatistics.getValue(options);
        if (reportAnalysisStatistics) {
            PointsToStats.init(this);
        }

        unsafeLoads = new ConcurrentHashMap<>();
        unsafeStores = new ConcurrentHashMap<>();

        timing = PointstoOptions.ProfileAnalysisOperations.getValue(options) ? new AnalysisTiming() : null;
        executor.init(timing);
    }

    @Override
    protected CompletionExecutor.Timing getTiming() {
        return timing;
    }

    @Override
    public void printTimerStatistics(PrintWriter out) {
        StatisticsPrinter.print(out, "typeflow_time_ms", typeFlowTimer.getTotalTime());
        StatisticsPrinter.print(out, "verify_time_ms", verifyHeapTimer.getTotalTime());
        StatisticsPrinter.print(out, "features_time_ms", processFeaturesTimer.getTotalTime());
        StatisticsPrinter.print(out, "total_analysis_time_ms", analysisTimer.getTotalTime());

        StatisticsPrinter.printLast(out, "total_memory_bytes", analysisTimer.getTotalMemory());
    }

    public boolean trackTypeFlowInputs() {
        return trackTypeFlowInputs;
    }

    public boolean reportAnalysisStatistics() {
        return reportAnalysisStatistics;
    }

    public MethodTypeFlowBuilder createMethodTypeFlowBuilder(PointsToAnalysis bb, PointsToAnalysisMethod method, MethodFlowsGraph flowsGraph, MethodFlowsGraph.GraphKind graphKind) {
        return new MethodTypeFlowBuilder(bb, method, flowsGraph, graphKind);
    }

    public void registerUnsafeLoad(AbstractUnsafeLoadTypeFlow unsafeLoad) {
        unsafeLoads.putIfAbsent(unsafeLoad, true);
    }

    public void registerUnsafeStore(AbstractUnsafeStoreTypeFlow unsafeStore) {
        unsafeStores.putIfAbsent(unsafeStore, true);
    }

    /**
     * Force update of the unsafe loads and unsafe store type flows when a field is registered as
     * unsafe accessed 'on the fly', i.e., during the analysis.
     *
     * @param field the newly unsafe registered field. We use its declaring type to filter the
     *            unsafe access flows that need to be updated.
     */
    public void forceUnsafeUpdate(AnalysisField field) {
        /*
         * It is cheaper to post the flows of all loads and stores even if they are not related to
         * the provided field.
         */

        // force update of the unsafe loads
        for (AbstractUnsafeLoadTypeFlow unsafeLoad : unsafeLoads.keySet()) {
            /* Force update for unsafe accessed static fields. */
            unsafeLoad.forceUpdate(this);

            /*
             * Force update for unsafe accessed instance fields: post the receiver object flow for
             * update; an update of the receiver object flow will trigger an updated of the
             * observers, i.e., of the unsafe load.
             */
            this.postFlow(unsafeLoad.receiver());
        }

        // force update of the unsafe stores
        for (AbstractUnsafeStoreTypeFlow unsafeStore : unsafeStores.keySet()) {
            /* Force update for unsafe accessed static fields. */
            unsafeStore.forceUpdate(this);

            /*
             * Force update for unsafe accessed instance fields: post the receiver object flow for
             * update; an update of the receiver object flow will trigger an updated of the
             * observers, i.e., of the unsafe store.
             */
            this.postFlow(unsafeStore.receiver());
        }
    }

    @Override
    public void registerAsJNIAccessed(AnalysisField f, boolean writable) {
        PointsToAnalysisField field = (PointsToAnalysisField) f;
        // Same as addRootField() and addRootStaticField():
        // create type flows for any subtype of the field's declared type
        TypeFlow<?> declaredTypeFlow = field.getType().getTypeFlow(this, true);
        if (isSupportedJavaKind(field.getStorageKind())) {
            if (field.isStatic()) {
                if (field.getStorageKind().isObject()) {
                    declaredTypeFlow.addUse(this, field.getStaticFieldFlow());
                } else {
                    field.saturatePrimitiveField();
                }
            } else {
                FieldTypeFlow instanceFieldFlow = field.getDeclaringClass().getContextInsensitiveAnalysisObject().getInstanceFieldFlow(this, field, writable);
                if (field.getStorageKind().isObject()) {
                    declaredTypeFlow.addUse(this, instanceFieldFlow);
                } else {
                    field.saturatePrimitiveField();
                }
            }
        }
    }

    /**
     * By default the analysis tracks all concrete objects for all types (if the analysis is context
     * sensitive). However, the client of the analysis can opt that some types should be analyzed
     * without tracking concrete objects even when the analysis is context sensitive.
     */
    public boolean trackConcreteAnalysisObjects(@SuppressWarnings("unused") AnalysisType type) {
        return true;
    }

    @Override
    public void cleanupAfterAnalysis() {
        super.cleanupAfterAnalysis();
        allSynchronizedTypeFlow = null;
        anyPrimitiveSourceTypeFlow = null;
        unsafeLoads = null;
        unsafeStores = null;

        ConstantObjectsProfiler.constantTypes.clear();
    }

    public AnalysisType lookup(JavaType type) {
        return universe.lookup(type);
    }

    public AnalysisType getObjectType() {
        return universe.objectType();
    }

    public AnalysisType getObjectArrayType() {
        return metaAccess.lookupJavaType(Object[].class);
    }

    public TypeFlow<?> getAllInstantiatedTypeFlow() {
        return objectType.getTypeFlow(this, true);
    }

    @Override
    public Iterable<AnalysisType> getAllInstantiatedTypes() {
        return getAllInstantiatedTypeFlow().getState().types(this);
    }

    public TypeFlow<?> getAllSynchronizedTypeFlow() {
        return allSynchronizedTypeFlow;
    }

    public AnyPrimitiveSourceTypeFlow getAnyPrimitiveSourceTypeFlow() {
        return anyPrimitiveSourceTypeFlow;
    }

    @Override
    public Iterable<AnalysisType> getAllSynchronizedTypes() {
        /*
         * If all-synchrnonized type flow, i.e., the type flow that keeps track of the types of all
         * monitor objects, is saturated then we need to assume that any type can be used for
         * monitors.
         */
        if (allSynchronizedTypeFlow.isSaturated()) {
            return getAllInstantiatedTypes();
        }
        return allSynchronizedTypeFlow.getState().types(this);
    }

    @Override
    public AnalysisMethod addRootMethod(Executable method, boolean invokeSpecial, Object reason, MultiMethod.MultiMethodKey... otherRoots) {
        return addRootMethod(metaAccess.lookupJavaMethod(method), invokeSpecial, reason, otherRoots);
    }

    @Override
    public AnalysisMethod forcedAddRootMethod(Executable method, boolean invokeSpecial, Object reason, MultiMethod.MultiMethodKey... otherRoots) {
        AnalysisError.guarantee(isBaseLayerAnalysisEnabled());
        PointsToAnalysisMethod analysisMethod = assertPointsToAnalysisMethod(metaAccess.lookupJavaMethod(method));
        postTask(ignore -> {
            MethodTypeFlow typeFlow = analysisMethod.getTypeFlow();
            /*
             * Calling MethodTypeFlow#ensureFlowsGraphCreated ensures that the method is not
             * optimized away by the analysis.
             */
            typeFlow.ensureFlowsGraphCreated(this, null);
            /*
             * Saturating all the parameters of the method allows to enforce that no optimization is
             * performed using the types of the parameters of the methods.
             */
            typeFlow.getMethodFlowsGraph().saturateAllParameters(this);
        });
        return addRootMethod(analysisMethod, invokeSpecial, reason, otherRoots);
    }

    @Override
    @SuppressWarnings("try")
    public AnalysisMethod addRootMethod(AnalysisMethod aMethod, boolean invokeSpecial, Object reason, MultiMethod.MultiMethodKey... otherRoots) {
        assert !universe.sealed() : "Cannot register root methods after analysis universe is sealed.";
        AnalysisError.guarantee(aMethod.isOriginalMethod());
        boolean isStatic = aMethod.isStatic();
        int paramCount = aMethod.getSignature().getParameterCount(!isStatic);
        PointsToAnalysisMethod originalPTAMethod = assertPointsToAnalysisMethod(aMethod);

        if (isStatic) {
            /*
             * For static methods trigger analysis in the empty context. This will trigger parsing
             * and return the method flows graph. Then the method parameter type flows are
             * initialized with the corresponding parameter declared type.
             */
            Consumer<PointsToAnalysisMethod> triggerStaticMethodFlow = (pointsToMethod) -> {
                postTask(() -> {
                    pointsToMethod.registerAsDirectRootMethod(reason);
                    pointsToMethod.registerAsImplementationInvoked(reason.toString());
                    MethodFlowsGraphInfo flowInfo = analysisPolicy.staticRootMethodGraph(this, pointsToMethod);
                    for (int idx = 0; idx < paramCount; idx++) {
                        AnalysisType declaredParamType = aMethod.getSignature().getParameterType(idx);
                        FormalParamTypeFlow parameter = flowInfo.getParameter(idx);
                        processParam(declaredParamType, parameter);
                    }
                });
            };
            triggerStaticMethodFlow.accept(originalPTAMethod);
            for (MultiMethod.MultiMethodKey key : otherRoots) {
                assert key != MultiMethod.ORIGINAL_METHOD : key;
                PointsToAnalysisMethod ptaMethod = assertPointsToAnalysisMethod(originalPTAMethod.getMultiMethod(key));
                triggerStaticMethodFlow.accept(ptaMethod);
            }
        } else {
            if (invokeSpecial && originalPTAMethod.isAbstract()) {
                throw AnalysisError.userError("Abstract methods cannot be registered as special invoke entry point.");
            }
            /*
             * For special invoked methods trigger method resolution by using the
             * context-insensitive special invoke type flow. This will resolve the method in its
             * declaring class when the declaring class is instantiated.
             *
             * For virtual methods trigger method resolution by using the context-insensitive
             * virtual invoke type flow. Since the virtual invoke observes the receiver flow state
             * it will get notified for any future reachable subtypes and will resolve the method in
             * each subtype.
             *
             * In both cases the context-insensitive invoke parameters are initialized with the
             * corresponding declared type state. When a callee is resolved the method is parsed and
             * the actual parameter type state is propagated to the formal parameters. Then the
             * callee is linked and registered as implementation-invoked.
             *
             * Note for virtual and special methods no action is needed when there are otherRoots.
             * This is due to two factors: First, the callee methods are only resolved once types
             * flow into the context insensitive invoke typeflow. Second, otherRoots is only
             * (currently) used for runtime compilation; in this use case, all necessary linking
             * will be done during callee resolution.
             */
            postTask(() -> {
                if (invokeSpecial) {
                    originalPTAMethod.registerAsDirectRootMethod(reason);
                } else {
                    originalPTAMethod.registerAsVirtualRootMethod(reason);
                }
                InvokeTypeFlow invoke = originalPTAMethod.initAndGetContextInsensitiveInvoke(PointsToAnalysis.this, null, invokeSpecial, MultiMethod.ORIGINAL_METHOD);
                /*
                 * Initialize the type flow of the invoke's actual parameters with the corresponding
                 * parameter declared type. Thus, when the invoke links callees it will propagate
                 * the parameter types too.
                 *
                 * The parameter iteration skips the primitive parameters, as these are not modeled.
                 * The type flow of the receiver is set to the receiver type already when the invoke
                 * is created.
                 */
                for (int idx = 1; idx < paramCount; idx++) {
                    /*
                     * Note: the Signature doesn't count the receiver of a virtual invoke as a
                     * parameter whereas the MethodTypeFlow does, hence when accessing the parameter
                     * type below we use idx-1 but when accessing the actual parameter flow we
                     * simply use idx.
                     */
                    AnalysisType declaredParamType = aMethod.getSignature().getParameterType(idx - 1);
                    TypeFlow<?> actualParameterFlow = invoke.getActualParameter(idx);
                    processParam(declaredParamType, actualParameterFlow);
                }
            });
        }
        return aMethod;

    }

    private void processParam(AnalysisType declaredParamType, TypeFlow<?> actualParameterFlow) {
        if (actualParameterFlow != null && isSupportedJavaKind(declaredParamType.getStorageKind())) {
            if (declaredParamType.getStorageKind() == JavaKind.Object) {
                TypeFlow<?> initialParameterFlow = declaredParamType.getTypeFlow(this, true);
                initialParameterFlow.addUse(this, actualParameterFlow);
            } else {
                actualParameterFlow.addState(this, TypeState.anyPrimitiveState());
            }
        }
    }

    public static PointsToAnalysisMethod assertPointsToAnalysisMethod(AnalysisMethod aMethod) {
        assert aMethod instanceof PointsToAnalysisMethod : "Only points-to analysis methods are supported";
        return ((PointsToAnalysisMethod) aMethod);
    }

    @Override
    public AnalysisType addRootClass(Class<?> clazz, boolean addFields, boolean addArrayClass) {
        AnalysisType type = metaAccess.lookupJavaType(clazz);
        return addRootClass(type, addFields, addArrayClass);
    }

    @SuppressWarnings({"try"})
    @Override
    public AnalysisType addRootClass(AnalysisType type, boolean addFields, boolean addArrayClass) {
        type.registerAsReachable("root class");
        for (ResolvedJavaField javaField : type.getInstanceFields(false)) {
            AnalysisField field = (AnalysisField) javaField;
            if (addFields) {
                field.registerAsAccessed("field of root class");
            }
            processRootField(type, field);
        }
        if (type.getSuperclass() != null) {
            addRootClass(type.getSuperclass(), addFields, addArrayClass);
        }
        if (addArrayClass) {
            addRootClass(type.getArrayClass(), false, false);
        }
        return type;
    }

    @Override
    @SuppressWarnings("try")
    public AnalysisType addRootField(Class<?> clazz, String fieldName) {
        AnalysisType type = addRootClass(clazz, false, false);
        for (ResolvedJavaField javaField : type.getInstanceFields(true)) {
            AnalysisField field = (AnalysisField) javaField;
            if (field.getName().equals(fieldName)) {
                return addRootField(type, field);
            }
        }
        throw shouldNotReachHere("field not found: " + fieldName);
    }

    @Override
    public AnalysisType addRootField(Field field) {
        AnalysisField analysisField = getMetaAccess().lookupJavaField(field);
        if (analysisField.isStatic()) {
            return addRootStaticField(analysisField);
        } else {
            AnalysisType analysisType = getMetaAccess().lookupJavaType(field.getDeclaringClass());
            return addRootField(analysisType, analysisField);
        }
    }

    private AnalysisType addRootField(AnalysisType type, AnalysisField field) {
        field.registerAsAccessed("root field");
        processRootField(type, field);
        return field.getType();
    }

    private void processRootField(AnalysisType type, AnalysisField field) {
        JavaKind storageKind = field.getStorageKind();
        if (isSupportedJavaKind(storageKind)) {
            var fieldFlow = type.getContextInsensitiveAnalysisObject().getInstanceFieldFlow(this, field, true);
            if (storageKind.isObject()) {
                /*
                 * For system classes any instantiated (sub)type of the declared field type can be
                 * written to the field flow.
                 */
                TypeFlow<?> fieldDeclaredTypeFlow = field.getType().getTypeFlow(this, true);
                fieldDeclaredTypeFlow.addUse(this, fieldFlow);
            } else {
                fieldFlow.addState(this, TypeState.anyPrimitiveState());
            }
        }
    }

    @SuppressWarnings({"try", "unused"})
    public AnalysisType addRootStaticField(Class<?> clazz, String fieldName) {
        addRootClass(clazz, false, false);
        Field reflectField;
        try {
            reflectField = clazz.getField(fieldName);
            AnalysisField field = metaAccess.lookupJavaField(reflectField);
            return addRootStaticField(field);

        } catch (NoSuchFieldException e) {
            throw shouldNotReachHere("field not found: " + fieldName);
        }
    }

    private AnalysisType addRootStaticField(AnalysisField field) {
        field.registerAsAccessed("static root field");
        JavaKind storageKind = field.getStorageKind();
        if (isSupportedJavaKind(storageKind)) {
            if (storageKind.isObject()) {
                TypeFlow<?> fieldFlow = field.getType().getTypeFlow(this, true);
                fieldFlow.addUse(this, field.getStaticFieldFlow());
            } else {
                field.getStaticFieldFlow().addState(this, TypeState.anyPrimitiveState());
            }
        }
        return field.getType();
    }

    @Override
    public void checkUserLimitations() {
    }

    public boolean isSupportedJavaKind(JavaKind javaKind) {
        return javaKind == JavaKind.Object || (trackPrimitiveValues && javaKind.isNumericInteger());
    }

    @Override
    public boolean trackPrimitiveValues() {
        return trackPrimitiveValues;
    }

    public interface TypeFlowRunnable extends DebugContextRunnable {
        TypeFlow<?> getTypeFlow();
    }

    public void postFlow(final TypeFlow<?> operation) {
        if (operation.inQueue) {
            return;
        }
        operation.inQueue = true;

        executor.execute(new TypeFlowRunnable() {

            @Override
            public void run(DebugContext ignored) {
                PointsToStats.registerTypeFlowQueuedUpdate(PointsToAnalysis.this, operation);

                operation.inQueue = false;
                if (operation.isValid()) {
                    operation.update(PointsToAnalysis.this);
                }
            }

            @Override
            public String toString() {
                return "Operation: " + operation;
            }

            @Override
            public TypeFlow<?> getTypeFlow() {
                return operation;
            }
        });
    }

    /**
     * Performs the analysis.
     *
     * @return Returns true if any changes are made, i.e. if any type flows are updated
     */
    @SuppressWarnings("try")
    @Override
    public boolean finish() throws InterruptedException {
        try (Indent indent = debug.logAndIndent("starting analysis in BigBang.finish")) {
            universe.setAnalysisDataValid(false);
            boolean didSomeWork = doTypeflow();
            assert executor.getPostedOperations() == 0 : executor.getPostedOperations();
            universe.setAnalysisDataValid(true);
            return didSomeWork;
        }
    }

    @SuppressWarnings("try")
    public boolean doTypeflow() throws InterruptedException {
        boolean didSomeWork;
        try (StopTimer ignored = typeFlowTimer.start()) {
            executor.start();
            executor.complete();
            didSomeWork = (executor.getPostedOperations() > 0);
            executor.shutdown();
        }
        /* Initialize for the next iteration. */
        executor.init(timing);
        return didSomeWork;
    }

    @Override
    public void onTypeInstantiated(AnalysisType type) {
        /* Register the type as instantiated with all its super types. */

        assert type.isInstantiated() : type;
        AnalysisError.guarantee(type.isArray() || (type.isInstanceClass() && !type.isAbstract()));

        TypeState typeState = TypeState.forExactType(this, type, true);
        TypeState typeStateNonNull = TypeState.forExactType(this, type, false);

        /* Register the instantiated type with its super types. */
        type.forAllSuperTypes(t -> {
            t.instantiatedTypes.addState(this, typeState);
            t.instantiatedTypesNonNull.addState(this, typeStateNonNull);
        });
    }

    public static class ConstantObjectsProfiler {

        static final ConcurrentHashMap<AnalysisType, MyInteger> constantTypes = new ConcurrentHashMap<>(ESTIMATED_NUMBER_OF_TYPES);
        static final int PROCESSED_CONSTANTS_DUMP_THRESHOLD = 100000;
        static final int CONSTANT_COUNTER_DUMP_THRESHOLD = 1000;

        static int processedConstants;

        static class ConstantCounterEntry {
            protected AnalysisType type;
            protected int counter;

            ConstantCounterEntry(AnalysisType type, int counter) {
                this.type = type;
                this.counter = counter;
            }
        }

        static class MyInteger {
            int myInt = 0;

            protected void increment() {
                myInt++;
            }

            protected int value() {
                return myInt;
            }

            @Override
            public String toString() {
                return myInt + "";
            }
        }

        static class ConstantCounterEntryComparator implements Comparator<ConstantCounterEntry> {

            @Override
            public int compare(ConstantCounterEntry o1, ConstantCounterEntry o2) {
                return Integer.compare(o2.counter, o1.counter);
            }
        }

        static final ConstantCounterEntryComparator CONSTANT_COUNTER_COMPARATOR = new ConstantCounterEntryComparator();

        public static void registerConstant(AnalysisType type) {
            processedConstants++;
            MyInteger counter = constantTypes.get(type);
            if (counter == null) {
                MyInteger newValue = new MyInteger();
                MyInteger oldValue = constantTypes.putIfAbsent(type, newValue);
                counter = oldValue != null ? oldValue : newValue;
            }
            counter.increment();
        }

        public static void maybeDumpConstantHistogram() {

            if (processedConstants > PROCESSED_CONSTANTS_DUMP_THRESHOLD) {

                processedConstants = 0;

                List<ConstantCounterEntry> constantCounters = new ArrayList<>();
                for (Map.Entry<AnalysisType, MyInteger> entry : constantTypes.entrySet()) {
                    AnalysisType type = entry.getKey();
                    Integer counter = entry.getValue().value();
                    if (counter > CONSTANT_COUNTER_DUMP_THRESHOLD) {
                        constantCounters.add(new ConstantCounterEntry(type, counter));
                    }
                }

                Collections.sort(constantCounters, CONSTANT_COUNTER_COMPARATOR);

                System.out.println(" - - - - - - - - - - - - - - - - - - - - - - - -  ");
                System.out.println("              CONSTANT HISTOGRAM                  ");
                for (ConstantCounterEntry constantCounter : constantCounters) {
                    System.out.format("%d : %s %n", constantCounter.counter, constantCounter.type.getName());
                }
                System.out.println(" - - - - - - - - - - - - - - - - - - - - - - - -  ");

            }
        }
    }

    protected abstract static class BucketTiming implements CompletionExecutor.Timing {
        private static final int NUM_BUCKETS = 10;

        private final AtomicLong numOperations = new AtomicLong();
        private final AtomicLong numAdded = new AtomicLong();
        private final AtomicLong numDone = new AtomicLong();
        private final AtomicLong numInQueue = new AtomicLong();
        private final AtomicLong totalTime = new AtomicLong();
        private final AtomicLongArray timeBuckets = new AtomicLongArray(NUM_BUCKETS);

        @Override
        public long getPrintIntervalNanos() {
            return 1_000_000_000;
        }

        @Override
        public void addScheduled(DebugContextRunnable r) {
            numOperations.incrementAndGet();
            numInQueue.incrementAndGet();
            numAdded.incrementAndGet();
        }

        @Override
        public void addCompleted(DebugContextRunnable r, long nanos) {
            numInQueue.decrementAndGet();
            numDone.incrementAndGet();
            totalTime.addAndGet(nanos);

            int bucket = 0;
            long bucketTime = nanos / 1000;
            while (bucketTime != 0 && bucket < NUM_BUCKETS - 1) {
                bucketTime /= 10;
                bucket++;
            }
            timeBuckets.incrementAndGet(bucket);

            if (nanos > 500_000_000L && r instanceof TypeFlowRunnable) {
                TypeFlow<?> tf = ((TypeFlowRunnable) r).getTypeFlow();
                String source = String.valueOf(tf.getSource());
                System.out.format("LONG RUNNING  %.2f  %s %x %s  state %s %x  uses %d observers %d%n", (double) nanos / 1_000_000_000, ClassUtil.getUnqualifiedName(tf.getClass()),
                                System.identityHashCode(tf), source, PointsToStats.asString(tf.getState()), System.identityHashCode(tf.getState()), tf.getUses().size(), tf.getObservers().size());
            }
        }

        @Override
        public void printHeader() {
            System.out.format("%9s %6s %6s %6s %10s %8s  %5s %5s %5s %5s %5s %5s %5s %5s %5s %5s  |", "total", "qlen", "added", "done", "total us", "avg us", "<1us", ">1us", "10", "100", ">1ms",
                            "10", "100", ">1s", "10", "100");
        }

        @Override
        public void print() {
            long operations = numOperations.get();
            long queued = numInQueue.get();
            long scheduled = numAdded.getAndSet(0);
            long completed = numDone.getAndSet(0);
            long time = totalTime.getAndSet(0);

            long[] buckets = new long[NUM_BUCKETS];
            for (int i = 0; i < NUM_BUCKETS; i++) {
                buckets[i] = timeBuckets.getAndSet(i, 0);
            }

            System.out.format("%9d %6d %6d %6d %10d %8d  ", operations, queued, scheduled, completed, time / 1000, completed != 0 ? time / 1000 / completed : 0L);
            for (int i = 0; i < NUM_BUCKETS; i++) {
                System.out.format("%5d ", buckets[i]);
            }
            System.out.print(" |");
        }
    }

    protected class AnalysisTiming extends BucketTiming {
        @Override
        public void printHeader() {
            System.out.format("%5s %5s %5s  |", "graphs", "types", "nid");
            super.printHeader();
            System.out.println();
        }

        @Override
        public void print() {
            System.out.format("%5d %5d %5d  |", numParsedGraphs.get(), StreamSupport.stream(getAllInstantiatedTypes().spliterator(), false).count(), universe.getNextTypeId());
            super.print();
            System.out.println();
        }
    }
}
