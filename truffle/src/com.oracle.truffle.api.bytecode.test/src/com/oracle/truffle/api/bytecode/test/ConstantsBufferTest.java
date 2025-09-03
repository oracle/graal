/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import java.util.stream.IntStream;

import org.junit.Test;

import com.oracle.truffle.api.bytecode.BytecodeSupport.ConstantsBuffer;

public class ConstantsBufferTest {

    @Test
    public void linearPathDeduplicatesBelowThreshold() {
        ConstantsBuffer b = new ConstantsBuffer();
        int a = b.add("A");
        int a2 = b.add(new String("A"));
        assertEquals(a, a2);
        assertEquals(1, b.materialize().length);
        b.clear();
    }

    @Test
    public void migrationHappensExactlyAtThreshold() {
        ConstantsBuffer b = new ConstantsBuffer();
        IntStream.range(0, 8).forEach(i -> b.add(i));
        int dup = b.add(3);
        assertEquals(3, dup);
        assertEquals(8, b.materialize().length);
        b.clear();
    }

    @Test
    public void mapDeduplicatesAndRehashes() {
        ConstantsBuffer b = new ConstantsBuffer();
        int n = 1_000;
        for (int i = 0; i < n; i++) {
            b.add(i);
        }
        for (int i = 0; i < n; i++) {
            assertEquals(i, b.add(i));
        }
        assertEquals(n, b.materialize().length);
        b.clear();
    }

    @Test
    public void addNullAllocatesIndependentSlots() {
        ConstantsBuffer b = new ConstantsBuffer();
        int i1 = b.addNull();
        int i2 = b.addNull();
        assertNotEquals(i1, i2);
        assertArrayEquals(new Object[]{null, null},
                        b.materialize());
        b.clear();
    }

    @Test
    public void materialiseResetsForReuse() {
        ConstantsBuffer b = new ConstantsBuffer();
        b.add("X");
        b.materialize();
        assertEquals(0, b.materialize().length);
        b.add("Y");
        assertEquals(1, b.materialize().length);
        b.clear();
    }

    @Test
    public void clearShrinksLargeBuffer() {
        ConstantsBuffer b = new ConstantsBuffer();
        IntStream.range(0, 600).forEach(b::add);
        b.materialize();
        // should trigger down size
        b.clear();
        b.add("Z");
        assertEquals(1, b.materialize().length);
    }

    @Test
    public void collisionStress() {
        ConstantsBuffer b = new ConstantsBuffer();
        for (int i = 0; i < 10_000; i++) {
            b.add(new Colliding(i));
        }
        assertEquals(10_000, b.materialize().length);
        b.clear();
    }

    @Test
    public void addRejectsNull() {
        ConstantsBuffer b = new ConstantsBuffer();
        assertThrows(NullPointerException.class, () -> b.add(null));
        assertEquals(0, b.materialize().length);
        b.clear();
    }

    @Test
    public void clearWithoutMaterialiseFails() {
        ConstantsBuffer b = new ConstantsBuffer();
        b.add("A");
        assertThrows(IllegalStateException.class, b::clear);
        assertEquals(1, b.materialize().length);
        b.clear();
    }

    @Test
    public void manyNulls1() {
        ConstantsBuffer b = new ConstantsBuffer();
        assertEquals(0, b.add(42));
        IntStream.range(0, 600).forEach(i -> b.addNull());
        assertEquals(0, b.add(42));

        assertEquals(601, b.materialize().length);
        b.clear();
    }

    @Test
    public void manyNulls2() {
        ConstantsBuffer b = new ConstantsBuffer();
        IntStream.range(0, 8).forEach(i -> b.add(i));
        IntStream.range(0, 600).forEach(i -> b.addNull());
        for (int i = 0; i < 8; i++) {
            assertEquals(i, b.add(i));
        }
        assertEquals(608, b.materialize().length);
        b.clear();
    }

    /** distinct objects that all share the same hashCode to stress collision handling. */
    private static final class Colliding {
        final int id;

        Colliding(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Colliding c && c.id == id;
        }

        @Override
        public int hashCode() {
            return 42;
        }
    }

}
