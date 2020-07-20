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
package com.oracle.graal.pointsto.meta;

import static jdk.vm.ci.common.JVMCIError.shouldNotReachHere;
import static jdk.vm.ci.common.JVMCIError.unimplemented;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.java.BytecodeParser.BytecodeParserError;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.util.GuardedAnnotationAccess;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.flow.AbstractVirtualInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.infrastructure.GraphProvider;
import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.graal.pointsto.infrastructure.WrappedSignature;
import com.oracle.graal.pointsto.results.StaticAnalysisResults;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.util.ReflectionUtil;

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

public class AnalysisMethod implements WrappedJavaMethod, GraphProvider, OriginalMethodProvider {

    private final AnalysisUniverse universe;
    public final ResolvedJavaMethod wrapped;

    private final int id;
    private final ExceptionHandler[] exceptionHandlers;
    private final LocalVariableTable localVariableTable;
    private final String qualifiedName;
    private MethodTypeFlow typeFlow;
    private final AnalysisType declaringClass;

    private boolean isRootMethod;
    private boolean isIntrinsicMethod;
    private Object entryPointData;
    private boolean isInvoked;
    private boolean isImplementationInvoked;

    /**
     * All concrete methods that can actually be called when calling this method. This includes all
     * overridden methods in subclasses, as well as this method if it is non-abstract.
     */
    protected AnalysisMethod[] implementations;

    private ConcurrentMap<InvokeTypeFlow, Object> invokedBy;
    private ConcurrentMap<InvokeTypeFlow, Object> implementationInvokedBy;

