/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.annotation.test;

import static jdk.graal.compiler.annotation.AnnotationValueSupport.getDeclaredAnnotationValue;
import static jdk.graal.compiler.annotation.AnnotationValueSupport.getDeclaredAnnotationValues;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.ElementTypeMismatch;
import jdk.graal.compiler.annotation.EnumElement;
import jdk.graal.compiler.annotation.ErrorElement;
import jdk.graal.compiler.annotation.MissingType;
import jdk.graal.compiler.annotation.TypeAnnotationValue;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.test.AddExports;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.internal.reflect.ConstantPool;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.annotation.Annotated;
import sun.reflect.annotation.AnnotationSupport;
import sun.reflect.annotation.ExceptionProxy;
import sun.reflect.annotation.TypeAnnotation;
import sun.reflect.annotation.TypeAnnotationParser;
import sun.reflect.annotation.TypeNotPresentExceptionProxy;

/**
 * Base class for annotation tests.
 */
@AddExports({"java.base/java.lang", "java.base/jdk.internal.reflect", "java.base/sun.reflect.annotation"})
public class TestAnnotationsBase extends Universe {

    @Test
    public void testIllegalElements() {
        if (!Assertions.assertionsEnabled()) {
            // Element checking is performed in the AnnotationValue constructor
            // only when assertions are enabled.
            return;
        }
        var annotationType = metaAccess.lookupJavaType(SuppressWarnings.class);

        List<Map<String, Object>> elementMapsWithIllegalValues = List.of(
                        CollectionsUtil.mapOf("first", new int[0]),
                        CollectionsUtil.mapOf("first", new Object[0]),
                        CollectionsUtil.mapOf("first", new Object()),
                        CollectionsUtil.mapOf("first", new Exception()),
                        CollectionsUtil.mapOf("first", List.of(new Object())),
                        CollectionsUtil.mapOf("first", List.of(new Exception())),
                        CollectionsUtil.mapOf("first", List.of(List.of())));

        for (Map<String, Object> elements : elementMapsWithIllegalValues) {
            try {
                new AnnotationValue(annotationType, elements);
                throw new AssertionError("expected " + IllegalArgumentException.class.getName());
            } catch (IllegalArgumentException e) {
                // expected
            }
        }
    }

    private static final Method classGetConstantPool = lookupMethod(Class.class, "getConstantPool");
    private static final Method parseTargetInfo = lookupMethod(TypeAnnotationParser.class, "parseTargetInfo", ByteBuffer.class);
    private static final Method parseTypeAnnotations = lookupMethod(TypeAnnotationParser.class, "parseTypeAnnotations", byte[].class, ConstantPool.class, AnnotatedElement.class, Class.class);

    public static List<TypeAnnotation> getTypeAnnotations(byte[] rawAnnotations, Class<?> container) {
        jdk.internal.reflect.ConstantPool cp = invokeMethod(classGetConstantPool, container);
        TypeAnnotation[] typeAnnotations = invokeMethod(parseTypeAnnotations, null, rawAnnotations, cp, null, container);
        return Stream.of(typeAnnotations).filter(ta -> ta.getAnnotation() != null).toList();
    }

    private static void getAnnotationValueExpectedToFail(Annotated annotated, ResolvedJavaType annotationType) {
        try {
            getDeclaredAnnotationValue(annotationType, annotated);
            String s = annotationType.toJavaName();
            throw new AssertionError("Expected IllegalArgumentException for retrieving (" + s + " from " + annotated);
        } catch (IllegalArgumentException iae) {
            assertTrue(iae.getMessage(), iae.getMessage().contains("not an annotation interface"));
        }
    }

    /**
     * Tests that {@link AnnotationValue}s obtained from a {@link Class}, {@link Method} or
     * {@link Field} match {@link AnnotatedElement#getDeclaredAnnotations()} for the corresponding
     * JVMCI object.
     *
     * @param annotatedElement a {@link Class}, {@link Method} or {@link Field} object
     */
    public static List<AnnotationValue> checkAnnotationValues(AnnotatedElement annotatedElement) {
        Annotated annotated = toAnnotated(annotatedElement);
        ResolvedJavaType objectType = metaAccess.lookupJavaType(Object.class);
        ResolvedJavaType suppressWarningsType = metaAccess.lookupJavaType(SuppressWarnings.class);
        getAnnotationValueExpectedToFail(annotated, objectType);

        // Check that querying a missing annotation returns null or an empty list
        assertNull(getDeclaredAnnotationValue(suppressWarningsType, annotated));
        Map<ResolvedJavaType, AnnotationValue> values = getDeclaredAnnotationValues(annotated);
        assertNull(values.toString(), values.get(suppressWarningsType));

        return testGetAnnotationValues(annotated, List.of(annotatedElement.getDeclaredAnnotations()));
    }

