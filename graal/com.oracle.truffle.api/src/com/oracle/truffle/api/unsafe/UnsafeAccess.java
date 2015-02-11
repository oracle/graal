/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.unsafe;

public interface UnsafeAccess {

    /**
     * Casts the given value to the value of the given type without any checks. The class must
     * evaluate to a constant. The condition parameter gives a hint to the compiler under which
     * circumstances this cast can be moved to an earlier location in the program.
     *
     * @param value the value that is known to have the specified type
     * @param type the specified new type of the value
     * @param condition the condition that makes this cast safe also at an earlier location of the
     *            program
     * @param nonNull whether value is known to never be null
     * @return the value to be casted to the new type
     */
    <T> T uncheckedCast(Object value, Class<T> type, boolean condition, boolean nonNull);

    /**
     * Unsafe access to a boolean value within an object. The condition parameter gives a hint to
     * the compiler under which circumstances this access can be moved to an earlier location in the
     * program. The location identity gives a hint to the compiler for improved global value
     * numbering.
     *
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that makes this access safe also at an earlier location in the
     *            program
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     * @return the accessed value
     */
    boolean getBoolean(Object receiver, long offset, boolean condition, Object locationIdentity);

    /**
     * Unsafe access to a byte value within an object. The condition parameter gives a hint to the
     * compiler under which circumstances this access can be moved to an earlier location in the
     * program. The location identity gives a hint to the compiler for improved global value
     * numbering.
     *
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that makes this access safe also at an earlier location in the
     *            program
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     * @return the accessed value
     */
    byte getByte(Object receiver, long offset, boolean condition, Object locationIdentity);

    /**
     * Unsafe access to a short value within an object. The condition parameter gives a hint to the
     * compiler under which circumstances this access can be moved to an earlier location in the
     * program. The location identity gives a hint to the compiler for improved global value
     * numbering.
     *
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that makes this access safe also at an earlier location in the
     *            program
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     * @return the accessed value
     */
    short getShort(Object receiver, long offset, boolean condition, Object locationIdentity);

    /**
     * Unsafe access to an int value within an object. The condition parameter gives a hint to the
     * compiler under which circumstances this access can be moved to an earlier location in the
     * program. The location identity gives a hint to the compiler for improved global value
     * numbering.
     *
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that makes this access safe also at an earlier location in the
     *            program
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     * @return the accessed value
     */
    int getInt(Object receiver, long offset, boolean condition, Object locationIdentity);

    /**
     * Unsafe access to a long value within an object. The condition parameter gives a hint to the
     * compiler under which circumstances this access can be moved to an earlier location in the
     * program. The location identity gives a hint to the compiler for improved global value
     * numbering.
     *
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that makes this access safe also at an earlier location in the
     *            program
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     * @return the accessed value
     */
    long getLong(Object receiver, long offset, boolean condition, Object locationIdentity);

    /**
     * Unsafe access to a float value within an object. The condition parameter gives a hint to the
     * compiler under which circumstances this access can be moved to an earlier location in the
     * program. The location identity gives a hint to the compiler for improved global value
     * numbering.
     *
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that makes this access safe also at an earlier location in the
     *            program
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     * @return the accessed value
     */
    float getFloat(Object receiver, long offset, boolean condition, Object locationIdentity);

    /**
     * Unsafe access to a double value within an object. The condition parameter gives a hint to the
     * compiler under which circumstances this access can be moved to an earlier location in the
     * program. The location identity gives a hint to the compiler for improved global value
     * numbering.
     *
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that makes this access safe also at an earlier location in the
     *            program
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     * @return the accessed value
     */
    double getDouble(Object receiver, long offset, boolean condition, Object locationIdentity);

    /**
     * Unsafe access to an Object value within an object. The condition parameter gives a hint to
     * the compiler under which circumstances this access can be moved to an earlier location in the
     * program. The location identity gives a hint to the compiler for improved global value
     * numbering.
     *
     * @param receiver the object that is accessed
     * @param offset the offset at which to access the object in bytes
     * @param condition the condition that makes this access safe also at an earlier location in the
     *            program
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     * @return the accessed value
     */
    Object getObject(Object receiver, long offset, boolean condition, Object locationIdentity);

    /**
     * Write a boolean value within an object. The location identity gives a hint to the compiler
     * for improved global value numbering.
     *
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     */
    void putBoolean(Object receiver, long offset, boolean value, Object locationIdentity);

    /**
     * Write a byte value within an object. The location identity gives a hint to the compiler for
     * improved global value numbering.
     *
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     */
    void putByte(Object receiver, long offset, byte value, Object locationIdentity);

    /**
     * Write a short value within an object. The location identity gives a hint to the compiler for
     * improved global value numbering.
     *
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     */
    void putShort(Object receiver, long offset, short value, Object locationIdentity);

    /**
     * Write an int value within an object. The location identity gives a hint to the compiler for
     * improved global value numbering.
     *
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     */
    void putInt(Object receiver, long offset, int value, Object locationIdentity);

    /**
     * Write a long value within an object. The location identity gives a hint to the compiler for
     * improved global value numbering.
     *
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     */
    void putLong(Object receiver, long offset, long value, Object locationIdentity);

    /**
     * Write a float value within an object. The location identity gives a hint to the compiler for
     * improved global value numbering.
     *
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     */
    void putFloat(Object receiver, long offset, float value, Object locationIdentity);

    /**
     * Write a double value within an object. The location identity gives a hint to the compiler for
     * improved global value numbering.
     *
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     */
    void putDouble(Object receiver, long offset, double value, Object locationIdentity);

    /**
     * Write an Object value within an object. The location identity gives a hint to the compiler
     * for improved global value numbering.
     *
     * @param receiver the object that is written to
     * @param offset the offset at which to write to the object in bytes
     * @param value the value to be written
     * @param locationIdentity the location identity token that can be used for improved global
     *            value numbering or null
     */
    void putObject(Object receiver, long offset, Object value, Object locationIdentity);
}
