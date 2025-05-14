/*
 * Copyright (c) 2025, Oracle and/or its affiliates.
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.graalvm.nativeimage.ProcessProperties;

public abstract class AbstractToolchainWrapper {

    /**
     * The path of the LLVM distribution, relative to the toolchain root. This is set at
     * native-image build time.
     */
    private static final String RELATIVE_LLVM_PATH = System.getProperty("org.graalvm.llvm.relative.path");

    private static ToolchainWrapperConfig config;

    public static final ToolchainWrapperConfig getConfig() {
        return config;
    }

    public record ToolchainWrapperConfig(String toolName, Path toolchainPath, Path llvmPath) {
    }

    public interface Tool {

        void run(String[] args);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    private static Path getCurrentExecutablePath() {
        final String path = ProcessProperties.getExecutableName();
        return path == null ? null : Paths.get(path);
    }

    private static String getFile(Path path) {
        Path file = path.getFileName();
        return file != null ? file.toString() : null;
    }

    protected void run(String[] args) {
        Path executablePath = getCurrentExecutablePath();
        if (executablePath == null) {
            System.err.println("Error: Could not determine current executable path");
            System.exit(1);
        }

        // executablePath is <toolchainPath>/bin/tool
        Path toolchainPath = executablePath.getParent();
        if (toolchainPath != null) {
            toolchainPath = toolchainPath.getParent();
        }
        if (toolchainPath == null) {
            System.err.println("Error: Could not find toolchain path");
            System.exit(1);
        }
        Path llvmPath = toolchainPath.resolve(RELATIVE_LLVM_PATH).normalize();

        String programName;
        if (isWindows()) {
            // set by the exe_link_template.cmd wrapper script
            programName = System.getenv("GRAALVM_ARGUMENT_VECTOR_PROGRAM_NAME");
        } else {
            programName = ProcessProperties.getArgumentVectorProgramName();
        }

        String binPathName;
        if (programName == null) {
            binPathName = getFile(executablePath);
        } else {
            binPathName = getFile(Paths.get(programName));
        }

        if (binPathName == null) {
            System.err.println("Error: Could not figure out process name");
            System.exit(1);
        }

        String toolName = binPathName;
        if (isWindows()) {
            if (toolName.endsWith(".cmd") || toolName.endsWith(".exe")) {
                toolName = toolName.substring(0, toolName.length() - 4);
            }
        }

        for (String prefix : getStrippedPrefixes()) {
            if (toolName.startsWith(prefix)) {
                toolName = toolName.substring(prefix.length());
            }
        }

        assert config == null : "AbstractToolchainWrapper.run called twice?";
        config = new ToolchainWrapperConfig(toolName, toolchainPath, llvmPath);

        getTool(toolName).run(args);
    }

    protected Iterable<String> getStrippedPrefixes() {
        return Collections.emptyList();
    }

    protected abstract Tool getTool(String toolName);
}
