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
package jdk.graal.compiler.hotspot.test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests {@link java.util.Base64} intrinsics.
 */
@SuppressWarnings("javadoc")
public class Base64SubstitutionsTest extends GraalOSRTestBase {

    private static final HexFormat HF = HexFormat.ofDelimiter(", ").withPrefix("0x");

    private void assertByteArraysEqual(Object expect, Object actual) {
        String message = String.format("%n%s !=%n%s", HF.formatHex((byte[]) expect), HF.formatHex((byte[]) actual));
        assertDeepEquals(message, expect, actual);
    }

    record EncoderTestCase(Encoder encoder, byte[][] encoded) {
    }

    record DecoderTestCase(String type, Decoder decoder, byte[][] encoded) {
    }

    record Results(Result expect, Result actual) {
    }

    private Results lastResults;

    /**
     * Interpose to capture last test results.
     */
    @Override
    protected void assertEquals(Result expect, Result actual) {
        lastResults = new Results(expect, actual);
        super.assertEquals(expect, actual);
    }

    /**
     * A byte[] supplier that creates an array given a length and an optional suffix.
     */
    static class ByteArraySupplier implements ArgSupplier {
        final int length;
        final byte[] suffix;
        List<byte[]> supplied = new ArrayList<>();

        ByteArraySupplier(int length) {
            this(length, null);
        }

        ByteArraySupplier(int length, byte[] suffix) {
            this.length = length;
            this.suffix = suffix;
        }

        @Override
        public Object get() {
            if (suffix != null) {
                byte[] res = new byte[length + suffix.length];
                System.arraycopy(suffix, 0, res, length, suffix.length);
                supplied.add(res);
                return res;
            }
            byte[] res = new byte[length];
            supplied.add(res);
            return res;
        }
    }

    private static EncoderTestCase[] getEncoders() {
        return new EncoderTestCase[]{
                        new EncoderTestCase(Base64.getEncoder(), BASE_ENCODED_TEXT),
                        new EncoderTestCase(Base64.getMimeEncoder(), MIME_ENCODED_TEXT),
                        new EncoderTestCase(Base64.getUrlEncoder(), URL_ENCODED_TEXT)
        };
    }

    private static DecoderTestCase[] getDecoders() {
        return new DecoderTestCase[]{
                        new DecoderTestCase("base", Base64.getDecoder(), BASE_ENCODED_TEXT),
                        new DecoderTestCase("mime", Base64.getMimeDecoder(), MIME_ENCODED_TEXT),
                        new DecoderTestCase("url", Base64.getUrlDecoder(), URL_ENCODED_TEXT)
        };
    }

    /**
     * Tests {@link Encoder#encode(byte[])}.
     */
    @Test
    public void testEncodeByteArray1() {
        for (EncoderTestCase tc : getEncoders()) {
            ResolvedJavaMethod m = getResolvedJavaMethod(Encoder.class, "encode", byte[].class);
            for (int i = 0; i < PLAIN_TEXT_BYTES.length; i++) {
                byte[] srcBytes = PLAIN_TEXT_BYTES[i];
                test(m, tc.encoder, srcBytes);
                assertByteArraysEqual(tc.encoded[i], lastResults.actual.returnValue);
            }
        }
    }

    /**
     * Tests {@link Encoder#encode(byte[], byte[])}.
     */
    @Test
    public void testEncodeByteArray2() {
        ResolvedJavaMethod m = getResolvedJavaMethod(Encoder.class, "encode", byte[].class, byte[].class);
        for (EncoderTestCase tc : getEncoders()) {
            for (int i = 0; i < PLAIN_TEXT_BYTES.length; i++) {
                byte[] srcBytes = PLAIN_TEXT_BYTES[i];
                ByteArraySupplier bas = new ByteArraySupplier(tc.encoded[i].length);
                test(m, tc.encoder, srcBytes, bas);
                Assert.assertEquals(lastResults.actual.returnValue, tc.encoded[i].length);
                Assert.assertEquals(bas.supplied.size(), 2);
                byte[] expect = bas.supplied.get(0);
                byte[] actual = bas.supplied.get(1);
                assertByteArraysEqual(expect, actual);
                assertByteArraysEqual(tc.encoded[i], actual);
            }
        }
    }

    public static byte[] encodeByteBufferSnippet(Encoder encoder, ByteBuffer srcBuf) {
        ByteBuffer dstBuf = encoder.encode(srcBuf);
        srcBuf.rewind();
        return dstBuf.array();
    }

