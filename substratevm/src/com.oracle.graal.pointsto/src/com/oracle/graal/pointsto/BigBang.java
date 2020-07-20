/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.SuppressFBWarnings;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Builder;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;

import com.oracle.graal.pointsto.ObjectScanner.ReusableSet;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.flow.AllSynchronizedTypeFlow;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.MethodTypeFlowBuilder;
import com.oracle.graal.pointsto.flow.OffsetLoadTypeFlow.AbstractUnsafeLoadTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetStoreTypeFlow.AbstractUnsafeStoreTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.flow.UnknownTypeFlow;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.flow.context.AnalysisContextPolicy;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.typestate.PointsToStats;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.CompletionExecutor;
import com.oracle.graal.pointsto.util.CompletionExecutor.DebugContextRunnable;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.Timer.StopTimer;
import com.oracle.svm.util.ImageGeneratorThreadMarker;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;

public abstract class BigBang {

    private final OptionValues options;
    private final List<DebugHandlersFactory> debugHandlerFactories;
    private final DebugContext debug;
    private final HostedProviders providers;
    private final Replacements replacements;

    private final HeapScanningPolicy heapScanningPolicy;

    /** The type of {@link java.lang.Object}. */
    private final AnalysisType objectType;
    private TypeFlow<?> allSynchronizedTypeFlow;
    private UnknownTypeFlow unknownTypeFlow;

    protected final AnalysisUniverse universe;
    protected final AnalysisMetaAccess metaAccess;
    protected final HostVM hostVM;
    private final UnsupportedFeatures unsupportedFeatures;

    protected final boolean trackTypeFlowInputs;
    protected final boolean reportAnalysisStatistics;

    /**
     * Processing queue.
     */
    private final CompletionExecutor executor;
    private final Runnable heartbeatCallback;

    private ConcurrentMap<AbstractUnsafeLoadTypeFlow, Boolean> unsafeLoads;
    private ConcurrentMap<AbstractUnsafeStoreTypeFlow, Boolean> unsafeStores;

    public final AtomicLong numParsedGraphs = new AtomicLong();
    private final CompletionExecutor.Timing timing;

    public final Timer typeFlowTimer;
    public final Timer checkObjectsTimer;
    public final Timer processFeaturesTimer;
    public final Timer analysisTimer;

    public BigBang(OptionValues options, AnalysisUniverse universe, HostedProviders providers, HostVM hostVM, ForkJoinPool executorService, Runnable heartbeatCallback,
                    UnsupportedFeatures unsupportedFeatures) {
        this.options = options;
        this.debugHandlerFactories = Collections.singletonList(new GraalDebugHandlersFactory(providers.getSnippetReflection()));
        this.debug = new Builder(options, debugHandlerFactories).build();
        this.hostVM = hostVM;
        String imageName = hostVM.getImageName();
        this.typeFlowTimer = new Timer(imageName, "(typeflow)", false);
        this.checkObjectsTimer = new Timer(imageName, "(objects)", false);
        this.processFeaturesTimer = new Timer(imageName, "(features)", false);
        this.analysisTimer = new Timer(imageName, "analysis", true);

        this.universe = universe;
        this.metaAccess = (AnalysisMetaAccess) providers.getMetaAccess();
        this.replacements = providers.getReplacements();
        this.unsupportedFeatures = unsupportedFeatures;
        this.providers = providers;

        this.objectType = metaAccess.lookupJavaType(Object.class);
        /*
         * Make sure the all-instantiated type flow is created early. We do not have any
         * instantiated types yet, so the state is empty at first.
         */
        objectType.getTypeFlow(this, true);
        allSynchronizedTypeFlow = new AllSynchronizedTypeFlow();
        unknownTypeFlow = new UnknownTypeFlow();

        trackTypeFlowInputs = PointstoOptions.TrackInputFlows.getValue(options);
        reportAnalysisStatistics = PointstoOptions.PrintPointsToStatistics.getValue(options);
        if (reportAnalysisStatistics) {
            PointsToStats.init(this);
        }

        unsafeLoads = new ConcurrentHashMap<>();
        unsafeStores = new ConcurrentHashMap<>();

        timing = PointstoOptions.ProfileAnalysisOperations.getValue(options) ? new AnalysisTiming() : null;
        executor = new CompletionExecutor(this, executorService, heartbeatCallback);
        executor.init(timing);
        this.heartbeatCallback = heartbeatCallback;

        heapScanningPolicy = PointstoOptions.ExhaustiveHeapScan.getValue(options)
                        ? HeapScanningPolicy.scanAll()
                        : HeapScanningPolicy.skipTypes(skippedHeapTypes());
    }

