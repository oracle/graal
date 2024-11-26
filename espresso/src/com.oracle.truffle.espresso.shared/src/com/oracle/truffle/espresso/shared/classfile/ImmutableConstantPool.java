/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.shared.classfile;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.shared.constantpool.ClassConstant;
import com.oracle.truffle.espresso.shared.constantpool.PoolConstant;
import com.oracle.truffle.espresso.shared.constantpool.Utf8Constant;
import com.oracle.truffle.espresso.shared.descriptors.Symbol;

/**
 * Immutable constant pool implementation backed by an array of constants.
 */
public final class ImmutableConstantPool extends ConstantPool {

    private final int majorVersion;
    private final int minorVersion;

    @CompilationFinal(dimensions = 1) //
    private final PoolConstant[] constants;

    private final int totalPoolBytes;

    ImmutableConstantPool(PoolConstant[] constants, int majorVersion, int minorVersion, int totalPoolBytes) {
        this.constants = Objects.requireNonNull(constants);
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        this.totalPoolBytes = totalPoolBytes;
    }

    @Override
    public int length() {
        return constants.length;
    }

    @Override
    public byte[] getRawBytes() {
        byte[] bytes = new byte[totalPoolBytes];
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        for (PoolConstant pc : constants) {
            pc.dumpBytes(bb);
        }
        return bytes;
    }

    @Override
    @TruffleBoundary
    public RuntimeException classFormatError(String message) {
        throw new ParserException.ClassFormatError(message);
    }

    @TruffleBoundary
    public RuntimeException classFormatError(int index, String description) {
        throw classFormatError("Constant pool index (" + index + ")" + (description == null ? "" : " for " + description) + " is out of range");
    }

    @Override
    public PoolConstant at(int index, String description) {
        try {
            return constants[index];
        } catch (IndexOutOfBoundsException exception) {
            throw classFormatError(index, description);
        }
    }

    @Override
    public int getMajorVersion() {
        return majorVersion;
    }

    @Override
    public int getMinorVersion() {
        return minorVersion;
    }

    ImmutableConstantPool patchForHiddenClass(int thisKlassIndex, Symbol<?> newName) {
        int newNamePos = constants.length;
        Utf8Constant newNameConstant = new Utf8Constant(newName);

        PoolConstant[] newEntries = Arrays.copyOf(constants, constants.length + 1);
        newEntries[newNamePos] = newNameConstant;
        newEntries[thisKlassIndex] = ClassConstant.create(newNamePos);
        // This will get resolved in the ObjectKlass constructor
        // See initSelfReferenceInPool

        int rawLengthIncrease = 2 /* u2 length */ + newName.length() /* symbol length */;
        return new ImmutableConstantPool(newEntries, majorVersion, minorVersion, totalPoolBytes + rawLengthIncrease);
    }
}
