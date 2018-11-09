/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jni;

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.meta.EspressoError;

class VarArgsImpl implements VarArgs {

    private static final TruffleObject popBoolean;
    private static final TruffleObject popByte;
    private static final TruffleObject popChar;
    private static final TruffleObject popShort;
    private static final TruffleObject popInt;
    private static final TruffleObject popFloat;
    private static final TruffleObject popDouble;
    private static final TruffleObject popLong;
    private static final TruffleObject popObject;

    static {
        try {
            popBoolean = NativeLibrary.lookupAndBind(JniEnv.nespressoLibrary, "popBoolean", "(sint64): uint8");
            popByte = NativeLibrary.lookupAndBind(JniEnv.nespressoLibrary, "popByte", "(sint64): uint8");
            popChar = NativeLibrary.lookupAndBind(JniEnv.nespressoLibrary, "popChar", "(sint64): uint16");
            popShort = NativeLibrary.lookupAndBind(JniEnv.nespressoLibrary, "popShort", "(sint64): sint16");
            popInt = NativeLibrary.lookupAndBind(JniEnv.nespressoLibrary, "popInt", "(sint64): sint32");
            popFloat = NativeLibrary.lookupAndBind(JniEnv.nespressoLibrary, "popFloat", "(sint64): float");
            popDouble = NativeLibrary.lookupAndBind(JniEnv.nespressoLibrary, "popDouble", "(sint64): double");
            popLong = NativeLibrary.lookupAndBind(JniEnv.nespressoLibrary, "popLong", "(sint64): sint64");
            popObject = NativeLibrary.lookupAndBind(JniEnv.nespressoLibrary, "popObject", "(sint64): object");
        } catch (UnknownIdentifierException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }
    @Node.Child static Node execute = Message.EXECUTE.createNode();

    private final long nativePointer;

    public VarArgsImpl(long nativePointer) {
        this.nativePointer = nativePointer;
    }

    static boolean popBoolean(long nativePointer) {
        try {
            return ((byte) ForeignAccess.sendExecute(execute, popBoolean, nativePointer)) != 0;
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new RuntimeException(e);
        }
    }

    static byte popByte(long nativePointer) {
        try {
            return (byte) ForeignAccess.sendExecute(execute, popByte, nativePointer);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new RuntimeException(e);
        }
    }

    static char popChar(long nativePointer) {
        try {
            return (char) ForeignAccess.sendExecute(execute, popChar, nativePointer);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new RuntimeException(e);
        }
    }

    static short popShort(long nativePointer) {
        try {
            return (short) ForeignAccess.sendExecute(execute, popShort, nativePointer);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new RuntimeException(e);
        }
    }

    static int popInt(long nativePointer) {
        try {
            return (int) ForeignAccess.sendExecute(execute, popInt, nativePointer);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new RuntimeException(e);
        }
    }

    static float popFloat(long nativePointer) {
        try {
            return (float) ForeignAccess.sendExecute(execute, popFloat, nativePointer);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new RuntimeException(e);
        }
    }

    static double popDouble(long nativePointer) {
        try {
            return (Double) ForeignAccess.sendExecute(execute, popDouble, nativePointer);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new RuntimeException(e);
        }
    }

    static long popLong(long nativePointer) {
        try {
            return (long) ForeignAccess.sendExecute(execute, popLong, nativePointer);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new RuntimeException(e);
        }
    }

    static Object popObject(long nativePointer) {
        try {
            return ForeignAccess.sendExecute(execute, popObject, nativePointer);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean popBoolean() {
        return popBoolean(nativePointer);
    }

    @Override
    public byte popByte() {
        return popByte(nativePointer);
    }

    @Override
    public char popChar() {
        return popChar(nativePointer);
    }

    @Override
    public short popShort() {
        return popShort(nativePointer);
    }

    @Override
    public int popInt() {
        return popInt(nativePointer);
    }

    @Override
    public float popFloat() {
        return popFloat(nativePointer);
    }

    @Override
    public double popDouble() {
        return popDouble(nativePointer);
    }

    @Override
    public long popLong() {
        return popLong(nativePointer);
    }

    @Override
    public Object popObject() {
        return popObject(nativePointer);
    }
}
