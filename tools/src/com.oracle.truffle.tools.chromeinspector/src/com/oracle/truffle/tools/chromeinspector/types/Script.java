/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.types;

import java.text.MessageFormat;

import com.oracle.truffle.api.source.Source;

public final class Script {

    private final int id;
    private final String url;
    private final Source source;

    public Script(int id, String url, Source source) {
        this.id = id;
        this.url = url;
        this.source = source;
    }

    public int getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public Source getSource() {
        return source;
    }

    public CharSequence getCharacters() {
        if (source.hasCharacters()) {
            return source.getCharacters();
        } else {
            return MessageFormat.format("Can not load source from {0}\n" +
                            "Please use the --inspect.SourcePath option to point to the source locations.\n" +
                            "Example: --inspect.SourcePath=/home/joe/project/src\n", url);
        }
    }

    public String getHash() {
        CharSequence code = getCharacters();
        // See
        // http://opendatastructures.org/versions/edition-0.1d/ods-java/node33.html#SECTION00832000000000000000
        // Join 5 hash codes:
        long[] p = {0xd8b862fd, 0xd950f97f, 0xeb329c71, 0xf71e5e6b, 0xfab1e57b};
        long[] random = {0xa5f881ccL, 0x63fff827L, 0x9568f4cbL, 0x1a2e3318L, 0x2af3fbd1L};
        int[] randomOdd = {0x3713d83b, 0x19033ac5, 0xe847047d, 0xcde9ca1f, 0x058bf00b};
        int numHashes = 5;
        long[] hashes = new long[numHashes];
        long[] zi = new long[]{1, 1, 1, 1, 1};

        int current = 0;
        int l4 = code.length() / 4;
        for (int i = 0; i < l4; i += 4) {
            int v = code.charAt(i);
            long xi = (v * randomOdd[current]) & 0x7FFFFFFF;
            hashes[current] = (hashes[current] + zi[current] * xi) % p[current];
            zi[current] = (zi[current] * random[current]) % p[current];
            current = current == numHashes - 1 ? 0 : current + 1;
        }
        if ((code.length() % 4) != 0) {
            int v = 0;
            for (int i = l4; i < code.length(); i++) {
                v <<= 8;
                v |= code.charAt(i);
            }
            long xi = (v * randomOdd[current]) & 0x7FFFFFFF;
            hashes[current] = (hashes[current] + zi[current] * xi) % p[current];
            zi[current] = (zi[current] * random[current]) % p[current];
        }

        for (int i = 0; i < numHashes; i++) {
            hashes[i] = (hashes[i] + zi[i] * (p[i] - 1)) % p[i];
        }

        StringBuilder hash = new StringBuilder();
        for (int i = 0; i < numHashes; i++) {
            hash.append(Integer.toHexString((int) hashes[i]));
        }
        return hash.toString();
    }

}
