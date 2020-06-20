/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodes.dfa;

import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.charset.CharMatchers;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.buffer.ObjectArrayBuffer;
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;

/**
 * Container for character matchers of DFA transitions, potentially specialized for a given string
 * encoding.
 */
public abstract class Matchers extends Node {

    private final short noMatchSuccessor;

    Matchers(short noMatchSuccessor) {
        this.noMatchSuccessor = noMatchSuccessor;
    }

    public short getNoMatchSuccessor() {
        return noMatchSuccessor;
    }

    /**
     * Returns the number of transitions represented by this object.
     */
    public abstract int size();

    static int size(CharMatcher[]... matchersArr) {
        for (CharMatcher[] matchers : matchersArr) {
            if (matchers != null) {
                return matchers.length;
            }
        }
        return 0;
    }

    /**
     * Returns {@code true} iff transition {@code i} matches {@code c}.
     */
    public abstract boolean match(int i, int c);

    /**
     * Returns the index of the transition that matches the given character {@code c}, or
     * {@code noMatchSuccessor}. For debugging purposes.
     */
    public int match(int c) {
        for (int i = 0; i < size(); i++) {
            if (match(i, c)) {
                return i;
            }
        }
        return noMatchSuccessor;
    }

    /**
     * Returns a String representation of transition {@code i}.
     */
    public abstract String toString(int i);

    static boolean match(CharMatcher[] matchers, int i, int c) {
        return matchers != null && matchers[i] != null && matchers[i].execute(c);
    }

    @TruffleBoundary
    static String toString(CharMatcher[] matchers, int i) {
        return matchers == null ? "" : Objects.toString(matchers[i]);
    }

    public static final class SimpleMatchers extends Matchers {

        @Children private final CharMatcher[] matchers;

        public SimpleMatchers(CharMatcher[] matchers, short noMatchSuccessor) {
            super(noMatchSuccessor);
            this.matchers = matchers;
        }

        public CharMatcher[] getMatchers() {
            return matchers;
        }

        @Override
        public int size() {
            return matchers.length;
        }

        @Override
        public boolean match(int i, int c) {
            return match(matchers, i, c);
        }

        @TruffleBoundary
        @Override
        public String toString(int i) {
            return matchers[i].toString();
        }
    }

    public static final class UTF16RawMatchers extends Matchers {

        @Children private final CharMatcher[] latin1;
        @Children private final CharMatcher[] bmp;

        public UTF16RawMatchers(CharMatcher[] latin1, CharMatcher[] bmp, short noMatchSuccessor) {
            super(noMatchSuccessor);
            this.latin1 = latin1;
            this.bmp = bmp;
        }

        public CharMatcher[] getLatin1() {
            return latin1;
        }

        public CharMatcher[] getBmp() {
            return bmp;
        }

        @Override
        public int size() {
            return size(latin1, bmp);
        }

        @Override
        public boolean match(int i, int c) {
            return match(latin1, i, c) || match(bmp, i, c);
        }

        @TruffleBoundary
        @Override
        public String toString(int i) {
            return toString(latin1, i) + toString(bmp, i);
        }
    }

    public static final class UTF16Matchers extends Matchers {

        @Children private final CharMatcher[] latin1;
        @Children private final CharMatcher[] bmp;
        @Children private final CharMatcher[] astral;

        public UTF16Matchers(CharMatcher[] latin1, CharMatcher[] bmp, CharMatcher[] astral, short noMatchSuccessor) {
            super(noMatchSuccessor);
            this.latin1 = latin1;
            this.bmp = bmp;
            this.astral = astral;
        }

        public CharMatcher[] getLatin1() {
            return latin1;
        }

        public CharMatcher[] getBmp() {
            return bmp;
        }

        public CharMatcher[] getAstral() {
            return astral;
        }

        @Override
        public int size() {
            return size(bmp, astral);
        }

        @Override
        public boolean match(int i, int c) {
            return match(bmp, i, c) || match(astral, i, c);
        }

        @TruffleBoundary
        @Override
        public String toString(int i) {
            return toString(bmp, i) + toString(astral, i);
        }
    }

    public static final class UTF8Matchers extends Matchers {

        @Children private final CharMatcher[] ascii;
        @Children private final CharMatcher[] enc2;
        @Children private final CharMatcher[] enc3;
        @Children private final CharMatcher[] enc4;

        public UTF8Matchers(CharMatcher[] ascii, CharMatcher[] enc2, CharMatcher[] enc3, CharMatcher[] enc4, short noMatchSuccessor) {
            super(noMatchSuccessor);
            this.ascii = ascii;
            this.enc2 = enc2;
            this.enc3 = enc3;
            this.enc4 = enc4;
        }

        public CharMatcher[] getAscii() {
            return ascii;
        }

        public CharMatcher[] getEnc2() {
            return enc2;
        }

        public CharMatcher[] getEnc3() {
            return enc3;
        }

        public CharMatcher[] getEnc4() {
            return enc4;
        }

        @Override
        public int size() {
            return size(ascii, enc2, enc3, enc4);
        }

        @Override
        public boolean match(int i, int c) {
            return match(ascii, i, c) || match(enc2, i, c) || match(enc3, i, c) || match(enc4, i, c);
        }

        @TruffleBoundary
        @Override
        public String toString(int i) {
            return toString(ascii, i) + toString(enc2, i) + toString(enc3, i) + toString(enc4, i);
        }
    }

    public static final class Builder {

        private final ObjectArrayBuffer<CharMatcher>[] buffers;
        private short noMatchSuccessor = -1;

        @SuppressWarnings("unchecked")
        public Builder(int nBuffers) {
            buffers = new ObjectArrayBuffer[nBuffers];
            for (int i = 0; i < buffers.length; i++) {
                buffers[i] = new ObjectArrayBuffer<>();
            }
        }

        public void reset(int nTransitions) {
            for (ObjectArrayBuffer<CharMatcher> buf : buffers) {
                buf.asFixedSizeArray(nTransitions);
            }
            noMatchSuccessor = -1;
        }

        public ObjectArrayBuffer<CharMatcher> getBuffer(int i) {
            return buffers[i];
        }

        public short getNoMatchSuccessor() {
            return noMatchSuccessor;
        }

        public void setNoMatchSuccessor(short noMatchSuccessor) {
            this.noMatchSuccessor = noMatchSuccessor;
        }

        public int estimatedCost(int i) {
            int ret = 0;
            for (ObjectArrayBuffer<CharMatcher> buf : buffers) {
                if (buf != null && buf.get(i) != null) {
                    ret = Math.max(ret, buf.get(i).estimatedCost());
                }
            }
            return ret;
        }

        public void createSplitMatcher(int i, CodePointSet cps, CompilationBuffer compilationBuffer, CodePointSet... splitRanges) {
            for (int j = 0; j < splitRanges.length; j++) {
                CodePointSet intersection = splitRanges[j].createIntersection(cps, compilationBuffer);
                if (intersection.matchesSomething()) {
                    assert i < buffers[j].length();
                    buffers[j].set(i, CharMatchers.createMatcher(intersection, compilationBuffer));
                }
            }
        }

        public CharMatcher[] materialize(int buf) {
            return isEmpty(buffers[buf]) ? null : buffers[buf].toArray(new CharMatcher[buffers[buf].length()]);
        }

        private static boolean isEmpty(ObjectArrayBuffer<CharMatcher> buf) {
            for (CharMatcher m : buf) {
                if (m != null) {
                    return false;
                }
            }
            return true;
        }
    }
}
