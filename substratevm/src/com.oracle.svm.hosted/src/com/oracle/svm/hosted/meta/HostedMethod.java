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
package com.oracle.svm.hosted.meta;

import static com.oracle.svm.core.util.VMError.intentionallyUnimplemented;
import static com.oracle.svm.core.util.VMError.shouldNotReachHereAtRuntime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.infrastructure.ResolvedSignature;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.ImageCodeInfo;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.graal.code.CustomCallingConventionMethod;
import com.oracle.svm.core.graal.code.ExplicitCallingConvention;
import com.oracle.svm.core.graal.code.StubCallingConvention;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionType;
import com.oracle.svm.core.graal.phases.SubstrateSafepointInsertionPhase;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SubstrateMethodPointerConstant;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.OpenTypeWorldFeature;
import com.oracle.svm.hosted.code.CompilationInfo;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.JavaMethodContext;
import jdk.graal.compiler.java.StableMethodNameFormatter;
import jdk.internal.vm.annotation.ForceInline;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

public final class HostedMethod extends HostedElement implements SharedMethod, WrappedJavaMethod, JavaMethodContext, OriginalMethodProvider, MultiMethod {

    public static final String METHOD_NAME_COLLISION_SEPARATOR = "%";

    public static final int MISSING_VTABLE_IDX = -1;
    public static final int INVALID_CODE_ADDRESS_OFFSET = -1;

    public final AnalysisMethod wrapped;

    private final HostedType holder;
    private final ResolvedSignature<HostedType> signature;
    private final ConstantPool constantPool;
    private final ExceptionHandler[] handlers;
    /**
     * Contains the index of the method computed by {@link VTableBuilder}.
     *
     * Within the closed type world, there exists a single table which describes all methods.
     * However, within the open type world, each type and interface has a unique table, so this
     * index is relative to the start of the appropriate table.
     */
    int computedVTableIndex = MISSING_VTABLE_IDX;

    /**
     * When using the open type world we must differentiate between the vtable index computed by
     * {@link VTableBuilder} for this method and the vtable index used for virtual calls.
     *
     * Note normally {@code indirectCallTarget == this}. Only for special HotSpot methods such as
     * miranda and overpass methods will the indirectCallTarget be a different method. The logic for
     * setting the indirectCallTarget can be found in
     * {@link OpenTypeWorldFeature#calculateIndirectCallTarget}.
     *
     * For additional information, see {@link SharedMethod#getIndirectCallTarget}.
     */
    private int indirectCallVTableIndex = MISSING_VTABLE_IDX;
    private HostedMethod indirectCallTarget = null;

    /**
     * The address offset of the compiled code relative to the code of the first method in the
     * buffer.
     */
    private int codeAddressOffset = INVALID_CODE_ADDRESS_OFFSET;
    /** Note that {@link #compiledInPriorLayer} does not imply {@link #compiled}. */
    private boolean compiled;
    private boolean compiledInPriorLayer;

