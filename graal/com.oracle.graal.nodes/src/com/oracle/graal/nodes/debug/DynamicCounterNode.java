/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.nodes.debug;

import java.io.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;

/**
 * This node can be used to add a counter to the code that will estimate the dynamic number of calls
 * by adding an increment to the compiled code. This should of course only be used for
 * debugging/testing purposes, and is not 100% accurate (because of concurrency issues while
 * accessing the counters).
 * 
 * A unique counter will be created for each unique String passed to the constructor.
 */
public class DynamicCounterNode extends FixedWithNextNode implements Lowerable {

    private static final int MAX_COUNTERS = 10 * 1024;
    private static final long[] COUNTERS = new long[MAX_COUNTERS];
    private static final HashMap<String, Integer> INDEXES = new HashMap<>();
    public static String excludedClassPrefix = null;
    public static boolean enabled = false;

    private final String name;
    private final long increment;
    private final boolean addContext;

    public DynamicCounterNode(String name, long increment, boolean addContext) {
        super(StampFactory.forVoid());
        if (!enabled) {
            throw new GraalInternalError("dynamic counters not enabled");
        }
        this.name = name;
        this.increment = increment;
        this.addContext = addContext;
    }

    public String getName() {
        return name;
    }

    public long getIncrement() {
        return increment;
    }

    public boolean isAddContext() {
        return addContext;
    }

    private static synchronized int getIndex(String name) {
        Integer index = INDEXES.get(name);
        if (index == null) {
            index = INDEXES.size();
            INDEXES.put(name, index);
            if (index >= MAX_COUNTERS) {
                throw new GraalInternalError("too many dynamic counters");
            }
            return index;
        } else {
            return index;
        }
    }

    public static synchronized void dump(PrintStream out, double seconds) {
        TreeMap<Long, String> sorted = new TreeMap<>();

        long sum = 0;
        for (int i = 0; i < MAX_COUNTERS; i++) {
            sum += COUNTERS[i];
        }
        long cutoff = sum / 1000;
        int cnt = 0;
        for (Map.Entry<String, Integer> entry : INDEXES.entrySet()) {
            if (COUNTERS[entry.getValue()] > cutoff) {
                sorted.put(COUNTERS[entry.getValue()] * MAX_COUNTERS + cnt++, entry.getKey());
            }
        }

        out.println("=========== dynamic counters, time = " + seconds + " s");
        for (Map.Entry<Long, String> entry : sorted.entrySet()) {
            long counter = entry.getKey() / MAX_COUNTERS;
            out.println((int) (counter / seconds) + "/s \t" + (counter * 100 / sum) + "% \t" + entry.getValue());
        }
        out.println((int) (sum / seconds) + "/s: total");
        out.println("============================");

        clear();
    }

    public static void clear() {
        Arrays.fill(COUNTERS, 0);
    }

    @Override
    public void lower(LoweringTool tool) {
        StructuredGraph graph = (StructuredGraph) graph();
        if (excludedClassPrefix == null || !graph.method().getDeclaringClass().getName().startsWith(excludedClassPrefix)) {
            int index = addContext ? getIndex(name + " @ " + MetaUtil.format("%h.%n", ((StructuredGraph) graph()).method())) : getIndex(name);

            ConstantNode arrayConstant = ConstantNode.forObject(COUNTERS, tool.getRuntime(), graph);
            ConstantNode indexConstant = ConstantNode.forInt(index, graph);
            LoadIndexedNode load = graph.add(new LoadIndexedNode(arrayConstant, indexConstant, Kind.Long));
            IntegerAddNode add = graph.add(new IntegerAddNode(Kind.Long, load, ConstantNode.forLong(increment, graph)));
            StoreIndexedNode store = graph.add(new StoreIndexedNode(arrayConstant, indexConstant, Kind.Long, add));

            graph.addBeforeFixed(this, load);
            graph.addBeforeFixed(this, store);
        }
        graph.removeFixed(this);
    }

    public static void addCounterBefore(String name, long increment, boolean addContext, FixedNode position) {
        if (enabled) {
            StructuredGraph graph = (StructuredGraph) position.graph();
            DynamicCounterNode counter = graph.add(new DynamicCounterNode(name, increment, addContext));
            graph.addBeforeFixed(position, counter);
        }
    }
}
