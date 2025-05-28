/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import com.oracle.svm.util.LogUtils;

public class ArchiveSupport {

    final boolean isVerbose;

    public ArchiveSupport(boolean isVerbose) {
        this.isVerbose = isVerbose;
    }

    public void compressDirToJar(Path inputRootDir, Path outputFilePath, Manifest manifest) {
        try (JarOutputStream jarOutStream = new JarOutputStream(Files.newOutputStream(outputFilePath), manifest)) {
            try (Stream<Path> walk = Files.walk(inputRootDir)) {
                walk.filter(Predicate.not(Files::isDirectory)).forEach(entry -> addFileToJar(inputRootDir, entry, outputFilePath, jarOutStream));
            }
        } catch (IOException e) {
            throw VMError.shouldNotReachHere("Failed to create JAR file " + outputFilePath.getFileName(), e);
        }
    }

    public void addFileToJar(Path inputDir, Path inputFile, Path outputFilePath, JarOutputStream jarOutStream) {
        String jarEntryName = inputDir.relativize(inputFile).toString();
        JarEntry entry = new JarEntry(jarEntryName.replace(File.separator, "/"));
        try {
            entry.setTime(Files.getLastModifiedTime(inputFile).toMillis());
            jarOutStream.putNextEntry(entry);
            Files.copy(inputFile, jarOutStream);
            jarOutStream.closeEntry();
        } catch (IOException e) {
            throw VMError.shouldNotReachHere("Failed to copy " + inputFile + " into JAR file " + outputFilePath.getFileName(), e);
        }
    }

    public Manifest createManifest() {
        return createManifest(null);
    }

    public Manifest createManifest(String mainClass) {
        Manifest mf = new Manifest();
        Attributes attributes = mf.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (mainClass != null) {
            attributes.put(Attributes.Name.MAIN_CLASS, mainClass);
        }
        return mf;
    }

    public void expandJarToDir(Path inputJarFilePath, Path outputDir) {
        expandJarToDir(Function.identity(), inputJarFilePath, outputDir, () -> false);
    }

    public void expandJarToDir(Function<Path, Path> relativizeEntry, Path inputJarFilePath, Path outputDir, BooleanSupplier outputDirDeleted) {
        try {
            try (JarFile archive = new JarFile(inputJarFilePath.toFile())) {
                Enumeration<JarEntry> jarEntries = archive.entries();
                while (jarEntries.hasMoreElements() && !outputDirDeleted.getAsBoolean()) {
                    JarEntry jarEntry = jarEntries.nextElement();
                    Path originalEntry = outputDir.resolve(jarEntry.getName());
                    Path targetEntry = relativizeEntry.apply(originalEntry);
                    try {
                        Path targetParent = targetEntry.getParent();
                        if (targetParent != null) {
                            Files.createDirectories(targetParent);
                        }
                        Files.copy(archive.getInputStream(jarEntry), targetEntry);
                    } catch (IOException e) {
                        throw VMError.shouldNotReachHere("Unable to copy " + jarEntry.getName() + " from " + targetEntry + " to " + targetEntry, e);
                    }
                }
            }
        } catch (IOException e) {
            throw VMError.shouldNotReachHere("Unable to expand JAR file " + inputJarFilePath.getFileName(), e);
        }
    }

    public static Map<String, String> loadProperties(Path propertiesPath) {
        if (Files.isReadable(propertiesPath)) {
            try {
                return loadProperties(Files.newInputStream(propertiesPath));
            } catch (IOException e) {
                throw VMError.shouldNotReachHere("Could not read properties-file: " + propertiesPath, e);
            }
        }
        return Collections.emptyMap();
    }

    public static Map<String, String> loadProperties(InputStream propertiesInputStream) {
        Properties properties = new Properties();
        try (InputStream input = propertiesInputStream) {
            properties.load(input);
        } catch (IOException e) {
            throw VMError.shouldNotReachHere("Could not read properties", e);
        }
        Map<String, String> map = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            map.put(key, properties.getProperty(key));
        }
        return Collections.unmodifiableMap(map);
    }

    private static final String deletedFileSuffix = ".deleted";

    private static boolean isDeletedPath(Path toDelete) {
        Path fileName = toDelete.getFileName();
        if (fileName == null) {
            throw VMError.shouldNotReachHere("Cannot determine file name for path.");
        }
        return fileName.toString().endsWith(deletedFileSuffix);
    }

    public void deleteAllFiles(Path toDelete) {
        try {
            Path deletedPath = toDelete;
            if (!isDeletedPath(deletedPath)) {
                deletedPath = toDelete.resolveSibling(toDelete.getFileName() + deletedFileSuffix);
                Files.move(toDelete, deletedPath);
            }
            Files.walk(deletedPath).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (IOException e) {
            if (isVerbose) {
                LogUtils.info("Could not recursively delete path: " + toDelete);
                e.printStackTrace();
            }
        }
    }

    public <T extends Throwable> Path createTempDir(String tempDirPrefix, AtomicBoolean tempDirDeleted) {
        try {
            Path tempDir = Files.createTempDirectory(tempDirPrefix);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                tempDirDeleted.set(true);
                deleteAllFiles(tempDir);
            }));
            return tempDir;
        } catch (IOException e) {
            throw VMError.shouldNotReachHere("Unable to create temp directory for prefix " + tempDirPrefix, e);
        }
    }

    public void ensureDirectoryExists(Path dir) {
        if (Files.exists(dir)) {
            if (!Files.isDirectory(dir)) {
                throw VMError.shouldNotReachHere("File " + dir + " is not a directory");
            }
        } else {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw VMError.shouldNotReachHere("Could not create directory " + dir);
            }
        }
    }

    public static String currentTime() {
        return ZonedDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
    }

    public static String parseTimestamp(String timestamp) {
        String localDateStr;
        try {
            ZonedDateTime dateTime = ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_DATE_TIME);
            localDateStr = dateTime.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL));
        } catch (DateTimeParseException e) {
            localDateStr = "unknown time";
        }
        return localDateStr;
    }

}
