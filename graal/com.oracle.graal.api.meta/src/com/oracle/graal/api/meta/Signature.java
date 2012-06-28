/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @see <a href="http://java.sun.com/docs/books/jvms/second_edition/html/ClassFile.doc.html#7035">Method Descriptors</a>
 */
public interface Signature {
    /**
     * Gets the number of arguments in this signature, adding 1 for a receiver if requested.
     *
     * @param receiver true if 1 is to be added to the result for a receiver
     * @return the number of arguments + 1 iff {@code receiver == true}
     */
    int argumentCount(boolean receiver);

    /**
     * Gets the argument type at the specified position. This method will return a
     * {@linkplain ResolvedJavaType resolved} type if possible but without
     * triggering any class loading or resolution.
     *
     * @param index the index into the parameters, with {@code 0} indicating the first parameter
     * @param accessingClass the context of the type lookup. If accessing class is resolved, its class loader
     *        is used to retrieve an existing resolved type. This value can be {@code null} if the caller does
     *        not care for a resolved type.
     * @return the {@code index}'th argument type
     */
    JavaType argumentTypeAt(int index, ResolvedJavaType accessingClass);

    /**
     * Gets the argument kind at the specified position.
     * @param index the index into the parameters, with {@code 0} indicating the first parameter
     * @return the kind of the argument at the specified position
     */
    Kind argumentKindAt(int index);

    /**
     * Gets the return type of this signature. This method will return a
     * {@linkplain ResolvedJavaType resolved} type if possible but without
     * triggering any class loading or resolution.
     *
     * @param accessingClass the context of the type lookup. If accessing class is resolved, its class loader
     *        is used to retrieve an existing resolved type. This value can be {@code null} if the caller does
     *        not care for a resolved type.
     * @return the compiler interface type representing the return type
     */
    JavaType returnType(JavaType accessingClass);

    /**
     * Gets the return kind of this signature.
     * @return the return kind
     */
    Kind returnKind();

    /**
     * Converts this signature to a string.
     * @return the signature as a string
     */
    String asString();

    /**
     * Gets the size, in Java slots, of the arguments to this signature.
     * @param withReceiver {@code true} if to add a slot for a receiver object; {@code false} not to include the receiver
     * @return the size of the arguments in slots
     */
    int argumentSlots(boolean withReceiver);
}
