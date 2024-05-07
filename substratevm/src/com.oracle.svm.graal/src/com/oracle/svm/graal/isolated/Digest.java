/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.isolated;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.debug.GraalError;

/**
 * Utility class to digest a String or a byte[] array using a non-cryptographic hash algorithm.
 * <p>
 * By default, the hash is encoded using only the 10 + 26 + 26 = 62 ascii number, uppercase letter,
 * and lowercase letter characters. We do not use a standard Base64 encoding because Base64 needs 2
 * special characters in addition to numbers and letters, and there are no such characters that work
 * universally everywhere (Java names, symbol names, ...). Hex encoding and {@link UUID} versions
 * are offered too.
 * <p>
 * The current implementation uses the 128-bit Murmur3 hash algorithm, but users of this class
 * should not assume that the hash algorithm remains stable.
 */
public final class Digest {

    private record LongLong(long l1, long l2) {
    }

    private static final byte[] DIGITS = {
                    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
                    'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
                    'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd',
                    'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
                    'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x',
                    'y', 'z',
    };

    /* Every long value can be encoded with 11 characters, because 2^64 < 62^11. */
    private static final int BASE62_DIGITS_PER_LONG = 11;

    public static final int DIGEST_SIZE = BASE62_DIGITS_PER_LONG * 2;

    /**
     * We do not need any special hash seed. In particular, we do not want to use a random seed
     * because the digest strings must be deterministic.
     */
    private static final long HASH_SEED = 0;

    private Digest() {
    }

