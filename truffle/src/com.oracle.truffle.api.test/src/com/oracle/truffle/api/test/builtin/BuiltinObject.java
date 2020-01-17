/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.builtin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
public abstract class BuiltinObject implements TruffleObject {

    static final int MEMBER_NAME_CACHE_SIZE = 5;

    protected abstract BuiltinDescriptor getBuiltinDescriptor();

    public static BuiltinDescriptor describe(List<? extends NodeFactory<? extends BuiltinNode>> nodes) {
        List<MemberEntry> entries = new ArrayList<>();
        for (NodeFactory<? extends BuiltinNode> factory : nodes) {
            Builtin builtin = factory.getNodeClass().getAnnotation(Builtin.class);
            if (builtin == null) {
                continue; // not a builtin
            }
            entries.add(new MemberEntry(builtin, factory));
        }
        return new BuiltinDescriptor(entries.toArray(new MemberEntry[entries.size()]));
    }

    @ExportMessage
    final boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    final Object getMembers(boolean includeInternal,
                    @Shared("descriptor") @Cached(value = "getDescriptorImpl(this)", allowUncached = true) BuiltinDescriptor descriptor) {
        return descriptor.membersArray;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static class ReadMember {

        @Specialization(guards = "cachedMember.equals(member)", limit = "MEMBER_NAME_CACHE_SIZE")
        static Object doCached(BuiltinObject receiver, String member,
                        @Cached("member") String cachedMember,
                        @Cached("getDescriptorImpl(receiver).lookup(cachedMember)") MemberEntry cachedEntry)
                        throws UnknownIdentifierException {
            if (cachedEntry == null) {
                CompilerDirectives.transferToInterpreter();
                throw UnknownIdentifierException.create(member);
            }
            return new MemberFunction(receiver, cachedEntry);
        }

        @Specialization(replaces = "doCached")
        @ExplodeLoop
        static Object doDefault(BuiltinObject receiver, String member,
                        @Shared("descriptor") @Cached(value = "getDescriptorImpl(receiver)", allowUncached = true) BuiltinDescriptor descriptor)
                        throws UnknownIdentifierException {
            int hash = member.hashCode();
            for (int i = 0; i < descriptor.members.length; i++) {
                MemberEntry entry = descriptor.members[i];
                if (hash == entry.hash && entry.name.equals(member)) {
                    return new MemberFunction(receiver, entry);
                }
            }
            CompilerDirectives.transferToInterpreter();
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportMessage(name = "isMemberReadable")
    @ExportMessage(name = "isMemberInvocable")
    @ExplodeLoop
    final boolean isMemberExisting(String member,
                    @Shared("descriptor") @Cached(value = "getDescriptorImpl(this)", allowUncached = true) BuiltinDescriptor descriptor) {
        int hash = member.hashCode();
        for (int i = 0; i < descriptor.members.length; i++) {
            MemberEntry entry = descriptor.members[i];
            if (hash == entry.hash && entry.name.equals(member)) {
                return true;
            }
        }
        return false;
    }

    static BuiltinDescriptor getDescriptorImpl(BuiltinObject object) {
        return object.getBuiltinDescriptor();
    }

    @GenerateNodeFactory
    @GenerateUncached(inherit = true)
    public abstract static class BuiltinNode extends Node {

        protected abstract Object execute(BuiltinObject receiver, Object... arguments) throws ArityException, UnsupportedTypeException;

        final Object executeImpl(MemberEntry member, BuiltinObject receiver, Object[] arguments) throws ArityException, UnsupportedTypeException {
            try {
                assert member != null;
                if (member.expectedArgumentCount != arguments.length) {
                    CompilerDirectives.transferToInterpreter();
                    throw ArityException.create(member.expectedArgumentCount, arguments.length);
                }
                return execute(receiver, arguments);
            } catch (UnsupportedSpecializationException e) {
                throw UnsupportedTypeException.create(arguments);
            }
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public @interface Builtin {

        String value() default "";

    }

    @ExportMessage
    static class InvokeMember {

        @Specialization(guards = "cachedMember.equals(member)", limit = "MEMBER_NAME_CACHE_SIZE")
        @SuppressWarnings("unused")
        static Object doCached(BuiltinObject receiver, String member, Object[] arguments,
                        @Cached("member") String cachedMember,
                        @Cached(value = "getDescriptorImpl(receiver).lookup(cachedMember)") MemberEntry cachedEntry,
                        @Cached("createNode(cachedEntry)") BuiltinNode node) throws ArityException, UnsupportedTypeException, UnknownIdentifierException {
            if (cachedEntry == null) {
                throw UnknownIdentifierException.create(member);
            }
            return node.executeImpl(cachedEntry, receiver, arguments);
        }

        protected static BuiltinNode createNode(MemberEntry entry) {
            if (entry == null) {
                return null;
            }
            return entry.createNode();
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        static Object doUncached(BuiltinObject receiver, String member, Object[] arguments,
                        @Shared("descriptor") @Cached(value = "getDescriptorImpl(receiver)", allowUncached = true) BuiltinDescriptor descriptor)
                        throws UnsupportedTypeException, ArityException, UnknownIdentifierException {
            MemberEntry memberEntry = descriptor.lookup(member);
            if (memberEntry == null) {
                throw UnknownIdentifierException.create(member);
            }
            return memberEntry.uncached.executeImpl(memberEntry, receiver, arguments);
        }

    }

    static class MemberEntry {

        final int expectedArgumentCount;
        final String name;
        final NodeFactory<? extends BuiltinNode> factory;
        final BuiltinNode uncached;
        final int hash;

        MemberEntry(Builtin builtin, NodeFactory<? extends BuiltinNode> factory) {
            String builtinName = builtin.value();
            if (!builtinName.equals("")) {
                this.name = builtinName;
            } else {
                String className = factory.getNodeClass().getSimpleName();
                this.name = Character.toLowerCase(className.charAt(0)) + className.substring(1, className.length());
            }
            this.expectedArgumentCount = factory.getExecutionSignature().size() - 1;
            this.hash = name.hashCode();
            this.factory = factory;
            this.uncached = factory.getUncachedInstance();
        }

        @TruffleBoundary
        BuiltinNode createNode() {
            return factory.createNode();
        }

    }

    @ExportLibrary(InteropLibrary.class)

    static final class MemberFunction implements TruffleObject {

        final BuiltinObject receiver;
        final MemberEntry member;

        MemberFunction(BuiltinObject receiver, MemberEntry member) {
            this.receiver = receiver;
            this.member = member;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        @ImportStatic(BuiltinObject.class)
        static class Execute {

            @Specialization(guards = "receiver.member == cachedMember", limit = "MEMBER_NAME_CACHE_SIZE")
            @SuppressWarnings("unused")
            static Object doCached(MemberFunction receiver, Object[] arguments,
                            @Cached("receiver.member") MemberEntry cachedMember,
                            @Cached("cachedMember.createNode()") BuiltinNode node) throws ArityException, UnsupportedTypeException {
                return node.executeImpl(cachedMember, receiver.receiver, arguments);
            }

            @Specialization(replaces = "doCached")
            @TruffleBoundary
            static Object doUncached(MemberFunction receiver, Object[] arguments)
                            throws UnsupportedTypeException, ArityException {
                return receiver.member.uncached.executeImpl(receiver.member, receiver.receiver, arguments);
            }
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class MembersArray implements TruffleObject {

        @CompilationFinal(dimensions = 1) private final MemberEntry[] members;

        MembersArray(MemberEntry[] members) {
            this.members = members;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return members.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long idx) {
            return 0 <= idx && idx < members.length;
        }

        @ExportMessage
        String readArrayElement(long idx,
                        @Cached BranchProfile exception) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(idx)) {
                exception.enter();
                throw InvalidArrayIndexException.create(idx);
            }
            return members[(int) idx].name;
        }
    }

    public static final class BuiltinDescriptor {

        @CompilationFinal(dimensions = 1) final MemberEntry[] members;
        final MembersArray membersArray;

        BuiltinDescriptor(MemberEntry[] members) {
            this.members = members;
            this.membersArray = new MembersArray(members);
        }

        @ExplodeLoop
        MemberEntry lookup(String member) {
            int hash = member.hashCode();
            for (int i = 0; i < members.length; i++) {
                MemberEntry entry = members[i];
                if (hash == entry.hash && entry.name.equals(member)) {
                    return entry;
                }
            }
            return null;
        }

    }

}
