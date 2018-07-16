/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.stream.Collectors;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.collections.Pair;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@AddExports("com.oracle.truffle.truffle_api/com.oracle.truffle.api.frame")
public class FrameDescriptorThreadSafetyTest {

    // Some of the thread safety tests may be not terminating when
    // the FrameDescriptor is not threadsafe, therefore their execution time is limited
    // and the timeout error should be considered as a test failure.
    @Rule public TestRule timeout = new DisableOnDebug(Timeout.seconds(5));

    private static final int PARTIES_COUNT = 4;
    private static final int ITERATIONS = 50;

    @Test
    public void addFrameSlot() throws InterruptedException {
        final FrameDescriptor frameDescriptor = makeThreadSafe(new FrameDescriptor());

        List<List<Boolean>> results = content(PARTIES_COUNT, ITERATIONS,
                        (partyIndex, iteration) -> {
                            try {
                                frameDescriptor.addFrameSlot("v" + iteration);
                                return true;
                            } catch (IllegalArgumentException ignored) {
                                // also correct
                                return false;
                            }
                        });

        assertEquals(ITERATIONS, frameDescriptor.getSize());

        for (int i = 0; i < ITERATIONS; i++) {
            assertNotNull(frameDescriptor.findFrameSlot("v" + i));
            assertEquals(1, results.get(i).stream().filter((v) -> v).count());
            assertEquals(PARTIES_COUNT - 1, results.get(i).stream().filter((v) -> !v).count());
        }
    }

    @Test
    public void findOrAddFrameSlot() throws InterruptedException {
        final FrameDescriptor frameDescriptor = makeThreadSafe(new FrameDescriptor());

        List<List<FrameSlot>> results = content(PARTIES_COUNT, ITERATIONS,
                        (partyIndex, iteration) -> frameDescriptor.findOrAddFrameSlot("v" + iteration));

        assertEquals(ITERATIONS, frameDescriptor.getSize());

        for (int i = 0; i < ITERATIONS; i++) {
            final FrameSlot slot = frameDescriptor.findFrameSlot("v" + i);
            assertNotNull(slot);
            assertTrue(results.get(i).stream().allMatch(v -> v == slot));
        }
    }

    @Test
    public void removeFrameSlot() throws InterruptedException {
        final FrameDescriptor frameDescriptor = makeThreadSafe(new FrameDescriptor());
        for (int r = 0; r < ITERATIONS; r++) {
            frameDescriptor.addFrameSlot("v" + r);
        }

        List<List<Boolean>> results = content(PARTIES_COUNT, ITERATIONS,
                        (partyIndex, iteration) -> {
                            try {
                                frameDescriptor.removeFrameSlot("v" + iteration);
                                return true;
                            } catch (IllegalArgumentException ignored) {
                                // also correct
                                return false;
                            }
                        });

        assertEquals(ITERATIONS, frameDescriptor.getSize());
        assertTrue(frameDescriptor.getSlots().isEmpty());
        for (int i = 0; i < ITERATIONS; i++) {
            assertEquals(1, results.get(i).stream().filter(v -> v).count());
            assertEquals(PARTIES_COUNT - 1, results.get(i).stream().filter(v -> !v).count());
        }
    }

