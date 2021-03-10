/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Map;
import java.util.ResourceBundle;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.debug.GraalError;

import com.oracle.svm.core.jdk.localization.bundles.CompressedBundle;

public class GzipBundleCompression implements BundleCompressionAlgorithm {

    @Override
    public CompressedBundle compress(ResourceBundle bundle) {
        final Map<String, Object> content = extractContent(bundle);
        Pair<String, int[]> input = serializeContent(content);
        if (input == null) {
            return null;
        }
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream(); GZIPOutputStream out = new GZIPOutputStream(byteStream)) {
            writeIndices(input.getRight(), out);
            writeText(input.getLeft(), out);
            out.finish();
            return new CompressedBundle(byteStream.toByteArray(), GzipBundleCompression::decompressBundle);
        } catch (IOException ex) {
            /*- If the compression fails for some reason, the bundle can still be saved uncompressed. */
            return null;
        }
    }

    private static Map<String, Object> decompressBundle(byte[] data) {
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(data))) {
            int[] indices = readIndices(input);
            String decompressed = readText(input);
            assert input.available() == 0 : "Input not fully consumed";
            return deserializeContent(indices, decompressed);
        } catch (IOException e) {
            throw GraalError.shouldNotReachHere(e, "Decompressing a resource bundle failed.");
        }
    }

    public static void writeIndices(int[] indices, GZIPOutputStream out) throws IOException {
        byte[] indicesInBytes = intsToBytes(indices);
        writeInt(out, indicesInBytes.length);
        out.write(indicesInBytes);
    }

    public static void writeText(String text, GZIPOutputStream out) throws IOException {
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        writeInt(out, textBytes.length);
        out.write(textBytes);
    }

    public static int[] readIndices(GZIPInputStream input) throws IOException {
        int indicesInBytesLen = readInt(input);
        byte[] indicesInBytes = new byte[indicesInBytesLen];
        int realIntsRead = readNBytes(input, indicesInBytes);
        assert realIntsRead == indicesInBytesLen : "Not enough indices bytes read";
        return bytesToInts(indicesInBytes);
    }

    public static String readText(GZIPInputStream input) throws IOException {
        int remainingBytesSize = readInt(input);
        byte[] stringBytes = new byte[remainingBytesSize];
        int allBytesRead = readNBytes(input, stringBytes);
        assert allBytesRead == remainingBytesSize : "Not enough indices bytes read";
        return new String(stringBytes, StandardCharsets.UTF_8);
    }
}
