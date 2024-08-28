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
package com.oracle.svm.hosted.jni;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.jni.CallVariant;
import com.oracle.svm.core.jni.functions.JNIFunctionTables;
import com.oracle.svm.core.jni.functions.JNIFunctions;
import com.oracle.svm.core.jni.functions.JNIFunctions.UnimplementedWithJNIEnvArgument;
import com.oracle.svm.core.jni.functions.JNIFunctions.UnimplementedWithJavaVMArgument;
import com.oracle.svm.core.jni.functions.JNIFunctionsJDK19OrLater;
import com.oracle.svm.core.jni.functions.JNIInvocationInterface;
import com.oracle.svm.core.jni.headers.JNIInvokeInterface;
import com.oracle.svm.core.jni.headers.JNINativeInterface;
import com.oracle.svm.core.jni.headers.JNINativeInterfaceJDK19OrLater;
import com.oracle.svm.core.meta.MethodPointer;
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
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Prepares the initialization of the JNI function table structures at image generation time,
 * creating and registering methods that implement JNI functions as necessary.
 */
public class JNIFunctionTablesFeature implements Feature {

    private final EnumSet<JavaKind> jniKinds = EnumSet.of(JavaKind.Object, JavaKind.Boolean, JavaKind.Byte, JavaKind.Char,
                    JavaKind.Short, JavaKind.Int, JavaKind.Long, JavaKind.Float, JavaKind.Double, JavaKind.Void);

    /**
     * Metadata about the table pointed to by the {@code JNIEnv*} C pointer.
     *
     * @see <a href=
     *      "https://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/functions.html#interface_function_table">Documentation
     *      for the Interface Function Table</a>
     */
    private StructInfo functionTableMetadata;
    private StructInfo functionTableMetadataJDK19OrLater;

    /**
     * Metadata about the table pointed to by the {@code JavaVM*} C pointer.
     *
     * @see <a href=
     *      "https://docs.oracle.com/javase/8/docs/technotes/guides/jni/spec/invocation.html#invocation_api_functions">Documentation
     *      for the Invocation API Function Table</a>
     */
    private StructInfo invokeInterfaceMetadata;

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(JNIAccessFeature.class);
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
        if (JavaVersionUtil.JAVA_SPEC > 17) {
            functionTableMetadataJDK19OrLater = (StructInfo) nativeLibraries.findElementInfo(metaAccess.lookupJavaType(JNINativeInterfaceJDK19OrLater.class));
        }

