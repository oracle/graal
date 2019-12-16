/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.meta.Local;

public class EspressoScope {

    public static Object createVariables(Local[] liveLocals, Frame frame) {
        List<? extends FrameSlot> slots;

        slots = frame.getFrameDescriptor().getSlots();
        int size = slots.size();

        Map<String, FrameSlot> slotsMap;
        if (slots.isEmpty()) {
            slotsMap = Collections.emptyMap();
        } else if (size == 1) {
            FrameSlot slot = slots.get(0);
            String identifier = getIdentifier(liveLocals, slot);
            getIdentifier(liveLocals, slot);
            slotsMap = Collections.singletonMap(Objects.toString(identifier), slot);
        } else {
            slotsMap = new LinkedHashMap<>(size);
            for (FrameSlot slot : slots) {
                String identifier = getIdentifier(liveLocals, slot);
                if (identifier != null) {
                    slotsMap.put(identifier, slot);
                }
            }
        }
        return new VariablesMapObject(slotsMap, frame);
    }

    private static String getIdentifier(Local[] liveLocals, FrameSlot slot) {
        String identifier = slot.getIdentifier().toString();
        for (Local local : liveLocals) {
            try {
                if (local.getSlot() == Integer.parseInt(identifier)) {
                    return local.getName().toString();
                }
            } catch (NumberFormatException nf) {
                // ignore
            }
        }
        return null;
    }

    @ExportLibrary(InteropLibrary.class)
    static final class VariablesMapObject implements TruffleObject {

        final Map<String, ? extends FrameSlot> slots;
        final Frame frame;

        private VariablesMapObject(Map<String, ? extends FrameSlot> slots, Frame frame) {
            this.slots = slots;
            this.frame = frame;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        @CompilerDirectives.TruffleBoundary
        Object readMember(String member) throws UnknownIdentifierException {
            if (frame == null) {
                return EspressoScope.NullValue.INSTANCE;
            }
            FrameSlot slot = slots.get(member);
            if (slot == null) {
                throw UnknownIdentifierException.create(member);
            } else {
                return frame.getValue(slot);
            }
        }

        @ExportMessage
        @CompilerDirectives.TruffleBoundary
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new EspressoScope.VariableNamesObject(slots.keySet());
        }

        @ExportMessage
        @CompilerDirectives.TruffleBoundary
        boolean isMemberReadable(String member) {
            return slots.containsKey(member);
        }

        @ExportMessage
        @CompilerDirectives.TruffleBoundary
        boolean isMemberModifiable(String member) {
            return slots.containsKey(member) && frame != null;
        }

        @ExportMessage
        @CompilerDirectives.TruffleBoundary
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
    }

    @ExportLibrary(InteropLibrary.class)
    static final class NullValue implements TruffleObject {

        private static final EspressoScope.NullValue INSTANCE = new EspressoScope.NullValue();

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

        static final EspressoScope.VariableNamesObject EMPTY = new EspressoScope.VariableNamesObject(Collections.emptySet());

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
        @CompilerDirectives.TruffleBoundary
        long getArraySize() {
            return names.size();
        }

        @ExportMessage
        @CompilerDirectives.TruffleBoundary
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (!isArrayElementReadable(index)) {
                throw InvalidArrayIndexException.create(index);
            }
            return names.get((int) index);
        }

        @ExportMessage
        @CompilerDirectives.TruffleBoundary
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < names.size();
        }
    }
}
