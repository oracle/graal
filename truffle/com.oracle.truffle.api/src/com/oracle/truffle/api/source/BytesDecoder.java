/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.charset.*;
import java.util.*;

/**
 * For a language where strings do not map into Java strings, provides utilities to find line
 * endings and to decode raw bytes into an approximate representation for tools to display.
 * <p>
 * See {@link Source#fromBytes}.
 */
public interface BytesDecoder {

    String decode(byte[] bytes, int byteIndex, int length);

    void decodeLines(byte[] bytes, int byteIndex, int length, LineMarker lineMarker);

    public interface LineMarker {

        void markLine(int index);

    }

    public static class UTF8BytesDecoder implements BytesDecoder {

        @Override
        public String decode(byte[] bytes, int byteIndex, int length) {
            return new String(Arrays.copyOfRange(bytes, byteIndex, byteIndex + length), StandardCharsets.UTF_8);
        }

        @Override
        public void decodeLines(byte[] bytes, int byteIndex, int length, LineMarker lineMarker) {
            for (int n = byteIndex; n < byteIndex + length; n++) {
                if (bytes[n] == '\n') {
                    lineMarker.markLine(n + 1);
                }
            }
        }

    }

}
