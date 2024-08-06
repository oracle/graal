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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
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
        LinkedHashMap<String, Object> slotsMap = new LinkedHashMap<>();
        FrameDescriptor descriptor = frame == null ? root.getFrameDescriptor() : frame.getFrameDescriptor();
        for (Map.Entry<Object, Integer> entry : descriptor.getAuxiliarySlots().entrySet()) {
            if (!isInternal(entry.getKey()) && (frame == null || InteropLibrary.isValidValue(frame.getAuxiliarySlot(entry.getValue())))) {
                slotsMap.put(Objects.toString(entry.getKey()), entry.getValue());
            }
        }
        return new DefaultScope(slotsMap, root, frame, language);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class DefaultScope implements TruffleObject {

        private final Map<String, Object> slots;
        private final RootNode root;
        private final Frame frame;
        private final Class<? extends TruffleLanguage<?>> language;

        private DefaultScope(Map<String, Object> slots, RootNode root, Frame frame, Class<? extends TruffleLanguage<?>> language) {
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

        @ExportMessage
        @TruffleBoundary
        Object readMember(String member) throws UnknownIdentifierException {
            if (frame == null) {
                return DefaultScopeNull.INSTANCE;
            }
            Object slot = slots.get(member);
            if (slot == null) {
                throw UnknownIdentifierException.create(member);
            } else {
                return frame.getAuxiliarySlot((int) slot);
            }
        }

        @ExportMessage
        @TruffleBoundary
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new DefaultScopeMembers(slots.keySet());
        }

        @ExportMessage
        @TruffleBoundary
        boolean isMemberReadable(String member) {
            return slots.containsKey(member);
        }

        @ExportMessage
        @TruffleBoundary
        boolean isMemberModifiable(String member) {
            return slots.containsKey(member) && frame != null;
        }

        @ExportMessage
        @TruffleBoundary
        void writeMember(String member, Object value) throws UnknownIdentifierException, UnsupportedMessageException {
            if (frame == null) {
                throw UnsupportedMessageException.create();
            }
            Object slot = slots.get(member);
            if (slot == null) {
                throw UnknownIdentifierException.create(member);
            } else {
                frame.setAuxiliarySlot((int) slot, value);
            }
        }

        @ExportMessage
        boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
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

        final String[] names;

        DefaultScopeMembers(Set<String> names) {
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
