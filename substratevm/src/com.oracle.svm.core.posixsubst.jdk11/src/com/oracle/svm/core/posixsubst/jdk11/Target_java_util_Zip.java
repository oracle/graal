/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posixsubst.jdk11;

import java.util.zip.DataFormatException;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.posixsubst.JavaUtilZipSubstitutions;
import com.oracle.svm.core.posixsubst.headers.ZLib;

import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.impl.DeprecatedPlatform;
import org.graalvm.word.WordFactory;

// Dummy class for file name.
public class Target_java_util_Zip {
}

@Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class})
@TargetClass(value = java.util.zip.Deflater.class, onlyWith = JDK11OrLater.class)
final class Target_java_util_zip_Deflater {

    // (JDK 11)src/java.base/share/native/libzip/Deflater.c
    // @formatter:off
    // Java_java_util_zip_Deflater_setDictionaryBuffer(JNIEnv *env, jclass cls, jlong addr,
    //                                                 jlong bufferAddr, jint len)
    // @formatter:on
    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    private static void setDictionaryBuffer(long addr, long bufAddress, int len) {
        JavaUtilZipSubstitutions.Util_java_util_zip_Deflater.doSetDictionary(addr, WordFactory.pointer(bufAddress), len);
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
                return Util_java_util_zip_Deflater.doDeflate(addr, inputPinned.addressOfArrayElement(inputOff), inputLen, outputPinned.addressOfArrayElement(outputOff), outputLen, flush,
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
            return Util_java_util_zip_Deflater.doDeflate(addr, inputPinned.addressOfArrayElement(inputOff), inputLen, WordFactory.pointer(outputBuffer), outputLen, flush, params);
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
            return Util_java_util_zip_Deflater.doDeflate(addr, WordFactory.pointer(inputBuffer), inputLen, outputPinned.addressOfArrayElement(outputOff), outputLen, flush, params);
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

        return Util_java_util_zip_Deflater.doDeflate(addr, WordFactory.pointer(inputBuffer), inputLen, WordFactory.pointer(outputBuffer), outputLen, flush, params);
    }

}

final class Util_java_util_zip_Deflater {

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

        ZLib.z_stream strm = WordFactory.pointer(addr);
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

@Platforms({DeprecatedPlatform.LINUX_SUBSTITUTION.class, DeprecatedPlatform.DARWIN_SUBSTITUTION.class})
@TargetClass(value = java.util.zip.Inflater.class, onlyWith = JDK11OrLater.class)
final class Target_java_util_zip_Inflater {

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
    // @formatter:off
    // Java_java_util_zip_Inflater_setDictionaryBuffer(JNIEnv *env, jclass cls, jlong addr,
    //                                                 jlong bufferAddr, jint len)
    // @formatter:on
    @Substitute
    @TargetElement(onlyWith = JDK11OrLater.class)
    private static void setDictionaryBuffer(long addr, long bufAddress, int len) {
        JavaUtilZipSubstitutions.Util_java_util_zip_Deflater.doSetDictionary(addr, WordFactory.pointer(bufAddress), len);
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
                return Util_java_util_zip_Inflater.doInflate(this, addr, inputBytes, inputLen, outputBytes, outputLen);
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
            return Util_java_util_zip_Inflater.doInflate(this, addr, inputBytes, inputLen, outputBytes, outputLen);
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
            return Util_java_util_zip_Inflater.doInflate(this, addr, inputBytes, inputLen, outputBytes, outputLen);
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
        return Util_java_util_zip_Inflater.doInflate(this, addr, inputBytes, inputLen, outputBytes, outputLen);
    }
}

final class Util_java_util_zip_Inflater {

    // (JDK 11)src/java.base/share/native/libzip/Inflater.c
    // @formatter:off
    // static jlong doInflate(JNIEnv *env, jobject this, jlong addr,
    //                        jbyte *input, jint inputLen,
    //                        jbyte *output, jint outputLen)
    // @formatter:on
    @SuppressWarnings({"cast"})
    static long doInflate(final Object obj, long addr, CCharPointer input, int inputLen, CCharPointer output, int outputLen) throws DataFormatException {
        Target_java_util_zip_Inflater instance = SubstrateUtil.cast(obj, Target_java_util_zip_Inflater.class);
        ZLib.z_stream strm = WordFactory.pointer(addr);
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
            }
            inputUsed = inputLen - strm.avail_in();
            outputUsed = outputLen - strm.avail_out();
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
