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
package com.oracle.svm.hosted.meta;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static com.oracle.svm.core.util.VMError.unimplemented;
import static com.oracle.svm.hosted.code.SubstrateCompilationDirectives.DEOPT_TARGET_METHOD;
import static com.oracle.svm.hosted.code.SubstrateCompilationDirectives.RUNTIME_COMPILED_METHOD;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.JavaMethodContext;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.infrastructure.GraphProvider;
import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.results.StaticAnalysisResults;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.code.StubCallingConvention;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SubstrateMethodPointerConstant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.code.CompilationInfo;

import jdk.internal.vm.annotation.ForceInline;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;

public final class HostedMethod implements SharedMethod, WrappedJavaMethod, GraphProvider, JavaMethodContext, OriginalMethodProvider, MultiMethod {

    public static final String METHOD_NAME_COLLISION_SEPARATOR = "*";
    public static final String MULTI_METHOD_KEY_SEPARATOR = "**";

    public final AnalysisMethod wrapped;

    private final HostedType holder;
    private final Signature signature;
    private final ConstantPool constantPool;
    private final ExceptionHandler[] handlers;
    StaticAnalysisResults staticAnalysisResults;
    int vtableIndex = -1;

    /**
     * The address offset of the compiled code relative to the code of the first method in the
     * buffer.
     */
    private int codeAddressOffset;
    private boolean codeAddressOffsetValid;
    private boolean compiled;

    /**
     * All concrete methods that can actually be called when calling this method. This includes all
     * overridden methods in subclasses, as well as this method if it is non-abstract.
     */
    HostedMethod[] implementations;

    public final CompilationInfo compilationInfo;
    private final LocalVariableTable localVariableTable;

    private final String name;
    private final String uniqueShortName;

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
    private static final AtomicReferenceFieldUpdater<HostedMethod, Map> MULTIMETHOD_MAP_UPDATER = AtomicReferenceFieldUpdater.newUpdater(HostedMethod.class, Map.class,
                    "multiMethodMap");

    public static final HostedMethod[] EMPTY_ARRAY = new HostedMethod[0];

    static HostedMethod create(HostedUniverse universe, AnalysisMethod wrapped, HostedType holder, Signature signature,
                    ConstantPool constantPool, ExceptionHandler[] handlers) {
        LocalVariableTable localVariableTable = createLocalVariableTable(universe, wrapped);

        return create0(wrapped, holder, signature, constantPool, handlers, ORIGINAL_METHOD, null, localVariableTable);
    }

    private static HostedMethod create0(AnalysisMethod wrapped, HostedType holder, Signature signature,
                    ConstantPool constantPool, ExceptionHandler[] handlers, MultiMethodKey key, Map<MultiMethodKey, MultiMethod> multiMethodMap, LocalVariableTable localVariableTable) {
        assert !(multiMethodMap == null && key != ORIGINAL_METHOD);

        Function<Integer, Pair<String, String>> nameGenerator = (collisionCount) -> {
            String name = wrapped.getName();
            if (key != ORIGINAL_METHOD) {
                name += MULTI_METHOD_KEY_SEPARATOR + key;
            }
            if (collisionCount > 0) {
                name = name + METHOD_NAME_COLLISION_SEPARATOR + collisionCount;
            }
            String uniqueShortName = SubstrateUtil.uniqueShortName(holder.getJavaClass().getClassLoader(), holder, name, signature, wrapped.isConstructor());

            return Pair.create(name, uniqueShortName);
        };

        Pair<String, String> names = ImageSingletons.lookup(HostedMethodNameFactory.class).createNames(nameGenerator);

        return new HostedMethod(wrapped, holder, signature, constantPool, handlers, names.getLeft(), names.getRight(), localVariableTable, key, multiMethodMap);
    }

