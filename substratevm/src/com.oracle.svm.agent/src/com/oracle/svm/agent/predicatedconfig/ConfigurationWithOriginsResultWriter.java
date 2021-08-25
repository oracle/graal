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
package com.oracle.svm.agent.predicatedconfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.oracle.svm.agent.tracing.ConfigurationResultWriter;
import com.oracle.svm.agent.tracing.core.Tracer;
import com.oracle.svm.agent.tracing.core.TracingResultWriter;
import com.oracle.svm.configure.config.PredefinedClassesConfiguration;
import com.oracle.svm.configure.config.ProxyConfiguration;
import com.oracle.svm.configure.config.ResourceConfiguration;
import com.oracle.svm.configure.config.SerializationConfiguration;
import com.oracle.svm.configure.config.TypeConfiguration;
import com.oracle.svm.configure.json.JsonWriter;
import com.oracle.svm.configure.trace.AccessAdvisor;
import com.oracle.svm.configure.trace.TraceProcessor;
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.jni.nativeapi.JNIMethodId;

/**
 * Processes trace events that carry origin information.
 *
 * All trace entries (events) passed to this tracer are accompanied by a full call stack. Using that
 * information we construct a call tree where each call node maintains its own configuration set
 * resulting from the trace events from that method. When writing configuration files, the call tree
 * is written node by node, once per configuration file.
 */
public class ConfigurationWithOriginsResultWriter extends Tracer implements TracingResultWriter {

    private final AccessAdvisor advisor;
    private final MethodCallNode rootNode;
    private final MethodInfoRecordKeeper methodInfoRecordKeeper;

    public ConfigurationWithOriginsResultWriter(AccessAdvisor advisor, MethodInfoRecordKeeper methodInfoRecordKeeper) {
        this.advisor = advisor;
        this.rootNode = MethodCallNode.createRoot();
        this.methodInfoRecordKeeper = methodInfoRecordKeeper;
    }

    @Override
    public void traceEntry(Map<String, Object> entry) {
        String tracer = (String) entry.get("tracer");
        if (tracer.equals("meta")) {
            String event = (String) entry.get("event");
            if (event.equals("phase_change")) {
                advisor.setInLivePhase(entry.get("phase").equals("live"));
            }
        } else {
            assert entry.containsKey("stack_trace");
            JNIMethodId[] rawStackTrace = (JNIMethodId[]) entry.remove("stack_trace");
            Map<String, Object> transformedEntry = ConfigurationResultWriter.arraysToLists(entry);

            if (rawStackTrace == null) {
                rootNode.traceEntry(this::createNewTraceProcessor, transformedEntry);
            } else {
                MethodInfo[] stackTrace = methodInfoRecordKeeper.getStackTraceInfo(rawStackTrace);
                rootNode.dispatchTraceEntry(stackTrace, stackTrace.length - 1, transformedEntry, this::createNewTraceProcessor);
            }
        }
    }

    private TraceProcessor createNewTraceProcessor() {
        TypeConfiguration jniConfig = new TypeConfiguration();
        TypeConfiguration reflectConfig = new TypeConfiguration();
        ProxyConfiguration proxyConfig = new ProxyConfiguration();
        ResourceConfiguration resourceConfig = new ResourceConfiguration();
        SerializationConfiguration serializationConfiguration = new SerializationConfiguration();
        PredefinedClassesConfiguration predefinedClassesConfiguration = new PredefinedClassesConfiguration(new Path[0], null);
        return new TraceProcessor(advisor, jniConfig, reflectConfig, proxyConfig, resourceConfig, serializationConfiguration, predefinedClassesConfiguration, null);
    }

    private static final class MethodCallNode {

        private final MethodInfo methodInfo;
        private final MethodCallNode parent;
        private final Map<MethodInfo, MethodCallNode> calledMethods;
        private TraceProcessor processor;

        private MethodCallNode(MethodInfo methodInfo, MethodCallNode parent) {
            this.methodInfo = methodInfo;
            this.parent = parent;
            this.calledMethods = new ConcurrentHashMap<>();
            this.processor = null;
        }

        public static MethodCallNode createRoot() {
            return new MethodCallNode(null, null);
        }

        public boolean isRoot() {
            return parent == null;
        }

        public boolean hasConfig(ConfigurationFile configFile) {
            return processor != null && !processor.getConfiguration(configFile).isEmpty();
        }

        public void dispatchTraceEntry(MethodInfo[] stackTrace, int stackTraceEntry, Map<String, Object> entry, Supplier<TraceProcessor> traceProcessorSupplier) {
            if (stackTraceEntry == -1) {
                traceEntry(traceProcessorSupplier, entry);
            } else {
                MethodInfo next = stackTrace[stackTraceEntry];
                calledMethods.computeIfAbsent(next, nextNodeInfo -> new MethodCallNode(nextNodeInfo, this));
                MethodCallNode nextCall = calledMethods.get(next);
                nextCall.dispatchTraceEntry(stackTrace, stackTraceEntry - 1, entry, traceProcessorSupplier);
            }
        }

