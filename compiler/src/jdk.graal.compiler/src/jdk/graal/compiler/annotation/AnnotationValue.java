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
package jdk.graal.compiler.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.AnnotationFormatError;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

import jdk.graal.compiler.util.CollectionsUtil;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Represents an annotation where element values are represented with the types described
 * {@linkplain #get here}.
 */
public final class AnnotationValue {

    private final ResolvedJavaType type;
    private final Map<String, Object> elements;
    private final AnnotationFormatError error;

    private static final Set<Class<?>> ELEMENT_TYPES = CollectionsUtil.setOf(
                    Boolean.class,
                    Byte.class,
                    Character.class,
                    Short.class,
                    Integer.class,
                    Float.class,
                    Long.class,
                    Double.class,
                    String.class,
                    MissingType.class,
                    ElementTypeMismatch.class,
                    EnumElement.class,
                    AnnotationValue.class);

    /**
     * Creates an annotation.
     *
     * @param type the annotation interface of this annotation, represented as a
     *            {@link ResolvedJavaType}
     * @param elements the names and values of this annotation's element values. Each value's type
     *            must be one of the {@code AnnotationValue} types described {@linkplain #get here}
     *            or it must be a {@link ErrorElement} object for an error seen while parsing the
     *            element. There is no distinction between a value explicitly present in the
     *            annotation and an element's default value.
     * @throws IllegalArgumentException if assertions are enabled and the value of an entry in
     *             {@code elements} is not of an accepted type
     * @throws NullPointerException if any of the above parameters is null or any entry in
     *             {@code elements} is null
     */
    public AnnotationValue(ResolvedJavaType type, Map<String, Object> elements) {
        this.type = Objects.requireNonNull(type);
        assert checkElements(elements);
        this.elements = elements;
        this.error = null;
    }

    /**
     * Creates a value that represents an error encountered when parsing the annotation class file
     * bytes. The only meaningful operations that can be performed on such a value are
     * {@link #isError()} and {@link #getError()}. All other operations will result in the
     * {@code error} being thrown.
     */
    public AnnotationValue(AnnotationFormatError error) {
        this.error = Objects.requireNonNull(error);
        this.type = null;
        this.elements = null;
    }

    private static boolean checkElements(Map<String, Object> elements) {
        for (Map.Entry<String, Object> e : elements.entrySet()) {
            checkEntry(e);
        }
        return true;
    }

    private static void checkEntry(Map.Entry<String, Object> e) {
        checkEntry0(e.getKey(), e.getValue(), true);
    }

    private static void checkEntry0(String key, Object value, boolean acceptList) {
        if (value instanceof List<?> list) {
            if (acceptList) {
                int i = 0;
                for (var element : list) {
                    checkEntry0(key + "[" + (i++) + "]", element, false);
                }
                return;
            }
        }
        Class<?> valueClass = value.getClass();
        if ((!ResolvedJavaType.class.isAssignableFrom(valueClass) &&
                        !ELEMENT_TYPES.contains(valueClass))) {
            throw new IllegalArgumentException("illegal type for element " + key + ": " + valueClass.getName());
        }
    }

    /**
     * Returns whether this object represent an annotation parsing error. The error is accessible
     * via {@link #getError()}.
     */
    public boolean isError() {
        return error != null;
    }

    /**
     * Gets the annotation parsing error represented by this value or {@code null} if it does not
     * represent an annotation parsing error.
     */
    public AnnotationFormatError getError() {
        return error;
    }

    private void checkError() {
        if (error != null) {
            throw new AnnotationFormatError(error);
        }
    }

    /**
     * Gets the annotation interface of this annotation, represented as a {@link ResolvedJavaType}.
     *
     * @see Annotation#annotationType()
     */
    public ResolvedJavaType getAnnotationType() {
        checkError();
        return type;
    }

    // @formatter:off
    /**
     * Gets the annotation element denoted by {@code name}. The following table shows the
     * correspondence between the type of an element as declared by a method in the annotation
     * interface and the type of value returned by this method:
     * <table>
     * <thead>
     * <tr><th>Annotation</th> <th>AnnotationValue</th></tr>
     * </thead><tbody>
     * <tr><td>boolean</td>    <td>Boolean</td></tr>
     * <tr><td>byte</td>       <td>Byte</td></tr>
     * <tr><td>char</td>       <td>Character</td></tr>
     * <tr><td>short</td>      <td>Short</td></tr>
     * <tr><td>int</td>        <td>Integer</td></tr>
     * <tr><td>float</td>      <td>Float</td></tr>
     * <tr><td>long</td>       <td>Long</td></tr>
     * <tr><td>double</td>     <td>Double</td></tr>
     * <tr><td>String</td>     <td>String</td></tr>
     * <tr><td>Class</td>      <td>ResolvedJavaType</td></tr>
     * <tr><td>Enum</td>       <td>EnumElement</td></tr>
     * <tr><td>Annotation</td> <td>AnnotationValue</td></tr>
     * <tr><td>T[]</td><td>unmodifiable List&lt;T&gt; where T is one of the above types</td></tr>
     * </tbody>
     * </table>
     *
     * @param <V> the type of the element as per the {@code AnnotationValue} column in the above
     *            table or {@link Object}
     * @param elementType the class for the type of the element or {@code Object.class}
     * @return the annotation element denoted by {@code name}
     * @throws ClassCastException if the element is not of type {@code elementType}
     * @throws IllegalArgumentException if this annotation has no element named {@code name}
     *            if {@code elementType != Object.class} and the element is of type
     *            {@link ErrorElement}
     */
    // @formatter:on
    public <V> V get(String name, Class<V> elementType) {
        checkError();
        Object val = elements.get(name);
        if (val == null) {
            throw new IllegalArgumentException(type.toJavaName() + " missing element " + name);
        }
        if (elementType != Object.class && val instanceof ErrorElement ee) {
            throw ee.generateException();
        }
        return elementType.cast(val);
    }

    /**
     * Gets the byte element denoted by {@code name}.
     *
     * @throws ClassCastException if the element is not a byte
     * @throws IllegalArgumentException if this annotation has no element named {@code name}
     */
    public byte getByte(String name) {
        return get(name, Byte.class);
    }

    /**
     * Gets the boolean element denoted by {@code name}.
     *
     * @throws ClassCastException if the element is not a boolean
     * @throws IllegalArgumentException if this annotation has no element named {@code name}
     */
    public boolean getBoolean(String name) {
        return get(name, Boolean.class);
    }

    /**
     * Gets the char element denoted by {@code name}.
     *
     * @throws ClassCastException if the element is not a char
     * @throws IllegalArgumentException if this annotation has no element named {@code name}
     */
    public char getChar(String name) {
        return get(name, Character.class);
    }

    /**
     * Gets the short element denoted by {@code name}.
     *
     * @throws ClassCastException if the element is not a short
     * @throws IllegalArgumentException if this annotation has no element named {@code name}
     */
    public short getShort(String name) {
        return get(name, Short.class);
    }

    /**
     * Gets the int element denoted by {@code name}.
     *
     * @throws ClassCastException if the element is not an int
     * @throws IllegalArgumentException if this annotation has no element named {@code name}
     */
    public int getInt(String name) {
        return get(name, Integer.class);
    }

    /**
     * Gets the float element denoted by {@code name}.
     *
     * @throws ClassCastException if the element is not a float
     * @throws IllegalArgumentException if this annotation has no element named {@code name}
     */
    public float getFloat(String name) {
        return get(name, Float.class);
    }

    /**
     * Gets the long element denoted by {@code name}.
     *
     * @throws ClassCastException if the element is not a long
     * @throws IllegalArgumentException if this annotation has no element named {@code name}
     */
    public long getLong(String name) {
        return get(name, Long.class);
    }

    /**
     * Gets the double element denoted by {@code name}.
     *
     * @throws ClassCastException if the element is not a double
     * @throws IllegalArgumentException if this annotation has no element named {@code name}
     */
    public double getDouble(String name) {
        return get(name, Double.class);
    }

    /**
     * Gets the string element denoted by {@code name}.
     *
     * @throws ClassCastException if the element is not a string
     * @throws IllegalArgumentException if this annotation has no element named {@code name}
     */
    public String getString(String name) {
        return get(name, String.class);
    }

    /**
     * Gets the {@link ResolvedJavaType} element denoted by {@code name}.
     *
     * @throws ClassCastException if the element is not a {@link ResolvedJavaType}
     * @throws IllegalArgumentException if this annotation has no element named {@code name}
     */
    public ResolvedJavaType getType(String name) {
        return get(name, ResolvedJavaType.class);
    }

    /**
     * Gets the annotation element denoted by {@code name}.
     *
     * @throws ClassCastException if the element is not an annotation
     * @throws IllegalArgumentException if this annotation has no element named {@code name}
     */
    public AnnotationValue getAnnotation(String name) {
        return get(name, AnnotationValue.class);
    }

    /**
     * Gets the enum element denoted by {@code name}.
     *
     * @throws ClassCastException if the element is not an enum
     * @throws IllegalArgumentException if this annotation has no element named {@code name}
     */
    public EnumElement getEnum(String name) {
        return get(name, EnumElement.class);
    }

    /**
     * Gets the enum element denoted by {@code name}, resolved to an {@code enumClass} constant.
     *
     * @throws ClassCastException if the element is not an enum
     * @throws IllegalArgumentException if this annotation has no element named {@code name}, if
     *             {@code enumClass} does not match the expected enum type or {@code enumClass} has
     *             no constant with the specified name
     */
    public <T extends Enum<T>> T getEnum(Class<T> enumClass, String name) {
        EnumElement enumElement = getEnum(name);
        String foundType = enumElement.enumType.toClassName();
        if (!foundType.equals(enumClass.getName())) {
            throw new IllegalArgumentException("Unexpected enum type: " + foundType);
        }
        return Enum.valueOf(enumClass, enumElement.name);
    }

    /**
     * Gets an unmodifiable view of the elements in this annotation value. The type for each value
     * in the returned map is specified by {@link #get(String, Class)}.
     */
    public Map<String, Object> getElements() {
        checkError();
        return elements;
    }

    @Override
    public String toString() {
        if (error != null) {
            return "!<" + error + ">";
        }
        return "@" + type.toClassName() + "(" + elements + ")";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof AnnotationValue that) {
            return Objects.equals(this.type, that.type) &&
                            Objects.equals(this.elements, that.elements) &&
                            Objects.equals(this.error, that.error);

        }
        return false;
    }

    @Override
    public int hashCode() {
        if (error != null) {
            return error.hashCode();
        }
        return type.hashCode() ^ elements.hashCode();
    }

    /**
     * Result of last call to {@link #toAnnotation}.
     */
    private volatile Annotation annotationCache;

    /**
     * Converts this to an {@link Annotation} of type {@code annotationType}, utilizing the provided
     * converter function. The result is cached to improve performance by reducing redundant
     * conversions.
     *
     * @param <T> the type of the annotation to be created
     * @param annotationType the desired annotation type
     * @param converter a function that does the conversion
     */
    @SuppressWarnings("unchecked")
    public <T extends Annotation> T toAnnotation(Class<T> annotationType, BiFunction<AnnotationValue, Class<T>, T> converter) {
        Annotation res = annotationCache;
        if (res == null || res.annotationType() != annotationType) {
            res = converter.apply(this, annotationType);
            annotationCache = res;
        }
        return (T) res;
    }
}
