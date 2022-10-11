/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Set;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.espresso.classfile.attributes.Local;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.nodes.EspressoFrame;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class EspressoScope {

    public static Object createVariables(Local[] liveLocals, Frame frame, Symbol<Symbol.Name> scopeName) {
        int slotCount = liveLocals.length;
        Map<String, FrameSlotInfo> slotsMap;
        Map<String, FrameSlotInfo> identifiersMap;
        if (liveLocals.length == 0) {
            slotsMap = Collections.emptyMap();
            identifiersMap = Collections.emptyMap();
        } else if (liveLocals.length == 1) {
            int slot = 0;
            String identifier = "0";
            Local local = liveLocals[0];
            FrameSlotInfo frameSlotInfo = new FrameSlotInfo(slot, Types.getJavaKind(local.getType().value()));
            slotsMap = Collections.singletonMap(identifier, frameSlotInfo);
            identifiersMap = Collections.singletonMap(local.getNameAsString(), frameSlotInfo);
        } else {
            slotsMap = new LinkedHashMap<>(slotCount);
            identifiersMap = new LinkedHashMap<>(slotCount);
            for (Local local : liveLocals) {
                String slotNumber = String.valueOf(local.getSlot());
                String localName = local.getNameAsString();
                FrameSlotInfo frameSlotInfo = new FrameSlotInfo(local.getSlot(), Types.getJavaKind(local.getType().value()));
                slotsMap.put(slotNumber, frameSlotInfo);
                identifiersMap.put(localName, frameSlotInfo);
            }
        }
        return new VariablesMapObject(slotsMap, identifiersMap, frame, scopeName);
    }

    // We map both variable names and their slot number to members. However we only expose the
    // variable names through the Interop API. Clients which are bytecode based, e.g. JDWP that use
    // slot numbers as identifiers must operate directly by using read/write member methods.
    @ExportLibrary(InteropLibrary.class)
    static final class VariablesMapObject implements TruffleObject {

        final Map<String, FrameSlotInfo> slots;
        final Map<String, FrameSlotInfo> identifiers;
        final Frame frame;
        final Symbol<Symbol.Name> scopeName;

        private VariablesMapObject(Map<String, FrameSlotInfo> slots, Map<String, FrameSlotInfo> identifiers, Frame frame, Symbol<Symbol.Name> scopeName) {
            this.slots = slots;
            this.identifiers = identifiers;
            this.frame = frame;
            this.scopeName = scopeName;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isScope() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return EspressoLanguage.class;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return scopeName.toString();
        }

        @ExportMessage
        @TruffleBoundary
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

            // @formatter:off
            switch (slotInfo.getKind()) {
                case Boolean: return EspressoFrame.getLocalInt(frame, slotInfo.getSlot()) != 0;
                case Byte:    return (byte) EspressoFrame.getLocalInt(frame, slotInfo.getSlot());
                case Short:   return (short) EspressoFrame.getLocalInt(frame, slotInfo.getSlot());
                case Char:    return (char) EspressoFrame.getLocalInt(frame, slotInfo.getSlot());
                case Int:     return EspressoFrame.getLocalInt(frame, slotInfo.getSlot());
                case Float:   return EspressoFrame.getLocalFloat(frame, slotInfo.getSlot());
                case Long:    return EspressoFrame.getLocalLong(frame, slotInfo.getSlot());
                case Double:  return EspressoFrame.getLocalDouble(frame, slotInfo.getSlot());
                case Object:  return EspressoFrame.getLocalObject(frame, slotInfo.getSlot());
                default:
                    CompilerAsserts.neverPartOfCompilation();
                    throw EspressoError.shouldNotReachHere();
            }
            // @formatter:on
        }

        @ExportMessage
        @TruffleBoundary
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new EspressoScope.VariableNamesObject(identifiers.keySet());
        }

        @ExportMessage
        @TruffleBoundary
        boolean isMemberReadable(String member) {
            return slots.containsKey(member) || identifiers.containsKey(member);
        }

        @ExportMessage
        @TruffleBoundary
        boolean isMemberModifiable(String member) {
            return (slots.containsKey(member) || identifiers.containsKey(member)) && frame != null;
        }

        @ExportMessage(limit = "9")
        @TruffleBoundary
        void writeMember(String member, Object value, @CachedLibrary("value") InteropLibrary interop) throws UnknownIdentifierException, UnsupportedMessageException {
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

            // @formatter:off
            switch (slotInfo.getKind()) {
                case Boolean: EspressoFrame.setLocalInt(frame, slotInfo.getSlot(), interop.asBoolean(value) ? 1 : 0);  break;
                case Byte:    EspressoFrame.setLocalInt(frame, slotInfo.getSlot(), interop.asByte(value));             break;
                case Short:   EspressoFrame.setLocalInt(frame, slotInfo.getSlot(), interop.asShort(value));            break;
                case Char:    EspressoFrame.setLocalInt(frame, slotInfo.getSlot(), interop.asString(value).charAt(0)); break;
                case Int:     EspressoFrame.setLocalInt(frame, slotInfo.getSlot(), interop.asInt(value));              break;
                case Float:   EspressoFrame.setLocalFloat(frame, slotInfo.getSlot(), interop.asFloat(value));          break;
                case Long:    EspressoFrame.setLocalLong(frame, slotInfo.getSlot(), interop.asLong(value));            break;
                case Double:  EspressoFrame.setLocalDouble(frame, slotInfo.getSlot(), interop.asDouble(value));        break;
                case Object:  EspressoFrame.setLocalObject(frame, slotInfo.getSlot(), (StaticObject) value);                 break;
                default:
                    CompilerAsserts.neverPartOfCompilation();
                    throw EspressoError.shouldNotReachHere();
            }
            // @formatter:on
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

    private static class FrameSlotInfo {

        private final int slot;
        private final JavaKind kind;

        FrameSlotInfo(int slot, JavaKind kind) {
            this.slot = slot;
            this.kind = kind;
        }

        public int getSlot() {
            return slot;
        }

        public JavaKind getKind() {
            return kind;
        }
    }
}
