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
package com.oracle.truffle.espresso.ffi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker for parameters and return types to hint the interop object is a native pointer.
 *
 * On the Java/Espresso side all pointers are represented as interop {@code TruffleObject}
 * instances. This annotation serves to generate proper signatures to communicate with native code.
 * It can also be used as a hint for fields and local variables; but no checks are performed and no
 * code is derived for those.
 *
 * <h4>Usage:</h4>
 * <p>
 * Receives a methodID and a pointer:
 *
 * <pre>
 * public void CallVoidMethodVarargs(&#64;Host(Object.class) StaticObject receiver, &#64;Handle(Method.class) long methodId, &#64;Pointer TruffleObject varargsPtr)
 * </pre>
 *
 * Returning a pointer:
 *
 * <pre>
 * public &#64;Pointer TruffleObject GetDoubleArrayElements(&#64;Host(double[].class) StaticObject array, &#64;Pointer TruffleObject isCopyPtr)
 * </pre>
 * </p>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE_USE)
public @interface Pointer {
}
