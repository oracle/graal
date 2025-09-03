/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jvmti;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.jvmti.JvmtiAgents;
import com.oracle.svm.core.jvmti.JvmtiEnvs;
import com.oracle.svm.core.jvmti.JvmtiFunctionTable;
import com.oracle.svm.core.jvmti.JvmtiFunctions;
import com.oracle.svm.core.jvmti.JvmtiSupport;
import com.oracle.svm.core.jvmti.headers.JvmtiInterface;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
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
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Prepares the JVMTI infrastructure and puts it into the image so that JVMTI support is available
 * at run-time. For more information on JVMTI in general, please refer to the
 * <a href="https://docs.oracle.com/en/java/javase/22/docs/specs/jvmti.html">specification</a>.
 */
@AutomaticallyRegisteredFeature
public class JvmtiFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.JVMTI.getValue();
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        UserError.guarantee(SubstrateOptions.JNI.getValue(), "JVMTI requires JNI. Please use option '%s' to enable JNI.", SubstrateOptionsParser.commandArgument(SubstrateOptions.JNI, "+"));

        ImageSingletons.add(JvmtiSupport.class, new JvmtiSupport());
        ImageSingletons.add(JvmtiAgents.class, new JvmtiAgents());
        ImageSingletons.add(JvmtiEnvs.class, new JvmtiEnvs());
        ImageSingletons.add(JvmtiFunctionTable.class, new JvmtiFunctionTable());

        RuntimeSupport.getRuntimeSupport().addInitializationHook(JvmtiSupport.initializationHook());
        RuntimeSupport.getRuntimeSupport().addTearDownHook(JvmtiSupport.teardownHook());
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess arg) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) arg;
        AnalysisMetaAccess metaAccess = access.getMetaAccess();
        registerCEntryPoints(metaAccess);
    }

    private static void registerCEntryPoints(AnalysisMetaAccess metaAccess) {
        /* Manually add the CEntryPoints, so that this is only done when JVMTI is enabled. */
        AnalysisType type = metaAccess.lookupJavaType(JvmtiFunctions.class);
        for (AnalysisMethod method : type.getDeclaredMethods(false)) {
            VMError.guarantee(AnnotationAccess.getAnnotation(method, CEntryPoint.class) != null, "Method %s does not have a @CEntryPoint annotation.", method.format("%H.%n(%p)"));
            CEntryPointCallStubSupport.singleton().registerStubForMethod(method, () -> CEntryPointData.create(method));
        }
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        BeforeCompilationAccessImpl access = (BeforeCompilationAccessImpl) a;
        fillJvmtiFunctionTable(access);

        /* CFunctionPointers are relocatable, so they must be in the read-only image heap. */
        access.registerAsImmutable(JvmtiFunctionTable.singleton().getReadOnlyFunctionTable());
    }

    private static void fillJvmtiFunctionTable(CompilationAccessImpl access) {
        NativeLibraries nativeLibraries = access.getNativeLibraries();
        MetaAccessProvider metaAccess = access.getMetaAccess();

        ResolvedJavaType jvmtiInterface = metaAccess.lookupJavaType(JvmtiInterface.class);
        StructInfo jvmtiInterfaceMetadata = (StructInfo) nativeLibraries.findElementInfo(jvmtiInterface);

        JvmtiFunctionTable functionTable = JvmtiFunctionTable.singleton();
        HostedType type = access.getMetaAccess().lookupJavaType(JvmtiFunctions.class);
        for (HostedMethod method : type.getDeclaredMethods(false)) {
            if (isIncluded(method)) {
                StructFieldInfo field = findFieldFor(jvmtiInterfaceMetadata, method.getName());
                int offset = field.getOffsetInfo().getProperty();
                functionTable.init(offset, getStubFunctionPointer(access, method));
            }
        }
    }

    private static boolean isIncluded(HostedMethod method) {
        CEntryPoint entryPoint = method.getAnnotation(CEntryPoint.class);
        return ReflectionUtil.newInstance(entryPoint.include()).getAsBoolean();
    }

    private static CFunctionPointer getStubFunctionPointer(CompilationAccessImpl access, HostedMethod method) {
        AnalysisMethod stub = CEntryPointCallStubSupport.singleton().getStubForMethod(method.getWrapped());
        return new MethodPointer(access.getUniverse().lookup(stub));
    }

    private static StructFieldInfo findFieldFor(StructInfo info, String name) {
        for (ElementInfo element : info.getChildren()) {
            if (element instanceof StructFieldInfo field) {
                if (field.getName().equals(name)) {
                    return field;
                }
            }
        }
        throw VMError.shouldNotReachHere("Cannot find function table field for: " + name);
    }
}
