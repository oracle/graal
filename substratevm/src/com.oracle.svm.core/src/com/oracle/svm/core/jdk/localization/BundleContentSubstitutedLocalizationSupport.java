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
package com.oracle.svm.core.jdk.localization;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListResourceBundle;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.debug.GraalError;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

// Checkstyle: stop
import sun.util.resources.OpenListResourceBundle;
import sun.util.resources.ParallelListResourceBundle;
// Checkstyle: resume

public class BundleContentSubstitutedLocalizationSupport extends LocalizationSupport {

    private interface StoredBundle {
        Map<String, Object> getContent();
    }

    private static final class ExtractedBundle implements StoredBundle {
        private final Map<String, Object> lookup;

        ExtractedBundle(Map<String, Object> lookup) {
            this.lookup = lookup;
        }

        @Override
        public Map<String, Object> getContent() {
            return lookup;
        }
    }

    private static final class CompressedBundle implements StoredBundle {
        private final byte[] content;
        private final Function<byte[], Map<String, Object>> decompressionAlgorithm;

        private CompressedBundle(byte[] content, Function<byte[], Map<String, Object>> decompressionAlgorithm) {
            this.content = content;
            this.decompressionAlgorithm = decompressionAlgorithm;
        }

        @Override
        public Map<String, Object> getContent() {
            return decompressionAlgorithm.apply(content);
        }
    }

    private final Map<Class<?>, StoredBundle> storedBundles = new ConcurrentHashMap<>();

    private final BundleCompressionAlgorithm compressionAlgorithm = new GzipBundleCompressionAlgorithm();

    public BundleContentSubstitutedLocalizationSupport(Locale defaultLocale, List<Locale> locales) {
        super(defaultLocale, locales);
    }

    @Override
    protected void onBundlePrepared(ResourceBundle bundle) {
        if (isBundleSupported(bundle)) {
            storeBundleContentOf(bundle);
        }
    }

    private void storeBundleContentOf(ResourceBundle bundle) {
        GraalError.guarantee(isBundleSupported(bundle), "Unsupported bundle %s of type %s", bundle, bundle.getClass());
        storedBundles.put(bundle.getClass(), processBundle(bundle));
    }

    private StoredBundle processBundle(ResourceBundle bundle) {
        boolean isInDefaultLocale = bundle.getLocale().equals(defaultLocale);
        if (!isInDefaultLocale) {
            StoredBundle compressed = compressionAlgorithm.compress(bundle);
            if (compressed != null) {
                return compressed;
            }
        }
        Map<String, Object> content = extractContent(bundle);
        return new ExtractedBundle(content);
    }

    @Override
    public Map<String, Object> getBundleContentOf(Class<?> bundleClass) {
        StoredBundle bundle = storedBundles.get(bundleClass);
        if (bundle != null) {
            try {
                return bundle.getContent();
            } catch (Exception ex) {
                // todo remove
// System.err.println("!!!" + bundleClass);
// ex.printStackTrace();
                System.exit(1);
                throw GraalError.shouldNotReachHere(ex, "Decompressing a resource bundle " + bundleClass.getName() + " failed.");
            }
        }
        return super.getBundleContentOf(bundleClass);
    }

    public boolean isBundleSupported(ResourceBundle bundle) {
        return bundle instanceof ListResourceBundle || bundle instanceof OpenListResourceBundle || bundle instanceof ParallelListResourceBundle;
    }

