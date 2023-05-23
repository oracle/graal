/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.driver.launcher;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;


public class BundleLauncher {
    private static final String BUNDLE_TEMP_DIR_PREFIX = "bundleRoot-";

    private static Path rootDir;
    private static Path inputDir;
    private static Path stageDir;
    private static Path auxiliaryDir;
    private static Path classPathDir;
    private static Path modulePathDir;


    public static void main(String[] args) {
        List<String> command = new ArrayList<>();
        Path javaExecutable = getJavaExecutable().toAbsolutePath().normalize();
        command.add(javaExecutable.toString());

        String bundleFilePath = BundleLauncher.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        unpackBundle(Path.of(bundleFilePath));

        Path environmentFile = stageDir.resolve("environment.json");
        if (Files.isReadable(environmentFile)) {
            try (Reader reader = Files.newBufferedReader(environmentFile)) {
                //new EnvironmentParser(nativeImage.imageBuilderEnvironment).parseAndRegister(reader);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read bundle-file " + environmentFile, e);
            }
        }


        List<String> classpath = new ArrayList<>();
        if (Files.isDirectory(classPathDir)) {
            try (Stream<Path> walk = Files.walk(classPathDir, 1)) {
                walk.filter(path -> path.toString().endsWith(".jar") || (Files.isDirectory(path) && !path.equals(classPathDir)))
                        .map(Path::toString)
                        .forEach(classpath::add);
            } catch (IOException e) {
                throw new RuntimeException("Failed to iterate through directory " + classPathDir, e);
            }

            classpath.add(classPathDir.toString());
            command.add("-cp");
            command.add(String.join(File.pathSeparator, classpath));
        }


        List<String> modulePath = new ArrayList<>();
        if (Files.isDirectory(modulePathDir)) {
            try (Stream<Path> walk = Files.walk(modulePathDir, 1)) {
                walk.filter(Files::isDirectory)
                        .filter(path -> !path.equals(modulePathDir))
                        .map(Path::toString)
                        .forEach(modulePath::add);
            } catch (IOException e) {
                throw new RuntimeException("Failed to iterate through directory " + modulePathDir, e);
            }

            if(!modulePath.isEmpty()) {
                command.add("-p");
                command.add(String.join(File.pathSeparator, modulePath));
            }
        }

        Path buildArgsFile = stageDir.resolve("run.json");
        try (Reader reader = Files.newBufferedReader(buildArgsFile)) {
            command.addAll(parseArray(readFully(reader)));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read bundle-file " + buildArgsFile, e);
        }

        if (System.getenv("BUNDLE_LAUNCHER_VERBOSE") != null) {
            System.out.println("Exec: " + String.join(" ", command));
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        Process p = null;
        try {
            p = pb.inheritIO().start();
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            showError("Failed to run bundled application");
        } finally {
            if(p != null) {
                p.destroy();
            }
        }
    }


    private static final Path buildTimeJavaHome = Paths.get(System.getProperty("java.home"));

    private static Path getJavaExecutable() {
        Path binJava = Paths.get("bin", System.getProperty("os.name").contains("Windows") ? "java.exe" : "java");

        Path javaCandidate = buildTimeJavaHome.resolve(binJava);
        if (Files.isExecutable(javaCandidate)) {
            return javaCandidate;
        }

        javaCandidate = Paths.get(".").resolve(binJava);
        if (Files.isExecutable(javaCandidate)) {
            return javaCandidate;
        }

        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null) {
            showError("No " + binJava + " and no environment variable JAVA_HOME");
        }
        try {
            javaCandidate = Paths.get(javaHome).resolve(binJava);
            if (Files.isExecutable(javaCandidate)) {
                return javaCandidate;
            }
        } catch (InvalidPathException e) {
            /* fallthrough */
        }
        showError("No " + binJava + " and invalid JAVA_HOME=" + javaHome);
        return null;
    }

    private static void showError(String s) {
        System.err.println("Error: " + s);
        System.exit(1);
    }

    private static String readFully(final Reader reader) throws IOException {
        final char[] arr = new char[1024];
        final StringBuilder sb = new StringBuilder();

        try {
            int numChars;
            while ((numChars = reader.read(arr, 0, arr.length)) > 0) {
                sb.append(arr, 0, numChars);
            }
        } finally {
            reader.close();
        }

        return sb.toString();
    }


    private static final int STATE_EMPTY = 0;
    private static final int STATE_ELEMENT_PARSED = 1;
    private static final int STATE_COMMA_PARSED = 2;

    private static int pos;

    private static List<String> parseArray(String jsonArray) {
        List<String> result = new ArrayList<>();
        int state = STATE_EMPTY;
        pos = 0;

        skipWhiteSpace(jsonArray);
        if(jsonArray.charAt(pos) != '[') {
            throw new RuntimeException("Expected [ but found " + jsonArray.charAt(pos));
        }
        pos++;

        while (pos < jsonArray.length()) {
            pos = skipWhiteSpace(jsonArray);

            switch (jsonArray.charAt(pos)) {
                case ',' -> {
                    if (state != STATE_ELEMENT_PARSED) {
                        throw new RuntimeException("Trailing comma is not allowed in JSON");
                    }
                    state = STATE_COMMA_PARSED;
                    pos++;
                }
                case ']' -> {
                    if (state == STATE_COMMA_PARSED) {
                        throw new RuntimeException("Trailing comma is not allowed in JSON");
                    }
                    return result;
                }
                default -> {
                    if (state == STATE_ELEMENT_PARSED) {
                        throw new RuntimeException("Expected , or ] but found " + jsonArray.charAt(pos));
                    }
                    result.add(parseString(jsonArray));
                    state = STATE_ELEMENT_PARSED;
                }
            }
        }

        throw new RuntimeException("Expected , or ] but found eof");
    }