        // Manually add functions as entry points so this is only done when JNI features are enabled
        AnalysisType invokes = metaAccess.lookupJavaType(JNIInvocationInterface.class);
        AnalysisType exports = metaAccess.lookupJavaType(JNIInvocationInterface.Exports.class);
        AnalysisType functions = metaAccess.lookupJavaType(JNIFunctions.class);
        AnalysisType functionsJDK19OrLater = JavaVersionUtil.JAVA_SPEC <= 17 ? null : metaAccess.lookupJavaType(JNIFunctionsJDK19OrLater.class);
        Stream<AnalysisMethod> analysisMethods = Stream.of(invokes, functions, functionsJDK19OrLater, exports).filter(type -> type != null).flatMap(type -> Stream.of(type.getDeclaredMethods(false)));
        Stream<AnalysisMethod> unimplementedMethods = Stream.of((AnalysisMethod) getSingleMethod(metaAccess, UnimplementedWithJNIEnvArgument.class),
                        (AnalysisMethod) getSingleMethod(metaAccess, UnimplementedWithJavaVMArgument.class));
        Stream.concat(analysisMethods, unimplementedMethods).forEach(method -> {
            CEntryPoint annotation = AnnotationAccess.getAnnotation(method, CEntryPoint.class);
            assert annotation != null : "only entry points allowed in class";
            CEntryPointCallStubSupport.singleton().registerStubForMethod(method, () -> {
                CEntryPointData data = CEntryPointData.create(method);
                if (!SubstrateOptions.JNIExportSymbols.getValue() && data.getPublishAs() != CEntryPoint.Publish.NotPublished) {
                    data = data.copyWithPublishAs(CEntryPoint.Publish.NotPublished);
                }
                return data;
            });
        });
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        BeforeCompilationAccessImpl access = (BeforeCompilationAccessImpl) a;
        fillJNIInvocationInterfaceTable(access);
        fillJNIFunctionsTable(access);
    }

    private static CFunctionPointer prepareCallTrampoline(CompilationAccessImpl access, CallVariant variant, boolean nonVirtual) {
        JNICallTrampolineMethod trampolineMethod = JNIAccessFeature.singleton().getCallTrampolineMethod(variant, nonVirtual);
        AnalysisMethod analysisTrampoline = access.getUniverse().getBigBang().getUniverse().lookup(trampolineMethod);
        HostedMethod hostedTrampoline = access.getUniverse().lookup(analysisTrampoline);
        hostedTrampoline.compilationInfo.setCustomParseFunction(trampolineMethod.createCustomParseFunction());
        hostedTrampoline.compilationInfo.setCustomCompileFunction(trampolineMethod.createCustomCompileFunction());
        return new MethodPointer(hostedTrampoline);
    }

    private static ResolvedJavaMethod getSingleMethod(MetaAccessProvider metaAccess, Class<?> holder) {
        ResolvedJavaMethod[] methods = metaAccess.lookupJavaType(holder).getDeclaredMethods(false);
        assert methods.length == 1;
        return methods[0];
    }

    private static CFunctionPointer getStubFunctionPointer(CompilationAccessImpl access, HostedMethod method) {
        AnalysisMethod stub = CEntryPointCallStubSupport.singleton().getStubForMethod(method.getWrapped());
        return new MethodPointer(access.getUniverse().lookup(stub));
    }

    private void fillJNIInvocationInterfaceTable(CompilationAccessImpl access) {
        CFunctionPointer unimplementedWithJavaVMArgument = getStubFunctionPointer(access, (HostedMethod) getSingleMethod(access.getMetaAccess(), UnimplementedWithJavaVMArgument.class));
        JNIFunctionTables.singleton().initInvokeInterfaceTable(unimplementedWithJavaVMArgument, access);

        HostedType invokes = access.getMetaAccess().lookupJavaType(JNIInvocationInterface.class);
        for (HostedMethod method : invokes.getDeclaredMethods(false)) {
            StructFieldInfo field = findFieldFor(invokeInterfaceMetadata, method.getName());
            int offset = field.getOffsetInfo().getProperty();
            JNIFunctionTables.singleton().initInvokeInterfaceEntry(offset, getStubFunctionPointer(access, method));
        }
    }

    private void fillJNIFunctionsTable(CompilationAccessImpl access) {
        JNIFunctionTables tables = JNIFunctionTables.singleton();
        CFunctionPointer unimplementedWithJNIEnvArgument = getStubFunctionPointer(access, (HostedMethod) getSingleMethod(access.getMetaAccess(), UnimplementedWithJNIEnvArgument.class));
        tables.initFunctionTable(unimplementedWithJNIEnvArgument, access);

        HostedType functions = access.getMetaAccess().lookupJavaType(JNIFunctions.class);
        for (HostedMethod method : functions.getDeclaredMethods(false)) {
            StructFieldInfo field = findFieldFor(functionTableMetadata, method.getName());
            int offset = field.getOffsetInfo().getProperty();
            tables.initFunctionEntry(offset, getStubFunctionPointer(access, method));
        }
        if (JavaVersionUtil.JAVA_SPEC > 17) {
            HostedType functionsJDK19OrLater = access.getMetaAccess().lookupJavaType(JNIFunctionsJDK19OrLater.class);
            for (HostedMethod method : functionsJDK19OrLater.getDeclaredMethods(false)) {
                StructFieldInfo field = findFieldFor(functionTableMetadataJDK19OrLater, method.getName());
                int offset = field.getOffsetInfo().getProperty();
                tables.initFunctionEntry(offset, getStubFunctionPointer(access, method));
            }
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
                    tables.initFunctionEntry(offset, trampoline);
                }
                StructFieldInfo field = findFieldFor(functionTableMetadata, "CallNonvirtual" + kind.name() + "Method" + suffix);
                int offset = field.getOffsetInfo().getProperty();
                tables.initFunctionEntry(offset, nonvirtualTrampoline);
            }
            StructFieldInfo field = findFieldFor(functionTableMetadata, "NewObject" + suffix);
            int offset = field.getOffsetInfo().getProperty();
            tables.initFunctionEntry(offset, trampoline);
        }
    }

    /**
     * Finds the field holding a pointer to a given method in a functions table.
     *
     * @param info the functions table to search in
     * @param name name of the method to search for
     * @return information about the field holding a pointer to the method named {@code name} in
     *         {@code info}
     */
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
