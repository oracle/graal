/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.library.test.examples;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.api.library.test.examples.ArrayStrategy1.ArgumentNode;
import com.oracle.truffle.api.library.test.examples.ArrayStrategy1.ExampleRootNode;
import com.oracle.truffle.api.library.test.examples.ArrayStrategy1.ExpressionNode;
import com.oracle.truffle.api.library.test.examples.ArrayStrategy3Factory.ArrayReadNodeGen;

@SuppressWarnings({"static-method", "unused"})
public class ArrayStrategy3 {

    @GenerateLibrary
    public abstract static class ArrayLibrary extends Library {

        public boolean isArray(Object receiver) {
            return false;
        }

        public abstract int read(Object receiver, int index);

        public static LibraryFactory<ArrayLibrary> getFactory() {
            return FACTORY;
        }

        public static ArrayLibrary getUncached() {
            return FACTORY.getUncached();
        }

        private static final LibraryFactory<ArrayLibrary> FACTORY = LibraryFactory.resolve(ArrayLibrary.class);
    }

    @ExportLibrary(ArrayLibrary.class)
    static final class BufferArray {

        private int length;
        private int[] buffer;

        BufferArray(int length) {
            this.length = length;
            this.buffer = new int[length];
        }

        @ExportMessage
        boolean isArray() {
            return true;
        }

        @ExportMessage
        int read(int index) {
            return buffer[index];
        }
    }

    @ExportLibrary(ArrayLibrary.class)
    static final class SequenceArray {
        private final int start;
        private final int stride;
        private final int length;

        SequenceArray(int start, int stride, int length) {
            this.start = start;
            this.stride = stride;
            this.length = length;
        }

        @ExportMessage
        boolean isArray() {
            return true;
        }

        @ExportMessage
        public int read(int index) {
            return start + (stride * index);
        }
    }

    @NodeChild
    @NodeChild
    abstract static class ArrayReadNode extends ExpressionNode {
        @Specialization(guards = "arrays.isArray(array)", limit = "2")
        int doDefault(Object array, int index,
                        @CachedLibrary("array") ArrayLibrary arrays) {
            return arrays.read(array, index);
        }
    }

    @Test
    public void runExample() {
        ArrayReadNode read = ArrayReadNodeGen.create(new ArgumentNode(0), new ArgumentNode(1));
        CallTarget target = Truffle.getRuntime().createCallTarget(new ExampleRootNode(read));

        assertEquals(3, target.call(new SequenceArray(1, 2, 3), 1));
        assertEquals(0, target.call(new BufferArray(2), 1));
    }

    @Test
    public void uncachedExample() {
        Object noArray = new Object();

        ArrayLibrary arrays = ArrayLibrary.getUncached();

        assertFalse(arrays.isArray(noArray));

        Object sequence = new SequenceArray(1, 2, 3);
        assertTrue(arrays.isArray(sequence));
        assertEquals(3, arrays.read(sequence, 1));

        Object buffer = new BufferArray(2);
        assertTrue(arrays.isArray(buffer));
        assertEquals(0, arrays.read(buffer, 1));
    }

}
