/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownMemberException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.TriState;
import com.oracle.truffle.sl.SLLanguage;

/**
 * Represents an SL object.
 *
 * This class defines operations that can be performed on SL Objects. While we could define all
 * these operations as individual AST nodes, we opted to define those operations by using
 * {@link com.oracle.truffle.api.library.Library a Truffle library}, or more concretely the
 * {@link InteropLibrary}. This has several advantages, but the primary one is that it allows SL
 * objects to be used in the interoperability message protocol, i.e. It allows other languages and
 * tools to operate on SL objects without necessarily knowing they are SL objects.
 *
 * SL Objects are essentially instances of {@link DynamicObject} (objects whose members can be
 * dynamically added and removed). We also annotate the class with {@link ExportLibrary} with value
 * {@link InteropLibrary InteropLibrary.class}. This essentially ensures that the build system and
 * runtime know that this class specifies the interop messages (i.e. operations) that SL can do on
 * {@link SLObject} instances.
 *
 * @see ExportLibrary
 * @see ExportMessage
 * @see InteropLibrary
 */
@SuppressWarnings("static-method")
@ExportLibrary(InteropLibrary.class)
public final class SLObject extends DynamicObject implements TruffleObject {
    protected static final int CACHE_LIMIT = 3;

    public SLObject(Shape shape) {
        super(shape);
    }

