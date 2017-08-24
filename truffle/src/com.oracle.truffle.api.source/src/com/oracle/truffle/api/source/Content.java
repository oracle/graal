/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.source;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Objects;

abstract class Content {

    private static final String URI_SCHEME = "truffle";

    CharSequence code;
    private volatile URI uri;

    abstract String findMimeType() throws IOException;

    abstract Reader getReader() throws IOException;

    abstract CharSequence getCode();

    abstract String getName();

    abstract Object getHashKey();

    abstract String getPath();

    abstract URL getURL();

    abstract URI getURI();

    @SuppressWarnings("unused")
    void appendCode(CharSequence chars) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Content other = (Content) obj;
        return Objects.equals(getCode(), other.getCode());
    }

    @Override
    public int hashCode() {
        return getHashKey().hashCode();
    }

    protected final URI createURIOnce(CreateURI cu) {
        if (uri == null) {
            synchronized (this) {
                if (uri == null) {
                    uri = cu.createURI();
                }
            }
        }
        return uri;
    }

    protected final URI getNamedURI(String name, byte[] bytes) {
        return getNamedURI(name, bytes, 0, bytes.length);
    }

    protected final URI getNamedURI(String name, byte[] bytes, int byteIndex, int length) {
        String digest;
        if (bytes != null) {
            digest = digest(bytes, byteIndex, length);
        } else {
            digest = Integer.toString(System.identityHashCode(this), 16);
        }
        if (name != null) {
            digest += '/' + name;
        }
        try {
            return new URI(URI_SCHEME, digest, null);
        } catch (URISyntaxException ex) {
            throw new Error(ex);    // Should not happen
        }
    }

    protected interface CreateURI {
        URI createURI();
    }

    private static final int[] S = new int[]{
                    0x29, 0x2E, 0x43, 0xC9, 0xA2, 0xD8, 0x7C, 0x01, 0x3D, 0x36, 0x54, 0xA1, 0xEC, 0xF0, 0x06, 0x13,
                    0x62, 0xA7, 0x05, 0xF3, 0xC0, 0xC7, 0x73, 0x8C, 0x98, 0x93, 0x2B, 0xD9, 0xBC, 0x4C, 0x82, 0xCA,
                    0x1E, 0x9B, 0x57, 0x3C, 0xFD, 0xD4, 0xE0, 0x16, 0x67, 0x42, 0x6F, 0x18, 0x8A, 0x17, 0xE5, 0x12,
                    0xBE, 0x4E, 0xC4, 0xD6, 0xDA, 0x9E, 0xDE, 0x49, 0xA0, 0xFB, 0xF5, 0x8E, 0xBB, 0x2F, 0xEE, 0x7A,
                    0xA9, 0x68, 0x79, 0x91, 0x15, 0xB2, 0x07, 0x3F, 0x94, 0xC2, 0x10, 0x89, 0x0B, 0x22, 0x5F, 0x21,
                    0x80, 0x7F, 0x5D, 0x9A, 0x5A, 0x90, 0x32, 0x27, 0x35, 0x3E, 0xCC, 0xE7, 0xBF, 0xF7, 0x97, 0x03,
                    0xFF, 0x19, 0x30, 0xB3, 0x48, 0xA5, 0xB5, 0xD1, 0xD7, 0x5E, 0x92, 0x2A, 0xAC, 0x56, 0xAA, 0xC6,
                    0x4F, 0xB8, 0x38, 0xD2, 0x96, 0xA4, 0x7D, 0xB6, 0x76, 0xFC, 0x6B, 0xE2, 0x9C, 0x74, 0x04, 0xF1,
                    0x45, 0x9D, 0x70, 0x59, 0x64, 0x71, 0x87, 0x20, 0x86, 0x5B, 0xCF, 0x65, 0xE6, 0x2D, 0xA8, 0x02,
                    0x1B, 0x60, 0x25, 0xAD, 0xAE, 0xB0, 0xB9, 0xF6, 0x1C, 0x46, 0x61, 0x69, 0x34, 0x40, 0x7E, 0x0F,
                    0x55, 0x47, 0xA3, 0x23, 0xDD, 0x51, 0xAF, 0x3A, 0xC3, 0x5C, 0xF9, 0xCE, 0xBA, 0xC5, 0xEA, 0x26,
                    0x2C, 0x53, 0x0D, 0x6E, 0x85, 0x28, 0x84, 0x09, 0xD3, 0xDF, 0xCD, 0xF4, 0x41, 0x81, 0x4D, 0x52,
                    0x6A, 0xDC, 0x37, 0xC8, 0x6C, 0xC1, 0xAB, 0xFA, 0x24, 0xE1, 0x7B, 0x08, 0x0C, 0xBD, 0xB1, 0x4A,
                    0x78, 0x88, 0x95, 0x8B, 0xE3, 0x63, 0xE8, 0x6D, 0xE9, 0xCB, 0xD5, 0xFE, 0x3B, 0x00, 0x1D, 0x39,
                    0xF2, 0xEF, 0xB7, 0x0E, 0x66, 0x58, 0xD0, 0xE4, 0xA6, 0x77, 0x72, 0xF8, 0xEB, 0x75, 0x4B, 0x0A,
                    0x31, 0x44, 0x50, 0xB4, 0x8F, 0xED, 0x1F, 0x1A, 0xDB, 0x99, 0x8D, 0x33, 0x9F, 0x11, 0x83, 0x14
    };

    static String digest(byte[] message, int from, int length) {
        int[] m = new int[19];
        int[] x = new int[48];
        int[] c = new int[16];

        int t;
        int loop = 1;
        int start = 0;
        int bytes = 0;

        for (int i = 0; i < 16; ++i) {
            x[i] = c[i] = 0;
        }

        int last = 0;
        int index = from;
        m[16] = m[17] = m[18] = 0;
        while (loop == 1) {
            m[0] = m[16];
            m[1] = m[17];
            m[2] = m[18];
            for (int i = 3; i < 16; i++) {
                m[i] = 0;
            }
            int i;
            for (i = start; index < length && i < 16; ++index) {
                int code = message[index];
                if (code < 0) {
                    code += 256;
                }
                m[i++] = code;
            }
            bytes += i - start;
            start = i - 16;

            if (index == length && i < 16) {
                loop = 2;
                t = 16 - (bytes & 15);
                for (; i < 16; ++i) {
                    m[i] = t;
                }
            }

            for (i = 0; i < 16; ++i) {
                c[i] ^= S[m[i] ^ last];
                last = c[i];
            }

            for (i = 0; i < loop; ++i) {
                int[] mOrC = i == 0 ? m : c;

                x[16] = mOrC[0];
                x[32] = x[16] ^ x[0];
                x[17] = mOrC[1];
                x[33] = x[17] ^ x[1];
                x[18] = mOrC[2];
                x[34] = x[18] ^ x[2];
                x[19] = mOrC[3];
                x[35] = x[19] ^ x[3];
                x[20] = mOrC[4];
                x[36] = x[20] ^ x[4];
                x[21] = mOrC[5];
                x[37] = x[21] ^ x[5];
                x[22] = mOrC[6];
                x[38] = x[22] ^ x[6];
                x[23] = mOrC[7];
                x[39] = x[23] ^ x[7];
                x[24] = mOrC[8];
                x[40] = x[24] ^ x[8];
                x[25] = mOrC[9];
                x[41] = x[25] ^ x[9];
                x[26] = mOrC[10];
                x[42] = x[26] ^ x[10];
                x[27] = mOrC[11];
                x[43] = x[27] ^ x[11];
                x[28] = mOrC[12];
                x[44] = x[28] ^ x[12];
                x[29] = mOrC[13];
                x[45] = x[29] ^ x[13];
                x[30] = mOrC[14];
                x[46] = x[30] ^ x[14];
                x[31] = mOrC[15];
                x[47] = x[31] ^ x[15];

                t = 0;
                for (int j = 0; j < 18; ++j) {
                    for (int k = 0; k < 48; ++k) {
                        x[k] = t = x[k] ^ S[t];
                    }
                    t = (t + j) & 0xFF;
                }
            }
        }

        StringBuilder result = new StringBuilder(32);
        for (int i = 0; i < 16; ++i) {
            final String hex = Integer.toHexString(x[i]);
            if (result.length() == 0) {
                if (hex.equals("0")) {
                    continue;
                }
            } else {
                if (hex.length() == 1) {
                    result.append("0");
                }
            }
            result.append(hex);
        }
        return result.toString();
    }

}
