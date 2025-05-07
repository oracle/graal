/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.ffi.nfi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.graalvm.home.HomeFinder;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.ffi.NativeAccess;
import com.oracle.truffle.espresso.ffi.NativeSignature;
import com.oracle.truffle.espresso.ffi.Pointer;
import com.oracle.truffle.espresso.ffi.SignatureCallNode;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoProperties;
import com.oracle.truffle.espresso.substitutions.Collect;

public final class NFISulongNativeAccess extends NFINativeAccess {

    @Override
    protected String nfiStringSignature(NativeSignature nativeSignature, boolean forFallbackSymbol) {
        String res = super.nfiStringSignature(nativeSignature, forFallbackSymbol);
        if (!forFallbackSymbol) {
            return "with llvm " + res;
        }
        return res;
    }

    @Override
    public boolean hasFallbackSymbols() {
        return true;
    }

    @TruffleBoundary
    private static boolean isSulongSymbolClass(Object symbolClass) {
        return "LLVMLanguage".equals(((Class<?>) symbolClass).getSimpleName());
    }

    @Override
    public boolean isFallbackSymbol(TruffleObject symbol) {
        return isFallbackSymbol(symbol, InteropLibrary.getUncached());
    }

    private NFINativeAccess fallback;

    @Override
    public NativeAccess getFallbackAccess() {
        if (fallback == null) {
            // races are benign
            fallback = new NFINativeAccess(env);
        }
        return fallback;
    }

    static boolean isFallbackSymbol(TruffleObject symbol, InteropLibrary interop) {
        Object symbolClass = getSymbolClass(symbol, interop);
        return symbolClass == null || !isSulongSymbolClass(symbolClass);
    }

    private static Object getSymbolClass(TruffleObject symbol, InteropLibrary interop) {
        if (!interop.hasLanguage(symbol)) {
            return null;
        }
        try {
            return interop.getLanguage(symbol);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    @Override
    public SignatureCallNode createSignatureCall(NativeSignature nativeSignature) {
        return NFISulongSignatureCallNode.create(this, nativeSignature);
    }

    NFISulongNativeAccess(TruffleLanguage.Env env) {
        super(env);
    }

    @Override
    protected @Pointer TruffleObject loadLibrary0(Path libraryPath) {
        String nfiSource = String.format("with llvm load(RTLD_LAZY|RTLD_LOCAL) '%s'", libraryPath);
        return loadLibraryHelper(nfiSource);
    }

    @Override
    public @Pointer TruffleObject loadDefaultLibrary() {
        return null; // not supported
    }

    /**
     * Returns the Java version of the specified Java home directory, as declared in the 'release'
     * file.
     * 
     * @return Java version as declared in the JAVA_VERSION property in the 'release' file, or null
     *         otherwise.
     */
    static String getJavaVersion(Path javaHome) {
        Path releaseFile = javaHome.resolve("release");
        if (!Files.isRegularFile(releaseFile)) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(releaseFile)) {
                if (line.startsWith("JAVA_VERSION=")) {
                    String version = line.substring("JAVA_VERSION=".length()).trim();
                    // JAVA_VERSION=<value> may be quoted or unquoted, both cases are supported.
                    if (version.length() > 2 && version.startsWith("\"") && version.endsWith("\"")) {
                        version = version.substring(1, version.length() - 1);
                    }
                    return version;
                }
            }
        } catch (IOException e) {
            // cannot read file, skip
        }
        return null; // JAVA_VERSION not found
    }

