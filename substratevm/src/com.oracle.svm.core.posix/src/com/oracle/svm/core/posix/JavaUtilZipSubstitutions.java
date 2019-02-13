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
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK10OrEarlier;
import com.oracle.svm.core.jdk.JDK11OrLater;
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
@TargetClass(value = java.util.zip.Deflater.class)
final class Target_java_util_zip_Deflater {
    @Alias //
    @TargetElement(onlyWith = JDK10OrEarlier.class) //
    private byte[] buf;

    @Alias //
    @TargetElement(onlyWith = JDK10OrEarlier.class) //
    int off;

    @Alias //
    @TargetElement(onlyWith = JDK10OrEarlier.class) //
    int len;

    @Alias //
    private int level;

    @Alias //
    private int strategy;

    @Alias //
    private boolean setParams;

    @Alias //
    private boolean finish;

    @Alias //
    private boolean finished;

    // (JDK 11)src/java.base/share/native/libzip/Deflater.c
    // @formatter:off
    // Java_java_util_zip_Deflater_init(JNIEnv *env, jclass cls, jint level,
    //                                  jint strategy, jboolean nowrap)
    // @formatter:on
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
                    throw new IllegalArgumentException(CTypeConversion.toJavaString(strm.msg()));
                } else {
                    throw new InternalError(CTypeConversion.toJavaString(strm.msg()));
                }
            }
        }
    }

    // (JDK 11)src/java.base/share/native/libzip/Deflater.c
    // @formatter:off
    // Java_java_util_zip_Deflater_setDictionary(JNIEnv *env, jclass cls, jlong addr,
    //                                           jbyteArray b, jint off, jint len)
    // @formatter:on
    @Substitute
    private static void setDictionary(long addr, byte[] b, int off, int len) {
        try (PinnedObject pinned = PinnedObject.create(b)) {
            Util_java_util_zip_Deflater.doSetDictionary(addr, pinned.addressOfArrayElement(off), len);
        }
    }

    // (JDK 11)src/java.base/share/native/libzip/Deflater.c
    // @formatter:off
    // Java_java_util_zip_Deflater_setDictionaryBuffer(JNIEnv *env, jclass cls, jlong addr,
    //                                                 jlong bufferAddr, jint len)
    // @formatter:on
    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    private static void setDictionaryBuffer(long addr, long bufAddress, int len) {
        Util_java_util_zip_Deflater.doSetDictionary(addr, WordFactory.pointer(bufAddress), len);
    }

    // (JDK 8)jdk/src/share/native/java/util/zip/Deflater.c
    // @formatter:off
    // Java_java_util_zip_Deflater_deflateBytes(JNIEnv *env, jobject this, jlong addr,
    //                                          jarray b, jint off, jint len, jint flush)
    // @formatter:on
    @SuppressWarnings("hiding")
    @Substitute
    @TargetElement(onlyWith = JDK10OrEarlier.class)
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
                    return Util_java_util_zip_Deflater_JDK10OrEarlier.update(this, len, strm);
                } else if (res == ZLib.Z_BUF_ERROR()) {
                    return 0;
                } else {
                    throw new InternalError(CTypeConversion.toJavaString(strm.msg()));
                }
            } else {
                int res = ZLib.deflate(strm, this.finish ? ZLib.Z_FINISH() : flush);
                if (res == ZLib.Z_STREAM_END()) {
                    this.finished = true;
                    return Util_java_util_zip_Deflater_JDK10OrEarlier.update(this, len, strm);
                } else if (res == ZLib.Z_OK()) {
                    return Util_java_util_zip_Deflater_JDK10OrEarlier.update(this, len, strm);
                } else if (res == ZLib.Z_BUF_ERROR()) {
                    return 0;
                } else {
                    throw new InternalError(CTypeConversion.toJavaString(strm.msg()));
                }
            }
        }
    }

    // (JDK 11)src/java.base/share/native/libzip/Deflater.c
    // @formatter:off
    // Java_java_util_zip_Deflater_deflateBytesBytes(JNIEnv *env, jobject this, jlong addr,
    //                                               jbyteArray inputArray, jint inputOff, jint inputLen,
    //                                               jbyteArray outputArray, jint outputOff, jint outputLen,
    //                                               jint flush, jint params)
    // @formatter:on
    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    @SuppressWarnings({"static-method"})
    private long deflateBytesBytes(long addr,
                    byte[] inputArray, int inputOff, int inputLen,
                    byte[] outputArray, int outputOff, int outputLen,
                    int flush, int params) {

        try (PinnedObject inputPinned = PinnedObject.create(inputArray)) {
            try (PinnedObject outputPinned = PinnedObject.create(outputArray)) {
                return Util_java_util_zip_Deflater_JDK11OrLater.doDeflate(addr, inputPinned.addressOfArrayElement(inputOff), inputLen, outputPinned.addressOfArrayElement(outputOff), outputLen, flush,
                                params);
            }
        }
    }

    // (JDK 11)src/java.base/share/native/libzip/Deflater.c
    // @formatter:off
    // Java_java_util_zip_Deflater_deflateBytesBuffer(JNIEnv *env, jobject this, jlong addr,
    //                                                jbyteArray inputArray, jint inputOff, jint inputLen,
    //                                                jlong outputBuffer, jint outputLen,
    //                                                jint flush, jint params)
    // @formatter:on
    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    @SuppressWarnings({"static-method"})
    private long deflateBytesBuffer(long addr,
                    byte[] inputArray, int inputOff, int inputLen,
                    long outputBuffer, int outputLen,
                    int flush, int params) {

        try (PinnedObject inputPinned = PinnedObject.create(inputArray)) {
            return Util_java_util_zip_Deflater_JDK11OrLater.doDeflate(addr, inputPinned.addressOfArrayElement(inputOff), inputLen, WordFactory.pointer(outputBuffer), outputLen, flush, params);
        }
    }

    // (JDK 11)src/java.base/share/native/libzip/Deflater.c
    // @formatter:off
    // Java_java_util_zip_Deflater_deflateBufferBytes(JNIEnv *env, jobject this, jlong addr,
    //                                                jlong inputBuffer, jint inputLen,
    //                                                jbyteArray outputArray, jint outputOff, jint outputLen,
    //                                                jint flush, jint params)
    // @formatter:on
    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    @SuppressWarnings({"static-method"})
    private long deflateBufferBytes(long addr,
                    long inputBuffer, int inputLen,
                    byte[] outputArray, int outputOff, int outputLen,
                    int flush, int params) {

        try (PinnedObject outputPinned = PinnedObject.create(outputArray)) {
            return Util_java_util_zip_Deflater_JDK11OrLater.doDeflate(addr, WordFactory.pointer(inputBuffer), inputLen, outputPinned.addressOfArrayElement(outputOff), outputLen, flush, params);
        }
    }

    // (JDK 11)src/java.base/share/native/libzip/Deflater.c
    // @formatter:off
    // Java_java_util_zip_Deflater_deflateBufferBuffer(JNIEnv *env, jobject this, jlong addr,
    //                                                 jlong inputBuffer, jint inputLen,
    //                                                 jlong outputBuffer, jint outputLen,
    //                                                 jint flush, jint params)
    // @formatter:on
    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    @SuppressWarnings({"static-method"})
    private long deflateBufferBuffer(long addr,
                    long inputBuffer, int inputLen,
                    long outputBuffer, int outputLen,
                    int flush, int params) {

        return Util_java_util_zip_Deflater_JDK11OrLater.doDeflate(addr, WordFactory.pointer(inputBuffer), inputLen, WordFactory.pointer(outputBuffer), outputLen, flush, params);
    }

    // (JDK 11)src/java.base/share/native/libzip/Deflater.c
    // Java_java_util_zip_Deflater_getAdler(JNIEnv *env, jclass cls, jlong addr)
    @Substitute
    private static int getAdler(long addr) {
        z_stream strm = WordFactory.pointer(addr);
        return (int) strm.adler().rawValue();
    }

    // (JDK 11)src/java.base/share/native/libzip/Deflater.c
    // Java_java_util_zip_Deflater_reset(JNIEnv *env, jclass cls, jlong addr)
    @Substitute
    private static void reset(long addr) {
        z_stream strm = WordFactory.pointer(addr);
        if (ZLib.deflateReset(strm) != ZLib.Z_OK()) {
            throw new InternalError(CTypeConversion.toJavaString(strm.msg()));
        }
    }

    // (JDK 11)src/java.base/share/native/libzip/Deflater.c
    // Java_java_util_zip_Deflater_end(JNIEnv *env, jclass cls, jlong addr)
    @Substitute
    private static void end(long addr) {
        z_stream strm = WordFactory.pointer(addr);
        if (ZLib.deflateEnd(strm) == ZLib.Z_STREAM_ERROR()) {
            throw new InternalError(CTypeConversion.toJavaString(strm.msg()));
        } else {
            LibC.free(strm);
        }
    }
}

