/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;

import java.util.zip.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.replacements.Snippet.Fold;
import com.oracle.graal.word.*;

/**
 * Substitutions for {@link CRC32}.
 */
@ClassSubstitution(value = CRC32.class, defaultGuard = CRC32Substitutions.Guard.class)
public class CRC32Substitutions {

    public static class Guard implements SubstitutionGuard {
        public boolean execute() {
            return runtime().getConfig().useCRC32Intrinsics;
        }
    }

    /**
     * Gets the address of {@code StubRoutines::x86::_crc_table} in {@code stubRoutines_x86.hpp}.
     */
    @Fold
    private static long crcTableAddress() {
        return runtime().getConfig().crcTableAddress;
    }

    @MethodSubstitution(isStatic = true)
    static int update(int crc, int b) {
        int c = ~crc;
        int index = (b ^ c) & 0xFF;
        int offset = index << 2;
        int result = Word.unsigned(crcTableAddress()).readInt(offset);
        result = result ^ (c >>> 8);
        return ~result;
    }

    @MethodSubstitution(isStatic = true)
    static int updateBytes(int crc, byte[] buf, int off, int len) {
        Word bufAddr = Word.unsigned(GetObjectAddressNode.get(buf) + arrayBaseOffset(Kind.Byte) + off);
        return updateBytes(UPDATE_BYTES_CRC32, crc, bufAddr, len);
    }

    @MethodSubstitution(isStatic = true, optional = true)
    static int updateByteBuffer(int crc, long addr, int off, int len) {
        Word bufAddr = Word.unsigned(addr).add(off);
        return updateBytes(UPDATE_BYTES_CRC32, crc, bufAddr, len);
    }

    public static final ForeignCallDescriptor UPDATE_BYTES_CRC32 = new ForeignCallDescriptor("updateBytesCRC32", int.class, int.class, Word.class, int.class);

    @NodeIntrinsic(ForeignCallNode.class)
    public static native int updateBytes(@ConstantNodeParameter ForeignCallDescriptor descriptor, int crc, Word buf, int length);
}
