/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;

import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.macho.MachOSymtab;
import com.oracle.svm.core.BuildDirectoryProvider;
import com.oracle.svm.core.LinkerInvocation;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.c.libc.BionicLibC;
import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.CGlobalDataFeature;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.codegen.CCompilerInvoker;
import com.oracle.svm.hosted.c.libc.HostedLibCBase;
import com.oracle.svm.hosted.jdk.JNIRegistrationSupport;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionStability;

public abstract class CCLinkerInvocation implements LinkerInvocation {

    protected CCLinkerInvocation(AbstractImage.NativeImageKind imageKind, NativeLibraries nativeLibs, List<ObjectFile.Symbol> imageSymbols) {
        this.imageKind = imageKind;
        this.nativeLibs = nativeLibs;
        this.imageSymbols = imageSymbols;
    }

    public static class Options {
        @Option(help = "Pass the provided raw option that will be appended to the linker command to produce the final binary. The possible options are platform specific and passed through without any validation.", //
                        stability = OptionStability.STABLE)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> NativeLinkerOption = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.build());
    }

    protected final List<String> additionalPreOptions = new ArrayList<>();
    protected final List<String> nativeLinkerOptions = new ArrayList<>();
    protected final List<Path> inputFilenames = new ArrayList<>();
    protected final List<String> rpaths = new ArrayList<>();
    protected final List<String> libpaths = new ArrayList<>();
    protected final List<String> libs = new ArrayList<>();

    protected final AbstractImage.NativeImageKind imageKind;
    protected final NativeLibraries nativeLibs;

    private final List<ObjectFile.Symbol> imageSymbols;

    abstract String getSymbolName(ObjectFile.Symbol symbol);

    protected Path tempDirectory;
    protected Path outputFile;

    @Override
    public List<String> getImageSymbols(boolean onlyGlobal) {
        Stream<ObjectFile.Symbol> stream = imageSymbols.stream();
        if (onlyGlobal) {
            Set<String> globalHiddenSymbols = CGlobalDataFeature.singleton().getGlobalHiddenSymbols();
            stream = stream.filter(symbol -> symbol.isGlobal() && !globalHiddenSymbols.contains(symbol.getName()));
        }
        if (!SubstrateOptions.useLLVMBackend()) {
            stream = stream.filter(ObjectFile.Symbol::isDefined);
        }
        return stream.map(this::getSymbolName).collect(Collectors.toList());
    }

    @Override
    public List<Path> getInputFiles() {
        return Collections.unmodifiableList(inputFilenames);
    }

    @Override
    public void addInputFile(Path filename) {
        inputFilenames.add(filename);
    }

    @Override
    public void addInputFile(int index, Path filename) {
        inputFilenames.add(index, filename);
    }

    @Override
    public List<String> getLibPaths() {
        return Collections.unmodifiableList(libpaths);
    }

    @Override
    public void addLibPath(String libPath) {
        addLibPath(libpaths.size(), libPath);
    }

    @Override
    public void addLibPath(int index, String libPath) {
        if (!libPath.isEmpty()) {
            libpaths.add(index, libPath);
        }
    }

    @Override
    public List<String> getRPaths() {
        return Collections.unmodifiableList(rpaths);
    }

    @Override
    public void addRPath(String rPath) {
        addRPath(rpaths.size(), rPath);
    }

    @Override
    public void addRPath(int index, String rPath) {
        if (!rPath.isEmpty()) {
            rpaths.add(rPath);
        }
    }

    @Override
    public Path getOutputFile() {
        return outputFile;
    }

    @Override
    public void setOutputFile(Path out) {
        outputFile = out;
    }

    public void setTempDirectory(Path tempDirectory) {
        this.tempDirectory = tempDirectory;
    }

    @Override
    public Path getTempDirectory() {
        return tempDirectory;
    }

    @Override
    public List<String> getLinkedLibraries() {
        return Collections.unmodifiableList(libs);
    }

    @Override
    public void addLinkedLibrary(String libname) {
        libs.add(libname);
    }

    @Override
    public void addLinkedLibrary(int index, String libname) {
        libs.add(index, libname);
    }

    protected List<String> getCompilerCommand(List<String> options) {
        /* Relativize input files where applicable to avoid unintentional leaking of host paths. */
        Path[] inputPaths = inputFilenames.stream()
                        .map(path -> path.startsWith(tempDirectory) ? tempDirectory.relativize(path) : path)
                        .toArray(Path[]::new);
        return ImageSingletons.lookup(CCompilerInvoker.class).createCompilerCommand(options, outputFile, inputPaths);
    }

    protected abstract void setOutputKind(List<String> cmd);

    @Override
    public List<String> getCommand() {
        List<String> compilerCmd = getCompilerCommand(additionalPreOptions);

        List<String> cmd = new ArrayList<>(compilerCmd);
        setOutputKind(cmd);

        cmd.add("-v");
        for (String libpath : libpaths) {
            cmd.add("-L" + libpath);
        }
        for (String rpath : rpaths) {
            cmd.add("-Wl,-rpath");
            cmd.add("-Wl," + rpath);
        }

        cmd.addAll(getLibrariesCommand());

        cmd.addAll(getNativeLinkerOptions());

        /* RISC-V always needs the -latomic option */
        if (Platform.includedIn(Platform.RISCV64.class)) {
            cmd.add("-latomic");
        }

        return cmd;
    }

    protected List<String> getLibrariesCommand() {
        List<String> cmd = new ArrayList<>();
        for (String lib : libs) {
            if (lib.startsWith("-")) {
                cmd.add("-Wl," + lib.replace(" ", ","));
            } else {
                cmd.add("-l" + lib);
            }
        }
        return cmd;
    }

    @Override
    public void addNativeLinkerOption(String option) {
        nativeLinkerOptions.add(option);
    }

    protected List<String> getNativeLinkerOptions() {
        return Stream.of(nativeLinkerOptions, Options.NativeLinkerOption.getValue().values())
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
    }

    private static class BinutilsCCLinkerInvocation extends CCLinkerInvocation {

        private final boolean dynamicLibC = SubstrateOptions.StaticExecutableWithDynamicLibC.getValue();
        private final boolean staticLibCpp = SubstrateOptions.StaticLibStdCpp.getValue();
        private final boolean customStaticLibs = dynamicLibC || staticLibCpp;

        BinutilsCCLinkerInvocation(AbstractImage.NativeImageKind imageKind, NativeLibraries nativeLibs, List<ObjectFile.Symbol> symbols) {
            super(imageKind, nativeLibs, symbols);
            additionalPreOptions.add("-z");
            additionalPreOptions.add("noexecstack");

            /*
             * This is needed if the default linker is ld.lld from LLVM. On the GNU linker this
             * option is the default, so we can just set it unconditionally.
             */
            additionalPreOptions.add("-z");
            additionalPreOptions.add("notext");

            if (SubstrateOptions.ForceNoROSectionRelocations.getValue()) {
                additionalPreOptions.add("-fuse-ld=gold");
                additionalPreOptions.add("-Wl,--rosegment");
            }

            if (SubstrateOptions.RemoveUnusedSymbols.getValue()) {
                /* Perform garbage collection of unused input sections. */
                additionalPreOptions.add("-Wl,--gc-sections");
            }

            /* Use --version-script to control the visibility of image symbols. */
            try {
                StringBuilder exportedSymbols = new StringBuilder();
                exportedSymbols.append("{\n");
                /* Only exported symbols are global ... */
                Set<String> globalSymbols = Stream.concat(getImageSymbols(true).stream(), JNIRegistrationSupport.getShimLibrarySymbols()).collect(Collectors.toSet());
                if (!globalSymbols.isEmpty()) {
                    exportedSymbols.append("global:\n");
                    globalSymbols.forEach(symbol -> exportedSymbols.append('\"').append(symbol).append("\";\n"));
                }
                /* ... everything else is local. */
                exportedSymbols.append("local: *;\n");
                exportedSymbols.append("};");

                Path exportedSymbolsPath = nativeLibs.tempDirectory.resolve("exported_symbols.list");
                Files.write(exportedSymbolsPath, Collections.singleton(exportedSymbols.toString()));
                additionalPreOptions.add("-Wl,--version-script," + exportedSymbolsPath.toAbsolutePath());
            } catch (IOException e) {
                VMError.shouldNotReachHere(e);
            }

            additionalPreOptions.addAll(HostedLibCBase.singleton().getAdditionalLinkerOptions(imageKind));

            if (SubstrateOptions.DeleteLocalSymbols.getValue()) {
                additionalPreOptions.add("-Wl,-x");
            }
        }

        @Override
        String getSymbolName(ObjectFile.Symbol symbol) {
            return symbol.getName();
        }

        @Override
        protected void setOutputKind(List<String> cmd) {
            switch (imageKind) {
                case EXECUTABLE:
                    /* Export global symbols. */
                    cmd.add("-Wl,--export-dynamic");
                    break;
                case STATIC_EXECUTABLE:
                    if (!customStaticLibs) {
                        cmd.add("-static");
                    }
                    break;
                case SHARED_LIBRARY:
                    cmd.add("-shared");
                    break;
                default:
                    VMError.shouldNotReachHereUnexpectedInput(imageKind); // ExcludeFromJacocoGeneratedReport
            }
        }

        private static final Set<String> LIB_C_NAMES = Set.of("pthread", "dl", "rt", "m");

        @Override
        protected List<String> getLibrariesCommand() {
            List<String> cmd = new ArrayList<>();
            if (customStaticLibs) {
                cmd.add("-Wl,--push-state");
            }
            for (String lib : libs) {
                String linkingMode = null;
                if (dynamicLibC) {
                    linkingMode = LIB_C_NAMES.contains(lib) ? "dynamic" : "static";
                } else if (staticLibCpp) {
                    linkingMode = lib.equals("stdc++") ? "static" : "dynamic";
                }
                if (linkingMode != null) {
                    cmd.add("-Wl,-B" + linkingMode);
                }
                cmd.add("-l" + lib);
            }
            if (customStaticLibs) {
                cmd.add("-Wl,--pop-state");
            }

            // Make sure libgcc gets statically linked
            if (customStaticLibs) {
                cmd.add("-static-libgcc");
            }
            return cmd;
        }
    }

    private static class DarwinCCLinkerInvocation extends CCLinkerInvocation {

        DarwinCCLinkerInvocation(AbstractImage.NativeImageKind imageKind, NativeLibraries nativeLibs, List<ObjectFile.Symbol> symbols) {
            // Workaround building images with older Xcode with new libraries
            super(imageKind, nativeLibs, symbols);
            setLinkerFlags(nativeLibs, false);
        }

        private void setLinkerFlags(NativeLibraries nativeLibs, boolean useFallback) {
            additionalPreOptions.add("-Wl,-U,___darwin_check_fd_set_overflow");

            boolean useLld = false;
            if (useFallback) {
                Path lld = BuildDirectoryProvider.singleton().getHome().resolve("lib").resolve("svm").resolve("bin").resolve("ld64.lld").toAbsolutePath();
                if (Files.exists(lld)) {
                    useLld = true;
                    additionalPreOptions.add("-fuse-ld=" + lld);
                } else {
                    throw new RuntimeException("This should not happen. ld64.lld should be shipped as part of Native Image, please report.");
                }
            }

            if (!SubstrateOptions.useLLVMBackend() && !useLld) {
                /* flag is not understood by LLVM linker */
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
                VMError.shouldNotReachHere(e);
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
        public List<String> getFallbackCommand() {
            additionalPreOptions.clear();
            setLinkerFlags(nativeLibs, true);
            return getCommand();
        }

        @Override
        public boolean shouldRunFallback(String message) {
            if (Platform.includedIn(Platform.AARCH64.class)) {
                /* detect ld64 limitation around inserting branch islands, retry with LLVM linker */
                if (message.contains("branch out of range") || message.contains("Unable to insert branch island")) {
                    return true;
                }

                // slightly different message with "the new linker" (~Xcode 15), e.g.:
                // > ld: B/BL out of range -178777824 (max +/-128MB) to '_throw_internal_error'
                return message.contains("out of range");
            }
            return false;
        }

        @Override
        String getSymbolName(ObjectFile.Symbol symbol) {
            return ((MachOSymtab.Entry) symbol).getNameInObject();
        }

        @Override
        protected void setOutputKind(List<String> cmd) {
            switch (imageKind) {
                case STATIC_EXECUTABLE:
                    // checked in the definition of --static
                    throw VMError.shouldNotReachHereUnexpectedInput(imageKind);
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

    private static class WindowsCCLinkerInvocation extends CCLinkerInvocation {

        private final String imageName;

        WindowsCCLinkerInvocation(AbstractImage.NativeImageKind imageKind, NativeLibraries nativeLibs, List<ObjectFile.Symbol> symbols, String imageName) {
            super(imageKind, nativeLibs, symbols);
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
                    VMError.shouldNotReachHereUnexpectedInput(imageKind); // ExcludeFromJacocoGeneratedReport
            }
        }

        @Override
        String getSymbolName(ObjectFile.Symbol symbol) {
            return symbol.getName();
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

            if (SubstrateOptions.useDebugInfoGeneration()) {
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
            /* JDK-8295231 removed implicit linking via pragma directives in source files. */
            cmd.add("mswsock.lib");

            if (SubstrateOptions.EnableWildcardExpansion.getValue() && imageKind == AbstractImage.NativeImageKind.EXECUTABLE) {
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

    static LinkerInvocation getLinkerInvocation(AbstractImage.NativeImageKind imageKind, NativeLibraries nativeLibs, Path[] inputFiles,
                    Path outputDirectory, Path tempDirectory, String imageName, List<ObjectFile.Symbol> symbols) {
        CCLinkerInvocation inv;

        switch (ObjectFile.getNativeFormat()) {
            case MACH_O:
                inv = new DarwinCCLinkerInvocation(imageKind, nativeLibs, symbols);
                break;
            case PECOFF:
                inv = new WindowsCCLinkerInvocation(imageKind, nativeLibs, symbols, imageName);
                break;
            case ELF:
            default:
                inv = new BinutilsCCLinkerInvocation(imageKind, nativeLibs, symbols);
                break;
        }

        Path outputFile = outputDirectory.resolve(imageName + imageKind.getFilenameSuffix());
        UserError.guarantee(!Files.isDirectory(outputFile), "Cannot write image to %s. Path exists as directory (use '-o /path/to/image').", outputFile);
        inv.setOutputFile(outputFile);
        inv.setTempDirectory(tempDirectory);

        inv.addLibPath(tempDirectory.toString());
        for (String libraryPath : nativeLibs.getLibraryPaths()) {
            inv.addLibPath(libraryPath);
        }

        for (String rPath : SubstrateOptions.LinkerRPath.getValue().values()) {
            inv.addRPath(rPath);
        }

        Collection<String> libraries = nativeLibs.getLibraries();
        if (Platform.includedIn(Platform.LINUX.class) && LibCBase.targetLibCIs(BionicLibC.class)) {
            // on Bionic LibC pthread.h and rt.h are included in standard library and adding them in
            // linker call produces error
            libraries = libraries.stream().filter(library -> !Arrays.asList("pthread", "rt").contains(library)).collect(Collectors.toList());
        }
        libraries.forEach(inv::addLinkedLibrary);

        for (Path filename : inputFiles) {
            inv.addInputFile(filename);
        }

        for (Path staticLibraryPath : nativeLibs.getStaticLibraries()) {
            inv.addInputFile(staticLibraryPath);
        }

        return inv;
    }
}
