/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jni;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * Marker for parameters and return types to hint the primitive is a handle.
 *
 * <h4>Usage:</h4>
 * <p>
 * JNI handle:
 *
 * <pre>
 * public &#64;Handle(StaticObject.class) long NewGlobalRef(&#64;Handle(StaticObject.class) long handle) {
 * </pre>
 *
 * Field handle:
 *
 * <pre>
 * public boolean GetBooleanField(StaticObject object, &#64;Handle(Field.class) long fieldId)
 * </pre>
 * </p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE_USE)
public @interface Handle {
    /**
     * Class of the object referenced by the handle. Expected types are {@link Field},
     * {@link Method} and {@link StaticObject}.
     */
    Class<?> value() default Handle.class;
}
