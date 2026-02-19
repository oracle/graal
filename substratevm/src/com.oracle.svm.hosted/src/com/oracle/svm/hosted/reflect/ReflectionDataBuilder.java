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

import static com.oracle.svm.configure.config.ConfigurationMemberInfo.ConfigurationMemberAccessibility.ACCESSED;
import static com.oracle.svm.configure.config.ConfigurationMemberInfo.ConfigurationMemberAccessibility.NONE;
import static com.oracle.svm.configure.config.ConfigurationMemberInfo.ConfigurationMemberAccessibility.QUERIED;
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
import static org.graalvm.nativeimage.dynamicaccess.AccessCondition.unconditional;

import java.lang.annotation.AnnotationFormatError;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.dynamicaccess.AccessCondition;
import org.graalvm.nativeimage.hosted.RuntimeProxyCreation;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

import com.oracle.graal.pointsto.ObjectScanner.OtherReason;
import com.oracle.graal.pointsto.ObjectScanner.ScanReason;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisElement;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.configure.config.ConfigurationMemberInfo.ConfigurationMemberAccessibility;
import com.oracle.svm.core.configure.ConditionalRuntimeValue;
import com.oracle.svm.core.configure.RuntimeDynamicAccessMetadata;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.imagelayer.BuildingImageLayerPredicate;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.reflect.SubstrateAccessor;
import com.oracle.svm.core.reflect.target.ReflectionSubstitutionSupport;
import com.oracle.svm.hosted.ClassLoaderFeature;
import com.oracle.svm.hosted.ConditionalConfigurationRegistry;
import com.oracle.svm.hosted.DeadlockWatchdog;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.LinkAtBuildTimeSupport;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.annotation.SubstrateAnnotationExtractor;
import com.oracle.svm.hosted.substitute.SubstitutionReflectivityFilter;
import com.oracle.svm.shared.singletons.ImageSingletonLoader;
import com.oracle.svm.shared.singletons.ImageSingletonWriter;
import com.oracle.svm.shared.singletons.LayeredPersistFlags;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.LayeredCallbacksSingletonTrait;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.JVMCIReflectionUtil;
import com.oracle.svm.util.OriginalClassProvider;
import com.oracle.svm.util.OriginalFieldProvider;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.util.TypeResult;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.ElementTypeMismatch;
import jdk.graal.compiler.annotation.EnumElement;
import jdk.graal.compiler.annotation.MissingType;
import jdk.graal.compiler.annotation.TypeAnnotationValue;
import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaRecordComponent;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.annotation.Annotated;
import sun.reflect.annotation.ExceptionProxy;
import sun.reflect.annotation.TypeNotPresentExceptionProxy;

/// [ReflectionDataBuilder] manages reflection registrations in Native Image. It makes sure that classes, methods and
/// fields registered through the Feature API or JSON metadata files are available through reflective queries at run-
/// time. To do so, [ReflectionDataBuilder] operates in four phases:
/// 1. Elements are registered for reflection through one of the entry points defined in [RuntimeReflectionSupport] or
///    [ReflectionHostedSupport] (for elements found on the heap). These entry points take core reflection types as
///    arguments, as they are expected to be called from user features.
/// 2. The `register...` methods
///    ([#registerClass(AccessCondition, ConfigurationMemberAccessibility, ResolvedJavaType, boolean)], etc.) then
///    accept JVMCI types and perform their conversion to [AnalysisType] as needed. They encapsulate the registration
///    code into analysis tasks, and check whether to include the registered elements using the
///    [ReflectionDataBuilder#reflectivityFilter]. They also fill the metadata maps ([#types], [#methods] and [#fields])
///    with the necessary information.
/// 3. Private `registerTypesFor...` methods then perform the necessary registrations for the registered elements to
///    be accessible at runtime. This can include making elements reachable, rescanning objects on the heap, etc. In
///    particular, [#registerTypesForTypeQuery(AccessCondition, AnalysisType, boolean, boolean)] registers the complete
///    metadata for a given type, including fields, methods, inner types, etc. The fields and methods are not registered
///    for reflective access themselves.
/// 4. The contents of the metadata maps are queried from NativeImageCodeCache using the [ReflectionHostedSupport]
///    API. Temporary code is currently used to match the existing [ReflectionHostedSupport] API, which will eventually
///    be replaced by an interface matching the structure of [ReflectionDataBuilder] (GR-72062)
///
/// Elements can be registered on the following levels (stored in [ElementData#accessibility]):
/// * `NONE`: The element was found on the heap. In this case, we have to register types for its generic signature and annotations,
///   which are lazily created at runtime using reflection.
/// * `QUERIED`: The element can be reflectively queried at runtime, for example through [Class#forName(String)] for
///   types, [Class#getDeclaredMethod(String, Class\[\])] for methods and [Class#getDeclaredField(String)] for fields.
///   In this case, we have to be able to reconstruct the queried object at run-time, and therefore need to make sure
///   that every field value of the element is seen as reachable by the analysis.
/// * `ACCESSED`: The element can be reflectively accessed at runtime, through [Field#get(Object)],
///   [Field#set(Object, Object)], [Method#invoke(Object, Object...)] or [Constructor#newInstance(Object...)]. This
///   level is not needed for types. In this case, we need to register the actual field or method represented by the
///   element as reachable, and trigger the creation of the necessary accessors (e.g. SubstrateMethodAccessor).
public class ReflectionDataBuilder extends ConditionalConfigurationRegistry implements RuntimeReflectionSupport, ReflectionHostedSupport {
    private AnalysisMetaAccess metaAccess;
    private final SubstrateAnnotationExtractor annotationExtractor;
    private BeforeAnalysisAccessImpl analysisAccess;
    private final ClassForNameSupport classForNameSupport;
    private LayeredReflectionDataBuilder layeredReflectionDataBuilder;
    private SubstitutionReflectivityFilter reflectivityFilter;
    private ClassAccess classAccess;

    // Metadata maps
    private final Map<AnalysisType, TypeData> types = new ConcurrentHashMap<>();
    private final Map<AnalysisField, ElementData> fields = new ConcurrentHashMap<>();
    private final Map<AnalysisMethod, ElementData> methods = new ConcurrentHashMap<>();

    // Intermediate bookkeeping
    private Map<Type, Set<Integer>> processedTypes = new ConcurrentHashMap<>();

    // Annotations handling
    private final Map<Annotated, AnnotationValue[]> filteredAnnotations = new ConcurrentHashMap<>();
    private final Map<AnalysisMethod, AnnotationValue[][]> filteredParameterAnnotations = new ConcurrentHashMap<>();
    private final Map<Annotated, TypeAnnotationValue[]> filteredTypeAnnotations = new ConcurrentHashMap<>();

    // Reason tracking for manually triggered rescans
    private final ScanReason scanReason = new OtherReason("Manual rescan triggered from " + ReflectionDataBuilder.class);

    public ReflectionDataBuilder(SubstrateAnnotationExtractor annotationExtractor) {
        this.annotationExtractor = annotationExtractor;
        classForNameSupport = ClassForNameSupport.currentLayer();
    }

    /* This data is only available at the duringSetup stage */
    void init(AnalysisMetaAccess analysisMetaAccess, AnalysisUniverse analysisUniverse) {
        this.metaAccess = analysisMetaAccess;
        classAccess = new ClassAccess(analysisMetaAccess);
        setUniverse(analysisUniverse);
        setHostVM((SVMHost) analysisUniverse.hostVM());
        if (ImageLayerBuildingSupport.buildingImageLayer()) {
            layeredReflectionDataBuilder = LayeredReflectionDataBuilder.singleton();
        }
        reflectivityFilter = SubstitutionReflectivityFilter.singleton();
    }

    void setAnalysisAccess(BeforeAnalysisAccessImpl beforeAnalysisAccess) {
        this.analysisAccess = beforeAnalysisAccess;
    }

    @Override
    public void register(AccessCondition condition, boolean preserved, Class<?> clazz) {
        abortIfSealed();
        Objects.requireNonNull(clazz, () -> nullErrorMessage("class", "reflection"));
        registerClass(condition, QUERIED, GuestAccess.get().lookupType(clazz), preserved);
    }

