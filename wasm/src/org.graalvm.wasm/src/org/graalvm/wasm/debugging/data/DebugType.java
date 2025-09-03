/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.wasm.debugging.data;

import org.graalvm.wasm.debugging.DebugLocation;
import org.graalvm.wasm.debugging.data.objects.DebugConstantObject;
import org.graalvm.wasm.debugging.representation.DebugConstantDisplayValue;

import com.oracle.truffle.api.interop.InteropLibrary;

/**
 * Represents a type in the debug information.
 */
public abstract class DebugType {

    /**
     * @return The name of the type.
     */
    public abstract String asTypeName();

    /**
     * @return The value length of the type.
     */
    public abstract int valueLength();

    /**
     * Returns <code>true</code> if the type represents a basic value like a string or an int, else
     * <code>false</code>.
     * 
     * @see #asValue(DebugContext, DebugLocation)
     */
    public boolean isValue() {
        return false;
    }

    /**
     * Returns the basic value if the type represents a {@link #isValue()} like value. Returns
     * {@link DebugConstantDisplayValue#UNDEFINED} if {@link #isValue()} returns <code>false</code>.
     * 
     * @see #isValue()
     */
    @SuppressWarnings("unused")
    public Object asValue(DebugContext context, DebugLocation location) {
        return DebugConstantDisplayValue.UNDEFINED;
    }

    /**
     * Returns <code>true</code> if the type represents a basic value that can be edited like an
     * int, else <code>false</code>.
     * 
     * @see #setValue(DebugContext, DebugLocation, Object, InteropLibrary)
     */
    public boolean isModifiableValue() {
        return false;
    }

    /**
     * Changes a value if the type represents a {@link #isModifiableValue()} like value.
     * 
     * @see #isModifiableValue()
     */
    @SuppressWarnings("unused")
    public void setValue(DebugContext context, DebugLocation location, Object value, InteropLibrary lib) {
    }

    /**
     * Returns <code>true</code> if the type represents a {@link DebugObject}, else
     * <code>false</code>.
     * 
     * @see #asDebugObject(DebugContext, DebugLocation)
     */
    public boolean isDebugObject() {
        return false;
    }

    /**
     * Returns the {@link DebugObject} if the type represents a {@link #isDebugObject()} like value.
     * Returns {@link DebugConstantObject#UNDEFINED} if {@link #isDebugObject()} returns
     * <code>false</code>.
     *
     * @see #isDebugObject()
     */
    @SuppressWarnings("unused")
    public DebugObject asDebugObject(DebugContext context, DebugLocation location) {
        return DebugConstantObject.UNDEFINED;
    }

    /**
     * Returns <code>true</code> if the type represents a {@link DebugLocation}, else
     * <code>false</code>.
     * 
     * @see #asLocation(DebugContext, DebugLocation)
     */
    public boolean isLocation() {
        return false;
    }

    /**
     * Returns the {@link DebugLocation} if the type represents a {@link #isLocation()} like value.
     * Returns an invalid {@link DebugLocation} if {@link #isLocation()} returns <code>false</code>.
     *
     * @see #isLocation()
     */
    @SuppressWarnings("unused")
    public DebugLocation asLocation(DebugContext context, DebugLocation location) {
        return location.invalidate();
    }

    /**
     * Returns <code>true</code> if the type value fits into an integer, else <code>false</code>.
     *
     * @see #asInt(DebugContext, DebugLocation)
     */
    public boolean fitsIntoInt() {
        return false;
    }

    /**
     * Returns an integer if the type represents a {@link #fitsIntoInt()} like value. Return zero if
     * {@link #fitsIntoInt()} returns <code>false</code>.
     *
     * @see #fitsIntoInt()
     */
    @SuppressWarnings("unused")
    public int asInt(DebugContext context, DebugLocation location) {
        return DebugConstants.DEFAULT_I32;
    }

    /**
     * Returns <code>true</code> if the type value fits into a long, else <code>false</code>.
     * 
     * @see #asLong(DebugContext, DebugLocation)
     */
    public boolean fitsIntoLong() {
        return false;
    }

    /**
     * Returns a long if the type represents a {@link #fitsIntoLong()} like value. Returns zero if
     * {@link #fitsIntoLong()} returns <code>false</code>.
     * 
     * @see #fitsIntoLong()
     */
    @SuppressWarnings("unused")
    public long asLong(DebugContext context, DebugLocation location) {
        return DebugConstants.DEFAULT_I64;
    }

    /**
     * Returns <code>true</code> if the type has members, else <code>false</code>.
     *
     * @see #readMember(DebugContext, DebugLocation, int)
     */
    public boolean hasMembers() {
        return false;
    }

    /**
     * Returns the number of members of the type. Returns zero if {@link #hasMembers()} returns
     * <code>false</code>.
     *
     * @see #hasMembers()
     */
    public int memberCount() {
        return 0;
    }

    /**
     * Returns the member at the given index if {@link #hasMembers()} returns <code>true</code>.
     * Returns {@link DebugConstantObject#UNDEFINED} otherwise.
     * 
     * @see #hasMembers()
     */
    @SuppressWarnings("unused")
    public DebugObject readMember(DebugContext context, DebugLocation location, int index) {
        return DebugConstantObject.UNDEFINED;
    }

    /**
     * Returns <code>true</code> if the type has array elements, else <code>false</code>.
     * 
     * @see #readArrayElement(DebugContext, DebugLocation, int)
     */
    public boolean hasArrayElements() {
        return false;
    }

    /**
     * Returns the number of array dimensions if {@link #hasArrayElements()} returns
     * <code>true</code>. Returns zero otherwise.
     * 
     * @see #hasArrayElements()
     * @see #arrayDimensionSize(int)
     */
    public int arrayDimensionCount() {
        return 0;
    }

    /**
     * Returns the number of elements of the given array dimension if {@link #hasArrayElements()}
     * returns <code>true</code>. Returns zero otherwise.
     *
     * @see #hasArrayElements()
     * @see #arrayDimensionCount()
     */
    @SuppressWarnings("unused")
    public int arrayDimensionSize(int dimension) {
        return 0;
    }

    /**
     * Returns the array element at the given index if {@link #hasArrayElements()} returns
     * <code>true</code>. Returns {@link DebugConstantObject#UNDEFINED} otherwise.
     * <p>
     * The index is an absolute value considering all array dimension. This means that index 3 in an
     * <code>int[2][2]</code> array represents the value at position [1][0].
     * 
     * @see #hasArrayElements()
     */
    @SuppressWarnings("unused")
    public DebugObject readArrayElement(DebugContext context, DebugLocation location, int index) {
        return DebugConstantObject.UNDEFINED;
    }

    @Override
    public String toString() {
        return asTypeName();
    }
}
