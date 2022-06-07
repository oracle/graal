/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.interop.examples;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
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
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

/**
 * A simple minimal implementation of interop members.
 */
@SuppressWarnings({"static-method", "unused"})
public class SimpleMemberExample extends AbstractPolyglotTest {

    @ExportLibrary(InteropLibrary.class)
    static final class ObjectWithMembers implements TruffleObject {

        static final int LIMIT = 3;

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        Object getMemberObjects() {
            return new MembersArray(new SimpleMember("a", false), new SimpleMember("f", true));
        }

        @ExportMessage
        static class IsMemberReadable {

            @Specialization
            static boolean isReadableMember(ObjectWithMembers receiver, SimpleMember member) {
                // Add a test whether the receiver really contains the provided member.
                return !member.isInvocable();
            }

            @Specialization(guards = "memberLibrary.isString(memberName)")
            static boolean isReadableString(ObjectWithMembers receiver, Object memberName,
                            @Shared("memberLibrary") @CachedLibrary(limit = "LIMIT") InteropLibrary memberLibrary) {
                String name;
                try {
                    name = memberLibrary.asString(memberName);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                return switch (name) {
                    case "a" -> true;
                    default -> false;
                };
            }

            @Fallback
            static boolean isReadableOther(ObjectWithMembers receiver, Object unknownMember) {
                return false;
            }
        }

        @ExportMessage
        static class ReadMember {

            @Specialization
            static Object readMember(ObjectWithMembers receiver, SimpleMember member) throws UnknownMemberException {
                if (member.isInvocable()) {
                    throw UnknownMemberException.create(member);
                }
                // Add a test whether the receiver really contains the provided member.
                return 10;
            }

            @Specialization(guards = "memberLibrary.isString(memberName)")
            static Object readString(ObjectWithMembers receiver, Object memberName,
                            @Shared("memberLibrary") @CachedLibrary(limit = "LIMIT") InteropLibrary memberLibrary) throws UnknownMemberException {
                String name;
                try {
                    name = memberLibrary.asString(memberName);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                return switch (name) {
                    case "a" -> 10;
                    default -> throw UnknownMemberException.create(memberName);
                };
            }

            @Fallback
            static Object readOther(ObjectWithMembers receiver, Object unknownMember) throws UnknownMemberException {
                throw UnknownMemberException.create(unknownMember);
            }
        }

        @ExportMessage
        static class IsMemberInvocable {

            @Specialization
            static boolean isInvocableMember(ObjectWithMembers receiver, SimpleMember member) {
                // Add a test whether the receiver really contains the provided member.
                return member.isInvocable();
            }

            @Specialization(guards = "memberLibrary.isString(memberName)")
            static boolean isInvocableString(ObjectWithMembers receiver, Object memberName,
                            @Shared("memberLibrary") @CachedLibrary(limit = "LIMIT") InteropLibrary memberLibrary) {
                String name;
                try {
                    name = memberLibrary.asString(memberName);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                return switch (name) {
                    case "f" -> true;
                    default -> false;
                };
            }

            @Fallback
            static boolean isInvocableOther(ObjectWithMembers receiver, Object unknownMember) {
                return false;
            }
        }

        @ExportMessage
        static class InvokeMember {

            @Specialization
            static Object invokeMember(ObjectWithMembers receiver, SimpleMember member, Object[] arguments) throws UnknownMemberException {
                if (!member.isInvocable()) {
                    throw UnknownMemberException.create(member);
                }
                // Add a test whether the receiver really contains the provided member.
                return arguments.length;
            }

            @Specialization(guards = "memberLibrary.isString(memberName)")
            static Object invokeString(ObjectWithMembers receiver, Object memberName, Object[] arguments,
                            @Shared("memberLibrary") @CachedLibrary(limit = "LIMIT") InteropLibrary memberLibrary) throws UnknownMemberException {
                String name;
                try {
                    name = memberLibrary.asString(memberName);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                return switch (name) {
                    case "f" -> arguments.length;
                    default -> throw UnknownMemberException.create(memberName);
                };
            }

            @Fallback
            static Object invokeOther(ObjectWithMembers receiver, Object unknownMember, Object[] arguments) throws UnknownMemberException {
                throw UnknownMemberException.create(unknownMember);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class SimpleMember implements TruffleObject {

        private final String name;
        private final boolean invocable;

        SimpleMember(String name, boolean invocable) {
            this.name = name;
            this.invocable = invocable;
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
        boolean isMemberKindField() {
            return !invocable;
        }

        @ExportMessage
        boolean isMemberKindMethod() {
            return invocable;
        }

        @ExportMessage
        boolean isMemberKindMetaObject() {
            return false;
        }

        boolean isInvocable() {
            return invocable;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class MembersArray implements TruffleObject {

        @CompilationFinal(dimensions = 1) private final Object[] array;

        MembersArray(Object... keys) {
            this.array = keys;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return array.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long idx) {
            return 0 <= idx && idx < array.length;
        }

        @ExportMessage
        Object readArrayElement(long idx,
                        @Cached InlinedBranchProfile exception, @Bind("$node") Node node) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(idx)) {
                exception.enter(node);
                throw InvalidArrayIndexException.create(idx);
            }
            return array[(int) idx];
        }
    }

    @Test
    public void test() throws Exception {
        InteropLibrary interop = InteropLibrary.getUncached();
        Object object = new ObjectWithMembers();
        assertTrue(interop.hasMembers(object));
        Object membersArray = interop.getMemberObjects(object);
        Object memberA = interop.readArrayElement(membersArray, 0);
        Object memberF = interop.readArrayElement(membersArray, 1);
        assertTrue(interop.isMember(memberA));
        assertTrue(interop.isMember(memberF));
        assertEquals("a", interop.getMemberSimpleName(memberA));
        assertTrue(interop.isMemberKindField(memberA));
        assertFalse(interop.isMemberKindMethod(memberA));
        assertEquals("f", interop.getMemberSimpleName(memberF));
        assertFalse(interop.isMemberKindField(memberF));
        assertTrue(interop.isMemberKindMethod(memberF));

        assertTrue(interop.isMemberReadable(object, memberA));
        assertEquals(10, interop.readMember(object, memberA));
        assertFails(() -> interop.readMember(object, memberF), UnknownMemberException.class);
        assertTrue(interop.isMemberInvocable(object, memberF));
        assertEquals(3, interop.invokeMember(object, memberF, new Object[]{1, 2, 3}));
        assertFails(() -> interop.invokeMember(object, memberA), UnknownMemberException.class);
    }
}