    private void registerAllClasses(AnalysisType type, boolean preserved) {
        forAllSuperTypes(type, t -> {
            try {
                for (var innerType : t.getDeclaredTypes()) {
                    if (innerType.isPublic()) {
                        innerType.registerAsReachable("Is inner class of class registered for reflection.");
                        if (!throwMissingRegistrationErrors() && !shouldExcludeClass(innerType, QUERIED)) {
                            classForNameSupport.registerClass(unconditional(), innerType.getJavaClass(), ClassLoaderFeature.getRuntimeClassLoader(ClassAccess.getClassLoader(innerType)),
                                            preserved);
                        }
                    }
                }
            } catch (LinkageError e) {
                // LinkageError will be handled by registerAllDeclaredClasses
            }
        });
    }

    private void registerAllDeclaredClasses(AnalysisType type, boolean preserved) {
        try {
            for (var innerType : type.getDeclaredTypes()) {
                innerType.registerAsReachable("Is inner class of class registered for reflection.");
                if (!throwMissingRegistrationErrors() && !shouldExcludeClass(innerType, QUERIED)) {
                    classForNameSupport.registerClass(unconditional(), innerType.getJavaClass(), ClassLoaderFeature.getRuntimeClassLoader(ClassAccess.getClassLoader(innerType)), preserved);
                }
            }
        } catch (LinkageError e) {
            types.get(type).classLookupLinkageError = e;
        }
    }

    @Override
    public void registerUnsafeAllocation(AccessCondition condition, boolean preserved, Class<?> clazz) {
        abortIfSealed();
        Objects.requireNonNull(clazz, () -> nullErrorMessage("class", "unsafe allocation"));
        registerUnsafeAllocation(condition, preserved, GuestAccess.get().lookupType(clazz));
    }

    /* GR-72061: Add to new JVMCI internal reflection API */
    public void registerUnsafeAllocation(AccessCondition condition, boolean preserved, ResolvedJavaType type) {
        if (type.isArray() || type.isInterface() || type.isAbstract()) {
            return;
        }
        runConditionalTask(condition, cnd -> {
            AnalysisType analysisType = reflectivityFilter.getFilteredAnalysisType(type);
            if (analysisType == null) {
                return;
            }
            if (layeredReflectionDataBuilder != null && layeredReflectionDataBuilder.isTypeUnsafeAllocated(analysisType)) {
                /* GR-66387: The runtime condition should be combined across layers. */
                return;
            }
            types.compute(analysisType, (_, td) -> {
                TypeData data = td != null ? td : new TypeData();
                if (data.unsafeAllocatedDynamicAccess == null) {
                    analysisType.registerAsReachable("Is registered for unsafe allocation.");
                    analysisType.registerAsUnsafeAllocated("Is registered through ReflectionDataBuilder");
                }
                data.updateUnsafeAllocatedDynamicAccessMetadata(cnd, preserved);
                return data;
            });
        });
    }

    private void registerClass(AccessCondition condition, ConfigurationMemberAccessibility accessibility, ResolvedJavaType type, boolean preserved) {
        VMError.guarantee(accessibility != ACCESSED, "Classes can only be queried, not accessed");
        runConditionalTask(condition, cnd -> {
            AnalysisType analysisType = reflectivityFilter.getFilteredAnalysisType(type);
            if (analysisType == null || shouldExcludeClass(analysisType, accessibility)) {
                return;
            }
            types.compute(analysisType, (_, td) -> {
                TypeData typeData = td == null ? new TypeData() : td;

                ConfigurationMemberAccessibility previous = typeData.registerAs(accessibility);
                if (previous == null) {
                    registerTypesForHeapType(analysisType);
                    typeData.linkageError = linkType(analysisType);
                }

                if (accessibility == NONE) {
                    typeData.inHeap = true;
                }
                if (accessibility == QUERIED) {
                    /*
                     * We need to register the type again if the condition changes. This will go
                     * away with GR-72063
                     */
                    registerTypesForTypeQuery(cnd, analysisType, preserved, typeData.linkageError != null);
                    typeData.updateDynamicAccessMetadata(cnd, preserved);
                }
                return typeData;
            });
        });
    }

    private void registerTypesForTypeQuery(AccessCondition condition, AnalysisType type, boolean preserved, boolean linkageError) {
        type.registerAsReachable("Is registered for reflection.");
        /* GR-72063: Integrate in ReflectionDataBuilder */
        classForNameSupport.registerClass(condition, type.getJavaClass(), ClassLoaderFeature.getRuntimeClassLoader(ClassAccess.getClassLoader(type)), preserved);

        runConditionalTask(unconditional(), _ -> {
            if (!linkageError) {
                registerAllDeclaredFieldsQuery(type, preserved, QUERIED);
                registerAllFieldsQuery(type, preserved, QUERIED);
                registerAllDeclaredMethodsQuery(type);
                registerAllMethodsQuery(type);
                registerAllDeclaredConstructorsQuery(type);
                registerAllConstructorsQuery(type);
                registerRecordComponents(type);
            }
            registerAllDeclaredClasses(type, preserved);
            registerAllClasses(type, preserved);
            registerPermittedSubclasses(type);
            registerNestMembers(type);
            registerSigners(type);
        });
    }

    /**
     * The JVM links types in a lazy way, which means that a different linkage error may be thrown
     * when calling {@link Class#getDeclaredMethods()} and {@link Class#getDeclaredFields()}, for
     * example. The JVM specification however allows implementations to perform eager linking
     * (https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-5.html), which we do to limit the
     * size of the runtime metadata.
     */
    private static LinkageError linkType(AnalysisType type) {
        try {
            type.link();
        } catch (LinkageError e) {
            if (LinkAtBuildTimeSupport.singleton().linkAtBuildTime(type)) {
                throw e;
            }
            return e;
        }
        return null;
    }

    @Override
    public void registerClassLookupException(AccessCondition condition, String typeName, Throwable t) {
        abortIfSealed();
        Objects.requireNonNull(typeName, () -> nullErrorMessage("class name", "reflection lookup exception"));
        runConditionalTask(condition, (cnd) -> {
            classForNameSupport.registerExceptionForClass(cnd, typeName, t, false);
        });
    }

    @Override
    public void registerClassLookup(AccessCondition condition, boolean preserved, String typeName) {
        abortIfSealed();
        Objects.requireNonNull(typeName, () -> nullErrorMessage("class name", "reflection lookup"));
        runConditionalTask(condition, (cnd) -> {
            TypeResult<ResolvedJavaType> type = ClassAccess.typeForName(typeName);
            if (type.isPresent()) {
                registerClass(cnd, QUERIED, type.get(), preserved);
            } else if (type.getException() instanceof ClassNotFoundException) {
                classForNameSupport.registerNegativeQuery(cnd, typeName);
            } else {
                classForNameSupport.registerExceptionForClass(cnd, typeName, type.getException(), preserved);
            }
        });
    }

    private static void registerPermittedSubclasses(AnalysisType type) {
        if (type.isSealed()) {
            type.getPermittedSubclasses().forEach(permittedSubtype -> permittedSubtype.registerAsReachable("Is permitted subclass of class registered for reflection."));
        }
    }

    private void registerNestMembers(AnalysisType type) {
        classAccess.getNestMembers(type).forEach(nestMemberType -> nestMemberType.registerAsReachable("Is nest member of class registered for reflection."));
    }

    private void registerSigners(AnalysisType type) {
        Object[] signers = ClassAccess.getSigners(type);
        if (signers != null) {
            for (Object signer : signers) {
                universe.getHeapScanner().rescanObject(signer, scanReason);
            }
        }
    }

    @Override
    public void register(AccessCondition condition, boolean preserved, Executable reflectExecutable) {
        abortIfSealed();
        Objects.requireNonNull(reflectExecutable, () -> nullErrorMessage("executable", "reflection"));
        ResolvedJavaMethod method = GuestAccess.get().lookupMethod(reflectExecutable);
        /*
         * Without hiding methods, the declaring class of the method has to be registered to allow
         * individual queries at run-time.
         */
        if (throwMissingRegistrationErrors()) {
            registerMethodDeclaringType(condition, method.getDeclaringClass(), method.isConstructor());
        }
        registerMethod(condition, ACCESSED, preserved, method);
    }

