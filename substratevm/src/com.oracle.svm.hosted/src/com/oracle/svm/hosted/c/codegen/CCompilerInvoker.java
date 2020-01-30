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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.regex.Pattern;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.util.FileUtils;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;

public abstract class CCompilerInvoker {

    public final Path tempDirectory;

    public static CCompilerInvoker create(Path tempDirectory) {
        OS hostOS = OS.getCurrent();
        switch (hostOS) {
            case LINUX:
                return new LinuxCCompilerInvoker(tempDirectory);
            case DARWIN:
                return new DarwinCCompilerInvoker(tempDirectory);
            case WINDOWS:
                return new WindowsCCompilerInvoker(tempDirectory);
            default:
                throw UserError.abort("No CCompilerInvoker for operating system " + hostOS.name());
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
        protected List<String> addTarget(Path target) {
            return Arrays.asList("/Fe" + target.toString());
        }

        @Override
        protected InputStream getCompilerErrorStream(Process compilingProcess) {
            return compilingProcess.getInputStream();
        }

        @Override
        protected List<String> getVersionInfoOptions() {
            return Collections.emptyList();
        }

        @Override
        protected CompilerInfo createCompilerInfo(Scanner scanner) {
            try {
                /* For cl.exe the first line holds all necessary information */
                scanner.findInLine(Pattern.quote("Microsoft (R) C/C++ Optimizing Compiler Version "));
                scanner.useDelimiter(Pattern.quote("."));
                int major = scanner.nextInt();
                int minor = scanner.nextInt();
                scanner.reset(); /* back to default delimiters */
                scanner.findInLine(" for ");
                Class<? extends Architecture> arch;
                switch (scanner.next()) {
                    case "x64":
                        arch = AMD64.class;
                        break;
                    default:
                        arch = null;
                }
                return new CompilerInfo("Microsoft", "C/C++ Optimizing Compiler", major, minor, arch);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public void verifyCompiler() {
            CompilerInfo compilerInfo = getCCompilerInfo();
            if (compilerInfo == null) {
                UserError.abort("Unable to detect supported Windows native software development toolchain.");
            }
            if (JavaVersionUtil.JAVA_SPEC >= 11) {
                if (compilerInfo.versionMajor < 19) {
                    UserError.abort("Java " + JavaVersionUtil.JAVA_SPEC +
                                    " native-image building on Windows requires Visual Studio 2015 version 14.0 or later (C/C++ Optimizing Compiler Version 19.* or later)");
                }
            } else {
                VMError.guarantee(JavaVersionUtil.JAVA_SPEC == 8, "Native-image building is only supported for Java 8 and Java 11 or later");
                if (compilerInfo.versionMajor != 16 || compilerInfo.versionMinor != 0) {
                    UserError.abort("Java 8 native-image building on Windows requires Microsoft Windows SDK 7.1");
                }
            }
            if (compilerInfo.target != AMD64.class) {
                UserError.abort("Native-image building on Windows currently only supported for target architecture: " + AMD64.class.getSimpleName());
            }
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
        void handle(ProcessBuilder current, Path source, String line);
    }

    public static final class CompilerInfo {
        public final String name;
        public final String vendor;
        public final int versionMajor;
        public final int versionMinor;
        public final Class<? extends Architecture> target;

        public CompilerInfo(String vendor, String name, int versionMajor, int versionMinor, Class<? extends Architecture> target) {
            this.name = name;
            this.vendor = vendor;
            this.versionMajor = versionMajor;
            this.versionMinor = versionMinor;
            this.target = target;
        }
    }

    public void verifyCompiler() {
        /* Currently verification is only implemented for Windows. */
    }

    public CompilerInfo getCCompilerInfo() {
        List<String> compilerCommand = createCompilerCommand(getVersionInfoOptions(), null);
        ProcessBuilder pb = new ProcessBuilder()
                        .command(compilerCommand)
                        .directory(tempDirectory.toFile())
                        .redirectErrorStream(true);
        CompilerInfo compilerInfo = null;
        Process process = null;
        try {
            process = pb.start();
            try (Scanner scanner = new Scanner(process.getInputStream())) {
                compilerInfo = createCompilerInfo(scanner);
            }
            process.waitFor();
        } catch (InterruptedException ex) {
            throw new InterruptImageBuilding();
        } catch (IOException e) {
            UserError.abort(e, "Collecting native-compiler info with '" + String.join(" ", pb.command()) + "' failed");
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return compilerInfo;
    }

    protected List<String> getVersionInfoOptions() {
        return Arrays.asList("-v");
    }

    @SuppressWarnings("unused")
    protected CompilerInfo createCompilerInfo(Scanner scanner) {
        return null;
    }

    public void compileAndParseError(List<String> options, Path source, Path target, CompilerErrorHandler handler) {
        ProcessBuilder pb = new ProcessBuilder()
                        .command(createCompilerCommand(options, target.normalize(), source.normalize()))
                        .directory(tempDirectory.toFile());
        Process compilingProcess = null;
        try {
            compilingProcess = pb.start();

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
        } catch (InterruptedException ex) {
            throw new InterruptImageBuilding();
        } catch (IOException ex) {
            throw UserError.abort(ex, "Unable to compile C-ABI query code. Make sure native software development toolchain is installed on your system.");
        } finally {
            if (compilingProcess != null) {
                compilingProcess.destroy();
            }
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
            command.addAll(addTarget(target));
        }
        for (Path elem : input) {
            command.add(elem.toString());
        }

        LibCBase currentLibc = ImageSingletons.lookup(LibCBase.class);
        command.addAll(currentLibc.getCCompilerOptions());
        return command;
    }

    protected List<String> addTarget(Path target) {
        return Arrays.asList("-o", target.toString());
    }
}
