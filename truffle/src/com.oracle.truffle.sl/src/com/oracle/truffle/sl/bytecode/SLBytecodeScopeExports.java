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
import com.oracle.truffle.api.bytecode.TagTreeNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
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
    static boolean hasRootInstance(TagTreeNode node, Frame frame, @Bind SLContext context) {
        return getRootInstanceSlowPath(context, node) != null;
    }

    @ExportMessage
    static Object getRootInstance(TagTreeNode node, Frame frame, @Bind SLContext context) throws UnsupportedMessageException {
        // The instance of the current RootNode is a function of the same name.
        return getRootInstanceSlowPath(context, node);
    }

    @TruffleBoundary
    private static Object getRootInstanceSlowPath(SLContext context, TagTreeNode node) {
        return context.getFunctionRegistry().getFunction(SLStrings.getSLRootName(node.getRootNode()));
    }

    @ExportMessage
    static boolean hasScope(TagTreeNode node, Frame frame) {
        return true;
    }

    @ExportMessage
    static Object getScope(TagTreeNode node, Frame frame, boolean nodeEnter) throws UnsupportedMessageException {
        if (node.hasTag(RootTag.class)) {
            /*
             * Simple language has special behavior for arguments outside of regular nodes as it
             * translates arguments to values directly.
             */
            return new ArgumentsScope(node, frame);
        } else {
            /*
             * We are lucky, language semantics exactly match the default scoping behavior
             */
            return node.createDefaultScope(frame, nodeEnter);
        }
    }

    /**
     * Scope of function arguments. This scope is provided by nodes just under a root, outside of
     * any block.
     */
    @ExportLibrary(InteropLibrary.class)
    static final class ArgumentsScope implements TruffleObject {

        @NeverDefault final SLBytecodeRootNode rootNode;
        final Frame frame;
        private Map<String, Integer> nameToIndex;

        private ArgumentsScope(TagTreeNode node, Frame frame) {
            this.rootNode = (SLBytecodeRootNode) node.getBytecodeNode().getBytecodeRootNode();
            this.frame = frame;
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
        @SuppressWarnings({"hiding", "unused"})//
        boolean accepts(@Cached(value = "this.rootNode", adopt = false) SLBytecodeRootNode cachedRootNode) {
            return this.rootNode == cachedRootNode;
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
            static Object doCached(ArgumentsScope scope, String member,
                            @Cached("member") String cachedMember,
                            @Cached("scope.slotToIndex(cachedMember)") int index) throws UnsupportedMessageException {
                if (index == -1) {
                    throw UnsupportedMessageException.create();
                }
                Frame frame = scope.frame;
                Object[] arguments = frame != null ? scope.frame.getArguments() : null;
                if (arguments == null || index >= arguments.length) {
                    return SLNull.SINGLETON;
                }
                return arguments[index];
            }

            @Specialization(replaces = "doCached")
            static Object doGeneric(ArgumentsScope scope, String member) throws UnsupportedMessageException {
                return doCached(scope, member, member, scope.slotToIndex(member));
            }
        }

        @ExportMessage
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new Members(rootNode.getArgumentNames());
        }

        @ExportMessage
        static class IsMemberReadable {

            @Specialization(guards = {"equalsString(cachedMember, member)"}, limit = "5")
            static boolean doCached(ArgumentsScope scope, String member,
                            @Cached("member") String cachedMember,
                            @Cached("scope.slotToIndex(cachedMember)") int index) {
                return index != -1;
            }

            @Specialization(replaces = "doCached")
            static boolean doGeneric(ArgumentsScope scope, String member) {
                return scope.slotToIndex(member) != -1;
            }

        }

        @ExportMessage
        static class IsMemberModifiable {

            @Specialization(guards = {"equalsString(cachedMember, member)"}, limit = "5")
            static boolean doCached(ArgumentsScope scope, String member,
                            @Cached("member") String cachedMember,
                            @Cached("scope.slotToIndex(cachedMember)") int index) {
                return index != -1 && scope.frame != null && index < scope.frame.getArguments().length;
            }

            @Specialization(replaces = "doCached")
            static boolean doGeneric(ArgumentsScope scope, String member) {
                return doCached(scope, member, member, scope.slotToIndex(member));
            }

        }

        @ExportMessage
        static class WriteMember {
            @Specialization(guards = {"equalsString(cachedMember, member)"}, limit = "5")
            static void doCached(ArgumentsScope scope, String member, Object value,
                            @Cached("member") String cachedMember,
                            @Cached("scope.slotToIndex(cachedMember)") int index) throws UnknownIdentifierException, UnsupportedMessageException {
                if (index == -1 || scope.frame == null) {
                    throw UnsupportedMessageException.create();
                }
                Object[] arguments = scope.frame.getArguments();
                if (index >= arguments.length) {
                    throw UnsupportedMessageException.create();
                }
                arguments[index] = value;
            }

            @Specialization(replaces = "doCached")
            @TruffleBoundary
            static void doGeneric(ArgumentsScope scope, String member, Object value) throws UnknownIdentifierException, UnsupportedMessageException {
                doCached(scope, member, value, member, scope.slotToIndex(member));
            }
        }

        @ExportMessage
        boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
            return false;
        }

        @ExportMessage
        @TruffleBoundary
        boolean hasSourceLocation() {
            return this.rootNode.ensureSourceSection() != null;
        }

        @ExportMessage
        @TruffleBoundary
        SourceSection getSourceLocation() throws UnsupportedMessageException {
            SourceSection section = this.rootNode.ensureSourceSection();
            if (section == null) {
                throw UnsupportedMessageException.create();
            }
            return section;
        }

        @ExportMessage
        @TruffleBoundary
        Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return rootNode.getName();
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
            Object[] names = this.rootNode.getArgumentNames();
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
            return new Members(rootNode.getArgumentNames());
        }

        @TruffleBoundary
        static boolean equalsString(String a, String b) {
            return a.equals(b);
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class Members implements TruffleObject {

        final Object[] argumentNames;

        Members(Object[] argumentNames) {
            this.argumentNames = argumentNames;
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return argumentNames.length;
        }

        @ExportMessage
        @TruffleBoundary
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            long size = getArraySize();
            if (index < 0 || index >= size) {
                throw InvalidArrayIndexException.create(index);
            }
            return argumentNames[(int) index];
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < getArraySize();
        }
    }

}
