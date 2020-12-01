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
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.runtime.StaticObject;

public class EspressoScope {

    public static Object createVariables(Local[] liveLocals, Frame frame) {
        int slotCount = liveLocals.length;
        Map<String, FrameSlotInfo> slotsMap;
        Map<String, FrameSlotInfo> identifiersMap;
        if (liveLocals.length == 0) {
            slotsMap = Collections.emptyMap();
            identifiersMap = Collections.emptyMap();
        } else if (liveLocals.length == 1) {
            int slot = 0;
            String identifier = "0";
            FrameSlotInfo frameSlotInfo = new FrameSlotInfo(slot);
            slotsMap = Collections.singletonMap(identifier, frameSlotInfo);
            Local local = getLocal(liveLocals, slot);
            identifiersMap = Collections.singletonMap(local.getNameAsString(), frameSlotInfo);
        } else {
            slotsMap = new LinkedHashMap<>(slotCount);
            identifiersMap = new LinkedHashMap<>(slotCount);
            for (int slot = 0; slot < slotCount; ++slot) {
                String slotNumber = String.valueOf(slot);
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

    private static Local getLocal(Local[] liveLocals, int slot) {
        for (Local local : liveLocals) {
            try {
                if (local.getSlot() == slot) {
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

            if (slotInfo == null) {
                throw UnknownIdentifierException.create(member);
            }
            FrameSlot refsSlot = frame.getFrameDescriptor().findFrameSlot("refs");
            FrameSlot primitivesSlot = frame.getFrameDescriptor().findFrameSlot("primitives");
            final Object[] refs = (Object[]) FrameUtil.getObjectSafe(frame, refsSlot);
            final long[] primitives = (long[]) FrameUtil.getObjectSafe(frame, primitivesSlot);

            if (slotInfo.getKind() == FrameSlotInfo.Kind.DOUBLE) {
                return BytecodeNode.getLocalDouble(primitives, slotInfo.getSlot());
            } else if (slotInfo.getKind() == FrameSlotInfo.Kind.FLOAT) {
                return BytecodeNode.getLocalFloat(primitives, slotInfo.getSlot());
            } else if (slotInfo.getKind() == FrameSlotInfo.Kind.LONG)
                return BytecodeNode.getLocalLong(primitives, slotInfo.getSlot());
            else if (slotInfo.getKind() == FrameSlotInfo.Kind.INT) {
                return (long) BytecodeNode.getLocalInt(primitives, slotInfo.getSlot());
            } else {
                Object localObject = BytecodeNode.getRawLocalObject(refs, slotInfo.getSlot());
                if (localObject != null) {
                    return localObject;
                }
                return BytecodeNode.getLocalLong(primitives, slotInfo.getSlot());
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
            return slots.containsKey(member) || identifiers.containsKey(member);
        }

        @ExportMessage
        @CompilerDirectives.TruffleBoundary
        boolean isMemberModifiable(String member) {
            return (slots.containsKey(member) || identifiers.containsKey(member)) && frame != null;
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
            if (slotInfo == null) {
                throw UnknownIdentifierException.create(member);
            }

            FrameSlot refsSlot = frame.getFrameDescriptor().findFrameSlot("refs");
            FrameSlot primitivesSlot = frame.getFrameDescriptor().findFrameSlot("primitives");
            final Object[] refs = (Object[]) FrameUtil.getObjectSafe(frame, refsSlot);
            final long[] primitives = (long[]) FrameUtil.getObjectSafe(frame, primitivesSlot);

            if (slotInfo.getKind() == FrameSlotInfo.Kind.DOUBLE) {
                BytecodeNode.setLocalDouble(primitives, slotInfo.getSlot(), (double) value);
            } else if (slotInfo.getKind() == FrameSlotInfo.Kind.FLOAT) {
                BytecodeNode.setLocalFloat(primitives, slotInfo.getSlot(), (float) value);
            } else if (slotInfo.getKind() == FrameSlotInfo.Kind.INT) {
                BytecodeNode.setLocalInt(primitives, slotInfo.getSlot(), (int) value);
            } else if (slotInfo.getKind() == FrameSlotInfo.Kind.LONG) {
                BytecodeNode.setLocalLong(primitives, slotInfo.getSlot(), (long) value);
            } else {
                BytecodeNode.setLocalObject(refs, slotInfo.getSlot(), (StaticObject) value);
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

        private final int slot;
        private final Kind kind;

        private enum Kind {
            DOUBLE,
            FLOAT,
            INT,
            LONG,
            OTHER
        }

        FrameSlotInfo(int slot) {
            this(slot, Kind.OTHER);
        }

        FrameSlotInfo(int slot, Kind kind) {
            this.slot = slot;
            this.kind = kind;
        }

        public int getSlot() {
            return slot;
        }

        public Kind getKind() {
            return kind;
        }
    }
}
