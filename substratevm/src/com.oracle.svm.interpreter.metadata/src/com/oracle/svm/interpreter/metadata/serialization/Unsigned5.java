/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.metadata.serialization;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class Unsigned5 {
    // Math constants for the modified UNSIGNED5 coding of Pack200
    private static final int lg_H = 6;       // log-base-2 of H (lg 64 == 6)
    private static final int H = 1 << lg_H;  // number of "high" bytes (64)
    private static final int X = 1;          // there is one excluded byte ('\0')
    private static final int MAX_b = (1 << Byte.SIZE) - 1;  // largest byte value
    private static final int L = (MAX_b + 1) - X - H;

    private static final int MAX_LENGTH = 5;

    public static void writeUnsignedInt(DataOutput out, int value) throws IOException {
        int sum = value;
        for (int i = 0; i < MAX_LENGTH - 1 && Integer.compareUnsigned(sum, L) >= 0; ++i) {
            sum -= L;
            out.writeByte((byte) (X + L + (sum & 0x3F)));
            sum >>>= lg_H;
        }
        out.writeByte((byte) (X + sum));
    }

    public static int readUnsignedInt(DataInput in) throws IOException {
        int sum = 0;
        for (int i = 0; i < MAX_LENGTH; ++i) {
            int bi = in.readUnsignedByte();
            if (bi < X) {
                throw new IllegalStateException("Invalid byte");
            }
            sum += (bi - X) << (i * lg_H);
            if (bi < X + L) {
                break;
            }
        }
        return sum;
    }
}
