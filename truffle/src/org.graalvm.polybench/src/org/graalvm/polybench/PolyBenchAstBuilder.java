/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polybench;

import org.graalvm.polybench.ast.Decl;
import org.graalvm.polybench.ast.Expr;
import org.graalvm.polybench.ast.Operator;
import org.graalvm.polybench.ast.Stat;
import org.graalvm.polybench.ast.Tree.Program;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.logging.Handler;

/**
 * Builds the AST of a PolyBench benchmark.
 *
 * The AST is composed of: the PolyBenchLauncher.runHarness method, all methods and classes
 * necessary for the execution of the PolyBenchLauncher.runHarness method (the
 * PolyBenchLauncher.repeatIterations method and the required Metric and Summary classes), and the
 * original benchmark code (contained raw as a whole - it is not separated into tokens).
 */
public class PolyBenchAstBuilder {
    private final PolyBenchLauncher launcher;
    private Program.Builder programBuilder;
    private final Config config;

    public PolyBenchAstBuilder(PolyBenchLauncher launcher) {
        this.launcher = launcher;
        this.programBuilder = null;
        this.config = launcher.getConfig();
    }

    public Program build(byte[] originalBenchmark, Context.Builder contextBuilder, boolean evalSourceOnly, int run) throws IOException {
        initializeAst();
        // Make sure benchmark code is at the start of the program.
        // There are some benchmarks which start with statements that must be at the start of the
        // file.
        stageBenchmarkCode(originalBenchmark);
        stageRepeatIterationsBody();
        stageRunHarnessBody(contextBuilder, evalSourceOnly, run);
        stageMetric();
        stageSummary();
        return programBuilder.build();
    }

    private void initializeAst() {
        Decl.Subroutine.Builder mainBuilder = new Decl.Subroutine.Builder(null, "main", null);
        mainBuilder.append(Expr.FunctionCall.of(new Expr.Reference.Ident("runHarness")));
        this.programBuilder = new Program.Builder(null, new Decl.Main(mainBuilder.build()));
    }

    private void stageBenchmarkCode(byte[] originalBenchmark) {
        programBuilder.append(new Stat.Comment("==============================================================="));
        programBuilder.append(new Stat.Comment("Benchmark code copied from '" + config.path + "' starts here."));
        programBuilder.append(new Stat.Comment("==============================================================="));
        programBuilder.append(new Decl.Raw(originalBenchmark));
        programBuilder.append(new Decl.Raw("\n".getBytes()));
        programBuilder.append(new Stat.Comment("==============================================================="));
        programBuilder.append(new Stat.Comment("Benchmark code copied from '" + config.path + "' ends here."));
        programBuilder.append(new Stat.Comment("==============================================================="));
    }

