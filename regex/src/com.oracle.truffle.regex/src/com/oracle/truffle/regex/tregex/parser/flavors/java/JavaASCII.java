/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.regex.tregex.parser.flavors.java;

import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.tregex.string.Encodings;

// Sets corresponding to the contents of java.util.regex.ASCII
class JavaASCII {
    static final CodePointSet UPPER = CodePointSet.createNoDedup(0x41, 0x5A);
    static final CodePointSet LOWER = CodePointSet.createNoDedup(0x61, 0x7A);
    static final CodePointSet DIGIT = CodePointSet.createNoDedup(0x30, 0x39);
    static final CodePointSet SPACE = CodePointSet.createNoDedup(0x09, 0x0D, 0x20, 0x20);
    static final CodePointSet NON_SPACE = SPACE.createInverse(Encodings.UTF_16);
    static final CodePointSet PUNCT = CodePointSet.createNoDedup(0x21, 0x2F, 0x3A, 0x40, 0x5B, 0x60, 0x7B, 0x7E);
    static final CodePointSet CNTRL = CodePointSet.createNoDedup(0x00, 0x1F, 0x7F, 0x7F);
    static final CodePointSet BLANK = CodePointSet.createNoDedup(0x09, 0x09, 0x20, 0x20);
    static final CodePointSet HEX = CodePointSet.createNoDedup(0x30, 0x39, 0x41, 0x46, 0x61, 0x66);
    static final CodePointSet ALPHA = UPPER.union(LOWER);
    static final CodePointSet ALNUM = ALPHA.union(DIGIT);
    static final CodePointSet GRAPH = ALNUM.union(PUNCT);
}
