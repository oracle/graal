/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.classfile.attributes.Local;

public class EspressoScope {

    public static Object createVariables(Local[] liveLocals, Frame frame) {
        List<? extends FrameSlot> slots;

        slots = frame.getFrameDescriptor().getSlots();
        int size = slots.size();

        Map<String, FrameSlotInfo> slotsMap;
        Map<String, FrameSlotInfo> identifiersMap;
        if (slots.isEmpty()) {
            slotsMap = Collections.emptyMap();
            identifiersMap = Collections.emptyMap();
        } else if (size == 1) {
            FrameSlot slot = slots.get(0);
            String identifier = slot.getIdentifier().toString();
            FrameSlotInfo frameSlotInfo = new FrameSlotInfo(slot);
            slotsMap = Collections.singletonMap(Objects.toString(identifier), frameSlotInfo);
            Local local = getLocal(liveLocals, slot);
            identifiersMap = Collections.singletonMap(local.getNameAsString(), frameSlotInfo);
        } else {
            slotsMap = new LinkedHashMap<>(size);
            identifiersMap = new LinkedHashMap<>(size);
            for (FrameSlot slot : slots) {
                String slotNumber = slot.getIdentifier().toString();
                Local local = getLocal(liveLocals, slot);
                if (local != null) {
                    String localName = local.getNameAsString();
                    String type = local.getTypeAsString();
                    if ("D".equals(type)) {
                        FrameSlotInfo frameSlotInfo = new FrameSlotInfo(slot, FrameSlotInfo.Kind.DOUBLE);
                        slotsMap.put(slotNumber, frameSlotInfo);
                        identifiersMap.put(localName, frameSlotInfo);
                    } else if ("F".equals(type)) {
                        FrameSlotInfo frameSlotInfo = new FrameSlotInfo(slot, FrameSlotInfo.Kind.FLOAT);
                        slotsMap.put(slotNumber, frameSlotInfo);
                        identifiersMap.put(localName, frameSlotInfo);
                    } else if ("J".equals(type)) {
                        FrameSlotInfo frameSlotInfo = new FrameSlotInfo(slot, FrameSlotInfo.Kind.LONG);
                        slotsMap.put(slotNumber, frameSlotInfo);
                        identifiersMap.put(localName, frameSlotInfo);
                    } else if ("I".equals(type)) {
                        FrameSlotInfo frameSlotInfo = new FrameSlotInfo(slot, FrameSlotInfo.Kind.INT);
                        slotsMap.put(slotNumber, frameSlotInfo);
                        identifiersMap.put(localName, frameSlotInfo);
                    } else {
                        FrameSlotInfo frameSlotInfo = new FrameSlotInfo(slot);
                        slotsMap.put(slotNumber, frameSlotInfo);
                        identifiersMap.put(localName, frameSlotInfo);
                    }
                }
            }
        }
        return new VariablesMapObject(slotsMap, identifiersMap, frame);
    }

    private static Local getLocal(Local[] liveLocals, FrameSlot slot) {
        String identifier = slot.getIdentifier().toString();
        for (Local local : liveLocals) {
            try {
                if (local.getSlot() == Integer.parseInt(identifier)) {
                    return local;
                }
            } catch (NumberFormatException nf) {
                // ignore
            }
        }
        return null;
    }

    // We map both variable names and their slot number to members. However we only expose the
    // variable names through the Interop API. Clients which are bytecode based, e.g. JDWP that use
    // slot numbers as identifiers must operate directly by using read/write member methods.
    @ExportLibrary(InteropLibrary.class)
    static final class VariablesMapObject implements TruffleObject {

        final Map<String, FrameSlotInfo> slots;
        final Map<String, FrameSlotInfo> identifiers;
        final Frame frame;

        private VariablesMapObject(Map<String, FrameSlotInfo> slots, Map<String, FrameSlotInfo> identifiers, Frame frame) {
            this.slots = slots;
            this.identifiers = identifiers;
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
            FrameSlotInfo slotInfo = slots.get(member);
            if (slotInfo == null) {
                // also try identifiers map
                slotInfo = identifiers.get(member);
            }
            if (slotInfo == null || slotInfo.getSlot() == null) {
                throw UnknownIdentifierException.create(member);
            } else if (slotInfo.getKind() == FrameSlotInfo.Kind.DOUBLE) {
                return Double.longBitsToDouble(FrameUtil.getLongSafe(frame, slotInfo.getSlot()));
            } else if (slotInfo.getKind() == FrameSlotInfo.Kind.FLOAT) {
                return Float.intBitsToFloat((int) FrameUtil.getLongSafe(frame, slotInfo.getSlot()));
            } else if (slotInfo.getKind() == FrameSlotInfo.Kind.LONG || slotInfo.getKind() == FrameSlotInfo.Kind.INT) {
                return FrameUtil.getLongSafe(frame, slotInfo.getSlot());
            } else {
                return frame.getValue(slotInfo.getSlot());
            }
        }

        @ExportMessage
        @CompilerDirectives.TruffleBoundary
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new EspressoScope.VariableNamesObject(identifiers.keySet());
        }

        @ExportMessage
        @CompilerDirectives.TruffleBoundary
        boolean isMemberReadable(String member) {
            return identifiers.containsKey(member);
        }

        @ExportMessage
        @CompilerDirectives.TruffleBoundary
        boolean isMemberModifiable(String member) {
            return identifiers.containsKey(member) && frame != null;
        }

        @ExportMessage
        @CompilerDirectives.TruffleBoundary
        void writeMember(String member, Object value) throws UnknownIdentifierException, UnsupportedMessageException {
            if (frame == null) {
                throw UnsupportedMessageException.create();
            }
            FrameSlotInfo slotInfo = slots.get(member);
            if (slotInfo == null) {
                // try identifiers map also
                slotInfo = identifiers.get(member);
            }
            if (slotInfo == null || slotInfo.getSlot() == null) {
                throw UnknownIdentifierException.create(member);
            } else if (slotInfo.getKind() == FrameSlotInfo.Kind.DOUBLE) {
                frame.setLong(slotInfo.getSlot(), Double.doubleToRawLongBits((double) value));
            } else if (slotInfo.getKind() == FrameSlotInfo.Kind.FLOAT) {
                frame.setLong(slotInfo.getSlot(), Float.floatToRawIntBits((float) value));
            } else if (slotInfo.getKind() == FrameSlotInfo.Kind.INT) {
                frame.setLong(slotInfo.getSlot(), (int) value);
            } else if (slotInfo.getKind() == FrameSlotInfo.Kind.LONG) {
                frame.setLong(slotInfo.getSlot(), (long) value);
            } else {
                frame.setObject(slotInfo.getSlot(), value);
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

    private static class FrameSlotInfo {

        private final FrameSlot slot;
        private final Kind kind;

        private enum Kind {
            DOUBLE,
            FLOAT,
            INT,
            LONG,
            OTHER
        }

        FrameSlotInfo(FrameSlot slot) {
            this(slot, Kind.OTHER);
        }

        FrameSlotInfo(FrameSlot slot, Kind kind) {
            this.slot = slot;
            this.kind = kind;
        }

        public FrameSlot getSlot() {
            return slot;
        }

        public Kind getKind() {
            return kind;
        }
    }
}
