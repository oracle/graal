/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes.serial;

/**
 * Experimental API. May change without notice. This interface is used as bridge between the
 * {@link PostOrderDeserializer}, {@link PostOrderSerializer} and underlying constant pool
 * implementation. A constant pool stores a value and returns an identifying index, with which the
 * object can later be returned from the pool again. All methods of this class are optional and may
 * throw a {@link UnsupportedOperationException}.
 */
public interface SerializerConstantPool {

    /**
     * Returns the constant pool index of a value that is not a java native type, a java
     * native-wrapper class or a {@link Class} instance. The implementor should support all
     * additional types that are necessary to serialize a truffle AST for a specific truffle
     * implementation. If a type is not supported by this constant pool implementation a
     * {@link UnsupportedConstantPoolTypeException} should be thrown.
     * 
     * @param clazz the {@link Class} of the value
     * @param value the value to be stored. Must be at least a subclass of the given clazz.
     * @return the constant pool index
     * @throws UnsupportedConstantPoolTypeException if a type is not supported for persistence in
     *             the constant pool.
     */
    int putObject(Class<?> clazz, Object value) throws UnsupportedConstantPoolTypeException;

    /**
     * Stores a value in the constant pool that is not a java native type, a java native-wrapper
     * class or a {@link Class} instance. The implementor should support all additional types that
     * are necessary to serialize a truffle AST for a specific truffle implementation. If a type is
     * not supported by this constant pool implementation a
     * {@link UnsupportedConstantPoolTypeException} should be thrown.
     * 
     * @param clazz the {@link Class} of the value in the constant pool.
     * @param cpi the previously returned index
     * @return the value stored inside the constant pool
     * @throws UnsupportedConstantPoolTypeException if a type is not supported for persistence in
     *             the constant pool.
     * @throws IllegalArgumentException if the provided cpi is not a valid constant pool index.
     */
    Object getObject(Class<?> clazz, int cpi) throws UnsupportedConstantPoolTypeException;

    /**
     * Stores a Class instance in the constant pool and returns the constant pool index.
     * 
     * @param value the class to store
     * @return the new or existing constant pool index of the Class
     */
    int putClass(Class<?> value);

    /**
     * Returns the {@link Class} instance to the given constant pool index.
     * 
     * @param cpi the constant pool index
     * @return stored value
     * @throws IllegalArgumentException if the constant pool indes is invalid.
     */
    Class<?> getClass(int cpi);

    /**
     * Stores an int value in the constant pool and returns the constant pool index.
     * 
     * @param value the value to store
     * @return the new or existing constant pool index of the value
     */
    int putInt(int value);

    /**
     * Returns the stored int value to the given constant pool index from the constant pool.
     * 
     * @param cpi the constant pool index
     * @return stored value
     * @throws IllegalArgumentException if the constant pool index is invalid.
     */
    int getInt(int cpi);

    /**
     * Stores a long value in the constant pool and returns the constant pool index.
     * 
     * @param value the value to store
     * @return the new or existing constant pool index of the value
     */
    int putLong(long value);

    /**
     * Returns the stored long value to the given constant pool index from the constant pool.
     * 
     * @param cpi the constant pool index
     * @return the stored value
     * @throws IllegalArgumentException if the constant pool index is invalid.
     */
    long getLong(int cpi);

    /**
     * Stores a double value in the constant pool and returns the constant pool index.
     * 
     * @param value the value to store
     * @return the new or existing constant pool index of the value
     */
    int putDouble(double value);

    /**
     * Returns the stored double value to the given constant pool index from the constant pool.
     * 
     * @param cpi the constant pool index
     * @return the stored value
     * @throws IllegalArgumentException if the constant pool index is invalid.
     */
    double getDouble(int cpi);

    /**
     * Stores a float value in the constant pool and returns the constant pool index.
     * 
     * @param value the value to store
     * @return the new or existing constant pool index of the value
     */
    int putFloat(float value);

    /**
     * Returns the stored float value to the given constant pool index from the constant pool.
     * 
     * @param cpi the constant pool index
     * @return the stored value
     * @throws IllegalArgumentException if the constant pool index is invalid.
     */
    float getFloat(int cpi);

}
