/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents the expected guest type represented by a builder-side value, including parameters and
 * return values of {@link GuestInvoked} methods. It can also be used outside guest-invoked
 * callbacks. This annotation is informational and does not affect guest invocation, host-proxy
 * conversion, or validation.
 * Use a primitive class literal through {@link #value()}, such as {@code @GuestType(int.class)},
 * when a builder-side value represents a guest primitive.
 *
 * A class literal identifies the expected guest type by name; it does not imply that host and guest
 * classes have the same identity or trigger guest type resolution. The string form can be used when
 * the type is not accessible as a builder-side class literal.
 *
 * Specify exactly one of {@link #value()} or {@link #typeName()}. This convention is not validated.
 * The string form for reference types and arrays follows {@link Class#getTypeName()}: package names
 * use dots, nested classes use {@code $}, and array dimensions use {@code []}.
 *
 * <pre>
 * {@code @GuestType(String.class) JavaConstant string}
 * {@code @GuestType(typeName = "java.lang.invoke.MemberName") JavaConstant memberName}
 * {@code @GuestType(typeName = "java.lang.Object[]") JavaConstant array}
 * {@code @GuestType(typeName = "java.util.Map$Entry[]") JavaConstant nestedArray}
 * </pre>
 *
 * An array type hint describes the guest array represented by a value, not a builder-side array of
 * values.
 *
 * @see GuestInvoked
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
public @interface GuestType {
    /**
     * Class literal identifying the expected guest type. The default denotes an unspecified type.
     */
    Class<?> value() default GuestType.class;

    /**
     * Type name identifying the expected guest type, in the format returned by
     * {@link Class#getTypeName()}. The empty string denotes an unspecified type.
     */
    String typeName() default "";
}
