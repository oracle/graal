/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates.
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
import java.util.Optional;

import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.truffle.llvm.toolchain.launchers.AbstractToolchainWrapper.ToolchainWrapperConfig;
import com.oracle.truffle.llvm.toolchain.launchers.common.Driver;

public final class BinUtil {

    public static void main(String[] args) {
        ToolchainWrapperConfig config = AbstractToolchainWrapper.getConfig();

        String toolName;
        if (config != null) {
            toolName = config.toolName();
        } else {
            // this is the boostrap toolchain...
            toolName = System.getProperty("org.graalvm.launcher.executablename");

            // ... or a legacy GraalVM
            if (toolName == null) {
                toolName = ProcessProperties.getArgumentVectorProgramName();
            }

            Path toolPath = Paths.get(toolName).getFileName();
            if (toolPath != null) {
                toolName = toolPath.toString();
            }
        }

        if (!toolName.startsWith("llvm-")) {
            toolName = "llvm-" + toolName;
        }

        final Path toolPath = Driver.getLLVMBinDir().resolve(toolName);
        runTool(toolPath, args);
    }

    public static void runTool(Path toolPath, String[] args) {
        ArrayList<String> utilArgs = new ArrayList<>(args.length + 1);
        utilArgs.add(toolPath.toString());
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
            Driver.printMissingToolMessage(Optional.ofNullable(toolPath.getParent()).map(Path::getParent).map(Path::toString).orElse("<invalid>"));
            System.exit(1);
        } catch (InterruptedException e) {
            if (p != null) {
                p.destroyForcibly();
            }
            System.err.println("Error: Subprocess interrupted: " + e.getMessage());
            System.exit(1);
        }
    }

}
