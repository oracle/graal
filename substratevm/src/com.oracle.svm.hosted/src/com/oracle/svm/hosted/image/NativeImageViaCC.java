/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.image;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;

import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.macho.MachOSymtab;
import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.BuildArtifacts.ArtifactType;
import com.oracle.svm.core.LinkerInvocation;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.BeforeImageWriteAccessImpl;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.util.FileUtils;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;

public abstract class NativeImageViaCC extends NativeImage {

    public NativeImageViaCC(NativeImageKind k, HostedUniverse universe, HostedMetaAccess metaAccess, NativeLibraries nativeLibs, NativeImageHeap heap, NativeImageCodeCache codeCache,
                    List<HostedMethod> entryPoints, ClassLoader imageClassLoader) {
        super(k, universe, metaAccess, nativeLibs, heap, codeCache, entryPoints, imageClassLoader);
    }

    class BinutilsCCLinkerInvocation extends CCLinkerInvocation {

        private final boolean staticExecWithDynamicallyLinkLibC = SubstrateOptions.StaticExecutableWithDynamicLibC.getValue();
        private final Set<String> libCLibaries = new HashSet<>(Arrays.asList("pthread", "dl", "rt", "m"));

        BinutilsCCLinkerInvocation() {
            additionalPreOptions.add("-z");
            additionalPreOptions.add("noexecstack");
            if (SubstrateOptions.ForceNoROSectionRelocations.getValue()) {
                additionalPreOptions.add("-fuse-ld=gold");
                additionalPreOptions.add("-Wl,--rosegment");
            }

            if (SubstrateOptions.RemoveUnusedSymbols.getValue()) {
                /* Perform garbage collection of unused input sections. */
                additionalPreOptions.add("-Wl,--gc-sections");
            }

            /*
             * On Linux we use --dynamic-list to ensure only our defined entrypoints end up as
             * global symbols in the dynamic symbol table of the image. However, when compiling a
             * static image these are not needed, and some linkers interpret them wrong, creating a
             * corrupt binary.
             */
            if (!SubstrateOptions.StaticExecutable.getValue()) {
                try {
                    StringBuilder exportedSymbols = new StringBuilder();
                    exportedSymbols.append("{\n");
                    for (String symbol : getImageSymbols(true)) {
                        exportedSymbols.append('\"').append(symbol).append("\";\n");
                    }
                    exportedSymbols.append("};");
                    Path exportedSymbolsPath = nativeLibs.tempDirectory.resolve("exported_symbols.list");
                    Files.write(exportedSymbolsPath, Collections.singleton(exportedSymbols.toString()));
                    additionalPreOptions.add("-Wl,--dynamic-list");
                    additionalPreOptions.add("-Wl," + exportedSymbolsPath.toAbsolutePath());

                    // Drop global symbols in linked static libraries: not covered by --dynamic-list
                    additionalPreOptions.add("-Wl,--exclude-libs,ALL");
                } catch (IOException e) {
                    VMError.shouldNotReachHere();
                }
            }

            if (SubstrateOptions.DeleteLocalSymbols.getValue()) {
                additionalPreOptions.add("-Wl,-x");
            }
        }

        @Override
        public List<String> getImageSymbols(boolean onlyGlobal) {
            return codeCache.getSymbols(getOrCreateDebugObjectFile(), onlyGlobal).stream()
                            .map(ObjectFile.Symbol::getName)
                            .collect(Collectors.toList());
        }

        @Override
        protected void setOutputKind(List<String> cmd) {
            switch (imageKind) {
                case EXECUTABLE:
                    break;
                case STATIC_EXECUTABLE:
                    if (!staticExecWithDynamicallyLinkLibC) {
                        cmd.add("-static");
                    }
                    break;
                case SHARED_LIBRARY:
                    cmd.add("-shared");
                    break;
                default:
                    VMError.shouldNotReachHere();
            }
        }

        @Override
        protected List<String> getLibrariesCommand() {
            List<String> cmd = new ArrayList<>();
            for (String lib : libs) {
                if (staticExecWithDynamicallyLinkLibC) {
                    String linkingMode = libCLibaries.contains(lib)
                                    ? "dynamic"
                                    : "static";
                    cmd.add("-Wl,-B" + linkingMode);
                }
                cmd.add("-l" + lib);
            }

            // Make sure libgcc gets statically linked
            if (staticExecWithDynamicallyLinkLibC) {
                cmd.add("-static-libgcc");
            }
            return cmd;
        }
    }

    class DarwinCCLinkerInvocation extends CCLinkerInvocation {

