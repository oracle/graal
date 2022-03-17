/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.agent.configwithorigins;

import java.io.IOException;
import java.util.Set;

import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.json.JsonPrintable;
import com.oracle.svm.configure.json.JsonWriter;
import com.oracle.svm.core.configure.ConfigurationFile;

public class ConfigurationWithOrigins implements JsonPrintable {

    private final MethodCallNode root;
    private final ConfigurationFile configFile;
    private static final ConfigurationSet emptyConfigurationSet = new ConfigurationSet();

    public ConfigurationWithOrigins(MethodCallNode root, ConfigurationFile configFile) {
        this.root = root;
        this.configFile = configFile;
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.append("[").newline()
                        .append("{").indent().newline()
                        .quote("configuration-with-origins").append(": [");
        printJson(root, writer, root.getNodesWithNonEmptyConfig(configFile));
        writer.newline()
                        .append("]").unindent().newline();

        writer.quote("configuration-without-origins").append(": ").indent();
        ConfigurationSet rootConfiguration = root.configuration != null ? root.configuration : emptyConfigurationSet;
        rootConfiguration.getConfiguration(configFile).printJson(writer);
        writer.append("}").newline()
                        .append("]");
    }

    private void printJson(MethodCallNode node, JsonWriter writer, Set<MethodCallNode> nodesWithNonEmptyConfig) throws IOException {
        writer.append("{").indent().newline();

        writer.quote("method").append(": ").quote(node.methodInfo).append(",").newline();

        writer.quote("methods").append(": [");
        boolean first = true;
        for (MethodCallNode methodCallNode : node.calledMethods.values()) {
            if (!nodesWithNonEmptyConfig.contains(methodCallNode)) {
                continue;
            }
            if (first) {
                first = false;
            } else {
                writer.append(",");
            }
            writer.newline();
            printJson(methodCallNode, writer, nodesWithNonEmptyConfig);
        }
        writer.newline().append("],").newline();

        writer.quote("config").append(": ");
        ConfigurationSet configuration = node.configuration != null ? node.configuration : emptyConfigurationSet;
        configuration.getConfiguration(configFile).printJson(writer);

        writer.unindent().newline();
        writer.append("}");

    }
}
