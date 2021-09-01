/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021, Alibaba Group Holding Limited. All rights reserved.
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
package com.oracle.svm.configure.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import com.oracle.svm.configure.ConfigurationBase;
import com.oracle.svm.configure.json.JsonWriter;
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.hub.PredefinedClassesSupport;

public class PredefinedClassesConfiguration implements ConfigurationBase {
    private final Path[] classDestinationDirs;
    private final ConcurrentHashMap<String, ConfigurationPredefinedClass> classes = new ConcurrentHashMap<>();
    private final Predicate<String> shouldExcludeClassWithHash;

    public PredefinedClassesConfiguration(Path[] classDestinationDirs, Predicate<String> shouldExcludeClassWithHash) {
        this.classDestinationDirs = classDestinationDirs;
        this.shouldExcludeClassWithHash = shouldExcludeClassWithHash;
    }

    public void add(String nameInfo, byte[] classData) {
        ensureDestinationDirsExist();
        String hash = PredefinedClassesSupport.hash(classData, 0, classData.length);
        if (shouldExcludeClassWithHash != null && shouldExcludeClassWithHash.test(hash)) {
            return;
        }
        if (classDestinationDirs != null) {
            for (Path dir : classDestinationDirs) {
                try {
                    Files.write(dir.resolve(getFileName(hash)), classData);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        ConfigurationPredefinedClass clazz = new ConfigurationPredefinedClass(nameInfo, hash);
        classes.put(hash, clazz);
    }

    public void add(String nameInfo, String hash, Path directory) {
        if (shouldExcludeClassWithHash != null && shouldExcludeClassWithHash.test(hash)) {
            return;
        }
        if (classDestinationDirs != null) {
            ensureDestinationDirsExist();
            for (Path destDir : classDestinationDirs) {
                if (!destDir.equals(directory)) {
                    try {
                        String fileName = getFileName(hash);
                        Path target = destDir.resolve(fileName);
                        if (directory != null) {
                            Files.copy(directory.resolve(fileName), target, StandardCopyOption.REPLACE_EXISTING);
                        } else if (!Files.exists(target)) {
                            throw new RuntimeException("Cannot copy class data file for predefined class " + nameInfo + " with hash " + hash + ": " +
                                            "source directory is unknown and file does not already exist in target directory.");
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        ConfigurationPredefinedClass clazz = new ConfigurationPredefinedClass(nameInfo, hash);
        classes.put(hash, clazz);
    }

    private void ensureDestinationDirsExist() {
        if (classDestinationDirs != null) {
            for (Path dir : classDestinationDirs) {
                if (!Files.isDirectory(dir)) {
                    try {
                        Files.createDirectory(dir);
                    } catch (IOException e) {
                        if (!Files.isDirectory(dir)) { // potential race
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }
    }

    private static String getFileName(String hash) {
        return hash + ConfigurationFile.PREDEFINED_CLASSES_AGENT_EXTRACTED_NAME_SUFFIX;
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.append('[').indent().newline();
        writer.append('{').indent().newline();
        writer.quote("type").append(':').quote("agent-extracted").append(',').newline();
        writer.quote("classes").append(":[").indent();
        String prefix = "";
        for (ConfigurationPredefinedClass value : classes.values()) {
            writer.append(prefix).newline();
            value.printJson(writer);
            prefix = ",";
        }
        writer.unindent().newline().append(']');
        writer.unindent().newline().append('}');
        writer.unindent().newline().append(']').newline();
    }

    @Override
    public boolean isEmpty() {
        return classes.isEmpty();
    }

    public boolean containsClassWithName(String className) {
        return classes.values().stream().anyMatch(clazz -> clazz.getNameInfo().equals(className));
    }

    public boolean containsClassWithHash(String hash) {
        return classes.containsKey(hash);
    }
}
