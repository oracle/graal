/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot;

import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeSet;

import jdk.graal.compiler.core.common.util.MethodKey;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.meta.ResolvedJavaMethod;

class CompilationCounters {

    public static class Options {
        // @formatter:off
        @Option(help = "The number of compilations allowed for any method before " +
                       "the VM exits (a value of 0 means there is no limit).", type = OptionType.Debug)
        public static final OptionKey<Integer> CompilationCountLimit = new OptionKey<>(0);
        // @formatter:on
    }

    private final OptionValues options;

    CompilationCounters(OptionValues options) {
        TTY.println("Warning: Compilation counters enabled, excessive recompilation of a method will cause a failure!");
        this.options = options;
    }

    private final Map<MethodKey, Integer> counters = new EconomicHashMap<>();

    /**
     * Counts the number of compilations for the {@link ResolvedJavaMethod} of the
     * {@link CompilationRequest}. If the number of compilations exceeds
     * {@link Options#CompilationCountLimit} this method prints an error message and exits the VM.
     *
     * @param method the method about to be compiled
     */
    synchronized void countCompilation(ResolvedJavaMethod method) {
        MethodKey key = new MethodKey(method);

        Integer val = counters.get(key);
        val = val != null ? val + 1 : 1;
        counters.put(key, val);
        if (val > Options.CompilationCountLimit.getValue(options)) {
            TTY.printf("Error. Method %s was compiled too many times. Number of compilations: %d\n", method.format("%H.%n(%p)"),
                            CompilationCounters.Options.CompilationCountLimit.getValue(options));
            TTY.println("==================================== High compilation counters ====================================");
            SortedSet<Map.Entry<MethodKey, Integer>> sortedCounters = new TreeSet<>(new CounterComparator());
            for (Map.Entry<MethodKey, Integer> e : counters.entrySet()) {
                sortedCounters.add(e);
            }
            for (Map.Entry<MethodKey, Integer> entry : sortedCounters) {
                if (entry.getValue() >= Options.CompilationCountLimit.getValue(options) / 2) {
                    TTY.out.printf("%d\t%s%n", entry.getValue(), entry.getKey());
                }
            }
            TTY.flush();
            HotSpotGraalServices.exit(-1, HotSpotJVMCIRuntime.runtime());
        }
    }

    static final class CounterComparator implements Comparator<Map.Entry<MethodKey, Integer>> {
        @Override
        public int compare(Entry<MethodKey, Integer> o1, Entry<MethodKey, Integer> o2) {
            if (o1.getValue() < o2.getValue()) {
                return -1;
            }
            if (o1.getValue() > o2.getValue()) {
                return 1;
            }
            return String.valueOf(o1.getKey()).compareTo(String.valueOf(o2.getKey()));
        }
    }
}