    private void registerMethodDeclaringType(AccessCondition condition, ResolvedJavaType declaringType, boolean isConstructor) {
        runConditionalTask(condition, _ -> {
            AnalysisType analysisType = reflectivityFilter.getFilteredAnalysisType(declaringType);
            if (analysisType != null) {
                types.compute(analysisType, (_, td) -> {
                    TypeData data = td == null ? new TypeData() : td;
                    if (isConstructor) {
                        data.constructorsRegistered = true;
                    } else {
                        data.methodsRegistered = true;
                    }
                    return data;
                });
                if (isConstructor) {
                    registerAllDeclaredConstructorsQuery(analysisType);
                } else {
                    registerAllDeclaredMethodsQuery(analysisType);
                }
            }
        });
    }

    private static void forAllSuperTypes(AnalysisType type, Consumer<AnalysisType> callback) {
        for (AnalysisType current = type; current != null; current = current.getSuperclass()) {
            callback.accept(current);
            for (AnalysisType intf : type.getInterfaces()) {
                forAllSuperTypes(intf, callback);
            }
        }
    }

    private void registerAllMethodsQuery(AnalysisType type) {
        forAllSuperTypes(type, t -> {
            for (var method : t.getDeclaredMethods(false)) {
                if (method.isPublic()) {
                    registerMethod(unconditional(), QUERIED, false, method);
                }
            }
        });
    }

    private void registerAllDeclaredMethodsQuery(AnalysisType type) {
        for (var method : type.getDeclaredMethods(false)) {
            registerMethod(unconditional(), QUERIED, false, method);
        }
    }

    private void registerAllConstructorsQuery(AnalysisType type) {
        for (var constructor : type.getDeclaredConstructors(false)) {
            if (constructor.isPublic()) {
                registerMethod(unconditional(), QUERIED, false, constructor);
            }
        }
    }

    private void registerAllDeclaredConstructorsQuery(AnalysisType type) {
        for (var constructor : type.getDeclaredConstructors(false)) {
            registerMethod(unconditional(), QUERIED, false, constructor);
        }
    }

    private void registerMethod(AccessCondition condition, ConfigurationMemberAccessibility accessibility, boolean preserved, ResolvedJavaMethod method) {
        runConditionalTask(condition, cnd -> {
            AnalysisMethod analysisMethod = reflectivityFilter.getFilteredAnalysisMethod(method);
            if (analysisMethod == null) {
                return;
            }
            if (layeredReflectionDataBuilder != null && layeredReflectionDataBuilder.isMethodRegistered(analysisMethod)) {
                /* GR-66387: The runtime condition should be combined across layers. */
                return;
            }
            methods.compute(analysisMethod, (aMethod, md) -> {
                ElementData data = md != null ? md : new ElementData();

                ConfigurationMemberAccessibility previous = data.registerAs(accessibility);
                if (previous == null) {
                    registerTypesForMethod(aMethod);
                }
                if (accessibility == NONE) {
                    data.inHeap = true;
                }
                if (accessibility.includes(QUERIED) && (previous == null || !previous.includes(QUERIED))) {
                    if (!aMethod.isConstructor() && aMethod.getDeclaringClass().isAnnotation()) {
                        processAnnotationMethod(accessibility, aMethod);
                    }
                    if (!throwMissingRegistrationErrors()) {
                        checkHidingMethods(aMethod);
                    }
                }
                if (accessibility.includes(ACCESSED)) {
                    data.updateDynamicAccessMetadata(cnd, preserved);
                    if ((previous == null || !previous.includes(ACCESSED))) {
                        registerMethodAccessor(aMethod);
                    }
                }

                return data;
            });
        });
    }

    private void checkHidingMethods(AnalysisMethod analysisMethod) {
        AnalysisType declaringType = analysisMethod.getDeclaringClass();
        if (!registerHidingElementsReachabilityHandler(declaringType)) {
            for (AnalysisType subtype : AnalysisUniverse.reachableSubtypes(declaringType)) {
                runConditionalTask(unconditional(), _ -> checkSubtypeForOverridingMethod(analysisMethod, subtype));
            }
        }
    }

    private void checkSubtypeForOverridingMethod(AnalysisMethod supertypeMethod, AnalysisType subtype) {
        if (methods.containsKey(supertypeMethod) && methods.get(supertypeMethod).isRegisteredAs(QUERIED)) {
            for (AnalysisMethod subtypeMethod : subtype.getDeclaredMethods(false)) {
                if (supertypeMethod.getName().equals(subtypeMethod.getName()) &&
                                supertypeMethod.getSignature().equals(subtypeMethod.getSignature())) {
                    methods.compute(subtypeMethod, (_, td) -> {
                        ElementData data = td == null ? new TypeData() : td;
                        data.hiding = true;
                        return data;
                    });
                }
            }
        }
    }

    @Override
    public void register(AccessCondition condition, boolean finalIsWritable, boolean preserved, Field reflectField) {
        abortIfSealed();
        Objects.requireNonNull(reflectField, () -> nullErrorMessage("field", "reflection"));
        ResolvedJavaField field = GuestAccess.get().lookupField(reflectField);
        /*
         * Without hiding fields, the declaring class of the field has to be registered to allow
         * individual queries at run-time.
         */
        if (throwMissingRegistrationErrors()) {
            registerFieldDeclaringType(condition, field.getDeclaringClass(), preserved);
        }
        registerField(condition, ACCESSED, preserved, field);
    }

    private void registerFieldDeclaringType(AccessCondition condition, ResolvedJavaType declaringType, boolean preserved) {
        runConditionalTask(condition, _ -> {
            AnalysisType analysisType = reflectivityFilter.getFilteredAnalysisType(declaringType);
            if (analysisType != null) {
                types.compute(analysisType, (_, td) -> {
                    TypeData data = td == null ? new TypeData() : td;
                    data.fieldsRegistered = true;
                    return data;
                });
                registerAllDeclaredFieldsQuery(analysisType, preserved, QUERIED);
            }
        });
    }

    @Override
    public void registerAllFields(AccessCondition condition, boolean preserved, Class<?> clazz) {
        abortIfSealed();
        Objects.requireNonNull(clazz, () -> nullErrorMessage("class", "reflection"));
        runConditionalTask(condition, _ -> {
            AnalysisType analysisType = reflectivityFilter.getFilteredAnalysisType(GuestAccess.get().lookupType(clazz));
            if (analysisType == null) {
                return;
            }
            registerAllFieldsQuery(analysisType, preserved, ACCESSED);
        });
    }

    @Override
    public void registerAllDeclaredFields(AccessCondition condition, boolean preserved, Class<?> clazz) {
        abortIfSealed();
        Objects.requireNonNull(clazz, () -> nullErrorMessage("class", "reflection"));
        runConditionalTask(condition, _ -> {
            AnalysisType analysisType = reflectivityFilter.getFilteredAnalysisType(GuestAccess.get().lookupType(clazz));
            if (analysisType == null) {
                return;
            }
            registerAllDeclaredFieldsQuery(analysisType, preserved, ACCESSED);
        });
    }

    private void registerAllFieldsQuery(AnalysisType type, boolean preserved, ConfigurationMemberAccessibility accessibility) {
        forAllSuperTypes(type, t -> {
            JVMCIReflectionUtil.getAllFields(t).forEach(field -> {
                if (field.isPublic()) {
                    registerField(unconditional(), accessibility, preserved, field);
                }
            });
        });
    }

    private void registerAllDeclaredFieldsQuery(AnalysisType type, boolean preserved, ConfigurationMemberAccessibility accessibility) {
        JVMCIReflectionUtil.getAllFields(type).forEach(field -> registerField(unconditional(), accessibility, preserved, field));
    }

