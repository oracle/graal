/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.guestgraal.truffle;

import com.oracle.truffle.compiler.ConstantFieldInfo;
import com.oracle.truffle.compiler.HostMethodInfo;
import com.oracle.truffle.compiler.OptimizedAssumptionDependency;
import com.oracle.truffle.compiler.PartialEvaluationMethodInfo;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilerRuntime;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

import java.lang.invoke.MethodHandle;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.CreateStringSupplier;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetConstantFieldInfo;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetHostMethodInfo;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.GetPartialEvaluationMethodInfo;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.IsSuppressedFailure;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.IsValueType;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.Log;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.OnCodeInstallation;
import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.RegisterOptimizedAssumptionDependency;
import static jdk.graal.compiler.hotspot.guestgraal.truffle.BuildTime.getOrFail;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;

final class HSTruffleCompilerRuntime extends HSIndirectHandle implements TruffleCompilerRuntime {

    private static volatile Handles handles;

    static void initialize(Map<String, MethodHandle> upCallHandles) {
        handles = new Handles(getOrFail(upCallHandles, GetPartialEvaluationMethodInfo),
                        getOrFail(upCallHandles, GetHostMethodInfo),
                        getOrFail(upCallHandles, OnCodeInstallation),
                        getOrFail(upCallHandles, RegisterOptimizedAssumptionDependency),
                        getOrFail(upCallHandles, IsValueType),
                        getOrFail(upCallHandles, GetConstantFieldInfo),
                        getOrFail(upCallHandles, Log),
                        getOrFail(upCallHandles, CreateStringSupplier),
                        getOrFail(upCallHandles, IsSuppressedFailure));
    }

    private record Handles(MethodHandle getPartialEvaluationMethodInfo, MethodHandle getHostMethodInfo,
                    MethodHandle onCodeInstallation, MethodHandle registerOptimizedAssumptionDependency,
                    MethodHandle isValueType, MethodHandle getConstantFieldInfo, MethodHandle log,
                    MethodHandle createStringSupplier, MethodHandle isSuppressedFailure) {
    }

    private final ResolvedJavaType classLoaderDelegate;

    HSTruffleCompilerRuntime(Object hsHandle, ResolvedJavaType classLoaderDelegate) {
        super(hsHandle);
        this.classLoaderDelegate = classLoaderDelegate;
    }

    @Override
    public PartialEvaluationMethodInfo getPartialEvaluationMethodInfo(ResolvedJavaMethod method) {
        long methodHandle = runtime().translate(method);
        byte[] array;
        try {
            array = (byte[]) handles.getPartialEvaluationMethodInfo.invoke(hsHandle, methodHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
        LoopExplosionKind loopExplosionKind = LoopExplosionKind.values()[array[0]];
        InlineKind peInlineKind = InlineKind.values()[array[1]];
        InlineKind inlineKind = InlineKind.values()[array[2]];
        boolean inlineable = array[3] != 0;
        boolean isSpecializationMethod = array[4] != 0;
        return new PartialEvaluationMethodInfo(loopExplosionKind, peInlineKind, inlineKind, inlineable, isSpecializationMethod);
    }

    @Override
    public HostMethodInfo getHostMethodInfo(ResolvedJavaMethod method) {
        long methodHandle = runtime().translate(method);
        byte[] array;
        try {
            array = (byte[]) handles.getHostMethodInfo.invoke(hsHandle, methodHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
        boolean truffleBoundary = array[0] != 0;
        boolean bytecodeInterpreterSwitch = array[1] != 0;
        boolean bytecodeInterpreterSwitchBoundary = array[2] != 0;
        boolean callIsInliningCutoff = array[3] != 0;
        return new HostMethodInfo(truffleBoundary, bytecodeInterpreterSwitch, bytecodeInterpreterSwitchBoundary, callIsInliningCutoff);
    }

    @Override
    public TruffleCompilable asCompilableTruffleAST(JavaConstant constant) {
        if (constant.isNull()) {
            return null;
        }
        long jniLocalRef = runtime().getJObjectValue((HotSpotObjectConstant) constant);
        Object compilableHsHandle = RunTime.createHandleForLocalReference(jniLocalRef);
        return compilableHsHandle == null ? null : new HSTruffleCompilable(compilableHsHandle);
    }

    @Override
    public void onCodeInstallation(TruffleCompilable compilable, InstalledCode installedCode) {
        long installedCodeHandle = runtime().translate(installedCode);
        try {
            handles.onCodeInstallation.invoke(hsHandle, ((HSTruffleCompilable) compilable).hsHandle, installedCodeHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public Consumer<OptimizedAssumptionDependency> registerOptimizedAssumptionDependency(JavaConstant optimizedAssumption) {
        long optimizedAssumptionHandle = runtime().translate(optimizedAssumption);
        Object hsDependencyHandle;
        try {
            hsDependencyHandle = handles.registerOptimizedAssumptionDependency.invoke(hsHandle, optimizedAssumptionHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
        return hsDependencyHandle == null ? null : new HSConsumer(hsDependencyHandle);
    }

    @Override
    public boolean isValueType(ResolvedJavaType type) {
        long typeHandle = runtime().translate(type);
        try {
            return (boolean) handles.isValueType.invoke(hsHandle, typeHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

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
        long typeHandle = runtime().translate(enclosingType);
        int rawValue;
        try {
            rawValue = (int) handles.getConstantFieldInfo.invoke(hsHandle, typeHandle, isStatic, fieldIndex);
        } catch (Throwable t) {
            throw handleException(t);
        }
        return switch (rawValue) {
            case Integer.MIN_VALUE -> null;
            case -1 -> ConstantFieldInfo.CHILD;
            case -2 -> ConstantFieldInfo.CHILDREN;
            default -> ConstantFieldInfo.forDimensions(rawValue);
        };
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

    private static String getInternalName(String fqn) {
        return "L" + fqn.replace('.', '/') + ";";
    }

    @Override
    public void log(String loggerId, TruffleCompilable compilable, String message) {
        try {
            handles.log.invoke(hsHandle, loggerId, ((HSTruffleCompilable) compilable).hsHandle, message);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }

    @Override
    public boolean isSuppressedFailure(TruffleCompilable compilable, Supplier<String> serializedException) {
        try {
            Object supplierHsHandle = handles.createStringSupplier.invoke(serializedException);
            return (boolean) handles.isSuppressedFailure.invoke(hsHandle, ((HSTruffleCompilable) compilable).hsHandle, supplierHsHandle);
        } catch (Throwable t) {
            throw handleException(t);
        }
    }
}
