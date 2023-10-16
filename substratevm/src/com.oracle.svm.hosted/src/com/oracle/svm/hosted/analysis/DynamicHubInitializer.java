/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.analysis;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner.OtherReason;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.classinitialization.ClassInitializationInfo;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jdk.ClassLoaderSupport;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.BootLoaderSupport;
import com.oracle.svm.hosted.ClassLoaderFeature;
import com.oracle.svm.hosted.ExceptionSynthesizer;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.classinitialization.SimulateClassInitializerSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

public class DynamicHubInitializer {

    private final BigBang bb;
    private final SVMHost hostVM;
    private final AnalysisMetaAccess metaAccess;
    private final ConstantReflectionProvider constantReflection;

    private final Map<InterfacesEncodingKey, DynamicHub[]> interfacesEncodings;

    private final Field dynamicHubClassInitializationInfoField;
    private final Field dynamicHubArrayHubField;
    private final Field dynamicHubInterfacesEncodingField;
    private final Field dynamicHubAnnotationsEnumConstantsReferenceField;

    public DynamicHubInitializer(BigBang bb) {
        this.bb = bb;
        this.metaAccess = bb.getMetaAccess();
        this.hostVM = (SVMHost) bb.getHostVM();
        this.constantReflection = bb.getConstantReflectionProvider();

        this.interfacesEncodings = new ConcurrentHashMap<>();

        dynamicHubClassInitializationInfoField = ReflectionUtil.lookupField(DynamicHub.class, "classInitializationInfo");
        dynamicHubArrayHubField = ReflectionUtil.lookupField(DynamicHub.class, "arrayHub");
        dynamicHubInterfacesEncodingField = ReflectionUtil.lookupField(DynamicHub.class, "interfacesEncoding");
        dynamicHubAnnotationsEnumConstantsReferenceField = ReflectionUtil.lookupField(DynamicHub.class, "enumConstantsReference");
    }

    public void initializeMetaData(ImageHeapScanner heapScanner, AnalysisType type) {
        assert type.isReachable() : "Type " + type.toJavaName(true) + " is not marked as reachable.";

        AnalysisError.guarantee(!BuildPhaseProvider.isAnalysisFinished(), "Initializing type metadata after analysis for %s.", type.toJavaName(true));

        Class<?> javaClass = type.getJavaClass();
        DynamicHub hub = hostVM.dynamicHub(type);

        registerPackage(heapScanner, javaClass, hub);

        /*
         * Start by rescanning the hub itself. This ensures the correct scan reason in case this is
         * the first time we see this hub.
         */
        heapScanner.rescanObject(hub, OtherReason.HUB);

        buildClassInitializationInfo(heapScanner, type, hub);

        if (type.getJavaKind() == JavaKind.Object) {
            if (type.isArray()) {
                AnalysisError.guarantee(hub.getComponentHub().getArrayHub() == null, "Array hub already initialized for %s.", type.getComponentType().toJavaName(true));
                hub.getComponentHub().setArrayHub(hub);
                heapScanner.rescanField(hub.getComponentHub(), dynamicHubArrayHubField);
            }

            fillInterfaces(type, hub);
            heapScanner.rescanField(hub, dynamicHubInterfacesEncodingField);

            /* Support for Java enumerations. */
            if (type.isEnum()) {
                AnalysisError.guarantee(hub.shouldInitEnumConstants(), "Enum constants already initialized for %s.", type.toJavaName(true));
                if (!hostVM.getClassInitializationSupport().maybeInitializeAtBuildTime(type)) {
                    hub.initEnumConstantsAtRuntime(javaClass);
                } else {
                    hub.initEnumConstants(retrieveEnumConstantArray(type, javaClass));
                }
                heapScanner.rescanField(hub, dynamicHubAnnotationsEnumConstantsReferenceField);
            }
        }
    }

    /**
     * For reachable classes, register class's package in appropriate class loader.
     */
    private static void registerPackage(ImageHeapScanner heapScanner, Class<?> javaClass, DynamicHub hub) {
        /*
         * Due to using {@link NativeImageSystemClassLoader}, a class's ClassLoader during runtime
         * may be different from the class used to load it during native-image generation.
         */
        Package packageValue = javaClass.getPackage();
        /* Array types, primitives and void don't have a package. */
        if (packageValue != null) {
            ClassLoader classloader = javaClass.getClassLoader();
            if (classloader == null) {
                classloader = BootLoaderSupport.getBootLoader();
            }
            ClassLoader runtimeClassLoader = ClassLoaderFeature.getRuntimeClassLoader(classloader);
            VMError.guarantee(runtimeClassLoader != null, "Class loader missing for class %s", hub.getName());
            String packageName = hub.getPackageName();
            var loaderPackages = ClassLoaderSupport.registerPackage(runtimeClassLoader, packageName, packageValue);
            heapScanner.rescanObject(loaderPackages);
        }
    }

