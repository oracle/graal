/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.print;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.core.util.VMError;

/**
 * Buffers bytes written to it and decodes them back to characters.
 */
public class JSDecodeBuffer {

    public final Charset cs;

    private final ArrayList<Byte> bytes = new ArrayList<>(128);

    private final List<Character> chars = new ArrayList<>(128);

    public JSDecodeBuffer(Charset cs) {
        this.cs = cs;
    }

    public JSDecodeBuffer() {
        this(Charset.defaultCharset());
    }

    public void write(byte b) {
        bytes.add(b);
        tryDecode();
    }

    public void write(byte[] bs) {
        write(bs, 0, bs.length);
    }

    public void write(byte[] bs, int offset, int len) {
        bytes.ensureCapacity(bytes.size() + len);
        for (int i = 0; i < len; i++) {
            bytes.add(bs[offset + i]);
        }
        tryDecode();
    }

    public int remaining() {
        return chars.size();
    }

    public char[] popAll() {
        char[] c = new char[remaining()];

        for (int i = 0; i < c.length; i++) {
            c[i] = chars.get(i);
        }

        chars.clear();
        return c;
    }

    public boolean hasNext() {
        return remaining() > 0;
    }

    protected void tryDecode() {
        ByteBuffer bb = ByteBuffer.allocate(bytes.size());

        bb.mark();

        for (Byte b : bytes) {
            bb.put(b);
        }

        bb.reset();
        bytes.clear();

        try {
            CharBuffer cb = decode(bb);
            int limit = cb.limit();
            for (int i = 0; i < limit; i++) {
                chars.add(cb.get(i));
            }

            /*
             * In case not all bytes could be parsed (e.g. if only parts of a single codepoint was
             * added), we add those bytes back to our byte buffer to be parsed next time.
             */
            while (bb.hasRemaining()) {
                bytes.add(bb.get());
            }

        } catch (CharacterCodingException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    /**
     * Decodes the bytes from the given ByteBuffer while allowing the BB to contain incomplete
     * codepoints at the end.
     *
     * This is basically a copy of {@link CharsetDecoder#decode(ByteBuffer)}, with the exception
     * that it doesn't assume `endOfInput == true`.
     */
    protected CharBuffer decode(ByteBuffer in) throws CharacterCodingException {
        CharsetDecoder dec = cs.newDecoder();

        int n = (int) (in.remaining() * dec.averageCharsPerByte());
        CharBuffer out = CharBuffer.allocate(n);

        if ((n == 0) && (in.remaining() == 0)) {
            return out;
        }
        dec.reset();
        for (;;) {
            CoderResult cr = dec.decode(in, out, false);

            if (cr.isUnderflow()) {
                break;
            }
            if (cr.isOverflow()) {
                n = 2 * n + 1;    // Ensure progress; n might be 0!
                CharBuffer o = CharBuffer.allocate(n);
                out.flip();
                o.put(out);
                out = o;
                continue;
            }
            cr.throwException();
        }
        out.flip();
        return out;
    }
}
