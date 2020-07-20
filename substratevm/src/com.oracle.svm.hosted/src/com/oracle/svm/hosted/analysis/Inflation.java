/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.pointsto.reports.AnalysisReportsOptions.PrintAnalysisCallTree;
import static com.oracle.svm.hosted.NativeImageOptions.MaxReachableTypes;
import static jdk.vm.ci.common.JVMCIError.shouldNotReachHere;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.MalformedParameterizedTypeException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.graalvm.compiler.core.common.SuppressSVMWarnings;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.ObjectScanner.ScanReason;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.MethodTypeFlowBuilder;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.reports.CallTreePrinter;
import com.oracle.graal.pointsto.util.AnalysisError.TypeNotFoundError;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.annotate.UnknownPrimitiveField;
import com.oracle.svm.core.graal.meta.SubstrateReplacements;
import com.oracle.svm.core.hub.AnnotatedSuperInfo;
import com.oracle.svm.core.hub.AnnotationsEncoding;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.GenericInfo;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.analysis.flow.SVMMethodTypeFlowBuilder;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.substitute.AnnotationSubstitutionProcessor;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

public class Inflation extends BigBang {
    private Set<AnalysisField> handledUnknownValueFields;
    private Map<GenericInterfacesEncodingKey, Type[]> genericInterfacesMap;
    private Map<AnnotatedInterfacesEncodingKey, AnnotatedType[]> annotatedInterfacesMap;
    private Map<InterfacesEncodingKey, DynamicHub[]> interfacesEncodings;

    private final Pattern illegalCalleesPattern;
    private final Pattern targetCallersPattern;
    private final AnnotationSubstitutionProcessor annotationSubstitutionProcessor;

    public Inflation(OptionValues options, AnalysisUniverse universe, HostedProviders providers, AnnotationSubstitutionProcessor annotationSubstitutionProcessor, ForkJoinPool executor,
                    Runnable heartbeatCallback) {
        super(options, universe, providers, universe.hostVM(), executor, heartbeatCallback, new SubstrateUnsupportedFeatures());
        this.annotationSubstitutionProcessor = annotationSubstitutionProcessor;

        String[] targetCallers = new String[]{"com\\.oracle\\.graal\\.", "org\\.graalvm[^\\.polyglot\\.nativeapi]"};
        targetCallersPattern = buildPrefixMatchPattern(targetCallers);

        String[] illegalCallees = new String[]{"java\\.util\\.stream", "java\\.util\\.Collection\\.stream", "java\\.util\\.Arrays\\.stream"};
        illegalCalleesPattern = buildPrefixMatchPattern(illegalCallees);

        handledUnknownValueFields = new HashSet<>();
        genericInterfacesMap = new HashMap<>();
        annotatedInterfacesMap = new HashMap<>();
        interfacesEncodings = new HashMap<>();
    }

    @Override
    public MethodTypeFlowBuilder createMethodTypeFlowBuilder(BigBang bb, MethodTypeFlow methodFlow) {
        return new SVMMethodTypeFlowBuilder(bb, methodFlow);
    }

    @Override
    public boolean addRoot(JavaConstant constant, Object root) {
        SubstrateObjectConstant sConstant = (SubstrateObjectConstant) constant;
        return sConstant.setRoot(root);
    }

    @Override
    public Object getRoot(JavaConstant constant) {
        SubstrateObjectConstant sConstant = (SubstrateObjectConstant) constant;
        return sConstant.getRoot();
    }

    @Override
    protected void checkObjectGraph(ObjectScanner objectScanner) {
        universe.getFields().forEach(this::handleUnknownValueField);
        universe.getTypes().forEach(this::checkType);

        /* Scan hubs of all types that end up in the native image. */
        universe.getTypes().stream().filter(type -> type.isInstantiated() || type.isInTypeCheck() || type.isPrimitive()).forEach(type -> scanHub(objectScanner, type));
    }

    @Override
    public SVMHost getHostVM() {
        return (SVMHost) hostVM;
    }

