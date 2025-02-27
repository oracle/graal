/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.substitutions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Hints the expected host type for guest parameters and return types.
 *
 * <br>
 * Used to derive correct signatures for substitutions and the JNI and VM implementations. Can be
 * used as a hint (better readability) for guest parameter/return types.
 *
 * <pre>
 * {@code @JavaType(byte[].class) StaticObject data}
 * {@code @JavaType(Class.class) StaticObject clazz}
 * {@code @JavaType(internalName = "Ljava/lang/invoke/MemberName;") StaticObject memberName}
 * {@code @JavaType(internalName = "Ljava/lang/Thread$State;") StaticObject threadState}
 * </pre>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE_USE)
public @interface JavaType {
    /**
     * Host class for the expected type.
     */
    Class<?> value() default JavaType.class;

    /**
     * Class in internal form. Used when the host class is not accessible.
     */
    String internalName() default "";
}
