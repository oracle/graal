/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.MissingRegistrationUtils.throwMissingRegistrationErrors;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_CLASSES_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_CONSTRUCTORS_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_DECLARED_CLASSES_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_DECLARED_CONSTRUCTORS_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_DECLARED_FIELDS_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_DECLARED_METHODS_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_FIELDS_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_METHODS_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_NEST_MEMBERS_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_PERMITTED_SUBCLASSES_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_RECORD_COMPONENTS_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_SIGNERS_FLAG;
import static com.oracle.svm.core.configure.ConfigurationFiles.Options.TreatAllTypeReachableConditionsAsTypeReached;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.MalformedParameterizedTypeException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.RuntimeProxyCreation;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

import com.oracle.graal.pointsto.ObjectScanner.ScanReason;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.MissingRegistrationUtils;
import com.oracle.svm.core.configure.ConditionalRuntimeValue;
import com.oracle.svm.core.configure.RuntimeConditionSet;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.reflect.SubstrateAccessor;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ConditionalConfigurationRegistry;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.LinkAtBuildTimeSupport;
import com.oracle.svm.hosted.annotation.AnnotationMemberValue;
import com.oracle.svm.hosted.annotation.AnnotationValue;
import com.oracle.svm.hosted.annotation.SubstrateAnnotationExtractor;
import com.oracle.svm.hosted.annotation.TypeAnnotationValue;
import com.oracle.svm.hosted.substitute.SubstitutionReflectivityFilter;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;
import sun.reflect.annotation.ExceptionProxy;

public class ReflectionDataBuilder extends ConditionalConfigurationRegistry implements RuntimeReflectionSupport, ReflectionHostedSupport {
    private AnalysisMetaAccess metaAccess;
    private AnalysisUniverse universe;
    private final SubstrateAnnotationExtractor annotationExtractor;
    private BeforeAnalysisAccessImpl analysisAccess;
    private ClassForNameSupport classForNameSupport;

    private boolean sealed;

    // Reflection data
    private final Map<Class<?>, RecordComponent[]> registeredRecordComponents = new ConcurrentHashMap<>();

    /**
     * Member classes accessible for reflection through {@link Class#getDeclaredClasses()} and
     * {@link Class#getClasses()}.
     */
    private final Map<Class<?>, Set<Class<?>>> innerClasses = new ConcurrentHashMap<>();
    private final Map<Class<?>, Integer> enabledQueriesFlags = new ConcurrentHashMap<>();
    private final Map<AnalysisField, ConditionalRuntimeValue<Field>> registeredFields = new ConcurrentHashMap<>();
    private final Set<AnalysisField> hidingFields = ConcurrentHashMap.newKeySet();
    private final Map<AnalysisMethod, ConditionalRuntimeValue<Executable>> registeredMethods = new ConcurrentHashMap<>();
    private final Map<AnalysisMethod, Object> methodAccessors = new ConcurrentHashMap<>();
    private final Set<AnalysisMethod> hidingMethods = ConcurrentHashMap.newKeySet();

    // Heap reflection data
    private final Set<DynamicHub> heapDynamicHubs = ConcurrentHashMap.newKeySet();
    private final Map<AnalysisField, Field> heapFields = new ConcurrentHashMap<>();
    private final Map<AnalysisMethod, Executable> heapMethods = new ConcurrentHashMap<>();

    // Negative queries
    private final Map<AnalysisType, Set<String>> negativeFieldLookups = new ConcurrentHashMap<>();
    private final Map<AnalysisType, Set<AnalysisMethod.Signature>> negativeMethodLookups = new ConcurrentHashMap<>();
    private final Map<AnalysisType, Set<AnalysisType[]>> negativeConstructorLookups = new ConcurrentHashMap<>();

    // Linkage error handling
    private final Map<Class<?>, Throwable> classLookupExceptions = new ConcurrentHashMap<>();
    private final Map<Class<?>, Throwable> fieldLookupExceptions = new ConcurrentHashMap<>();
    private final Map<Class<?>, Throwable> methodLookupExceptions = new ConcurrentHashMap<>();
    private final Map<Class<?>, Throwable> constructorLookupExceptions = new ConcurrentHashMap<>();

    // Intermediate bookkeeping
    private final Map<Type, Set<Integer>> processedTypes = new ConcurrentHashMap<>();
    private final Map<Class<?>, Set<Method>> pendingRecordClasses;

    record ConditionalTask(ConfigurationCondition condition, Consumer<ConfigurationCondition> task) {
    }

    private final Set<ConditionalTask> pendingConditionalTasks = ConcurrentHashMap.newKeySet();

    // Annotations handling
    private final Map<AnnotatedElement, AnnotationValue[]> filteredAnnotations = new ConcurrentHashMap<>();
    private final Map<AnalysisMethod, AnnotationValue[][]> filteredParameterAnnotations = new ConcurrentHashMap<>();
    private final Map<AnnotatedElement, TypeAnnotationValue[]> filteredTypeAnnotations = new ConcurrentHashMap<>();

    ReflectionDataBuilder(SubstrateAnnotationExtractor annotationExtractor) {
        this.annotationExtractor = annotationExtractor;
        pendingRecordClasses = !throwMissingRegistrationErrors() ? new ConcurrentHashMap<>() : null;
        classForNameSupport = ClassForNameSupport.singleton();
    }

    public void duringSetup(AnalysisMetaAccess analysisMetaAccess, AnalysisUniverse analysisUniverse) {
        this.metaAccess = analysisMetaAccess;
        this.universe = analysisUniverse;
        for (var conditionalTask : pendingConditionalTasks) {
            registerConditionalConfiguration(conditionalTask.condition, (cnd) -> universe.getBigbang().postTask(debug -> conditionalTask.task.accept(cnd)));
        }
        pendingConditionalTasks.clear();
    }

