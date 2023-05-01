/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.GetPartialEvaluationMethodInfo;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsSuppressedFailure;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.IsValueType;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.Log;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.OnCodeInstallation;
import static org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id.RegisterOptimizedAssumptionDependency;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callAsCompilableTruffleAST;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callConsumeOptimizedAssumptionDependency;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callCreateStringSupplier;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callGetCallTargetForCallNode;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callGetConstantFieldInfo;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callIsSuppressedFailure;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callIsValueType;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callLog;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callOnCodeInstallation;
import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.HSTruffleCompilerRuntimeGen.callRegisterOptimizedAssumptionDependency;
import static org.graalvm.jniutils.JNIMethodScope.env;
import static org.graalvm.jniutils.JNIMethodScope.scope;
import static org.graalvm.jniutils.JNIUtil.getInternalName;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.ConstantFieldInfo;
import org.graalvm.compiler.truffle.common.HostMethodInfo;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;
import org.graalvm.compiler.truffle.common.PartialEvaluationMethodInfo;
import org.graalvm.compiler.truffle.common.TruffleCompilerAssumptionDependency;
import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleCompilerRuntime;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id;
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

    HSTruffleCompilerRuntime(JNIEnv env, JObject handle, ResolvedJavaType classLoaderDelegate, OptionValues options) {
        /*
         * Note global duplicates may happen if the compiler is initialized by a host compilation.
         */
        super(env, handle, true, false);
        this.classLoaderDelegate = classLoaderDelegate;
        this.initialOptions = options;
    }

    @TruffleFromLibGraal(GetPartialEvaluationMethodInfo)
    @Override
    public PartialEvaluationMethodInfo getPartialEvaluationMethodInfo(ResolvedJavaMethod method) {
        long methodHandle = LibGraal.translate(method);
        JByteArray hsByteArray = HSTruffleCompilerRuntimeGen.callGetPartialEvaluationMethodInfo(env(), getHandle(), methodHandle);
        CCharPointer buffer = StackValue.get(5);
        JNIUtil.GetByteArrayRegion(env(), hsByteArray, 0, 5, buffer);
        BinaryInput in = BinaryInput.create(buffer, 5);
        LoopExplosionKind loopExplosionKind = LoopExplosionKind.values()[in.readByte()];
        InlineKind peInlineKind = InlineKind.values()[in.readByte()];
        InlineKind inlineKind = InlineKind.values()[in.readByte()];
        boolean inlineable = in.readBoolean();
        boolean isSpecializationMethod = in.readBoolean();
        return new PartialEvaluationMethodInfo(loopExplosionKind, peInlineKind, inlineKind, inlineable, isSpecializationMethod);
    }

    @TruffleFromLibGraal(Id.GetHostMethodInfo)
    @Override
    public HostMethodInfo getHostMethodInfo(ResolvedJavaMethod method) {
        long methodHandle = LibGraal.translate(method);
        JByteArray hsByteArray = HSTruffleCompilerRuntimeGen.callGetHostMethodInfo(env(), getHandle(), methodHandle);
        CCharPointer buffer = StackValue.get(4);
        JNIUtil.GetByteArrayRegion(env(), hsByteArray, 0, 4, buffer);
        BinaryInput in = BinaryInput.create(buffer, 4);
        boolean truffleBoundary = in.readBoolean();
        boolean bytecodeInterpreterSwitch = in.readBoolean();
        boolean bytecodeInterpreterSwitchBoundary = in.readBoolean();
        boolean callIsInliningCutoff = in.readBoolean();
        return new HostMethodInfo(truffleBoundary, bytecodeInterpreterSwitch, bytecodeInterpreterSwitchBoundary, callIsInliningCutoff);
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

    @TruffleFromLibGraal(IsValueType)
    @Override
    public boolean isValueType(ResolvedJavaType type) {
        return callIsValueType(env(), getHandle(), LibGraal.translate(type));
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
        int rawValue = callGetConstantFieldInfo(env(), getHandle(), typeHandle, isStatic, fieldIndex);
        if (rawValue == Integer.MIN_VALUE) {
            return null;
        }
        switch (rawValue) {
            case Integer.MIN_VALUE:
                return null;
            case -1:
                return ConstantFieldInfo.CHILD;
            case -2:
                return ConstantFieldInfo.CHILDREN;
            default:
                return ConstantFieldInfo.forDimensions(rawValue);
        }
    }

    @Override
    public ResolvedJavaType resolveType(MetaAccessProvider metaAccess, String className, boolean required) {
        String internalName = getInternalName(className);
        JavaType jt = runtime().lookupType(internalName, (HotSpotResolvedObjectType) classLoaderDelegate, required);
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
                CompilableTruffleAST ast = dependency.getCompilable();
                if (ast == null) {
                    /*
                     * Compilable may be null if the compilation was triggered by a libgraal host
                     * compilation.
                     */
                    compilable = WordFactory.nullPointer();
                } else {
                    compilable = ((HSCompilableTruffleAST) dependency.getCompilable()).getHandle();
                }
                installedCode = LibGraal.translate(dependency.getInstalledCode());
            }
            callConsumeOptimizedAssumptionDependency(env(), getHandle(), compilable, installedCode);
        }
    }
}
