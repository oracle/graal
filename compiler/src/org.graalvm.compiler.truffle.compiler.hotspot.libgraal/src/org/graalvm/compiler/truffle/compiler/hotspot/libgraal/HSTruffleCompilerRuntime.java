/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.CreateInliningPlan;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetCallTargetForCallNode;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetConstantFieldInfo;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetFrameSlotKindTagForJavaKind;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetFrameSlotKindTagsCount;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetInlineKind;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetJavaKindForFrameSlotKind;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetLoopExplosionKind;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetTruffleCallBoundaryMethods;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsBytecodeInterpreterSwitch;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsBytecodeInterpreterSwitchBoundary;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsSpecializationMethod;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsTruffleBoundary;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsValueType;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.Log;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.OnCodeInstallation;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.RegisterOptimizedAssumptionDependency;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callAsCompilableTruffleAST;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callConsumeOptimizedAssumptionDependency;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callCreateInliningPlan;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callGetCallTargetForCallNode;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callGetConstantFieldInfo;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callGetFrameSlotKindTagForJavaKind;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callGetFrameSlotKindTagsCount;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callGetInlineKind;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callGetJavaKindForFrameSlotKind;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callGetLoopExplosionKind;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callGetTruffleCallBoundaryMethods;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callIsBytecodeInterpreterSwitch;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callIsBytecodeInterpreterSwitchBoundary;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callIsSpecializationMethod;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callIsTruffleBoundary;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callIsValueType;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callLog;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callOnCodeInstallation;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callRegisterOptimizedAssumptionDependency;
import static org.graalvm.libgraal.jni.JNILibGraalScope.env;
import static org.graalvm.libgraal.jni.JNILibGraalScope.scope;
import static org.graalvm.libgraal.jni.JNIUtil.GetArrayLength;
import static org.graalvm.libgraal.jni.JNIUtil.GetLongArrayElements;
import static org.graalvm.libgraal.jni.JNIUtil.ReleaseLongArrayElements;
import static org.graalvm.libgraal.jni.JNIUtil.getInternalName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.common.TruffleMetaAccessProvider;
import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal;
import org.graalvm.libgraal.LibGraal;
import org.graalvm.libgraal.jni.HSObject;
import org.graalvm.libgraal.jni.JNI.JArray;
import org.graalvm.libgraal.jni.JNI.JLongArray;
import org.graalvm.libgraal.jni.JNI.JNIEnv;
import org.graalvm.libgraal.jni.JNI.JObject;
import org.graalvm.libgraal.jni.JNI.JString;
import org.graalvm.libgraal.jni.JNILibGraalScope;
import org.graalvm.libgraal.jni.JNIUtil;
import org.graalvm.libgraal.jni.annotation.FromLibGraalEntryPointsResolver;
import org.graalvm.nativeimage.c.type.CLongPointer;
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

    private final ResolvedJavaType classLoaderDelegate;
    private final OptionValues initialOptions;

    private final ConcurrentHashMap<ResolvedJavaMethod, MethodCache> methodCache = new ConcurrentHashMap<>();

    HSTruffleCompilerRuntime(JNIEnv env, JObject handle, ResolvedJavaType classLoaderDelegate, OptionValues options) {
        super(env, handle);
        this.classLoaderDelegate = classLoaderDelegate;
        this.initialOptions = options;
    }

    private MethodCache getMethodCache(ResolvedJavaMethod method) {
        return methodCache.computeIfAbsent(method, (m) -> new MethodCache(getLoopExplosionKindImpl(method), getInlineKindImpl(method, true), getInlineKindImpl(method, false),
                        isTruffleBoundaryImpl(method), isBytecodeInterpreterSwitchImpl(method), isBytecodeInterpreterSwitchBoundaryImpl(method)));
    }

    @TruffleFromLibGraal(CreateInliningPlan)
    @Override
    public TruffleMetaAccessProvider createInliningPlan() {
        JNILibGraalScope<?> scope = JNILibGraalScope.scopeOrNull();
        if (scope == null) {
            return null;
        }
        JObject hsInliningPlan = callCreateInliningPlan(scope.getEnv(), getHandle());
        return new HSTruffleInliningPlan(scope.narrow(TruffleToLibGraal.Id.class), hsInliningPlan);
    }

    @TruffleFromLibGraal(AsCompilableTruffleAST)
    @Override
    public CompilableTruffleAST asCompilableTruffleAST(JavaConstant constant) {
        JNILibGraalScope<?> scope = JNILibGraalScope.scopeOrNull();
        if (scope == null) {
            return null;
        }
        long constantHandle = LibGraal.translate(constant);
        JObject hsCompilable = callAsCompilableTruffleAST(scope.getEnv(), getHandle(), constantHandle);
        if (hsCompilable.isNull()) {
            return null;
        } else {
            return new HSCompilableTruffleAST(scope.narrow(TruffleToLibGraal.Id.class), hsCompilable);
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
        return assumptionConsumer.isNull() ? null : new HSConsumer(scope().narrow(TruffleToLibGraal.Id.class), assumptionConsumer);
    }

    @TruffleFromLibGraal(GetCallTargetForCallNode)
    @Override
    public JavaConstant getCallTargetForCallNode(JavaConstant callNode) {
        long callNodeHandle = LibGraal.translate(callNode);
        JNIEnv env = env();
        long callTargetHandle = callGetCallTargetForCallNode(env, getHandle(), callNodeHandle);
        return LibGraal.unhand(JavaConstant.class, callTargetHandle);
    }

    @TruffleFromLibGraal(IsTruffleBoundary)
    @Override
    public boolean isTruffleBoundary(ResolvedJavaMethod method) {
        MethodCache cache = getMethodCache(method);
        return cache.isTruffleBoundary;
    }

    private boolean isTruffleBoundaryImpl(ResolvedJavaMethod method) {
        return callIsTruffleBoundary(env(), getHandle(), LibGraal.translate(method));
    }

    @TruffleFromLibGraal(IsSpecializationMethod)
    @Override
    public boolean isSpecializationMethod(ResolvedJavaMethod method) {
        return callIsSpecializationMethod(env(), getHandle(), LibGraal.translate(method));
    }

    @TruffleFromLibGraal(IsBytecodeInterpreterSwitch)
    @Override
    public boolean isBytecodeInterpreterSwitch(ResolvedJavaMethod method) {
        if (JNILibGraalScope.scope() != null) {
            MethodCache cache = getMethodCache(method);
            return cache.isBytecodeInterpreterSwitch;
        } else {
            return false;
        }
    }

    private boolean isBytecodeInterpreterSwitchImpl(ResolvedJavaMethod method) {
        return callIsBytecodeInterpreterSwitch(env(), getHandle(), LibGraal.translate(method));
    }

    @TruffleFromLibGraal(IsBytecodeInterpreterSwitchBoundary)
    @Override
    public boolean isBytecodeInterpreterSwitchBoundary(ResolvedJavaMethod method) {
        if (JNILibGraalScope.scope() != null) {
            MethodCache cache = getMethodCache(method);
            return cache.isBytecodeInterpreterSwitchBoundary;
        } else {
            return false;
        }
    }

    private boolean isBytecodeInterpreterSwitchBoundaryImpl(ResolvedJavaMethod method) {
        return callIsBytecodeInterpreterSwitchBoundary(env(), getHandle(), LibGraal.translate(method));
    }

    @TruffleFromLibGraal(IsValueType)
    @Override
    public boolean isValueType(ResolvedJavaType type) {
        return callIsValueType(env(), getHandle(), LibGraal.translate(type));
    }

    @TruffleFromLibGraal(GetInlineKind)
    @Override
    public InlineKind getInlineKind(ResolvedJavaMethod method, boolean duringPartialEvaluation) {
        MethodCache cache = getMethodCache(method);
        if (duringPartialEvaluation) {
            return cache.inlineKindPE;
        } else {
            return cache.inlineKindNonPE;
        }
    }

    private InlineKind getInlineKindImpl(ResolvedJavaMethod original, boolean duringPartialEvaluation) {
        long methodHandle = LibGraal.translate(original);
        int inlineKindOrdinal = callGetInlineKind(env(), getHandle(), methodHandle, duringPartialEvaluation);
        return InlineKind.values()[inlineKindOrdinal];
    }

    @TruffleFromLibGraal(GetLoopExplosionKind)
    @Override
    public LoopExplosionKind getLoopExplosionKind(ResolvedJavaMethod method) {
        return getMethodCache(method).explosionKind;
    }

    private LoopExplosionKind getLoopExplosionKindImpl(ResolvedJavaMethod method) {
        long methodHandle = LibGraal.translate(method);
        int loopExplosionKindOrdinal = callGetLoopExplosionKind(env(), getHandle(), methodHandle);
        return LoopExplosionKind.values()[loopExplosionKindOrdinal];
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

    @TruffleFromLibGraal(GetTruffleCallBoundaryMethods)
    @Override
    public Iterable<ResolvedJavaMethod> getTruffleCallBoundaryMethods() {
        JNIEnv env = env();
        JLongArray handles = callGetTruffleCallBoundaryMethods(env, getHandle());
        int len = GetArrayLength(env, handles);
        List<ResolvedJavaMethod> res = new ArrayList<>();
        CLongPointer longs = GetLongArrayElements(env, handles, WordFactory.nullPointer());
        try {
            for (int i = 0; i < len; i++) {
                res.add(LibGraal.unhand(ResolvedJavaMethod.class, longs.read(i)));
            }
        } finally {
            ReleaseLongArrayElements(env, handles, longs, JArray.MODE_RELEASE);
        }
        return res;
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
        return (ResolvedJavaType) jt;
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

    static final class MethodCache {

        final LoopExplosionKind explosionKind;
        final InlineKind inlineKindPE;
        final InlineKind inlineKindNonPE;
        final boolean isTruffleBoundary;
        final boolean isBytecodeInterpreterSwitch;
        final boolean isBytecodeInterpreterSwitchBoundary;

        MethodCache(LoopExplosionKind explosionKind, InlineKind inlineKindPE, InlineKind inlineKindNonPE, boolean isTruffleBoundary, boolean isBytecodeInterpreterSwitch,
                        boolean isBytecodeInterpreterSwitchBoundary) {
            this.explosionKind = explosionKind;
            this.inlineKindPE = inlineKindPE;
            this.inlineKindNonPE = inlineKindNonPE;
            this.isTruffleBoundary = isTruffleBoundary;
            this.isBytecodeInterpreterSwitch = isBytecodeInterpreterSwitch;
            this.isBytecodeInterpreterSwitchBoundary = isBytecodeInterpreterSwitchBoundary;
        }
    }

    private static class HSConsumer extends HSObject implements Consumer<OptimizedAssumptionDependency> {

        HSConsumer(JNILibGraalScope<TruffleToLibGraal.Id> scope, JObject handle) {
            super(scope, handle);
        }

        @TruffleFromLibGraal(ConsumeOptimizedAssumptionDependency)
        @Override
        public void accept(OptimizedAssumptionDependency dependency) {
            JObject dependencyHandle = dependency == null ? WordFactory.nullPointer() : ((HSCompilableTruffleAST) dependency).getHandle();
            callConsumeOptimizedAssumptionDependency(env(), getHandle(), dependencyHandle);
        }
    }
}
