/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.tools;

import java.io.File;

import com.oracle.truffle.llvm.runtime.options.LLVMBaseOptionFacade;
import com.oracle.truffle.llvm.tools.util.PathUtil;
import com.oracle.truffle.llvm.tools.util.ProcessUtil;

public class Clang extends CompilerBase {

    public static final class ClangOptions {

        public enum OptimizationLevel {
            NONE,
            O1,
            O2,
            O3;
        }

        private OptimizationLevel optimizationLevel;

        private ClangOptions() {
            optimizationLevel = OptimizationLevel.NONE;
        }

        public static ClangOptions builder() {
            return new ClangOptions();
        }

        public ClangOptions optimizationLevel(OptimizationLevel optLevel) {
            this.optimizationLevel = optLevel;
            return this;
        }

        public OptimizationLevel getOptimizationLevel() {
            return optimizationLevel;
        }

    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new AssertionError("please call with one file path as input argument!");
        }
        compileToLLVMIR(new File(args[0]), new File("test.ll"), ClangOptions.builder());
    }

    public static void compileToLLVMIR(File path, File destinationFile, ClangOptions options) {
        String fileExtension = PathUtil.getExtension(path.getName());
        File tool;
        if (ProgrammingLanguage.C.isFile(path)) {
            tool = LLVMToolPaths.LLVM_CLANG;
        } else if (ProgrammingLanguage.C_PLUS_PLUS.isFile(path)) {
            tool = LLVMToolPaths.LLVM_CLANGPlusPlus;
        } else if (ProgrammingLanguage.OBJECTIVE_C.isFile(path)) {
            tool = LLVMToolPaths.LLVM_CLANG;
        } else {
            throw new IllegalArgumentException(fileExtension);
        }
        String[] command = new String[]{tool.getAbsolutePath(), "-I " + LLVMBaseOptionFacade.getProjectRoot() + "/../include", emitLLVMIRTo(destinationFile), optimizationLevel(options),
                        path.getAbsolutePath()};
        ProcessUtil.executeNativeCommandZeroReturn(command);
    }

    private static String optimizationLevel(ClangOptions options) {
        switch (options.getOptimizationLevel()) {
            case NONE:
                return "";
            case O1:
                return "-O1";
            case O2:
                return "-O2";
            case O3:
                return "-O3";
            default:
                throw new AssertionError();
        }
    }

    public static ProgrammingLanguage[] getSupportedLanguages() {
        return new ProgrammingLanguage[]{ProgrammingLanguage.C, ProgrammingLanguage.C_PLUS_PLUS, ProgrammingLanguage.OBJECTIVE_C};
    }

}
