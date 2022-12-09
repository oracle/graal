/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.reflect;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.MalformedParameterizedTypeException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;
import org.graalvm.nativeimage.hosted.RuntimeProxyCreation;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisElement;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.ClassLoadingExceptionSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jdk.RecordSupport;
import com.oracle.svm.core.reflect.SubstrateAccessor;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ConditionalConfigurationRegistry;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.annotation.AnnotationMemberValue;
import com.oracle.svm.hosted.annotation.AnnotationSubstitutionType;
import com.oracle.svm.hosted.annotation.AnnotationValue;
import com.oracle.svm.hosted.annotation.SubstrateAnnotationExtractor;
import com.oracle.svm.hosted.annotation.TypeAnnotationValue;
import com.oracle.svm.hosted.substitute.SubstitutionReflectivityFilter;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.reflect.annotation.ExceptionProxy;

public class ReflectionDataBuilder extends ConditionalConfigurationRegistry implements RuntimeReflectionSupport, ReflectionHostedSupport {

    private final Set<Class<?>> modifiedClasses = ConcurrentHashMap.newKeySet();
    private boolean sealed;

    private final Set<Class<?>> reflectionClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, Throwable> inaccessibleClasses = new ConcurrentHashMap<>();
    private final Set<Class<?>> unsafeInstantiatedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<Executable, ExecutableAccessibility> reflectionMethods = new ConcurrentHashMap<>();
    private final Map<Executable, Object> methodAccessors = new ConcurrentHashMap<>();
    private final Set<Field> reflectionFields = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<AnalysisField> hidingFields = ConcurrentHashMap.newKeySet();
    private final Set<AnalysisMethod> hidingMethods = ConcurrentHashMap.newKeySet();
    private final Map<Executable, AnalysisMethod> registeredMethods = new ConcurrentHashMap<>();
    private final Map<Field, AnalysisField> registeredFields = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object[]> registeredRecordComponents = new ConcurrentHashMap<>();
    private final Set<DynamicHub> heapDynamicHubs = ConcurrentHashMap.newKeySet();
    private final Set<AccessibleObject> heapReflectionObjects = ConcurrentHashMap.newKeySet();
    private final Map<Class<?>, Set<Class<?>>> innerClasses = new ConcurrentHashMap<>();

    private final Map<Type, Integer> processedTypes = new HashMap<>();
    private final Set<DynamicHub> processedDynamicHubs = new HashSet<>();
    private final Map<AnalysisField, Set<AnalysisType>> processedHidingFields = new HashMap<>();
    private final Map<AnalysisMethod, Set<AnalysisType>> processedHidingMethods = new HashMap<>();
    private final Map<AccessibleObject, AnalysisElement> processedHeapReflectionObjects = new ConcurrentHashMap<>();

    /* Keep track of annotation interface members to include in proxy classes */
    private final Map<Class<?>, Set<Member>> annotationMembers = new HashMap<>();

    private final Map<AnnotatedElement, AnnotationValue[]> filteredAnnotations = new ConcurrentHashMap<>();
    private final Map<AnalysisMethod, AnnotationValue[][]> filteredParameterAnnotations = new ConcurrentHashMap<>();
    private final Map<AnnotatedElement, TypeAnnotationValue[]> filteredTypeAnnotations = new ConcurrentHashMap<>();

    private final SubstrateAnnotationExtractor annotationExtractor;

    ReflectionDataBuilder(SubstrateAnnotationExtractor annotationExtractor) {
        this.annotationExtractor = annotationExtractor;
    }

    @Override
    public void register(ConfigurationCondition condition, boolean unsafeInstantiated, Class<?> clazz) {
        checkNotSealed();
        registerConditionalConfiguration(condition, () -> {
            if (unsafeInstantiated) {
                unsafeInstantiatedClasses.add(clazz);
            }
            if (reflectionClasses.add(clazz)) {
                modifiedClasses.add(clazz);
            }
        });
    }

    @Override
    public void registerClassLookupException(ConfigurationCondition condition, String typeName, Throwable t) {
        checkNotSealed();
        registerConditionalConfiguration(condition, () -> {
            inaccessibleClasses.put(typeName, t);
        });
    }

    @Override
    public void register(ConfigurationCondition condition, boolean queriedOnly, Executable... methods) {
        checkNotSealed();
        registerConditionalConfiguration(condition, () -> registerMethods(queriedOnly, methods));
    }