    public void beforeAnalysis(BeforeAnalysisAccessImpl beforeAnalysisAccess) {
        this.analysisAccess = beforeAnalysisAccess;
    }

    private void runConditionalInAnalysisTask(ConfigurationCondition condition, Consumer<ConfigurationCondition> task) {
        if (sealed) {
            throw UserError.abort("Too late to add classes, methods, and fields for reflective access. Registration must happen in a Feature before the analysis has finished.");
        }

        if (universe != null) {
            registerConditionalConfiguration(condition, (cnd) -> universe.getBigbang().postTask(debug -> task.accept(cnd)));
        } else {
            pendingConditionalTasks.add(new ConditionalTask(condition, task));
            VMError.guarantee(universe == null, "There shouldn't be a race condition on Feature.duringSetup.");
        }
    }

    private void setQueryFlag(Class<?> clazz, int flag) {
        enabledQueriesFlags.compute(clazz, (key, oldValue) -> (oldValue == null) ? flag : (oldValue | flag));
    }

    @Override
    public void register(ConfigurationCondition condition, boolean unsafeInstantiated, Class<?> clazz) {
        Objects.requireNonNull(clazz, () -> nullErrorMessage("class"));
        runConditionalInAnalysisTask(condition, (cnd) -> registerClass(cnd, clazz, unsafeInstantiated, true));
    }

    @Override
    public void registerAllClassesQuery(ConfigurationCondition condition, Class<?> clazz) {
        guaranteeNotRuntimeConditionForQueries(condition, true);
        runConditionalInAnalysisTask(condition, (cnd) -> {
            setQueryFlag(clazz, ALL_CLASSES_FLAG);
            try {
                for (Class<?> innerClass : clazz.getClasses()) {
                    innerClasses.computeIfAbsent(innerClass.getDeclaringClass(), c -> ConcurrentHashMap.newKeySet()).add(innerClass);
                    registerClass(cnd, innerClass, false, !MissingRegistrationUtils.throwMissingRegistrationErrors());
                }
            } catch (LinkageError e) {
                registerLinkageError(clazz, e, classLookupExceptions);
            }
        });
    }

    /**
     * Runtime conditions can only be used with type, so they are not valid here.
     */
    @SuppressWarnings("unused")
    private static void guaranteeNotRuntimeConditionForQueries(ConfigurationCondition cnd, boolean queriedOnly) {
        if (!TreatAllTypeReachableConditionsAsTypeReached.getValue()) {
            VMError.guarantee(!queriedOnly || cnd.isAlwaysTrue() || !cnd.isRuntimeChecked(), "Bulk queries can only be set with 'name' which does not allow run-time conditions.");
        }
    }

    @Override
    public void registerAllDeclaredClassesQuery(ConfigurationCondition condition, Class<?> clazz) {
        guaranteeNotRuntimeConditionForQueries(condition, true);
        runConditionalInAnalysisTask(condition, (cnd) -> {
            setQueryFlag(clazz, ALL_DECLARED_CLASSES_FLAG);
            try {
                for (Class<?> innerClass : clazz.getDeclaredClasses()) {
                    innerClasses.computeIfAbsent(clazz, c -> ConcurrentHashMap.newKeySet()).add(innerClass);
                    registerClass(cnd, innerClass, false, !MissingRegistrationUtils.throwMissingRegistrationErrors());
                }
            } catch (LinkageError e) {
                registerLinkageError(clazz, e, classLookupExceptions);
            }
        });
    }

    private void registerClass(ConfigurationCondition condition, Class<?> clazz, boolean unsafeInstantiated, boolean allowForName) {
        if (shouldExcludeClass(clazz)) {
            return;
        }

        AnalysisType type = metaAccess.lookupJavaType(clazz);
        type.registerAsReachable("Is registered for reflection.");
        if (unsafeInstantiated) {
            type.registerAsUnsafeAllocated("Is registered for reflection.");
        }

        if (allowForName) {
            classForNameSupport.registerClass(condition, clazz);

            if (!MissingRegistrationUtils.throwMissingRegistrationErrors()) {
                /*
                 * We have to ensure that code that relies on classes registered for reflection
                 * being accessible through Class.get(Declared)Classes() keeps working. However,
                 * this behavior means that those methods can return incomplete sets of inner
                 * classes, which is not coherent with the Java specification and is therefore
                 * disabled under the strict metadata mode (-H:ThrowMissingRegistrationErrors).
                 */
                try {
                    if (clazz.getEnclosingClass() != null) {
                        Class<?> enclosingClass = metaAccess.lookupJavaType(clazz.getEnclosingClass()).getJavaClass();
                        innerClasses.computeIfAbsent(enclosingClass, (enclosingType) -> ConcurrentHashMap.newKeySet()).add(clazz);
                    }
                } catch (LinkageError e) {
                    reportLinkingErrors(clazz, List.of(e));
                }
            }
        }
    }

    @Override
    public void registerClassLookupException(ConfigurationCondition condition, String typeName, Throwable t) {
        runConditionalInAnalysisTask(condition, (cnd) -> classForNameSupport.registerExceptionForClass(cnd, typeName, t));
    }

    @Override
    public void registerClassLookup(ConfigurationCondition condition, String typeName) {
        runConditionalInAnalysisTask(condition, (cnd) -> {
            try {
                registerClass(cnd, Class.forName(typeName, false, ClassLoader.getSystemClassLoader()), false, true);
            } catch (ClassNotFoundException e) {
                classForNameSupport.registerNegativeQuery(cnd, typeName);
            } catch (Throwable t) {
                classForNameSupport.registerExceptionForClass(cnd, typeName, t);
            }
        });
    }

