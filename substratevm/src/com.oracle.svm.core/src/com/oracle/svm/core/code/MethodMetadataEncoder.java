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
package com.oracle.svm.core.code;

// Checkstyle: allow reflection

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.lang.annotation.Annotation;
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

import org.graalvm.collections.Pair;
import org.graalvm.compiler.core.common.util.TypeConversion;
import org.graalvm.compiler.core.common.util.UnsafeArrayTypeWriter;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.util.GuardedAnnotationAccess;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.code.CodeInfoDecoder.MethodDescriptor;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.util.ByteArrayReader;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.JavaType;
import sun.invoke.util.Wrapper;
import sun.reflect.annotation.AnnotationType;
import sun.reflect.annotation.TypeAnnotation;
import sun.reflect.annotation.TypeAnnotationParser;

@Platforms(Platform.HOSTED_ONLY.class)
public class MethodMetadataEncoder {
    public static final int NO_METHOD_METADATA = -1;

    private CodeInfoEncoder.Encoders encoders;
    private TreeSet<SharedType> sortedTypes;
    private Map<SharedType, Set<Pair<SharedMethod, Executable>>> queriedMethodData;
    private Map<SharedType, Set<SharedMethod>> reachableMethodData;
    private Map<SharedType, Set<MethodDescriptor>> hiddenMethodData;

    private byte[] methodDataEncoding;
    private byte[] methodDataIndexEncoding;

    MethodMetadataEncoder(CodeInfoEncoder.Encoders encoders) {
        this.encoders = encoders;
        this.sortedTypes = new TreeSet<>(Comparator.comparingLong(t -> t.getHub().getTypeID()));
        if (SubstrateOptions.ConfigureReflectionMetadata.getValue()) {
            this.queriedMethodData = new HashMap<>();
            this.hiddenMethodData = new HashMap<>();
        }
        if (SubstrateOptions.IncludeMethodData.getValue()) {
            this.reachableMethodData = new HashMap<>();
        }
    }

    void encodeAllAndInstall() {
        encodeMethodMetadata();
        ImageSingletons.lookup(MethodMetadataEncoding.class).setMethodsEncoding(methodDataEncoding);
        ImageSingletons.lookup(MethodMetadataEncoding.class).setIndexEncoding(methodDataIndexEncoding);
    }

    @SuppressWarnings("unchecked")
    public void prepareMetadataForClass(Class<?> clazz) {
        encoders.sourceClasses.addObject(clazz);
        if (clazz.isAnnotation()) {
            try {
                for (String valueName : AnnotationType.getInstance((Class<? extends Annotation>) clazz).members().keySet()) {
                    encoders.sourceMethodNames.addObject(valueName);
                }
            } catch (LinkageError | RuntimeException t) {
                // ignore
            }
        }
    }

    private static final Method parseAllTypeAnnotations = ReflectionUtil.lookupMethod(TypeAnnotationParser.class, "parseAllTypeAnnotations", AnnotatedElement.class);

