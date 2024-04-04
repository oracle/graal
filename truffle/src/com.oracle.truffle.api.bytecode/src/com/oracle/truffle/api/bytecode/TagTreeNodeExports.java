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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
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
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

@ExportLibrary(value = NodeLibrary.class, receiverType = TagTreeNode.class)
@SuppressWarnings({"static-method", "unused"})
final class TagTreeNodeExports {

    @ExportMessage
    static boolean hasScope(TagTreeNode node, Frame frame) {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static Object getScope(TagTreeNode node, Frame frame, boolean nodeEnter) throws UnsupportedMessageException {
        return new Scope(node, frame);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Scope implements TruffleObject {

        final TagTreeNode node;
        final Frame frame;

        private Class<? extends TruffleLanguage<?>> language;
        private Map<String, Integer> nameToIndex;
        @CompilationFinal private BytecodeRootNode root;
        @CompilationFinal private Members members;

        private Scope(TagTreeNode node, Frame frame) {
            this.node = node;
            this.frame = frame;
        }

        @ExportMessage
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage() throws UnsupportedMessageException {
            return node.getLanguage();
        }

        @ExportMessage
        boolean isScope() {
            return true;
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        public BytecodeRootNode getRoot() {
            BytecodeRootNode localRoot = this.root;
            if (localRoot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                root = localRoot = BytecodeNode.get(this.node).getBytecodeRootNode();
            }
            return localRoot;
        }

        public RootNode getRootNode() {
            return (RootNode) getRoot();
        }

        @ExportMessage
        static class ReadMember {

            @Specialization(guards = {"frame != null", "equalsString(cachedMember, member)"}, limit = "5")
            static Object doCached(Scope scope, String member,
                            @Bind("scope.frame") Frame frame,
                            @Cached("member") String cachedMember,
                            @Cached("scope.slotToIndex(cachedMember)") int index) throws UnsupportedMessageException {
                if (index == -1) {
                    throw UnsupportedMessageException.create();
                }
                Object o = scope.getRoot().getLocal(frame, index);
                if (o == null) {
                    o = Null.INSTANCE;
                }
                return o;
            }

            @Specialization(guards = {"frame != null"}, replaces = "doCached")
            static Object doGeneric(Scope scope, String member,
                            @Bind("scope.frame") Frame frame) throws UnsupportedMessageException {
                return doCached(scope, member, frame, member, scope.slotToIndex(member));
            }

            @Specialization(guards = "scope.frame != null")
            static Object doNullFrame(Scope scope, String member) {
                return Null.INSTANCE;
            }

        }

        @ExportMessage
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            Members m = this.members;
            if (m == null) {
                // for AOT we do not deoptimize but just create a members object
                m = createMembers();
                if (CompilerDirectives.inInterpreter()) {
                    this.members = m;
                }
            }
            return m;
        }

        @ExportMessage
        static class IsMemberReadable {

            @Specialization(guards = {"equalsString(cachedMember, member)"}, limit = "5")
            static boolean doCached(Scope scope, String member,
                            @Bind("scope.frame") Frame frame,
                            @Cached("member") String cachedMember,
                            @Cached("scope.slotToIndex(cachedMember)") int index) {
                return index != -1;
            }

            @Specialization(replaces = "doCached")
            static boolean doGeneric(Scope scope, String member,
                            @Bind("scope.frame") Frame frame) {
                return scope.slotToIndex(member) != -1;
            }

        }

        @ExportMessage
        static class IsMemberModifiable {

            @Specialization(guards = {"scope.frame != null", "equalsString(cachedMember, member)"}, limit = "5")
            static boolean doCached(Scope scope, String member,
                            @Cached("member") String cachedMember,
                            @Cached("scope.slotToIndex(cachedMember)") int index) {
                return index != -1;
            }

            @Specialization(guards = {"scope.frame != null"}, replaces = "doCached")
            static boolean doGeneric(Scope scope, String member) {
                return scope.slotToIndex(member) != -1;
            }

            @Specialization(guards = "scope.frame != null")
            static boolean doNullFrame(Scope scope, String member) {
                return false;
            }

        }

        @ExportMessage
        static class WriteMember {
            @Specialization(guards = {"frame != null", "equalsString(cachedMember, member)"}, limit = "5")
            static void doCached(Scope scope, String member, Object value,
                            @Bind("scope.frame") Frame frame,
                            @Cached("member") String cachedMember,
                            @Cached("scope.slotToIndex(cachedMember)") int index) throws UnknownIdentifierException, UnsupportedMessageException {
                if (index == -1) {
                    throw UnsupportedMessageException.create();
                }
                scope.getRoot().setLocal(frame, index, value);
            }

            @Specialization(guards = {"frame != null"}, replaces = "doCached")
            static void doGeneric(Scope scope, String member, Object value,
                            @Bind("scope.frame") Frame frame) throws UnknownIdentifierException, UnsupportedMessageException {
                doCached(scope, member, value, frame, member, scope.slotToIndex(member));
            }

            @Specialization(guards = "scope.frame != null")
            static void doNullFrame(Scope scope, String member, Object value) throws UnsupportedMessageException {
                throw UnsupportedMessageException.create();
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
            String name = ((RootNode) root).getName();
            if (name == null) {
                name = "local";
            }
            return name;
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
            Map<String, Integer> locals;
            locals = new HashMap<>();
            int index = 0;
            for (Object local : getRoot().getLocalNames()) {
                String name = null;
                if (local != null) {
                    try {
                        name = InteropLibrary.getUncached().asString(local);
                    } catch (UnsupportedMessageException e) {
                    }
                }
                if (name == null) {
                    name = "arg" + index;
                }
                locals.put(name, index);
                index++;
            }
            return locals;
        }

        @TruffleBoundary
        private Members createMembers() {
            return new Members(getNameToIndex().keySet());
        }

        @TruffleBoundary
        static boolean equalsString(String a, String b) {
            return a.equals(b);
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

    @ExportLibrary(InteropLibrary.class)
    static final class Members implements TruffleObject {

        final String[] names;

        Members(Set<String> names) {
            this.names = names.toArray(new String[0]);
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return names.length;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                throw InvalidArrayIndexException.create(index);
            }
            return names[(int) index];
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < names.length;
        }
    }

}
