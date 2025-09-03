/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.libs.libzip.impl;

import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.ffi.nfi.NativeUtils;
import com.oracle.truffle.espresso.libs.LibsMeta;
import com.oracle.truffle.espresso.libs.LibsState;
import com.oracle.truffle.espresso.libs.libzip.LibZip;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;

/**
 * EspressoLibs substitution for the Inflater class. The implementation associates a HostInflater
 * with every GuestInflater by providing the guest with a handle that maps to the HostInflater
 * (handle2Inflater). This handle is passed as an argument to every native method, allowing easy
 * retrieval of the HostInflater and implementation of the GuestInflater's native methods.
 *
 * The Inflater class implementation differs between Java 8 and Java 11+: In Java 8, input is
 * provided through class fields (which need to be updated by native code). In Java 11 and later,
 * input is passed explicitly as an argument to the inflate method
 *
 * Additionally: In Java 8, the native code writes the 'finished' and 'needDict' fields. In Java
 * 11+, 'needDict' and 'finished' are encoded in the native inflate method's return value, and the
 * fields are subsequently updated in the guest code
 */
@EspressoSubstitutions(value = Inflater.class, group = LibZip.class)
public final class Target_java_util_zip_Inflater {
    @Substitution
    public static void initIDs() {
        // Do nothing
    }

    @Substitution
    @TruffleBoundary
    public static long init(boolean nowarp, @Inject LibsState libsState) {
        return libsState.handlifyInflater(new Inflater(nowarp));
    }

    @Substitution
    public static void setDictionary(long addr, @JavaType(byte[].class) StaticObject b, int off,
                    int len, @Inject LibsState libsState, @Inject EspressoLanguage language) {
        byte[] byteArrayB = b.unwrap(language);
        libsState.getInflater(addr).setDictionary(byteArrayB, off, len);
    }

