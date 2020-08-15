/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Consumer;

import com.oracle.svm.core.c.libc.LibCBase;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.util.FileUtils;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;

public abstract class CCompilerInvoker {

    public final Path tempDirectory;
    public final CompilerInfo compilerInfo;

    protected CCompilerInvoker(Path tempDirectory) {
        this.tempDirectory = tempDirectory;
        try {
            this.compilerInfo = getCCompilerInfo();
            if (this.compilerInfo == null) {
                UserError.abort(String.format("Unable to detect supported %s native software development toolchain.", OS.getCurrent().name()));
            }
        } catch (UserError.UserException err) {
            throw addSkipCheckingInfo(err);
        }
    }

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

    public void verifyCompiler() {
        if (SubstrateOptions.CheckToolchain.getValue()) {
            try {
                verify();
            } catch (UserError.UserException err) {
                throw addSkipCheckingInfo(err);
            }
        }
    }

    private static UserError.UserException addSkipCheckingInfo(UserError.UserException err) {
        List<String> messages = new ArrayList<>();
        err.getMessages().forEach(messages::add);
        messages.add("To prevent native-toolchain checking provide command-line option " + SubstrateOptionsParser.commandArgument(SubstrateOptions.CheckToolchain, "-"));
        return UserError.abort(messages);
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
        protected CompilerInfo createCompilerInfo(Path compilerPath, Scanner outerScanner) {
            try (Scanner scanner = new Scanner(outerScanner.nextLine())) {
                String targetArch = null;
                /* For cl.exe the first line holds all necessary information */
                if (scanner.hasNext("\u7528\u4E8E")) {
                    /* Simplified-Chinese has targetArch first */
                    scanner.next();
                    targetArch = scanner.next();
                }
                if (scanner.findInLine("Microsoft.*\\(R\\) C/C\\+\\+") == null) {
                    return null;
                }
                scanner.useDelimiter("\\D");
                while (!scanner.hasNextInt()) {
                    scanner.next();
                }
                int major = scanner.nextInt();
                int minor0 = scanner.nextInt();
                int minor1 = scanner.nextInt();
                if (targetArch == null) {
                    scanner.reset();
                    while (scanner.hasNext()) {
                        /* targetArch is last token in line */
                        targetArch = scanner.next();
                    }
                }
                return new CompilerInfo(compilerPath, "microsoft", "C/C++ Optimizing Compiler", "cl", major, minor0, minor1, targetArch);
            } catch (NoSuchElementException e) {
                return null;
            }
        }

        @Override
        protected void verify() {
            if (JavaVersionUtil.JAVA_SPEC >= 11) {
                if (compilerInfo.versionMajor < 19) {
                    UserError.abort("Java " + JavaVersionUtil.JAVA_SPEC +
                                    " native-image building on Windows requires Visual Studio 2015 version 14.0 or later (C/C++ Optimizing Compiler Version 19.* or later)");
                }
            } else {
                VMError.guarantee(JavaVersionUtil.JAVA_SPEC == 8, "Native-image building is only supported for Java 8 and Java 11 or later");
                if (compilerInfo.versionMajor != 16 || compilerInfo.versionMinor0 != 0) {
                    UserError.abort("Java 8 native-image building on Windows requires Microsoft Windows SDK 7.1");
                }
            }
            if (guessArchitecture(compilerInfo.targetArch) != AMD64.class) {
                UserError.abort(String.format("Native-image building on Windows currently only supports target architecture: %s (%s unsupported)",
                                AMD64.class.getSimpleName(), compilerInfo.targetArch));
            }
        }

    }

    private static class LinuxCCompilerInvoker extends CCompilerInvoker {

        LinuxCCompilerInvoker(Path tempDirectory) {
            super(tempDirectory);
        }

        @Override
        protected String getDefaultCompiler() {
            return LibCBase.singleton().getTargetCompiler();
        }

        @Override
        protected CompilerInfo createCompilerInfo(Path compilerPath, Scanner scanner) {
            try {
                String[] triplet = guessTargetTriplet(scanner);
                while (scanner.findInLine("gcc version ") == null) {
                    scanner.nextLine();
                }
                scanner.useDelimiter("[. ]");
                int major = scanner.nextInt();
                int minor0 = scanner.nextInt();
                int minor1 = scanner.nextInt();
                return new CompilerInfo(compilerPath, triplet[1], "GNU project C and C++ compiler", "gcc", major, minor0, minor1, triplet[0]);
            } catch (NoSuchElementException e) {
                return null;
            }
        }

        @Override
        protected void verify() {
            Class<? extends Architecture> substrateTargetArch = ImageSingletons.lookup(SubstrateTargetDescription.class).arch.getClass();
            Class<? extends Architecture> guessed = guessArchitecture(compilerInfo.targetArch);
            if (guessed == null) {
                UserError.abort(String.format("Native toolchain (%s) has no matching native-image target architecture.", compilerInfo.targetArch));
            }
            if (guessed != substrateTargetArch) {
                UserError.abort(String.format("Native toolchain (%s) implies native-image target architecture %s but configured native-image target architecture is %s.",
                                compilerInfo.targetArch, guessed, substrateTargetArch));
            }
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

        @Override
        @SuppressWarnings("try")
        protected CompilerInfo createCompilerInfo(Path compilerPath, Scanner scanner) {
            try {
                while (scanner.findInLine("Apple (clang|LLVM) version ") == null) {
                    scanner.nextLine();
                }
                scanner.useDelimiter("[. ]");
                int major = scanner.nextInt();
                int minor0 = scanner.nextInt();
                // On Yosemite and prior the compiler might not report a patch version
                // https://trac.macports.org/wiki/XcodeVersionInfo
                int minor1 = scanner.hasNextInt() ? scanner.nextInt() : 0;
                scanner.reset(); /* back to default delimiters */
                String[] triplet = guessTargetTriplet(scanner);
                return new CompilerInfo(compilerPath, triplet[1], "LLVM", "clang", major, minor0, minor1, triplet[0]);
            } catch (NoSuchElementException e) {
                return null;
            }
        }

        @Override
        protected void verify() {
            if (guessArchitecture(compilerInfo.targetArch) != AMD64.class) {
                UserError.abort(String.format("Native-image building on Darwin currently only supports target architecture: %s (%s unsupported)",
                                AMD64.class.getSimpleName(), compilerInfo.targetArch));
            }
        }

    }