    public AnalysisType[] skippedHeapTypes() {
        return new AnalysisType[]{metaAccess.lookupJavaType(String.class)};
    }

    public Runnable getHeartbeatCallback() {
        return heartbeatCallback;
    }

    public boolean trackTypeFlowInputs() {
        return trackTypeFlowInputs;
    }

    public boolean reportAnalysisStatistics() {
        return reportAnalysisStatistics;
    }

    public OptionValues getOptions() {
        return options;
    }

    public List<DebugHandlersFactory> getDebugHandlerFactories() {
        return debugHandlerFactories;
    }

    public DebugContext getDebug() {
        return debug;
    }

    public MethodTypeFlowBuilder createMethodTypeFlowBuilder(BigBang bb, MethodTypeFlow methodFlow) {
        return new MethodTypeFlowBuilder(bb, methodFlow);
    }

    /** Associates a JavaConstant with a root. */
    public abstract boolean addRoot(JavaConstant constant, Object root);

    /** Retrieves a root associated with a JavaConstant. */
    public abstract Object getRoot(JavaConstant constant);

    public void registerUnsafeLoad(AbstractUnsafeLoadTypeFlow unsafeLoad) {
        unsafeLoads.putIfAbsent(unsafeLoad, true);
    }

    public void registerUnsafeStore(AbstractUnsafeStoreTypeFlow unsafeStore) {
        unsafeStores.putIfAbsent(unsafeStore, true);
    }

