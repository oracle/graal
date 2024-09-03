/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.nativeimage.c.constant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.nativeimage.c.CContext;

/**
 * Annotation to import a C enumeration to Java. In C, enumeration values are plain integer
 * constants. In Java, enumeration values are object constants. Therefore, the Java enumeration
 * value cannot just represent the C value, as it is done for {@link CConstant}. Instead, the Java
 * enumeration with this annotation can define the following methods to convert between C and Java
 * values:
 * <ul>
 * <li>An instance method annotated with {@link CEnumValue} to convert the Java object value to the
 * C integer value.
 * <li>A static method annotated with {@link CEnumLookup} to retrieve the Java object value from a C
 * integer value.
 * </ul>
 * The Java enumeration values can be annotated with {@link CEnumConstant} to specify the C
 * enumeration value. This annotation is optional and only needed if an attribute has a non-default
 * value.
 * <p>
 * Note that in C, the keyword "enum" is merely a type-safe way to specify integer constants.
 * Therefore, it is also possible to import C enumeration values as regular {@link CConstant}s into
 * Java.
 * <p>
 * {@link CEnum} can also be used to import multiple (potentially unrelated) regular integer C
 * constants into a single Java enumeration. However, please note that each C constant will be
 * converted to the C type that {@link CEnum} specifies.
 * <p>
 * The annotated class, or an outer class that contains the class, must be annotated with
 * {@link CContext}.
 *
 * @since 19.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CEnum {

    /**
     * Specifies the name of the imported C enum type. If no name is provided, the C data type
     * <code>int</code> is used instead.
     *
     * @since 19.0
     */
    String value() default "";

    /**
     * Add the C <code>enum</code> keyword to the name specified in {@link #value()}.
     *
     * @since 19.0
     */
    boolean addEnumKeyword() default false;
}