    protected InputStream getCompilerErrorStream(Process compilingProcess) {
        return compilingProcess.getErrorStream();
    }

    public static final class CompilerInfo {
        public final Path compilerPath;
        public final String name;
        public final String shortName;
        public final String vendor;
        public final int versionMajor;
        public final int versionMinor0;
        public final int versionMinor1;
        public final String targetArch;

        public CompilerInfo(Path compilerPath, String vendor, String name, String shortName, int versionMajor, int versionMinor0, int versionMinor1, String targetArch) {
            this.compilerPath = compilerPath;
            this.name = name;
            this.vendor = vendor;
            this.shortName = shortName;
            this.versionMajor = versionMajor;
            this.versionMinor0 = versionMinor0;
            this.versionMinor1 = versionMinor1;
            this.targetArch = targetArch;
        }

        @Override
        public String toString() {
            return String.join("|", Arrays.asList(shortName, vendor, targetArch,
                            String.format("%d.%d.%d", versionMajor, versionMinor0, versionMinor1)));
        }

        public void dump(Consumer<String> sink) {
            sink.accept("Name: " + name + " (" + shortName + ")");
            sink.accept("Vendor: " + vendor);
            sink.accept(String.format("Version: %d.%d.%d", versionMajor, versionMinor0, versionMinor1));
            sink.accept("Target architecture: " + targetArch);
            sink.accept("Path: " + compilerPath);
        }
    }

    protected abstract void verify();

    private CompilerInfo getCCompilerInfo() {
        Path compilerPath = getCCompilerPath().toAbsolutePath();
        if (!SubstrateOptions.CheckToolchain.getValue()) {
            return new CompilerInfo(compilerPath, null, getClass().getSimpleName(), null, 0, 0, 0, null);
        }
        List<String> compilerCommand = createCompilerCommand(compilerPath, getVersionInfoOptions(), null);
        ProcessBuilder pb = new ProcessBuilder()
                        .command(compilerCommand)
                        .directory(tempDirectory.toFile())
                        .redirectErrorStream(true);
        pb.environment().put("LC_ALL", "C");
        CompilerInfo result = null;
        Process process = null;
        try {
            process = pb.start();
            try (Scanner scanner = new Scanner(process.getInputStream())) {
                result = createCompilerInfo(compilerPath, scanner);
            }
            process.waitFor();
        } catch (InterruptedException ex) {
            throw new InterruptImageBuilding();
        } catch (IOException e) {
            UserError.abort(e, "Collecting native-compiler info with '" + SubstrateUtil.getShellCommandString(pb.command(), false) + "' failed");
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return result;
    }

    protected List<String> getVersionInfoOptions() {
        return Arrays.asList("-v");
    }

    protected abstract CompilerInfo createCompilerInfo(Path compilerPath, Scanner scanner);

    protected static String[] guessTargetTriplet(Scanner scanner) {
        while (scanner.findInLine("Target: ") == null) {
            scanner.nextLine();
        }
        scanner.useDelimiter("-");
        String arch = scanner.next();
        String vendor = scanner.next();
        String os = scanner.nextLine();
        os = os.startsWith("-") ? os.substring(1) : os;
        scanner.reset(); /* back to default delimiters */
        return new String[]{arch, vendor, os};
    }

    @SuppressWarnings({"unchecked", "fallthrough"})
    protected static Class<? extends Architecture> guessArchitecture(String archStr) {
        switch (archStr) {
            case "x86_64":
            case "x64": /* Windows notation */
                return AMD64.class;
            case "aarch64":
                return AArch64.class;
            case "i686":
            case "80x86": /* Windows notation */
                /* Graal does not support 32-bit architectures */
            default:
                return null;
        }
    }

    public interface CompilerErrorHandler {
        void handle(ProcessBuilder current, Path source, String line);
    }

    @SuppressWarnings("try")
    public void compileAndParseError(List<String> options, Path source, Path target, CompilerErrorHandler handler, DebugContext debug) {
        ProcessBuilder pb = new ProcessBuilder()
                        .command(createCompilerCommand(options, target.normalize(), source.normalize()))
                        .directory(tempDirectory.toFile());
        Process compilingProcess = null;
        try {
            try (DebugContext.Scope s = debug.scope("InvokeCC")) {
                debug.log("Using CompilerCommand: %s", SubstrateUtil.getShellCommandString(pb.command(), false));
            }
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
                        .filter(p -> Files.isExecutable(p) && !Files.isDirectory(p))
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
        return createCompilerCommand(compilerInfo.compilerPath, options, target, input);
    }

    private List<String> createCompilerCommand(Path compilerPath, List<String> options, Path target, Path... input) {
        List<String> command = new ArrayList<>();
        command.add(compilerPath.toString());
        command.addAll(Arrays.asList(SubstrateOptions.CCompilerOption.getValue()));
        command.addAll(options);

        if (target != null) {
            command.addAll(addTarget(target));
        }
        for (Path elem : input) {
            command.add(elem.toString());
        }

        return command;
    }

    protected List<String> addTarget(Path target) {
        return Arrays.asList("-o", target.toString());
    }
}
