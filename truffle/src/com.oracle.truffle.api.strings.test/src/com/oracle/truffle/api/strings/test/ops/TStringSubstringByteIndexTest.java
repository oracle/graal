/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.api.strings.test.ops;

import static org.junit.runners.Parameterized.Parameter;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.MutableTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.test.TStringTestBase;

@RunWith(Parameterized.class)
public class TStringSubstringByteIndexTest extends TStringTestBase {

    @Parameter public TruffleString.SubstringByteIndexNode node;
    @Parameter(1) public MutableTruffleString.SubstringByteIndexNode nodeMutable;

    @Parameters(name = "{0}, {1}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(
                        new Object[]{TruffleString.SubstringByteIndexNode.create(), MutableTruffleString.SubstringByteIndexNode.create()},
                        new Object[]{TruffleString.SubstringByteIndexNode.getUncached(), MutableTruffleString.SubstringByteIndexNode.getUncached()});
    }

    @Test
    public void testAll() throws Exception {
        forAllStrings(true, (a, array, codeRange, isValid, encoding, codepoints, byteIndices) -> {
            int end = array.length;
            for (int[] bounds : new int[][]{
                            {0, 0},
                            {end, 0},
                            {0, 1},
                            {0, 2},
                            {1, 1},
                            {1, 2},
                            {end - 1, 1},
                            {end - 2, 2},
                            {0, end - 1},
                            {1, end - 1},
                            {1, end - 2},
                            {0, end}
            }) {
                if (bounds[0] >= 0 && bounds[1] >= 0 && bounds[0] + bounds[1] <= codepoints.length) {
                    int fromByteIndex = bounds[0] == codepoints.length ? array.length : byteIndices[bounds[0]];
                    int byteLength = (bounds[0] + bounds[1] == codepoints.length ? array.length : byteIndices[bounds[0] + bounds[1]]) - fromByteIndex;
                    for (AbstractTruffleString b : new AbstractTruffleString[]{
                                    node.execute(a, fromByteIndex, byteLength, encoding, true),
                                    node.execute(a, fromByteIndex, byteLength, encoding, false),
                                    nodeMutable.execute(a, fromByteIndex, byteLength, encoding),
                    }) {
                        assertBytesEqual(b, encoding, array, fromByteIndex, byteLength);
                        if (bounds[1] == codepoints.length && a instanceof TruffleString && b instanceof TruffleString) {
                            Assert.assertSame(a, b);
                        } else {
                            Assert.assertNotSame(a, b);
                        }
                    }
                }
            }
        });
    }

    @Test
    public void testNull() throws Exception {
        checkNullSE((s, e) -> node.execute(s, 0, 1, e, false));
        checkNullSE((s, e) -> nodeMutable.execute(s, 0, 1, e));
    }

    @Test
    public void testOutOfBounds() throws Exception {
        checkOutOfBoundsRegion(true, (a, fromIndex, length, encoding) -> node.execute(a, fromIndex, length, encoding, false));
        checkOutOfBoundsRegion(true, (a, fromIndex, length, encoding) -> node.execute(a, fromIndex, length, encoding, true));
        checkOutOfBoundsRegion(true, (a, fromIndex, length, encoding) -> nodeMutable.execute(a, fromIndex, length, encoding));
    }
}
