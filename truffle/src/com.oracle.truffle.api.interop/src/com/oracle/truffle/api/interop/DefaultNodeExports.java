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
package com.oracle.truffle.api.interop;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Default implementation of {@link NodeLibrary}.
 */
@ExportLibrary(value = NodeLibrary.class, receiverType = Node.class)
@SuppressWarnings("static-method")
final class DefaultNodeExports {

    @ExportMessage
    @SuppressWarnings("unused")
    static boolean hasScope(Node node, Frame frame) {
        return hasScopeSlowPath(node);
    }

    @TruffleBoundary
    private static boolean hasScopeSlowPath(Node node) {
        RootNode root = node.getRootNode();
        TruffleLanguage<?> language = InteropAccessor.NODES.getLanguage(root);
        return language != null && (node == root || InteropAccessor.INSTRUMENT.isInstrumentable(node));
    }

    @ExportMessage
    @SuppressWarnings("unused")
    static Object getScope(Node node, Frame frame, boolean nodeEnter) throws UnsupportedMessageException {
        return getScopeSlowPath(node, frame != null ? frame.materialize() : null);
    }

    @TruffleBoundary
    @SuppressWarnings("unchecked")
    private static Object getScopeSlowPath(Node node, MaterializedFrame frame) throws UnsupportedMessageException {
        RootNode root = node.getRootNode();
        TruffleLanguage<?> language = InteropAccessor.NODES.getLanguage(root);
        if (language != null && (node == root || InteropAccessor.INSTRUMENT.isInstrumentable(node))) {
            return createDefaultScope(root, frame, (Class<? extends TruffleLanguage<?>>) language.getClass());
        }
        throw UnsupportedMessageException.create();
    }

    private static boolean isInternal(Object identifier) {
        if (identifier == null) {
            return true;
        }
        if (InteropAccessor.INSTRUMENT.isInputValueSlotIdentifier(identifier)) {
            return true;
        }
        return false;
    }

