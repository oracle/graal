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

public class CompileTimeBenchmark extends GraalBenchmark {

    @MethodSpec(declaringClass = String.class, name = "equals")
    public static class StringEquals extends GraalCompilerState {
    }

    @MethodSpec(declaringClass = HashMap.class, name = "computeIfAbsent")
    public static class HashMapComputeIfAbsent extends GraalCompilerState {
    }

    // Checkstyle: stop method name check
    @Benchmark
    public CompilationResult compile_STRING_equals(StringEquals s) {
        return s.compile();
    }

    @Benchmark
    public CompilationResult compile_HASHMAP_computeIfAbsent(HashMapComputeIfAbsent s) {
        return s.compile();
    }
    // Checkstyle: resume method name check
}
