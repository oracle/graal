/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.nodes.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownMemberException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.runtime.SLStrings;

/**
 * A container class used to store per-node attributes used by the instrumentation framework.
 */
@ExportLibrary(InteropLibrary.class)
public abstract class NodeObjectDescriptor implements TruffleObject {

    static final int LIMIT = 3;
    private final TruffleString name;

    private NodeObjectDescriptor(TruffleString name) {
        assert name != null;
        this.name = name;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasMembers() {
        return true;
    }

    public static NodeObjectDescriptor readVariable(TruffleString name) {
        return new ReadDescriptor(name);
    }

    public static NodeObjectDescriptor writeVariable(TruffleString name, SourceSection sourceSection) {
        return new WriteDescriptor(name, sourceSection);
    }

    @ExportMessage
    static class IsMemberReadable {

        @Specialization
        static boolean isReadable(NodeObjectDescriptor descriptor, NodeObjectDescriptorMember member) {
            return descriptor.isReadable(member);
        }

        @Specialization(guards = "memberLibrary.isString(member)", limit = "LIMIT")
        static boolean isReadable(NodeObjectDescriptor descriptor, Object member,
                        @CachedLibrary("member") InteropLibrary memberLibrary) {
            String name = NodeObjectDescriptor.toString(member, memberLibrary);
            return descriptor.isReadable(name);
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean isReadable(NodeObjectDescriptor descriptor, Object member) {
            return false;
        }
    }

    @ExportMessage
    static class ReadMember {

        @Specialization
        static Object read(NodeObjectDescriptor descriptor, NodeObjectDescriptorMember member,
                        @Bind("$node") Node node,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnknownMemberException {
            if (descriptor.isReadable(member)) {
                return descriptor.read();
            } else {
                error.enter(node);
                throw UnknownMemberException.create(member);
            }
        }

        @Specialization(guards = "memberLibrary.isString(member)", limit = "LIMIT")
        static Object read(NodeObjectDescriptor descriptor, Object member,
                        @CachedLibrary("member") InteropLibrary memberLibrary,
                        @Bind("$node") Node node,
                        @Shared("error") @Cached InlinedBranchProfile error) throws UnknownMemberException {
            String name = NodeObjectDescriptor.toString(member, memberLibrary);
            if (descriptor.isReadable(name)) {
                return descriptor.read();
            } else {
                error.enter(node);
                throw UnknownMemberException.create(member);
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object read(NodeObjectDescriptor descriptor, Object member) throws UnknownMemberException {
            throw UnknownMemberException.create(member);
        }
    }

    private static String toString(Object member, InteropLibrary memberLibrary) {
        assert memberLibrary.isString(member) : member;
        try {
            return memberLibrary.asString(member);
        } catch (UnsupportedMessageException ex) {
            throw CompilerDirectives.shouldNotReachHere(ex);
        }
    }

    @ExportMessage
    abstract Object getMemberObjects();

    abstract boolean isReadable(String member);

    abstract boolean isReadable(NodeObjectDescriptorMember member);

    Object read() {
        return name;
    }

    static final class ReadDescriptor extends NodeObjectDescriptor {

        private static final NodeObjectDescriptorMember MEMBER_READ = new NodeObjectDescriptorMember(SLStrings.constant(StandardTags.ReadVariableTag.NAME));
        private static final TruffleObject KEYS_READ = new NodeObjectDescriptorKeys(MEMBER_READ);

        ReadDescriptor(TruffleString name) {
            super(name);
        }

        @Override
        boolean isReadable(String member) {
            return StandardTags.ReadVariableTag.NAME.equals(member);
        }

        @Override
        boolean isReadable(NodeObjectDescriptorMember member) {
            return MEMBER_READ == member;
        }

        @Override
        Object getMemberObjects() {
            return KEYS_READ;
        }
    }

    static final class WriteDescriptor extends NodeObjectDescriptor {

        private static final NodeObjectDescriptorMember MEMBER_WRITE = new NodeObjectDescriptorMember(SLStrings.constant(StandardTags.WriteVariableTag.NAME));
        private static final TruffleObject KEYS_WRITE = new NodeObjectDescriptorKeys(MEMBER_WRITE);

        private final Object nameSymbol;

        WriteDescriptor(TruffleString name, SourceSection sourceSection) {
            super(name);
            this.nameSymbol = new NameSymbol(name, sourceSection);
        }

        @Override
        boolean isReadable(String member) {
            return StandardTags.WriteVariableTag.NAME.equals(member);
        }

        @Override
        boolean isReadable(NodeObjectDescriptorMember member) {
            return MEMBER_WRITE == member;
        }

        @Override
        Object getMemberObjects() {
            return KEYS_WRITE;
        }

        @Override
        Object read() {
            return nameSymbol;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class NameSymbol implements TruffleObject {

        private final TruffleString name;
        private final SourceSection sourceSection;

        NameSymbol(TruffleString name, SourceSection sourceSection) {
            this.name = name;
            this.sourceSection = sourceSection;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isString() {
            return true;
        }

        @ExportMessage
        String asString(@Cached TruffleString.ToJavaStringNode toJavaStringNode) {
            return toJavaStringNode.execute(name);
        }

        @ExportMessage
        TruffleString asTruffleString() {
            return name;
        }

        @ExportMessage
        boolean hasSourceLocation() {
            return sourceSection != null;
        }

        @ExportMessage
        SourceSection getSourceLocation() throws UnsupportedMessageException {
            if (sourceSection != null) {
                return sourceSection;
            } else {
                throw UnsupportedMessageException.create();
            }
        }
    }
}
