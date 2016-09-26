/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot;

import java.io.PrintStream;
import java.util.IdentityHashMap;
import java.util.Map;

import com.oracle.graal.options.Option;
import com.oracle.graal.options.OptionType;
import com.oracle.graal.options.OptionValue;
import com.oracle.graal.options.StableOptionValue;

import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.meta.ResolvedJavaMethod;

class CompilationCounters {

    public static class Options {
        // @formatter:off
        @Option(help = "The number of compilations allowed for any method before " +
                       "the VM exits (a value of 0 means there is no limit).", type = OptionType.Debug)
        public static final OptionValue<Integer> CompilationCountLimit = new StableOptionValue<>(0);
        // @formatter:on
    }

    static boolean compilationCountersEnabled() {
        return Options.CompilationCountLimit.getValue() > 0;
    }

    private final IdentityHashMap<ResolvedJavaMethod, Integer> counters = new IdentityHashMap<>();

    /**
     * Counts the number of compilations for the {@link ResolvedJavaMethod} of the
     * {@link CompilationRequest}. If the number of compilations exceeds the limit determined by
     * {@link Options#CompilationCountLimit} this method will return {@code false}.
     *
     * @param request the compilation request that is about to be compiled
     * @return {@code true} if the method was compiled less often than the compilation limit,
     *         {@code false} otherwise
     */
    synchronized boolean countCompilation(CompilationRequest request) {
        final ResolvedJavaMethod method = request.getMethod();
        Integer val = counters.get(method);
        val = val != null ? val + 1 : 1;
        counters.put(method, val);
        if (val > Options.CompilationCountLimit.getValue()) {
            return false;
        }
        return true;
    }

    synchronized void dumpCounters(PrintStream s) {
        for (Map.Entry<ResolvedJavaMethod, Integer> entry : counters.entrySet()) {
            s.printf("Method %s compiled %d times.%n", entry.getKey(), entry.getValue());
        }
    }

}
