/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.api.meta;

/**
 * Represents a method signature provided by the runtime.
 * 
 * @see <a href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.3">Method
 *      Descriptors</a>
 */
public interface Signature {

    /**
     * Returns the number of parameters in this signature, adding 1 for a receiver if requested.
     * 
     * @param receiver true if 1 is to be added to the result for a receiver
     * @return the number of parameters; + 1 iff {@code receiver == true}
     */
    int getParameterCount(boolean receiver);

    /**
     * Gets the parameter type at the specified position. This method returns a
     * {@linkplain ResolvedJavaType resolved} type if possible but without triggering any class
     * loading or resolution.
     * 
     * @param index the index into the parameters, with {@code 0} indicating the first parameter
     * @param accessingClass the context of the type lookup. If accessing class is provided, its
     *            class loader is used to retrieve an existing resolved type. This value can be
     *            {@code null} if the caller does not care for a resolved type.
     * @return the {@code index}'th parameter type
     */
    JavaType getParameterType(int index, ResolvedJavaType accessingClass);

    /**
     * Gets the parameter kind at the specified position. This is the same as calling
     * {@link #getParameterType}. {@link JavaType#getKind getKind}.
     * 
     * @param index the index into the parameters, with {@code 0} indicating the first parameter
     * @return the kind of the parameter at the specified position
     */
    Kind getParameterKind(int index);

    /**
     * Gets the return type of this signature. This method will return a
     * {@linkplain ResolvedJavaType resolved} type if possible but without triggering any class
     * loading or resolution.
     * 
     * @param accessingClass the context of the type lookup. If accessing class is provided, its
     *            class loader is used to retrieve an existing resolved type. This value can be
     *            {@code null} if the caller does not care for a resolved type.
     * @return the return type
     */
    JavaType getReturnType(ResolvedJavaType accessingClass);

    /**
     * Gets the return kind of this signature. This is the same as calling {@link #getReturnType}.
     * {@link JavaType#getKind getKind}.
     */
    Kind getReturnKind();

    /**
     * Gets the size, in Java slots, of the parameters to this signature.
     * 
     * @param withReceiver {@code true} if to add a slot for a receiver object; {@code false} not to
     *            include the receiver
     * @return the size of the parameters in slots
     */
    int getParameterSlots(boolean withReceiver);
}