    @TruffleBoundary
    private static Object createDefaultScope(RootNode root, MaterializedFrame frame, Class<? extends TruffleLanguage<?>> language) {
        LinkedHashMap<String, DefaultScopeMember> slotsMap = new LinkedHashMap<>();
        FrameDescriptor descriptor = frame == null ? root.getFrameDescriptor() : frame.getFrameDescriptor();
        for (Map.Entry<Object, Integer> entry : descriptor.getAuxiliarySlots().entrySet()) {
            if (!isInternal(entry.getKey()) && (frame == null || InteropLibrary.isValidValue(frame.getAuxiliarySlot(entry.getValue())))) {
                String name = Objects.toString(entry.getKey());
                slotsMap.put(name, new DefaultScopeMember(entry.getKey(), name, entry.getValue(), descriptor));
            }
        }
        return new DefaultScope(slotsMap, root, frame, language);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class DefaultScope implements TruffleObject {

        static final int LIMIT = 5;

        private final Map<String, DefaultScopeMember> slots;
        private final RootNode root;
        private final Frame frame;
        private final Class<? extends TruffleLanguage<?>> language;

        private DefaultScope(Map<String, DefaultScopeMember> slots, RootNode root, Frame frame, Class<? extends TruffleLanguage<?>> language) {
            this.slots = slots;
            this.root = root;
            this.frame = frame;
            this.language = language;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof DefaultScope;
        }

        @ExportMessage
        boolean hasLanguage() {
            return language != null;
        }

        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage() throws UnsupportedMessageException {
            if (language == null) {
                throw UnsupportedMessageException.create();
            }
            return language;
        }

        @ExportMessage
        boolean isScope() {
            return true;
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @TruffleBoundary
        DefaultScopeMember getMember(String name) {
            return slots.get(name);
        }

        @TruffleBoundary
        boolean hasMember(String name) {
            return slots.containsKey(name);
        }

        @ExportMessage
        static class ReadMember {

            @Specialization(guards = "memberLibrary.isString(memberString)")
            static Object read(DefaultScope receiver,
                            Object memberString,
                            @Shared("memberLibrary") @CachedLibrary(limit = "LIMIT") InteropLibrary memberLibrary) throws UnknownMemberException {
                String name;
                try {
                    name = memberLibrary.asString(memberString);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                DefaultScopeMember slot = receiver.getMember(name);
                if (slot == null) {
                    throw UnknownMemberException.create(memberString);
                } else if (receiver.frame == null) {
                    return DefaultScopeNull.INSTANCE;
                } else {
                    return receiver.frame.getAuxiliarySlot(slot.index);
                }
            }

            @Specialization
            static Object read(DefaultScope receiver,
                            DefaultScopeMember slot) throws UnknownMemberException {
                FrameDescriptor descriptor = receiver.frame == null ? receiver.root.getFrameDescriptor() : receiver.frame.getFrameDescriptor();
                if (slot.descriptor != descriptor) {
                    throw UnknownMemberException.create(slot);
                }
                if (receiver.frame == null) {
                    return DefaultScopeNull.INSTANCE;
                } else {
                    return receiver.frame.getAuxiliarySlot(slot.index);
                }
            }

            @Fallback
            static Object read(@SuppressWarnings("unused") DefaultScope receiver,
                            Object unknown) throws UnknownMemberException {
                throw UnknownMemberException.create(unknown);
            }
        }

        @ExportMessage
        @TruffleBoundary
        Object getMemberObjects() {
            return new DefaultScopeMembers(slots.values());
        }

        @ExportMessage
        static class IsMemberReadable {

            @Specialization(guards = "memberLibrary.isString(memberString)")
            static boolean isReadable(DefaultScope receiver,
                            Object memberString,
                            @Shared("memberLibrary") @CachedLibrary(limit = "LIMIT") InteropLibrary memberLibrary) {
                String name;
                try {
                    name = memberLibrary.asString(memberString);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                return receiver.hasMember(name);
            }

            @Specialization
            static boolean isReadable(DefaultScope receiver,
                            DefaultScopeMember slot) {
                FrameDescriptor descriptor = receiver.frame == null ? receiver.root.getFrameDescriptor() : receiver.frame.getFrameDescriptor();
                return slot.descriptor == descriptor;
            }

            @Fallback
            @SuppressWarnings("unused")
            static boolean isReadable(DefaultScope receiver,
                            Object unknown) {
                return false;
            }
        }

        @ExportMessage
        static class IsMemberModifiable {

            @Specialization(guards = "memberLibrary.isString(memberString)")
            static boolean isModifiable(DefaultScope receiver,
                            Object memberString,
                            @Shared("memberLibrary") @CachedLibrary(limit = "LIMIT") InteropLibrary memberLibrary) {
                if (receiver.frame == null) {
                    return false;
                }
                String name;
                try {
                    name = memberLibrary.asString(memberString);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                return receiver.hasMember(name);
            }

            @Specialization
            static boolean isModifiable(DefaultScope receiver,
                            DefaultScopeMember slot) {
                FrameDescriptor descriptor = receiver.frame == null ? receiver.root.getFrameDescriptor() : receiver.frame.getFrameDescriptor();
                return slot.descriptor == descriptor && receiver.frame != null;
            }

            @Fallback
            @SuppressWarnings("unused")
            static boolean isModifiable(DefaultScope receiver,
                            Object unknown) {
                return false;
            }
        }

        @ExportMessage
        static class WriteMember {

            @Specialization(guards = "memberLibrary.isString(memberString)")
            static void write(DefaultScope receiver,
                            Object memberString,
                            Object value,
                            @Shared("memberLibrary") @CachedLibrary(limit = "LIMIT") InteropLibrary memberLibrary) throws UnknownMemberException, UnsupportedMessageException {
                if (receiver.frame == null) {
                    throw UnsupportedMessageException.create();
                }
                String name;
                try {
                    name = memberLibrary.asString(memberString);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                DefaultScopeMember slot = receiver.getMember(name);
                if (slot == null) {
                    throw UnknownMemberException.create(memberString);
                } else {
                    receiver.frame.setAuxiliarySlot(slot.index, value);
                }
            }

            @Specialization
            static void write(DefaultScope receiver,
                            DefaultScopeMember slot,
                            Object value) throws UnsupportedMessageException, UnknownMemberException {
                if (receiver.frame == null) {
                    throw UnsupportedMessageException.create();
                }
                FrameDescriptor descriptor = receiver.frame == null ? receiver.root.getFrameDescriptor() : receiver.frame.getFrameDescriptor();
                if (slot.descriptor != descriptor) {
                    throw UnknownMemberException.create(slot);
                } else {
                    receiver.frame.setAuxiliarySlot(slot.index, value);
                }
            }

            @Fallback
            @SuppressWarnings("unused")
            static void write(DefaultScope receiver,
                            Object unknown,
                            Object value) throws UnknownMemberException {
                throw UnknownMemberException.create(unknown);
            }
        }

        @ExportMessage
        boolean isMemberInsertable(@SuppressWarnings("unused") Object member) {
            return false;
        }

        @ExportMessage
        @TruffleBoundary
        boolean hasSourceLocation() {
            return root.getSourceSection() != null;
        }

        @ExportMessage
        @TruffleBoundary
        SourceSection getSourceLocation() throws UnsupportedMessageException {
            SourceSection section = root.getSourceSection();
            if (section == null) {
                throw UnsupportedMessageException.create();
            }
            return section;
        }

        @ExportMessage
        @TruffleBoundary
        Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            String name = root.getName();
            if (name == null) {
                name = "local";
            }
            return name;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class DefaultScopeNull implements TruffleObject {

        private static final DefaultScopeNull INSTANCE = new DefaultScopeNull();

        private DefaultScopeNull() {
        }

        @ExportMessage
        boolean isNull() {
            return true;
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class DefaultScopeMembers implements TruffleObject {

        @CompilationFinal(dimensions = 1) final DefaultScopeMember[] members;

        DefaultScopeMembers(Collection<DefaultScopeMember> members) {
            this.members = members.toArray(new DefaultScopeMember[0]);
        }

        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return members.length;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                throw InvalidArrayIndexException.create(index);
            }
            return members[(int) index];
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < members.length;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class DefaultScopeMember implements TruffleObject {

        final Object slot;
        private final String name;
        final int index;
        final FrameDescriptor descriptor;

        DefaultScopeMember(Object slot, String name, int index, FrameDescriptor descriptor) {
            this.slot = slot;
            this.name = name;
            this.index = index;
            this.descriptor = descriptor;
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
            return true;
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
}
