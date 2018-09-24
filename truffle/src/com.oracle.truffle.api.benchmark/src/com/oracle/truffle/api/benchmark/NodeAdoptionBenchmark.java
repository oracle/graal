/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import com.oracle.truffle.api.nodes.Node;

@State(Scope.Thread)
public class NodeAdoptionBenchmark extends TruffleBenchmark {

    @Benchmark
    public Object shallowSmallBlocks() {
        Node block = createBlock(0, 5);
        block.adoptChildren();
        return block;
    }

    @Benchmark
    public Object shallowBigBlocks() {
        Node block = createBlock(0, 30);
        block.adoptChildren();
        return block;
    }

    @Benchmark
    public Object deepSmallBlocks() {
        Node block = createBlock(5, 5);
        block.adoptChildren();
        return block;
    }

    @Benchmark
    public Object deepBigBlocks() {
        Node block = createBlock(5, 30);
        block.adoptChildren();
        return block;
    }

    @Benchmark
    public Object insertionBig() {
        Binary binary = new Binary();
        binary.adoptChildren();
        binary.setChild0(createBlock(4, 10));
        binary.setChild1(createBlock(4, 10));
        return binary;
    }

    @Benchmark
    public Object insertionSmall() {
        Binary binary = new Binary();
        binary.adoptChildren();
        binary.setChild0(createBlock(0, 10));
        binary.setChild1(createBlock(0, 10));
        return binary;
    }

    @Benchmark
    public Object replaceBig() {
        Binary binary = new Binary(new Expression(), new Expression());
        binary.adoptChildren();
        binary.child0.replace(createBlock(3, 10));
        binary.child1.replace(createBlock(3, 10));
        return binary;
    }

    @Benchmark
    public Object replaceSmall() {
        Binary binary = new Binary(createBlock(0, 10), createBlock(0, 10));
        binary.adoptChildren();
        binary.child0.replace(createBlock(0, 10));
        binary.child1.replace(createBlock(0, 10));
        return binary;
    }

    @Benchmark
    public Object replaceALot() {
        Binary binary = new Binary();
        Expression block1 = createBlock(0, 10);
        Expression block2 = createBlock(0, 10);
        binary.setChild0(block1);
        binary.adoptChildren();
        for (int i = 0; i < 1000; i++) {
            binary.child0.replace(i % 2 == 0 ? block2 : block1);
        }
        return binary;
    }

    private static class Block extends Expression {

        @Children final Expression[] children;

        Block(Expression[] children) {
            this.children = children;
        }

    }

    private static class Expression extends Node {

    }

    private static class Binary extends Expression {

        @Child private Expression child0;
        @Child private Expression child1;

        Binary() {
        }

        Binary(Expression child0, Expression child1) {
            this.child0 = child0;
            this.child1 = child1;
        }

        public void setChild0(Expression child0) {
            this.child0 = insert(child0);
        }

        public void setChild1(Expression child1) {
            this.child1 = insert(child1);
        }

    }

    private static class Unary extends Expression {

        @Child private Expression child0;

        Unary(Expression child0) {
            this.child0 = child0;
        }

    }

    /*
     * This method aims to produce ASTs that mimic guest language application asts.
     */
    private static Expression createBlock(int depth, int blockSize) {
        Expression[] expressions = new Expression[blockSize];
        for (int i = 0; i < expressions.length; i++) {

            int statement = depth <= 0 ? i % 3 : i % 4;
            Expression e;
            switch (statement) {
                case 0:
                    e = new Binary(new Unary(new Expression()), new Expression());
                    break;
                case 1:
                    e = new Unary(new Expression());
                    break;
                case 2:
                    e = new Binary(new Binary(new Unary(new Binary(new Expression(), new Expression())), new Expression()), new Expression());
                    break;
                case 3:
                    e = createBlock(depth - 1, blockSize);
                    break;
                default:
                    throw new AssertionError();
            }
            expressions[i] = e;
        }

        return new Block(expressions);
    }

}
