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
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callIsTruffleBoundary;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callIsValueType;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callLog;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callOnCodeInstallation;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callRegisterOptimizedAssumptionDependency;
import static org.graalvm.libgraal.jni.JNIUtil.GetArrayLength;
import static org.graalvm.libgraal.jni.JNIUtil.GetLongArrayElements;
import static org.graalvm.libgraal.jni.JNIUtil.ReleaseLongArrayElements;
import static org.graalvm.libgraal.jni.JNIUtil.getInternalName;
import static org.graalvm.libgraal.jni.JNILibGraalScope.env;
import static org.graalvm.libgraal.jni.JNILibGraalScope.scope;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.UnmodifiableMapCursor;
import org.graalvm.compiler.options.OptionDescriptors;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.options.OptionsParser;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.common.TruffleInliningPlan;
import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleToLibGraal;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal;
import org.graalvm.libgraal.LibGraal;
import org.graalvm.libgraal.jni.HSObject;
import org.graalvm.libgraal.jni.JNILibGraalScope;
import org.graalvm.libgraal.jni.JNI.JArray;
import org.graalvm.libgraal.jni.JNI.JLongArray;
import org.graalvm.libgraal.jni.JNI.JNIEnv;
import org.graalvm.libgraal.jni.JNI.JObject;
import org.graalvm.libgraal.jni.JNI.JString;
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

    private static final Map<Integer, JavaKind> JAVA_KINDS;
    static {
        Map<Integer, JavaKind> m = new HashMap<>();
        for (JavaKind jk : JavaKind.values()) {
            m.put(jk.getBasicType(), jk);
        }
        JAVA_KINDS = Collections.unmodifiableMap(m);
    }

    private final ResolvedJavaType classLoaderDelegate;
    private final OptionValues initialOptions;
    private volatile Map<String, Object> cachedOptionsMap;

    HSTruffleCompilerRuntime(JNIEnv env, JObject handle, ResolvedJavaType classLoaderDelegate, OptionValues options) {
        super(env, handle);
        this.classLoaderDelegate = classLoaderDelegate;
        this.initialOptions = options;
    }

    @TruffleFromLibGraal(CreateInliningPlan)
    @Override
    public TruffleInliningPlan createInliningPlan(CompilableTruffleAST compilable, TruffleCompilationTask task) {
        JNILibGraalScope<?> scope = JNILibGraalScope.scopeOrNull();
        if (scope == null) {
            return null;
        }
        JObject compilableHandle = ((HSCompilableTruffleAST) compilable).getHandle();
        JObject taskHandle = task == null ? WordFactory.nullPointer() : ((HSTruffleCompilationTask) task).getHandle();
        JObject hsInliningPlan = callCreateInliningPlan(scope.getEnv(), getHandle(), compilableHandle, taskHandle);
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
        return callIsTruffleBoundary(env(), getHandle(), LibGraal.translate(method));
    }

    @TruffleFromLibGraal(IsValueType)
    @Override
    public boolean isValueType(ResolvedJavaType type) {
        return callIsValueType(env(), getHandle(), LibGraal.translate(type));
    }

    @TruffleFromLibGraal(GetInlineKind)
    @Override
    public InlineKind getInlineKind(ResolvedJavaMethod original, boolean duringPartialEvaluation) {
        long methodHandle = LibGraal.translate(original);
        int inlineKindOrdinal = callGetInlineKind(env(), getHandle(), methodHandle, duringPartialEvaluation);
        return InlineKind.values()[inlineKindOrdinal];
    }

    @TruffleFromLibGraal(GetLoopExplosionKind)
    @Override
    public LoopExplosionKind getLoopExplosionKind(ResolvedJavaMethod method) {
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

    @TruffleFromLibGraal(GetJavaKindForFrameSlotKind)
    @Override
    public JavaKind getJavaKindForFrameSlotKind(int frameSlotKindTag) {
        int basicType = callGetJavaKindForFrameSlotKind(env(), getHandle(), frameSlotKindTag);
        return JAVA_KINDS.get(basicType);
    }

    @TruffleFromLibGraal(GetFrameSlotKindTagsCount)
    @Override
    public int getFrameSlotKindTagsCount() {
        return callGetFrameSlotKindTagsCount(env(), getHandle());
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

    @TruffleFromLibGraal(GetFrameSlotKindTagForJavaKind)
    @Override
    public int getFrameSlotKindTagForJavaKind(JavaKind kind) {
        return callGetFrameSlotKindTagForJavaKind(env(), getHandle(), kind.getBasicType());
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
    public void log(CompilableTruffleAST compilable, String message) {
        JNIEnv env = env();
        JString jniMessage = JNIUtil.createHSString(env, message);
        callLog(env, getHandle(), ((HSCompilableTruffleAST) compilable).getHandle(), jniMessage);
    }

    @Override
    public Map<String, Object> getOptions() {
        Map<String, Object> res = cachedOptionsMap;
        if (res == null) {
            res = new HashMap<>();
            UnmodifiableMapCursor<OptionKey<?>, Object> optionValues = initialOptions.getMap().getEntries();
            while (optionValues.advance()) {
                final OptionKey<?> key = optionValues.getKey();
                Object value = optionValues.getValue();
                res.put(key.getName(), value);
            }
            cachedOptionsMap = res;
        }
        return res;
    }

    @Override
    public <T> T getOptions(Class<T> optionValuesType) {
        if (optionValuesType == OptionValues.class) {
            return optionValuesType.cast(initialOptions);
        }
        return HotSpotTruffleCompilerRuntime.super.getOptions(optionValuesType);
    }

    @Override
    public <T> T convertOptions(Class<T> optionValuesType, Map<String, Object> map) {
        if (optionValuesType == OptionValues.class) {
            final EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
            final Iterable<OptionDescriptors> loader = OptionsParser.getOptionsLoader();
            for (Map.Entry<String, Object> e : map.entrySet()) {
                final String optionName = e.getKey();
                final Object optionValue = e.getValue();
                OptionsParser.parseOption(optionName, optionValue, values, loader);
            }
            return optionValuesType.cast(new OptionValues(values));
        }
        return HotSpotTruffleCompilerRuntime.super.convertOptions(optionValuesType, map);
    }

    @Override
    public TruffleCompiler getTruffleCompiler(CompilableTruffleAST compilable) {
        throw new UnsupportedOperationException("Should never be called in the compiler.");
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
