/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.microbenchmarks.lir;

import java.util.HashMap;

import org.openjdk.jmh.annotations.Benchmark;

import com.oracle.graal.code.CompilationResult;
import com.oracle.graal.microbenchmarks.graal.GraalBenchmark;
import com.oracle.graal.microbenchmarks.graal.util.MethodSpec;
import com.oracle.graal.nodes.StructuredGraph;

public class CompileTimeBenchmark extends GraalBenchmark {

    // Checkstyle: stop method name check
    @MethodSpec(declaringClass = String.class, name = "equals")
    public static class Compile_StringEquals extends GraalCompilerState.Compile {
    }

    @MethodSpec(declaringClass = HashMap.class, name = "computeIfAbsent")
    public static class Compile_HashMapComputeIfAbsent extends GraalCompilerState.Compile {
    }

    @Benchmark
    public CompilationResult compile_STRING_equals(Compile_StringEquals s) {
        return s.compile();
    }

    @Benchmark
    public CompilationResult compile_HASHMAP_computeIfAbsent(Compile_HashMapComputeIfAbsent s) {
        return s.compile();
    }

    @MethodSpec(declaringClass = String.class, name = "equals")
    public static class FrontEndOnly_StringEquals extends GraalCompilerState.FrontEndOnly {
    }

    @MethodSpec(declaringClass = HashMap.class, name = "computeIfAbsent")
    public static class FrontEndOnly_HashMapComputeIfAbsent extends GraalCompilerState.FrontEndOnly {
    }

    @Benchmark
    public StructuredGraph frontend_STRING_equals(FrontEndOnly_StringEquals s) {
        return s.compile();
    }

    @Benchmark
    public StructuredGraph frontend_HASHMAP_computeIfAbsent(FrontEndOnly_HashMapComputeIfAbsent s) {
        return s.compile();
    }

    @MethodSpec(declaringClass = String.class, name = "equals")
    public static class BackEndOnly_StringEquals extends GraalCompilerState.BackEndOnly {
    }

    @MethodSpec(declaringClass = HashMap.class, name = "computeIfAbsent")
    public static class BackEndOnly_HashMapComputeIfAbsent extends GraalCompilerState.BackEndOnly {
    }

    @Benchmark
    public CompilationResult backend_STRING_equals(BackEndOnly_StringEquals s) {
        return s.compile();
    }

    @Benchmark
    public CompilationResult backend_HASHMAP_computeIfAbsent(BackEndOnly_HashMapComputeIfAbsent s) {
        return s.compile();
    }
    // Checkstyle: resume method name check
}
