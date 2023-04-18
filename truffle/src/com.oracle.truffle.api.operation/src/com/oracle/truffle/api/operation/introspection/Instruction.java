/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.operation.introspection;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class Instruction {

    // [int bci, String name, short[] bytes, Object[][] arguments, Object[][] subinstructions?]
    private final Object[] data;

    Instruction(Object[] data) {
        this.data = data;
    }

    public int getBci() {
        return (int) data[0];
    }

    public String getName() {
        return (String) data[1];
    }

    public byte[] getBytes() {
        short[] shorts = (short[]) data[2];
        byte[] result = new byte[shorts.length * 2];
        for (int i = 0; i < shorts.length; i++) {
            result[2 * i] = (byte) (shorts[i] & 0xff);
            result[2 * i + 1] = (byte) ((shorts[i] >> 8) & 0xff);
        }

        return result;
    }

    public List<Argument> getArgumentValues() {
        if (data[3] == null) {
            return List.of();
        }
        return Arrays.stream((Object[]) data[3]).map(x -> new Argument((Object[]) x)).collect(Collectors.toUnmodifiableList());
    }

    public List<Instruction> getSubInstructions() {
        if (data.length >= 5) {
            return Arrays.stream((Object[]) data[4]).map(x -> new Instruction((Object[]) x)).collect(Collectors.toUnmodifiableList());
        } else {
            return List.of();
        }
    }

    private static final int REASONABLE_INSTRUCTION_LENGTH = 3;

    @Override
    public String toString() {
        return toString("");
    }

    private String toString(String prefix) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s[%04x] ", prefix, getBci()));

        byte[] bytes = getBytes();
        for (int i = 0; i < REASONABLE_INSTRUCTION_LENGTH; i++) {
            if (i < bytes.length) {
                sb.append(String.format("%02x ", bytes[i]));
            } else {
                sb.append("   ");
            }
        }

        for (int i = REASONABLE_INSTRUCTION_LENGTH; i < bytes.length; i++) {
            sb.append(String.format("%02x ", bytes[i]));
        }

        sb.append(String.format("%-20s", getName()));

        for (Argument a : getArgumentValues()) {
            sb.append(' ').append(a.toString());
        }

        for (Instruction instr : getSubInstructions()) {
            sb.append('\n').append(instr.toString(prefix + "      "));
        }

        return sb.toString();
    }
}