    public AnalysisMethod(AnalysisUniverse universe, ResolvedJavaMethod wrapped) {
        this.universe = universe;
        this.wrapped = wrapped;
        this.id = universe.nextMethodId.getAndIncrement();
        declaringClass = universe.lookup(wrapped.getDeclaringClass());

        if (PointstoOptions.TrackAccessChain.getValue(universe.hostVM().options())) {
            startTrackInvocations();
        }

        ExceptionHandler[] original = wrapped.getExceptionHandlers();
        exceptionHandlers = new ExceptionHandler[original.length];
        for (int i = 0; i < original.length; i++) {
            ExceptionHandler h = original[i];
            JavaType catchType = getCatchType(h);
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

        typeFlow = new MethodTypeFlow(universe.hostVM().options(), this);

        if (getName().startsWith("$SWITCH_TABLE$")) {
            /*
             * The Eclipse Java compiler generates methods that lazily initializes tables for Enum
             * switches. The first invocation fills the table, subsequent invocations reuse the
             * table. We call the method here, so that the table gets built. This ensures that Enum
             * switches are allocation-free at run time.
             */
            assert Modifier.isStatic(getModifiers());
            assert getSignature().getParameterCount(false) == 0;
            try {
                Method switchTableMethod = ReflectionUtil.lookupMethod(getDeclaringClass().getJavaClass(), getName());
                switchTableMethod.invoke(null);
            } catch (ReflectiveOperationException ex) {
                throw GraalError.shouldNotReachHere(ex);
            }
        }
        this.qualifiedName = format("%H.%n(%P)");
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    private JavaType getCatchType(ExceptionHandler handler) {
        JavaType catchType = handler.getCatchType();
        if (catchType == null) {
            return null;
        }
        ResolvedJavaType resolvedCatchType;
        try {
            resolvedCatchType = catchType.resolve(wrapped.getDeclaringClass());
        } catch (NoClassDefFoundError e) {
            /*
             * Type resolution fails if the catch type is missing. Just return the unresolved type.
             * The analysis doesn't model unresolved types, but we can reuse the JVMCI type; the
             * UniverseBuilder and the BytecodeParser know how to deal with that.
             */
            return catchType;
        }
        return universe.lookup(resolvedCatchType);
    }

    public void cleanupAfterAnalysis() {
        typeFlow = null;
        invokedBy = null;
        implementationInvokedBy = null;
    }

    public void startTrackInvocations() {
        if (invokedBy == null) {
            invokedBy = new ConcurrentHashMap<>();
        }
        if (implementationInvokedBy == null) {
            implementationInvokedBy = new ConcurrentHashMap<>();
        }
    }

    public int getId() {
        return id;
    }

    public MethodTypeFlow getTypeFlow() {
        return typeFlow;
    }

    /**
     * Registers this method as intrinsified to Graal nodes via a {@link InvocationPlugin graph
     * builder plugin}. Such a method is treated similar to an invoked method. For example, method
     * resolution must be able to find the method (otherwise the intrinsification would not work).
     */
    public void registerAsIntrinsicMethod() {
        isIntrinsicMethod = true;
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

    public void registerAsInvoked(InvokeTypeFlow invoke) {
        isInvoked = true;
        if (invokedBy != null && invoke != null) {
            invokedBy.put(invoke, Boolean.TRUE);
        }
    }

    public void registerAsImplementationInvoked(InvokeTypeFlow invoke) {
        assert !Modifier.isAbstract(getModifiers());
        isImplementationInvoked = true;
        if (implementationInvokedBy != null && invoke != null) {
            implementationInvokedBy.put(invoke, Boolean.TRUE);
        }

        /*
         * The class constant of the declaring class is used for exception metadata, so marking a
         * method as invoked also makes the declaring class reachable.
         */
        getDeclaringClass().registerAsInTypeCheck();
    }

    /** Get the set of all callers for this method, as inferred by the static analysis. */
    public Set<AnalysisMethod> getCallers() {
        return getInvokeLocations().stream().map(location -> (AnalysisMethod) location.getMethod()).collect(Collectors.toSet());
    }

    /** Get the list of all invoke locations for this method, as inferred by the static analysis. */
    public List<BytecodePosition> getInvokeLocations() {
        List<BytecodePosition> locations = new ArrayList<>();
        for (InvokeTypeFlow invoke : implementationInvokedBy.keySet()) {
            if (InvokeTypeFlow.isContextInsensitiveVirtualInvoke(invoke)) {
                locations.addAll(((AbstractVirtualInvokeTypeFlow) invoke).getInvokeLocations());
            } else {
                locations.add(invoke.getSource());
            }
        }
        return locations;
    }

    public boolean isEntryPoint() {
        return entryPointData != null;
    }

    public Object getEntryPointData() {
        return entryPointData;
    }

    public boolean isIntrinsicMethod() {
        return isIntrinsicMethod;
    }

    public void registerAsRootMethod() {
        isRootMethod = true;

        /*
         * The class constant of the declaring class is used for exception metadata, so marking a
         * method as invoked also makes the declaring class reachable.
         */
        getDeclaringClass().registerAsInTypeCheck();
    }

    public boolean isRootMethod() {
        return isRootMethod;
    }

    public boolean isSimplyInvoked() {
        return isInvoked;
    }

    public boolean isSimplyImplementationInvoked() {
        return isImplementationInvoked;
    }

    /**
     * Returns true if this method is ever used as the target of a call site.
     */
    public boolean isInvoked() {
        return isIntrinsicMethod || isEntryPoint() || isInvoked;
    }

    /**
     * Returns true if the method body can ever be executed.
     */
    public boolean isImplementationInvoked() {
        return !Modifier.isAbstract(getModifiers()) && (isIntrinsicMethod || isEntryPoint() || isImplementationInvoked);
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
        return universe.lookup(wrapped.getSignature(), getDeclaringClass());
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
        throw unimplemented();
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
        assert universe.analysisDataValid;
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
        return universe.lookup(wrapped.getConstantPool(), getDeclaringClass());
    }

    @Override
    public Annotation[] getAnnotations() {
        return GuardedAnnotationAccess.getAnnotations(wrapped);
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return GuardedAnnotationAccess.getDeclaredAnnotations(wrapped);
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return GuardedAnnotationAccess.getAnnotation(wrapped, annotationClass);
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
        return true;
    }

    @Override
    public boolean hasNeverInlineDirective() {
        return wrapped.hasNeverInlineDirective();
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
        return OriginalMethodProvider.getJavaMethod(universe.getOriginalSnippetReflection(), wrapped);
    }

    /**
     * Unique, per method, context insensitive invoke. The context insensitive invoke uses the
     * receiver type of the method, i.e., its declaring class. Therefore this invoke will link with
     * all possible callees.
     */
    private final AtomicReference<InvokeTypeFlow> contextInsensitiveInvoke = new AtomicReference<>();

    public InvokeTypeFlow initAndGetContextInsensitiveInvoke(BigBang bb, BytecodePosition originalLocation) {
        if (contextInsensitiveInvoke.get() == null) {
            InvokeTypeFlow invoke = InvokeTypeFlow.createContextInsensitiveInvoke(bb, this, originalLocation);
            boolean set = contextInsensitiveInvoke.compareAndSet(null, invoke);
            if (set) {
                /*
                 * Only register the winning context insensitive invoke as an observer of the target
                 * method declaring class type flow.
                 */
                InvokeTypeFlow.initContextInsensitiveInvoke(bb, this, invoke);
            }
        }
        return contextInsensitiveInvoke.get();
    }

    public InvokeTypeFlow getContextInsensitiveInvoke() {
        InvokeTypeFlow invoke = contextInsensitiveInvoke.get();
        AnalysisError.guarantee(invoke != null);
        return invoke;
    }

}
