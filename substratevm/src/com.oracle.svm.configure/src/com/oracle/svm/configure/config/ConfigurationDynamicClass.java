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

import com.oracle.svm.configure.json.JsonPrintable;
import com.oracle.svm.configure.json.JsonWriter;
import com.oracle.svm.core.configure.ConfigurationFiles;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigurationDynamicClass implements JsonPrintable {

    private final String qualifiedJavaName;
    private final String checksum;
    private final Path classFilePath;
    private final byte[] classContents;

    public static ConfigurationDynamicClass newAgentTraceTimeConfig(String qualifiedJavaName, String checksum, byte[] classContents) {
        return new ConfigurationDynamicClass(qualifiedJavaName, checksum, classContents, null);
    }

    public static ConfigurationDynamicClass newAgentMergeTimeConfig(String qualifiedJavaName, String checksum, Path classFile) {
        return new ConfigurationDynamicClass(qualifiedJavaName, checksum, null, classFile);
    }

    private ConfigurationDynamicClass(String qualifiedJavaName, String checksum, byte[] classContents, Path classFilePath) {
        assert qualifiedJavaName.indexOf('/') == -1 : "Requires qualified Java name, not internal representation";
        assert !qualifiedJavaName.startsWith("[") : "Requires Java source array syntax, for example java.lang.String[]";
        assert !(classContents == null && classFilePath == null) : "classContents and classFilePath can't be null at the same time";
        this.qualifiedJavaName = qualifiedJavaName;
        this.checksum = checksum;
        this.classContents = classContents;
        this.classFilePath = classFilePath;
    }

    public boolean contains(String definedClassName, String checksum2Match) {
        return definedClassName != null && definedClassName.equals(qualifiedJavaName) && checksum2Match.equals(this.checksum);
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        // Mangle class' qualified name into a system independent format
        String internalName = qualifiedJavaName.replace(".", ConfigurationFiles.MANGLE_SLASH);
        Path basePath = writer.getPath().getParent();
        Path dumpDir = basePath.resolve(ConfigurationFiles.DUMP_CLASSES_DIR);
        Path dumpFile = dumpDir.resolve(internalName + ".class");
        checkAndMkDir(dumpDir);
        if (classContents != null) {
            try (FileOutputStream stream = new FileOutputStream(dumpFile.toFile());) {
                stream.write(classContents);
            }
        }
        if (classFilePath != null) {
            Files.copy(classFilePath, dumpFile);
        }
        writer.append('{').newline();
        writer.quote("name").append(':').quote(qualifiedJavaName).append(',').newline();
        writer.quote("classFile").append(":").quote(basePath.relativize(dumpFile).toString()).append(',').newline();
        writer.quote("checksum").append(":").quote(checksum).newline();
        writer.append("}");
    }

    private static void checkAndMkDir(Path dumpDirs) throws IOException {
        if (!Files.exists(dumpDirs)) {
            Files.createDirectories(dumpDirs);
        } else if (!Files.isDirectory(dumpDirs)) {
            throw new IOException("Existing " + dumpDirs + " is a file, but a directory is expected.");
        }
    }
}