        DarwinCCLinkerInvocation() {
            // Workaround building images with older Xcode with new libraries
            additionalPreOptions.add("-Wl,-U,___darwin_check_fd_set_overflow");

            if (!SubstrateOptions.useLLVMBackend()) {
                additionalPreOptions.add("-Wl,-no_compact_unwind");
            }

            if (SubstrateOptions.RemoveUnusedSymbols.getValue()) {
                /* Remove functions and data unreachable by entry points. */
                additionalPreOptions.add("-Wl,-dead_strip");
            }

            /*
             * On Darwin we use -exported_symbols_list to ensure only our defined entrypoints end up
             * as global symbols in the dynamic symbol table of the image.
             */
            try {
                Path exportedSymbolsPath = nativeLibs.tempDirectory.resolve("exported_symbols.list");
                Files.write(exportedSymbolsPath, getImageSymbols(true));
                additionalPreOptions.add("-Wl,-exported_symbols_list");
                additionalPreOptions.add("-Wl," + exportedSymbolsPath.toAbsolutePath());
            } catch (IOException e) {
                VMError.shouldNotReachHere();
            }

            if (SubstrateOptions.DeleteLocalSymbols.getValue()) {
                additionalPreOptions.add("-Wl,-x");
            }

            additionalPreOptions.add("-arch");
            if (Platform.includedIn(Platform.AMD64.class)) {
                additionalPreOptions.add("x86_64");
            } else if (Platform.includedIn(Platform.AARCH64.class)) {
                additionalPreOptions.add("arm64");
            }
        }

        @Override
        public List<String> getImageSymbols(boolean onlyGlobal) {
            return codeCache.getSymbols(getOrCreateDebugObjectFile(), onlyGlobal).stream()
                            .map(symbol -> ((MachOSymtab.Entry) symbol).getNameInObject())
                            .collect(Collectors.toList());
        }

        @Override
        protected void setOutputKind(List<String> cmd) {
            switch (imageKind) {
                case STATIC_EXECUTABLE:
                    throw UserError.abort("%s does not support building static executable images.", OS.getCurrent().name());
                case SHARED_LIBRARY:
                    cmd.add("-shared");
                    if (Platform.includedIn(Platform.DARWIN.class)) {
                        cmd.add("-undefined");
                        cmd.add("dynamic_lookup");
                    }
                    break;
            }
        }
    }

    class WindowsCCLinkerInvocation extends CCLinkerInvocation {

        private final String imageName;

        WindowsCCLinkerInvocation(String imageName) {
            this.imageName = imageName;
        }

        @Override
        protected void setOutputKind(List<String> cmd) {
            switch (imageKind) {
                case EXECUTABLE:
                case STATIC_EXECUTABLE:
                    // cmd.add("/MT");
                    // Must use /MD in order to link with JDK native libraries built that way
                    cmd.add("/MD");
                    break;
                case SHARED_LIBRARY:
                    cmd.add("/MD");
                    cmd.add("/LD");
                    break;
                default:
                    VMError.shouldNotReachHere();
            }
        }

        @Override
        public List<String> getImageSymbols(boolean onlyGlobal) {
            return codeCache.getSymbols(getOrCreateDebugObjectFile(), onlyGlobal).stream()
                            .map(ObjectFile.Symbol::getName)
                            .collect(Collectors.toList());
        }

