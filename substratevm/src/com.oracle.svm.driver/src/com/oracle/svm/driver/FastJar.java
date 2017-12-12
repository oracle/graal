/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.driver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Fast JAR access. Taken from NetBeans <a href=
 * "http://hg.netbeans.org/releases/file/27ca605240c9/java.source.base/src/org/netbeans/modules/java/source/parsing/FastJar.java">
 * FastJar</a>.
 *
 * @author Tomas Zezula
 */
final class FastJar {

    private FastJar() {
    }

    private static final int GIVE_UP = 1 << 16;

    private static class RandomAccessFileInputStream extends InputStream {

        private final RandomAccessFile b;
        private final long len;

        RandomAccessFileInputStream(RandomAccessFile b) throws IOException {
            assert b != null;
            this.b = b;
            this.len = b.length();
        }

        RandomAccessFileInputStream(RandomAccessFile b, long len) throws IOException {
            assert b != null;
            assert len >= 0;
            this.b = b;
            this.len = b.getFilePointer() + len;
        }

        @Override
        public int read(byte[] data, int offset, int size) throws IOException {
            int rem = available();
            if (rem == 0) {
                return -1;
            }
            int rlen;
            if (size < rem) {
                rlen = size;
            } else {
                rlen = rem;
            }
            return this.b.read(data, offset, rlen);
        }

        @Override
        public int read() throws java.io.IOException {
            if (available() == 0) {
                return -1;
            } else {
                return b.readByte();
            }
        }

        @Override
        public int available() throws IOException {
            return (int) (len - this.b.getFilePointer());
        }

        @Override
        public void close() throws IOException {
            b.close();
        }
    }

    static final class Entry {

        public final String name;
        final long offset;
        private final long dosTime;

        Entry(String name, long offset, long time) {
            assert name != null;
            this.name = name;
            this.offset = offset;
            this.dosTime = time;
        }

        public long getTime() {
            @SuppressWarnings("deprecation")
            Date d = new Date((int) (((dosTime >> 25) & 0x7f) + 80),
                            (int) (((dosTime >> 21) & 0x0f) - 1),
                            (int) ((dosTime >> 16) & 0x1f),
                            (int) ((dosTime >> 11) & 0x1f),
                            (int) ((dosTime >> 5) & 0x3f),
                            (int) ((dosTime << 1) & 0x3e));
            return d.getTime();
        }
    }

    public static InputStream getInputStream(final File file, final Entry e) throws IOException {
        return getInputStream(file, e.offset);
    }

    static InputStream getInputStream(final File file, final long offset) throws IOException {
        RandomAccessFile f = new RandomAccessFile(file, "r");     // NOI18N
        f.seek(offset);
        ZipInputStream in = new ZipInputStream(new RandomAccessFileInputStream(f));
        ZipEntry e = in.getNextEntry();
        if (e != null && e.getCrc() == 0L && e.getMethod() == ZipEntry.STORED) {
            long cp = f.getFilePointer();
            in.close();
            f = new RandomAccessFile(file, "r");     // NOI18N
            f.seek(cp);
            return new RandomAccessFileInputStream(f, e.getSize());
        }
        return in;
    }

    static ZipEntry getZipEntry(final File file, final long offset) throws IOException {
        RandomAccessFile f = new RandomAccessFile(file, "r");     // NOI18N
        try {
            f.seek(offset);
            ZipInputStream in = new ZipInputStream(new RandomAccessFileInputStream(f));
            try {
                return in.getNextEntry();
            } finally {
                in.close();
            }
        } finally {
            f.close();
        }
    }

    public static Iterable<? extends Entry> list(File f) throws IOException {
        RandomAccessFile b = new RandomAccessFile(f, "r");      // NOI18N
        try {
            final long size = (int) b.length();
            b.seek(size - ZipFile.ENDHDR);

            byte[] data = new byte[ZipFile.ENDHDR];
            int giveup = 0;

            do {
                if (b.read(data, 0, ZipFile.ENDHDR) != ZipFile.ENDHDR) {
                    throw new IOException();
                }
                b.seek(b.getFilePointer() - (ZipFile.ENDHDR + 1));
                giveup++;
                if (giveup > GIVE_UP) {
                    throw new IOException();
                }
            } while (getsig(data) != ZipFile.ENDSIG);

            final long censize = endsiz(data);
            final long cenoff = endoff(data);
            b.seek(cenoff);

            List<Entry> result = new LinkedList<>();
            int cenread = 0;
            data = new byte[ZipFile.CENHDR];
            while (cenread < censize) {
                if (b.read(data, 0, ZipFile.CENHDR) != ZipFile.CENHDR) {
                    throw new IOException("No central table");         // NOI18N
                }
                if (getsig(data) != ZipFile.CENSIG) {
                    throw new IOException("No central table");          // NOI18N
                }
                int cennam = cennam(data);
                int cenext = cenext(data);
                int cencom = cencom(data);
                long lhoff = cenoff(data);
                long centim = centim(data);
                String name = name(b, cennam);
                int seekby = cenext + cencom;
                int cendatalen = ZipFile.CENHDR + cennam + seekby;
                cenread += cendatalen;
                result.add(new Entry(name, lhoff, centim));
                seekBy(b, seekby);
            }
            return result;
        } finally {
            b.close();
        }
    }

    private static String name(final RandomAccessFile b, final int cennam) throws IOException {
        byte[] name = new byte[cennam];
        b.read(name, 0, cennam);
        return new String(name, "UTF-8");       // NOI18N
    }

    private static long getsig(final byte[] b) {
        return get32(b, 0);
    }

    private static long endsiz(final byte[] b) {
        return get32(b, ZipFile.ENDSIZ);
    }

    private static long endoff(final byte[] b) {
        return get32(b, ZipFile.ENDOFF);
    }

    private static long centim(final byte[] b) {
        return get32(b, ZipFile.CENTIM);
    }

    private static int cennam(final byte[] b) {
        return get16(b, ZipFile.CENNAM);
    }

    private static int cenext(final byte[] b) {
        return get16(b, ZipFile.CENEXT);
    }

    private static int cencom(final byte[] b) {
        return get16(b, ZipFile.CENCOM);
    }

    private static long cenoff(final byte[] b) {
        return get32(b, ZipFile.CENOFF);
    }

    private static void seekBy(final RandomAccessFile b, int offset) throws IOException {
        b.seek(b.getFilePointer() + offset);
    }

    private static int get16(final byte[] b, int off) {
        final int b1 = b[off];
        final int b2 = b[off + 1];
        return (b1 & 0xff) | ((b2 & 0xff) << 8);
    }

    private static long get32(final byte[] b, int off) {
        final int s1 = get16(b, off);
        final int s2 = get16(b, off + 2);
        return s1 | ((long) s2 << 16);
    }
}
