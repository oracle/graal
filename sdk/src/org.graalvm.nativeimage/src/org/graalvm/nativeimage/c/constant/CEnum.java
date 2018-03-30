/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * Note that C enumeration are merely a type-safe way to specify integer constants in C. Therefore,
 * C enumeration values can be imported to Java as a regular {@link CConstant}; and {@link CEnum}
 * can be used to import regular integer C constants as a Java enumeration.
 * <p>
 * The annotated class, or an outer class that contains the class, must be annotated with
 * {@link CContext}.
 *
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CEnum {

    /**
     * Specifies the name of the imported C enum type. If no name is provided, <code>int</code> is
     * used instead.
     *
     * @since 1.0
     */
    String value() default "";

    /**
     * Add the C <code>enum</code> keyword to the name specified in {@link #value()}.
     *
     * @since 1.0
     */
    boolean addEnumKeyword() default false;
}