        @Override
        public List<String> getCommand() {
            List<String> compilerCmd = getCompilerCommand(additionalPreOptions);

            List<String> cmd = new ArrayList<>(compilerCmd);
            setOutputKind(cmd);

            for (Path staticLibrary : nativeLibs.getStaticLibraries()) {
                cmd.add(staticLibrary.toString());
            }

            /* Add linker options. */
            cmd.add("/link");
            cmd.add("/INCREMENTAL:NO");
            cmd.add("/NODEFAULTLIB:LIBCMT");

            /* Use page size alignment to support memory mapping of the image heap. */
            cmd.add("/FILEALIGN:4096");

            /* Put .lib and .exp files in a temp dir as we don't usually need them. */
            cmd.add("/IMPLIB:" + getTempDirectory().resolve(imageName + ".lib"));

            if (SubstrateOptions.GenerateDebugInfo.getValue() > 0) {
                cmd.add("/DEBUG");

                if (SubstrateOptions.DeleteLocalSymbols.getValue()) {
                    String pdbFile = imageName + ".pdb";
                    /* We don't need a full PDB file, so leave it in a temp dir ... */
                    cmd.add("/PDB:" + getTempDirectory().resolve(pdbFile));
                    /* ... and provide the stripped PDB file instead. */
                    cmd.add("/PDBSTRIPPED:" + getOutputFile().resolveSibling(pdbFile));
                }
            }

            if (!SubstrateOptions.RemoveUnusedSymbols.getValue()) {
                /* Disable removal as it is on by default. */
                cmd.add("/OPT:NOREF,NOICF");
            }

            // Add clibrary paths to command
            for (String libraryPath : nativeLibs.getLibraryPaths()) {
                cmd.add("/LIBPATH:" + libraryPath);
            }

            for (String library : nativeLibs.getLibraries()) {
                cmd.add(library + ".lib");
            }

            // Add required Windows Libraries
            cmd.add("advapi32.lib");
            cmd.add("ws2_32.lib");
            cmd.add("secur32.lib");
            cmd.add("iphlpapi.lib");
            cmd.add("userenv.lib");

            if (SubstrateOptions.EnableWildcardExpansion.getValue() && imageKind == NativeImageKind.EXECUTABLE) {
                /*
                 * Enable wildcard expansion in command line arguments, see
                 * https://docs.microsoft.com/en-us/cpp/c-language/expanding-wildcard-arguments.
                 */
                cmd.add("setargv.obj");
            }

            cmd.addAll(getNativeLinkerOptions());

            return cmd;
        }
    }

    LinkerInvocation getLinkerInvocation(Path outputDirectory, Path tempDirectory, String imageName) {
        CCLinkerInvocation inv;

        switch (ObjectFile.getNativeFormat()) {
            case MACH_O:
                inv = new DarwinCCLinkerInvocation();
                break;
            case PECOFF:
                inv = new WindowsCCLinkerInvocation(imageName);
                break;
            case ELF:
            default:
                inv = new BinutilsCCLinkerInvocation();
                break;
        }

        Path outputFile = outputDirectory.resolve(imageName + imageKind.getFilenameSuffix());
        UserError.guarantee(!Files.isDirectory(outputFile), "Cannot write image to %s. Path exists as directory. (Use -H:Name=<image name>)", outputFile);
        inv.setOutputFile(outputFile);
        inv.setTempDirectory(tempDirectory);

        inv.addLibPath(tempDirectory.toString());
        for (String libraryPath : nativeLibs.getLibraryPaths()) {
            inv.addLibPath(libraryPath);
        }

        for (String rPath : OptionUtils.flatten(",", SubstrateOptions.LinkerRPath.getValue())) {
            inv.addRPath(rPath);
        }

        Collection<String> libraries = nativeLibs.getLibraries();
        if (Platform.includedIn(Platform.LINUX.class) && ImageSingletons.lookup(LibCBase.class).getName().equals("bionic")) {
            // on Bionic LibC pthread.h and rt.h are included in standard library and adding them in
            // linker call produces error
            libraries = libraries.stream().filter(library -> !Arrays.asList("pthread", "rt").contains(library)).collect(Collectors.toList());
        }
        libraries.forEach(inv::addLinkedLibrary);

        for (Path filename : codeCache.getCCInputFiles(tempDirectory, imageName)) {
            inv.addInputFile(filename);
        }

        for (Path staticLibraryPath : nativeLibs.getStaticLibraries()) {
            inv.addInputFile(staticLibraryPath);
        }

        return inv;
    }

    private static List<String> diagnoseLinkerFailure(String linkerOutput) {
        List<String> potentialCauses = new ArrayList<>();
        if (linkerOutput.contains("access beyond end of merged section")) {
            potentialCauses.add("Native Image is using a linker that appears to be incompatible with the tool chain used to build the JDK static libraries. " +
                            "The latter is typically shown in the output of `java -Xinternalversion`.");
        }
        if (SubstrateOptions.ForceNoROSectionRelocations.getValue() && (linkerOutput.contains("fatal error: cannot find ") ||
                        linkerOutput.contains("error: invalid linker name in argument"))) {
            potentialCauses.add(SubstrateOptions.ForceNoROSectionRelocations.getName() + " option cannot be used if ld.gold linker is missing from the host system");
        }

        Pattern p = Pattern.compile(".*cannot find -l([^\\s]+)\\s.*", Pattern.DOTALL);
        Matcher m = p.matcher(linkerOutput);
        if (m.matches()) {
            OS os = OS.getCurrent();
            String libPrefix = os == OS.WINDOWS ? "" : "lib";
            String libSuffix = os == OS.WINDOWS ? ".lib" : ".a";
            potentialCauses.add(String.format("It appears as though %s%s%s is missing. Please install it.", libPrefix, m.group(1), libSuffix));
        }
        return potentialCauses;
    }

