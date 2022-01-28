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
package com.oracle.svm.agent.configwithorigins;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.oracle.svm.configure.json.JsonWriter;
import com.oracle.svm.configure.trace.AccessAdvisor;
import com.oracle.svm.core.configure.ConfigurationFile;

public class ConfigurationWithOriginsResultWriter extends ConfigurationWithOriginsResultWriterBase {

    public static final String CONFIG_WITH_ORIGINS_SUFFIX = "-origins.json";

    public ConfigurationWithOriginsResultWriter(AccessAdvisor advisor, MethodInfoRecordKeeper methodInfoRecordKeeper) {
        super(advisor, methodInfoRecordKeeper);
    }

    @Override
    public boolean supportsOnUnloadTraceWriting() {
        return true;
    }

    @Override
    protected String getConfigFileSuffix() {
        return CONFIG_WITH_ORIGINS_SUFFIX;
    }

    public static void writeJson(MethodCallNode root, JsonWriter writer, ConfigurationFile configFile) throws IOException {
        Set<MethodCallNode> includedNodes = new HashSet<>();

        /*
         * Recursively construct a set of included nodes. Included nodes are the ones that will
         * eventually be printed. A node is considered included if it or its children have
         * configuration.
         */
        root.visitPostOrder(node -> {
            /* Nodes with configuration are always included */
            if (node.hasConfig(configFile)) {
                includedNodes.add(node);
            }
            /* If a node is already included, also include its parent */
            if (includedNodes.contains(node) && node.parent != null) {
                includedNodes.add(node.parent);
            }
        });

        writeJson(root, writer, configFile, includedNodes);
    }

    private static void writeJson(MethodCallNode node, JsonWriter writer, ConfigurationFile configFile, Set<MethodCallNode> includedNodes) throws IOException {
        if (node.isRoot()) {
            writer.append("[").newline()
                            .append("{").indent().newline()
                            .quote("configuration-with-origins").append(": [");
            printChildMethodJson(node, writer, configFile, includedNodes);
            writer.newline()
                            .append("]").unindent().newline();
            if (node.hasConfig(configFile)) {
                writer.quote("configuration-without-origins").append(": ").indent();
                writeConfigJson(node, writer, configFile);
                writer.unindent().newline();
            }
            writer.append("}").newline()
                            .append("]").newline();
        } else {
            writer.append("{").indent().newline();

            writer.quote("method").append(": ").quote(node.methodInfo.getJavaDeclaringClassName() + "#" + node.methodInfo.getJavaMethodNameAndSignature()).append(",").newline();

            if (anyChildrenIncluded(node, includedNodes)) {
                writer.quote("methods").append(": [");
                printChildMethodJson(node, writer, configFile, includedNodes);
                writer.newline().append("]");
                if (node.hasConfig(configFile)) {
                    writer.append(",").newline();
                }
            }

            if (node.hasConfig(configFile)) {
                writeConfigJson(node, writer, configFile);
            }

            writer.unindent().newline();
            writer.append("}");
        }
    }

    private static void printChildMethodJson(MethodCallNode node, JsonWriter writer, ConfigurationFile configFile, Set<MethodCallNode> includedNodes) throws IOException {
        boolean first = true;
        for (MethodCallNode methodCallNode : node.calledMethods.values()) {
            if (!includedNodes.contains(methodCallNode)) {
                continue;
            }
            if (first) {
                first = false;
            } else {
                writer.append(",");
            }
            writer.newline();
            writeJson(methodCallNode, writer, configFile, includedNodes);
        }
    }

    private static boolean anyChildrenIncluded(MethodCallNode node, Set<MethodCallNode> includedNodes) {
        return node.calledMethods.values().stream().anyMatch(includedNodes::contains);
    }

    private static void writeConfigJson(MethodCallNode node, JsonWriter writer, ConfigurationFile configFile) throws IOException {
        writer.quote("config").append(": ");
        node.processor.getConfiguration(configFile).printJson(writer);
    }

    @Override
    protected void writeConfig(JsonWriter writer, ConfigurationFile configFile) throws IOException {
        writeJson(rootNode, writer, configFile);
    }
}