    private static LocalVariableTable createLocalVariableTable(HostedUniverse universe, AnalysisMethod wrapped) {
        LocalVariableTable lvt = wrapped.getLocalVariableTable();
        if (lvt == null) {
            return null;
        }
        try {
            Local[] origLocals = lvt.getLocals();
            Local[] newLocals = new Local[origLocals.length];
            for (int i = 0; i < newLocals.length; ++i) {
                Local origLocal = origLocals[i];
                JavaType origType = origLocal.getType();
                if (!universe.contains(origType)) {
                    throw new UnsupportedFeatureException("No HostedType for given AnalysisType");
                }
                HostedType newType = universe.lookup(origType);
                newLocals[i] = new Local(origLocal.getName(), newType, origLocal.getStartBCI(), origLocal.getEndBCI(), origLocal.getSlot());
            }
            return new LocalVariableTable(newLocals);
        } catch (UnsupportedFeatureException e) {
            return null;
        }
    }

    private HostedMethod(AnalysisMethod wrapped, HostedType holder, Signature signature, ConstantPool constantPool,
                    ExceptionHandler[] handlers, String name, String uniqueShortName, LocalVariableTable localVariableTable, MultiMethodKey multiMethodKey,
                    Map<MultiMethodKey, MultiMethod> multiMethodMap) {
        this.wrapped = wrapped;
        this.holder = holder;
        this.signature = signature;
        this.constantPool = constantPool;
        this.handlers = handlers;
        this.compilationInfo = new CompilationInfo(this);
        this.localVariableTable = localVariableTable;
        this.name = name;
        this.uniqueShortName = uniqueShortName;
        this.multiMethodKey = multiMethodKey;
        this.multiMethodMap = multiMethodMap;
    }

    @Override
    public HostedMethod[] getImplementations() {
        return implementations;
    }

    public String getQualifiedName() {
        return wrapped.getQualifiedName();
    }

    public void setCodeAddressOffset(int address) {
        assert isCompiled();
        assert !codeAddressOffsetValid;

        codeAddressOffset = address;
        codeAddressOffsetValid = true;
    }

    /**
     * Returns the address offset of the compiled code relative to the code of the first method in
     * the buffer.
     */
    public int getCodeAddressOffset() {
        if (!codeAddressOffsetValid) {
            throw VMError.shouldNotReachHere(format("%H.%n(%p)") + ": has no code address offset set.");
        }
        return codeAddressOffset;
    }

    public boolean isCodeAddressOffsetValid() {
        return codeAddressOffsetValid;
    }

    public void setCompiled() {
        this.compiled = true;
    }

    public boolean isCompiled() {
        return compiled;
    }

    public String getUniqueShortName() {
        return uniqueShortName;
    }

    /*
     * Release compilation related information.
     */
    public void clear() {
        compilationInfo.clear();
        staticAnalysisResults = null;
    }

    @Override
    public boolean hasCodeOffsetInImage() {
        throw unimplemented();
    }

    @Override
    public int getCodeOffsetInImage() {
        throw unimplemented();
    }

    @Override
    public int getDeoptOffsetInImage() {
        int result = 0;
        HostedMethod deoptTarget = getMultiMethod(DEOPT_TARGET_METHOD);
        if (deoptTarget != null && deoptTarget.isCodeAddressOffsetValid()) {
            result = deoptTarget.getCodeAddressOffset();
            assert result != 0;
        }
        return result;
    }

    @Override
    public AnalysisMethod getWrapped() {
        return wrapped;
    }

    @Override
    public Parameter[] getParameters() {
        return wrapped.getParameters();
    }

    @Override
    public boolean isDeoptTarget() {
        return multiMethodKey == DEOPT_TARGET_METHOD;
    }

    @Override
    public boolean canDeoptimize() {
        return compilationInfo.canDeoptForTesting() || multiMethodKey == RUNTIME_COMPILED_METHOD;
    }

    public boolean hasVTableIndex() {
        return vtableIndex != -1;
    }

    @Override
    public int getVTableIndex() {
        assert vtableIndex != -1;
        return vtableIndex;
    }

    @Override
    public Deoptimizer.StubType getDeoptStubType() {
        Deoptimizer.DeoptStub stubAnnotation = getAnnotation(Deoptimizer.DeoptStub.class);
        if (stubAnnotation != null) {
            return stubAnnotation.stubType();
        }
        return Deoptimizer.StubType.NoDeoptStub;
    }

    /**
     * Returns true if this method is a native entry point, i.e., called from C code. The method
     * must not be called from Java code then.
     */
    @Override
    public boolean isEntryPoint() {
        return wrapped.isEntryPoint();
    }