    private void checkType(AnalysisType type) {
        DynamicHub hub = getHostVM().dynamicHub(type);
        if (hub.getGenericInfo() == null) {
            fillGenericInfo(type, hub);
        }
        if (hub.getAnnotatedSuperInfo() == null) {
            fillAnnotatedSuperInfo(type, hub);
        }

        if (type.getJavaKind() == JavaKind.Object) {
            if (type.isArray() && (type.isInstantiated() || type.isInTypeCheck())) {
                hub.getComponentHub().setArrayHub(hub);
            }

            try {
                AnalysisType enclosingType = type.getEnclosingType();
                if (enclosingType != null) {
                    hub.setEnclosingClass(getHostVM().dynamicHub(enclosingType));
                }
            } catch (UnsupportedFeatureException ex) {
                getUnsupportedFeatures().addMessage(type.toJavaName(true), null, ex.getMessage(), null, ex);
            }

            if (hub.getInterfacesEncoding() == null) {
                fillInterfaces(type, hub);
            }

            /*
             * Support for Java annotations.
             */
            try {
                /*
                 * Get the annotations from the wrapped type since AnalysisType.getAnnotations()
                 * defends against JDK-7183985 and we want to get the original behavior.
                 */
                Annotation[] annotations = type.getWrappedWithoutResolve().getAnnotations();
                Annotation[] declared = type.getWrappedWithoutResolve().getDeclaredAnnotations();
                hub.setAnnotationsEncoding(encodeAnnotations(metaAccess, annotations, declared, hub.getAnnotationsEncoding()));
            } catch (ArrayStoreException e) {
                /* If we hit JDK-7183985 just encode the exception. */
                hub.setAnnotationsEncoding(e);
            }

            /*
             * Support for Java enumerations.
             */
            if (type.isEnum() && hub.shouldInitEnumConstants()) {
                if (getHostVM().getClassInitializationSupport().shouldInitializeAtRuntime(type)) {
                    hub.initEnumConstantsAtRuntime(type.getJavaClass());
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
                        enumConstants = (Enum<?>[]) type.getJavaClass().getEnumConstants();
                    } else {
                        enumConstants = (Enum[]) SubstrateObjectConstant.asObject(getConstantReflectionProvider().readFieldValue(found, null));
                        assert enumConstants != null;
                    }
                    hub.initEnumConstants(enumConstants);
                }
            }
        }
    }

    @Override
    public void cleanupAfterAnalysis() {
        super.cleanupAfterAnalysis();
        handledUnknownValueFields = null;
        genericInterfacesMap = null;
        annotatedInterfacesMap = null;
        interfacesEncodings = null;
    }

    @Override
    public void checkUserLimitations() {
        int maxReachableTypes = MaxReachableTypes.getValue();
        if (maxReachableTypes >= 0) {
            CallTreePrinter callTreePrinter = new CallTreePrinter(this);
            callTreePrinter.buildCallTree();
            int numberOfTypes = callTreePrinter.classesSet(false).size();
            if (numberOfTypes > maxReachableTypes) {
                throw UserError.abort("Reachable " + numberOfTypes + " types but only " + maxReachableTypes + " allowed (because the " + MaxReachableTypes.getName() +
                                " option is set). To see all reachable types use " + PrintAnalysisCallTree.getName() + "; to change the maximum number of allowed types use " +
                                MaxReachableTypes.getName() +
                                ".");
            }
        }
    }

    public AnnotationSubstitutionProcessor getAnnotationSubstitutionProcessor() {
        return annotationSubstitutionProcessor;
    }

    class GenericInterfacesEncodingKey {
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

    class AnnotatedInterfacesEncodingKey {
        final AnnotatedType[] interfaces;

        AnnotatedInterfacesEncodingKey(AnnotatedType[] aInterfaces) {
            this.interfaces = aInterfaces;
        }

        /*
         * JDK 12 introduced a broken implementation of hashCode() and equals() for the
         * implementation classes of annotated types, leading to an infinite recursion. Tracked as
         * JDK-8224012. As a workaround, we use shallow implementations that only depend on the
         * identity hash code and reference equality. This is the same behavior as on JDK 8 and JDK
         * 11 anyway.
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

    private void fillGenericInfo(AnalysisType type, DynamicHub hub) {
        Class<?> javaClass = type.getJavaClass();

        TypeVariable<?>[] typeParameters = javaClass.getTypeParameters();

        Type[] allGenericInterfaces;
        try {
            allGenericInterfaces = javaClass.getGenericInterfaces();
        } catch (MalformedParameterizedTypeException | TypeNotPresentException | NoClassDefFoundError t) {
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
        } catch (MalformedParameterizedTypeException | TypeNotPresentException | NoClassDefFoundError t) {
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
        } catch (MalformedParameterizedTypeException | TypeNotPresentException | NoClassDefFoundError t) {
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
    }

    private void fillAnnotatedSuperInfo(AnalysisType type, DynamicHub hub) {
        Class<?> javaClass = type.getJavaClass();

        AnnotatedType annotatedSuperclass;
        try {
            annotatedSuperclass = javaClass.getAnnotatedSuperclass();
        } catch (MalformedParameterizedTypeException | TypeNotPresentException | NoClassDefFoundError t) {
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
        } catch (MalformedParameterizedTypeException | TypeNotPresentException | NoClassDefFoundError t) {
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
    }

    private boolean isTypeAllowed(Type t) {
        if (t instanceof Class) {
            Optional<? extends ResolvedJavaType> resolved = metaAccess.optionalLookupJavaType((Class<?>) t);
            return resolved.isPresent() && universe.platformSupported(resolved.get());
        }
        return true;
    }

    class InterfacesEncodingKey {
        final AnalysisType[] aInterfaces;

        InterfacesEncodingKey(AnalysisType[] aInterfaces) {
            this.aInterfaces = aInterfaces;
        }

        DynamicHub[] createHubs() {
            SVMHost svmHost = (SVMHost) hostVM;
            DynamicHub[] hubs = new DynamicHub[aInterfaces.length];
            for (int i = 0; i < hubs.length; i++) {
                hubs[i] = svmHost.dynamicHub(aInterfaces[i]);
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
        SVMHost svmHost = (SVMHost) hostVM;

        AnalysisType[] aInterfaces = type.getInterfaces();
        if (aInterfaces.length == 0) {
            hub.setInterfacesEncoding(null);
        } else if (aInterfaces.length == 1) {
            hub.setInterfacesEncoding(svmHost.dynamicHub(aInterfaces[0]));
        } else {
            /*
             * Many interfaces arrays are the same, e.g., all arrays implement the same two
             * interfaces. We want to avoid duplicate arrays with the same content in the native
             * image heap.
             */
            hub.setInterfacesEncoding(interfacesEncodings.computeIfAbsent(new InterfacesEncodingKey(aInterfaces), InterfacesEncodingKey::createHubs));
        }
    }

    private void scanHub(ObjectScanner objectScanner, AnalysisType type) {
        SVMHost svmHost = (SVMHost) hostVM;
        JavaConstant hubConstant = SubstrateObjectConstant.forObject(svmHost.dynamicHub(type));
        objectScanner.scanConstant(hubConstant, ScanReason.HUB);
    }

    private void handleUnknownValueField(AnalysisField field) {
        if (handledUnknownValueFields.contains(field)) {
            return;
        }

        UnknownObjectField unknownObjectField = field.getAnnotation(UnknownObjectField.class);
        UnknownPrimitiveField unknownPrimitiveField = field.getAnnotation(UnknownPrimitiveField.class);
        if (unknownObjectField != null) {
            assert !Modifier.isFinal(field.getModifiers()) : "@UnknownObjectField annotated field " + field.format("%H.%n") + " cannot be final";
            assert field.getJavaKind() == JavaKind.Object;

            field.setCanBeNull(unknownObjectField.canBeNull());

            List<AnalysisType> aAnnotationTypes = extractAnnotationTypes(field, unknownObjectField);

            /*
             * Only if an UnknownValue field is really accessed, we register all the field's
             * sub-types as allocated.
             */
            if (field.isAccessed()) {
                for (AnalysisType type : aAnnotationTypes) {
                    type.registerAsAllocated(null);
                }
            }

            /*
             * Use the annotation types, instead of the declared type, in the UnknownObjectField
             * annotated fields initialization.
             */
            handleUnknownObjectField(field, aAnnotationTypes.toArray(new AnalysisType[0]));

        } else if (unknownPrimitiveField != null) {
            assert !Modifier.isFinal(field.getModifiers()) : "@UnknownPrimitiveField annotated field " + field.format("%H.%n") + " cannot be final";
            /*
             * Register a primitive field as containing unknown values(s), i.e., is usually written
             * only in hosted code.
             */

            field.registerAsWritten(null);
        }

        handledUnknownValueFields.add(field);
    }

    private List<AnalysisType> extractAnnotationTypes(AnalysisField field, UnknownObjectField unknownObjectField) {
        List<Class<?>> annotationTypes = new ArrayList<>(Arrays.asList(unknownObjectField.types()));
        for (String annotationTypeName : unknownObjectField.fullyQualifiedTypes()) {
            try {
                Class<?> annotationType = Class.forName(annotationTypeName);
                annotationTypes.add(annotationType);
            } catch (ClassNotFoundException e) {
                throw shouldNotReachHere("Annotation type not found " + annotationTypeName);
            }
        }

        List<AnalysisType> aAnnotationTypes = new ArrayList<>();
        AnalysisType declaredType = field.getType();

        for (Class<?> annotationType : annotationTypes) {
            AnalysisType aAnnotationType = metaAccess.lookupJavaType(annotationType);

            assert !WordBase.class.isAssignableFrom(annotationType) : "Annotation type must not be a subtype of WordBase: field: " + field + " | declared type: " + declaredType +
                            " | annotation type: " + annotationType;
            assert declaredType.isAssignableFrom(aAnnotationType) : "Annotation type must be a subtype of the declared type: field: " + field + " | declared type: " + declaredType +
                            " | annotation type: " + annotationType;
            assert aAnnotationType.isArray() || (aAnnotationType.isInstanceClass() && !Modifier.isAbstract(aAnnotationType.getModifiers())) : "Annotation type failure: field: " + field +
                            " | annotation type " + aAnnotationType;

            aAnnotationTypes.add(aAnnotationType);
        }
        return aAnnotationTypes;
    }

    /**
     * Register a field as containing unknown object(s), i.e., is usually written only in hosted
     * code. It can have multiple declared types provided via annotation.
     */
    private void handleUnknownObjectField(AnalysisField aField, AnalysisType... declaredTypes) {
        assert aField.getJavaKind() == JavaKind.Object;

        aField.registerAsWritten(null);

        /* Link the field with all declared types. */
        for (AnalysisType fieldDeclaredType : declaredTypes) {
            TypeFlow<?> fieldDeclaredTypeFlow = fieldDeclaredType.getTypeFlow(this, true);
            if (aField.isStatic()) {
                fieldDeclaredTypeFlow.addUse(this, aField.getStaticFieldFlow());
            } else {
                fieldDeclaredTypeFlow.addUse(this, aField.getInitialInstanceFieldFlow());
                if (fieldDeclaredType.isArray()) {
                    AnalysisType fieldComponentType = fieldDeclaredType.getComponentType();
                    aField.getInitialInstanceFieldFlow().addUse(this, aField.getInstanceFieldFlow());
                    // TODO is there a better way to signal that the elements type flow of a unknown
                    // object field is not empty?
                    if (!fieldComponentType.isPrimitive()) {
                        /*
                         * Write the component type abstract object into the field array elements
                         * type flow, i.e., the array elements type flow of the abstract object of
                         * the field declared type.
                         *
                         * This is required so that the index loads from this array return all the
                         * possible objects that can be stored in the array.
                         */
                        TypeFlow<?> elementsFlow = fieldDeclaredType.getContextInsensitiveAnalysisObject().getArrayElementsFlow(this, true);
                        fieldComponentType.getTypeFlow(this, false).addUse(this, elementsFlow);

                        /*
                         * In the current implementation it is not necessary to do it it recursively
                         * for multidimensional arrays since we don't model individual array
                         * elements, so from the point of view of the static analysis the field's
                         * array elements value is non null (in the case of a n-dimensional array
                         * that value is another array, n-1 dimensional).
                         */
                    }
                }
            }
        }
    }

    private static Set<Annotation> filterUsedAnnotation(Set<Annotation> used, Annotation[] rest) {
        if (rest == null) {
            return null;
        }

        Set<Annotation> restUsed = new HashSet<>();
        for (Annotation a : rest) {
            if (used.contains(a)) {
                restUsed.add(a);
            }
        }
        return restUsed;
    }

    public static Object encodeAnnotations(AnalysisMetaAccess metaAccess, Annotation[] allAnnotations, Annotation[] declaredAnnotations, Object oldEncoding) {
        Object newEncoding;
        if (allAnnotations.length == 0 && declaredAnnotations.length == 0) {
            newEncoding = null;
        } else {
            Set<Annotation> all = new HashSet<>();
            Collections.addAll(all, allAnnotations);
            Collections.addAll(all, declaredAnnotations);
            final Set<Annotation> usedAnnotations = all.stream()
                            .filter(a -> {
                                try {
                                    AnalysisType annotationClass = metaAccess.lookupJavaType(a.getClass());
                                    return isAnnotationUsed(annotationClass);
                                } catch (TypeNotFoundError e) {
                                    /*
                                     * Silently ignore the annotation if its type was not discovered
                                     * by the static analysis.
                                     */
                                    return false;
                                }
                            }).collect(Collectors.toSet());
            Set<Annotation> usedDeclared = filterUsedAnnotation(usedAnnotations, declaredAnnotations);
            newEncoding = usedAnnotations.size() == 0
                            ? null
                            : AnnotationsEncoding.encodeAnnotations(usedAnnotations, usedDeclared);
        }

        /*
         * Return newEncoding only if the value is different from oldEncoding. Without this guard,
         * for tests that do runtime compilation, the field appears as being continuously updated
         * during BigBang.checkObjectGraph.
         */
        if (oldEncoding != null && oldEncoding.equals(newEncoding)) {
            return oldEncoding;
        } else {
            return newEncoding;
        }
    }

    /**
     * We only want annotations in the native image heap that are "used" at run time. In our case,
     * "used" means that the annotation interface is used at least in a type check. This leaves one
     * case where Substrate VM behaves differently than a normal Java VM: When you just query the
     * number of annotations on a class, then we might return a lower number.
     */
    private static boolean isAnnotationUsed(AnalysisType annotationType) {
        if (annotationType.isInstantiated() || annotationType.isInTypeCheck()) {
            return true;
        }
        assert annotationType.getInterfaces().length == 1 : annotationType;

        AnalysisType annotationInterfaceType = annotationType.getInterfaces()[0];
        return annotationInterfaceType.isInstantiated() || annotationInterfaceType.isInTypeCheck();
    }

    public static ResolvedJavaType toWrappedType(ResolvedJavaType type) {
        if (type instanceof AnalysisType) {
            return ((AnalysisType) type).getWrappedWithoutResolve();
        } else if (type instanceof HostedType) {
            return ((HostedType) type).getWrapped().getWrappedWithoutResolve();
        } else {
            return type;
        }
    }

    @Override
    public boolean trackConcreteAnalysisObjects(AnalysisType type) {
        /*
         * For classes marked as UnknownClass no context sensitive analysis is done, i.e., no
         * concrete objects are tracked.
         *
         * It is assumed that an object of type C can be of any type compatible with C. At the same
         * type fields of type C can be of any type compatible with their declared type.
         */

        if (SVMHost.isUnknownClass(type)) {
            return false;
        }

        if (type.isArray() && SVMHost.isUnknownClass(type.getComponentType())) {
            // TODO are arrays of unknown value types also unknown?
            throw JVMCIError.unimplemented();
        }

        return true;
    }

    /**
     * Builds a pattern that checks if the tested string starts with any of the target prefixes,
     * like so: {@code ^(str1(.*)|str2(.*)|str3(.*))}.
     */
    private static Pattern buildPrefixMatchPattern(String[] targetPrefixes) {
        StringBuilder patternStr = new StringBuilder("^(");
        for (int i = 0; i < targetPrefixes.length; i++) {
            String prefix = targetPrefixes[i];
            patternStr.append(prefix);
            patternStr.append("(.*)");
            if (i < targetPrefixes.length - 1) {
                patternStr.append("|");
            }
        }
        patternStr.append(')');
        return Pattern.compile(patternStr.toString());
    }

    @Override
    public boolean isCallAllowed(BigBang bb, AnalysisMethod caller, AnalysisMethod callee, NodeSourcePosition srcPosition) {
        String calleeName = callee.getQualifiedName();
        if (illegalCalleesPattern.matcher(calleeName).find()) {
            String callerName = caller.getQualifiedName();
            if (targetCallersPattern.matcher(callerName).find()) {
                SuppressSVMWarnings suppress = caller.getAnnotation(SuppressSVMWarnings.class);
                AnalysisType callerType = caller.getDeclaringClass();
                while (suppress == null && callerType != null) {
                    suppress = callerType.getAnnotation(SuppressSVMWarnings.class);
                    callerType = callerType.getEnclosingType();
                }
                if (suppress != null) {
                    String[] reasons = suppress.value();
                    for (String r : reasons) {
                        if (r.equals("AllowUseOfStreamAPI")) {
                            return true;
                        }
                    }
                }
                String message = "Illegal: Graal/Truffle use of Stream API: " + calleeName;
                int bci = srcPosition.getBCI();
                String trace = caller.asStackTraceElement(bci).toString();
                bb.getUnsupportedFeatures().addMessage(callerName, caller, message, trace);
                return false;
            }
        }
        return true;
    }

    @Override
    public SubstrateReplacements getReplacements() {
        return (SubstrateReplacements) super.getReplacements();
    }
}
