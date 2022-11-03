/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package micro.benchmarks;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

// Copied from jdk/test/micro/org/openjdk/bench/java/nio/CharsetEncodeDecode.java
@State(Scope.Thread)
public class CharsetEncodeDecodeBenchmark extends BenchmarkBase {

    private byte[] bytes;
    private char[] chars;

    private CharsetEncoder encoder;
    private CharsetDecoder decoder;

    // "BIG5" is not supported on Java 11. ISO-8859-15 is not supported on the Native Image as it's
    // not among standard charsets (java.nio.charset.StandardCharsets).
    @Param({"UTF-8", "ISO-8859-1", "ASCII", "UTF-16"}) private String type;

    @Param("16384") private int size;

    @Setup
    public void prepare() {
        bytes = new byte[size];
        chars = new char[size];
        for (int i = 0; i < size; ++i) {
            int val = 48 + (i % 16);
            bytes[i] = (byte) val;
            chars[i] = (char) val;
        }

        encoder = Charset.forName(type).newEncoder();
        decoder = Charset.forName(type).newDecoder();
    }

    @Benchmark
    public ByteBuffer encode() throws CharacterCodingException {
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        return encoder.encode(charBuffer);
    }

    @Benchmark
    public CharBuffer decode() throws CharacterCodingException {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        return decoder.decode(byteBuffer);
    }
}