    /**
     * Test {@link Encoder#encode(ByteBuffer)}.
     */
    @Test
    public void testEncodeByteBuffer() {
        for (EncoderTestCase tc : getEncoders()) {
            for (int i = 0; i < PLAIN_TEXT_BYTES.length; i++) {
                byte[] srcBytes = PLAIN_TEXT_BYTES[i];
                ByteBuffer srcBuf = ByteBuffer.wrap(srcBytes);
                test("encodeByteBufferSnippet", tc.encoder, srcBuf);
                assertByteArraysEqual(tc.encoded[i], lastResults.actual.returnValue);
            }
        }
    }

    /**
     * Tests {@link Encoder#encodeToString(byte[])}.
     */
    @Test
    public void testEncodeToString() {
        ResolvedJavaMethod m = getResolvedJavaMethod(Encoder.class, "encodeToString", byte[].class);
        for (EncoderTestCase tc : getEncoders()) {
            for (int i = 0; i < PLAIN_TEXT_BYTES.length; i++) {
                byte[] srcBytes = PLAIN_TEXT_BYTES[i];
                test(m, tc.encoder, srcBytes);
            }
        }
    }

    /**
     * Tests OSR compilation of {@code Encoder#encode0(byte[], int, int, byte[])}.
     */
    @Test
    public void testEncode0() {
        ResolvedJavaMethod method = getResolvedJavaMethod(Encoder.class, "encode0", byte[].class, int.class, int.class, byte[].class);
        compileOSR(getInitialOptions(), method);
    }

    /**
     * Supplies illegal Base64 characters.
     */
    static class IllegalBase64CharSupplier implements Supplier<Byte> {
        final byte[] illegals;

        IllegalBase64CharSupplier(boolean url) {
            Set<Byte> set = new HashSet<>();
            for (int val = 0; val < 256; val++) {
                if ((val >= 'A' && val <= 'Z') ||
                                (val >= 'a' && val <= 'z') ||
                                (val >= '0' && val <= '9') || val == '=') {
                    continue;
                }
                if (url) {
                    if (val == '-' || val == '_') {
                        continue;
                    }
                } else {
                    if (val == '+' || val == '/') {
                        continue;
                    }
                }
                set.add((byte) val);
            }
            illegals = new byte[set.size()];
            int i = 0;
            for (Byte b : set) {
                illegals[i++] = b;
            }
            assert i == illegals.length;
        }

        int next;

        @Override
        public Byte get() {
            return illegals[next++ % illegals.length];
        }
    }

    /**
     * Tests {@link Decoder#decode(byte[])}.
     */
    @Test
    public void testDecodeByteArray1() {
        Random ran = getRandomInstance();
        ResolvedJavaMethod m = getResolvedJavaMethod(Decoder.class, "decode", byte[].class);
        for (DecoderTestCase tc : getDecoders()) {
            IllegalBase64CharSupplier illegals = new IllegalBase64CharSupplier(tc.type.equals("url"));
            for (int i = 0; i < PLAIN_TEXT_BYTES.length; i++) {
                byte[] srcBytes = tc.encoded[i];
                test(m, tc.decoder, srcBytes);
                assertDeepEquals(PLAIN_TEXT_BYTES[i], lastResults.actual.returnValue);

                // test that an illegal Base64 character is detected
                if (!tc.type.equals("mime") && srcBytes.length != 0) {
                    byte[] srcBytesCopy = srcBytes.clone();
                    int bytePosToCorrupt = ran.nextInt(srcBytesCopy.length);
                    byte illegal = illegals.get();
                    srcBytesCopy[bytePosToCorrupt] = illegal;
                    Result result = executeActual(m, tc.decoder, srcBytesCopy);
                    if (!(result.exception instanceof IllegalArgumentException)) {
                        String hexBuf = HexFormat.ofDelimiter(", ").withPrefix("0x").formatHex(srcBytesCopy);
                        throw new AssertionError(String.format("%s decoder did not catch illegal base64 character 0x%02x at position %d in encoded buffer of length %d%nbuf:%s",
                                        tc.type, illegal, bytePosToCorrupt, srcBytesCopy.length, hexBuf));
                    }
                }
            }
        }
    }

    /**
     * Tests {@link Decoder#decode(byte[], byte[])}.
     */
    @Test
    public void testDecodeByteArray2() {
        ResolvedJavaMethod m = getResolvedJavaMethod(Decoder.class, "decode", byte[].class, byte[].class);
        for (DecoderTestCase tc : getDecoders()) {
            for (int i = 0; i < PLAIN_TEXT_BYTES.length; i++) {
                byte[] srcBytes = tc.encoded[i];
                // JDK-8273108: Test for output buffer overrun
                byte[] suffix = {0, (byte) 167};
                ByteArraySupplier bas = new ByteArraySupplier(srcBytes.length, suffix);
                test(m, tc.decoder, srcBytes, bas);
                Assert.assertEquals(bas.supplied.size(), 2);
                byte[] expect = Arrays.copyOfRange(bas.supplied.get(0), 0, srcBytes.length);
                byte[] actual = Arrays.copyOfRange(bas.supplied.get(1), 0, srcBytes.length);
                assertByteArraysEqual(expect, actual);

                byte[] actualSuffix = Arrays.copyOfRange(bas.supplied.get(1), srcBytes.length, srcBytes.length + suffix.length);
                assertByteArraysEqual(suffix, actualSuffix);
            }
        }
    }

