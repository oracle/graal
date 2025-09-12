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
import static org.graalvm.nativeimage.dynamicaccess.AccessCondition.unconditional;

import java.lang.annotation.AnnotationFormatError;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.dynamicaccess.AccessCondition;
import org.graalvm.nativeimage.hosted.RuntimeProxyCreation;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;
import org.graalvm.nativeimage.impl.TypeReachabilityCondition;

import com.oracle.graal.pointsto.ObjectScanner.OtherReason;
import com.oracle.graal.pointsto.ObjectScanner.ScanReason;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.meta.AnalysisElement;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.FutureDefaultsOptions;
import com.oracle.svm.core.MissingRegistrationUtils;
import com.oracle.svm.core.configure.ConditionalRuntimeValue;
import com.oracle.svm.core.configure.RuntimeConditionSet;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.imagelayer.BuildingImageLayerPredicate;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.reflect.SubstrateAccessor;
import com.oracle.svm.core.reflect.target.ReflectionSubstitutionSupport;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.SingletonLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTrait;
import com.oracle.svm.core.traits.SingletonTraitKind;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ClassLoaderFeature;
import com.oracle.svm.hosted.ConditionalConfigurationRegistry;
import com.oracle.svm.hosted.DeadlockWatchdog;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.LinkAtBuildTimeSupport;
import com.oracle.svm.hosted.annotation.SubstrateAnnotationExtractor;
import com.oracle.svm.hosted.substitute.SubstitutionReflectivityFilter;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.ElementTypeMismatch;
import jdk.graal.compiler.annotation.EnumElement;
import jdk.graal.compiler.annotation.MissingType;
import jdk.graal.compiler.annotation.TypeAnnotationValue;
import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.reflect.annotation.ExceptionProxy;
import sun.reflect.annotation.TypeNotPresentExceptionProxy;

public class ReflectionDataBuilder extends ConditionalConfigurationRegistry implements RuntimeReflectionSupport, ReflectionHostedSupport {
    private AnalysisMetaAccess metaAccess;
    private final SubstrateAnnotationExtractor annotationExtractor;
    private BeforeAnalysisAccessImpl analysisAccess;
    private final ClassForNameSupport classForNameSupport;
    private LayeredReflectionDataBuilder layeredReflectionDataBuilder;
    private SubstitutionReflectivityFilter reflectivityFilter;

    // Reflection data
    private final Map<Class<?>, RecordComponent[]> registeredRecordComponents = new ConcurrentHashMap<>();

    /**
     * Member classes accessible for reflection through {@link Class#getDeclaredClasses()} and
     * {@link Class#getClasses()}.
     */
    private final Map<Class<?>, Set<Class<?>>> innerClasses = new ConcurrentHashMap<>();
    private final Map<Class<?>, Integer> enabledQueriesFlags = new ConcurrentHashMap<>();
    private final Map<AnalysisType, Map<AnalysisField, ConditionalRuntimeValue<Field>>> registeredFields = new ConcurrentHashMap<>();
    private final Set<AnalysisField> hidingFields = ConcurrentHashMap.newKeySet();
    private final Map<AnalysisType, Map<AnalysisMethod, ConditionalRuntimeValue<Executable>>> registeredMethods = new ConcurrentHashMap<>();
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
    private final Map<Class<?>, Throwable> recordComponentsLookupExceptions = new ConcurrentHashMap<>();

    // Intermediate bookkeeping
    private Map<Type, Set<Integer>> processedTypes = new ConcurrentHashMap<>();
    private Map<Class<?>, Set<Method>> pendingRecordClasses;

    // Annotations handling
    private final Map<AnnotatedElement, AnnotationValue[]> filteredAnnotations = new ConcurrentHashMap<>();
    private final Map<AnalysisMethod, AnnotationValue[][]> filteredParameterAnnotations = new ConcurrentHashMap<>();
    private final Map<AnnotatedElement, TypeAnnotationValue[]> filteredTypeAnnotations = new ConcurrentHashMap<>();

    // Reason tracking for manually triggered rescans
    private final ScanReason scanReason = new OtherReason("Manual rescan triggered from " + ReflectionDataBuilder.class);

    public ReflectionDataBuilder(SubstrateAnnotationExtractor annotationExtractor) {
        this.annotationExtractor = annotationExtractor;
        pendingRecordClasses = !throwMissingRegistrationErrors() ? new ConcurrentHashMap<>() : null;
        classForNameSupport = ClassForNameSupport.currentLayer();
    }

    public void duringSetup(AnalysisMetaAccess analysisMetaAccess, AnalysisUniverse analysisUniverse) {
        this.metaAccess = analysisMetaAccess;
        setUniverse(analysisUniverse);
        if (ImageLayerBuildingSupport.buildingImageLayer()) {
            layeredReflectionDataBuilder = LayeredReflectionDataBuilder.singleton();
        }
        reflectivityFilter = SubstitutionReflectivityFilter.singleton();
    }

    public void beforeAnalysis(BeforeAnalysisAccessImpl beforeAnalysisAccess) {
        this.analysisAccess = beforeAnalysisAccess;
    }

    private void runConditionalInAnalysisTask(AccessCondition condition, Consumer<AccessCondition> task) {
        abortIfSealed();
        runConditionalTask(condition, task);
    }

    private void setQueryFlag(Class<?> clazz, int flag) {
        enabledQueriesFlags.compute(clazz, (_, oldValue) -> (oldValue == null) ? flag : (oldValue | flag));
    }

    private boolean isQueryFlagSet(Class<?> clazz, int flag) {
        return (enabledQueriesFlags.getOrDefault(clazz, 0) & flag) != 0;
    }

    @Override
    public void register(AccessCondition condition, boolean unsafeInstantiated, boolean preserved, Class<?> clazz) {
        Objects.requireNonNull(clazz, () -> nullErrorMessage("class", "reflection"));
        runConditionalInAnalysisTask(condition, (cnd) -> {
            registerClass(cnd, clazz, unsafeInstantiated, true, preserved);
            if (FutureDefaultsOptions.completeReflectionTypes()) {
                registerClassMetadata(cnd, clazz);
            }
        });
    }

    @Override
    public void registerAllClassesQuery(AccessCondition condition, boolean preserved, Class<?> clazz) {
        guaranteeNotRuntimeConditionForQueries(condition, true);
        runConditionalInAnalysisTask(condition, (cnd) -> {
            setQueryFlag(clazz, ALL_CLASSES_FLAG);
            try {
                for (Class<?> innerClass : clazz.getClasses()) {
                    if (innerClass.getDeclaringClass() != null) {
                        /* Malformed inner classes can have no declaring class */
                        innerClasses.computeIfAbsent(innerClass.getDeclaringClass(), _ -> ConcurrentHashMap.newKeySet()).add(innerClass);
                    }
                    registerClass(cnd, innerClass, false, !MissingRegistrationUtils.throwMissingRegistrationErrors(), preserved);
                }
            } catch (LinkageError e) {
                registerLinkageError(clazz, e, classLookupExceptions);
            }
        });
    }

