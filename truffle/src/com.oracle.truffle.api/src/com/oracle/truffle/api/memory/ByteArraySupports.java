/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.api.memory;

import java.nio.ByteOrder;

/**
 * Provides predefined {@link ByteArraySupport} compositions used to access byte arrays either in
 * big-endian or little-endian order. Both compositions have bounds checks enabled.
 * <p>
 * {@link ByteArraySupport}'s child classes are either base implementations or
 * <a href="https://en.wikipedia.org/wiki/Proxy_pattern">proxy</a> objects wrapping a base
 * implementation and adding extra functionality. They can be
 * <a href="https://en.wikipedia.org/wiki/Composition_over_inheritance">composed</a> together to
 * create accessors with specific sets of features.
 * <p>
 * Base implementations:
 * <ul>
 * <li>{@link SimpleByteArraySupport} implements accesses by simply indexing individual bytes.</li>
 * <li>{@link UnsafeByteArraySupport} implements accesses using {@link sun.misc.Unsafe}.</li>
 * </ul>
 * <p>
 * Proxies:
 * <ul>
 * <li>{@link CheckedByteArraySupport} adds bounds checking.</li>
 * <li>{@link ReversedByteArraySupport} reverses bytes ordering.</li>
 * </ul>
 * <p>
 * Compositions are statically created depending on the architecture
 * ({@code System.getProperty("os.arch")}) and its bytes-ordering ({@code ByteOrder.nativeOrder()}).
 *
 * @since 20.3
 */
final class ByteArraySupports {
    private ByteArraySupports() {
    }

    static final ByteArraySupport LITTLE_ENDIAN;
    static final ByteArraySupport BIG_ENDIAN;

    static {
        // We only use Unsafe for platforms that we know support it, and that support unaligned
        // accesses.
        if (System.getProperty("os.arch").equals("x86_64") || System.getProperty("os.arch").equals("aarch64") || System.getProperty("os.arch").equals("amd64")) {
            final ByteArraySupport nativeOrder = new CheckedByteArraySupport(new UnsafeByteArraySupport());
            if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
                BIG_ENDIAN = nativeOrder;
                LITTLE_ENDIAN = new ReversedByteArraySupport(nativeOrder);
            } else {
                BIG_ENDIAN = new ReversedByteArraySupport(nativeOrder);
                LITTLE_ENDIAN = nativeOrder;
            }
        } else {
            BIG_ENDIAN = new SimpleByteArraySupport();
            LITTLE_ENDIAN = new ReversedByteArraySupport(BIG_ENDIAN);
        }
    }
}
