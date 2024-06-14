/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.configure;

import static com.oracle.svm.core.configure.ConfigurationParser.JNI_KEY;
import static com.oracle.svm.core.configure.ConfigurationParser.REFLECTION_KEY;
import static com.oracle.svm.core.configure.ConfigurationParser.RESOURCES_KEY;
import static com.oracle.svm.core.configure.ConfigurationParser.SERIALIZATION_KEY;

import java.util.Arrays;

public enum ConfigurationFile {
    /* Combined file */
    REACHABILITY_METADATA("reachability-metadata", null, true, true),
    /* Main metadata categories (order matters) */
    REFLECTION("reflect", REFLECTION_KEY, true, false),
    RESOURCES("resource", RESOURCES_KEY, true, false),
    SERIALIZATION("serialization", SERIALIZATION_KEY, true, false),
    JNI("jni", JNI_KEY, true, false),
    /* Deprecated metadata categories */
    DYNAMIC_PROXY("proxy", null, true, false),
    PREDEFINED_CLASSES_NAME("predefined-classes", null, true, false),
    /* Non-metadata categories */
    FOREIGN("foreign", null, false, false),
    SERIALIZATION_DENY("serialization-deny", null, false, false);

    public static final String LEGACY_FILE_NAME_SUFFIX = "-config.json";
    public static final String COMBINED_FILE_NAME_SUFFIX = ".json";
    private final String name;
    private final String fieldName;
    private final boolean canAgentGenerate;
    private final boolean combinedFile;

    public static final String LOCK_FILE_NAME = ".lock";
    public static final String PREDEFINED_CLASSES_AGENT_EXTRACTED_SUBDIR = "agent-extracted-predefined-classes";
    public static final String PREDEFINED_CLASSES_AGENT_EXTRACTED_NAME_SUFFIX = ".classdata";
    public static final String PARTIAL_CONFIGURATION_WITH_ORIGINS = "partial-config-with-origins.json";

    private static final ConfigurationFile[] agentGeneratedFiles = computeAgentGeneratedFiles();

    ConfigurationFile(String name, String fieldName, boolean canAgentGenerate, boolean combinedFile) {
        this.name = name;
        this.fieldName = fieldName;
        this.canAgentGenerate = canAgentGenerate;
        this.combinedFile = combinedFile;
    }

    public String getName() {
        return name;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getFileName() {
        return name + (combinedFile ? COMBINED_FILE_NAME_SUFFIX : LEGACY_FILE_NAME_SUFFIX);
    }

    public String getFileName(String suffix) {
        return name + suffix;
    }

    public boolean canBeGeneratedByAgent() {
        return canAgentGenerate && !combinedFile;
    }

    public static ConfigurationFile getByName(String name) {
        for (ConfigurationFile file : values()) {
            if (file.getName().equals(name)) {
                return file;
            }
        }
        return null;
    }

    public static ConfigurationFile[] agentGeneratedFiles() {
        return agentGeneratedFiles;
    }

    private static ConfigurationFile[] computeAgentGeneratedFiles() {
        return Arrays.stream(values()).filter(f -> f.canBeGeneratedByAgent()).toArray(ConfigurationFile[]::new);
    }
}
