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
package com.oracle.svm.hosted.code;

import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_FLAGS_MASK;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_NEST_MEMBERS_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_PERMITTED_SUBCLASSES_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.ALL_SIGNERS_FLAG;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.CLASS_ACCESS_FLAGS_MASK;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.COMPLETE_FLAG_MASK;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.FIRST_ERROR_INDEX;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.HIDING_FLAG_MASK;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.IN_HEAP_FLAG_MASK;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.NEGATIVE_FLAG_MASK;
import static com.oracle.svm.core.code.RuntimeMetadataDecoderImpl.NULL_OBJECT;
import static com.oracle.svm.core.reflect.RuntimeMetadataDecoder.NO_DATA;
import static com.oracle.svm.hosted.code.ReflectionRuntimeMetadata.AccessibleObjectMetadata;
import static com.oracle.svm.hosted.code.ReflectionRuntimeMetadata.ClassMetadata;
import static com.oracle.svm.hosted.code.ReflectionRuntimeMetadata.ConstructorMetadata;
import static com.oracle.svm.hosted.code.ReflectionRuntimeMetadata.ExecutableMetadata;
import static com.oracle.svm.hosted.code.ReflectionRuntimeMetadata.FieldMetadata;
import static com.oracle.svm.hosted.code.ReflectionRuntimeMetadata.MethodMetadata;
import static com.oracle.svm.hosted.code.ReflectionRuntimeMetadata.RecordComponentMetadata;
import static com.oracle.svm.hosted.code.ReflectionRuntimeMetadata.ReflectParameterMetadata;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