    @Substitution
    public static void setDictionaryBuffer(long addr, long bufAddress, int len, @Inject LibsState libsState) {
        ByteBuffer dst = NativeUtils.directByteBuffer(bufAddress, len);
        libsState.getInflater(addr).setDictionary(dst);
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    public static long inflateBytesBytes(@JavaType(Inflater.class) StaticObject guestInflater, long addr,
                    @JavaType(byte[].class) StaticObject inputArray, int inputOff, int inputLen,
                    @JavaType(byte[].class) StaticObject outputArray, int outputOff, int outputLen,
                    @Inject LibsState libsState, @Inject LibsMeta libsMeta, @Inject EspressoLanguage language) {
        // get Input/Output Array/Buffer
        byte[] inputByteArray = inputArray.unwrap(language);
        byte[] outputByteArray = outputArray.unwrap(language);
        // get host Inflater and set Input
        Inflater hostInflater = libsState.getInflater(addr);
        hostInflater.setInput(inputByteArray, inputOff, inputLen);
        // cache bytes/read/written for the exception case
        long bytesReadOld = hostInflater.getBytesRead();
        long bytesWrittenOld = hostInflater.getBytesWritten();
        try {
            int written = hostInflater.inflate(outputByteArray, outputOff, outputLen);
            int read = Math.toIntExact(hostInflater.getBytesRead() - bytesReadOld);
            return encodeResult(read, written, hostInflater);
        } catch (DataFormatException e) {
            updateGuestInflater(hostInflater, bytesReadOld, bytesWrittenOld, guestInflater, libsMeta);
            throw libsMeta.getMeta().throwExceptionWithMessage(libsMeta.java_util_zip_DataFormatException, e.getMessage());
        }
    }

    @Substitution(hasReceiver = true)
    public static long inflateBytesBuffer(@JavaType(Inflater.class) StaticObject guestInflater, long addr,
                    @JavaType(byte[].class) StaticObject inputArray, int inputOff, int inputLen,
                    long outputAddress, int outputLen, @Inject LibsState libsState, @Inject LibsMeta libsMeta, @Inject EspressoLanguage lang) {
        // get Input/Output Array/Buffer
        byte[] inputByteArray = inputArray.unwrap(lang);
        ByteBuffer outputByteBuffer = NativeUtils.directByteBuffer(outputAddress, outputLen);
        // get host Inflater and set Input
        Inflater hostInflater = libsState.getInflater(addr);
        hostInflater.setInput(inputByteArray, inputOff, inputLen);
        // cache bytes/read/written for the exception case
        long bytesReadOld = hostInflater.getBytesRead();
        long bytesWrittenOld = hostInflater.getBytesWritten();
        try {
            int written = hostInflater.inflate(outputByteBuffer);
            int read = Math.toIntExact(hostInflater.getBytesRead() - bytesReadOld);
            return encodeResult(read, written, hostInflater);
        } catch (DataFormatException e) {
            updateGuestInflater(hostInflater, bytesReadOld, bytesWrittenOld, guestInflater, libsMeta);
            throw libsMeta.getMeta().throwExceptionWithMessage(libsMeta.java_util_zip_DataFormatException, e.getMessage());
        }
    }

    @Substitution(hasReceiver = true)
    public static long inflateBufferBytes(@JavaType(Inflater.class) StaticObject guestInflater, long addr,
                    long inputAddress, int inputLen,
                    @JavaType(byte[].class) StaticObject outputArray, int outputOff, int outputLen,
                    @Inject LibsState libsState, @Inject LibsMeta libsMeta,
                    @Inject EspressoLanguage lang) {

        // get Input/Output Array/Buffer
        ByteBuffer inputByteBuffer = NativeUtils.directByteBuffer(inputAddress, inputLen);
        byte[] outputByteArray = outputArray.unwrap(lang);
        // get host Inflater and set Input
        Inflater hostInflater = libsState.getInflater(addr);
        hostInflater.setInput(inputByteBuffer);
        // cache bytes/read/written for the exception case
        long bytesReadOld = hostInflater.getBytesRead();
        long bytesWrittenOld = hostInflater.getBytesWritten();
        try {
            int written = hostInflater.inflate(outputByteArray, outputOff, outputLen);
            int read = Math.toIntExact(hostInflater.getBytesRead() - bytesReadOld);
            return encodeResult(read, written, hostInflater);
        } catch (DataFormatException e) {
            updateGuestInflater(hostInflater, bytesReadOld, bytesWrittenOld, guestInflater, libsMeta);
            throw libsMeta.getMeta().throwExceptionWithMessage(libsMeta.java_util_zip_DataFormatException, e.getMessage());
        }
    }

    @Substitution(hasReceiver = true)
    @SuppressWarnings("unused")
    public static long inflateBufferBuffer(@JavaType(Inflater.class) StaticObject guestInflater, long addr,
                    long inputAddress, int inputLen,
                    long outputAddress, int outputLen,
                    @Inject LibsState libsState, @Inject LibsMeta libsMeta,
                    @Inject EspressoLanguage lang) {
        // get Input/Output Array/Buffer
        ByteBuffer inputByteBuffer = NativeUtils.directByteBuffer(inputAddress, inputLen);
        ByteBuffer outputByteBuffer = NativeUtils.directByteBuffer(outputAddress, outputLen);
        // get host Inflater and set Input
        Inflater hostInflater = libsState.getInflater(addr);
        hostInflater.setInput(inputByteBuffer);
        // cache bytes/read/written for the exception case
        long bytesReadOld = hostInflater.getBytesRead();
        long bytesWrittenOld = hostInflater.getBytesWritten();
        try {
            int written = hostInflater.inflate(outputByteBuffer);
            int read = Math.toIntExact(hostInflater.getBytesRead() - bytesReadOld);
            return encodeResult(read, written, hostInflater);
        } catch (DataFormatException e) {
            updateGuestInflater(hostInflater, bytesReadOld, bytesWrittenOld, guestInflater, libsMeta);
            throw libsMeta.getMeta().throwExceptionWithMessage(libsMeta.java_util_zip_DataFormatException, e.getMessage());
        }
    }

    private static void updateGuestInflater(Inflater hostInflater, long bytesReadOld, long bytesWrittenOld,
                    @JavaType(Inflater.class) StaticObject guestInflater, LibsMeta libsMeta) {
        int inputConsumed = Math.toIntExact(hostInflater.getBytesRead() - bytesReadOld);
        int outputConsumed = Math.toIntExact(hostInflater.getBytesWritten() - bytesWrittenOld);
        libsMeta.java_util_zip_Inflater_inputConsumed.setInt(guestInflater, inputConsumed);
        libsMeta.java_util_zip_Inflater_outputConsumed.setInt(guestInflater, outputConsumed);
    }

    @Substitution
    public static int getAdler(long addr, @Inject LibsState libsState) {
        return libsState.getInflater(addr).getAdler();
    }

    @Substitution
    public static void reset(long addr, @Inject LibsState libsState) {
        libsState.getInflater(addr).reset();
    }

    @Substitution
    @TruffleBoundary
    public static void end(long addr, @Inject LibsState libsState) {
        libsState.getInflater(addr).end();
        // release handle
        libsState.cleanInflater(addr);
    }

    private static long encodeResult(int read, int written, Inflater hostInflater) {
        long result = 0;
        boolean needDict = hostInflater.needsDictionary();
        boolean finished = hostInflater.finished();

        // Set bit 63 (needDict flag)
        if (needDict) {
            result |= 1L << 63;
        }

        // Set bit 62 (finished flag)
        if (finished) {
            result |= 1L << 62;
        }

        // Set bits 31-61 (written value)
        result |= (written & 0x7fff_ffffL) << 31;

        // Set bits 0-30 (read value)
        result |= read & 0x7fff_ffffL;

        return result;
    }
}