    public void prepareMetadataForMethod(SharedMethod method, Executable reflectMethod, boolean complete) {
        if (reflectMethod instanceof Constructor<?>) {
            encoders.sourceMethodNames.addObject("<init>");
        } else {
            encoders.sourceMethodNames.addObject(method.getName());
        }

        if (complete) {
            encoders.sourceMethodNames.addObject(getSignature(reflectMethod));
            for (Parameter parameter : reflectMethod.getParameters()) {
                encoders.sourceMethodNames.addObject(parameter.getName());
            }

            /* Register string values in annotations */
            registerStrings(GuardedAnnotationAccess.getDeclaredAnnotations(method));
            for (Annotation[] annotations : reflectMethod.getParameterAnnotations()) {
                registerStrings(annotations);
            }
            try {
                for (TypeAnnotation typeAnnotation : (TypeAnnotation[]) parseAllTypeAnnotations.invoke(null, reflectMethod)) {
                    // Checkstyle: allow direct annotation access
                    registerStrings(typeAnnotation.getAnnotation());
                    // Checkstyle: disallow direct annotation access
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw shouldNotReachHere();
            }
        }

        SharedType declaringType = (SharedType) method.getDeclaringClass();
        sortedTypes.add(declaringType);
        if (complete) {
            queriedMethodData.computeIfAbsent(declaringType, t -> new HashSet<>()).add(Pair.create(method, reflectMethod));
        } else {
            reachableMethodData.computeIfAbsent(declaringType, t -> new HashSet<>()).add(method);
        }
    }

    public void prepareHiddenMethodMetadata(SharedType type, String name, Class<?>[] parameterTypes) {
        encoders.sourceMethodNames.addObject(name);
        sortedTypes.add(type);
        hiddenMethodData.computeIfAbsent(type, t -> new HashSet<>()).add(new MethodDescriptor(name, parameterTypes));
    }

    private static final Method hasRealParameterData = ReflectionUtil.lookupMethod(Executable.class, "hasRealParameterData");

    private void encodeMethodMetadata() {
        UnsafeArrayTypeWriter dataEncodingBuffer = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());
        UnsafeArrayTypeWriter indexEncodingBuffer = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());
        long lastTypeID = -1;
        for (SharedType declaringType : sortedTypes) {
            long typeID = declaringType.getHub().getTypeID();
            assert typeID > lastTypeID;
            lastTypeID++;
            while (lastTypeID < typeID) {
                indexEncodingBuffer.putS4(NO_METHOD_METADATA);
                lastTypeID++;
            }
            long index = dataEncodingBuffer.getBytesWritten();
            indexEncodingBuffer.putS4(index);
            if (SubstrateOptions.ConfigureReflectionMetadata.getValue()) {
                Set<Pair<SharedMethod, Executable>> completeMethods = queriedMethodData.getOrDefault(declaringType, Collections.emptySet());
                dataEncodingBuffer.putUV(completeMethods.size());
                for (Pair<SharedMethod, Executable> method : completeMethods) {
                    SharedMethod hostedMethod = method.getLeft();
                    Executable reflectMethod = method.getRight();

                    Class<?> declaringClass = getJavaClass((SharedType) hostedMethod.getDeclaringClass());
                    final int classIndex = encoders.sourceClasses.getIndex(declaringClass);
                    dataEncodingBuffer.putSV(classIndex);

                    String name = hostedMethod.isConstructor() ? "<init>" : hostedMethod.getName();
                    final int nameIndex = encoders.sourceMethodNames.getIndex(name);
                    dataEncodingBuffer.putSV(nameIndex);

                    dataEncodingBuffer.putUV(reflectMethod.getModifiers());

                    /* Parameter types do not include the receiver */
                    JavaType[] parameterTypes = hostedMethod.getSignature().toParameterTypes(null);
                    dataEncodingBuffer.putUV(parameterTypes.length);
                    for (JavaType parameterType : parameterTypes) {
                        Class<?> parameterClass = getJavaClass((SharedType) parameterType);
                        final int paramClassIndex = encoders.sourceClasses.getIndex(parameterClass);
                        dataEncodingBuffer.putSV(paramClassIndex);
                    }

                    Class<?> returnType = void.class;
                    if (!hostedMethod.isConstructor()) {
                        returnType = getJavaClass((SharedType) hostedMethod.getSignature().getReturnType(null));
                    }
                    final int returnTypeIndex = encoders.sourceClasses.getIndex(returnType);
                    dataEncodingBuffer.putSV(returnTypeIndex);

                    /*
                     * Only include types that are in the image (i.e. that can actually be thrown)
                     */
                    Class<?>[] exceptionTypes = filterTypes(reflectMethod.getExceptionTypes());
                    dataEncodingBuffer.putUV(exceptionTypes.length);
                    for (Class<?> exceptionClazz : exceptionTypes) {
                        final int exceptionClassIndex = encoders.sourceClasses.getIndex(exceptionClazz);
                        dataEncodingBuffer.putSV(exceptionClassIndex);
                    }

                    final int signatureIndex = encoders.sourceMethodNames.getIndex(getSignature(reflectMethod));
                    dataEncodingBuffer.putSV(signatureIndex);

                    try {
                        byte[] annotations = encodeAnnotations(GuardedAnnotationAccess.getDeclaredAnnotations(hostedMethod));
                        dataEncodingBuffer.putUV(annotations.length);
                        for (byte b : annotations) {
                            dataEncodingBuffer.putS1(b);
                        }

                        byte[] parameterAnnotations = encodeParameterAnnotations(reflectMethod.getParameterAnnotations());
                        dataEncodingBuffer.putUV(parameterAnnotations.length);
                        for (byte b : parameterAnnotations) {
                            dataEncodingBuffer.putS1(b);
                        }

                        byte[] typeAnnotations = encodeTypeAnnotations((TypeAnnotation[]) parseAllTypeAnnotations.invoke(null, reflectMethod));
                        dataEncodingBuffer.putUV(typeAnnotations.length);
                        for (byte b : typeAnnotations) {
                            dataEncodingBuffer.putS1(b);
                        }

                        boolean parameterDataPresent = (boolean) hasRealParameterData.invoke(reflectMethod);
                        dataEncodingBuffer.putU1(parameterDataPresent ? 1 : 0);
                        if (parameterDataPresent) {
                            Parameter[] parameters = reflectMethod.getParameters();
                            dataEncodingBuffer.putUV(parameters.length);
                            for (Parameter parameter : parameters) {
                                final int parameterNameIndex = encoders.sourceMethodNames.getIndex(parameter.getName());
                                dataEncodingBuffer.putSV(parameterNameIndex);
                                dataEncodingBuffer.putS4(parameter.getModifiers());
                            }
                        }
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw shouldNotReachHere();
                    }
                }

                Set<MethodDescriptor> hiddenMethods = hiddenMethodData.getOrDefault(declaringType, Collections.emptySet());
                dataEncodingBuffer.putUV(hiddenMethods.size());
                for (MethodDescriptor hiddenMethod : hiddenMethods) {
                    String name = hiddenMethod.getName();
                    final int nameIndex = encoders.sourceMethodNames.getIndex(name);
                    dataEncodingBuffer.putSV(nameIndex);

                    /* Parameter types do not include the receiver */
                    Class<?>[] parameterTypes = hiddenMethod.getParameterTypes();
                    dataEncodingBuffer.putUV(parameterTypes.length);
                    for (Class<?> parameterType : parameterTypes) {
                        final int paramTypeIndex = encoders.sourceClasses.getIndex(parameterType);
                        dataEncodingBuffer.putSV(paramTypeIndex);
                    }
                }
            }
            if (SubstrateOptions.IncludeMethodData.getValue()) {
                Set<SharedMethod> partialMethods = reachableMethodData.getOrDefault(declaringType, Collections.emptySet());
                dataEncodingBuffer.putUV(partialMethods.size());
                for (SharedMethod hostedMethod : partialMethods) {
                    Class<?> declaringClass = getJavaClass((SharedType) hostedMethod.getDeclaringClass());
                    final int classIndex = encoders.sourceClasses.getIndex(declaringClass);
                    dataEncodingBuffer.putSV(classIndex);

                    String name = hostedMethod.isConstructor() ? "<init>" : hostedMethod.getName();
                    final int nameIndex = encoders.sourceMethodNames.getIndex(name);
                    dataEncodingBuffer.putSV(nameIndex);

                    /* Parameter types do not include the receiver */
                    JavaType[] parameterTypes = hostedMethod.getSignature().toParameterTypes(null);
                    dataEncodingBuffer.putUV(parameterTypes.length);
                    for (JavaType parameterType : parameterTypes) {
                        Class<?> parameterClass = getJavaClass((SharedType) parameterType);
                        final int paramClassIndex = encoders.sourceClasses.getIndex(parameterClass);
                        dataEncodingBuffer.putSV(paramClassIndex);
                    }
                }
            }
        }
        while (lastTypeID < ImageSingletons.lookup(DynamicHubSupport.class).getMaxTypeId()) {
            indexEncodingBuffer.putS4(NO_METHOD_METADATA);
            lastTypeID++;
        }
        methodDataEncoding = new byte[TypeConversion.asS4(dataEncodingBuffer.getBytesWritten())];
        dataEncodingBuffer.toArray(methodDataEncoding);
        methodDataIndexEncoding = new byte[TypeConversion.asS4(indexEncodingBuffer.getBytesWritten())];
        indexEncodingBuffer.toArray(methodDataIndexEncoding);
    }

    private static Class<?> getJavaClass(SharedType sharedType) {
        return sharedType.getHub().getHostedJavaClass();
    }

    private Class<?>[] filterTypes(Class<?>[] types) {
        List<Class<?>> filteredTypes = new ArrayList<>();
        for (Class<?> type : types) {
            if (encoders.sourceClasses.contains(type)) {
                filteredTypes.add(type);
            }
        }
        return filteredTypes.toArray(new Class<?>[0]);
    }

    private static final Method getMethodSignature = ReflectionUtil.lookupMethod(Method.class, "getGenericSignature");
    private static final Method getConstructorSignature = ReflectionUtil.lookupMethod(Constructor.class, "getSignature");

    private static String getSignature(Executable method) {
        try {
            return (String) (method instanceof Method ? getMethodSignature.invoke(method) : getConstructorSignature.invoke(method));
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw shouldNotReachHere();
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
     * {@link com.oracle.svm.core.jdk.Target_jdk_internal_reflect_ConstantPool})
     */
    byte[] encodeAnnotations(Annotation[] annotations) throws InvocationTargetException, IllegalAccessException {
        UnsafeArrayTypeWriter buf = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());

        Annotation[] filteredAnnotations = filterAnnotations(annotations);
        buf.putU2(filteredAnnotations.length);
        for (Annotation annotation : filteredAnnotations) {
            encodeAnnotation(buf, annotation);
        }

        return buf.toArray();
    }

    byte[] encodeParameterAnnotations(Annotation[][] annotations) throws InvocationTargetException, IllegalAccessException {
        UnsafeArrayTypeWriter buf = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());

        buf.putU1(annotations.length);
        for (Annotation[] parameterAnnotations : annotations) {
            Annotation[] filteredParameterAnnotations = filterAnnotations(parameterAnnotations);
            buf.putU2(filteredParameterAnnotations.length);
            for (Annotation parameterAnnotation : filteredParameterAnnotations) {
                encodeAnnotation(buf, parameterAnnotation);
            }
        }

        return buf.toArray();
    }

    void encodeAnnotation(UnsafeArrayTypeWriter buf, Annotation annotation) throws InvocationTargetException, IllegalAccessException {
        buf.putS4(encoders.sourceClasses.getIndex(annotation.annotationType()));
        AnnotationType type = AnnotationType.getInstance(annotation.annotationType());
        buf.putU2(type.members().size());
        for (Map.Entry<String, Method> entry : type.members().entrySet()) {
            String memberName = entry.getKey();
            Method valueAccessor = entry.getValue();
            buf.putS4(encoders.sourceMethodNames.getIndex(memberName));
            encodeValue(buf, valueAccessor.invoke(annotation), type.memberTypes().get(memberName));
        }
    }

    void encodeValue(UnsafeArrayTypeWriter buf, Object value, Class<?> type) throws InvocationTargetException, IllegalAccessException {
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
                    throw shouldNotReachHere();
            }
        } else {
            throw shouldNotReachHere();
        }
    }

    void encodeArray(UnsafeArrayTypeWriter buf, Object value, Class<?> componentType) throws InvocationTargetException, IllegalAccessException {
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

    byte tag(Class<?> type) {
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
        } else {
            throw shouldNotReachHere();
        }
    }

    private Annotation[] filterAnnotations(Annotation[] annotations) {
        List<Annotation> filteredAnnotations = new ArrayList<>();
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> annotationClass = annotation.annotationType();
            if (supportedValue(annotationClass, annotation, null)) {
                filteredAnnotations.add(annotation);
            }
        }
        return filteredAnnotations.toArray(new Annotation[0]);
    }

    private void registerStrings(Annotation... annotations) {
        for (Annotation annotation : annotations) {
            List<String> stringValues = new ArrayList<>();
            if (supportedValue(annotation.annotationType(), annotation, stringValues)) {
                for (String stringValue : stringValues) {
                    encoders.sourceMethodNames.addObject(stringValue);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean supportedValue(Class<?> type, Object value, List<String> stringValues) {
        if (type.isAnnotation()) {
            Annotation annotation = (Annotation) value;
            if (!encoders.sourceClasses.contains(annotation.annotationType())) {
                return false;
            }
            AnnotationType annotationType = AnnotationType.getInstance((Class<? extends Annotation>) type);
            for (Map.Entry<String, Class<?>> entry : annotationType.memberTypes().entrySet()) {
                String valueName = entry.getKey();
                Class<?> valueType = entry.getValue();
                try {
                    Method getAnnotationValue = annotationType.members().get(valueName);
                    getAnnotationValue.setAccessible(true);
                    Object annotationValue = getAnnotationValue.invoke(annotation);
                    if (!supportedValue(valueType, annotationValue, stringValues)) {
                        return false;
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    return false;
                }
            }
        } else if (type.isArray()) {
            boolean supported = true;
            Class<?> componentType = type.getComponentType();
            if (!componentType.isPrimitive()) {
                for (Object val : (Object[]) value) {
                    supported &= supportedValue(componentType, val, stringValues);
                }
            }
            return supported;
        } else if (type == Class.class) {
            return encoders.sourceClasses.contains((Class<?>) value);
        } else if (type == String.class) {
            if (stringValues != null) {
                stringValues.add((String) value);
            }
        } else if (type.isEnum()) {
            if (stringValues != null) {
                stringValues.add(((Enum<?>) value).name());
            }
            return encoders.sourceClasses.contains(type);
        }
        return true;
    }

    byte[] encodeTypeAnnotations(TypeAnnotation[] annotations) throws InvocationTargetException, IllegalAccessException {
        UnsafeArrayTypeWriter buf = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());

        buf.putU2(annotations.length);
        for (TypeAnnotation typeAnnotation : annotations) {
            encodeTypeAnnotation(buf, typeAnnotation);
        }

        return buf.toArray();
    }

    void encodeTypeAnnotation(UnsafeArrayTypeWriter buf, TypeAnnotation typeAnnotation) throws InvocationTargetException, IllegalAccessException {
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

    void encodeTargetInfo(UnsafeArrayTypeWriter buf, TypeAnnotation.TypeAnnotationTargetInfo targetInfo) {
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

    void encodeLocationInfo(UnsafeArrayTypeWriter buf, TypeAnnotation.LocationInfo locationInfo) throws IllegalAccessException {
        int depth = (int) locationInfoDepth.get(locationInfo);
        buf.putU1(depth);
        TypeAnnotation.LocationInfo.Location[] locations = (TypeAnnotation.LocationInfo.Location[]) locationInfoLocations.get(locationInfo);
        for (TypeAnnotation.LocationInfo.Location location : locations) {
            buf.putS1(location.tag);
            buf.putU1(location.index);
        }

    }
}