    public static void assertTypeAnnotationsEquals(
                    List<TypeAnnotation> typeAnnotations,
                    List<TypeAnnotationValue> typeAnnotationValues) throws AssertionError {
        assertEquals(typeAnnotations.size(), typeAnnotationValues.size());
        for (int i = 0; i < typeAnnotations.size(); i++) {
            TypeAnnotation typeAnnotation = typeAnnotations.get(i);
            TypeAnnotationValue typeAnnotationValue = typeAnnotationValues.get(i);
            assertTypeAnnotationEquals(typeAnnotation, typeAnnotationValue);
        }
    }

    private static List<AnnotationValue> testGetAnnotationValues(Annotated annotated, List<Annotation> annotations) throws AssertionError {
        try {
            return testGetAnnotationValues0(annotated, annotations);
        } catch (AssertionError e) {
            throw new AssertionError("Annotated: " + annotated, e);
        }
    }

    private static List<AnnotationValue> testGetAnnotationValues0(Annotated annotated, List<Annotation> annotations) throws AssertionError {
        List<AnnotationValue> res = new ArrayList<>(annotations.size());

        Map<ResolvedJavaType, AnnotationValue> allAnnotationValues = getDeclaredAnnotationValues(annotated);
        assertEquals(annotations.size(), allAnnotationValues.size());

        for (Annotation a : annotations) {
            var annotationType = metaAccess.lookupJavaType(a.annotationType());
            AnnotationValue av = getDeclaredAnnotationValue(annotationType, annotated);
            assertAnnotationsEquals(a, av);

            // Check that encoding/decoding produces a stable result
            AnnotationValue av2 = getDeclaredAnnotationValue(annotationType, annotated);
            assertEquals(av, av2);

            av2 = allAnnotationValues.get(annotationType);
            assertAnnotationsEquals(a, av2);

            Map<String, Object> elements = av.getElements();
            for (var e : elements.entrySet()) {
                String name = e.getKey();
                Object expect = e.getValue();
                Class<?> elementType = expect.getClass();
                if (e.getValue() instanceof ErrorElement) {
                    elementType = Object.class;
                }
                Object actual = av.get(name, elementType);
                assertEquals(expect, actual);
            }
            res.add(av);
        }
        return res;
    }

    private static Annotated toAnnotated(AnnotatedElement element) {
        switch (element) {
            case Class<?> t -> {
                return metaAccess.lookupJavaType(t);
            }
            case Method m -> {
                return metaAccess.lookupJavaMethod(m);
            }
            case RecordComponent rc -> {
                return metaAccess.lookupJavaRecordComponent(rc);
            }
            case null, default -> {
                Field f = (Field) element;
                return metaAccess.lookupJavaField(f);
            }
        }
    }

    /**
     * Used to test error handling in {@link AnnotationValue#getEnum(Class, String)}.
     */
    enum MyEnum {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void assertAnnotationsEquals(Annotation a, AnnotationValue av) {
        Map<String, Object> values = AnnotationSupport.memberValues(a);
        for (Map.Entry<String, Object> e : values.entrySet()) {
            String name = e.getKey();
            Object aElement = e.getValue();
            Object avElement = av.get(name, Object.class);
            try {
                assertAnnotationElementsEqual(aElement, avElement);
            } catch (ClassCastException ex) {
                throw new AssertionError(a.getClass().getName() + "." + name + " has wrong type: " + avElement.getClass().getName(), ex);
            }

            if (!(aElement instanceof ExceptionProxy)) {
                Class<?> elementType = toAnnotationValueElementType(aElement.getClass());
                av.get(name, elementType);

                Object actual;
                if (elementType == Byte.class) {
                    actual = av.getByte(name);
                } else if (elementType == Boolean.class) {
                    actual = av.getBoolean(name);
                } else if (elementType == Short.class) {
                    actual = av.getShort(name);
                } else if (elementType == Character.class) {
                    actual = av.getChar(name);
                } else if (elementType == Integer.class) {
                    actual = av.getInt(name);
                } else if (elementType == Float.class) {
                    actual = av.getFloat(name);
                } else if (elementType == Long.class) {
                    actual = av.getLong(name);
                } else if (elementType == Double.class) {
                    actual = av.getDouble(name);
                } else if (elementType == String.class) {
                    actual = av.getString(name);
                } else if (elementType == ResolvedJavaType.class) {
                    actual = av.getType(name);
                } else if (elementType == EnumElement.class) {
                    actual = av.getEnum(name);
                    Class<? extends Enum> enumClass = (Class<? extends Enum>) aElement.getClass();
                    var avEnumConstant = av.getEnum(enumClass, name);
                    assertEquals(aElement, avEnumConstant);
                    try {
                        av.getEnum(MyEnum.class, name);
                        fail("expected " + IllegalArgumentException.class.getName());
                    } catch (IllegalArgumentException iae) {
                        // expected
                    }
                } else if (elementType == AnnotationValue.class) {
                    actual = av.getAnnotation(name);
                } else {
                    assert elementType == List.class : aElement + " // " + elementType;
                    actual = avElement;
                }
                assertAnnotationElementsEqual(aElement, actual);
            }
        }
    }