    @Override
    public boolean hasCalleeSavedRegisters() {
        return StubCallingConvention.Utils.hasStubCallingConvention(this);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Signature getSignature() {
        return signature;
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        return wrapped.buildGraph(debug, method, providers, purpose);
    }

    @Override
    public boolean allowRuntimeCompilation() {
        return wrapped.allowRuntimeCompilation();
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
    public HostedType getDeclaringClass() {
        return holder;
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
        return implementations.length == 1 && implementations[0].equals(this);
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        return handlers;
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        return wrapped.asStackTraceElement(bci);
    }

    @Override
    public StaticAnalysisResults getProfilingInfo() {
        return staticAnalysisResults;
    }

    @Override
    public StaticAnalysisResults getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        return staticAnalysisResults;
    }

    @Override
    public ConstantPool getConstantPool() {
        return constantPool;
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
        return wrapped.hasNeverInlineDirective();
    }

    @Override
    public boolean shouldBeInlined() {
        return getAnnotation(AlwaysInline.class) != null || getAnnotation(ForceInline.class) != null;
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        return wrapped.getLineNumberTable();
    }

    @Override
    public String toString() {
        return "HostedMethod<" + format("%h.%n") + " -> " + wrapped.toString() + ">";
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
    public boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
        return hasVTableIndex();
    }

    @Override
    public Constant getEncoding() {
        return new SubstrateMethodPointerConstant(new MethodPointer(this));
    }

    @Override
    public boolean isDefault() {
        throw unimplemented();
    }

    @Override
    public SpeculationLog getSpeculationLog() {
        throw shouldNotReachHere();
    }

    @Override
    public JavaMethod asJavaMethod() {
        return this;
    }

    @Override
    public int hashCode() {
        return wrapped.hashCode();
    }

    @Override
    public Executable getJavaMethod() {
        return OriginalMethodProvider.getJavaMethod(getDeclaringClass().universe.getSnippetReflection(), wrapped);
    }

    @Override
    public MultiMethodKey getMultiMethodKey() {
        return multiMethodKey;
    }

    @Override
    public HostedMethod getOrCreateMultiMethod(MultiMethodKey key) {
        if (key == multiMethodKey) {
            return this;
        }

        if (multiMethodMap == null) {
            ConcurrentHashMap<MultiMethodKey, MultiMethod> newMultiMethodMap = new ConcurrentHashMap<>();
            newMultiMethodMap.put(multiMethodKey, this);
            MULTIMETHOD_MAP_UPDATER.compareAndSet(this, null, newMultiMethodMap);
        }

        return (HostedMethod) multiMethodMap.computeIfAbsent(key, (k) -> {
            HostedMethod newMultiMethod = create0(wrapped, holder, signature, constantPool, handlers, k, multiMethodMap, localVariableTable);
            newMultiMethod.staticAnalysisResults = staticAnalysisResults;
            newMultiMethod.implementations = implementations;
            newMultiMethod.vtableIndex = vtableIndex;
            return newMultiMethod;
        });
    }

    @Override
    public HostedMethod getMultiMethod(MultiMethodKey key) {
        if (key == multiMethodKey) {
            return this;
        } else if (multiMethodMap == null) {
            return null;
        } else {
            return (HostedMethod) multiMethodMap.get(key);
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
}

@Platforms(Platform.HOSTED_ONLY.class)
@AutomaticallyRegisteredFeature
class HostedMethodNameFactory implements InternalFeature {
    Map<String, Integer> methodNameCount = new ConcurrentHashMap<>();
    Set<String> uniqueShortNames = ConcurrentHashMap.newKeySet();

    Pair<String, String> createNames(Function<Integer, Pair<String, String>> nameGenerator) {
        Pair<String, String> result = nameGenerator.apply(0);

        int collisionCount = methodNameCount.merge(result.getRight(), 0, (oldValue, value) -> oldValue + 1);

        if (collisionCount != 0) {
            result = nameGenerator.apply(collisionCount);
        }

        boolean added = uniqueShortNames.add(result.getRight());
        VMError.guarantee(added, "failed to generate uniqueShortName for HostedMethod: " + result.getRight());

        return result;
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        methodNameCount = null;
        uniqueShortNames = null;
    }
}
