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

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicClassesConfiguration implements JsonPrintable {

    private final ConcurrentHashMap<String, ConfigurationDynamicClass> dynamicDefinedClasses = new ConcurrentHashMap<>();

    /**
     * Collect dynamic class configuration elements to create a new ConfigurationDynamicClass at
     * agent collecting time.
     *
     * @param definedClassName dynamically generated class' full qualified name
     * @param classContents class contents in byte array
     * @param checksum dynamically generated class' SHA checksum. The "source" attribute is ignored
     *            when calculating the checksum.
     */
    public void add(String definedClassName, byte[] classContents, String checksum) {
        ConfigurationDynamicClass configuration = ConfigurationDynamicClass.newAgentTraceTimeConfig(definedClassName, checksum, classContents);
        dynamicDefinedClasses.put(definedClassName, configuration);
    }

    /**
     * Collect dynamic class configuration elements to create a new ConfigurationDynamicClass at
     * agent merging or configuration tool generating time. In this scenario, the class contents
     * have been dumped to the file, we need to record its location for later moving usage.
     *
     * @param definedClassName class name read from original configuration
     * @param originalClassPath originally dumped class file's path
     * @param checksum class checksum read from original configuration
     */
    public void add(String definedClassName, Path originalClassPath, String checksum) {
        ConfigurationDynamicClass configuration = ConfigurationDynamicClass.newAgentMergeTimeConfig(definedClassName, checksum, originalClassPath);
        dynamicDefinedClasses.put(definedClassName, configuration);
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.append('[').indent();
        String prefix = "";
        for (ConfigurationDynamicClass value : dynamicDefinedClasses.values()) {
            writer.append(prefix);
            writer.newline();
            value.printJson(writer);
            prefix = ",";
        }
        writer.unindent().newline();
        writer.append(']').newline();
    }
}
