/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.headers;

import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

//Checkstyle: stop

/**
 * Definitions manually translated from the C header file zlib.h.
 *
 * We only include this class in the JNI implementation in order to add -lz to the link line.
 */
@Platforms({Platform.DARWIN.class, Platform.LINUX.class})
@CContext(PosixDirectives.class)
@CLibrary("z")
public class ZLib {
    // extern uLong adler32 (uLong adler, const Bytef *buf, uInt len);
    @CFunction
    public static native UnsignedWord adler32(UnsignedWord adler, CCharPointer buf, int len);

    // extern uLong crc32 (uLong crc, const Bytef *buf, uInt len);
    @CFunction
    public static native UnsignedWord crc32(UnsignedWord crc, CCharPointer buf, int len);

    interface alloc_func extends CFunctionPointer {
        @InvokeCFunctionPointer
        WordPointer invoke(WordPointer opaque, int items, int size);
    }

    interface free_func extends CFunctionPointer {
        @InvokeCFunctionPointer
        void invoke(WordPointer opaque, WordPointer address);
    }

    @CStruct
    public interface z_stream extends PointerBase {
        @CField
        CCharPointer next_in();

        @CField
        void set_next_in(CCharPointer value);

        @CField
        int avail_in();

        @CField
        void set_avail_in(int value);

        @CField
        UnsignedWord total_in();

        @CField
        CCharPointer next_out();

        @CField
        void set_next_out(CCharPointer value);

        @CField
        int avail_out();

        @CField
        void set_avail_out(int value);

        @CField
        UnsignedWord total_out();

        @CField
        CCharPointer msg();

        @CField
        WordPointer state();

        @CField
        alloc_func zalloc();

        @CField
        free_func zfree();

        @CField
        WordPointer opaque();

        @CField
        int data_type();

        @CField
        UnsignedWord adler();

        @CField
        UnsignedWord reserved();
    }

    @CStruct
    public interface gz_header extends PointerBase {
        @CField
        int text();

        @CField
        UnsignedWord time();

        @CField
        int xflags();

        @CField
        int os();

        @CField
        CCharPointer extra();

        @CField
        int extra_len();

        @CField
        int extra_max();

        @CField
        CCharPointer name();

        @CField
        int name_max();

        @CField
        CCharPointer comment();

        @CField
        int comm_max();

        @CField
        int hcrc();

        @CField
        int done();
    }

    /*
     * Deflater definitions
     */

    @CConstant
    public static native byte[] ZLIB_VERSION();

    @CFunction
    static native int deflateInit2_(z_stream strm, int level, int method, int windowBits, int memLevel, int strategy, CCharPointer version, int stream_size);

    public static int deflateInit2(z_stream strm, int level, int method, int windowBits, int memLevel, int strategy) {
        try (PinnedObject zlib_version_byteArray = PinnedObject.create(ZLIB_VERSION())) {
            CCharPointer zlib_version = zlib_version_byteArray.addressOfArrayElement(0);
            return deflateInit2_(strm, level, method, windowBits, memLevel, strategy, zlib_version, SizeOf.get(z_stream.class));
        }
    }

    // extern int deflateSetDictionary (z_streamp strm, const Bytef *dictionary, uInt dictLength)
    @CFunction
    public static native int deflateSetDictionary(z_stream strm, CCharPointer dictionary, int dictLength);

    // extern int deflateReset (z_streamp strm);
    @CFunction
    public static native int deflateReset(z_stream strm);

    // extern int deflateEnd (z_streamp strm);
    @CFunction
    public static native int deflateEnd(z_stream strm);

    // extern int deflateParams (z_streamp strm, int level, int strategy)
    @CFunction
    public static native int deflateParams(z_stream strm, int level, int strategy);

    // extern int deflate (z_streamp strm, int flush);
    @CFunction
    public static native int deflate(z_stream strm, int flush);

    /*
     * Inflater definitions
     */

    // extern int inflateInit2_ (z_streamp strm, int windowBits, const char *version, int
    // stream_size)
    @CFunction
    static native int inflateInit2_(z_stream strm, int windowBits, CCharPointer version, int stream_size);

