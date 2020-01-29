/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c.codegen;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.util.FileUtils;

public abstract class CCompilerInvoker {

    public final Path tempDirectory;

    public static CCompilerInvoker create(Path tempDirectory) {
        if (Platform.includedIn(Platform.WINDOWS.class)) {
            return new WindowsCCompilerInvoker(tempDirectory);
        } else if (Platform.includedIn(Platform.LINUX.class)) {
            return new LinuxCCompilerInvoker(tempDirectory);
        } else if (Platform.includedIn(Platform.DARWIN.class)) {
            return new DarwinCCompilerInvoker(tempDirectory);
        } else {
            throw VMError.shouldNotReachHere("No CCompilerInvoker for " + ImageSingletons.lookup(Platform.class).getClass().getName());
        }
    }

    private static class WindowsCCompilerInvoker extends CCompilerInvoker {
        WindowsCCompilerInvoker(Path tempDirectory) {
            super(tempDirectory);
        }

        @Override
        public String asExecutableName(String basename) {
            String suffix = ".exe";
            if (basename.endsWith(suffix)) {
                return basename;
            }
            return basename + suffix;
        }

        @Override
        protected String getDefaultCompiler() {
            return "cl";
        }

        @Override
        protected void addTarget(List<String> command, Path target) {
            command.add("/Fe" + target.toString());
        }

        @Override
        protected InputStream getCompilerErrorStream(Process compilingProcess) {
            return compilingProcess.getInputStream();
        }
    }

    private static class LinuxCCompilerInvoker extends CCompilerInvoker {
        LinuxCCompilerInvoker(Path tempDirectory) {
            super(tempDirectory);
        }

        @Override
        protected String getDefaultCompiler() {
            return "gcc";
        }
    }

    private static class DarwinCCompilerInvoker extends CCompilerInvoker {
        DarwinCCompilerInvoker(Path tempDirectory) {
            super(tempDirectory);
        }

        @Override
        protected String getDefaultCompiler() {
            return "cc";
        }
    }

    public CCompilerInvoker(Path tempDirectory) {
        this.tempDirectory = tempDirectory;
    }

    protected InputStream getCompilerErrorStream(Process compilingProcess) {
        return compilingProcess.getErrorStream();
    }

    public interface CompilerErrorHandler {
        void handle(ProcessBuilder currentRun, Path source, String line);
    }

    public void compileAndParseError(List<String> options, Path source, Path target, CompilerErrorHandler handler) {
        try {
            List<String> command = createCompilerCommand(options, target.normalize(), source.normalize());
            ProcessBuilder pb = new ProcessBuilder().command(command).directory(tempDirectory.toFile());
            Process compilingProcess = pb.start();

            List<String> lines;
            try (InputStream compilerErrors = getCompilerErrorStream(compilingProcess)) {
                lines = FileUtils.readAllLines(compilerErrors);
            }
            boolean errorReported = false;
            for (String line : lines) {
                if (detectError(line)) {
                    if (handler != null) {
                        handler.handle(pb, source, line);
                    }
                    errorReported = true;
                }
            }

            int status = compilingProcess.waitFor();
            if (status != 0 && !errorReported) {
                if (handler != null) {
                    handler.handle(pb, source, lines.toString());
                }
            }
            compilingProcess.destroy();

        } catch (InterruptedException ex) {
            throw new InterruptImageBuilding();
        } catch (IOException ex) {
            throw UserError.abort(ex, "Unable to compile C-ABI query code. Make sure native software development toolchain is installed on your system.");
        }
    }

    protected boolean detectError(String line) {
        return line.contains(": error:") || line.contains(": fatal error:");
    }

    public static Optional<Path> lookupSearchPath(String name) {
        return Arrays.stream(System.getenv("PATH").split(File.pathSeparator))
                        .map(entry -> Paths.get(entry, name))
                        .filter(Files::isExecutable)
                        .findFirst();
    }

    public Path getCCompilerPath() {
        Path compilerPath;
        String userDefinedPath = SubstrateOptions.CCompilerPath.getValue();
        if (userDefinedPath != null) {
            compilerPath = Paths.get(userDefinedPath);
        } else {
            String executableName = asExecutableName(getDefaultCompiler());
            Optional<Path> optCompilerPath = lookupSearchPath(executableName);
            if (optCompilerPath.isPresent()) {
                compilerPath = optCompilerPath.get();
            } else {
                throw UserError.abort("Default native-compiler executable '" + executableName + "' not found via environment variable PATH");
            }
        }
        if (Files.isDirectory(compilerPath) || !Files.isExecutable(compilerPath)) {
            String msgSubject;
            if (userDefinedPath != null) {
                msgSubject = SubstrateOptionsParser.commandArgument(SubstrateOptions.CCompilerPath, userDefinedPath);
            } else {
                msgSubject = "Default native-compiler '" + compilerPath + "'";
            }
            throw UserError.abort(msgSubject + " does not specify a path to an executable.");
        }
        return compilerPath;
    }

    protected abstract String getDefaultCompiler();

    public String asExecutableName(String basename) {
        return basename;
    }

    public List<String> createCompilerCommand(List<String> options, Path target, Path... input) {
        List<String> command = new ArrayList<>();

        command.add(getCCompilerPath().normalize().toString());
        command.addAll(Arrays.asList(SubstrateOptions.CCompilerOption.getValue()));
        command.addAll(options);

        if (target != null) {
            addTarget(command, target);
        }
        for (Path elem : input) {
            command.add(elem.toString());
        }

        LibCBase currentLibc = ImageSingletons.lookup(LibCBase.class);
        command.addAll(currentLibc.getCCompilerOptions());
        return command;
    }

    protected void addTarget(List<String> command, Path target) {
        command.add("-o");
        command.add(target.toString());
    }
}
