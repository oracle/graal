/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.llvm;

import static com.oracle.svm.core.graal.llvm.objectfile.LLVMObjectFile.getLld;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.util.CompletionExecutor;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.SectionName;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.llvm.util.LLVMOptions;
import com.oracle.svm.core.graal.llvm.util.LLVMTargetSpecific;
import com.oracle.svm.hosted.image.LLVMToolchain;

public class LLVMToolchainUtils {
    public static void llvmOptimize(DebugContext debug, String outputPath, String inputPath, Path basePath, Function<String, String> outputPathFormat) {
        List<String> args = new ArrayList<>();
        List<String> passes = new ArrayList<>();
        if (LLVMOptions.BitcodeOptimizations.getValue()) {
            /*
             * This runs LLVM's bitcode optimizations in addition to the Graal optimizations.
             * Inlining has to be disabled in this case as the functions are already stored in the
             * image heap and inlining them would produce bogus runtime information for garbage
             * collection and exception handling. Starting with LLVM 16, the -disable-inlining flag
             * doesn't work anymore. But inlining is implicitly disabled by adding no-inline to all
             * bitcode functions.
             */
            passes.add("default<O2>");
        } else {
            /*
             * Mem2reg has to be run before rewriting statepoints as it promotes allocas, which are
             * not supported for statepoints.
             */
            passes.add("function(mem2reg)");
        }
        passes.add("rewrite-statepoints-for-gc");
        passes.add("always-inline");

        args.add("--passes=" + String.join(",", passes));

        args.add("-o");
        args.add(outputPath);
        args.add(inputPath);

        try {
            LLVMToolchain.runLLVMCommand("opt", basePath, args);
        } catch (LLVMToolchain.RunFailureException e) {
            debug.log("%s", e.getOutput());
            throw new GraalError("LLVM optimization failed for " + outputPathFormat.apply(inputPath) + ": " + e.getStatus() + "\nCommand: opt " + String.join(" ", args));
        }
    }

    public static void llvmCompile(DebugContext debug, String outputPath, String inputPath, Path basePath, Function<String, String> outputPathFormat) {
        List<String> args = new ArrayList<>();
        args.add("-relocation-model=pic");
        /*
         * Makes sure that unreachable instructions get emitted into the machine code. This prevents
         * a situation where a call is the last instruction of a function, resulting in its return
         * address being located in the next function, which causes trouble with runtime information
         * emission.
         */
        args.add("--trap-unreachable");
        args.add("-march=" + LLVMTargetSpecific.get().getLLVMArchName());
        args.addAll(LLVMTargetSpecific.get().getLLCAdditionalOptions());
        args.add("-O" + optimizationLevel());
        args.add("-filetype=obj");
        args.add("-o");
        args.add(outputPath);
        args.add(inputPath);

        try {
            LLVMToolchain.runLLVMCommand("llc", basePath, args);
        } catch (LLVMToolchain.RunFailureException e) {
            debug.log("%s", e.getOutput());
            throw new GraalError("LLVM compilation failed for " + outputPathFormat.apply(inputPath) + ": " + e.getStatus() + "\nCommand: llc " + String.join(" ", args));
        }
    }

    private static int optimizationLevel() {
        return switch (SubstrateOptions.optimizationLevel()) {
            case O0, BUILD_TIME, SIZE -> 0;
            case O1 -> 1;
            case O2 -> 2;
            case O3 -> 3;
        };
    }

    public static void llvmLink(DebugContext debug, String outputPath, List<String> inputPaths, Path basePath, Function<String, String> outputPathFormat) {
        List<String> args = new ArrayList<>();
        args.add("-o");
        args.add(outputPath);
        args.addAll(inputPaths);

        try {
            LLVMToolchain.runLLVMCommand("llvm-link", basePath, args);
        } catch (LLVMToolchain.RunFailureException e) {
            debug.log("%s", e.getOutput());
            throw new GraalError("LLVM linking failed into " + outputPathFormat.apply(outputPath) + ": " + e.getStatus());
        }
    }

    public static void nativeLink(DebugContext debug, String outputPath, List<String> inputPaths, Path basePath, Function<String, String> outputPathFormat) {
        List<String> cmd = new ArrayList<>();
        if (LLVMOptions.CustomLD.hasBeenSet()) {
            cmd.add(LLVMOptions.CustomLD.getValue());
        }
        cmd.add("-r");
        cmd.add("-o");
        cmd.add(outputPath);
        cmd.addAll(inputPaths);

        try {
            if (LLVMOptions.CustomLD.hasBeenSet()) {
                LLVMToolchain.runCommand(basePath, cmd);
            } else {
                LLVMToolchain.runLLVMCommand(getLld(), basePath, cmd);
            }
        } catch (LLVMToolchain.RunFailureException e) {
            debug.log("%s", e.getOutput());
            throw new GraalError("Native linking failed into " + outputPathFormat.apply(outputPath) + ": " + e.getStatus());
        }
    }

    public static void llvmCleanupStackMaps(DebugContext debug, String inputPath, Path basePath) {
        List<String> args = new ArrayList<>();
        args.add("--remove-section=" + SectionName.LLVM_STACKMAPS.getFormatDependentName(ObjectFile.getNativeFormat()));
        args.add(inputPath);

        try {
            LLVMToolchain.runLLVMCommand("llvm-objcopy", basePath, args);
        } catch (LLVMToolchain.RunFailureException e) {
            debug.log("%s", e.getOutput());
            throw new GraalError("Removing stack maps failed for " + inputPath + ": " + e.getStatus() + "\nCommand: llvm-objcopy " + String.join(" ", args));
        }
    }

    public static final class BatchExecutor {
        private CompletionExecutor executor;

        public BatchExecutor(DebugContext debug, BigBang bb) {
            this.executor = new CompletionExecutor(debug, bb);
            executor.init();
        }

        public void forEach(int num, IntFunction<CompletionExecutor.DebugContextRunnable> callback) {
            try {
                executor.start();
                for (int i = 0; i < num; ++i) {
                    executor.execute(callback.apply(i));
                }
                executor.complete();
                executor.init();
            } catch (InterruptedException e) {
                throw new GraalError(e);
            }
        }

        public <T> void forEach(List<T> list, Function<T, CompletionExecutor.DebugContextRunnable> callback) {
            try {
                executor.start();
                for (T elem : list) {
                    executor.execute(callback.apply(elem));
                }
                executor.complete();
                executor.init();
            } catch (InterruptedException e) {
                throw new GraalError(e);
            }
        }

        public CompletionExecutor getExecutor() {
            return executor;
        }
    }
}
