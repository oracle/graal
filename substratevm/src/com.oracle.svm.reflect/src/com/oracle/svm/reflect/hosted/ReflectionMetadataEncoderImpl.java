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
package com.oracle.svm.reflect.hosted;

import static com.oracle.svm.reflect.target.ReflectionMetadataDecoderImpl.COMPLETE_FLAG_MASK;
import static com.oracle.svm.reflect.target.ReflectionMetadataDecoderImpl.HIDING_FLAG_MASK;
import static com.oracle.svm.reflect.target.ReflectionMetadataDecoderImpl.IN_HEAP_FLAG_MASK;
import static com.oracle.svm.reflect.target.ReflectionMetadataDecoderImpl.NULL_OBJECT;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.core.common.util.TypeConversion;
import org.graalvm.compiler.core.common.util.UnsafeArrayTypeWriter;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;
import org.graalvm.util.GuardedAnnotationAccess;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.code.CodeInfoEncoder;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.reflect.ReflectionMetadataDecoder;
import com.oracle.svm.core.reflect.Target_jdk_internal_reflect_ConstantPool;
import com.oracle.svm.core.util.ByteArrayReader;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.image.NativeImageCodeCache.ReflectionMetadataEncoder;
import com.oracle.svm.hosted.image.NativeImageCodeCache.ReflectionMetadataEncoderFactory;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.substitute.DeletedElementException;
import com.oracle.svm.reflect.hosted.ReflectionMetadata.AccessibleObjectMetadata;
import com.oracle.svm.reflect.hosted.ReflectionMetadata.ClassMetadata;
import com.oracle.svm.reflect.hosted.ReflectionMetadata.ConstructorMetadata;
import com.oracle.svm.reflect.hosted.ReflectionMetadata.ExecutableMetadata;
import com.oracle.svm.reflect.hosted.ReflectionMetadata.FieldMetadata;
import com.oracle.svm.reflect.hosted.ReflectionMetadata.MethodMetadata;
import com.oracle.svm.reflect.hosted.ReflectionMetadata.RecordComponentMetadata;
import com.oracle.svm.reflect.hosted.ReflectionMetadata.ReflectParameterMetadata;
import com.oracle.svm.reflect.target.ReflectionMetadataDecoderImpl;
import com.oracle.svm.reflect.target.ReflectionMetadataEncoding;
import com.oracle.svm.reflect.target.Target_sun_reflect_annotation_AnnotationParser;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import sun.invoke.util.Wrapper;
import sun.reflect.annotation.AnnotationType;
import sun.reflect.annotation.EnumConstantNotPresentExceptionProxy;
import sun.reflect.annotation.ExceptionProxy;
import sun.reflect.annotation.TypeAnnotation;
import sun.reflect.annotation.TypeAnnotationParser;
import sun.reflect.annotation.TypeNotPresentExceptionProxy;

/**
 * The method metadata encoding puts data in the image for three distinct types of methods.
 * <ol>
 * <li>Methods that are queried for reflection, but never accessed: in that case, the encoding
 * includes everything required to recreate an {@link Executable} object at runtime.</li>
 * <li>Methods that hide a method registered for reflection, but are not registered themselves: only
 * basic method information is stored for those methods (declaring class, name and parameter types).
 * They are used to ensure that the hidden superclass method is not incorrectly returned by a
 * reflection query on the subclass where it is hidden.</li>
 * <li>Methods that are included in the image: all reachable methods have their basic information
 * included to enable introspecting the produced executable.</li>
 * </ol>
 *
 * Emitting the metadata happens in two phases. In the first phase, the string and class encoders
 * are filled with the necessary values (in the {@code add*MethodMetadata} functions). In a second
 * phase, the values are encoded as byte arrays and stored in {@link DynamicHub} arrays (see
 * {@link #encodeAllAndInstall()}).
 */
public class ReflectionMetadataEncoderImpl implements ReflectionMetadataEncoder {

    static class Factory implements ReflectionMetadataEncoderFactory {
        @Override
        public ReflectionMetadataEncoder create(CodeInfoEncoder.Encoders encoders) {
            return new ReflectionMetadataEncoderImpl(encoders);
        }
    }

    private final CodeInfoEncoder.Encoders encoders;
    private final Map<Pair<Annotation, String>, JavaConstant> annotationExceptionProxies = new HashMap<>();
    private final TreeSet<HostedType> sortedTypes = new TreeSet<>(Comparator.comparingLong(t -> t.getHub().getTypeID()));
    private final Map<HostedType, ClassMetadata> classData = new HashMap<>();
    private final Map<HostedType, Set<FieldMetadata>> fieldData = new HashMap<>();
    private final Map<HostedType, Set<MethodMetadata>> methodData = new HashMap<>();
    private final Map<HostedType, Set<ConstructorMetadata>> constructorData = new HashMap<>();

    private final Set<AccessibleObjectMetadata> heapData = new HashSet<>();

    private final Map<AccessibleObject, byte[]> annotationsEncodings = new HashMap<>();
    private final Map<Executable, byte[]> parameterAnnotationsEncodings = new HashMap<>();
    private final Map<Method, byte[]> annotationDefaultEncodings = new HashMap<>();
    private final Map<AccessibleObject, byte[]> typeAnnotationsEncodings = new HashMap<>();
    private final Map<Executable, byte[]> reflectParametersEncodings = new HashMap<>();

    public ReflectionMetadataEncoderImpl(CodeInfoEncoder.Encoders encoders) {
        this.encoders = encoders;
    }

