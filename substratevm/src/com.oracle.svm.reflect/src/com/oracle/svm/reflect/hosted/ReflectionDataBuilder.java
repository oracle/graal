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
package com.oracle.svm.reflect.hosted;

import static com.oracle.svm.reflect.hosted.ReflectionMetadataEncoderImpl.getAnnotationEncodingType;
import static com.oracle.svm.reflect.hosted.ReflectionMetadataEncoderImpl.getTypeAnnotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.MalformedParameterizedTypeException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.util.GuardedAnnotationAccess;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.hub.AnnotationTypeSupport;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jdk.RecordSupport;
import com.oracle.svm.core.jdk.proxy.DynamicProxyRegistry;
import com.oracle.svm.core.reflect.SubstrateAccessor;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ConditionalConfigurationRegistry;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.annotation.AnnotationSubstitutionType;
import com.oracle.svm.hosted.meta.InternalRuntimeReflectionSupport;
import com.oracle.svm.hosted.substitute.SubstitutionReflectivityFilter;
import com.oracle.svm.util.ModuleSupport;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.reflect.annotation.AnnotationType;
import sun.reflect.annotation.EnumConstantNotPresentExceptionProxy;
import sun.reflect.annotation.TypeAnnotation;
import sun.reflect.annotation.TypeNotPresentExceptionProxy;

public class ReflectionDataBuilder extends ConditionalConfigurationRegistry implements InternalRuntimeReflectionSupport {

    private final Set<Class<?>> modifiedClasses = ConcurrentHashMap.newKeySet();
    private boolean sealed;

    private final Set<Class<?>> reflectionClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Class<?>> unsafeInstantiatedClasses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<Executable, ExecutableAccessibility> reflectionMethods = new ConcurrentHashMap<>();
    private final Map<Executable, Object> methodAccessors = new ConcurrentHashMap<>();
    private final Set<Field> reflectionFields = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<AnalysisField> hidingFields = ConcurrentHashMap.newKeySet();
    private final Set<AnalysisMethod> hidingMethods = ConcurrentHashMap.newKeySet();
    private final Set<Executable> registeredMethods = ConcurrentHashMap.newKeySet();
    private final Set<Field> registeredFields = ConcurrentHashMap.newKeySet();
    private final Map<Class<?>, Object[]> registeredRecordComponents = new ConcurrentHashMap<>();
    private final Set<DynamicHub> heapDynamicHubs = ConcurrentHashMap.newKeySet();
    private final Set<AccessibleObject> heapReflectionObjects = ConcurrentHashMap.newKeySet();
    private final Map<Class<?>, Set<Class<?>>> innerClasses = new ConcurrentHashMap<>();

    private final Set<Class<?>> processedClasses = new HashSet<>();
    private final Set<Type> processedTypes = new HashSet<>();
    private final Set<DynamicHub> processedDynamicHubs = new HashSet<>();
    private final Map<AnalysisField, Set<AnalysisType>> processedHidingFields = new HashMap<>();
    private final Map<AnalysisMethod, Set<AnalysisType>> processedHidingMethods = new HashMap<>();
    private final Set<AccessibleObject> processedHeapReflectionObjects = new HashSet<>();

    /* Keep track of annotation interface members to include in proxy classes */
    private final Map<Class<?>, Set<Member>> annotationMembers = new HashMap<>();

    public ReflectionDataBuilder() {
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
        processReachableTypes(access);
        processRegisteredElements(access);
        processMethodMetadata(access);
    }

