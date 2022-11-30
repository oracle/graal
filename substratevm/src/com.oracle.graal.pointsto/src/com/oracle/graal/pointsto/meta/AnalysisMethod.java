/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.BytecodeParser.BytecodeParserError;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.EncodedGraph.EncodedNodeReference;
import org.graalvm.compiler.nodes.GraphDecoder;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.infrastructure.GraphProvider;
import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.graal.pointsto.infrastructure.WrappedSignature;
import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.graal.pointsto.results.StaticAnalysisResults;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.AtomicUtils;

import jdk.vm.ci.code.BytecodePosition;
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

public abstract class AnalysisMethod extends AnalysisElement implements WrappedJavaMethod, GraphProvider, OriginalMethodProvider {
    private static final AtomicIntegerFieldUpdater<AnalysisMethod> isVirtualRootMethodUpdater = AtomicIntegerFieldUpdater
                    .newUpdater(AnalysisMethod.class, "isVirtualRootMethod");

    private static final AtomicIntegerFieldUpdater<AnalysisMethod> isDirectRootMethodUpdater = AtomicIntegerFieldUpdater
                    .newUpdater(AnalysisMethod.class, "isDirectRootMethod");

    private static final AtomicReferenceFieldUpdater<AnalysisMethod, Object> isInvokedUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisMethod.class, Object.class, "isInvoked");

    private static final AtomicReferenceFieldUpdater<AnalysisMethod, Object> isImplementationInvokedUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisMethod.class, Object.class, "isImplementationInvoked");

    private static final AtomicReferenceFieldUpdater<AnalysisMethod, Object> isIntrinsicMethodUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisMethod.class, Object.class, "isIntrinsicMethod");

    private static final AtomicReferenceFieldUpdater<AnalysisMethod, Object> isInlinedUpdater = AtomicReferenceFieldUpdater
                    .newUpdater(AnalysisMethod.class, Object.class, "isInlined");

    public final ResolvedJavaMethod wrapped;

    private final int id;
    private final boolean hasNeverInlineDirective;
    private final ExceptionHandler[] exceptionHandlers;
    private final LocalVariableTable localVariableTable;
    private final String qualifiedName;

    private final AnalysisType declaringClass;
    private final int parsingContextMaxDepth;

    /** Virtually invoked method registered as root. */
    @SuppressWarnings("unused") private volatile int isVirtualRootMethod;
    /** Direct (special or static) invoked method registered as root. */
    @SuppressWarnings("unused") private volatile int isDirectRootMethod;
    private Object entryPointData;
    @SuppressWarnings("unused") private volatile Object isInvoked;
    @SuppressWarnings("unused") private volatile Object isImplementationInvoked;
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

    public AnalysisMethod(AnalysisUniverse universe, ResolvedJavaMethod wrapped) {
        this.wrapped = wrapped;
        this.id = universe.nextMethodId.getAndIncrement();
        declaringClass = universe.lookup(wrapped.getDeclaringClass());

        hasNeverInlineDirective = universe.hostVM().hasNeverInlineDirective(wrapped);

        if (PointstoOptions.TrackAccessChain.getValue(universe.hostVM().options())) {
            startTrackInvocations();
        }

        ExceptionHandler[] original = wrapped.getExceptionHandlers();
        exceptionHandlers = new ExceptionHandler[original.length];
        for (int i = 0; i < original.length; i++) {
            ExceptionHandler h = original[i];
            JavaType catchType = getCatchType(universe, h);
            exceptionHandlers[i] = new ExceptionHandler(h.getStartBCI(), h.getEndBCI(), h.getHandlerBCI(), h.catchTypeCPI(), catchType);
        }

        LocalVariableTable newLocalVariableTable = null;
        if (wrapped.getLocalVariableTable() != null) {
            try {
                Local[] origLocals = wrapped.getLocalVariableTable().getLocals();
                Local[] newLocals = new Local[origLocals.length];
                ResolvedJavaType accessingClass = getDeclaringClass().getWrapped();
                for (int i = 0; i < newLocals.length; ++i) {
                    Local origLocal = origLocals[i];
                    ResolvedJavaType origLocalType = origLocal.getType() instanceof ResolvedJavaType ? (ResolvedJavaType) origLocal.getType() : origLocal.getType().resolve(accessingClass);
                    AnalysisType type = universe.lookup(origLocalType);
                    newLocals[i] = new Local(origLocal.getName(), type, origLocal.getStartBCI(), origLocal.getEndBCI(), origLocal.getSlot());
                }
                newLocalVariableTable = new LocalVariableTable(newLocals);
            } catch (LinkageError | UnsupportedFeatureException | BytecodeParserError e) {
                newLocalVariableTable = null;
            }

        }
        localVariableTable = newLocalVariableTable;
        this.qualifiedName = format("%H.%n(%P)");

        registerSignatureTypes();
        parsingContextMaxDepth = PointstoOptions.ParsingContextMaxDepth.getValue(universe.hostVM.options());
    }

    /**
     * Lookup the parameters and return type so that they are added to the universe even if the
     * method is never linked and parsed.
     */
    private void registerSignatureTypes() {
        boolean isStatic = Modifier.isStatic(getModifiers());
        int parameterCount = getSignature().getParameterCount(!isStatic);

        int offset = isStatic ? 0 : 1;
        for (int i = offset; i < parameterCount; i++) {
            getSignature().getParameterType(i - offset, getDeclaringClass());
        }

        getSignature().getReturnType(getDeclaringClass());
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    private JavaType getCatchType(AnalysisUniverse universe, ExceptionHandler handler) {
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

    private AnalysisUniverse getUniverse() {
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
    public abstract BytecodePosition getParsingReason();

    /**
     * @return the parsing context in which given method was parsed
     */
    public final StackTraceElement[] getParsingContext() {
        List<StackTraceElement> trace = new ArrayList<>();
        BytecodePosition curr = getParsingReason();

        while (curr != null) {
            if (trace.size() > parsingContextMaxDepth) {
                trace.add(ReportUtils.truncatedStackTraceSentinel(this));
                break;
            }
            AnalysisMethod caller = (AnalysisMethod) curr.getMethod();
            trace.add(caller.asStackTraceElement(curr.getBCI()));
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
        AtomicUtils.atomicSetAndRun(this, reason, isIntrinsicMethodUpdater, this::onReachable);
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
        assert !Modifier.isAbstract(getModifiers());

        /*
         * The class constant of the declaring class is used for exception metadata, so marking a
         * method as invoked also makes the declaring class reachable.
         *
         * Even though the class could in theory be marked as reachable only if we successfully mark
         * the method as invoked, it would have an unwanted side effect, where this method could
         * return before the class gets marked as reachable.
         */
        getDeclaringClass().registerAsReachable("declared method " + this.format("%H.%n(%p)") + " is registered as implementation invoked");
        return AtomicUtils.atomicSetAndRun(this, reason, isImplementationInvokedUpdater, this::onReachable);
    }

    public void registerAsInlined(Object reason) {
        assert isValidReason(reason) : "Registering a method as inlined needs to provide a valid reason, found: " + reason;
        AtomicUtils.atomicSetAndRun(this, reason, isInlinedUpdater, this::onReachable);
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

    /**
     * Registers this method as a virtual root for the analysis.
     *
     * The class constant of the declaring class is used for exception metadata, so marking a method
     * as invoked also makes the declaring class reachable.
     *
     * Class is always marked as reachable regardless of the success of the atomic mark, same reason
     * as in {@link AnalysisMethod#registerAsImplementationInvoked(Object)}.
     */
    public boolean registerAsVirtualRootMethod() {
        getDeclaringClass().registerAsReachable("declared method " + this.format("%H.%n(%p)") + " is registered as virtual root");
        return AtomicUtils.atomicMark(this, isVirtualRootMethodUpdater);
    }

    /**
     * Registers this method as a direct (special or static) root for the analysis.
     */
    public boolean registerAsDirectRootMethod() {
        getDeclaringClass().registerAsReachable("declared method " + this.format("%H.%n(%p)") + " is registered as direct root");
        return AtomicUtils.atomicMark(this, isDirectRootMethodUpdater);
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

    /**
     * Returns true if the method body can ever be executed. Methods registered as root are also
     * registered as implementation invoked when they are linked.
     */
    public boolean isImplementationInvoked() {
        return !Modifier.isAbstract(getModifiers()) && (isIntrinsicMethod() || AtomicUtils.isSet(this, isImplementationInvokedUpdater));
    }

    @Override
    public boolean isReachable() {
        return isImplementationInvoked() || AtomicUtils.isSet(this, isInlinedUpdater);
    }

    @Override
    public boolean isTriggered() {
        if (isReachable()) {
            return true;
        }
        return isClassInitializer() && getDeclaringClass().isInitialized();
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
                 * override notifications. If this method resolves in a super type, and it has an
                 * override handler installed in that type, pass this method to the callback. It
                 * doesn't matter if the superMethod is actually reachable, only if it has any
                 * override handlers installed.
                 */
                AnalysisMethod superMethod = resolveInType(superType);
                if (superMethod != null) {
                    superMethod.notifyMethodOverride(AnalysisMethod.this);
                }
            });
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
        return wrapped.getName();
    }

    @Override
    public WrappedSignature getSignature() {
        return getUniverse().lookup(wrapped.getSignature(), getDeclaringClass());
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        if (wrapped instanceof GraphProvider) {
            return ((GraphProvider) wrapped).buildGraph(debug, method, providers, purpose);
        }
        return null;
    }

    @Override
    public boolean allowRuntimeCompilation() {
        if (wrapped instanceof GraphProvider) {
            return ((GraphProvider) wrapped).allowRuntimeCompilation();
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
        assert getUniverse().analysisDataValid;
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
        /*
         * This is also the profiling information used when parsing methods for static analysis, so
         * it needs to be conservative.
         */
        return StaticAnalysisResults.NO_RESULTS;
    }

    @Override
    public ConstantPool getConstantPool() {
        return getUniverse().lookup(wrapped.getConstantPool(), getDeclaringClass());
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
        return "AnalysisMethod<" + format("%H.%n") + " -> " + wrapped.toString() + ">";
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
    public Executable getJavaMethod() {
        return OriginalMethodProvider.getJavaMethod(getUniverse().getOriginalSnippetReflection(), wrapped);
    }

    /**
     * Ensures that the method has been parsed, i.e., that the {@link StructuredGraph Graal IR} for
     * the method is available.
     */
    public AnalysisParsedGraph ensureGraphParsed(BigBang bb) {
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

            if (curState == GRAPH_CACHE_UNPARSED) {
                ReentrantLock lock = new ReentrantLock();
                lock.lock();
                try {
                    /*
                     * Atomically try to claim the parsing. Note that the lock must be locked
                     * already, and remain locked until the parsing is done. Other threads will wait
                     * on this lock.
                     */
                    if (!parsedGraphCacheState.compareAndSet(GRAPH_CACHE_UNPARSED, lock)) {
                        /* We lost the race, another thread is doing the parsing. */
                        continue;
                    }

                    AnalysisParsedGraph graph = AnalysisParsedGraph.parseBytecode(bb, this);

                    /*
                     * Since we still hold the parsing lock, the transition form "parsing" to
                     * "parsed" cannot fail.
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

            } else if (curState instanceof ReentrantLock) {
                ReentrantLock lock = (ReentrantLock) curState;
                AnalysisError.guarantee(!lock.isHeldByCurrentThread(), "Recursive parsing request, would lead to endless waiting loop");

                lock.lock();
                /*
                 * When we can acquire the lock, parsing has finished. The next loop iteration will
                 * return the result.
                 */
                AnalysisError.guarantee(parsedGraphCacheState.get() != lock, "Parsing must have finished in the thread that installed the lock");
                lock.unlock();

            } else if (curState instanceof AnalysisParsedGraph) {
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

    /**
     * Returns the {@link StructuredGraph Graal IR} for the method that has been processed by the
     * static analysis.
     */
    public StructuredGraph decodeAnalyzedGraph(DebugContext debug, Iterable<EncodedNodeReference> nodeReferences) {
        if (analyzedGraph == null) {
            return null;
        }

        StructuredGraph result = new StructuredGraph.Builder(debug.getOptions(), debug)
                        .method(this)
                        .recordInlinedMethods(false)
                        .trackNodeSourcePosition(analyzedGraph.trackNodeSourcePosition()).build();
        GraphDecoder decoder = new GraphDecoder(AnalysisParsedGraph.HOST_ARCHITECTURE, result);
        decoder.decode(analyzedGraph, nodeReferences);
        return result;
    }

    public void setAnalyzedGraph(EncodedGraph analyzedGraph) {
        this.analyzedGraph = analyzedGraph;
    }
}