    private void registerClass(HostedType type, ClassMetadata metadata) {
        sortedTypes.add(type);
        classData.put(type, metadata);
    }

    private void registerField(HostedType declaringType, FieldMetadata metadata) {
        sortedTypes.add(declaringType);
        fieldData.computeIfAbsent(declaringType, t -> new HashSet<>()).add(metadata);
    }

    private void registerMethod(HostedType declaringType, MethodMetadata metadata) {
        sortedTypes.add(declaringType);
        methodData.computeIfAbsent(declaringType, t -> new HashSet<>()).add(metadata);
    }

    private void registerConstructor(HostedType declaringType, ConstructorMetadata metadata) {
        sortedTypes.add(declaringType);
        constructorData.computeIfAbsent(declaringType, t -> new HashSet<>()).add(metadata);
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
        Object[] enclosingMethodInfo = getEnclosingMethodInfo(javaClass);
        HostedType enclosingMethodDeclaringClass = enclosingMethodInfo != null ? ((HostedMetaAccess) metaAccess).lookupJavaType((Class<?>) enclosingMethodInfo[0]) : null;
        String enclosingMethodName = enclosingMethodInfo != null ? (String) enclosingMethodInfo[1] : null;
        String enclosingMethodDescriptor = enclosingMethodInfo != null ? (String) enclosingMethodInfo[2] : null;
        RecordComponentMetadata[] recordComponents = getRecordComponents(metaAccess, type, javaClass);
        Class<?>[] permittedSubclasses = getPermittedSubclasses(metaAccess, javaClass);
        Annotation[] annotations = GuardedAnnotationAccess.getDeclaredAnnotations(type);
        TypeAnnotation[] typeAnnotations = getTypeAnnotations(javaClass);

        /* Register string and class values in annotations */
        encoders.sourceClasses.addObject(javaClass);
        if (enclosingMethodInfo != null) {
            encoders.sourceClasses.addObject(enclosingMethodDeclaringClass.getJavaClass());
            encoders.sourceMethodNames.addObject(enclosingMethodName);
            encoders.sourceMethodNames.addObject(enclosingMethodDescriptor);
        }
        HostedType[] innerTypes = registerClassValues(metaAccess, innerClasses);
        HostedType[] permittedSubtypes = (permittedSubclasses != null) ? registerClassValues(metaAccess, permittedSubclasses) : null;
        annotations = registerAnnotationValues(metaAccess, annotations);
        typeAnnotations = registerTypeAnnotationValues(metaAccess, typeAnnotations);

        registerClass(type, new ClassMetadata(innerTypes, enclosingMethodDeclaringClass, enclosingMethodName, enclosingMethodDescriptor, recordComponents, permittedSubtypes, annotations,
                        typeAnnotations));
    }

    private static final Method getEnclosingMethodInfo = ReflectionUtil.lookupMethod(Class.class, "getEnclosingMethod0");

    private static Object[] getEnclosingMethodInfo(Class<?> clazz) {
        try {
            return (Object[]) getEnclosingMethodInfo.invoke(clazz);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw GraalError.shouldNotReachHere(e);
        }
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
                HostedType hostedType = ((HostedMetaAccess) metaAccess).optionalLookupJavaType(permittedSubclass).orElse(null);
                if (hostedType != null && hostedType.getWrapped().isReachable()) {
                    reachablePermittedSubclasses.add(permittedSubclass);
                }
            }
            return reachablePermittedSubclasses.toArray(new Class<?>[0]);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw GraalError.shouldNotReachHere(e);
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
        Annotation[] annotations = GuardedAnnotationAccess.getDeclaredAnnotations(hostedField);
        TypeAnnotation[] typeAnnotations = getTypeAnnotations(reflectField);
        int offset = hostedField.wrapped.isUnsafeAccessed() ? hostedField.getOffset() : SharedField.LOC_UNINITIALIZED;
        Delete deleteAnnotation = GuardedAnnotationAccess.getAnnotation(hostedField, Delete.class);
        String deletedReason = (deleteAnnotation != null) ? deleteAnnotation.value() : null;

        /* Fill encoders with the necessary values. */
        encoders.sourceMethodNames.addObject(name);
        encoders.sourceClasses.addObject(type.getJavaClass());
        encoders.sourceMethodNames.addObject(signature);
        encoders.sourceMethodNames.addObject(deletedReason);
        /* Register string and class values in annotations */
        annotations = registerAnnotationValues(metaAccess, annotations);
        typeAnnotations = registerTypeAnnotationValues(metaAccess, typeAnnotations);

        registerField(declaringType, new FieldMetadata(declaringType, name, type, modifiers, trustedFinal, signature,
                        annotations, typeAnnotations, offset, deletedReason));
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
        Annotation[] annotations = GuardedAnnotationAccess.getDeclaredAnnotations(hostedMethod);
        Annotation[][] parameterAnnotations = reflectMethod.getParameterAnnotations();
        Object annotationDefault = isMethod ? ((Method) reflectMethod).getDefaultValue() : null;
        TypeAnnotation[] typeAnnotations = getTypeAnnotations(reflectMethod);
        ReflectParameterMetadata[] reflectParameters = getReflectParameters(reflectMethod);

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
        annotations = registerAnnotationValues(metaAccess, annotations);
        for (int i = 0; i < parameterAnnotations.length; ++i) {
            parameterAnnotations[i] = registerAnnotationValues(metaAccess, parameterAnnotations[i]);
        }
        if (isMethod && annotationDefault != null) {
            registerAnnotationValue(annotationDefault.getClass(), annotationDefault);
        }
        typeAnnotations = registerTypeAnnotationValues(metaAccess, typeAnnotations);
        if (reflectParameters != null) {
            for (ReflectParameterMetadata parameter : reflectParameters) {
                encoders.sourceMethodNames.addObject(parameter.name);
            }
        }
        JavaConstant accessorConstant = null;
        if (accessor != null) {
            accessorConstant = SubstrateObjectConstant.forObject(accessor);
            encoders.objectConstants.addObject(accessorConstant);
        }

