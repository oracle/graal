/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot;

import jdk.vm.ci.meta.JavaKind;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.Pair;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.hotspot.nodes.CurrentJavaThreadNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.AndNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.RightShiftNode;
import org.graalvm.compiler.nodes.java.AtomicReadAndAddNode;
import org.graalvm.compiler.nodes.memory.OnHeapMemoryAccess;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;
import org.graalvm.word.LocationIdentity;
import sun.misc.Unsafe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Array of counters mapped to a key.
 *
 * @param <T> the type of the key to which the counters are associated.
 */
public class CpuLocalCounterArray<T> {

    public static class Options {
        //@formatter:off
        @Option(help = "Upper bound on the number of cpu locals per counter")
        public static final OptionKey<Integer> MaxCpuLocalsPerCounter = new OptionKey<>(64);
        //@formatter:on
    }

    /**
     * This corresponds to the cache-line size.
     */
    public static final int PER_CPU_LOCAL_BYTES = 64;
    private static final int MIN_CPU_LOCALS_PER_COUNTER = 1;
    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

    private final int maxNumberOfCounters;
    private final int bytesPerCounter;
    /**
     * cpuLocalsPerCounter should be a power of 2.
     * This way, when we compute the modulus when computing the cpu-local offset,
     * we can use a mask instead of the actual modulus operation which is more expensive.
     */
    private final int cpuLocalsPerCounter;
    private final int knuthMethodMultiplier;
    private final long totalBytesAllocated;
    private final List<T> keys;
    private final EconomicMap<T, Long> key2address;
    private final EconomicMap<T, Integer> key2numberOfCounters;
    private final Object monitor;
    private final String name;
    private long counterArrayAddress;
    private long nextCounterAddress;

    public CpuLocalCounterArray(int maxNumberOfCounters, String name) {
        if (maxNumberOfCounters < 0) {
            GraalError.shouldNotReachHere("The number of counters cannot be negative");
        }
        if (name == null || name.isEmpty()) {
            GraalError.shouldNotReachHere("The name should not be null nor empty.");
        }

        this.name = name;
        this.monitor = new Object();
        this.maxNumberOfCounters = maxNumberOfCounters;

        // threadLocalsPerCounter should be a power of 2.
        // This way, when we compute the modulus when computing the cpu local offset,
        // we can use a mask instead of the actual modulus operation which is more expensive.
        this.cpuLocalsPerCounter = getCpuLocalsPerCounter();
        // Two consecutive integers are co-prime.
        // In Knuth's multiplicative method, we need the multiplier and the divider to be co-prime.
        this.knuthMethodMultiplier = this.cpuLocalsPerCounter - 1;

        this.bytesPerCounter = this.cpuLocalsPerCounter * PER_CPU_LOCAL_BYTES;

        this.totalBytesAllocated = (long) this.maxNumberOfCounters * this.bytesPerCounter;
        this.counterArrayAddress = UNSAFE.allocateMemory(this.totalBytesAllocated);
        this.nextCounterAddress = this.counterArrayAddress;

        this.keys = new ArrayList<>();
        this.key2address = EconomicMap.create();
        this.key2numberOfCounters = EconomicMap.create();
    }

    /**
     * Allocates the given number of counters for the given key if not already allocated.
     * The counters are allocated contiguously into the memory.
     *
     * @param key the key for which the counters are allocated.
     * @param numberOfCounters the number of counters to allocate.
     * @throws IllegalStateException if it has already been called for the same key
     *             but with a different number of counters.
     * @throws IllegalArgumentException if the given key is null or numberOfCounters is smaller or equals to 0.
     */
    public void allocateCounters(T key, int numberOfCounters) {
        synchronized (monitor) {
            assert counterArrayAddress != 0L : "The collection has already been destroyed";

            if (key == null || numberOfCounters <= 0) {
                throw new IllegalArgumentException("The key passed as argument is null or the requested number of counters is smaller or equals to 0.");
            }

            if (key2address.containsKey(key)) {
                if (numberOfCounters != key2numberOfCounters.get(key)) {
                    throw new IllegalStateException("A different number of counters has already been allocated for the given key.");
                }
                return;
            }

            final long memoryRequested = (long) numberOfCounters * bytesPerCounter;
            final long maxAddress = counterArrayAddress + totalBytesAllocated;
            if (nextCounterAddress + memoryRequested > maxAddress) {
                throw new RuntimeException("Not enough memory allocated.");
            }

            final long counterAddress = nextCounterAddress;
            nextCounterAddress += memoryRequested;

            // Initialize the counter memory to 0.
            UNSAFE.setMemory(counterAddress, memoryRequested, (byte) 0);

            key2address.put(key, counterAddress);
            key2numberOfCounters.put(key, numberOfCounters);
            keys.add(key);
        }
    }

