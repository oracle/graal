/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jni.functions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import org.graalvm.util.GuardedAnnotationAccess;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeCompilationAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.CompilationAccessImpl;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.c.info.StructFieldInfo;
import com.oracle.svm.hosted.c.info.StructInfo;
import com.oracle.svm.hosted.code.CEntryPointCallStubSupport;
import com.oracle.svm.hosted.code.CEntryPointData;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.MethodPointer;
import com.oracle.svm.jni.JNIGlobalHandlesFeature;
import com.oracle.svm.jni.JNIThreadLocalEnvironmentFeature;
import com.oracle.svm.jni.access.JNIAccessFeature;
import com.oracle.svm.jni.functions.JNIFunctions.UnimplementedWithJNIEnvArgument;
import com.oracle.svm.jni.functions.JNIFunctions.UnimplementedWithJavaVMArgument;
import com.oracle.svm.jni.hosted.JNICallTrampolineMethod;
import com.oracle.svm.jni.hosted.JNIFieldAccessorMethod;
import com.oracle.svm.jni.hosted.JNIJavaCallWrapperMethod.CallVariant;
import com.oracle.svm.jni.hosted.JNIPrimitiveArrayOperationMethod;
import com.oracle.svm.jni.hosted.JNIPrimitiveArrayOperationMethod.Operation;
import com.oracle.svm.jni.nativeapi.JNIInvokeInterface;
import com.oracle.svm.jni.nativeapi.JNINativeInterface;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Prepares the initialization of the JNI function table structures at image generation time,
 * creating and registering methods that implement JNI functions as necessary.
 */
public class JNIFunctionTablesFeature implements Feature {

    private final EnumSet<JavaKind> jniKinds = EnumSet.of(JavaKind.Object, JavaKind.Boolean, JavaKind.Byte, JavaKind.Char,
                    JavaKind.Short, JavaKind.Int, JavaKind.Long, JavaKind.Float, JavaKind.Double, JavaKind.Void);

    private StructInfo functionTableMetadata;

    private StructInfo invokeInterfaceMetadata;

