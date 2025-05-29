/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasm.codegen;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.c.libc.TemporaryBuildDirectoryProvider;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.codegen.CCompilerInvoker;
import com.oracle.svm.hosted.c.util.FileUtils;
import com.oracle.svm.hosted.webimage.wasm.WebImageWasmOptions;

import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;

/**
 * Assembles a WebAssembly text file to the binary format using an external assembler.
 * <p>
 * Currently, two assemblers are available: {@link Wat2Wasm} and {@link Binaryen}.
 */
public abstract class WasmAssembler {

    public static class Options {
        @Option(help = "Provide custom path to the wat2wasm assembler used", type = OptionType.User)//
        public static final HostedOptionKey<String> Wat2WasmPath = new HostedOptionKey<>(null, Options::validateWat2WasmPath);

        private static void validateWat2WasmPath(HostedOptionKey<String> optionKey) {
            if (optionKey.hasBeenSet() && BinaryenCompat.usesBinaryen()) {
                throw UserError.abort("The option %s cannot be used if Binaryen is enabled (%s)", optionKey.getName(),
                                SubstrateOptionsParser.commandArgument(BinaryenCompat.Options.UseBinaryen, "+"));
            }
        }

        /**
         * Path to a custom {@code wasm-as} executable. Can only be used if Binaryen is used.
         *
         * @see BinaryenCompat.Options#UseBinaryen
         */
        @Option(help = "Provide custom path to the wasm-as assembler used. This option can only be used when wasm-as (from Binaryen) is used as the assembler (enabled using -H:+UseBinaryen)", type = OptionType.User)//
        public static final HostedOptionKey<String> WasmAsPath = new HostedOptionKey<>(null, Options::validateWasmWasPath);

        private static void validateWasmWasPath(HostedOptionKey<String> optionKey) {
            if (optionKey.hasBeenSet() && !BinaryenCompat.usesBinaryen()) {
                throw UserError.abort("The option %s can only be used if Binaryen is enabled (%s)", optionKey.getName(),
                                SubstrateOptionsParser.commandArgument(BinaryenCompat.Options.UseBinaryen, "+"));
            }
        }

    }

    public final Path tempDirectory;
    public final AssemblerInfo assemblerInfo;

    @SuppressWarnings("this-escape")
    protected WasmAssembler(Path tempDirectory) {
        this.tempDirectory = tempDirectory;
        try {
            this.assemblerInfo = getAssemblerInfo();
        } catch (UserError.UserException err) {
            throw rethrowWithInfo(err);
        }
    }

    public static WasmAssembler singleton() {
        return ImageSingletons.lookup(WasmAssembler.class);
    }

    /**
     * Creates a {@link WasmAssembler} instance and installs an {@link ImageSingletons image
     * singleton}.
     * <p>
     * Access the assembler through {@link #singleton()}.
     */
    public static void install() {
        assert !ImageSingletons.contains(WasmAssembler.class);

        Path tempDirectory = ImageSingletons.lookup(TemporaryBuildDirectoryProvider.class).getTemporaryBuildDirectory();

        WasmAssembler assembler;
        if (BinaryenCompat.usesBinaryen()) {
            assembler = new WasmAssembler.Binaryen(tempDirectory);
        } else {
            assembler = new WasmAssembler.Wat2Wasm(tempDirectory);
        }

        ImageSingletons.add(WasmAssembler.class, assembler);
        assembler.verifyAssembler();
    }

    public void assemble(Path watPath, Path wasmPath, OptionValues options, Consumer<String> printer) throws IOException, InterruptedException {
        var result = runAssembler(watPath, wasmPath);
        List<String> outLines = result.outputLines();

        int exitCode = result.exitCode;
        if (!outLines.isEmpty() && (exitCode != 0 || DebugOptions.LogVerbose.getValue(options))) {
            printer.accept("Output for " + result.commandLine + ":");
            outLines.forEach(printer);
        }
        UserError.guarantee(exitCode == 0, "%s failed with exit code: %s", result.executable.toString(), exitCode);

    }

    /**
     * Assembles the Wasm text format in the given file ({@code watPath}) to binary.
     * <p>
     * The output is placed into {@code wasmPath}
     *
     * @return Program return code and output lines
     *
     * @see #runCommand(Path, List)
     */
    public RunResult runAssembler(Path watPath, Path wasmPath) throws IOException, InterruptedException {
        Path executable = assemblerInfo.assemblerPath;

        List<String> flags = new ArrayList<>();
        if (WebImageWasmOptions.DebugNames.getValue()) {
            flags.add(getDebugNamesFlag());
        }

        flags.addAll(getExtraFlags());

        List<String> args = createAssemblerFlags(flags, wasmPath, watPath);
        return runCommand(executable, args);
    }

