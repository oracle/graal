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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner.OtherReason;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.classinitialization.ClassInitializationInfo;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ExceptionSynthesizer;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;

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
        if (BuildPhaseProvider.isAnalysisFinished()) {
            throw VMError.shouldNotReachHere("Initializing type metadata after analysis: " + type);
        }

        Class<?> javaClass = type.getJavaClass();
        heapScanner.rescanObject(javaClass.getPackage());

        DynamicHub hub = hostVM.dynamicHub(type);
        /*
         * Start by rescanning the hub itself. This ensures the correct scan reason in case this is
         * the first time we see this hub.
         */
        heapScanner.rescanObject(hub, OtherReason.HUB);
        if (hub.getClassInitializationInfo() == null) {
            buildClassInitializationInfo(heapScanner, type, hub);
        }
        if (hub.getSignature() == null) {
            fillSignature(type, hub);
        }

        if (type.getJavaKind() == JavaKind.Object) {
            if (type.isArray()) {
                hub.getComponentHub().setArrayHub(hub);
                heapScanner.rescanField(hub.getComponentHub(), dynamicHubArrayHubField);
            }

            if (hub.getInterfacesEncoding() == null) {
                fillInterfaces(type, hub);
                heapScanner.rescanField(hub, dynamicHubInterfacesEncodingField);
            }

            /*
             * Support for Java enumerations.
             */
            if (type.isEnum() && hub.shouldInitEnumConstants()) {
                if (hostVM.getClassInitializationSupport().shouldInitializeAtRuntime(type)) {
                    hub.initEnumConstantsAtRuntime(javaClass);
                } else {
                    /*
                     * We want to retrieve the enum constant array that is maintained as a private
                     * static field in the enumeration class. We do not want a copy because that
                     * would mean we have the array twice in the native image: as the static field,
                     * and in the enumConstant field of DynamicHub. The only way to get the original
                     * value is via a reflective field access, and we even have to guess the field
                     * name.
                     */
                    AnalysisField found = null;
                    for (AnalysisField f : type.getStaticFields()) {
                        if (f.getName().endsWith("$VALUES")) {
                            if (found != null) {
                                /*
                                 * Enumeration has more than one static field with enumeration
                                 * values. Bailout and use Class.getEnumConstants() to get the value
                                 * instead.
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
                         * Class.getEnumConstants(). This is not ideal since
                         * Class.getEnumConstants() returns a copy of the array, so we will have two
                         * arrays with the same content in the image heap, but it is better than
                         * failing image generation.
                         */
                        enumConstants = (Enum<?>[]) javaClass.getEnumConstants();
                    } else {
                        enumConstants = (Enum<?>[]) SubstrateObjectConstant.asObject(constantReflection.readFieldValue(found, null));
                        assert enumConstants != null;
                    }
                    hub.initEnumConstants(enumConstants);
                }
                heapScanner.rescanField(hub, dynamicHubAnnotationsEnumConstantsReferenceField);
            }
        }
    }

    private void buildClassInitializationInfo(ImageHeapScanner heapScanner, AnalysisType type, DynamicHub hub) {
        ClassInitializationInfo info;
        if (hostVM.getClassInitializationSupport().shouldInitializeAtRuntime(type)) {
            info = buildRuntimeInitializationInfo(type);
        } else {
            assert type.isInitialized();
            info = type.getClassInitializer() == null ? ClassInitializationInfo.NO_INITIALIZER_INFO_SINGLETON : ClassInitializationInfo.INITIALIZED_INFO_SINGLETON;
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
            bb.addRootMethod(throwVerifyError, true);
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
            bb.addRootMethod(classInitializer, true);
            classInitializerFunction = new MethodPointer(classInitializer);
        }
        return new ClassInitializationInfo(classInitializerFunction);
    }

    private static final Method getSignature = ReflectionUtil.lookupMethod(Class.class, "getGenericSignature0");

    private static void fillSignature(AnalysisType type, DynamicHub hub) {
        Class<?> javaClass = type.getJavaClass();
        String signature;
        try {
            signature = (String) getSignature.invoke(javaClass);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw GraalError.shouldNotReachHere();
        }
        hub.setSignature(signature);
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