    void registerClassMetadata(AccessCondition condition, Class<?> clazz) {
        registerAllDeclaredFieldsQuery(condition, true, false, clazz);
        registerAllFieldsQuery(condition, true, false, clazz);
        registerAllDeclaredMethodsQuery(condition, true, false, clazz);
        registerAllMethodsQuery(condition, true, false, clazz);
        registerAllDeclaredConstructorsQuery(condition, true, false, clazz);
        registerAllConstructorsQuery(condition, true, false, clazz);
        registerAllDeclaredClassesQuery(condition, false, clazz);
        registerAllClassesQuery(condition, false, clazz);
        registerAllRecordComponentsQuery(condition, clazz);
        registerAllPermittedSubclassesQuery(condition, false, clazz);
        registerAllNestMembersQuery(condition, false, clazz);
        registerAllSignersQuery(condition, clazz);
    }

    /**
     * Runtime conditions can only be used with type, so they are not valid here.
     */
    @SuppressWarnings("unused")
    private static void guaranteeNotRuntimeConditionForQueries(AccessCondition cnd, boolean queriedOnly) {
        VMError.guarantee(cnd instanceof TypeReachabilityCondition, "Condition must be TypeReachabilityCondition.");
        TypeReachabilityCondition reachabilityCondition = (TypeReachabilityCondition) cnd;
        if (!TreatAllTypeReachableConditionsAsTypeReached.getValue()) {
            VMError.guarantee(!queriedOnly || reachabilityCondition.isAlwaysTrue() || !reachabilityCondition.isRuntimeChecked(),
                            "Bulk queries can only be set with 'name' which does not allow run-time conditions.");
        }
    }

    @Override
    public void registerAllDeclaredClassesQuery(AccessCondition condition, boolean preserved, Class<?> clazz) {
        guaranteeNotRuntimeConditionForQueries(condition, true);
        runConditionalInAnalysisTask(condition, (cnd) -> {
            setQueryFlag(clazz, ALL_DECLARED_CLASSES_FLAG);
            try {
                for (Class<?> innerClass : clazz.getDeclaredClasses()) {
                    innerClasses.computeIfAbsent(clazz, _ -> ConcurrentHashMap.newKeySet()).add(innerClass);
                    registerClass(cnd, innerClass, false, !MissingRegistrationUtils.throwMissingRegistrationErrors(), preserved);
                }
            } catch (LinkageError e) {
                registerLinkageError(clazz, e, classLookupExceptions);
            }
        });
    }

