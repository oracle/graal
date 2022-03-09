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
package com.oracle.svm.agent.conditionalconfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.svm.agent.configwithorigins.HumanReadableConfigurationWithOrigins;
import com.oracle.svm.configure.filters.ComplexFilter;
import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.agent.configwithorigins.ConfigurationWithOriginsTracer;
import com.oracle.svm.agent.configwithorigins.MethodCallNode;
import com.oracle.svm.agent.configwithorigins.MethodInfo;
import com.oracle.svm.agent.configwithorigins.MethodInfoRecordKeeper;
import com.oracle.svm.agent.tracing.core.TracingResultWriter;
import com.oracle.svm.configure.config.ConditionalConfigurationPredicate;
import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.config.PredefinedClassesConfiguration;
import com.oracle.svm.configure.config.ProxyConfiguration;
import com.oracle.svm.configure.config.ResourceConfiguration;
import com.oracle.svm.configure.config.SerializationConfiguration;
import com.oracle.svm.configure.config.TypeConfiguration;
import com.oracle.svm.configure.trace.TraceProcessor;

/**
 * Outputs configuration augmented with reachability conditions.
 *
 * This writer leverages the configuration origin information to deduce conditions for the
 * configuration. See {@link #createConditionalConfiguration()}
 */
public class ConditionalConfigurationWriter extends ConfigurationWithOriginsTracer implements TracingResultWriter {
    private final ComplexFilter userCodeFilter;
    private ConfigurationSet configurationContainer = new ConfigurationSet();
    private final ConditionalConfigurationPredicate predicate;

    public ConditionalConfigurationWriter(TraceProcessor processor, MethodInfoRecordKeeper methodInfoRecordKeeper, ComplexFilter userCodeFilter, ConditionalConfigurationPredicate predicate) {
        super(processor, methodInfoRecordKeeper);
        this.userCodeFilter = userCodeFilter;
        this.predicate = predicate;
    }

    private Map<MethodInfo, List<MethodCallNode>> mapMethodsToCallNodes() {
        /* Create a map that maps each method to the call nodes of that method in the call graph. */
        Map<MethodInfo, List<MethodCallNode>> methodCallNodes = new HashMap<>();
        ConfigurationSet emptyConfigurationSet = new ConfigurationSet();
        rootNode.visitPostOrder(node -> {
            if (node.configuration == null) {
                node.configuration = emptyConfigurationSet;
            }
            List<MethodCallNode> callNodes = methodCallNodes.computeIfAbsent(node.methodInfo, info -> new ArrayList<>());
            callNodes.add(node);
        });

        return methodCallNodes;
    }

    /* This code is only ever executed by one thread. */
    @SuppressWarnings("NonAtomicOperationOnVolatileField")
    private static Set<MethodInfo> maybePropagateConfiguration(List<MethodCallNode> callNodes) {
        /*
         * Iterate over a given method's call nodes and try to find the common config across all
         * calls of that method. Then, for each call node of the given method: 1. Set the common
         * config as the configuration of that node 2. Find the parent call node of that node 3. Add
         * the config difference between the previous config and the common config of that node to
         * the parent node config
         */

        /* Only one call of this method happened. Keep everything as it is. */
        if (callNodes.size() <= 1) {
            return Collections.emptySet();
        }

        /* Find configuration present in every call of this method */
        ConfigurationSet commonConfig = findCommonConfigurationForMethod(callNodes);

        /*
         * For each call, determine the configuration unique to that call and see if any such
         * configuration exists
         */
        List<ConfigurationSet> newNodeConfiguration = new ArrayList<>();
        boolean hasNonEmptyNode = false;
        for (MethodCallNode node : callNodes) {
            ConfigurationSet callParentConfig = node.configuration.copyAndSubtract(commonConfig);
            if (!callParentConfig.isEmpty()) {
                hasNonEmptyNode = true;
            }
            newNodeConfiguration.add(callParentConfig);
        }

        /* All remaining configuration is common to each node, no need to propagate anything. */
        if (!hasNonEmptyNode) {
            return Collections.emptySet();
        }

        Set<MethodInfo> affectedNodes = new HashSet<>();
        for (int i = 0; i < callNodes.size(); i++) {
            MethodCallNode node = callNodes.get(i);
            ConfigurationSet uniqueNodeConfig = newNodeConfiguration.get(i);
            node.configuration = new ConfigurationSet(commonConfig);
            node.parent.configuration = node.parent.configuration.copyAndMerge(uniqueNodeConfig);
            affectedNodes.add(node.parent.methodInfo);
        }

        return affectedNodes;
    }

    private static ConfigurationSet findCommonConfigurationForMethod(List<MethodCallNode> callNodes) {
        ConfigurationSet config = null;
        for (MethodCallNode node : callNodes) {
            if (config == null) {
                config = node.configuration;
            } else {
                config = config.copyAndIntersectWith(node.configuration);
            }
        }
        return config;
    }

