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
package com.oracle.truffle.sl.bytecode;

import java.util.LinkedHashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.TagTreeNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.runtime.SLContext;
import com.oracle.truffle.sl.runtime.SLNull;
import com.oracle.truffle.sl.runtime.SLStrings;

@ExportLibrary(value = NodeLibrary.class, receiverType = TagTreeNode.class)
@SuppressWarnings({"static-method", "unused"})
final class SLBytecodeScopeExports {

    @ExportMessage
    static boolean hasRootInstance(TagTreeNode node, Frame frame) {
        return getRootInstanceSlowPath(node) != null;
    }

    @ExportMessage
    static Object getRootInstance(TagTreeNode node, Frame frame) throws UnsupportedMessageException {
        // The instance of the current RootNode is a function of the same name.
        return getRootInstanceSlowPath(node);
    }

    @TruffleBoundary
    private static Object getRootInstanceSlowPath(TagTreeNode node) {
        return SLContext.get(node).getFunctionRegistry().getFunction(SLStrings.getSLRootName(node.getRootNode()));
    }

    @ExportMessage
    static boolean hasScope(TagTreeNode node, Frame frame) {
        return true;
    }

    @ExportMessage
    static Object getScope(TagTreeNode node, Frame frame, boolean nodeEnter) throws UnsupportedMessageException {
        return new Scope(node, frame, nodeEnter);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Scope implements TruffleObject {

        @NeverDefault final BytecodeNode bytecode;
        @NeverDefault final TagTreeNode node;
        @NeverDefault final int bci;

        final boolean rootScope;

        final Frame frame;

        private Map<String, Integer> nameToIndex;

        private Scope(TagTreeNode node, Frame frame, boolean nodeEnter) {
            this.bytecode = node.getBytecodeNode();
            this.node = node;
            this.frame = frame;
            this.rootScope = node.hasTag(RootTag.class);
            this.bci = nodeEnter ? node.getStartBci() : node.getEndBci();
        }

        @ExportMessage
        boolean accepts(@Shared @Cached("this.bytecode") BytecodeNode cachedBytecode,
                        @Shared @Cached("this.node") TagTreeNode cachedNode,
                        @Shared @Cached("this.bci") int cachedBci,
                        @Shared @Cached("this.rootScope") boolean cachedRootScope) {
            return this.bytecode == cachedBytecode && this.bci == cachedBci && this.node == cachedNode && this.rootScope == cachedRootScope;
        }

        @ExportMessage
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage(
                        @Shared @Cached("this.node") TagTreeNode cachedNode) throws UnsupportedMessageException {
            return SLLanguage.class;
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

            @Specialization(guards = {"equalsString(cachedMember, member)"}, limit = "5")
            static Object doCached(Scope scope, String member,
                            @Shared @Cached("scope.bytecode") BytecodeNode cachedBytecode,
                            @Shared @Cached("scope.bci") int cachedBci,
                            @Shared @Cached(value = "scope.rootScope", neverDefault = false) boolean cachedRootScope,
                            @Cached("member") String cachedMember,
                            @Cached("scope.slotToIndex(cachedMember)") int index) throws UnsupportedMessageException {
                if (index == -1) {
                    throw UnsupportedMessageException.create();
                }
                Frame frame = scope.frame;
                if (frame == null) {
                    return SLNull.SINGLETON;
                }
                if (cachedRootScope) {
                    return frame.getArguments()[index];
                } else {
                    Object o = cachedBytecode.getLocalValue(cachedBci, frame, index);
                    if (o == null) {
                        o = SLNull.SINGLETON;
                    }
                    return o;
                }
            }

            @Specialization(replaces = "doCached")
            @TruffleBoundary
            static Object doGeneric(Scope scope, String member) throws UnsupportedMessageException {
                return doCached(scope, member, scope.bytecode, scope.bci, scope.rootScope, member, scope.slotToIndex(member));
            }

        }

        @ExportMessage
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new Members(this);
        }

        @ExportMessage
        static class IsMemberReadable {

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
            @Specialization(guards = {"equalsString(cachedMember, member)"}, limit = "5")
            static void doCached(Scope scope, String member, Object value,
                            @Shared @Cached("scope.bytecode") BytecodeNode cachedBytecode,
                            @Shared @Cached("scope.bci") int cachedBci,
                            @Shared @Cached(value = "scope.rootScope", neverDefault = false) boolean cachedRootScope,
                            @Cached("member") String cachedMember,
                            @Cached("scope.slotToIndex(cachedMember)") int index) throws UnknownIdentifierException, UnsupportedMessageException {
                if (index == -1 || scope.frame == null) {
                    throw UnsupportedMessageException.create();
                }
                if (cachedRootScope) {
                    scope.frame.getArguments()[index] = value;
                } else {
                    cachedBytecode.setLocalValue(cachedBci, scope.frame, index, value);
                }
            }

            @Specialization(replaces = "doCached")
            @TruffleBoundary
            static void doGeneric(Scope scope, String member, Object value) throws UnknownIdentifierException, UnsupportedMessageException {
                doCached(scope, member, value, scope.bytecode, scope.bci, scope.rootScope, member, scope.slotToIndex(member));
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
        int slotToIndex(String member) {
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
            Object[] names;
            if (this.rootScope) {
                SLBytecodeRootNode root = (SLBytecodeRootNode) this.node.getBytecodeNode().getBytecodeRootNode();
                names = root.getArgumentNames();
            } else {
                names = this.bytecode.getLocalNames(this.bci);
            }
            for (Object local : names) {
                String name = resolveLocalName(local);
                locals.put(name, index);
                index++;
            }
            return locals;
        }

        private String resolveLocalName(Object local) {
            String name = null;
            if (local != null) {
                try {
                    name = InteropLibrary.getUncached().asString(local);
                } catch (UnsupportedMessageException e) {
                }
            }
            return name;
        }

        private Members createMembers() {
            return new Members(this);
        }

        @TruffleBoundary
        static boolean equalsString(String a, String b) {
            return a.equals(b);
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class Members implements TruffleObject {

        final Scope scope;

        Members(Scope scope) {
            this.scope = scope;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        long getArraySize() {
            if (scope.rootScope) {
                return ((SLBytecodeRootNode) scope.bytecode.getBytecodeRootNode()).parameterCount;
            } else {
                return scope.bytecode.getLocalCount(scope.bci);
            }
        }

        @ExportMessage
        @TruffleBoundary
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            long size = getArraySize();
            if (index < 0 || index >= size) {
                throw InvalidArrayIndexException.create(index);
            }
            if (scope.rootScope) {
                return scope.bytecode.getLocals().get((int) index).getName();
            } else {
                return scope.bytecode.getLocalName(scope.bci, (int) index);
            }
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < getArraySize();
        }
    }

}
