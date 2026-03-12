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
import java.util.zip.Deflater;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.ffi.memory.NativeMemory.IllegalMemoryAccessException;
import com.oracle.truffle.espresso.libs.libzip.LibZipState;
import com.oracle.truffle.espresso.libs.libzip.PureJavaLibZipFilter;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;

// does not belong to the LibZip group as we use those substitution outside espressoLibs
@EspressoSubstitutions(group = Substitution.class)
public final class Target_java_util_zip_Deflater {
    @Substitution(languageFilter = PureJavaLibZipFilter.class)
    @TruffleBoundary
    public static long init(int level, int strategy, boolean nowrap, @Inject LibZipState libZipState) {
        Deflater deflater = new Deflater(level, nowrap);
        /*
         * Will always be called with the default strategy (0). Thus, we don't need to set it as it
         * will default to 0 on the host too.
         */
        assert strategy == 0;
        return libZipState.handlifyDeflater(deflater);
    }

    @Substitution(languageFilter = PureJavaLibZipFilter.class)
    public static void setDictionary(long addr, @JavaType(byte[].class) StaticObject b, int off,
                    int len, @Inject LibZipState libZipState, @Inject EspressoLanguage language) {
        byte[] byteArrayB = b.unwrap(language);
        libZipState.getDeflater(addr).setDictionary(byteArrayB, off, len);
    }

    @Substitution(languageFilter = PureJavaLibZipFilter.class)
    @TruffleBoundary
    public static void setDictionaryBuffer(long addr, long bufAddress, int len, @Inject LibZipState libZipState, @Inject EspressoContext ctx, @Inject Meta meta) {
        try {
            libZipState.getDeflater(addr).setDictionary(ctx.getNativeAccess().nativeMemory().wrapNativeMemory(bufAddress, len));
        } catch (IllegalMemoryAccessException e) {
            throw meta.throwIllegalArgumentExceptionBoundary("Invalid memory access: bufAddress and len refer to memory outside the allocated region");
        }
    }

    @Substitution(hasReceiver = true, languageFilter = PureJavaLibZipFilter.class)
    @TruffleBoundary
    public static long deflateBytesBytes(@SuppressWarnings("unused") @JavaType(Deflater.class) StaticObject guestDeflater, long addr,
                    @JavaType(byte[].class) StaticObject inputArray, int inputOff, int inputLen,
                    @JavaType(byte[].class) StaticObject outputArray, int outputOff, int outputLen,
                    int flush, int params,
                    @Inject LibZipState libZipState, @Inject EspressoLanguage language) {
        // set input
        byte[] inputByteArray = inputArray.unwrap(language);
        Deflater hostDeflater = libZipState.getDeflater(addr);
        hostDeflater.setInput(inputByteArray, inputOff, inputLen);

        handleParams(params, hostDeflater);
        int hostFlush = handleFlush(flush, hostDeflater);
        // cache old bytes read in order to calculate the actual bytes read
        long bytesReadOld = hostDeflater.getBytesRead();
        // do deflate
        byte[] outputByteArray = outputArray.unwrap(language);
        int written = hostDeflater.deflate(outputByteArray, outputOff, outputLen, hostFlush);
        return encodeResult(bytesReadOld, written, hostDeflater);
    }

    @Substitution(hasReceiver = true, languageFilter = PureJavaLibZipFilter.class)
    public static long deflateBytesBuffer(@SuppressWarnings("unused") @JavaType(Deflater.class) StaticObject guestDeflater, long addr,
                    @JavaType(byte[].class) StaticObject inputArray, int inputOff, int inputLen,
                    long outputAddress, int outputLen,
                    int flush, int params,
                    @Inject LibZipState libZipState,
                    @Inject EspressoLanguage language, @Inject EspressoContext ctx, @Inject Meta meta) {
        // set input
        byte[] inputByteArray = inputArray.unwrap(language);
        Deflater hostDeflater = libZipState.getDeflater(addr);
        hostDeflater.setInput(inputByteArray, inputOff, inputLen);

        handleParams(params, hostDeflater);
        int hostFlush = handleFlush(flush, hostDeflater);
        // cache old bytes read in order to calculate the actual bytes read
        long bytesReadOld = hostDeflater.getBytesRead();
        try {
            ByteBuffer outputBuffer = ctx.getNativeAccess().nativeMemory().wrapNativeMemory(outputAddress, outputLen);
            int written = hostDeflater.deflate(outputBuffer, hostFlush);
            return encodeResult(bytesReadOld, written, hostDeflater);
        } catch (IllegalMemoryAccessException e) {
            throw meta.throwIllegalArgumentExceptionBoundary("Invalid memory access: outputAddress and outputLen refer to memory outside the allocated region");
        }
    }

