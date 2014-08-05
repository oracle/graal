/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.debug.internal.MemUseTrackerImpl.*;

import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.nodes.*;

/**
 * Used to benchmark memory usage during Graal compilation.
 */
public class MemoryUsageBenchmark extends GraalCompilerTest {

    public static int simple(int a, int b) {
        return a + b;
    }

    public static int complex(CharSequence cs) {
        if (cs instanceof String) {
            return cs.hashCode();
        }
        int[] hash = {0};
        cs.chars().forEach(c -> hash[0] += c);
        return hash[0];
    }

    static class MemoryUsageCloseable implements AutoCloseable {

        private final long start;
        private final String name;

        public MemoryUsageCloseable(String name) {
            this.name = name;
            this.start = getCurrentThreadAllocatedBytes();
        }

        @Override
        public void close() {
            long end = getCurrentThreadAllocatedBytes();
            long allocated = end - start;
            System.out.println(name + ": " + allocated);
        }
    }

    public static void main(String[] args) {
        new MemoryUsageBenchmark().run();
    }

    private void doCompilation(StructuredGraph graph) {
        CompilationResult compResult = super.compile(graph.method(), graph);
        addMethod(graph.method(), compResult);
    }

    private void compileAndTime(String methodName) {
        Method method = getMethod(methodName);
        StructuredGraph graph = parse(method);

        try (MemoryUsageCloseable c = new MemoryUsageCloseable(methodName + "[cold]")) {
            doCompilation(graph);
        }

        // Warm up compiler for this compilation
        for (int i = 0; i < 10; i++) {
            doCompilation(graph);
        }

        try (MemoryUsageCloseable c = new MemoryUsageCloseable(methodName + "[warm]")) {
            doCompilation(graph);
        }
    }

    public void run() {
        compileAndTime("simple");
        compileAndTime("complex");
    }
}
