/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import static jdk.graal.compiler.util.Digest.DigestBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

public class NativeImageClassLoaderSupportTest {

    @Test
    public void pathDigestForSmallInputMatchesSingleArrayDigest() throws IOException {
        byte[] input = createInput(1024);

        DigestBuilder expected = new DigestBuilder();
        expected.update(input);

        Assert.assertArrayEquals(expected.digest(), digest(input, 1));
    }

    @Test
    public void pathDigestForLargeInputIgnoresShortReadBoundaries() throws IOException {
        byte[] input = createInput(200_000);
        byte[] expected = digest(input, input.length);

        Assert.assertArrayEquals(expected, digest(input, 1));
        Assert.assertArrayEquals(expected, digest(input, 17));
        Assert.assertArrayEquals(expected, digest(input, 8191));
    }

    private static byte[] digest(byte[] input, int maxBytesPerRead) throws IOException {
        DigestBuilder db = new DigestBuilder();
        NativeImageClassLoaderSupport.updatePathDigest(db, new ShortReadInputStream(input, maxBytesPerRead));
        return db.digest();
    }

    private static byte[] createInput(int length) {
        byte[] input = new byte[length];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) (31 * i + 7);
        }
        return input;
    }

    private static final class ShortReadInputStream extends ByteArrayInputStream {
        private final int maxBytesPerRead;

        private ShortReadInputStream(byte[] input, int maxBytesPerRead) {
            super(Arrays.copyOf(input, input.length));
            this.maxBytesPerRead = maxBytesPerRead;
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) {
            return super.read(b, off, Math.min(len, maxBytesPerRead));
        }
    }
}