    private void registerMethods(boolean queriedOnly, Executable[] methods) {
        for (Executable method : methods) {
            ExecutableAccessibility oldValue;
            ExecutableAccessibility newValue;
            do {
                newValue = queriedOnly ? ExecutableAccessibility.QueriedOnly : ExecutableAccessibility.Accessed;
                oldValue = reflectionMethods.get(method);
                if (oldValue != null) {
                    newValue = ExecutableAccessibility.max(oldValue, newValue);
                }
            } while (oldValue == null ? reflectionMethods.putIfAbsent(method, newValue) != null : !reflectionMethods.replace(method, oldValue, newValue));
            if (oldValue != newValue) {
                modifiedClasses.add(method.getDeclaringClass());
            }
        }
    }

    @Override
    public void register(ConfigurationCondition condition, boolean finalIsWritable, Field... fields) {
        checkNotSealed();
        registerConditionalConfiguration(condition, () -> registerFields(fields));
    }

    private void registerFields(Field[] fields) {
        // Unsafe and write accesses are always enabled for fields because accessors use Unsafe.
        for (Field field : fields) {
            if (reflectionFields.add(field)) {
                modifiedClasses.add(field.getDeclaringClass());
            }
        }
    }

    private void checkNotSealed() {
        if (sealed) {
            throw UserError.abort("Too late to add classes, methods, and fields for reflective access. Registration must happen in a Feature before the analysis has finished.");
        }
    }