    private void registerField(AccessCondition condition, ConfigurationMemberAccessibility accessibility, boolean preserved, ResolvedJavaField field) {
        runConditionalTask(condition, cnd -> {
            AnalysisField analysisField = reflectivityFilter.getFilteredAnalysisField(field);
            if (analysisField == null) {
                return;
            }
            if (layeredReflectionDataBuilder != null && layeredReflectionDataBuilder.isFieldRegistered(analysisField)) {
                /* GR-66387: The runtime condition should be combined across layers. */
                return;
            }
            fields.compute(analysisField, (_, fd) -> {
                abortIfSealed();
                ElementData data = fd != null ? fd : new ElementData();

                ConfigurationMemberAccessibility previous = data.registerAs(accessibility);
                if (previous == null) {
                    registerTypesForField(analysisField);
                }
                if (accessibility == NONE) {
                    data.inHeap = true;
                    analysisField.registerAsUnsafeAccessed("is registered for reflection.");
                }
                if (accessibility.includes(QUERIED) && (previous == null || !previous.includes(QUERIED))) {
                    if (analysisField.getDeclaringClass().isAnnotation()) {
                        processAnnotationField(accessibility, analysisField);
                    }
                    if (!throwMissingRegistrationErrors()) {
                        checkHidingFields(analysisField);
                    }
                }
                if (accessibility.includes(ACCESSED)) {
                    data.updateDynamicAccessMetadata(cnd, preserved);
                    if (previous == null || !previous.includes(ACCESSED)) {
                        /*
                         * Reflection accessors use Unsafe, so ensure that all reflectively
                         * accessible fields are registered as unsafe-accessible.
                         */
                        analysisField.registerAsUnsafeAccessed("is registered for reflection.");
                    }
                }

                return data;
            });
        });
    }

    private void checkHidingFields(AnalysisField analysisField) {
        AnalysisType declaringType = analysisField.getDeclaringClass();
        if (!registerHidingElementsReachabilityHandler(declaringType)) {
            for (AnalysisType subtype : AnalysisUniverse.reachableSubtypes(declaringType)) {
                runConditionalTask(unconditional(), _ -> checkSubtypeForOverridingField(analysisField, subtype));
            }
        }
    }

    private void checkSubtypeForOverridingField(AnalysisField supertypeField, AnalysisType subtype) {
        if (fields.containsKey(supertypeField) && fields.get(supertypeField).isRegisteredAs(QUERIED)) {
            for (ResolvedJavaField javaField : JVMCIReflectionUtil.getAllFields(subtype)) {
                AnalysisField subtypeField = (AnalysisField) javaField;
                if (subtypeField.getName().equals(supertypeField.getName())) {
                    fields.compute(subtypeField, (_, td) -> {
                        ElementData data = td == null ? new TypeData() : td;
                        data.hiding = true;
                        return data;
                    });
                    subtypeField.getType().registerAsReachable("Is the declared type of a hiding Field used by reflection");
                }
            }
        }
    }

    @Override
    public void registerFieldLookup(AccessCondition condition, boolean preserved, Class<?> declaringClass, String fieldName) {
        runConditionalTask(condition, (cnd) -> {
            try {
                ResolvedJavaField field = JVMCIReflectionUtil.getUniqueDeclaredField(true, GuestAccess.get().lookupType(declaringClass), fieldName);
                if (field != null) {
                    registerField(cnd, ACCESSED, preserved, field);
                }
            } catch (LinkageError ignored) {
                // Field lookup errors will be handled by the declaring class registration
            }
        });
    }

    private boolean registerHidingElementsReachabilityHandler(AnalysisType type) {
        TypeData data = types.computeIfAbsent(type, _ -> new TypeData());
        if (!data.reachabilityHandlerRegistered) {
            data.reachabilityHandlerRegistered = true;
            analysisAccess.registerSubtypeReachabilityHandler((_, subtype) -> runConditionalTask(unconditional(), _ -> checkSubtypeForOverridingElements(type, metaAccess.lookupJavaType(subtype))),
                            type.getJavaClass());
            return true;
        } else {
            return false;
        }
    }

    private void checkSubtypeForOverridingElements(AnalysisType declaringType, AnalysisType subtype) {
        /* All fields and methods are already registered, no need for hiding elements */
        if (!types.containsKey(subtype) || !types.get(subtype).isRegisteredAs(QUERIED)) {
            DeadlockWatchdog.singleton().recordActivity();
            try {
                for (AnalysisMethod supertypeMethod : declaringType.getDeclaredMethods(false)) {
                    checkSubtypeForOverridingMethod(supertypeMethod, subtype);
                }
            } catch (UnsupportedFeatureException | LinkageError e) {
                /*
                 * A method that is not supposed to end up in the image is considered as being
                 * absent for reflection purposes.
                 */
            }
            try {
                for (ResolvedJavaField supertypeField : JVMCIReflectionUtil.getAllFields(declaringType)) {
                    checkSubtypeForOverridingField((AnalysisField) supertypeField, subtype);
                }
            } catch (UnsupportedFeatureException | LinkageError e) {
                /*
                 * A field that is not supposed to end up in the image is considered as being absent
                 * for reflection purposes.
                 */
            }
        }
    }

    /*
     * Proxy classes for annotations present the annotation default methods and fields as their own.
     */
    private void processAnnotationMethod(ConfigurationMemberAccessibility accessibility, AnalysisMethod method) {
        AnalysisType annotationType = method.getDeclaringClass();
        AnalysisType proxyType = classAccess.getProxyType(annotationType);
        AnalysisMethod proxyMethod = proxyType != null ? proxyType.findMethod(method.getName(), method.getSignature()) : null;
        if (proxyMethod != null) {
            registerMethod(unconditional(), accessibility, false, proxyMethod);
        }
    }

    private void processAnnotationField(ConfigurationMemberAccessibility accessibility, AnalysisField field) {
        AnalysisType annotationType = field.getDeclaringClass();
        AnalysisType proxyType = classAccess.getProxyType(annotationType);
        AnalysisField proxyField = proxyType != null ? universe.lookup(JVMCIReflectionUtil.getUniqueDeclaredField(true, proxyType, field.getName())) : null;
        if (proxyField != null) {
            registerField(unconditional(), accessibility, false, proxyField);
        }
    }

    private void registerTypesForHeapType(AnalysisType analysisType) {
        /*
         * The generic signature is parsed at run time, so we need to make all the types necessary
         * for parsing also available at run time.
         */
        registerTypesForGenericSignature(ClassAccess.getTypeParameters(analysisType));
        registerTypesForGenericSignature(ClassAccess.getGenericSuperclass(analysisType));
        registerTypesForGenericSignature(ClassAccess.getGenericInterfaces(analysisType));

        registerTypesForEnclosingMethodInfo(analysisType);
        registerTypesForAnnotations(analysisType);
        registerTypesForTypeAnnotations(analysisType);
    }

    private void registerRecordComponents(AnalysisType type) {
        List<? extends ResolvedJavaRecordComponent> recordComponents = type.getRecordComponents();
        if (recordComponents != null) {
            for (ResolvedJavaRecordComponent recordComponent : recordComponents) {
                registerTypesForRecordComponent(recordComponent);
            }
        }
    }

    private void registerTypesForEnclosingMethodInfo(AnalysisType type) {
        try {
            AnalysisType enclosingType = type.getEnclosingType();
            if (enclosingType != null) {
                enclosingType.registerAsReachable("Enclosing class of a type registered for reflection");
            }
            AnalysisMethod enclosingMethod;
            try {
                enclosingMethod = type.getEnclosingMethod();
            } catch (UnsupportedFeatureException | TypeNotPresentException | LinkageError | InternalError e) {
                enclosingMethod = null;
            }
            if (enclosingMethod != null) {
                /* Enclosing method lookup searches the declared methods of the enclosing class */
                registerMethod(unconditional(), QUERIED, false, enclosingMethod);
            }
        } catch (LinkageError e) {
            /*
             * Linkage error will be processed when querying the method through its declaring class
             */
        }
    }

    private void registerTypesForField(AnalysisField analysisField) {
        /*
         * In some rare cases, the type of the field can be absent from its generic signature.
         */
        analysisField.getType().registerAsReachable("Is present in a Field object reconstructed by reflection");

        /*
         * The generic signature is parsed at run time, so we need to make all the types necessary
         * for parsing also available at run time.
         */
        registerTypesForGenericSignature(ClassAccess.getGenericType(analysisField));

        /*
         * Enable runtime instantiation of annotations
         */
        registerTypesForAnnotations(analysisField);
        registerTypesForTypeAnnotations(analysisField);
    }

