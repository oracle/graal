/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.oracle.svm.hosted.webimage.wasm.snippets;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;

import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;

import jdk.graal.compiler.word.Word;

/**
 * Implementation of atomic operations without atomicity.
 * <p>
 * In a single threaded environment, simply implementing the semantics without using atomic
 * operation is enough to guarantee atomicity (since there will not be any concurrent accesses).
 * <p>
 * TODO GR-42105 Once we have a 32-bit architecture, the addresses should be words.
 */
public class SingleThreadedAtomics {
    public static final SubstrateForeignCallDescriptor VALUE_COMPARE_AND_SWAP_BYTE = SnippetRuntime.findForeignCall(SingleThreadedAtomics.class, "byteCompareAndSwap", HAS_SIDE_EFFECT,
                    LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor LOGIC_COMPARE_AND_SWAP_BYTE = SnippetRuntime.findForeignCall(SingleThreadedAtomics.class, "logicByteCompareAndSwap", HAS_SIDE_EFFECT,
                    LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor VALUE_COMPARE_AND_SWAP_CHAR = SnippetRuntime.findForeignCall(SingleThreadedAtomics.class, "charCompareAndSwap", HAS_SIDE_EFFECT,
                    LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor LOGIC_COMPARE_AND_SWAP_CHAR = SnippetRuntime.findForeignCall(SingleThreadedAtomics.class, "logicCharCompareAndSwap", HAS_SIDE_EFFECT,
                    LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor VALUE_COMPARE_AND_SWAP_INT = SnippetRuntime.findForeignCall(SingleThreadedAtomics.class, "intCompareAndSwap", HAS_SIDE_EFFECT,
                    LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor LOGIC_COMPARE_AND_SWAP_INT = SnippetRuntime.findForeignCall(SingleThreadedAtomics.class, "logicIntCompareAndSwap", HAS_SIDE_EFFECT,
                    LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor VALUE_COMPARE_AND_SWAP_LONG = SnippetRuntime.findForeignCall(SingleThreadedAtomics.class, "longCompareAndSwap", HAS_SIDE_EFFECT,
                    LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor LOGIC_COMPARE_AND_SWAP_LONG = SnippetRuntime.findForeignCall(SingleThreadedAtomics.class, "logicLongCompareAndSwap", HAS_SIDE_EFFECT,
                    LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor VALUE_COMPARE_AND_SWAP_OBJECT = SnippetRuntime.findForeignCall(SingleThreadedAtomics.class, "objectCompareAndSwap", HAS_SIDE_EFFECT,
                    LocationIdentity.any());
    public static final SubstrateForeignCallDescriptor LOGIC_COMPARE_AND_SWAP_OBJECT = SnippetRuntime.findForeignCall(SingleThreadedAtomics.class, "logicObjectCompareAndSwap", HAS_SIDE_EFFECT,
                    LocationIdentity.any());

    public static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{
                    VALUE_COMPARE_AND_SWAP_BYTE, LOGIC_COMPARE_AND_SWAP_BYTE,
                    VALUE_COMPARE_AND_SWAP_CHAR, LOGIC_COMPARE_AND_SWAP_CHAR,
                    VALUE_COMPARE_AND_SWAP_INT, LOGIC_COMPARE_AND_SWAP_INT,
                    VALUE_COMPARE_AND_SWAP_LONG, LOGIC_COMPARE_AND_SWAP_LONG,
                    VALUE_COMPARE_AND_SWAP_OBJECT, LOGIC_COMPARE_AND_SWAP_OBJECT
    };

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static int intCompareAndSwap(int addressNum, int expectedValue, int newValue) {
        Word address = Word.unsigned(addressNum);
        int oldValue = address.readInt(0);
        if (oldValue == expectedValue) {
            address.writeInt(0, newValue);
        }
        return oldValue;
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static byte byteCompareAndSwap(int addressNum, byte expectedValue, byte newValue) {
        Word address = Word.unsigned(addressNum);
        byte oldValue = address.readByte(0);
        if (oldValue == expectedValue) {
            address.writeByte(0, newValue);
        }
        return oldValue;
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static boolean logicByteCompareAndSwap(int address, char expectedValue, char newValue) {
        return charCompareAndSwap(address, expectedValue, newValue) == expectedValue;
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static char charCompareAndSwap(int addressNum, char expectedValue, char newValue) {
        Word address = Word.unsigned(addressNum);
        char oldValue = address.readChar(0);
        if (oldValue == expectedValue) {
            address.writeChar(0, newValue);
        }
        return oldValue;
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static boolean logicCharCompareAndSwap(int address, byte expectedValue, byte newValue) {
        return byteCompareAndSwap(address, expectedValue, newValue) == expectedValue;
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static boolean logicIntCompareAndSwap(int address, int expectedValue, int newValue) {
        return intCompareAndSwap(address, expectedValue, newValue) == expectedValue;
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static long longCompareAndSwap(int addressNum, long expectedValue, long newValue) {
        Word address = Word.unsigned(addressNum);
        long oldValue = address.readLong(0);
        if (oldValue == expectedValue) {
            address.writeLong(0, newValue);
        }
        return oldValue;
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static boolean logicLongCompareAndSwap(int address, long expectedValue, long newValue) {
        return longCompareAndSwap(address, expectedValue, newValue) == expectedValue;
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static Object objectCompareAndSwap(int addressNum, Object expectedValue, Object newValue) {
        Word address = Word.unsigned(addressNum);
        Object oldValue = address.readObject(0);
        if (oldValue == expectedValue) {
            address.writeObject(0, newValue);
        }
        return oldValue;
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static boolean logicObjectCompareAndSwap(int address, Object expectedValue, Object newValue) {
        return objectCompareAndSwap(address, expectedValue, newValue) == expectedValue;
    }
}