    private void createConditionalConfiguration() {
        Map<MethodInfo, List<MethodCallNode>> methodCallNodes = mapMethodsToCallNodes();

        propagateConfiguration(methodCallNodes);

        deduceConditionalConfiguration(methodCallNodes);
    }

    private void deduceConditionalConfiguration(Map<MethodInfo, List<MethodCallNode>> methodCallNodes) {
        /*
         * Once the configuration has been propagated, iterate over all call nodes and use each call
         * node as the condition for that call node's config.
         */
        MethodCallNode rootCallNode = methodCallNodes.remove(null).get(0);

        for (List<MethodCallNode> value : methodCallNodes.values()) {
            for (MethodCallNode node : value) {
                String className = node.methodInfo.getJavaDeclaringClassName();
                ConfigurationCondition condition = ConfigurationCondition.create(className);

                addConfigurationWithCondition(node.configuration, condition);
            }
        }

        addConfigurationWithCondition(rootCallNode.configuration, ConfigurationCondition.alwaysTrue());

        filterConfiguration();
    }

    private void addConfigurationWithCondition(ConfigurationSet nodeConfig, ConfigurationCondition condition) {
        TypeConfiguration reflectionConfig = nodeConfig.getReflectionConfiguration();
        configurationContainer.getReflectionConfiguration().mergeConditional(condition, reflectionConfig);

        TypeConfiguration jniConfig = nodeConfig.getJniConfiguration();
        configurationContainer.getJniConfiguration().mergeConditional(condition, jniConfig);

        ResourceConfiguration resourceConfiguration = nodeConfig.getResourceConfiguration();
        configurationContainer.getResourceConfiguration().mergeConditional(condition, resourceConfiguration);

        ProxyConfiguration proxyConfiguration = nodeConfig.getProxyConfiguration();
        configurationContainer.getProxyConfiguration().mergeConditional(condition, proxyConfiguration);

        SerializationConfiguration serializationConfiguration = nodeConfig.getSerializationConfiguration();
        configurationContainer.getSerializationConfiguration().mergeConditional(condition, serializationConfiguration);

        PredefinedClassesConfiguration predefinedClassesConfiguration = nodeConfig.getPredefinedClassesConfiguration();
        configurationContainer.getPredefinedClassesConfiguration().mergeConditional(condition, predefinedClassesConfiguration);
    }

    private void filterConfiguration() {
        configurationContainer = configurationContainer.filter(predicate);
    }

    private static void propagateConfiguration(Map<MethodInfo, List<MethodCallNode>> methodCallNodes) {
        /*
         * Iteratively propagate configuration from children to parent calls until an iteration
         * doesn't produce any changes.
         */

        Set<MethodInfo> methodsToHandle = methodCallNodes.keySet();
        while (methodsToHandle.size() != 0) {
            Set<MethodInfo> nextIterationMethodsToHandle = new HashSet<>();
            for (List<MethodCallNode> callNodes : methodCallNodes.values()) {
                Set<MethodInfo> affectedMethods = maybePropagateConfiguration(callNodes);
                nextIterationMethodsToHandle.addAll(affectedMethods);
            }
            methodsToHandle = nextIterationMethodsToHandle;
        }
    }

    private boolean methodOriginatesFromApplicationPackage(MethodInfo methodInfo) {
        return userCodeFilter.includes(methodInfo.getJavaDeclaringClassName());
    }

    @Override
    protected MethodInfo[] filterStackTrace(MethodInfo[] stackTrace) {
        /* Keep only the classes from the selected package names on the stack trace. */
        int dest = 0;
        for (int i = 0; i < stackTrace.length; ++i) {
            if (methodOriginatesFromApplicationPackage(stackTrace[i])) {
                stackTrace[dest++] = stackTrace[i];
            }
        }

        /* No classes on this trace originated from user code */
        if (dest == 0) {
            return null;
        }

        return Arrays.copyOfRange(stackTrace, 0, dest);
    }

    @Override
    public boolean supportsOnUnloadTraceWriting() {
        return true;
    }

    @Override
    public boolean supportsPeriodicTraceWriting() {
        return false;
    }

    @Override
    public List<Path> writeToDirectory(Path directoryPath) throws IOException {
        List<Path> writtenFiles;
        writtenFiles = ConfigurationSet.writeConfiguration(configurationFile -> directoryPath.resolve(configurationFile.getFileName("-origins.txt")),
                        configurationFile -> new HumanReadableConfigurationWithOrigins(rootNode, configurationFile));
        createConditionalConfiguration();
        writtenFiles.addAll(configurationContainer.writeConfiguration(configurationFile -> directoryPath.resolve(configurationFile.getFileName())));
        return writtenFiles;
    }
}
