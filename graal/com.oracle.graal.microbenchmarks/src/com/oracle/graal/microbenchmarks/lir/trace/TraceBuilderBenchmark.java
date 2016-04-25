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
package com.oracle.graal.microbenchmarks.lir.trace;

import java.util.Arrays;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Warmup;

import com.oracle.graal.compiler.common.alloc.BiDirectionalTraceBuilder;
import com.oracle.graal.compiler.common.alloc.TraceBuilderResult;
import com.oracle.graal.compiler.common.alloc.UniDirectionalTraceBuilder;
import com.oracle.graal.microbenchmarks.graal.GraalBenchmark;
import com.oracle.graal.nodes.cfg.Block;

@Warmup(iterations = 15)
public class TraceBuilderBenchmark extends GraalBenchmark {

    public static class State extends ControlFlowGraphState {
        @MethodDescString @Param({
                        "java.lang.String#equals",
                        "java.util.HashMap#computeIfAbsent"
        }) public String method;
    }

    @Benchmark
    public TraceBuilderResult<Block> uniDirectionalTraceBuilder(State s) {
        return UniDirectionalTraceBuilder.computeTraces(s.cfg.getStartBlock(), Arrays.asList(s.cfg.getBlocks()));
    }

    @Benchmark
    public TraceBuilderResult<Block> biDirectionalTraceBuilder(State s) {
        return BiDirectionalTraceBuilder.computeTraces(s.cfg.getStartBlock(), Arrays.asList(s.cfg.getBlocks()));
    }

}