    @Override
    @SuppressWarnings("try")
    public LinkerInvocation write(DebugContext debug, Path outputDirectory, Path tempDirectory, String imageName, BeforeImageWriteAccessImpl config) {
        try (Indent indent = debug.logAndIndent("Writing native image")) {
            // 1. write the relocatable file
            write(debug, tempDirectory.resolve(imageName + ObjectFile.getFilenameSuffix()));
            if (NativeImageOptions.ExitAfterRelocatableImageWrite.getValue()) {
                return null;
            }
            // 2. run a command to make an executable of it
            /*
             * To support automated stub generation, we first search for a libsvm.a in the images
             * directory. FIXME: make this a per-image directory, to avoid clobbering on multiple
             * runs. It actually doesn't matter, because it's a .a file which will get absorbed into
             * the executable, but avoiding clobbering will help debugging.
             */

            LinkerInvocation inv = getLinkerInvocation(outputDirectory, tempDirectory, imageName);
            for (Function<LinkerInvocation, LinkerInvocation> fn : config.getLinkerInvocationTransformers()) {
                inv = fn.apply(inv);
            }
            List<String> cmd = inv.getCommand();
            String commandLine = SubstrateUtil.getShellCommandString(cmd, false);

            Process linkerProcess = null;
            try {
                ProcessBuilder linkerCommand = FileUtils.prepareCommand(cmd, inv.getTempDirectory());
                linkerCommand.redirectErrorStream(true);

                FileUtils.traceCommand(linkerCommand);

                linkerProcess = linkerCommand.start();

                List<String> lines;
                try (InputStream inputStream = linkerProcess.getInputStream()) {
                    lines = FileUtils.readAllLines(inputStream);
                    FileUtils.traceCommandOutput(lines);
                }

                int status = linkerProcess.waitFor();
                if (status != 0) {
                    String output = String.join(System.lineSeparator(), lines);
                    throw handleLinkerFailure("Linker command exited with " + status, commandLine, output);
                }

                Path imagePath = inv.getOutputFile();
                BuildArtifacts.singleton().add(imageKind.isExecutable ? ArtifactType.EXECUTABLE : ArtifactType.SHARED_LIB, imagePath);

                if (OS.getCurrent() == OS.WINDOWS && !imageKind.isExecutable) {
                    /* Provide an import library for the built shared library. */
                    String importLib = imageName + ".lib";
                    Path importLibPath = imagePath.resolveSibling(importLib);
                    Files.move(inv.getTempDirectory().resolve(importLib), importLibPath, StandardCopyOption.REPLACE_EXISTING);
                    BuildArtifacts.singleton().add(ArtifactType.IMPORT_LIB, importLibPath);
                }

                if (SubstrateOptions.GenerateDebugInfo.getValue() > 0) {
                    BuildArtifacts.singleton().add(ArtifactType.DEBUG_INFO, SubstrateOptions.getDebugInfoSourceCacheRoot());
                    if (OS.getCurrent() == OS.WINDOWS) {
                        BuildArtifacts.singleton().add(ArtifactType.DEBUG_INFO, imagePath.resolveSibling(imageName + ".pdb"));
                    } else {
                        BuildArtifacts.singleton().add(ArtifactType.DEBUG_INFO, imagePath);
                    }
                }
            } catch (IOException e) {
                throw handleLinkerFailure(e.toString(), commandLine, null);
            } catch (InterruptedException e) {
                throw new InterruptImageBuilding("Interrupted during native-image linking step for " + imageName);
            } finally {
                if (linkerProcess != null) {
                    linkerProcess.destroy();
                }
            }

            return inv;
        }
    }

    private static RuntimeException handleLinkerFailure(String message, String commandLine, String output) {
        Formatter buf = new Formatter();
        buf.format("There was an error linking the native image: %s%n%n", message);
        List<String> potentialCauses = output == null ? Collections.emptyList() : diagnoseLinkerFailure(output);
        if (!potentialCauses.isEmpty()) {
            int causeNum = 1;
            buf.format("Based on the linker command output, possible reasons for this include:%n");
            for (String cause : potentialCauses) {
                buf.format("%d. %s%n", causeNum, cause);
                causeNum++;
            }
            buf.format("%n");
        }
        buf.format("Linker command executed:%n%s", commandLine);
        if (output != null) {
            buf.format("%n%nLinker command output:%n%s", output);
        }
        throw new RuntimeException(buf.toString());
    }
}