    private void registerClass(AccessCondition condition, Class<?> clazz, boolean unsafeInstantiated, boolean allowForName, boolean preserved) {
        if (shouldExcludeClass(clazz)) {
            return;
        }
        AnalysisType type = metaAccess.lookupJavaType(clazz);
        type.registerAsReachable("Is registered for reflection.");
        if (unsafeInstantiated) {
            type.registerAsUnsafeAllocated("Is registered via reflection metadata.");
            classForNameSupport.registerUnsafeAllocated(condition, clazz, preserved);
        }

        if (allowForName) {
            classForNameSupport.registerClass(condition, clazz, ClassLoaderFeature.getRuntimeClassLoader(clazz.getClassLoader()), preserved);

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
                        innerClasses.computeIfAbsent(enclosingClass, _ -> ConcurrentHashMap.newKeySet()).add(clazz);
                    }
                } catch (LinkageError e) {
                    reportLinkingErrors(clazz, List.of(e));
                }
            }
        }
    }

    @Override
    public void registerClassLookupException(AccessCondition condition, String typeName, Throwable t) {
        runConditionalInAnalysisTask(condition, (cnd) -> classForNameSupport.registerExceptionForClass(cnd, typeName, t, false));
    }

    @Override
    public void registerClassLookup(AccessCondition condition, boolean preserved, String typeName) {
        runConditionalInAnalysisTask(condition, (cnd) -> {
            try {
                registerClass(cnd, Class.forName(typeName, false, ClassLoader.getSystemClassLoader()), false, true, preserved);
            } catch (ClassNotFoundException e) {
                classForNameSupport.registerNegativeQuery(cnd, typeName);
            } catch (Throwable t) {
                classForNameSupport.registerExceptionForClass(cnd, typeName, t, preserved);
            }
        });
    }

    @Override
    public void registerAllRecordComponentsQuery(AccessCondition condition, Class<?> clazz) {
        guaranteeNotRuntimeConditionForQueries(condition, true);
        runConditionalInAnalysisTask(condition, _ -> {
            setQueryFlag(clazz, ALL_RECORD_COMPONENTS_FLAG);
            registerRecordComponents(clazz);
        });
    }

    @Override
    public void registerAllPermittedSubclassesQuery(AccessCondition condition, boolean preserved, Class<?> clazz) {
        guaranteeNotRuntimeConditionForQueries(condition, true);
        runConditionalInAnalysisTask(condition, _ -> {
            setQueryFlag(clazz, ALL_PERMITTED_SUBCLASSES_FLAG);
            if (clazz.isSealed()) {
                for (Class<?> permittedSubclass : clazz.getPermittedSubclasses()) {
                    registerClass(condition, permittedSubclass, false, false, preserved);
                }
            }
        });
    }

    @Override
    public void registerAllNestMembersQuery(AccessCondition condition, boolean preserved, Class<?> clazz) {
        guaranteeNotRuntimeConditionForQueries(condition, true);
        runConditionalInAnalysisTask(condition, _ -> {
            setQueryFlag(clazz, ALL_NEST_MEMBERS_FLAG);
            for (Class<?> nestMember : clazz.getNestMembers()) {
                if (nestMember != clazz) {
                    registerClass(condition, nestMember, false, false, preserved);
                }
            }
        });
    }

    @Override
    public void registerAllSignersQuery(AccessCondition condition, Class<?> clazz) {
        guaranteeNotRuntimeConditionForQueries(condition, true);
        runConditionalInAnalysisTask(condition, _ -> {
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
    public void register(AccessCondition condition, boolean queriedOnly, boolean preserved, Executable... executables) {
        requireNonNull(executables, "executable", "reflection");
        runConditionalInAnalysisTask(condition, (cnd) -> registerMethods(cnd, queriedOnly, preserved, executables));
    }

    @Override
    public void registerAllMethodsQuery(AccessCondition condition, boolean queriedOnly, boolean preserved, Class<?> clazz) {
        guaranteeNotRuntimeConditionForQueries(condition, queriedOnly);
        runConditionalInAnalysisTask(condition, (cnd) -> {
            for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
                setQueryFlag(current, ALL_METHODS_FLAG);
            }
            try {
                registerMethods(cnd, queriedOnly, preserved, clazz.getMethods());
            } catch (LinkageError e) {
                registerLinkageError(clazz, e, methodLookupExceptions);
            }
        });
    }

    @Override
    public void registerAllDeclaredMethodsQuery(AccessCondition condition, boolean queriedOnly, boolean preserved, Class<?> clazz) {
        guaranteeNotRuntimeConditionForQueries(condition, queriedOnly);
        runConditionalInAnalysisTask(condition, (cnd) -> {
            setQueryFlag(clazz, ALL_DECLARED_METHODS_FLAG);
            try {
                registerMethods(cnd, queriedOnly, preserved, clazz.getDeclaredMethods());
            } catch (LinkageError e) {
                registerLinkageError(clazz, e, methodLookupExceptions);
            }
        });
    }

    @Override
    public void registerAllConstructorsQuery(AccessCondition condition, boolean queriedOnly, boolean preserved, Class<?> clazz) {
        guaranteeNotRuntimeConditionForQueries(condition, queriedOnly);
        runConditionalInAnalysisTask(condition, (cnd) -> {
            for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
                setQueryFlag(current, ALL_CONSTRUCTORS_FLAG);
            }
            try {
                registerMethods(cnd, queriedOnly, preserved, clazz.getConstructors());
            } catch (LinkageError e) {
                registerLinkageError(clazz, e, constructorLookupExceptions);
            }
        });
    }

    @Override
    public void registerAllDeclaredConstructorsQuery(AccessCondition condition, boolean queriedOnly, boolean preserved, Class<?> clazz) {
        guaranteeNotRuntimeConditionForQueries(condition, queriedOnly);
        runConditionalInAnalysisTask(condition, (cnd) -> {
            setQueryFlag(clazz, ALL_DECLARED_CONSTRUCTORS_FLAG);
            try {
                registerMethods(cnd, queriedOnly, preserved, clazz.getDeclaredConstructors());
            } catch (LinkageError e) {
                registerLinkageError(clazz, e, constructorLookupExceptions);
            }
        });
    }

    private void registerMethods(AccessCondition cnd, boolean queriedOnly, boolean preserved, Executable[] reflectExecutables) {
        for (Executable reflectExecutable : reflectExecutables) {
            registerMethod(cnd, queriedOnly, preserved, reflectExecutable);
        }
    }

    private void registerMethod(AccessCondition cnd, boolean queriedOnly, boolean preserved, Executable reflectExecutable) {
        if (reflectivityFilter.shouldExclude(reflectExecutable)) {
            return;
        }

        AnalysisMethod analysisMethod = metaAccess.lookupJavaMethod(reflectExecutable);
        AnalysisType declaringType = analysisMethod.getDeclaringClass();

        if (layeredReflectionDataBuilder != null && layeredReflectionDataBuilder.isMethodRegistered(analysisMethod)) {
            /* GR-66387: The runtime condition should be combined across layers. */
            return;
        }

        var classMethods = registeredMethods.computeIfAbsent(declaringType, _ -> new ConcurrentHashMap<>());
        var shouldRegisterReachabilityHandler = classMethods.isEmpty();

        boolean registered = false;
        ConditionalRuntimeValue<Executable> conditionalValue = classMethods.get(analysisMethod);
        if (conditionalValue == null) {
            var newConditionalValue = new ConditionalRuntimeValue<>(RuntimeConditionSet.emptySet(preserved), reflectExecutable);
            conditionalValue = classMethods.putIfAbsent(analysisMethod, newConditionalValue);
            if (conditionalValue == null) {
                conditionalValue = newConditionalValue;
                registered = true;
            }
        } else if (!preserved) {
            conditionalValue.getConditions().setNotPreserved();
        }
        if (!queriedOnly) {
            /* queryOnly methods are conditioned by the type itself */
            conditionalValue.getConditions().addCondition(cnd);
        }

        if (registered) {
            registerTypesForMethod(analysisMethod, reflectExecutable);
            Class<?> declaringClass = declaringType.getJavaClass();

            /*
             * The image needs to know about subtypes shadowing methods registered for reflection to
             * ensure the correctness of run-time reflection queries.
             */
            if (shouldRegisterReachabilityHandler) {
                analysisAccess.registerSubtypeReachabilityHandler(
                                (_, subType) -> universe.getBigbang()
                                                .postTask(_ -> checkSubtypeForOverridingMethods(metaAccess.lookupJavaType(subType), registeredMethods.get(declaringType).keySet())),
                                declaringClass);
            } else {
                /*
                 * We need to perform the check for already reachable subtypes since the
                 * reachability handler was already called for them.
                 */
                for (AnalysisType subtype : AnalysisUniverse.reachableSubtypes(declaringType)) {
                    universe.getBigbang().postTask(_ -> checkSubtypeForOverridingMethods(subtype, Collections.singleton(analysisMethod)));
                }
            }

            if (declaringType.isAnnotation() && !analysisMethod.isConstructor()) {
                processAnnotationMethod(queriedOnly, (Method) reflectExecutable);
            }

            if (!throwMissingRegistrationErrors() && declaringClass.isRecord()) {
                pendingRecordClasses.computeIfPresent(declaringClass, (_, unregisteredAccessors) -> {
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
            methodAccessors.computeIfAbsent(analysisMethod, _ -> {
                SubstrateAccessor accessor = ImageSingletons.lookup(ReflectionFeature.class).getOrCreateAccessor(reflectExecutable);
                universe.getHeapScanner().rescanObject(accessor, scanReason);
                return accessor;
            });
        }
    }

    @Override
    public void registerMethodLookup(AccessCondition condition, boolean preserved, Class<?> declaringClass, String methodName, Class<?>... parameterTypes) {
        guaranteeNotRuntimeConditionForQueries(condition, true);
        runConditionalInAnalysisTask(condition, (cnd) -> {
            try {
                registerMethod(cnd, true, preserved, declaringClass.getDeclaredMethod(methodName, parameterTypes));
            } catch (NoSuchMethodException e) {
                negativeMethodLookups.computeIfAbsent(metaAccess.lookupJavaType(declaringClass), _ -> ConcurrentHashMap.newKeySet())
                                .add(new AnalysisMethod.Signature(methodName, metaAccess.lookupJavaTypes(parameterTypes)));
            } catch (LinkageError le) {
                registerLinkageError(declaringClass, le, methodLookupExceptions);
            }
        });
    }

    @Override
    public void registerConstructorLookup(AccessCondition condition, boolean preserved, Class<?> declaringClass, Class<?>... parameterTypes) {
        guaranteeNotRuntimeConditionForQueries(condition, true);
        runConditionalInAnalysisTask(condition, (cnd) -> {
            try {
                registerMethod(cnd, true, preserved, declaringClass.getDeclaredConstructor(parameterTypes));
            } catch (NoSuchMethodException e) {
                negativeConstructorLookups.computeIfAbsent(metaAccess.lookupJavaType(declaringClass), _ -> ConcurrentHashMap.newKeySet())
                                .add(metaAccess.lookupJavaTypes(parameterTypes));
            } catch (LinkageError le) {
                registerLinkageError(declaringClass, le, constructorLookupExceptions);
            }
        });
    }

    @Override
    public void register(AccessCondition condition, boolean finalIsWritable, boolean preserved, Field... fields) {
        requireNonNull(fields, "field", "reflection");
        runConditionalInAnalysisTask(condition, (cnd) -> registerFields(cnd, false, preserved, fields));
    }

    @Override
    public void registerAllFields(AccessCondition condition, boolean preserved, Class<?> clazz) {
        registerAllFieldsQuery(condition, false, preserved, clazz);
    }

    public void registerAllFieldsQuery(AccessCondition condition, boolean queriedOnly, boolean preserved, Class<?> clazz) {
        guaranteeNotRuntimeConditionForQueries(condition, queriedOnly);
        runConditionalInAnalysisTask(condition, (cnd) -> {
            for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
                setQueryFlag(current, ALL_FIELDS_FLAG);
            }
            try {
                registerFields(cnd, queriedOnly, preserved, clazz.getFields());
            } catch (LinkageError e) {
                registerLinkageError(clazz, e, fieldLookupExceptions);
            }
        });
    }

    @Override
    public void registerAllDeclaredFields(AccessCondition condition, boolean preserved, Class<?> clazz) {
        registerAllDeclaredFieldsQuery(condition, false, preserved, clazz);
    }

    private record AllDeclaredFieldsQuery(AccessCondition condition, boolean queriedOnly, Class<?> clazz) {
    }

    // Tracks types that have had all declared fields registered for reflection.
    // The boolean value in the map tracks whether the registration was preserved.
    private Map<AllDeclaredFieldsQuery, Boolean> existingAllDeclaredFieldsQuery = new ConcurrentHashMap<>();

    public void registerAllDeclaredFieldsQuery(AccessCondition condition, boolean queriedOnly, boolean preserved, Class<?> clazz) {
        final var query = new AllDeclaredFieldsQuery(condition, queriedOnly, clazz);
        /*
         * Register the query if it was never registered, or if it was registered by preserve but
         * preserved == false (re-register to unmark the fields as preserved). This limits
         * registration to at most twice per class.
         */
        if (!existingAllDeclaredFieldsQuery.containsKey(query) ||
                        existingAllDeclaredFieldsQuery.get(query) && !preserved) {
            runConditionalInAnalysisTask(condition, (cnd) -> {
                setQueryFlag(clazz, ALL_DECLARED_FIELDS_FLAG);
                try {
                    registerFields(cnd, queriedOnly, preserved, clazz.getDeclaredFields());
                } catch (LinkageError e) {
                    registerLinkageError(clazz, e, fieldLookupExceptions);
                }
            });
            existingAllDeclaredFieldsQuery.put(query, preserved);
        }
    }

    private void registerFields(AccessCondition cnd, boolean queriedOnly, boolean preserved, Field[] reflectFields) {
        for (Field reflectField : reflectFields) {
            registerField(cnd, queriedOnly, preserved, reflectField);
        }
    }

    private void registerField(AccessCondition cnd, boolean queriedOnly, boolean preserved, Field reflectField) {
        if (reflectivityFilter.shouldExclude(reflectField)) {
            return;
        }

        AnalysisField analysisField = metaAccess.lookupJavaField(reflectField);
        AnalysisType declaringClass = analysisField.getDeclaringClass();

        if (layeredReflectionDataBuilder != null && layeredReflectionDataBuilder.isFieldRegistered(analysisField)) {
            /* GR-66387: The runtime condition should be combined across layers. */
            return;
        }

        var classFields = registeredFields.computeIfAbsent(declaringClass, _ -> new ConcurrentHashMap<>());
        boolean exists = classFields.containsKey(analysisField);
        boolean shouldRegisterReachabilityHandler = classFields.isEmpty();
        var cndValue = classFields.computeIfAbsent(analysisField, _ -> new ConditionalRuntimeValue<>(RuntimeConditionSet.emptySet(preserved), reflectField));
        if (exists) {
            if (!preserved) {
                cndValue.getConditions().setNotPreserved();
            }
        } else {
            registerTypesForField(analysisField, reflectField, queriedOnly);

            /*
             * The image needs to know about subtypes shadowing fields registered for reflection to
             * ensure the correctness of run-time reflection queries.
             */
            if (shouldRegisterReachabilityHandler) {
                analysisAccess.registerSubtypeReachabilityHandler(
                                (_, subType) -> universe.getBigbang()
                                                .postTask(_ -> checkSubtypeForOverridingFields(metaAccess.lookupJavaType(subType),
                                                                registeredFields.get(declaringClass).keySet())),
                                declaringClass.getJavaClass());
            } else {
                /*
                 * We need to perform the check for already reachable subtypes since the
                 * reachability handler was already called for them.
                 */
                for (AnalysisType subtype : AnalysisUniverse.reachableSubtypes(declaringClass)) {
                    universe.getBigbang().postTask(_ -> checkSubtypeForOverridingFields(subtype, Collections.singleton(analysisField)));
                }
            }

            if (declaringClass.isAnnotation()) {
                processAnnotationField(reflectField);
            }
        }

        /*
         * We need to run this even if the field has already been registered, in case it was only
         * registered as queried.
         */
        if (!queriedOnly) {
            /* queryOnly methods are conditioned on the type itself */
            cndValue.getConditions().addCondition(cnd);
            registerTypesForField(analysisField, reflectField, false);
        }
    }

    @Override
    public void registerFieldLookup(AccessCondition condition, boolean preserved, Class<?> declaringClass, String fieldName) {
        runConditionalInAnalysisTask(condition, (cnd) -> {
            try {
                registerField(cnd, false, preserved, declaringClass.getDeclaredField(fieldName));
            } catch (NoSuchFieldException e) {
                /*
                 * This path is not possible to reach at runtime with run-time conditions as they
                 * can only be used with `type` which includes all fields so negative lookups are
                 * not necessary.
                 */
                negativeFieldLookups.computeIfAbsent(metaAccess.lookupJavaType(declaringClass), _ -> ConcurrentHashMap.newKeySet()).add(fieldName);
            } catch (LinkageError le) {
                registerLinkageError(declaringClass, le, fieldLookupExceptions);
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
            register(unconditional(), queriedOnly, false, proxyClass.getDeclaredMethod(method.getName(), method.getParameterTypes()));
        } catch (NoSuchMethodException e) {
            /*
             * The annotation member is not present in the proxy class so we don't add it.
             */
        }
    }

    @SuppressWarnings("deprecation")
    private void processAnnotationField(Field field) {
        Class<?> annotationClass = field.getDeclaringClass();
        Class<?> proxyClass = Proxy.getProxyClass(annotationClass.getClassLoader(), annotationClass);
        try {
            register(unconditional(), false, false, proxyClass.getDeclaredField(field.getName()));
        } catch (NoSuchFieldException e) {
            /*
             * The annotation member is not present in the proxy class so we don't add it.
             */
        }
    }

    /**
     * @see ReflectionHostedSupport#getHidingReflectionFields()
     */
    private void checkSubtypeForOverridingFields(AnalysisType subtype, Collection<AnalysisField> superclassFields) {
        if (isQueryFlagSet(subtype.getJavaClass(), ALL_DECLARED_FIELDS_FLAG)) {
            /* All fields are already registered, no need for hiding fields */
            return;
        }
        try {
            Set<ResolvedJavaField> subClassFields = new HashSet<>();
            subClassFields.addAll(Arrays.asList(subtype.getInstanceFields(false)));
            subClassFields.addAll(Arrays.asList(subtype.getStaticFields()));
            for (ResolvedJavaField javaField : subClassFields) {
                for (AnalysisField registeredField : superclassFields) {
                    AnalysisField subclassField = (AnalysisField) javaField;
                    if (subclassField.getName().equals(registeredField.getName())) {
                        hidingFields.add(subclassField);
                        subclassField.getType().registerAsReachable("Is the declared type of a hiding Field used by reflection");
                    }
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
     * Filtering {@link Class#getDeclaredMethods()} here instead of directly calling
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
    private void checkSubtypeForOverridingMethods(AnalysisType subtype, Collection<AnalysisMethod> superclassMethods) {
        if (isQueryFlagSet(subtype.getJavaClass(), ALL_DECLARED_METHODS_FLAG)) {
            /* All methods are already registered, no need for hiding methods */
            return;
        }
        try {
            DeadlockWatchdog.singleton().recordActivity();
            for (AnalysisMethod subClassMethod : subtype.getDeclaredMethods(false)) {
                for (AnalysisMethod registeredMethod : superclassMethods) {
                    if (registeredMethod.getName().equals(subClassMethod.getName()) &&
                                    registeredMethod.getSignature().equals(subClassMethod.getSignature())) {
                        hidingMethods.add(subClassMethod);
                    }
                }
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
        try {
            RecordComponent[] recordComponents = clazz.getRecordComponents();
            if (recordComponents == null) {
                return;
            }
            for (RecordComponent recordComponent : recordComponents) {
                registerTypesForRecordComponent(recordComponent);
            }
            registeredRecordComponents.put(clazz, recordComponents);
        } catch (LinkageError le) {
            registerLinkageError(clazz, le, recordComponentsLookupExceptions);
        }
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
         * In some rare cases, the type of the field can be absent from its generic signature.
         */
        metaAccess.lookupJavaType(reflectField.getType()).registerAsReachable("Is present in a Field object reconstructed by reflection");

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
         * In some rare cases, the parameter, exception or return types of the method can be absent
         * from their generic signature.
         */
        Arrays.stream(reflectExecutable.getParameterTypes()).forEach(clazz -> metaAccess.lookupJavaType(clazz).registerAsReachable("Is present in an Executable object reconstructed by reflection"));
        Arrays.stream(reflectExecutable.getExceptionTypes()).forEach(clazz -> metaAccess.lookupJavaType(clazz).registerAsReachable("Is present in an Executable object reconstructed by reflection"));
        if (reflectExecutable instanceof Method method) {
            metaAccess.lookupJavaType(method.getReturnType()).registerAsReachable("Is present in a Method object reconstructed by reflection");
        }
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
            if (type == null || !processedTypes.computeIfAbsent(type, _ -> ConcurrentHashMap.newKeySet()).add(dimension)) {
                return;
            }
        } catch (MalformedParameterizedTypeException | TypeNotPresentException | LinkageError e) {
            /*
             * Hash code computation can trigger an exception if a type in wildcard bounds is
             * missing, in which case we cannot add `type` to `processedTypes`, but even so, we
             * still have to process it. Otherwise, we might fail to make some type reachable.
             */
        }

        if (type instanceof Class<?> clazz) {
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
            // GR-68706: this registration is marked as preserved to avoid clobbering "preserved"
            // metadata. it should use a scoped condition once they are supported.
            classForNameSupport.registerClass(AccessCondition.unconditional(), clazz, ClassLoaderFeature.getRuntimeClassLoader(clazz.getClassLoader()), true);
        } else if (type instanceof TypeVariable<?>) {
            /* Bounds are reified lazily. */
            registerTypesForGenericSignature(queryGenericInfo(((TypeVariable<?>) type)::getBounds), dimension);
        } else if (type instanceof GenericArrayType) {
            registerTypesForGenericSignature(queryGenericInfo(((GenericArrayType) type)::getGenericComponentType), dimension + 1);
        } else if (type instanceof ParameterizedType parameterizedType) {
            registerTypesForGenericSignature(queryGenericInfo(parameterizedType::getActualTypeArguments));
            registerTypesForGenericSignature(queryGenericInfo(parameterizedType::getRawType), dimension);
            registerTypesForGenericSignature(queryGenericInfo(parameterizedType::getOwnerType));
        } else if (type instanceof WildcardType wildcardType) {
            /* Bounds are reified lazily. */
            registerTypesForGenericSignature(queryGenericInfo(wildcardType::getLowerBounds), dimension);
            registerTypesForGenericSignature(queryGenericInfo(wildcardType::getUpperBounds), dimension);
        }
    }

    private void registerTypesForRecordComponent(RecordComponent recordComponent) {
        Method accessorOrNull = recordComponent.getAccessor();
        if (accessorOrNull != null) {
            register(unconditional(), true, false, accessorOrNull);
        }
        registerTypesForAnnotations(recordComponent);
        registerTypesForTypeAnnotations(recordComponent);
    }

    private void registerTypesForAnnotations(AnnotatedElement annotatedElement) {
        if (annotatedElement != null) {
            if (!filteredAnnotations.containsKey(annotatedElement)) {
                List<AnnotationValue> includedAnnotations = new ArrayList<>();
                for (AnnotationValue annotation : annotationExtractor.getDeclaredAnnotationValues(annotatedElement).values()) {
                    if (includeAnnotation(annotation)) {
                        includedAnnotations.add(annotation);
                        registerTypesForAnnotation(annotation);
                    }
                }
                filteredAnnotations.put(annotatedElement, includedAnnotations.toArray(NO_ANNOTATIONS));
            }
        }
    }

    private void registerTypesForParameterAnnotations(AnalysisMethod method) {
        if (method != null) {
            if (!filteredParameterAnnotations.containsKey(method)) {
                List<List<AnnotationValue>> parameterAnnotations = annotationExtractor.getParameterAnnotationValues(method);
                AnnotationValue[][] includedParameterAnnotations = parameterAnnotations.isEmpty() ? NO_PARAMETER_ANNOTATIONS : new AnnotationValue[parameterAnnotations.size()][];
                for (int i = 0; i < includedParameterAnnotations.length; ++i) {
                    List<AnnotationValue> annotations = parameterAnnotations.get(i);
                    List<AnnotationValue> includedAnnotations = new ArrayList<>();
                    for (AnnotationValue annotation : annotations) {
                        if (includeAnnotation(annotation)) {
                            includedAnnotations.add(annotation);
                            registerTypesForAnnotation(annotation);
                        }
                    }
                    includedParameterAnnotations[i] = includedAnnotations.toArray(NO_ANNOTATIONS);
                }
                filteredParameterAnnotations.put(method, includedParameterAnnotations);
            }
        }
    }

    private void registerTypesForTypeAnnotations(AnnotatedElement annotatedElement) {
        if (annotatedElement != null) {
            if (!filteredTypeAnnotations.containsKey(annotatedElement)) {
                List<TypeAnnotationValue> includedTypeAnnotations = new ArrayList<>();
                for (TypeAnnotationValue typeAnnotation : annotationExtractor.getTypeAnnotationValues(annotatedElement)) {
                    if (includeAnnotation(typeAnnotation.getAnnotation())) {
                        includedTypeAnnotations.add(typeAnnotation);
                        registerTypesForAnnotation(typeAnnotation.getAnnotation());
                    }
                }
                filteredTypeAnnotations.put(annotatedElement, includedTypeAnnotations.toArray(NO_TYPE_ANNOTATIONS));
            }
        }
    }

    private void registerTypesForAnnotationDefault(AnalysisMethod method) {
        Object annotationDefault = annotationExtractor.getAnnotationDefaultValue(method);
        if (annotationDefault != null) {
            registerTypesForMemberValue(annotationDefault);
        }
    }

    class IncludeAnnotation implements Consumer<Class<?>> {
        boolean answer = true;

        @Override
        public void accept(Class<?> type) {
            if (type == null || reflectivityFilter.shouldExclude(type)) {
                answer = false;
            }
        }
    }

    private boolean includeAnnotation(AnnotationValue annotationValue) {
        if (annotationValue == null) {
            return false;
        }
        IncludeAnnotation visitor = new IncludeAnnotation();
        visitTypesForAnnotation(annotationValue, visitor);
        return visitor.answer;
    }

    private void visitTypesForAnnotation(AnnotationValue annotationValue, Consumer<Class<?>> typeConsumer) {
        if (!annotationValue.isError()) {
            typeConsumer.accept(OriginalClassProvider.getJavaClass(annotationValue.getAnnotationType()));
            for (Object memberValue : annotationValue.getElements().values()) {
                visitTypesForMemberValue(memberValue, typeConsumer);
            }
        }
    }

    private static final Class<?> AnnotationTypeMismatchExceptionProxy = ReflectionUtil.lookupClass("sun.reflect.annotation.AnnotationTypeMismatchExceptionProxy");

    private void visitTypesForMemberValue(Object memberValue, Consumer<Class<?>> typeConsumer) {
        switch (memberValue) {
            case AnnotationValue av -> {
                typeConsumer.accept(OriginalClassProvider.getJavaClass(av.getAnnotationType()));
                visitTypesForAnnotation(av, typeConsumer);
            }
            case ResolvedJavaType type -> typeConsumer.accept(OriginalClassProvider.getJavaClass(type));
            case EnumElement el -> typeConsumer.accept(OriginalClassProvider.getJavaClass(el.enumType));
            case String _ -> typeConsumer.accept(String.class);
            case List<?> list -> {
                for (Object element : list) {
                    visitTypesForMemberValue(element, typeConsumer);
                }
            }
            case MissingType _ -> typeConsumer.accept(TypeNotPresentExceptionProxy.class);
            case ElementTypeMismatch _ -> typeConsumer.accept(AnnotationTypeMismatchExceptionProxy);
            case AnnotationFormatError _, Number _, Boolean _, Character _ -> {
            }
            default -> throw GraalError.shouldNotReachHere("Invalid annotation member type: " + memberValue.getClass()); // ExcludeFromJacocoGeneratedReport
        }
    }

    private void registerTypesForMemberValue(Object memberValue) {
        visitTypesForMemberValue(memberValue, this::registerType);
    }

    private void registerTypesForAnnotation(AnnotationValue annotationValue) {
        visitTypesForAnnotation(annotationValue, this::registerType);
    }

    private void registerType(Class<?> type) {
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
            /*
             * The image heap scanning does not see the actual instances, so we need to be
             * conservative and assume fields can have any value.
             */
            analysisType.registerAsInstantiated("Is used by annotation of element registered for reflection.");
            for (var f : analysisType.getInstanceFields(true)) {
                var aField = (AnalysisField) f;
                universe.getBigbang().injectFieldTypes(aField, List.of(aField.getType()), true);
            }
        }
    }

    private boolean shouldExcludeClass(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            return true; // primitives cannot be looked up by name and have no methods or fields
        }
        return reflectivityFilter.shouldExclude(clazz);
    }

    private static <T> T queryGenericInfo(Callable<T> callable) {
        try {
            return callable.call();
        } catch (MalformedParameterizedTypeException | TypeNotPresentException | LinkageError | AssertionError e) {
            /* These are rethrown at run time, so we can simply ignore them when querying. */
            return null;
        } catch (Throwable t) {
            throw VMError.shouldNotReachHere(callable.toString(), t);
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
        try {
            Method[] accessors = RecordUtils.getRecordComponentAccessorMethods(clazz);
            Set<Method> unregisteredAccessors = ConcurrentHashMap.newKeySet();
            for (Method accessor : accessors) {
                if (reflectivityFilter.shouldExclude(accessor)) {
                    return;
                }
                unregisteredAccessors.add(accessor);
            }
            pendingRecordClasses.put(clazz, unregisteredAccessors);

            AnalysisType analysisType = metaAccess.lookupJavaType(clazz);
            unregisteredAccessors.removeIf(accessor -> registeredMethods.getOrDefault(analysisType, Collections.emptyMap()).containsKey(metaAccess.lookupJavaMethod(accessor)));
            if (unregisteredAccessors.isEmpty()) {
                registerRecordComponents(clazz);
            }
        } catch (LinkageError le) {
            registerLinkageError(clazz, le, recordComponentsLookupExceptions);
        }
    }

    private void registerLinkageError(Class<?> clazz, LinkageError error, Map<Class<?>, Throwable> errorMap) {
        if (LinkAtBuildTimeSupport.singleton().linkAtBuildTime(clazz)) {
            throw error;
        } else {
            var registeredError = errorMap.computeIfAbsent(clazz, _ -> {
                universe.getHeapScanner().rescanObject(error, scanReason);
                return error;
            });
            assert registeredError.toString().equals(error.toString()) : "Attempting to replace " + registeredError + " with " + error;
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
        sealed();
        processedTypes = null;
        if (!throwMissingRegistrationErrors()) {
            pendingRecordClasses = null;
        }
        existingAllDeclaredFieldsQuery = null;
    }

    @Override
    public Map<Class<?>, Set<Class<?>>> getReflectionInnerClasses() {
        assert isSealed();
        return Collections.unmodifiableMap(innerClasses);
    }

    public int getEnabledReflectionQueries(Class<?> clazz) {
        int enabledQueries = enabledQueriesFlags.getOrDefault(clazz, 0);
        /*
         * Primitives and arrays are registered by default since they provide reflective access to
         * no members.
         */
        if (clazz.isPrimitive() || clazz.isArray()) {
            enabledQueries |= ALL_DECLARED_CLASSES_FLAG | ALL_CLASSES_FLAG | ALL_DECLARED_CONSTRUCTORS_FLAG | ALL_CONSTRUCTORS_FLAG | ALL_DECLARED_METHODS_FLAG | ALL_METHODS_FLAG |
                            ALL_DECLARED_FIELDS_FLAG | ALL_FIELDS_FLAG;
        }
        return enabledQueries;
    }

    @Override
    public Map<AnalysisType, Map<AnalysisField, ConditionalRuntimeValue<Field>>> getReflectionFields() {
        assert isSealed();
        return Collections.unmodifiableMap(registeredFields);
    }

    @Override
    public Map<AnalysisType, Map<AnalysisMethod, ConditionalRuntimeValue<Executable>>> getReflectionExecutables() {
        assert isSealed();
        return Collections.unmodifiableMap(registeredMethods);
    }

    @Override
    public Object getAccessor(AnalysisMethod method) {
        assert isSealed();
        return methodAccessors.get(method);
    }

    @Override
    public Set<ResolvedJavaField> getHidingReflectionFields() {
        assert isSealed();
        return Collections.unmodifiableSet(hidingFields);
    }

    @Override
    public Set<ResolvedJavaMethod> getHidingReflectionMethods() {
        assert isSealed();
        return Collections.unmodifiableSet(hidingMethods);
    }

    @Override
    public RecordComponent[] getRecordComponents(Class<?> type) {
        assert isSealed();
        return registeredRecordComponents.get(type);
    }

    @Override
    public void registerHeapDynamicHub(Object object, ScanReason reason) {
        DynamicHub hub = (DynamicHub) object;
        Class<?> javaClass = hub.getHostedJavaClass();
        if (heapDynamicHubs.add(hub)) {
            if (isSealed()) {
                throw new UnsupportedFeatureException("Registering new class for reflection when the image heap is already sealed: " + javaClass);
            }
            if (!reflectivityFilter.shouldExclude(javaClass)) {
                registerTypesForClass(metaAccess.lookupJavaType(javaClass), javaClass);
            }
        }
    }

    @Override
    public Set<DynamicHub> getHeapDynamicHubs() {
        assert isSealed();
        return Collections.unmodifiableSet(heapDynamicHubs);
    }

    @Override
    public void registerHeapReflectionField(Field reflectField, ScanReason reason) {
        AnalysisField analysisField = metaAccess.lookupJavaField(reflectField);
        if (heapFields.put(analysisField, reflectField) == null) {
            if (isSealed()) {
                throw new UnsupportedFeatureException("Registering new field for reflection when the image heap is already sealed: " + reflectField);
            }
            if (!reflectivityFilter.shouldExclude(reflectField)) {
                registerTypesForField(analysisField, reflectField, false);
                if (analysisField.getDeclaringClass().isAnnotation()) {
                    processAnnotationField(reflectField);
                }
            }
        }
    }

    @Override
    public void registerHeapReflectionExecutable(Executable reflectExecutable, ScanReason reason) {
        if (reflectExecutable instanceof Constructor<?> reflectConstructor && ReflectionSubstitutionSupport.singleton().isCustomSerializationConstructor(reflectConstructor)) {
            /*
             * Constructors created by Constructor.newWithAccessor are indistinguishable from an
             * equivalent constructor with a "correct" accessor, and as such could hide this
             * constructor from reflection queries. We therefore exclude such constructors from the
             * internal reflection metadata.
             */
            return;
        }
        AnalysisMethod analysisMethod = metaAccess.lookupJavaMethod(reflectExecutable);
        if (heapMethods.put(analysisMethod, reflectExecutable) == null) {
            if (isSealed()) {
                throw new UnsupportedFeatureException("Registering new method for reflection when the image heap is already sealed: " + reflectExecutable);
            }
            if (!reflectivityFilter.shouldExclude(reflectExecutable)) {
                registerTypesForMethod(analysisMethod, reflectExecutable);
                if (reflectExecutable instanceof Method && reflectExecutable.getDeclaringClass().isAnnotation()) {
                    processAnnotationMethod(false, (Method) reflectExecutable);
                }
            }
        }
    }

    @Override
    public Map<AnalysisField, Field> getHeapReflectionFields() {
        assert isSealed();
        return Collections.unmodifiableMap(heapFields);
    }

    @Override
    public Map<AnalysisMethod, Executable> getHeapReflectionExecutables() {
        assert isSealed();
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

    @Override
    public Map<Class<?>, Throwable> getRecordComponentLookupErrors() {
        return Collections.unmodifiableMap(recordComponentsLookupExceptions);
    }

    private static final AnnotationValue[] NO_ANNOTATIONS = new AnnotationValue[0];

    public AnnotationValue[] getAnnotationData(AnnotatedElement element) {
        assert isSealed();
        return filteredAnnotations.getOrDefault(element, NO_ANNOTATIONS);
    }

    private static final AnnotationValue[][] NO_PARAMETER_ANNOTATIONS = new AnnotationValue[0][0];

    public AnnotationValue[][] getParameterAnnotationData(AnalysisMethod element) {
        assert isSealed();
        return filteredParameterAnnotations.getOrDefault(element, NO_PARAMETER_ANNOTATIONS);
    }

    private static final TypeAnnotationValue[] NO_TYPE_ANNOTATIONS = new TypeAnnotationValue[0];

    public TypeAnnotationValue[] getTypeAnnotationData(AnnotatedElement element) {
        assert isSealed();
        return filteredTypeAnnotations.getOrDefault(element, NO_TYPE_ANNOTATIONS);
    }

    public Object getAnnotationDefaultData(AnnotatedElement element) {
        return annotationExtractor.getAnnotationDefaultValue(element);
    }

    @Override
    public int getReflectionMethodsCount() {
        return countConditionalElements(registeredMethods);
    }

    @Override
    public int getReflectionFieldsCount() {
        return countConditionalElements(registeredFields);
    }

    private static int countConditionalElements(Map<? extends AnalysisElement, ? extends Map<? extends AnalysisElement, ?>> conditionalElements) {
        return conditionalElements.values().stream()
                        .map(Map::size)
                        .reduce(0, Integer::sum);
    }

    public static class TestBackdoor {
        public static void registerField(ReflectionDataBuilder reflectionDataBuilder, boolean queriedOnly, Field field) {
            reflectionDataBuilder.runConditionalInAnalysisTask(unconditional(), (cnd) -> reflectionDataBuilder.registerField(cnd, queriedOnly, false, field));
        }
    }

    @AutomaticallyRegisteredImageSingleton(onlyWith = BuildingImageLayerPredicate.class)
    @SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = LayeredReflectionDataBuilder.LayeredCallbacks.class, layeredInstallationKind = Independent.class)
    public static class LayeredReflectionDataBuilder {
        public static final String METHODS = "methods";
        public static final String FIELDS = "fields";
        public static final String REFLECTION_DATA_BUILDER = "reflection data builder";
        public static final String REFLECTION_DATA_BUILDER_CLASSES = REFLECTION_DATA_BUILDER + " classes";
        /**
         * The methods registered for reflection in the previous layers. The key of the map is the
         * id of the declaring type and the set contains the method ids.
         */
        private final Map<Integer, Set<Integer>> previousLayerRegisteredMethods;
        /**
         * The fields registered for reflection in the previous layers. The key of the map is the id
         * of the declaring type and the set contains the field ids.
         */
        private final Map<Integer, Set<Integer>> previousLayerRegisteredFields;

        public LayeredReflectionDataBuilder() {
            this(Map.of(), Map.of());
        }

        private LayeredReflectionDataBuilder(Map<Integer, Set<Integer>> previousLayerRegisteredMethods, Map<Integer, Set<Integer>> previousLayerRegisteredFields) {
            this.previousLayerRegisteredMethods = previousLayerRegisteredMethods;
            this.previousLayerRegisteredFields = previousLayerRegisteredFields;
        }

        public static LayeredReflectionDataBuilder singleton() {
            return ImageSingletons.lookup(LayeredReflectionDataBuilder.class);
        }

        public boolean isMethodRegistered(AnalysisMethod analysisMethod) {
            return isElementRegistered(previousLayerRegisteredMethods, analysisMethod.getDeclaringClass(), analysisMethod.getId());
        }

        public boolean isFieldRegistered(AnalysisField analysisField) {
            return isElementRegistered(previousLayerRegisteredFields, analysisField.getDeclaringClass(), analysisField.getId());
        }

        private static boolean isElementRegistered(Map<Integer, Set<Integer>> previousLayerRegisteredElements, AnalysisType declaringClass, int elementId) {
            Set<Integer> previousLayerRegisteredElementIds = previousLayerRegisteredElements.get(declaringClass.getId());
            if (declaringClass.isInBaseLayer() && previousLayerRegisteredElementIds != null) {
                return previousLayerRegisteredElementIds.contains(elementId);
            }
            return false;
        }

        private static String getClassesKeyName(String element) {
            return REFLECTION_DATA_BUILDER_CLASSES + " " + element;
        }

        private static String getElementKeyName(String element, int typeId) {
            return REFLECTION_DATA_BUILDER + " " + element + " " + typeId;
        }

        static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {

            @Override
            public SingletonTrait getLayeredCallbacksTrait() {
                return new SingletonTrait(SingletonTraitKind.LAYERED_CALLBACKS, new SingletonLayeredCallbacks<LayeredReflectionDataBuilder>() {
                    private static <T, U> void persistRegisteredElements(ImageSingletonWriter writer, Map<AnalysisType, Map<T, U>> registeredElements, Function<T, Integer> getId, String element) {
                        List<Integer> classes = new ArrayList<>();
                        for (var entry : registeredElements.entrySet()) {
                            classes.add(entry.getKey().getId());
                            writer.writeIntList(getElementKeyName(element, entry.getKey().getId()), entry.getValue().keySet().stream().map(getId).toList());
                        }
                        writer.writeIntList(getClassesKeyName(element), classes);
                    }

                    @Override
                    public LayeredImageSingleton.PersistFlags doPersist(ImageSingletonWriter writer, LayeredReflectionDataBuilder singleton) {
                        ReflectionDataBuilder reflectionDataBuilder = (ReflectionDataBuilder) ImageSingletons.lookup(RuntimeReflectionSupport.class);
                        persistRegisteredElements(writer, reflectionDataBuilder.registeredMethods, AnalysisMethod::getId, METHODS);
                        persistRegisteredElements(writer, reflectionDataBuilder.registeredFields, AnalysisField::getId, FIELDS);
                        return LayeredImageSingleton.PersistFlags.CREATE;
                    }

                    @Override
                    public Class<? extends LayeredSingletonInstantiator<?>> getSingletonInstantiator() {
                        return SingletonInstantiator.class;
                    }
                });
            }

            static class SingletonInstantiator implements SingletonLayeredCallbacks.LayeredSingletonInstantiator<LayeredReflectionDataBuilder> {

                private static Map<Integer, Set<Integer>> loadRegisteredElements(ImageSingletonLoader loader, String element) {
                    Map<Integer, Set<Integer>> previousLayerRegisteredElements = new HashMap<>();
                    var classes = loader.readIntList(getClassesKeyName(element));
                    for (int key : classes) {
                        var elements = loader.readIntList(getElementKeyName(element, key)).stream().collect(Collectors.toUnmodifiableSet());
                        previousLayerRegisteredElements.put(key, elements);
                    }
                    return Collections.unmodifiableMap(previousLayerRegisteredElements);
                }

                @Override
                public LayeredReflectionDataBuilder createFromLoader(ImageSingletonLoader loader) {
                    var previousLayerRegisteredMethods = loadRegisteredElements(loader, METHODS);
                    var previousLayerRegisteredFields = loadRegisteredElements(loader, FIELDS);
                    return new LayeredReflectionDataBuilder(previousLayerRegisteredMethods, previousLayerRegisteredFields);
                }
            }
        }
    }
}
