/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot;

import java.util.Set;

import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractValueImpl;

/**
 * Represents a polyglot value. Polyglot values can either result from a {@link #isHostObject()
 * host} or guest language.
 *
 * @since 1.0
 */
public final class Value {

    final Object receiver;
    final AbstractValueImpl impl;

    Value(AbstractValueImpl impl, Object value) {
        this.impl = impl;
        this.receiver = value;
    }

    /**
     * Returns the meta representation of this polyglot value. The interpretation of this function
     * depends on the guest language. For example, in JavaScript the expression
     * <code>context.eval("js", "42")</code> will return the "number" string as meta object.
     *
     * @since 1.0
     */
    public Value getMetaObject() {
        return impl.getMetaObject(receiver);
    }

    /**
     * Returns <code>true</code> if this polyglot value has array elements. In this case array
     * elements can be accessed using {@link #getArrayElement(long)},
     * {@link #setArrayElement(long, Object)} and the array size can be queried using
     * {@link #getArraySize()}.
     *
     * @since 1.0
     */
    public boolean hasArrayElements() {
        return impl.hasArrayElements(receiver);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public Value getArrayElement(long index) {
        return impl.getArrayElement(receiver, index);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public void setArrayElement(long index, Object value) {
        impl.setArrayElement(receiver, index, value);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public long getArraySize() {
        return impl.getArraySize(receiver);
    }

    // dynamic object

    /**
     * TODO
     *
     * @since 1.0
     */
    public boolean hasMembers() {
        return impl.hasMembers(receiver);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public Value getMember(String key) {
        return impl.getMember(receiver, key);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public boolean hasMember(String key) {
        return impl.hasMember(receiver, key);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public Set<String> getMemberKeys() {
        return impl.getMemberKeys(receiver);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public void putMember(String key, Object member) {
        impl.putMember(receiver, key, member);
    }

    // executable

    /**
     * Returns <code>true</code> if the value can be executed. This indicates that the
     * {@link #execute(Object...)} can be used with this value.
     *
     * @since 1.0
     */
    public boolean canExecute() {
        return impl.canExecute(receiver);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public Value execute(Object... arguments) {
        return impl.execute(receiver, arguments);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public boolean isString() {
        return impl.isString(receiver);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public String asString() {
        return impl.asString(receiver);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public boolean fitsInInt() {
        return impl.fitsInInt(receiver);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public int asInt() {
        return impl.asInt(receiver);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public boolean isBoolean() {
        return impl.isBoolean(receiver);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public boolean asBoolean() {
        return impl.asBoolean(receiver);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public boolean isNumber() {
        return impl.isNumber(receiver);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public boolean fitsInLong() {
        return impl.fitsInLong(receiver);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public long asLong() {
        return impl.asLong(receiver);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public boolean fitsInDouble() {
        return impl.fitsInDouble(receiver);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public double asDouble() {
        return impl.asDouble(receiver);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public boolean fitsInFloat() {
        return impl.fitsInFloat(receiver);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public float asFloat() {
        return impl.asFloat(receiver);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public boolean fitsInByte() {
        return impl.fitsInByte(receiver);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public byte asByte() {
        return impl.asByte(receiver);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public boolean isNull() {
        return impl.isNull(receiver);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public boolean isNativePointer() {
        return impl.isNativePointer(receiver);
    }

    /**
     * TODO
     *
     * @since 1.0
     */
    public long asNativePointer() {
        return impl.asNativePointer(receiver);
    }

    /**
     * Returns <code>true</code> if the value originated form the host language Java. In such a case
     * the value can be accessed using {@link #asHostObject()}.
     *
     * @since 1.0
     */
    public boolean isHostObject() {
        return impl.isHostObject(receiver);
    }

    /**
     * Returns the original Java host language object.
     *
     * @throws UnsupportedOperationException if {@link #isHostObject()} is <code>false</code>.
     * @since 1.0
     */
    @SuppressWarnings("unchecked")
    public <T> T asHostObject() {
        return (T) impl.asHostObject(receiver);
    }

    /**
     * Language specific string representation of the value, when printed.
     *
     * @since 1.0
     */
    @Override
    public String toString() {
        return impl.toString(receiver);
    }

}