    /**
     * Runs a command and returns its result in a {@link RunResult}.
     * <p>
     * The error stream is redirected to the output stream and thus their output is interleaved.
     * <p>
     * The command executed is {@code [executable] + args}
     */
    private static RunResult runCommand(Path executable, List<String> args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(args.size() + 1);
        command.add(executable.toString());
        command.addAll(args);

        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();

        try (InputStream inputStream = p.getInputStream()) {
            List<String> outLines = FileUtils.readAllLines(inputStream);

            int exitCode = p.waitFor();
            return new RunResult(executable, command, outLines, exitCode);
        }
    }

    /**
     * The standard name of the assembler executable.
     */
    protected abstract String getExecutable();

    /**
     * The hosted option the user can use to specify a custom executable.
     */
    protected abstract HostedOptionKey<String> getPathOption();

    /**
     * Flag to enable debug names in the produced binary.
     */
    protected abstract String getDebugNamesFlag();

    /**
     * Flags to write binary to given path.
     */
    protected abstract List<String> getOutputFlags(Path wasmPath);

    /**
     * Any additional flags required for this assembler (e.g. to enable specific features).
     */
    protected abstract List<String> getExtraFlags();

    /**
     * Path to the resource file containing a WebAssembly module in text format that this assembler
     * must be able to assemble.
     * <p>
     * The path is relative to this class.
     */
    protected abstract String getVerificationFile();

    protected List<String> getVersionInfoOptions() {
        return List.of("--version");
    }

    public void verifyAssembler() {
        if (SubstrateOptions.CheckToolchain.getValue()) {
            try {
                verify();
            } catch (UserError.UserException err) {
                throw rethrowWithInfo(err);
            }
        }
    }

    private UserError.UserException rethrowWithInfo(UserError.UserException err) {
        List<String> messages = new ArrayList<>();
        err.getMessages().forEach(messages::add);
        HostedOptionKey<String> pathOption = getPathOption();
        if (!pathOption.hasBeenSet()) {
            messages.add("A custom path to the " + getExecutable() + " executable can be set with the " + SubstrateOptionsParser.commandArgument(getPathOption(), "<path>") + " command-line option");
        }
        messages.add("To prevent native-toolchain checking provide command-line option " + SubstrateOptionsParser.commandArgument(SubstrateOptions.CheckToolchain, "-"));
        return UserError.abort(messages);
    }

    private static String formatCommandInfo(RunResult result) {
        return formatCommandInfo(result.commandLine(), result.exitCode(), result.outputLines);
    }

    /**
     * Nicely formats information about a failed command.
     */
    private static String formatCommandInfo(List<String> cmd, int exitCode, List<String> outputLines) {
        assert exitCode != 0;
        String message = "Command '%s' failed with exit code %d. Output:%n%s";
        return message.formatted(SubstrateUtil.getShellCommandString(cmd, false), exitCode,
                        outputLines.stream().map(str -> "  " + str).collect(Collectors.joining(System.lineSeparator())));
    }

    /**
     * Verifies that the assembler can assemble a small Wasm program in the text format.
     * <p>
     * We only check that it succeeds, no checking on the correctness of the output is done.
     *
     * @see #getVerificationFile()
     */
    private void verify() {
        String fileName = getVerificationFile();
        try (InputStream stream = WasmAssembler.class.getResourceAsStream(fileName)) {
            VMError.guarantee(stream != null, "Couldn't find file (%s) used to verify Wasm assembler", fileName);
            Path path = tempDirectory.resolve(fileName);
            Path outputPath = tempDirectory.resolve(fileName + ".wasm");
            // Copy the file out of the JAR
            Files.copy(stream, path);

            RunResult result = runAssembler(path, outputPath);

            if (result.exitCode != 0) {
                throw UserError.abort("Wasm assembler could not assemble sample Wasm text format file. " +
                                "The chosen assembler may not support all required features.%n%s",
                                formatCommandInfo(result));
            }

        } catch (InterruptedException e) {
            throw new InterruptImageBuilding("Interrupted while checking Wasm assembler " + assemblerInfo.assemblerPath);
        } catch (IOException e) {
            throw UserError.abort(e, "Assembling sample Wasm text format file failed");
        }
    }

