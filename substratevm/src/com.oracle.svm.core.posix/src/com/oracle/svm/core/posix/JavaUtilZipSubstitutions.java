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
package com.oracle.svm.core.posix;

import java.util.zip.DataFormatException;

import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posix.headers.ZLib;
import com.oracle.svm.core.posix.headers.ZLib.z_stream;
import com.oracle.svm.core.snippets.KnownIntrinsics;

@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
@TargetClass(java.util.zip.Adler32.class)
final class Target_java_util_zip_Adler32 {
    // Adler32.c-Java_java_util_zip_Adler32_update(JNIEnv *env, jclass cls, jint adler, jint b)
    @Substitute
    private static int update(int adler, int b) {
        // Only the low eight bits of the argument b should be used
        CCharPointer bytes = StackValue.get(CCharPointer.class);
        bytes.write((byte) b);
        return (int) ZLib.adler32(WordFactory.unsigned(adler), bytes, 1).rawValue();
    }

    // Adler32.c-Java_java_util_zip_Adler32_updateBytes(JNIEnv *env, jclass cls, jint adler,
    @Substitute
    private static int updateBytes(int adler, byte[] b, int off, int len) {
        try (PinnedObject pinned = PinnedObject.create(b)) {
            CCharPointer bytes = pinned.addressOfArrayElement(off);
            return (int) ZLib.adler32(WordFactory.unsigned(adler), bytes, len).rawValue();
        }
    }

    // Adler32.c-Java_java_util_zip_Adler32_updateByteBuffer(JNIEnv *env, jclass cls, jint adler,
    @Substitute
    private static int updateByteBuffer(int adler, long addr, int off, int len) {
        CCharPointer bytes = WordFactory.pointer(addr);
        CCharPointer bytesAtOffset = bytes.addressOf(off);
        return (int) ZLib.adler32(WordFactory.unsigned(adler), bytesAtOffset, len).rawValue();
    }
}

@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
@TargetClass(java.util.zip.CRC32.class)
final class Target_java_util_zip_CRC32 {
    // CRC32.c-Java_java_util_zip_CRC32_update(JNIEnv *env, jclass cls, jint crc, jint b)
    @Substitute
    private static int update(int crc, int b) {
        // Only the low eight bits of the argument b should be used
        CCharPointer bytes = StackValue.get(CCharPointer.class);
        bytes.write((byte) b);
        return (int) ZLib.crc32(WordFactory.unsigned(crc), bytes, 1).rawValue();
    }

    // CRC32.c-Java_java_util_zip_CRC32_updateBytes(JNIEnv *env, jclass cls, jint crc,
    @Substitute
    private static int updateBytes(int crc, byte[] b, int off, int len) {
        try (PinnedObject pinned = PinnedObject.create(b)) {
            CCharPointer bytes = pinned.addressOfArrayElement(off);
            return (int) ZLib.crc32(WordFactory.unsigned(crc), bytes, len).rawValue();
        }
    }

    // CRC32.c-Java_java_util_zip_CRC32_updateByteBuffer(JNIEnv *env, jclass cls, jint crc,
    @Substitute
    private static int updateByteBuffer(int crc, long addr, int off, int len) {
        CCharPointer bytes = WordFactory.pointer(addr);
        CCharPointer bytesAtOffset = bytes.addressOf(off);
        return (int) ZLib.crc32(WordFactory.unsigned(crc), bytesAtOffset, len).rawValue();
    }
}

@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
@TargetClass(java.util.zip.Deflater.class)
final class Target_java_util_zip_Deflater {

    @Alias private byte[] buf;
    @Alias int off;
    @Alias int len;
    @Alias private int level;
    @Alias private int strategy;
    @Alias private boolean setParams;
    @Alias private boolean finish;
    @Alias private boolean finished;

    // Deflater.c-Java_java_util_zip_Deflater_init(JNIEnv *env, jclass cls, jint level,
    @Substitute
    private static long init(int level, int strategy, boolean nowrap) {
        z_stream strm = LibC.calloc(WordFactory.unsigned(1), SizeOf.unsigned(z_stream.class));

        if (strm.isNull()) {
            throw new OutOfMemoryError();
        } else {
            int ret = ZLib.deflateInit2(strm, level, ZLib.Z_DEFLATED(), nowrap ? -ZLib.MAX_WBITS() : ZLib.MAX_WBITS(), Util_java_util_zip_Deflater.DEF_MEM_LEVEL, strategy);

            if (ret == ZLib.Z_OK()) {
                return strm.rawValue();
            } else {
                LibC.free(strm);
                if (ret == ZLib.Z_MEM_ERROR()) {
                    throw new OutOfMemoryError();
                } else if (ret == ZLib.Z_STREAM_ERROR()) {
                    throw new IllegalArgumentException();
                } else {
                    throw new InternalError();
                }
            }
        }
    }