    /**
     * Finds a folder containing libraries with LLVM bitcode compatible with the specified Java
     * version. First checks the 'default' folder, as it matches the Java version of the parent
     * GraalVM. Then checks remaining folders under llvmRoot.
     */
    private static Path legacyGraalvmllvmBootLibraryPath(String javaVersion, Path llvmRoot) {
        // Try $ESPRESSO_HOME/lib/llvm/default first.
        Path llvmDefault = llvmRoot.resolve("default");
        String llvmDefaultVersion = getJavaVersion(llvmDefault);
        if (javaVersion.equals(llvmDefaultVersion)) {
            return llvmDefault;
        }

        /*
         * Try directories in $ESPRESSO_HOME/lib/llvm/* for libraries with LLVM-bitcode compatible
         * with the specified Java version.
         */
        if (!Files.exists(llvmRoot)) {
            return null; // no folders with Java libraries + embedded LLVM-bitcode.
        }

        List<Path> sortedPaths;
        try (var stream = Files.list(llvmRoot)) {
            // Order must be deterministic.
            sortedPaths = stream //
                            .filter(f -> !llvmDefault.equals(f) && Files.isDirectory(f)) //
                            .sorted() //
                            .toList();
        } catch (IOException e) {
            throw EspressoError.shouldNotReachHere(e.getMessage(), e);
        }

        for (Path llvmImpl : sortedPaths) {
            String llvmImplVersion = getJavaVersion(llvmImpl);
            LOGGER.fine(() -> "Checking " + llvmImpl + " with Java version: " + llvmImplVersion);
            if (javaVersion.equals(llvmImplVersion)) {
                return llvmImpl;
            }
        }

        return null; // not found
    }

    /**
     * Overrides the JVMLibraryPath and BootLibraryPath properties only if the options are not set
     * by the user.
     *
     * Looks for libraries with embedded LLVM bitcode located in languages/java/lib/llvm/* , every
     * folder represents different Java versions and/or different configurations.
     */
    @Override
    public void updateEspressoProperties(EspressoProperties.Builder builder, OptionValues options) {
        if (options.hasBeenSet(EspressoOptions.BootLibraryPath)) {
            LOGGER.info("--java.BootLibraryPath was set by the user, skipping override for " + Provider.ID);
        } else {
            String targetJavaVersion = getJavaVersion(builder.javaHome());
            if (targetJavaVersion == null) {
                LOGGER.warning("Cannot determine the Java version for '" + builder.javaHome() + "'. The default --java.BootLibraryPath will be used.");
            } else {
                Path espressoHome = HomeFinder.getInstance().getLanguageHomes().get(EspressoLanguage.ID);
                if (espressoHome != null && Files.isDirectory(espressoHome)) {
                    Path llvmRoot = espressoHome.resolve("lib").resolve("llvm");
                    Path llvmBootLibraryPath = legacyGraalvmllvmBootLibraryPath(targetJavaVersion, llvmRoot);
                    if (llvmBootLibraryPath == null) {
                        LOGGER.warning("Couldn't find libraries with LLVM bitcode for Java version '" + targetJavaVersion + "'. The default --java.BootLibraryPath will be used.");
                    } else {
                        builder.bootLibraryPath(Collections.singletonList(llvmBootLibraryPath));
                    }
                } else {
                    Path llvmRoot = builder.javaHome().resolve("lib").resolve("llvm");
                    if (Files.isDirectory(llvmRoot)) {
                        builder.bootLibraryPath(Collections.singletonList(llvmRoot));
                    } else {
                        LOGGER.warning("Couldn't find libraries with LLVM bitcode. The default --java.BootLibraryPath will be used.");
                    }
                }
            }
        }

        /*
         * The location of Espresso's libjvm is updated to point to exactly the same file inside the
         * Espresso home so it can be loaded by Sulong without extra configuration of the Truffle
         * file system.
         */
        if (options.hasBeenSet(EspressoOptions.JVMLibraryPath)) {
            LOGGER.info("--java.JVMLibraryPath was set by the user, skipping override for " + Provider.ID);
        } else {
            builder.jvmLibraryPath(Collections.singletonList(builder.espressoLibs()));
        }
    }

    @Collect(NativeAccess.class)
    public static final class Provider implements NativeAccess.Provider {

        public static final String ID = "nfi-llvm";

        @Override
        public String id() {
            return ID;
        }

        @Override
        public NativeAccess create(TruffleLanguage.Env env) {
            return new NFISulongNativeAccess(env);
        }
    }
}