    public void reportIllegalUnknownUse(AnalysisMethod method, BytecodePosition source, String message) {
        String trace = "Location: " + (source == null ? "[unknown]" : source.getMethod().asStackTraceElement(source.getBCI()).toString()) + "\n";
        trace += "Call path:";
        getUnsupportedFeatures().addMessage(method.format("%H.%n(%p)"), method, message, trace);
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
            unsafeLoad.initClone(this);

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
            unsafeStore.initClone(this);

            /*
             * Force update for unsafe accessed instance fields: post the receiver object flow for
             * update; an update of the receiver object flow will trigger an updated of the
             * observers, i.e., of the unsafe store.
             */
            this.postFlow(unsafeStore.receiver());
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

    /** You can blacklist certain callees here. */
    @SuppressWarnings("unused")
    public boolean isCallAllowed(BigBang bb, AnalysisMethod caller, AnalysisMethod target, NodeSourcePosition srcPosition) {
        return true;
    }

    public void cleanupAfterAnalysis() {
        allSynchronizedTypeFlow = null;
        unsafeLoads = null;
        unsafeStores = null;
        unknownTypeFlow = null;
        scannedObjects = null;

        ConstantObjectsProfiler.constantTypes.clear();

        universe.getTypes().forEach(AnalysisType::cleanupAfterAnalysis);
        universe.getFields().forEach(AnalysisField::cleanupAfterAnalysis);
        universe.getMethods().forEach(AnalysisMethod::cleanupAfterAnalysis);
    }

    public AnalysisPolicy analysisPolicy() {
        return universe.analysisPolicy();
    }

    public AnalysisContextPolicy<AnalysisContext> contextPolicy() {
        return universe.analysisPolicy().getContextPolicy();
    }

    public AnalysisUniverse getUniverse() {
        return universe;
    }

    public HostedProviders getProviders() {
        return providers;
    }

    public AnalysisMetaAccess getMetaAccess() {
        return metaAccess;
    }

    public Replacements getReplacements() {
        return replacements;
    }

    public UnsupportedFeatures getUnsupportedFeatures() {
        return unsupportedFeatures;
    }

    public AnalysisType lookup(JavaType type) {
        return universe.lookup(type);
    }

    public AnalysisType getObjectType() {
        return metaAccess.lookupJavaType(Object.class);
    }

    public AnalysisType getObjectArrayType() {
        return metaAccess.lookupJavaType(Object[].class);
    }

    public AnalysisType getGraalNodeType() {
        return metaAccess.lookupJavaType(org.graalvm.compiler.graph.Node.class);
    }

    public AnalysisType getGraalNodeListType() {
        return metaAccess.lookupJavaType(org.graalvm.compiler.graph.NodeList.class);
    }

    public AnalysisType getThrowableType() {
        return metaAccess.lookupJavaType(Throwable.class);
    }

    public AnalysisType getThreadType() {
        return metaAccess.lookupJavaType(Thread.class);
    }

    public AnalysisType forClass(Class<?> clazz) {
        return metaAccess.lookupJavaType(clazz);
    }

    public AnalysisType forClass(String className) {
        try {
            return forClass(Class.forName(className));
        } catch (ClassNotFoundException e) {
            throw JVMCIError.shouldNotReachHere(e);
        }
    }

    public TypeFlow<?> getAllInstantiatedTypeFlow() {
        return objectType.getTypeFlow(this, true);
    }

    public TypeFlow<?> getUnknownTypeFlow() {
        return unknownTypeFlow;
    }

    public TypeFlow<?> getAllSynchronizedTypeFlow() {
        return allSynchronizedTypeFlow;
    }

    public TypeState getAllSynchronizedTypeState() {
        /*
         * If all-synchrnonized type flow, i.e., the type flow that keeps track of the types of all
         * monitor objects, is saturated then we need to assume that any type can be used for
         * monitors.
         */
        if (allSynchronizedTypeFlow.isSaturated()) {
            return getAllInstantiatedTypeFlow().getState();
        }
        return allSynchronizedTypeFlow.getState();
    }

    public boolean executorIsStarted() {
        return executor.isStarted();
    }

    public AnalysisMethod addRootMethod(Executable method) {
        AnalysisMethod aMethod = metaAccess.lookupJavaMethod(method);
        addRootMethod(aMethod);
        return aMethod;
    }

    @SuppressWarnings("try")
    public AnalysisMethod addRootMethod(AnalysisMethod aMethod) {
        if (aMethod.isRootMethod()) {
            return aMethod;
        }
        aMethod.registerAsRootMethod();

        final MethodTypeFlow methodFlow = aMethod.getTypeFlow();
        try (Indent indent = debug.logAndIndent("add root method %s", aMethod.getName())) {
            boolean isStatic = Modifier.isStatic(aMethod.getModifiers());
            int paramCount = aMethod.getSignature().getParameterCount(!isStatic);
            int offset = 0;
            if (!isStatic) {
                methodFlow.setInitialReceiverFlow(this, aMethod.getDeclaringClass());
                offset = 1;
            }
            for (int i = offset; i < paramCount; i++) {
                AnalysisType declaredParamType = (AnalysisType) aMethod.getSignature().getParameterType(i - offset, aMethod.getDeclaringClass());
                if (declaredParamType.getJavaKind() == JavaKind.Object) {
                    methodFlow.setInitialParameterFlow(this, declaredParamType, i);
                }
            }
        }

        postTask(new DebugContextRunnable() {
            @Override
            public void run(DebugContext ignore) {
                methodFlow.addContext(BigBang.this, BigBang.this.contextPolicy().emptyContext(), null);
            }

            @Override
            public DebugContext getDebug(OptionValues opts, List<DebugHandlersFactory> factories) {
                assert opts == getOptions();
                return DebugContext.disabled(opts);
            }
        });

        return aMethod;
    }

    public AnalysisType addSystemClass(Class<?> clazz, boolean addFields, boolean addArrayClass) {
        return addSystemClass(metaAccess.lookupJavaType(clazz), addFields, addArrayClass);
    }

    @SuppressWarnings({"try"})
    private AnalysisType addSystemClass(AnalysisType type, boolean addFields, boolean addArrayClass) {
        try (Indent indent = debug.logAndIndent("add system class %s", type.getName())) {
            for (AnalysisField field : type.getInstanceFields(false)) {
                if (addFields) {
                    field.registerAsAccessed();
                }
                /*
                 * For system classes any instantiated (sub)type of the declared field type can be
                 * written to the field flow.
                 */
                TypeFlow<?> fieldDeclaredTypeFlow = field.getType().getTypeFlow(this, true);
                fieldDeclaredTypeFlow.addUse(this, type.getContextInsensitiveAnalysisObject().getInstanceFieldFlow(this, field, true));
            }
            if (type.getSuperclass() != null) {
                addSystemClass(type.getSuperclass(), addFields, addArrayClass);
            }
            if (addArrayClass) {
                addSystemClass(type.getArrayClass(), false, false);
            }
        }
        return type;
    }

    @SuppressWarnings("try")
    public AnalysisType addSystemField(Class<?> clazz, String fieldName) {
        AnalysisType type = addSystemClass(clazz, false, false);
        for (AnalysisField field : type.getInstanceFields(true)) {
            if (field.getName().equals(fieldName)) {
                try (Indent indent = debug.logAndIndent("add system field %s in class %s", fieldName, clazz.getName())) {
                    field.registerAsAccessed();
                    /*
                     * For system classes any instantiated (sub)type of the declared field type can
                     * be written to the field flow.
                     */
                    TypeFlow<?> fieldDeclaredTypeFlow = field.getType().getTypeFlow(this, true);
                    fieldDeclaredTypeFlow.addUse(this, type.getContextInsensitiveAnalysisObject().getInstanceFieldFlow(this, field, true));
                }
                return field.getType();
            }
        }
        throw shouldNotReachHere("field not found: " + fieldName);
    }

    @SuppressWarnings("try")
    public AnalysisType addSystemStaticField(Class<?> clazz, String fieldName) {
        addSystemClass(clazz, false, false);
        Field reflectField;
        try {
            try (Indent indent = debug.logAndIndent("add system static field %s in class %s", fieldName, clazz.getName())) {
                reflectField = clazz.getField(fieldName);
                AnalysisField field = metaAccess.lookupJavaField(reflectField);
                field.registerAsAccessed();
                TypeFlow<?> fieldFlow = field.getType().getTypeFlow(this, true);
                fieldFlow.addUse(this, field.getStaticFieldFlow());
                return field.getType();
            }
        } catch (NoSuchFieldException e) {
            throw shouldNotReachHere("field not found: " + fieldName);
        }
    }

    public void addSystemMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        try {
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
            addRootMethod(method);
        } catch (NoSuchMethodException ex) {
            throw shouldNotReachHere(ex);
        }
    }

