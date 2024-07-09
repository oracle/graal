/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode;

import java.util.LinkedHashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.SourceSection;

@ExportLibrary(value = NodeLibrary.class, receiverType = TagTreeNode.class)
@SuppressWarnings({"static-method"})
final class TagTreeNodeExports {

    @ExportMessage
    @SuppressWarnings("unused")
    static boolean hasScope(TagTreeNode node, Frame frame) {
        return true;
    }

    @ExportMessage
    static Object getScope(TagTreeNode node, Frame frame, boolean nodeEnter) {
        return new Scope(node, frame, nodeEnter);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Scope implements TruffleObject {

        @NeverDefault final BytecodeNode bytecode;
        @NeverDefault final TagTreeNode node;
        @NeverDefault final int bci;

        final Frame frame;

        private Map<String, Integer> nameToIndex;

        private Scope(TagTreeNode node, Frame frame, boolean nodeEnter) {
            this.bytecode = node.getBytecodeNode();
            this.node = node;
            this.frame = frame;
            this.bci = nodeEnter ? node.getEnterBytecodeIndex() : node.getReturnBytecodeIndex();
        }

        @ExportMessage
        boolean accepts(@Shared @Cached("this.bytecode") BytecodeNode cachedBytecode,
                        @Shared @Cached("this.node") TagTreeNode cachedNode,
                        @Shared @Cached("this.bci") int cachedBci) {
            return this.bytecode == cachedBytecode && this.bci == cachedBci && this.node == cachedNode;
        }

        @ExportMessage
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage(
                        @Shared @Cached("this.node") TagTreeNode cachedNode) {
            return cachedNode.getLanguage();
        }

        @ExportMessage
        boolean isScope() {
            return true;
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        static class ReadMember {

            @SuppressWarnings("unused")
            @Specialization(guards = {"equalsString(cachedMember, member)"}, limit = "5")
            static Object doCached(Scope scope, String member,
                            @Shared @Cached("scope.bytecode") BytecodeNode cachedBytecode,
                            @Shared @Cached("scope.bci") int cachedBci,
                            @Cached("member") String cachedMember,
                            @Cached("scope.slotToIndex(cachedMember)") int index) throws UnsupportedMessageException {
                if (index == -1 || scope.frame == null) {
                    throw UnsupportedMessageException.create();
                }
                Object o = cachedBytecode.getLocalValue(cachedBci, scope.frame, index);
                if (o == null) {
                    o = Null.INSTANCE;
                }
                return o;
            }

            @Specialization(replaces = "doCached")
            @TruffleBoundary
            static Object doGeneric(Scope scope, String member) throws UnsupportedMessageException {
                return doCached(scope, member, scope.bytecode, scope.bci, member, scope.slotToIndex(member));
            }

        }

        @ExportMessage
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new Members(bytecode, bci);
        }

        @ExportMessage
        static class IsMemberReadable {

            @SuppressWarnings("unused")
            @Specialization(guards = {"equalsString(cachedMember, member)"}, limit = "5")
            static boolean doCached(Scope scope, String member,
                            @Cached("member") String cachedMember,
                            @Cached("scope.slotToIndex(cachedMember)") int index) {
                return index != -1;
            }

            @Specialization(replaces = "doCached")
            static boolean doGeneric(Scope scope, String member) {
                return scope.slotToIndex(member) != -1;
            }

        }

        @ExportMessage
        static class IsMemberModifiable {

            @SuppressWarnings("unused")
            @Specialization(guards = {"equalsString(cachedMember, member)"}, limit = "5")
            static boolean doCached(Scope scope, String member,
                            @Cached("member") String cachedMember,
                            @Cached("scope.slotToIndex(cachedMember)") int index) {
                return index != -1 && scope.frame != null;
            }

            @Specialization(replaces = "doCached")
            static boolean doGeneric(Scope scope, String member) {
                return scope.slotToIndex(member) != -1 && scope.frame != null;
            }

        }

        @ExportMessage
        static class WriteMember {
            @SuppressWarnings("unused")
            @Specialization(guards = {"equalsString(cachedMember, member)"}, limit = "5")
            static void doCached(Scope scope, String member, Object value,
                            @Shared @Cached("scope.bytecode") BytecodeNode cachedBytecode,
                            @Shared @Cached("scope.bci") int cachedBci,
                            @Cached("member") String cachedMember,
                            @Cached("scope.slotToIndex(cachedMember)") int index) throws UnknownIdentifierException, UnsupportedMessageException {
                if (index == -1 || scope.frame == null) {
                    throw UnsupportedMessageException.create();
                }
                cachedBytecode.setLocalValue(cachedBci, scope.frame, index, value);
            }

            @Specialization(replaces = "doCached")
            @TruffleBoundary
            static void doGeneric(Scope scope, String member, Object value) throws UnknownIdentifierException, UnsupportedMessageException {
                doCached(scope, member, value, scope.bytecode, scope.bci, member, scope.slotToIndex(member));
            }
        }

        @ExportMessage
        boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
            return false;
        }

        @ExportMessage
        @TruffleBoundary
        boolean hasSourceLocation() {
            return node.getSourceSection() != null;
        }

        @ExportMessage
        @TruffleBoundary
        SourceSection getSourceLocation() throws UnsupportedMessageException {
            SourceSection section = node.getSourceSection();
            if (section == null) {
                throw UnsupportedMessageException.create();
            }
            return section;
        }

        @ExportMessage
        @TruffleBoundary
        Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return bytecode.getRootNode().getName();
        }

        @Override
        public String toString() {
            return "Scope[" + getNameToIndex() + "]";
        }

        @TruffleBoundary
        final int slotToIndex(String member) {
            Map<String, Integer> locals = getNameToIndex();
            Integer index = locals.get(member);
            if (index == null) {
                return -1;
            }
            return index;
        }

        private Map<String, Integer> getNameToIndex() {
            Map<String, Integer> names = this.nameToIndex;
            if (names == null) {
                names = createNameToIndex();
                this.nameToIndex = names;
            }
            return names;
        }

        private Map<String, Integer> createNameToIndex() {
            Map<String, Integer> locals = new LinkedHashMap<>();
            int index = 0;
            for (Object local : this.bytecode.getLocalNames(this.bci)) {
                String name = null;
                if (local != null) {
                    try {
                        name = InteropLibrary.getUncached().asString(local);
                    } catch (UnsupportedMessageException e) {
                    }
                }
                if (name == null) {
                    name = "local" + index;
                }
                locals.put(name, index);
                index++;
            }
            return locals;
        }

        @TruffleBoundary
        private Members createMembers() {
            return new Members(this.bytecode, this.bci);
        }

        @TruffleBoundary
        static boolean equalsString(String a, String b) {
            return a.equals(b);
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class Members implements TruffleObject {
        final BytecodeNode bytecode;
        final int bci;

        Members(BytecodeNode bytecode, int bci) {
            this.bytecode = bytecode;
            this.bci = bci;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        long getArraySize() {
            return bytecode.getLocalCount(bci);
        }

        @ExportMessage
        @TruffleBoundary
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            long size = getArraySize();
            if (index < 0 || index >= size) {
                throw InvalidArrayIndexException.create(index);
            }

            Object localName = bytecode.getLocalName(bci, (int) index);
            if (localName == null) {
                return "local" + index;
            } else {
                return localName;
            }
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < getArraySize();
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Null implements TruffleObject {

        private static final Null INSTANCE = new Null();

        private Null() {
        }

        @ExportMessage
        boolean isNull() {
            return true;
        }

    }

}
