/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm;

import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.CShortPointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.hosted.webimage.wasm.gc.WasmAllocation;
import com.oracle.svm.webimage.platform.WebImageWasmLMPlatform;
import com.oracle.svm.webimage.wasmgc.annotation.WasmExport;

import jdk.graal.compiler.word.Word;

/**
 * Support class to create Java strings from the JS runtime through exported functions.
 * <p>
 * The general approach is that JS code calls {@link #prepare(int)} to set up a buffer it can fill
 * in with the string's characters. After that, it calls {@link #finish(int, CShortPointer)} to get
 * the Java string.
 * <p>
 * This is not a general purpose API, use with care. The string instance returned by
 * {@link #finish(int, CShortPointer)} is represented as a {@code BigInt} on the JS side and cannot
 * be tracked by the WasmLM GC. Because of this, it is crucial that no code that could trigger a GC
 * can run before this instance pointer is passed back to Java code.
 * <p>
 * The only safe way to ensure this is to not call any Java code from JS while holding this pointer.
 * Returning the pointer to Java code by passing it to an exported function or by returning it as a
 * return value from the imported function called from Java is safe. In both cases, the pointer has
 * to be considered invalid once returned.
 */
@Platforms(WebImageWasmLMPlatform.class)
public class WasmLMStringSupport {

    /**
     * Prepares a buffer to hold the string character data.
     * <p>
     * Pass the filled buffer to {@link #finish(int, CShortPointer)}.
     *
     * @param numChars Number of 16-bit characters in the string
     * @return A pointer to the allocated buffer of {@code 2 * numChars} bytes
     */
    @WasmExport("string.prepare")
    private static CShortPointer prepare(int numChars) {
        assert numChars >= 0;
        return (CShortPointer) WasmAllocation.malloc(Word.unsigned(2 * numChars));
    }

    /**
     * Finishes creating the Java string.
     * <p>
     * This frees the passed pointer, which must not be used afterward.
     *
     * @param numChars Number of 16-bit characters in the string
     * @param charPointer Filled buffer of {@code numChars} 16-bit characters.
     * @return A Java {@link String} instance with the provided character data. See class
     *         documentation for correctness concerns.
     */
    @WasmExport("string.finish")
    private static String finish(int numChars, CShortPointer charPointer) {
        char[] chars = new char[numChars];

        for (int i = 0; i < numChars; i++) {
            chars[i] = (char) charPointer.read(i);
        }

        WasmAllocation.free((Pointer) charPointer);

        return new String(chars);
    }
}