    public static int inflateInit2(z_stream strm, int windowBits) {
        try (PinnedObject zlib_version_byteArray = PinnedObject.create(ZLIB_VERSION())) {
            CCharPointer zlib_version = zlib_version_byteArray.addressOfArrayElement(0);
            return inflateInit2_(strm, windowBits, zlib_version, SizeOf.get(z_stream.class));
        }
    }

    // extern int inflateSetDictionary (z_streamp strm, const Bytef *dictionary, uInt dictLength)
    @CFunction
    public static native int inflateSetDictionary(z_stream strm, CCharPointer dictionary, int dictLength);

    // extern int inflateReset (z_streamp strm);
    @CFunction
    public static native int inflateReset(z_stream strm);

    // extern int inflateEnd (z_streamp strm);
    @CFunction
    public static native int inflateEnd(z_stream strm);

    // extern int inflate (z_streamp strm, int flush);
    @CFunction
    public static native int inflate(z_stream strm, int flush);

    /*
     * Utility Functions (compress/uncompress definitions)
     */

    // extern int compress (Bytef *dest, uLongf *destLen, const Bytef *source, uLong sourceLen)
    @CFunction
    static native int compress(CCharPointer dest, CLongPointer destLen, CCharPointer source, UnsignedWord sourceLen);

    // extern int compress2 (Bytef *dest, ..., uLong sourceLen, int level)
    @CFunction
    static native int compress2(CCharPointer dest, CLongPointer destLen, CCharPointer source, UnsignedWord sourceLen, int level);

    // extern uLong compressBound (uLong sourceLen)
    @CFunction
    static native UnsignedWord compressBound(UnsignedWord sourceLen);

    // extern int uncompress (Bytef *dest, uLongf *destLen, const Bytef *source, uLong sourceLen)
    @CFunction
    static native int uncompress(CCharPointer dest, CLongPointer destLen, CCharPointer source, UnsignedWord sourceLen);

    /*
     * zconf.h constants
     */

    @CConstant
    public static native int MAX_WBITS();

    /*
     * zlib.h constants
     */

    @CConstant
    public static native int Z_NO_FLUSH();

    @CConstant
    public static native int Z_PARTIAL_FLUSH();

    @CConstant
    public static native int Z_SYNC_FLUSH();

    @CConstant
    public static native int Z_FULL_FLUSH();

    @CConstant
    public static native int Z_FINISH();

    @CConstant
    public static native int Z_BLOCK();

    /*- Only available in 1.2.4 and higher
     * @CConstant
     * public static native int Z_TREES();
     */

    @CConstant
    public static native int Z_OK();

    @CConstant
    public static native int Z_STREAM_END();

    @CConstant
    public static native int Z_NEED_DICT();

    @CConstant
    public static native int Z_ERRNO();

    @CConstant
    public static native int Z_STREAM_ERROR();

    @CConstant
    public static native int Z_DATA_ERROR();

    @CConstant
    public static native int Z_MEM_ERROR();

    @CConstant
    public static native int Z_BUF_ERROR();

    @CConstant
    public static native int Z_VERSION_ERROR();

    @CConstant
    public static native int Z_NO_COMPRESSION();

    @CConstant
    public static native int Z_BEST_SPEED();

    @CConstant
    public static native int Z_BEST_COMPRESSION();

    @CConstant
    public static native int Z_DEFAULT_COMPRESSION();

    @CConstant
    public static native int Z_FILTERED();

    @CConstant
    public static native int Z_HUFFMAN_ONLY();

    @CConstant
    public static native int Z_RLE();

    @CConstant
    public static native int Z_FIXED();

    @CConstant
    public static native int Z_DEFAULT_STRATEGY();

    @CConstant
    public static native int Z_BINARY();

    @CConstant
    public static native int Z_TEXT();

    @CConstant
    public static native int Z_ASCII();

    @CConstant
    public static native int Z_UNKNOWN();

    @CConstant
    public static native int Z_DEFLATED();

    @CConstant
    public static native int Z_NULL();
}