    @Test
    public void setFrameSlotKind() throws InterruptedException {
        final FrameDescriptor frameDescriptor = makeThreadSafe(new FrameDescriptor());
        final FrameSlot slot = frameDescriptor.addFrameSlot("v");

        List<List<Pair<Assumption, Boolean>>> results = content(PARTIES_COUNT, ITERATIONS,
                        () -> frameDescriptor.setFrameSlotKind(slot, FrameSlotKind.Boolean),
                        (partyIndex, iteration) -> {
                            frameDescriptor.setFrameSlotKind(slot, FrameSlotKind.Int);
                            Assumption version = frameDescriptor.getVersion();
                            return Pair.create(version, version.isValid());
                        });

        for (int i = 0; i < ITERATIONS; i++) {
            List<Pair<Assumption, Boolean>> versions = results.get(i).stream().distinct().collect(Collectors.toList());
            final Pair<Assumption, Boolean> pair0;
            switch (versions.size()) {
                case 1:
                    pair0 = versions.get(0);
                    assertTrue(pair0.getRight());
                    break;
                case 2:
                    pair0 = versions.get(0);
                    Pair<Assumption, Boolean> pair1 = versions.get(1);
                    assertTrue((pair0.getRight() && !pair1.getRight()) || (!pair0.getRight() && pair1.getRight()));
                    assertNotEquals(pair0.getLeft(), pair1.getLeft());
                    break;
                default:
                    Assert.fail("more then 2 versions " + versions);
            }
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getSlotsReturnsUnmodifiableSnapshot() {
        final FrameDescriptor frameDescriptor = makeThreadSafe(new FrameDescriptor());
        List<? extends FrameSlot> slotsSnapshot1 = frameDescriptor.getSlots();
        assertTrue(slotsSnapshot1.isEmpty());

        frameDescriptor.addFrameSlot("v");
        assertTrue(slotsSnapshot1.isEmpty());
        List<? extends FrameSlot> slotsSnapshot2 = frameDescriptor.getSlots();
        assertEquals(1, slotsSnapshot2.size());

        slotsSnapshot2.remove(0); // throws
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getIdentifiersReturnsUnmodifiableSnapshot() {
        final FrameDescriptor frameDescriptor = makeThreadSafe(new FrameDescriptor());
        Set<Object> slotsSnapshot1 = frameDescriptor.getIdentifiers();
        assertTrue(slotsSnapshot1.isEmpty());

        frameDescriptor.addFrameSlot("v");
        assertTrue(slotsSnapshot1.isEmpty());
        Set<Object> slotsSnapshot2 = frameDescriptor.getIdentifiers();
        assertEquals(1, slotsSnapshot2.size());

        slotsSnapshot2.remove("v"); // throws
    }

    @Test
    public void shallowCopySharesLock() throws NoSuchFieldException, IllegalAccessException {
        final FrameDescriptor frameDescriptor = new FrameDescriptor();
        frameDescriptor.addFrameSlot("v");
        final FrameDescriptor copyBeforeSafe = getShallowCopy(frameDescriptor);
        makeThreadSafe(frameDescriptor);
        final FrameDescriptor copyAfterSafe = getShallowCopy(frameDescriptor);

        Object lock = getLock(frameDescriptor);
        assertEquals(lock, getLock(copyBeforeSafe));
        assertEquals(lock, getLock(copyAfterSafe));
    }

    @Test
    public void getNotInFrameAssumption() throws InterruptedException {
        final FrameDescriptor frameDescriptor = makeThreadSafe(new FrameDescriptor());

        List<List<Assumption>> results = content(PARTIES_COUNT, ITERATIONS,
                        (partyIndex, iteration) -> frameDescriptor.getNotInFrameAssumption("v" + iteration));

        for (int i = 0; i < ITERATIONS; i++) {
            Object assumption = results.get(i).get(0);
            assertTrue(results.get(i).stream().allMatch(v -> v == assumption));
        }
    }

    private static Object getLock(FrameDescriptor copyBeforeSafe) throws IllegalAccessException, NoSuchFieldException {
        Field lockField = FrameDescriptor.class.getDeclaredField("lock");
        lockField.setAccessible(true);
        return lockField.get(copyBeforeSafe);
    }

    @SuppressWarnings("deprecation")
    private static FrameDescriptor getShallowCopy(FrameDescriptor d) {
        return d.shallowCopy();
    }

    @SuppressWarnings("unused")
    private static FrameDescriptor makeThreadSafe(FrameDescriptor frameDescriptor) {
        new RootNode(null, frameDescriptor) {
            @Override
            public Object execute(VirtualFrame frame) {
                return null;
            }
        };

        return frameDescriptor;
    }

    interface Contention<V> {
        V run(int partyIndex, int iteration);
    }

    private static <V> List<List<V>> content(int partiesCount, int iterations, Contention<V> contention) throws InterruptedException {
        return content(partiesCount, iterations, null, contention);
    }

    private static <V> List<List<V>> content(
                    int partiesCount,
                    int iterations,
                    Runnable barrierAction,
                    Contention<V> contention) throws InterruptedException {

        final List<List<V>> results = createResults(partiesCount, iterations);
        final CyclicBarrier barrier = new CyclicBarrier(partiesCount, barrierAction);
        final Thread[] parties = new Thread[partiesCount];

        for (int partyIndex = 0; partyIndex < partiesCount; partyIndex++) {
            parties[partyIndex] = new Thread(makePartyRunnable(barrier, partyIndex, iterations, results, contention));
            parties[partyIndex].start();
        }

        for (int i = 0; i < partiesCount; i++) {
            parties[i].join();
        }

        return results;
    }

    private static <V> List<List<V>> createResults(int partiesCount, int iterations) {
        final List<List<V>> results = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            ArrayList<V> partyResults = new ArrayList<>(partiesCount);
            for (int p = 0; p < partiesCount; p++) {
                partyResults.add(null);
            }
            results.add(partyResults);
        }
        return results;
    }

    private static <V> Runnable makePartyRunnable(CyclicBarrier barrier, int partyIndex, int iterations, List<List<V>> results, Contention<V> contention) {
        return () -> {
            for (int iteration = 0; iteration < iterations; iteration++) {
                try {
                    barrier.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    throw new RuntimeException(e);
                }
                results.get(iteration).set(partyIndex, contention.run(partyIndex, iteration));
            }
        };
    }

}
