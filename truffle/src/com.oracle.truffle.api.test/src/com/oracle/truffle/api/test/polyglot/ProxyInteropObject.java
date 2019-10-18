/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * Helper class for tests to simplify the declaration of interop objects.
 */
@SuppressWarnings("unused")
@ExportLibrary(InteropLibrary.class)
public abstract class ProxyInteropObject implements TruffleObject {

    @ExportMessage
    protected boolean isNumber() {
        return false;
    }

    @ExportMessage
    protected float asFloat() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected boolean fitsInByte() {
        return false;
    }

    @ExportMessage
    protected boolean fitsInLong() {
        return false;
    }

    @ExportMessage
    protected boolean fitsInFloat() {
        return false;
    }

    @ExportMessage
    protected boolean fitsInShort() {
        return false;
    }

    @ExportMessage
    protected boolean fitsInDouble() {
        return false;
    }

    @ExportMessage
    protected double asDouble() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected byte asByte() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected short asShort() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected long asLong() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected int asInt() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected boolean fitsInInt() {
        return false;
    }

    @ExportMessage
    protected boolean isNull() {
        return false;
    }

    @ExportMessage
    protected boolean isBoolean() {
        return false;
    }

    @ExportMessage
    protected boolean asBoolean() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected boolean hasMembers() {
        return false;
    }

    @ExportMessage
    protected boolean isMemberReadable(String member) {
        return false;
    }

    @ExportMessage
    protected boolean isMemberModifiable(String member) {
        return false;
    }

    @ExportMessage
    protected boolean isMemberRemovable(String member) {
        return false;
    }

    @ExportMessage
    protected boolean isMemberInternal(String member) {
        return false;
    }

    @ExportMessage
    protected boolean isMemberInsertable(String member) {
        return false;
    }

    @ExportMessage
    protected boolean hasMemberReadSideEffects(String member) {
        return false;
    }

    @ExportMessage
    protected boolean hasMemberWriteSideEffects(String member) {
        return false;
    }

    @ExportMessage
    protected void removeMember(String member) throws UnsupportedMessageException, UnknownIdentifierException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected Object readMember(String member) throws UnsupportedMessageException, UnknownIdentifierException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected void writeMember(String member, Object value) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected boolean hasArrayElements() {
        return false;
    }

    @ExportMessage
    protected boolean isArrayElementReadable(long index) {
        return false;
    }

    @ExportMessage
    protected boolean isArrayElementModifiable(long index) {
        return false;
    }

    @ExportMessage
    protected boolean isArrayElementInsertable(long index) {
        return false;
    }

    @ExportMessage
    protected boolean isArrayElementRemovable(long index) {
        return false;
    }

    @ExportMessage
    protected void writeArrayElement(long index, Object value) throws UnsupportedMessageException, UnsupportedTypeException, InvalidArrayIndexException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected void removeArrayElement(long index) throws UnsupportedMessageException, InvalidArrayIndexException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected Object readArrayElement(long index) throws UnsupportedMessageException, InvalidArrayIndexException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected long getArraySize() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected Object execute(Object[] arguments) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected boolean isExecutable() {
        return false;
    }

    @ExportMessage
    protected Object instantiate(Object[] arguments) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected boolean isInstantiable() {
        return false;
    }

    @ExportMessage
    protected boolean isString() {
        return false;
    }

