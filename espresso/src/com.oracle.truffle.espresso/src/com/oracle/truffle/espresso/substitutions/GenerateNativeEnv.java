/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * Annotate a class implementing a native interface with this annotation to have the native env
 * processor generate boilerplate code.
 * 
 * @see com.oracle.truffle.espresso.vm.VM
 * @see com.oracle.truffle.espresso.jni.JniEnv
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface GenerateNativeEnv {
    /**
     * @return The annotation used to mark methods that are part of the implemented native
     *         interface.
     */
    Class<?> target();

    /**
     * Prepend the native env pointer to the signature.
     */
    boolean prependEnv() default false;

    /**
     * Indicates whether the methods in this class should generate code for
     * {@link CallableFromNative#invokeDirect(Object, Object[])}. This prevents methods that are not
     * expected to be directly bound to a java native method to mess with SVM reachability analysis.
     */
    boolean reachableForAutoSubstitution() default false;
}
