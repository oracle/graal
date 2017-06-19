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
 * The dynamic nature of languages requires dynamic access to polyglot values.
 */
public final class Value {

    final Object receiver;
    final AbstractValueImpl impl;

    Value(AbstractValueImpl impl, Object value) {
        this.impl = impl;
        this.receiver = value;
    }

    public Value getMetaObject() {
        return impl.getMetaObject(receiver);
    }

    public boolean hasArrayElements() {
        return impl.hasArrayElements(receiver);
    }

    public Value getArrayElement(long index) {
        return impl.getArrayElement(receiver, index);
    }

    public void setArrayElement(long index, Object value) {
        impl.setArrayElement(receiver, index, value);
    }

    public long getArraySize() {
        return impl.getArraySize(receiver);
    }

    // dynamic object

    public boolean hasMembers() {
        return impl.hasMembers(receiver);
    }

    public Value getMember(String key) {
        return impl.getMember(receiver, key);
    }

    public boolean hasMember(String key) {
        return impl.hasMember(receiver, key);
    }

    public Set<String> getMemberKeys() {
        return impl.getMemberKeys(receiver);
    }

    public void putMember(String key, Object member) {
        impl.putMember(receiver, key, member);
    }

    // executable

    public boolean canExecute() {
        return impl.canExecute(receiver);
    }

    public Value execute(Object... arguments) {
        return impl.execute(receiver, arguments);
    }

    public boolean isString() {
        return impl.isString(receiver);
    }

    public String asString() {
        return impl.asString(receiver);
    }

    public boolean fitsInInt() {
        return impl.fitsInInt(receiver);
    }

    public int asInt() {
        return impl.asInt(receiver);
    }

    public boolean isBoolean() {
        return impl.isBoolean(receiver);
    }

    public boolean asBoolean() {
        return impl.asBoolean(receiver);
    }

    public boolean isNumber() {
        return impl.isNumber(receiver);
    }

    public boolean fitsInLong() {
        return impl.fitsInLong(receiver);
    }

    public long asLong() {
        return impl.asLong(receiver);
    }

    public boolean fitsInDouble() {
        return impl.fitsInDouble(receiver);
    }

    public double asDouble() {
        return impl.asDouble(receiver);
    }

    public boolean fitsInFloat() {
        return impl.fitsInFloat(receiver);
    }

    public float asFloat() {
        return impl.asFloat(receiver);
    }

    public boolean fitsInByte() {
        return impl.fitsInByte(receiver);
    }

    public byte asByte() {
        return impl.asByte(receiver);
    }

    public boolean isNull() {
        return impl.isNull(receiver);
    }

    public boolean isNativePointer() {
        return impl.isNativePointer(receiver);
    }

    public long asNativePointer() {
        return impl.asNativePointer(receiver);
    }

    /**
     * Returns <code>true</code> if the value is a Java object.
     *
     * @param clazz
     * @return
     */
    public boolean isHostObject() {
        return impl.isHostObject(receiver);
    }

    /**
     * Return as coerced original java type.
     *
     * @param clazz
     * @return
     */
    @SuppressWarnings("unchecked")
    public <T> T asHostObject() {
        return (T) impl.asHostObject(receiver);
    }

    /**
     * Language specific string representation of the value, when printed.
     */
    @Override
    public String toString() {
        return impl.toString(receiver);
    }

}