    public static byte[] decodeByteBufferSnippet(Decoder decoder, ByteBuffer srcBuf) {
        ByteBuffer dstBuf = decoder.decode(srcBuf);
        srcBuf.rewind();
        return dstBuf.array();
    }

    /**
     * Tests {@link Decoder#decode(ByteBuffer)}.
     */
    @Test
    public void testDecodeByteBuffer() {
        for (DecoderTestCase tc : getDecoders()) {
            for (int i = 0; i < PLAIN_TEXT_BYTES.length; i++) {
                byte[] srcBytes = tc.encoded[i];
                ByteBuffer srcBuf = ByteBuffer.wrap(srcBytes);
                test("decodeByteBufferSnippet", tc.decoder, srcBuf);
            }
        }
    }

    /**
     * Tests {@link Decoder#decode(String)}.
     */
    @Test
    public void testDecodeString() {
        ResolvedJavaMethod m = getResolvedJavaMethod(Decoder.class, "decode", String.class);
        for (DecoderTestCase tc : getDecoders()) {
            for (int i = 0; i < PLAIN_TEXT_BYTES.length; i++) {
                byte[] srcBytes = tc.encoded[i];
                String srcString = new String(srcBytes, StandardCharsets.ISO_8859_1);
                test(m, tc.decoder, srcString);
                assertByteArraysEqual(PLAIN_TEXT_BYTES[i], lastResults.actual.returnValue);
            }
        }
    }

    /**
     * Tests OSR compilation of {@code Decoder#decode0(byte[], int, int, byte[])}.
     */
    @Test
    public void testDecode0() {
        ResolvedJavaMethod method = getResolvedJavaMethod(Decoder.class, "decode0", byte[].class, int.class, int.class, byte[].class);
        compileOSR(getInitialOptions(), method);
    }

