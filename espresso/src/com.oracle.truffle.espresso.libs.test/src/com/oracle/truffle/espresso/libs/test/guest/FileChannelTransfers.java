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
package com.oracle.truffle.espresso.libs.test.guest;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Guest-side code for {@code FileChannelTransferTest}. Runs inside Espresso and only performs the
 * {@link FileChannel} operations; all assertions are done by the host test.
 */
public final class FileChannelTransfers {

    /** Maximum number of consecutive zero-byte transfers before giving up (no progress). */
    private static final int MAX_ZERO_TRANSFERS = 16;

    private FileChannelTransfers() {
    }

    /**
     * Transfers {@code count} bytes starting at {@code position} of {@code source} to
     * {@code target}, calling {@link FileChannel#transferTo} until done or the source is exhausted.
     * The source channel position is set to 1 beforehand to detect unwanted modifications.
     *
     * @return {@code {bytes transferred, source position, target position}}
     */
    public static long[] transferTo(String source, long position, long count, String target) throws IOException {
        try (FileChannel in = FileChannel.open(Path.of(source), StandardOpenOption.READ);
                        FileChannel out = FileChannel.open(Path.of(target), StandardOpenOption.WRITE)) {
            in.position(1);
            long done = 0;
            int zeroTransfers = 0;
            while (done < count) {
                long n = in.transferTo(position + done, count - done, out);
                if (n == 0 && (position + done >= in.size() || ++zeroTransfers > MAX_ZERO_TRANSFERS)) {
                    break;
                }
                done += n;
            }
            return new long[]{done, in.position(), out.position()};
        }
    }

    /**
     * Transfers {@code count} bytes from {@code source} into {@code target} at {@code position},
     * calling {@link FileChannel#transferFrom} until done or the source is exhausted.
     *
     * @return {@code {bytes transferred, source position, target position}}
     */
    public static long[] transferFrom(String source, String target, long position, long count) throws IOException {
        try (FileChannel in = FileChannel.open(Path.of(source), StandardOpenOption.READ);
                        FileChannel out = FileChannel.open(Path.of(target), StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            long done = 0;
            int zeroTransfers = 0;
            while (done < count) {
                long n = out.transferFrom(in, position + done, count - done);
                if (n == 0 && (in.position() >= in.size() || ++zeroTransfers > MAX_ZERO_TRANSFERS)) {
                    break;
                }
                done += n;
            }
            return new long[]{done, in.position(), out.position()};
        }
    }

    /**
     * Sets the channel position to {@code channelPosition}, then writes {@code heapBytes} from a
     * heap buffer at {@code heapPosition} and {@code directByte} from a direct buffer at
     * {@code directPosition} using positional writes.
     *
     * @return {@code {bytes written, channel position after the writes}}
     */
    public static long[] positionalWrite(String file, long channelPosition, String heapBytes, long heapPosition, int directByte, long directPosition) throws IOException {
        try (FileChannel ch = FileChannel.open(Path.of(file), StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ch.position(channelPosition);
            long written = ch.write(ByteBuffer.wrap(heapBytes.getBytes(StandardCharsets.ISO_8859_1)), heapPosition);
            ByteBuffer direct = ByteBuffer.allocateDirect(1);
            direct.put((byte) directByte).flip();
            written += ch.write(direct, directPosition);
            return new long[]{written, ch.position()};
        }
    }
}
