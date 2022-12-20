/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.collections.test;

import org.graalvm.collections.SeqLockPrefixTree;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.function.Consumer;

public class SeqLockPrefixTreeTest {
    @Test
    public void smallAlphabet() {
        SeqLockPrefixTree tree = new SeqLockPrefixTree();
        tree.root().at(2L).at(12L).at(18L).setValue(42);
        tree.root().at(2L).at(12L).at(19L).setValue(43);
        tree.root().at(2L).at(12L).at(20L).setValue(44);

        Assert.assertEquals(42, tree.root().at(2L).at(12L).at(18L).value());
        Assert.assertEquals(43, tree.root().at(2L).at(12L).at(19L).value());
        Assert.assertEquals(44, tree.root().at(2L).at(12L).at(20L).value());

        tree.root().at(3L).at(19L).setValue(21);

        Assert.assertEquals(42, tree.root().at(2L).at(12L).at(18L).value());
        Assert.assertEquals(21, tree.root().at(3L).at(19L).value());

        tree.root().at(2L).at(6L).at(11L).setValue(123);

        Assert.assertEquals(123, tree.root().at(2L).at(6L).at(11L).value());

        tree.root().at(3L).at(19L).at(11L).incValue();
        tree.root().at(3L).at(19L).at(11L).incValue();

        Assert.assertEquals(2, tree.root().at(3L).at(19L).at(11L).value());

        for (long i = 1L; i < 6L; i++) {
            tree.root().at(1L).at(2L).at(i).setValue(i * 10);
        }
        for (long i = 1L; i < 6L; i++) {
            Assert.assertEquals(i * 10, tree.root().at(1L).at(2L).at(i).value());
        }
    }

    @Test
    public void largeAlphabet() {
        SeqLockPrefixTree tree = new SeqLockPrefixTree();
        for (long i = 1L; i < 128L; i++) {
            SeqLockPrefixTree.Node first = tree.root().at(i);
            for (long j = 1L; j < 64L; j++) {
                SeqLockPrefixTree.Node second = first.at(j);
                second.setValue(i * j);
            }
        }
        for (long i = 1L; i < 128L; i++) {
            SeqLockPrefixTree.Node first = tree.root().at(i);
            for (long j = 1L; j < 64L; j++) {
                SeqLockPrefixTree.Node second = first.at(j);
                Assert.assertEquals(i * j, second.value());
            }
        }
    }

    private static void inParallel(int parallelism, Consumer<Integer> body) {
        Thread[] threads = new Thread[parallelism];
        for (int t = 0; t < parallelism; t++) {
            final int threadIndex = t;
            threads[t] = new Thread() {
                @Override
                public void run() {
                    body.accept(threadIndex);
                }
            };
        }
        for (int t = 0; t < parallelism; t++) {
            threads[t].start();
        }
        for (int t = 0; t < parallelism; t++) {
            try {
                threads[t].join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Ignore("GR-41705")
    @Test
    public void largeMultithreaded() {
        final SeqLockPrefixTree tree = new SeqLockPrefixTree();

        final int parallelism = 8;
        inParallel(parallelism, threadIndex -> {
            for (long i = 1L; i < 2048L; i++) {
                SeqLockPrefixTree.Node first = tree.root().at(threadIndex * 2048L + i);
                for (long j = 1L; j < 2048L; j++) {
                    SeqLockPrefixTree.Node second = first.at(j);
                    second.setValue(i * j);
                }
            }
        });

        for (int t = 0; t < parallelism; t++) {
            for (long i = 1L; i < 2048L; i++) {
                SeqLockPrefixTree.Node first = tree.root().at(t * 2048L + i);
                for (long j = 1L; j < 2048L; j++) {
                    SeqLockPrefixTree.Node second = first.at(j);
                    Assert.assertEquals(i * j, second.value());
                }
            }
        }
    }

    private void verifyValue(SeqLockPrefixTree.Node node, int depth, int parallelism) {
        if (depth == 0) {
            Assert.assertEquals(parallelism, node.value());
        } else {
            for (long i = 1L; i < 14L; i++) {
                final SeqLockPrefixTree.Node child = node.at(i);
                verifyValue(child, depth - 1, parallelism);
            }
        }
    }

    @Test
    public void deepMultiThreaded() {
        final SeqLockPrefixTree tree = new SeqLockPrefixTree();
        final int depth = 6;

        final int parallelism = 8;
        inParallel(parallelism, new Consumer<Integer>() {
            @Override
            public void accept(Integer threadIndex) {
                insert(tree.root(), depth);
            }

            private void insert(SeqLockPrefixTree.Node node, int currentDepth) {
                if (currentDepth == 0) {
                    node.incValue();
                } else {
                    for (long i = 1L; i < 14L; i++) {
                        final SeqLockPrefixTree.Node child = node.at(i);
                        insert(child, currentDepth - 1);
                    }
                }
            }
        });
        verifyValue(tree.root(), depth, parallelism);
        Assert.assertEquals("The root node has 13 children.", 26, tree.root().seqlockValue());
    }

    @Test
    public void manyMultiThreaded() {
        final SeqLockPrefixTree tree = new SeqLockPrefixTree();

        int parallelism = 8;
        int multiplier = 1024;
        long batch = 2000L;
        inParallel(parallelism, new Consumer<Integer>() {
            @Override
            public void accept(Integer threadIndex) {
                if (threadIndex % 2 == 0) {
                    // Mostly read.
                    for (int j = 0; j < multiplier; j++) {
                        for (long i = 1L; i < batch; i++) {
                            tree.root().at(i).incValue();
                        }
                    }
                } else {
                    // Mostly add new nodes.
                    for (long i = batch + 1L; i < multiplier * batch; i++) {
                        tree.root().at(threadIndex * multiplier * batch + i).incValue();
                    }
                }
            }
        });
        for (long i = 1L; i < batch; i++) {
            Assert.assertEquals(parallelism * multiplier / 2, tree.root().at(i).value());
        }
        final long expectedSeqlock = 2 * ((batch - 1) + parallelism / 2 * (multiplier * batch - (batch + 1)));
        Assert.assertEquals(expectedSeqlock, tree.root().seqlockValue());
    }
}
