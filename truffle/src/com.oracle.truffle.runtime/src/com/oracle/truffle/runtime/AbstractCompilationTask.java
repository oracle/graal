/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime;

import static com.oracle.truffle.runtime.OptimizedCallTarget.runtime;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import com.oracle.truffle.api.dsl.Introspection;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.compiler.TruffleSourceLanguagePosition;

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

    public TruffleCompilable[] inlinedTargets() {
        return inlinedTargets.toArray(new TruffleCompilable[0]);
    }

    @Override
    public void addInlinedTarget(TruffleCompilable target) {
        inlinedTargets.add((OptimizedCallTarget) target);
    }

    @Override
    public void addTargetToDequeue(TruffleCompilable target) {
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
