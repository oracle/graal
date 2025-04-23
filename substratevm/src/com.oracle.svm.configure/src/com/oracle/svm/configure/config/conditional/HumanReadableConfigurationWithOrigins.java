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
package com.oracle.svm.configure.config.conditional;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Set;

import com.oracle.svm.configure.ConfigurationBase;
import com.oracle.svm.configure.ConfigurationFile;

import jdk.graal.compiler.util.json.JsonPrintable;
import jdk.graal.compiler.util.json.JsonWriter;

public class HumanReadableConfigurationWithOrigins implements JsonPrintable {

    static final String CONNECTING_INDENT = "\u2502   "; // "| "
    static final String EMPTY_INDENT = "    ";
    static final String CHILD = "\u251c\u2500\u2500 "; // "|-- "
    static final String LAST_CHILD = "\u2514\u2500\u2500 "; // "`-- "

    private final MethodCallNode root;
    private final ConfigurationFile configFile;
    private final Set<MethodCallNode> nodesWithNonEmptyConfig;

    public HumanReadableConfigurationWithOrigins(MethodCallNode root, ConfigurationFile configFile) {
        this.root = root;
        this.configFile = configFile;
        this.nodesWithNonEmptyConfig = root.getNodesWithNonEmptyConfig(configFile);
    }

    private MethodCallNode findLastChildWithNonEmptyConfig(List<MethodCallNode> nodes) {
        MethodCallNode child = null;
        for (MethodCallNode node : nodes) {
            if (nodesWithNonEmptyConfig.contains(node)) {
                child = node;
            }
        }
        return child;
    }

    private void printNode(JsonWriter writer, String prefix, MethodCallNode node) throws IOException {
        writer.append(prefix).append(node.methodInfo.toString());
        if (node.hasConfig(configFile)) {
            writer.append(" - ");
            ConfigurationBase<?, ?> config = node.configuration.getConfiguration(configFile);
            StringWriter sw = new StringWriter();
            JsonWriter jw = new JsonWriter(sw);
            if (config.supportsCombinedFile()) {
                config.printJson(jw);
            } else {
                config.printLegacyJson(jw);
            }
            writer.append(sw.toString().replace("\n", " "));
        }
        writer.newline();
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.append("root").newline();

        List<MethodCallNode> nodes = List.copyOf(root.calledMethods.values());
        MethodCallNode lastChild = findLastChildWithNonEmptyConfig(nodes);
        for (MethodCallNode node : nodes) {
            if (nodesWithNonEmptyConfig.contains(node)) {
                printNode(writer, node == lastChild ? LAST_CHILD : CHILD, node);
                printChildNodes(writer, node == lastChild ? EMPTY_INDENT : CONNECTING_INDENT, node);
            }
        }
    }

    private void printChildNodes(JsonWriter writer, String prefix, MethodCallNode node) throws IOException {
        List<MethodCallNode> nodes = List.copyOf(node.calledMethods.values());
        MethodCallNode lastChild = findLastChildWithNonEmptyConfig(nodes);
        for (MethodCallNode child : node.calledMethods.values()) {
            if (nodesWithNonEmptyConfig.contains(child)) {
                printNode(writer, prefix + (child == lastChild ? LAST_CHILD : CHILD), child);
                printChildNodes(writer, prefix + (child == lastChild ? EMPTY_INDENT : CONNECTING_INDENT), child);
            }
        }
    }
}
