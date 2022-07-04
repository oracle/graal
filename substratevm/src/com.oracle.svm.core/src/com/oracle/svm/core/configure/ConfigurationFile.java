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

import java.util.Arrays;

public enum ConfigurationFile {
    DYNAMIC_PROXY("proxy", true),
    RESOURCES("resource", true),
    JNI("jni", true),
    REFLECTION("reflect", true),
    SERIALIZATION("serialization", true),
    SERIALIZATION_DENY("serialization-deny", false),
    PREDEFINED_CLASSES_NAME("predefined-classes", true);

    public static final String DEFAULT_FILE_NAME_SUFFIX = "-config.json";
    private final String name;
    private final boolean canAgentGenerate;

    public static final String LOCK_FILE_NAME = ".lock";
    public static final String PREDEFINED_CLASSES_AGENT_EXTRACTED_SUBDIR = "agent-extracted-predefined-classes";
    public static final String PREDEFINED_CLASSES_AGENT_EXTRACTED_NAME_SUFFIX = ".classdata";
    public static final String PARTIAL_CONFIGURATION_WITH_ORIGINS = "partial-config-with-origins.json";

    private static final ConfigurationFile[] agentGeneratedFiles = computeAgentGeneratedFiles();

    ConfigurationFile(String name, boolean canAgentGenerate) {
        this.name = name;
        this.canAgentGenerate = canAgentGenerate;
    }

    public String getName() {
        return name;
    }

    public String getFileName() {
        return name + DEFAULT_FILE_NAME_SUFFIX;
    }

    public String getFileName(String suffix) {
        return name + suffix;
    }

    public boolean canBeGeneratedByAgent() {
        return canAgentGenerate;
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
        return Arrays.stream(values()).filter(ConfigurationFile::canBeGeneratedByAgent).toArray(ConfigurationFile[]::new);
    }
}