    // Deflater.c-Java_java_util_zip_Deflater_setDictionary(JNIEnv *env, jclass cls, jlong addr,
    @Substitute
    private static void setDictionary(long addr, byte[] b, int off, int len) {
        try (PinnedObject pinned = PinnedObject.create(b)) {
            CCharPointer bufPlusOff = pinned.addressOfArrayElement(off);
            int res = ZLib.deflateSetDictionary(WordFactory.pointer(addr), bufPlusOff, len);
            if (res == ZLib.Z_OK()) {
                return;
            } else if (res == ZLib.Z_STREAM_ERROR()) {
                throw new IllegalArgumentException();
            } else {
                throw new InternalError();
            }
        }
    }

    // Deflater.c-Java_java_util_zip_Deflater_deflateBytes(JNIEnv *env, jobject this, jlong addr,
    @SuppressWarnings("hiding")
    @Substitute
    private int deflateBytes(long addr, byte[] b, int off, int len, int flush) {
        z_stream strm = WordFactory.pointer(addr);

        try (PinnedObject pinnedInBuf = PinnedObject.create(this.buf); PinnedObject pinnedOutBuf = PinnedObject.create(b)) {
            strm.set_next_in(pinnedInBuf.addressOfArrayElement(this.off));
            strm.set_next_out(pinnedOutBuf.addressOfArrayElement(off));
            strm.set_avail_in(this.len);
            strm.set_avail_out(len);

            if (this.setParams) {
                int res = ZLib.deflateParams(strm, level, strategy);
                this.setParams = false;
                if (res == ZLib.Z_OK()) {
                    return Util_java_util_zip_Deflater.update(this, len, strm);
                } else if (res == ZLib.Z_BUF_ERROR()) {
                    return 0;
                } else {
                    throw new InternalError();
                }
            } else {
                int res = ZLib.deflate(strm, this.finish ? ZLib.Z_FINISH() : flush);
                if (res == ZLib.Z_STREAM_END()) {
                    this.finished = true;
                    return Util_java_util_zip_Deflater.update(this, len, strm);
                } else if (res == ZLib.Z_OK()) {
                    return Util_java_util_zip_Deflater.update(this, len, strm);
                } else if (res == ZLib.Z_BUF_ERROR()) {
                    return 0;
                } else {
                    throw new InternalError();
                }
            }
        }
    }

    // Deflater.c-Java_java_util_zip_Deflater_getAdler(JNIEnv *env, jclass cls, jlong addr)
    @Substitute
    private static int getAdler(long addr) {
        z_stream strm = WordFactory.pointer(addr);
        return (int) strm.adler().rawValue();
    }

    // Deflater.c-Java_java_util_zip_Deflater_reset(JNIEnv *env, jclass cls, jlong addr)
    @Substitute
    private static void reset(long addr) {
        if (ZLib.deflateReset(WordFactory.pointer(addr)) != ZLib.Z_OK()) {
            throw new InternalError();
        }
    }

    // Deflater.c-Java_java_util_zip_Deflater_end(JNIEnv *env, jclass cls, jlong addr)
    @Substitute
    private static void end(long addr) {
        z_stream strm = WordFactory.pointer(addr);
        if (ZLib.deflateEnd(strm) == ZLib.Z_STREAM_ERROR()) {
            throw new InternalError();
        } else {
            LibC.free(strm);
        }
    }
}

final class Util_java_util_zip_Deflater {
    // Checkstyle: stop
    static int DEF_MEM_LEVEL = 8;
    // Checkstyle: resume

    static int update(Object obj, int len, z_stream strm) {
        Target_java_util_zip_Deflater instance = KnownIntrinsics.unsafeCast(obj, Target_java_util_zip_Deflater.class);
        instance.off += instance.len - strm.avail_in();
        instance.len = strm.avail_in();
        return len - strm.avail_out();
    }
}

@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
@TargetClass(java.util.zip.Inflater.class)
final class Target_java_util_zip_Inflater {
    @Alias private byte[] buf;
    @Alias int off;
    @Alias int len;
    @Alias private boolean finished;
    @Alias private boolean needDict;