final class Util_java_util_zip_Deflater {

    // Checkstyle: stop
    static int DEF_MEM_LEVEL = 8;
    // Checkstyle: resume

    // (JDK 11)src/java.base/share/native/libzip/Deflater.c
    // static void doSetDictionary(JNIEnv *env, jlong addr, jbyte *buf, jint len)
    static void doSetDictionary(long addr, CCharPointer buf, int len) {
        z_stream strm = WordFactory.pointer(addr);
        int res = ZLib.deflateSetDictionary(strm, buf, len);
        if (res == ZLib.Z_OK()) {
            return;
        } else if (res == ZLib.Z_STREAM_ERROR()) {
            throw new IllegalArgumentException(CTypeConversion.toJavaString(strm.msg()));
        } else {
            throw new InternalError(CTypeConversion.toJavaString(strm.msg()));
        }
    }
}

final class Util_java_util_zip_Deflater_JDK11OrLater {

    // (JDK 11)src/java.base/share/native/libzip/Deflater.c
    // @formatter:off
    // static jlong doDeflate(JNIEnv *env, jobject this, jlong addr,
    //                        jbyte *input, jint inputLen,
    //                        jbyte *output, jint outputLen,
    //                        jint flush, jint params)
    // @formatter:on
    @SuppressWarnings({"cast"})
    static long doDeflate(long addr,
                    CCharPointer input, int inputLen,
                    CCharPointer output, int outputLen,
                    int flush, int params) {

        z_stream strm = WordFactory.pointer(addr);
        int inputUsed;
        int outputUsed;
        int finished = 0;
        int setParams = params & 1;

        strm.set_next_in(input);
        strm.set_next_out(output);
        strm.set_avail_in(inputLen);
        strm.set_avail_out(outputLen);

        if (setParams != 0) {
            int strategy = (params >> 1) & 3;
            int level = params >> 3;
            int res = ZLib.deflateParams(strm, level, strategy);
            if (res != ZLib.Z_OK() && res != ZLib.Z_BUF_ERROR()) {
                throw new InternalError(CTypeConversion.toJavaString(strm.msg()));
            }
            if (res == ZLib.Z_OK()) {
                setParams = 0;
            }
        } else {
            int res = ZLib.deflate(strm, flush);
            if (res != ZLib.Z_OK() && res != ZLib.Z_STREAM_END() && res != ZLib.Z_BUF_ERROR()) {
                throw new InternalError(CTypeConversion.toJavaString(strm.msg()));
            }
            if (res == ZLib.Z_STREAM_END()) {
                finished = 1;
            }
        }
        inputUsed = inputLen - strm.avail_in();
        outputUsed = outputLen - strm.avail_out();
        return ((long) inputUsed) | (((long) outputUsed) << 31) | (((long) finished) << 62) | (((long) setParams) << 63);
    }
}