    private static void assertTypeAnnotationEquals(TypeAnnotation ta, TypeAnnotationValue tav) {
        TypeAnnotation.TypeAnnotationTargetInfo tiLeft = ta.getTargetInfo();
        TypeAnnotation.TypeAnnotationTargetInfo tiRight = invokeMethod(parseTargetInfo, null, ByteBuffer.wrap(tav.getTargetInfo()));
        assertEquals(tiLeft.toString(), tiRight.toString());
        TypeAnnotation.LocationInfo liLeft = ta.getLocationInfo();
        TypeAnnotation.LocationInfo liRight = TypeAnnotation.LocationInfo.parseLocationInfo(ByteBuffer.wrap(tav.getTypePath()));
        assertEquals(toString(liLeft), toString(liRight));
        assertAnnotationsEquals(ta.getAnnotation(), tav.getAnnotation());
    }

    private static final Field locationInfoDepth = lookupField(TypeAnnotation.LocationInfo.class, "depth");
    private static final Field locationInfoLocations = lookupField(TypeAnnotation.LocationInfo.class, "locations");
    private static final Class<?> Location = lookupClass("sun.reflect.annotation.TypeAnnotation$LocationInfo$Location");
    private static final Field locationTag = lookupField(Location, "tag");
    private static final Field locationIndex = lookupField(Location, "index");

    private static String locationToString(Object location) {
        return String.format("{tag: %s, index: %s}", getFieldValue(locationTag, location), getFieldValue(locationIndex, location));
    }

    private static String toString(TypeAnnotation.LocationInfo li) {
        String locations = Stream.of(getFieldValue(locationInfoLocations, li)).map(TestAnnotationsBase::locationToString).collect(Collectors.joining(", "));
        return String.format("{depth: %s, locations: %s}", getFieldValue(locationInfoDepth, li), locations);
    }

    /**
     * Gets the type of an element in {@link AnnotationValue} for {@code type}.
     *
     * @param type the type of an annotation element as returned by
     *            {@code AnnotationInvocationHandler}
     */
    public static Class<?> toAnnotationValueElementType(Class<?> type) {
        if (type == Class.class) {
            return ResolvedJavaType.class;
        }
        if (Enum.class.isAssignableFrom(type)) {
            return EnumElement.class;
        }
        if (Annotation.class.isAssignableFrom(type)) {
            return AnnotationValue.class;
        }
        if (type.isArray()) {
            return List.class;
        }
        return type;
    }

    private static final Class<?> AnnotationTypeMismatchExceptionProxy = lookupClass("sun.reflect.annotation.AnnotationTypeMismatchExceptionProxy");
    private static final Field proxyFoundType = lookupField(AnnotationTypeMismatchExceptionProxy, "foundType");

    public static void assertAnnotationElementsEqual(Object aElement, Object avElement) {
        Class<?> valueType = aElement.getClass();
        if (valueType.isEnum()) {
            String avEnumName = ((EnumElement) avElement).name;
            String aEnumName = ((Enum<?>) aElement).name();
            assertEquals(avEnumName, aEnumName);
        } else if (aElement instanceof Class) {
            assertClassObjectsEquals(aElement, avElement);
        } else if (aElement instanceof Annotation) {
            assertAnnotationObjectsEquals(aElement, avElement);
        } else if (aElement instanceof TypeNotPresentExceptionProxy proxy) {
            assertTrue(avElement.toString(), avElement instanceof MissingType);
            MissingType mt = (MissingType) avElement;
            assertEquals(proxy.typeName(), mt.getTypeName());
        } else if (AnnotationTypeMismatchExceptionProxy.isInstance(aElement)) {
            assertTrue(avElement.toString(), avElement instanceof ElementTypeMismatch);
            ElementTypeMismatch etm = (ElementTypeMismatch) avElement;
            String foundType = getFieldValue(proxyFoundType, aElement);
            assertEquals(foundType, etm.getFoundType());
        } else if (valueType.isArray()) {
            int length = Array.getLength(aElement);
            List<?> avList = (List<?>) avElement;
            assertEquals(length, avList.size());
            for (int i = 0; i < length; i++) {
                assertAnnotationElementsEqual(Array.get(aElement, i), avList.get(i));
            }
        } else {
            assertEquals(aElement.getClass(), avElement.getClass());
            assertEquals(aElement, avElement);
        }
    }

    private static void assertClassObjectsEquals(Object aElement, Object avElement) {
        String aName = ((Class<?>) aElement).getName();
        String avName = ((ResolvedJavaType) avElement).toClassName();
        assertEquals(aName, avName);
    }

    private static void assertAnnotationObjectsEquals(Object aElement, Object avElement) {
        Annotation aAnnotation = (Annotation) aElement;
        AnnotationValue avAnnotation = (AnnotationValue) avElement;
        assertAnnotationsEquals(aAnnotation, avAnnotation);
    }
}
