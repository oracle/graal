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

import com.oracle.truffle.llvm.tools.util.ProcessUtil;

public final class GCC extends CompilerBase {

    private GCC() {
    }

    public static void compileObjectToMachineCode(File objectFile, File executable) {
        String linkCommand = "gcc-4.6 " + objectFile.getAbsolutePath() + " -o " + executable.getAbsolutePath() + " -lm -lgfortran -lgmp";
        ProcessUtil.executeNativeCommandZeroReturn(linkCommand);
        executable.setExecutable(true);
    }

    public static void compileToLLVMIR(File toBeCompiled, File destinationFile) {
        String tool;
        if (ProgrammingLanguage.C.isFile(toBeCompiled)) {
            tool = "gcc-4.6 -std=gnu99";
        } else if (ProgrammingLanguage.FORTRAN.isFile(toBeCompiled)) {
            tool = "gfortran-4.6";
        } else if (ProgrammingLanguage.C_PLUS_PLUS.isFile(toBeCompiled)) {
            tool = "g++-4.6";
        } else {
            throw new AssertionError(toBeCompiled);
        }
        String[] command = new String[]{tool, "-S", dragonEggOption(), "-fplugin-arg-dragonegg-emit-ir", "-o " + destinationFile, toBeCompiled.getAbsolutePath()};
        ProcessUtil.executeNativeCommandZeroReturn(command);
    }

    private static String dragonEggOption() {
        return "-fplugin=" + LLVMToolPaths.LLVM_DRAGONEGG;
    }

    public static ProgrammingLanguage[] getSupportedLanguages() {
        return new ProgrammingLanguage[]{ProgrammingLanguage.C, ProgrammingLanguage.C_PLUS_PLUS, ProgrammingLanguage.FORTRAN};
    }
}
