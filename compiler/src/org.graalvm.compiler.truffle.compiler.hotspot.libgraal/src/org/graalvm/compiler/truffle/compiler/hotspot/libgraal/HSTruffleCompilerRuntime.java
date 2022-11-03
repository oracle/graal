/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.hotspot.libgraal;

import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.AsCompilableTruffleAST;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.ConsumeOptimizedAssumptionDependency;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.CreateStringSupplier;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetCallTargetForCallNode;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetConstantFieldInfo;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetFrameSlotKindTagForJavaKind;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetFrameSlotKindTagsCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetJavaKindForFrameSlotKind;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsSpecializationMethod;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsSuppressedFailure;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsValueType;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.Log;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.OnCodeInstallation;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.ReadMethodCache;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.RegisterOptimizedAssumptionDependency;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callAsCompilableTruffleAST;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callConsumeOptimizedAssumptionDependency;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callCreateStringSupplier;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callGetCallTargetForCallNode;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callGetConstantFieldInfo;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callGetFrameSlotKindTagForJavaKind;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callGetFrameSlotKindTagsCount;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callGetJavaKindForFrameSlotKind;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callIsSpecializationMethod;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callIsSuppressedFailure;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callIsValueType;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callLog;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callOnCodeInstallation;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callReadMethodCache;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callRegisterOptimizedAssumptionDependency;
import static org.graalvm.jniutils.JNIMethodScope.env;
import static org.graalvm.jniutils.JNIMethodScope.scope;
import static org.graalvm.jniutils.JNIUtil.getInternalName;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.graalvm.compiler.core.common.util.MethodKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.common.TruffleCompilerAssumptionDependency;
import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal;
import org.graalvm.jniutils.HSObject;
import org.graalvm.jniutils.JNI.JByteArray;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNI.JObject;
import org.graalvm.jniutils.JNI.JString;
import org.graalvm.jniutils.JNIMethodScope;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.libgraal.LibGraal;
import org.graalvm.libgraal.jni.annotation.FromLibGraalEntryPointsResolver;
import org.graalvm.nativebridge.BinaryInput;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.WordFactory;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * Proxy for a {@link HotSpotTruffleCompilerRuntime} object in the HotSpot heap.
 */
@FromLibGraalEntryPointsResolver(value = TruffleFromLibGraal.Id.class, entryPointsClassName = "org.graalvm.compiler.truffle.runtime.hotspot.libgraal.TruffleFromLibGraalEntryPoints")
final class HSTruffleCompilerRuntime extends HSObject implements HotSpotTruffleCompilerRuntime {

    private static final int MAX_METHOD_CACHE_SIZE = 1_000;

    private final ResolvedJavaType classLoaderDelegate;
    private final OptionValues initialOptions;