    /**
     * Extracts the content of the bundle by looking up the lookup field. All the jdk internal
     * bundles can be resolved this way, except from the BreakIterators. In the future, it can be
     * extended with a fallback to user defined bundles by using the handleKeySet and
     * handleGetObject methods.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractContent(ResourceBundle bundle) {
        bundle.keySet(); // force lazy initialization
        Class<?> clazz = bundle.getClass().getSuperclass();
        while (clazz != null && ResourceBundle.class.isAssignableFrom(clazz)) {
            try {
                return (Map<String, Object>) ReflectionUtil.lookupField(clazz, "lookup").get(bundle);
            } catch (ReflectionUtil.ReflectionUtilError | ReflectiveOperationException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw VMError.shouldNotReachHere("Failed to extract content for " + bundle + " of type " + bundle.getClass());
    }

    private abstract static class BundleCompressionAlgorithm {
        public abstract CompressedBundle compress(ResourceBundle bundle);

        protected Pair<String, int[]> serializeContent(Map<String, Object> content) {
            List<Integer> indices = new ArrayList<>();
            StringBuilder builder = new StringBuilder();
            for (Map.Entry<String, Object> entry : content.entrySet()) {
                String key = entry.getKey();
                builder.append(key);
                Object value = entry.getValue();
                if (value instanceof String) {
                    builder.append(value);
                    indices.add(-1);
                    indices.add(key.length());
                    indices.add(((String) value).length());
                } else if (value instanceof Object[]) {
                    Object[] arr = (Object[]) value;
                    indices.add(arr.length);
                    indices.add(key.length());
                    for (Object o : arr) {
                        if (!(o instanceof String)) {
                            return null;
                        }
                        builder.append(o);
                        indices.add(((String) o).length());
                    }
                } else {
                    return null;
                }
            }
            int[] res = new int[indices.size()];
            for (int i = 0; i < indices.size(); i++) {
                res[i] = indices.get(i);
            }
            return Pair.create(builder.toString(), res);
        }
    }

    static class GzipBundleCompressionAlgorithm extends BundleCompressionAlgorithm {
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
                return new CompressedBundle(byteStream.toByteArray(), GzipBundleCompressionAlgorithm::decompressBundle);
            } catch (IOException ex) {
                // if the compression fails for some reason, the bundle can still be saved
                // uncompressed
                // todo log this as a warning?
                return null;
            }
        }

        private static void writeIndices(int[] indices, GZIPOutputStream out) throws IOException {
            byte[] indicesInBytes = intsToBytes(indices);
            writeInt(out, indicesInBytes.length);
            out.write(indicesInBytes);
        }

        private static void writeText(String text, GZIPOutputStream out) throws IOException {
            byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
            writeInt(out, textBytes.length);
            out.write(textBytes);
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

        private static Map<String, Object> deserializeContent(int[] indices, String text) {
            Map<String, Object> content = new HashMap<>();
            int i = 0;
            int offset = 0;
            while (i < indices.length) {
                int valueCnt = indices[i++];
                int keyLen = indices[i++];
                String key = text.substring(offset, offset + keyLen);
                offset += keyLen;
                boolean isArray = valueCnt != -1;
                if (isArray) {
                    Object[] values = new String[valueCnt];
                    for (int j = 0; j < valueCnt; j++) {
                        int valueLen = indices[i++];
                        values[j] = text.substring(offset, offset + valueLen);
                        offset += valueLen;
                    }
                    content.put(key, values);
                } else {
                    int valueLen = indices[i++];
                    String value = text.substring(offset, offset + valueLen);
                    offset += valueLen;
                    content.put(key, value);
                }
            }
            return content;
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

        private static byte[] intsToBytes(int[] data) {
            ByteBuffer byteBuffer = ByteBuffer.allocate(data.length * 4);
            IntBuffer intBuffer = byteBuffer.asIntBuffer();
            intBuffer.put(data);
            return byteBuffer.array();
        }

        private static int[] bytesToInts(byte[] data) {
            IntBuffer intBuf = ByteBuffer.wrap(data)
                            .order(ByteOrder.BIG_ENDIAN)
                            .asIntBuffer();
            int[] array = new int[intBuf.remaining()];
            intBuf.get(array);
            return array;
        }

        private static int readInt(InputStream stream) throws IOException {
            return stream.read() << 24 | stream.read() << 16 | stream.read() << 8 | stream.read();
        }

        private static void writeInt(OutputStream stream, int value) throws IOException {
            stream.write((byte) (value >>> 24));
            stream.write((byte) (value >>> 16));
            stream.write((byte) (value >>> 8));
            stream.write((byte) value);
        }

        private static int readNBytes(InputStream input, byte[] dst) throws IOException {
            int remaining = dst.length;
            int offset = 0;
            int bytesRead = input.read(dst, 0, remaining);
            while (bytesRead > 0) {
                offset += bytesRead;
                remaining -= bytesRead;
                bytesRead = input.read(dst, offset, remaining);
            }
            return offset;
        }
    }
}