    private static String parseString(String json) {
        // String buffer is only instantiated if string contains escape sequences.
        int start = ++pos;
        StringBuilder sb = null;

        while (pos < json.length()) {
            final int c = json.charAt(pos);
            pos++;
            if (c <= 0x1f) {
                // Characters < 0x1f are not allowed in JSON strings.
                throw new RuntimeException("String contains control character");

            } else if (c == '\\') {
                if (sb == null) {
                    sb = new StringBuilder(pos - start + 16);
                }
                sb.append(json, start, pos - 1);
                sb.append(parseEscapeSequence(json));
                start = pos;

            } else if (c == '"') {
                if (sb != null) {
                    sb.append(json, start, pos - 1);
                    return sb.toString();
                }
                return json.substring(start, pos - 1);
            }
        }

        throw new RuntimeException("Missing close quote");
    }

    private static char parseEscapeSequence(String json) {
        final int c = json.charAt(pos);
        pos++;
        return switch (c) {
            case '"' -> '"';
            case '\\' -> '\\';
            case '/' -> '/';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'u' -> parseUnicodeEscape(json);
            default -> throw new RuntimeException("Invalid escape character");
        };
    }

    private static char parseUnicodeEscape(String json) {
        return (char) (parseHexDigit(json) << 12 | parseHexDigit(json) << 8 | parseHexDigit(json) << 4 | parseHexDigit(json));
    }

    private static int parseHexDigit(String json) {
        final int c = json.charAt(pos);
        pos++;
        if (c >= '0' && c <= '9') {
            return c - '0';
        } else if (c >= 'A' && c <= 'F') {
            return c + 10 - 'A';
        } else if (c >= 'a' && c <= 'f') {
            return c + 10 - 'a';
        }
        throw new RuntimeException("Invalid hex digit");
    }

    private static int skipWhiteSpace(String str) {
        while (pos < str.length()) {
            switch (str.charAt(pos)) {
                case '\t', '\r', '\n', ' ' -> pos++;
                default -> {
                    return pos;
                }
            }
        }

        return pos;
    }

    private static final AtomicBoolean deleteBundleRoot = new AtomicBoolean();
    private static Path createBundleRootDir() throws IOException {
        Path bundleRoot = Files.createTempDirectory(BUNDLE_TEMP_DIR_PREFIX);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            deleteBundleRoot.set(true);
            deleteAllFiles(bundleRoot);
        }));
        return bundleRoot;
    }


    private static final String deletedFileSuffix = ".deleted";
    private static boolean isDeletedPath(Path toDelete) {
        return toDelete.getFileName().toString().endsWith(deletedFileSuffix);
    }

    private static void deleteAllFiles(Path toDelete) {
        try {
            Path deletedPath = toDelete;
            if (!isDeletedPath(deletedPath)) {
                deletedPath = toDelete.resolveSibling(toDelete.getFileName() + deletedFileSuffix);
                Files.move(toDelete, deletedPath);
            }
            Files.walk(deletedPath).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException e) {
            //if (isVerbose()) {
                System.out.println("Could not recursively delete path: " + toDelete);
                e.printStackTrace();
            //}
        }
    }


    private static void unpackBundle(Path bundleFilePath) {
        try {
            rootDir = createBundleRootDir();
            inputDir = rootDir.resolve("input");

            try (JarFile archive = new JarFile(bundleFilePath.toFile())) {
                Enumeration<JarEntry> jarEntries = archive.entries();
                while (jarEntries.hasMoreElements() && !deleteBundleRoot.get()) {
                    JarEntry jarEntry = jarEntries.nextElement();
                    Path bundleEntry = rootDir.resolve(jarEntry.getName());
                    if (bundleEntry.startsWith(inputDir)) {
                        try {
                            Path bundleFileParent = bundleEntry.getParent();
                            if (bundleFileParent != null) {
                                Files.createDirectories(bundleFileParent);
                            }
                            Files.copy(archive.getInputStream(jarEntry), bundleEntry);
                        } catch (IOException e) {
                            throw new RuntimeException("Unable to copy " + jarEntry.getName() + " from bundle " + bundleEntry + " to " + bundleEntry, e);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to expand bundle directory layout from bundle file " + bundleFilePath, e);
        }

        if (deleteBundleRoot.get()) {
            /* Abort image build request without error message and exit with 0 */
            throw new RuntimeException("");
        }

        try {
            inputDir = rootDir.resolve("input");
            stageDir = Files.createDirectories(inputDir.resolve("stage"));
            auxiliaryDir = Files.createDirectories(inputDir.resolve("auxiliary"));
            Path classesDir = inputDir.resolve("classes");
            classPathDir = Files.createDirectories(classesDir.resolve("cp"));
            modulePathDir = Files.createDirectories(classesDir.resolve("p"));
        } catch (IOException e) {
            throw new RuntimeException("Unable to create bundle directory layout", e);
        }
    }
}