        private void traceEntry(Supplier<TraceProcessor> traceProcessorSupplier, Map<String, Object> entry) {
            if (processor == null) {
                synchronized (this) {
                    if (processor == null) {
                        processor = traceProcessorSupplier.get();
                    }
                }
            }
            processor.processEntry(entry);
        }

        public void writeJson(JsonWriter writer, ConfigurationFile configFile) throws IOException {
            Set<MethodCallNode> includedNodes = new HashSet<>();

            /*
             * Recursively construct a set of included nodes. Included nodes are the ones that will
             * eventually be printed. A node is considered included if it or its children have
             * configuration.
             */
            visitPostOrder(node -> {
                /* Nodes with configuration are always included */
                if (node.hasConfig(configFile)) {
                    includedNodes.add(node);
                }
                /* If a node is already included, also include its parent */
                if (includedNodes.contains(node) && node.parent != null) {
                    includedNodes.add(node.parent);
                }
            });

            writeJson(writer, configFile, includedNodes);
        }

        private void writeJson(JsonWriter writer, ConfigurationFile configFile, Set<MethodCallNode> includedNodes) throws IOException {
            if (isRoot()) {
                writer.append("[").newline()
                                .append("{").indent().newline()
                                .quote("configuration-with-origins").append(": [");
                printChildMethodJson(writer, configFile, includedNodes);
                writer.newline()
                                .append("]").unindent().newline();
                if (hasConfig(configFile)) {
                    writer.quote("configuration-without-origins").append(": ");
                    writeConfigJson(writer, configFile);
                    writer.unindent().newline();
                }
                writer.append("}").newline()
                                .append("]").newline();
            } else {
                writer.append("{").indent().newline();

                writer.quote("method").append(": ").quote(methodInfo.getJavaDeclaringClassName() + "#" + methodInfo.getJavaMethodNameAndSignature()).append(",").newline();

                if (anyChildrenIncluded(includedNodes)) {
                    writer.quote("methods").append(": [");
                    printChildMethodJson(writer, configFile, includedNodes);
                    writer.newline().append("]");
                    if (hasConfig(configFile)) {
                        writer.append(",").newline();
                    }
                }

                if (hasConfig(configFile)) {
                    writeConfigJson(writer, configFile);
                }

                writer.unindent().newline();
                writer.append("}");
            }
        }

        private void printChildMethodJson(JsonWriter writer, ConfigurationFile configFile, Set<MethodCallNode> includedNodes) throws IOException {
            boolean first = true;
            for (MethodCallNode methodCallNode : calledMethods.values()) {
                if (!includedNodes.contains(methodCallNode)) {
                    continue;
                }
                if (first) {
                    first = false;
                } else {
                    writer.append(",");
                }
                writer.newline();
                methodCallNode.writeJson(writer, configFile, includedNodes);
            }
        }

        private boolean anyChildrenIncluded(Set<MethodCallNode> includedNodes) {
            return calledMethods.values().stream().anyMatch(includedNodes::contains);
        }

        private void writeConfigJson(JsonWriter writer, ConfigurationFile configFile) throws IOException {
            writer.quote("config").append(": ");
            processor.getConfiguration(configFile).printJson(writer);
        }

        private void visitPostOrder(Consumer<MethodCallNode> methodCallNodeConsumer) {
            for (MethodCallNode node : calledMethods.values()) {
                node.visitPostOrder(methodCallNodeConsumer);
            }
            methodCallNodeConsumer.accept(this);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MethodCallNode that = (MethodCallNode) o;
            return methodInfo.equals(that.methodInfo) && Objects.equals(parent, that.parent) && calledMethods.equals(that.calledMethods) && Objects.equals(processor, that.processor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(methodInfo, parent, processor);
        }
    }

    @Override
    public boolean supportsPeriodicTraceWriting() {
        return false;
    }

    @Override
    public boolean supportsOnUnloadTraceWriting() {
        return true;
    }

    public static final String CONFIG_WITH_ORIGINS_FILE_SUFFIX = "-origins.json";

    @Override
    public List<Path> writeToDirectory(Path directoryPath) throws IOException {
        List<Path> writtenPaths = new ArrayList<>();
        for (ConfigurationFile configFile : ConfigurationFile.values()) {
            if (configFile.canBeGeneratedByAgent()) {
                Path filePath = directoryPath.resolve(configFile.getFileName(CONFIG_WITH_ORIGINS_FILE_SUFFIX));
                try (JsonWriter writer = new JsonWriter(filePath)) {
                    rootNode.writeJson(writer, configFile);
                }
                writtenPaths.add(filePath);
            }
        }
        return writtenPaths;
    }
}
