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

public enum ConfigurationFile {
    DYNAMIC_PROXY("proxy", true),
    RESOURCES("resource", true),
    JNI("jni", true),
    REFLECTION("reflect", true),
    SERIALIZATION("serialization", true),
    SERIALIZATION_DENY("serialization-deny", false),
    PREDEFINED_CLASSES_NAME("predefined-classes", true);

    private static final String DEFAULT_FILE_NAME_SUFFIX = "-config.json";
    private final String fileName;
    private final boolean canAgentGenerate;

    public static final String PREDEFINED_CLASSES_AGENT_EXTRACTED_SUBDIR = "agent-extracted-predefined-classes";
    public static final String PREDEFINED_CLASSES_AGENT_EXTRACTED_NAME_SUFFIX = ".classdata";

    ConfigurationFile(String fileName, boolean canAgentGenerate) {
        this.fileName = fileName;
        this.canAgentGenerate = canAgentGenerate;
    }

    public String getFileName() {
        return fileName + DEFAULT_FILE_NAME_SUFFIX;
    }

    public String getFileName(String suffix) {
        return fileName + suffix;
    }

    public boolean canBeGeneratedByAgent() {
        return canAgentGenerate;
    }

}