    @Override
    public void registerAllRecordComponentsQuery(ConfigurationCondition condition, Class<?> clazz) {
        guaranteeNotRuntimeConditionForQueries(condition, true);
        runConditionalInAnalysisTask(condition, (cnd) -> {
            setQueryFlag(clazz, ALL_RECORD_COMPONENTS_FLAG);
            registerRecordComponents(clazz);
        });
    }

    @Override
    public void registerAllPermittedSubclassesQuery(ConfigurationCondition condition, Class<?> clazz) {
        guaranteeNotRuntimeConditionForQueries(condition, true);
        runConditionalInAnalysisTask(condition, (cnd) -> {
            setQueryFlag(clazz, ALL_PERMITTED_SUBCLASSES_FLAG);
            if (clazz.isSealed()) {
                for (Class<?> permittedSubclass : clazz.getPermittedSubclasses()) {
                    registerClass(condition, permittedSubclass, false, false);
                }
            }
        });
    }

    @Override
    public void registerAllNestMembersQuery(ConfigurationCondition condition, Class<?> clazz) {
        guaranteeNotRuntimeConditionForQueries(condition, true);
        runConditionalInAnalysisTask(condition, (cnd) -> {
            setQueryFlag(clazz, ALL_NEST_MEMBERS_FLAG);
            for (Class<?> nestMember : clazz.getNestMembers()) {
                if (nestMember != clazz) {
                    registerClass(condition, nestMember, false, false);
                }
            }
        });
    }

    @Override
    public void registerAllSignersQuery(ConfigurationCondition condition, Class<?> clazz) {
        guaranteeNotRuntimeConditionForQueries(condition, true);
        runConditionalInAnalysisTask(condition, (cnd) -> {
            setQueryFlag(clazz, ALL_SIGNERS_FLAG);
            Object[] signers = clazz.getSigners();
            if (signers != null) {
                for (Object signer : signers) {
                    metaAccess.lookupJavaType(signer.getClass()).registerAsInstantiated("signer");
                }
            }
        });
    }

    @Override
    public void register(ConfigurationCondition condition, boolean queriedOnly, Executable... executables) {
        requireNonNull(executables, "executable");
        runConditionalInAnalysisTask(condition, (cnd) -> registerMethods(cnd, queriedOnly, executables));
    }

