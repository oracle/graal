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
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.configure.ConfigurationBase;
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.configure.ConfigurationParser;
import com.oracle.svm.core.configure.PredefinedClassesConfigurationParser;
import com.oracle.svm.core.hub.PredefinedClassesSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.util.json.JsonWriter;

public final class PredefinedClassesConfiguration extends ConfigurationBase<PredefinedClassesConfiguration, PredefinedClassesConfiguration.Predicate> {
    private final Path[] classDestinationDirs;
    private final ConcurrentMap<String, ConfigurationPredefinedClass> classes = new ConcurrentHashMap<>();
    private final java.util.function.Predicate<String> shouldExcludeClassWithHash;

    public PredefinedClassesConfiguration(Path[] classDestinationDirs, java.util.function.Predicate<String> shouldExcludeClassWithHash) {
        this.classDestinationDirs = classDestinationDirs;
        this.shouldExcludeClassWithHash = shouldExcludeClassWithHash;
    }

    public PredefinedClassesConfiguration(PredefinedClassesConfiguration other) {
        this.classDestinationDirs = other.classDestinationDirs;
        classes.putAll(other.classes);
        this.shouldExcludeClassWithHash = other.shouldExcludeClassWithHash;
    }

    @Override
    public PredefinedClassesConfiguration copy() {
        return new PredefinedClassesConfiguration(this);
    }

    @Override
    protected void merge(PredefinedClassesConfiguration other) {
        classes.putAll(other.classes);
    }

    @Override
    protected void subtract(PredefinedClassesConfiguration other) {
        classes.keySet().removeAll(other.classes.keySet());
    }

    @Override
    protected void intersect(PredefinedClassesConfiguration other) {
        classes.keySet().retainAll(other.classes.keySet());
    }

    @Override
    protected void removeIf(Predicate predicate) {
        classes.values().removeIf(predicate::testPredefinedClass);
    }

    @Override
    public void mergeConditional(ConfigurationCondition condition, PredefinedClassesConfiguration other) {
        /* Not implemented with conditions yet */
        classes.putAll(other.classes);
    }

    public void add(String nameInfo, byte[] classData) {
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

    public void add(String nameInfo, String hash, URI baseUri) {
        if (shouldExcludeClassWithHash != null && shouldExcludeClassWithHash.test(hash)) {
            return;
        }
        if (classDestinationDirs != null) {
            Path localBaseDir;
            try {
                localBaseDir = Path.of(baseUri);
            } catch (Exception ignored) {
                localBaseDir = null;
            }
            for (Path destDir : classDestinationDirs) {
                if (!destDir.equals(localBaseDir)) {
                    try {
                        String fileName = getFileName(hash);
                        Path target = destDir.resolve(fileName);
                        if (baseUri != null) {
                            try (InputStream is = PredefinedClassesConfigurationParser.openClassdataStream(baseUri, hash)) {
                                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                            }
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
    public ConfigurationParser createParser(boolean strictMetadata) {
        VMError.guarantee(!strictMetadata, "Predefined classes configuration is not supported with strict metadata");
        return new PredefinedClassesConfigurationParser(this::add, true);
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

    public interface Predicate {

        boolean testPredefinedClass(ConfigurationPredefinedClass clazz);

    }
}
