package org.graalvm.collections.test;


import org.graalvm.collections.PrefixTree;
import org.junit.Assert;
import org.junit.Test;
import org.graalvm.collections.LockFreePrefixTree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Consumer;


public class LockFreePrefixTreeTest {

    @Test
    public void smallAlphabet() {
        LockFreePrefixTree tree = new LockFreePrefixTree();

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
        LockFreePrefixTree tree = new LockFreePrefixTree();
        for (long i = 1L; i < 128L; i++) {
            LockFreePrefixTree.Node first = tree.root().at(i);
            for (long j = 1L; j < 64L; j++) {
                LockFreePrefixTree.Node second = first.at(j);
                second.setValue(i * j);
            }
        }
        for (long i = 1L; i < 128L; i++) {
            LockFreePrefixTree.Node first = tree.root().at(i);
            for (long j = 1L; j < 64L; j++) {
                LockFreePrefixTree.Node second = first.at(j);
                Assert.assertEquals(i * j, second.value());
            }
        }
    }

    private void inParallel(int parallelism, Consumer<Integer> body) {
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


    @Test public void flatMultithreaded(){
        final LockFreePrefixTree tree = new LockFreePrefixTree();
        final int parallelism = 8;
        final int size = 10;
        inParallel(parallelism,threadIndex -> {
            for(int i = 1;  i < size;++i){
                tree.root().at(i).incValue();
            }
//            System.out.println(Thread.currentThread().getName()  + " " + LockFreePrefixTree.Node.count.get());
        });


//        for(int i = 1; i < size;++i) {
//
//            System.out.println(tree.root().at(i));
//        }


//        LockFreePrefixTree.Node.queue.stream().forEach(s -> System.out.println(s));

        for(int i = 1; i < size;++i){

            Assert.assertEquals(parallelism, tree.root().at(i).get());

        }
    }

    @Test
    public void largeMultithreaded() {
        final LockFreePrefixTree tree = new LockFreePrefixTree();

        final int parallelism = 8;
        inParallel(parallelism, threadIndex -> {
            for (long i = 1L; i < 2048L; i++) {
                LockFreePrefixTree.Node first = tree.root().at(threadIndex * 2048L + i);
                for (long j = 1L; j < 2048L; j++) {
                    LockFreePrefixTree.Node second = first.at(j);
                    second.setValue(i * j);
                }
            }
        });

        for (int t = 0; t < parallelism; t++) {
            for (long i = 1L; i < 2048L; i++) {
                LockFreePrefixTree.Node first = tree.root().at(t * 2048L + i);
                for (long j = 1L; j < 2048L; j++) {
                    LockFreePrefixTree.Node second = first.at(j);
                    Assert.assertEquals(i * j, second.value());
                }
            }
        }
    }

    private void verifyValue(LockFreePrefixTree.Node node, int depth, int parallelism) {
        if (depth == 0) {
            Assert.assertEquals(parallelism, node.value());
        } else {
            for (long i = 1L; i < 14L; i++) {
                final LockFreePrefixTree.Node child = node.at(i);
                verifyValue(child, depth - 1, parallelism);
            }
        }
    }


    @Test
    public void deepMultiThreaded()
    {
        final LockFreePrefixTree tree = new LockFreePrefixTree();
        final int depth = 6;
        final int parallelism = 8;
        final long multiplier = 14L;
        inParallel(parallelism, new Consumer<Integer>() {
            @Override
            public void accept(Integer threadIndex) {
                insert(tree.root(), depth);
            }

            private void insert(LockFreePrefixTree.Node node, int depth) {
                if (depth == 0) {
                    node.incValue();
                } else {
                    for (long i = 1L; i < multiplier; i++) {
                        final LockFreePrefixTree.Node child = node.at(i);
                        insert(child, depth - 1);
                    }
                }
            }
        });

        verifyValue(tree.root(), depth, parallelism);
    }


//    private <T> boolean  checkListUnique(List<T> list) {
//        Set<T> set = new HashSet<>(list);
//        return set.size() == list.size();
//    }
//
//    private List<Long> getAllKeys(LockFreePrefixTree tree){
//        List<Long> keys = new ArrayList<>();
//        //TODO: general case
//        AtomicReferenceArray<Node> children =  (AtomicReferenceArray<?>) tree.root().readChildren();
//
//        for (long)
//    }

//    @Test
//    public void checkKeyUniqueness(){
//        final LockFreePrefixTree tree = new LockFreePrefixTree();
//        int parallelism = 8;
//        int numKeysPerThread = 3;
//        inParallel(parallelism, new Consumer<Integer>() {
//            @Override
//            public void accept(Integer threadIndex) {
//                for (int i = threadIndex * numKeysPerThread; i < (threadIndex + 1) * numKeysPerThread ; i++) {
//                    tree.root().at(i).setValue(2);
//                }
//
//            }
//        });
//        for (Object children:) {
//
//        }
//    }


    @Test
    public void manyMultiThreaded() {
            final LockFreePrefixTree tree = new LockFreePrefixTree();

            int parallelism = 8;
            int multiplier = 1;
            long batch = 100L;
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
        }

}


