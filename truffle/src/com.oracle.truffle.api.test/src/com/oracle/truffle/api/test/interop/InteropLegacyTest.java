/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnknownMemberException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;

import java.util.Arrays;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Test that all deprecated messages are correctly translated to the new messages.
 */
@SuppressWarnings({"deprecation", "truffle-abstract-export"})
public class InteropLegacyTest extends InteropLibraryBaseTest {

    private static final InteropLibrary INTEROP = InteropLibrary.getUncached();

    @ExportLibrary(InteropLibrary.class)
    static class LegacyMembers implements TruffleObject {

        private Object[] names;

        LegacyMembers(Object... names) {
            this.names = names;
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        Object getMembers(@SuppressWarnings("unused") boolean inludeInternal) {
            return new InteropDefaultsTest.Array(names);
        }

        private boolean haveName(String name) {
            for (int i = 0; i < names.length; i++) {
                if (names[i].equals(name)) {
                    return true;
                }
            }
            return false;
        }

        @ExportMessage
        boolean isMemberReadable(String name) {
            return haveName(name);
        }

        @ExportMessage
        Object readMember(String name) throws UnknownIdentifierException {
            for (int i = 0; i < names.length; i++) {
                if (names[i].equals(name)) {
                    return Integer.toString(i);
                }
            }
            throw UnknownIdentifierException.create(name);
        }

        @ExportMessage
        boolean isMemberModifiable(String name) {
            return haveName(name);
        }

        @ExportMessage
        boolean isMemberInsertable(String name) {
            return !haveName(name) && name.equals("insert");
        }

        @ExportMessage
        void writeMember(String name, @SuppressWarnings("unused") Object value) throws UnknownIdentifierException {
            if (!haveName(name) && !name.equals("insert")) {
                throw UnknownIdentifierException.create(name);
            }
        }

        @ExportMessage
        boolean isMemberInvocable(String name) {
            return haveName(name);
        }

        @ExportMessage
        Object invokeMember(String name, Object[] args) throws UnknownIdentifierException {
            if (!haveName(name)) {
                throw UnknownIdentifierException.create(name);
            }
            return "(" + name + ")(" + Arrays.toString(args) + ")";
        }

        @ExportMessage
        boolean isMemberRemovable(String name) {
            return names.length > 0 && names[0].equals(name);
        }

        @ExportMessage
        void removeMember(String name) throws UnknownIdentifierException {
            if (names.length > 0 && names[0].equals(name)) {
                names = Arrays.copyOfRange(names, 1, names.length);
            } else {
                throw UnknownIdentifierException.create(name);
            }
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static class StringMemberWithLocation implements TruffleObject {

        private final String name;

        StringMemberWithLocation(String name) {
            this.name = name;
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
        boolean hasSourceLocation() {
            return true;
        }

        @ExportMessage
        SourceSection getSourceLocation() {
            return Source.newBuilder(ProxyLanguage.ID, name, name).build().createSection(1);
        }
    }

    @Test
    public void testStringToObjectMembers() throws InteropException {
        LegacyMembers membersATest = new LegacyMembers();
        InteropLibrary membersALib = createLibrary(InteropLibrary.class, membersATest);
        LegacyMembers membersBTest = new LegacyMembers("m1", "m2", "insert", new StringMemberWithLocation("location"));
        InteropLibrary membersBLib = createLibrary(InteropLibrary.class, membersBTest);
        assertTrue(membersALib.hasMembers(membersATest));
        Object membersA = membersALib.getMemberObjects(membersATest);
        assertTrue(INTEROP.hasArrayElements(membersA));
        Object membersB = membersBLib.getMemberObjects(membersBTest);

        Object m1 = INTEROP.readArrayElement(membersB, 0);
        Object m2 = INTEROP.readArrayElement(membersB, 1);
        assertTrue(INTEROP.isMember(m1));
        assertTrue(INTEROP.isMember(m2));
        assertEquals("m1", INTEROP.getMemberSimpleName(m1));
        assertEquals("m1", INTEROP.getMemberQualifiedName(m1));
        assertFalse(INTEROP.hasDeclaringMetaObject(m1));
        assertFalse(INTEROP.hasMemberSignature(m1));

        assertTrue(membersBLib.isMemberReadable(membersBTest, m1));
        Object r = membersBLib.readMember(membersBTest, m1);
        assertEquals("0", r);
        assertFalse(membersALib.isMemberReadable(membersATest, m1));
        assertFails(() -> membersALib.readMember(membersATest, m1), UnknownMemberException.class);

        assertTrue(membersBLib.isMemberWritable(membersBTest, m1));
        membersBLib.writeMember(membersBTest, m1, 0);
        assertFalse(membersALib.isMemberWritable(membersATest, m1));
        assertFails(() -> {
            membersALib.writeMember(membersATest, m1, 0);
            return null;
        }, UnknownMemberException.class);

        Object insert = INTEROP.readArrayElement(membersB, 2);
        assertFalse(membersALib.isMemberInsertable(membersATest, m1));
        assertTrue(membersALib.isMemberInsertable(membersATest, insert));
        membersALib.writeMember(membersATest, insert, 0);
        assertFails(() -> {
            membersALib.writeMember(membersATest, m1, 0);
            return null;
        }, UnknownMemberException.class);

        assertTrue(membersBLib.isMemberInvocable(membersBTest, m1));
        Object iRes = membersBLib.invokeMember(membersBTest, m1, 1, 2);
        assertEquals("(m1)([1, 2])", iRes);
        assertFalse(membersALib.isMemberInvocable(membersATest, m1));
        assertFails(() -> membersALib.invokeMember(membersATest, m1, 1, 2), UnknownMemberException.class);

        assertTrue(membersBLib.isMemberRemovable(membersBTest, m1));
        assertFalse(membersBLib.isMemberRemovable(membersBTest, m2));
        assertFails(() -> {
            membersALib.removeMember(membersATest, m2);
            return null;
        }, UnknownMemberException.class);
        membersBLib.removeMember(membersBTest, m1);

        Object location = INTEROP.readArrayElement(membersB, 3);
        assertTrue(INTEROP.isMember(location));
        assertEquals("location", INTEROP.getMemberSimpleName(location));
        assertTrue(INTEROP.hasSourceLocation(location));
        assertEquals("location", INTEROP.getSourceLocation(location).getCharacters());
    }

    @ExportLibrary(InteropLibrary.class)
    static class ObjectMembers implements TruffleObject {

        private Object[] members;
        private String[] names;

        ObjectMembers(Object... members) throws UnsupportedMessageException {
            this.members = members;
            this.names = new String[members.length];
            for (int i = 0; i < members.length; i++) {
                names[i] = INTEROP.asString(INTEROP.getMemberSimpleName(members[i]));
            }
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        Object getMemberObjects() {
            return new InteropDefaultsTest.Array(members);
        }

        private boolean haveName(String name) {
            for (int i = 0; i < names.length; i++) {
                if (names[i].equals(name)) {
                    return true;
                }
            }
            return false;
        }

        private boolean haveMember(Object member) {
            if (InteropLibrary.getUncached().isString(member)) {
                String name;
                try {
                    name = InteropLibrary.getUncached().asString(member);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                return haveName(name);
            }
            for (int i = 0; i < members.length; i++) {
                if (members[i].equals(member)) {
                    return true;
                }
            }
            return false;
        }

        private static String getName(Object member) {
            String name;
            if (InteropLibrary.getUncached().isString(member)) {
                try {
                    name = InteropLibrary.getUncached().asString(member);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            } else if (member instanceof ObjectMember) {
                name = ((ObjectMember) member).name;
            } else {
                name = null;
            }
            return name;
        }

        @ExportMessage
        boolean isMemberReadable(Object member) {
            return haveMember(member);
        }

        @ExportMessage
        Object readMember(Object member) throws UnknownMemberException {
            if (InteropLibrary.getUncached().isString(member)) {
                String name;
                try {
                    name = InteropLibrary.getUncached().asString(member);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                for (int i = 0; i < names.length; i++) {
                    if (names[i].equals(name)) {
                        return Integer.toString(i);
                    }
                }
            } else {
                for (int i = 0; i < members.length; i++) {
                    if (members[i] == member) {
                        return Integer.toString(i);
                    }
                }
            }
            throw UnknownMemberException.create(member);
        }

        @ExportMessage
        boolean isMemberModifiable(Object member) {
            return haveMember(member);
        }

        @ExportMessage
        boolean isMemberInsertable(Object member) {
            if (haveMember(member)) {
                return false;
            }
            String name = getName(member);
            return "insert".equals(name);
        }

        @ExportMessage
        void writeMember(Object member, @SuppressWarnings("unused") Object value) throws UnknownMemberException {
            if (!haveMember(member) && !"insert".equals(getName(member))) {
                throw UnknownMemberException.create(member);
            }
        }

        @ExportMessage
        boolean isMemberInvocable(Object member) {
            return haveMember(member);
        }

        @ExportMessage
        Object invokeMember(Object member, Object[] args) throws UnknownMemberException {
            if (!haveMember(member)) {
                throw UnknownMemberException.create(member);
            }
            return "(" + getName(member) + ")(" + Arrays.toString(args) + ")";
        }

        @ExportMessage
        boolean isMemberRemovable(Object member) {
            return names.length > 0 && names[0] == getName(member);
        }

        @ExportMessage
        void removeMember(Object member) throws UnknownMemberException {
            if (names.length > 0 && names[0] == getName(member)) {
                members = Arrays.copyOfRange(members, 1, members.length);
                names = Arrays.copyOfRange(names, 1, names.length);
            } else {
                throw UnknownMemberException.create(member);
            }
        }

    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static class ObjectMember implements TruffleObject {

        final String name;

        ObjectMember(String name) {
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
        @CompilerDirectives.TruffleBoundary
        Object getMemberQualifiedName() {
            return ObjectMember.class.getPackageName() + '.' + name;
        }

        @ExportMessage
        final boolean isMemberKindField() {
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
    }

    @ExportLibrary(InteropLibrary.class)
    static class ObjectMemberWithLocation extends ObjectMember {

        ObjectMemberWithLocation(String name) {
            super(name);
        }

        @ExportMessage
        boolean hasSourceLocation() {
            return true;
        }

        @ExportMessage
        SourceSection getSourceLocation() {
            return Source.newBuilder(ProxyLanguage.ID, name, name).build().createSection(1);
        }
    }

    @Test
    public void testObjectToStringMembers() throws InteropException {
        ObjectMembers membersATest = new ObjectMembers();
        InteropLibrary membersALib = createLibrary(InteropLibrary.class, membersATest);
        Object m1Obj = new ObjectMember("m1");
        Object m2Obj = new ObjectMember("m2");
        Object insertObj = new ObjectMember("insert");
        Object locationObj = new ObjectMemberWithLocation("location");
        ObjectMembers membersBTest = new ObjectMembers(m1Obj, m2Obj, insertObj, locationObj);
        InteropLibrary membersBLib = createLibrary(InteropLibrary.class, membersBTest);
        assertTrue(membersALib.hasMembers(membersATest));
        Object membersA = membersALib.getMembers(membersATest);
        assertTrue(INTEROP.hasArrayElements(membersA));
        Object membersB = membersBLib.getMembers(membersBTest);

        String m1 = INTEROP.asString(INTEROP.readArrayElement(membersB, 0));
        String m2 = INTEROP.asString(INTEROP.readArrayElement(membersB, 1));
        assertEquals("m1", m1);
        assertEquals("m2", m2);

        assertTrue(membersBLib.isMemberReadable(membersBTest, m1));
        Object r = membersBLib.readMember(membersBTest, m1);
        assertEquals("0", r);
        assertFalse(membersALib.isMemberReadable(membersATest, m1));
        assertFails(() -> membersALib.readMember(membersATest, m1), UnknownIdentifierException.class);

        assertTrue(membersBLib.isMemberWritable(membersBTest, m1));
        membersBLib.writeMember(membersBTest, m1, 0);
        assertFalse(membersALib.isMemberWritable(membersATest, m1));
        assertFails(() -> {
            membersALib.writeMember(membersATest, m1, 0);
            return null;
        }, UnknownIdentifierException.class);

        String insert = INTEROP.asString(INTEROP.readArrayElement(membersB, 2));
        assertFalse(membersALib.isMemberInsertable(membersATest, m1));
        assertTrue(membersALib.isMemberInsertable(membersATest, insert));
        membersALib.writeMember(membersATest, insert, 0);
        assertFails(() -> {
            membersALib.writeMember(membersATest, m1, 0);
            return null;
        }, UnknownIdentifierException.class);

        assertTrue(membersBLib.isMemberInvocable(membersBTest, m1));
        Object iRes = membersBLib.invokeMember(membersBTest, m1, 1, 2);
        assertEquals("(m1)([1, 2])", iRes);
        assertFalse(membersALib.isMemberInvocable(membersATest, m1));
        assertFails(() -> membersALib.invokeMember(membersATest, m1, 1, 2), UnknownIdentifierException.class);

        assertTrue(membersBLib.isMemberRemovable(membersBTest, m1));
        assertFalse(membersBLib.isMemberRemovable(membersBTest, m2));
        assertFails(() -> {
            membersALib.removeMember(membersATest, m2);
            return null;
        }, UnknownIdentifierException.class);
        membersBLib.removeMember(membersBTest, m1);

        Object location = INTEROP.readArrayElement(membersB, 3);
        assertTrue(INTEROP.isString(location));
        assertEquals("location", INTEROP.asString(location));
        assertTrue(INTEROP.hasSourceLocation(location));
        assertEquals("location", INTEROP.getSourceLocation(location).getCharacters());
    }
}
