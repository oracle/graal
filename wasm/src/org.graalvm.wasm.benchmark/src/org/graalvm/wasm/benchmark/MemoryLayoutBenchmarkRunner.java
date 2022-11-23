/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.wasm.benchmark;

import java.io.IOException;
import java.util.Arrays;

import org.graalvm.polyglot.Context;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmLanguage;
import org.graalvm.wasm.benchmark.memory.MemoryNode;
import org.graalvm.wasm.benchmark.memory.PathParser;
import org.graalvm.wasm.utils.WasmResource;
import org.graalvm.wasm.utils.cases.WasmCase;
import org.openjdk.jol.info.GraphPathRecord;
import org.openjdk.jol.info.GraphVisitor;
import org.openjdk.jol.info.GraphWalker;

public class MemoryLayoutBenchmarkRunner {

    private static final String BENCHCASES_TYPE = "bench";

    private static final String BENCHCASES_RESOURCE = "wasm/memory";

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args[0].equals("--list")) {
            System.out.println(WasmResource.getResourceIndex(String.format("/%s/%s", BENCHCASES_TYPE, BENCHCASES_RESOURCE)));
            return;
        }

        if (args.length < 5 || !args[0].equals("--warmup-iterations") || !args[2].equals("--result-iterations")) {
            System.err.println("Usage: --warmup-iterations <n> --result-iterations <n> <case_spec>...");
        }

        final int warmup_iterations = Integer.parseInt(args[1]);
        final int result_iterations = Integer.parseInt(args[3]);

        // Support debugging
        int offset = 4;
        if (args[4].equals("Listening")) {
            offset = 11;
        }

        for (final String caseSpec : Arrays.copyOfRange(args, offset, args.length)) {
            final WasmCase benchmarkCase = WasmCase.collectFileCase(BENCHCASES_TYPE, BENCHCASES_RESOURCE, caseSpec);
            assert benchmarkCase != null : String.format("Test case %s/%s not found.", BENCHCASES_RESOURCE, caseSpec);

            final Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID);
            contextBuilder.allowExperimentalOptions(true);
            contextBuilder.option("wasm.Builtins", "go");
            contextBuilder.option("wasm.MemoryOverheadMode", "true");

            for (int i = 0; i < warmup_iterations + result_iterations; i++) {
                final Context context = contextBuilder.build();

                benchmarkCase.getSources().forEach(context::eval);
                context.getBindings(WasmLanguage.ID).getMember("main").getMember("run");

                sleep();
                System.gc();
                sleep();

                if (i >= warmup_iterations) {
                    MemoryNode memoryRoot = readMemoryLayout(context);
                    memoryRoot.print();
                } else {
                    System.out.println("warmup iteration : ");
                }
                context.close();
            }
        }
    }

    private static MemoryNode readMemoryLayout(Context context) {
        final MemoryNode memoryRoot = new MemoryNode("context", 0);
        GraphVisitor v = new GraphVisitor() {
            private String contextPath = null;

            @Override
            public void visit(GraphPathRecord graphPathRecord) {
                if (WasmContext.class.getCanonicalName().equals(graphPathRecord.klass().getCanonicalName())) {
                    contextPath = graphPathRecord.path();
                }
                // System.out.println(graphPathRecord.path());
                if (contextPath != null && graphPathRecord.path().contains(contextPath + ".")) {
                    PathParser.parse(graphPathRecord.path().substring(contextPath.length()).toCharArray(), memoryRoot, graphPathRecord);
                }
            }
        };
        GraphWalker g = new GraphWalker(v);
        g.walk(context);
        return memoryRoot;
    }

    private static void sleep() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
