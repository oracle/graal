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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.oracle.svm.core.c.libc.LibCBase;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.macho.MachOSymtab;
import com.oracle.svm.core.LinkerInvocation;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.BeforeImageWriteAccessImpl;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.util.FileUtils;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;

public abstract class NativeBootImageViaCC extends NativeBootImage {

    public NativeBootImageViaCC(NativeImageKind k, HostedUniverse universe, HostedMetaAccess metaAccess, NativeLibraries nativeLibs, NativeImageHeap heap, NativeImageCodeCache codeCache,
                    List<HostedMethod> entryPoints, ClassLoader imageClassLoader) {
        super(k, universe, metaAccess, nativeLibs, heap, codeCache, entryPoints, imageClassLoader);
    }

    public NativeImageKind getOutputKind() {
        return kind;
    }

    private static boolean removeUnusedSymbols() {
        if (SubstrateOptions.RemoveUnusedSymbols.hasBeenSet()) {
            return SubstrateOptions.RemoveUnusedSymbols.getValue();
        }
        /*
         * The Darwin linker sometimes segfaults when option -dead_strip is used. Thus, Linux is the
         * only platform were RemoveUnusedSymbols can be safely enabled per default.
         */
        return Platform.includedIn(Platform.LINUX.class);
    }

    class BinutilsCCLinkerInvocation extends CCLinkerInvocation {

        BinutilsCCLinkerInvocation() {
            additionalPreOptions.add("-z");
            additionalPreOptions.add("noexecstack");

            if (removeUnusedSymbols()) {
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
                    List<String> exportedSymbols = new ArrayList<>();
                    exportedSymbols.add("{");
                    codeCache.getGlobalSymbols(getOrCreateDebugObjectFile()).stream()
                                    .map(symbol -> "\"" + symbol.getName() + "\";")
                                    .forEachOrdered(exportedSymbols::add);
                    exportedSymbols.add("};");
                    Path exportedSymbolsPath = nativeLibs.tempDirectory.resolve("exported_symbols.list");
                    Files.write(exportedSymbolsPath, exportedSymbols);
                    additionalPreOptions.add("-Wl,--dynamic-list");
                    additionalPreOptions.add("-Wl," + exportedSymbolsPath.toAbsolutePath());
                } catch (IOException e) {
                    VMError.shouldNotReachHere();
                }
            }

            if (SubstrateOptions.DeleteLocalSymbols.getValue()) {
                additionalPreOptions.add("-Wl,-x");
            }

            LibCBase currentLibc = ImageSingletons.lookup(LibCBase.class);
            additionalPreOptions.addAll(currentLibc.getLinkerPreOptions());
        }

