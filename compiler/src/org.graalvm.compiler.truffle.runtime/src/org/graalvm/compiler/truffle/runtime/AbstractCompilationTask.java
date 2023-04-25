/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import static org.graalvm.compiler.truffle.runtime.OptimizedCallTarget.runtime;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilationTask;
import org.graalvm.compiler.truffle.common.TruffleSourceLanguagePosition;

import com.oracle.truffle.api.dsl.Introspection;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.SourceSection;

import jdk.vm.ci.meta.JavaConstant;

public abstract class AbstractCompilationTask implements TruffleCompilationTask {

    @SuppressWarnings("deprecation") private TruffleInlining inliningData;

    private final List<OptimizedCallTarget> targetsToDequeue = new ArrayList<>();
    private final List<OptimizedCallTarget> inlinedTargets = new ArrayList<>();
    private int callCount = -1;
    private int inlinedCallCount = -1;
    private EconomicMap<Node, TruffleSourceLanguagePosition> sourcePositionCache;
    private int nodeIdCounter;

    /**
     * @deprecated this method is scheduled for removal. Use methods of
     *             {@link AbstractCompilationTask} instead.
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public TruffleInlining getInlining() {
        if (inliningData == null) {
            inliningData = new TruffleInlining(this);
        }
        return inliningData;
    }

    public void dequeueTargets() {
        for (OptimizedCallTarget target : targetsToDequeue) {
            target.dequeueInlined();
        }
    }

    public int countCalls() {
        return callCount;
    }

    public int countInlinedCalls() {
        return inlinedCallCount;
    }

    public CompilableTruffleAST[] inlinedTargets() {
        return inlinedTargets.toArray(new CompilableTruffleAST[0]);
    }

    @Override
    public void addInlinedTarget(CompilableTruffleAST target) {
        inlinedTargets.add((OptimizedCallTarget) target);
    }

    @Override
    public void addTargetToDequeue(CompilableTruffleAST target) {
        targetsToDequeue.add((OptimizedCallTarget) target);
    }

    @Override
    public void setCallCounts(int total, int inlined) {
        callCount = total;
        inlinedCallCount = inlined;
    }

    @Override
    public TruffleSourceLanguagePosition getPosition(JavaConstant node) {
        Node truffleNode = runtime().asObject(Node.class, node);
        if (truffleNode == null) {
            return null;
        }
        EconomicMap<Node, TruffleSourceLanguagePosition> cache = this.sourcePositionCache;
        if (cache == null) {
            sourcePositionCache = cache = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        }
        TruffleSourceLanguagePosition position = cache.get(truffleNode);
        if (position != null) {
            return position;
        }
        SourceSection section = null;
        if (truffleNode instanceof DirectCallNode) {
            section = ((DirectCallNode) truffleNode).getCurrentRootNode().getSourceSection();
        }
        if (section == null) {
            section = truffleNode.getSourceSection();
        }
        if (section == null) {
            Node cur = truffleNode.getParent();
            while (cur != null) {
                section = cur.getSourceSection();
                if (section != null) {
                    break;
                }
                cur = cur.getParent();
            }
        }
        position = new AbstractCompilationTask.TruffleSourcePositionImpl(section, truffleNode.getClass(), nodeIdCounter++);
        sourcePositionCache.put(truffleNode, position);
        return position;
    }

    @Override
    public Map<String, Object> getDebugProperties(JavaConstant node) {
        Node truffleNode = runtime().asObject(Node.class, node);
        if (truffleNode == null) {
            return Collections.emptyMap();
        }
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.putAll(truffleNode.getDebugProperties());
        computeNodeProperties(map, truffleNode);
        return map;
    }

    private static void computeNodeProperties(Map<String, ? super Object> properties, com.oracle.truffle.api.nodes.Node node) {
        NodeCost nodeCost = node.getCost();
        if (nodeCost != null) {
            properties.put("cost", node.getCost());
        }
        var nodeInfo = node.getClass().getAnnotation(com.oracle.truffle.api.nodes.NodeInfo.class);
        if (nodeInfo != null) {
            if (!nodeInfo.shortName().isEmpty()) {
                properties.put("shortName", nodeInfo.shortName());
            }
        }
        properties.putAll(NodeUtil.collectNodeProperties(node));

        if (Introspection.isIntrospectable(node)) {
            final List<Introspection.SpecializationInfo> specializations = Introspection.getSpecializations(node);
            for (Introspection.SpecializationInfo specialization : specializations) {
                final String methodName = "specialization." + specialization.getMethodName();
                String state;
                if (specialization.isActive()) {
                    state = "active";
                } else if (specialization.isExcluded()) {
                    state = "excluded";
                } else {
                    state = "inactive";
                }
                properties.put(methodName, state);
                if (specialization.getInstances() > 1 || (specialization.getInstances() == 1 && specialization.getCachedData(0).size() > 0)) {
                    properties.put(methodName + ".instances", specialization.getInstances());
                    for (int instance = 0; instance < specialization.getInstances(); instance++) {
                        final List<Object> cachedData = specialization.getCachedData(instance);
                        int cachedIndex = 0;
                        for (Object o : cachedData) {
                            properties.put(methodName + ".instance[" + instance + "].cached[" + cachedIndex + "]", o);
                            cachedIndex++;
                        }
                    }
                }
            }
        }

        for (var entry : properties.entrySet()) {
            Object value = entry.getValue();
            if (isValidProtocolValue(value)) {
                continue;
            }
            if (value instanceof Enum<?> e) {
                value = e.getDeclaringClass().getSimpleName() + "." + e.name();
            } else {
                value = value.toString();
            }
            properties.put(entry.getKey(), value);
        }
    }

    /*
     * We only support primitives across native and isolation boundary. So we can just as well call
     * toString() for all non primitive values. In case toString() would fail we fail, we have the
     * same behavior as in isolated modes.
     */
    private static boolean isValidProtocolValue(Object object) {
        if (object == null) {
            return true;
        }
        Class<?> clz = object.getClass();
        return clz == String.class || clz == Boolean.class || clz == Byte.class || clz == Integer.class || clz == Long.class || clz == Float.class || clz == Double.class;
    }

