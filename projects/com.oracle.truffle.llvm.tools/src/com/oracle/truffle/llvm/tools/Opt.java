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
import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.llvm.tools.Opt.OptOptions.Pass;
import com.oracle.truffle.llvm.tools.util.ProcessUtil;

public class Opt {

    public static final class OptOptions {

        private final List<Pass> passes = new ArrayList<>();

        private OptOptions() {
        }

        public enum Pass {

            MEM_TO_REG("mem2reg"),
            SIMPLIFY_CFG("simplifycfg"),
            BASIC_BLOCK_VECTORIZE("bb-vectorize"),
            INST_COMBINE("instcombine"),
            FUNC_ATTRS("functionattrs"),
            JUMP_THREADING("jump-threading"),
            SCALAR_REPLACEMENT_AGGREGATES("scalarrepl"),
            ALWAYS_INLINE("always-inline");

            private final String option;

            Pass(String option) {
                this.option = option;
            }

            public String getOption() {
                return "-" + option;
            }

        }

        public static OptOptions builder() {
            return new OptOptions();
        }

        public OptOptions pass(Pass pass) {
            getPasses().add(pass);
            return this;
        }

        public List<Pass> getPasses() {
            return passes;
        }

    }

    public static void optimizeBitcodeFile(File bitCodeFile, File destinationFile, OptOptions options) {
        String clangCompileCommand = LLVMToolPaths.LLVM_OPT + " -S " + getStringPasses(options.getPasses()) + " " + bitCodeFile.getAbsolutePath() + " -o " + destinationFile;
        ProcessUtil.executeNativeCommandZeroReturn(clangCompileCommand);
    }

    private static String getStringPasses(List<Pass> passes) {
        StringBuilder sb = new StringBuilder();
        for (Pass pass : passes) {
            sb.append(pass.getOption());
            sb.append(" ");
        }
        return sb.toString();
    }

}
