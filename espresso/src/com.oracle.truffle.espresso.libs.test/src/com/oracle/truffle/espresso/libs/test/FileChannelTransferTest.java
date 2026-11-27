/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.libs.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.oracle.truffle.espresso.libs.test.guest.FileChannelTransfers;

/**
 * Tests {@code FileChannel.transferTo}, {@code FileChannel.transferFrom} and positional writes
 * in a guest running with {@code -XX:+UseEspressoLibs} (Truffle VFS based NIO).
 *
 * <p>
 * The guest code lives in {@link FileChannelTransfers}; this host test prepares the files and
 * checks contents and channel positions against the {@code FileChannel} contract.
 *
 * @see <a href="https://github.com/oracle/graal/issues/14856">GH-14856</a>
 */
public class FileChannelTransferTest {

    /** Above {@code FileChannelImpl.MAPPED_TRANSFER_THRESHOLD} (16 KiB), uses the mapped path. */
    private static final int LARGE = 100 * 1024;

    private static Context context;
    private static Value guest;

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @BeforeClass
    public static void setUp() throws URISyntaxException {
        String guestClasspath = Path.of(FileChannelTransfers.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        context = Context.newBuilder("java") //
                        .allowAllAccess(true) //
                        .allowExperimentalOptions(true) //
                        .option("java.UseEspressoLibs", "true") //
                        .option("java.NativeBackend", "no-native") //
                        .option("java.Classpath", guestClasspath) //
                        .build();
        guest = context.getBindings("java").getMember(FileChannelTransfers.class.getName());
    }

    @AfterClass
    public static void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    // Given/When/Then: transferTo

    @Test
    public void givenSmallFileWhenTransferToFromOffsetThenRangeCopied() throws IOException {
        // given
        byte[] content = data(11);
        Path source = file(content);
        Path target = file(new byte[0]);
        // when
        long[] result = transferTo(source, 3, 8, target);
        // then
        assertResult(new long[]{8, 1, 8}, result);
        assertArrayEquals(Arrays.copyOfRange(content, 3, 11), Files.readAllBytes(target));
    }

    @Test
    public void givenLargeFileWhenTransferToThenCopiedViaMappedFallback() throws IOException {
        // given
        byte[] content = data(LARGE + 3);
        Path source = file(content);
        Path target = file(new byte[0]);
        // when
        long[] result = transferTo(source, 3, LARGE, target);
        // then
        assertResult(new long[]{LARGE, 1, LARGE}, result);
        assertArrayEquals(Arrays.copyOfRange(content, 3, LARGE + 3), Files.readAllBytes(target));
    }

    @Test
    public void givenCountBeyondEndOfFileWhenTransferToThenCountClamped() throws IOException {
        // given
        byte[] content = data(20);
        Path source = file(content);
        Path target = file(new byte[0]);
        // when
        long[] result = transferTo(source, 5, 1000, target);
        // then
        assertResult(new long[]{15, 1, 15}, result);
        assertArrayEquals(Arrays.copyOfRange(content, 5, 20), Files.readAllBytes(target));
    }

    // Given/When/Then: transferFrom

    @Test
    public void givenSmallFileWhenTransferFromAtPositionThenWrittenAfterExistingBytes() throws IOException {
        // given
        byte[] content = data(8);
        Path source = file(content);
        Path target = file(ascii("HDR"));
        // when
        long[] result = transferFrom(source, target, 3, 8);
        // then
        assertResult(new long[]{8, 8, 0}, result);
        assertArrayEquals(concat(ascii("HDR"), content), Files.readAllBytes(target));
    }

    @Test
    public void givenLargeFileWhenTransferFromThenCopiedViaMappedFallback() throws IOException {
        // given
        byte[] content = data(LARGE);
        Path source = file(content);
        Path target = file(ascii("HDR"));
        // when
        long[] result = transferFrom(source, target, 3, LARGE);
        // then
        assertResult(new long[]{LARGE, LARGE, 0}, result);
        assertArrayEquals(concat(ascii("HDR"), content), Files.readAllBytes(target));
    }

    // Given/When/Then: positional write

    @Test
    public void givenChannelPositionWhenPositionalWritesThenPositionKept() throws IOException {
        // given
        Path f = file(ascii("0123456789"));
        // when
        long[] result = guest.invokeMember("positionalWrite", f.toString(), 2L, "ab", 6L, (int) 'Z', 0L).as(long[].class);
        // then
        assertResult(new long[]{3, 2}, result);
        assertEquals("Z12345ab89", new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1));
    }

    private static long[] transferTo(Path source, long position, long count, Path target) {
        return guest.invokeMember("transferTo", source.toString(), position, count, target.toString()).as(long[].class);
    }

    private static long[] transferFrom(Path source, Path target, long position, long count) {
        return guest.invokeMember("transferFrom", source.toString(), target.toString(), position, count).as(long[].class);
    }

    private static void assertResult(long[] expected, long[] actual) {
        assertArrayEquals("{bytes, source position, target position}: " + Arrays.toString(actual), expected, actual);
    }

    private Path file(byte[] content) throws IOException {
        Path f = tmp.newFile().toPath();
        Files.write(f, content);
        return f;
    }

    private static byte[] data(int size) {
        byte[] b = new byte[size];
        for (int i = 0; i < size; i++) {
            b[i] = (byte) (i * 31 + 7);
        }
        return b;
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
