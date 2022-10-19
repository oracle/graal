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
package com.oracle.svm.hosted.reflect;

import static com.oracle.svm.core.reflect.ReflectionMetadataDecoder.NO_DATA;
import static com.oracle.svm.core.reflect.target.ReflectionMetadataDecoderImpl.ALL_FLAGS_MASK;
import static com.oracle.svm.core.reflect.target.ReflectionMetadataDecoderImpl.COMPLETE_FLAG_MASK;
import static com.oracle.svm.core.reflect.target.ReflectionMetadataDecoderImpl.FIRST_ERROR_INDEX;
import static com.oracle.svm.core.reflect.target.ReflectionMetadataDecoderImpl.HIDING_FLAG_MASK;
import static com.oracle.svm.core.reflect.target.ReflectionMetadataDecoderImpl.IN_HEAP_FLAG_MASK;
import static com.oracle.svm.core.reflect.target.ReflectionMetadataDecoderImpl.NULL_OBJECT;
import static com.oracle.svm.hosted.reflect.ReflectionMetadata.AccessibleObjectMetadata;
import static com.oracle.svm.hosted.reflect.ReflectionMetadata.ClassMetadata;
import static com.oracle.svm.hosted.reflect.ReflectionMetadata.ConstructorMetadata;
import static com.oracle.svm.hosted.reflect.ReflectionMetadata.ExecutableMetadata;
import static com.oracle.svm.hosted.reflect.ReflectionMetadata.FieldMetadata;
import static com.oracle.svm.hosted.reflect.ReflectionMetadata.MethodMetadata;
import static com.oracle.svm.hosted.reflect.ReflectionMetadata.RecordComponentMetadata;
import static com.oracle.svm.hosted.reflect.ReflectionMetadata.ReflectParameterMetadata;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
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

import org.graalvm.compiler.core.common.util.TypeConversion;
import org.graalvm.compiler.core.common.util.UnsafeArrayTypeWriter;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

