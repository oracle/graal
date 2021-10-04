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
import java.util.stream.Collectors;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;

import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.c.util.FileUtils;
import com.oracle.svm.util.ClassUtil;

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
                throw UserError.abort("No CCompilerInvoker for operating system %s", hostOS.name());
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
                /*
                 * Some cl.exe print "... Microsoft (R) C/C++ ... ##.##.#####" while others print
                 * "...C/C++ ... Microsoft (R) ... ##.##.#####".
                 */
                if (scanner.findInLine("Microsoft.*\\(R\\) C/C\\+\\+") == null &&
                                scanner.findInLine("C/C\\+\\+.*Microsoft.*\\(R\\)") == null) {
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
            // See details on _MSC_VER at https://en.wikipedia.org/wiki/Microsoft_Visual_C%2B%2B
            // The constraint of `_MSC_VER >= 1912` reflects the version used for building OpenJDK8.
            if (compilerInfo.versionMajor < 19 || compilerInfo.versionMinor0 < 12) {
                UserError.abort("Java %d native-image building on Windows requires Visual Studio 2017 version 15.5 or later (C/C++ Optimizing Compiler Version 19.12 or later).%nCompiler info detected: %s",
                                JavaVersionUtil.JAVA_SPEC, compilerInfo);
            }
            if (guessArchitecture(compilerInfo.targetArch) != AMD64.class) {
                String targetPrefix = compilerInfo.targetArch.matches("(.*x|i\\d)86$") ? "32-bit architecture " : "";
                UserError.abort("Native-image building on Windows currently only supports target architecture: %s (%s%s unsupported)",
                                AMD64.class.getSimpleName(), targetPrefix, compilerInfo.targetArch);
            }
        }

        @Override
        protected List<String> compileStrictOptions() {
            /*
             * On Windows `/Wall` corresponds to `-Wall -Wextra`. Therefore we use /W4 instead.
             * Options `/wd4244` and `/wd4245` are needed because our query code makes use of
             * implicit unsigned/signed conversions to detect signedness of types. `/wd4800`,
             * `/wd4804` are needed to silence warnings when querying bool types. `/wd4214` is
             * needed to make older versions of cl.exe accept bitfields larger than int-size.
             */
            return Arrays.asList("/WX", "/W4", "/wd4244", "/wd4245", "/wd4800", "/wd4804", "/wd4214");
        }
    }

    private static class LinuxCCompilerInvoker extends CCompilerInvoker {

        LinuxCCompilerInvoker(Path tempDirectory) {
            super(tempDirectory);
        }

        @Override
        protected String getDefaultCompiler() {
            if (Platform.includedIn(Platform.LINUX.class)) {
                return LibCBase.singleton().getTargetCompiler();
            }
            return "gcc";
        }

        @Override
        protected CompilerInfo createCompilerInfo(Path compilerPath, Scanner scanner) {
            try {
                if (scanner.findInLine("icc version ") != null) {
                    scanner.useDelimiter("[. ]");
                    int major = scanner.nextInt();
                    int minor0 = scanner.nextInt();
                    int minor1 = scanner.nextInt();
                    return new CompilerInfo(compilerPath, "intel", "Intel(R) C++ Compiler", "icc", major, minor0, minor1, "x86_64");
                }

                if (scanner.findInLine("clang version ") != null) {
                    scanner.useDelimiter("[. -]");
                    int major = scanner.nextInt();
                    int minor0 = scanner.nextInt();
                    int minor1 = scanner.nextInt();
                    String[] triplet = guessTargetTriplet(scanner);
                    return new CompilerInfo(compilerPath, "llvm", "Clang C++ Compiler", "clang", major, minor0, minor1, triplet[0]);
                }

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
                UserError.abort("Linux native toolchain (%s) has no matching native-image target architecture.", compilerInfo.targetArch);
            }
            if (guessed != substrateTargetArch) {
                UserError.abort("Linux native toolchain (%s) implies native-image target architecture %s but configured native-image target architecture is %s.",
                                compilerInfo.targetArch, guessed, substrateTargetArch);
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
            Class<? extends Architecture> substrateTargetArch = ImageSingletons.lookup(SubstrateTargetDescription.class).arch.getClass();
            Class<? extends Architecture> guessed = guessArchitecture(compilerInfo.targetArch);
            if (guessed == null) {
                UserError.abort("Darwin native toolchain (%s) has no matching native-image target architecture.", compilerInfo.targetArch);
            }
            if (guessed != substrateTargetArch) {
                UserError.abort("Darwin native toolchain (%s) implies native-image target architecture %s but configured native-image target architecture is %s.",
                                compilerInfo.targetArch, guessed, substrateTargetArch);
            }
        }

        @Override
        protected List<String> compileStrictOptions() {
            List<String> strictOptions = new ArrayList<>(super.compileStrictOptions());
            strictOptions.add("-Wno-tautological-compare");
            return strictOptions;
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
            return new CompilerInfo(compilerPath, null, ClassUtil.getUnqualifiedName(getClass()), null, 0, 0, 0, null);
        }
        List<String> compilerCommand = createCompilerCommand(compilerPath, getVersionInfoOptions(), null);
        Process compilerProcess = null;
        try {
            ProcessBuilder processBuilder = FileUtils.prepareCommand(compilerCommand, tempDirectory);
            processBuilder.redirectErrorStream(true);
            processBuilder.environment().put("LC_ALL", "C");

            FileUtils.traceCommand(processBuilder);

            compilerProcess = processBuilder.start();
            List<String> lines;
            CompilerInfo result;
            try (InputStream inputStream = compilerProcess.getInputStream()) {
                lines = FileUtils.readAllLines(inputStream);

                FileUtils.traceCommandOutput(lines);

                result = createCompilerInfo(compilerPath, new Scanner(String.join(System.lineSeparator(), lines)));
            }
            compilerProcess.waitFor();
            if (result == null) {
                String errorMessage = "Unable to detect supported %s native software development toolchain.%n" +
                                "Querying with command '%s' prints:%n%s";
                throw UserError.abort(errorMessage, OS.getCurrent().name(), SubstrateUtil.getShellCommandString(compilerCommand, false),
                                lines.stream().map(str -> "  " + str).collect(Collectors.joining(System.lineSeparator())));
            }
            return result;
        } catch (InterruptedException ex) {
            throw new InterruptImageBuilding("Interrupted during checking native-compiler " + compilerPath);
        } catch (IOException e) {
            throw UserError.abort(e, "Collecting native-compiler info with '%s' failed", SubstrateUtil.getShellCommandString(compilerCommand, false));
        } finally {
            if (compilerProcess != null) {
                compilerProcess.destroy();
            }
        }
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
            case "arm64": /* Darwin notation */
                return AArch64.class;
            case "i686":
            case "80x86": /* Windows notation */
            case "x86":
                /* Graal does not support 32-bit architectures */
            default:
                return null;
        }
    }

    public interface CompilerErrorHandler {
        void handle(ProcessBuilder current, Path source, String line);
    }

    @SuppressWarnings("try")
    public void compileAndParseError(boolean strict, List<String> compileOptions, Path source, Path target, CompilerErrorHandler handler) {
        List<String> options = strict ? createStrictOptions(compileOptions) : compileOptions;
        Process compilingProcess = null;
        try {
            ProcessBuilder compileCommand = FileUtils.prepareCommand(createCompilerCommand(options, target.normalize(), source.normalize()), tempDirectory);

            FileUtils.traceCommand(compileCommand);

            compilingProcess = compileCommand.start();

            List<String> lines;
            try (InputStream compilerErrors = getCompilerErrorStream(compilingProcess)) {
                lines = FileUtils.readAllLines(compilerErrors);
                FileUtils.traceCommandOutput(lines);
            }
            boolean errorReported = false;
            for (String line : lines) {
                if (detectError(line)) {
                    if (handler != null) {
                        handler.handle(compileCommand, source, line);
                    }
                    errorReported = true;
                }
            }

            int status = compilingProcess.waitFor();
            if (status != 0 && !errorReported) {
                if (handler != null) {
                    handler.handle(compileCommand, source, lines.toString());
                }
            }
        } catch (InterruptedException ex) {
            throw new InterruptImageBuilding("Interrupted during C-ABI query code compilation of " + source);
        } catch (IOException ex) {
            throw UserError.abort(ex, "Unable to compile C-ABI query code %s. Make sure native software development toolchain is installed on your system.", source);
        } finally {
            if (compilingProcess != null) {
                compilingProcess.destroy();
            }
        }
    }

    private List<String> createStrictOptions(List<String> compileOptions) {
        ArrayList<String> strictCompileOptions = new ArrayList<>(compileStrictOptions());
        strictCompileOptions.addAll(compileOptions);
        return strictCompileOptions;
    }

    protected List<String> compileStrictOptions() {
        return Arrays.asList("-Wall", "-Werror");
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
                throw UserError.abort("Default native-compiler executable '%s' not found via environment variable PATH", executableName);
            }
        }
        if (Files.isDirectory(compilerPath) || !Files.isExecutable(compilerPath)) {
            String msgSubject;
            if (userDefinedPath != null) {
                msgSubject = SubstrateOptionsParser.commandArgument(SubstrateOptions.CCompilerPath, userDefinedPath);
            } else {
                msgSubject = "Default native-compiler '" + compilerPath + "'";
            }
            throw UserError.abort("%s does not specify a path to an executable.", msgSubject);
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
        command.addAll(SubstrateOptions.CCompilerOption.getValue().values());
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
