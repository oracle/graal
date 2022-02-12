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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.MalformedParameterizedTypeException;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner.OtherReason;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.classinitialization.ClassInitializationInfo;
import com.oracle.svm.core.hub.AnnotatedSuperInfo;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.GenericInfo;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ExceptionSynthesizer;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.code.CompilationInfoSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

public class DynamicHubInitializer {

    private final BigBang bb;
    private final SVMHost hostVM;
    private final AnalysisMetaAccess metaAccess;
    private final UnsupportedFeatures unsupportedFeatures;
    private final ConstantReflectionProvider constantReflection;

    private final Map<GenericInterfacesEncodingKey, Type[]> genericInterfacesMap;
    private final Map<AnnotatedInterfacesEncodingKey, AnnotatedType[]> annotatedInterfacesMap;
    private final Map<InterfacesEncodingKey, DynamicHub[]> interfacesEncodings;

    private final Field dynamicHubClassInitializationInfoField;
    private final Field dynamicHubArrayHubField;
    private final Field dynamicHubEnclosingClassField;
    private final Field dynamicHubInterfacesEncodingField;
    private final Field dynamicHubAnnotationsEncodingField;
    private final Field dynamicHubAnnotationsEnumConstantsReferenceField;
    private final Field dynamicHubAnnotatedSuperInfoField;
    private final Field dynamicHubGenericInfoField;

    public DynamicHubInitializer(BigBang bb) {
        this.bb = bb;
        this.metaAccess = bb.getMetaAccess();
        this.hostVM = (SVMHost) bb.getHostVM();
        this.unsupportedFeatures = bb.getUnsupportedFeatures();
        this.constantReflection = bb.getConstantReflectionProvider();

        this.genericInterfacesMap = new ConcurrentHashMap<>();
        this.annotatedInterfacesMap = new ConcurrentHashMap<>();
        this.interfacesEncodings = new ConcurrentHashMap<>();

        dynamicHubClassInitializationInfoField = ReflectionUtil.lookupField(DynamicHub.class, "classInitializationInfo");
        dynamicHubArrayHubField = ReflectionUtil.lookupField(DynamicHub.class, "arrayHub");
        dynamicHubEnclosingClassField = ReflectionUtil.lookupField(DynamicHub.class, "enclosingClass");
        dynamicHubInterfacesEncodingField = ReflectionUtil.lookupField(DynamicHub.class, "interfacesEncoding");
        dynamicHubAnnotationsEncodingField = ReflectionUtil.lookupField(DynamicHub.class, "annotationsEncoding");
        dynamicHubAnnotationsEnumConstantsReferenceField = ReflectionUtil.lookupField(DynamicHub.class, "enumConstantsReference");
        dynamicHubAnnotatedSuperInfoField = ReflectionUtil.lookupField(DynamicHub.class, "annotatedSuperInfo");
        dynamicHubGenericInfoField = ReflectionUtil.lookupField(DynamicHub.class, "genericInfo");
    }

