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
package com.oracle.svm.core.posix.linux;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.posix.PosixUtils;

class ProcFSSupport {
    private static final int ST_ADDR_START = 1;
    private static final int ST_ADDR_END = 2;
    private static final int ST_PERMS = 3;
    private static final int ST_OFFSET = 4;
    private static final int ST_DEV = 5;
    private static final int ST_INODE = 6;
    private static final int ST_SPACE = 7;
    private static final int ST_FILENAME = 8;
    private static final int ST_SKIP = 9;

    /**
     * Find a mapping in /proc/self/maps format which encloses the specified address range. The
     * buffer is dual-purpose and used to return the file's path name if requested via the
     * {@code needName} parameter. As such the buffer should be large enough to accommodate a path.
     * If not enough buffer capacity is available, and needName is true, false will be returned. If
     * a mapping is not found, or an error has occurred, false will be returned.
     *
     * @param fd a file descriptor pointing to /proc/self/maps
     * @param buffer a buffer for reading operations, and optionally for returning the path name of
     *            the mapping
     * @param bufferLen the length of the buffer
     * @param beginAddress the start address of the address range to find within a mapping
     * @param endAddress the end address of the address range to find within a mapping
     * @param startAddrPtr the start address range for a found mapping
     * @param fileOffsetPtr the file offset of the found mapping in its backing file
     * @param needName whether the matching path name is required and should be returned in buffer
     * @return true if a mapping is found and no errors occurred, false otherwise.
     */
    @Uninterruptible(reason = "Called during isolate initialization.")
    @SuppressWarnings("fallthrough")
    static boolean findMapping(int fd, CCharPointer buffer, int bufferLen, UnsignedWord beginAddress, UnsignedWord endAddress,
                    WordPointer startAddrPtr, WordPointer fileOffsetPtr, boolean needName) {
        int readOffset = 0;
        int endOffset = 0;
        int position = 0;
        int state = ST_ADDR_START;
        int b;

        UnsignedWord start = WordFactory.zero();
        UnsignedWord end = WordFactory.zero();
        UnsignedWord fileOffset = WordFactory.zero();
        OUT: for (;;) {
            while (position == endOffset) { // fill buffer
                int readBytes = PosixUtils.readBytes(fd, buffer, bufferLen, readOffset);
                if (readBytes <= 0) {
                    return false; // read failure or 0 == EOF -> not matched
                }
                position = readOffset;
                endOffset = readOffset + readBytes;
            }
            b = buffer.read(position++) & 0xff;
            switch (state) {
                case ST_ADDR_START: {
                    if (b == '-') {
                        state = beginAddress.aboveOrEqual(start) ? ST_ADDR_END : ST_SKIP;
                    } else if ('0' <= b && b <= '9') {
                        start = start.shiftLeft(4).add(b - '0');
                    } else if ('a' <= b && b <= 'f') {
                        start = start.shiftLeft(4).add(b - 'a' + 10);
                    } else {
                        return false; // garbage == not matched
                    }
                    break;
                }
                case ST_ADDR_END: {
                    if (b == ' ') {
                        state = endAddress.belowOrEqual(end) ? ST_PERMS : ST_SKIP;
                    } else if ('0' <= b && b <= '9') {
                        end = end.shiftLeft(4).add(b - '0');
                    } else if ('a' <= b && b <= 'f') {
                        end = end.shiftLeft(4).add(b - 'a' + 10);
                    } else {
                        return false; // garbage == not matched
                    }
                    break;
                }
                case ST_PERMS: {
                    if (b == ' ') {
                        fileOffset = WordFactory.zero();
                        state = ST_OFFSET;
                    }
                    break; // ignore anything else
                }
                case ST_OFFSET: {
                    if (b == ' ') {
                        state = ST_DEV;
                    } else if ('0' <= b && b <= '9') {
                        fileOffset = fileOffset.shiftLeft(4).add(b - '0');
                    } else if ('a' <= b && b <= 'f') {
                        fileOffset = fileOffset.shiftLeft(4).add(b - 'a' + 10);
                    } else {
                        return false; // garbage == not matched
                    }
                    break;
                }
                case ST_DEV: {
                    if (b == ' ') {
                        state = ST_INODE;
                    } // ignore anything else
                    break;
                }
                case ST_INODE: {
                    if (b == ' ') {
                        readOffset = 0;
                        if (!needName) {
                            buffer.write(0, (byte) 0);
                            break OUT;
                        }
                        state = ST_SPACE;
                    } // ignore anything else
                    break;
                }
                case ST_SPACE: {
                    if (b == ' ') {
                        break;
                    }
                    state = ST_FILENAME;
                }
                // fallthru
                case ST_FILENAME: {
                    if (b == '\n') {
                        buffer.write(readOffset, (byte) 0);
                        break OUT;
                    } else {
                        if (readOffset < position - 1) {
                            buffer.write(readOffset, (byte) (b & 0xFF));
                        }
                        if (++readOffset >= bufferLen) {
                            return false; // advance out of capacity, garbage
                        }
                    }
                    break;
                }
                case ST_SKIP: {
                    if (b == '\n') {
                        start = WordFactory.zero();
                        end = WordFactory.zero();
                        state = ST_ADDR_START;
                    }
                    break;
                }
            }
        }
        if (startAddrPtr.isNonNull()) {
            startAddrPtr.write(start);
        }
        if (fileOffsetPtr.isNonNull()) {
            fileOffsetPtr.write(fileOffset);
        }
        return true;
    }
}
