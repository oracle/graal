// Checkstyle: stop
// @formatter:off
// Class copied from JDK9
package com.oracle.svm.core.jdk8.zipfile;
/*
 * Copyright (c) 1995, 2017, Oracle and/or its affiliates. All rights reserved.
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

//package java.util.zip;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.JDK8OrEarlier;

import sun.nio.cs.ArrayDecoder;
import sun.nio.cs.ArrayEncoder;

/**
 * Utility class for zipfile name and comment decoding and encoding
 */
@SuppressWarnings("all")
@Substitute
@TargetClass(className = "java.util.zip.ZipCoder", onlyWith = JDK8OrEarlier.class)
final class ZipCoder {

    String toString(byte[] ba, int off, int length) {
        CharsetDecoder cd = decoder().reset();
        int len = (int)(length * cd.maxCharsPerByte());
        char[] ca = new char[len];
        if (len == 0)
            return new String(ca);
        // UTF-8 only for now. Other ArrayDeocder only handles
        // CodingErrorAction.REPLACE mode. ZipCoder uses
        // REPORT mode.
        if (isUTF8 && cd instanceof ArrayDecoder) {
            int clen = ((ArrayDecoder)cd).decode(ba, off, length, ca);
            if (clen == -1)    // malformed
                throw new IllegalArgumentException("MALFORMED");
            return new String(ca, 0, clen);
        }
        ByteBuffer bb = ByteBuffer.wrap(ba, off, length);
        CharBuffer cb = CharBuffer.wrap(ca);
        CoderResult cr = cd.decode(bb, cb, true);
        if (!cr.isUnderflow())
            throw new IllegalArgumentException(cr.toString());
        cr = cd.flush(cb);
        if (!cr.isUnderflow())
            throw new IllegalArgumentException(cr.toString());
        return new String(ca, 0, cb.position());
    }

    @Substitute
    String toString(byte[] ba, int length) {
        return toString(ba, 0, length);
    }

    @Substitute
    String toString(byte[] ba) {
        return toString(ba, 0, ba.length);
    }

    @Substitute
    byte[] getBytes(String s) {
        CharsetEncoder ce = encoder().reset();
        char[] ca = s.toCharArray();
        int len = (int)(ca.length * ce.maxBytesPerChar());
        byte[] ba = new byte[len];
        if (len == 0)
            return ba;
        // UTF-8 only for now. Other ArrayDeocder only handles
        // CodingErrorAction.REPLACE mode.
        if (isUTF8 && ce instanceof ArrayEncoder) {
            int blen = ((ArrayEncoder)ce).encode(ca, 0, ca.length, ba);
            if (blen == -1)    // malformed
                throw new IllegalArgumentException("MALFORMED");
            return Arrays.copyOf(ba, blen);
        }
        ByteBuffer bb = ByteBuffer.wrap(ba);
        CharBuffer cb = CharBuffer.wrap(ca);
        CoderResult cr = ce.encode(cb, bb, true);
        if (!cr.isUnderflow())
            throw new IllegalArgumentException(cr.toString());
        cr = ce.flush(bb);
        if (!cr.isUnderflow())
            throw new IllegalArgumentException(cr.toString());
        if (bb.position() == ba.length)  // defensive copy?
            return ba;
        else
            return Arrays.copyOf(ba, bb.position());
    }

    // assume invoked only if "this" is not utf8
    
    @Substitute
    byte[] getBytesUTF8(String s) {
        if (isUTF8)
            return getBytes(s);
        if (utf8 == null)
            utf8 = new ZipCoder(StandardCharsets.UTF_8);
        return utf8.getBytes(s);
    }

    @Substitute
    String toStringUTF8(byte[] ba, int len) {
        return toStringUTF8(ba, 0, len);
    }

    String toStringUTF8(byte[] ba, int off, int len) {
        if (isUTF8)
            return toString(ba, off, len);
        if (utf8 == null)
            utf8 = new ZipCoder(StandardCharsets.UTF_8);
        return utf8.toString(ba, off, len);
    }

    @Substitute
    boolean isUTF8() {
        return isUTF8;
    }

    @Substitute
    private Charset cs;
    @Substitute
    private CharsetDecoder dec;
    @Substitute
    private CharsetEncoder enc;
    @Substitute
    private boolean isUTF8;
    @Substitute
    private ZipCoder utf8;

    @Substitute
    private ZipCoder(Charset cs) {
        this.cs = cs;
        this.isUTF8 = cs.name().equals(StandardCharsets.UTF_8.name());
    }

    @Substitute 
    static ZipCoder get(Charset charset) {
        return new ZipCoder(charset);
    }

    @Substitute
    private CharsetDecoder decoder() {
        if (dec == null) {
            dec = cs.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
        }
        return dec;
    }

    @Substitute
    private CharsetEncoder encoder() {
        if (enc == null) {
            enc = cs.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
        }
        return enc;
    }
}
