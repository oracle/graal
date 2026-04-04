/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jni;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.graalvm.nativeimage.Platform;

import com.oracle.svm.core.jni.access.JNINativeLinkage;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.util.FileUtils;
import com.oracle.svm.shared.util.LogUtils;

final class DarwinBuiltinJNISymbolSupport {
    private static final DarwinBuiltinJNISymbolSupport SINGLETON = new DarwinBuiltinJNISymbolSupport();

    private volatile Set<String> availableSymbols;
    private volatile boolean scanFailed;
    private final Set<String> warnedSymbols = ConcurrentHashMap.newKeySet();

    private DarwinBuiltinJNISymbolSupport() {
    }

    static String builtInSymbolName(JNINativeLinkage linkage) {
        return SINGLETON.getBuiltInSymbolName(linkage);
    }

    private String getBuiltInSymbolName(JNINativeLinkage linkage) {
        if (!Platform.includedIn(Platform.DARWIN.class) || !linkage.isBuiltInFunction()) {
            return linkage.isBuiltInFunction() ? linkage.getShortName() : null;
        }
        if (scanFailed) {
            return linkage.getShortName();
        }
        Set<String> symbols = getAvailableSymbols();
        String shortName = linkage.getShortName();
        if (symbols.contains(shortName)) {
            return shortName;
        }
        String longName = linkage.getLongName();
        if (symbols.contains(longName)) {
            return longName;
        }
        if (warnedSymbols.add(shortName)) {
            LogUtils.warning("JNI built-in symbol %s is absent from the Darwin static JDK archives. Native Image will use runtime lookup for %s instead of a link-time reference.",
                            shortName, shortName);
        }
        return null;
    }

    private Set<String> getAvailableSymbols() {
        Set<String> symbols = availableSymbols;
        if (symbols == null && !scanFailed) {
            synchronized (this) {
                symbols = availableSymbols;
                if (symbols == null && !scanFailed) {
                    try {
                        symbols = scanAvailableSymbols();
                        availableSymbols = symbols;
                    } catch (IOException | InterruptedException e) {
                        if (e instanceof InterruptedException) {
                            Thread.currentThread().interrupt();
                        }
                        scanFailed = true;
                        LogUtils.warning("Could not scan Darwin static JDK archives for JNI symbols. Built-in JNI methods will keep using link-time symbol references. Cause: %s",
                                        e.getMessage());
                        return Set.of();
                    }
                }
            }
        }
        return symbols == null ? Set.of() : symbols;
    }

    private static Set<String> scanAvailableSymbols() throws IOException, InterruptedException {
        LinkedHashSet<String> symbols = new LinkedHashSet<>();
        for (String libraryPath : NativeLibraries.singleton().getLibraryPaths()) {
            try (Stream<Path> paths = Files.list(Paths.get(libraryPath))) {
                for (Path path : paths.filter(Files::isRegularFile).filter(candidate -> candidate.getFileName().toString().endsWith(".a")).toList()) {
                    symbols.addAll(readDefinedSymbols(path));
                }
            }
        }
        return Set.copyOf(symbols);
    }

    private static Set<String> readDefinedSymbols(Path staticLibrary) throws IOException, InterruptedException {
        List<String> commandLine = List.of("nm", "-g", "-U", "-j", staticLibrary.toString());
        ProcessBuilder command = FileUtils.prepareCommand(commandLine, null).redirectErrorStream(true);
        FileUtils.traceCommand(command);
        Process process = command.start();
        try (Closeable _ = process::destroy; InputStream inputStream = process.getInputStream()) {
            List<String> lines = FileUtils.readAllLines(inputStream);
            FileUtils.traceCommandOutput(lines);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Native symbol scan failed with exit code " + exitCode + " for " + staticLibrary.getFileName());
            }
            LinkedHashSet<String> symbols = new LinkedHashSet<>();
            for (String line : lines) {
                String symbol = normalizeNmOutput(line);
                if (symbol != null) {
                    symbols.add(symbol);
                }
            }
            return symbols;
        }
    }

    private static String normalizeNmOutput(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.endsWith(":")) {
            return null;
        }
        if (trimmed.startsWith("_")) {
            return trimmed.substring(1);
        }
        return trimmed;
    }
}