import com.oracle.graal.pointsto.infrastructure.WrappedElement;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.code.CodeInfoEncoder;
import com.oracle.svm.core.code.RuntimeMetadataDecoderImpl;
import com.oracle.svm.core.code.RuntimeMetadataEncoding;
import com.oracle.svm.core.configure.ConditionalRuntimeValue;
import com.oracle.svm.core.configure.RuntimeConditionSet;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.reflect.target.EncodedRuntimeMetadataSupplier;
import com.oracle.svm.core.reflect.target.Target_jdk_internal_reflect_ConstantPool;
import com.oracle.svm.core.util.ByteArrayReader;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.annotation.AnnotationMemberValue;
import com.oracle.svm.hosted.annotation.AnnotationValue;
import com.oracle.svm.hosted.annotation.TypeAnnotationValue;
import com.oracle.svm.hosted.image.NativeImageCodeCache.ReflectionMetadataEncoderFactory;
import com.oracle.svm.hosted.image.NativeImageCodeCache.RuntimeMetadataEncoder;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.reflect.ReflectionDataBuilder;
import com.oracle.svm.hosted.reflect.ReflectionHostedSupport;
import com.oracle.svm.hosted.substitute.DeletedElementException;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.core.common.util.TypeConversion;
import jdk.graal.compiler.core.common.util.UnsafeArrayTypeWriter;
import jdk.internal.reflect.Reflection;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * The runtime metadata encoder creates metadata for reflection objects (classes, fields, methods
 * and constructors), as well as for annotations and other objects queried from the VM to enable
 * their creation at runtime. The metadata related to classes is encoded in a single byte array (see
 * {@link RuntimeMetadataEncoding}), with the index into this array saved in the corresponding
 * {@link DynamicHub}. The metadata for reflection objects already present in the image heap is
 * encoded directly as byte arrays into the corresponding object through field recomputations.
 *
 * Method, field and constructor metadata can be complete, meaning it can be used to recreate a
 * working object of the corresponding type, or not, meaning it only holds the element's signature.
 * The incomplete form is used to implement hiding methods, which are methods that are not
 * registered for reflection but override a registered method, and as such should be seen by the JDK
 * code determining which superclass methods to include in a certain class (see
 * {@link Class#getMethods()}).
 *
 * Emitting the metadata happens in two phases. In the first phase, the string and class encoders
 * are filled with the necessary values (in the {@code #add*Metadata} functions). In a second phase,
 * the values are encoded into their intended byte arrays (see
 * {@link RuntimeMetadataEncoder#encodeAllAndInstall()}).
 *
 * The metadata encoding format is detailed in {@link RuntimeMetadataDecoderImpl}.
 */
public class RuntimeMetadataEncoderImpl implements RuntimeMetadataEncoder {

    @AutomaticallyRegisteredImageSingleton(ReflectionMetadataEncoderFactory.class)
    static class Factory implements ReflectionMetadataEncoderFactory {
        @Override
        public RuntimeMetadataEncoder create(SnippetReflectionProvider snippetReflection, CodeInfoEncoder.Encoders encoders) {
            return new RuntimeMetadataEncoderImpl(snippetReflection, encoders);
        }
    }

    private final SnippetReflectionProvider snippetReflection;
    private final CodeInfoEncoder.Encoders encoders;
    private final ReflectionDataAccessors accessors;
    private final ReflectionDataBuilder dataBuilder;
    private final TreeSet<HostedType> sortedTypes = new TreeSet<>(Comparator.comparingLong(t -> t.getHub().getTypeID()));
    private final Map<HostedType, ClassMetadata> classData = new HashMap<>();
    private final Map<HostedType, Map<Object, FieldMetadata>> fieldData = new HashMap<>();
    private final Map<HostedType, Map<Object, MethodMetadata>> methodData = new HashMap<>();
    private final Map<HostedType, Map<Object, ConstructorMetadata>> constructorData = new HashMap<>();

    private final Map<HostedType, Throwable> classLookupErrors = new HashMap<>();
    private final Map<HostedType, Throwable> fieldLookupErrors = new HashMap<>();
    private final Map<HostedType, Throwable> methodLookupErrors = new HashMap<>();
    private final Map<HostedType, Throwable> constructorLookupErrors = new HashMap<>();

    private final Set<AccessibleObjectMetadata> heapData = new HashSet<>();

    private final Map<AccessibleObject, byte[]> annotationsEncodings = new HashMap<>();
    private final Map<Executable, byte[]> parameterAnnotationsEncodings = new HashMap<>();
    private final Map<Method, byte[]> annotationDefaultEncodings = new HashMap<>();
    private final Map<AccessibleObject, byte[]> typeAnnotationsEncodings = new HashMap<>();
    private final Map<Executable, byte[]> reflectParametersEncodings = new HashMap<>();

    public RuntimeMetadataEncoderImpl(SnippetReflectionProvider snippetReflection, CodeInfoEncoder.Encoders encoders) {
        this.snippetReflection = snippetReflection;
        this.encoders = encoders;
        this.accessors = new ReflectionDataAccessors();
        this.dataBuilder = (ReflectionDataBuilder) ImageSingletons.lookup(RuntimeReflectionSupport.class);
    }

    private void addType(HostedType type) {
        if (type.getWrapped().isReachable() && sortedTypes.add(type)) {
            encoders.classes.addObject(type.getJavaClass());
        }
    }

    private void registerClass(HostedType type, ClassMetadata metadata) {
        addType(type);
        classData.put(type, metadata);
    }

    private void registerField(HostedType declaringType, Object field, FieldMetadata metadata) {
        addType(declaringType);
        FieldMetadata oldData = fieldData.computeIfAbsent(declaringType, t -> new HashMap<>()).put(field, metadata);
        assert oldData == null;
    }

    private FieldMetadata[] getFields(HostedType declaringType) {
        Field[] jdkFields = accessors.getDeclaredFields(declaringType.getJavaClass());
        Map<Object, FieldMetadata> fieldMetadata = fieldData.getOrDefault(declaringType, Collections.emptyMap());
        return sortElements(jdkFields, fieldMetadata).toArray(new FieldMetadata[0]);
    }

    private void registerMethod(HostedType declaringType, Object method, MethodMetadata metadata) {
        addType(declaringType);
        MethodMetadata oldData = methodData.computeIfAbsent(declaringType, t -> new HashMap<>()).put(method, metadata);
        assert oldData == null;
    }

    private MethodMetadata[] getMethods(HostedType declaringType) {
        Method[] jdkMethods = accessors.getDeclaredMethods(declaringType.getJavaClass());
        Map<Object, MethodMetadata> methodMetadata = methodData.getOrDefault(declaringType, Collections.emptyMap());
        return sortElements(jdkMethods, methodMetadata).toArray(new MethodMetadata[0]);
    }

    private void registerConstructor(HostedType declaringType, Object constructor, ConstructorMetadata metadata) {
        addType(declaringType);
        ConstructorMetadata oldData = constructorData.computeIfAbsent(declaringType, t -> new HashMap<>()).put(constructor, metadata);
        assert oldData == null;
    }

    private ConstructorMetadata[] getConstructors(HostedType declaringType) {
        Constructor<?>[] jdkConstructors = accessors.getDeclaredConstructors(declaringType.getJavaClass());
        Map<Object, ConstructorMetadata> constructorMetadata = constructorData.getOrDefault(declaringType, Collections.emptyMap());
        return sortElements(jdkConstructors, constructorMetadata).toArray(new ConstructorMetadata[0]);
    }

    /* Sort elements in the same order as the JCK */
    private static <T, M> List<M> sortElements(T[] jdkElements, Map<T, M> metadata) {
        List<M> orderedElements = new ArrayList<>();
        List<M> trailingElements = new ArrayList<>();
        for (T element : jdkElements) {
            if (metadata.containsKey(element)) {
                M elementMetadata = metadata.remove(element);
                if (element instanceof Method && ((Method) element).isBridge()) {
                    trailingElements.add(elementMetadata);
                } else {
                    orderedElements.add(elementMetadata);
                }
            }
        }
        /* Add non-reflection metadata to the end of the list */
        orderedElements.addAll(metadata.values());
        orderedElements.addAll(trailingElements);
        return orderedElements;
    }

    @Override
    public byte[] getAnnotationsEncoding(AccessibleObject object) {
        return annotationsEncodings.get(object);
    }

    @Override
    public byte[] getParameterAnnotationsEncoding(Executable object) {
        return parameterAnnotationsEncodings.get(object);
    }

    @Override
    public byte[] getAnnotationDefaultEncoding(Method object) {
        return annotationDefaultEncodings.get(object);
    }

    @Override
    public byte[] getTypeAnnotationsEncoding(AccessibleObject object) {
        return typeAnnotationsEncodings.get(object);
    }

    @Override
    public byte[] getReflectParametersEncoding(Executable object) {
        return reflectParametersEncodings.get(object);
    }

    @Override
    public void addClassMetadata(MetaAccessProvider metaAccess, HostedType type, Class<?>[] innerClasses) {
        Class<?> javaClass = type.getHub().getHostedJavaClass();
        Object enclosingMethodInfo = getEnclosingMethodInfo(javaClass);
        RecordComponentMetadata[] recordComponents = getRecordComponents(metaAccess, type, javaClass);
        Class<?>[] permittedSubclasses = getPermittedSubclasses(metaAccess, javaClass);
        Class<?>[] nestMembers = getNestMembers(metaAccess, javaClass);
        Object[] signers = getSigners(javaClass);
        int classAccessFlags = Reflection.getClassAccessFlags(javaClass) & CLASS_ACCESS_FLAGS_MASK;
        int enabledQueries = dataBuilder.getEnabledReflectionQueries(javaClass);
        VMError.guarantee((classAccessFlags & enabledQueries) == 0);
        int flags = classAccessFlags | enabledQueries;

        /* Register string and class values in annotations */
        encoders.classes.addObject(javaClass);
        if (enclosingMethodInfo instanceof Throwable) {
            registerError((Throwable) enclosingMethodInfo);
        } else {
            registerEnclosingMethodInfo((Object[]) enclosingMethodInfo);
        }
        HostedType[] innerTypes = registerClassValues(metaAccess, innerClasses);
        HostedType[] permittedSubtypes = (permittedSubclasses != null) ? registerClassValues(metaAccess, permittedSubclasses) : null;
        HostedType[] nestMemberTypes = (nestMembers != null) ? registerClassValues(metaAccess, nestMembers) : null;
        JavaConstant[] signerConstants = null;
        if (signers != null) {
            signerConstants = new JavaConstant[signers.length];
            for (int i = 0; i < signers.length; ++i) {
                signerConstants[i] = snippetReflection.forObject(signers[i]);
                addConstantObject(signerConstants[i]);
            }
        }
        AnalysisType analysisType = type.getWrapped();
        AnnotationValue[] annotations = registerAnnotationValues(analysisType);
        TypeAnnotationValue[] typeAnnotations = registerTypeAnnotationValues(analysisType);

        registerClass(type, new ClassMetadata(innerTypes, enclosingMethodInfo, recordComponents, permittedSubtypes, nestMemberTypes, signerConstants, flags, annotations, typeAnnotations));
    }

    private void addConstantObject(JavaConstant constant) {
        encoders.objectConstants.addObject(constant);
    }

    private void registerError(Throwable error) {
        addConstantObject(snippetReflection.forObject(error));
    }

    private static final Method getEnclosingMethod0 = ReflectionUtil.lookupMethod(Class.class, "getEnclosingMethod0");

    private static Object getEnclosingMethodInfo(Class<?> clazz) {
        try {
            return getEnclosingMethod0.invoke(clazz);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof LinkageError) {
                return e.getCause(); /* It's rethrown at run time. */
            }
            throw VMError.shouldNotReachHere(e);
        } catch (IllegalAccessException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private void registerEnclosingMethodInfo(Object[] enclosingMethodInfo) {
        if (enclosingMethodInfo == null) {
            return;
        }
        encoders.classes.addObject((Class<?>) enclosingMethodInfo[0]);
        encoders.memberNames.addObject((String) enclosingMethodInfo[1]);
        encoders.otherStrings.addObject((String) enclosingMethodInfo[2]);
    }

    private static final Method getPermittedSubclasses = ReflectionUtil.lookupMethod(true, Class.class, "getPermittedSubclasses");

    private Class<?>[] getPermittedSubclasses(MetaAccessProvider metaAccess, Class<?> clazz) {
        if ((dataBuilder.getEnabledReflectionQueries(clazz) & ALL_PERMITTED_SUBCLASSES_FLAG) == 0) {
            return null;
        }
        try {
            Class<?>[] permittedSubclasses = (Class<?>[]) getPermittedSubclasses.invoke(clazz);
            return filterDeletedClasses(metaAccess, permittedSubclasses);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private Class<?>[] getNestMembers(MetaAccessProvider metaAccess, Class<?> clazz) {
        if ((dataBuilder.getEnabledReflectionQueries(clazz) & ALL_NEST_MEMBERS_FLAG) == 0) {
            return null;
        }
        return filterDeletedClasses(metaAccess, clazz.getNestMembers());
    }

    private Object[] getSigners(Class<?> clazz) {
        if ((dataBuilder.getEnabledReflectionQueries(clazz) & ALL_SIGNERS_FLAG) == 0) {
            return null;
        }
        return clazz.getSigners();
    }

    private static Class<?>[] filterDeletedClasses(MetaAccessProvider metaAccess, Class<?>[] classes) {
        if (classes == null) {
            return null;
        }
        Set<Class<?>> reachableClasses = new HashSet<>();
        for (Class<?> clazz : classes) {
            try {
                metaAccess.lookupJavaType(clazz);
                reachableClasses.add(clazz);
            } catch (DeletedElementException dee) {
                // class has been deleted -> ignore
            }
        }
        return reachableClasses.toArray(new Class<?>[0]);
    }

    @Override
    public void addReflectionFieldMetadata(MetaAccessProvider metaAccess, HostedField hostedField, ConditionalRuntimeValue<Field> conditionalReflectField) {
        Field reflectField = conditionalReflectField.getValueUnconditionally();
        HostedType declaringType = hostedField.getDeclaringClass();
        String name = hostedField.getName();
        HostedType type = hostedField.getType();
        /* Reflect method because substitution of Object.hashCode() is private */
        int modifiers = reflectField.getModifiers();
        boolean trustedFinal = isTrustedFinal(reflectField);
        String signature = getSignature(reflectField);
        int offset = hostedField.wrapped.isUnsafeAccessed() ? hostedField.getOffset() : SharedField.LOC_UNINITIALIZED;
        Delete deleteAnnotation = AnnotationAccess.getAnnotation(hostedField, Delete.class);
        String deletedReason = (deleteAnnotation != null) ? deleteAnnotation.value() : null;
        RuntimeConditionSet conditions = conditionalReflectField.getConditions();
        /* Fill encoders with the necessary values. */
        for (Class<?> conditionType : conditions.getTypesForEncoding()) {
            encoders.classes.addObject(conditionType);
        }
        encoders.memberNames.addObject(name);
        encoders.classes.addObject(type.getJavaClass());
        encoders.otherStrings.addObject(signature);
        encoders.otherStrings.addObject(deletedReason);
        /* Register string and class values in annotations */
        AnalysisField analysisField = hostedField.getWrapped();
        AnnotationValue[] annotations = registerAnnotationValues(analysisField);
        TypeAnnotationValue[] typeAnnotations = registerTypeAnnotationValues(analysisField);

        registerField(declaringType, reflectField, new FieldMetadata(conditions, declaringType, name, type, modifiers, trustedFinal, signature, annotations, typeAnnotations, offset, deletedReason));
    }

    @Override
    public void addReflectionExecutableMetadata(MetaAccessProvider metaAccess, HostedMethod hostedMethod, ConditionalRuntimeValue<Executable> conditionalMethod, Object accessor) {
        boolean isMethod = !hostedMethod.isConstructor();
        HostedType declaringType = hostedMethod.getDeclaringClass();
        String name = isMethod ? hostedMethod.getName() : null;
        HostedType[] parameterTypes = getParameterTypes(hostedMethod);
        /* Reflect method because substitution of Object.hashCode() is private */
        Executable reflectMethod = conditionalMethod.getValueUnconditionally();
        RuntimeConditionSet conditions = conditionalMethod.getConditions();
        int modifiers = reflectMethod.getModifiers();
        HostedType returnType = hostedMethod.getSignature().getReturnType();
        HostedType[] exceptionTypes = getExceptionTypes(metaAccess, reflectMethod);
        String signature = getSignature(reflectMethod);

        /* Fill encoders with the necessary values. */
        for (Class<?> type : conditions.getTypesForEncoding()) {
            encoders.classes.addObject(type);
        }
        if (isMethod) {
            encoders.memberNames.addObject(name);
            encoders.classes.addObject(returnType.getJavaClass());
        }
        for (HostedType parameterType : parameterTypes) {
            encoders.classes.addObject(parameterType.getJavaClass());
        }
        for (HostedType exceptionType : exceptionTypes) {
            encoders.classes.addObject(exceptionType.getJavaClass());
        }
        encoders.otherStrings.addObject(signature);
        /* Register string and class values in annotations */
        AnalysisMethod analysisMethod = hostedMethod.getWrapped();
        AnnotationValue[] annotations = registerAnnotationValues(analysisMethod);
        AnnotationValue[][] parameterAnnotations = registerParameterAnnotationValues(analysisMethod);
        AnnotationMemberValue annotationDefault = isMethod ? registerAnnotationDefaultValues(analysisMethod) : null;
        TypeAnnotationValue[] typeAnnotations = registerTypeAnnotationValues(analysisMethod);
        ReflectParameterMetadata[] reflectParameters = registerReflectParameters(reflectMethod);
        JavaConstant accessorConstant = null;
        if (accessor != null) {
            accessorConstant = snippetReflection.forObject(accessor);
            addConstantObject(accessorConstant);
        }

        if (isMethod) {
            registerMethod(declaringType, reflectMethod,
                            new MethodMetadata(conditions, declaringType, name, parameterTypes, modifiers, returnType, exceptionTypes, signature, annotations, parameterAnnotations,
                                            annotationDefault, typeAnnotations, reflectParameters, accessorConstant));
        } else {
            registerConstructor(declaringType, reflectMethod,
                            new ConstructorMetadata(conditions, declaringType, parameterTypes, modifiers, exceptionTypes, signature, annotations, parameterAnnotations,
                                            typeAnnotations, reflectParameters, accessorConstant));
        }
    }

    private static final Method isFieldTrustedFinal = ReflectionUtil.lookupMethod(true, Field.class, "isTrustedFinal");

    private static boolean isTrustedFinal(Field field) {
        try {
            return (boolean) isFieldTrustedFinal.invoke(field);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    @Override
    public void addHeapAccessibleObjectMetadata(MetaAccessProvider metaAccess, WrappedElement hostedObject, AccessibleObject object, boolean registered) {
        boolean isExecutable = object instanceof Executable;
        boolean isMethod = object instanceof Method;

        /* Register string and class values in annotations */
        AnnotatedElement analysisObject = hostedObject.getWrapped();
        AnnotationValue[] annotations = registerAnnotationValues(analysisObject);
        AnnotationValue[][] parameterAnnotations = isExecutable ? registerParameterAnnotationValues((AnalysisMethod) analysisObject) : null;
        TypeAnnotationValue[] typeAnnotations = registerTypeAnnotationValues(analysisObject);
        AnnotationMemberValue annotationDefault = isMethod ? registerAnnotationDefaultValues((AnalysisMethod) analysisObject) : null;
        ReflectParameterMetadata[] reflectParameters = isExecutable ? registerReflectParameters((Executable) object) : null;
        AccessibleObject holder = RuntimeMetadataEncoder.getHolder(object);
        JavaConstant heapObjectConstant = snippetReflection.forObject(holder);
        addConstantObject(heapObjectConstant);

        AccessibleObjectMetadata metadata;
        /* In-heap objects have an always satisfied condition */
        RuntimeConditionSet alwaysSatisfied = RuntimeConditionSet.unmodifiableEmptySet();
        if (isMethod) {
            metadata = new MethodMetadata(alwaysSatisfied, registered, heapObjectConstant, annotations, parameterAnnotations, annotationDefault, typeAnnotations, reflectParameters);
            registerMethod((HostedType) metaAccess.lookupJavaType(((Method) object).getDeclaringClass()), holder, (MethodMetadata) metadata);
        } else if (isExecutable) {
            metadata = new ConstructorMetadata(alwaysSatisfied, registered, heapObjectConstant, annotations, parameterAnnotations, typeAnnotations, reflectParameters);
            registerConstructor((HostedType) metaAccess.lookupJavaType(((Constructor<?>) object).getDeclaringClass()), holder, (ConstructorMetadata) metadata);
        } else {
            metadata = new FieldMetadata(alwaysSatisfied, registered, heapObjectConstant, annotations, typeAnnotations);
            registerField((HostedType) metaAccess.lookupJavaType(((Field) object).getDeclaringClass()), holder, (FieldMetadata) metadata);
        }
        heapData.add(metadata);
    }

    private HostedType[] registerClassValues(MetaAccessProvider metaAccess, Class<?>[] classes) {
        Set<HostedType> includedClasses = new HashSet<>();
        for (Class<?> clazz : classes) {
            HostedType type;
            try {
                type = ((HostedMetaAccess) metaAccess).optionalLookupJavaType(clazz).orElse(null);
            } catch (DeletedElementException e) {
                type = null;
            }
            if (type != null && type.getWrapped().isReachable()) {
                encoders.classes.addObject(type.getJavaClass());
                includedClasses.add(type);
            }
        }
        return includedClasses.toArray(HostedType.EMPTY_ARRAY);
    }

    private AnnotationValue[] registerAnnotationValues(AnnotatedElement element) {
        AnnotationValue[] annotations = dataBuilder.getAnnotationData(element);
        for (AnnotationValue annotation : annotations) {
            registerValues(annotation);
        }
        return annotations;
    }

    private AnnotationValue[][] registerParameterAnnotationValues(AnalysisMethod element) {
        AnnotationValue[][] parameterAnnotations = dataBuilder.getParameterAnnotationData(element);
        for (AnnotationValue[] annotations : parameterAnnotations) {
            for (AnnotationValue annotation : annotations) {
                registerValues(annotation);
            }
        }
        return parameterAnnotations;
    }

    private AnnotationMemberValue registerAnnotationDefaultValues(AnalysisMethod method) {
        AnnotationMemberValue annotationDefault = dataBuilder.getAnnotationDefaultData(method);
        if (annotationDefault != null) {
            registerValues(annotationDefault);
        }
        return annotationDefault;
    }

    private TypeAnnotationValue[] registerTypeAnnotationValues(AnnotatedElement element) {
        TypeAnnotationValue[] typeAnnotations = dataBuilder.getTypeAnnotationData(element);
        for (TypeAnnotationValue typeAnnotation : typeAnnotations) {
            registerValues(typeAnnotation.getAnnotationData());
        }
        return typeAnnotations;
    }

    private void registerValues(AnnotationMemberValue annotationValue) {
        AnnotationMetadataEncoder.registerAnnotationMember(annotationValue, encoders, snippetReflection);
    }

    private ReflectParameterMetadata[] registerReflectParameters(Executable executable) {
        ReflectParameterMetadata[] reflectParameters = getReflectParameters(executable);
        if (reflectParameters != null) {
            for (ReflectParameterMetadata parameter : reflectParameters) {
                encoders.otherStrings.addObject(parameter.name);
            }
        }
        return reflectParameters;
    }

    @Override
    public void addHidingFieldMetadata(AnalysisField analysisField, HostedType declaringType, String name, HostedType type, int modifiers) {
        /* Fill encoders with the necessary values. */
        encoders.memberNames.addObject(name);
        encoders.classes.addObject(type.getJavaClass());

        addType(declaringType);
        registerField(declaringType, analysisField, new FieldMetadata(RuntimeConditionSet.emptySet(), declaringType, name, type, modifiers));
    }

    @Override
    public void addHidingMethodMetadata(AnalysisMethod analysisMethod, HostedType declaringType, String name, HostedType[] parameterTypes, int modifiers, HostedType returnType) {
        /* Fill encoders with the necessary values. */
        encoders.memberNames.addObject(name);
        for (HostedType parameterType : parameterTypes) {
            encoders.classes.addObject(parameterType.getJavaClass());
        }
        encoders.classes.addObject(returnType.getJavaClass());

        addType(declaringType);
        registerMethod(declaringType, analysisMethod, new MethodMetadata(RuntimeConditionSet.emptySet(), declaringType, name, parameterTypes, modifiers, returnType));
    }

    @Override
    public void addReachableFieldMetadata(HostedField field) {
        HostedType declaringType = field.getDeclaringClass();
        String name = field.getName();

        /* Fill encoders with the necessary values. */
        encoders.memberNames.addObject(name);

        registerField(declaringType, field, new FieldMetadata(RuntimeConditionSet.emptySet(), declaringType, name, false));
    }

    @Override
    public void addReachableExecutableMetadata(HostedMethod executable) {
        boolean isMethod = !executable.isConstructor();
        HostedType declaringType = executable.getDeclaringClass();
        String name = isMethod ? executable.getName() : null;
        String[] parameterTypeNames = getParameterTypeNames(executable);

        /* Fill encoders with the necessary values. */
        if (isMethod) {
            encoders.memberNames.addObject(name);
        }
        for (String parameterTypeName : parameterTypeNames) {
            encoders.otherStrings.addObject(parameterTypeName);
        }

        if (isMethod) {
            registerMethod(declaringType, executable, new MethodMetadata(RuntimeConditionSet.emptySet(), declaringType, name, parameterTypeNames));
        } else {
            registerConstructor(declaringType, executable, new ConstructorMetadata(RuntimeConditionSet.emptySet(), declaringType, parameterTypeNames));
        }
    }

    @Override
    public void addNegativeFieldQueryMetadata(HostedType declaringClass, String fieldName) {
        encoders.memberNames.addObject(fieldName);
        registerField(declaringClass, fieldName, new FieldMetadata(RuntimeConditionSet.emptySet(), declaringClass, fieldName, true));
    }

    @Override
    public void addNegativeMethodQueryMetadata(HostedType declaringClass, String methodName, HostedType[] parameterTypes) {
        encoders.memberNames.addObject(methodName);
        for (HostedType parameterType : parameterTypes) {
            encoders.classes.addObject(parameterType.getJavaClass());
        }
        registerMethod(declaringClass, Pair.create(methodName, parameterTypes), new MethodMetadata(RuntimeConditionSet.emptySet(), declaringClass, methodName, parameterTypes));
    }

    @Override
    public void addNegativeConstructorQueryMetadata(HostedType declaringClass, HostedType[] parameterTypes) {
        for (HostedType parameterType : parameterTypes) {
            encoders.classes.addObject(parameterType.getJavaClass());
        }
        registerConstructor(declaringClass, parameterTypes, new ConstructorMetadata(RuntimeConditionSet.emptySet(), declaringClass, parameterTypes));
    }

    @Override
    public void addClassLookupError(HostedType declaringClass, Throwable exception) {
        addType(declaringClass);
        registerError(exception);
        classLookupErrors.put(declaringClass, exception);
    }

    @Override
    public void addFieldLookupError(HostedType declaringClass, Throwable exception) {
        addType(declaringClass);
        registerError(exception);
        fieldLookupErrors.put(declaringClass, exception);
    }

    @Override
    public void addMethodLookupError(HostedType declaringClass, Throwable exception) {
        addType(declaringClass);
        registerError(exception);
        methodLookupErrors.put(declaringClass, exception);
    }

    @Override
    public void addConstructorLookupError(HostedType declaringClass, Throwable exception) {
        addType(declaringClass);
        registerError(exception);
        constructorLookupErrors.put(declaringClass, exception);
    }

    private static HostedType[] getParameterTypes(HostedMethod method) {
        HostedType[] parameterTypes = new HostedType[method.getSignature().getParameterCount(false)];
        for (int i = 0; i < parameterTypes.length; ++i) {
            parameterTypes[i] = method.getSignature().getParameterType(i);
        }
        return parameterTypes;
    }

    private static String[] getParameterTypeNames(HostedMethod method) {
        String[] parameterTypeNames = new String[method.getSignature().getParameterCount(false)];
        for (int i = 0; i < parameterTypeNames.length; ++i) {
            parameterTypeNames[i] = method.getSignature().getParameterType(i).toJavaName();
        }
        return parameterTypeNames;
    }

    private static HostedType[] getExceptionTypes(MetaAccessProvider metaAccess, Executable reflectMethod) {
        Class<?>[] exceptionClasses = reflectMethod.getExceptionTypes();
        HostedType[] exceptionTypes = new HostedType[exceptionClasses.length];
        for (int i = 0; i < exceptionClasses.length; ++i) {
            exceptionTypes[i] = (HostedType) metaAccess.lookupJavaType(exceptionClasses[i]);
        }
        return exceptionTypes;
    }

    private static ReflectParameterMetadata[] getReflectParameters(Executable reflectMethod) {
        Parameter[] rawParameters = getRawParameters(reflectMethod);
        if (rawParameters == null) {
            return null;
        }
        ReflectParameterMetadata[] reflectParameters = new ReflectParameterMetadata[rawParameters.length];
        for (int i = 0; i < rawParameters.length; ++i) {
            reflectParameters[i] = new ReflectParameterMetadata(rawParameters[i].getName(), rawParameters[i].getModifiers());
        }
        return reflectParameters;
    }

    private RecordComponentMetadata[] getRecordComponents(MetaAccessProvider metaAccess, HostedType declaringType, Class<?> clazz) {
        RecordComponent[] recordComponents = ImageSingletons.lookup(ReflectionHostedSupport.class).getRecordComponents(clazz);
        if (recordComponents == null) {
            return null;
        }
        RecordComponentMetadata[] metadata = new RecordComponentMetadata[recordComponents.length];
        for (int i = 0; i < recordComponents.length; ++i) {
            RecordComponent recordComponent = recordComponents[i];
            String name = recordComponent.getName();
            HostedType type = (HostedType) metaAccess.lookupJavaType(recordComponent.getType());
            String signature = recordComponent.getGenericSignature();

            /* Fill encoders with the necessary values. */
            encoders.memberNames.addObject(name);
            encoders.classes.addObject(type.getJavaClass());
            encoders.otherStrings.addObject(signature);
            /* Register string and class values in annotations */
            AnnotationValue[] annotations = registerAnnotationValues(recordComponent);
            TypeAnnotationValue[] typeAnnotations = registerTypeAnnotationValues(recordComponent);

            metadata[i] = new RecordComponentMetadata(declaringType, name, type, signature, annotations, typeAnnotations);
        }
        return metadata;
    }

    /**
     * See {@link RuntimeMetadataDecoderImpl} for the encoding format description.
     */
    @Override
    public void encodeAllAndInstall() {
        UnsafeArrayTypeWriter buf = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());
        int typesIndex = encodeAndAddCollection(buf, sortedTypes.toArray(HostedType.EMPTY_ARRAY), this::encodeType, false);
        assert typesIndex == 0;
        for (HostedType declaringType : sortedTypes) {
            DynamicHub hub = declaringType.getHub();
            ClassMetadata classMetadata = classData.get(declaringType);

            int enclosingMethodInfoIndex = classMetadata.enclosingMethodInfo instanceof Throwable
                            ? encodeErrorIndex((Throwable) classMetadata.enclosingMethodInfo)
                            : addElement(buf, encodeEnclosingMethodInfo((Object[]) classMetadata.enclosingMethodInfo));
            int annotationsIndex = addEncodedElement(buf, encodeAnnotations(classMetadata.annotations));
            int typeAnnotationsIndex = addEncodedElement(buf, encodeTypeAnnotations(classMetadata.typeAnnotations));
            int classesEncodingIndex = encodeAndAddCollection(buf, classMetadata.classes, classLookupErrors.get(declaringType), this::encodeType, false);
            int permittedSubclassesIndex = encodeAndAddCollection(buf, classMetadata.permittedSubclasses, this::encodeType, true);
            int nestMembersEncodingIndex = encodeAndAddCollection(buf, classMetadata.nestMembers, this::encodeType, true);
            int signersEncodingIndex = encodeAndAddCollection(buf, classMetadata.signers, this::encodeObject, true);
            if (anySet(enclosingMethodInfoIndex, annotationsIndex, typeAnnotationsIndex, classesEncodingIndex, permittedSubclassesIndex, nestMembersEncodingIndex, signersEncodingIndex)) {
                hub.setHubMetadata(enclosingMethodInfoIndex, annotationsIndex, typeAnnotationsIndex, classesEncodingIndex, permittedSubclassesIndex, nestMembersEncodingIndex, signersEncodingIndex);
            }

            int fieldsIndex = encodeAndAddCollection(buf, getFields(declaringType), fieldLookupErrors.get(declaringType), this::encodeField, false);
            int methodsIndex = encodeAndAddCollection(buf, getMethods(declaringType), methodLookupErrors.get(declaringType), this::encodeExecutable, false);
            int constructorsIndex = encodeAndAddCollection(buf, getConstructors(declaringType), constructorLookupErrors.get(declaringType), this::encodeExecutable, false);
            int recordComponentsIndex = encodeAndAddCollection(buf, classMetadata.recordComponents, this::encodeRecordComponent, true);
            int classFlags = classMetadata.flags;
            if (anySet(fieldsIndex, methodsIndex, constructorsIndex, recordComponentsIndex) || classFlags != hub.getModifiers()) {
                hub.setReflectionMetadata(fieldsIndex, methodsIndex, constructorsIndex, recordComponentsIndex, classFlags);
            }
        }
        for (AccessibleObjectMetadata metadata : heapData) {
            AccessibleObject heapObject = snippetReflection.asObject(AccessibleObject.class, metadata.heapObject);
            annotationsEncodings.put(heapObject, encodeAnnotations(metadata.annotations));
            typeAnnotationsEncodings.put(heapObject, encodeTypeAnnotations(metadata.typeAnnotations));
            if (metadata instanceof ExecutableMetadata) {
                parameterAnnotationsEncodings.put((Executable) heapObject, encodeParameterAnnotations(((ExecutableMetadata) metadata).parameterAnnotations));
                if (((ExecutableMetadata) metadata).reflectParameters != null) {
                    reflectParametersEncodings.put((Executable) heapObject, encodeReflectParameters(((ExecutableMetadata) metadata).reflectParameters));
                }
                if (metadata instanceof MethodMetadata) {
                    annotationDefaultEncodings.put((Method) heapObject, encodeAnnotationDefault(((MethodMetadata) metadata).annotationDefault));
                }
            }
        }
        install(buf);
        /* Enable field recomputers in reflection objects to see the computed values */
        ImageSingletons.add(EncodedRuntimeMetadataSupplier.class, this);
    }

    private int encodeErrorIndex(Throwable error) {
        int index = encoders.objectConstants.getIndex(snippetReflection.forObject(error));
        int encodedIndex = FIRST_ERROR_INDEX - index;
        VMError.guarantee(RuntimeMetadataDecoderImpl.isErrorIndex(encodedIndex));
        return encodedIndex;
    }

    private <T> int encodeAndAddCollection(UnsafeArrayTypeWriter buf, T[] data, BiConsumer<UnsafeArrayTypeWriter, T> encodeCallback, boolean canBeNull) {
        return encodeAndAddCollection(buf, data, null, encodeCallback, canBeNull);
    }

    private <T> int encodeAndAddCollection(UnsafeArrayTypeWriter buf, T[] data, Throwable lookupError, BiConsumer<UnsafeArrayTypeWriter, T> encodeCallback, boolean canBeNull) {
        int offset = TypeConversion.asS4(buf.getBytesWritten());
        if (lookupError != null) {
            buf.putSV(encodeErrorIndex(lookupError));
        } else if (data == null || (!canBeNull && data.length == 0)) {
            /*
             * We must encode a zero-length array if it does not have the same meaning as a null
             * array (e.g. for permitted classes)
             */
            return NO_DATA;
        } else {
            encodeArray(buf, data, element -> encodeCallback.accept(buf, element));
        }
        return offset;
    }

    private static int addElement(UnsafeArrayTypeWriter buf, byte[] encoding) {
        if (encoding == null) {
            return NO_DATA;
        }
        int offset = TypeConversion.asS4(buf.getBytesWritten());
        encodeBytes(buf, encoding);
        return offset;
    }

    private static int addEncodedElement(UnsafeArrayTypeWriter buf, byte[] encoding) {
        if (encoding == null) {
            return NO_DATA;
        }
        int offset = TypeConversion.asS4(buf.getBytesWritten());
        encodeByteArray(buf, encoding);
        return offset;
    }

    private static void install(UnsafeArrayTypeWriter encodingBuffer) {
        int encodingSize = TypeConversion.asS4(encodingBuffer.getBytesWritten());
        byte[] dataEncoding = new byte[encodingSize];
        ImageSingletons.lookup(RuntimeMetadataEncoding.class).setEncoding(encodingBuffer.toArray(dataEncoding));
    }

    private static boolean anySet(int... indices) {
        for (int index : indices) {
            if (index != NO_DATA) {
                return true;
            }
        }
        return false;
    }

    private void encodeField(UnsafeArrayTypeWriter buf, FieldMetadata field) {
        /* Make sure we do not overwrite actual modifiers with our flags */
        assert (field.modifiers & ALL_FLAGS_MASK) == 0;
        int modifiers = field.modifiers;
        modifiers |= field.complete ? COMPLETE_FLAG_MASK : 0;
        modifiers |= field.heapObject != null ? IN_HEAP_FLAG_MASK : 0;
        modifiers |= field.hiding ? HIDING_FLAG_MASK : 0;
        modifiers |= field.negative ? NEGATIVE_FLAG_MASK : 0;
        buf.putUV(modifiers);
        encodeConditions(buf, field.conditions);

        if (field.heapObject != null) {
            encodeObject(buf, field.heapObject);
        } else {
            encodeMemberName(buf, field.name);
            if (field.complete || field.hiding) {
                encodeType(buf, field.type);
            }
            if (field.complete) {
                buf.putU1(field.trustedFinal ? 1 : 0);
                encodeOtherString(buf, field.signature);
                encodeByteArray(buf, encodeAnnotations(field.annotations));
                encodeByteArray(buf, encodeTypeAnnotations(field.typeAnnotations));
                buf.putSV(field.offset);
                encodeOtherString(buf, field.deletedReason);
            }
        }
    }

    private void encodeConditions(UnsafeArrayTypeWriter buf, RuntimeConditionSet conditions) {
        encodeArray(buf, conditions.getTypesForEncoding().toArray(Class[]::new), t -> encodeType(buf, t));
    }

    private void encodeExecutable(UnsafeArrayTypeWriter buf, ExecutableMetadata executable) {
        boolean isMethod = executable instanceof MethodMetadata;
        boolean isHiding = isMethod && ((MethodMetadata) executable).hiding;
        /* Make sure we do not overwrite actual modifiers with our flags */
        assert (executable.modifiers & ALL_FLAGS_MASK) == 0;
        int modifiers = executable.modifiers;
        modifiers |= executable.complete ? COMPLETE_FLAG_MASK : 0;
        modifiers |= executable.heapObject != null ? IN_HEAP_FLAG_MASK : 0;
        modifiers |= isHiding ? HIDING_FLAG_MASK : 0;
        modifiers |= executable.negative ? NEGATIVE_FLAG_MASK : 0;
        buf.putUV(modifiers);

        encodeConditions(buf, executable.conditions);

        if (executable.heapObject != null) {
            encodeObject(buf, executable.heapObject);
        } else {
            if (isMethod) {
                encodeMemberName(buf, ((MethodMetadata) executable).name);
            }
            if (executable.complete || isHiding || executable.negative) {
                encodeArray(buf, (HostedType[]) executable.parameterTypes, parameterType -> encodeType(buf, parameterType));
            } else {
                encodeArray(buf, (String[]) executable.parameterTypes, parameterTypeName -> encodeOtherString(buf, parameterTypeName));
            }
            if (isMethod && (executable.complete || isHiding)) {
                encodeType(buf, ((MethodMetadata) executable).returnType);
            }
            if (executable.complete) {
                encodeArray(buf, executable.exceptionTypes, exceptionType -> encodeType(buf, exceptionType));
                encodeOtherString(buf, executable.signature);
                encodeByteArray(buf, encodeAnnotations(executable.annotations));
                encodeByteArray(buf, encodeParameterAnnotations(executable.parameterAnnotations));
                if (isMethod && executable.declaringType.getHub().getHostedJavaClass().isAnnotation()) {
                    encodeByteArray(buf, encodeAnnotationDefault(((MethodMetadata) executable).annotationDefault));
                }
                encodeByteArray(buf, encodeTypeAnnotations(executable.typeAnnotations));
                encodeByteArray(buf, encodeReflectParameters(executable.reflectParameters));
                encodeObject(buf, executable.accessor);
            }
        }
    }

    private void encodeType(UnsafeArrayTypeWriter buf, HostedType type) {
        encodeType(buf, type.getJavaClass());
    }

    private void encodeType(UnsafeArrayTypeWriter buf, Class<?> type) {
        buf.putSV(encoders.classes.getIndex(type));
    }

    private void encodeMemberName(UnsafeArrayTypeWriter buf, String name) {
        buf.putSV(encoders.memberNames.getIndex(name));
    }

    private void encodeOtherString(UnsafeArrayTypeWriter buf, String str) {
        buf.putSV(encoders.otherStrings.getIndex(str));
    }

    private void encodeObject(UnsafeArrayTypeWriter buf, JavaConstant object) {
        if (object == null) {
            buf.putSV(NULL_OBJECT);
        } else {
            buf.putSV(encoders.objectConstants.getIndex(object));
        }
    }

    private static <T> void encodeArray(UnsafeArrayTypeWriter buf, T[] array, Consumer<T> elementEncoder) {
        buf.putSV(array.length);
        for (T elem : array) {
            elementEncoder.accept(elem);
        }
    }

    private static void encodeByteArray(UnsafeArrayTypeWriter buf, byte[] array) {
        if (array == null) {
            buf.putUV(NO_DATA);
            return;
        }
        buf.putUV(array.length);
        encodeBytes(buf, array);
    }

    private static void encodeBytes(UnsafeArrayTypeWriter buf, byte[] bytes) {
        for (byte b : bytes) {
            buf.putS1(b);
        }
    }

    private static final Method getFieldSignature = ReflectionUtil.lookupMethod(Field.class, "getGenericSignature");
    private static final Method getMethodSignature = ReflectionUtil.lookupMethod(Method.class, "getGenericSignature");
    private static final Method getConstructorSignature = ReflectionUtil.lookupMethod(Constructor.class, "getSignature");
    private static final Method getExecutableParameters = ReflectionUtil.lookupMethod(Executable.class, "getParameters0");

    private static String getSignature(Field field) {
        try {
            return (String) getFieldSignature.invoke(field);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private static String getSignature(Executable executable) {
        try {
            return (String) (executable instanceof Method ? getMethodSignature.invoke(executable) : getConstructorSignature.invoke(executable));
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private static Parameter[] getRawParameters(Executable executable) {
        try {
            return (Parameter[]) getExecutableParameters.invoke(executable);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    /**
     * The following methods encode annotations attached to a method or parameter in a format based
     * on the one used internally by the JDK ({@link sun.reflect.annotation.AnnotationParser}). The
     * format we use differs from that one on a few points, based on the fact that the JDK encoding
     * is based on constant pool indices, which are not available in that form at runtime.
     *
     * Class and String values are represented by their index in the source metadata encoders
     * instead of their constant pool indices. Additionally, Class objects are encoded directly
     * instead of through their type signature. Primitive values are written directly into the
     * encoding. This means that our encoding can be of a different length from the JDK one.
     *
     * We use a modified version of the ConstantPool and AnnotationParser classes to decode the
     * data, since those are not used in their original functions at runtime. (see
     * {@link Target_jdk_internal_reflect_ConstantPool} and
     * {@code Target_sun_reflect_annotation_AnnotationParser})
     */
    private byte[] encodeAnnotations(AnnotationValue[] annotations) {
        if (annotations.length == 0) {
            return null;
        }
        UnsafeArrayTypeWriter buf = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess(), true);
        AnnotationMetadataEncoder.encodeArray(buf, annotations, annotation -> AnnotationMetadataEncoder.encodeAnnotation(buf, annotation, encoders));
        return buf.toArray();
    }

    private byte[] encodeParameterAnnotations(AnnotationValue[][] parameterAnnotations) {
        if (parameterAnnotations.length == 0) {
            return null;
        }
        UnsafeArrayTypeWriter buf = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess(), true);
        buf.putU1(parameterAnnotations.length);
        for (AnnotationValue[] annotations : parameterAnnotations) {
            AnnotationMetadataEncoder.encodeArray(buf, annotations, annotation -> AnnotationMetadataEncoder.encodeAnnotation(buf, annotation, encoders));
        }
        return buf.toArray();
    }

    private byte[] encodeAnnotationDefault(AnnotationMemberValue annotationDefault) {
        if (annotationDefault == null) {
            return null;
        }
        UnsafeArrayTypeWriter buf = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess(), true);
        AnnotationMetadataEncoder.encodeAnnotationMember(buf, annotationDefault, encoders);
        return buf.toArray();
    }

    private byte[] encodeTypeAnnotations(TypeAnnotationValue[] typeAnnotations) {
        if (typeAnnotations.length == 0) {
            return null;
        }
        UnsafeArrayTypeWriter buf = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess(), true);
        AnnotationMetadataEncoder.encodeArray(buf, typeAnnotations,
                        typeAnnotation -> AnnotationMetadataEncoder.encodeTypeAnnotation(buf, typeAnnotation, encoders));
        return buf.toArray();
    }

    private byte[] encodeReflectParameters(ReflectParameterMetadata[] reflectParameters) {
        if (reflectParameters == null) {
            return null;
        }
        UnsafeArrayTypeWriter buf = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());
        encodeArray(buf, reflectParameters, reflectParameter -> encodeReflectParameter(buf, reflectParameter));
        return buf.toArray();
    }

    private void encodeReflectParameter(UnsafeArrayTypeWriter buf, ReflectParameterMetadata reflectParameter) {
        encodeOtherString(buf, reflectParameter.name);
        buf.putUV(reflectParameter.modifiers);
    }

    private void encodeRecordComponent(UnsafeArrayTypeWriter buf, RecordComponentMetadata recordComponent) {
        encodeMemberName(buf, recordComponent.name);
        encodeType(buf, recordComponent.type);
        encodeOtherString(buf, recordComponent.signature);
        encodeByteArray(buf, encodeAnnotations(recordComponent.annotations));
        encodeByteArray(buf, encodeTypeAnnotations(recordComponent.typeAnnotations));
    }

    private byte[] encodeEnclosingMethodInfo(Object[] enclosingMethodInfo) {
        if (enclosingMethodInfo == null) {
            return null;
        }
        assert enclosingMethodInfo.length == 3;
        UnsafeArrayTypeWriter buf = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());
        encodeType(buf, (Class<?>) enclosingMethodInfo[0]);
        encodeMemberName(buf, (String) enclosingMethodInfo[1]);
        encodeOtherString(buf, (String) enclosingMethodInfo[2]);
        return buf.toArray();
    }

    static final class ReflectionDataAccessors {
        private final Method privateGetDeclaredFields;
        private final Method privateGetDeclaredMethods;
        private final Method privateGetDeclaredConstructors;

        ReflectionDataAccessors() {
            privateGetDeclaredFields = ReflectionUtil.lookupMethod(Class.class, "privateGetDeclaredFields", boolean.class);
            privateGetDeclaredMethods = ReflectionUtil.lookupMethod(Class.class, "privateGetDeclaredMethods", boolean.class);
            privateGetDeclaredConstructors = ReflectionUtil.lookupMethod(Class.class, "privateGetDeclaredConstructors", boolean.class);
        }

        Field[] getDeclaredFields(Object obj) {
            try {
                return (Field[]) privateGetDeclaredFields.invoke(obj, false);
            } catch (IllegalAccessException | InvocationTargetException e) {
                /* Don't enforce an order if querying fails */
                return new Field[0];
            }
        }

        Method[] getDeclaredMethods(Object obj) {
            try {
                return (Method[]) privateGetDeclaredMethods.invoke(obj, false);
            } catch (IllegalAccessException | InvocationTargetException e) {
                /* Don't enforce an order if querying fails */
                return new Method[0];
            }
        }

        Constructor<?>[] getDeclaredConstructors(Object obj) {
            try {
                return (Constructor<?>[]) privateGetDeclaredConstructors.invoke(obj, false);
            } catch (IllegalAccessException | InvocationTargetException e) {
                /* Don't enforce an order if querying fails */
                return new Constructor<?>[0];
            }
        }
    }
}