    /*
     * Process all reachable types, looking for array types or types that have an enclosing method
     * or constructor. Initialize the reflection metadata for those types.
     */
    private void processReachableTypes(DuringAnalysisAccessImpl access) {
        /*
         * We need to find all classes that have an enclosingMethod or enclosingConstructor.
         * Unfortunately, there is no reverse lookup (ask a Method or Constructor about the classes
         * they contain), so we need to iterate through all types that have been loaded so far.
         * Accessing the original java.lang.Class for a ResolvedJavaType is not 100% reliable,
         * especially in the case of class and method substitutions. But it is the best we can do
         * here, and we assume that user code that requires reflection support is not using
         * substitutions.
         */
        for (AnalysisType type : access.getUniverse().getTypes()) {
            Class<?> originalClass = type.getJavaClass();
            if (originalClass != null) {
                if (processedClasses.contains(originalClass)) {
                    /* Class has already been processed. */
                    continue;
                }
                if (type.isArray() && !access.isReachable(type)) {
                    /*
                     * We don't want the array type (and its elemental type) to become reachable as
                     * a result of initializing its reflection data.
                     */
                    continue;
                }
                if (type.isArray() || enclosingMethodOrConstructor(originalClass, null) != null) {
                    /*
                     * This type is either an array or it has an enclosing method or constructor. In
                     * either case we process the class, i.e., initialize its reflection data, mark
                     * it as processed and require an analysis iteration.
                     */
                    processClass(access, originalClass);
                    processedClasses.add(originalClass);
                    access.requireAnalysisIteration();
                }
                if (type.getWrappedWithoutResolve() instanceof AnnotationSubstitutionType) {
                    /*
                     * Proxy classes for annotations present the annotation default methods and
                     * fields as their own.
                     */
                    ResolvedJavaType annotationType = ((AnnotationSubstitutionType) type.getWrappedWithoutResolve()).getAnnotationInterfaceType();
                    Class<?> annotationClass = access.getUniverse().lookup(annotationType).getJavaClass();
                    if (!annotationMembers.containsKey(annotationClass)) {
                        processClass(access, annotationClass);
                    }
                    for (Member member : annotationMembers.get(annotationClass)) {
                        try {
                            if (member instanceof Field) {
                                Field field = (Field) member;
                                register(ConfigurationCondition.alwaysTrue(), false, originalClass.getDeclaredField(field.getName()));
                            } else if (member instanceof Method) {
                                Method method = (Method) member;
                                register(ConfigurationCondition.alwaysTrue(), false, originalClass.getDeclaredMethod(method.getName(), method.getParameterTypes()));
                            }
                        } catch (NoSuchFieldException | NoSuchMethodException e) {
                            /*
                             * The annotation member is not present in the proxy class so we don't
                             * add it.
                             */
                        }
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
            if (!registeredFields.contains(reflectField) && !SubstitutionReflectivityFilter.shouldExclude(reflectField, access.getMetaAccess(), access.getUniverse())) {
                AnalysisField analysisField = access.getMetaAccess().lookupJavaField(reflectField);
                registerTypesForField(access, analysisField, reflectField);
                registerHidingSubTypeFields(access, analysisField, analysisField.getDeclaringClass());
                registeredFields.add(reflectField);
            }
        }
        for (Executable method : reflectionMethods.keySet()) {
            if (SubstitutionReflectivityFilter.shouldExclude(method, access.getMetaAccess(), access.getUniverse())) {
                continue;
            }
            if (!registeredMethods.contains(method)) {
                AnalysisMethod analysisMethod = access.getMetaAccess().lookupJavaMethod(method);
                registerTypesForMethod(access, analysisMethod, method);
                registerHidingSubTypeMethods(access, analysisMethod, analysisMethod.getDeclaringClass());
                registeredMethods.add(method);
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
        for (AccessibleObject object : heapReflectionObjects) {
            if (!processedHeapReflectionObjects.contains(object)) {
                if (object instanceof Field) {
                    Field field = (Field) object;
                    if (!SubstitutionReflectivityFilter.shouldExclude(field, access.getMetaAccess(), access.getUniverse())) {
                        AnalysisField analysisField = access.getMetaAccess().lookupJavaField(field);
                        registerTypesForField(access, analysisField, field);
                        registerHidingSubTypeFields(access, analysisField, analysisField.getDeclaringClass());
                    }
                } else if (object instanceof Executable) {
                    Executable executable = (Executable) object;
                    if (!SubstitutionReflectivityFilter.shouldExclude(executable, access.getMetaAccess(), access.getUniverse())) {
                        AnalysisMethod analysisMethod = access.getMetaAccess().lookupJavaMethod(executable);
                        registerTypesForMethod(access, analysisMethod, executable);
                        registerHidingSubTypeMethods(access, analysisMethod, analysisMethod.getDeclaringClass());
                    }
                }
                processedHeapReflectionObjects.add(object);
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
        List<Throwable> errors = new ArrayList<>();
        makeTypeReachable(access, query(clazz::getGenericSuperclass, errors));
        Type[] genericInterfaces = query(clazz::getGenericInterfaces, errors);
        if (genericInterfaces != null) {
            for (Type genericInterface : genericInterfaces) {
                try {
                    makeTypeReachable(access, genericInterface);
                } catch (TypeNotPresentException | LinkageError e) {
                    errors.add(e);
                }
            }
        }
        Executable enclosingMethod = enclosingMethodOrConstructor(clazz, errors);
        if (enclosingMethod != null) {
            makeAnalysisTypeReachable(access, access.getMetaAccess().lookupJavaType(enclosingMethod.getDeclaringClass()));
            RuntimeReflection.registerAsQueried(enclosingMethod);
        }
        reportLinkingErrors(clazz, errors);

        Object[] recordComponents = buildRecordComponents(clazz, access);
        if (recordComponents != null) {
            for (Object recordComponent : recordComponents) {
                registerTypesForRecordComponent(access, recordComponent);
            }
            registeredRecordComponents.put(clazz, recordComponents);
        }
        for (Annotation annotation : GuardedAnnotationAccess.getDeclaredAnnotations(analysisType)) {
            registerTypesForAnnotation(access, annotation);
        }
        for (TypeAnnotation typeAnnotation : getTypeAnnotations(clazz)) {
            // Checkstyle: allow direct annotation access
            registerTypesForAnnotation(access, typeAnnotation.getAnnotation());
            // Checkstyle: disallow direct annotation access
        }
    }

    private void registerTypesForField(DuringAnalysisAccessImpl access, AnalysisField analysisField, Field reflectField) {
        /*
         * Reflection accessors use Unsafe, so ensure that all reflectively accessible fields are
         * registered as unsafe-accessible, whether they have been explicitly registered or their
         * Field object is reachable in the image heap.
         */
        if (!analysisField.isUnsafeAccessed() && !GuardedAnnotationAccess.isAnnotationPresent(analysisField, InjectAccessors.class)) {
            analysisField.registerAsAccessed();
            analysisField.registerAsUnsafeAccessed();
        }

        makeAnalysisTypeReachable(access, analysisField.getDeclaringClass());
        makeAnalysisTypeReachable(access, analysisField.getType());
        makeTypeReachable(access, reflectField.getGenericType());

        /*
         * Enable runtime instantiation of annotations
         */
        for (Annotation annotation : GuardedAnnotationAccess.getDeclaredAnnotations(analysisField)) {
            registerTypesForAnnotation(access, annotation);
        }
        for (TypeAnnotation typeAnnotation : getTypeAnnotations(reflectField)) {
            // Checkstyle: allow direct annotation access
            registerTypesForAnnotation(access, typeAnnotation.getAnnotation());
            // Checkstyle: disallow direct annotation access
        }
    }

    private void registerTypesForMethod(DuringAnalysisAccessImpl access, AnalysisMethod analysisMethod, Executable reflectMethod) {
        makeAnalysisTypeReachable(access, analysisMethod.getDeclaringClass());

        for (TypeVariable<?> type : reflectMethod.getTypeParameters()) {
            makeTypeReachable(access, type);
        }
        for (Type paramType : analysisMethod.getGenericParameterTypes()) {
            makeTypeReachable(access, paramType);
        }
        if (!analysisMethod.isConstructor()) {
            makeTypeReachable(access, ((Method) reflectMethod).getGenericReturnType());
        }
        for (Type exceptionType : reflectMethod.getGenericExceptionTypes()) {
            makeTypeReachable(access, exceptionType);
        }

        /*
         * Enable runtime instantiation of annotations
         */
        for (Annotation annotation : GuardedAnnotationAccess.getDeclaredAnnotations(analysisMethod)) {
            registerTypesForAnnotation(access, annotation);
        }
        for (Annotation[] parameterAnnotations : reflectMethod.getParameterAnnotations()) {
            for (Annotation parameterAnnotation : parameterAnnotations) {
                registerTypesForAnnotation(access, parameterAnnotation);
            }
        }
        for (TypeAnnotation typeAnnotation : getTypeAnnotations(reflectMethod)) {
            // Checkstyle: allow direct annotation access
            registerTypesForAnnotation(access, typeAnnotation.getAnnotation());
            // Checkstyle: disallow direct annotation access
        }
        if (reflectMethod instanceof Method) {
            Object defaultValue = ((Method) reflectMethod).getDefaultValue();
            if (defaultValue != null) {
                registerTypesForAnnotationValue(access, getAnnotationEncodingType(defaultValue), defaultValue);
            }
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

    private void makeTypeReachable(DuringAnalysisAccessImpl access, Type type) {
        try {
            if (type == null || processedTypes.contains(type)) {
                return;
            }
        } catch (TypeNotPresentException e) {
            /* Hash code computation can trigger an exception if the type is missing */
            return;
        }
        processedTypes.add(type);
        if (type instanceof Class<?> && !SubstitutionReflectivityFilter.shouldExclude((Class<?>) type, access.getMetaAccess(), access.getUniverse())) {
            Class<?> clazz = (Class<?>) type;
            makeAnalysisTypeReachable(access, access.getMetaAccess().lookupJavaType(clazz));

            /*
             * Reflection signature parsing will try to instantiate classes via Class.forName().
             */
            if (ClassForNameSupport.forNameOrNull(clazz.getName(), null) == null) {
                access.requireAnalysisIteration();
            }
            ClassForNameSupport.registerClass(clazz);
        } else if (type instanceof TypeVariable<?>) {
            for (Type bound : ((TypeVariable<?>) type).getBounds()) {
                makeTypeReachable(access, bound);
            }
        } else if (type instanceof GenericArrayType) {
            makeTypeReachable(access, ((GenericArrayType) type).getGenericComponentType());
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            for (Type actualType : parameterizedType.getActualTypeArguments()) {
                makeTypeReachable(access, actualType);
            }
            makeTypeReachable(access, parameterizedType.getRawType());
            makeTypeReachable(access, parameterizedType.getOwnerType());
        } else if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            for (Type lowerBound : wildcardType.getLowerBounds()) {
                makeTypeReachable(access, lowerBound);
            }
            for (Type upperBound : wildcardType.getUpperBounds()) {
                makeTypeReachable(access, upperBound);
            }
        }
    }

    private static void registerTypesForRecordComponent(DuringAnalysisAccessImpl access, Object recordComponent) {
        for (Annotation annotation : GuardedAnnotationAccess.getAnnotations((AnnotatedElement) recordComponent)) {
            registerTypesForAnnotation(access, annotation);
        }
        for (TypeAnnotation typeAnnotation : getTypeAnnotations((AnnotatedElement) recordComponent)) {
            // Checkstyle: allow direct annotation access
            registerTypesForAnnotation(access, typeAnnotation.getAnnotation());
            // Checkstyle: disallow direct annotation access
        }
    }

    private static void registerTypesForAnnotation(DuringAnalysisAccessImpl access, Annotation annotation) {
        if (annotation != null) {
            registerTypesForAnnotationValue(access, annotation.annotationType(), annotation);
        }
    }

    @SuppressWarnings("unchecked")
    private static void registerTypesForAnnotationValue(DuringAnalysisAccessImpl access, Class<?> type, Object value) {
        if (type.isAnnotation() && !SubstitutionReflectivityFilter.shouldExclude(type, access.getMetaAccess(), access.getUniverse())) {
            makeAnalysisTypeReachable(access, access.getMetaAccess().lookupJavaType(type));
            /*
             * Parsing annotation data in reflection classes requires being able to instantiate all
             * annotation types at runtime.
             */
            ImageSingletons.lookup(AnnotationTypeSupport.class).createInstance((Class<? extends Annotation>) type);
            ModuleSupport.openModuleByClass(type, ReflectionDataBuilder.class);
            ImageSingletons.lookup(DynamicProxyRegistry.class).addProxyClass(type);

            Annotation annotation = (Annotation) value;
            AnnotationType annotationType = AnnotationType.getInstance((Class<? extends Annotation>) type);
            for (Map.Entry<String, Class<?>> entry : annotationType.memberTypes().entrySet()) {
                String valueName = entry.getKey();
                Class<?> valueType = entry.getValue();
                try {
                    Method getAnnotationValue = annotationType.members().get(valueName);
                    getAnnotationValue.setAccessible(true);
                    Object annotationValue = getAnnotationValue.invoke(annotation);
                    registerTypesForAnnotationValue(access, valueType, annotationValue);
                } catch (IllegalAccessException | InvocationTargetException | InaccessibleObjectException e) {
                    // Ignore the value
                    Throwable exception = e instanceof InvocationTargetException ? ((InvocationTargetException) e).getTargetException() : e;
                    System.out.println("Warning: unable to register annotation value \"" + valueName + "\" for annotation type " + type + ". Reason: " + exception);
                    if (e instanceof InvocationTargetException) {
                        if (exception instanceof TypeNotPresentException) {
                            AnalysisType proxyType = access.getMetaAccess().lookupJavaType(TypeNotPresentExceptionProxy.class);
                            makeAnalysisTypeReachable(access, proxyType);
                            proxyType.registerAsInHeap();
                        } else if (exception instanceof EnumConstantNotPresentException) {
                            AnalysisType proxyType = access.getMetaAccess().lookupJavaType(EnumConstantNotPresentExceptionProxy.class);
                            makeAnalysisTypeReachable(access, proxyType);
                            proxyType.registerAsInHeap();
                        }
                    }
                }
            }
        } else if (type.isArray()) {
            Class<?> componentType = type.getComponentType();
            if (!componentType.isPrimitive()) {
                for (Object val : (Object[]) value) {
                    registerTypesForAnnotationValue(access, componentType, val);
                }
            }
        } else if (type == Class.class) {
            makeAnalysisTypeReachable(access, access.getMetaAccess().lookupJavaType((Class<?>) value));
        } else if (type.isEnum()) {
            makeAnalysisTypeReachable(access, access.getMetaAccess().lookupJavaType(type));
        }
    }

    private static void makeAnalysisTypeReachable(DuringAnalysisAccessImpl access, AnalysisType type) {
        if (type.registerAsReachable()) {
            access.requireAnalysisIteration();
        }
    }

    private void processRegisteredElements(DuringAnalysisAccessImpl access) {
        if (modifiedClasses.isEmpty()) {
            return;
        }
        access.requireAnalysisIteration();

        for (Class<?> clazz : modifiedClasses) {
            processClass(access, clazz);
        }
        modifiedClasses.clear();
    }

    private void processClass(DuringAnalysisAccessImpl access, Class<?> clazz) {
        if (SubstitutionReflectivityFilter.shouldExclude(clazz, access.getMetaAccess(), access.getUniverse())) {
            return;
        }

        AnalysisType type = access.getMetaAccess().lookupJavaType(clazz);
        /*
         * Make sure the class is registered as reachable before its fields are accessed below to
         * build the reflection metadata.
         */
        type.registerAsReachable();
        if (unsafeInstantiatedClasses.contains(clazz)) {
            type.registerAsAllocated(null);
        }

        if (reflectionClasses.contains(clazz)) {
            ClassForNameSupport.registerClass(clazz);

            List<Throwable> errors = new ArrayList<>();
            if (query(clazz::getEnclosingClass, errors) != null) {
                innerClasses.computeIfAbsent(access.getMetaAccess().lookupJavaType(clazz.getEnclosingClass()).getJavaClass(), (enclosingType) -> ConcurrentHashMap.newKeySet()).add(clazz);
            }
            reportLinkingErrors(clazz, errors);
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

    private static <T> T query(Callable<T> callable, List<Throwable> errors) {
        try {
            return callable.call();
        } catch (MalformedParameterizedTypeException | TypeNotPresentException | LinkageError e) {
            errors.add(e);
        } catch (Exception e) {
            throw VMError.shouldNotReachHere(e);
        }
        return null;
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

    private static Executable enclosingMethodOrConstructor(Class<?> clazz, List<Throwable> errors) {
        Method enclosingMethod;
        Constructor<?> enclosingConstructor;
        try {
            enclosingMethod = clazz.getEnclosingMethod();
            enclosingConstructor = clazz.getEnclosingConstructor();
        } catch (TypeNotPresentException | LinkageError e) {
            /*
             * If any of the methods or fields in the class of the enclosing method reference
             * missing types or types that have incompatible changes a LinkageError is thrown. Skip
             * the class.
             */
            if (errors != null) {
                errors.add(e);
            }
            return null;
        } catch (InternalError ex) {
            /*
             * Could not find the enclosing method of the class. This is a host VM error which can
             * happen due to invalid bytecode. For example if the eclosing method index points to a
             * synthetic method for a anonymous class declared inside a lambda. We skip registering
             * the enclosing method for such classes.
             */
            if (errors != null) {
                errors.add(ex);
            }
            return null;
        }

        if (enclosingMethod == null && enclosingConstructor == null) {
            return null;
        }
        if (enclosingMethod != null && enclosingConstructor != null) {
            throw VMError.shouldNotReachHere("Class has both an enclosingMethod and an enclosingConstructor: " + clazz + ", " + enclosingMethod + ", " + enclosingConstructor);
        }

        return enclosingMethod != null ? enclosingMethod : enclosingConstructor;
    }

    @Override
    public Map<Class<?>, Set<Class<?>>> getReflectionInnerClasses() {
        assert sealed;
        return Collections.unmodifiableMap(innerClasses);
    }

    @Override
    public Set<Field> getReflectionFields() {
        assert sealed;
        return Collections.unmodifiableSet(registeredFields);
    }

    @Override
    public Set<Executable> getReflectionExecutables() {
        assert sealed;
        return Collections.unmodifiableSet(registeredMethods);
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