    public final SnippetReflectionProvider getSnippetReflectionProvider() {
        return providers.getSnippetReflection();
    }

    public final ConstantReflectionProvider getConstantReflectionProvider() {
        return providers.getConstantReflection();
    }

    public ConstantFieldProvider getConstantFieldProvider() {
        return providers.getConstantFieldProvider();
    }

    public CompletionExecutor getExecutor() {
        return executor;
    }

    public void checkUserLimitations() {
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
                PointsToStats.registerTypeFlowQueuedUpdate(BigBang.this, operation);

                operation.inQueue = false;
                operation.update(BigBang.this);
            }

            @Override
            public String toString() {
                return "Operation: " + operation.toString();
            }

            @Override
            public TypeFlow<?> getTypeFlow() {
                return operation;
            }

            @Override
            public DebugContext getDebug(OptionValues opts, List<DebugHandlersFactory> factories) {
                assert opts == getOptions();
                return DebugContext.disabled(opts);
            }
        });
    }

    public void postTask(final DebugContextRunnable task) {
        executor.execute(task);
    }

    /**
     * Performs the analysis.
     *
     * @return Returns true if any changes are made, i.e. if any type flows are updated
     */
    @SuppressWarnings("try")
    public boolean finish() throws InterruptedException {
        try (Indent indent = debug.logAndIndent("starting analysis in BigBang.finish")) {
            universe.setAnalysisDataValid(this, false);
            boolean didSomeWork = false;

            int numTypes;
            do {
                didSomeWork |= doTypeflow();

                /*
                 * Check if the object graph introduces any new types, which leads to new operations
                 * being posted.
                 */
                assert executor.getPostedOperations() == 0;
                numTypes = universe.getTypes().size();
                try (StopTimer t = checkObjectsTimer.start()) {
                    // track static fields
                    checkObjectGraph();
                }
            } while (executor.getPostedOperations() != 0 || numTypes != universe.getTypes().size());

            universe.setAnalysisDataValid(this, true);

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

    private ReusableSet scannedObjects = new ReusableSet();

    @SuppressWarnings("try")
    private void checkObjectGraph() throws InterruptedException {
        scannedObjects.reset();
        // scan constants
        ObjectScanner objectScanner = new AnalysisObjectScanner(this, scannedObjects);
        checkObjectGraph(objectScanner);
        if (PointstoOptions.ScanObjectsParallel.getValue(options)) {
            executor.start();
            objectScanner.scanBootImageHeapRoots(executor);
            executor.complete();
            executor.shutdown();
            executor.init(timing);
        } else {
            objectScanner.scanBootImageHeapRoots(null);
        }
        AnalysisType.updateAssignableTypes(this);
    }

    public HeapScanningPolicy scanningPolicy() {
        return heapScanningPolicy;
    }

    /**
     * Traverses the object graph to discover references to new types.
     *
     * @param objectScanner
     */
    protected void checkObjectGraph(ObjectScanner objectScanner) {
    }

    public HostVM getHostVM() {
        return hostVM;
    }

    @SuppressFBWarnings(value = "NP_NONNULL_PARAM_VIOLATION", justification = "ForkJoinPool does support null for the exception handler.")
    public static ForkJoinPool createExecutor(DebugContext debug, int numberOfThreads) {
        ForkJoinPool.ForkJoinWorkerThreadFactory factory = debugThreadFactory(debug.areScopesEnabled() || debug.areMetricsEnabled() ? debug : null);
        return new ForkJoinPool(numberOfThreads, factory, null, false);
    }

    private static ForkJoinPool.ForkJoinWorkerThreadFactory debugThreadFactory(DebugContext debug) {
        return pool -> new SubstrateWorkerThread(pool, debug);
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
                System.out.format("LONG RUNNING  %.2f  %s %x %s  state %s %x  uses %d observers %d%n", (double) nanos / 1_000_000_000, tf.getClass().getSimpleName(), System.identityHashCode(tf),
                                source, PointsToStats.asString(tf.getState()), System.identityHashCode(tf.getState()), tf.getUses().size(), tf.getObservers().size());
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
            System.out.format("%5d %5d %5d  |", numParsedGraphs.get(), getAllInstantiatedTypeFlow().getState().typesCount(), universe.getNextTypeId());
            super.print();
            System.out.println();
        }
    }

    private static class SubstrateWorkerThread extends ForkJoinWorkerThread
                    implements ImageGeneratorThreadMarker {
        private final DebugContext debug;

        SubstrateWorkerThread(ForkJoinPool pool, DebugContext debug) {
            super(pool);
            this.debug = debug;
        }

        @Override
        protected void onTermination(Throwable exception) {
            if (debug != null) {
                debug.closeDumpHandlers(true);
            }
        }
    }
}