    private void stageRepeatIterationsBody() {
        Stat thenBranch;
        Stat elseBranch;
        Stat.Block.Builder builder = new Stat.Block.Builder(null);
        builder.append(Decl.Variable.of("iterationResults"));
        builder.append(new Stat.Assign(new Expr.Reference.Ident("iterationResults"), new Expr.Atom.EmptyList()));
        builder.append(Decl.Variable.of("i"));
        try (Stat.For.Builder forBuilder = new Stat.For.Builder(builder,
                        new Stat.Assign(new Expr.Reference.Ident("i"), new Expr.Atom.Int(0)),
                        new Expr.BinaryOp(new Expr.Reference.Ident("i"), new Expr.Reference.Ident("iterations"), Operator.LESS_THAN),
                        new Stat.Assign(new Expr.Reference.Ident("i"), new Expr.BinaryOp(new Expr.Reference.Ident("i"), new Expr.Atom.Int(1), Operator.PLUS)))) {
            // Invoke: beforeIteration -> run -> afterIteration
            forBuilder.append(new Expr.FunctionCall(new Expr.Reference.CompoundReference(new Expr.Reference.Ident("configMetric"),
                            new Expr.Reference.Ident("beforeIteration")), new Expr[]{new Expr.Reference.Ident("warmup"), new Expr.Reference.Ident("i")}));
            forBuilder.append(new Expr.FunctionCall(new Expr.Reference.Ident("run"), null));
            forBuilder.append(new Expr.FunctionCall(new Expr.Reference.CompoundReference(new Expr.Reference.Ident("configMetric"),
                            new Expr.Reference.Ident("afterIteration")), new Expr[]{new Expr.Reference.Ident("warmup"), new Expr.Reference.Ident("i")}));
            // Get iteration value
            forBuilder.append(Decl.Variable.of("iterationValue"));
            forBuilder.append(new Stat.Assign(new Expr.Reference.Ident("iterationValue"),
                            Expr.FunctionCall.of(new Expr.Reference.CompoundReference(new Expr.Reference.Ident("configMetric"), new Expr.Reference.Ident("reportAfterIteration")))));
            // if (iterationValue != null)
            try (Stat.Block.Builder thenBranchBuilder = new Stat.Block.Builder(null)) {
                // Log iteration value
                thenBranchBuilder.append(new Expr.FunctionCall.LogCall(new Expr.StringConcatenation(new Expr[]{
                                new Expr.Atom.String("["),
                                new Expr.Reference.Ident("name"),
                                new Expr.Atom.String("] iteration "),
                                new Expr.Reference.Ident("i"),
                                new Expr.Atom.String(": "),
                                new Expr.FunctionCall(new Expr.Reference.Ident("roundToTwoDecimals"), new Expr[]{new Expr.Reference.Ident("iterationValue")}),
                                new Expr.Atom.String(" "),
                                Expr.FunctionCall.of(new Expr.Reference.CompoundReference(new Expr.Reference.Ident("configMetric"), new Expr.Reference.Ident("unit")))
                })));
                // Store iteration value
                thenBranchBuilder.append(new Expr.FunctionCall.AppendToListCall(new Expr.Reference.Ident("iterationResults"), new Expr.Reference.Ident("iterationValue")));
                thenBranch = thenBranchBuilder.build();
            }
            forBuilder.append(new Stat.If(new Expr.BinaryOp(new Expr.Reference.Ident("iterationValue"), new Expr.Atom.Null(), Operator.NOT_EQUALS), thenBranch, null));
        }
        builder.append(new Expr.FunctionCall.LogCall(new Expr.Atom.String("------")));
        // Get the stage name
        builder.append(Decl.Variable.of("stageName"));
        try (Stat.Block.Builder thenBranchBuilder = new Stat.Block.Builder(null)) {
            thenBranchBuilder.append(new Stat.Assign(new Expr.Reference.Ident("stageName"), new Expr.Atom.String("warmup")));
            thenBranch = thenBranchBuilder.build();
        }
        try (Stat.Block.Builder elseBranchBuilder = new Stat.Block.Builder(null)) {
            elseBranchBuilder.append(new Stat.Assign(new Expr.Reference.Ident("stageName"), new Expr.Atom.String("run")));
            elseBranch = elseBranchBuilder.build();
        }
        builder.append(new Stat.If(new Expr.Reference.Ident("warmup"), thenBranch, elseBranch));
        // Get the average value
        builder.append(Decl.Variable.of("avgValue"));
        builder.append(new Stat.Assign(new Expr.Reference.Ident("avgValue"),
                        Expr.FunctionCall.of(new Expr.Reference.CompoundReference(new Expr.Reference.Ident("configMetric"), new Expr.Reference.Ident("reportAfterAll")))));
        // if (avgValue != null)
        try (Stat.Block.Builder thenBranchBuilder = new Stat.Block.Builder(null)) {
            // Log average value
            thenBranchBuilder.append(new Expr.FunctionCall.LogCall(new Expr.StringConcatenation(new Expr[]{
                            new Expr.Atom.String("["),
                            new Expr.Reference.Ident("name"),
                            new Expr.Atom.String("] after "),
                            new Expr.Reference.Ident("stageName"),
                            new Expr.Atom.String(": "),
                            new Expr.FunctionCall(new Expr.Reference.Ident("roundToTwoDecimals"), new Expr[]{new Expr.Reference.Ident("avgValue")}),
                            new Expr.Atom.String(" "),
                            new Expr.FunctionCall(new Expr.Reference.CompoundReference(new Expr.Reference.Ident("configMetric"), new Expr.Reference.Ident("unit")), null)
            })));
            thenBranch = thenBranchBuilder.build();
        }
        builder.append(new Stat.If(new Expr.BinaryOp(new Expr.Reference.Ident("avgValue"), new Expr.Atom.Null(), Operator.NOT_EQUALS), thenBranch, null));
        // Get the summary aggregate
        builder.append(Decl.Variable.of("summaryAggregate"));
        try (Stat.Block.Builder thenBranchBuilder = new Stat.Block.Builder(null)) {
            thenBranchBuilder.append(new Stat.Assign(new Expr.Reference.Ident("summaryAggregate"),
                            new Expr.FunctionCall(new Expr.Reference.CompoundReference(new Expr.Reference.Ident("configSummary"), new Expr.Reference.Ident("postprocess")),
                                            new Expr[]{new Expr.Reference.Ident("iterationResults")})));
            thenBranch = thenBranchBuilder.build();
        }
        builder.append(new Stat.If(new Expr.BinaryOp(new Expr.Reference.Ident("configSummary"), new Expr.Atom.Null(), Operator.NOT_EQUALS), thenBranch, null));
        // if (summaryAggregate != null)
        try (Stat.Block.Builder thenBranchBuilder = new Stat.Block.Builder(null)) {
            // Log summary aggregate
            thenBranchBuilder.append(new Expr.FunctionCall.LogCall(new Expr.StringConcatenation(new Expr[]{
                            new Expr.Atom.String("["),
                            new Expr.Reference.Ident("name"),
                            new Expr.Atom.String("] "),
                            new Expr.Reference.Ident("stageName"),
                            new Expr.Atom.String(" aggregate summary: "),
                            new Expr.FunctionCall(new Expr.Reference.Ident("roundToTwoDecimals"), new Expr[]{new Expr.Reference.Ident("summaryAggregate")}),
                            new Expr.Atom.String(" "),
                            new Expr.FunctionCall(new Expr.Reference.CompoundReference(new Expr.Reference.Ident("configMetric"), new Expr.Reference.Ident("unit")), null)
            })));
            thenBranch = thenBranchBuilder.build();
        }
        builder.append(new Stat.If(new Expr.BinaryOp(new Expr.Reference.Ident("summaryAggregate"), new Expr.Atom.Null(), Operator.NOT_EQUALS), thenBranch, null));
        // Declare the function and append it to the program
        Decl.Subroutine repeatIterations = new Decl.Subroutine("repeatIterations", Decl.Variable.list("name", "warmup", "iterations"), builder.build());
        programBuilder.append(repeatIterations);
    }