    /**
     * All concrete methods that can actually be called when calling this method. This includes all
     * overridden methods in subclasses, as well as this method if it is non-abstract.
     * <p>
     * With an open type world analysis the list of implementations is incomplete, i.e., no
     * aggressive optimizations should be performed based on the contents of this list as one must
     * assume that additional implementations can be discovered later.
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

    static HostedMethod create(HostedUniverse universe, AnalysisMethod wrapped, HostedType holder, ResolvedSignature<HostedType> signature,
                    ConstantPool constantPool, ExceptionHandler[] handlers) {
        LocalVariableTable localVariableTable = createLocalVariableTable(universe, wrapped);

        return create0(wrapped, holder, signature, constantPool, handlers, wrapped.getMultiMethodKey(), null, localVariableTable);
    }

    private static HostedMethod create0(AnalysisMethod wrapped, HostedType holder, ResolvedSignature<HostedType> signature,
                    ConstantPool constantPool, ExceptionHandler[] handlers, MultiMethodKey key, Map<MultiMethodKey, MultiMethod> multiMethodMap, LocalVariableTable localVariableTable) {
        var generator = new HostedMethodNameFactory.NameGenerator() {

            @Override
            public HostedMethodNameFactory.MethodNameInfo generateMethodNameInfo(int collisionCount) {
                String name = wrapped.wrapped.getName(); // want name w/o any multimethodkey suffix
                if (key != ORIGINAL_METHOD) {
                    name += StableMethodNameFormatter.MULTI_METHOD_KEY_SEPARATOR + key;
                }
                if (collisionCount > 0) {
                    name = name + METHOD_NAME_COLLISION_SEPARATOR + collisionCount;
                }

                String uniqueShortName = generateUniqueName(name);

                return new HostedMethodNameFactory.MethodNameInfo(name, uniqueShortName);
            }

            @Override
            public String generateUniqueName(String name) {
                return SubstrateUtil.uniqueShortName(holder.getJavaClass().getClassLoader(), holder, name, signature, wrapped.isConstructor());
            }
        };

        HostedMethodNameFactory.MethodNameInfo names = HostedMethodNameFactory.singleton().createNames(generator, wrapped);

        return new HostedMethod(wrapped, holder, signature, constantPool, handlers, names.name(), names.uniqueShortName(), localVariableTable, key, multiMethodMap);
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

    private HostedMethod(AnalysisMethod wrapped, HostedType holder, ResolvedSignature<HostedType> signature, ConstantPool constantPool,
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
        assert isCompiled() || isCompiledInPriorLayer();
        assert codeAddressOffset == INVALID_CODE_ADDRESS_OFFSET && address != INVALID_CODE_ADDRESS_OFFSET : Assertions.errorMessage(codeAddressOffset, address);

        codeAddressOffset = address;
    }

    /**
     * Returns the address offset of the compiled code relative to the code of the first method in
     * the buffer.
     */
    public int getCodeAddressOffset() {
        if (!isCodeAddressOffsetValid()) {
            throw VMError.shouldNotReachHere(format("%H.%n(%p)") + ": has no code address offset set.");
        }
        return codeAddressOffset;
    }

    public boolean isCodeAddressOffsetValid() {
        return codeAddressOffset != INVALID_CODE_ADDRESS_OFFSET;
    }

    public void setCompiled() {
        this.compiled = true;
    }

    /**
     * Whether the method has been compiled in the current build or layer, but {@code false} if it
     * was only {@linkplain #isCompiledInPriorLayer() compiled in a prior layer}.
     */
    public boolean isCompiled() {
        return compiled;
    }

    public void setCompiledInPriorLayer() {
        this.compiledInPriorLayer = true;
    }

    /**
     * Whether the method has been compiled in a prior layer, but if so, that does not imply
     * {@link #isCompiled}.
     */
    public boolean isCompiledInPriorLayer() {
        return compiledInPriorLayer;
    }

    public String getUniqueShortName() {
        return uniqueShortName;
    }

    /*
     * Release compilation related information.
     */
    public void clear() {
        compilationInfo.clear();
    }

