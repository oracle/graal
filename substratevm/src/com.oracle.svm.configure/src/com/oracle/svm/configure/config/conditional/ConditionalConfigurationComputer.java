/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.svm.configure.ConfigurationBase;
import com.oracle.svm.configure.ConfigurationFile;
import com.oracle.svm.configure.NamedConfigurationTypeDescriptor;
import com.oracle.svm.configure.UnresolvedConfigurationCondition;
import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.filters.ComplexFilter;

/**
 * Generates a {@link ConfigurationSet} from a {@link MethodCallNode tree of calling contexts}. This
 * process generates conditional configuration entries using a heuristic to infer the methods
 * responsible for each entry.
 */
public class ConditionalConfigurationComputer {

    private final MethodCallNode rootNode;
    private final ComplexFilter userCodeFilter;
    private final ConditionalConfigurationPredicate configurationFilter;

    public ConditionalConfigurationComputer(MethodCallNode rootNode, ComplexFilter userCodeFilter, ConditionalConfigurationPredicate configurationFilter) {
        this.rootNode = rootNode;
        this.userCodeFilter = userCodeFilter;
        this.configurationFilter = configurationFilter;
    }

    public ConfigurationSet computeConditionalConfiguration() {
        retainOnlyUserCodeMethodCallNodes();
        Map<MethodInfo, List<MethodCallNode>> methodCallNodes = mapMethodsToCallNodes();
        propagateConfiguration(methodCallNodes);
        return inferConditionalConfiguration(methodCallNodes);
    }

    private void retainOnlyUserCodeMethodCallNodes() {
        retainOnlyUserCodeMethodCallNodes(rootNode);
    }

    /**
     * Remove nodes from the tree that don't match the user filter. The configuration (shown in
     * parentheses) of a removed node is merged into the parent, and its children are merged into
     * the parent's children. For example:
     *
     * <pre>
     *          A(v)                             A(v+x)
     *         /   \                            /     \
     *      B(w)   to_filter(x)     ==>     B(w+z)     C(y)
     *             /        \
     *           C(y)      B(z)
     * </pre>
     */
    private void retainOnlyUserCodeMethodCallNodes(MethodCallNode node) {
        /* Make a copy as the map can change under our feet */
        List<MethodCallNode> calledMethods = new ArrayList<>(node.calledMethods.values());
        for (MethodCallNode child : calledMethods) {
            retainOnlyUserCodeMethodCallNodes(child);
        }

        node.calledMethods.values().removeIf(child -> {
            if (!userCodeFilter.includes(child.methodInfo.getJavaDeclaringClassName())) {
                child.parent.mergeSubTree(child, child.parent != rootNode);
                return true;
            }
            return false;
        });
    }

