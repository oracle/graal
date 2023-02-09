/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;
import org.graalvm.collections.LockFreePrefixTree;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public class LockFreePrefixTreeTest {

    private static final int RETRY_DELAY_MILLIS = 10;

    @Test
    public void smallAlphabetHeap() {
        LockFreePrefixTree.HeapAllocator a = new LockFreePrefixTree.HeapAllocator();
        smallAlphabet(a);
    }

    @Test
    public void smallAlphabetPool() {
        LockFreePrefixTree.ObjectPoolingAllocator a = new LockFreePrefixTree.ObjectPoolingAllocator();
        smallAlphabet(a);
    }

    private static void smallAlphabet(LockFreePrefixTree.Allocator a) {
        try {
            LockFreePrefixTree tree = new LockFreePrefixTree(a);

            tree.root().at(a, 2L).at(a, 12L).at(a, 18L).setValue(42);
            tree.root().at(a, 2L).at(a, 12L).at(a, 19L).setValue(43);
            tree.root().at(a, 2L).at(a, 12L).at(a, 20L).setValue(44);

            Assert.assertEquals(42, tree.root().at(a, 2L).at(a, 12L).at(a, 18L).value());
            Assert.assertEquals(43, tree.root().at(a, 2L).at(a, 12L).at(a, 19L).value());
            Assert.assertEquals(44, tree.root().at(a, 2L).at(a, 12L).at(a, 20L).value());

            tree.root().at(a, 3L).at(a, 19L).setValue(21);

            Assert.assertEquals(42, tree.root().at(a, 2L).at(a, 12L).at(a, 18L).value());
            Assert.assertEquals(21, tree.root().at(a, 3L).at(a, 19L).value());

            tree.root().at(a, 2L).at(a, 6L).at(a, 11L).setValue(123);

            Assert.assertEquals(123, tree.root().at(a, 2L).at(a, 6L).at(a, 11L).value());

            tree.root().at(a, 3L).at(a, 19L).at(a, 11L).incValue();
            tree.root().at(a, 3L).at(a, 19L).at(a, 11L).incValue();

            Assert.assertEquals(2, tree.root().at(a, 3L).at(a, 19L).at(a, 11L).value());

            for (long i = 1L; i < 6L; i++) {
                tree.root().at(a, 1L).at(a, 2L).at(a, i).setValue(i * 10);
            }
            for (long i = 1L; i < 6L; i++) {
                Assert.assertEquals(i * 10, tree.root().at(a, 1L).at(a, 2L).at(a, i).value());
            }
        } finally {
            a.shutdown();
        }
    }

    @Test
    public void largeAlphabetHeap() {
        LockFreePrefixTree.HeapAllocator a = new LockFreePrefixTree.HeapAllocator();
        LockFreePrefixTree tree = new LockFreePrefixTree(a);
        try {
            largeAlphabet(tree, a);
        } finally {
            a.shutdown();
        }
    }

    @Test
    public void largeAlphabetPool() throws InterruptedException {
        LockFreePrefixTree.ObjectPoolingAllocator a = new LockFreePrefixTree.ObjectPoolingAllocator();
        LockFreePrefixTree tree = new LockFreePrefixTree(a);
        try {
            while (!largeAlphabet(tree, a)) {
                delay(a);
            }
        } finally {
            a.shutdown();
        }
    }

    private static void delay(Object a) throws InterruptedException {
        synchronized (a) {
            a.wait(RETRY_DELAY_MILLIS);
        }
    }

    private static boolean largeAlphabet(LockFreePrefixTree tree, LockFreePrefixTree.Allocator a) {
        for (long i = 1L; i < 128L; i++) {
            LockFreePrefixTree.Node first = tree.root().at(a, i);
            if (first == null) {
                return false;
            }
            for (long j = 1L; j < 64L; j++) {
                LockFreePrefixTree.Node second = first.at(a, j);
                if (second == null) {
                    return false;
                }
                second.setValue(i * j);
            }
        }
        for (long i = 1L; i < 128L; i++) {
            LockFreePrefixTree.Node first = tree.root().at(a, i);
            if (first == null) {
                return false;
            }
            for (long j = 1L; j < 64L; j++) {
                LockFreePrefixTree.Node second = first.at(a, j);
                if (second == null) {
                    return false;
                }
                Assert.assertEquals(i * j, second.value());
            }
        }
        return true;
    }

    private static boolean inParallel(int parallelism, Function<Integer, Boolean> body) {
        Thread[] threads = new Thread[parallelism];
        AtomicBoolean successful = new AtomicBoolean(true);
        for (int t = 0; t < parallelism; t++) {
            final int threadIndex = t;
            threads[t] = new Thread() {
                @Override
                public void run() {
                    boolean successfullyCompleted = body.apply(threadIndex);
                    if (!successfullyCompleted) {
                        successful.set(false);
                    }
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
        return successful.get();
    }

    @Test
    public void hashFlatMultithreadedHeap() {
        final LockFreePrefixTree.HeapAllocator a = new LockFreePrefixTree.HeapAllocator();
        final LockFreePrefixTree tree = new LockFreePrefixTree(a);
        try {
            hashFlatMultiThreaded(tree, a);
        } finally {
            a.shutdown();
        }
    }

    @Test
    public void hashFlatMultithreadedPool() throws InterruptedException {
        final LockFreePrefixTree.ObjectPoolingAllocator a = new LockFreePrefixTree.ObjectPoolingAllocator();
        final LockFreePrefixTree tree = new LockFreePrefixTree(a);
        try {
            while (!hashFlatMultiThreaded(tree, a)) {
                delay(a);
            }
        } finally {
            a.shutdown();
        }
    }

    private static boolean hashFlatMultiThreaded(LockFreePrefixTree tree, LockFreePrefixTree.Allocator a) {
        final int parallelism = 10;
        final int size = 10000;
        boolean built = inParallel(parallelism, threadIndex -> {
            for (int i = 1; i < size; ++i) {
                LockFreePrefixTree.Node node = tree.root().at(a, i);
                if (node == null) {
                    return false;
                }
                node.bitwiseOrValue(1L << threadIndex);
            }
            return true;
        });
        if (!built) {
            return false;
        }
        for (int i = 1; i < size; ++i) {
            Assert.assertEquals((1L << parallelism) - 1, tree.root().at(a, i).get());
        }
        return true;
    }

    @Test
    public void linearFlatMultithreadedHeap() {
        final LockFreePrefixTree.HeapAllocator a = new LockFreePrefixTree.HeapAllocator();
        final LockFreePrefixTree tree = new LockFreePrefixTree(a);
        linearFlatMultithreaded(tree, a);
    }

    @Test
    public void linearFlatMultithreadedPool() throws InterruptedException {
        final LockFreePrefixTree.ObjectPoolingAllocator a = new LockFreePrefixTree.ObjectPoolingAllocator();
        final LockFreePrefixTree tree = new LockFreePrefixTree(a);
        try {
            while (!linearFlatMultithreaded(tree, a)) {
                delay(a);
            }
        } finally {
            a.shutdown();
        }
    }

    private static boolean linearFlatMultithreaded(LockFreePrefixTree tree, LockFreePrefixTree.Allocator a) {
        final int parallelism = 10;
        final int size = 7;
        boolean built = inParallel(parallelism, threadIndex -> {
            for (int i = 1; i < size; ++i) {
                LockFreePrefixTree.Node node = tree.root().at(a, i);
                if (node == null) {
                    return false;
                }
                node.bitwiseOrValue(1L << threadIndex);
            }
            return true;
        });
        if (!built) {
            return false;
        }
        for (int i = 1; i < size; ++i) {
            Assert.assertEquals((1L << parallelism) - 1, tree.root().at(a, i).get());
        }
        return true;
    }

    @Test
    public void largeMultiThreadedHeap() {
        final LockFreePrefixTree.HeapAllocator a = new LockFreePrefixTree.HeapAllocator();
        final LockFreePrefixTree tree = new LockFreePrefixTree(a);
        largeMultiThreaded(a, tree);
    }

    @Test
    public void largeMultiThreadedPool() throws InterruptedException {
        final LockFreePrefixTree.ObjectPoolingAllocator a = new LockFreePrefixTree.ObjectPoolingAllocator();
        final LockFreePrefixTree tree = new LockFreePrefixTree(a);
        try {
            while (!largeMultiThreaded(a, tree)) {
                delay(a);
            }
        } finally {
            a.shutdown();
        }
    }

    private static boolean largeMultiThreaded(LockFreePrefixTree.Allocator a, LockFreePrefixTree tree) {
        final int parallelism = 8;
        boolean built = inParallel(parallelism, threadIndex -> {
            for (long i = 1L; i < 2048L; i++) {
                LockFreePrefixTree.Node first = tree.root().at(a, threadIndex * 2048L + i);
                if (first == null) {
                    return false;
                }
                for (long j = 1L; j < 2048L; j++) {
                    LockFreePrefixTree.Node second = first.at(a, j);
                    if (second == null) {
                        return false;
                    }
                    second.setValue(i * j);
                }
            }
            return true;
        });
        if (!built) {
            return false;
        }
        for (int t = 0; t < parallelism; t++) {
            for (long i = 1L; i < 2048L; i++) {
                LockFreePrefixTree.Node first = tree.root().at(a, t * 2048L + i);
                for (long j = 1L; j < 2048L; j++) {
                    LockFreePrefixTree.Node second = first.at(a, j);
                    Assert.assertEquals(i * j, second.value());
                }
            }
        }
        return true;
    }

    @Test
    public void deepHashMultiThreadedHeap() {
        final LockFreePrefixTree.HeapAllocator a = new LockFreePrefixTree.HeapAllocator();
        final LockFreePrefixTree tree = new LockFreePrefixTree(a);
        deepHashMultiThreaded(tree, a);
    }

    @Test
    public void deepHashMultiThreadedPool() throws InterruptedException {
        final LockFreePrefixTree.ObjectPoolingAllocator a = new LockFreePrefixTree.ObjectPoolingAllocator();
        final LockFreePrefixTree tree = new LockFreePrefixTree(a);
        try {
            while (!deepHashMultiThreaded(tree, a)) {
                delay(a);
            }
        } finally {
            a.shutdown();
        }
    }

    private void verifyValue(LockFreePrefixTree.Allocator a, LockFreePrefixTree.Node node, int depth, int parallelism) {
        if (depth == 0) {
            Assert.assertEquals((1L << parallelism) - 1, node.value());
        } else {
            for (long i = 1L; i < 14L; i++) {
                final LockFreePrefixTree.Node child = node.at(a, i);
                verifyValue(a, child, depth - 1, parallelism);
            }
        }
    }

    private boolean deepHashMultiThreaded(LockFreePrefixTree tree, LockFreePrefixTree.Allocator a) {
        final int depth = 6;
        final int parallelism = 8;
        final long multiplier = 14L;
        boolean built = inParallel(parallelism, new Function<>() {
            @Override
            public Boolean apply(Integer threadIndex) {
                return insert(threadIndex, tree.root(), depth);
            }

            private boolean insert(int threadIndex, LockFreePrefixTree.Node node, int currentDepth) {
                if (currentDepth == 0) {
                    node.bitwiseOrValue(1L << threadIndex);
                    return true;
                } else {
                    for (long i = 1L; i < multiplier; i++) {
                        final LockFreePrefixTree.Node child = node.at(a, i);
                        if (child == null) {
                            return false;
                        }
                        if (!insert(threadIndex, child, currentDepth - 1)) {
                            return false;
                        }
                    }
                    return true;
                }
            }
        });
        if (!built) {
            return false;
        }
        verifyValue(a, tree.root(), depth, parallelism);
        return true;
    }

    @Test
    public void deepLinearMultiThreadedHeap() {
        final LockFreePrefixTree.HeapAllocator a = new LockFreePrefixTree.HeapAllocator();
        final LockFreePrefixTree tree = new LockFreePrefixTree(a);
        deepLinearMultiThreaded(tree, a);
    }

    @Test
    public void deepLinearMultiThreadedPool() throws InterruptedException {
        final LockFreePrefixTree.ObjectPoolingAllocator a = new LockFreePrefixTree.ObjectPoolingAllocator();
        final LockFreePrefixTree tree = new LockFreePrefixTree(a);
        try {
            while (!deepLinearMultiThreaded(tree, a)) {
                delay(a);
            }
        } finally {
            a.shutdown();
        }
    }

    private boolean deepLinearMultiThreaded(LockFreePrefixTree tree, LockFreePrefixTree.Allocator a) {
        final int depth = 10;
        final int parallelism = 8;
        final int numChildren = 4;
        boolean built = inParallel(parallelism, new Function<>() {
            @Override
            public Boolean apply(Integer threadIndex) {
                return fillDeepTree(threadIndex, a, tree.root(), depth, numChildren);
            }
        });
        if (!built) {
            return false;
        }
        checkDeepTree(a, tree.root(), depth, numChildren, parallelism);
        return true;
    }

    private boolean fillDeepTree(Integer threadIndex, LockFreePrefixTree.Allocator a, LockFreePrefixTree.Node node, int depth, int numChildren) {
        if (depth == 0) {
            node.bitwiseOrValue(1L << threadIndex);
            return true;
        } else {
            for (int i = 1; i <= numChildren; i++) {
                LockFreePrefixTree.Node child = node.at(a, i);
                if (child == null) {
                    return false;
                }
                if (!fillDeepTree(threadIndex, a, child, depth - 1, numChildren)) {
                    return false;
                }
            }
            return true;
        }
    }

    private void checkDeepTree(LockFreePrefixTree.Allocator a, LockFreePrefixTree.Node node, int depth, int numChildren, int parallelism) {
        if (depth == 0) {
            Assert.assertEquals((1L << parallelism) - 1, node.value());
        } else {
            for (long i = 1L; i <= numChildren; i++) {
                checkDeepTree(a, node.at(a, i), depth - 1, numChildren, parallelism);
            }
        }
    }

    @Test
    public void deepHashMultiThreadedV2() {
        LockFreePrefixTree.HeapAllocator a = new LockFreePrefixTree.HeapAllocator();
        final LockFreePrefixTree tree = new LockFreePrefixTree(a);
        final int depth = 6;
        final int parallelism = 8;
        final int numChildren = 10;
        inParallel(parallelism, new Function<>() {
            @Override
            public Boolean apply(Integer threadIndex) {
                fillDeepTree(threadIndex, a, tree.root(), depth, numChildren);
                return true;
            }
        });
        checkDeepTree(a, tree.root(), depth, numChildren, parallelism);
    }

    @Test
    public void manyMultiThreaded() {
        final LockFreePrefixTree.HeapAllocator a = new LockFreePrefixTree.HeapAllocator();
        final LockFreePrefixTree tree = new LockFreePrefixTree(a);
        int parallelism = 8;
        int multiplier = 1024;
        long batch = 2000L;
        inParallel(parallelism, new Function<>() {
            @Override
            public Boolean apply(Integer threadIndex) {
                if (threadIndex % 2 == 0) {
                    // Mostly read.
                    for (int j = 0; j < multiplier; j++) {
                        for (long i = 1L; i < batch; i++) {
                            tree.root().at(a, i).incValue();
                        }
                    }
                } else {
                    // Mostly add new nodes.
                    for (long i = batch + 1L; i < multiplier * batch; i++) {
                        tree.root().at(a, threadIndex * multiplier * batch + i).incValue();
                    }
                }
                return true;
            }
        });
        for (long i = 1L; i < batch; i++) {
            Assert.assertEquals(parallelism * multiplier / 2, tree.root().at(a, i).value());
        }
    }
}