    /**
     * Allocates a counter for the given key if not already allocated.
     *
     * @param key the key for which a counter is allocated.
     */
    public void allocateCounter(T key) {
        allocateCounters(key, 1);
    }

    /**
     * Produces a Pair of nodes representing a counter.
     * The first node in the Pair corresponds to the reading of the counter value.
     * The second node in the Pair corresponds to the writing of the counter value.
     * Note that if we want to use an atomic counter, e.g., {@link AtomicReadAndAddNode},
     * the first and the second node in the Pair will be the same.
     *
     * @param graph the graph in which the counter is inserted.
     * @param key the key associated with the counter to increment.
     * @param counterIndex the index of the counter among all counters associated with the given key.
     * @return a Pair representing the counter.
     */
    public Pair<FixedWithNextNode, FixedWithNextNode> createIncrementCounter(StructuredGraph graph, T key, ValueNode counterIndex) {
        final AddressNode counterAddress = createComputeAddress(graph, key, counterIndex);
        final ValueNode increment = graph.addOrUniqueWithInputs(ConstantNode.forLong(1L));
        final ReadNode memoryRead = graph.add(new ReadNode(counterAddress, LocationIdentity.any(), StampFactory.forKind(JavaKind.Long), OnHeapMemoryAccess.BarrierType.NONE));
        final ValueNode addNode = graph.addOrUniqueWithInputs(AddNode.create(memoryRead, increment, NodeView.DEFAULT));
        final WriteNode memoryWrite = graph.add(new WriteNode(counterAddress, LocationIdentity.any(), addNode, OnHeapMemoryAccess.BarrierType.NONE));
        graph.addAfterFixed(memoryRead, memoryWrite);
        return Pair.create(memoryRead, memoryWrite);
    }

    public Pair<FixedWithNextNode, FixedWithNextNode> createIncrementCounter(StructuredGraph graph, T key, int counterIndex) {
        final ValueNode indexNode = graph.addOrUniqueWithInputs(ConstantNode.forLong(counterIndex));
        return createIncrementCounter(graph, key, indexNode);
    }

    /**
     * Dumps the counter values (for debugging purposes).
     * It dumps only the allocated counters.
     *
     * @param formatter specifies the format in which the counters are dumped.
     */
    public void dumpCounters(BiConsumer<Pair<T, Integer>, Long> formatter) {
        final List<Pair<Pair<T, Integer>, Long>> dumps = new ArrayList<>();

        synchronized (monitor) {
            assert counterArrayAddress != 0L : "The collection has already been freed.";
            for (T key : keys) {
                long baseAddress = key2address.get(key);
                for (int i = 0; i < key2numberOfCounters.get(key); i++) {
                    for (int j = 0; j < cpuLocalsPerCounter; j++) {
                        long offset = i * bytesPerCounter + j * PER_CPU_LOCAL_BYTES;
                        long counter = UNSAFE.getLong(baseAddress + offset);
                        dumps.add(Pair.create(Pair.create(key, i), counter));
                    }
                }
            }
        }

        for (Pair<Pair<T, Integer>, Long> row : dumps) {
            formatter.accept(row.getLeft(), row.getRight());
        }
    }

