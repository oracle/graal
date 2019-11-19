/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Marker for parameters, denoting native-word-sized (pointer, word, handle...) rather than 64-bit.
 * This annotation can be applied to {@code long} parameters and return types.
 * 
 * On the Java side all pointers are represented as {@code long}, regardless of the native word
 * size. This annotation serves to generate proper signatures to communicate with native code. It
 * can also be used as a hint for fields; but no checks are performed and no code is derived for
 * fields.
 *
 * <h4>Usage:</h4>
 * <p>
 * Receives a methodID and a pointer; both word-sized:
 * 
 * <pre>
 * public void CallVoidMethodVarargs(&#64;Host(Object.class) StaticObject receiver, &#64;Word long methodId, &#64;Word long varargsPtr)
 * </pre>
 * 
 * Returning a pointer:
 * 
 * <pre>
 * public &#64;Word long GetDoubleArrayElements(&#64;Host(double[].class) StaticObject array, &#64;Word long isCopyPtr)
 * </pre>
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
public @interface Word {
}
