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
    private final String name;
    private final boolean addContext;

    public DynamicCounterNode(String name, boolean addContext) {
        super(StampFactory.forVoid());
        this.name = name;
        this.addContext = addContext;
    }

    private static synchronized int getIndex(String name) {
        Integer index = INDEXES.get(name);
        if (index == null) {
            index = INDEXES.size();
            if (index == 0) {
                Runtime.getRuntime().addShutdownHook(new Thread() {

                    @Override
                    public void run() {
                        dump();
                    }
                });
            }
            INDEXES.put(name, index);
            if (index >= MAX_COUNTERS) {
                throw new GraalInternalError("too many dynamic counters");
            }
            return index;
        } else {
            return index;
        }
    }

    private static synchronized void dump() {
        TreeMap<Long, String> sorted = new TreeMap<>();

        long sum = 0;
        for (int i = 0; i < MAX_COUNTERS; i++) {
            sum += COUNTERS[i];
        }
        int cnt = 0;
        for (Map.Entry<String, Integer> entry : INDEXES.entrySet()) {
            sorted.put(COUNTERS[entry.getValue()] * MAX_COUNTERS + cnt++, entry.getKey());
        }

        for (Map.Entry<Long, String> entry : sorted.entrySet()) {
            System.out.println((entry.getKey() / MAX_COUNTERS) + ": " + entry.getValue());
        }
        System.out.println(sum + ": total");

    }

    @Override
    public void lower(LoweringTool tool) {
        int index = addContext ? getIndex(name + " @ " + MetaUtil.format("%h.%n", ((StructuredGraph) graph()).method())) : getIndex(name);

        StructuredGraph graph = (StructuredGraph) graph();
        ConstantNode arrayConstant = ConstantNode.forObject(COUNTERS, tool.getRuntime(), graph);
        ConstantNode indexConstant = ConstantNode.forInt(index, graph);
        LoadIndexedNode load = graph.add(new LoadIndexedNode(arrayConstant, indexConstant, Kind.Long));
        IntegerAddNode add = graph.add(new IntegerAddNode(Kind.Long, load, ConstantNode.forLong(1, graph)));
        StoreIndexedNode store = graph.add(new StoreIndexedNode(arrayConstant, indexConstant, Kind.Long, add));

        graph.addBeforeFixed(this, load);
        graph.addBeforeFixed(this, store);
        graph.removeFixed(this);
    }

    public static void createCounter(String name, FixedNode before, boolean addContext) {
        StructuredGraph graph = (StructuredGraph) before.graph();
        DynamicCounterNode counter = graph.add(new DynamicCounterNode(name, addContext));
        graph.addBeforeFixed(before, counter);
    }
}