    // @formatter:off
    private static final String[] PLAIN_TEXT = """
                    This test data is part of rfc2045 which includes all characters a~z A~Z, 0~9 and all symbols,
                    It is used to test java.util.Base64.Encoder, and will be encoded by org.apache.commons.codec.binary.Base64.java
                    to test java.util.Base64.Decoder;

                    Freed & Borenstein          Standards Track                     [Page 1]
                    RFC 2045                Internet Message Bodies            November 1996

                       These documents are revisions of RFCs 1521, 1522, and 1590, which
                       themselves were revisions of RFCs 1341 and 1342.  An appendix in RFC
                       2049 describes differences and changes from previous versions.

                    Table of Contents

                       1. Introduction .........................................    3
                       2. Definitions, Conventions, and Generic BNF Grammar ....    5
                       3. MIME Header Fields ...................................    8
                       4. MIME-Version Header Field ............................    8
                       5. Content-Type Header Field ............................   10
                       6. Content-Transfer-Encoding Header Field ...............   14
                       7. Content-ID Header Field ..............................   26
                       8. Content-Description Header Field .....................   27
                       9. Additional MIME Header Fields ........................   27
                       10. Summary .............................................   27
                       11. Security Considerations .............................   27
                       12. Authors' Addresses ..................................   28
                       A. Collected Grammar ....................................   29

                    Freed & Borenstein          Standards Track                     [Page 7]
                    RFC 2045                Internet Message Bodies            November 1996

                    3.  MIME Header Fields

                       MIME defines a number of new RFC 822 header fields that are used to
                       describe the content of a MIME entity.  These header fields occur in
                       at least two contexts:

                        (1)   As part of a regular RFC 822 message header.

                        (2)   In a MIME body part header within a multipart
                              construct.

                       The formal definition of these header fields is as follows:

                         MIME-message-headers := entity-headers
                                                 fields
                                                 version CRLF
                                                 ; The ordering of the header
                                                 ; fields implied by this BNF
                                                 ; definition should be ignored.

                         MIME-part-headers := entity-headers
                                              [ fields ]
                                              ; Any field not beginning with
                                              ; "content-" can have no defined
                                              ; meaning and may be ignored.
                                              ; The ordering of the header
                                              ; fields implied by this BNF
                                              ; definition should be ignored.

                       The syntax of the various specific MIME header fields will be
                       described in the following sections.

                    Freed & Borenstein          Standards Track                    [Page 11]
                    RFC 2045                Internet Message Bodies            November 1996

                    5.1.  Syntax of the Content-Type Header Field

                       In the Augmented BNF notation of RFC 822, a Content-Type header field
                       value is defined as follows:

                         content := "Content-Type" ":" type "/" subtype
                                    *(";" parameter)
                                    ; Matching of media type and subtype
                                    ; is ALWAYS case-insensitive.

                         type := discrete-type / composite-type

                         discrete-type := "text" / "image" / "audio" / "video" /
                                          "application" / extension-token

                         composite-type := "message" / "multipart" / extension-token

                         extension-token := ietf-token / x-token

                         ietf-token := <An extension token defined by a
                                        standards-track RFC and registered
                                        with IANA.>

                         x-token := <The two characters "X-" or "x-" followed, with
                                     no intervening white space, by any token>

                         subtype := extension-token / iana-token

                         iana-token := <A publicly-defined extension token. Tokens
                                        of this form must be registered with IANA
                                        as specified in RFC 2048.>

                         parameter := attribute "=" value

                         attribute := token
                                      ; Matching of attributes
                                      ; is ALWAYS case-insensitive.

                         value := token / quoted-string

                         token := 1*<any (US-ASCII) CHAR except SPACE, CTLs,
                                     or tspecials>

                         tspecials :=  "(" / ")" / "<" / ">" / "@" /
                                       "," / ";" / ":" / "\" / <">
                                       "/" / "[" / "]" / "?" / "="
                                       ; Must be in quoted-string,
                                       ; to use within parameter values

                         description := "Content-Description" ":" *text

                         encoding := "Content-Transfer-Encoding" ":" mechanism

                         entity-headers := [ content CRLF ]
                                        [ encoding CRLF ]
                                        [ id CRLF ]
                                        [ description CRLF ]
                                        *( MIME-extension-field CRLF )

                         hex-octet := "=" 2(DIGIT / "A" / "B" / "C" / "D" / "E" / "F")
                                   ; Octet must be used for characters > 127, =,
                                   ; SPACEs or TABs at the ends of lines, and is
                                   ; recommended for any character not listed in
                                   ; RFC 2049 as "mail-safe".

                    RFC 2045                Internet Message Bodies            November 1996

                              must be used.  An equal sign as the last character on a
                              encoded line indicates such a non-significant ("soft")
                              line break in the encoded text.

                       Thus if the "raw" form of the line is a single unencoded line that
                       says:

                         Now's the time for all folk to come to the aid of their country.

                       This can be represented, in the Quoted-Printable encoding, as:

                         Now's the time =
                         for all folk to come=
                          to the aid of their country.

                       Since the hyphen character ("-") may be represented as itself in the
                       Quoted-Printable encoding, care must be taken, when encapsulating a
                       quoted-printable encoded body inside one or more multipart entities,
                       to ensure that the boundary delimiter does not appear anywhere in the
                       encoded body.  (A good strategy is to choose a boundary that includes
                       a character sequence such as "=_" which can never appear in a
                       quoted-printable body.  See the definition of multipart messages in
                       RFC 2046.)

                         !"#$@[\\]^`{|}~%

                    Freed &
                        Borenstein Standards Track[Page 24]

                        RFC 2045
                        Internet Message
                        Bodies November 1996

                        Table 1:
                        The Base64
                        Alphabet

                        Value
                        Encoding Value
                        Encoding Value
                        Encoding Value Encoding 0 A 17 R 34 i 51 z 1 B 18 S 35 j 52 0 2 C 19 T 36 k 53 1 3 D 20 U 37 l 54 2 4 E 21 V 38 m 55 3 5 F 22 W 39 n 56 4 6 G 23 X 40 o 57 5 7 H 24 Y 41 p 58 6 8 I 25 Z 42 q 59 7 9 J 26 a 43 r 60 8 10 K 27 b 44 s 61 9 11 L 28 c 45 t 62+12 M 29 d 46 u 63/13 N 30 e 47 v 14 O 31 f 48

                        w         (pad) =
                            15 P            32 g            49 x
                            16 Q            33 h            50 y
                    """
                    .lines().toArray(String[]::new);
    // @formatter:on

    private static final byte[][] PLAIN_TEXT_BYTES = Stream.of(PLAIN_TEXT).map(s -> s.getBytes(StandardCharsets.US_ASCII)).toArray(byte[][]::new);

    private static final byte[][] BASE_ENCODED_TEXT = Stream.of(PLAIN_TEXT_BYTES).map(s -> Base64.getEncoder().encode(s)).toArray(byte[][]::new);
    private static final byte[][] MIME_ENCODED_TEXT = Stream.of(PLAIN_TEXT_BYTES).map(s -> Base64.getMimeEncoder().encode(s)).toArray(byte[][]::new);
    private static final byte[][] URL_ENCODED_TEXT = Stream.of(PLAIN_TEXT_BYTES).map(s -> Base64.getUrlEncoder().encode(s)).toArray(byte[][]::new);
}