    static String className(Class<?> clazz) {
        String name = clazz.getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private static class TruffleSourcePositionImpl implements TruffleSourceLanguagePosition {

        private final SourceSection sourceSection;
        private final Class<?> nodeClass;
        private final int nodeId;

        TruffleSourcePositionImpl(SourceSection section, Class<?> nodeClass, int nodeId) {
            this.sourceSection = section;
            this.nodeClass = nodeClass;
            this.nodeId = nodeId;
        }

        @Override
        public String getDescription() {
            if (sourceSection == null) {
                return "<no-description>";
            }
            return sourceSection.getSource().getURI() + " " + sourceSection.getStartLine() + ":" + sourceSection.getStartColumn();
        }

        @Override
        public int getOffsetEnd() {
            if (sourceSection == null) {
                return -1;
            }
            return sourceSection.getCharEndIndex();
        }

        @Override
        public int getOffsetStart() {
            if (sourceSection == null) {
                return -1;
            }
            return sourceSection.getCharIndex();
        }

        @Override
        public int getLineNumber() {
            if (sourceSection == null) {
                return -1;
            }
            return sourceSection.getStartLine();
        }

        @Override
        public URI getURI() {
            if (sourceSection == null) {
                return null;
            }
            return sourceSection.getSource().getURI();
        }

        @Override
        public String getLanguage() {
            if (sourceSection == null) {
                return null;
            }
            return sourceSection.getSource().getLanguage();
        }

        @Override
        public int getNodeId() {
            return nodeId;
        }

        @Override
        public String getNodeClassName() {
            return nodeClass.getName();
        }

    }

}
