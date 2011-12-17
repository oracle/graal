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
package com.sun.cri.ri;

/**
 * Represents the runtime representation of the constant pool that is
 * used by the compiler when parsing bytecode. The {@code lookupXXX} methods look up a constant
 * pool entry without performing resolution, and are used during compilation.
 */
public interface RiConstantPool {

    /**
     * Makes sure that the type referenced by the specified constant pool entry is loaded and
     * initialized. This can be used to compile time resolve a type. It works for field, method,
     * or type constant pool entries.
     * @param cpi the index of the constant pool entry that references the type
     * @param opcode the opcode of the instruction that references the type
     */
    void loadReferencedType(int cpi, int opcode);

    /**
     * Looks up a reference to a field. If {@code opcode} is non-negative, then resolution checks
     * specific to the JVM instruction it denotes are performed if the field is already resolved.
     * Should any of these checks fail, an {@linkplain RiField#isResolved() unresolved}
     * field reference is returned.
     *
     * @param cpi the constant pool index
     * @param opcode the opcode of the instruction for which the lookup is being performed or {@code -1}
     * @return a reference to the field at {@code cpi} in this pool
     * @throws ClassFormatError if the entry at {@code cpi} is not a field
     */
    RiField lookupField(int cpi, int opcode);

    /**
     * Looks up a reference to a method. If {@code opcode} is non-negative, then resolution checks
     * specific to the JVM instruction it denotes are performed if the method is already resolved.
     * Should any of these checks fail, an {@linkplain RiMethod#isResolved() unresolved}
     * method reference is returned.
     *
     * @param cpi the constant pool index
     * @param opcode the opcode of the instruction for which the lookup is being performed or {@code -1}
     * @return a reference to the method at {@code cpi} in this pool
     * @throws ClassFormatError if the entry at {@code cpi} is not a method
     */
    RiMethod lookupMethod(int cpi, int opcode);

    /**
     * Looks up a reference to a type. If {@code opcode} is non-negative, then resolution checks
     * specific to the JVM instruction it denotes are performed if the type is already resolved.
     * Should any of these checks fail, an {@linkplain RiType#isResolved() unresolved}
     * type reference is returned.
     *
     * @param cpi the constant pool index
     * @param opcode the opcode of the instruction for which the lookup is being performed or {@code -1}
     * @return a reference to the compiler interface type
     */
    RiType lookupType(int cpi, int opcode);

    /**
     * Looks up a method signature.
     *
     * @param cpi the constant pool index
     * @return the method signature at index {@code cpi} in this constant pool
     */
    RiSignature lookupSignature(int cpi);

    /**
     * Looks up a constant at the specified index.
     * @param cpi the constant pool index
     * @return the {@code CiConstant} instance representing the constant
     */
    Object lookupConstant(int cpi);
}
