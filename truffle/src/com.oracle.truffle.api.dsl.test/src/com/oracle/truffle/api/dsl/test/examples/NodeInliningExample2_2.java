/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test.examples;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.DisableStateBitWidthModfication;
import com.oracle.truffle.api.dsl.test.ObjectSizeEstimate;
import com.oracle.truffle.api.dsl.test.examples.NodeInliningExample2_2Factory.SumArrayNodeGen;
import com.oracle.truffle.api.nodes.Node;

@DisableStateBitWidthModfication
public class NodeInliningExample2_2 {

    abstract static class AbstractArray {
    }

    static final class RangeArray extends AbstractArray {

        final int start;
        final int end;

        RangeArray(int start, int end) {
            this.start = start;
            this.end = end;
        }

        int[] getStore() {
            int[] array = new int[end - start];
            for (int i = 0; i < array.length; i++) {
                array[i] = start + i;
            }
            return array;
        }

    }

    static final class MaterializedArray extends AbstractArray {
        private final int[] array;

        MaterializedArray(int size) {
            this.array = new int[size];
        }

        int[] getStore() {
            return array;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetStoreNode extends Node {

        abstract int[] execute(Node node, Object v0);

        @Specialization
        int[] doRange(RangeArray array) {
            return array.getStore();
        }

        @Specialization
        int[] doMaterialized(MaterializedArray array) {
            return array.getStore();
        }

    }

    @SuppressWarnings("truffle-inlining")
    @DisableStateBitWidthModfication
    public abstract static class SumArrayNode extends Node {

        abstract int execute(Object v0);

        @Specialization(guards = {"cachedClass != null", "cachedClass == array.getClass()"}, limit = "2")
        static int doCached(Object array,
                        @Bind Node node,
                        @Cached("getCachedClass(array)") Class<?> cachedClass,
                        @Cached GetStoreNode getStore) {
            Object castStore = cachedClass.cast(array);
            int[] store = getStore.execute(node, castStore);
            int sum = 0;
            for (int element : store) {
                sum += element;
                TruffleSafepoint.poll(node);
            }
            return sum;
        }

        static Class<?> getCachedClass(Object array) {
            if (array instanceof AbstractArray) {
                return array.getClass();
            }
            return null;
        }
    }

    @Test
    public void test() {
        RangeArray array1 = new RangeArray(0, 42);
        MaterializedArray array2 = new MaterializedArray(42);
        Arrays.fill(array2.array, 1);

        SumArrayNode sum = SumArrayNodeGen.create();
        assertEquals(861, sum.execute(array1));
        assertEquals(42, sum.execute(array2));

        // 80 bytes down from 120 bytes with partial object inlining
        assertEquals(80, ObjectSizeEstimate.forObject(sum).getCompressedTotalBytes());
    }
}