    /**
     * Frees the collection (implying no further usage).
     */
    public void destroy() {
        synchronized (monitor) {
            long address = counterArrayAddress;
            counterArrayAddress = 0L;

            if (address == 0L) {
                // The memory has already been freed.
                throw new IllegalStateException("The ThreadCountersArray has already been freed");
            }

            UNSAFE.freeMemory(address);
        }
    }

    public String getStatistics() {
        final StringBuilder statistics = new StringBuilder();
        statistics.append("Counter '").append(name).append("'").append(System.lineSeparator());
        statistics.append("---------------").append(System.lineSeparator());

        final long numberOfAllocatedCounters = (nextCounterAddress - counterArrayAddress) / bytesPerCounter;
        statistics.append("Number of counters allocated: ").append(numberOfAllocatedCounters).append(System.lineSeparator());

        double averageNumberOfCountersPerKey;
        int maxNumberOfCountersPerKey = 0;
        T keyWithMaxNumberOfCounters = null;

        final MapCursor<T, Integer> entry = key2numberOfCounters.getEntries();
        while (entry.advance()) {
            if (entry.getValue() > maxNumberOfCountersPerKey) {
                maxNumberOfCountersPerKey = entry.getValue();
                keyWithMaxNumberOfCounters = entry.getKey();
            }
        }
        if (keys.size() == 0) {
            averageNumberOfCountersPerKey = 0.0;
        } else {
            averageNumberOfCountersPerKey = numberOfAllocatedCounters / (double) keys.size();
        }
        statistics.append("Average number of counters per key: ").append(averageNumberOfCountersPerKey).append(System.lineSeparator());
        statistics.append("Max number of counters allocated for a key: ").append(maxNumberOfCountersPerKey).append("(key: ").append(keyWithMaxNumberOfCounters).append(")").append(System.lineSeparator());

        statistics.append("Number of keys: ").append(keys.size()).append(System.lineSeparator());
        statistics.append(System.lineSeparator());

        return statistics.toString();
    }

    /**
     * Produces an AddressNode that represents the address computation of the counterIndex-th counter associated
     * with the given key for a particular key.
     *
     * @param graph the graph in which the address computation is inserted.
     * @param key the key associated with the counter whose address is computed.
     * @param counterIndex the index of the counter among all counters associated with the given key.
     * @return an AddressNode representing the address computation.
     */
    private AddressNode createComputeAddress(StructuredGraph graph, T key, ValueNode counterIndex) {
        if (graph == null || key == null || counterIndex == null) {
            throw new IllegalArgumentException("At least one parameter is null.");
        }

        final long baseCounterAddress;
        synchronized (monitor) {
            if (!key2address.containsKey(key)) {
                throw new IllegalStateException("No counters have been allocated for the given key");
            }
            baseCounterAddress = key2address.get(key);
        }

        final ValueNode baseCounterAddressNode = graph.addOrUniqueWithInputs(ConstantNode.forLong(baseCounterAddress));
        final ValueNode offset = graph.addOrUniqueWithInputs(MulNode.create(counterIndex, ConstantNode.forLong(bytesPerCounter), NodeView.DEFAULT));
        final ValueNode counterAddress = graph.addOrUniqueWithInputs(AddNode.create(baseCounterAddressNode, offset, NodeView.DEFAULT));

        return getLocalAddress(graph, counterAddress);
    }

    /**
     * Produces a ValueNode that represents the computation of an identifier for a particular thread.
     *
     * @param graph the StructureGraph in which the ValueNode is added.
     * @return a ValueNode representing the identifier computation.
     */
    private ValueNode getThreadIdentifier(StructuredGraph graph) {
        final ValueNode thread = graph.addOrUnique(new CurrentJavaThreadNode(JavaKind.Long));

        // We shift the thread address 11 bits to the left.
        // Indeed, the 11 least significant bits of the thread address are set to 0 due to its alignment.
        // By shifting to the left, we get rid of the 0s.
        // The number 11 has been determined empirically.
        final ValueNode shiftValue = graph.addOrUniqueWithInputs(ConstantNode.forInt(11));

        return graph.addOrUniqueWithInputs(RightShiftNode.create(thread, shiftValue, NodeView.DEFAULT));
    }

