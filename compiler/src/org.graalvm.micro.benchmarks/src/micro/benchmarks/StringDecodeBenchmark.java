/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.charset.Charset;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Thread)
public class StringDecodeBenchmark extends BenchmarkBase {

    // Reduced by default to only UTF-8, previous coverage:
    // @Param({"US-ASCII", "ISO-8859-1", "UTF-8", "MS932", "ISO-8859-6", "ISO-2022-KR"})
    @Param({"UTF-8"}) private String charsetName;

    private Charset charset;
    private byte[] asciiString;
    private byte[] longAsciiString;
    private byte[] utf16String;
    private byte[] longUtf16EndString;
    private byte[] longUtf16StartString;
    private byte[] longUtf16OnlyString;
    private byte[] latin1String;
    private byte[] longLatin1EndString;
    private byte[] longLatin1StartString;
    private byte[] longLatin1OnlyString;

    private static final String LOREM = """
                    Lorem ipsum dolor sit amet, consectetur adipiscing elit. Aliquam ac sem eu
                    urna egestas placerat. Etiam finibus ipsum nulla, non mattis dolor cursus a.
                    Nulla nec nisl consectetur, lacinia neque id, accumsan ante. Curabitur et
                    sapien in magna porta ultricies. Sed vel pellentesque nibh. Pellentesque dictum
                    dignissim diam eu ultricies. Class aptent taciti sociosqu ad litora torquent
                    per conubia nostra, per inceptos himenaeos. Suspendisse erat diam, fringilla
                    sed massa sed, posuere viverra orci. Suspendisse tempor libero non gravida
                    efficitur. Vivamus lacinia risus non orci viverra, at consectetur odio laoreet.
                    Suspendisse potenti.""";
    private static final String UTF16_STRING = "\uFF11".repeat(31);
    private static final String LATIN1_STRING = "\u00B6".repeat(31);

    @Setup
    public void setup() {
        charset = Charset.forName(charsetName);
        asciiString = LOREM.substring(0, 32).getBytes(charset);
        longAsciiString = LOREM.repeat(200).getBytes(charset);
        utf16String = "UTF-\uFF11\uFF16 string".getBytes(charset);
        longUtf16EndString = LOREM.repeat(4).concat(UTF16_STRING).getBytes(charset);
        longUtf16StartString = UTF16_STRING.concat(LOREM.repeat(4)).getBytes(charset);
        longUtf16OnlyString = UTF16_STRING.repeat(10).getBytes(charset);
        latin1String = LATIN1_STRING.getBytes(charset);
        longLatin1EndString = LOREM.repeat(4).concat(LATIN1_STRING).getBytes(charset);
        longLatin1StartString = LATIN1_STRING.concat(LOREM.repeat(4)).getBytes(charset);
        longLatin1OnlyString = LATIN1_STRING.repeat(10).getBytes(charset);
    }

    @Benchmark
    public void decodeAsciiShort(Blackhole bh) {
        bh.consume(new String(asciiString, charset));
        bh.consume(new String(longAsciiString, 0, 15, charset));
        bh.consume(new String(asciiString, 0, 3, charset));
        bh.consume(new String(longAsciiString, 512, 7, charset));
    }

    @Benchmark
    public void decodeAsciiLong(Blackhole bh) {
        bh.consume(new String(longAsciiString, charset));
        bh.consume(new String(longAsciiString, 0, 1024 + 31, charset));
    }

    @Benchmark
    public void decodeLatin1Short(Blackhole bh) {
        bh.consume(new String(latin1String, charset));
        bh.consume(new String(latin1String, 0, 15, charset));
        bh.consume(new String(latin1String, 0, 3, charset));
        bh.consume(new String(longLatin1OnlyString, 512, 7, charset));
    }

    @Benchmark
    public String decodeLatin1LongStart() {
        return new String(longLatin1StartString, charset);
    }

    @Benchmark
    public String decodeLatin1LongEnd() {
        return new String(longLatin1EndString, charset);
    }

    @Benchmark
    public String decodeLatin1LongOnly() {
        return new String(longLatin1OnlyString, charset);
    }

    @Benchmark
    public void decodeLatin1Mixed(Blackhole bh) {
        bh.consume(new String(longLatin1EndString, charset));
        bh.consume(new String(longLatin1StartString, charset));
        bh.consume(new String(latin1String, charset));
        bh.consume(new String(longLatin1OnlyString, charset));
    }

    @Benchmark
    public void decodeUTF16Short(Blackhole bh) {
        bh.consume(new String(utf16String, charset));
        bh.consume(new String(utf16String, 0, 15, charset));
        bh.consume(new String(utf16String, 0, 3, charset));
        bh.consume(new String(utf16String, 0, 7, charset));
    }

    @Benchmark
    public String decodeUTF16LongEnd() {
        return new String(longUtf16EndString, charset);
    }

    @Benchmark
    public String decodeUTF16LongStart() {
        return new String(longUtf16StartString, charset);
    }

    @Benchmark
    public String decodeUTF16LongOnly() {
        return new String(longUtf16OnlyString, charset);
    }

    @Benchmark
    public void decodeUTF16Mixed(Blackhole bh) {
        bh.consume(new String(longUtf16StartString, charset));
        bh.consume(new String(longUtf16EndString, charset));
        bh.consume(new String(utf16String, charset));
        bh.consume(new String(longUtf16OnlyString, charset));
    }

    @Benchmark
    public void decodeAllMixed(Blackhole bh) {
        bh.consume(new String(utf16String, charset));
        bh.consume(new String(longUtf16EndString, charset));
        bh.consume(new String(utf16String, 0, 15, charset));
        bh.consume(new String(longUtf16StartString, charset));
        bh.consume(new String(asciiString, 0, 3, charset));
        bh.consume(new String(longUtf16OnlyString, charset));
        bh.consume(new String(latin1String, charset));
        bh.consume(new String(longLatin1EndString, charset));
        bh.consume(new String(longLatin1StartString, charset));
        bh.consume(new String(latin1String, 0, 7, charset));
        bh.consume(new String(longLatin1OnlyString, charset));
        bh.consume(new String(asciiString, charset));
        bh.consume(new String(longAsciiString, charset));
    }

    @Benchmark
    public void decodeShortMixed(Blackhole bh) {
        bh.consume(new String(utf16String, 0, 15, charset));
        bh.consume(new String(latin1String, 0, 15, charset));
        bh.consume(new String(asciiString, charset));
        bh.consume(new String(utf16String, charset));
        bh.consume(new String(latin1String, 0, 3, charset));
        bh.consume(new String(asciiString, 0, 3, charset));
        bh.consume(new String(utf16String, 0, 7, charset));
        bh.consume(new String(latin1String, charset));
        bh.consume(new String(asciiString, 0, 7, charset));
        bh.consume(new String(utf16String, 0, 3, charset));
        bh.consume(new String(latin1String, 0, 7, charset));
        bh.consume(new String(asciiString, 0, 15, charset));
    }
}