    @SuppressWarnings("serial") private final Map<MethodKey, MethodCache> methodCache = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<MethodKey, MethodCache> eldest) {
            return size() > MAX_METHOD_CACHE_SIZE;
        }
    });

    HSTruffleCompilerRuntime(JNIEnv env, JObject handle, ResolvedJavaType classLoaderDelegate, OptionValues options) {
        super(env, handle);
        this.classLoaderDelegate = classLoaderDelegate;
        this.initialOptions = options;
    }

    private MethodCache getMethodCache(ResolvedJavaMethod method) {
        MethodKey key = new MethodKey(method);
        // It intentionally does not use Map#computeIfAbsent.
        // Collections.SynchronizedMap#computeIfAbsent implementation blocks readers during the
        // creation of the MethodCache.
        MethodCache cache = methodCache.get(key);
        if (cache == null) {
            cache = createMethodCache(method);
            methodCache.putIfAbsent(key, cache);
        }
        return cache;
    }

    @TruffleFromLibGraal(ReadMethodCache)
    private MethodCache createMethodCache(ResolvedJavaMethod method) {
        long methodHandle = LibGraal.translate(method);
        JByteArray hsByteArray = callReadMethodCache(env(), getHandle(), methodHandle);
        CCharPointer buffer = StackValue.get(19);
        JNIUtil.GetByteArrayRegion(env(), hsByteArray, 0, 19, buffer);
        BinaryInput in = BinaryInput.create(buffer, 19);
        LoopExplosionKind loopExplosionKind = LoopExplosionKind.values()[in.readInt()];
        InlineKind peInlineKind = InlineKind.values()[in.readInt()];
        InlineKind inlineKind = InlineKind.values()[in.readInt()];
        boolean inlineable = in.readBoolean();
        boolean truffleBoundary = in.readBoolean();
        boolean bytecodeInterpreterSwitch = in.readBoolean();
        boolean bytecodeInterpreterSwitchBoundary = in.readBoolean();
        boolean inInterpreter = in.readBoolean();
        boolean transferToInterpreterMethod = in.readBoolean();
        boolean callIsInliningCutoff = in.readBoolean();
        return new MethodCache(loopExplosionKind, peInlineKind, inlineKind, inlineable,
                        truffleBoundary, bytecodeInterpreterSwitch, bytecodeInterpreterSwitchBoundary,
                        inInterpreter, transferToInterpreterMethod, callIsInliningCutoff);
    }

    @TruffleFromLibGraal(AsCompilableTruffleAST)
    @Override
    public CompilableTruffleAST asCompilableTruffleAST(JavaConstant constant) {
        JNIMethodScope scope = JNIMethodScope.scopeOrNull();
        if (scope == null) {
            return null;
        }
        long constantHandle = LibGraal.translate(constant);
        JObject hsCompilable = callAsCompilableTruffleAST(scope.getEnv(), getHandle(), constantHandle);
        if (hsCompilable.isNull()) {
            return null;
        } else {
            return new HSCompilableTruffleAST(scope, hsCompilable);
        }
    }

    @TruffleFromLibGraal(OnCodeInstallation)
    @Override
    public void onCodeInstallation(CompilableTruffleAST compilable, InstalledCode installedCode) {
        long installedCodeHandle = LibGraal.translate(installedCode);
        JNIEnv env = env();
        callOnCodeInstallation(env, getHandle(), ((HSCompilableTruffleAST) compilable).getHandle(), installedCodeHandle);
    }

    @TruffleFromLibGraal(RegisterOptimizedAssumptionDependency)
    @Override
    public Consumer<OptimizedAssumptionDependency> registerOptimizedAssumptionDependency(JavaConstant optimizedAssumption) {
        long optimizedAssumptionHandle = LibGraal.translate(optimizedAssumption);
        JNIEnv env = env();
        JObject assumptionConsumer = callRegisterOptimizedAssumptionDependency(env, getHandle(), optimizedAssumptionHandle);
        return assumptionConsumer.isNull() ? null : new HSConsumer(scope(), assumptionConsumer);
    }

    @TruffleFromLibGraal(GetCallTargetForCallNode)
    @Override
    public JavaConstant getCallTargetForCallNode(JavaConstant callNode) {
        long callNodeHandle = LibGraal.translate(callNode);
        JNIEnv env = env();
        long callTargetHandle = callGetCallTargetForCallNode(env, getHandle(), callNodeHandle);
        return LibGraal.unhand(JavaConstant.class, callTargetHandle);
    }

    @Override
    public boolean isInlineable(ResolvedJavaMethod method) {
        MethodCache cache = getMethodCache(method);
        return cache.isInlineable;
    }

    @Override
    public boolean isTruffleBoundary(ResolvedJavaMethod method) {
        MethodCache cache = getMethodCache(method);
        return cache.isTruffleBoundary;
    }

    @TruffleFromLibGraal(IsSpecializationMethod)
    @Override
    public boolean isSpecializationMethod(ResolvedJavaMethod method) {
        return callIsSpecializationMethod(env(), getHandle(), LibGraal.translate(method));
    }

    @Override
    public boolean isBytecodeInterpreterSwitch(ResolvedJavaMethod method) {
        if (JNIMethodScope.scope() != null) {
            MethodCache cache = getMethodCache(method);
            return cache.isBytecodeInterpreterSwitch;
        } else {
            return false;
        }
    }

    @Override
    public boolean isInliningCutoff(ResolvedJavaMethod method) {
        if (JNIMethodScope.scope() != null) {
            MethodCache cache = getMethodCache(method);
            return cache.isInliningCutoff;
        } else {
            return false;
        }
    }

    @Override
    public boolean isBytecodeInterpreterSwitchBoundary(ResolvedJavaMethod method) {
        if (JNIMethodScope.scope() != null) {
            MethodCache cache = getMethodCache(method);
            return cache.isBytecodeInterpreterSwitchBoundary;
        } else {
            return false;
        }
    }

    @Override
    public boolean isInInterpreter(ResolvedJavaMethod method) {
        if (JNIMethodScope.scope() != null) {
            MethodCache cache = getMethodCache(method);
            return cache.isInInterpreter;
        } else {
            return false;
        }
    }

    @Override
    public boolean isTransferToInterpreterMethod(ResolvedJavaMethod method) {
        if (JNIMethodScope.scope() != null) {
            MethodCache cache = getMethodCache(method);
            return cache.isTransferToInterpreterMethod;
        } else {
            return false;
        }
    }

    @TruffleFromLibGraal(IsValueType)
    @Override
    public boolean isValueType(ResolvedJavaType type) {
        return callIsValueType(env(), getHandle(), LibGraal.translate(type));
    }

    @Override
    public InlineKind getInlineKind(ResolvedJavaMethod method, boolean duringPartialEvaluation) {
        MethodCache cache = getMethodCache(method);
        if (duringPartialEvaluation) {
            return cache.inlineKindPE;
        } else {
            return cache.inlineKindNonPE;
        }
    }

    @Override
    public LoopExplosionKind getLoopExplosionKind(ResolvedJavaMethod method) {
        return getMethodCache(method).explosionKind;
    }

    @TruffleFromLibGraal(GetConstantFieldInfo)
    @Override
    public ConstantFieldInfo getConstantFieldInfo(ResolvedJavaField field) {
        ResolvedJavaType enclosingType = field.getDeclaringClass();
        boolean isStatic = field.isStatic();
        ResolvedJavaField[] declaredFields = isStatic ? enclosingType.getStaticFields() : enclosingType.getInstanceFields(false);
        int fieldIndex = -1;
        for (int i = 0; i < declaredFields.length; i++) {
            if (field.equals(declaredFields[i])) {
                fieldIndex = i;
                break;
            }
        }
        if (fieldIndex == -1) {
            throw new IllegalStateException(String.format(
                            "%s field: %s declared in: %s is not in declared fields: %s",
                            isStatic ? "Static" : "Instance",
                            field,
                            enclosingType,
                            Arrays.toString(declaredFields)));
        }
        long typeHandle = LibGraal.translate(enclosingType);
        int fieldInfoDimension = callGetConstantFieldInfo(env(), getHandle(), typeHandle, isStatic, fieldIndex);
        switch (fieldInfoDimension) {
            case Integer.MIN_VALUE:
                return null;
            case -2:
                return ConstantFieldInfo.CHILDREN;
            case -1:
                return ConstantFieldInfo.CHILD;
            default:
                return ConstantFieldInfo.forDimensions(fieldInfoDimension);
        }
    }

    private volatile JavaKind[] frameSlotKindToTag;
    private volatile int[] javaKindToTag;

    @TruffleFromLibGraal(GetJavaKindForFrameSlotKind)
    @Override
    public JavaKind getJavaKindForFrameSlotKind(int frameSlotTag) {
        JavaKind[] values = frameSlotKindToTag;
        if (values == null) {
            JavaKind[] newValues = new JavaKind[callGetFrameSlotKindTagsCount(env(), getHandle())];
            for (int tag = 0; tag < newValues.length; tag++) {
                newValues[tag] = JavaKind.values()[callGetJavaKindForFrameSlotKind(env(), getHandle(), tag)];
            }
            this.frameSlotKindToTag = values = newValues;
        }
        return values[frameSlotTag];
    }

    @TruffleFromLibGraal(GetFrameSlotKindTagForJavaKind)
    @Override
    public int getFrameSlotKindTagForJavaKind(JavaKind kind) {
        int[] values = javaKindToTag;
        if (values == null) {
            int[] newValues = new int[JavaKind.values().length];
            for (int i = 0; i < newValues.length; i++) {
                newValues[i] = callGetFrameSlotKindTagForJavaKind(env(), getHandle(), i);
            }
            this.javaKindToTag = values = newValues;
        }
        return values[kind.ordinal()];
    }

    @TruffleFromLibGraal(GetFrameSlotKindTagsCount)
    @Override
    public int getFrameSlotKindTagsCount() {
        JavaKind[] kinds = frameSlotKindToTag;
        if (kinds == null) {
            return callGetFrameSlotKindTagsCount(env(), getHandle());
        } else {
            return kinds.length;
        }
    }

    @Override
    public ResolvedJavaType resolveType(MetaAccessProvider metaAccess, String className, boolean required) {
        String internalName = getInternalName(className);
        JavaType jt = runtime().lookupType(internalName, (HotSpotResolvedObjectType) classLoaderDelegate, true);
        if (jt instanceof UnresolvedJavaType) {
            if (required) {
                throw new NoClassDefFoundError(internalName);
            } else {
                return null;
            }
        }
        ResolvedJavaType resolvedType = (ResolvedJavaType) jt;
        // In some situations, we may need the class to be linked now, especially if we are
        // compiling immediately (e.g., to successfully devirtualize FrameWithoutBoxing methods).
        resolvedType.link();
        return resolvedType;
    }

    @TruffleFromLibGraal(Log)
    @Override
    public void log(String loggerId, CompilableTruffleAST compilable, String message) {
        JNIEnv env = env();
        JString jniLoggerId = JNIUtil.createHSString(env, loggerId);
        JString jniMessage = JNIUtil.createHSString(env, message);
        callLog(env, getHandle(), jniLoggerId, ((HSCompilableTruffleAST) compilable).getHandle(), jniMessage);
    }

    @Override
    public <T> T getGraalOptions(Class<T> optionValuesType) {
        if (optionValuesType == OptionValues.class) {
            return optionValuesType.cast(initialOptions);
        }
        return HotSpotTruffleCompilerRuntime.super.getGraalOptions(optionValuesType);
    }

    @Override
    public TruffleCompiler getTruffleCompiler(CompilableTruffleAST compilable) {
        throw new UnsupportedOperationException("Should never be called in the compiler.");
    }

    @TruffleFromLibGraal(CreateStringSupplier)
    @TruffleFromLibGraal(IsSuppressedFailure)
    @Override
    public boolean isSuppressedFailure(CompilableTruffleAST compilable, Supplier<String> serializedException) {
        long serializedExceptionHandle = LibGraalObjectHandles.create(serializedException);
        boolean success = false;
        JNIEnv env = env();
        try {
            JObject instance = callCreateStringSupplier(env, serializedExceptionHandle);
            boolean res = callIsSuppressedFailure(env, getHandle(), ((HSCompilableTruffleAST) compilable).getHandle(), instance);
            success = true;
            return res;
        } finally {
            if (!success) {
                LibGraalObjectHandles.remove(serializedExceptionHandle);
            }
        }
    }

    static final class MethodCache {

        final LoopExplosionKind explosionKind;
        final InlineKind inlineKindPE;
        final InlineKind inlineKindNonPE;
        final boolean isInlineable;
        final boolean isTruffleBoundary;
        final boolean isBytecodeInterpreterSwitch;
        final boolean isBytecodeInterpreterSwitchBoundary;
        final boolean isInInterpreter;
        final boolean isTransferToInterpreterMethod;
        final boolean isInliningCutoff;

        MethodCache(LoopExplosionKind explosionKind, InlineKind inlineKindPE, InlineKind inlineKindNonPE, boolean isInlineable, boolean isTruffleBoundary, boolean isBytecodeInterpreterSwitch,
                        boolean isBytecodeInterpreterSwitchBoundary, boolean isInInterpreter, boolean isTransferToInterpreterMethod, boolean isInliningCutoff) {
            this.explosionKind = explosionKind;
            this.inlineKindPE = inlineKindPE;
            this.inlineKindNonPE = inlineKindNonPE;
            this.isInlineable = isInlineable;
            this.isTruffleBoundary = isTruffleBoundary;
            this.isBytecodeInterpreterSwitch = isBytecodeInterpreterSwitch;
            this.isBytecodeInterpreterSwitchBoundary = isBytecodeInterpreterSwitchBoundary;
            this.isInInterpreter = isInInterpreter;
            this.isTransferToInterpreterMethod = isTransferToInterpreterMethod;
            this.isInliningCutoff = isInliningCutoff;
        }

    }

    private static class HSConsumer extends HSObject implements Consumer<OptimizedAssumptionDependency> {

        HSConsumer(JNIMethodScope scope, JObject handle) {
            super(scope, handle);
        }

        @TruffleFromLibGraal(ConsumeOptimizedAssumptionDependency)
        @Override
        public void accept(OptimizedAssumptionDependency optimizedDependency) {
            TruffleCompilerAssumptionDependency dependency = (TruffleCompilerAssumptionDependency) optimizedDependency;
            JObject compilable;
            long installedCode;
            if (dependency == null) {
                compilable = WordFactory.nullPointer();
                installedCode = 0;
            } else {
                compilable = ((HSCompilableTruffleAST) dependency.getCompilable()).getHandle();
                installedCode = LibGraal.translate(dependency.getInstalledCode());
            }
            callConsumeOptimizedAssumptionDependency(env(), getHandle(), compilable, installedCode);
        }
    }
}