    private void stageRunHarnessBody(Context.Builder contextBuilder, boolean evalSourceOnly, int run) throws InvalidObjectException {
        Stat thenBranch;
        Stat.Block.Builder builder = new Stat.Block.Builder(null);
        builder.append(new Expr.FunctionCall.LogCall(new Expr.StringConcatenation(new Expr[]{
                        new Expr.Atom.String("::: Starting "),
                        new Expr.Atom.String(config.path),
                        new Expr.Atom.String(" :::")
        })));
        builder.append(new Expr.FunctionCall.LogCall(new Expr.Atom.String(config.toString())));
        builder.append(new Expr.FunctionCall.LogCall(new Expr.Atom.String("")));

        contextBuilder.options(config.metric.getEngineOptions(config));
        Handler handler = config.metric.getLogHandler();
        if (handler != null) {
            contextBuilder.logHandler(handler);
        }

        try (Context context = contextBuilder.build()) {
            builder.append(new Expr.FunctionCall.LogCall(new Expr.Atom.String("::: Initializing :::")));

            PolyBenchLauncher.EvalResult evalResult;
            context.enter();
            try {
                evalResult = launcher.evalSource(context);
            } finally {
                context.leave();
            }

            builder.append(new Expr.FunctionCall.LogCall(new Expr.Atom.String("run:        " + run)));
            builder.append(new Expr.FunctionCall.LogCall(new Expr.Atom.String("language:   " + evalResult.languageId)));
            builder.append(new Expr.FunctionCall.LogCall(new Expr.Atom.String("type:       " + (evalResult.isBinarySource ? "binary" : "source code"))));
            builder.append(new Expr.FunctionCall.LogCall(new Expr.Atom.String("length:     " + evalResult.sourceLength + (evalResult.isBinarySource ? " bytes" : " characters"))));
            builder.append(new Expr.FunctionCall.LogCall(new Expr.Atom.String("")));

            builder.append(new Expr.FunctionCall.LogCall(new Expr.Atom.String("::: Bench specific options :::")));
            if (evalResult.value instanceof Value value) {
                if (evalResult.languageId.equals("wasm")) {
                    value = value.getMember("exports");
                }
                config.parseBenchSpecificDefaults(value);
                builder.append(new Expr.FunctionCall(new Expr.Reference.CompoundReference(new Expr.Reference.Ident("configMetric"), new Expr.Reference.Ident("parseBenchSpecificOptions")), null));
            }
            builder.append(new Expr.FunctionCall.LogCall(new Expr.Atom.String(config.toString())));

            builder.append(new Expr.FunctionCall.LogCall(new Expr.Atom.String("Initialization completed.")));
            builder.append(new Expr.FunctionCall.LogCall(new Expr.Atom.String("")));

            if (evalSourceOnly) {
                builder.append(new Expr.FunctionCall.LogCall(new Expr.Atom.String("::: Iterations skipped :::")));
            } else {
                builder.append(new Expr.FunctionCall.LogCall(new Expr.Atom.String("::: Running warmup :::")));
                builder.append(Decl.Variable.of("warmupIterationsLocal"));
                builder.append(new Stat.Assign(new Expr.Reference.Ident("warmupIterationsLocal"), new Expr.Atom.Int(Config.DEFAULT_WARMUP)));
                try (Stat.Block.Builder thenBranchBuilder = new Stat.Block.Builder(null)) {
                    // Get number of warmup iterations from warmupIterations function
                    thenBranchBuilder.append(new Stat.Assign(new Expr.Reference.Ident("warmupIterationsLocal"), new Expr.FunctionCall(new Expr.Reference.Ident("warmupIterations"), null)));
                    thenBranch = thenBranchBuilder.build();
                }
                builder.append(new Stat.If(new Expr.FunctionCall(new Expr.Reference.Ident("checkIfFunctionExists"), new Expr[]{new Expr.Atom.String("warmupIterations")}), thenBranch, null));
                builder.append(new Expr.FunctionCall(new Expr.Reference.Ident("repeatIterations"), new Expr[]{
                                new Expr.Atom.String(evalResult.sourceName),
                                new Expr.Atom.Bool(true),
                                new Expr.Reference.Ident("warmupIterationsLocal")
                }));
                builder.append(new Expr.FunctionCall.LogCall(new Expr.Atom.String("")));

                // Split up the "::: Running :::" string so the log statement in the code does not
                // trigger mx rule if staged program is also logged to stdout
                builder.append(new Expr.FunctionCall.LogCall(new Expr.StringConcatenation(new Expr[]{new Expr.Atom.String("::: Runn"), new Expr.Atom.String("ing :::")})));
                builder.append(new Expr.FunctionCall(new Expr.Reference.CompoundReference(new Expr.Reference.Ident("configMetric"), new Expr.Reference.Ident("reset")), null));
                builder.append(Decl.Variable.of("iterationsLocal"));
                builder.append(new Stat.Assign(new Expr.Reference.Ident("iterationsLocal"), new Expr.Atom.Int(Config.DEFAULT_ITERATIONS)));
                try (Stat.Block.Builder thenBranchBuilder = new Stat.Block.Builder(null)) {
                    // Get number of iterations from iterations function
                    thenBranchBuilder.append(new Stat.Assign(new Expr.Reference.Ident("iterationsLocal"), new Expr.FunctionCall(new Expr.Reference.Ident("iterations"), null)));
                    thenBranch = thenBranchBuilder.build();
                }
                builder.append(new Stat.If(new Expr.FunctionCall(new Expr.Reference.Ident("checkIfFunctionExists"), new Expr[]{new Expr.Atom.String("iterations")}), thenBranch, null));
                builder.append(new Expr.FunctionCall(new Expr.Reference.Ident("repeatIterations"), new Expr[]{
                                new Expr.Atom.String(evalResult.sourceName),
                                new Expr.Atom.Bool(false),
                                new Expr.Reference.Ident("iterationsLocal")
                }));
                builder.append(new Expr.FunctionCall.LogCall(new Expr.Atom.String("")));
            }
        }

        // Declare the function and append it to the program
        Decl.Subroutine runHarness = new Decl.Subroutine("runHarness", null, builder.build());
        programBuilder.append(runHarness);
    }

