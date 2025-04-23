/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk.localization.compression;

import static com.oracle.svm.core.jdk.localization.compression.utils.BundleSerializationUtils.deserializeContent;
import static com.oracle.svm.core.jdk.localization.compression.utils.BundleSerializationUtils.extractContent;
import static com.oracle.svm.core.jdk.localization.compression.utils.BundleSerializationUtils.serializeContent;
import static com.oracle.svm.core.jdk.localization.compression.utils.CompressionUtils.bytesToInts;
import static com.oracle.svm.core.jdk.localization.compression.utils.CompressionUtils.intsToBytes;
import static com.oracle.svm.core.jdk.localization.compression.utils.CompressionUtils.readInt;
import static com.oracle.svm.core.jdk.localization.compression.utils.CompressionUtils.readNBytes;
import static com.oracle.svm.core.jdk.localization.compression.utils.CompressionUtils.writeInt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.jdk.localization.bundles.CompressedBundle;
import com.oracle.svm.core.jdk.localization.compression.utils.BundleSerializationUtils.SerializedContent;
import com.oracle.svm.core.util.VMError;

/**
 * Class responsible for serialization and compression of resource bundles. Only bundles whose
 * values are strings or arrays of strings are supported. While in theory the bundles can contain
 * any objects, in practise it is rarely the case.
 *
 * The serialization format is the following:
 *
 * LEN1 INDICES LEN2 TEXT
 *
 * where LEN1 and LEN2 are the lengths of byte arrays, TEXT is the actual serialized content of all
 * keys and values merged into a single string and INDICES describe how to deserialize the content
 * back into a map. The format of indices is the following:
 *
 * ( ARR_LEN KEY_LEN VALUE_LEN{ARR_LEN} )*
 *
 * It is a variable length list of entries. Each entry starts with ARR_LEN, which indicates the
 * length of the value array or -1 for simple string values. KEY_LEN and VALUE_LEN should be
 * self-explanatory.
 *
 */
public class GzipBundleCompression {

    @Platforms(Platform.HOSTED_ONLY.class)
    public static boolean canCompress(ResourceBundle bundle) {
        return extractContent(bundle)
                        .values()
                        .stream()
                        .allMatch(value -> value instanceof String || (value instanceof Object[] && Arrays.stream(((Object[]) value)).allMatch(elem -> elem instanceof String)));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static CompressedBundle compress(ResourceBundle bundle) {
        final Map<String, Object> content = extractContent(bundle);
        SerializedContent input = serializeContent(content);
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream(); GZIPOutputStream out = new GZIPOutputStream(byteStream)) {
            writeIndices(input.indices(), out);
            writeText(input.text(), out);
            out.finish();
            return new CompressedBundle(byteStream.toByteArray(), GzipBundleCompression::decompressBundle);
        } catch (IOException ex) {
            throw VMError.shouldNotReachHere("Compression of a bundle " + bundle.getClass() + " failed. This is an internal error. Please open an issue and submit a reproducer.", ex);
        }
    }

    private static Map<String, Object> decompressBundle(byte[] data) {
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(data))) {
            int[] indices = readIndices(input);
            String decompressed = readText(input);
            return deserializeContent(indices, decompressed);
        } catch (IOException e) {
            throw VMError.shouldNotReachHere("Decompressing a resource bundle failed.", e);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static void writeIndices(int[] indices, GZIPOutputStream out) throws IOException {
        byte[] indicesInBytes = intsToBytes(indices);
        writeInt(out, indicesInBytes.length);
        out.write(indicesInBytes);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static void writeText(String text, GZIPOutputStream out) throws IOException {
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        writeInt(out, textBytes.length);
        out.write(textBytes);
    }

    private static int[] readIndices(GZIPInputStream input) throws IOException {
        int indicesInBytesLen = readInt(input);
        byte[] indicesInBytes = new byte[indicesInBytesLen];
        int realIntsRead = readNBytes(input, indicesInBytes);
        assert realIntsRead == indicesInBytesLen : "Not enough indices bytes read";
        return bytesToInts(indicesInBytes);
    }

    private static String readText(GZIPInputStream input) throws IOException {
        int remainingBytesSize = readInt(input);
        byte[] stringBytes = new byte[remainingBytesSize];
        int allBytesRead = readNBytes(input, stringBytes);
        assert allBytesRead == remainingBytesSize : "Not enough indices bytes read";
        return new String(stringBytes, StandardCharsets.UTF_8);
    }
}
