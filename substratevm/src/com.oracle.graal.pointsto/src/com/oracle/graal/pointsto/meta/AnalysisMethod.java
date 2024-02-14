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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
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

    public record Signature(String name, AnalysisType[] parameterTypes) {
    }

    public final ResolvedJavaMethod wrapped;

    private final int id;
    private final boolean hasNeverInlineDirective;
    private final ExceptionHandler[] exceptionHandlers;
    private final LocalVariableTable localVariableTable;
    private final String name;
    private final String qualifiedName;

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
    private Object entryPointData;
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

    private final AtomicReference<Object> parsedGraphCacheState = new AtomicReference<>(GRAPH_CACHE_UNPARSED);
    private static final Object GRAPH_CACHE_UNPARSED = "unparsed";
    private static final Object GRAPH_CACHE_CLEARED = "cleared by cleanupAfterAnalysis";

    private EncodedGraph analyzedGraph;

    /**
     * All concrete methods that can actually be called when calling this method. This includes all
     * overridden methods in subclasses, as well as this method if it is non-abstract.
     */
    protected AnalysisMethod[] implementations;

    /**
     * Indicates that this method returns all instantiated types. This is necessary when there are
     * control flows present which cannot be tracked by analysis, which happens for continuation
     * support.
     *
     * This should only be set via calling
     * {@code FeatureImpl.BeforeAnalysisAccessImpl#registerOpaqueMethodReturn}.
     */
    private boolean returnsAllInstantiatedTypes;

    @SuppressWarnings("this-escape")
    protected AnalysisMethod(AnalysisUniverse universe, ResolvedJavaMethod wrapped, MultiMethodKey multiMethodKey, Map<MultiMethodKey, MultiMethod> multiMethodMap) {
        this.wrapped = wrapped;
        id = universe.nextMethodId.getAndIncrement();

        declaringClass = universe.lookup(wrapped.getDeclaringClass());
        signature = getUniverse().lookup(wrapped.getSignature(), wrapped.getDeclaringClass());
        hasNeverInlineDirective = universe.hostVM().hasNeverInlineDirective(wrapped);

        name = createName(wrapped, multiMethodKey);
        qualifiedName = format("%H.%n(%P)");

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

        if (PointstoOptions.TrackAccessChain.getValue(declaringClass.universe.hostVM().options())) {
            startTrackInvocations();
        }
        parsingContextMaxDepth = PointstoOptions.ParsingContextMaxDepth.getValue(declaringClass.universe.hostVM.options());
    }

    @SuppressWarnings("this-escape")
    protected AnalysisMethod(AnalysisMethod original, MultiMethodKey multiMethodKey) {
        wrapped = original.wrapped;
        id = original.id;
        declaringClass = original.declaringClass;
        signature = original.signature;
        hasNeverInlineDirective = original.hasNeverInlineDirective;
        exceptionHandlers = original.exceptionHandlers;
        localVariableTable = original.localVariableTable;
        parsingContextMaxDepth = original.parsingContextMaxDepth;

        name = createName(wrapped, multiMethodKey);
        qualifiedName = format("%H.%n(%P)");

        this.multiMethodKey = multiMethodKey;
        assert original.multiMethodMap != null;
        multiMethodMap = original.multiMethodMap;
        returnsAllInstantiatedTypes = original.returnsAllInstantiatedTypes;

        if (PointstoOptions.TrackAccessChain.getValue(declaringClass.universe.hostVM().options())) {
            startTrackInvocations();
        }
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
    protected AnalysisUniverse getUniverse() {
        /* Access the universe via the declaring class to avoid storing it here. */
        return declaringClass.getUniverse();
    }

    public void cleanupAfterAnalysis() {
        if (parsedGraphCacheState.get() instanceof AnalysisParsedGraph) {
            parsedGraphCacheState.set(GRAPH_CACHE_CLEARED);
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

    /**
     * Registers this method as intrinsified to Graal nodes via a {@link InvocationPlugin graph
     * builder plugin}. Such a method is treated similar to an invoked method. For example, method
     * resolution must be able to find the method (otherwise the intrinsification would not work).
     */
    public void registerAsIntrinsicMethod(Object reason) {
        assert isValidReason(reason) : "Registering a method as intrinsic needs to provide a valid reason, found: " + reason;
        AtomicUtils.atomicSetAndRun(this, reason, isIntrinsicMethodUpdater, this::onImplementationInvoked);
    }

    public void registerAsEntryPoint(Object newEntryPointData) {
        assert newEntryPointData != null;
        if (entryPointData != null && !entryPointData.equals(newEntryPointData)) {
            throw new UnsupportedFeatureException("Method is registered as entry point with conflicting entry point data: " + entryPointData + ", " + newEntryPointData);
        }
        entryPointData = newEntryPointData;
        /* We need that to check that entry points are not invoked from other Java methods. */
        startTrackInvocations();
    }

    public boolean registerAsInvoked(Object reason) {
        assert isValidReason(reason) : "Registering a method as invoked needs to provide a valid reason, found: " + reason;
        return AtomicUtils.atomicSet(this, reason, isInvokedUpdater);
    }

    public boolean registerAsImplementationInvoked(Object reason) {
        assert isValidReason(reason) : "Registering a method as implementation invoked needs to provide a valid reason, found: " + reason;
        assert isImplementationInvokable() : this;
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
        return AtomicUtils.atomicSetAndRun(this, reason, isImplementationInvokedUpdater, this::onImplementationInvoked);
    }

    public void registerAsInlined(Object reason) {
        assert reason instanceof NodeSourcePosition || reason instanceof ResolvedJavaMethod : "Registering a method as inlined needs to provide the inline location as reason, found: " + reason;
        AtomicUtils.atomicSetAndRun(this, reason, isInlinedUpdater, this::onReachable);
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

    /** Get the set of all callers for this method, as inferred by the static analysis. */
    public Set<AnalysisMethod> getCallers() {
        return getInvokeLocations().stream().map(location -> (AnalysisMethod) location.getMethod()).collect(Collectors.toSet());
    }

    /** Get the list of all invoke locations for this method, as inferred by the static analysis. */
    public abstract List<BytecodePosition> getInvokeLocations();

    public boolean isEntryPoint() {
        return entryPointData != null;
    }

    public Object getEntryPointData() {
        return entryPointData;
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
     * Registers this method as a direct (special or static) root for the analysis.
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

    public boolean isSimplyImplementationInvoked() {
        return AtomicUtils.isSet(this, isImplementationInvokedUpdater);
    }

    /**
     * Returns true if this method is ever used as the target of a call site.
     */
    public boolean isInvoked() {
        return isIntrinsicMethod() || isVirtualRootMethod() || isDirectRootMethod() || AtomicUtils.isSet(this, isInvokedUpdater);
    }

    protected Object getInvokedReason() {
        return isInvoked;
    }

    /**
     * Returns true if the method body can ever be executed. Methods registered as root are also
     * registered as implementation invoked when they are linked.
     */
    public boolean isImplementationInvoked() {
        return !Modifier.isAbstract(getModifiers()) && (isIntrinsicMethod() || AtomicUtils.isSet(this, isImplementationInvokedUpdater));
    }

    protected Object getImplementationInvokedReason() {
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

    public void onImplementationInvoked() {
        onReachable();
        notifyImplementationInvokedCallbacks();
    }

    @Override
    public void onReachable() {
        notifyReachabilityCallbacks(declaringClass.getUniverse(), new ArrayList<>());
        processMethodOverrides();
    }

    private void processMethodOverrides() {
        if (wrapped.canBeStaticallyBound() || isConstructor()) {
            notifyMethodOverride(this);
        } else if (declaringClass.isAnySubtypeInstantiated()) {
            /*
             * If neither the declaring class nor a subtype is instantiated then this method cannot
             * be marked as invoked, so it cannot be an override.
             */
            declaringClass.forAllSuperTypes(superType -> {
                /*
                 * Iterate all the super types (including this type itself) looking for installed
                 * override notifications. If this method is found in a super type, and it has an
                 * override handler installed in that type, pass this method to the callback. It
                 * doesn't matter if the superMethod is actually reachable, only if it has any
                 * override handlers installed. Note that ResolvedJavaType.resolveMethod() cannot be
                 * used here because it only resolves methods declared by the type itself or if the
                 * method's declaring class is assignable from the type.
                 */
                AnalysisMethod superMethod = findInType(superType);
                if (superMethod != null) {
                    superMethod.notifyMethodOverride(AnalysisMethod.this);
                }
            });
        }
    }

    /** Find if the type declares a method with the same name and signature as this method. */
    private AnalysisMethod findInType(AnalysisType type) {
        try {
            return type.findMethod(wrapped.getName(), getSignature());
        } catch (UnsupportedFeatureException | LinkageError e) {
            /* Ignore linking errors and deleted methods. */
            return null;
        }
    }

    protected void notifyMethodOverride(AnalysisMethod override) {
        declaringClass.getOverrideReachabilityNotifications(this).forEach(n -> n.notifyCallback(getUniverse(), override));
    }

    public void registerOverrideReachabilityNotification(MethodOverrideReachableNotification notification) {
        declaringClass.registerOverrideReachabilityNotification(this, notification);
    }

    /**
     * Resolves this method in the provided type, but only if the type or any of its subtypes is
     * marked as instantiated.
     */
    protected AnalysisMethod resolveInType(AnalysisType holder) {
        return resolveInType(holder, holder.isAnySubtypeInstantiated());
    }

    protected AnalysisMethod resolveInType(AnalysisType holder, boolean holderOrSubtypeInstantiated) {
        /*
         * If the holder and all subtypes are not instantiated, then we do not need to resolve the
         * method. The method cannot be marked as invoked.
         */
        if (holderOrSubtypeInstantiated || isIntrinsicMethod()) {
            AnalysisMethod resolved;
            try {
                resolved = holder.resolveConcreteMethod(this, null);
            } catch (UnsupportedFeatureException e) {
                /* An unsupported overriding method is not reachable. */
                resolved = null;
            }
            /*
             * resolved == null means that the method in the base class was called, but never with
             * this holder.
             */
            return resolved;
        }
        return null;
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
        return wrapped.getModifiers();
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

    public AnalysisMethod[] getImplementations() {
        assert getUniverse().analysisDataValid : this;
        if (implementations == null) {
            return new AnalysisMethod[0];
        }
        return implementations;
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
        return !hasNeverInlineDirective();
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
                        (isVirtualRootMethod() ? ", virtual root" : "") + (isDirectRootMethod() ? ", direct root" : "") + (isEntryPoint() ? ", entry point" : "") + ">";
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
        return OriginalMethodProvider.getJavaMethod(this);
    }

    /**
     * Forces the graph to be reparsed and the reparsing to be done by this thread.
     */
    public AnalysisParsedGraph reparseGraph(BigBang bb) {
        return ensureGraphParsedHelper(bb, true);
    }

    /**
     * Ensures that the method has been parsed, i.e., that the {@link StructuredGraph Graal IR} for
     * the method is available.
     */
    public AnalysisParsedGraph ensureGraphParsed(BigBang bb) {
        return ensureGraphParsedHelper(bb, false);
    }

    private AnalysisParsedGraph ensureGraphParsedHelper(BigBang bb, boolean forceReparse) {
        while (true) {
            Object curState = parsedGraphCacheState.get();

            /*-
             * This implements a state machine that ensures parsing is atomic. States:
             * 1) unparsed: represented by the String "unparsed".
             * 2) parsing: represented by a locked ReentrantLock object that other threads can wait on.
             * 3) parsed: represented by the ParsedGraph with the parsing result
             * 4) cleared: represented by the String "cleared".
             * 5) parsing error: represented by a Throwable
             */

            if (curState == GRAPH_CACHE_UNPARSED || (forceReparse && curState instanceof AnalysisParsedGraph)) {
                AnalysisParsedGraph graph = parseGraph(bb, curState);
                if (graph != null) {
                    return graph;
                }
            } else if (curState instanceof ReentrantLock) {
                waitOnLock((ReentrantLock) curState);

            } else if (!forceReparse && curState instanceof AnalysisParsedGraph) {
                return (AnalysisParsedGraph) curState;

            } else if (curState instanceof Throwable) {
                throw AnalysisError.shouldNotReachHere("parsing had failed in another thread", (Throwable) curState);

            } else if (curState == GRAPH_CACHE_CLEARED) {
                return null;

            } else {
                throw AnalysisError.shouldNotReachHere("Unknown state: " + curState);
            }
        }
    }

    private AnalysisParsedGraph parseGraph(BigBang bb, Object expectedValue) {
        ReentrantLock lock = new ReentrantLock();
        lock.lock();
        try {
            /*
             * Atomically try to claim the parsing. Note that the lock must be locked already, and
             * remain locked until the parsing is done. Other threads will wait on this lock.
             */
            if (!parsedGraphCacheState.compareAndSet(expectedValue, lock)) {
                /* We lost the race, another thread is doing the parsing. */
                return null;
            }

            AnalysisParsedGraph graph = AnalysisParsedGraph.parseBytecode(bb, this);

            /*
             * Since we still hold the parsing lock, the transition form "parsing" to "parsed"
             * cannot fail.
             */
            boolean result = parsedGraphCacheState.compareAndSet(lock, graph);
            AnalysisError.guarantee(result, "State transition failed");

            return graph;

        } catch (Throwable ex) {
            parsedGraphCacheState.set(ex);
            throw ex;

        } finally {
            lock.unlock();
        }
    }

    private void waitOnLock(ReentrantLock lock) {
        AnalysisError.guarantee(!lock.isHeldByCurrentThread(), "Recursive parsing request, would lead to endless waiting loop");

        lock.lock();
        try {
            /*
             * When we can acquire the lock, parsing has finished. The next loop iteration will
             * return the result.
             */
            AnalysisError.guarantee(parsedGraphCacheState.get() != lock, "Parsing must have finished in the thread that installed the lock");
        } finally {
            lock.unlock();
        }
    }

    public StructuredGraph decodeAnalyzedGraph(DebugContext debug, Iterable<EncodedNodeReference> nodeReferences) {
        if (analyzedGraph == null) {
            return null;
        }

        return decodeAnalyzedGraph(debug, nodeReferences, analyzedGraph.trackNodeSourcePosition(), GraphDecoder::new);
    }

    /**
     * Returns the {@link StructuredGraph Graal IR} for the method that has been processed by the
     * static analysis.
     */
    public StructuredGraph decodeAnalyzedGraph(DebugContext debug, Iterable<EncodedNodeReference> nodeReferences, boolean trackNodeSourcePosition,
                    BiFunction<Architecture, StructuredGraph, GraphDecoder> decoderProvider) {
        if (analyzedGraph == null) {
            return null;
        }

        var allowAssumptions = getUniverse().hostVM().allowAssumptions(this);
        // Note we never record inlined methods. This is correct even for runtime compiled methods
        StructuredGraph result = new StructuredGraph.Builder(debug.getOptions(), debug, allowAssumptions).method(this).recordInlinedMethods(false).trackNodeSourcePosition(
                        trackNodeSourcePosition).build();
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
    public void setReturnsAllInstantiatedTypes() {
        returnsAllInstantiatedTypes = true;
    }

    public boolean getReturnsAllInstantiatedTypes() {
        return returnsAllInstantiatedTypes;
    }

    protected abstract AnalysisMethod createMultiMethod(AnalysisMethod analysisMethod, MultiMethodKey newMultiMethodKey);

    public abstract boolean isImplementationInvokable();
}