    private void registerTypesForMethod(AnalysisMethod analysisMethod) {
        /*
         * In some rare cases, the parameter, exception or return types of the method can be absent
         * from their generic signature.
         */
        analysisMethod.getSignature().toParameterList(null).forEach(type -> type.registerAsReachable("Is present in an Executable object reconstructed by reflection"));
        classAccess.getExceptionTypes(analysisMethod).forEach(type -> type.registerAsReachable("Is present in an Executable object reconstructed by reflection"));
        if (!analysisMethod.isConstructor()) {
            analysisMethod.getSignature().getReturnType().registerAsReachable("Is present in a Method object reconstructed by reflection");
        }

        /*
         * The generic signature is parsed at run time, so we need to make all the types necessary
         * for parsing also available at run time.
         */
        registerTypesForGenericSignature(ClassAccess.getTypeParameters(analysisMethod));
        registerTypesForGenericSignature(ClassAccess.getGenericParameterTypes(analysisMethod));
        registerTypesForGenericSignature(ClassAccess.getGenericExceptionTypes(analysisMethod));
        if (!analysisMethod.isConstructor()) {
            registerTypesForGenericSignature(ClassAccess.getGenericReturnType(analysisMethod));
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

    private void registerMethodAccessor(AnalysisMethod method) {
        SubstrateAccessor accessor = ImageSingletons.lookup(ReflectionFeature.class).getOrCreateAccessor(method.getJavaMethod());
        universe.getHeapScanner().rescanObject(accessor, scanReason);
    }

    private void registerTypesForGenericSignature(Type[] genericTypes) {
        registerTypesForGenericSignature(genericTypes, 0);
    }

    private void registerTypesForGenericSignature(Type[] genericTypes, int dimension) {
        if (genericTypes != null) {
            for (Type type : genericTypes) {
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
            AnalysisType analysisType = metaAccess.lookupJavaType(clazz);
            if (analysisType == null || shouldExcludeClass(analysisType, QUERIED)) {
                return;
            }

            if (dimension > 0) {
                /*
                 * We only need to register the array type here, since it is the one that gets
                 * stored in the heap. The component type will be registered elsewhere if needed.
                 */
                analysisType.getArrayClass(dimension).registerAsReachable("Is used by generic signature of element registered for reflection.");
            }

            /*
             * Reflection signature parsing will try to instantiate classes via Class.forName().
             */
            // GR-68706: this registration is marked as preserved to avoid clobbering "preserved"
            // metadata. it should use a scoped condition once they are supported.
            classForNameSupport.registerClass(unconditional(), clazz, ClassLoaderFeature.getRuntimeClassLoader(ClassAccess.getClassLoader(analysisType)), true);
        } else if (type instanceof TypeVariable<?>) {
            /* Bounds are reified lazily. */
            registerTypesForGenericSignature(ClassAccess.queryGenericInfo(((TypeVariable<?>) type)::getBounds), dimension);
        } else if (type instanceof GenericArrayType) {
            registerTypesForGenericSignature(ClassAccess.queryGenericInfo(((GenericArrayType) type)::getGenericComponentType), dimension + 1);
        } else if (type instanceof ParameterizedType parameterizedType) {
            registerTypesForGenericSignature(ClassAccess.queryGenericInfo(parameterizedType::getActualTypeArguments));
            registerTypesForGenericSignature(ClassAccess.queryGenericInfo(parameterizedType::getRawType), dimension);
            registerTypesForGenericSignature(ClassAccess.queryGenericInfo(parameterizedType::getOwnerType));
        } else if (type instanceof WildcardType wildcardType) {
            /* Bounds are reified lazily. */
            registerTypesForGenericSignature(ClassAccess.queryGenericInfo(wildcardType::getLowerBounds), dimension);
            registerTypesForGenericSignature(ClassAccess.queryGenericInfo(wildcardType::getUpperBounds), dimension);
        }
    }

    private void registerTypesForRecordComponent(ResolvedJavaRecordComponent recordComponent) {
        universe.lookup(recordComponent.getType()).registerAsReachable("Type of a record component registered for reflection");
        registerTypesForAnnotations(recordComponent);
        registerTypesForTypeAnnotations(recordComponent);
    }

    private void registerTypesForAnnotations(Annotated annotated) {
        if (annotated != null) {
            filteredAnnotations.computeIfAbsent(annotated, (element) -> {
                List<AnnotationValue> includedAnnotations = new ArrayList<>();
                for (AnnotationValue annotation : annotationExtractor.getDeclaredAnnotationValues(element).values()) {
                    if (includeAnnotation(annotation)) {
                        includedAnnotations.add(annotation);
                        registerTypesForAnnotation(annotation);
                    }
                }
                return includedAnnotations.toArray(NO_ANNOTATIONS);
            });
        }
    }

    private void registerTypesForParameterAnnotations(AnalysisMethod analysisMethod) {
        if (analysisMethod != null) {
            filteredParameterAnnotations.computeIfAbsent(analysisMethod, (method) -> {
                List<List<AnnotationValue>> parameterAnnotations = annotationExtractor.getParameterAnnotationValues(method);
                AnnotationValue[][] includedParameterAnnotations = NO_PARAMETER_ANNOTATIONS;
                if (parameterAnnotations != null) {
                    includedParameterAnnotations = new AnnotationValue[parameterAnnotations.size()][];
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
                }
                return includedParameterAnnotations;
            });
        }
    }

    private void registerTypesForTypeAnnotations(Annotated annotated) {
        if (annotated != null) {
            filteredTypeAnnotations.computeIfAbsent(annotated, (element) -> {
                List<TypeAnnotationValue> includedTypeAnnotations = new ArrayList<>();
                for (TypeAnnotationValue typeAnnotation : annotationExtractor.getTypeAnnotationValues(element)) {
                    if (includeAnnotation(typeAnnotation.getAnnotation())) {
                        includedTypeAnnotations.add(typeAnnotation);
                        registerTypesForAnnotation(typeAnnotation.getAnnotation());
                    }
                }
                return includedTypeAnnotations.toArray(NO_TYPE_ANNOTATIONS);
            });
        }
    }

    private void registerTypesForAnnotationDefault(AnalysisMethod method) {
        Object annotationDefault = annotationExtractor.getAnnotationDefaultValue(method);
        if (annotationDefault != null) {
            registerTypesForMemberValue(annotationDefault);
        }
    }

    class IncludeAnnotation implements Consumer<ResolvedJavaType> {
        boolean answer = true;

        @Override
        public void accept(ResolvedJavaType type) {
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

    private void visitTypesForAnnotation(AnnotationValue annotationValue, Consumer<ResolvedJavaType> typeConsumer) {
        if (!annotationValue.isError()) {
            typeConsumer.accept(annotationValue.getAnnotationType());
            for (Object memberValue : annotationValue.getElements().values()) {
                visitTypesForMemberValue(memberValue, typeConsumer);
            }
        }
    }

    private static final ResolvedJavaType AnnotationTypeMismatchExceptionProxy;

    static {
        Class<?> cls = ReflectionUtil.lookupClass("sun.reflect.annotation.AnnotationTypeMismatchExceptionProxy");
        AnnotationTypeMismatchExceptionProxy = GuestAccess.get().lookupType(cls);
    }

    private void visitTypesForMemberValue(Object memberValue, Consumer<ResolvedJavaType> typeConsumer) {
        switch (memberValue) {
            case AnnotationValue av -> {
                typeConsumer.accept(av.getAnnotationType());
                visitTypesForAnnotation(av, typeConsumer);
            }
            case ResolvedJavaType type -> typeConsumer.accept(type);
            case EnumElement el -> typeConsumer.accept(el.enumType);
            case String _ -> typeConsumer.accept(GuestAccess.get().lookupType(String.class));
            case List<?> list -> {
                for (Object element : list) {
                    visitTypesForMemberValue(element, typeConsumer);
                }
            }
            case MissingType _ -> typeConsumer.accept(GuestAccess.get().lookupType(TypeNotPresentExceptionProxy.class));
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

    private void registerType(ResolvedJavaType type) {
        AnalysisType analysisType = universe.lookup(type);
        analysisType.registerAsReachable("Is used by annotation of element registered for reflection.");
        /*
         * Annotations require their proxy class and themselves to be reflectively accessible to
         * build their AnnotationType
         */
        if (type.isAnnotation()) {
            RuntimeProxyCreation.register(OriginalClassProvider.getJavaClass(type));
            registerClass(unconditional(), QUERIED, analysisType, false);
        }
        /*
         * Exception proxies are stored as-is in the image heap
         */
        if (GuestAccess.get().lookupType(ExceptionProxy.class).isAssignableFrom(type)) {
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

    private boolean shouldExcludeClass(AnalysisType type, ConfigurationMemberAccessibility accessibility) {
        if (type.isPrimitive() && accessibility.includes(QUERIED)) {
            return true; // primitives cannot be looked up by name and have no methods or fields
        }
        return reflectivityFilter.shouldExclude(type);
    }

    void afterAnalysis() {
        seal();
        processedTypes = null;
    }

    @Override
    public Map<Class<?>, Set<Class<?>>> getReflectionInnerClasses() {
        assert isSealed();
        Map<Class<?>, Set<Class<?>>> innerClasses = new HashMap<>();
        types.forEach((type, typeData) -> {
            if (typeData.isRegisteredAs(QUERIED)) {
                try {
                    var innerTypes = innerClasses.computeIfAbsent(type.getJavaClass(), _ -> new HashSet<>());
                    for (var innerType : type.getDeclaredTypes()) {
                        innerTypes.add(innerType.getJavaClass());
                    }
                    forAllSuperTypes(type, t -> {
                        var superTypeInnerTypes = innerClasses.computeIfAbsent(t.getJavaClass(), _ -> new HashSet<>());
                        for (var innerType : t.getDeclaredTypes()) {
                            if (innerType.isPublic()) {
                                superTypeInnerTypes.add(innerType.getJavaClass());
                            }
                        }
                    });
                    if (!throwMissingRegistrationErrors()) {
                        AnalysisType enclosingType = type.getEnclosingType();
                        if (enclosingType != null) {
                            innerClasses.computeIfAbsent(enclosingType.getJavaClass(), _ -> new HashSet<>()).add(type.getJavaClass());
                        }
                    }
                } catch (LinkageError ignored) {
                    // The linkage error is handled in registerAllDeclaredClasses
                }
            }
        });
        return Collections.unmodifiableMap(innerClasses);
    }

    public int getEnabledReflectionQueries(Class<?> clazz) {
        /*
         * Primitives and arrays are registered by default since they provide reflective access to
         * no members.
         */
        AnalysisType type = metaAccess.lookupJavaType(clazz);
        if (type.isPrimitive() || type.isArray() || types.get(type).isRegisteredAs(QUERIED)) {
            return ALL_DECLARED_CLASSES_FLAG | ALL_CLASSES_FLAG | ALL_DECLARED_CONSTRUCTORS_FLAG | ALL_CONSTRUCTORS_FLAG | ALL_DECLARED_METHODS_FLAG | ALL_METHODS_FLAG |
                            ALL_DECLARED_FIELDS_FLAG | ALL_FIELDS_FLAG | ALL_RECORD_COMPONENTS_FLAG | ALL_PERMITTED_SUBCLASSES_FLAG | ALL_NEST_MEMBERS_FLAG | ALL_SIGNERS_FLAG;
        } else {
            int flags = 0;
            if (types.get(type).methodsRegistered) {
                flags |= ALL_DECLARED_METHODS_FLAG | ALL_METHODS_FLAG;
            }
            if (types.get(type).constructorsRegistered) {
                flags |= ALL_DECLARED_CONSTRUCTORS_FLAG | ALL_CONSTRUCTORS_FLAG;
            }
            if (types.get(type).fieldsRegistered) {
                flags |= ALL_DECLARED_FIELDS_FLAG | ALL_FIELDS_FLAG;
            }
            return flags;
        }
    }

    @Override
    public Map<AnalysisType, Map<AnalysisField, ConditionalRuntimeValue<Field>>> getReflectionFields() {
        assert isSealed();
        Map<AnalysisType, Map<AnalysisField, ConditionalRuntimeValue<Field>>> registeredFields = new HashMap<>();
        fields.forEach((field, data) -> {
            if (data.isRegisteredAs(QUERIED)) {
                if (ClassAccess.getJavaField(field) != null) {
                    registeredFields.computeIfAbsent(field.getDeclaringClass(), _ -> new HashMap<>()).put(field,
                                    new ConditionalRuntimeValue<>(data.getDynamicAccessMetadata(), ClassAccess.getJavaField(field)));
                }
            }
        });
        return Collections.unmodifiableMap(registeredFields);
    }

    @Override
    public Map<AnalysisType, Map<AnalysisMethod, ConditionalRuntimeValue<Executable>>> getReflectionExecutables() {
        assert isSealed();
        Map<AnalysisType, Map<AnalysisMethod, ConditionalRuntimeValue<Executable>>> registeredMethods = new HashMap<>();
        methods.forEach((method, data) -> {
            if (data.isRegisteredAs(QUERIED)) {
                if (method.getJavaMethod() != null) {
                    registeredMethods.computeIfAbsent(method.getDeclaringClass(), _ -> new HashMap<>()).put(method,
                                    new ConditionalRuntimeValue<>(data.getDynamicAccessMetadata(), method.getJavaMethod()));
                }
            }
        });
        return Collections.unmodifiableMap(registeredMethods);
    }

    @Override
    public Object getAccessor(AnalysisMethod method) {
        assert isSealed();
        if (!methods.containsKey(method) || !methods.get(method).isRegisteredAs(ACCESSED)) {
            return null;
        }
        return ImageSingletons.lookup(ReflectionFeature.class).getOrCreateAccessor(method.getJavaMethod());
    }

    @Override
    public Set<ResolvedJavaField> getHidingReflectionFields() {
        assert isSealed();
        Set<ResolvedJavaField> hidingFields = new HashSet<>();
        fields.forEach((field, data) -> {
            if (data.hiding) {
                hidingFields.add(field);
            }
        });
        return Collections.unmodifiableSet(hidingFields);
    }

    @Override
    public Set<ResolvedJavaMethod> getHidingReflectionMethods() {
        assert isSealed();
        Set<ResolvedJavaMethod> hidingMethods = new HashSet<>();
        methods.forEach((method, data) -> {
            if (data.hiding) {
                hidingMethods.add(method);
            }
        });
        return Collections.unmodifiableSet(hidingMethods);
    }

    @Override
    public RecordComponent[] getRecordComponents(Class<?> clazz) {
        assert isSealed();
        return types.get(metaAccess.lookupJavaType(clazz)).isRegisteredAs(QUERIED) ? ClassAccess.getRecordComponents(clazz) : null;
    }

    public RuntimeDynamicAccessMetadata getUnsafeAllocationMetadata(Class<?> clazz) {
        return types.get(metaAccess.lookupJavaType(clazz)).unsafeAllocatedDynamicAccess;
    }

    @Override
    public void registerHeapDynamicHub(Object object, ScanReason reason) {
        Class<?> clazz = ((DynamicHub) object).getHostedJavaClass();
        registerClass(unconditional(), NONE, GuestAccess.get().lookupType(clazz), false);
    }

    @Override
    public Set<DynamicHub> getHeapDynamicHubs() {
        assert isSealed();
        Set<DynamicHub> heapDynamicHubs = new HashSet<>();
        types.forEach((type, data) -> {
            if (data.inHeap) {
                heapDynamicHubs.add(getHostVM().dynamicHub(type));
            }
        });
        return Collections.unmodifiableSet(heapDynamicHubs);
    }

    @Override
    public void registerHeapReflectionField(Field reflectField, ScanReason reason) {
        registerField(unconditional(), NONE, false, GuestAccess.get().lookupField(reflectField));
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
        registerMethod(unconditional(), NONE, false, GuestAccess.get().lookupMethod(reflectExecutable));
    }

    @Override
    public Map<AnalysisField, Field> getHeapReflectionFields() {
        assert isSealed();
        Map<AnalysisField, Field> heapFields = new HashMap<>();
        fields.forEach((field, data) -> {
            if (data.inHeap) {
                heapFields.put(field, ClassAccess.getJavaField(field));
            }
        });
        return Collections.unmodifiableMap(heapFields);
    }

    @Override
    public Map<AnalysisMethod, Executable> getHeapReflectionExecutables() {
        assert isSealed();
        Map<AnalysisMethod, Executable> heapMethods = new HashMap<>();
        methods.forEach((method, data) -> {
            if (data.inHeap) {
                heapMethods.put(method, method.getJavaMethod());
            }
        });
        return Collections.unmodifiableMap(heapMethods);
    }

    @Override
    public Map<AnalysisType, Set<String>> getNegativeFieldQueries() {
        return Collections.emptyMap();
    }

    @Override
    public Map<AnalysisType, Set<AnalysisMethod.Signature>> getNegativeMethodQueries() {
        return Collections.emptyMap();
    }

    @Override
    public Map<AnalysisType, Set<AnalysisType[]>> getNegativeConstructorQueries() {
        return Collections.emptyMap();
    }

    @Override
    public Map<Class<?>, Throwable> getClassLookupErrors() {
        Map<Class<?>, Throwable> classLookupExceptions = new HashMap<>();
        types.forEach((type, data) -> {
            if (data.classLookupLinkageError != null) {
                classLookupExceptions.put(type.getJavaClass(), data.classLookupLinkageError);
            }
        });
        return Collections.unmodifiableMap(classLookupExceptions);
    }

    @Override
    public Map<Class<?>, Throwable> getFieldLookupErrors() {
        Map<Class<?>, Throwable> fieldLookupExceptions = new HashMap<>();
        types.forEach((type, data) -> {
            if (data.linkageError != null) {
                fieldLookupExceptions.put(type.getJavaClass(), data.linkageError);
            }
        });
        return Collections.unmodifiableMap(fieldLookupExceptions);
    }

    @Override
    public Map<Class<?>, Throwable> getMethodLookupErrors() {
        Map<Class<?>, Throwable> methodLookupExceptions = new HashMap<>();
        types.forEach((type, data) -> {
            if (data.linkageError != null) {
                methodLookupExceptions.put(type.getJavaClass(), data.linkageError);
            }
        });
        return Collections.unmodifiableMap(methodLookupExceptions);
    }

    @Override
    public Map<Class<?>, Throwable> getConstructorLookupErrors() {
        Map<Class<?>, Throwable> constructorLookupExceptions = new HashMap<>();
        types.forEach((type, data) -> {
            if (data.linkageError != null) {
                constructorLookupExceptions.put(type.getJavaClass(), data.linkageError);
            }
        });
        return Collections.unmodifiableMap(constructorLookupExceptions);
    }

    @Override
    public Map<Class<?>, Throwable> getRecordComponentLookupErrors() {
        Map<Class<?>, Throwable> recordComponentLookupExceptions = new HashMap<>();
        types.forEach((type, data) -> {
            if (data.linkageError != null) {
                recordComponentLookupExceptions.put(type.getJavaClass(), data.linkageError);
            }
        });
        return Collections.unmodifiableMap(recordComponentLookupExceptions);
    }

    private static final AnnotationValue[] NO_ANNOTATIONS = new AnnotationValue[0];

    public AnnotationValue[] getAnnotationData(Annotated element) {
        assert isSealed();
        return filteredAnnotations.getOrDefault(element, NO_ANNOTATIONS);
    }

    private static final AnnotationValue[][] NO_PARAMETER_ANNOTATIONS = new AnnotationValue[0][0];

    public AnnotationValue[][] getParameterAnnotationData(AnalysisMethod element) {
        assert isSealed();
        return filteredParameterAnnotations.getOrDefault(element, NO_PARAMETER_ANNOTATIONS);
    }

    private static final TypeAnnotationValue[] NO_TYPE_ANNOTATIONS = new TypeAnnotationValue[0];

    public TypeAnnotationValue[] getTypeAnnotationData(Annotated element) {
        assert isSealed();
        return filteredTypeAnnotations.getOrDefault(element, NO_TYPE_ANNOTATIONS);
    }

    public Object getAnnotationDefaultData(AnalysisMethod element) {
        return annotationExtractor.getAnnotationDefaultValue(element);
    }

    @Override
    public int getReflectionMethodsCount() {
        return countElements(methods);
    }

    @Override
    public int getReflectionFieldsCount() {
        return countElements(fields);
    }

    private static int countElements(Map<? extends AnalysisElement, ElementData> elements) {
        return (int) elements.values().stream()
                        .filter(data -> data.isRegisteredAs(ACCESSED))
                        .count();
    }

    public static class TestBackdoor {
        public static void registerField(ReflectionDataBuilder reflectionDataBuilder, ConfigurationMemberAccessibility accessibility, Field field) {
            reflectionDataBuilder.registerField(unconditional(), accessibility, false, GuestAccess.get().lookupField(field));
        }
    }

    private static final class TypeData extends ElementData {
        RuntimeDynamicAccessMetadata unsafeAllocatedDynamicAccess = null;
        /*
         * These flags are set to true when a class is not registered for reflection, but its
         * methods or fields are, to ensure that individual queries on the type return the correct
         * answer.
         */
        private boolean methodsRegistered = false;
        private boolean constructorsRegistered = false;
        private boolean fieldsRegistered = false;
        private boolean reachabilityHandlerRegistered = false;
        /*
         * Linkage errors caught when registering class metadata need to be stored in the image and
         * rethrown at runtime.
         */
        private LinkageError linkageError = null;
        private LinkageError classLookupLinkageError = null;

        void updateUnsafeAllocatedDynamicAccessMetadata(AccessCondition condition, boolean preserved) {
            if (unsafeAllocatedDynamicAccess == null) {
                unsafeAllocatedDynamicAccess = RuntimeDynamicAccessMetadata.emptySet(preserved);
            }
            updateDynamicAccessMetadata(unsafeAllocatedDynamicAccess, condition, preserved);
        }
    }

    private static class ElementData {
        private ConfigurationMemberAccessibility accessibility = null;
        private RuntimeDynamicAccessMetadata dynamicAccess = null;
        boolean inHeap = false;
        boolean hiding = false;

        ConfigurationMemberAccessibility registerAs(ConfigurationMemberAccessibility newAccessibility) {
            ConfigurationMemberAccessibility previous = accessibility;
            if (previous == null || !previous.includes(newAccessibility)) {
                accessibility = newAccessibility;
            }
            return previous;
        }

        boolean isRegisteredAs(ConfigurationMemberAccessibility target) {
            return accessibility != null && accessibility.includes(target);
        }

        void updateDynamicAccessMetadata(AccessCondition condition, boolean preserved) {
            if (dynamicAccess == null) {
                dynamicAccess = RuntimeDynamicAccessMetadata.emptySet(preserved);
            }
            updateDynamicAccessMetadata(dynamicAccess, condition, preserved);
        }

        static void updateDynamicAccessMetadata(RuntimeDynamicAccessMetadata metadata, AccessCondition condition, boolean preserved) {
            metadata.addCondition(condition);
            if (!preserved) {
                metadata.setNotPreserved();
            }
        }

        RuntimeDynamicAccessMetadata getDynamicAccessMetadata() {
            VMError.guarantee((dynamicAccess == null) == (accessibility == QUERIED), "Dynamic access metadata should only be present on accessed elements");
            return dynamicAccess != null ? dynamicAccess : RuntimeDynamicAccessMetadata.emptySet(false);
        }
    }

    /// This class encapsulates the remaining accesses to core reflection performed by
    /// [ReflectionDataBuilder].
    /// This class should eventually disappear once GR-72109 is unblocked.
    private record ClassAccess(AnalysisMetaAccess metaAccess) {

        private Collection<AnalysisType> filterClasses(Class<?>[] classes) {
            Set<AnalysisType> analysisTypes = new HashSet<>();
            for (Class<?> clazz : classes) {
                try {
                    analysisTypes.add(metaAccess.lookupJavaType(clazz));
                } catch (UnsupportedFeatureException e) {
                    // ignore
                }
            }
            return analysisTypes;
        }

        public static TypeResult<ResolvedJavaType> typeForName(String typeName) {
            try {
                Class<?> cls = Class.forName(typeName, false, ClassLoader.getSystemClassLoader());
                return TypeResult.forType(typeName, GuestAccess.get().lookupType(cls));
            } catch (Throwable e) {
                return TypeResult.forException(typeName, e);
            }
        }

        public static ClassLoader getClassLoader(AnalysisType type) {
            return type.getJavaClass().getClassLoader();
        }

        public static RecordComponent[] getRecordComponents(Class<?> clazz) {
            return clazz.getRecordComponents();
        }

        public Collection<AnalysisType> getNestMembers(AnalysisType type) {
            Class<?> javaClass = type.getJavaClass();
            return javaClass != null ? filterClasses(javaClass.getNestMembers()) : Collections.singleton(type);
        }

        public static Object[] getSigners(AnalysisType type) {
            Class<?> javaClass = type.getJavaClass();
            return javaClass != null ? javaClass.getSigners() : null;
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

        public static Type[] getTypeParameters(AnalysisType type) {
            Class<?> javaClass = type.getJavaClass();
            return javaClass != null ? queryGenericInfo(javaClass::getTypeParameters) : null;
        }

        public static Type getGenericSuperclass(AnalysisType type) {
            Class<?> javaClass = type.getJavaClass();
            return javaClass != null ? queryGenericInfo(javaClass::getGenericSuperclass) : null;
        }

        public static Type[] getGenericInterfaces(AnalysisType type) {
            Class<?> javaClass = type.getJavaClass();
            return javaClass != null ? queryGenericInfo(javaClass::getGenericInterfaces) : null;
        }

        public static Type getGenericType(AnalysisField field) {
            Field javaField = getJavaField(field);
            return javaField != null ? queryGenericInfo(javaField::getGenericType) : null;
        }

        public Collection<AnalysisType> getExceptionTypes(AnalysisMethod method) {
            Executable reflectMethod = method.getJavaMethod();
            return reflectMethod != null ? filterClasses(method.getJavaMethod().getExceptionTypes()) : Collections.emptyList();
        }

        public static Type[] getTypeParameters(AnalysisMethod method) {
            Executable reflectMethod = method.getJavaMethod();
            return reflectMethod != null ? queryGenericInfo(reflectMethod::getTypeParameters) : null;
        }

        public static Type[] getGenericParameterTypes(AnalysisMethod method) {
            Executable reflectMethod = method.getJavaMethod();
            return reflectMethod != null ? queryGenericInfo(reflectMethod::getGenericParameterTypes) : null;
        }

        public static Type[] getGenericExceptionTypes(AnalysisMethod method) {
            Executable reflectMethod = method.getJavaMethod();
            return reflectMethod != null ? queryGenericInfo(reflectMethod::getGenericParameterTypes) : null;
        }

        public static Type getGenericReturnType(AnalysisMethod method) {
            Method reflectMethod = (Method) method.getJavaMethod();
            return reflectMethod != null ? queryGenericInfo(reflectMethod::getGenericReturnType) : null;
        }

        public static Field getJavaField(AnalysisField field) {
            return OriginalFieldProvider.getJavaField(field);
        }

        @SuppressWarnings("deprecation")
        public AnalysisType getProxyType(AnalysisType intf) {
            try {
                return metaAccess.lookupJavaType(Proxy.getProxyClass(getClassLoader(intf), intf.getJavaClass()));
            } catch (LinkageError e) {
                return null;
            }
        }
    }

    @AutomaticallyRegisteredImageSingleton(onlyWith = BuildingImageLayerPredicate.class)
    @SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = LayeredReflectionDataBuilder.LayeredCallbacks.class)
    public static class LayeredReflectionDataBuilder {
        public static final String METHODS = "methods";
        public static final String FIELDS = "fields";
        public static final String UNSAFE_ALLOCATED_TYPES = "unsafe allocated types";
        public static final String REFLECTION_DATA_BUILDER = "reflection data builder";
        /**
         * The methods registered for reflection in the previous layers. The set contains the method
         * ids.
         */
        private final Set<Integer> previousLayerRegisteredMethods;
        /**
         * The fields registered for reflection in the previous layers. The set contains the field
         * ids.
         */
        private final Set<Integer> previousLayerRegisteredFields;
        /**
         * The types registered for unsafe allocation in previous layers. The set contains the type
         * ids.
         */
        private final Set<Integer> previousLayerUnsafe;

        public LayeredReflectionDataBuilder() {
            this(Set.of(), Set.of(), Set.of());
        }

        private LayeredReflectionDataBuilder(Set<Integer> previousLayerRegisteredMethods, Set<Integer> previousLayerRegisteredFields, Set<Integer> previousLayerUnsafe) {
            this.previousLayerRegisteredMethods = previousLayerRegisteredMethods;
            this.previousLayerRegisteredFields = previousLayerRegisteredFields;
            this.previousLayerUnsafe = previousLayerUnsafe;
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

        private static boolean isElementRegistered(Set<Integer> previousLayerRegisteredElements, AnalysisType declaringClass, int elementId) {
            if (declaringClass.isInSharedLayer()) {
                return previousLayerRegisteredElements.contains(elementId);
            }
            return false;
        }

        public boolean isTypeUnsafeAllocated(AnalysisType analysisType) {
            return previousLayerUnsafe.contains(analysisType.getId());
        }

        private static String getElementKeyName(String element) {
            return REFLECTION_DATA_BUILDER + " " + element;
        }

        static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {

            @Override
            public LayeredCallbacksSingletonTrait getLayeredCallbacksTrait() {
                return new LayeredCallbacksSingletonTrait(new SingletonLayeredCallbacks<LayeredReflectionDataBuilder>() {
                    private static <T> void persistRegisteredElements(ImageSingletonWriter writer, Set<T> registeredElements, Function<T, Integer> getId, String element) {
                        writer.writeIntList(getElementKeyName(element), registeredElements.stream().map(getId).toList());
                    }

                    @Override
                    public LayeredPersistFlags doPersist(ImageSingletonWriter writer, LayeredReflectionDataBuilder singleton) {
                        ReflectionDataBuilder reflectionDataBuilder = (ReflectionDataBuilder) ImageSingletons.lookup(RuntimeReflectionSupport.class);
                        persistRegisteredElements(writer, getRegisteredElements(reflectionDataBuilder.methods), AnalysisMethod::getId, METHODS);
                        persistRegisteredElements(writer, getRegisteredElements(reflectionDataBuilder.fields), AnalysisField::getId, FIELDS);
                        persistRegisteredElements(writer, getRegisteredElements(reflectionDataBuilder.types, d -> d.unsafeAllocatedDynamicAccess != null), AnalysisType::getId, UNSAFE_ALLOCATED_TYPES);
                        return LayeredPersistFlags.CREATE;
                    }

                    private <T extends AnalysisElement, D extends ElementData> Set<T> getRegisteredElements(Map<T, D> elements) {
                        return getRegisteredElements(elements, d -> d.isRegisteredAs(ACCESSED));
                    }

                    private <T extends AnalysisElement, D extends ElementData> Set<T> getRegisteredElements(Map<T, D> elements, Predicate<D> filter) {
                        return elements.entrySet().stream()
                                        .filter(e -> filter.test(e.getValue()))
                                        .map(Map.Entry::getKey)
                                        .collect(Collectors.toUnmodifiableSet());
                    }

                    @Override
                    public Class<? extends LayeredSingletonInstantiator<?>> getSingletonInstantiator() {
                        return SingletonInstantiator.class;
                    }
                });
            }

            static class SingletonInstantiator implements SingletonLayeredCallbacks.LayeredSingletonInstantiator<LayeredReflectionDataBuilder> {

                private static Set<Integer> loadRegisteredElements(ImageSingletonLoader loader, String element) {
                    Set<Integer> previousLayerRegisteredElements = new HashSet<>(loader.readIntList(getElementKeyName(element)));
                    return Collections.unmodifiableSet(previousLayerRegisteredElements);
                }

                @Override
                public LayeredReflectionDataBuilder createFromLoader(ImageSingletonLoader loader) {
                    var previousLayerRegisteredMethods = loadRegisteredElements(loader, METHODS);
                    var previousLayerRegisteredFields = loadRegisteredElements(loader, FIELDS);
                    var previousLayerUnsafe = loadRegisteredElements(loader, UNSAFE_ALLOCATED_TYPES);
                    return new LayeredReflectionDataBuilder(previousLayerRegisteredMethods, previousLayerRegisteredFields, previousLayerUnsafe);
                }
            }
        }
    }
}
