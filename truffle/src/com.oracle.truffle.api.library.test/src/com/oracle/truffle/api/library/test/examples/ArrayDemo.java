/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.test.examples.ArrayDemoFactory.SumVectorNodeGen;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;

/**
 * Shows:
 * <ul>
 * <li>Simple example with two type representations
 * <li>How to call active libraries from nodes.
 * </ul>
 */
@SuppressWarnings("unused")
public class ArrayDemo {

    @GenerateLibrary
    public abstract static class VectorLibrary extends Library {

        public boolean isVector(Object receiver) {
            return false;
        }

        public abstract int readVector(Object receiver, int index);

        public abstract int getVectorLength(Object receiver);
    }

    @ExportLibrary(VectorLibrary.class)
    public static final class IntSequence {

        private final int start;
        private final int stride;
        private final int length;

        IntSequence(int start, int stride, int length) {
            this.start = start;
            this.stride = stride;
            this.length = length;
        }

        @ExportMessage
        int readVector(int index) {
            if (index < 0 && index >= length) {
                CompilerDirectives.transferToInterpreter();
                throw new ArrayIndexOutOfBoundsException();
            }
            return start + (index * stride);
        }

        @ExportMessage
        int getVectorLength() {
            return length;
        }

    }

    @ExportLibrary(VectorLibrary.class)
    public static final class IntVector {

        private final int[] data;

        IntVector(int[] data) {
            this.data = data;
        }

        @ExportMessage
        int readVector(int index) {
            return data[index];
        }

        @ExportMessage
        int getVectorLength() {
            return data.length;
        }

    }

    public abstract static class SumVector extends Node {

        abstract int execute(Object vector);

        @Specialization(guards = "lib.isVector(vector)", limit = "2")
        int doSum(Object vector, @CachedLibrary("vector") VectorLibrary lib) {
            int vectorLength = lib.getVectorLength(vector);
            int sum = 0;
            for (int i = 0; i < vectorLength; i++) {
                sum = lib.readVector(vector, i);
            }
            return sum;
        }
    }

    public static void main(String[] args) {
        SumVector node = SumVectorNodeGen.create();
        System.out.println(node.execute(new IntSequence(1, 2, 5)));
        System.out.println(node.execute(new IntVector(new int[]{1, 2, 3})));
    }

}
