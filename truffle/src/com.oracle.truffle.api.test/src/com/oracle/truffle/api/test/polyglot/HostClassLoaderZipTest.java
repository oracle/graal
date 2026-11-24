/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import com.oracle.truffle.api.test.TestAPIAccessor;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.test.polyglot.FileSystemsTest.ForwardingFileSystem;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class HostClassLoaderZipTest extends AbstractPolyglotTest {

    private static final String ENTRY_NAME = "entry";
    private static final int SIZE64 = 1;
    private static final int COMPRESSED_SIZE64 = 2;
    private static final int OFFSET64 = 4;
    private static final int DISK64 = 8;
    private static final int ZIP64_ENDHDR = 56;
    private static final int ZIP64_LOCHDR = 20;
    private static final String CLASS_NAME = HostClassLoadingTestClass1.class.getPackageName() + "." + HostClassLoadingTest.TEST_REPLACE_CLASS_NAME;
    private static final String[] NAMES = {CLASS_NAME.replace('.', '/') + ".class", "empty", "bytes", "zero-crc", "large", "normal"};
    private TrackingFileSystem fileSystem;

    public HostClassLoaderZipTest() {
        needsLanguageEnv = true;
    }

    @BeforeClass
    public static void setupClass() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        TruffleTestAssumptions.assumeNotAOT();
    }

    @Before
    public void initializeFileSystem() throws IOException {
        fileSystem = new TrackingFileSystem();
        IOAccess io = IOAccess.newBuilder().fileSystem(fileSystem).build();
        setupEnv(Context.newBuilder(ProxyLanguage.ID).allowAllAccess(true).allowIO(io).build());
    }

    @Test
    public void testValidZip64ExtraFields() throws Exception {
        assertIndexed(createZip(0, new byte[0]));
        for (int flags = 0; flags < 16; flags++) {
            byte[] extra = zip64Extra(flags);
            assertIndexed(createZip(flags, extra));
            // All non-negative long values are allowed by validation. Reading large entries is
            // separate from validating their metadata.
            ByteBuffer data = ByteBuffer.wrap(extra).order(ByteOrder.LITTLE_ENDIAN);
            for (int offset = 4; offset + Long.BYTES <= extra.length; offset += Long.BYTES) {
                data.putLong(offset, Long.MAX_VALUE);
            }
            assertIndexed(createZip(flags, extra));
        }
    }

    @Test
    public void testMissingZip64ExtraFields() throws Exception {
        for (int flags = 1; flags < 16; flags++) {
            assertInvalid(createZip(flags, new byte[0]), "missing ZIP64 extra data field");
        }
    }

    @Test
    public void testInvalidZip64ExtraFieldSizes() throws Exception {
        for (int flags = 0; flags < 16; flags++) {
            int expectedLength = zip64Extra(flags).length - 4;
            for (int length = 0; length <= 29; length++) {
                if (length != expectedLength) {
                    assertInvalid(createZip(flags, extraField(1, new byte[length])), "invalid ZIP64 extra data field size");
                }
            }
        }
    }

    @Test
    public void testNegativeZip64Values() throws Exception {
        String[] messages = {"block size value", "block compressed size value", "block LOC offset value"};
        for (int flags = 1; flags < 16; flags++) {
            int offset = 4;
            for (int field = 0; field < 3; field++) {
                if ((flags & (1 << field)) != 0) {
                    for (long value : new long[]{-1, Long.MIN_VALUE}) {
                        byte[] extra = zip64Extra(flags);
                        ByteBuffer.wrap(extra).order(ByteOrder.LITTLE_ENDIAN).putLong(offset, value);
                        assertInvalid(createZip(flags, extra), messages[field]);
                    }
                    offset += Long.BYTES;
                }
            }
        }
    }

    @Test
    public void testUnknownAndTrailingExtraFields() throws Exception {
        byte[] unknown = extraField(0xcafe, new byte[]{1, 2, 3});
        byte[] emptyUnknown = extraField(0xbeef, new byte[0]);
        assertIndexed(createZip(0, unknown));
        assertIndexed(createZip(0, emptyUnknown));
        for (int flags = 0; flags < 16; flags++) {
            byte[] zip64 = zip64Extra(flags);
            // JDK-8313765 tolerates incomplete trailing headers, including after ZIP64 fields.
            for (int padding = 0; padding < 4; padding++) {
                ByteBuffer extra = ByteBuffer.allocate(unknown.length + zip64.length + emptyUnknown.length + padding);
                extra.put(unknown).put(zip64).put(emptyUnknown);
                assertIndexed(createZip(flags, extra.array()));
            }
        }
        for (int padding = 1; padding < 4; padding++) {
            assertIndexed(createZip(0, new byte[padding]));
        }
    }

    @Test
    public void testExtraFieldBlockOverrun() throws Exception {
        for (int tag : new int[]{1, 0xcafe}) {
            for (int length : new int[]{0, 1, 8}) {
                byte[] extra = extraField(tag, new byte[length]);
                ByteBuffer.wrap(extra).order(ByteOrder.LITTLE_ENDIAN).putShort(2, (short) (length + 1));
                assertInvalid(createZip(0, extra), "invalid extra data field size");
            }
        }
    }

    @Test
    public void testExtraFieldExceedsCentralDirectory() throws Exception {
        byte[] zip = createZip(0, extraField(0xcafe, new byte[0]));
        ByteBuffer data = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
        int cenOffset = data.getInt(zip.length - ZipFile.ENDHDR + ZipFile.ENDOFF);
        // The next byte exists, but belongs to END rather than to this CEN extra field.
        data.putShort(cenOffset + ZipFile.CENEXT, (short) 5);
        assertInvalid(zip, "bad header size");
    }

    @Test
    public void testTruncatedExtraFields() throws Exception {
        byte[] zip = createZip(0, extraField(0xcafe, new byte[0]));
        ByteBuffer data = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
        int endOffset = zip.length - ZipFile.ENDHDR;
        int cenOffset = data.getInt(endOffset + ZipFile.ENDOFF);
        // Let the declared extra field extend beyond the physical file, while still fitting
        // within the declared CEN region. END validation must reject the inconsistent offset.
        int extraLength = 4 + ZipFile.ENDHDR + 1;
        data.putShort(cenOffset + ZipFile.CENEXT, (short) extraLength);
        data.putInt(endOffset + ZipFile.ENDSIZ, ZipFile.CENHDR + ENTRY_NAME.length() + extraLength);
        assertInvalid(zip, "bad central directory offset");
    }

    @Test
    public void testDirectoryExtraFieldsValidated() throws Exception {
        byte[] extra = extraField(0xcafe, new byte[0]);
        ByteBuffer.wrap(extra).order(ByteOrder.LITTLE_ENDIAN).putShort(2, (short) 1);
        assertInvalid(createZip(0, extra, "directory/"), "invalid extra data field size");
    }

    @Test
    public void testCentralDirectoryRecordBounds() throws Exception {
        for (int field : new int[]{ZipFile.CENNAM, ZipFile.CENEXT, ZipFile.CENCOM}) {
            for (int increment : new int[]{1, 0xff00}) {
                byte[] zip = createZip(0, new byte[0]);
                ByteBuffer data = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
                int cenOffset = data.getInt(zip.length - ZipFile.ENDHDR + ZipFile.ENDOFF);
                int length = Short.toUnsignedInt(data.getShort(cenOffset + field));
                data.putShort(cenOffset + field, (short) (length + increment));
                assertInvalid(zip, "bad header size");
            }
        }
        for (int cenSize : new int[]{1, ZipFile.CENHDR - 1, ZipFile.CENHDR, ZipFile.CENHDR + ENTRY_NAME.length() - 1}) {
            byte[] zip = createZip(0, new byte[0]);
            ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN).putInt(zip.length - ZipFile.ENDHDR + ZipFile.ENDSIZ, cenSize);
            // A smaller declared CEN moves its inferred start into the existing record.
            assertInvalid(zip, cenSize < ZipFile.CENHDR ? "bad header size" : "bad signature");
        }
    }

    @Test
    public void testCentralDirectoryHeaderSizeLimit() throws Exception {
        int maxCommentLength = 0xffff - ZipFile.CENHDR - ENTRY_NAME.length();
        assertIndexed(createZip(0, new byte[0], ENTRY_NAME, new byte[maxCommentLength]));
        assertInvalid(createZip(0, new byte[0], ENTRY_NAME, new byte[maxCommentLength + 1]), "bad header size");
        // Each variable-length field fits in an unsigned short, but their combined record does not.
        byte[] extra = extraField(0xcafe, new byte[32764]);
        assertInvalid(createZip(0, extra, ENTRY_NAME, new byte[32768]), "bad header size");
    }

    @Test
    public void testCentralDirectoryTrailingBytes() throws Exception {
        byte[] original = createZip(0, new byte[0]);
        int endOffset = original.length - ZipFile.ENDHDR;
        int cenSize = ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN).getInt(endOffset + ZipFile.ENDSIZ);
        for (int length : new int[]{1, ZipFile.CENHDR - 1}) {
            ByteBuffer data = ByteBuffer.allocate(original.length + length).order(ByteOrder.LITTLE_ENDIAN);
            data.put(original, 0, endOffset).put(new byte[length]).put(original, endOffset, ZipFile.ENDHDR);
            data.putInt(endOffset + length + ZipFile.ENDSIZ, cenSize + length);
            assertInvalid(data.array(), "bad header size");
        }
    }

    @Test
    public void testTruncatedCentralDirectoryHeader() throws Exception {
        byte[] zip = createZip(0, new byte[0]);
        ByteBuffer data = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
        int endOffset = zip.length - ZipFile.ENDHDR;
        int cenSize = data.getInt(endOffset + ZipFile.ENDSIZ);
        // The declared directory includes another fixed header, but only END bytes remain.
        // END validation rejects this impossible size before attempting to read that header.
        data.putInt(endOffset + ZipFile.ENDSIZ, cenSize + ZipFile.CENHDR);
        assertInvalid(zip, "bad central directory size");
    }

    @Test
    public void testTruncatedEntryName() throws Exception {
        byte[] zip = createZip(0, new byte[0]);
        ByteBuffer data = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
        int endOffset = zip.length - ZipFile.ENDHDR;
        int cenOffset = data.getInt(endOffset + ZipFile.ENDOFF);
        int nameLength = ENTRY_NAME.length() + ZipFile.ENDHDR + 1;
        data.putShort(cenOffset + ZipFile.CENNAM, (short) nameLength);
        data.putInt(endOffset + ZipFile.ENDSIZ, ZipFile.CENHDR + nameLength);
        assertInvalid(zip, "bad central directory offset");
    }

    @Test
    public void testInvalidCentralDirectorySignature() throws Exception {
        byte[] zip = createZip(0, new byte[0]);
        ByteBuffer data = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
        int cenOffset = data.getInt(zip.length - ZipFile.ENDHDR + ZipFile.ENDOFF);
        data.putInt(cenOffset, 0);
        assertInvalid(zip, "bad signature");
    }

    @Test
    public void testUnsupportedCompressionMethods() throws Exception {
        for (int method : new int[]{1, 9, 12, 99, 0xffff}) {
            byte[] zip = createZip(0, new byte[0]);
            ByteBuffer data = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
            int cenOffset = data.getInt(zip.length - ZipFile.ENDHDR + ZipFile.ENDOFF);
            data.putShort(cenOffset + ZipFile.CENHOW, (short) method);
            assertInvalid(zip, "bad compression method: " + method);
        }
    }

    @Test
    public void testEncryptedEntries() throws Exception {
        for (String name : new String[]{ENTRY_NAME, "directory/"}) {
            byte[] zip = createZip(0, new byte[0], name);
            ByteBuffer data = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
            int cenOffset = data.getInt(zip.length - ZipFile.ENDHDR + ZipFile.ENDOFF);
            data.putShort(cenOffset + ZipFile.CENFLG, (short) (data.getShort(cenOffset + ZipFile.CENFLG) | 1));
            assertInvalid(zip, "encrypted entry");
        }
    }

    @Test
    public void testValidEntryComments() throws Exception {
        for (String comment : new String[]{"", "ASCII comment", "\u0000\u03b1\u20ac\ud83d\ude00"}) {
            byte[] extra = extraField(0xcafe, new byte[]{(byte) 0xff});
            assertIndexed(createZip(0, extra, ENTRY_NAME, comment.getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Test
    public void testInvalidEntryComments() throws Exception {
        byte[][] comments = {{(byte) 0xff}, {(byte) 0xc3}, {(byte) 0xc3, 0x28}, {(byte) 0xc0, (byte) 0xaf}};
        for (String name : new String[]{ENTRY_NAME, "directory/"}) {
            for (byte[] comment : comments) {
                assertInvalid(createZip(0, new byte[0], name, comment), "bad entry comment");
            }
        }
    }

    @Test
    public void testTruncatedEntryComment() throws Exception {
        byte[] zip = createZip(0, new byte[0], ENTRY_NAME, new byte[1]);
        ByteBuffer data = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
        int endOffset = zip.length - ZipFile.ENDHDR;
        int cenOffset = data.getInt(endOffset + ZipFile.ENDOFF);
        int commentLength = 1 + ZipFile.ENDHDR + 1;
        data.putShort(cenOffset + ZipFile.CENCOM, (short) commentLength);
        data.putInt(endOffset + ZipFile.ENDSIZ, ZipFile.CENHDR + ENTRY_NAME.length() + commentLength);
        assertInvalid(zip, "bad central directory offset");
    }

    @Test
    public void testMultipleEntriesWithComments() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String name : new String[]{ENTRY_NAME, "second"}) {
                ZipEntry entry = new ZipEntry(name);
                entry.setExtra(extraField(0xcafe, new byte[]{1, 2, 3}));
                entry.setComment("Entry comment: \u03b1\u20ac\ud83d\ude00");
                zip.putNextEntry(entry);
                zip.write(new byte[]{1, 2, 3});
                zip.closeEntry();
            }
        }
        Map<?, ?> root = (Map<?, ?>) readEntries(out.toByteArray()).get("");
        assertNotNull(root);
        assertEquals(2, root.size());
        assertTrue(root.containsKey(ENTRY_NAME));
        assertTrue(root.containsKey("second"));
    }

    @Test
    public void testValidUtf8EntryNames() throws Exception {
        for (String name : new String[]{ENTRY_NAME, "\u03b1\u20ac\ud83d\ude00"}) {
            Map<?, ?> root = (Map<?, ?>) readEntries(createZip(0, new byte[0], name)).get("");
            assertNotNull(root);
            assertTrue(root.containsKey(name));
        }
    }

    @Test
    public void testInvalidUtf8EntryNames() throws Exception {
        byte[][] invalidNames = {{(byte) 0xff}, {(byte) 0xc3}, {(byte) 0xc3, 0x28}, {(byte) 0xc0, (byte) 0xaf},
                        {(byte) 0xed, (byte) 0xa0, (byte) 0x80}, {(byte) 0xf4, (byte) 0x90, (byte) 0x80, (byte) 0x80}};
        for (int utf8Flag : new int[]{0, 0x800}) {
            for (byte[] invalidName : invalidNames) {
                byte[] zip = createZip(0, new byte[0]);
                ByteBuffer data = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
                int cenOffset = data.getInt(zip.length - ZipFile.ENDHDR + ZipFile.ENDOFF);
                data.putShort(cenOffset + ZipFile.CENFLG, (short) utf8Flag);
                // Keep the lengths intact, including a truncated sequence at the end of the name.
                data.position(cenOffset + ZipFile.CENHDR + ENTRY_NAME.length() - invalidName.length);
                data.put(invalidName);
                assertInvalid(zip, "bad entry name");
            }
        }
        byte[] directory = createZip(0, new byte[0], "directory/");
        ByteBuffer data = ByteBuffer.wrap(directory).order(ByteOrder.LITTLE_ENDIAN);
        int cenOffset = data.getInt(directory.length - ZipFile.ENDHDR + ZipFile.ENDOFF);
        directory[cenOffset + ZipFile.CENHDR] = (byte) 0xff;
        assertInvalid(directory, "bad entry name");
    }

    @Test
    public void testArchiveCommentsAndEmptyArchives() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.setComment("");
        }
        for (int length : new int[]{0, 1, 0xffff}) {
            byte[] comment = new byte[length];
            assertIndexed(withArchiveComment(createZip(0, new byte[0]), comment));
            assertTrue(readEntries(withArchiveComment(out.toByteArray(), comment)).isEmpty());
        }
        for (boolean saturated : new boolean[]{false, true}) {
            assertTrue(readEntries(withZip64End(out.toByteArray(), saturated, 0)).isEmpty());
        }
    }

    @Test
    public void testFalseEndSignaturesInArchiveComment() throws Exception {
        byte[] comment = new byte[64];
        ByteBuffer data = ByteBuffer.wrap(comment).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(4, (int) ZipFile.ENDSIG);
        data.putShort(4 + ZipFile.ENDCOM, (short) 0xffff);
        int fakeEnd = comment.length - ZipFile.ENDHDR - 1;
        data.putInt(fakeEnd, (int) ZipFile.ENDSIG);
        // This candidate leaves one unexplained byte and has no supporting CEN/LOC structure.
        assertIndexed(withArchiveComment(createZip(0, new byte[0]), comment));
    }

    @Test
    public void testTruncatedArchiveComment() throws Exception {
        byte[] zip = withArchiveComment(createZip(0, new byte[0]), new byte[5]);
        ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN).putShort(zip.length - 5 - ZipFile.ENDHDR + ZipFile.ENDCOM, (short) 10);
        assertInvalid(zip, "ZIP end header not found");
    }

    @Test
    public void testTrailingArchivePadding() throws Exception {
        for (boolean zip64 : new boolean[]{false, true}) {
            byte[] zip = createZip(0, new byte[0]);
            if (zip64) {
                zip = withZip64End(zip, true, 0);
            }
            zip = withArchiveComment(zip, new byte[7]);
            for (int length : new int[]{1, 37}) {
                assertIndexed(Arrays.copyOf(zip, zip.length + length));
            }
        }
    }

    @Test
    public void testTrailingPaddingRequiresStructure() throws Exception {
        for (boolean corruptLocalHeader : new boolean[]{false, true}) {
            byte[] zip = createZip(0, new byte[0]);
            ByteBuffer data = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
            int cenOffset = data.getInt(zip.length - ZipFile.ENDHDR + ZipFile.ENDOFF);
            data.putInt(corruptLocalHeader ? 0 : cenOffset, 0);
            assertInvalid(Arrays.copyOf(zip, zip.length + 1), "ZIP end header not found");
        }
    }

    @Test
    public void testEndDirectoryBounds() throws Exception {
        for (int field : new int[]{ZipFile.ENDSIZ, ZipFile.ENDOFF}) {
            for (int value : new int[]{-1, 0x7fffffff}) {
                byte[] zip = createZip(0, new byte[0]);
                ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN).putInt(zip.length - ZipFile.ENDHDR + field, value);
                assertInvalid(zip, field == ZipFile.ENDSIZ ? "bad central directory size" : "bad central directory offset");
            }
        }
        ByteBuffer empty = ByteBuffer.allocate(ZipFile.ENDHDR).order(ByteOrder.LITTLE_ENDIAN);
        empty.putInt(0, (int) ZipFile.ENDSIG).putInt(ZipFile.ENDSIZ, 1);
        assertInvalid(empty.array(), "bad central directory size");
    }

    @Test
    public void testPrefixedArchives() throws Exception {
        int prefixLength = 17;
        for (boolean zip64 : new boolean[]{false, true}) {
            byte[] original = createZip(0, new byte[0]);
            if (zip64) {
                original = withZip64End(original, true, 0);
            }
            ByteBuffer data = ByteBuffer.allocate(prefixLength + original.length).order(ByteOrder.LITTLE_ENDIAN);
            data.position(prefixLength);
            data.put(original);
            if (zip64) {
                int locator = data.capacity() - ZipFile.ENDHDR - ZIP64_LOCHDR;
                data.putLong(locator + 8, data.getLong(locator + 8) + prefixLength);
            }
            Map<?, ?> root = (Map<?, ?>) readEntries(data.array()).get("");
            assertNotNull(root);
            assertEquals((long) prefixLength, root.get(ENTRY_NAME));
        }
    }

    @Test
    public void testZip64EndRecords() throws Exception {
        for (int saturatedFields = 0; saturatedFields < 8; saturatedFields++) {
            for (int extensionLength : new int[]{0, 13}) {
                byte[] zip = withZip64End(createZip(0, new byte[0]), false, extensionLength);
                ByteBuffer data = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
                int end = zip.length - ZipFile.ENDHDR;
                if ((saturatedFields & 1) != 0) {
                    data.putInt(end + ZipFile.ENDSIZ, -1);
                }
                if ((saturatedFields & 2) != 0) {
                    data.putInt(end + ZipFile.ENDOFF, -1);
                }
                if ((saturatedFields & 4) != 0) {
                    data.putShort(end + ZipFile.ENDTOT, (short) 0xffff);
                }
                assertIndexed(zip);
                assertIndexed(withArchiveComment(zip, new byte[0xffff]));
            }
        }
    }

    @Test
    public void testInvalidZip64LocatorOffsets() throws Exception {
        for (boolean saturated : new boolean[]{false, true}) {
            for (long position : new long[]{-1, Long.MIN_VALUE, Long.MAX_VALUE, 0x100000000L}) {
                byte[] zip = withZip64End(createZip(0, new byte[0]), saturated, 0);
                int locator = zip.length - ZipFile.ENDHDR - ZIP64_LOCHDR;
                ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN).putLong(locator + 8, position);
                assertInvalid(zip, "bad record offset");
            }
        }
    }

    @Test
    public void testInvalidZip64EndSignature() throws Exception {
        byte[] zip = withZip64End(createZip(0, new byte[0]), true, 0);
        int zip64End = zip.length - ZipFile.ENDHDR - ZIP64_LOCHDR - ZIP64_ENDHDR;
        ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN).putInt(zip64End, 0);
        assertInvalid(zip, "bad signature or truncated record");
    }

    @Test
    public void testInvalidZip64EndRecordLengths() throws Exception {
        for (long length : new long[]{-1, 0, 43, 45, Long.MAX_VALUE}) {
            byte[] zip = withZip64End(createZip(0, new byte[0]), true, 0);
            int zip64End = zip.length - ZipFile.ENDHDR - ZIP64_LOCHDR - ZIP64_ENDHDR;
            ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN).putLong(zip64End + 4, length);
            assertInvalid(zip, "bad record size");
        }
    }

    @Test
    public void testInvalidZip64DirectoryBounds() throws Exception {
        for (int field : new int[]{40, 48}) {
            for (long value : new long[]{-1, Long.MIN_VALUE, Long.MAX_VALUE}) {
                byte[] zip = withZip64End(createZip(0, new byte[0]), true, 0);
                int zip64End = zip.length - ZipFile.ENDHDR - ZIP64_LOCHDR - ZIP64_ENDHDR;
                ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN).putLong(zip64End + field, value);
                assertInvalid(zip, field == 40 ? "bad central directory size" : "bad central directory offset");
            }
        }
    }

    @Test
    public void testInconsistentZip64EndValues() throws Exception {
        for (int field : new int[]{32, 40, 48}) {
            byte[] zip = withZip64End(createZip(0, new byte[0]), false, 0);
            int zip64End = zip.length - ZipFile.ENDHDR - ZIP64_LOCHDR - ZIP64_ENDHDR;
            ByteBuffer data = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
            long value = data.getLong(zip64End + field);
            data.putLong(zip64End + field, field == 32 ? value + 1 : value - 1);
            assertInvalid(zip, "inconsistent END values");
        }
    }

    @Test
    public void testMissingZip64EndRecord() throws Exception {
        byte[] zip = createZip(0, new byte[0]);
        ByteBuffer data = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
        int end = zip.length - ZipFile.ENDHDR;
        data.putInt(end + ZipFile.ENDSIZ, -1).putInt(end + ZipFile.ENDOFF, -1);
        assertInvalid(zip, "bad central directory size");
    }

    @Test
    public void testEndCountsDoNotControlAllocation() throws Exception {
        byte[] zip = withZip64End(createZip(0, new byte[0]), true, 0);
        int zip64End = zip.length - ZipFile.ENDHDR - ZIP64_LOCHDR - ZIP64_ENDHDR;
        ByteBuffer data = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
        data.putLong(zip64End + 32, Long.MAX_VALUE);
        assertIndexed(zip);
        data.putLong(zip64End + 32, -1);
        assertInvalid(zip, "negative entry count");
        zip = createZip(0, new byte[0]);
        ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN).putShort(zip.length - ZipFile.ENDHDR + ZipFile.ENDTOT, (short) 0xffff);
        assertIndexed(zip);
    }

    @Test
    public void testEndSearchBoundedByFileStart() throws Exception {
        for (int length : new int[]{0, 1, ZipFile.ENDHDR - 1, ZipFile.ENDHDR, ZipFile.ENDHDR + 1}) {
            assertInvalid(new byte[length], "ZIP end header not found");
        }
    }

    @Test
    public void testStoredDescriptors() throws Exception {
        for (boolean signed : new boolean[]{false, true}) {
            for (boolean zip64 : new boolean[]{false, true}) {
                initializeFileSystem();
                Archive archive = createArchive(signed, zip64);
                assertResources(openArchive(archive), archive);
                assertEquals(0, fileSystem.openChannels);
            }
        }
    }

    @Test
    public void testStoredDescriptorsOnDisk() throws Exception {
        setupEnv();
        Path path = Files.createTempFile("stored-descriptors", ".jar");
        try {
            Archive archive = createArchive(true, false);
            assertResources(openArchive(archive, languageEnv.getPublicTruffleFile(path.toString())), archive);
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    public void testUnsignedReadsAndStreamContracts() throws Exception {
        Archive archive = createArchive(false, false);
        ClassLoader loader = openArchive(archive);
        try (InputStream stream = resource(loader, 2).openStream()) {
            assertEquals(4, stream.available());
            assertEquals(0, stream.read());
            assertEquals(0x7f, stream.read());
            assertEquals(0x80, stream.read());
            assertEquals(0xff, stream.read());
            assertEquals(0, stream.available());
            assertEquals(-1, stream.read());
            assertEquals(0, stream.read(new byte[0]));
            assertThrows(IndexOutOfBoundsException.class, () -> stream.read(new byte[1], 1, 1));
            assertThrows(NullPointerException.class, () -> stream.read(null, 0, 0));
        }
        assertEquals(0, fileSystem.openChannels);
    }

    @Test
    public void testSkipAndIndependentStreams() throws Exception {
        Archive archive = createArchive(true, false);
        ClassLoader loader = openArchive(archive);
        try (InputStream first = resource(loader, 2).openStream(); InputStream second = resource(loader, 2).openStream()) {
            assertEquals(2, first.skip(2));
            assertEquals(0x80, first.read());
            assertEquals(1, first.skip(Long.MAX_VALUE));
            assertEquals(-1, first.read());
            assertArrayEquals(archive.contents()[2], second.readAllBytes());
        }
        assertEquals(0, fileSystem.openChannels);
    }

    @Test
    public void testShortChannelReads() throws Exception {
        fileSystem.readLimit = 1;
        Archive archive = createArchive(true, true);
        assertResources(openArchive(archive), archive);
        assertEquals(0, fileSystem.openChannels);
    }

    @Test
    public void testDefaultPathReadsLocalHeaderOnce() throws Exception {
        Archive archive = createArchive(true, false);
        ClassLoader loader = openArchive(archive);
        try (InputStream stream = resource(loader, 5).openStream()) {
            TrackingChannel channel = fileSystem.lastChannel;
            assertArrayEquals(archive.contents()[5], stream.readAllBytes());
            assertEquals(1, Collections.frequency(channel.readPositions, (long) archive.localOffsets()[5]));
        }
        assertEquals(0, fileSystem.openChannels);
    }

    @Test
    public void testInvalidLocalHeadersCloseChannel() throws Exception {
        for (int field : new int[]{0, ZipFile.LOCHOW, ZipFile.LOCFLG, ZipFile.LOCNAM, ZipFile.LOCEXT}) {
            Archive archive = createArchive(true, false);
            ByteBuffer data = ByteBuffer.wrap(archive.bytes()).order(ByteOrder.LITTLE_ENDIAN);
            int offset = archive.localOffsets()[2];
            if (field == 0) {
                data.putInt(offset, 0);
            } else {
                data.putShort(offset + field, (short) (field == ZipFile.LOCHOW ? ZipEntry.DEFLATED : field == ZipFile.LOCFLG ? 0x801 : 0xffff));
            }
            ClassLoader loader = openArchive(archive);
            assertThrows(ZipException.class, () -> resource(loader, 2).openStream());
            assertEquals(0, fileSystem.openChannels);
            initializeFileSystem();
        }
    }

    @Test
    public void testMissingLocalDescriptorFlag() throws Exception {
        Archive archive = createArchive(false, false);
        ByteBuffer.wrap(archive.bytes()).order(ByteOrder.LITTLE_ENDIAN).putShort(archive.localOffsets()[2] + ZipFile.LOCFLG, (short) 0x800);
        ClassLoader loader = openArchive(archive);
        assertThrows(ZipException.class, () -> resource(loader, 2).openStream());
        assertEquals(0, fileSystem.openChannels);
    }

    @Test
    public void testCorruptPayloadCheckedOnLastRead() throws Exception {
        Archive archive = createArchive(true, false);
        archive.bytes()[archive.dataOffsets()[2]] ^= 1;
        ClassLoader loader = openArchive(archive);
        try (InputStream stream = resource(loader, 2).openStream()) {
            ZipException error = assertThrows(ZipException.class, () -> stream.readNBytes(archive.contents()[2].length));
            assertTrue(error.getMessage().contains("CRC or size mismatch"));
        }
        assertEquals(0, fileSystem.openChannels);
    }

    @Test
    public void testCorruptDescriptors() throws Exception {
        for (boolean signed : new boolean[]{false, true}) {
            for (boolean zip64 : new boolean[]{false, true}) {
                for (int field = 0; field < 3; field++) {
                    Archive archive = createArchive(signed, zip64);
                    int offset = archive.descriptorOffsets()[2] + (signed ? 4 : 0) + (field == 0 ? 0 : 4 + (field - 1) * (zip64 ? 8 : 4));
                    archive.bytes()[offset] ^= 1;
                    ClassLoader loader = openArchive(archive);
                    try (InputStream stream = resource(loader, 2).openStream()) {
                        assertThrows(ZipException.class, stream::readAllBytes);
                    }
                    assertEquals(0, fileSystem.openChannels);
                    initializeFileSystem();
                }
            }
        }
    }

    @Test
    public void testCorruptEmptyDescriptorClosesChannel() throws Exception {
        Archive archive = createArchive(true, false);
        archive.bytes()[archive.descriptorOffsets()[1] + 4] ^= 1;
        ClassLoader loader = openArchive(archive);
        assertThrows(ZipException.class, () -> resource(loader, 1).openStream());
        assertEquals(0, fileSystem.openChannels);
    }

    @Test
    public void testTruncatedHeaderClosesChannel() throws Exception {
        Archive archive = createArchive(true, false);
        ClassLoader loader = openArchive(archive);
        fileSystem.readEnd = archive.localOffsets()[2] + ZipFile.LOCHDR - 1;
        assertThrows(ZipException.class, () -> resource(loader, 2).openStream());
        assertEquals(0, fileSystem.openChannels);
    }

    @Test
    public void testTruncatedDataAndDescriptor() throws Exception {
        for (boolean descriptor : new boolean[]{false, true}) {
            Archive archive = createArchive(false, false);
            ClassLoader loader = openArchive(archive);
            try (InputStream stream = resource(loader, 2).openStream()) {
                fileSystem.readEnd = descriptor ? archive.descriptorOffsets()[2] + 11 : archive.descriptorOffsets()[2] - 1;
                assertThrows(ZipException.class, stream::readAllBytes);
            }
            assertEquals(0, fileSystem.openChannels);
            initializeFileSystem();
        }
    }

    @Test
    public void testOversizedStoredEntry() throws Exception {
        Archive archive = createArchive(false, false);
        ByteBuffer data = ByteBuffer.wrap(archive.bytes()).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(archive.centralOffsets()[2] + ZipFile.CENLEN, -2);
        data.putInt(archive.centralOffsets()[2] + ZipFile.CENSIZ, -2);
        ClassLoader loader = openArchive(archive);
        assertThrows(ZipException.class, () -> resource(loader, 2).openStream());
        assertEquals(0, fileSystem.openChannels);
    }

    private ClassLoader openArchive(Archive archive) throws IOException, InteropException {
        return openArchive(archive, languageEnv.getPublicTruffleFile("/stored-descriptors.jar"));
    }

    private ClassLoader openArchive(Archive archive, TruffleFile file) throws IOException, InteropException {
        try (OutputStream out = file.newOutputStream()) {
            out.write(archive.bytes());
        }
        languageEnv.addToHostClassPath(file);
        Object symbol = languageEnv.lookupHostSymbol(CLASS_NAME);
        return ((Class<?>) InteropLibrary.getUncached().asHostObject(symbol)).getClassLoader();
    }

    private static URL resource(ClassLoader loader, int index) {
        URL resource = loader.getResource(NAMES[index]);
        assertNotNull(resource);
        return resource;
    }

    private static void assertResources(ClassLoader loader, Archive archive) throws IOException {
        for (int i = 1; i < NAMES.length; i++) {
            try (InputStream stream = resource(loader, i).openStream()) {
                assertArrayEquals(archive.contents()[i], stream.readAllBytes());
                assertEquals(-1, stream.read());
                assertEquals(0, stream.read(new byte[0]));
            }
            try (InputStream stream = loader.getResourceAsStream(NAMES[i])) {
                assertNotNull(stream);
                assertArrayEquals(archive.contents()[i], stream.readAllBytes());
            }
        }
    }

    private record Archive(byte[] bytes, int[] localOffsets, int[] dataOffsets, int[] descriptorOffsets, int[] centralOffsets, byte[][] contents) {
    }

    private static Archive createArchive(boolean signed, boolean zip64) throws IOException {
        Path classes = HostClassLoadingTest.renameHostClass(HostClassLoadingTestClass1.class, HostClassLoadingTest.TEST_REPLACE_CLASS_NAME);
        byte[] classBytes;
        try {
            classBytes = Files.readAllBytes(classes.resolve(NAMES[0]));
        } finally {
            HostClassLoadingTest.deleteDir(classes);
        }
        byte[] large = new byte[4097];
        for (int i = 0; i < large.length; i++) {
            large[i] = (byte) i;
        }
        byte[][] contents = {classBytes, new byte[0], new byte[]{0, 0x7f, (byte) 0x80, (byte) 0xff},
                        new byte[]{(byte) 0x9d, 0x0a, (byte) 0xd9, 0x6d}, large, "following entry".getBytes(StandardCharsets.UTF_8)};
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int[] local = new int[NAMES.length];
        int[] payload = new int[NAMES.length];
        int[] descriptors = new int[NAMES.length];
        int[] central = new int[NAMES.length];
        long[] checksums = new long[NAMES.length];
        for (int i = 0; i < NAMES.length; i++) {
            byte[] name = NAMES[i].getBytes(StandardCharsets.UTF_8);
            boolean descriptor = i != NAMES.length - 1;
            boolean wide = descriptor && zip64;
            CRC32 crc = new CRC32();
            crc.update(contents[i]);
            checksums[i] = crc.getValue();
            local[i] = out.size();
            // LOC has extra fields absent from CEN, to check that the LOC lengths are used.
            ByteBuffer header = ByteBuffer.allocate(ZipFile.LOCHDR + name.length + 7 + (wide ? 20 : 0)).order(ByteOrder.LITTLE_ENDIAN);
            header.putInt((int) ZipFile.LOCSIG).putShort((short) (wide ? 45 : 20));
            header.putShort((short) (0x800 | (descriptor ? 8 : 0))).putShort((short) ZipEntry.STORED);
            header.putInt(0).putInt(descriptor ? 0 : (int) checksums[i]);
            int length = descriptor ? (wide ? -1 : 0) : contents[i].length;
            header.putInt(length).putInt(length).putShort((short) name.length).putShort((short) (7 + (wide ? 20 : 0))).put(name);
            if (wide) {
                header.putShort((short) 1).putShort((short) 16).putLong(0).putLong(0);
            }
            header.putShort((short) 0xcafe).putShort((short) 3).put(new byte[]{1, 2, 3});
            out.write(header.array());
            payload[i] = out.size();
            out.write(contents[i]);
            descriptors[i] = out.size();
            if (descriptor) {
                ByteBuffer data = ByteBuffer.allocate((signed ? 4 : 0) + 4 + (wide ? 16 : 8)).order(ByteOrder.LITTLE_ENDIAN);
                if (signed) {
                    data.putInt((int) ZipFile.EXTSIG);
                }
                data.putInt((int) checksums[i]);
                if (wide) {
                    data.putLong(contents[i].length).putLong(contents[i].length);
                } else {
                    data.putInt(contents[i].length).putInt(contents[i].length);
                }
                out.write(data.array());
            }
        }
        int cenStart = out.size();
        for (int i = 0; i < NAMES.length; i++) {
            central[i] = out.size();
            byte[] name = NAMES[i].getBytes(StandardCharsets.UTF_8);
            ByteBuffer header = ByteBuffer.allocate(ZipFile.CENHDR + name.length).order(ByteOrder.LITTLE_ENDIAN);
            header.putInt((int) ZipFile.CENSIG).putShort((short) 20).putShort((short) 20);
            header.putShort((short) (0x800 | (i != NAMES.length - 1 ? 8 : 0))).putShort((short) ZipEntry.STORED);
            header.putInt(0).putInt((int) checksums[i]).putInt(contents[i].length).putInt(contents[i].length);
            header.putShort((short) name.length).putShort((short) 0).putShort((short) 0);
            header.putShort((short) 0).putShort((short) 0).putInt(0).putInt(local[i]).put(name);
            out.write(header.array());
        }
        int cenSize = out.size() - cenStart;
        ByteBuffer end = ByteBuffer.allocate(ZipFile.ENDHDR).order(ByteOrder.LITTLE_ENDIAN);
        end.putInt((int) ZipFile.ENDSIG).putShort((short) 0).putShort((short) 0);
        end.putShort((short) NAMES.length).putShort((short) NAMES.length).putInt(cenSize).putInt(cenStart).putShort((short) 0);
        out.write(end.array());
        return new Archive(out.toByteArray(), local, payload, descriptors, central, contents);
    }

    private static final class TrackingFileSystem extends ForwardingFileSystem {
        private int openChannels;
        private int readLimit = Integer.MAX_VALUE;
        private long readEnd = Long.MAX_VALUE;
        private TrackingChannel lastChannel;

        TrackingFileSystem() throws IOException {
            super(new MemoryFileSystem());
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
            lastChannel = new TrackingChannel(super.newByteChannel(path, options, attrs), this);
            openChannels++;
            return lastChannel;
        }
    }

    private static final class TrackingChannel implements SeekableByteChannel {
        private final SeekableByteChannel delegate;
        private final TrackingFileSystem fileSystem;
        private final List<Long> readPositions = new ArrayList<>();

        TrackingChannel(SeekableByteChannel delegate, TrackingFileSystem fileSystem) {
            this.delegate = delegate;
            this.fileSystem = fileSystem;
        }

        @Override
        public int read(ByteBuffer data) throws IOException {
            long position = delegate.position();
            readPositions.add(position);
            if (position >= fileSystem.readEnd) {
                return -1;
            }
            int limit = data.limit();
            data.limit(data.position() + (int) Math.min(Math.min(data.remaining(), fileSystem.readLimit), fileSystem.readEnd - position));
            try {
                return delegate.read(data);
            } finally {
                data.limit(limit);
            }
        }

        @Override
        public int write(ByteBuffer data) throws IOException {
            return delegate.write(data);
        }

        @Override
        public long position() throws IOException {
            return delegate.position();
        }

        @Override
        public SeekableByteChannel position(long position) throws IOException {
            delegate.position(position);
            return this;
        }

        @Override
        public long size() throws IOException {
            return delegate.size();
        }

        @Override
        public SeekableByteChannel truncate(long size) throws IOException {
            delegate.truncate(size);
            return this;
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }

        @Override
        public void close() throws IOException {
            if (delegate.isOpen()) {
                try {
                    delegate.close();
                } finally {
                    fileSystem.openChannels--;
                }
            }
        }
    }

    private static byte[] withArchiveComment(byte[] original, byte[] comment) {
        int end = original.length - ZipFile.ENDHDR;
        ByteBuffer data = ByteBuffer.allocate(original.length + comment.length).order(ByteOrder.LITTLE_ENDIAN);
        data.put(original).put(comment);
        data.putShort(end + ZipFile.ENDCOM, (short) comment.length);
        return data.array();
    }

    private static byte[] withZip64End(byte[] original, boolean saturated, int extensionLength) {
        int end = original.length - ZipFile.ENDHDR;
        ByteBuffer old = ByteBuffer.wrap(original).order(ByteOrder.LITTLE_ENDIAN);
        long size = Integer.toUnsignedLong(old.getInt(end + ZipFile.ENDSIZ));
        long offset = Integer.toUnsignedLong(old.getInt(end + ZipFile.ENDOFF));
        int entries = Short.toUnsignedInt(old.getShort(end + ZipFile.ENDTOT));
        ByteBuffer data = ByteBuffer.allocate(original.length + ZIP64_ENDHDR + extensionLength + ZIP64_LOCHDR).order(ByteOrder.LITTLE_ENDIAN);
        data.put(original, 0, end);
        data.putInt(0x06064b50).putLong(ZIP64_ENDHDR - 12L + extensionLength);
        data.putShort((short) 45).putShort((short) 45).putInt(0).putInt(0);
        data.putLong(entries).putLong(entries).putLong(size).putLong(offset).put(new byte[extensionLength]);
        data.putInt(0x07064b50).putInt(0).putLong(end).putInt(1);
        int ordinaryEnd = data.position();
        data.put(original, end, ZipFile.ENDHDR);
        if (saturated) {
            data.putInt(ordinaryEnd + ZipFile.ENDSIZ, -1).putInt(ordinaryEnd + ZipFile.ENDOFF, -1);
            data.putShort(ordinaryEnd + ZipFile.ENDTOT, (short) 0xffff).putShort(ordinaryEnd + ZipFile.ENDSUB, (short) 0xffff);
        }
        return data.array();
    }

    private void assertIndexed(byte[] zip) throws Exception {
        Map<?, ?> entries = readEntries(zip);
        assertEquals(1, entries.size());
        Map<?, ?> root = (Map<?, ?>) entries.get("");
        assertNotNull(root);
        assertTrue(root.containsKey(ENTRY_NAME));
    }

    private void assertInvalid(byte[] zip, String message) {
        ZipException exception = assertThrows(ZipException.class, () -> readEntries(zip));
        assertTrue(exception.getMessage(), exception.getMessage().contains(message));
    }

    private Map<?, ?> readEntries(byte[] zip) throws Exception {
        TruffleFile file = languageEnv.getPublicTruffleFile("/test.zip");
        try (OutputStream out = file.newOutputStream()) {
            out.write(zip);
        }
        try (SeekableByteChannel channel = file.newByteChannel(EnumSet.of(StandardOpenOption.READ))) {
            return TestAPIAccessor.HOST.readZipEntries(channel);
        }
    }

    private static byte[] zip64Extra(int flags) {
        ByteBuffer data = ByteBuffer.allocate(Integer.bitCount(flags & 7) * Long.BYTES + ((flags & DISK64) != 0 ? Integer.BYTES : 0)).order(ByteOrder.LITTLE_ENDIAN);
        for (int flag : new int[]{SIZE64, COMPRESSED_SIZE64, OFFSET64}) {
            if ((flags & flag) != 0) {
                data.putLong(0);
            }
        }
        if ((flags & DISK64) != 0) {
            // The disk number affects the required length, but its value is not validated.
            data.putInt(-1);
        }
        return extraField(1, data.array());
    }

    private static byte[] extraField(int tag, byte[] data) {
        return ByteBuffer.allocate(4 + data.length).order(ByteOrder.LITTLE_ENDIAN).putShort((short) tag).putShort((short) data.length).put(data).array();
    }

    private static byte[] createZip(int flags, byte[] extra) throws IOException {
        return createZip(flags, extra, ENTRY_NAME);
    }

    private static byte[] createZip(int flags, byte[] extra, String name) throws IOException {
        return createZip(flags, extra, name, new byte[0]);
    }

    private static byte[] createZip(int flags, byte[] extra, String name, byte[] comment) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            ZipEntry entry = new ZipEntry(name);
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(0);
            entry.setCompressedSize(0);
            entry.setCrc(0);
            zip.putNextEntry(entry);
            zip.closeEntry();
        }
        byte[] original = out.toByteArray();
        int endOffset = original.length - ZipFile.ENDHDR;
        int addedLength = extra.length + comment.length;
        ByteBuffer data = ByteBuffer.allocate(original.length + addedLength).order(ByteOrder.LITTLE_ENDIAN);
        data.put(original, 0, endOffset).put(extra).put(comment).put(original, endOffset, ZipFile.ENDHDR);
        int cenOffset = data.getInt(endOffset + addedLength + ZipFile.ENDOFF);
        data.putShort(cenOffset + ZipFile.CENEXT, (short) extra.length);
        data.putShort(cenOffset + ZipFile.CENCOM, (short) comment.length);
        data.putInt(endOffset + addedLength + ZipFile.ENDSIZ, endOffset - cenOffset + addedLength);
        int[] fields = {ZipFile.CENLEN, ZipFile.CENSIZ, ZipFile.CENOFF};
        for (int field = 0; field < fields.length; field++) {
            if ((flags & (1 << field)) != 0) {
                data.putInt(cenOffset + fields[field], -1);
            }
        }
        if ((flags & DISK64) != 0) {
            data.putShort(cenOffset + ZipFile.CENDSK, (short) -1);
        }
        return data.array();
    }
}
