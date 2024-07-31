/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.graalvm.compiler.core.riscv64.RISCV64ReflectionUtil;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;

import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.libc.HostedLibCBase;
import com.oracle.svm.hosted.c.util.FileUtils;
import com.oracle.svm.util.ClassUtil;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;

public abstract class CCompilerInvoker {
    public static final String VISUAL_STUDIO_MINIMUM_REQUIRED_VERSION = "Visual Studio 2022 version 17.1.0";

    public final Path tempDirectory;
    public final CompilerInfo compilerInfo;

    @SuppressWarnings("this-escape")
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
            Path detectVersionInfoFile = tempDirectory.resolve("detect-cl-version-info.c").toAbsolutePath();
            try {
                Files.write(detectVersionInfoFile, List.of("M_X64=_M_X64", "M_ARM64EC=_M_ARM64EC", "MSC_FULL_VER=_MSC_FULL_VER"));
            } catch (IOException ioe) {
                throw VMError.shouldNotReachHere("Unable to create file to detect cl version info", ioe);
            }
            return List.of("/EP", detectVersionInfoFile.toString());
        }

        @Override
        protected CompilerInfo createCompilerInfo(Path compilerPath, Scanner scanner) {
            try {
                if (scanner.findInLine("Microsoft.*\\(R\\)") == null) {
                    return null; // not a Microsoft compiler
                }
                scanner.nextLine(); // skip rest of first line
                scanner.nextLine(); // skip copyright line
                scanner.nextLine(); // skip blank separator line
                skipLineIfHasNext(scanner, "detect-cl-version-info.c");
                scanner.nextLine(); // skip blank separator line
                skipLineIfHasNext(scanner, "M_X64=100"); // _M_X64 is defined
                skipLineIfHasNext(scanner, "M_ARM64EC=_M_ARM64EC"); // _M_ARM64EC is not defined
                if (scanner.findInLine("MSC_FULL_VER=") == null) {
                    return null;
                }
                String mscFullVerValue = scanner.nextLine();
                if (mscFullVerValue.length() < 5) {
                    return null;
                }
                int major = Integer.parseInt(mscFullVerValue.substring(0, 2));
                int minor0 = Integer.parseInt(mscFullVerValue.substring(2, 4));
                int minor1 = Integer.parseInt(mscFullVerValue.substring(4));
                return new CompilerInfo(compilerPath, "microsoft", "C/C++ Optimizing Compiler", "cl", major, minor0, minor1, "x64");
            } catch (NoSuchElementException | NumberFormatException e) {
                return null;
            }
        }

        private static void skipLineIfHasNext(Scanner scanner, String expectedToken) {
            if (scanner.hasNext(expectedToken)) {
                scanner.nextLine();
            } else {
                throw new NoSuchElementException(expectedToken);
            }
        }

        @Override
        protected void verify() {
            // See details on _MSC_VER at
            // https://learn.microsoft.com/en-us/cpp/preprocessor/predefined-macros?view=msvc-170
            int minimumMajorVersion = 19;
            int minimumMinorVersion = 31;
            if (compilerInfo.versionMajor < minimumMajorVersion || compilerInfo.versionMinor0 < minimumMinorVersion) {
                UserError.abort("On Windows, GraalVM Native Image for JDK %s requires %s or later (C/C++ Optimizing Compiler Version %s.%s or later).%nCompiler info detected: %s",
                                JavaVersionUtil.JAVA_SPEC, VISUAL_STUDIO_MINIMUM_REQUIRED_VERSION, minimumMajorVersion, minimumMinorVersion, compilerInfo.getShortDescription());
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
             * `/wd4201` enables the use of nameless struct/union, which is used by libffi.
             */
            return Arrays.asList("/WX", "/W4", "/wd4201", "/wd4244", "/wd4245", "/wd4800", "/wd4804", "/wd4214");
        }
    }

    private static class LinuxCCompilerInvoker extends CCompilerInvoker {

        LinuxCCompilerInvoker(Path tempDirectory) {
            super(tempDirectory);
        }

        @Override
        protected String getDefaultCompiler() {
            if (Platform.includedIn(Platform.LINUX.class)) {
                return HostedLibCBase.singleton().getTargetCompiler();
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

                if (scanner.findInLine(Pattern.quote("Intel(R) oneAPI DPC++/C++ Compiler ")) != null) {
                    scanner.useDelimiter("[. ]");
                    int major = scanner.nextInt();
                    int minor0 = scanner.nextInt();
                    int minor1 = scanner.nextInt();
                    return new CompilerInfo(compilerPath, "intel", "Intel(R) oneAPI DPC++/C++ Compiler", "icx", major, minor0, minor1, "x86_64");
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

        public String getShortDescription() {
            return String.format("%s (%s, %s, %d.%d.%d)", compilerPath.toFile().getName(), vendor, targetArch, versionMajor, versionMinor0, versionMinor1);
        }

        public String toCGlobalDataString() {
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
        return List.of("-v");
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
            case "riscv64":
                return (Class<? extends Architecture>) RISCV64ReflectionUtil.getArch(false);
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
        String envPath = System.getenv("PATH");
        if (envPath == null) {
            return Optional.empty();
        }
        return Arrays.stream(envPath.split(File.pathSeparator))
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
