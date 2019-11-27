/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.util.GuardedAnnotationAccess;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.FrameAccess;
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
        return Arrays.asList(JNIAccessFeature.class, JNIThreadLocalEnvironmentFeature.class);
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
            CEntryPointCallStubSupport.singleton().registerStubForMethod(method, () -> {
                CEntryPointData data = CEntryPointData.create(method);
                if (!SubstrateOptions.JNIExportSymbols.getValue() && data.getPublishAs() != CEntryPointOptions.Publish.NotPublished) {
                    data = data.copyWithPublishAs(CEntryPointOptions.Publish.NotPublished);
                }
                return data;
            });
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
        fillJNIInvocationInterfaceTable(access, JNIFunctionTables.singleton().invokeInterfaceDataPrototype, unimplementedWithJavaVMArgument);

        CFunctionPointer unimplementedWithJNIEnvArgument = getStubFunctionPointer(access, (HostedMethod) getSingleMethod(metaAccess, UnimplementedWithJNIEnvArgument.class));
        fillJNIFunctionsTable(access, JNIFunctionTables.singleton().functionTableData, unimplementedWithJNIEnvArgument);
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

    private void fillJNIInvocationInterfaceTable(CompilationAccessImpl access, CFunctionPointer[] table, CFunctionPointer defaultValue) {
        initializeFunctionPointerTable(access, table, defaultValue);

        HostedType invokes = access.getMetaAccess().lookupJavaType(JNIInvocationInterface.class);
        HostedMethod[] methods = invokes.getDeclaredMethods();
        for (HostedMethod method : methods) {
            StructFieldInfo field = findFieldFor(invokeInterfaceMetadata, method.getName());
            int offset = field.getOffsetInfo().getProperty();
            setFunctionPointerTable(table, offset, getStubFunctionPointer(access, method));
        }
    }

    private void fillJNIFunctionsTable(CompilationAccessImpl access, CFunctionPointer[] table, CFunctionPointer defaultValue) {
        initializeFunctionPointerTable(access, table, defaultValue);

        Class<JNIFunctions> clazz = JNIFunctions.class;
        HostedType functions = access.getMetaAccess().lookupJavaType(clazz);
        HostedMethod[] methods = functions.getDeclaredMethods();
        for (HostedMethod method : methods) {
            StructFieldInfo field = findFieldFor(functionTableMetadata, method.getName());
            int offset = field.getOffsetInfo().getProperty();
            setFunctionPointerTable(table, offset, getStubFunctionPointer(access, method));
        }
        for (ResolvedJavaMethod accessor : generatedMethods) {
            StructFieldInfo field = findFieldFor(functionTableMetadata, accessor.getName());

            AnalysisUniverse analysisUniverse = access.getUniverse().getBigBang().getUniverse();
            AnalysisMethod analysisMethod = analysisUniverse.lookup(accessor);
            HostedMethod hostedMethod = access.getUniverse().lookup(analysisMethod);

            int offset = field.getOffsetInfo().getProperty();
            setFunctionPointerTable(table, offset, MethodPointer.factory(hostedMethod));
        }
        for (CallVariant variant : CallVariant.values()) {
            CFunctionPointer trampoline = prepareCallTrampoline(access, variant, false);
            String suffix = (variant == CallVariant.ARRAY) ? "A" : ((variant == CallVariant.VA_LIST) ? "V" : "");
            CFunctionPointer nonvirtualTrampoline = prepareCallTrampoline(access, variant, true);
            for (JavaKind kind : jniKinds) {
                String[] prefixes = {"Call", "CallStatic"};
                for (String prefix : prefixes) {
                    StructFieldInfo field = findFieldFor(functionTableMetadata, prefix + kind.name() + "Method" + suffix);
                    int offset = field.getOffsetInfo().getProperty();
                    setFunctionPointerTable(table, offset, trampoline);
                }
                StructFieldInfo field = findFieldFor(functionTableMetadata, "CallNonvirtual" + kind.name() + "Method" + suffix);
                int offset = field.getOffsetInfo().getProperty();
                setFunctionPointerTable(table, offset, nonvirtualTrampoline);
            }
            StructFieldInfo field = findFieldFor(functionTableMetadata, "NewObject" + suffix);
            int offset = field.getOffsetInfo().getProperty();
            setFunctionPointerTable(table, offset, trampoline);
        }
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

    private static void initializeFunctionPointerTable(CompilationAccessImpl access, CFunctionPointer[] table, CFunctionPointer defaultValue) {
        for (int i = 0; i < table.length; i++) {
            table[i] = defaultValue;
        }
        /*
         * All objects in the image heap that have function pointers, i.e., relocations, must be
         * immutable at run time.
         */
        access.registerAsImmutable(table);
    }

    private static void setFunctionPointerTable(CFunctionPointer[] table, int offsetInBytes, CFunctionPointer value) {
        int wordSize = FrameAccess.wordSize();
        VMError.guarantee(offsetInBytes % wordSize == 0);
        table[offsetInBytes / wordSize] = value;
    }
}
