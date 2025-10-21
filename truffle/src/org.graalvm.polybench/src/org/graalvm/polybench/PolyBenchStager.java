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

import org.graalvm.polybench.ast.Tree.Program;
import org.graalvm.polyglot.Context;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Coordinates the staging of a PolyBench benchmark. Builds an AST of the benchmark and then
 * generates the target language program from the AST.
 */
class PolyBenchStager {
    private final Config config;
    private final PolyBenchAstBuilder astBuilder;
    private final LanguageGenerator stager;

    PolyBenchStager(PolyBenchLauncher launcher) {
        this.config = launcher.getConfig();
        this.astBuilder = new PolyBenchAstBuilder(launcher);
        this.stager = LanguageGeneratorFactory.getLanguageGenerator(this.config.stagingLanguage);
    }

    public void execute(Context.Builder contextBuilder, boolean evalSourceOnly, int run) {
        if (!config.isSingleEngine()) {
            // If/when this becomes supported the idea of staged code reuse across runs should be
            // looked into.
            throw new IllegalArgumentException("Staging of multi-engine benchmark runs is not supported!");
        }
        if (config.stagingFilePath == null) {
            String msg = "Staging to '" + config.stagingLanguage + "' language requested but no destination file path specified! Please specify a file path using the '--stage-to-file' option!";
            throw new IllegalArgumentException(msg);
        }
        try {
            byte[] originalBenchmark = readOriginalBenchmarkSourceCode();
            Program programAst = astBuilder.build(originalBenchmark, contextBuilder, evalSourceOnly, run);
            byte[] stagedProgram = stager.generate(programAst);
            dumpProgram(config.stagingFilePath, stagedProgram);
            conditionallyLogStagedProgram(config.logStagedProgram);
            log("Staged " + this.getClass().getSimpleName() + ".runHarness method in " + config.stagingLanguage + " to '" + config.stagingFilePath + "'.");
            conditionallyRunStagedProgram(config.stagedProgramLauncher);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] readOriginalBenchmarkSourceCode() throws IOException {
        return java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(config.path));
    }

    private void conditionallyLogStagedProgram(boolean logStagedProgram) throws IOException {
        if (!logStagedProgram) {
            return;
        }
        String fileHeader = "================" + config.stagingFilePath + "================";
        log(fileHeader);
        try (BufferedReader reader = new BufferedReader(new FileReader(config.stagingFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log("| " + line);
            }
        }
        log("=".repeat(fileHeader.length()));
    }

    private void conditionallyRunStagedProgram(String stagedProgramLauncher) throws IOException, InterruptedException {
        if (stagedProgramLauncher == null) {
            return;
        }
        List<String> command = List.of(stagedProgramLauncher, config.stagingFilePath);
        ProcessBuilder processBuilder = new ProcessBuilder(command);

        log("Executing staged " + this.getClass().getSimpleName() + ".runHarness method using the command: " + command);
        Process process = processBuilder.start();
        // Capture stdout
        try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log(line);
            }
        }
        // Capture stderr
        try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.err.println(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Staged harness subprocess failed with exit code: " + exitCode);
        }
        log("Staged harness subprocess finished execution successfully!");
    }

    private static void dumpProgram(String filePath, byte[] program) throws IOException {
        try (FileOutputStream out = new FileOutputStream(filePath)) {
            out.write(program);
        }
    }

    private static void log(String msg) {
        System.out.println(msg);
    }
}