    /**
     * Hashes the passed string parameter and returns the encoding of the hash.
     */
    public static String digest(String value) {
        return digest(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Hashes the passed byte array parameter and returns the encoding of the hash.
     */
    public static String digest(byte[] bytes) {
        return digest(bytes, 0, bytes.length);
    }

    /**
     * Hashes the passed range of the byte array parameter and returns the encoding of the hash.
     */
    public static String digest(byte[] bytes, int offset, int length) {
        String result = new String(digestAsByteArray(bytes, offset, length), StandardCharsets.UTF_8);
        assert result.length() == DIGEST_SIZE : "--" + result + "--";
        return result.toString();
    }

    /**
     * Hashes the passed range of the byte array parameter and returns the encoding of the hash as a
     * new byte array.
     */
    public static byte[] digestAsByteArray(byte[] bytes, int offset, int length) {
        LongLong hash = MurmurHash3_x64_128(bytes, offset, length, HASH_SEED);

        byte[] array = new byte[DIGEST_SIZE];
        encodeBase62(hash.l1, array, 0);
        encodeBase62(hash.l2, array, BASE62_DIGITS_PER_LONG);
        return array;
    }

    private static void encodeBase62(long value, byte[] result, int resultIndex) {
        long cur = value;
        int base = DIGITS.length;
        for (int i = 0; i < BASE62_DIGITS_PER_LONG; i++) {
            result[resultIndex + i] = DIGITS[NumUtil.safeToInt(Long.remainderUnsigned(cur, base))];
            cur = Long.divideUnsigned(cur, base);
        }
        GraalError.guarantee(cur == 0, "Too few loop iterations processing digits");
    }

    public static String digestAsHex(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        LongLong hash = MurmurHash3_x64_128(bytes, 0, bytes.length, HASH_SEED);
        return Long.toHexString(hash.l1) + Long.toHexString(hash.l2);
    }

    public static UUID digestAsUUID(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        LongLong hash = MurmurHash3_x64_128(bytes, 0, bytes.length, HASH_SEED);
        return new UUID(hash.l1, hash.l2);
    }

    /*-
     * The hash implementation is a straightforward port from C to Java of
     * https://github.com/aappleby/smhasher/blob/61a0530f28277f2e850bfc39600ce61d02b518de/src/MurmurHash3.cpp#L255
     *
     * All variables in the C code are unsigned. But since there is no difference between signed and
     * unsigned arithmetic for *, +, and ^ there is no need for special unsigned handling in Java.
     * Right-shifts are >>> so that they are unsigned. The byte-sized loads in the switch need to be
     * zero-extended from byte to long.
     */

    // Checkstyle: stop

    // -----------------------------------------------------------------------------
    // MurmurHash3 was written by Austin Appleby, and is placed in the public
    // domain. The author hereby disclaims copyright to this source code.

    private static final VarHandle LONG_VIEW = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.nativeOrder());

    @SuppressWarnings("fallthrough")
    private static LongLong MurmurHash3_x64_128(byte[] bytes, int offset, int len, long seed) {
        int nblocks = len / 16;

        long h1 = seed;
        long h2 = seed;

        long c1 = 0x87c37b91114253d5L;
        long c2 = 0x4cf5ad432745937fL;

        // ----------
        // body

        for (int i = 0; i < nblocks; i++) {
            long k1 = (long) LONG_VIEW.get(bytes, offset + (i * 2 + 0) * 8);
            long k2 = (long) LONG_VIEW.get(bytes, offset + (i * 2 + 1) * 8);

            k1 *= c1;
            k1 = Long.rotateLeft(k1, 31);
            k1 *= c2;
            h1 ^= k1;

            h1 = Long.rotateLeft(h1, 27);
            h1 += h2;
            h1 = h1 * 5 + 0x52dce729L;

            k2 *= c2;
            k2 = Long.rotateLeft(k2, 33);
            k2 *= c1;
            h2 ^= k2;

            h2 = Long.rotateLeft(h2, 31);
            h2 += h1;
            h2 = h2 * 5 + 0x38495ab5L;
        }

        // ----------
        // tail

        int tail = offset + nblocks * 16;

        long k1 = 0;
        long k2 = 0;

        /* Intentional fall-through of all case labels down to case 0. */
        switch (len & 15) {
            case 15:
                k2 ^= (bytes[tail + 14] & 0xffL) << 48;
            case 14:
                k2 ^= (bytes[tail + 13] & 0xffL) << 40;
            case 13:
                k2 ^= (bytes[tail + 12] & 0xffL) << 32;
            case 12:
                k2 ^= (bytes[tail + 11] & 0xffL) << 24;
            case 11:
                k2 ^= (bytes[tail + 10] & 0xffL) << 16;
            case 10:
                k2 ^= (bytes[tail + 9] & 0xffL) << 8;
            case 9:
                k2 ^= (bytes[tail + 8] & 0xffL) << 0;
                k2 *= c2;
                k2 = Long.rotateLeft(k2, 33);
                k2 *= c1;
                h2 ^= k2;

            case 8:
                k1 ^= (bytes[tail + 7] & 0xffL) << 56;
            case 7:
                k1 ^= (bytes[tail + 6] & 0xffL) << 48;
            case 6:
                k1 ^= (bytes[tail + 5] & 0xffL) << 40;
            case 5:
                k1 ^= (bytes[tail + 4] & 0xffL) << 32;
            case 4:
                k1 ^= (bytes[tail + 3] & 0xffL) << 24;
            case 3:
                k1 ^= (bytes[tail + 2] & 0xffL) << 16;
            case 2:
                k1 ^= (bytes[tail + 1] & 0xffL) << 8;
            case 1:
                k1 ^= (bytes[tail + 0] & 0xffL) << 0;
                k1 *= c1;
                k1 = Long.rotateLeft(k1, 31);
                k1 *= c2;
                h1 ^= k1;

            case 0:
                break;
            default:
                throw GraalError.shouldNotReachHere("All 16-byte blocks are processed in loop above");
        }

        // ----------
        // finalization

        h1 ^= len;
        h2 ^= len;

        h1 += h2;
        h2 += h1;

        h1 = fmix64(h1);
        h2 = fmix64(h2);

        h1 += h2;
        h2 += h1;

        return new LongLong(h1, h2);
    }

    private static long fmix64(long input) {
        long k = input;
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;

        return k;
    }
}
