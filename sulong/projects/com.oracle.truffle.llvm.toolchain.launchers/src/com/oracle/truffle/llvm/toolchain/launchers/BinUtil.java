/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.toolchain.launchers;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.truffle.llvm.toolchain.launchers.common.Driver;

public final class BinUtil {
    public static void main(String[] args) {
        String processName = getProcessName();

        if (processName == null) {
            System.err.println("Error: Could not figure out process name");
            System.exit(1);
        }

        if (!processName.startsWith("llvm-")) {
            processName = "llvm-" + processName;
        }

        final Driver driver = new Driver();
        final String targetName = driver.getLLVMBinDir().resolve(processName).toString();

        ArrayList<String> utilArgs = new ArrayList<>(args.length + 1);
        utilArgs.add(targetName);
        if (args.length > 0) {
            utilArgs.addAll(Arrays.asList(args));
        }
        ProcessBuilder pb = new ProcessBuilder(utilArgs).redirectError(ProcessBuilder.Redirect.INHERIT).redirectInput(ProcessBuilder.Redirect.INHERIT).redirectOutput(ProcessBuilder.Redirect.INHERIT);
        Process p = null;
        try {
            p = pb.start();
            p.waitFor();
            System.exit(p.exitValue());
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            driver.printMissingToolMessage();
            System.exit(1);
        } catch (InterruptedException e) {
            if (p != null) {
                p.destroyForcibly();
            }
            System.err.println("Error: Subprocess interrupted: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String getProcessName() {
        String binPathName = System.getProperty("org.graalvm.launcher.executablename");

        if (binPathName == null) {
            if (ProcessProperties.getArgumentVectorBlockSize() <= 0) {
                return null;
            }
            binPathName = ProcessProperties.getArgumentVectorProgramName();

            if (binPathName == null) {
                return null;
            }
        }

        Path p = Paths.get(binPathName);
        Path f = p.getFileName();

        if (f == null) {
            return null;
        }

        return f.toString();
    }
}