import com.oracle.graal.pointsto.infrastructure.WrappedElement;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.code.CodeInfoEncoder;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.reflect.Target_jdk_internal_reflect_ConstantPool;
import com.oracle.svm.core.reflect.target.EncodedReflectionMetadataSupplier;
import com.oracle.svm.core.reflect.target.ReflectionMetadataDecoderImpl;
import com.oracle.svm.core.reflect.target.ReflectionMetadataEncoding;
import com.oracle.svm.core.reflect.target.Target_sun_reflect_annotation_AnnotationParser;
import com.oracle.svm.core.util.ByteArrayReader;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.annotation.AnnotationMemberValue;
import com.oracle.svm.hosted.annotation.AnnotationValue;
import com.oracle.svm.hosted.annotation.TypeAnnotationValue;
import com.oracle.svm.hosted.image.NativeImageCodeCache.ReflectionMetadataEncoder;
import com.oracle.svm.hosted.image.NativeImageCodeCache.ReflectionMetadataEncoderFactory;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.substitute.DeletedElementException;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.reflect.Reflection;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * The reflection metadata encoder creates metadata for reflection objects (classes, fields, methods
 * and constructors), as well as for annotations and other objects queried from the VM to enable
 * their creation at runtime. The metadata related to classes is saved to a single byte array (see
 * {@link ReflectionMetadataEncoding}), with the index into this array saved in the corresponding
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
 * the values are encoded into their intended byte arrays (see {@link #encodeAllAndInstall()}).
 *
 * The metadata encoding format is detailed in {@link ReflectionMetadataDecoderImpl}.
 */
public class ReflectionMetadataEncoderImpl implements ReflectionMetadataEncoder {

    @AutomaticallyRegisteredImageSingleton(ReflectionMetadataEncoderFactory.class)
    @Platforms(InternalPlatform.NATIVE_ONLY.class)
    static class Factory implements ReflectionMetadataEncoderFactory {
        @Override
        public ReflectionMetadataEncoder create(CodeInfoEncoder.Encoders encoders) {
            return new ReflectionMetadataEncoderImpl(encoders);
        }
    }

    private final CodeInfoEncoder.Encoders encoders;
    private final ReflectionDataAccessors accessors;
    private final ReflectionDataBuilder dataBuilder;
    private final TreeSet<HostedType> sortedTypes = new TreeSet<>(Comparator.comparingLong(t -> t.getHub().getTypeID()));
    private final Map<HostedType, ClassMetadata> classData = new HashMap<>();
    private final Map<HostedType, Map<Object, FieldMetadata>> fieldData = new HashMap<>();
    private final Map<HostedType, Map<Object, MethodMetadata>> methodData = new HashMap<>();
    private final Map<HostedType, Map<Object, ConstructorMetadata>> constructorData = new HashMap<>();

    private final Set<AccessibleObjectMetadata> heapData = new HashSet<>();

    private final Map<AccessibleObject, byte[]> annotationsEncodings = new HashMap<>();
    private final Map<Executable, byte[]> parameterAnnotationsEncodings = new HashMap<>();
    private final Map<Method, byte[]> annotationDefaultEncodings = new HashMap<>();
    private final Map<AccessibleObject, byte[]> typeAnnotationsEncodings = new HashMap<>();
    private final Map<Executable, byte[]> reflectParametersEncodings = new HashMap<>();

    public ReflectionMetadataEncoderImpl(CodeInfoEncoder.Encoders encoders) {
        this.encoders = encoders;
        this.accessors = new ReflectionDataAccessors();
        this.dataBuilder = (ReflectionDataBuilder) ImageSingletons.lookup(RuntimeReflectionSupport.class);
    }

    private void registerClass(HostedType type, ClassMetadata metadata) {
        sortedTypes.add(type);
        classData.put(type, metadata);
    }

    private void registerField(HostedType declaringType, Object field, FieldMetadata metadata) {
        sortedTypes.add(declaringType);
        FieldMetadata oldData = fieldData.computeIfAbsent(declaringType, t -> new HashMap<>()).put(field, metadata);
        assert oldData == null;
    }

    private FieldMetadata[] getFields(HostedType declaringType) {
        Field[] jdkFields = accessors.getDeclaredFields(declaringType.getJavaClass());
        Map<Object, FieldMetadata> fieldMetadata = fieldData.getOrDefault(declaringType, Collections.emptyMap());
        return sortElements(jdkFields, fieldMetadata).toArray(new FieldMetadata[0]);
    }

    private void registerMethod(HostedType declaringType, Object method, MethodMetadata metadata) {
        sortedTypes.add(declaringType);
        MethodMetadata oldData = methodData.computeIfAbsent(declaringType, t -> new HashMap<>()).put(method, metadata);
        assert oldData == null;
    }

    private MethodMetadata[] getMethods(HostedType declaringType) {
        Method[] jdkMethods = accessors.getDeclaredMethods(declaringType.getJavaClass());
        Map<Object, MethodMetadata> methodMetadata = methodData.getOrDefault(declaringType, Collections.emptyMap());
        return sortElements(jdkMethods, methodMetadata).toArray(new MethodMetadata[0]);
    }

    private void registerConstructor(HostedType declaringType, Object constructor, ConstructorMetadata metadata) {
        sortedTypes.add(declaringType);
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
        int classAccessFlags = Reflection.getClassAccessFlags(javaClass);

        /* Register string and class values in annotations */
        encoders.sourceClasses.addObject(javaClass);
        if (enclosingMethodInfo instanceof Throwable) {
            registerError((Throwable) enclosingMethodInfo);
        } else {
            registerEnclosingMethodInfo((Object[]) enclosingMethodInfo);
        }
        HostedType[] innerTypes = registerClassValues(metaAccess, innerClasses);
        HostedType[] permittedSubtypes = (permittedSubclasses != null) ? registerClassValues(metaAccess, permittedSubclasses) : null;
        AnalysisType analysisType = type.getWrapped();
        AnnotationValue[] annotations = registerAnnotationValues(analysisType);
        TypeAnnotationValue[] typeAnnotations = registerTypeAnnotationValues(analysisType);

        registerClass(type, new ClassMetadata(innerTypes, enclosingMethodInfo, recordComponents, permittedSubtypes, classAccessFlags, annotations, typeAnnotations));
    }

    private void registerError(Throwable error) {
        encoders.objectConstants.addObject(SubstrateObjectConstant.forObject(error));
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
        encoders.sourceClasses.addObject((Class<?>) enclosingMethodInfo[0]);
        encoders.sourceMethodNames.addObject((String) enclosingMethodInfo[1]);
        encoders.sourceMethodNames.addObject((String) enclosingMethodInfo[2]);
    }

    private static final Method getPermittedSubclasses = ReflectionUtil.lookupMethod(true, Class.class, "getPermittedSubclasses");

    private static Class<?>[] getPermittedSubclasses(MetaAccessProvider metaAccess, Class<?> clazz) {
        if (JavaVersionUtil.JAVA_SPEC < 17) {
            return null;
        }
        try {
            Class<?>[] permittedSubclasses = (Class<?>[]) getPermittedSubclasses.invoke(clazz);
            if (permittedSubclasses == null) {
                return null;
            }
            Set<Class<?>> reachablePermittedSubclasses = new HashSet<>();
            for (Class<?> permittedSubclass : permittedSubclasses) {
                try {
                    HostedType hostedType = ((HostedMetaAccess) metaAccess).optionalLookupJavaType(permittedSubclass).orElse(null);
                    if (hostedType != null && hostedType.getWrapped().isReachable()) {
                        reachablePermittedSubclasses.add(permittedSubclass);
                    }
                } catch (DeletedElementException dee) {
                    // permitted subclass has been deleted -> ignore
                }
            }
            return reachablePermittedSubclasses.toArray(new Class<?>[0]);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    @Override
    public void addReflectionFieldMetadata(MetaAccessProvider metaAccess, HostedField hostedField, Field reflectField) {
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

        /* Fill encoders with the necessary values. */
        encoders.sourceMethodNames.addObject(name);
        encoders.sourceClasses.addObject(type.getJavaClass());
        encoders.sourceMethodNames.addObject(signature);
        encoders.sourceMethodNames.addObject(deletedReason);
        /* Register string and class values in annotations */
        AnalysisField analysisField = hostedField.getWrapped();
        AnnotationValue[] annotations = registerAnnotationValues(analysisField);
        TypeAnnotationValue[] typeAnnotations = registerTypeAnnotationValues(analysisField);

        registerField(declaringType, reflectField, new FieldMetadata(declaringType, name, type, modifiers, trustedFinal, signature, annotations, typeAnnotations, offset, deletedReason));
    }

    @Override
    public void addReflectionExecutableMetadata(MetaAccessProvider metaAccess, HostedMethod hostedMethod, Executable reflectMethod, Object accessor) {
        boolean isMethod = !hostedMethod.isConstructor();
        HostedType declaringType = hostedMethod.getDeclaringClass();
        String name = isMethod ? hostedMethod.getName() : null;
        HostedType[] parameterTypes = getParameterTypes(hostedMethod);
        /* Reflect method because substitution of Object.hashCode() is private */
        int modifiers = reflectMethod.getModifiers();
        HostedType returnType = (HostedType) hostedMethod.getSignature().getReturnType(null);
        HostedType[] exceptionTypes = getExceptionTypes(metaAccess, reflectMethod);
        String signature = getSignature(reflectMethod);

        /* Fill encoders with the necessary values. */
        if (isMethod) {
            encoders.sourceMethodNames.addObject(name);
            encoders.sourceClasses.addObject(returnType.getJavaClass());
        }
        for (HostedType parameterType : parameterTypes) {
            encoders.sourceClasses.addObject(parameterType.getJavaClass());
        }
        for (HostedType exceptionType : exceptionTypes) {
            encoders.sourceClasses.addObject(exceptionType.getJavaClass());
        }
        encoders.sourceMethodNames.addObject(signature);
        /* Register string and class values in annotations */
        AnalysisMethod analysisMethod = hostedMethod.getWrapped();
        AnnotationValue[] annotations = registerAnnotationValues(analysisMethod);
        AnnotationValue[][] parameterAnnotations = registerParameterAnnotationValues(analysisMethod);
        AnnotationMemberValue annotationDefault = isMethod ? registerAnnotationDefaultValues(analysisMethod) : null;
        TypeAnnotationValue[] typeAnnotations = registerTypeAnnotationValues(analysisMethod);
        ReflectParameterMetadata[] reflectParameters = registerReflectParameters(reflectMethod);
        JavaConstant accessorConstant = null;
        if (accessor != null) {
            accessorConstant = SubstrateObjectConstant.forObject(accessor);
            encoders.objectConstants.addObject(accessorConstant);
        }

        if (isMethod) {
            registerMethod(declaringType, reflectMethod, new MethodMetadata(declaringType, name, parameterTypes, modifiers, returnType, exceptionTypes, signature, annotations, parameterAnnotations,
                            annotationDefault, typeAnnotations, reflectParameters, accessorConstant));
        } else {
            registerConstructor(declaringType, reflectMethod, new ConstructorMetadata(declaringType, parameterTypes, modifiers, exceptionTypes, signature, annotations, parameterAnnotations,
                            typeAnnotations, reflectParameters, accessorConstant));
        }
    }

    private static final Method isFieldTrustedFinal = ReflectionUtil.lookupMethod(true, Field.class, "isTrustedFinal");

    private static boolean isTrustedFinal(Field field) {
        if (JavaVersionUtil.JAVA_SPEC < 17) {
            return false;
        }
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
        AnnotatedElement analysisObject = (AnnotatedElement) hostedObject.getWrapped();
        AnnotationValue[] annotations = registerAnnotationValues(analysisObject);
        AnnotationValue[][] parameterAnnotations = isExecutable ? registerParameterAnnotationValues((AnalysisMethod) analysisObject) : null;
        TypeAnnotationValue[] typeAnnotations = registerTypeAnnotationValues(analysisObject);
        AnnotationMemberValue annotationDefault = isMethod ? registerAnnotationDefaultValues((AnalysisMethod) analysisObject) : null;
        ReflectParameterMetadata[] reflectParameters = isExecutable ? registerReflectParameters((Executable) object) : null;
        AccessibleObject holder = ReflectionMetadataEncoder.getHolder(object);
        JavaConstant heapObjectConstant = SubstrateObjectConstant.forObject(holder);
        encoders.objectConstants.addObject(heapObjectConstant);

        AccessibleObjectMetadata metadata;
        if (isMethod) {
            metadata = new MethodMetadata(registered, heapObjectConstant, annotations, parameterAnnotations, annotationDefault, typeAnnotations, reflectParameters);
            registerMethod((HostedType) metaAccess.lookupJavaType(((Method) object).getDeclaringClass()), holder, (MethodMetadata) metadata);
        } else if (isExecutable) {
            metadata = new ConstructorMetadata(registered, heapObjectConstant, annotations, parameterAnnotations, typeAnnotations, reflectParameters);
            registerConstructor((HostedType) metaAccess.lookupJavaType(((Constructor<?>) object).getDeclaringClass()), holder, (ConstructorMetadata) metadata);
        } else {
            metadata = new FieldMetadata(registered, heapObjectConstant, annotations, typeAnnotations);
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
                encoders.sourceClasses.addObject(type.getJavaClass());
                includedClasses.add(type);
            }
        }
        return includedClasses.toArray(new HostedType[0]);
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
        for (Class<?> type : annotationValue.getTypes()) {
            encoders.sourceClasses.addObject(type);
        }
        for (String string : annotationValue.getStrings()) {
            encoders.sourceMethodNames.addObject(string);
        }
        for (JavaConstant proxy : annotationValue.getExceptionProxies()) {
            encoders.objectConstants.addObject(proxy);
        }
    }

    private ReflectParameterMetadata[] registerReflectParameters(Executable executable) {
        ReflectParameterMetadata[] reflectParameters = getReflectParameters(executable);
        if (reflectParameters != null) {
            for (ReflectParameterMetadata parameter : reflectParameters) {
                encoders.sourceMethodNames.addObject(parameter.name);
            }
        }
        return reflectParameters;
    }

    @Override
    public void addHidingFieldMetadata(AnalysisField analysisField, HostedType declaringType, String name, HostedType type, int modifiers) {
        /* Fill encoders with the necessary values. */
        encoders.sourceMethodNames.addObject(name);
        encoders.sourceClasses.addObject(type.getJavaClass());

        sortedTypes.add(declaringType);
        registerField(declaringType, analysisField, new FieldMetadata(declaringType, name, type, modifiers));
    }

    @Override
    public void addHidingMethodMetadata(AnalysisMethod analysisMethod, HostedType declaringType, String name, HostedType[] parameterTypes, int modifiers, HostedType returnType) {
        /* Fill encoders with the necessary values. */
        encoders.sourceMethodNames.addObject(name);
        for (HostedType parameterType : parameterTypes) {
            encoders.sourceClasses.addObject(parameterType.getJavaClass());
        }
        encoders.sourceClasses.addObject(returnType.getJavaClass());

        sortedTypes.add(declaringType);
        registerMethod(declaringType, analysisMethod, new MethodMetadata(declaringType, name, parameterTypes, modifiers, returnType));
    }

    @Override
    public void addReachableFieldMetadata(HostedField field) {
        HostedType declaringType = field.getDeclaringClass();
        String name = field.getName();

        /* Fill encoders with the necessary values. */
        encoders.sourceMethodNames.addObject(name);

        registerField(declaringType, field, new FieldMetadata(declaringType, name));
    }

    @Override
    public void addReachableExecutableMetadata(HostedMethod executable) {
        boolean isMethod = !executable.isConstructor();
        HostedType declaringType = executable.getDeclaringClass();
        String name = isMethod ? executable.getName() : null;
        HostedType[] parameterTypes = getParameterTypes(executable);

        /* Fill encoders with the necessary values. */
        if (isMethod) {
            encoders.sourceMethodNames.addObject(name);
        }
        for (HostedType parameterType : parameterTypes) {
            encoders.sourceClasses.addObject(parameterType.getJavaClass());
        }

        if (isMethod) {
            registerMethod(declaringType, executable, new MethodMetadata(declaringType, name, parameterTypes));
        } else {
            registerConstructor(declaringType, executable, new ConstructorMetadata(declaringType, parameterTypes));
        }
    }

    private static HostedType[] getParameterTypes(HostedMethod method) {
        HostedType[] parameterTypes = new HostedType[method.getSignature().getParameterCount(false)];
        for (int i = 0; i < parameterTypes.length; ++i) {
            parameterTypes[i] = (HostedType) method.getSignature().getParameterType(i, null);
        }
        return parameterTypes;
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
        Object[] recordComponents = ImageSingletons.lookup(ReflectionHostedSupport.class).getRecordComponents(clazz);
        if (recordComponents == null) {
            return null;
        }
        RecordComponentMetadata[] metadata = new RecordComponentMetadata[recordComponents.length];
        for (int i = 0; i < recordComponents.length; ++i) {
            AnnotatedElement recordComponent = (AnnotatedElement) recordComponents[i];
            String name = getRecordComponentName(recordComponent);
            HostedType type = (HostedType) metaAccess.lookupJavaType(getRecordComponentType(recordComponent));
            String signature = getRecordComponentSignature(recordComponent);
            Method accessor = getRecordComponentAccessor(recordComponent);

            /* Fill encoders with the necessary values. */
            encoders.sourceMethodNames.addObject(name);
            encoders.sourceClasses.addObject(type.getJavaClass());
            encoders.sourceMethodNames.addObject(signature);
            /* Register string and class values in annotations */
            AnnotationValue[] annotations = registerAnnotationValues(recordComponent);
            TypeAnnotationValue[] typeAnnotations = registerTypeAnnotationValues(recordComponent);
            JavaConstant accessorConstant = null;
            if (accessor != null) {
                accessorConstant = SubstrateObjectConstant.forObject(accessor);
                encoders.objectConstants.addObject(accessorConstant);
            }

            metadata[i] = new RecordComponentMetadata(declaringType, name, type, signature, accessorConstant, annotations, typeAnnotations);
        }
        return metadata;
    }

    private static final Class<?> recordComponentClass;

    static {
        try {
            recordComponentClass = (JavaVersionUtil.JAVA_SPEC >= 17) ? Class.forName("java.lang.reflect.RecordComponent") : null;
        } catch (ClassNotFoundException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private static final Method getRecordComponentName = (JavaVersionUtil.JAVA_SPEC >= 17) ? ReflectionUtil.lookupMethod(recordComponentClass, "getName") : null;
    private static final Method getRecordComponentType = (JavaVersionUtil.JAVA_SPEC >= 17) ? ReflectionUtil.lookupMethod(recordComponentClass, "getType") : null;
    private static final Method getRecordComponentSignature = (JavaVersionUtil.JAVA_SPEC >= 17) ? ReflectionUtil.lookupMethod(recordComponentClass, "getGenericSignature") : null;
    private static final Method getRecordComponentAccessor = (JavaVersionUtil.JAVA_SPEC >= 17) ? ReflectionUtil.lookupMethod(recordComponentClass, "getAccessor") : null;

    private static String getRecordComponentName(Object recordComponent) {
        try {
            return (String) getRecordComponentName.invoke(recordComponent);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private static Class<?> getRecordComponentType(Object recordComponent) {
        try {
            return (Class<?>) getRecordComponentType.invoke(recordComponent);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private static String getRecordComponentSignature(Object recordComponent) {
        try {
            return (String) getRecordComponentSignature.invoke(recordComponent);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private static Method getRecordComponentAccessor(Object recordComponent) {
        try {
            return (Method) getRecordComponentAccessor.invoke(recordComponent);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    /**
     * See {@link ReflectionMetadataDecoderImpl} for the encoding format description.
     */
    @Override
    public void encodeAllAndInstall() {
        UnsafeArrayTypeWriter buf = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());
        int typesIndex = encodeAndAddCollection(buf, sortedTypes.toArray(new HostedType[0]), this::encodeType, false);
        assert typesIndex == 0;
        for (HostedType declaringType : sortedTypes) {
            DynamicHub hub = declaringType.getHub();
            ClassMetadata classMetadata = classData.get(declaringType);

            int enclosingMethodInfoIndex = classMetadata.enclosingMethodInfo instanceof Throwable
                            ? encodeErrorIndex((Throwable) classMetadata.enclosingMethodInfo)
                            : addElement(buf, encodeEnclosingMethodInfo((Object[]) classMetadata.enclosingMethodInfo));
            int annotationsIndex = addEncodedElement(buf, encodeAnnotations(classMetadata.annotations));
            int typeAnnotationsIndex = addEncodedElement(buf, encodeTypeAnnotations(classMetadata.typeAnnotations));
            int classesEncodingIndex = encodeAndAddCollection(buf, classMetadata.classes, this::encodeType, false);
            int permittedSubclassesIndex = JavaVersionUtil.JAVA_SPEC >= 17 ? encodeAndAddCollection(buf, classMetadata.permittedSubclasses, this::encodeType, true) : NO_DATA;
            if (anySet(enclosingMethodInfoIndex, annotationsIndex, typeAnnotationsIndex, classesEncodingIndex, permittedSubclassesIndex)) {
                hub.setHubMetadata(enclosingMethodInfoIndex, annotationsIndex, typeAnnotationsIndex, classesEncodingIndex, permittedSubclassesIndex);
            }

            int fieldsIndex = encodeAndAddCollection(buf, getFields(declaringType), this::encodeField, false);
            int methodsIndex = encodeAndAddCollection(buf, getMethods(declaringType), this::encodeExecutable, false);
            int constructorsIndex = encodeAndAddCollection(buf, getConstructors(declaringType), this::encodeExecutable, false);
            int recordComponentsIndex = JavaVersionUtil.JAVA_SPEC >= 17 ? encodeAndAddCollection(buf, classMetadata.recordComponents, this::encodeRecordComponent, true) : NO_DATA;
            int classAccessFlags = classMetadata.classAccessFlags;
            if (anySet(fieldsIndex, methodsIndex, constructorsIndex, recordComponentsIndex) || classAccessFlags != hub.getModifiers()) {
                hub.setReflectionMetadata(fieldsIndex, methodsIndex, constructorsIndex, recordComponentsIndex, classAccessFlags);
            }
        }
        for (AccessibleObjectMetadata metadata : heapData) {
            AccessibleObject heapObject = (AccessibleObject) SubstrateObjectConstant.asObject(metadata.heapObject);
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
        ImageSingletons.add(EncodedReflectionMetadataSupplier.class, this);
    }

    private int encodeErrorIndex(Throwable error) {
        int index = encoders.objectConstants.getIndex(SubstrateObjectConstant.forObject(error));
        int encodedIndex = FIRST_ERROR_INDEX - index;
        VMError.guarantee(ReflectionMetadataDecoderImpl.isErrorIndex(encodedIndex));
        return encodedIndex;
    }

    private static <T> int encodeAndAddCollection(UnsafeArrayTypeWriter buf, T[] data, BiConsumer<UnsafeArrayTypeWriter, T> encodeCallback, boolean canBeNull) {
        if (data == null || (!canBeNull && data.length == 0)) {
            /*
             * We must encode a zero-length array if it does not have the same meaning as a null
             * array (e.g. for permitted classes)
             */
            return NO_DATA;
        }
        int offset = TypeConversion.asS4(buf.getBytesWritten());
        encodeArray(buf, data, element -> encodeCallback.accept(buf, element));
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
        ImageSingletons.lookup(ReflectionMetadataEncoding.class).setEncoding(encodingBuffer.toArray(dataEncoding));
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
        buf.putUV(modifiers);
        if (field.heapObject != null) {
            encodeObject(buf, field.heapObject);
        } else {
            encodeName(buf, field.name);
            if (field.complete || field.hiding) {
                encodeType(buf, field.type);
            }
            if (field.complete) {
                if (JavaVersionUtil.JAVA_SPEC >= 17) {
                    buf.putU1(field.trustedFinal ? 1 : 0);
                }
                encodeName(buf, field.signature);
                encodeByteArray(buf, encodeAnnotations(field.annotations));
                encodeByteArray(buf, encodeTypeAnnotations(field.typeAnnotations));
                buf.putSV(field.offset);
                encodeName(buf, field.deletedReason);
            }
        }
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
        buf.putUV(modifiers);
        if (executable.heapObject != null) {
            encodeObject(buf, executable.heapObject);
        } else {
            if (isMethod) {
                encodeName(buf, ((MethodMetadata) executable).name);
            }
            encodeArray(buf, executable.parameterTypes, parameterType -> encodeType(buf, parameterType));
            if (isMethod && (executable.complete || isHiding)) {
                encodeType(buf, ((MethodMetadata) executable).returnType);
            }
            if (executable.complete) {
                encodeArray(buf, executable.exceptionTypes, exceptionType -> encodeType(buf, exceptionType));
                encodeName(buf, executable.signature);
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
        buf.putSV(encoders.sourceClasses.getIndex(type));
    }

    private void encodeName(UnsafeArrayTypeWriter buf, String name) {
        buf.putSV(encoders.sourceMethodNames.getIndex(name));
    }

    private void encodeObject(UnsafeArrayTypeWriter buf, JavaConstant object) {
        if (object == null) {
            buf.putSV(NULL_OBJECT);
        } else {
            buf.putSV(encoders.objectConstants.getIndex(object));
        }
    }

    private static <T> void encodeArray(UnsafeArrayTypeWriter buf, T[] array, Consumer<T> elementEncoder) {
        buf.putUV(array.length);
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
     * {@link Target_sun_reflect_annotation_AnnotationParser})
     */
    public byte[] encodeAnnotations(AnnotationValue[] annotations) {
        if (annotations.length == 0) {
            return null;
        }
        UnsafeArrayTypeWriter buf = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess(), true);
        AnnotationEncoder.encodeArray(buf, annotations, annotation -> AnnotationEncoder.encodeAnnotation(buf, annotation, encoders));
        return buf.toArray();
    }

    private byte[] encodeParameterAnnotations(AnnotationValue[][] parameterAnnotations) {
        if (parameterAnnotations.length == 0) {
            return null;
        }
        UnsafeArrayTypeWriter buf = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess(), true);
        buf.putU1(parameterAnnotations.length);
        for (AnnotationValue[] annotations : parameterAnnotations) {
            AnnotationEncoder.encodeArray(buf, annotations, annotation -> AnnotationEncoder.encodeAnnotation(buf, annotation, encoders));
        }
        return buf.toArray();
    }

    private byte[] encodeAnnotationDefault(AnnotationMemberValue annotationDefault) {
        if (annotationDefault == null) {
            return null;
        }
        UnsafeArrayTypeWriter buf = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess(), true);
        AnnotationEncoder.encodeAnnotationMember(buf, annotationDefault, encoders);
        return buf.toArray();
    }

    public byte[] encodeTypeAnnotations(TypeAnnotationValue[] typeAnnotations) {
        if (typeAnnotations.length == 0) {
            return null;
        }
        UnsafeArrayTypeWriter buf = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess(), true);
        AnnotationEncoder.encodeArray(buf, typeAnnotations,
                        typeAnnotation -> AnnotationEncoder.encodeTypeAnnotation(buf, typeAnnotation, encoders));
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
        encodeName(buf, reflectParameter.name);
        buf.putUV(reflectParameter.modifiers);
    }

    private void encodeRecordComponent(UnsafeArrayTypeWriter buf, RecordComponentMetadata recordComponent) {
        encodeName(buf, recordComponent.name);
        encodeType(buf, recordComponent.type);
        encodeName(buf, recordComponent.signature);
        encodeObject(buf, recordComponent.accessor);
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
        encodeName(buf, (String) enclosingMethodInfo[1]);
        encodeName(buf, (String) enclosingMethodInfo[2]);
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