    private AssemblerInfo getAssemblerInfo() {
        Path executablePath = getAssemblerPath().toAbsolutePath();

        if (!SubstrateOptions.CheckToolchain.getValue()) {
            return new AssemblerInfo(executablePath, null);
        }

        String versionString;
        try {
            List<String> helpFlags = createAssemblerFlags(getVersionInfoOptions(), null, null);
            RunResult result = runCommand(executablePath, helpFlags);
            if (result.exitCode() != 0) {
                throw UserError.abort("Collecting Wasm assembler info failed.%n%s", formatCommandInfo(result));
            }

            versionString = String.join(System.lineSeparator(), result.outputLines());
        } catch (InterruptedException e) {
            throw new InterruptImageBuilding("Interrupted while checking Wasm assembler " + executablePath);
        } catch (IOException e) {
            throw UserError.abort(e, "Collecting Wasm assembler info failed");
        }

        return new AssemblerInfo(executablePath, versionString);
    }

    private Path getAssemblerPath() {
        Path executablePath;
        HostedOptionKey<String> pathOption = getPathOption();
        String userDefinedPath = pathOption.getValue();

        if (userDefinedPath != null) {
            executablePath = Paths.get(userDefinedPath);
        } else {
            String executableName = asExecutableName(getExecutable());
            Optional<Path> optPath = CCompilerInvoker.lookupSearchPath(executableName);

            if (optPath.isPresent()) {
                executablePath = optPath.get();
            } else {
                throw UserError.abort("'%s' not found on the system path. Please extend the PATH environment variable so that it provides a Wasm assembler executable called '%s'.", executableName,
                                executableName);
            }

        }
        if (Files.isDirectory(executablePath) || !Files.isExecutable(executablePath)) {
            String msgSubject;
            if (userDefinedPath != null) {
                msgSubject = SubstrateOptionsParser.commandArgument(pathOption, userDefinedPath);
            } else {
                msgSubject = "Default Wasm assembler '" + executablePath + "'";
            }

            throw UserError.abort("%s does not specify a path to an executable.", msgSubject);
        }

        return executablePath;
    }

    protected String asExecutableName(String basename) {
        if (OS.WINDOWS.isCurrent()) {
            String suffix = ".exe";
            if (!basename.endsWith(suffix)) {
                return basename + suffix;
            }
        }

        return basename;
    }

    /**
     * @param flags Additional commandline flags
     * @param output Path to the output file. May be {@code null}, in which case no output file is
     *            specified
     * @param input Path to the input file. May be {@code null}, in which case no file is passed to
     *            the assembler
     */
    private List<String> createAssemblerFlags(List<String> flags, Path output, Path input) {
        List<String> command = new ArrayList<>(flags);

        if (output != null) {
            command.addAll(getOutputFlags(output));
        }

        if (input != null) {
            command.add(input.toString());
        }

        return command;
    }

    /**
     * Uses {@code wat2wasm}.
     * <p>
     * Note: {@code wat2wasm} does not currently implement some Wasm proposals. It currently only
     * works in the WasmLM backend.
     */
    public static class Wat2Wasm extends WasmAssembler {

        protected Wat2Wasm(Path tempDirectory) {
            super(tempDirectory);
        }

        @Override
        protected String getExecutable() {
            return "wat2wasm";
        }

        @Override
        protected HostedOptionKey<String> getPathOption() {
            return Options.Wat2WasmPath;
        }

        @Override
        protected String getDebugNamesFlag() {
            return "--debug-names";
        }

        @Override
        protected List<String> getOutputFlags(Path wasmPath) {
            return Collections.singletonList("--output=" + wasmPath);
        }

        @Override
        protected List<String> getExtraFlags() {
            return List.of("--enable-exceptions", "--enable-function-references");
        }

        @Override
        protected String getVerificationFile() {
            return "verify-wat2wasm.wast";
        }
    }

    /**
     * Uses {@code wasm-as} from the binaryen project.
     * <p>
     * Note: Binaryen does not fully support the Wasm text format, some workarounds in the text
     * format are necessary.
     *
     * @see BinaryenCompat
     */
    public static class Binaryen extends WasmAssembler {

        protected Binaryen(Path tempDirectory) {
            super(tempDirectory);
        }

        @Override
        protected String getExecutable() {
            return "wasm-as";
        }

        @Override
        protected HostedOptionKey<String> getPathOption() {
            return Options.WasmAsPath;
        }

        @Override
        protected String getDebugNamesFlag() {
            return "-g";
        }

        @Override
        protected List<String> getOutputFlags(Path wasmPath) {
            return Collections.singletonList("--output=" + wasmPath);
        }

        @Override
        protected List<String> getExtraFlags() {
            return List.of("--enable-exception-handling", "--enable-nontrapping-float-to-int", "--enable-bulk-memory", "--enable-reference-types", "--enable-gc");
        }

        @Override
        protected String getVerificationFile() {
            return "verify-wasm-as.wast";
        }
    }

    public record AssemblerInfo(Path assemblerPath, String versionString) {
    }

    public record RunResult(Path executable, List<String> commandLine, List<String> outputLines, int exitCode) {
    }
}