    @ExportMessage
    protected String asString() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected long asPointer() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected boolean isPointer() {
        return false;
    }

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    public static class InteropWrapper extends ProxyInteropObject {

        protected final Object delegate;

        protected InteropWrapper(Object delegate) {
            this.delegate = delegate;
        }

        @Override
        protected boolean isNumber() {
            return INTEROP.isNumber(delegate);
        }

        @Override
        protected float asFloat() throws UnsupportedMessageException {
            return INTEROP.asFloat(delegate);
        }

        @Override
        protected boolean fitsInByte() {
            return INTEROP.fitsInByte(delegate);
        }

        @Override
        protected boolean fitsInLong() {
            return INTEROP.fitsInLong(delegate);
        }

        @Override
        protected boolean fitsInFloat() {
            return INTEROP.fitsInFloat(delegate);
        }

        @Override
        protected boolean fitsInShort() {
            return INTEROP.fitsInShort(delegate);
        }

        @Override
        protected boolean fitsInDouble() {
            return INTEROP.fitsInDouble(delegate);
        }

        @Override
        protected double asDouble() throws UnsupportedMessageException {
            return INTEROP.asDouble(delegate);
        }

        @Override
        protected byte asByte() throws UnsupportedMessageException {
            return INTEROP.asByte(delegate);
        }

        @Override
        protected short asShort() throws UnsupportedMessageException {
            return INTEROP.asShort(delegate);
        }

        @Override
        protected long asLong() throws UnsupportedMessageException {
            return INTEROP.asLong(delegate);
        }

        @Override
        protected int asInt() throws UnsupportedMessageException {
            return INTEROP.asInt(delegate);
        }

        @Override
        protected boolean fitsInInt() {
            return INTEROP.fitsInInt(delegate);
        }

        @Override
        protected boolean isNull() {
            return INTEROP.isNull(delegate);
        }

        @Override
        protected boolean isBoolean() {
            return INTEROP.isBoolean(delegate);
        }

        @Override
        protected boolean asBoolean() throws UnsupportedMessageException {
            return INTEROP.asBoolean(delegate);
        }

        @Override
        protected boolean hasMembers() {
            return INTEROP.hasMembers(delegate);
        }

        @Override
        protected boolean isMemberReadable(String member) {
            return INTEROP.isMemberReadable(delegate, member);
        }

        @Override
        protected boolean isMemberModifiable(String member) {
            return INTEROP.isMemberModifiable(delegate, member);
        }

        @Override
        protected boolean isMemberRemovable(String member) {
            return INTEROP.isMemberRemovable(delegate, member);
        }

        @Override
        protected boolean isMemberInternal(String member) {
            return INTEROP.isMemberInternal(delegate, member);
        }

        @Override
        protected boolean isMemberInsertable(String member) {
            return INTEROP.isMemberInsertable(delegate, member);
        }

        @Override
        protected void removeMember(String member) throws UnsupportedMessageException, UnknownIdentifierException {
            INTEROP.removeMember(delegate, member);
        }

        @Override
        protected Object readMember(String member) throws UnsupportedMessageException, UnknownIdentifierException {
            return INTEROP.readMember(delegate, member);
        }

        @Override
        protected Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
            return INTEROP.getMembers(delegate, includeInternal);
        }

        @Override
        protected void writeMember(String member, Object value) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
            INTEROP.writeMember(delegate, member, value);
        }

        @Override
        protected boolean hasArrayElements() {
            return INTEROP.hasArrayElements(delegate);
        }

        @Override
        protected boolean isArrayElementReadable(long index) {
            return INTEROP.isArrayElementReadable(delegate, index);
        }

        @Override
        protected boolean hasMemberReadSideEffects(String member) {
            return INTEROP.hasMemberReadSideEffects(delegate, member);
        }

        @Override
        protected boolean hasMemberWriteSideEffects(String member) {
            return INTEROP.hasMemberWriteSideEffects(delegate, member);
        }

        @Override
        protected boolean isArrayElementModifiable(long index) {
            return INTEROP.isArrayElementModifiable(delegate, index);
        }

        @Override
        protected boolean isArrayElementInsertable(long index) {
            return INTEROP.isArrayElementInsertable(delegate, index);
        }

        @Override
        protected boolean isArrayElementRemovable(long index) {
            return INTEROP.isArrayElementRemovable(delegate, index);
        }

        @Override
        protected void writeArrayElement(long index, Object value) throws UnsupportedMessageException, UnsupportedTypeException, InvalidArrayIndexException {
            INTEROP.writeArrayElement(delegate, index, value);
        }

        @Override
        protected void removeArrayElement(long index) throws UnsupportedMessageException, InvalidArrayIndexException {
            INTEROP.removeArrayElement(delegate, index);
        }

        @Override
        protected Object readArrayElement(long index) throws UnsupportedMessageException, InvalidArrayIndexException {
            return INTEROP.readArrayElement(delegate, index);
        }

        @Override
        protected long getArraySize() throws UnsupportedMessageException {
            return INTEROP.getArraySize(delegate);
        }

        @Override
        protected Object execute(Object[] arguments) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
            return INTEROP.execute(delegate, arguments);
        }

        @Override
        protected boolean isExecutable() {
            return INTEROP.isExecutable(delegate);
        }

        @Override
        protected Object instantiate(Object[] arguments) throws UnsupportedMessageException, UnsupportedTypeException, ArityException {
            return INTEROP.instantiate(delegate, arguments);
        }

        @Override
        protected boolean isInstantiable() {
            return INTEROP.isInstantiable(delegate);
        }

        @Override
        protected boolean isString() {
            return INTEROP.isString(delegate);
        }

        @Override
        protected String asString() throws UnsupportedMessageException {
            return INTEROP.asString(delegate);
        }

        @Override
        protected long asPointer() throws UnsupportedMessageException {
            return INTEROP.asPointer(delegate);
        }

        @Override
        protected boolean isPointer() {
            return INTEROP.isPointer(delegate);
        }

    }

}