    // Inflater.c-Java_java_util_zip_Inflater_init(JNIEnv *env, jclass cls, jboolean nowrap)
    @Substitute
    private static long init(boolean nowrap) {
        z_stream strm = LibC.calloc(WordFactory.unsigned(1), SizeOf.unsigned(z_stream.class));

        if (strm.isNull()) {
            throw new OutOfMemoryError();
        } else {
            int ret = ZLib.inflateInit2(strm, nowrap ? -ZLib.MAX_WBITS() : ZLib.MAX_WBITS());

            if (ret == ZLib.Z_OK()) {
                return strm.rawValue();
            } else {
                LibC.free(strm);
                if (ret == ZLib.Z_MEM_ERROR()) {
                    throw new OutOfMemoryError();
                } else {
                    throw new InternalError();
                }
            }
        }
    }

    // Inflater.c-Java_java_util_zip_Inflater_setDictionary(JNIEnv *env, jclass cls, jlong addr,
    @Substitute
    private static void setDictionary(long addr, byte[] b, int off, int len) {
        try (PinnedObject pinned = PinnedObject.create(b)) {
            CCharPointer bufPlusOff = pinned.addressOfArrayElement(off);
            int res = ZLib.inflateSetDictionary(WordFactory.pointer(addr), bufPlusOff, len);
            if (res == ZLib.Z_OK()) {
                return;
            } else if (res == ZLib.Z_STREAM_ERROR() || res == ZLib.Z_DATA_ERROR()) {
                throw new IllegalArgumentException();
            } else {
                throw new InternalError();
            }
        }
    }

    // Inflater.c-Java_java_util_zip_Inflater_inflateBytes(JNIEnv *env, jobject this, jlong addr,
    @SuppressWarnings("hiding")
    @Substitute
    private int inflateBytes(long addr, byte[] b, int off, int len) throws DataFormatException {
        z_stream strm = WordFactory.pointer(addr);

        try (PinnedObject pinnedInBuf = PinnedObject.create(this.buf); PinnedObject pinnedOutBuf = PinnedObject.create(b)) {
            strm.set_next_in(pinnedInBuf.addressOfArrayElement(this.off));
            strm.set_next_out(pinnedOutBuf.addressOfArrayElement(off));

            strm.set_avail_in(this.len);
            strm.set_avail_out(len);

            int ret = ZLib.inflate(strm, ZLib.Z_PARTIAL_FLUSH());
            if (ret == ZLib.Z_STREAM_END()) {
                this.finished = true;
                return Util_java_util_zip_Inflater.update(this, len, strm);
            } else if (ret == ZLib.Z_OK()) {
                return Util_java_util_zip_Inflater.update(this, len, strm);
            } else if (ret == ZLib.Z_NEED_DICT()) {
                needDict = true;
                Util_java_util_zip_Inflater.update(this, len, strm);
                return 0;
            } else if (ret == ZLib.Z_BUF_ERROR()) {
                return 0;
            } else if (ret == ZLib.Z_DATA_ERROR()) {
                throw new DataFormatException(CTypeConversion.toJavaString(strm.msg()));
            } else if (ret == ZLib.Z_MEM_ERROR()) {
                throw new OutOfMemoryError();
            } else {
                throw new InternalError();
            }
        }
    }

    // Inflater.c-Java_java_util_zip_Inflater_getAdler(JNIEnv *env, jclass cls, jlong addr)
    @Substitute
    private static int getAdler(long addr) {
        z_stream strm = WordFactory.pointer(addr);
        return (int) strm.adler().rawValue();
    }

    // Inflater.c-Java_java_util_zip_Inflater_reset(JNIEnv *env, jclass cls, jlong addr)
    @Substitute
    private static void reset(long addr) {
        if (ZLib.inflateReset(WordFactory.pointer(addr)) != ZLib.Z_OK()) {
            throw new InternalError();
        }
    }

    // Inflater.c-Java_java_util_zip_Inflater_end(JNIEnv *env, jclass cls, jlong addr)
    @Substitute
    private static void end(long addr) {
        z_stream strm = WordFactory.pointer(addr);
        if (ZLib.inflateEnd(strm) == ZLib.Z_STREAM_ERROR()) {
            throw new InternalError();
        } else {
            LibC.free(strm);
        }
    }
}

final class Util_java_util_zip_Inflater {
    static int update(Object obj, int len, z_stream strm) {
        Target_java_util_zip_Inflater instance = KnownIntrinsics.unsafeCast(obj, Target_java_util_zip_Inflater.class);
        instance.off += instance.len - strm.avail_in();
        instance.len = strm.avail_in();
        return len - strm.avail_out();
    }

}

/** Dummy class to have a class with the file's name. */
public final class JavaUtilZipSubstitutions {
}