        if (isMethod) {
            registerMethod(declaringType, new MethodMetadata(declaringType, name, parameterTypes, modifiers, returnType, exceptionTypes, signature,
                            annotations, parameterAnnotations, annotationDefault, typeAnnotations, reflectParameters, accessorConstant));
        } else {
            registerConstructor(declaringType, new ConstructorMetadata(declaringType, parameterTypes, modifiers, exceptionTypes, signature, annotations,
                            parameterAnnotations, typeAnnotations, reflectParameters, accessorConstant));
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
            throw GraalError.shouldNotReachHere(e);
        }
    }

    @Override
    public void addHeapObjectMetadata(MetaAccessProvider metaAccess, AccessibleObject object) {
        boolean isExecutable = object instanceof Executable;
        boolean isMethod = object instanceof Method;
        Annotation[] annotations = GuardedAnnotationAccess.getDeclaredAnnotations(object);
        Annotation[][] parameterAnnotations = isExecutable ? ((Executable) object).getParameterAnnotations() : null;
        Object annotationDefault = isMethod ? ((Method) object).getDefaultValue() : null;
        TypeAnnotation[] typeAnnotations = getTypeAnnotations(object);
        ReflectParameterMetadata[] reflectParameters = isExecutable ? getReflectParameters((Executable) object) : null;

        /* Register string and class values in annotations */
        annotations = registerAnnotationValues(metaAccess, annotations);
        typeAnnotations = registerTypeAnnotationValues(metaAccess, typeAnnotations);
        if (isExecutable) {
            for (int i = 0; i < parameterAnnotations.length; ++i) {
                parameterAnnotations[i] = registerAnnotationValues(metaAccess, parameterAnnotations[i]);
            }
            if (isMethod && annotationDefault != null) {
                registerAnnotationValue(annotationDefault.getClass(), annotationDefault);
            }
            if (reflectParameters != null) {
                for (ReflectParameterMetadata parameter : reflectParameters) {
                    encoders.sourceMethodNames.addObject(parameter.name);
                }
            }
        }
        JavaConstant heapObjectConstant = SubstrateObjectConstant.forObject(getHolder(object));
        encoders.objectConstants.addObject(heapObjectConstant);

        AccessibleObjectMetadata metadata;
        if (isMethod) {
            metadata = new MethodMetadata(heapObjectConstant, annotations, parameterAnnotations, annotationDefault, typeAnnotations, reflectParameters);
            registerMethod((HostedType) metaAccess.lookupJavaType(((Method) object).getDeclaringClass()), (MethodMetadata) metadata);
        } else if (isExecutable) {
            metadata = new ConstructorMetadata(heapObjectConstant, annotations, parameterAnnotations, typeAnnotations, reflectParameters);
            registerConstructor((HostedType) metaAccess.lookupJavaType(((Constructor<?>) object).getDeclaringClass()), (ConstructorMetadata) metadata);
        } else {
            metadata = new FieldMetadata(heapObjectConstant, annotations, typeAnnotations);
            registerField((HostedType) metaAccess.lookupJavaType(((Field) object).getDeclaringClass()), (FieldMetadata) metadata);
        }
        heapData.add(metadata);
    }

    private static final Method getRoot = ReflectionUtil.lookupMethod(AccessibleObject.class, "getRoot");

    private static AccessibleObject getHolder(AccessibleObject accessibleObject) {
        try {
            AccessibleObject root = (AccessibleObject) getRoot.invoke(accessibleObject);
            return root == null ? accessibleObject : root;
        } catch (InvocationTargetException | IllegalAccessException e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }

    private static final Method parseAllTypeAnnotations = ReflectionUtil.lookupMethod(TypeAnnotationParser.class, "parseAllTypeAnnotations", AnnotatedElement.class);

    static TypeAnnotation[] getTypeAnnotations(AnnotatedElement annotatedElement) {
        try {
            return (TypeAnnotation[]) parseAllTypeAnnotations.invoke(null, annotatedElement);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw GraalError.shouldNotReachHere(e);
        }
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

    private Annotation[] registerAnnotationValues(MetaAccessProvider metaAccess, Annotation... annotations) {
        Set<Annotation> includedAnnotations = new HashSet<>();
        for (Annotation annotation : annotations) {
            if (registerAnnotation(metaAccess, annotation)) {
                includedAnnotations.add(annotation);
            }
        }
        return includedAnnotations.toArray(new Annotation[0]);
    }

    private TypeAnnotation[] registerTypeAnnotationValues(MetaAccessProvider metaAccess, TypeAnnotation... typeAnnotations) {
        Set<TypeAnnotation> includedTypeAnnotations = new HashSet<>();
        for (TypeAnnotation typeAnnotation : typeAnnotations) {
            // Checkstyle: allow direct annotation access
            Annotation annotation = typeAnnotation.getAnnotation();
            // Checkstyle: disallow direct annotation access
            if (registerAnnotation(metaAccess, annotation)) {
                includedTypeAnnotations.add(typeAnnotation);
            }
        }
        return includedTypeAnnotations.toArray(new TypeAnnotation[0]);
    }

    private boolean registerAnnotation(MetaAccessProvider metaAccess, Annotation annotation) {
        /*
         * Only include annotations types that have a chance to be queried at runtime.
         */
        HostedType annotationType = ((HostedMetaAccess) metaAccess).optionalLookupJavaType(annotation.annotationType()).orElse(null);
        if (annotationType != null && annotationType.getWrapped().isReachable()) {
            encoders.sourceClasses.addObject(annotation.annotationType());
            registerAnnotationValue(annotation.annotationType(), annotation);
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void registerAnnotationValue(Class<?> type, Object value) {
        if (type.isAnnotation()) {
            Annotation annotation = (Annotation) value;
            AnnotationType annotationType = AnnotationType.getInstance((Class<? extends Annotation>) type);
            encoders.sourceClasses.addObject(type);
            for (Map.Entry<String, Class<?>> entry : annotationType.memberTypes().entrySet()) {
                String valueName = entry.getKey();
                Class<?> valueType = entry.getValue();
                encoders.sourceMethodNames.addObject(valueName);
                Method getAnnotationValue = annotationType.members().get(valueName);
                getAnnotationValue.setAccessible(true);
                Object annotationValue;
                try {
                    annotationValue = getAnnotationValue.invoke(annotation);
                    registerAnnotationValue(valueType, annotationValue);
                } catch (IllegalAccessException e) {
                    throw GraalError.shouldNotReachHere(e);
                } catch (InvocationTargetException e) {
                    Throwable targetException = e.getTargetException();
                    ExceptionProxy exceptionProxy;
                    if (targetException instanceof TypeNotPresentException) {
                        exceptionProxy = new TypeNotPresentExceptionProxy(((TypeNotPresentException) targetException).typeName(), targetException.getCause());
                    } else if (targetException instanceof EnumConstantNotPresentException) {
                        EnumConstantNotPresentException enumException = (EnumConstantNotPresentException) targetException;
                        exceptionProxy = new EnumConstantNotPresentExceptionProxy((Class<? extends Enum<?>>) enumException.enumType(), enumException.constantName());
                    } else {
                        throw GraalError.shouldNotReachHere(e);
                    }
                    JavaConstant javaConstant = annotationExceptionProxies.computeIfAbsent(Pair.create(annotation, valueName), (ignored) -> SubstrateObjectConstant.forObject(exceptionProxy));
                    encoders.objectConstants.addObject(javaConstant);
                }
            }
        } else if (type.isArray()) {
            Class<?> componentType = type.getComponentType();
            if (!componentType.isPrimitive()) {
                for (Object val : (Object[]) value) {
                    registerAnnotationValue(componentType, val);
                }
            }
        } else if (type == Class.class) {
            encoders.sourceClasses.addObject((Class<?>) value);
        } else if (type == String.class) {
            encoders.sourceMethodNames.addObject((String) value);
        } else if (type.isEnum()) {
            encoders.sourceClasses.addObject(type);
            encoders.sourceMethodNames.addObject(((Enum<?>) value).name());
        }
    }

    @Override
    public void addHidingMethodMetadata(HostedType declaringType, String name, HostedType[] parameterTypes, int modifiers, HostedType returnType) {
        /* Fill encoders with the necessary values. */
        encoders.sourceMethodNames.addObject(name);
        for (HostedType parameterType : parameterTypes) {
            encoders.sourceClasses.addObject(parameterType.getJavaClass());
        }
        encoders.sourceClasses.addObject(returnType.getJavaClass());

        sortedTypes.add(declaringType);
        registerMethod(declaringType, new MethodMetadata(true, declaringType, name, parameterTypes, modifiers, returnType));
    }

    @Override
    public void addReachableFieldMetadata(HostedField field) {
        HostedType declaringType = field.getDeclaringClass();
        String name = field.getName();

        /* Fill encoders with the necessary values. */
        encoders.sourceMethodNames.addObject(name);

        registerField(declaringType, new FieldMetadata(declaringType, name));
    }

    @Override
    public void addReachableMethodMetadata(HostedMethod method) {
        boolean isMethod = !method.isConstructor();
        HostedType declaringType = method.getDeclaringClass();
        String name = isMethod ? method.getName() : null;
        HostedType[] parameterTypes = getParameterTypes(method);
        int modifiers = method.getModifiers();

        /* Fill encoders with the necessary values. */
        if (isMethod) {
            encoders.sourceMethodNames.addObject(name);
        }
        for (HostedType parameterType : parameterTypes) {
            encoders.sourceClasses.addObject(parameterType.getJavaClass());
        }

        if (isMethod) {
            registerMethod(declaringType, new MethodMetadata(false, declaringType, name, parameterTypes, modifiers, null));
        } else {
            registerConstructor(declaringType, new ConstructorMetadata(declaringType, parameterTypes, modifiers));
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
        Object[] recordComponents = ImageSingletons.lookup(RuntimeReflectionSupport.class).getRecordComponents(clazz);
        if (recordComponents == null) {
            return null;
        }
        Set<RecordComponentMetadata> metadata = new HashSet<>();
        for (Object recordComponent : recordComponents) {
            String name = getRecordComponentName(recordComponent);
            HostedType type = (HostedType) metaAccess.lookupJavaType(getRecordComponentType(recordComponent));
            String signature = getRecordComponentSignature(recordComponent);
            Method accessor = getRecordComponentAccessor(recordComponent);
            Annotation[] annotations = GuardedAnnotationAccess.getDeclaredAnnotations((AnnotatedElement) recordComponent);
            TypeAnnotation[] typeAnnotations = getTypeAnnotations((AnnotatedElement) recordComponent);

            /* Fill encoders with the necessary values. */
            encoders.sourceMethodNames.addObject(name);
            encoders.sourceClasses.addObject(type.getJavaClass());
            encoders.sourceMethodNames.addObject(signature);
            /* Register string and class values in annotations */
            annotations = registerAnnotationValues(metaAccess, annotations);
            typeAnnotations = registerTypeAnnotationValues(metaAccess, typeAnnotations);
            JavaConstant accessorConstant = null;
            if (accessor != null) {
                accessorConstant = SubstrateObjectConstant.forObject(accessor);
                encoders.objectConstants.addObject(accessorConstant);
            }

            metadata.add(new RecordComponentMetadata(declaringType, name, type, signature, accessorConstant, annotations, typeAnnotations));
        }
        return metadata.toArray(new RecordComponentMetadata[0]);
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
            throw GraalError.shouldNotReachHere(e);
        }
    }

    private static Class<?> getRecordComponentType(Object recordComponent) {
        try {
            return (Class<?>) getRecordComponentType.invoke(recordComponent);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }

    private static String getRecordComponentSignature(Object recordComponent) {
        try {
            return (String) getRecordComponentSignature.invoke(recordComponent);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }

    private static Method getRecordComponentAccessor(Object recordComponent) {
        try {
            return (Method) getRecordComponentAccessor.invoke(recordComponent);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }

    /**
     * See {@link ReflectionMetadataDecoderImpl} for the encoding format description.
     */
    @Override
    public void encodeAllAndInstall() {
        UnsafeArrayTypeWriter buf = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());
        encodeAndAddCollection(buf, sortedTypes.toArray(new HostedType[0]), this::encodeType, (ignored) -> {
        }, false);
        for (HostedType declaringType : sortedTypes) {
            DynamicHub hub = declaringType.getHub();
            ClassMetadata classMetadata = classData.get(declaringType);
            encodeAndAddCollection(buf, classMetadata.classes, this::encodeType, hub::setClassesEncodingIndex, false);
            encodeAndAddElement(buf, new Object[]{classMetadata.enclosingMethodDeclaringClass, classMetadata.enclosingMethodName, classMetadata.enclosingMethodDescriptor},
                            this::encodeEnclosingMethod, hub::setEnclosingMethodInfoIndex);
            if (JavaVersionUtil.JAVA_SPEC >= 17) {
                encodeAndAddCollection(buf, classMetadata.recordComponents, this::encodeRecordComponent, hub::setRecordComponentsEncodingIndex, true);
                encodeAndAddCollection(buf, classMetadata.permittedSubclasses, this::encodeType, hub::setPermittedSubclassesEncodingIndex, true);
            }
            encodeAndAddEncodedElement(buf, classMetadata.annotations, this::encodeAnnotations, hub::setAnnotationsEncodingIndex);
            encodeAndAddEncodedElement(buf, classMetadata.typeAnnotations, this::encodeTypeAnnotations,
                            hub::setTypeAnnotationsEncodingIndex);
            encodeAndAddCollection(buf, fieldData.getOrDefault(declaringType, Collections.emptySet()).toArray(new FieldMetadata[0]), this::encodeField, hub::setFieldsEncodingIndex,
                            false);
            encodeAndAddCollection(buf, methodData.getOrDefault(declaringType, Collections.emptySet()).toArray(new MethodMetadata[0]), this::encodeExecutable,
                            hub::setMethodsEncodingIndex, false);
            encodeAndAddCollection(buf, constructorData.getOrDefault(declaringType, Collections.emptySet()).toArray(new ConstructorMetadata[0]), this::encodeExecutable,
                            hub::setConstructorsEncodingIndex, false);
        }
        for (AccessibleObjectMetadata metadata : heapData) {
            AccessibleObject heapObject = (AccessibleObject) SubstrateObjectConstant.asObject(metadata.heapObject);
            encodeAndAddHeapElement(metadata.annotations, this::encodeAnnotations, (array) -> annotationsEncodings.put(heapObject, array));
            encodeAndAddHeapElement(metadata.typeAnnotations, this::encodeTypeAnnotations, (array) -> typeAnnotationsEncodings.put(heapObject, array));
            if (metadata instanceof ExecutableMetadata) {
                encodeAndAddHeapElement(((ExecutableMetadata) metadata).parameterAnnotations, this::encodeParameterAnnotations,
                                (array) -> parameterAnnotationsEncodings.put((Executable) heapObject, array));
                if (((ExecutableMetadata) metadata).reflectParameters != null) {
                    encodeAndAddHeapElement(((ExecutableMetadata) metadata).reflectParameters, this::encodeReflectParameters,
                                    (array) -> reflectParametersEncodings.put((Executable) heapObject, array));
                }
                if (metadata instanceof MethodMetadata && ((Method) SubstrateObjectConstant.asObject(metadata.heapObject)).getDeclaringClass().isAnnotation() &&
                                ((MethodMetadata) metadata).annotationDefault != null) {
                    encodeAndAddHeapElement(((MethodMetadata) metadata).annotationDefault, this::encodeMemberValue,
                                    (array) -> annotationDefaultEncodings.put((Method) heapObject, array));
                }
            }
        }
        install(buf);
        /* Enable field recomputers in reflection objects to see the computed values */
        ImageSingletons.add(ReflectionMetadataEncoder.class, this);
    }

    private static <T> void encodeAndAddCollection(UnsafeArrayTypeWriter buf, T[] data, BiConsumer<UnsafeArrayTypeWriter, T> encodeCallback, Consumer<Integer> saveCallback, boolean canBeNull) {
        int offset = ReflectionMetadataDecoder.NULL_ARRAY;
        if (!canBeNull || data != null) {
            offset = TypeConversion.asS4(buf.getBytesWritten());
            encodeArray(buf, data, element -> encodeCallback.accept(buf, element));
        }
        saveCallback.accept(offset);
    }

    private static <T> void encodeAndAddElement(UnsafeArrayTypeWriter buf, T data, Function<T, byte[]> encodeCallback, Consumer<Integer> saveCallback) {
        byte[] encoding = encodeCallback.apply(data);
        int offset = ReflectionMetadataDecoder.NULL_ARRAY;
        if (encoding != null) {
            offset = TypeConversion.asS4(buf.getBytesWritten());
            encodeBytes(buf, encoding);
        }
        saveCallback.accept(offset);
    }

    private static <T> void encodeAndAddEncodedElement(UnsafeArrayTypeWriter buf, T data, Function<T, byte[]> encodeCallback, Consumer<Integer> saveCallback) {
        int offset = TypeConversion.asS4(buf.getBytesWritten());
        encodeByteArray(buf, encodeCallback.apply(data));
        saveCallback.accept(offset);
    }

    private static <T> void encodeAndAddHeapElement(T data, Function<T, byte[]> encodeCallback, Consumer<byte[]> saveCallback) {
        byte[] encoding = encodeCallback.apply(data);
        saveCallback.accept(encoding);
    }

    private static void install(UnsafeArrayTypeWriter encodingBuffer) {
        int encodingSize = TypeConversion.asS4(encodingBuffer.getBytesWritten());
        byte[] dataEncoding = new byte[encodingSize];
        ImageSingletons.lookup(ReflectionMetadataEncoding.class).setEncoding(encodingBuffer.toArray(dataEncoding));
    }

    private void encodeField(UnsafeArrayTypeWriter buf, FieldMetadata field) {
        assert (field.modifiers & COMPLETE_FLAG_MASK) == 0 && (field.modifiers & IN_HEAP_FLAG_MASK) == 0;
        int modifiers = field.modifiers;
        modifiers |= field.complete ? COMPLETE_FLAG_MASK : 0;
        modifiers |= field.heapObject != null ? IN_HEAP_FLAG_MASK : 0;
        buf.putUV(modifiers);
        if (field.heapObject != null) {
            encodeObject(buf, field.heapObject);
        } else {
            encodeName(buf, field.name);
            if (field.complete) {
                encodeType(buf, field.type);
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
        assert (executable.modifiers & COMPLETE_FLAG_MASK) == 0 && (executable.modifiers & IN_HEAP_FLAG_MASK) == 0 && (executable.modifiers & HIDING_FLAG_MASK) == 0;
        int modifiers = executable.modifiers;
        modifiers |= executable.complete ? COMPLETE_FLAG_MASK : 0;
        modifiers |= executable.heapObject != null ? IN_HEAP_FLAG_MASK : 0;
        modifiers |= isMethod && ((MethodMetadata) executable).hiding ? HIDING_FLAG_MASK : 0;
        buf.putUV(modifiers);
        if (executable.heapObject != null) {
            encodeObject(buf, executable.heapObject);
        } else {
            if (isMethod) {
                encodeName(buf, ((MethodMetadata) executable).name);
            }
            encodeArray(buf, executable.parameterTypes, parameterType -> encodeType(buf, parameterType));
            if (isMethod && (executable.complete || ((MethodMetadata) executable).hiding)) {
                encodeType(buf, ((MethodMetadata) executable).returnType);
            }
            if (executable.complete) {
                encodeArray(buf, executable.exceptionTypes, exceptionType -> encodeType(buf, exceptionType));
                encodeName(buf, executable.signature);
                encodeByteArray(buf, encodeAnnotations(executable.annotations));
                encodeByteArray(buf, encodeParameterAnnotations(executable.parameterAnnotations));
                if (isMethod && executable.declaringType.getHub().getHostedJavaClass().isAnnotation()) {
                    encodeByteArray(buf, encodeMemberValue(((MethodMetadata) executable).annotationDefault));
                } else {
                    assert !isMethod || ((MethodMetadata) executable).annotationDefault == null;
                }
                encodeByteArray(buf, encodeTypeAnnotations(executable.typeAnnotations));
                encodeByteArray(buf, encodeReflectParameters(executable.reflectParameters));
                encodeObject(buf, executable.accessor);
            }
        }
    }

    private void encodeType(UnsafeArrayTypeWriter buf, HostedType type) {
        buf.putSV(encoders.sourceClasses.getIndex(type.getJavaClass()));
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
            throw GraalError.shouldNotReachHere(e);
        }
    }

    private static String getSignature(Executable executable) {
        try {
            return (String) (executable instanceof Method ? getMethodSignature.invoke(executable) : getConstructorSignature.invoke(executable));
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }

    private static Parameter[] getRawParameters(Executable executable) {
        try {
            return (Parameter[]) getExecutableParameters.invoke(executable);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw GraalError.shouldNotReachHere(e);
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
    public byte[] encodeAnnotations(Annotation[] annotations) {
        UnsafeArrayTypeWriter buf = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess(), true);
        buf.putU2(annotations.length);
        for (Annotation annotation : annotations) {
            encodeAnnotation(buf, annotation);
        }
        return buf.toArray();
    }

    private byte[] encodeParameterAnnotations(Annotation[][] annotations) {
        UnsafeArrayTypeWriter buf = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess(), true);
        buf.putU1(annotations.length);
        for (Annotation[] parameterAnnotations : annotations) {
            buf.putU2(parameterAnnotations.length);
            for (Annotation parameterAnnotation : parameterAnnotations) {
                encodeAnnotation(buf, parameterAnnotation);
            }
        }
        return buf.toArray();
    }

    private void encodeAnnotation(UnsafeArrayTypeWriter buf, Annotation annotation) {
        buf.putS4(encoders.sourceClasses.getIndex(annotation.annotationType()));
        AnnotationType type = AnnotationType.getInstance(annotation.annotationType());
        buf.putU2(type.members().size());
        for (Map.Entry<String, Method> entry : type.members().entrySet()) {
            String memberName = entry.getKey();
            Method valueAccessor = entry.getValue();
            buf.putS4(encoders.sourceMethodNames.getIndex(memberName));
            try {
                encodeValue(buf, valueAccessor.invoke(annotation), type.memberTypes().get(memberName));
            } catch (InvocationTargetException e) {
                encodeValue(buf, annotationExceptionProxies.get(Pair.create(annotation, memberName)), Throwable.class);
            } catch (IllegalAccessException e) {
                throw GraalError.shouldNotReachHere(e);
            }
        }
    }

    private byte[] encodeMemberValue(Object value) {
        if (value == null) {
            return new byte[0];
        }
        UnsafeArrayTypeWriter buf = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess(), true);
        Class<?> type = value.getClass();
        if (Proxy.isProxyClass(type)) {
            assert type.getInterfaces().length == 1;
            type = type.getInterfaces()[0];
        }
        encodeValue(buf, value, type);
        return buf.toArray();
    }

    private void encodeValue(UnsafeArrayTypeWriter buf, Object value, Class<?> type) {
        buf.putU1(tag(type));
        if (type.isAnnotation()) {
            encodeAnnotation(buf, (Annotation) value);
        } else if (type.isEnum()) {
            buf.putS4(encoders.sourceClasses.getIndex(type));
            buf.putS4(encoders.sourceMethodNames.getIndex(((Enum<?>) value).name()));
        } else if (type.isArray()) {
            encodeArray(buf, value, type.getComponentType());
        } else if (type == Class.class) {
            buf.putS4(encoders.sourceClasses.getIndex((Class<?>) value));
        } else if (type == String.class) {
            buf.putS4(encoders.sourceMethodNames.getIndex((String) value));
        } else if (type.isPrimitive() || Wrapper.isWrapperType(type)) {
            Wrapper wrapper = type.isPrimitive() ? Wrapper.forPrimitiveType(type) : Wrapper.forWrapperType(type);
            switch (wrapper) {
                case BOOLEAN:
                    buf.putU1((boolean) value ? 1 : 0);
                    break;
                case BYTE:
                    buf.putS1((byte) value);
                    break;
                case SHORT:
                    buf.putS2((short) value);
                    break;
                case CHAR:
                    buf.putU2((char) value);
                    break;
                case INT:
                    buf.putS4((int) value);
                    break;
                case LONG:
                    buf.putS8((long) value);
                    break;
                case FLOAT:
                    buf.putS4(Float.floatToRawIntBits((float) value));
                    break;
                case DOUBLE:
                    buf.putS8(Double.doubleToRawLongBits((double) value));
                    break;
                default:
                    throw GraalError.shouldNotReachHere();
            }
        } else if (type == Throwable.class) {
            buf.putS4(encoders.objectConstants.getIndex((JavaConstant) value));
        } else {
            throw GraalError.shouldNotReachHere();
        }
    }

    private void encodeArray(UnsafeArrayTypeWriter buf, Object value, Class<?> componentType) {
        if (!componentType.isPrimitive()) {
            Object[] array = (Object[]) value;
            buf.putU2(array.length);
            for (Object val : array) {
                encodeValue(buf, val, componentType);
            }
        } else if (componentType == boolean.class) {
            boolean[] array = (boolean[]) value;
            buf.putU2(array.length);
            for (boolean val : array) {
                encodeValue(buf, val, componentType);
            }
        } else if (componentType == byte.class) {
            byte[] array = (byte[]) value;
            buf.putU2(array.length);
            for (byte val : array) {
                encodeValue(buf, val, componentType);
            }
        } else if (componentType == short.class) {
            short[] array = (short[]) value;
            buf.putU2(array.length);
            for (short val : array) {
                encodeValue(buf, val, componentType);
            }
        } else if (componentType == char.class) {
            char[] array = (char[]) value;
            buf.putU2(array.length);
            for (char val : array) {
                encodeValue(buf, val, componentType);
            }
        } else if (componentType == int.class) {
            int[] array = (int[]) value;
            buf.putU2(array.length);
            for (int val : array) {
                encodeValue(buf, val, componentType);
            }
        } else if (componentType == long.class) {
            long[] array = (long[]) value;
            buf.putU2(array.length);
            for (long val : array) {
                encodeValue(buf, val, componentType);
            }
        } else if (componentType == float.class) {
            float[] array = (float[]) value;
            buf.putU2(array.length);
            for (float val : array) {
                encodeValue(buf, val, componentType);
            }
        } else if (componentType == double.class) {
            double[] array = (double[]) value;
            buf.putU2(array.length);
            for (double val : array) {
                encodeValue(buf, val, componentType);
            }
        }
    }

    private static byte tag(Class<?> type) {
        if (type.isAnnotation()) {
            return '@';
        } else if (type.isEnum()) {
            return 'e';
        } else if (type.isArray()) {
            return '[';
        } else if (type == Class.class) {
            return 'c';
        } else if (type == String.class) {
            return 's';
        } else if (type.isPrimitive()) {
            return (byte) Wrapper.forPrimitiveType(type).basicTypeChar();
        } else if (Wrapper.isWrapperType(type)) {
            return (byte) Wrapper.forWrapperType(type).basicTypeChar();
        } else if (type == Throwable.class) {
            return 'E';
        } else {
            throw GraalError.shouldNotReachHere(type.toString());
        }
    }

    private byte[] encodeTypeAnnotations(TypeAnnotation[] annotations) {
        UnsafeArrayTypeWriter buf = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess(), true);
        buf.putU2(annotations.length);
        for (TypeAnnotation typeAnnotation : annotations) {
            encodeTypeAnnotation(buf, typeAnnotation);
        }
        return buf.toArray();
    }

    private void encodeTypeAnnotation(UnsafeArrayTypeWriter buf, TypeAnnotation typeAnnotation) {
        encodeTargetInfo(buf, typeAnnotation.getTargetInfo());
        encodeLocationInfo(buf, typeAnnotation.getLocationInfo());
        // Checkstyle: allow direct annotation access
        encodeAnnotation(buf, typeAnnotation.getAnnotation());
        // Checkstyle: disallow direct annotation access
    }

    private static final byte CLASS_TYPE_PARAMETER = 0x00;
    private static final byte METHOD_TYPE_PARAMETER = 0x01;
    private static final byte CLASS_EXTENDS = 0x10;
    private static final byte CLASS_TYPE_PARAMETER_BOUND = 0x11;
    private static final byte METHOD_TYPE_PARAMETER_BOUND = 0x12;
    private static final byte FIELD = 0x13;
    private static final byte METHOD_RETURN = 0x14;
    private static final byte METHOD_RECEIVER = 0x15;
    private static final byte METHOD_FORMAL_PARAMETER = 0x16;
    private static final byte THROWS = 0x17;

    private static void encodeTargetInfo(UnsafeArrayTypeWriter buf, TypeAnnotation.TypeAnnotationTargetInfo targetInfo) {
        switch (targetInfo.getTarget()) {
            case CLASS_TYPE_PARAMETER:
                buf.putU1(CLASS_TYPE_PARAMETER);
                buf.putU1(targetInfo.getCount());
                break;
            case METHOD_TYPE_PARAMETER:
                buf.putU1(METHOD_TYPE_PARAMETER);
                buf.putU1(targetInfo.getCount());
                break;
            case CLASS_EXTENDS:
                buf.putU1(CLASS_EXTENDS);
                buf.putS2(-1);
                break;
            case CLASS_IMPLEMENTS:
                buf.putU1(CLASS_EXTENDS);
                buf.putS2(targetInfo.getCount());
                break;
            case CLASS_TYPE_PARAMETER_BOUND:
                buf.putU1(CLASS_TYPE_PARAMETER_BOUND);
                buf.putU1(targetInfo.getCount());
                buf.putU1(targetInfo.getSecondaryIndex());
                break;
            case METHOD_TYPE_PARAMETER_BOUND:
                buf.putU1(METHOD_TYPE_PARAMETER_BOUND);
                buf.putU1(targetInfo.getCount());
                buf.putU1(targetInfo.getSecondaryIndex());
                break;
            case FIELD:
                buf.putU1(FIELD);
                break;
            case METHOD_RETURN:
                buf.putU1(METHOD_RETURN);
                break;
            case METHOD_RECEIVER:
                buf.putU1(METHOD_RECEIVER);
                break;
            case METHOD_FORMAL_PARAMETER:
                buf.putU1(METHOD_FORMAL_PARAMETER);
                buf.putU1(targetInfo.getCount());
                break;
            case THROWS:
                buf.putU1(THROWS);
                buf.putU2(targetInfo.getCount());
                break;
        }
    }

    private static final Field locationInfoDepth = ReflectionUtil.lookupField(TypeAnnotation.LocationInfo.class, "depth");
    private static final Field locationInfoLocations = ReflectionUtil.lookupField(TypeAnnotation.LocationInfo.class, "locations");

    private static void encodeLocationInfo(UnsafeArrayTypeWriter buf, TypeAnnotation.LocationInfo locationInfo) {
        try {
            int depth = (int) locationInfoDepth.get(locationInfo);
            buf.putU1(depth);
            TypeAnnotation.LocationInfo.Location[] locations;
            locations = (TypeAnnotation.LocationInfo.Location[]) locationInfoLocations.get(locationInfo);
            for (TypeAnnotation.LocationInfo.Location location : locations) {
                buf.putS1(location.tag);
                buf.putU1(location.index);
            }
        } catch (IllegalAccessException e) {
            throw GraalError.shouldNotReachHere(e);
        }
    }

    private byte[] encodeReflectParameters(ReflectParameterMetadata[] reflectParameters) {
        if (reflectParameters == null) {
            return new byte[0];
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

    private byte[] encodeEnclosingMethod(Object[] enclosingMethod) {
        assert enclosingMethod.length == 3;
        if (enclosingMethod[0] == null && enclosingMethod[1] == null && enclosingMethod[2] == null) {
            return null;
        }
        UnsafeArrayTypeWriter buf = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());
        encodeType(buf, (HostedType) enclosingMethod[0]);
        encodeName(buf, (String) enclosingMethod[1]);
        encodeName(buf, (String) enclosingMethod[2]);
        return buf.toArray();
    }
}
