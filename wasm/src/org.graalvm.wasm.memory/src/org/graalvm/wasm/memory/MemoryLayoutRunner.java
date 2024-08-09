/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.memory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.wasm.WasmContext;
import org.graalvm.wasm.WasmLanguage;

import org.openjdk.jol.info.GraphPathRecord;
import org.openjdk.jol.info.GraphVisitor;
import org.openjdk.jol.info.GraphWalker;

public class MemoryLayoutRunner {
    private static final int DEFAULT_WARMUP_ITERATIONS = 8;
    private static final String DEFAULT_ENTRY_POINT = "main";

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            printUsage();
            return;
        }
        final Map<String, String> parameters = parseParameters(args);

        if (parameters.containsKey("help")) {
            printUsage();
            return;
        }
        final int warmupIterations;
        if (parameters.containsKey("warmup-iterations")) {
            warmupIterations = Integer.parseInt(parameters.get("warmup-iterations"));
        } else {
            warmupIterations = DEFAULT_WARMUP_ITERATIONS;
        }

        final String entryPoint = parameters.getOrDefault("entry-point", DEFAULT_ENTRY_POINT);

        final PrintStream output;
        if (parameters.containsKey("output")) {
            output = new PrintStream(new FileOutputStream(parameters.get("output")));
        } else {
            output = System.out;
        }
        final String filePath = parameters.get("path");

        final Context.Builder contextBuilder = Context.newBuilder(WasmLanguage.ID);
        contextBuilder.allowExperimentalOptions(true);
        for (String param : parameters.keySet()) {
            if (param.startsWith("wasm")) {
                contextBuilder.option(param, parameters.get(param));
            }
        }
        contextBuilder.option("wasm.MemoryOverheadMode", "true");

        final Source source = Source.newBuilder(WasmLanguage.ID, new File(filePath)).cached(false).build();

        System.out.println("...::: Creating memory layout for " + Paths.get(filePath).getFileName() + " :::...");

        for (int i = 0; i < warmupIterations + 1; i++) {
            final Context context = contextBuilder.build();

            Value mainModule = context.eval(source);
            mainModule.getMember(entryPoint);

            sleep();
            System.gc();
            sleep();

            if (i >= warmupIterations) {
                System.out.println("Creating output...");
                MemoryNode memoryRoot = readMemoryLayout(context);
                memoryRoot.print(output);
            } else {
                System.out.println("Performing warmup...(" + (i + 1) + "/" + warmupIterations + ")");
            }
            context.close();
        }

        System.out.println("Done");
    }

    private static Map<String, String> parseParameters(String[] args) {
        Map<String, String> parameters = new HashMap<>();
        for (int i = 0; i < args.length - 1; i++) {
            final String[] parts = args[i].replace("--", "").split("=");
            if (parts.length > 1) {
                parameters.put(parts[0], parts[1]);
            } else {
                parameters.put(parts[0], "true");
            }
        }
        parameters.put("path", args[args.length - 1]);
        return parameters;
    }

    private static void printUsage() {
        System.out.println("Usage: wasm-memory-layout -- <options> <path-to-wasm-file>");
        System.out.println("Options:");
        System.out.println("\t--help\tPrint this help message");
        System.out.println("\t--warmup-iterations=<n>\tThe number of warmup iterations (default " + DEFAULT_WARMUP_ITERATIONS + ")");
        System.out.println("\t--entry-point=<name>\tThe name of the main function in the module (default " + DEFAULT_ENTRY_POINT + ")");
        System.out.println("\t--output=<file>\tThe file to print the output to (default stdout)");
        System.out.println("\t--wasm.<option>\tA option passed to WebAssembly");
    }

    private static MemoryNode readMemoryLayout(Context context) {
        final MemoryNode memoryRoot = new MemoryNode("context", 0);
        GraphVisitor v = new GraphVisitor() {
            private String contextPath = null;

            @Override
            public void visit(GraphPathRecord graphPathRecord) {
                if (contextPath == null && WasmContext.class.getCanonicalName().equals(graphPathRecord.klass().getCanonicalName())) {
                    contextPath = graphPathRecord.path();
                }
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