    @Override
    public void registerAllMethodsQuery(ConfigurationCondition condition, boolean queriedOnly, Class<?> clazz) {
        guaranteeNotRuntimeConditionForQueries(condition, queriedOnly);
        runConditionalInAnalysisTask(condition, (cnd) -> {
            for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
                setQueryFlag(current, ALL_METHODS_FLAG);
            }
            try {
                registerMethods(cnd, queriedOnly, clazz.getMethods());
            } catch (LinkageError e) {
                registerLinkageError(clazz, e, methodLookupExceptions);
            }
        });
    }

    @Override
    public void registerAllDeclaredMethodsQuery(ConfigurationCondition condition, boolean queriedOnly, Class<?> clazz) {
        guaranteeNotRuntimeConditionForQueries(condition, queriedOnly);
        runConditionalInAnalysisTask(condition, (cnd) -> {
            setQueryFlag(clazz, ALL_DECLARED_METHODS_FLAG);
            try {
                registerMethods(cnd, queriedOnly, clazz.getDeclaredMethods());
            } catch (LinkageError e) {
                registerLinkageError(clazz, e, methodLookupExceptions);
            }
        });
    }

    @Override
    public void registerAllConstructorsQuery(ConfigurationCondition condition, boolean queriedOnly, Class<?> clazz) {
        guaranteeNotRuntimeConditionForQueries(condition, queriedOnly);
        runConditionalInAnalysisTask(condition, (cnd) -> {
            for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
                setQueryFlag(current, ALL_CONSTRUCTORS_FLAG);
            }
            try {
                registerMethods(cnd, queriedOnly, clazz.getConstructors());
            } catch (LinkageError e) {
                registerLinkageError(clazz, e, constructorLookupExceptions);
            }
        });
    }

    @Override
    public void registerAllDeclaredConstructorsQuery(ConfigurationCondition condition, boolean queriedOnly, Class<?> clazz) {
        guaranteeNotRuntimeConditionForQueries(condition, queriedOnly);
        runConditionalInAnalysisTask(condition, (cnd) -> {
            setQueryFlag(clazz, ALL_DECLARED_CONSTRUCTORS_FLAG);
            try {
                registerMethods(cnd, queriedOnly, clazz.getDeclaredConstructors());
            } catch (LinkageError e) {
                registerLinkageError(clazz, e, constructorLookupExceptions);
            }
        });
    }

    private void registerMethods(ConfigurationCondition cnd, boolean queriedOnly, Executable[] reflectExecutables) {
        for (Executable reflectExecutable : reflectExecutables) {
            registerMethod(cnd, queriedOnly, reflectExecutable);
        }
    }

    private void registerMethod(ConfigurationCondition cnd, boolean queriedOnly, Executable reflectExecutable) {
        if (SubstitutionReflectivityFilter.shouldExclude(reflectExecutable, metaAccess, universe)) {
            return;
        }

        AnalysisMethod analysisMethod = metaAccess.lookupJavaMethod(reflectExecutable);
        var exists = registeredMethods.containsKey(analysisMethod);
        var conditionalValue = registeredMethods.computeIfAbsent(analysisMethod, (t) -> new ConditionalRuntimeValue<>(RuntimeConditionSet.emptySet(), reflectExecutable));
        if (!queriedOnly) {
            /* queryOnly methods are conditioned by the type itself */
            conditionalValue.getConditions().addCondition(cnd);
        }

        if (!exists) {
            registerTypesForMethod(analysisMethod, reflectExecutable);
            AnalysisType declaringType = analysisMethod.getDeclaringClass();
            Class<?> declaringClass = declaringType.getJavaClass();

            /*
             * The image needs to know about subtypes shadowing methods registered for reflection to
             * ensure the correctness of run-time reflection queries.
             */
            analysisAccess.registerSubtypeReachabilityHandler(
                            (access, subType) -> universe.getBigbang().postTask(debug -> checkSubtypeForOverridingMethod(analysisMethod, metaAccess.lookupJavaType(subType))), declaringClass);

            if (declaringType.isAnnotation() && !analysisMethod.isConstructor()) {
                processAnnotationMethod(queriedOnly, (Method) reflectExecutable);
            }

            if (!throwMissingRegistrationErrors() && declaringClass.isRecord()) {
                pendingRecordClasses.computeIfPresent(declaringClass, (clazz, unregisteredAccessors) -> {
                    if (unregisteredAccessors.remove(reflectExecutable) && unregisteredAccessors.isEmpty()) {
                        registerRecordComponents(declaringClass);
                    }
                    return unregisteredAccessors;
                });
            }
        }

        /*
         * We need to run this even if the method has already been registered, in case it was only
         * registered as queried.
         */
        if (!queriedOnly) {
            methodAccessors.computeIfAbsent(analysisMethod, aMethod -> {
                SubstrateAccessor accessor = ImageSingletons.lookup(ReflectionFeature.class).getOrCreateAccessor(reflectExecutable);
                universe.getHeapScanner().rescanObject(accessor);
                return accessor;
            });
        }
    }

    @Override
    public void registerMethodLookup(ConfigurationCondition condition, Class<?> declaringClass, String methodName, Class<?>... parameterTypes) {
        guaranteeNotRuntimeConditionForQueries(condition, true);
        runConditionalInAnalysisTask(condition, (cnd) -> {
            try {
                registerMethod(cnd, true, declaringClass.getDeclaredMethod(methodName, parameterTypes));
            } catch (NoSuchMethodException e) {
                negativeMethodLookups.computeIfAbsent(metaAccess.lookupJavaType(declaringClass), (key) -> ConcurrentHashMap.newKeySet())
                                .add(new AnalysisMethod.Signature(methodName, metaAccess.lookupJavaTypes(parameterTypes)));
            }
        });
    }

    @Override
    public void registerConstructorLookup(ConfigurationCondition condition, Class<?> declaringClass, Class<?>... parameterTypes) {
        guaranteeNotRuntimeConditionForQueries(condition, true);
        runConditionalInAnalysisTask(condition, (cnd) -> {
            try {
                registerMethod(cnd, true, declaringClass.getDeclaredConstructor(parameterTypes));
            } catch (NoSuchMethodException e) {
                negativeConstructorLookups.computeIfAbsent(metaAccess.lookupJavaType(declaringClass), (key) -> ConcurrentHashMap.newKeySet())
                                .add(metaAccess.lookupJavaTypes(parameterTypes));
            }
        });
    }

    @Override
    public void register(ConfigurationCondition condition, boolean finalIsWritable, Field... fields) {
        requireNonNull(fields, "field");
        runConditionalInAnalysisTask(condition, (cnd) -> registerFields(cnd, false, fields));
    }

    @Override
    public void registerAllFieldsQuery(ConfigurationCondition condition, Class<?> clazz) {
        registerAllFieldsQuery(condition, false, clazz);
    }

    private void registerAllFieldsQuery(ConfigurationCondition condition, boolean queriedOnly, Class<?> clazz) {
        guaranteeNotRuntimeConditionForQueries(condition, queriedOnly);
        runConditionalInAnalysisTask(condition, (cnd) -> {
            for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
                setQueryFlag(current, ALL_FIELDS_FLAG);
            }
            try {
                registerFields(cnd, queriedOnly, clazz.getFields());
            } catch (LinkageError e) {
                registerLinkageError(clazz, e, fieldLookupExceptions);
            }
        });
    }

    @Override
    public void registerAllDeclaredFieldsQuery(ConfigurationCondition condition, Class<?> clazz) {
        registerAllDeclaredFieldsQuery(condition, false, clazz);
    }

    private void registerAllDeclaredFieldsQuery(ConfigurationCondition condition, boolean queriedOnly, Class<?> clazz) {
        runConditionalInAnalysisTask(condition, (cnd) -> {
            setQueryFlag(clazz, ALL_DECLARED_FIELDS_FLAG);
            try {
                registerFields(cnd, queriedOnly, clazz.getDeclaredFields());
            } catch (LinkageError e) {
                registerLinkageError(clazz, e, fieldLookupExceptions);
            }
        });
    }

    private void registerFields(ConfigurationCondition cnd, boolean queriedOnly, Field[] reflectFields) {
        for (Field reflectField : reflectFields) {
            registerField(cnd, queriedOnly, reflectField);
        }
    }

    private void registerField(ConfigurationCondition cnd, boolean queriedOnly, Field reflectField) {
        if (SubstitutionReflectivityFilter.shouldExclude(reflectField, metaAccess, universe)) {
            return;
        }

        AnalysisField analysisField = metaAccess.lookupJavaField(reflectField);

        if (!registeredFields.containsKey(analysisField)) {
            registerTypesForField(analysisField, reflectField, true);
            AnalysisType declaringClass = analysisField.getDeclaringClass();

            /*
             * The image needs to know about subtypes shadowing fields registered for reflection to
             * ensure the correctness of run-time reflection queries.
             */
            analysisAccess.registerSubtypeReachabilityHandler(
                            (access, subType) -> universe.getBigbang().postTask(debug -> checkSubtypeForOverridingField(analysisField, metaAccess.lookupJavaType(subType))),
                            declaringClass.getJavaClass());

            if (declaringClass.isAnnotation()) {
                processAnnotationField(cnd, reflectField);
            }
        }

        var cndValue = registeredFields.computeIfAbsent(analysisField, f -> new ConditionalRuntimeValue<>(RuntimeConditionSet.emptySet(), reflectField));
        cndValue.getConditions().addCondition(cnd);

        /*
         * We need to run this even if the method has already been registered, in case it was only
         * registered as queried.
         */
        if (!queriedOnly) {
            registerTypesForField(analysisField, reflectField, false);
        }
    }

    @Override
    public void registerFieldLookup(ConfigurationCondition condition, Class<?> declaringClass, String fieldName) {
        runConditionalInAnalysisTask(condition, (cnd) -> {
            try {
                registerField(cnd, false, declaringClass.getDeclaredField(fieldName));
            } catch (NoSuchFieldException e) {
                /*
                 * This path is not possible to reach at runtime with run-time conditions as they
                 * can only be used with `type` which includes all fields so negative lookups are
                 * not necessary.
                 */
                negativeFieldLookups.computeIfAbsent(metaAccess.lookupJavaType(declaringClass), (key) -> ConcurrentHashMap.newKeySet()).add(fieldName);
            }
        });
    }

    /*
     * Proxy classes for annotations present the annotation default methods and fields as their own.
     */
    @SuppressWarnings("deprecation")
    private void processAnnotationMethod(boolean queriedOnly, Method method) {
        Class<?> annotationClass = method.getDeclaringClass();
        Class<?> proxyClass = Proxy.getProxyClass(annotationClass.getClassLoader(), annotationClass);
        try {
            var condition = ConfigurationCondition.create(proxyClass, true);
            register(condition, queriedOnly, proxyClass.getDeclaredMethod(method.getName(), method.getParameterTypes()));
        } catch (NoSuchMethodException e) {
            /*
             * The annotation member is not present in the proxy class so we don't add it.
             */
        }
    }

    @SuppressWarnings("deprecation")
    private void processAnnotationField(ConfigurationCondition cnd, Field field) {
        Class<?> annotationClass = field.getDeclaringClass();
        Class<?> proxyClass = Proxy.getProxyClass(annotationClass.getClassLoader(), annotationClass);
        try {
            register(cnd, false, proxyClass.getDeclaredField(field.getName()));
        } catch (NoSuchFieldException e) {
            /*
             * The annotation member is not present in the proxy class so we don't add it.
             */
        }
    }

    /**
     * @see ReflectionHostedSupport#getHidingReflectionFields()
     */
    private void checkSubtypeForOverridingField(AnalysisField field, AnalysisType subtype) {
        try {
            ResolvedJavaField[] subClassFields = field.isStatic() ? subtype.getStaticFields() : subtype.getInstanceFields(false);
            for (ResolvedJavaField javaField : subClassFields) {
                AnalysisField subclassField = (AnalysisField) javaField;
                if (subclassField.getName().equals(field.getName())) {
                    hidingFields.add(subclassField);
                }
            }
        } catch (UnsupportedFeatureException | LinkageError e) {
            /*
             * A field that is not supposed to end up in the image is considered as being absent for
             * reflection purposes.
             */
        }
    }

    /**
     * Using {@link AnalysisType#findMethod(String, Signature)} here which uses
     * {@link Class#getDeclaredMethods()} internally, instead of
     * {@link AnalysisType#resolveConcreteMethod(ResolvedJavaMethod)} which gives different results
     * in at least two scenarios:
     * <p>
     * 1) When resolving a static method, resolveConcreteMethod does not return a subclass method
     * with the same signature, since they are actually fully distinct methods. However, these
     * methods need to be included in the hiding list because them showing up in a reflection query
     * would be wrong.
     * <p>
     * 2) When resolving an interface method from an abstract class, resolveConcreteMethod returns
     * an undeclared method with the abstract subclass as declaring class, which is not the
     * reflection API behavior.
     *
     * @see ReflectionHostedSupport#getHidingReflectionMethods()
     */
    private void checkSubtypeForOverridingMethod(AnalysisMethod method, AnalysisType subtype) {
        try {
            AnalysisMethod subClassMethod = subtype.findMethod(method.getName(), method.getSignature());
            if (subClassMethod != null) {
                hidingMethods.add(subClassMethod);
            }
        } catch (UnsupportedFeatureException | LinkageError e) {
            /*
             * A method that is not supposed to end up in the image is considered as being absent
             * for reflection purposes.
             */
        }
    }

    private void registerTypesForClass(AnalysisType analysisType, Class<?> clazz) {
        /*
         * The generic signature is parsed at run time, so we need to make all the types necessary
         * for parsing also available at run time.
         */
        registerTypesForGenericSignature(queryGenericInfo(clazz::getTypeParameters));
        registerTypesForGenericSignature(queryGenericInfo(clazz::getGenericSuperclass));
        registerTypesForGenericSignature(queryGenericInfo(clazz::getGenericInterfaces));

        registerTypesForEnclosingMethodInfo(clazz);
        if (!throwMissingRegistrationErrors()) {
            maybeRegisterRecordComponents(clazz);
        }

        registerTypesForAnnotations(analysisType);
        registerTypesForTypeAnnotations(analysisType);
    }

    private void registerRecordComponents(Class<?> clazz) {
        RecordComponent[] recordComponents = clazz.getRecordComponents();
        if (recordComponents == null) {
            return;
        }
        for (RecordComponent recordComponent : recordComponents) {
            registerTypesForRecordComponent(recordComponent);
        }
        registeredRecordComponents.put(clazz, recordComponents);
    }

    private void registerTypesForEnclosingMethodInfo(Class<?> clazz) {
        Object[] enclosingMethodInfo = getEnclosingMethodInfo(clazz);
        if (enclosingMethodInfo == null) {
            return; /* Nothing to do. */
        }

        /* Ensure the class stored in the enclosing method info is available at run time. */
        metaAccess.lookupJavaType((Class<?>) enclosingMethodInfo[0]).registerAsReachable("Is used by the enclosing method info of an element registered for reflection.");

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

    private void registerTypesForField(AnalysisField analysisField, Field reflectField, boolean queriedOnly) {
        if (!queriedOnly) {
            /*
             * Reflection accessors use Unsafe, so ensure that all reflectively accessible fields
             * are registered as unsafe-accessible, whether they have been explicitly registered or
             * their Field object is reachable in the image heap.
             */
            analysisField.registerAsUnsafeAccessed("is registered for reflection.");
        }

        /*
         * The generic signature is parsed at run time, so we need to make all the types necessary
         * for parsing also available at run time.
         */
        registerTypesForGenericSignature(queryGenericInfo(reflectField::getGenericType));

        /*
         * Enable runtime instantiation of annotations
         */
        registerTypesForAnnotations(analysisField);
        registerTypesForTypeAnnotations(analysisField);
    }

    private void registerTypesForMethod(AnalysisMethod analysisMethod, Executable reflectExecutable) {
        /*
         * The generic signature is parsed at run time, so we need to make all the types necessary
         * for parsing also available at run time.
         */
        registerTypesForGenericSignature(queryGenericInfo(reflectExecutable::getTypeParameters));
        registerTypesForGenericSignature(queryGenericInfo(reflectExecutable::getGenericParameterTypes));
        registerTypesForGenericSignature(queryGenericInfo(reflectExecutable::getGenericExceptionTypes));
        if (!analysisMethod.isConstructor()) {
            registerTypesForGenericSignature(queryGenericInfo(((Method) reflectExecutable)::getGenericReturnType));
        }

        /*
         * Enable runtime instantiation of annotations
         */
        registerTypesForAnnotations(analysisMethod);
        registerTypesForParameterAnnotations(analysisMethod);
        registerTypesForTypeAnnotations(analysisMethod);
        if (!analysisMethod.isConstructor()) {
            registerTypesForAnnotationDefault(analysisMethod);
        }
    }

    private void registerTypesForGenericSignature(Type[] types) {
        registerTypesForGenericSignature(types, 0);
    }

    private void registerTypesForGenericSignature(Type[] types, int dimension) {
        if (types != null) {
            for (Type type : types) {
                registerTypesForGenericSignature(type, dimension);
            }
        }
    }

    private void registerTypesForGenericSignature(Type type) {
        registerTypesForGenericSignature(type, 0);
    }

    /*
     * We need the dimension argument to keep track of how deep in the stack of GenericArrayType
     * instances we are so we register the correct array type once we get to the leaf Class object.
     */
    private void registerTypesForGenericSignature(Type type, int dimension) {
        try {
            if (type == null || !processedTypes.computeIfAbsent(type, t -> ConcurrentHashMap.newKeySet()).add(dimension)) {
                return;
            }
        } catch (MalformedParameterizedTypeException | TypeNotPresentException | LinkageError e) {
            /*
             * Hash code computation can trigger an exception if a type in wildcard bounds is
             * missing, in which case we cannot add `type` to `processedTypes`, but even so, we
             * still have to process it. Otherwise, we might fail to make some type reachable.
             */
        }

        if (type instanceof Class<?>) {
            Class<?> clazz = (Class<?>) type;
            if (shouldExcludeClass(clazz)) {
                return;
            }

            if (dimension > 0) {
                /*
                 * We only need to register the array type here, since it is the one that gets
                 * stored in the heap. The component type will be registered elsewhere if needed.
                 */
                metaAccess.lookupJavaType(clazz).getArrayClass(dimension).registerAsReachable("Is used by generic signature of element registered for reflection.");
            }

            /*
             * Reflection signature parsing will try to instantiate classes via Class.forName().
             */
            classForNameSupport.registerClass(clazz);
        } else if (type instanceof TypeVariable<?>) {
            /* Bounds are reified lazily. */
            registerTypesForGenericSignature(queryGenericInfo(((TypeVariable<?>) type)::getBounds), dimension);
        } else if (type instanceof GenericArrayType) {
            registerTypesForGenericSignature(queryGenericInfo(((GenericArrayType) type)::getGenericComponentType), dimension + 1);
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            registerTypesForGenericSignature(queryGenericInfo(parameterizedType::getActualTypeArguments));
            registerTypesForGenericSignature(queryGenericInfo(parameterizedType::getRawType), dimension);
            registerTypesForGenericSignature(queryGenericInfo(parameterizedType::getOwnerType));
        } else if (type instanceof WildcardType) {
            /* Bounds are reified lazily. */
            WildcardType wildcardType = (WildcardType) type;
            registerTypesForGenericSignature(queryGenericInfo(wildcardType::getLowerBounds), dimension);
            registerTypesForGenericSignature(queryGenericInfo(wildcardType::getUpperBounds), dimension);
        }
    }

    private void registerTypesForRecordComponent(RecordComponent recordComponent) {
        Method accessorOrNull = recordComponent.getAccessor();
        if (accessorOrNull != null) {
            register(ConfigurationCondition.alwaysTrue(), true, accessorOrNull);
        }
        registerTypesForAnnotations(recordComponent);
        registerTypesForTypeAnnotations(recordComponent);
    }

    private void registerTypesForAnnotations(AnnotatedElement annotatedElement) {
        if (annotatedElement != null) {
            if (!filteredAnnotations.containsKey(annotatedElement)) {
                List<AnnotationValue> includedAnnotations = new ArrayList<>();
                for (AnnotationValue annotation : annotationExtractor.getDeclaredAnnotationData(annotatedElement)) {
                    if (includeAnnotation(annotation)) {
                        includedAnnotations.add(annotation);
                        registerTypesForAnnotation(annotation);
                    }
                }
                filteredAnnotations.put(annotatedElement, includedAnnotations.toArray(new AnnotationValue[0]));
            }
        }
    }

    private void registerTypesForParameterAnnotations(AnalysisMethod method) {
        if (method != null) {
            if (!filteredParameterAnnotations.containsKey(method)) {
                AnnotationValue[][] parameterAnnotations = annotationExtractor.getParameterAnnotationData(method);
                AnnotationValue[][] includedParameterAnnotations = new AnnotationValue[parameterAnnotations.length][];
                for (int i = 0; i < includedParameterAnnotations.length; ++i) {
                    AnnotationValue[] annotations = parameterAnnotations[i];
                    List<AnnotationValue> includedAnnotations = new ArrayList<>();
                    for (AnnotationValue annotation : annotations) {
                        if (includeAnnotation(annotation)) {
                            includedAnnotations.add(annotation);
                            registerTypesForAnnotation(annotation);
                        }
                    }
                    includedParameterAnnotations[i] = includedAnnotations.toArray(new AnnotationValue[0]);
                }
                filteredParameterAnnotations.put(method, includedParameterAnnotations);
            }
        }
    }

    private void registerTypesForTypeAnnotations(AnnotatedElement annotatedElement) {
        if (annotatedElement != null) {
            if (!filteredTypeAnnotations.containsKey(annotatedElement)) {
                List<TypeAnnotationValue> includedTypeAnnotations = new ArrayList<>();
                for (TypeAnnotationValue typeAnnotation : annotationExtractor.getTypeAnnotationData(annotatedElement)) {
                    if (includeAnnotation(typeAnnotation.getAnnotationData())) {
                        includedTypeAnnotations.add(typeAnnotation);
                        registerTypesForAnnotation(typeAnnotation.getAnnotationData());
                    }
                }
                filteredTypeAnnotations.put(annotatedElement, includedTypeAnnotations.toArray(new TypeAnnotationValue[0]));
            }
        }
    }

    private void registerTypesForAnnotationDefault(AnalysisMethod method) {
        AnnotationMemberValue annotationDefault = annotationExtractor.getAnnotationDefaultData(method);
        if (annotationDefault != null) {
            registerTypes(annotationDefault.getTypes());
        }
    }

    private boolean includeAnnotation(AnnotationValue annotationValue) {
        if (annotationValue == null) {
            return false;
        }
        for (Class<?> type : annotationValue.getTypes()) {
            if (type == null || SubstitutionReflectivityFilter.shouldExclude(type, metaAccess, universe)) {
                return false;
            }
        }
        return true;
    }

    private void registerTypesForAnnotation(AnnotationValue annotationValue) {
        registerTypes(annotationValue.getTypes());
    }

    @SuppressWarnings("cast")
    private void registerTypes(Collection<Class<?>> types) {
        for (Class<?> type : types) {
            AnalysisType analysisType = metaAccess.lookupJavaType(type);
            analysisType.registerAsReachable("Is used by annotation of element registered for reflection.");
            if (type.isAnnotation()) {
                RuntimeProxyCreation.register(type);
                RuntimeReflection.registerAllDeclaredMethods(type);
            }
            /*
             * Exception proxies are stored as-is in the image heap
             */
            if (ExceptionProxy.class.isAssignableFrom(type)) {
                analysisType.registerAsInstantiated("Is used by annotation of element registered for reflection.");
            }
        }
    }

    private boolean shouldExcludeClass(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            return true; // primitives cannot be looked up by name and have no methods or fields
        }
        return SubstitutionReflectivityFilter.shouldExclude(clazz, metaAccess, universe);
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

    private void maybeRegisterRecordComponents(Class<?> clazz) {
        if (!clazz.isRecord()) {
            return;
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
        Method[] accessors = RecordUtils.getRecordComponentAccessorMethods(clazz);
        Set<Method> unregisteredAccessors = ConcurrentHashMap.newKeySet();
        for (Method accessor : accessors) {
            if (SubstitutionReflectivityFilter.shouldExclude(accessor, metaAccess, universe)) {
                return;
            }
            unregisteredAccessors.add(accessor);
        }
        pendingRecordClasses.put(clazz, unregisteredAccessors);

        unregisteredAccessors.removeIf(accessor -> registeredMethods.containsKey(metaAccess.lookupJavaMethod(accessor)));
        if (unregisteredAccessors.isEmpty()) {
            registerRecordComponents(clazz);
        }
    }

    private static void registerLinkageError(Class<?> clazz, LinkageError error, Map<Class<?>, Throwable> errorMap) {
        if (LinkAtBuildTimeSupport.singleton().linkAtBuildTime(clazz)) {
            throw error;
        } else {
            errorMap.put(clazz, error);
        }
    }

    private static void reportLinkingErrors(Class<?> clazz, List<Throwable> errors) {
        if (errors.isEmpty()) {
            return;
        }
        String messages = errors.stream().map(e -> e.getClass().getTypeName() + ": " + e.getMessage())
                        .distinct().collect(Collectors.joining(", "));
        LogUtils.warning("Could not register complete reflection metadata for %s. Reason(s): %s.", clazz.getTypeName(), messages);
    }

    protected void afterAnalysis() {
        sealed = true;
        processedTypes.clear();
        if (!throwMissingRegistrationErrors()) {
            pendingRecordClasses.clear();
        }
    }

    @Override
    public Map<Class<?>, Set<Class<?>>> getReflectionInnerClasses() {
        assert sealed;
        return Collections.unmodifiableMap(innerClasses);
    }

    public int getEnabledReflectionQueries(Class<?> clazz) {
        int enabledQueries = enabledQueriesFlags.getOrDefault(clazz, 0);
        /*
         * Primitives, arrays and object are registered by default since they provide reflective
         * access to either no members or only Object methods.
         */
        if (clazz == Object.class || clazz.isPrimitive() || clazz.isArray()) {
            enabledQueries |= ALL_DECLARED_CLASSES_FLAG | ALL_CLASSES_FLAG | ALL_DECLARED_CONSTRUCTORS_FLAG | ALL_CONSTRUCTORS_FLAG | ALL_DECLARED_METHODS_FLAG | ALL_METHODS_FLAG |
                            ALL_DECLARED_FIELDS_FLAG | ALL_FIELDS_FLAG;
        }
        return enabledQueries;
    }

    @Override
    public Map<AnalysisField, ConditionalRuntimeValue<Field>> getReflectionFields() {
        assert sealed;
        return Collections.unmodifiableMap(registeredFields);
    }

    @Override
    public Map<AnalysisMethod, ConditionalRuntimeValue<Executable>> getReflectionExecutables() {
        assert sealed;
        return Collections.unmodifiableMap(registeredMethods);
    }

    @Override
    public Object getAccessor(AnalysisMethod method) {
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
    public RecordComponent[] getRecordComponents(Class<?> type) {
        assert sealed;
        return registeredRecordComponents.get(type);
    }

    @Override
    public void registerHeapDynamicHub(Object object, ScanReason reason) {
        assert !sealed;
        DynamicHub hub = (DynamicHub) object;
        Class<?> javaClass = hub.getHostedJavaClass();
        if (heapDynamicHubs.add(hub) && !SubstitutionReflectivityFilter.shouldExclude(javaClass, metaAccess, universe)) {
            registerTypesForClass(metaAccess.lookupJavaType(javaClass), javaClass);
        }
    }

    @Override
    public Set<DynamicHub> getHeapDynamicHubs() {
        assert sealed;
        return Collections.unmodifiableSet(heapDynamicHubs);
    }

    @Override
    public void registerHeapReflectionField(Field reflectField, ScanReason reason) {
        assert !sealed;
        AnalysisField analysisField = metaAccess.lookupJavaField(reflectField);
        if (heapFields.put(analysisField, reflectField) == null && !SubstitutionReflectivityFilter.shouldExclude(reflectField, metaAccess, universe)) {
            registerTypesForField(analysisField, reflectField, false);
            if (analysisField.getDeclaringClass().isAnnotation()) {
                processAnnotationField(ConfigurationCondition.alwaysTrue(), reflectField);
            }
        }
    }

    @Override
    public void registerHeapReflectionExecutable(Executable reflectExecutable, ScanReason reason) {
        assert !sealed;
        AnalysisMethod analysisMethod = metaAccess.lookupJavaMethod(reflectExecutable);
        if (heapMethods.put(analysisMethod, reflectExecutable) == null && !SubstitutionReflectivityFilter.shouldExclude(reflectExecutable, metaAccess, universe)) {
            registerTypesForMethod(analysisMethod, reflectExecutable);
            if (reflectExecutable instanceof Method && reflectExecutable.getDeclaringClass().isAnnotation()) {
                processAnnotationMethod(false, (Method) reflectExecutable);
            }
        }
    }

    @Override
    public Map<AnalysisField, Field> getHeapReflectionFields() {
        assert sealed;
        return Collections.unmodifiableMap(heapFields);
    }

    @Override
    public Map<AnalysisMethod, Executable> getHeapReflectionExecutables() {
        assert sealed;
        return Collections.unmodifiableMap(heapMethods);
    }

    @Override
    public Map<AnalysisType, Set<String>> getNegativeFieldQueries() {
        return Collections.unmodifiableMap(negativeFieldLookups);
    }

    @Override
    public Map<AnalysisType, Set<AnalysisMethod.Signature>> getNegativeMethodQueries() {
        return Collections.unmodifiableMap(negativeMethodLookups);
    }

    @Override
    public Map<AnalysisType, Set<AnalysisType[]>> getNegativeConstructorQueries() {
        return Collections.unmodifiableMap(negativeConstructorLookups);
    }

    @Override
    public Map<Class<?>, Throwable> getClassLookupErrors() {
        return Collections.unmodifiableMap(classLookupExceptions);
    }

    @Override
    public Map<Class<?>, Throwable> getFieldLookupErrors() {
        return Collections.unmodifiableMap(fieldLookupExceptions);
    }

    @Override
    public Map<Class<?>, Throwable> getMethodLookupErrors() {
        return Collections.unmodifiableMap(methodLookupExceptions);
    }

    @Override
    public Map<Class<?>, Throwable> getConstructorLookupErrors() {
        return Collections.unmodifiableMap(constructorLookupExceptions);
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
    public int getReflectionMethodsCount() {
        return registeredMethods.size();
    }

    @Override
    public int getReflectionFieldsCount() {
        return registeredFields.size();
    }

    private static void requireNonNull(Object[] values, String kind) {
        for (Object value : values) {
            Objects.requireNonNull(value, () -> nullErrorMessage(kind));
        }
    }

    private static String nullErrorMessage(String kind) {
        return "Cannot register null value as " + kind + " for reflection. Please ensure that all values you register are not null.";
    }

    public static class TestBackdoor {
        public static void registerField(ReflectionDataBuilder reflectionDataBuilder, boolean queriedOnly, Field field) {
            reflectionDataBuilder.runConditionalInAnalysisTask(ConfigurationCondition.alwaysTrue(), (cnd) -> reflectionDataBuilder.registerField(cnd, queriedOnly, field));
        }
    }
}
