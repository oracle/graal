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
package jdk.graal.compiler.libgraal.truffle;

import com.oracle.truffle.compiler.ConstantFieldInfo;
import com.oracle.truffle.compiler.HostMethodInfo;
import com.oracle.truffle.compiler.OptimizedAssumptionDependency;
import com.oracle.truffle.compiler.PartialEvaluationMethodInfo;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilerRuntime;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.libgraal.LibGraalFeature;
import jdk.graal.compiler.truffle.hotspot.HotSpotTruffleCompilationSupport;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;
import org.graalvm.jniutils.HSObject;
import org.graalvm.jniutils.JNIMethodScope;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class HSTruffleCompilerRuntime extends HSIndirectHandle implements TruffleCompilerRuntime {

    static final String COMPILER_VERSION = HotSpotTruffleCompilationSupport.readCompilerVersion();

    // TranslatedException is package-private
    private static final Class<?> TRANSLATED_EXCEPTION = LibGraalFeature.lookupClass("jdk.internal.vm.TranslatedException");

    private final ResolvedJavaType classLoaderDelegate;

    HSTruffleCompilerRuntime(Object hsHandle, long runtimeClass) {
        super(hsHandle);
        this.classLoaderDelegate = HotSpotJVMCIRuntime.runtime().asResolvedJavaType(runtimeClass);
        if (this.classLoaderDelegate == null) {
            throw GraalError.shouldNotReachHere("The object class needs to be available for a Truffle runtime object.");
        }
        TruffleFromLibGraalStartPoints.initializeJNI(Word.pointer(runtimeClass));
    }

    @Override
    public PartialEvaluationMethodInfo getPartialEvaluationMethodInfo(ResolvedJavaMethod method) {
        long methodHandle = HotSpotJVMCIRuntime.runtime().translate(method);
        byte[] array = TruffleFromLibGraalStartPoints.getPartialEvaluationMethodInfo(hsHandle, methodHandle);
        LoopExplosionKind loopExplosionKind = LoopExplosionKind.values()[array[0]];
        InlineKind peInlineKind = InlineKind.values()[array[1]];
        InlineKind inlineKind = InlineKind.values()[array[2]];
        boolean inlineable = array[3] != 0;
        boolean isSpecializationMethod = array[4] != 0;
        return new PartialEvaluationMethodInfo(loopExplosionKind, peInlineKind, inlineKind, inlineable, isSpecializationMethod);
    }

    @Override
    public HostMethodInfo getHostMethodInfo(ResolvedJavaMethod method) {
        throw new UnsupportedOperationException("Use TruffleHostEnvironment#getHostMethodInfo()");
    }

    @Override
    public TruffleCompilable asCompilableTruffleAST(JavaConstant constant) {
        if (constant.isNull()) {
            return null;
        }
        long jniLocalRef = HotSpotJVMCIRuntime.runtime().getJObjectValue((HotSpotObjectConstant) constant);
        JNIMethodScope scope = JNIMethodScope.scopeOrNull();
        if (scope == null) {
            return null;
        }
        return new HSTruffleCompilable(new HSObject(scope, Word.pointer(jniLocalRef)));
    }

    @Override
    public void onCodeInstallation(TruffleCompilable compilable, InstalledCode installedCode) {
        long installedCodeHandle = HotSpotJVMCIRuntime.runtime().translate(installedCode);
        TruffleFromLibGraalStartPoints.onCodeInstallation(hsHandle, ((HSTruffleCompilable) compilable).hsHandle, installedCodeHandle);
    }

    @Override
    public Consumer<OptimizedAssumptionDependency> registerOptimizedAssumptionDependency(JavaConstant optimizedAssumption) {
        long optimizedAssumptionHandle = HotSpotJVMCIRuntime.runtime().translate(optimizedAssumption);
        Object hsDependencyHandle;
        hsDependencyHandle = TruffleFromLibGraalStartPoints.registerOptimizedAssumptionDependency(hsHandle, optimizedAssumptionHandle);
        return hsDependencyHandle == null ? null : new HSConsumer(hsDependencyHandle);
    }

    @Override
    public boolean isValueType(ResolvedJavaType type) {
        long typeHandle = HotSpotJVMCIRuntime.runtime().translate(type);
        return TruffleFromLibGraalStartPoints.isValueType(hsHandle, typeHandle);
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
        long typeHandle = HotSpotJVMCIRuntime.runtime().translate(enclosingType);
        int rawValue = TruffleFromLibGraalStartPoints.getConstantFieldInfo(hsHandle, typeHandle, isStatic, fieldIndex);
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
        JavaType jt;
        try {
            jt = HotSpotJVMCIRuntime.runtime().lookupType(internalName, (HotSpotResolvedObjectType) classLoaderDelegate, required);
        } catch (Exception e) {
            if (TRANSLATED_EXCEPTION != null && TRANSLATED_EXCEPTION.isInstance(e)) {
                /*
                 * As of JDK 24 (JDK-8335553), a translated exception is boxed in a
                 * TranslatedException. Unbox a translated unchecked exception as they are the only
                 * ones that can be expected by callers since this method is not declared in checked
                 * exceptions.
                 */
                Throwable cause = e.getCause();
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
            }
            throw e;
        }
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
        TruffleFromLibGraalStartPoints.log(hsHandle, loggerId, ((HSTruffleCompilable) compilable).hsHandle, message);
    }

    @Override
    public boolean isSuppressedFailure(TruffleCompilable compilable, Supplier<String> serializedException) {
        Object supplierHsHandle = TruffleFromLibGraalStartPoints.createStringSupplier(serializedException);
        return TruffleFromLibGraalStartPoints.isSuppressedFailure(hsHandle, ((HSTruffleCompilable) compilable).hsHandle, supplierHsHandle);
    }
}