        @Override
        protected void setOutputKind(List<String> cmd) {
            switch (kind) {
                case EXECUTABLE:
                    break;
                case STATIC_EXECUTABLE:
                    cmd.add("-static");
                    break;
                case SHARED_LIBRARY:
                    cmd.add("-shared");
                    break;
                default:
                    VMError.shouldNotReachHere();
            }
        }

    }

    class DarwinCCLinkerInvocation extends CCLinkerInvocation {

        DarwinCCLinkerInvocation() {
            if (!SubstrateOptions.CompilerBackend.getValue().equals("llvm")) {
                additionalPreOptions.add("-Wl,-no_compact_unwind");
            }

            if (removeUnusedSymbols()) {
                /* Remove functions and data unreachable by entry points. */
                additionalPreOptions.add("-Wl,-dead_strip");
            }

            /*
             * On Darwin we use -exported_symbols_list to ensure only our defined entrypoints end up
             * as global symbols in the dynamic symbol table of the image.
             */
            try {
                List<ObjectFile.Symbol> exportedSymbols = codeCache.getGlobalSymbols(getOrCreateDebugObjectFile());
                Path exportedSymbolsPath = nativeLibs.tempDirectory.resolve("exported_symbols.list");
                Files.write(exportedSymbolsPath, exportedSymbols.stream()
                                .map(symbol -> ((MachOSymtab.Entry) symbol).getNameInObject())
                                .collect(Collectors.toList()));
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
        protected void setOutputKind(List<String> cmd) {
            switch (kind) {
                case STATIC_EXECUTABLE:
                    throw UserError.abort(OS.getCurrent().name() + " does not support building static executable images.");
                case SHARED_LIBRARY:
                    cmd.add("-shared");
                    if (Platform.includedIn(InternalPlatform.DARWIN_JNI_AND_SUBSTITUTIONS.class)) {
                        cmd.add("-undefined");
                        cmd.add("dynamic_lookup");
                    }
                    break;
            }
        }
    }

    class WindowsCCLinkerInvocation extends CCLinkerInvocation {

        WindowsCCLinkerInvocation() {
            setCompilerCommand("CL");
        }

        @Override
        protected void setOutputKind(List<String> cmd) {
            switch (kind) {
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
        public List<String> getCommand() {
            ArrayList<String> cmd = new ArrayList<>();
            cmd.add(getCompilerCommand());

            setOutputKind(cmd);

            // Add debugging info
            cmd.add("/Zi");

            if (removeUnusedSymbols()) {
                additionalPreOptions.add("/OPT:REF");
            }

            if (SubstrateOptions.DeleteLocalSymbols.getValue()) {
                cmd.add("/PDBSTRIPPED");
            }

            cmd.add("/Fe" + outputFile.toString());

            cmd.addAll(inputFilenames);
            for (Path staticLibrary : nativeLibs.getStaticLibraries()) {
                cmd.add(staticLibrary.toString());
            }

            cmd.add("/link /INCREMENTAL:NO /NODEFAULTLIB:LIBCMT");

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
                inv = new WindowsCCLinkerInvocation();
                break;
            case ELF:
            default:
                inv = new BinutilsCCLinkerInvocation();
                break;
        }

        Path outputFile = outputDirectory.resolve(imageName + getBootImageKind().getFilenameSuffix());
        UserError.guarantee(!Files.isDirectory(outputFile), "Cannot write image to %s. Path exists as directory. (Use -H:Name=<image name>)", outputFile);
        inv.setOutputFile(outputFile);
        inv.setOutputKind(getOutputKind());

        /*
         * Libraries defined via @CLibrary annotations are added at the end of the list of libraries
         * so that the written object file AND the static JDK libraries can depend on them.
         */
        nativeLibs.processAnnotated();

        inv.addLibPath(tempDirectory.toString());
        for (String libraryPath : nativeLibs.getLibraryPaths()) {
            inv.addLibPath(libraryPath);
        }

        for (String rPath : OptionUtils.flatten(",", SubstrateOptions.LinkerRPath.getValue())) {
            inv.addRPath(rPath);
        }

        for (String library : nativeLibs.getLibraries()) {
            inv.addLinkedLibrary(library);
        }

        for (String filename : codeCache.getCCInputFiles(tempDirectory, imageName)) {
            inv.addInputFile(filename);
        }

        for (Path staticLibraryPath : nativeLibs.getStaticLibraries()) {
            inv.addInputFile(staticLibraryPath.toString());
        }

        return inv;
    }

    private static List<String> diagnoseLinkerFailure(String linkerOutput) {
        List<String> potentialCauses = new ArrayList<>();
        if (linkerOutput.contains("access beyond end of merged section")) {
            potentialCauses.add("Native Image is using a linker that appears to be incompatible with the tool chain used to build the JDK static libraries. " +
                            "The latter is typically shown in the output of `java -Xinternalversion`.");
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

            // Since we're using FileChannel.map, and we can't unmap the file,
            // we have to copy the file or the linker will fail to open it.
            if (OS.getCurrent() == OS.WINDOWS) {
                Path tempFile = tempDirectory.resolve(imageName + ".tmp");
                write(tempFile);
                try {
                    Files.copy(tempFile, tempDirectory.resolve(imageName + ObjectFile.getFilenameSuffix()));
                    // Files.delete(tempFile);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to create Object file " + e);
                }
            } else {
                write(tempDirectory.resolve(imageName + ObjectFile.getFilenameSuffix()));
            }
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
            StringBuilder sb = new StringBuilder();
            for (String s : cmd) {
                if (s.indexOf(' ') != -1) {
                    // Quote command line arguments that contain a space
                    sb.append('\'').append(s).append('\'');
                } else {
                    sb.append(s);
                }
                sb.append(' ');
            }
            String commandLine = sb.toString().trim();
            try (DebugContext.Scope s = debug.scope("InvokeCC")) {
                debug.log("Running command: %s", sb);

                if (NativeImageOptions.MachODebugInfoTesting.getValue()) {
                    System.out.printf("Testing Mach-O debuginfo generation - SKIP %s%n", commandLine);
                    return inv;
                } else {
                    ProcessBuilder pb = new ProcessBuilder().command(cmd);
                    pb.directory(tempDirectory.toFile());
                    pb.redirectErrorStream(true);
                    int status;
                    ByteArrayOutputStream output;
                    try {
                        Process p = pb.start();

                        output = new ByteArrayOutputStream();
                        FileUtils.drainInputStream(p.getInputStream(), output);
                        status = p.waitFor();
                    } catch (IOException | InterruptedException e) {
                        throw handleLinkerFailure(e.toString(), commandLine, null);
                    }

                    debug.log("%s", output);

                    if (status != 0) {
                        throw handleLinkerFailure("Linker command exited with " + status, commandLine, output.toString());
                    }
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
            buf.format("%n%nLinker command ouput:%n%s", output);
        }
        throw new RuntimeException(buf.toString());
    }
}
