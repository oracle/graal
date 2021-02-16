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
package com.oracle.truffle.espresso.descriptors;

final class SymbolKey {
    private final int hash;
    // mutable
    protected ByteSequence seq;

    SymbolKey(ByteSequence seq) {
        this.seq = seq;
        this.hash = seq.hashCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        // Always a SymbolKey.
        SymbolKey that = (SymbolKey) other;
        ByteSequence thisSeq = seq;
        ByteSequence thatSeq = that.seq;
        if (thisSeq == thatSeq) {
            return true;
        }
        if (thisSeq.hashCode != thatSeq.hashCode) {
            return false;
        }
        int len = thisSeq.length();
        if (len != thatSeq.length()) {
            return false;
        }
        byte[] thisBytes = thisSeq.getUnderlyingBytes();
        byte[] thatBytes = thatSeq.getUnderlyingBytes();
        int thisOffset = thisSeq.offset();
        int thatOffset = thatSeq.offset();
        for (int i = 0; i < len; i++) {
            if (thisBytes[i + thisOffset] != thatBytes[i + thatOffset]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
