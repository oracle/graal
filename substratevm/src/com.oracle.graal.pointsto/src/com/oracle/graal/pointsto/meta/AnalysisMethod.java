/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.meta;

import static jdk.vm.ci.common.JVMCIError.shouldNotReachHere;
import static jdk.vm.ci.common.JVMCIError.unimplemented;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.api.ImageLayerLoader;
import com.oracle.graal.pointsto.api.ImageLayerWriter;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.flow.AnalysisParsedGraph.Stage;
import com.oracle.graal.pointsto.infrastructure.GraphProvider;
import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.infrastructure.ResolvedSignature;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AtomicUtils;
import com.oracle.graal.pointsto.util.ConcurrentLightHashSet;
import com.oracle.svm.common.meta.MultiMethod;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.java.BytecodeParser.BytecodeParserError;
import jdk.graal.compiler.java.StableMethodNameFormatter;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.EncodedGraph.EncodedNodeReference;
import jdk.graal.compiler.nodes.GraphDecoder;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

public abstract class AnalysisMethod extends AnalysisElement implements WrappedJavaMethod, GraphProvider, OriginalMethodProvider, MultiMethod {
    private static final AtomicReferenceFieldUpdater<AnalysisMethod, Object> isVirtualRootMethodUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisMethod.class, Object.class, "isVirtualRootMethod");

    private static final AtomicReferenceFieldUpdater<AnalysisMethod, Object> isDirectRootMethodUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisMethod.class, Object.class, "isDirectRootMethod");

    private static final AtomicReferenceFieldUpdater<AnalysisMethod, Object> isInvokedUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisMethod.class, Object.class, "isInvoked");

    private static final AtomicReferenceFieldUpdater<AnalysisMethod, Object> isImplementationInvokedUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisMethod.class, Object.class, "isImplementationInvoked");

    private static final AtomicReferenceFieldUpdater<AnalysisMethod, Object> implementationInvokedNotificationsUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisMethod.class, Object.class, "implementationInvokedNotifications");

    private static final AtomicReferenceFieldUpdater<AnalysisMethod, Object> isIntrinsicMethodUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisMethod.class, Object.class, "isIntrinsicMethod");

    private static final AtomicReferenceFieldUpdater<AnalysisMethod, Object> isInlinedUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisMethod.class, Object.class, "isInlined");

    static final AtomicReferenceFieldUpdater<AnalysisMethod, Object> allImplementationsUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisMethod.class, Object.class, "allImplementations");

    private static final AtomicReferenceFieldUpdater<AnalysisMethod, Boolean> reachableInCurrentLayerUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisMethod.class, Boolean.class, "reachableInCurrentLayer");

    public static final AnalysisMethod[] EMPTY_ARRAY = new AnalysisMethod[0];

    public record Signature(String name, AnalysisType[] parameterTypes) {
    }

    public final ResolvedJavaMethod wrapped;

    private final int id;
    private final boolean buildingSharedLayer;
    /** Marks a method loaded from a base layer. */
    private final boolean isInBaseLayer;
    private final boolean analyzedInPriorLayer;
    private final boolean hasNeverInlineDirective;
    private final ExceptionHandler[] exceptionHandlers;
    private final LocalVariableTable localVariableTable;
    private final String name;
    private final String qualifiedName;
    private final int modifiers;

    protected final AnalysisType declaringClass;
    protected final ResolvedSignature<AnalysisType> signature;
    private final int parsingContextMaxDepth;

    private final MultiMethodKey multiMethodKey;

    /**
     * Map from a key to the corresponding implementation. All multi-method implementations for a
     * given Java method share the same map. This allows one to easily switch between different
     * implementations when needed. When {@code multiMethodMap} is null, then
     * {@link #multiMethodKey} points to {@link #ORIGINAL_METHOD} and no other implementations exist
     * for the method. This is done to reduce the memory overhead in the common case when only this
     * one implementation is present.
     */
    private volatile Map<MultiMethodKey, MultiMethod> multiMethodMap;

    @SuppressWarnings("rawtypes") //
    private static final AtomicReferenceFieldUpdater<AnalysisMethod, Map> MULTIMETHOD_MAP_UPDATER = AtomicReferenceFieldUpdater.newUpdater(AnalysisMethod.class, Map.class,
                    "multiMethodMap");

    /** Virtually invoked method registered as root. */
    @SuppressWarnings("unused") private volatile Object isVirtualRootMethod;
    /** Direct (special or static) invoked method registered as root. */
    @SuppressWarnings("unused") private volatile Object isDirectRootMethod;
    private Object nativeEntryPointData;
    @SuppressWarnings("unused") private volatile Object isInvoked;
    @SuppressWarnings("unused") private volatile Object isImplementationInvoked;
    /**
     * Contains callbacks that are notified when this method is marked as implementation-invoked.
     * Each callback is called at least once, but there are no guarantees that it will be called
     * exactly once.
     */
    @SuppressWarnings("unused") private volatile Object implementationInvokedNotifications;
    @SuppressWarnings("unused") private volatile Object isIntrinsicMethod;
    @SuppressWarnings("unused") private volatile Object isInlined;
    @SuppressWarnings("unused") private volatile Boolean reachableInCurrentLayer;
    private final boolean enableReachableInCurrentLayer;

    private final AtomicReference<GraphCacheEntry> parsedGraphCacheState = new AtomicReference<>(GraphCacheEntry.UNPARSED);
    private final AtomicBoolean trackedGraphPersisted = new AtomicBoolean(false);

    private EncodedGraph analyzedGraph;

    /**
     * Concrete methods that could possibly be called when calling this method. This also includes
     * methods that are not reachable yet, i.e., this set must be filtered before it can be used. It
     * never includes the method itself to reduce the size. See
     * {@link AnalysisMethod#collectMethodImplementations} for more details.
     */
    @SuppressWarnings("unused") private volatile Object allImplementations;

    /**
     * Indicates that this method has opaque return. This is necessary when there are control flows
     * present which cannot be tracked by analysis, which happens for continuation support.
     *
     * This should only be set via calling
     * {@code FeatureImpl.BeforeAnalysisAccessImpl#registerOpaqueMethodReturn}.
     */
    private boolean hasOpaqueReturn;

    private CompilationBehavior compilationBehavior = CompilationBehavior.DEFAULT;

    @SuppressWarnings({"this-escape", "unchecked"})
    protected AnalysisMethod(AnalysisUniverse universe, ResolvedJavaMethod wrapped, MultiMethodKey multiMethodKey, Map<MultiMethodKey, MultiMethod> multiMethodMap) {
        super(universe.hostVM.enableTrackAcrossLayers());
        HostVM hostVM = universe.hostVM();
        this.wrapped = wrapped;

        declaringClass = universe.lookup(wrapped.getDeclaringClass());
        var wrappedSignature = wrapped.getSignature();
        if (wrappedSignature instanceof ResolvedSignature<?> resolvedSignature) {
            /* BaseLayerMethods return fully resolved signatures */
            if (resolvedSignature.getReturnType() instanceof AnalysisType) {
                signature = (ResolvedSignature<AnalysisType>) resolvedSignature;
            } else {
                signature = getUniverse().lookup(wrappedSignature, wrapped.getDeclaringClass());
            }
        } else {
            signature = getUniverse().lookup(wrappedSignature, wrapped.getDeclaringClass());
        }
        hasNeverInlineDirective = hostVM.hasNeverInlineDirective(wrapped);

        name = createName(wrapped, multiMethodKey);
        qualifiedName = format("%H.%n(%P)");
        modifiers = wrapped.getModifiers();

        buildingSharedLayer = hostVM.buildingSharedLayer();
        if (hostVM.buildingExtensionLayer() && declaringClass.isInBaseLayer()) {
            int mid = universe.getImageLayerLoader().lookupHostedMethodInBaseLayer(this);
            if (mid != -1) {
                /*
                 * This id is the actual link between the corresponding method from the base layer
                 * and this new method.
                 */
                id = mid;
                isInBaseLayer = true;
            } else {
                id = universe.computeNextMethodId();
                isInBaseLayer = false;
            }
        } else {
            id = universe.computeNextMethodId();
            isInBaseLayer = false;
        }
        analyzedInPriorLayer = isInBaseLayer && hostVM.analyzedInPriorLayer(this);

        ExceptionHandler[] original = wrapped.getExceptionHandlers();
        exceptionHandlers = new ExceptionHandler[original.length];
        for (int i = 0; i < original.length; i++) {
            ExceptionHandler h = original[i];
            JavaType catchType = getCatchType(universe, wrapped, h);
            exceptionHandlers[i] = new ExceptionHandler(h.getStartBCI(), h.getEndBCI(), h.getHandlerBCI(), h.catchTypeCPI(), catchType);
        }

        LocalVariableTable analysisLocalVariableTable = null;
        if (wrapped.getLocalVariableTable() != null) {
            try {
                Local[] origLocals = wrapped.getLocalVariableTable().getLocals();
                Local[] newLocals = new Local[origLocals.length];
                ResolvedJavaType accessingClass = declaringClass.getWrapped();
                for (int i = 0; i < newLocals.length; ++i) {
                    Local origLocal = origLocals[i];
                    ResolvedJavaType origLocalType = origLocal.getType() instanceof ResolvedJavaType ? (ResolvedJavaType) origLocal.getType() : origLocal.getType().resolve(accessingClass);
                    AnalysisType type = universe.lookup(origLocalType);
                    newLocals[i] = new Local(origLocal.getName(), type, origLocal.getStartBCI(), origLocal.getEndBCI(), origLocal.getSlot());
                }
                analysisLocalVariableTable = new LocalVariableTable(newLocals);
            } catch (LinkageError | UnsupportedFeatureException | BytecodeParserError e) {
                // in this case, localVariableTable = null
            }
        }
        localVariableTable = analysisLocalVariableTable;

        this.multiMethodKey = multiMethodKey;
        this.multiMethodMap = multiMethodMap;

        if (universe.analysisPolicy().trackAccessChain()) {
            startTrackInvocations();
        }
        parsingContextMaxDepth = universe.analysisPolicy().parsingContextMaxDepth();

        this.enableReachableInCurrentLayer = universe.hostVM.enableReachableInCurrentLayer();
    }

    @SuppressWarnings("this-escape")
    protected AnalysisMethod(AnalysisMethod original, MultiMethodKey multiMethodKey) {
        super(original.enableTrackAcrossLayers);
        wrapped = original.wrapped;
        id = original.id;
        buildingSharedLayer = original.buildingSharedLayer;
        isInBaseLayer = original.isInBaseLayer;
        analyzedInPriorLayer = original.analyzedInPriorLayer;
        declaringClass = original.declaringClass;
        signature = original.signature;
        hasNeverInlineDirective = original.hasNeverInlineDirective;
        exceptionHandlers = original.exceptionHandlers;
        localVariableTable = original.localVariableTable;
        parsingContextMaxDepth = original.parsingContextMaxDepth;

        name = createName(wrapped, multiMethodKey);
        qualifiedName = format("%H.%n(%P)");
        modifiers = original.modifiers;

        this.multiMethodKey = multiMethodKey;
        assert original.multiMethodMap != null;
        multiMethodMap = original.multiMethodMap;
        hasOpaqueReturn = original.hasOpaqueReturn;

        if (original.getUniverse().analysisPolicy().trackAccessChain()) {
            startTrackInvocations();
        }

        this.enableReachableInCurrentLayer = original.enableReachableInCurrentLayer;
    }

    /**
     * This method should not be used directly, except to set the {@link CompilationBehavior} from a
     * previous layer. To set a new {@link CompilationBehavior}, please use the associated setter.
     */
    public void setCompilationBehavior(CompilationBehavior compilationBehavior) {
        assert getUniverse().getBigbang().getHostVM().buildingImageLayer() : "The method compilation behavior can only be set in layered images";
        this.compilationBehavior = compilationBehavior;
    }

    private void setNewCompilationBehavior(CompilationBehavior compilationBehavior) {
        assert (!isInBaseLayer && this.compilationBehavior == CompilationBehavior.DEFAULT) || this.compilationBehavior == compilationBehavior : "The method was already assigned " +
                        this.compilationBehavior + ", but trying to assign " + compilationBehavior;
        setCompilationBehavior(compilationBehavior);
    }

    public CompilationBehavior getCompilationBehavior() {
        return compilationBehavior;
    }

    /**
     * Delays this method to the application layer. This should not be called after the method was
     * already parsed to avoid analyzing all the method's callees.
     */
    public void setFullyDelayedToApplicationLayer() {
        HostVM hostVM = getUniverse().getBigbang().getHostVM();
        AnalysisError.guarantee(hostVM.buildingImageLayer(), "Methods can only be delayed in layered images: %s", this);
        AnalysisError.guarantee(parsedGraphCacheState.get() == GraphCacheEntry.UNPARSED, "The method %s was marked as delayed to the application layer but was already parsed", this);
        AnalysisError.guarantee(!hostVM.hasAlwaysInlineDirective(this), "Method %s with an always inline directive cannot be delayed to the application layer as such methods cannot be inlined", this);
        AnalysisError.guarantee(isConcrete(), "Method %s is not concrete and cannot be delayed to the application layer", this);
        setNewCompilationBehavior(CompilationBehavior.FULLY_DELAYED_TO_APPLICATION_LAYER);
    }

    /**
     * Returns true if this method is marked as delayed to the application layer and the current
     * layer is a shared layer.
     */
    public boolean isDelayed() {
        return compilationBehavior == CompilationBehavior.FULLY_DELAYED_TO_APPLICATION_LAYER && buildingSharedLayer;
    }

    public void setPinnedToInitialLayer(Object reason) {
        BigBang bigbang = getUniverse().getBigbang();
        AnalysisError.guarantee(bigbang.getHostVM().buildingInitialLayer(), "Methods can only be pinned to the initial layer: %s", this);
        boolean nonAbstractInstanceClass = !declaringClass.isArray() && declaringClass.isInstanceClass() && !declaringClass.isAbstract();
        AnalysisError.guarantee(nonAbstractInstanceClass, "Only methods from non abstract instance class can be pinned: %s", this);
        bigbang.forcedAddRootMethod(this, true, "pinned to initial layer: " + reason);
        if (!isStatic()) {
            declaringClass.registerAsInstantiated("declared method " + this.format("%H.%n(%p)") + " is pinned to initial layer: " + reason);
        }
        setNewCompilationBehavior(CompilationBehavior.PINNED_TO_INITIAL_LAYER);
    }

    public boolean isPinnedToInitialLayer() {
        return compilationBehavior == CompilationBehavior.PINNED_TO_INITIAL_LAYER;
    }

    private static String createName(ResolvedJavaMethod wrapped, MultiMethodKey multiMethodKey) {
        String aName = wrapped.getName();
        if (multiMethodKey != ORIGINAL_METHOD) {
            aName += StableMethodNameFormatter.MULTI_METHOD_KEY_SEPARATOR + multiMethodKey;
        }
        return aName;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    private static JavaType getCatchType(AnalysisUniverse universe, ResolvedJavaMethod wrapped, ExceptionHandler handler) {
        JavaType catchType = handler.getCatchType();
        if (catchType == null) {
            return null;
        }
        ResolvedJavaType resolvedCatchType;
        try {
            resolvedCatchType = catchType.resolve(wrapped.getDeclaringClass());
        } catch (LinkageError e) {
            /*
             * Type resolution fails if the catch type is missing. Just return the unresolved type.
             * The analysis doesn't model unresolved types, but we can reuse the JVMCI type; the
             * UniverseBuilder and the BytecodeParser know how to deal with that.
             */
            return catchType;
        }
        return universe.lookup(resolvedCatchType);
    }

    @Override
    public AnalysisUniverse getUniverse() {
        /* Access the universe via the declaring class to avoid storing it here. */
        return declaringClass.getUniverse();
    }

    /**
     * @see PointsToAnalysis#validateFixedPointState
     */
    public boolean validateFixedPointState(@SuppressWarnings("unused") BigBang bb) {
        return true;
    }

    public void cleanupAfterAnalysis() {
        GraphCacheEntry graphCacheEntry = parsedGraphCacheState.get();
        if (graphCacheEntry != GraphCacheEntry.CLEARED) {
            parsedGraphCacheState.set(GraphCacheEntry.CLEARED);
        }
    }

    public abstract void startTrackInvocations();

    /**
     * @return analysis related invoke information for given method, mainly the possible callees to
     *         traverse the call graph
     */
    public abstract Iterable<? extends InvokeInfo> getInvokes();

    /**
     * @return the position of the invocation that triggered parsing for this method, or null
     */
    public abstract Object getParsingReason();

    /**
     * @return the parsing context in which given method was parsed
     */
    public final StackTraceElement[] getParsingContext() {
        List<StackTraceElement> trace = new ArrayList<>();
        Object curr = getParsingReason();

        while (curr != null) {
            if (!(curr instanceof BytecodePosition)) {
                AnalysisError.guarantee(curr instanceof String, "Parsing reason should be a BytecodePosition or String: %s", curr);
                trace.add(ReportUtils.rootMethodSentinel((String) curr));
                break;
            }
            if (trace.size() > parsingContextMaxDepth) {
                trace.add(ReportUtils.truncatedStackTraceSentinel(this));
                break;
            }
            BytecodePosition position = (BytecodePosition) curr;
            AnalysisMethod caller = (AnalysisMethod) position.getMethod();
            trace.add(caller.asStackTraceElement(position.getBCI()));
            curr = caller.getParsingReason();
        }
        return trace.toArray(new StackTraceElement[0]);
    }

    public int getId() {
        return id;
    }

    public boolean isInBaseLayer() {
        return isInBaseLayer;
    }

    public boolean analyzedInPriorLayer() {
        return analyzedInPriorLayer;
    }

    public boolean reachableInCurrentLayer() {
        return enableReachableInCurrentLayer && reachableInCurrentLayer != null && reachableInCurrentLayer;
    }

    public void setReachableInCurrentLayer() {
        if (enableReachableInCurrentLayer && !reachableInCurrentLayer()) {
            AtomicUtils.atomicSetAndRun(this, true, reachableInCurrentLayerUpdater, () -> {
                ImageLayerLoader imageLayerLoader = getUniverse().getImageLayerLoader();
                if (imageLayerLoader != null) {
                    imageLayerLoader.loadPriorStrengthenedGraphAnalysisElements(this);
                }
                ConcurrentLightHashSet.forEach(this, allImplementationsUpdater, AnalysisMethod::setReachableInCurrentLayer);
            });
        }
    }

    /**
     * Registers this method as intrinsified to Graal nodes via a {@link InvocationPlugin graph
     * builder plugin}. Such a method is treated similar to an invoked method. For example, method
     * resolution must be able to find the method (otherwise the intrinsification would not work).
     */
    public void registerAsIntrinsicMethod(Object reason) {
        assert isValidReason(reason) : "Registering a method as intrinsic needs to provide a valid reason, found: " + reason;
        AtomicUtils.atomicSetAndRun(this, reason, isIntrinsicMethodUpdater, () -> onImplementationInvoked(reason));
    }

    /**
     * Registers this method as a native entrypoint, i.e. a method callable from the host
     * environment. Only direct root methods can be registered as entrypoints.
     */
    public void registerAsNativeEntryPoint(Object newEntryPointData) {
        assert newEntryPointData != null;
        assert isDirectRootMethod() : "All native entrypoints must be direct root methods: " + this;
        if (nativeEntryPointData != null && !nativeEntryPointData.equals(newEntryPointData)) {
            throw new UnsupportedFeatureException("Method is registered as entry point with conflicting entry point data: " + nativeEntryPointData + ", " + newEntryPointData);
        }
        nativeEntryPointData = newEntryPointData;
        /* We need that to check that entry points are not invoked from other Java methods. */
        startTrackInvocations();
    }

    public boolean registerAsInvoked(Object reason) {
        assert isValidReason(reason) : "Registering a method as invoked needs to provide a valid reason, found: " + reason;
        registerAsTrackedAcrossLayers(reason);
        return AtomicUtils.atomicSet(this, reason, isInvokedUpdater);
    }

    public boolean registerAsImplementationInvoked(Object reason) {
        assert isValidReason(reason) : "Registering a method as implementation invoked needs to provide a valid reason, found: " + reason;
        assert !Modifier.isAbstract(getModifiers()) : this;

        /*
         * The class constant of the declaring class is used for exception metadata, so marking a
         * method as invoked also makes the declaring class reachable.
         *
         * Even though the class could in theory be marked as reachable only if we successfully mark
         * the method as invoked, it would have an unwanted side effect, where this method could
         * return before the class gets marked as reachable.
         */
        getDeclaringClass().registerAsReachable("declared method " + qualifiedName + " is registered as implementation invoked");
        return AtomicUtils.atomicSetAndRun(this, reason, isImplementationInvokedUpdater, () -> onImplementationInvoked(reason));
    }

    public void registerAsInlined(Object reason) {
        assert reason instanceof NodeSourcePosition || reason instanceof ResolvedJavaMethod : "Registering a method as inlined needs to provide the inline location as reason, found: " + reason;
        AtomicUtils.atomicSetAndRun(this, reason, isInlinedUpdater, () -> onReachable(reason));
    }

    public void registerImplementationInvokedCallback(Consumer<DuringAnalysisAccess> callback) {
        if (this.isImplementationInvoked()) {
            /* If the method is already implementation-invoked just trigger the callback. */
            execute(getUniverse(), () -> callback.accept(declaringClass.getUniverse().getConcurrentAnalysisAccess()));
        } else {
            ElementNotification notification = new ElementNotification(callback);
            ConcurrentLightHashSet.addElement(this, implementationInvokedNotificationsUpdater, notification);
            if (this.isImplementationInvoked()) {
                /* Trigger callback if method became implementation-invoked during registration. */
                notifyImplementationInvokedCallback(notification);
            }
        }
    }

    private void notifyImplementationInvokedCallback(ElementNotification notification) {
        notification.notifyCallback(declaringClass.getUniverse(), this);
        ConcurrentLightHashSet.removeElement(this, implementationInvokedNotificationsUpdater, notification);
    }

    protected void notifyImplementationInvokedCallbacks() {
        ConcurrentLightHashSet.forEach(this, implementationInvokedNotificationsUpdater, (ElementNotification c) -> c.notifyCallback(declaringClass.getUniverse(), this));
        ConcurrentLightHashSet.removeElementIf(this, implementationInvokedNotificationsUpdater, ElementNotification::isNotified);
    }

    private void persistTrackedGraph(AnalysisParsedGraph graph) {
        if (isTrackedAcrossLayers() && trackedGraphPersisted.compareAndSet(false, true)) {
            ImageLayerWriter imageLayerWriter = getUniverse().getImageLayerWriter();
            imageLayerWriter.persistAnalysisParsedGraph(this, graph);
        }
    }

    /** Get the set of all callers for this method, as inferred by the static analysis. */
    public Set<AnalysisMethod> getCallers() {
        return getInvokeLocations().stream().map(location -> (AnalysisMethod) location.getMethod()).collect(Collectors.toSet());
    }

    /** Get the list of all invoke locations for this method, as inferred by the static analysis. */
    public abstract List<BytecodePosition> getInvokeLocations();

    /**
     * Returns true if this method is a native entrypoint, i.e. it may be called from the host
     * environment.
     */
    public boolean isNativeEntryPoint() {
        return nativeEntryPointData != null;
    }

    public Object getNativeEntryPointData() {
        return nativeEntryPointData;
    }

    public boolean isIntrinsicMethod() {
        return AtomicUtils.isSet(this, isIntrinsicMethodUpdater);
    }

    public Object getIntrinsicMethodReason() {
        return isIntrinsicMethod;
    }

    /**
     * Registers this method as a virtual root for the analysis.
     *
     * The class constant of the declaring class is used for exception metadata, so marking a method
     * as invoked also makes the declaring class reachable.
     *
     * Class is always marked as reachable regardless of the success of the atomic mark, same reason
     * as in {@link AnalysisMethod#registerAsImplementationInvoked(Object)}.
     */
    public boolean registerAsVirtualRootMethod(Object reason) {
        getDeclaringClass().registerAsReachable("declared method " + qualifiedName + " is registered as virtual root");
        return AtomicUtils.atomicSet(this, reason, isVirtualRootMethodUpdater);
    }

    /**
     * Registers this method as a direct (special or static) root for the analysis. Note that for
     * `invokespecial` direct roots, this <b>does not</b> guarantee that the method is
     * implementation invoked, as that registration is delayed until a suitable receiver type is
     * marked as instantiated.
     */
    public boolean registerAsDirectRootMethod(Object reason) {
        getDeclaringClass().registerAsReachable("declared method " + qualifiedName + " is registered as direct root");
        return AtomicUtils.atomicSet(this, reason, isDirectRootMethodUpdater);
    }

    /**
     * Returns true if the method is marked as virtual root. This doesn't necessarily mean that the
     * method is implementation-invoked, that depends on the instantiation state of the respective
     * receiver types.
     */
    public boolean isVirtualRootMethod() {
        return AtomicUtils.isSet(this, isVirtualRootMethodUpdater);
    }

    /**
     * Returns true if the method is marked as direct root. For special invoked methods this doesn't
     * necessarily mean that the method is implementation-invoked, that depends on the instantiation
     * state of the respective receiver types. Static root methods are immediately registered as
     * implementation-invoked too.
     */
    public boolean isDirectRootMethod() {
        return AtomicUtils.isSet(this, isDirectRootMethodUpdater);
    }

    public boolean isSimplyInvoked() {
        return AtomicUtils.isSet(this, isInvokedUpdater);
    }

    public boolean isSimplyImplementationInvoked() {
        return AtomicUtils.isSet(this, isImplementationInvokedUpdater);
    }

    /**
     * Returns true if this method is ever used as the target of a call site.
     */
    public boolean isInvoked() {
        return isIntrinsicMethod() || isVirtualRootMethod() || isDirectRootMethod() || AtomicUtils.isSet(this, isInvokedUpdater);
    }

    public Object getInvokedReason() {
        return isInvoked;
    }

    /**
     * Returns true if the method body can ever be executed. Methods registered as root are also
     * registered as implementation invoked when they are linked.
     */
    public boolean isImplementationInvoked() {
        return !Modifier.isAbstract(getModifiers()) && (isIntrinsicMethod() || AtomicUtils.isSet(this, isImplementationInvokedUpdater));
    }

    public Object getImplementationInvokedReason() {
        return isImplementationInvoked;
    }

    public boolean isInlined() {
        return AtomicUtils.isSet(this, isInlinedUpdater);
    }

    protected Object getInlinedReason() {
        return isInlined;
    }

    @Override
    public boolean isReachable() {
        return isImplementationInvoked() || isInlined();
    }

    @Override
    public boolean isTriggered() {
        if (isReachable()) {
            return true;
        }
        return isClassInitializer() && getDeclaringClass().isInitialized();
    }

    public void onImplementationInvoked(Object reason) {
        onReachable(reason);
        notifyImplementationInvokedCallbacks();
    }

    @Override
    public void onReachable(Object reason) {
        registerAsTrackedAcrossLayers(reason);
        notifyReachabilityCallbacks(declaringClass.getUniverse(), new ArrayList<>());
    }

    @Override
    protected void onTrackedAcrossLayers(Object reason) {
        AnalysisError.guarantee(!getUniverse().sealed(), "Method %s was marked as tracked after the universe was sealed", this);
        getUniverse().getImageLayerWriter().onTrackedAcrossLayer(this, reason);
        declaringClass.registerAsTrackedAcrossLayers(reason);
        for (AnalysisType parameter : toParameterList()) {
            parameter.registerAsTrackedAcrossLayers(reason);
        }
        signature.getReturnType().registerAsTrackedAcrossLayers(reason);

        if (getParsedGraphCacheStateObject() instanceof AnalysisParsedGraph analysisParsedGraph) {
            persistTrackedGraph(analysisParsedGraph);
        }
    }

    public void registerOverrideReachabilityNotification(MethodOverrideReachableNotification notification) {
        getUniverse().registerOverrideReachabilityNotification(this, notification);
    }

    @Override
    public ResolvedJavaMethod getWrapped() {
        return wrapped;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ResolvedSignature<AnalysisType> getSignature() {
        return signature;
    }

    @Override
    public JavaType[] toParameterTypes() {
        throw JVMCIError.shouldNotReachHere("ResolvedJavaMethod.toParameterTypes returns the wrong result for constructors. Use toParameterList instead.");
    }

    public List<AnalysisType> toParameterList() {
        return getSignature().toParameterList(isStatic() ? null : getDeclaringClass());
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose) {
        if (wrapped instanceof GraphProvider graphProvider) {
            return graphProvider.buildGraph(debug, method, providers, purpose);
        }
        return null;
    }

    @Override
    public boolean allowRuntimeCompilation() {
        if (wrapped instanceof GraphProvider graphProvider) {
            return graphProvider.allowRuntimeCompilation();
        }
        return true;
    }

    @Override
    public byte[] getCode() {
        return wrapped.getCode();
    }

    @Override
    public int getCodeSize() {
        return wrapped.getCodeSize();
    }

    @Override
    public AnalysisType getDeclaringClass() {
        return declaringClass;
    }

    @Override
    public int getMaxLocals() {
        return wrapped.getMaxLocals();
    }

    @Override
    public int getMaxStackSize() {
        return wrapped.getMaxStackSize();
    }

    @Override
    public Parameter[] getParameters() {
        return wrapped.getParameters();
    }

    @Override
    public int getModifiers() {
        return modifiers;
    }

    @Override
    public boolean isSynthetic() {
        return wrapped.isSynthetic();
    }

    @Override
    public boolean isVarArgs() {
        return wrapped.isVarArgs();
    }

    @Override
    public boolean isBridge() {
        return wrapped.isBridge();
    }

    @Override
    public boolean isDeclared() {
        return wrapped.isDeclared();
    }

    @Override
    public boolean isClassInitializer() {
        return wrapped.isClassInitializer();
    }

    @Override
    public boolean isConstructor() {
        return wrapped.isConstructor();
    }

    @Override
    public boolean canBeStaticallyBound() {
        boolean result = wrapped.canBeStaticallyBound();
        assert !isStatic() || result : "static methods must always be statically bindable: " + format("%H.%n");
        return result;

    }

    /**
     * Returns all methods that override (= implement) this method. If the
     * {@code includeInlinedMethods} parameter is true, all reachable overrides are returned; if it
     * is false, only invoked methods are returned (and methods that are already inlined at all call
     * sites are excluded).
     *
     * In the parallel static analysis, it is difficult to have this information always available:
     * when a method becomes reachable or invoked, it is not known which other methods it overrides.
     * Therefore, we collect all possible implementations in {@link #allImplementations} without
     * taking reachability into account, and then filter this too-large set of methods here on
     * demand.
     */
    public Set<AnalysisMethod> collectMethodImplementations(boolean includeInlinedMethods) {
        /*
         * To keep the allImplementations set as small as possible (and empty for most methods), the
         * set never includes this method itself. It is clear that every method is always an
         * implementation of itself.
         */
        boolean includeOurselfs = (isStatic() || getDeclaringClass().isAnySubtypeInstantiated()) &&
                        (includeInlinedMethods ? isReachable() : isImplementationInvoked());

        int allImplementationsSize = ConcurrentLightHashSet.size(this, allImplementationsUpdater);
        if (allImplementationsSize == 0) {
            /* Fast-path that avoids allocation of a full HashSet. */
            return includeOurselfs ? Set.of(this) : Set.of();
        }

        Set<AnalysisMethod> result = new HashSet<>(allImplementationsSize + 1);
        if (includeOurselfs) {
            result.add(this);
        }
        ConcurrentLightHashSet.forEach(this, allImplementationsUpdater, (AnalysisMethod override) -> {
            if (override.getDeclaringClass().isAnySubtypeInstantiated()) {
                if (includeInlinedMethods ? override.isReachable() : override.isImplementationInvoked()) {
                    result.add(override);
                }
            }
        });

        return result;
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        return exceptionHandlers;
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        return wrapped.asStackTraceElement(bci);
    }

    @Override
    public ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        return null;
    }

    @Override
    public ConstantPool getConstantPool() {
        return getUniverse().lookup(wrapped.getConstantPool(), wrapped.getDeclaringClass());
    }

    @Override
    public Annotation[][] getParameterAnnotations() {
        return wrapped.getParameterAnnotations();
    }

    @Override
    public Type[] getGenericParameterTypes() {
        return wrapped.getGenericParameterTypes();
    }

    @Override
    public boolean canBeInlined() {
        /* Delayed methods should not be inlined in the current layer */
        return !hasNeverInlineDirective() && !isDelayed();
    }

    @Override
    public boolean hasNeverInlineDirective() {
        return hasNeverInlineDirective;
    }

    @Override
    public boolean shouldBeInlined() {
        throw unimplemented();
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        return wrapped.getLineNumberTable();
    }

    @Override
    public String toString() {
        return "AnalysisMethod<" + format("%h.%n") + " -> " + wrapped.toString() + ", invoked: " + (isInvoked != null) +
                        ", implInvoked: " + (isImplementationInvoked != null) + ", intrinsic: " + (isIntrinsicMethod != null) + ", inlined: " + (isInlined != null) +
                        (isVirtualRootMethod() ? ", virtual root" : "") + (isDirectRootMethod() ? ", direct root" : "") + (isNativeEntryPoint() ? ", entry point" : "") + ">";
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        return localVariableTable;
    }

    @Override
    public void reprofile() {
        throw unimplemented();
    }

    @Override
    public Constant getEncoding() {
        throw unimplemented();
    }

    @Override
    public boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
        return false;
    }

    @Override
    public boolean isDefault() {
        return wrapped.isDefault();
    }

    @Override
    public SpeculationLog getSpeculationLog() {
        throw shouldNotReachHere();
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public ResolvedJavaMethod unwrapTowardsOriginalMethod() {
        return wrapped;
    }

    public Executable getJavaMethod() {
        if (wrapped instanceof BaseLayerMethod) {
            /* We don't know the corresponding Java method. */
            return null;
        }
        return OriginalMethodProvider.getJavaMethod(this);
    }

    /**
     * Forces the graph to be reparsed and the reparsing to be done by this thread.
     */
    public AnalysisParsedGraph reparseGraph(BigBang bb) {
        return ensureGraphParsedHelper(bb, Stage.finalStage(), true);
    }

    /**
     * Returns the object currently stored in the parsed graph cache. This won't trigger any parsing
     * or cache state transition.
     */
    public Object getParsedGraphCacheStateObject() {
        return parsedGraphCacheState.get().get(Stage.finalStage());
    }

    /**
     * Ensures that the method has been parsed, i.e., that the {@link StructuredGraph Graal IR} for
     * the method is available.
     */
    public AnalysisParsedGraph ensureGraphParsed(BigBang bb) {
        return ensureGraphParsed(bb, Stage.finalStage());
    }

    /**
     * Ensures that the method has been processed up to and including the required stage, i.e., that
     * the {@link StructuredGraph Graal IR} for the method is available.
     */
    public AnalysisParsedGraph ensureGraphParsed(BigBang bb, Stage stage) {
        return ensureGraphParsedHelper(bb, stage, false);
    }

    /**
     * Invariant:
     *
     * <pre>
     * isStageParsed(OPTIMIZATIONS_APPLIED) => isStageParsed(BYTECODE_PARSED)
     * </pre>
     *
     * This invariant ensures that we do not create the parsed graphs for earlier stages if the
     * graph is already available for a later stage. There are three reasons why this is necessary:
     * (1) For performance, we allow to directly create the graph for later stages if the
     * {@link Stage#isRequiredStage stage definition} doesn't require the previous stages to be
     * published explicitly. The invariant ensures that we don't drop this performance advantage.
     * (2) Parsed graphs for the final stage may be loaded from a file or similar (e.g. in case of
     * layered images). In that case, the bytecode for a method may not be available and creating
     * earlier stages is just not possible. (3) If a graph for a later stage is already available,
     * creating them for earlier stages may result in different graphs and therefore inconsistent
     * results due to global optimizations and their state.
     */
    private record GraphCacheEntry(Object bytecodeParsedObject, Object afterParsingHooksDoneObject) {

        private record Sentinel(String description) {
            @Override
            public String toString() {
                return description;
            }
        }

        private static final Object GRAPH_CACHE_UNPARSED = new Sentinel("unparsed");
        private static final Object GRAPH_CACHE_CLEARED = new Sentinel("cleared by cleanupAfterAnalysis");
        private static final GraphCacheEntry UNPARSED = new GraphCacheEntry(GRAPH_CACHE_UNPARSED, GRAPH_CACHE_UNPARSED);
        private static final GraphCacheEntry CLEARED = new GraphCacheEntry(GRAPH_CACHE_CLEARED, GRAPH_CACHE_CLEARED);

        private GraphCacheEntry {
            // invariant: isStageParsed(OPTIMIZATIONS_APPLIED) => isStageParsed(BYTECODE_PARSED)
            assert !(afterParsingHooksDoneObject instanceof AnalysisParsedGraph) || bytecodeParsedObject instanceof AnalysisParsedGraph;
        }

        static GraphCacheEntry createLockEntry(Stage stage, GraphCacheEntry base, ReentrantLock lock) {
            return switch (stage) {
                case BYTECODE_PARSED -> new GraphCacheEntry(lock, lock);
                /*
                 * If the stage 1 is skipped, the first stage needs to be locked too, to avoid
                 * another thread stealing the unparsed state.
                 */
                case OPTIMIZATIONS_APPLIED -> base.bytecodeParsedObject == GRAPH_CACHE_UNPARSED ? new GraphCacheEntry(lock, lock) : new GraphCacheEntry(base.bytecodeParsedObject, lock);
            };
        }

        static GraphCacheEntry createParsingError(Stage stage, GraphCacheEntry base, Throwable throwable) {
            return switch (stage) {
                case BYTECODE_PARSED -> new GraphCacheEntry(throwable, GRAPH_CACHE_UNPARSED);
                case OPTIMIZATIONS_APPLIED -> new GraphCacheEntry(base.bytecodeParsedObject, throwable);
            };
        }

        boolean isUnparsed(Stage stage) {
            return get(stage) == GRAPH_CACHE_UNPARSED;
        }

        private Object get(Stage stage) {
            return switch (stage) {
                case BYTECODE_PARSED -> bytecodeParsedObject;
                case OPTIMIZATIONS_APPLIED -> afterParsingHooksDoneObject;
            };
        }

        boolean isParsing(Stage stage) {
            return get(stage) instanceof ReentrantLock;
        }

        boolean isStageParsed(Stage stage) {
            return get(stage) instanceof AnalysisParsedGraph;
        }

        boolean isParsingError() {
            assert !(bytecodeParsedObject instanceof Throwable && afterParsingHooksDoneObject instanceof Throwable);
            return bytecodeParsedObject instanceof Throwable || afterParsingHooksDoneObject instanceof Throwable;
        }

        boolean isCleared() {
            return bytecodeParsedObject == GRAPH_CACHE_CLEARED && afterParsingHooksDoneObject == GRAPH_CACHE_CLEARED;
        }

        @Override
        public String toString() {
            return String.format("GraphCacheState(%s, %s)", bytecodeParsedObject, afterParsingHooksDoneObject);
        }
    }

    private AnalysisParsedGraph ensureGraphParsedHelper(BigBang bb, Stage stage, boolean forceReparse) {
        assert Stage.isRequiredStage(stage, this);
        while (true) {
            GraphCacheEntry curState = parsedGraphCacheState.get();

            /*-
             * This implements a state machine that ensures parsing is atomic. States:
             * 1) unparsed: stage1 and stage2 object are set to a sentinel value for the unparsed state.
             * 2) stage1 parsing: represented by a locked ReentrantLock object that other threads can wait on.
             * 3) stage1 parsed: represented by the ParsedGraph in 'bytecodeParsedObject'
             * 4) stage2 parsing: represented by a locked ReentrantLock object that other threads can wait on.
             * 5) stage2 parsed: represented by the ParsedGraph in 'afterParsingHooksDoneObject'
             * 6) cleared: stage1 and stage2 object are set to String "cleared".
             * 7) stage1 parsing error: represented by a Throwable in 'bytecodeParsedObject'
             * 8) stage2 parsing error: represented by a Throwable in 'afterParsingHooksDoneObject'
             *
             * Transitions:
             *
             * -) Common case: The method to be parsed is not a class initializer and stage 2 is requested.
             *    This omits the stage 1 graph since it will never be necessary to provide it because only
             *    class initializers can have cyclic dependencies.
             *    1 -> 4 -> 5
             *
             * -) Full case: The method to be parsed is a class initializer and stage 2 is requested.
             *    In this case, the stage 1 graph will be created and published to avoid parsing problems
             *    (either a deadlock or an endless recursion) due to cyclic dependencies.
             *    1 -> 2 -> 3 -> 4 -> 5
             *
             * -) Error transitions:
             *    ... -> 2 -> 7
             *    ... -> 4 -> 8
             *
             * -) After analysis, parsed graphs are cleared to save memory:
             *           1 -> 6
             *    ... -> 3 -> 6
             *    ... -> 5 -> 6
             *    ... -> 7 -> 6
             *    ... -> 8 -> 6
             *
             * The only end state is state 6 (i.e. no further transition is possible).
             */

            if (curState.isUnparsed(stage) || (forceReparse && curState.isStageParsed(stage))) {
                AnalysisParsedGraph graph;
                if (isInBaseLayer && getUniverse().getImageLayerLoader().hasAnalysisParsedGraph(this)) {
                    graph = getBaseLayerGraph(bb, curState);
                } else {
                    graph = createAnalysisParsedGraph(bb, stage, curState, forceReparse);
                }
                if (graph != null) {
                    return graph;
                }
            } else if (curState.isParsing(stage)) {
                waitOnLock(stage, (ReentrantLock) curState.get(stage));

            } else if (!forceReparse && curState.isStageParsed(stage)) {
                return (AnalysisParsedGraph) curState.get(stage);

            } else if (curState.isParsingError()) {
                throw AnalysisError.shouldNotReachHere("parsing had failed in another thread", (Throwable) curState.get(stage));

            } else if (curState.isCleared()) {
                return null;

            } else {
                throw AnalysisError.shouldNotReachHere("Unknown state: " + curState);
            }
        }
    }

    @FunctionalInterface
    private interface GraphSupplier {
        GraphCacheEntry get(BigBang bb, AnalysisMethod method, GraphCacheEntry curState);
    }

    private static final GraphSupplier CREATE_FIRST_STAGE = (bb, method, curState) -> new GraphCacheEntry(AnalysisParsedGraph.parseBytecode(bb, method), GraphCacheEntry.GRAPH_CACHE_UNPARSED);

    private static final GraphSupplier GET_FROM_BASE_LAYER = (bb, method, curState) -> {
        AnalysisParsedGraph graph = method.getUniverse().getImageLayerLoader().getAnalysisParsedGraph(method);
        return new GraphCacheEntry(graph, graph);
    };

    private static final GraphSupplier CREATE_FINAL_STAGE = (bb, method, curState) -> {
        AnalysisParsedGraph stage1Graph = null;
        Stage previous = Stage.firstStage();
        if (curState.isStageParsed(previous)) {
            stage1Graph = (AnalysisParsedGraph) curState.get(previous);
        }
        // if stage1 graph is null, stage2 graph will directly be created
        AnalysisParsedGraph stage2Graph = AnalysisParsedGraph.createFinalStage(bb, method, stage1Graph);
        if (stage1Graph != null) {
            return new GraphCacheEntry(stage1Graph, stage2Graph);
        }
        /*
         * If we directly created the stage2 graph, the graph will also be used if someone requests
         * the stage1 graph. This is necessary to maintain the invariant: if a stage2 graph is
         * available, a stage1 graph is also available.
         */
        return new GraphCacheEntry(stage2Graph, stage2Graph);
    };

    private static final GraphSupplier REPARSE_FINAL_STAGE = (bb, method, curState) -> {
        // when reparsing, we MUST NOT reuse any graph of a previous stage
        AnalysisParsedGraph stage2Graph = AnalysisParsedGraph.createFinalStage(bb, method, null);
        return new GraphCacheEntry(stage2Graph, stage2Graph);
    };

    private static GraphSupplier getGraphSupplierForStage(Stage stage, boolean forceReparse) {
        return switch (stage) {
            case BYTECODE_PARSED -> CREATE_FIRST_STAGE;
            case OPTIMIZATIONS_APPLIED -> forceReparse ? REPARSE_FINAL_STAGE : CREATE_FINAL_STAGE;
        };
    }

    private AnalysisParsedGraph getBaseLayerGraph(BigBang bb, GraphCacheEntry expectedValue) {
        /*
         * If the ParsedGraph is loaded from the base layer, it will also be used if someone
         * requests the stage1 graph. This is necessary to maintain the invariant: if a stage2 graph
         * is available, a stage1 graph is also available (see description of GraphCacheEntry).
         */
        return setGraph(bb, Stage.finalStage(), expectedValue, GET_FROM_BASE_LAYER);
    }

    private AnalysisParsedGraph createAnalysisParsedGraph(BigBang bb, Stage stage, GraphCacheEntry curState, boolean forceReparse) {
        /*
         * If the requested stage requires that the previous stage is explicitly available, we still
         * need to create the previous stage's result first and publish it. Then we can create the
         * requested stage's result.
         *
         * Note: If 'stage == Stage.firstStage()' then 'previous == null' and we will never enter
         * this branch.
         */
        if (stage.hasPrevious() && Stage.isRequiredStage(stage.previous(), this) && !curState.isStageParsed(stage.previous())) {
            /*
             * We need to do a recursive call to 'ensureGraphParsedHelper' because we don't know
             * anything about stage1's state here.
             */
            ensureGraphParsedHelper(bb, stage.previous(), forceReparse);
            // do another round in the outer loop such that 'curState' is reloaded
            return null;
        }
        return setGraph(bb, stage, curState, getGraphSupplierForStage(stage, forceReparse));
    }

    private AnalysisParsedGraph setGraph(BigBang bb, Stage stage, GraphCacheEntry expectedValue, GraphSupplier graphSupplier) {
        ReentrantLock lock = new ReentrantLock();
        lock.lock();
        try {
            /*
             * Atomically try to claim the parsing. Note that the lock must be locked already, and
             * remain locked until the parsing is done. Other threads will wait on this lock.
             */
            GraphCacheEntry lockState = GraphCacheEntry.createLockEntry(stage, expectedValue, lock);
            if (!parsedGraphCacheState.compareAndSet(expectedValue, lockState)) {
                /* We lost the race, another thread is doing the parsing. */
                return null;
            }
            AnalysisError.guarantee(!isDelayed(), "The method %s was parsed even though it was marked as delayed to the application layer", this);

            GraphCacheEntry newEntry = graphSupplier.get(bb, this, expectedValue);

            /*
             * Since we still hold the parsing lock, the transition form "parsing" to "parsed"
             * cannot fail.
             */
            boolean result = parsedGraphCacheState.compareAndSet(lockState, newEntry);
            AnalysisError.guarantee(result, "State transition failed");

            AnalysisParsedGraph analysisParsedGraph = (AnalysisParsedGraph) newEntry.get(stage);

            if (stage == Stage.finalStage()) {
                persistTrackedGraph(analysisParsedGraph);
            }

            return analysisParsedGraph;

        } catch (Throwable ex) {
            parsedGraphCacheState.set(GraphCacheEntry.createParsingError(stage, expectedValue, ex));
            throw ex;

        } finally {
            lock.unlock();
        }
    }

    private void waitOnLock(Stage stage, ReentrantLock lock) {
        AnalysisError.guarantee(!lock.isHeldByCurrentThread(), "Recursive parsing request, would lead to endless waiting loop");

        lock.lock();
        try {
            /*
             * When we can acquire the lock, parsing has finished. The next loop iteration will
             * return the result.
             */
            AnalysisError.guarantee(parsedGraphCacheState.get().get(stage) != lock, "Parsing must have finished in the thread that installed the lock");
        } finally {
            lock.unlock();
        }
    }

    public StructuredGraph decodeAnalyzedGraph(DebugContext debug, Iterable<EncodedNodeReference> nodeReferences) {
        if (analyzedGraph == null) {
            return null;
        }

        return decodeAnalyzedGraph(debug, nodeReferences, analyzedGraph.trackNodeSourcePosition(), analyzedGraph.isRecordingInlinedMethods(), GraphDecoder::new);
    }

    /**
     * Returns the {@link StructuredGraph Graal IR} for the method that has been processed by the
     * static analysis.
     */
    public StructuredGraph decodeAnalyzedGraph(DebugContext debug, Iterable<EncodedNodeReference> nodeReferences, boolean trackNodeSourcePosition, boolean recordInlinedMethods,
                    BiFunction<Architecture, StructuredGraph, GraphDecoder> decoderProvider) {
        if (analyzedGraph == null) {
            return null;
        }

        var allowAssumptions = getUniverse().hostVM().allowAssumptions(this);
        StructuredGraph result = new StructuredGraph.Builder(debug.getOptions(), debug, allowAssumptions)
                        .method(this)
                        .trackNodeSourcePosition(trackNodeSourcePosition)
                        .recordInlinedMethods(recordInlinedMethods)
                        .build();
        GraphDecoder decoder = decoderProvider.apply(AnalysisParsedGraph.HOST_ARCHITECTURE, result);
        decoder.decode(analyzedGraph, nodeReferences);
        /*
         * Since we are merely decoding the graph, the resulting graph should have the same
         * assumptions as the analyzed graph.
         */
        switch (allowAssumptions) {
            case YES -> {
                assert analyzedGraph.getAssumptions().equals(result.getAssumptions()) : this;
            }
            case NO -> {
                assert analyzedGraph.getAssumptions() == null && result.getAssumptions() == null : this;
            }
        }
        return result;
    }

    public void setAnalyzedGraph(EncodedGraph analyzedGraph) {
        this.analyzedGraph = analyzedGraph;
    }

    public void clearAnalyzedGraph() {
        this.analyzedGraph = null;
    }

    public EncodedGraph getAnalyzedGraph() {
        return analyzedGraph;
    }

    @Override
    public MultiMethodKey getMultiMethodKey() {
        return multiMethodKey;
    }

    @Override
    public AnalysisMethod getOrCreateMultiMethod(MultiMethodKey key) {
        return getOrCreateMultiMethod(key,
                        (k) -> {
                        });
    }

    @Override
    public AnalysisMethod getMultiMethod(MultiMethodKey key) {
        if (key == multiMethodKey) {
            return this;
        } else if (multiMethodMap == null) {
            return null;
        } else {
            return (AnalysisMethod) multiMethodMap.get(key);
        }
    }

    @Override
    public Collection<MultiMethod> getAllMultiMethods() {
        if (multiMethodMap == null) {
            return Collections.singleton(this);
        } else {
            return multiMethodMap.values();
        }
    }

    public AnalysisMethod getOrCreateMultiMethod(MultiMethodKey key, Consumer<AnalysisMethod> createAction) {
        if (key == multiMethodKey) {
            return this;
        }

        if (multiMethodMap == null) {
            ConcurrentHashMap<MultiMethodKey, MultiMethod> newMultiMethodMap = new ConcurrentHashMap<>();
            newMultiMethodMap.put(multiMethodKey, this);
            MULTIMETHOD_MAP_UPDATER.compareAndSet(this, null, newMultiMethodMap);
        }

        return (AnalysisMethod) multiMethodMap.computeIfAbsent(key, (k) -> {
            AnalysisMethod newMethod = createMultiMethod(AnalysisMethod.this, k);
            createAction.accept(newMethod);
            return newMethod;
        });
    }

    /**
     * This should only be set via calling
     * {@code FeatureImpl.BeforeAnalysisAccessImpl#registerOpaqueMethodReturn}.
     */
    public void setOpaqueReturn() {
        hasOpaqueReturn = true;
    }

    public boolean hasOpaqueReturn() {
        return hasOpaqueReturn;
    }

    protected abstract AnalysisMethod createMultiMethod(AnalysisMethod analysisMethod, MultiMethodKey newMultiMethodKey);

    /**
     * This state represents how a method should be compiled in layered images. The state of a
     * method can only be decided in the first layer if it is marked as tracked across layers. The
     * state has to stay the same across all the extension layers. If not specified, the state of a
     * method will be {@link CompilationBehavior#DEFAULT}.
     */
    public enum CompilationBehavior {

        /**
         * Method remains unanalyzed until the application layer and any inlining in a shared layer
         * is prevented. A call to the method in a shared layer will be replaced by an indirect
         * call. The compilation of those methods is then forced in the application layer and the
         * corresponding symbol is declared as global.
         *
         * A delayed method that is not referenced in any shared layer is treated as a
         * {@link CompilationBehavior#DEFAULT} method in the application layer and does not have to
         * be compiled. If it is only referenced in the application layer, it might be inlined and
         * not compiled at all.
         */
        FULLY_DELAYED_TO_APPLICATION_LAYER,

        /**
         * Method can be inlined into other methods, both before analysis and during compilation,
         * and will be compiled as a distinct compilation unit as stipulated by the normal native
         * image generation process (i.e., the method is installed as a root and/or a reference to
         * the method exists via a call and/or an explicit MethodReference).
         */
        DEFAULT,

        /**
         * Method is pinned to the initial layer, meaning it has to be analyzed and compiled in this
         * specific layer.
         */
        PINNED_TO_INITIAL_LAYER,
    }
}