    private void stageMetric() {
        if (config.metric instanceof StageableClass stageableMetric) {
            boolean metricStaged = stageableMetric.stageClass(programBuilder);
            if (!metricStaged) {
                String msg = "Cannot stage " + config.metric.getClass().getSimpleName() + " metric in " + config.stagingLanguage + "!" +
                                " Please select a metric stageable in " + config.stagingLanguage + "!";
                throw new UnsupportedOperationException(msg);
            }
            boolean constructorCallStaged = stageableMetric.stageSingletonInitialization(programBuilder, "configMetric");
            if (!constructorCallStaged) {
                String msg = "Cannot stage " + config.metric.getClass().getSimpleName() + " constructor call in " + config.stagingLanguage + "!" +
                                " Please select a metric with a stageable constructor call in " + config.stagingLanguage + "!";
                throw new UnsupportedOperationException(msg);
            }
        } else {
            throw new UnsupportedOperationException("Cannot stage " + config.metric.getClass().getSimpleName() + " metric! Please select a stageable metric!");
        }
    }

    private void stageSummary() {
        if (config.summary instanceof StageableClass stageableSummary) {
            boolean summaryStaged = stageableSummary.stageClass(programBuilder);
            if (!summaryStaged) {
                String msg = "Cannot stage " + config.summary.getClass().getSimpleName() + " summary in " + config.stagingLanguage + "!" +
                                " Please select a summary stageable in " + config.stagingLanguage + "!";
                throw new UnsupportedOperationException(msg);
            }
            boolean constructorCallStaged = stageableSummary.stageSingletonInitialization(programBuilder, "configSummary");
            if (!constructorCallStaged) {
                String msg = "Cannot stage " + config.summary.getClass().getSimpleName() + " constructor call in " + config.stagingLanguage + "!" +
                                " Please select a summary with a stageable constructor call in " + config.stagingLanguage + "!";
                throw new UnsupportedOperationException(msg);
            }
        } else {
            throw new UnsupportedOperationException("Cannot stage " + config.summary.getClass().getSimpleName() + " summary! Please select a stageable summary!");
        }
    }
}
