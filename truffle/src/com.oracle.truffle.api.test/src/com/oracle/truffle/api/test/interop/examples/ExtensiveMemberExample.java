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

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
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
 * An extensive implementation of interop members.
 */
@SuppressWarnings({"static-method", "unused"})
public class ExtensiveMemberExample extends AbstractPolyglotTest {

    /**
     * An interop object that has a metaobject and members. Some members come from the metaobject's
     * declared members and some do not.
     */
    @ExportLibrary(InteropLibrary.class)
    static final class ObjectWithMetaAndMembers implements TruffleObject {

        static final int LIMIT = 3;

        private final MetaObject meta;
        private final Member fieldB = new Member("b", false, null);
        private final Member methodG = new Member("g", true, null);

        ObjectWithMetaAndMembers(MetaObject meta) {
            this.meta = meta;
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        Object getMemberObjects() {
            Object[] metaMembers = ((InteropArray) meta.getDeclaredMembers()).array;
            int i = metaMembers.length;
            Object[] members = Arrays.copyOf(metaMembers, i + 2);
            // Extra object members, which do not come from the metaobject
            members[i] = fieldB;
            members[i + 1] = methodG;
            return new InteropArray(members);
        }

        @ExportMessage
        boolean hasMetaObject() {
            return true;
        }

        @ExportMessage
        Object getMetaObject() {
            return meta;
        }

        @ExportMessage
        static class IsMemberReadable {

            @Specialization
            static boolean isReadableMember(ObjectWithMetaAndMembers receiver, Member member) {
                if (member == receiver.fieldB) {
                    return true;
                }
                if (receiver.meta == member.meta) {
                    return !member.isInvocable();
                } else {
                    return false;
                }
            }

            @Specialization
            static boolean isReadableMember(ObjectWithMetaAndMembers receiver, MetaObject innerMeta) {
                return receiver.meta == innerMeta.outerMeta;
            }

            @Specialization(guards = "memberLibrary.isString(memberName)")
            static boolean isReadableString(ObjectWithMetaAndMembers receiver, Object memberName,
                            @Shared("memberLibrary") @CachedLibrary(limit = "LIMIT") InteropLibrary memberLibrary) {
                String name;
                try {
                    name = memberLibrary.asString(memberName);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                return switch (name) {
                    case "a", "b" -> true;
                    default -> false;
                };
            }

            @Fallback
            static boolean isReadableOther(ObjectWithMetaAndMembers receiver, Object unknownMember) {
                return false;
            }
        }

        @ExportMessage
        static class ReadMember {

            @Specialization
            static Object readMember(ObjectWithMetaAndMembers receiver, Member member) throws UnknownMemberException {
                if (member == receiver.fieldB) {
                    return "B";
                }
                if (member.isInvocable() || receiver.meta != member.meta) {
                    throw UnknownMemberException.create(member);
                }
                return 10;
            }

            @Specialization
            static Object readMember(ObjectWithMetaAndMembers receiver, MetaObject innerMeta) throws UnknownMemberException {
                if (receiver.meta == innerMeta.outerMeta) {
                    return "Content of " + innerMeta.fullName;
                } else {
                    throw UnknownMemberException.create(innerMeta);
                }
            }

            @Specialization(guards = "memberLibrary.isString(memberName)")
            static Object readString(ObjectWithMetaAndMembers receiver, Object memberName,
                            @Shared("memberLibrary") @CachedLibrary(limit = "LIMIT") InteropLibrary memberLibrary) throws UnknownMemberException {
                String name;
                try {
                    name = memberLibrary.asString(memberName);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                return switch (name) {
                    case "a" -> 10;
                    case "b" -> "B";
                    default -> throw UnknownMemberException.create(memberName);
                };
            }

            @Fallback
            static Object readOther(ObjectWithMetaAndMembers receiver, Object unknownMember) throws UnknownMemberException {
                throw UnknownMemberException.create(unknownMember);
            }
        }

        @ExportMessage
        static class IsMemberInvocable {

            @Specialization
            static boolean isInvocableMember(ObjectWithMetaAndMembers receiver, Member member) {
                if (member == receiver.methodG) {
                    return true;
                }
                if (receiver.meta == member.meta) {
                    return member.isInvocable();
                } else {
                    return false;
                }
            }

            @Specialization(guards = "memberLibrary.isString(memberName)")
            static boolean isInvocableString(ObjectWithMetaAndMembers receiver, Object memberName,
                            @Shared("memberLibrary") @CachedLibrary(limit = "LIMIT") InteropLibrary memberLibrary) {
                String name;
                try {
                    name = memberLibrary.asString(memberName);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                return switch (name) {
                    case "f", "g" -> true;
                    default -> false;
                };
            }

            @Fallback
            static boolean isInvocableOther(ObjectWithMetaAndMembers receiver, Object unknownMember) {
                return false;
            }
        }

        @ExportMessage
        static class InvokeMember {

            @Specialization
            static Object invokeMember(ObjectWithMetaAndMembers receiver, Member member, Object[] arguments) throws UnknownMemberException {
                if (member == receiver.methodG) {
                    return arguments.length;
                }
                if (!member.isInvocable() || receiver.meta != member.meta) {
                    throw UnknownMemberException.create(member);
                }
                // method "f":
                return new ObjectWithMetaAndMembers(receiver.meta);
            }

            @Specialization(guards = "memberLibrary.isString(memberName)")
            static Object invokeString(ObjectWithMetaAndMembers receiver, Object memberName, Object[] arguments,
                            @Shared("memberLibrary") @CachedLibrary(limit = "LIMIT") InteropLibrary memberLibrary) throws UnknownMemberException {
                String name;
                try {
                    name = memberLibrary.asString(memberName);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                return switch (name) {
                    case "f" -> new ObjectWithMetaAndMembers(receiver.meta);
                    case "g" -> arguments.length;
                    default -> throw UnknownMemberException.create(memberName);
                };
            }

            @Fallback
            static Object invokeOther(ObjectWithMetaAndMembers receiver, Object unknownMember, Object[] arguments) throws UnknownMemberException {
                throw UnknownMemberException.create(unknownMember);
            }
        }
    }

    /**
     * A metaobject, that contains declared members. It is also a member if it has an outer
     * metaobject.
     */
    @ExportLibrary(InteropLibrary.class)
    static final class MetaObject implements TruffleObject {

        private final String fullName;
        private final String simpleName;
        private final MetaObject outerMeta;

        MetaObject(String name, MetaObject outerMeta) {
            this.fullName = name;
            int i = name.lastIndexOf('.');
            this.simpleName = (i > 0) ? name.substring(i + 1) : name;
            this.outerMeta = outerMeta;
        }

        @ExportMessage
        boolean isMetaObject() {
            return true;
        }

        @ExportMessage
        boolean hasDeclaredMembers() {
            return true;
        }

        @ExportMessage
        Object getDeclaredMembers() {
            return new InteropArray(new Member("a", false, this), new Member("f", true, this), new MetaObject(fullName + '.' + "sub", this));
        }

        @ExportMessage
        Object getMetaQualifiedName() {
            return fullName;
        }

        @ExportMessage
        Object getMetaSimpleName() {
            return simpleName;
        }

        @ExportMessage
        static class IsMetaInstance {

            @Specialization
            static boolean isInstanceOf(MetaObject meta, ObjectWithMetaAndMembers instance) {
                return meta == instance.getMetaObject();
            }

            @Fallback
            static boolean isInstanceOf(MetaObject meta, Object unknownInstance) {
                return false;
            }
        }

        @ExportMessage
        boolean isMember() {
            return outerMeta != null;
        }

        @ExportMessage
        Object getMemberSimpleName() throws UnsupportedMessageException {
            if (outerMeta == null) {
                throw UnsupportedMessageException.create();
            }
            return simpleName;
        }

        @ExportMessage
        Object getMemberQualifiedName() throws UnsupportedMessageException {
            if (outerMeta == null) {
                throw UnsupportedMessageException.create();
            }
            return fullName;
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
            return outerMeta != null;
        }
    }

    /**
     * A member object, that optionally has a metaobject associated.
     */
    @ExportLibrary(InteropLibrary.class)
    static final class Member implements TruffleObject {

        private final String name;
        private final boolean invocable;
        private final MetaObject meta;

        Member(String name, boolean invocable, MetaObject meta) {
            this.name = name;
            this.invocable = invocable;
            this.meta = meta;
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
            if (meta == null) {
                return name;
            } else {
                return meta.getMetaQualifiedName().toString() + '.' + name;
            }
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

        @ExportMessage
        boolean hasMemberSignature() {
            return invocable;
        }

        @ExportMessage
        Object getMemberSignature() throws UnsupportedMessageException {
            if (!invocable) {
                throw UnsupportedMessageException.create();
            }
            // Returns an instance of the MetaObject and takes an integer as an argument.
            return new InteropArray(new SignatureElement(null, meta), new SignatureElement("integer", null));
        }

        boolean isInvocable() {
            return invocable;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class SignatureElement implements TruffleObject {

        private final String name;
        private final MetaObject meta;

        SignatureElement(String name, MetaObject meta) {
            this.name = name;
            this.meta = meta;
        }

        @ExportMessage
        boolean isSignatureElement() {
            return true;
        }

        @ExportMessage
        boolean hasSignatureElementName() {
            return name != null;
        }

        @ExportMessage
        Object getSignatureElementName() throws UnsupportedMessageException {
            if (name == null) {
                throw UnsupportedMessageException.create();
            }
            return name;
        }

        @ExportMessage
        boolean hasSignatureElementMetaObject() {
            return meta != null;
        }

        @ExportMessage
        Object getSignatureElementMetaObject() throws UnsupportedMessageException {
            if (meta == null) {
                throw UnsupportedMessageException.create();
            }
            return meta;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class InteropArray implements TruffleObject {

        @CompilationFinal(dimensions = 1) final Object[] array;

        InteropArray(Object... keys) {
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
        Object meta = new MetaObject("metaspace", null);
        assertTrue(interop.isMetaObject(meta));
        assertTrue(interop.hasDeclaredMembers(meta));
        Object metaDeclaredMembers = interop.getDeclaredMembers(meta);
        assertEquals(3, interop.getArraySize(metaDeclaredMembers));

        Object object = new ObjectWithMetaAndMembers((MetaObject) meta);
        assertTrue(interop.hasMembers(object));
        assertTrue(interop.hasMetaObject(object));
        assertSame(meta, interop.getMetaObject(object));
        Object members = interop.getMemberObjects(object);
        assertEquals(5, interop.getArraySize(members));

        Object memberA = interop.readArrayElement(members, 0);
        Object memberF = interop.readArrayElement(members, 1);
        Object memberS = interop.readArrayElement(members, 2);
        Object memberB = interop.readArrayElement(members, 3);
        Object memberG = interop.readArrayElement(members, 4);

        assertEquals("a", interop.getMemberSimpleName(memberA));
        assertEquals("metaspace.a", interop.getMemberQualifiedName(memberA));
        assertTrue(interop.isMemberKindField(memberA));
        assertFalse(interop.isMemberKindMethod(memberA));
        assertFalse(interop.isMemberKindMetaObject(memberA));
        assertFalse(interop.hasMemberSignature(memberA));
        assertFails(() -> interop.getMemberSignature(memberA), UnsupportedMessageException.class);

        assertEquals("f", interop.getMemberSimpleName(memberF));
        assertEquals("metaspace.f", interop.getMemberQualifiedName(memberF));
        assertFalse(interop.isMemberKindField(memberF));
        assertTrue(interop.isMemberKindMethod(memberF));
        assertFalse(interop.isMemberKindMetaObject(memberF));
        assertTrue(interop.hasMemberSignature(memberF));
        Object signatureF = interop.getMemberSignature(memberF);

        assertEquals("sub", interop.getMemberSimpleName(memberS));
        assertEquals("metaspace.sub", interop.getMemberQualifiedName(memberS));
        assertFalse(interop.isMemberKindField(memberS));
        assertFalse(interop.isMemberKindMethod(memberS));
        assertTrue(interop.isMemberKindMetaObject(memberS));
        assertFalse(interop.hasMemberSignature(memberS));
        assertFails(() -> interop.getMemberSignature(memberS), UnsupportedMessageException.class);

        assertEquals("b", interop.getMemberSimpleName(memberB));
        assertEquals("b", interop.getMemberQualifiedName(memberB));
        assertTrue(interop.isMemberKindField(memberB));
        assertFalse(interop.isMemberKindMethod(memberB));
        assertFalse(interop.isMemberKindMetaObject(memberB));
        assertFalse(interop.hasMemberSignature(memberB));
        assertFails(() -> interop.getMemberSignature(memberB), UnsupportedMessageException.class);

        assertEquals("g", interop.getMemberSimpleName(memberG));
        assertEquals("g", interop.getMemberQualifiedName(memberG));
        assertFalse(interop.isMemberKindField(memberG));
        assertTrue(interop.isMemberKindMethod(memberG));
        assertFalse(interop.isMemberKindMetaObject(memberG));
        assertTrue(interop.hasMemberSignature(memberG));
        Object signatureG = interop.getMemberSignature(memberG);

        Object subMembers = interop.getDeclaredMembers(memberS);
        Object memberSubA = interop.readArrayElement(subMembers, 0);
        Object memberSubF = interop.readArrayElement(subMembers, 1);

        assertTrue(interop.isMemberReadable(object, memberA));
        assertFalse(interop.isMemberReadable(object, memberSubA));
        assertEquals(10, interop.readMember(object, memberA));
        assertFails(() -> interop.readMember(object, memberSubA), UnknownMemberException.class);

        assertTrue(interop.isMemberInvocable(object, memberF));
        assertFalse(interop.isMemberInvocable(object, memberSubF));
        Object retF = interop.invokeMember(object, memberF);
        assertFails(() -> interop.invokeMember(object, memberSubF), UnknownMemberException.class);

        Object signRetF = interop.readArrayElement(signatureF, 0);
        Object signArgF = interop.readArrayElement(signatureF, 1);
        assertTrue(interop.isSignatureElement(signRetF));
        assertTrue(interop.isSignatureElement(signArgF));
        assertFalse(interop.hasSignatureElementName(signRetF));
        assertFails(() -> interop.getSignatureElementName(signRetF), UnsupportedMessageException.class);
        assertTrue(interop.hasSignatureElementMetaObject(signRetF));
        Object retFMeta = interop.getSignatureElementMetaObject(signRetF);
        assertSame(meta, retFMeta);
        assertTrue(interop.hasSignatureElementName(signArgF));
        assertEquals("integer", interop.getSignatureElementName(signArgF));
        assertFalse(interop.hasSignatureElementMetaObject(signArgF));
        assertFails(() -> interop.getSignatureElementMetaObject(signArgF), UnsupportedMessageException.class);
    }
}
