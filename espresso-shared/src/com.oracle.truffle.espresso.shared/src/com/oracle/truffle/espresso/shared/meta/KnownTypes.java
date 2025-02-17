/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.shared.meta;

/**
 * Provides access to certain known classes.
 */
public interface KnownTypes<C extends TypeAccess<C, M, F>, M extends MethodAccess<C, M, F>, F extends FieldAccess<C, M, F>> {
    // Checkstyle: stop method name check
    /**
     * @return The runtime's representation of {@link java.lang.Object}
     */
    C java_lang_Object();

    /**
     * @return The runtime's representation of {@link java.lang.Throwable}
     */
    C java_lang_Throwable();

    /**
     * @return The runtime's representation of {@link java.lang.Class}
     */
    C java_lang_Class();

    /**
     * @return The runtime's representation of {@link java.lang.String}
     */
    C java_lang_String();

    /**
     * @return The runtime's representation of {@link java.lang.invoke.MethodType}
     */
    C java_lang_invoke_MethodType();

    /**
     * @return The runtime's representation of {@link java.lang.invoke.MethodHandle}
     */
    C java_lang_invoke_MethodHandle();
}