final class Util_java_util_zip_Deflater_JDK10OrEarlier {

    static int update(Object obj, int len, z_stream strm) {
        Target_java_util_zip_Deflater instance = KnownIntrinsics.unsafeCast(obj, Target_java_util_zip_Deflater.class);
        instance.off += instance.len - strm.avail_in();
        instance.len = strm.avail_in();
        return len - strm.avail_out();
    }
}

@Platforms({Platform.LINUX.class, Platform.DARWIN.class})
@TargetClass(value = java.util.zip.Inflater.class)
final class Target_java_util_zip_Inflater {

    @Alias //
    @TargetElement(onlyWith = JDK10OrEarlier.class) //
    private byte[] buf;

    @Alias //
    @TargetElement(onlyWith = JDK10OrEarlier.class) //
    int off;

    @Alias //
    @TargetElement(onlyWith = JDK10OrEarlier.class) //
    int len;

    @Alias private boolean finished;

    @Alias private boolean needDict;

    @Alias //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    int inputConsumed;

    @Alias //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    int outputConsumed;

    @Substitute //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    private static void initIDs() {
        // nothing
    }

    // (JDK 11)src/java.base/share/native/libzip/Inflater.c
    // Java_java_util_zip_Inflater_init(JNIEnv *env, jclass cls, jboolean nowrap)
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
                    throw new InternalError(CTypeConversion.toJavaString(strm.msg()));
                }
            }
        }
    }

    // (JDK 11)src/java.base/share/native/libzip/Inflater.c
    // @formatter:off
    // Java_java_util_zip_Inflater_setDictionary(JNIEnv *env, jclass cls, jlong addr,
    //                                           jbyteArray b, jint off, jint len)
    // @formatter:on
    @Substitute
    private static void setDictionary(long addr, byte[] b, int off, int len) {
        try (PinnedObject pinned = PinnedObject.create(b)) {
            CCharPointer bytes = pinned.addressOfArrayElement(off);
            Util_java_util_zip_Inflater.doSetDictionary(addr, bytes, len);
        }
    }

    // (JDK 11)src/java.base/share/native/libzip/Inflater.c
    // @formatter:off
    // Java_java_util_zip_Inflater_setDictionaryBuffer(JNIEnv *env, jclass cls, jlong addr,
    //                                                 jlong bufferAddr, jint len)
    // @formatter:on
    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    private static void setDictionaryBuffer(long addr, long bufAddress, int len) {
        Util_java_util_zip_Inflater.doSetDictionary(addr, WordFactory.pointer(bufAddress), len);
    }

    // (JDK 8)src/share/native/java/util/zip/Inflater.c
    // @formatter:off
    // Java_java_util_zip_Inflater_inflateBytes(JNIEnv *env, jobject this, jlong addr,
    //                                          jarray b, jint off, jint len)
    // @formatter:on
    @SuppressWarnings("hiding")
    @Substitute
    @TargetElement(onlyWith = JDK10OrEarlier.class)
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
                return Util_java_util_zip_Inflater_JDK10OrEarlier.update(this, len, strm);
            } else if (ret == ZLib.Z_OK()) {
                return Util_java_util_zip_Inflater_JDK10OrEarlier.update(this, len, strm);
            } else if (ret == ZLib.Z_NEED_DICT()) {
                needDict = true;
                Util_java_util_zip_Inflater_JDK10OrEarlier.update(this, len, strm);
                return 0;
            } else if (ret == ZLib.Z_BUF_ERROR()) {
                return 0;
            } else if (ret == ZLib.Z_DATA_ERROR()) {
                throw new DataFormatException(CTypeConversion.toJavaString(strm.msg()));
            } else if (ret == ZLib.Z_MEM_ERROR()) {
                throw new OutOfMemoryError();
            } else {
                throw new InternalError(CTypeConversion.toJavaString(strm.msg()));
            }
        }
    }

    // (JDK 11)src/java.base/share/native/libzip/Inflater.c
    // @formatter:off
    // Java_java_util_zip_Inflater_inflateBytesBytes(JNIEnv *env, jobject this, jlong addr,
    //                                               jbyteArray inputArray, jint inputOff, jint inputLen,
    //                                               jbyteArray outputArray, jint outputOff, jint outputLen)
    // @formatter:on
    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    private long inflateBytesBytes(long addr,
                    byte[] inputArray, int inputOff, int inputLen,
                    byte[] outputArray, int outputOff, int outputLen) throws DataFormatException {

        try (PinnedObject inputPinned = PinnedObject.create(inputArray)) {
            CCharPointer inputBytes = inputPinned.addressOfArrayElement(inputOff);
            try (PinnedObject outputPinned = PinnedObject.create(outputArray)) {
                CCharPointer outputBytes = outputPinned.addressOfArrayElement(outputOff);
                return Util_java_util_zip_Inflater_JDK11OrLater.doInflate(this, addr, inputBytes, inputLen, outputBytes, outputLen);
            }
        }
    }

    // (JDK 11)src/java.base/share/native/libzip/Inflater.c
    // @formatter:off
    // Java_java_util_zip_Inflater_inflateBytesBuffer(JNIEnv *env, jobject this, jlong addr,
    //                                                jbyteArray inputArray, jint inputOff, jint inputLen,
    //                                                jlong outputBuffer, jint outputLen)
    // @formatter:on
    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    private long inflateBytesBuffer(long addr,
                    byte[] inputArray, int inputOff, int inputLen,
                    long outputAddress, int outputLen) throws DataFormatException {

        try (PinnedObject inputPinned = PinnedObject.create(inputArray)) {
            CCharPointer inputBytes = inputPinned.addressOfArrayElement(inputOff);
            CCharPointer outputBytes = WordFactory.pointer(outputAddress);
            return Util_java_util_zip_Inflater_JDK11OrLater.doInflate(this, addr, inputBytes, inputLen, outputBytes, outputLen);
        }
    }

    // (JDK 11)src/java.base/share/native/libzip/Inflater.c
    // @formatter:off
    // Java_java_util_zip_Inflater_inflateBufferBytes(JNIEnv *env, jobject this, jlong addr,
    //                                                jlong inputBuffer, jint inputLen,
    //                                                jbyteArray outputArray, jint outputOff, jint outputLen)
    // @formatter:on
    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    private long inflateBufferBytes(long addr,
                    long inputAddress, int inputLen,
                    byte[] outputArray, int outputOff, int outputLen) throws DataFormatException {

        CCharPointer inputBytes = WordFactory.pointer(inputAddress);
        try (PinnedObject outputPinned = PinnedObject.create(outputArray)) {
            CCharPointer outputBytes = outputPinned.addressOfArrayElement(outputOff);
            return Util_java_util_zip_Inflater_JDK11OrLater.doInflate(this, addr, inputBytes, inputLen, outputBytes, outputLen);
        }
    }

    // (JDK 11)src/java.base/share/native/libzip/Inflater.c
    // @formatter:off
    // Java_java_util_zip_Inflater_inflateBufferBuffer(JNIEnv *env, jobject this, jlong addr,
    //                                                 jlong inputBuffer, jint inputLen,
    //                                                 jlong outputBuffer, jint outputLen)
    // @formatter:on
    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    private long inflateBufferBuffer(long addr,
                    long inputAddress, int inputLen,
                    long outputAddress, int outputLen) throws DataFormatException {

        CCharPointer inputBytes = WordFactory.pointer(inputAddress);
        CCharPointer outputBytes = WordFactory.pointer(outputAddress);
        return Util_java_util_zip_Inflater_JDK11OrLater.doInflate(this, addr, inputBytes, inputLen, outputBytes, outputLen);
    }

    // (JDK 11)src/java.base/share/native/libzip/Inflater.c
    // Java_java_util_zip_Inflater_getAdler(JNIEnv *env, jclass cls, jlong addr)
    @Substitute
    private static int getAdler(long addr) {
        z_stream strm = WordFactory.pointer(addr);
        return (int) strm.adler().rawValue();
    }

    // (JDK 11)src/java.base/share/native/libzip/Inflater.c
    // Java_java_util_zip_Inflater_reset(JNIEnv *env, jclass cls, jlong addr)
    @Substitute
    private static void reset(long addr) {
        if (ZLib.inflateReset(WordFactory.pointer(addr)) != ZLib.Z_OK()) {
            // zlib does not set a message on failure of inflateReset.
            throw new InternalError();
        }
    }

    // (JDK 11)src/java.base/share/native/libzip/Inflater.c
    // Java_java_util_zip_Inflater_end(JNIEnv *env, jclass cls, jlong addr)
    @Substitute
    private static void end(long addr) {
        z_stream strm = WordFactory.pointer(addr);
        if (ZLib.inflateEnd(strm) == ZLib.Z_STREAM_ERROR()) {
            // zlib does not set a message on failure of inflateEnd.
            throw new InternalError();
        } else {
            LibC.free(strm);
        }
    }
}