    @ExportMessage
    boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    Class<? extends TruffleLanguage<?>> getLanguage() {
        return SLLanguage.class;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static final class IsIdenticalOrUndefined {
        @Specialization
        static TriState doSLObject(SLObject receiver, SLObject other) {
            return TriState.valueOf(receiver == other);
        }

        @Fallback
        static TriState doOther(SLObject receiver, Object other) {
            return TriState.UNDEFINED;
        }
    }

    @ExportMessage
    @TruffleBoundary
    int identityHashCode() {
        return System.identityHashCode(this);
    }

    @ExportMessage
    boolean hasMetaObject() {
        return true;
    }

    @ExportMessage
    Object getMetaObject() {
        return SLType.OBJECT;
    }

    @ExportMessage
    @TruffleBoundary
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "Object";
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    Object getMemberObjects(@CachedLibrary("this") DynamicObjectLibrary objectLibrary) {
        return new Keys(objectLibrary.getKeyArray(this));
    }

    private static TruffleString getNameFromStringMember(Object member, InteropLibrary memberLibrary) {
        assert memberLibrary.isString(member) : member;
        TruffleString name;
        try {
            name = memberLibrary.asTruffleString(member);
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere("incompatible member");
        }
        return name;
    }

    @ExportMessage(name = "isMemberReadable")
    @ExportMessage(name = "isMemberModifiable")
    @ExportMessage(name = "isMemberRemovable")
    static class ExistsMember {

        @Specialization
        static boolean exists(SLObject receiver, SLMember member,
                        @CachedLibrary("receiver") DynamicObjectLibrary objectLibrary) {
            return objectLibrary.containsKey(receiver, member.getName());
        }

        @Specialization(guards = "memberLibrary.isString(member)")
        static boolean exists(SLObject receiver, Object member,
                        @CachedLibrary("receiver") DynamicObjectLibrary objectLibrary,
                        @Shared("memberLibrary") @CachedLibrary(limit = "CACHE_LIMIT") InteropLibrary memberLibrary) {
            TruffleString name = getNameFromStringMember(member, memberLibrary);
            return objectLibrary.containsKey(receiver, name);
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean isReadable(SLObject receiver, Object member) {
            return false;
        }
    }

    @ExportMessage
    boolean isMemberInsertable(Object member,
                    @CachedLibrary("this") InteropLibrary receivers) {
        return !receivers.isMemberExisting(this, member);
    }

    /**
     * {@link DynamicObjectLibrary} provides the polymorphic inline cache for reading properties.
     */
    @ExportMessage
    static class ReadMember {

        @Specialization
        static Object read(SLObject receiver, SLMember member,
                        @CachedLibrary("receiver") DynamicObjectLibrary objectLibrary) throws UnknownMemberException {
            TruffleString name = member.getName();
            return doRead(receiver, member, name, objectLibrary);
        }

        @Specialization(guards = "memberLibrary.isString(member)")
        static Object read(SLObject receiver, Object member,
                        @CachedLibrary("receiver") DynamicObjectLibrary objectLibrary,
                        @Shared("memberLibrary") @CachedLibrary(limit = "CACHE_LIMIT") InteropLibrary memberLibrary) throws UnknownMemberException {
            TruffleString name = getNameFromStringMember(member, memberLibrary);
            return doRead(receiver, member, name, objectLibrary);
        }

        @Fallback
        static Object read(@SuppressWarnings("unused") SLObject receiver, Object member) throws UnknownMemberException {
            throw UnknownMemberException.create(member);
        }

        private static Object doRead(SLObject receiver, Object member, TruffleString name, DynamicObjectLibrary objectLibrary) throws UnknownMemberException {
            Object value = objectLibrary.getOrDefault(receiver, name, null);
            if (value == null) {
                /* Property does not exist. */
                throw UnknownMemberException.create(member);
            }
            return value;
        }
    }

    /**
     * {@link DynamicObjectLibrary} provides the polymorphic inline cache for writing properties.
     */
    @ExportMessage
    static class WriteMember {

        @Specialization
        static void write(SLObject receiver, SLMember member, Object value,
                        @CachedLibrary("receiver") DynamicObjectLibrary objectLibrary) {
            TruffleString name = member.getName();
            doWrite(receiver, name, value, objectLibrary);
        }

        @Specialization(guards = "memberLibrary.isString(member)")
        static void write(SLObject receiver, Object member, Object value,
                        @CachedLibrary("receiver") DynamicObjectLibrary objectLibrary,
                        @Shared("memberLibrary") @CachedLibrary(limit = "CACHE_LIMIT") InteropLibrary memberLibrary) {
            TruffleString name = getNameFromStringMember(member, memberLibrary);
            doWrite(receiver, name, value, objectLibrary);
        }

        @Fallback
        @SuppressWarnings("unused")
        static void write(SLObject receiver, Object member, Object value) throws UnknownMemberException {
            throw UnknownMemberException.create(member);
        }

        private static void doWrite(SLObject receiver, TruffleString name, Object value, DynamicObjectLibrary objectLibrary) {
            objectLibrary.put(receiver, name, value);
        }
    }

    @ExportMessage
    static class RemoveMember {

        @Specialization
        static void remove(SLObject receiver, SLMember member,
                        @CachedLibrary("receiver") DynamicObjectLibrary objectLibrary) throws UnknownMemberException {
            TruffleString name = member.getName();
            doRemove(receiver, member, name, objectLibrary);
        }

        @Specialization(guards = "memberLibrary.isString(member)")
        static void remove(SLObject receiver, Object member,
                        @CachedLibrary("receiver") DynamicObjectLibrary objectLibrary,
                        @Shared("memberLibrary") @CachedLibrary(limit = "CACHE_LIMIT") InteropLibrary memberLibrary) throws UnknownMemberException {
            TruffleString name = getNameFromStringMember(member, memberLibrary);
            doRemove(receiver, member, name, objectLibrary);
        }

        @Fallback
        static void remove(@SuppressWarnings("unused") SLObject receiver, Object member) throws UnknownMemberException {
            throw UnknownMemberException.create(member);
        }

        private static void doRemove(SLObject receiver, Object member, TruffleString name, DynamicObjectLibrary objectLibrary) throws UnknownMemberException {
            if (objectLibrary.containsKey(receiver, name)) {
                objectLibrary.removeKey(receiver, name);
            } else {
                throw UnknownMemberException.create(member);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Keys implements TruffleObject {

        private final Object[] keys;

        Keys(Object[] keys) {
            this.keys = keys;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                throw InvalidArrayIndexException.create(index);
            }
            return new SLMember((TruffleString) keys[(int) index]);
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return keys.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < keys.length;
        }
    }
}
