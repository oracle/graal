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

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.Unistd;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.word.WordFactory;

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
     * Find a mapping in /proc/self/maps format which corresponds to the
     * specified address. The buffer is dual-purpose and used to return the
     * file's path name if requested via the needName parameter. As such the
     * buffer should be large enough to accommodate a path. If not enough buffer
     * capacity is available, and needName is true, false will be returned and
     * the buffer null prefixed to prevent accidental use.
     *
     * If a mapping is not found, or an error has occured, false will be
     * returned.
     *
     * @param fd an FD pointing to /proc/self/maps
     * @param buffer a buffer for reading operations, and optionally returning
     *               the path name of the mapping.
     * @param bufferLen the length of the buffer
     * @param address the address to search for that is within a mapping entry's
     *                address range
     * @param startAddrPtr the start address range for a found mapping
     * @param offsetPtr the file offset for the backing file of the found
     *                  mapping
     * @param devPtr    the device id of the matching mapping's backing file
     * @param inodePtr  the inode of the matching mapping's backing file
     * @param needName  whether the matching path name is desired and should be
     *                  returned in buffer
     * @return true if a mapping is found and no errors, false otherwise.
     */
    @Uninterruptible(reason = "Called during isolate initialization.")
    @SuppressWarnings("fallthrough")
    static boolean findMapping(int fd, CCharPointer buffer, int bufferLen, long address, CLongPointer startAddrPtr,
                               CLongPointer offsetPtr, CIntPointer devPtr, CLongPointer inodePtr, boolean needName) {
        int rem = 0;
        int pos = 0;
        int state = ST_ADDR_START;
        int b;
        int dev = 0;
        long inode = 0;
        long offs = 0;
        long start = 0;
        long end = 0;
        int fns = 0;
        OUT: for (;;) {
            while (pos == rem) {
                // fill buffer
                int res;
                do {
                    res = (int) Unistd.NoTransitions.read(fd, buffer.addressOf(fns), WordFactory.unsigned(bufferLen - fns)).rawValue();
                } while (res == -1 && Errno.errno() == Errno.EINTR());
                if (res == -1 || res == 0) {
                    // read failure or EOF == not matched
                    return false;
                }
                pos = fns;
                rem = res + fns;
            }
            b = buffer.read(pos++) & 0xff;
            switch (state) {
                case ST_ADDR_START: {
                    if (b == '-') {
                        state = address > start ? ST_ADDR_END : ST_SKIP;
                    } else if ('0' <= b && b <= '9') {
                        start = (start << 4) + (b - '0');
                    } else if ('a' <= b && b <= 'f') {
                        start = (start << 4) + (b - 'a' + 10);
                    } else {
                        // garbage == not matched
                        return false;
                    }
                    break;
                }
                case ST_ADDR_END: {
                    if (b == ' ') {
                        state = address < end ? ST_PERMS : ST_SKIP;
                    } else if ('0' <= b && b <= '9') {
                        end = (end << 4) + (b - '0');
                    } else if ('a' <= b && b <= 'f') {
                        end = (end << 4) + (b - 'a' + 10);
                    } else {
                        // garbage == not matched
                        return false;
                    }
                    break;
                }
                case ST_PERMS: {
                    if (b == ' ') {
                        offs = 0;
                        state = ST_OFFSET;
                    }
                    // ignore anything else
                    break;
                }
                case ST_OFFSET: {
                    if (b == ' ') {
                        dev = 0;
                        state = ST_DEV;
                    } else if ('0' <= b && b <= '9') {
                        offs = (offs << 4) + (b - '0');
                    } else if ('a' <= b && b <= 'f') {
                        offs = (offs << 4) + (b - 'a' + 10);
                    } else {
                        // garbage == not matched
                        return false;
                    }
                    break;
                }
                case ST_DEV: {
                    if (b == ' ') {
                        inode = 0;
                        state = ST_INODE;
                    } else if (b == ':') {
                        // ignore
                    } else if ('0' <= b && b <= '9') {
                        dev = (dev << 4) + (b - '0');
                    } else if ('a' <= b && b <= 'f') {
                        dev = (dev << 4) + (b - 'a' + 10);
                    }
                    break;
                }
                case ST_INODE: {
                    if (b == ' ') {
                        fns = 0;
                        if (!needName) {
                            buffer.write(0, (byte)0);
                            break OUT;
                        }
                        state = ST_SPACE;
                    } else if ('0' <= b && b <= '9') {
                        inode = (inode << 3) + (inode << 1) + (b - '0');
                    } else {
                        // garbage == not matched
                        return false;
                    }
                    break;
                }
                case ST_SPACE: {
                    if (b == ' ') {
                        break;
                    }
                    state = ST_FILENAME;
                    // fall thru
                }
                case ST_FILENAME: {
                    if (b == '\n') {
                        buffer.write(fns, (byte) 0);
                        break OUT;
                    } else {
                        if (fns < pos - 1) {
                            buffer.write(fns, (byte)(b & 0xFF));
                        }
                        if (++fns >= bufferLen) {
                            // advance out of capacity, garbage
                            return false;
                        }
                    }
                    break;
                }
                case ST_SKIP: {
                    if (b == '\n') {
                        start = 0;
                        end = 0;
                        state = ST_ADDR_START;
                    }
                    break;
                }
            }
        }
        if (startAddrPtr.isNonNull()) startAddrPtr.write(start);
        if (offsetPtr.isNonNull()) offsetPtr.write(offs);
        if (devPtr.isNonNull()) devPtr.write(dev);
        if (inodePtr.isNonNull()) inodePtr.write(inode);
        return true;
    }
}