final class Util_java_util_zip_Inflater_JDK11OrLater {

    // (JDK 11)src/java.base/share/native/libzip/Inflater.c
    // @formatter:off
    // static jlong doInflate(JNIEnv *env, jobject this, jlong addr,
    //                        jbyte *input, jint inputLen,
    //                        jbyte *output, jint outputLen)
    // @formatter:on
    @SuppressWarnings({"cast"})
    static long doInflate(final Object obj, long addr, CCharPointer input, int inputLen, CCharPointer output, int outputLen) throws DataFormatException {
        Target_java_util_zip_Inflater instance = KnownIntrinsics.unsafeCast(obj, Target_java_util_zip_Inflater.class);
        z_stream strm = WordFactory.pointer(addr);
        int inputUsed = 0;
        int outputUsed = 0;

        strm.set_next_in(input);
        strm.set_next_out(output);
        strm.set_avail_in(inputLen);
        strm.set_avail_out(outputLen);

        int ret = ZLib.inflate(strm, ZLib.Z_PARTIAL_FLUSH());

        int finished = 0;
        int needDict = 0;

        if (ret == ZLib.Z_STREAM_END() || ret == ZLib.Z_OK()) {
            if (ret == ZLib.Z_STREAM_END()) {
                finished = 1;
                inputUsed = inputLen - strm.avail_in();
                outputUsed = outputLen - strm.avail_out();
            }
        } else if (ret == ZLib.Z_NEED_DICT()) {
            needDict = 1;
            inputUsed = inputLen - strm.avail_in();
            outputUsed = outputLen - strm.avail_out();
        } else if (ret == ZLib.Z_DATA_ERROR()) {
            // these fields act as "out" parameters
            instance.inputConsumed = inputLen - strm.avail_in();
            instance.outputConsumed = outputLen - strm.avail_out();
            throw new DataFormatException(CTypeConversion.toJavaString(strm.msg()));
        } else if (ret == ZLib.Z_MEM_ERROR()) {
            throw new OutOfMemoryError();
        } else if (ret != ZLib.Z_BUF_ERROR()) {
            throw new InternalError(CTypeConversion.toJavaString(strm.msg()));
        }
        return ((long) inputUsed) | (((long) outputUsed) << 31) | (((long) finished) << 62) | (((long) needDict) << 63);
    }
}

