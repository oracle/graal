/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.test;

import static com.oracle.graal.debug.internal.DebugHistogramAsciiPrinter.*;

import java.io.*;
import java.util.*;

import com.google.monitoring.runtime.instrumentation.*;
import com.oracle.graal.debug.DebugHistogram.CountedValue;
import com.oracle.graal.debug.internal.*;

class AllocationScope implements AutoCloseable {
    final Object context;
    final AllocationScope parent;
    final Map<String, CountedValue> bytesPerType = new HashMap<>();
    final Map<String, CountedValue> instancesPerType = new HashMap<>();

    public AllocationScope(Object context) {
        this.context = context;
        parent = allocationCounter.get();
        allocationCounter.set(this);
    }

    private static List<CountedValue> sortedValues(Map<String, CountedValue> map) {
        ArrayList<CountedValue> res = new ArrayList<>(map.values());
        Collections.sort(res);
        return res;
    }

    public void close() {
        allocationCounter.set(parent);
        PrintStream out = System.out;
        out.println("\n\nAllocation histograms for " + context);
        DebugHistogramAsciiPrinter printer = new DebugHistogramAsciiPrinter(out, 20, DefaultNameSize, DefaultBarSize);
        printer.print(sortedValues(instancesPerType), "InstancesPerType");
        printer.print(sortedValues(bytesPerType), "BytesPerType");
    }

    public CountedValue instancesPerType(String desc) {
        CountedValue count = instancesPerType.get(desc);
        if (count == null) {
            count = new CountedValue(0, desc);
            instancesPerType.put(desc, count);
        }
        return count;
    }

    public CountedValue bytesPerType(String desc) {
        CountedValue count = bytesPerType.get(desc);
        if (count == null) {
            count = new CountedValue(0, desc);
            bytesPerType.put(desc, count);
        }
        return count;
    }

    static ThreadLocal<AllocationScope> allocationCounter = new ThreadLocal<>();

    static class AllocationSampler implements Sampler {

        public void sampleAllocation(int count, String desc, Object newObj, long size) {
            AllocationScope c = allocationCounter.get();
            if (c != null) {
                String type;
                if (count != -1) {
                    type = desc + "[]";
                } else {
                    type = desc;
                }

                c.instancesPerType(type).inc();
                c.bytesPerType(type).add((int) size);
            }
        }

    }

    static {
        AllocationRecorder.addSampler(new AllocationSampler());
    }
}