    /**
     * Create a mapping from each method to the call nodes of that method in the call tree.
     */
    private Map<MethodInfo, List<MethodCallNode>> mapMethodsToCallNodes() {
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

    /**
     * Perform one round of propagation. Return the set of methods that inherited configurations
     * from their children (i.e., methods that need to be re-visited).
     */
    @SuppressWarnings("NonAtomicOperationOnVolatileField") // only executed by one thread
    private static Set<MethodInfo> maybePropagateConfiguration(List<MethodCallNode> callNodes) {
        // Only one call of this method happened. Cannot infer a config to propagate.
        if (callNodes.size() <= 1) {
            return Collections.emptySet();
        }

        // Compute the configuration common to every call of this method.
        ConfigurationSet commonConfig = findCommonConfigurationForMethod(callNodes);

        // Compute the configuration to propagate from each node by subtracting the common config.
        final boolean commonConfigIsEmpty = commonConfig.isEmpty();
        ConfigurationSet[] configsToPropagate = new ConfigurationSet[callNodes.size()];
        boolean configsToPropagateAllEmpty = true;
        for (int i = 0; i < callNodes.size(); i++) {
            configsToPropagate[i] = commonConfigIsEmpty ? callNodes.get(i).configuration : callNodes.get(i).configuration.copyAndSubtract(commonConfig);
            configsToPropagateAllEmpty = configsToPropagateAllEmpty && configsToPropagate[i].isEmpty();
        }

        // All nodes share the same configuration. Nothing to propagate.
        if (configsToPropagateAllEmpty) {
            return Collections.emptySet();
        }

        // Propagate the configurations.
        Set<MethodInfo> nodesWithPropagatedConfig = new HashSet<>();
        for (int i = 0; i < callNodes.size(); i++) {
            if (configsToPropagate[i].isEmpty()) {
                continue; // Nothing to propagate.
            }
            MethodCallNode node = callNodes.get(i);
            /*
             * NB: We propagate to the first ancestor that represents a different method. If the
             * method is recursive, its parent could be itself; we don't want to propagate the
             * configuration to the same method, because 1) the propagated configuration may be lost
             * when the parent is assigned the common configuration and 2) it will eventually be
             * propagated up anyway.
             */
            MethodCallNode nodeToUpdate = getFirstDifferingAncestor(node);
            node.configuration = new ConfigurationSet(commonConfig);
            nodeToUpdate.configuration = nodeToUpdate.configuration.copyAndMerge(configsToPropagate[i]);
            nodesWithPropagatedConfig.add(nodeToUpdate.methodInfo);
        }
        return nodesWithPropagatedConfig;
    }

    private static MethodCallNode getFirstDifferingAncestor(MethodCallNode method) {
        assert method.methodInfo != null;
        MethodCallNode current = method;
        do {
            current = current.parent;
        } while (method.methodInfo.equals(current.methodInfo));
        return current;
    }

    /**
     * Compute the set intersection of the call nodes' configurations.
     */
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

    /**
     * Use the call tree with propagated configurations to generate a final configuration set with
     * inferred conditional entries.
     */
    private ConfigurationSet inferConditionalConfiguration(Map<MethodInfo, List<MethodCallNode>> methodCallNodes) {
        ConfigurationSet configurationSet = new ConfigurationSet();

        /*
         * For any configuration entries propagated to the root, the algorithm could not infer a
         * method that "caused" them. Register them as unconditional.
         */
        MethodCallNode rootCallNode = methodCallNodes.remove(null).getFirst();
        addConfigurationWithCondition(configurationSet, rootCallNode.configuration, UnresolvedConfigurationCondition.alwaysTrue());

        /*
         * For other configuration entries, use the associated method's class as the "cause".
         */
        for (List<MethodCallNode> list : methodCallNodes.values()) {
            ConfigurationSet configurationToAdd = list.getFirst().configuration;
            assert list.stream().allMatch(node -> node.configuration.equals(configurationToAdd)) : "The ";
            for (MethodCallNode node : list) {
                String className = node.methodInfo.getJavaDeclaringClassName();
                UnresolvedConfigurationCondition condition = UnresolvedConfigurationCondition.create(NamedConfigurationTypeDescriptor.fromJSONName(className));
                var resolvedCondition = ConfigurationConditionResolver.identityResolver().resolveCondition(condition);
                addConfigurationWithCondition(configurationSet, node.configuration, resolvedCondition.get());
            }
        }

        return configurationSet.filter(configurationFilter);
    }

    /* Force the compiler to believe us we're referring to the same type. */
    private static <T extends ConfigurationBase<T, ?>> void mergeWithCondition(ConfigurationSet destConfigSet, ConfigurationSet srcConfigSet, UnresolvedConfigurationCondition condition,
                    ConfigurationFile configType) {
        T destConfig = destConfigSet.getConfiguration(configType);
        T srcConfig = srcConfigSet.getConfiguration(configType);
        destConfig.mergeConditional(condition, srcConfig);
    }

    private static void addConfigurationWithCondition(ConfigurationSet destConfigSet, ConfigurationSet srcConfigSet, UnresolvedConfigurationCondition condition) {
        for (ConfigurationFile configType : ConfigurationFile.agentGeneratedFiles()) {
            mergeWithCondition(destConfigSet, srcConfigSet, condition, configType);
        }
    }

    /**
     * Attempt to infer the methods responsible for configuration entries. This algorithm is a
     * fixed-point algorithm that uses a heuristic and is not guaranteed to be correct.
     * <p>
     * The general idea: the configuration entries common to all call sites of method {@code M}
     * (i.e., the intersection of their configurations) can probably be attributed to {@code M}. For
     * example, if all call sites of {@code M} cause a reflective access of class {@code Foo},
     * {@code M} likely performs reflection on {@code Foo}.
     * </p>
     * <p>
     * Conversely, the configuration entries <i>not</i> shared across all call sites of {@code M}
     * are likely not caused by {@code M}, and instead should be attributed to some method in the
     * calling context. For example, if {@code M} performs a reflective access on a type passed in
     * as a parameter, the associated configuration entry will be different at each call site, and
     * so the entry should be attributed to some other method in the calling context (i.e., some
     * method that caused the type to flow into {@code M}).
     * </p>
     * <p>
     * To implement this heuristic, we assign each node of {@code M} the "common" shared
     * configuration and then <i>propagate</i> the remaining configuration up the call tree (merging
     * it with the parent node). We repeat this process until there are no changes.
     * </p>
     * <p>
     * This heuristic is a little fragile and may miss some conditional configurations depending on
     * the method traversal order. For example, if {@code M} calls {@code N} in one context and
     * {@code O} in another, and both {@code N} and {@code O} have some configuration entry
     * {@code x} due to {@code M}, we would only attribute the entry to {@code M} if {@code N} and
     * {@code O} propagate their configuration before {@code M}; if {@code M} is processed in
     * between the two, {@code x} would not be common to all {@code M} nodes, and would incorrectly
     * be propagated up.
     * </p>
     */
    private static void propagateConfiguration(Map<MethodInfo, List<MethodCallNode>> methodCallNodes) {
        Set<MethodInfo> worklist = methodCallNodes.keySet();
        while (!worklist.isEmpty()) {
            Set<MethodInfo> newWorkList = new HashSet<>();
            for (List<MethodCallNode> callNodes : methodCallNodes.values()) {
                newWorkList.addAll(maybePropagateConfiguration(callNodes));
            }
            worklist = newWorkList;
        }
    }

}