final class Util_java_util_zip_Inflater_JDK10OrEarlier {
    static int update(Object obj, int len, z_stream strm) {
        Target_java_util_zip_Inflater instance = KnownIntrinsics.unsafeCast(obj, Target_java_util_zip_Inflater.class);
        instance.off += instance.len - strm.avail_in();
        instance.len = strm.avail_in();
        return len - strm.avail_out();
    }

}

final class Util_java_util_zip_Inflater {

    // (JDK 11)src/java.base/share/native/libzip/Inflater.c
    // static void doSetDictionary(JNIEnv *env, jlong addr, jbyte *buf, jint len)
    static void doSetDictionary(long addr, CCharPointer bytes, int len) {
        z_stream strm = WordFactory.pointer(addr);
        int res = ZLib.inflateSetDictionary(strm, bytes, len);
        if (res == ZLib.Z_OK()) {
            return;
        }
        if (res == ZLib.Z_STREAM_ERROR() || res == ZLib.Z_DATA_ERROR()) {
            throw new IllegalArgumentException(CTypeConversion.toJavaString(strm.msg()));
        }
        throw new InternalError(CTypeConversion.toJavaString(strm.msg()));
    }
}

/** Dummy class to have a class with the file's name. */
public final class JavaUtilZipSubstitutions {
}