    public void initializeMetaData(ImageHeapScanner heapScanner, AnalysisType type) {
        assert type.isReachable() : "Type " + type.toJavaName(true) + " is not marked as reachable.";
        if (BuildPhaseProvider.isAnalysisFinished()) {
            throw VMError.shouldNotReachHere("Initializing type metadata after analysis: " + type);
        }

        Class<?> javaClass = type.getJavaClass();
        heapScanner.rescanObject(javaClass.getPackage());

        DynamicHub hub = hostVM.dynamicHub(type);
        if (hub.getClassInitializationInfo() == null) {
            buildClassInitializationInfo(heapScanner, type, hub);
        }
        if (hub.getGenericInfo() == null) {
            fillGenericInfo(heapScanner, type, hub);
        }
        if (hub.getAnnotatedSuperInfo() == null) {
            fillAnnotatedSuperInfo(heapScanner, type, hub);
        }

        if (type.getJavaKind() == JavaKind.Object) {
            if (type.isArray()) {
                hub.getComponentHub().setArrayHub(hub);
                heapScanner.rescanField(hub.getComponentHub(), dynamicHubArrayHubField);
            }

            try {
                AnalysisType enclosingType = type.getEnclosingType();
                if (enclosingType != null) {
                    hub.setEnclosingClass(hostVM.dynamicHub(enclosingType));
                    heapScanner.rescanField(hub, dynamicHubEnclosingClassField);
                }
            } catch (UnsupportedFeatureException ex) {
                unsupportedFeatures.addMessage(type.toJavaName(true), null, ex.getMessage(), null, ex);
            }

            if (hub.getInterfacesEncoding() == null) {
                fillInterfaces(type, hub);
                heapScanner.rescanField(hub, dynamicHubInterfacesEncodingField);
            }

            /*
             * Support for Java annotations.
             * 
             * The annotation encodings must be updated after each analysis iteration since only the
             * annotation types marked as reachable are included.
             */
            try {
                /*
                 * Get the annotations from the wrapped type since AnalysisType.getAnnotations()
                 * defends against JDK-7183985, and we want to get the original behavior.
                 */
                Annotation[] annotations = type.getWrappedWithoutResolve().getAnnotations();
                Annotation[] declared = type.getWrappedWithoutResolve().getDeclaredAnnotations();
                Object annotationsEncoding = AnnotationsProcessor.encodeAnnotations(metaAccess, annotations, declared, hub.getAnnotationsEncoding());
                if (hub.setAnnotationsEncoding(annotationsEncoding)) {
                    heapScanner.rescanField(hub, dynamicHubAnnotationsEncodingField);
                }
            } catch (ArrayStoreException e) {
                /* If we hit JDK-7183985 just encode the exception. */
                hub.setAnnotationsEncoding(e);
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
        heapScanner.rescanObject(hub, OtherReason.HUB);
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
            registerAsCompiled(throwVerifyError);
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
            registerAsCompiled(classInitializer);
            classInitializerFunction = new MethodPointer(classInitializer);
        }
        return new ClassInitializationInfo(classInitializerFunction);
    }

    private void registerAsCompiled(AnalysisMethod aMethod) {
        bb.addRootMethod(aMethod).registerAsImplementationInvoked();
        CompilationInfoSupport.singleton().registerForcedCompilation(aMethod);
    }

    static class GenericInterfacesEncodingKey {
        final Type[] interfaces;

        GenericInterfacesEncodingKey(Type[] aInterfaces) {
            this.interfaces = aInterfaces;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof GenericInterfacesEncodingKey && Arrays.equals(interfaces, ((GenericInterfacesEncodingKey) obj).interfaces);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(interfaces);
        }
    }

    /** Modified copy of {@link Arrays#equals(Object[], Object[])}. */
    private static boolean shallowEquals(Object[] a, Object[] a2) {
        if (a == a2) {
            return true;
        } else if (a == null || a2 == null) {
            return false;
        }
        int length = a.length;
        if (a2.length != length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            /* Modification: use reference equality. */
            if (a[i] != a2[i]) {
                return false;
            }
        }
        return true;
    }

    /** Modified copy of {@link Arrays#hashCode(Object[])}. */
    private static int shallowHashCode(Object[] a) {
        if (a == null) {
            return 0;
        }
        int result = 1;

        for (Object element : a) {
            /* Modification: use identity hash code. */
            result = 31 * result + System.identityHashCode(element);
        }
        return result;
    }

    static class AnnotatedInterfacesEncodingKey {
        final AnnotatedType[] interfaces;

        AnnotatedInterfacesEncodingKey(AnnotatedType[] aInterfaces) {
            this.interfaces = aInterfaces;
        }

        /*
         * After JDK 11, the implementation of hashCode() and equals() for the implementation
         * classes of annotated types can lead to the reification of generic bounds, which can lead
         * to TypeNotPresentException when the class path is incomplete. Therefore, we use shallow
         * implementations that only depend on the identity hash code and reference equality. This
         * is the same behavior as on JDK 8 and JDK 11 anyway.
         */

        @Override
        public boolean equals(Object obj) {
            return obj instanceof AnnotatedInterfacesEncodingKey && shallowEquals(interfaces, ((AnnotatedInterfacesEncodingKey) obj).interfaces);
        }

        @Override
        public int hashCode() {
            return shallowHashCode(interfaces);
        }
    }

    private void fillGenericInfo(ImageHeapScanner heapScanner, AnalysisType type, DynamicHub hub) {
        Class<?> javaClass = type.getJavaClass();

        TypeVariable<?>[] typeParameters = javaClass.getTypeParameters();

        Type[] allGenericInterfaces;
        try {
            allGenericInterfaces = javaClass.getGenericInterfaces();
        } catch (MalformedParameterizedTypeException | TypeNotPresentException | LinkageError t) {
            /*
             * Loading generic interfaces can fail due to missing types. Ignore the exception and
             * return an empty array.
             */
            allGenericInterfaces = new Type[0];
        }

        Type[] genericInterfaces = Arrays.stream(allGenericInterfaces).filter(this::isTypeAllowed).toArray(Type[]::new);
        Type[] cachedGenericInterfaces;
        try {
            cachedGenericInterfaces = genericInterfacesMap.computeIfAbsent(new GenericInterfacesEncodingKey(genericInterfaces), k -> genericInterfaces);
        } catch (MalformedParameterizedTypeException | TypeNotPresentException | LinkageError t) {
            /*
             * Computing the hash code of generic interfaces can fail due to missing types. Ignore
             * the exception and proceed without caching. De-duplication of generic interfaces is an
             * optimization and not necessary for correctness.
             */
            cachedGenericInterfaces = genericInterfaces;
        }

        Type genericSuperClass;
        try {
            genericSuperClass = javaClass.getGenericSuperclass();
        } catch (MalformedParameterizedTypeException | TypeNotPresentException | LinkageError t) {
            /*
             * Loading the generic super class can fail due to missing types. Ignore the exception
             * and return null.
             */
            genericSuperClass = null;
        }
        if (!isTypeAllowed(genericSuperClass)) {
            genericSuperClass = null;
        }
        hub.setGenericInfo(GenericInfo.factory(typeParameters, cachedGenericInterfaces, genericSuperClass));
        heapScanner.rescanField(hub, dynamicHubGenericInfoField);
    }

    private void fillAnnotatedSuperInfo(ImageHeapScanner heapScanner, AnalysisType type, DynamicHub hub) {
        Class<?> javaClass = type.getJavaClass();

        AnnotatedType annotatedSuperclass;
        try {
            annotatedSuperclass = javaClass.getAnnotatedSuperclass();
        } catch (MalformedParameterizedTypeException | TypeNotPresentException | LinkageError t) {
            /*
             * Loading the annotated super class can fail due to missing types. Ignore the exception
             * and return null.
             */
            annotatedSuperclass = null;
        }
        if (annotatedSuperclass != null && !isTypeAllowed(annotatedSuperclass.getType())) {
            annotatedSuperclass = null;
        }

        AnnotatedType[] allAnnotatedInterfaces;
        try {
            allAnnotatedInterfaces = javaClass.getAnnotatedInterfaces();
        } catch (MalformedParameterizedTypeException | TypeNotPresentException | LinkageError t) {
            /*
             * Loading annotated interfaces can fail due to missing types. Ignore the exception and
             * return an empty array.
             */
            allAnnotatedInterfaces = new AnnotatedType[0];
        }

        AnnotatedType[] annotatedInterfaces = Arrays.stream(allAnnotatedInterfaces)
                        .filter(ai -> isTypeAllowed(ai.getType())).toArray(AnnotatedType[]::new);
        AnnotatedType[] cachedAnnotatedInterfaces = annotatedInterfacesMap.computeIfAbsent(
                        new AnnotatedInterfacesEncodingKey(annotatedInterfaces), k -> annotatedInterfaces);
        hub.setAnnotatedSuperInfo(AnnotatedSuperInfo.factory(annotatedSuperclass, cachedAnnotatedInterfaces));
        heapScanner.rescanField(hub, dynamicHubAnnotatedSuperInfoField);
    }

    private boolean isTypeAllowed(Type t) {
        if (t instanceof Class) {
            Optional<? extends ResolvedJavaType> resolved = metaAccess.optionalLookupJavaType((Class<?>) t);
            return resolved.isPresent() && hostVM.platformSupported(resolved.get());
        }
        return true;
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
