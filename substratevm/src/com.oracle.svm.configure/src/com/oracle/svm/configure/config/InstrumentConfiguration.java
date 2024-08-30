/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Alibaba Group Holding Limited. All rights reserved.
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

import com.oracle.svm.configure.ConfigurationBase;
import com.oracle.svm.core.configure.ConfigurationParser;
import com.oracle.svm.core.configure.InstrumentConfigurationParser;
import com.oracle.svm.core.configure.InstrumentRegistry;
import com.oracle.svm.core.util.VMError;
import org.graalvm.nativeimage.impl.UnresolvedConfigurationCondition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jdk.graal.compiler.phases.common.LazyValue;
import jdk.graal.compiler.util.json.JsonWriter;

import static com.oracle.svm.core.configure.ConfigurationFile.CLASS_POSTFIX;
import static com.oracle.svm.core.configure.ConfigurationFile.PREDEFINED_CLASSES_AGENT_EXTRACTED_NAME_SUFFIX;
import static com.oracle.svm.core.configure.ConfigurationFile.UNNAMED_MODULE;

public class InstrumentConfiguration extends ConfigurationBase<InstrumentConfiguration, InstrumentConfiguration.Predicate> {
    private final List<LazyValue<Path>> classDestinationDirs;
    private final ConcurrentMap<String, ConfigurationInstrument.Class> classes = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConfigurationInstrument.Method> methods = new ConcurrentHashMap<>();

    public InstrumentConfiguration(List<LazyValue<Path>> classDestinationDirs) {
        this.classDestinationDirs = classDestinationDirs;
    }

    public InstrumentConfiguration(InstrumentConfiguration other) {
        classes.putAll(other.classes);
        methods.putAll(other.methods);
        classDestinationDirs = other.classDestinationDirs;
    }

    @Override
    public boolean isEmpty() {
        return classes.isEmpty() && methods.isEmpty();
    }

    @Override
    public InstrumentConfiguration copy() {
        return new InstrumentConfiguration(this);
    }

    @Override
    protected void merge(InstrumentConfiguration other) {
        classes.putAll(other.classes);
        methods.putAll(other.methods);
    }

    @Override
    public void mergeConditional(UnresolvedConfigurationCondition condition, InstrumentConfiguration other) {
        classes.putAll(other.classes);
        methods.putAll(other.methods);
    }

    @Override
    protected void subtract(InstrumentConfiguration other) {
        classes.keySet().removeAll(other.classes.keySet());
        methods.keySet().removeAll(other.methods.keySet());
    }

    @Override
    protected void intersect(InstrumentConfiguration other) {
        classes.keySet().retainAll(other.classes.keySet());
        methods.keySet().retainAll(other.methods.keySet());
    }

    @Override
    protected void removeIf(InstrumentConfiguration.Predicate predicate) {
        classes.values().removeIf(predicate::testExcludeClass);
        methods.values().removeIf(predicate::testExcludeMethod);
    }

    @Override
    public boolean supportsCombinedFile() {
        return false;
    }

    @Override
    public ConfigurationParser createParser(boolean strictMetadata) {
        VMError.guarantee(!strictMetadata, "Instrumentation configuration is not supported with strict metadata");
        return new InstrumentConfigurationParser(new InstrumentRegistry() {

            @Override
            public void add(String premainClass, int index, String options) {
                methods.put(premainClass, new ConfigurationInstrument.Method(premainClass, index, options));
            }

            @Override
            public void add(String transformedClass, String type) {
                classes.put(transformedClass, new ConfigurationInstrument.Class(transformedClass, type));
            }
        }, true);
    }

    // Add premain method
    public void add(String className, int index, String optionString) {
        methods.put(className, new ConfigurationInstrument.Method(className, index, optionString));
    }

    public void add(String className, byte[] data, String type, boolean isJDKInternal, String moduleName) {
        if (classDestinationDirs != null) {
            for (LazyValue<Path> dir : classDestinationDirs) {
                // Dump the class file. Transformed JDK classes are dumped as ".classdata" files
                try {
                    String moduleDir = UNNAMED_MODULE;
                    if (moduleName != null) {
                        moduleDir = moduleName;
                    }
                    Path moduleDirPath = dir.get().resolve(moduleDir);
                    Path transformedClassFile = moduleDirPath.resolve(className + (isJDKInternal ? PREDEFINED_CLASSES_AGENT_EXTRACTED_NAME_SUFFIX : CLASS_POSTFIX));
                    Path parent = transformedClassFile.getParent();
                    if (Files.notExists(parent)) {
                        Files.createDirectories(parent);
                    }
                    Files.write(transformedClassFile, data);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        classes.put(className, new ConfigurationInstrument.Class(className, type));
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.append('[').indent().newline();
        writer.append('{').indent().newline();
        writer.quote("type").append(':').quote("agent-extracted").append(',').newline();
        writer.quote("classes").append(":[").indent();
        String prefix = "";
        for (ConfigurationInstrument.Class value : classes.values()) {
            writer.append(prefix).newline();
            value.printJson(writer);
            prefix = ",";
        }
        writer.unindent().newline().append(']').append(',');
        writer.quote("methods").append(":[").indent();
        prefix = "";
        for (ConfigurationInstrument.Method value : methods.values()) {
            writer.append(prefix).newline();
            value.printJson(writer);
            prefix = ",";
        }
        writer.unindent().newline().append(']');
        writer.unindent().newline().append('}');
        writer.unindent().newline().append(']').newline();
    }

    public interface Predicate {
        boolean testExcludeClass(ConfigurationInstrument.Class instrumentClass);

        boolean testExcludeMethod(ConfigurationInstrument.Method instrumentClass);
    }
}
