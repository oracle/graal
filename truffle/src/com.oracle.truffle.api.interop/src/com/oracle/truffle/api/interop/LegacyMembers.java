/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop;

import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Translates legacy (String-based) members to Member interop objects.
 */
final class LegacyMembers {

    private LegacyMembers() {
    }

    static Object fromLegacyMembers(Object receiver, Object legacyMembers) {
        return new StringMembers(receiver, legacyMembers);
    }

    static String getNameOf(Object member) {
        if (member instanceof String) {
            return (String) member;
        }
        if (member instanceof LegacyMembers.StringMember) {
            return ((LegacyMembers.StringMember) member).getName();
        }
        return null;
    }

    static Object fromMemberObjects(Object memberObjects) {
        return new MemberStrings(memberObjects);
    }

    /**
     * Translates a list {@link StringMembers#legacyMembers} of string names to a list of member
     * objects {@link StringMember}.
     */
    @ExportLibrary(InteropLibrary.class)
    static final class StringMembers implements TruffleObject {

        static final int LIMIT = 5;
        final Object receiver;
        final Object legacyMembers;

        StringMembers(Object receiver, Object legacyMembers) {
            this.receiver = receiver;
            this.legacyMembers = legacyMembers;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize(@CachedLibrary("this.legacyMembers") InteropLibrary membersLib) throws UnsupportedMessageException {
            return membersLib.getArraySize(legacyMembers);
        }

        @ExportMessage
        boolean isArrayElementReadable(long idx,
                        @CachedLibrary("this.legacyMembers") InteropLibrary membersLib) {
            return membersLib.isArrayElementReadable(legacyMembers, idx);
        }

        @ExportMessage
        Object readArrayElement(long idx,
                        @CachedLibrary("this.legacyMembers") InteropLibrary membersLib, //
                        @CachedLibrary(limit = "LIMIT") InteropLibrary nameLib) throws InvalidArrayIndexException, UnsupportedMessageException {
            Object member = membersLib.readArrayElement(legacyMembers, idx);
            String simpleName = nameLib.asString(member);
            return new StringMember(receiver, member, simpleName);
        }

        @ExportMessage
        boolean isArrayElementRemovable(long idx,
                        @CachedLibrary("this.legacyMembers") InteropLibrary membersLib) {
            return membersLib.isArrayElementRemovable(legacyMembers, idx);
        }

        @ExportMessage
        void removeArrayElement(long idx, @CachedLibrary("this.legacyMembers") InteropLibrary membersLib) throws InvalidArrayIndexException, UnsupportedMessageException {
            membersLib.removeArrayElement(legacyMembers, idx);
        }
    }

    /**
     * A member implementation based on a string name.
     */
    @ExportLibrary(value = InteropLibrary.class, delegateTo = "legacyMember")
    @SuppressWarnings("static-method")
    static final class StringMember implements TruffleObject {

        final Object receiver;
        final Object legacyMember;
        private final String name;

        StringMember(Object receiver, Object legacyMember, String name) {
            this.receiver = receiver;
            this.legacyMember = legacyMember;
            this.name = name;
        }

        @ExportMessage
        boolean isMember() {
            return true;
        }

        @ExportMessage
        Object getMemberSimpleName() {
            return name;
        }

        @ExportMessage
        Object getMemberQualifiedName() {
            return name;
        }

        @ExportMessage
        @SuppressWarnings("deprecation")
        boolean isMemberKindField(@CachedLibrary("this.receiver") InteropLibrary interop) {
            return !isMemberKindMethod(interop) && (interop.isMemberReadable(receiver, name) || interop.isMemberWritable(receiver, name));
        }

        @ExportMessage
        @SuppressWarnings("deprecation")
        boolean isMemberKindMethod(@CachedLibrary("this.receiver") InteropLibrary interop) {
            return interop.isMemberInvocable(receiver, name);
        }

        @ExportMessage
        boolean isMemberKindMetaObject() {
            return false;
        }

        String getName() {
            return name;
        }
    }

    /**
     * Translates a list {@link MemberStrings#memberObjects} of member objects to a list of string
     * names {@link MemberString}.
     */
    @ExportLibrary(InteropLibrary.class)
    static final class MemberStrings implements TruffleObject {

        static final int LIMIT = 5;
        final Object memberObjects;

        MemberStrings(Object memberObjects) {
            this.memberObjects = memberObjects;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize(@CachedLibrary("this.memberObjects") InteropLibrary membersLib) throws UnsupportedMessageException {
            return membersLib.getArraySize(memberObjects);
        }

        @ExportMessage
        boolean isArrayElementReadable(long idx,
                        @CachedLibrary("this.memberObjects") InteropLibrary membersLib) {
            return membersLib.isArrayElementReadable(memberObjects, idx);
        }

        @ExportMessage
        Object readArrayElement(long idx, //
                        @CachedLibrary("this.memberObjects") InteropLibrary membersLib, //
                        @CachedLibrary(limit = "LIMIT") InteropLibrary memberLib, //
                        @CachedLibrary(limit = "LIMIT") InteropLibrary nameLib) throws InvalidArrayIndexException, UnsupportedMessageException {
            Object member = membersLib.readArrayElement(memberObjects, idx);
            String simpleName = nameLib.asString(memberLib.getMemberSimpleName(member));
            return new MemberString(member, simpleName);
        }

        @ExportMessage
        boolean isArrayElementRemovable(long idx,
                        @CachedLibrary("this.memberObjects") InteropLibrary membersLib) {
            return membersLib.isArrayElementRemovable(memberObjects, idx);
        }

        @ExportMessage
        void removeArrayElement(long idx,
                        @CachedLibrary("this.memberObjects") InteropLibrary membersLib) throws InvalidArrayIndexException, UnsupportedMessageException {
            membersLib.removeArrayElement(memberObjects, idx);
        }

    }

    /**
     * A string name based on a member object.
     */
    @ExportLibrary(value = InteropLibrary.class, delegateTo = "member")
    @SuppressWarnings("static-method")
    static final class MemberString implements TruffleObject {

        final Object member;
        final String name;

        MemberString(Object member, String name) {
            this.member = member;
            this.name = name;
        }

        @ExportMessage
        boolean isMember() {
            return false;
        }

        @ExportMessage
        Object getMemberSimpleName() throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        Object getMemberQualifiedName() throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        boolean isMemberKindField() {
            return false;
        }

        @ExportMessage
        boolean isMemberKindMethod() {
            return false;
        }

        @ExportMessage
        boolean isMemberKindMetaObject() {
            return false;
        }

        @ExportMessage
        boolean isString() {
            return true;
        }

        @ExportMessage
        String asString() {
            return name;
        }

        @ExportMessage
        TruffleString asTruffleString() {
            return TruffleString.fromJavaStringUncached(name, TruffleString.Encoding.UTF_16);
        }
    }

}
