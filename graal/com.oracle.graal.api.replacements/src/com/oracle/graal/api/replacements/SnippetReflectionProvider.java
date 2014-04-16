/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.replacements;

import com.oracle.graal.api.meta.*;

/**
 * Reflection operations on values represented as {@linkplain Constant constants} for the processing
 * of snippets. Snippets need a direct access to the value of object constants, which is not allowed
 * in other parts of Graal to enforce compiler-VM separation.
 * <p>
 * This interface must not be used in Graal code that is not related to snippet processing.
 */
public interface SnippetReflectionProvider {

    /**
     * Creates a boxed {@link Kind#Object object} constant.
     *
     * @param object the object value to box
     * @return a constant containing {@code object}
     */
    Constant forObject(Object object);

    /**
     * Returns the object reference the given constant represents. The constant must have kind
     * {@link Kind#Object}.
     *
     * @param constant the to access
     * @return the object value of the constant
     */
    Object asObject(Constant constant);

    /**
     * Creates a boxed constant for the given kind from an Object. The object needs to be of the
     * Java boxed type corresponding to the kind.
     *
     * @param kind the kind of the constant to create
     * @param value the Java boxed value: a {@link Byte} instance for {@link Kind#Byte}, etc.
     * @return the boxed copy of {@code value}
     */
    Constant forBoxed(Kind kind, Object value);

    /**
     * Returns the value of this constant as a boxed Java value.
     *
     * @param constant the constant to box
     * @return the value of the constant
     */
    Object asBoxedValue(Constant constant);
}