    /**
     * Produces a ValueNode that represents the computation of the offset of the cpu local.
     *
     * @param graph the StructuredGraph in which the ValueNode is added.
     * @param threadIdentifier the ValueNode representing the computation of a particular thread identifier.
     * @return a ValueNode representing the offset computation.
     */
    private ValueNode getThreadLocalOffset(StructuredGraph graph, ValueNode threadIdentifier) {
        final ValueNode index = getKnuthHash(graph, threadIdentifier);
        final ValueNode cpuCounterBytes = graph.addOrUniqueWithInputs(ConstantNode.forLong(PER_CPU_LOCAL_BYTES));
        return graph.addOrUniqueWithInputs(MulNode.create(index, cpuCounterBytes, NodeView.DEFAULT));
    }

    /**
     * Produces a ValueNode that represents the computation of a hash value of the thread identifier.
     * The resulting hash value ranges from 0 to the number of cpu locals per counter minus 1.
     * Knuth's multiplicative method is used to compute the hash value.
     *
     * @param graph the StructuredGraph in which the ValueNode is added.
     * @param threadIdentifier the ValueNode representing the computation of the thread identifier.
     * @return a ValueNode representing the hash value computation.
     */
    private ValueNode getKnuthHash(StructuredGraph graph, ValueNode threadIdentifier) {
        final ValueNode multiplier = graph.addOrUniqueWithInputs(ConstantNode.forLong(knuthMethodMultiplier));
        final ValueNode mulNode = graph.addOrUniqueWithInputs(MulNode.create(threadIdentifier, multiplier, NodeView.DEFAULT));
        final ValueNode mask = graph.addOrUniqueWithInputs(ConstantNode.forLong(cpuLocalsPerCounter - 1));
        return graph.addOrUniqueWithInputs(AndNode.create(mulNode, mask, NodeView.DEFAULT));
    }

    /**
     * Produces an AddressNode holding the address of a counter slot.
     *
     * @param graph the StructuredGraph in which the ValueNode is added.
     * @param offset the ValueNode representing the offset of the address.
     * @param baseAddress the ValueNode representing the base of the address.
     * @return an AddressNode of the counter slot.
     */
    private AddressNode generateAddressNode(StructuredGraph graph, ValueNode offset, ValueNode baseAddress) {
        return graph.addOrUnique(new OffsetAddressNode(baseAddress, offset));
    }

    private AddressNode getLocalAddress(StructuredGraph graph, long counterAddress) {
        final ValueNode counterAddressNode = graph.addOrUniqueWithInputs(ConstantNode.forLong(counterAddress));
        return getLocalAddress(graph, counterAddressNode);
    }

    private AddressNode getLocalAddress(StructuredGraph graph, ValueNode counterAddress) {
        final ValueNode threadIdentifier = getThreadIdentifier(graph);
        final ValueNode threadLocalOffset = getThreadLocalOffset(graph, threadIdentifier);
        return generateAddressNode(graph, threadLocalOffset, counterAddress);
    }

    private int getCpuLocalsPerCounter() {
        final Runtime runtime = Runtime.getRuntime();
        final int numberOfCPU = runtime.availableProcessors();
        // We multiply numberOfCpu by two to reduce the thread-id collision probability.
        final int nearestHighestPowerOfTwo = roundUpToNearestPowerOfTwo(numberOfCPU * 2);
        return Math.min(Math.max(nearestHighestPowerOfTwo, MIN_CPU_LOCALS_PER_COUNTER), Options.MaxCpuLocalsPerCounter.getDefaultValue());
    }

    /**
     * Round up the given number to the nearest power of 2, according to the algorithm described in the
     * link below.
     *
     * @param x the number to round up to the nearest power of 2.
     * @return the power of 2 nearest to the given number.
     */
    private int roundUpToNearestPowerOfTwo(int x) {
        int y = x - 1;
        y |= y >> 1;
        y |= y >> 2;
        y |= y >> 4;
        y |= y >> 8;
        y |= y >> 16;
        y += 1;
        return y;
    }
}