    private ResolvedJavaMethod[] generatedMethods;

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(JNIAccessFeature.class, JNIThreadLocalEnvironmentFeature.class, JNIGlobalHandlesFeature.class);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess arg) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) arg;
        AnalysisMetaAccess metaAccess = access.getMetaAccess();

        JNIFunctionTables.create();

        NativeLibraries nativeLibraries = access.getNativeLibraries();
        AnalysisType invokeInterface = metaAccess.lookupJavaType(JNIInvokeInterface.class);
        invokeInterfaceMetadata = (StructInfo) nativeLibraries.findElementInfo(invokeInterface);
        AnalysisType functionTable = metaAccess.lookupJavaType(JNINativeInterface.class);
        functionTableMetadata = (StructInfo) nativeLibraries.findElementInfo(functionTable);

        // Manually add functions as entry points so this is only done when JNI features are enabled
        AnalysisType invokes = metaAccess.lookupJavaType(JNIInvocationInterface.class);
        AnalysisType exports = metaAccess.lookupJavaType(JNIInvocationInterface.Exports.class);
        AnalysisType functions = metaAccess.lookupJavaType(JNIFunctions.class);
        Stream<AnalysisMethod> analysisMethods = Stream.of(invokes, functions, exports).flatMap(t -> Stream.of(t.getDeclaredMethods()));
        Stream<AnalysisMethod> unimplementedMethods = Stream.of((AnalysisMethod) getSingleMethod(metaAccess, UnimplementedWithJNIEnvArgument.class),
                        (AnalysisMethod) getSingleMethod(metaAccess, UnimplementedWithJavaVMArgument.class));
        Stream.concat(analysisMethods, unimplementedMethods).forEach(method -> {
            CEntryPoint annotation = GuardedAnnotationAccess.getAnnotation(method, CEntryPoint.class);
            assert annotation != null : "only entry points allowed in class";
            CEntryPointCallStubSupport.singleton().registerStubForMethod(method, () -> CEntryPointData.create(method));
        });

        ArrayList<ResolvedJavaMethod> generated = new ArrayList<>();
        MetaAccessProvider wrappedMetaAccess = metaAccess.getWrapped();
        ResolvedJavaType generatedMethodClass = wrappedMetaAccess.lookupJavaType(JNIFunctions.class);
        ConstantPool constantPool = generatedMethodClass.getDeclaredMethods()[0].getConstantPool();
        // Generate JNI field accessors
        EnumSet<JavaKind> fldKinds = jniKinds.clone();
        fldKinds.remove(JavaKind.Void);
        for (JavaKind kind : fldKinds) {
            boolean[] trueFalse = {true, false};
            for (boolean isSetter : trueFalse) {
                for (boolean isStatic : trueFalse) {
                    JNIFieldAccessorMethod method = new JNIFieldAccessorMethod(kind, isSetter, isStatic, generatedMethodClass, constantPool, wrappedMetaAccess);
                    AnalysisMethod analysisMethod = access.getUniverse().lookup(method);
                    access.getBigBang().addRootMethod(analysisMethod).registerAsEntryPoint(method.createEntryPointData());
                    generated.add(method);
                }
            }
        }
        // Generate JNI primitive array operations
        EnumSet<JavaKind> primitiveArrayKinds = jniKinds.clone();
        primitiveArrayKinds.remove(JavaKind.Void);
        primitiveArrayKinds.remove(JavaKind.Object);
        for (JavaKind kind : primitiveArrayKinds) {
            for (Operation op : Operation.values()) {
                JNIPrimitiveArrayOperationMethod method = new JNIPrimitiveArrayOperationMethod(kind, op, generatedMethodClass, constantPool, wrappedMetaAccess);
                AnalysisMethod analysisMethod = access.getUniverse().lookup(method);
                access.getBigBang().addRootMethod(analysisMethod).registerAsEntryPoint(method.createEntryPointData());
                generated.add(method);
            }
        }
        generatedMethods = generated.toArray(new ResolvedJavaMethod[0]);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        BeforeCompilationAccessImpl access = (BeforeCompilationAccessImpl) a;
        HostedMetaAccess metaAccess = access.getMetaAccess();

        CFunctionPointer unimplementedWithJavaVMArgument = getStubFunctionPointer(access, (HostedMethod) getSingleMethod(metaAccess, UnimplementedWithJavaVMArgument.class));
        JNIStructFunctionsInitializer<JNIInvokeInterface> invokesInitializer = buildInvokesInitializer(access, unimplementedWithJavaVMArgument);

        CFunctionPointer unimplementedWithJNIEnvArgument = getStubFunctionPointer(access, (HostedMethod) getSingleMethod(metaAccess, UnimplementedWithJNIEnvArgument.class));
        JNIStructFunctionsInitializer<JNINativeInterface> functionsInitializer = buildFunctionsInitializer(access, unimplementedWithJNIEnvArgument);

        JNIFunctionTables.singleton().initialize(invokesInitializer, functionsInitializer);
    }

    private static CFunctionPointer prepareCallTrampoline(CompilationAccessImpl access, CallVariant variant, boolean nonVirtual) {
        JNICallTrampolineMethod trampolineMethod = JNIAccessFeature.singleton().getCallTrampolineMethod(variant, nonVirtual);
        AnalysisMethod analysisTrampoline = access.getUniverse().getBigBang().getUniverse().lookup(trampolineMethod);
        HostedMethod hostedTrampoline = access.getUniverse().lookup(analysisTrampoline);
        hostedTrampoline.compilationInfo.setCustomParseFunction(trampolineMethod.createCustomParseFunction());
        hostedTrampoline.compilationInfo.setCustomCompileFunction(trampolineMethod.createCustomCompileFunction());
        return MethodPointer.factory(hostedTrampoline);
    }

    private static ResolvedJavaMethod getSingleMethod(MetaAccessProvider metaAccess, Class<?> holder) {
        ResolvedJavaMethod[] methods = metaAccess.lookupJavaType(holder).getDeclaredMethods();
        assert methods.length == 1;
        return methods[0];
    }

    private static CFunctionPointer getStubFunctionPointer(CompilationAccessImpl access, HostedMethod method) {
        AnalysisMethod stub = CEntryPointCallStubSupport.singleton().getStubForMethod(method.getWrapped());
        return MethodPointer.factory(access.getUniverse().lookup(stub));
    }

    private JNIStructFunctionsInitializer<JNIInvokeInterface> buildInvokesInitializer(CompilationAccessImpl access, CFunctionPointer unimplemented) {
        HostedType invokes = access.getMetaAccess().lookupJavaType(JNIInvocationInterface.class);
        HostedMethod[] methods = invokes.getDeclaredMethods();
        int index = 0;
        int[] offsets = new int[methods.length];
        CFunctionPointer[] pointers = new CFunctionPointer[offsets.length];
        for (HostedMethod method : methods) {
            StructFieldInfo field = findFieldFor(invokeInterfaceMetadata, method.getName());
            offsets[index] = field.getOffsetInfo().getProperty();
            pointers[index] = getStubFunctionPointer(access, method);
            index++;
        }
        VMError.guarantee(index == offsets.length && index == pointers.length);
        JNIStructFunctionsInitializer<JNIInvokeInterface> initializer = new JNIStructFunctionsInitializer<>(JNIInvokeInterface.class, offsets, pointers, unimplemented);
        access.registerAsImmutable(pointers);
        access.registerAsImmutable(initializer);
        return initializer;
    }

    private JNIStructFunctionsInitializer<JNINativeInterface> buildFunctionsInitializer(CompilationAccessImpl access, CFunctionPointer unimplemented) {
        Class<JNIFunctions> clazz = JNIFunctions.class;
        HostedType functions = access.getMetaAccess().lookupJavaType(clazz);
        HostedMethod[] methods = functions.getDeclaredMethods();
        int index = 0;
        int count = methods.length + generatedMethods.length;
        // Call, CallStatic, CallNonvirtual: for each return value kind: array, va_list, varargs
        // NewObject: array, va_list, varargs
        count += (jniKinds.size() * 3 + 1) * 3;
        int[] offsets = new int[count];
        CFunctionPointer[] pointers = new CFunctionPointer[offsets.length];
        access.registerAsImmutable(pointers);
        for (HostedMethod method : methods) {
            StructFieldInfo field = findFieldFor(functionTableMetadata, method.getName());
            offsets[index] = field.getOffsetInfo().getProperty();
            pointers[index] = getStubFunctionPointer(access, method);
            index++;
        }
        for (ResolvedJavaMethod accessor : generatedMethods) {
            StructFieldInfo field = findFieldFor(functionTableMetadata, accessor.getName());

            AnalysisUniverse analysisUniverse = access.getUniverse().getBigBang().getUniverse();
            AnalysisMethod analysisMethod = analysisUniverse.lookup(accessor);
            HostedMethod hostedMethod = access.getUniverse().lookup(analysisMethod);

            offsets[index] = field.getOffsetInfo().getProperty();
            pointers[index] = MethodPointer.factory(hostedMethod);
            index++;
        }
        for (CallVariant variant : CallVariant.values()) {
            CFunctionPointer trampoline = prepareCallTrampoline(access, variant, false);
            String suffix = (variant == CallVariant.ARRAY) ? "A" : ((variant == CallVariant.VA_LIST) ? "V" : "");
            CFunctionPointer nonvirtualTrampoline = prepareCallTrampoline(access, variant, true);
            for (JavaKind kind : jniKinds) {
                String[] prefixes = {"Call", "CallStatic"};
                for (String prefix : prefixes) {
                    StructFieldInfo field = findFieldFor(functionTableMetadata, prefix + kind.name() + "Method" + suffix);
                    offsets[index] = field.getOffsetInfo().getProperty();
                    pointers[index] = trampoline;
                    index++;
                }
                StructFieldInfo field = findFieldFor(functionTableMetadata, "CallNonvirtual" + kind.name() + "Method" + suffix);
                offsets[index] = field.getOffsetInfo().getProperty();
                pointers[index] = nonvirtualTrampoline;
                index++;
            }
            StructFieldInfo field = findFieldFor(functionTableMetadata, "NewObject" + suffix);
            offsets[index] = field.getOffsetInfo().getProperty();
            pointers[index] = trampoline;
            index++;
        }
        VMError.guarantee(index == offsets.length && index == pointers.length);
        JNIStructFunctionsInitializer<JNINativeInterface> initializer = new JNIStructFunctionsInitializer<>(JNINativeInterface.class, offsets, pointers, unimplemented);
        access.registerAsImmutable(pointers);
        access.registerAsImmutable(initializer);
        return initializer;
    }

    private static StructFieldInfo findFieldFor(StructInfo info, String name) {
        for (ElementInfo element : info.getChildren()) {
            if (element instanceof StructFieldInfo) {
                StructFieldInfo field = (StructFieldInfo) element;
                if (field.getName().equals(name)) {
                    return field;
                }
            }
        }
        throw VMError.shouldNotReachHere("Cannot find JNI function table field for: " + name);
    }
}