    @Substitution(hasReceiver = true, languageFilter = PureJavaLibZipFilter.class)
    @TruffleBoundary
    public static long deflateBufferBytes(@SuppressWarnings("unused") @JavaType(Deflater.class) StaticObject guestDeflater, long addr,
                    long inputAddress, int inputLen,
                    @JavaType(byte[].class) StaticObject outputArray, int outputOff, int outputLen,
                    int flush, int params,
                    @Inject LibZipState libZipState, @Inject EspressoLanguage language,
                    @Inject EspressoContext ctx, @Inject Meta meta) {
        Deflater hostDeflater = libZipState.getDeflater(addr);
        handleParams(params, hostDeflater);
        int hostFlush = handleFlush(flush, hostDeflater);
        // cache old bytes read in order to calculate the actual bytes read
        long bytesReadOld = hostDeflater.getBytesRead();
        // set input
        try {
            hostDeflater.setInput(ctx.getNativeAccess().nativeMemory().wrapNativeMemory(inputAddress, inputLen));
        } catch (IllegalMemoryAccessException e) {
            throw meta.throwIllegalArgumentExceptionBoundary("Invalid memory access: inputAddress and inputLen refer to memory outside the allocated region");
        }
        // do deflate
        byte[] outputByteArray = outputArray.unwrap(language);
        int written = hostDeflater.deflate(outputByteArray, outputOff, outputLen, hostFlush);
        return encodeResult(bytesReadOld, written, hostDeflater);
    }

    @Substitution(hasReceiver = true, languageFilter = PureJavaLibZipFilter.class)
    public static long deflateBufferBuffer(@SuppressWarnings("unused") @JavaType(Deflater.class) StaticObject guestDeflater, long addr,
                    long inputAddress, int inputLen,
                    long outputAddress, int outputLen,
                    int flush, int params,
                    @Inject LibZipState libZipState, @Inject EspressoContext ctx, @Inject Meta meta) {
        Deflater hostDeflater = libZipState.getDeflater(addr);
        handleParams(params, hostDeflater);
        int hostFlush = handleFlush(flush, hostDeflater);
        // cache old bytes read in order to calculate the actual bytes read
        long bytesReadOld = hostDeflater.getBytesRead();
        try {
            // set input
            ByteBuffer inputBuffer = ctx.getNativeAccess().nativeMemory().wrapNativeMemory(inputAddress, inputLen);
            hostDeflater.setInput(inputBuffer);
            // do deflate
            ByteBuffer outputBuffer = ctx.getNativeAccess().nativeMemory().wrapNativeMemory(outputAddress, outputLen);
            int written = hostDeflater.deflate(outputBuffer, hostFlush);
            return encodeResult(bytesReadOld, written, hostDeflater);
        } catch (IllegalMemoryAccessException e) {
            throw meta.throwIllegalArgumentExceptionBoundary("Invalid memory access: an input or output address and length refers to memory outside the allocated region");
        }
    }

    /**
     * Updates the compression level and strategy if requested by the guest.
     */
    private static void handleParams(int params, Deflater deflater) {
        /*
         * As one can see in java.util.zip.Deflater.deflate(java.nio.ByteBuffer, int) params is
         * encoded as follows:
         */
        // bit 0: true to set params
        // bit 1-2: strategy (0, 1, or 2)
        // bit 3-31: level (0..9 or -1)
        if ((params & 1) != 0) {
            int strategy = (params >> 1) & 3;
            deflater.setStrategy(strategy);
            int level = params >> 3;
            deflater.setLevel(level);
        }
    }

    private static int handleFlush(int flush, Deflater deflater) {
        if (flush == 4) {
            // indicates the guest called deflater.finish()
            deflater.finish();
            return 0;
        }
        return flush;
    }

    private static long encodeResult(long bytesReadOld, int written, Deflater hostDeflater) {
        long finishedBit = hostDeflater.finished() ? (1L << 62) : 0L;
        int read = Math.toIntExact(hostDeflater.getBytesRead() - bytesReadOld);
        return (read & 0x7FFF_FFFFL) | ((written & 0x7FFF_FFFFL) << 31) | finishedBit;
    }

    @Substitution(languageFilter = PureJavaLibZipFilter.class)
    public static int getAdler(long addr, @Inject LibZipState libZipState) {
        return libZipState.getDeflater(addr).getAdler();
    }

    @Substitution(languageFilter = PureJavaLibZipFilter.class)
    public static void reset(long addr, @Inject LibZipState libZipState) {
        libZipState.getDeflater(addr).reset();
    }

    @Substitution(languageFilter = PureJavaLibZipFilter.class)
    @TruffleBoundary
    public static void end(long addr, @Inject LibZipState libZipState) {
        libZipState.getDeflater(addr).end();
        // release handle
        libZipState.cleanDeflater(addr);
    }
}