    @Override
    public ImageCodeInfo getImageCodeInfo() {
        throw intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public boolean forceIndirectCall() {
        /*
         * Methods delayed to the application layer need to be called indirectly as they are not
         * available in the current layer.
         */
        return isCompiledInPriorLayer() || wrapped.isDelayed();
    }

    @Override
    public boolean hasImageCodeOffset() {
        throw intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public int getImageCodeOffset() {
        throw intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public int getImageCodeDeoptOffset() {
        int result = 0;
        HostedMethod deoptTarget = getMultiMethod(SubstrateCompilationDirectives.DEOPT_TARGET_METHOD);
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
        return SubstrateCompilationDirectives.isDeoptTarget(this);
    }

    @Override
    public boolean canDeoptimize() {
        return compilationInfo.canDeoptForTesting() || multiMethodKey == SubstrateCompilationDirectives.RUNTIME_COMPILED_METHOD;
    }

    @Override
    public boolean isUninterruptible() {
        return Uninterruptible.Utils.isUninterruptible(wrapped);
    }

    @Override
    public boolean needSafepointCheck() {
        return SubstrateSafepointInsertionPhase.needSafepointCheck(wrapped);
    }

    @Override
    public boolean isForeignCallTarget() {
        return isAnnotationPresent(SubstrateForeignCallTarget.class);
    }

    @Override
    public boolean isSnippet() {
        return isAnnotationPresent(Snippet.class);
    }

    public boolean hasVTableIndex() {
        return indirectCallVTableIndex != MISSING_VTABLE_IDX;
    }

    @Override
    public int getVTableIndex() {
        assert hasVTableIndex() : "Missing vtable index for method " + this.format("%H.%n(%p)");
        return indirectCallVTableIndex;
    }

    public void setIndirectCallTarget(HostedMethod alias) {
        assert indirectCallTarget == null : indirectCallTarget;
        if (!alias.equals(this)) {
            /*
             * When there is an indirectCallTarget installed which is not the original method, we
             * currently expect the target method to either have an interface as its declaring class
             * or for the declaring class to be unchanged. If the declaring class is different, then
             * we must ensure that the layout of the vtable matches for all relevant indexes between
             * the original and alias methods' declaring classes.
             */
            VMError.guarantee(alias.getDeclaringClass().isInterface() || alias.getDeclaringClass().equals(getDeclaringClass()), "Invalid indirect call target for %s: %s", this, alias);
        }
        indirectCallTarget = alias;
    }

    @Override
    public HostedMethod getIndirectCallTarget() {
        Objects.requireNonNull(indirectCallTarget);
        return indirectCallTarget;
    }

    void finalizeIndirectCallVTableIndex() {
        indirectCallVTableIndex = indirectCallTarget.computedVTableIndex;
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
        return wrapped.isNativeEntryPoint();
    }

    @Override
    public SubstrateCallingConventionKind getCallingConventionKind() {
        return ExplicitCallingConvention.Util.getCallingConventionKind(wrapped, isEntryPoint());
    }

    @Override
    public SubstrateCallingConventionType getCustomCallingConventionType() {
        VMError.guarantee(getCallingConventionKind().isCustom(), "%s does not have a custom calling convention.", name);
        VMError.guarantee(wrapped.getWrapped() instanceof CustomCallingConventionMethod, "%s has a custom calling convention but doesn't implement %s", name, CustomCallingConventionMethod.class);
        return ((CustomCallingConventionMethod) wrapped.getWrapped()).getCallingConvention();
    }

    @Override
    public boolean hasCalleeSavedRegisters() {
        return StubCallingConvention.Utils.hasStubCallingConvention(this);
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Returns the original name of the method, without any suffix that might have been added by
     * {@link HostedMethodNameFactory}.
     */
    public String getReflectionName() {
        VMError.guarantee(this.isOriginalMethod());
        return wrapped.getName();
    }

    @Override
    public ResolvedSignature<HostedType> getSignature() {
        return signature;
    }

    @Override
    public JavaType[] toParameterTypes() {
        throw JVMCIError.shouldNotReachHere("ResolvedJavaMethod.toParameterTypes returns the wrong result for constructors.");
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
        if (holder.universe.hostVM().isClosedTypeWorld()) {
            return implementations.length == 1 && implementations[0].equals(this);
        }
        /*
         * In open type world analysis we cannot make assumptions based on discovered
         * implementations.
         */
        return wrapped.canBeStaticallyBound();
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
    public ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        return null;
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
        return wrapped.canBeInlined();
    }

    @Override
    public boolean hasNeverInlineDirective() {
        return wrapped.hasNeverInlineDirective();
    }

    @Override
    public boolean shouldBeInlined() {
        return getAnnotation(AlwaysInline.class) != null || getAnnotation(ForceInline.class) != null;
    }

    private LineNumberTable lineNumberTable;

    @Override
    public LineNumberTable getLineNumberTable() {
        if (lineNumberTable == null) {
            lineNumberTable = wrapped.getLineNumberTable();
        }
        return lineNumberTable;
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
        throw intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
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
        throw intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public SpeculationLog getSpeculationLog() {
        throw shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
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
    public ResolvedJavaMethod unwrapTowardsOriginalMethod() {
        return wrapped;
    }

    @Override
    public MultiMethodKey getMultiMethodKey() {
        return multiMethodKey;
    }

    void setMultiMethodMap(ConcurrentHashMap<MultiMethodKey, MultiMethod> newMultiMethodMap) {
        VMError.guarantee(multiMethodMap == null, "Resetting already initialized multimap");
        if (!MULTIMETHOD_MAP_UPDATER.compareAndSet(this, null, newMultiMethodMap)) {
            throw VMError.shouldNotReachHere("unable to set multimeMethodMap");
        }
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
            newMultiMethod.implementations = implementations;
            newMultiMethod.computedVTableIndex = computedVTableIndex;
            newMultiMethod.indirectCallTarget = indirectCallTarget;
            newMultiMethod.indirectCallVTableIndex = indirectCallVTableIndex;
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