    protected void duringAnalysis(DuringAnalysisAccess a) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;
        processAnnotationProxyTypes(access);
        processRegisteredElements(access);
        processMethodMetadata(access);
    }

    private void processAnnotationProxyTypes(DuringAnalysisAccessImpl access) {
        for (AnalysisType type : access.getUniverse().getTypes()) {
            if (type.getWrappedWithoutResolve() instanceof AnnotationSubstitutionType) {
                /*
                 * Proxy classes for annotations present the annotation default methods and fields
                 * as their own.
                 */
                ResolvedJavaType annotationType = ((AnnotationSubstitutionType) type.getWrappedWithoutResolve()).getAnnotationInterfaceType();
                Class<?> annotationClass = access.getUniverse().lookup(annotationType).getJavaClass();
                if (!annotationMembers.containsKey(annotationClass)) {
                    processClass(access, annotationClass);
                }
                for (Member member : annotationMembers.get(annotationClass)) {
                    try {
                        Class<?> annotationProxyClass = type.getJavaClass();
                        if (member instanceof Field) {
                            Field field = (Field) member;
                            register(ConfigurationCondition.alwaysTrue(), false, annotationProxyClass.getDeclaredField(field.getName()));
                        } else if (member instanceof Method) {
                            Method method = (Method) member;
                            register(ConfigurationCondition.alwaysTrue(), false, annotationProxyClass.getDeclaredMethod(method.getName(), method.getParameterTypes()));
                        }
                    } catch (NoSuchFieldException | NoSuchMethodException e) {
                        /*
                         * The annotation member is not present in the proxy class so we don't add
                         * it.
                         */
                    }
                }
            }
        }
    }

    /**
     * See {@link ReflectionMetadataEncoderImpl} for details.
     */
    protected void processMethodMetadata(DuringAnalysisAccessImpl access) {
        for (DynamicHub hub : heapDynamicHubs) {
            if (!processedDynamicHubs.contains(hub)) {
                AnalysisType type = access.getHostVM().lookupType(hub);
                if (!SubstitutionReflectivityFilter.shouldExclude(type.getJavaClass(), access.getMetaAccess(), access.getUniverse())) {
                    registerTypesForClass(access, type, type.getJavaClass());
                    processedDynamicHubs.add(hub);
                }
            }
        }
        for (Field reflectField : reflectionFields) {
            if (!registeredFields.containsKey(reflectField) && !SubstitutionReflectivityFilter.shouldExclude(reflectField, access.getMetaAccess(), access.getUniverse())) {
                AnalysisField analysisField = access.getMetaAccess().lookupJavaField(reflectField);
                access.requireAnalysisIteration();
                registerTypesForField(access, analysisField, reflectField);
                registeredFields.put(reflectField, analysisField);
            }
        }
        for (AnalysisField registeredField : registeredFields.values()) {
            registerHidingSubTypeFields(access, registeredField, registeredField.getDeclaringClass());
        }
        for (Executable method : reflectionMethods.keySet()) {
            if (SubstitutionReflectivityFilter.shouldExclude(method, access.getMetaAccess(), access.getUniverse())) {
                continue;
            }
            if (!registeredMethods.containsKey(method)) {
                AnalysisMethod analysisMethod = access.getMetaAccess().lookupJavaMethod(method);
                access.requireAnalysisIteration();
                registerTypesForMethod(access, analysisMethod, method);
                registeredMethods.put(method, analysisMethod);
            }
            if (reflectionMethods.get(method) == ExecutableAccessibility.Accessed) {
                /*
                 * We must also generate the accessor for a method that was registered as queried
                 * and then registered again as accessed
                 */
                SubstrateAccessor accessor = ImageSingletons.lookup(ReflectionFeature.class).getOrCreateAccessor(method);
                if (methodAccessors.putIfAbsent(method, accessor) == null) {
                    access.rescanObject(accessor);
                }
            }
        }
        for (AnalysisMethod registeredMethod : registeredMethods.values()) {
            registerHidingSubTypeMethods(access, registeredMethod, registeredMethod.getDeclaringClass());
        }
        for (AccessibleObject object : heapReflectionObjects) {
            if (!processedHeapReflectionObjects.containsKey(object)) {
                AnalysisElement analysisElement = null;
                if (object instanceof Field) {
                    Field field = (Field) object;
                    if (!SubstitutionReflectivityFilter.shouldExclude(field, access.getMetaAccess(), access.getUniverse())) {
                        analysisElement = access.getMetaAccess().lookupJavaField(field);
                        access.requireAnalysisIteration();
                        registerTypesForField(access, (AnalysisField) analysisElement, field);
                    }
                } else if (object instanceof Executable) {
                    Executable executable = (Executable) object;
                    if (!SubstitutionReflectivityFilter.shouldExclude(executable, access.getMetaAccess(), access.getUniverse())) {
                        analysisElement = access.getMetaAccess().lookupJavaMethod(executable);
                        access.requireAnalysisIteration();
                        registerTypesForMethod(access, (AnalysisMethod) analysisElement, executable);
                    }
                }
                if (analysisElement != null) {
                    processedHeapReflectionObjects.put(object, analysisElement);
                }
            }
        }
        for (AnalysisElement processedObject : processedHeapReflectionObjects.values()) {
            if (processedObject instanceof AnalysisField) {
                AnalysisField analysisField = (AnalysisField) processedObject;
                registerHidingSubTypeFields(access, analysisField, analysisField.getDeclaringClass());
            } else if (processedObject instanceof AnalysisMethod) {
                AnalysisMethod analysisMethod = (AnalysisMethod) processedObject;
                registerHidingSubTypeMethods(access, analysisMethod, analysisMethod.getDeclaringClass());
            }
        }
        if (SubstrateOptions.IncludeMethodData.getValue()) {
            for (AnalysisField field : access.getUniverse().getFields()) {
                if (field.isAccessed()) {
                    registerTypesForReachableField(access, field);
                }
            }
            for (AnalysisMethod method : access.getUniverse().getMethods()) {
                if (method.isReachable() && !method.isIntrinsicMethod()) {
                    registerTypesForReachableMethod(access, method);
                }
            }
        }
    }

    private void registerHidingSubTypeFields(DuringAnalysisAccess access, AnalysisField field, AnalysisType type) {
        if (!type.equals(field.getDeclaringClass()) && type.isReachable()) {
            if (!processedHidingFields.containsKey(field) || !processedHidingFields.get(field).contains(type)) {
                processedHidingFields.computeIfAbsent(field, m -> ConcurrentHashMap.newKeySet()).add(type);
                try {
                    AnalysisField[] subClassFields = field.isStatic() ? type.getStaticFields() : type.getInstanceFields(false);
                    for (AnalysisField subclassField : subClassFields) {
                        if (subclassField.getName().equals(field.getName())) {
                            hidingFields.add(subclassField);
                        }
                    }
                    /*
                     * Lookup can lead to the creation of new AnalysisField objects, so we need to
                     * run another analysis iteration.
                     */
                    access.requireAnalysisIteration();

                } catch (UnsupportedFeatureException | LinkageError e) {
                    /*
                     * A field that is not supposed to end up in the image is considered as being
                     * absent for reflection purposes.
                     */
                }
            }
        }
        for (AnalysisType subType : type.getSubTypes()) {
            if (!subType.equals(type)) {
                registerHidingSubTypeFields(access, field, subType);
            }
        }
    }

    private void registerHidingSubTypeMethods(DuringAnalysisAccess access, AnalysisMethod method, AnalysisType type) {
        if (!type.equals(method.getDeclaringClass()) && type.isReachable()) {
            if (!processedHidingMethods.containsKey(method) || !processedHidingMethods.get(method).contains(type)) {
                processedHidingMethods.computeIfAbsent(method, m -> ConcurrentHashMap.newKeySet()).add(type);
                try {
                    /*
                     * Using findMethod here which uses getDeclaredMethods internally, instead of
                     * resolveConcreteMethods which gives different results in at least two
                     * scenarios:
                     *
                     * 1) When resolving a static method, resolveConcreteMethods does not return a
                     * subclass method with the same signature, since they are actually fully
                     * distinct methods. However these methods need to be included in the hiding
                     * list because them showing up in a reflection query would be wrong.
                     *
                     * 2) When resolving an interface method from an abstract class,
                     * resolveConcreteMethods returns an undeclared method with the abstract
                     * subclass as declaring class, which is not the reflection API behavior.
                     */
                    AnalysisMethod subClassMethod = type.findMethod(method.getName(), method.getSignature());
                    if (subClassMethod != null) {
                        hidingMethods.add(subClassMethod);
                    }
                    /*
                     * findMethod can lead to the creation of new AnalysisMethod, so we need to run
                     * another analysis iteration.
                     */
                    access.requireAnalysisIteration();

                } catch (UnsupportedFeatureException | LinkageError e) {
                    /*
                     * A method that is not supposed to end up in the image is considered as being
                     * absent for reflection purposes.
                     */
                }
            }
        }
        for (AnalysisType subType : type.getSubTypes()) {
            if (!subType.equals(type)) {
                registerHidingSubTypeMethods(access, method, subType);
            }
        }
    }

    private void registerTypesForClass(DuringAnalysisAccessImpl access, AnalysisType analysisType, Class<?> clazz) {
        /*
         * The generic signature is parsed at run time, so we need to make all the types necessary
         * for parsing also available at run time.
         */
        registerTypesForGenericSignature(access, queryGenericInfo(clazz::getTypeParameters));
        registerTypesForGenericSignature(access, queryGenericInfo(clazz::getGenericSuperclass));
        registerTypesForGenericSignature(access, queryGenericInfo(clazz::getGenericInterfaces));

        registerTypesForEnclosingMethodInfo(access, clazz);

        Object[] recordComponents = buildRecordComponents(clazz, access);
        if (recordComponents != null) {
            for (Object recordComponent : recordComponents) {
                registerTypesForRecordComponent(access, recordComponent);
            }
            registeredRecordComponents.put(clazz, recordComponents);
        }
        registerTypesForAnnotations(access, analysisType);
        registerTypesForTypeAnnotations(access, analysisType);
    }

    private void registerTypesForEnclosingMethodInfo(DuringAnalysisAccessImpl access, Class<?> clazz) {
        Object[] enclosingMethodInfo = getEnclosingMethodInfo(clazz);
        if (enclosingMethodInfo == null) {
            return; /* Nothing to do. */
        }

        /* Ensure the class stored in the enclosing method info is available at run time. */
        makeAnalysisTypeReachable(access, access.getMetaAccess().lookupJavaType((Class<?>) enclosingMethodInfo[0]));

        Executable enclosingMethodOrConstructor;
        try {
            enclosingMethodOrConstructor = Optional.<Executable> ofNullable(clazz.getEnclosingMethod())
                            .orElse(clazz.getEnclosingConstructor());
        } catch (TypeNotPresentException | LinkageError | InternalError e) {
            /*
             * These are rethrown at run time. However, note that `LinkageError` is rethrown as
             * `InternalError` due to GR-40122.
             */
            return;
        }

        if (enclosingMethodOrConstructor != null) {
            /* Make the metadata for the enclosing method or constructor available at run time. */
            RuntimeReflection.registerAsQueried(enclosingMethodOrConstructor);
            access.requireAnalysisIteration();
        }
    }

    private final Method getEnclosingMethod0 = ReflectionUtil.lookupMethod(Class.class, "getEnclosingMethod0");

    private Object[] getEnclosingMethodInfo(Class<?> clazz) {
        try {
            return (Object[]) getEnclosingMethod0.invoke(clazz);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof LinkageError) {
                /*
                 * This error is handled when creating `DynamicHub` (but is then triggered by
                 * `Class.getDeclaringClass0`), so we can simply ignore it here.
                 */
                return null;
            }
            throw VMError.shouldNotReachHere(e);
        } catch (IllegalAccessException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private void registerTypesForField(DuringAnalysisAccessImpl access, AnalysisField analysisField, Field reflectField) {
        /*
         * Reflection accessors use Unsafe, so ensure that all reflectively accessible fields are
         * registered as unsafe-accessible, whether they have been explicitly registered or their
         * Field object is reachable in the image heap.
         */
        access.registerAsUnsafeAccessed(analysisField, "is registered for reflection");

        /*
         * The generic signature is parsed at run time, so we need to make all the types necessary
         * for parsing also available at run time.
         */
        registerTypesForGenericSignature(access, queryGenericInfo(reflectField::getGenericType));

        /*
         * Enable runtime instantiation of annotations
         */
        registerTypesForAnnotations(access, analysisField);
        registerTypesForTypeAnnotations(access, analysisField);
    }

    private void registerTypesForMethod(DuringAnalysisAccessImpl access, AnalysisMethod analysisMethod, Executable reflectMethod) {
        /*
         * The generic signature is parsed at run time, so we need to make all the types necessary
         * for parsing also available at run time.
         */
        registerTypesForGenericSignature(access, queryGenericInfo(reflectMethod::getTypeParameters));
        registerTypesForGenericSignature(access, queryGenericInfo(reflectMethod::getGenericParameterTypes));
        registerTypesForGenericSignature(access, queryGenericInfo(reflectMethod::getGenericExceptionTypes));
        if (!analysisMethod.isConstructor()) {
            registerTypesForGenericSignature(access, queryGenericInfo(((Method) reflectMethod)::getGenericReturnType));
        }

        /*
         * Enable runtime instantiation of annotations
         */
        registerTypesForAnnotations(access, analysisMethod);
        registerTypesForParameterAnnotations(access, analysisMethod);
        registerTypesForTypeAnnotations(access, analysisMethod);
        if (!analysisMethod.isConstructor()) {
            registerTypesForAnnotationDefault(access, analysisMethod);
        }
    }

    private static void registerTypesForReachableField(DuringAnalysisAccessImpl access, AnalysisField analysisField) {
        makeAnalysisTypeReachable(access, analysisField.getDeclaringClass());
    }

    private static void registerTypesForReachableMethod(DuringAnalysisAccessImpl access, AnalysisMethod analysisMethod) {
        makeAnalysisTypeReachable(access, analysisMethod.getDeclaringClass());
        for (JavaType paramType : analysisMethod.toParameterTypes()) {
            makeAnalysisTypeReachable(access, (AnalysisType) paramType);
        }
    }

    private void registerTypesForGenericSignature(DuringAnalysisAccessImpl access, Type[] types) {
        if (types != null) {
            for (Type type : types) {
                registerTypesForGenericSignature(access, type);
            }
        }
    }

    private void registerTypesForGenericSignature(DuringAnalysisAccessImpl access, Type type) {
        registerTypesForGenericSignature(access, type, 0);
    }

    /*
     * We need the dimension argument to keep track of how deep in the stack of GenericArrayType
     * instances we are so we register the correct array type once we get to the leaf Class object.
     */
    private void registerTypesForGenericSignature(DuringAnalysisAccessImpl access, Type type, int dimension) {
        if (type == null) {
            return;
        }

        try {
            if (processedTypes.getOrDefault(type, -1) >= dimension) {
                return; /* Already processed. */
            }
            processedTypes.put(type, dimension);
        } catch (MalformedParameterizedTypeException | TypeNotPresentException | LinkageError e) {
            /*
             * Hash code computation can trigger an exception if a type in wildcard bounds is
             * missing, in which case we cannot add `type` to `processedTypes`, but even so, we
             * still have to process it. Otherwise, we might fail to make some type reachable.
             */
        }

        if (type instanceof Class<?> && !shouldExcludeClass(access, (Class<?>) type)) {
            Class<?> clazz = (Class<?>) type;
            if (dimension > 0) {
                /*
                 * We only need to register the array type here, since it is the one that gets
                 * stored in the heap. The component type will be registered elsewhere if needed.
                 */
                makeAnalysisTypeReachable(access, access.getMetaAccess().lookupJavaType(clazz).getArrayClass(dimension));
            }
            /* Generic signature parsing will try to instantiate classes via Class.forName(). */
            ClassForNameSupport.registerClass(clazz);
        } else if (type instanceof TypeVariable<?>) {
            /* Bounds are reified lazily. */
            registerTypesForGenericSignature(access, queryGenericInfo(((TypeVariable<?>) type)::getBounds));
        } else if (type instanceof GenericArrayType) {
            registerTypesForGenericSignature(access, ((GenericArrayType) type).getGenericComponentType(), dimension + 1);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            registerTypesForGenericSignature(access, parameterizedType.getActualTypeArguments());
            registerTypesForGenericSignature(access, parameterizedType.getRawType(), dimension);
            registerTypesForGenericSignature(access, parameterizedType.getOwnerType());
        } else if (type instanceof WildcardType) {
            /* Bounds are reified lazily. */
            WildcardType wildcardType = (WildcardType) type;
            registerTypesForGenericSignature(access, queryGenericInfo(wildcardType::getLowerBounds));
            registerTypesForGenericSignature(access, queryGenericInfo(wildcardType::getUpperBounds));
        }
    }

    private void registerTypesForRecordComponent(DuringAnalysisAccessImpl access, Object recordComponent) {
        registerTypesForAnnotations(access, (AnnotatedElement) recordComponent);
        registerTypesForTypeAnnotations(access, (AnnotatedElement) recordComponent);
    }

    private void registerTypesForAnnotations(DuringAnalysisAccessImpl access, AnnotatedElement annotatedElement) {
        if (annotatedElement != null) {
            filteredAnnotations.computeIfAbsent(annotatedElement, element -> {
                List<AnnotationValue> includedAnnotations = new ArrayList<>();
                for (AnnotationValue annotation : annotationExtractor.getDeclaredAnnotationData(element)) {
                    if (includeAnnotation(access, annotation)) {
                        includedAnnotations.add(annotation);
                        registerTypes(access, annotation.getTypes());
                    }
                }
                return includedAnnotations.toArray(new AnnotationValue[0]);
            });
        }
    }

    private void registerTypesForParameterAnnotations(DuringAnalysisAccessImpl access, AnalysisMethod executable) {
        if (executable != null) {
            filteredParameterAnnotations.computeIfAbsent(executable, element -> {
                AnnotationValue[][] parameterAnnotations = annotationExtractor.getParameterAnnotationData(element);
                AnnotationValue[][] includedParameterAnnotations = new AnnotationValue[parameterAnnotations.length][];
                for (int i = 0; i < includedParameterAnnotations.length; ++i) {
                    AnnotationValue[] annotations = parameterAnnotations[i];
                    List<AnnotationValue> includedAnnotations = new ArrayList<>();
                    for (AnnotationValue annotation : annotations) {
                        if (includeAnnotation(access, annotation)) {
                            includedAnnotations.add(annotation);
                            registerTypes(access, annotation.getTypes());
                        }
                    }
                    includedParameterAnnotations[i] = includedAnnotations.toArray(new AnnotationValue[0]);
                }
                return includedParameterAnnotations;
            });
        }
    }

    private void registerTypesForTypeAnnotations(DuringAnalysisAccessImpl access, AnnotatedElement annotatedElement) {
        if (annotatedElement != null) {
            filteredTypeAnnotations.computeIfAbsent(annotatedElement, element -> {
                List<TypeAnnotationValue> includedTypeAnnotations = new ArrayList<>();
                for (TypeAnnotationValue typeAnnotation : annotationExtractor.getTypeAnnotationData(element)) {
                    if (includeAnnotation(access, typeAnnotation.getAnnotationData())) {
                        includedTypeAnnotations.add(typeAnnotation);
                        registerTypes(access, typeAnnotation.getAnnotationData().getTypes());
                    }
                }
                return includedTypeAnnotations.toArray(new TypeAnnotationValue[0]);
            });
        }
    }

    private void registerTypesForAnnotationDefault(DuringAnalysisAccessImpl access, AnalysisMethod method) {
        AnnotationMemberValue annotationDefault = annotationExtractor.getAnnotationDefaultData(method);
        if (annotationDefault != null) {
            registerTypes(access, annotationDefault.getTypes());
        }
    }

    private static boolean includeAnnotation(DuringAnalysisAccessImpl access, AnnotationValue annotationValue) {
        if (annotationValue == null) {
            return false;
        }
        for (Class<?> type : annotationValue.getTypes()) {
            if (type == null || SubstitutionReflectivityFilter.shouldExclude(type, access.getMetaAccess(), access.getUniverse())) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("cast")
    private static void registerTypes(DuringAnalysisAccessImpl access, Collection<Class<?>> types) {
        for (Class<?> type : types) {
            AnalysisType analysisType = access.getMetaAccess().lookupJavaType(type);
            makeAnalysisTypeReachable(access, analysisType);
            if (type.isAnnotation()) {
                RuntimeProxyCreation.register(type);
            }
            /*
             * Exception proxies are stored as-is in the image heap
             */
            if (ExceptionProxy.class.isAssignableFrom(type)) {
                analysisType.registerAsInHeap("Is used by annotation of element registered for reflection.");
            }
        }
    }

    private static void makeAnalysisTypeReachable(DuringAnalysisAccessImpl access, AnalysisType type) {
        if (type.registerAsReachable("registered as side effect of reflection registration")) {
            access.requireAnalysisIteration();
        }
    }

    private void processRegisteredElements(DuringAnalysisAccessImpl access) {
        if (!modifiedClasses.isEmpty()) {
            for (Class<?> clazz : modifiedClasses) {
                processClass(access, clazz);
            }
            modifiedClasses.clear();
            access.requireAnalysisIteration();
        }

        if (!inaccessibleClasses.isEmpty()) {
            inaccessibleClasses.forEach(ClassLoadingExceptionSupport::registerClass);
            inaccessibleClasses.clear();
            access.requireAnalysisIteration();
        }
    }

    private static boolean shouldExcludeClass(DuringAnalysisAccessImpl access, Class<?> clazz) {
        if (clazz.isPrimitive()) {
            return true; // primitives cannot be looked up by name and have no methods or fields
        }
        return SubstitutionReflectivityFilter.shouldExclude(clazz, access.getMetaAccess(), access.getUniverse());
    }

    private void processClass(DuringAnalysisAccessImpl access, Class<?> clazz) {
        if (shouldExcludeClass(access, clazz)) {
            return;
        }

        AnalysisType type = access.getMetaAccess().lookupJavaType(clazz);
        /*
         * Make sure the class is registered as reachable before its fields are accessed below to
         * build the reflection metadata.
         */
        type.registerAsReachable("is registered for reflection");
        if (unsafeInstantiatedClasses.contains(clazz)) {
            type.registerAsAllocated("Is registered for reflection.");
        }

        if (reflectionClasses.contains(clazz)) {
            ClassForNameSupport.registerClass(clazz);

            try {
                if (clazz.getEnclosingClass() != null) {
                    innerClasses.computeIfAbsent(access.getMetaAccess().lookupJavaType(clazz.getEnclosingClass()).getJavaClass(), (enclosingType) -> ConcurrentHashMap.newKeySet()).add(clazz);
                }
            } catch (LinkageError e) {
                reportLinkingErrors(clazz, List.of(e));
            }
        }

        if (type.isAnnotation()) {
            /*
             * Cache the annotation members to allow proxy classes seen later to include those in
             * their own reflection data
             */
            Set<Member> members = new HashSet<>();
            for (Field field : reflectionFields) {
                if (field.getDeclaringClass().equals(clazz) && !SubstitutionReflectivityFilter.shouldExclude(field, access.getMetaAccess(), access.getUniverse())) {
                    members.add(field);
                }
            }
            for (Executable executable : reflectionMethods.keySet()) {
                if (executable.getDeclaringClass().equals(clazz) && !SubstitutionReflectivityFilter.shouldExclude(executable, access.getMetaAccess(), access.getUniverse())) {
                    members.add(executable);
                }
            }
            annotationMembers.put(clazz, members);
            access.requireAnalysisIteration(); /* Need the proxy class to see the added members */
        }
    }

    private static <T> T queryGenericInfo(Callable<T> callable) {
        try {
            return callable.call();
        } catch (MalformedParameterizedTypeException | TypeNotPresentException | LinkageError e) {
            /* These are rethrown at run time, so we can simply ignore them when querying. */
            return null;
        } catch (Throwable t) {
            throw VMError.shouldNotReachHere(t);
        }
    }

    private Object[] buildRecordComponents(Class<?> clazz, DuringAnalysisAccessImpl access) {
        RecordSupport support = RecordSupport.singleton();
        if (!support.isRecord(clazz)) {
            return null;
        }

        /*
         * RecordComponent objects expose the "accessor method" as a java.lang.reflect.Method
         * object. We leverage this tight coupling of RecordComponent and its accessor method to
         * avoid a separate reflection configuration for record components: When all accessor
         * methods of the record class are registered for reflection, then the record components are
         * available. We do not want to expose a partial list of record components, that would be
         * confusing and error-prone. So as soon as a single accessor method is missing from the
         * reflection configuration, we provide no record components. Accessing the record
         * components in that case will throw an exception at image run time, see
         * DynamicHub.getRecordComponents0().
         */
        Method[] allMethods = support.getRecordComponentAccessorMethods(clazz);
        for (Method method : allMethods) {
            if (!reflectionMethods.containsKey(method) || SubstitutionReflectivityFilter.shouldExclude(method, access.getMetaAccess(), access.getUniverse())) {
                return null;
            }
        }
        return support.getRecordComponents(clazz);
    }

    private static void reportLinkingErrors(Class<?> clazz, List<Throwable> errors) {
        if (errors.isEmpty()) {
            return;
        }
        String messages = errors.stream().map(e -> e.getClass().getTypeName() + ": " + e.getMessage())
                        .distinct().collect(Collectors.joining(", "));
        System.out.println("Warning: Could not register complete reflection metadata for " + clazz.getTypeName() + ". Reason(s): " + messages);
    }

    protected void afterAnalysis() {
        sealed = true;
        if (!modifiedClasses.isEmpty()) {
            throw UserError.abort("Registration of classes, methods, and fields for reflective access during analysis must set DuringAnalysisAccess.requireAnalysisIteration().");
        }
    }

    @Override
    public boolean requiresProcessing() {
        return !modifiedClasses.isEmpty();
    }

    @Override
    public Map<Class<?>, Set<Class<?>>> getReflectionInnerClasses() {
        assert sealed;
        return Collections.unmodifiableMap(innerClasses);
    }

    @Override
    public Set<Field> getReflectionFields() {
        assert sealed;
        return Collections.unmodifiableSet(registeredFields.keySet());
    }

    @Override
    public Set<Executable> getReflectionExecutables() {
        assert sealed;
        return Collections.unmodifiableSet(registeredMethods.keySet());
    }

    @Override
    public Object getAccessor(Executable method) {
        assert sealed;
        return methodAccessors.get(method);
    }

    @Override
    public Set<ResolvedJavaField> getHidingReflectionFields() {
        assert sealed;
        return Collections.unmodifiableSet(hidingFields);
    }

    @Override
    public Set<ResolvedJavaMethod> getHidingReflectionMethods() {
        assert sealed;
        return Collections.unmodifiableSet(hidingMethods);
    }

    @Override
    public Object[] getRecordComponents(Class<?> type) {
        assert sealed;
        return registeredRecordComponents.get(type);
    }

    @Override
    public void registerHeapDynamicHub(Object hub) {
        assert !sealed;
        heapDynamicHubs.add((DynamicHub) hub);
    }

    @Override
    public Set<DynamicHub> getHeapDynamicHubs() {
        assert sealed;
        return Collections.unmodifiableSet(heapDynamicHubs);
    }

    @Override
    public void registerHeapReflectionObject(AccessibleObject object) {
        assert !sealed;
        heapReflectionObjects.add(object);
    }

    @Override
    public Set<AccessibleObject> getHeapReflectionObjects() {
        assert sealed;
        return Collections.unmodifiableSet(heapReflectionObjects);
    }

    private static final AnnotationValue[] NO_ANNOTATIONS = new AnnotationValue[0];

    public AnnotationValue[] getAnnotationData(AnnotatedElement element) {
        assert sealed;
        return filteredAnnotations.getOrDefault(element, NO_ANNOTATIONS);
    }

    private static final AnnotationValue[][] NO_PARAMETER_ANNOTATIONS = new AnnotationValue[0][0];

    public AnnotationValue[][] getParameterAnnotationData(AnalysisMethod element) {
        assert sealed;
        return filteredParameterAnnotations.getOrDefault(element, NO_PARAMETER_ANNOTATIONS);
    }

    private static final TypeAnnotationValue[] NO_TYPE_ANNOTATIONS = new TypeAnnotationValue[0];

    public TypeAnnotationValue[] getTypeAnnotationData(AnnotatedElement element) {
        assert sealed;
        return filteredTypeAnnotations.getOrDefault(element, NO_TYPE_ANNOTATIONS);
    }

    public AnnotationMemberValue getAnnotationDefaultData(AnnotatedElement element) {
        return annotationExtractor.getAnnotationDefaultData(element);
    }

    @Override
    public int getReflectionClassesCount() {
        return reflectionClasses.size();
    }

    @Override
    public int getReflectionMethodsCount() {
        return registeredMethods.size();
    }

    @Override
    public int getReflectionFieldsCount() {
        return registeredFields.size();
    }

    private enum ExecutableAccessibility {
        QueriedOnly,
        Accessed;

        static ExecutableAccessibility max(ExecutableAccessibility a, ExecutableAccessibility b) {
            return a == Accessed || b == Accessed ? Accessed : QueriedOnly;
        }
    }
}
