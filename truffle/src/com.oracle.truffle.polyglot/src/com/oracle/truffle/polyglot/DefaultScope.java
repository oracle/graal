/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * A default {@link FrameSlot}-based implementation of variables contained in the frame.
 */
final class DefaultScope {

    private static boolean isInternal(FrameSlot slot) {
        Object identifier = slot.getIdentifier();
        if (identifier == null) {
            return true;
        }
        if (EngineAccessor.INSTRUMENT.isInputValueSlotIdentifier(identifier)) {
            return true;
        }
        return false;
    }

    @TruffleBoundary
    static Object getVariables(RootNode root, Frame frame, Class<? extends TruffleLanguage<?>> language) {
        List<? extends FrameSlot> slots;
        if (frame == null) {
            slots = root.getFrameDescriptor().getSlots();
        } else {
            slots = frame.getFrameDescriptor().getSlots();
            // Filter out slots with null values:
            List<FrameSlot> nonNulls = null;
            int lastI = 0;
            for (int i = 0; i < slots.size(); i++) {
                FrameSlot slot = slots.get(i);
                if (!EngineAccessor.INTEROP.isInteropType(frame.getValue(slot)) || isInternal(slot)) {
                    if (nonNulls == null) {
                        nonNulls = new ArrayList<>(slots.size());
                    }
                    nonNulls.addAll(slots.subList(lastI, i));
                    lastI = i + 1;
                }
            }
            if (nonNulls != null) {
                if (lastI < slots.size()) {
                    nonNulls.addAll(slots.subList(lastI, slots.size()));
                }
                slots = nonNulls;
            }
        }
        Map<String, FrameSlot> slotsMap;
        if (slots.isEmpty()) {
            slotsMap = Collections.emptyMap();
        } else if (slots.size() == 1) {
            FrameSlot slot = slots.get(0);
            slotsMap = Collections.singletonMap(Objects.toString(slot.getIdentifier()), slot);
        } else {
            slotsMap = new LinkedHashMap<>(slots.size());
            for (FrameSlot slot : slots) {
                slotsMap.put(Objects.toString(slot.getIdentifier()), slot);
            }
        }
        return new VariablesMapObject(slotsMap, root, frame, language);
    }

    static Object getArguments(Object[] frameArguments, Class<? extends TruffleLanguage<?>> language) {
        return new ArgumentsArrayObject(frameArguments, language);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class VariablesMapObject implements TruffleObject {

        final Map<String, ? extends FrameSlot> slots;
        final RootNode root;
        final Frame frame;
        private final Class<? extends TruffleLanguage<?>> language;

        private VariablesMapObject(Map<String, ? extends FrameSlot> slots, RootNode root, Frame frame, Class<? extends TruffleLanguage<?>> language) {
            this.slots = slots;
            this.root = root;
            this.frame = frame;
            this.language = language;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof VariablesMapObject;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return language;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isScope() {
            return true;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        Object readMember(String member) throws UnknownIdentifierException {
            if (frame == null) {
                return NullValue.INSTANCE;
            }
            FrameSlot slot = slots.get(member);
            if (slot == null) {
                throw UnknownIdentifierException.create(member);
            } else {
                return frame.getValue(slot);
            }
        }

        @ExportMessage
        @TruffleBoundary
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new VariableNamesObject(slots.keySet());
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
            FrameSlot slot = slots.get(member);
            if (slot == null) {
                throw UnknownIdentifierException.create(member);
            } else {
                frame.setObject(slot, value);
            }
        }

        @SuppressWarnings("static-method")
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
        SourceSection getSourceLocation() {
            return root.getSourceSection();
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
    static final class NullValue implements TruffleObject {

        private static final NullValue INSTANCE = new NullValue();

        NullValue() {
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isNull() {
            return true;
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class VariableNamesObject implements TruffleObject {

        static final VariableNamesObject EMPTY = new VariableNamesObject(Collections.emptySet());

        final List<String> names;

        VariableNamesObject(Set<String> names) {
            this.names = new ArrayList<>(names);
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        long getArraySize() {
            return names.size();
        }

        @ExportMessage
        @TruffleBoundary
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                throw InvalidArrayIndexException.create(index);
            }
            return names.get((int) index);
        }

        @ExportMessage
        @TruffleBoundary
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < names.size();
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class ArgumentsArrayObject implements TruffleObject {

        final Object[] args;
        private final Class<? extends TruffleLanguage<?>> language;

        ArgumentsArrayObject(Object[] args, Class<? extends TruffleLanguage<?>> language) {
            this.args = args;
            this.language = language;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return language;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isScope() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new ArgumentNamesObject(args.length);
        }

        @ExportMessage
        @TruffleBoundary
        boolean isMemberReadable(String member) {
            try {
                int index = Integer.parseInt(member);
                if (0 <= index && index < args.length) {
                    return EngineAccessor.INTEROP.isInteropType(args[index]);
                } else {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }

        @ExportMessage(name = "isMemberModifiable")
        @ExportMessage(name = "isMemberInsertable")
        @SuppressWarnings("static-method")
        boolean isMemberModifiable(@SuppressWarnings("unused") String member) {
            return false;
        }

        @ExportMessage
        @TruffleBoundary
        Object readMember(String member) throws UnsupportedMessageException {
            try {
                int index = Integer.parseInt(member);
                if (0 <= index && index < args.length) {
                    return args[index];
                }
            } catch (NumberFormatException e) {
            }
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        void writeMember(@SuppressWarnings("unused") String member, @SuppressWarnings("unused") Object value) throws UnsupportedMessageException {
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return "local";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class ArgumentNamesObject implements TruffleObject {

        private final int n;

        ArgumentNamesObject(int n) {
            this.n = n;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        long getArraySize() {
            return n;
        }

        @ExportMessage
        @TruffleBoundary
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                throw InvalidArrayIndexException.create(index);
            }
            return Long.toString(index);
        }

        @ExportMessage
        @TruffleBoundary
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < n;
        }
    }

}