    private Enum<?>[] retrieveEnumConstantArray(AnalysisType type, Class<?> javaClass) {
        /*
         * We want to retrieve the enum constant array that is maintained as a private static field
         * in the enumeration class. We do not want a copy because that would mean we have the array
         * twice in the native image: as the static field, and in the enumConstant field of
         * DynamicHub. The only way to get the original value is via a reflective field access, and
         * we even have to guess the field name.
         */
        AnalysisField found = null;
        for (ResolvedJavaField javaField : type.getStaticFields()) {
            AnalysisField f = (AnalysisField) javaField;
            if (f.getName().endsWith("$VALUES")) {
                if (found != null) {
                    /*
                     * Enumeration has more than one static field with enumeration values. Bailout
                     * and use Class.getEnumConstants() to get the value instead.
                     */
                    found = null;
                    break;
                }
                found = f;
            }
        }
        Enum<?>[] enumConstants;
        if (found == null) {
            /*
             * We could not find a unique $VALUES field, so we use the value returned by
             * Class.getEnumConstants(). This is not ideal since Class.getEnumConstants() returns a
             * copy of the array, so we will have two arrays with the same content in the image
             * heap, but it is better than failing image generation.
             */
            enumConstants = (Enum<?>[]) javaClass.getEnumConstants();
        } else {
            enumConstants = bb.getSnippetReflectionProvider().asObject(Enum[].class, constantReflection.readFieldValue(found, null));
            assert enumConstants != null;
        }
        return enumConstants;
    }

    private void buildClassInitializationInfo(ImageHeapScanner heapScanner, AnalysisType type, DynamicHub hub) {
        AnalysisError.guarantee(hub.getClassInitializationInfo() == null, "Class initialization info already computed for %s.", type.toJavaName(true));
        boolean initializedAtBuildTime = SimulateClassInitializerSupport.singleton().trySimulateClassInitializer(bb, type);
        ClassInitializationInfo info;
        if (initializedAtBuildTime) {
            info = type.getClassInitializer() == null ? ClassInitializationInfo.NO_INITIALIZER_INFO_SINGLETON : ClassInitializationInfo.INITIALIZED_INFO_SINGLETON;
        } else {
            info = buildRuntimeInitializationInfo(type);
        }
        hub.setClassInitializationInfo(info);
        heapScanner.rescanField(hub, dynamicHubClassInitializationInfoField);
    }

    private ClassInitializationInfo buildRuntimeInitializationInfo(AnalysisType type) {
        assert !type.isInitialized();
        try {
            /*
             * Check if there are any linking errors. This method throws an error even if linking
             * already failed in a previous attempt.
             */
            type.link();

        } catch (VerifyError e) {
            /* Synthesize a VerifyError to be thrown at run time. */
            AnalysisMethod throwVerifyError = metaAccess.lookupJavaMethod(ExceptionSynthesizer.throwExceptionMethod(VerifyError.class));
            bb.addRootMethod(throwVerifyError, true, "Class initialization error, registered in " + DynamicHubInitializer.class);
            return new ClassInitializationInfo(new MethodPointer(throwVerifyError));
        } catch (Throwable t) {
            /*
             * All other linking errors will be reported as NoClassDefFoundError when initialization
             * is attempted at run time.
             */
            return ClassInitializationInfo.FAILED_INFO_SINGLETON;
        }

        /*
         * Now we now that there are no linking errors, we can register the class initialization
         * information.
         */
        assert type.isLinked();
        CFunctionPointer classInitializerFunction = null;
        AnalysisMethod classInitializer = type.getClassInitializer();
        if (classInitializer != null) {
            assert classInitializer.getCode() != null;
            bb.addRootMethod(classInitializer, true, "Class initialization, registered in " + DynamicHubInitializer.class);
            classInitializerFunction = new MethodPointer(classInitializer);
        }
        return new ClassInitializationInfo(classInitializerFunction);
    }

    class InterfacesEncodingKey {
        final AnalysisType[] aInterfaces;

        InterfacesEncodingKey(AnalysisType[] aInterfaces) {
            this.aInterfaces = aInterfaces;
        }

        DynamicHub[] createHubs() {
            DynamicHub[] hubs = new DynamicHub[aInterfaces.length];
            for (int i = 0; i < hubs.length; i++) {
                hubs[i] = hostVM.dynamicHub(aInterfaces[i]);
            }
            return hubs;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof InterfacesEncodingKey && Arrays.equals(aInterfaces, ((InterfacesEncodingKey) obj).aInterfaces);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(aInterfaces);
        }
    }

    /**
     * Fill array returned by Class.getInterfaces().
     */
    private void fillInterfaces(AnalysisType type, DynamicHub hub) {
        AnalysisError.guarantee(hub.getInterfacesEncoding() == null, "Interfaces already computed for %s.", type.toJavaName(true));
        AnalysisType[] aInterfaces = type.getInterfaces();
        if (aInterfaces.length == 0) {
            hub.setInterfacesEncoding(null);
        } else if (aInterfaces.length == 1) {
            hub.setInterfacesEncoding(hostVM.dynamicHub(aInterfaces[0]));
        } else {
            /*
             * Many interfaces arrays are the same, e.g., all arrays implement the same two
             * interfaces. We want to avoid duplicate arrays with the same content in the native
             * image heap.
             */
            hub.setInterfacesEncoding(interfacesEncodings.computeIfAbsent(new InterfacesEncodingKey(aInterfaces), InterfacesEncodingKey::createHubs));
        }
    }

}
