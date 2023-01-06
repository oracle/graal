/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.graph.Node;
import org.graalvm.util.CollectionsUtil;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Encodes and decodes the {@link InliningLog}.
 *
 * @see CompanionObjectCodec
 */
public class InliningLogCodec extends CompanionObjectCodec<InliningLog, InliningLogCodec.EncodedInliningLog> {
    /**
     * An encoded representation of a {@link InliningLog.Callsite}. The fields match
     * {@link InliningLog.Callsite}, except there is an {@link #invokeOrderId} instead of a
     * {@link Invokable}.
     */
    private static final class EncodedCallsite {
        private final List<InliningLog.Decision> decisions;
        private final List<EncodedCallsite> children;
        private final int bci;
        private final EncodedCallsite originalCallsite;
        private final Integer invokeOrderId;
        private final ResolvedJavaMethod target;
        private final boolean indirect;

        private EncodedCallsite(List<InliningLog.Decision> decisions, List<EncodedCallsite> children, int bci,
                        EncodedCallsite originalCallsite, Integer invokeOrderId, ResolvedJavaMethod target,
                        boolean indirect) {
            this.decisions = decisions;
            this.children = children;
            this.bci = bci;
            this.originalCallsite = originalCallsite;
            this.invokeOrderId = invokeOrderId;
            this.target = target;
            this.indirect = indirect;
        }
    }

    /**
     * An encoded instance of the inlining log. It is not necessary to encode leaf callsites,
     * because they are created on {@link InliningLogDecoder#registerNode registration}.
     */
    protected static final class EncodedInliningLog implements EncodedObject {
        private EncodedCallsite root;
    }

    private static final class InliningLogEncoder implements Encoder<InliningLog, EncodedInliningLog> {
        @Override
        public boolean shouldBeEncoded(InliningLog inliningLog) {
            return inliningLog != null;
        }

        @Override
        public EncodedInliningLog prepare(InliningLog inliningLog) {
            assert shouldBeEncoded(inliningLog);
            return new EncodedInliningLog();
        }

        @Override
        public void encode(EncodedInliningLog encodedObject, InliningLog inliningLog, Function<Node, Integer> mapper) {
            assert shouldBeEncoded(inliningLog);
            EconomicMap<InliningLog.Callsite, EncodedCallsite> replacements = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
            encodedObject.root = encodeSubtree(inliningLog.getRootCallsite(), mapper, replacements);
        }

        private static Integer encodeInvokable(Invokable invokable, Function<Node, Integer> mapper) {
            if (invokable instanceof Node && ((Node) invokable).isAlive()) {
                return mapper.apply((Node) invokable);
            }
            return null;
        }

        private static EncodedCallsite encodeSubtree(InliningLog.Callsite replacementSite, Function<Node, Integer> mapper,
                        EconomicMap<InliningLog.Callsite, EncodedCallsite> mapping) {
            Integer invokableNode = encodeInvokable(replacementSite.getInvoke(), mapper);
            EncodedCallsite originalCallsite = replacementSite.getOriginalCallsite() == null ? null : mapping.get(replacementSite.getOriginalCallsite());
            EncodedCallsite site = new EncodedCallsite(new ArrayList<>(replacementSite.getDecisions()), new ArrayList<>(), replacementSite.getBci(),
                            originalCallsite, invokableNode, replacementSite.getTarget(), replacementSite.isIndirect());
            mapping.put(replacementSite, site);
            for (InliningLog.Callsite replacementChild : replacementSite.getChildren()) {
                site.children.add(encodeSubtree(replacementChild, mapper, mapping));
            }
            return site;
        }
    }

    private static final class InliningLogDecoder implements Decoder<InliningLog> {
        private EconomicMap<Integer, InliningLog.Callsite> orderIdToCallsite;

        @Override
        public InliningLog decode(StructuredGraph graph, Object encodedObject) {
            if (graph.getInliningLog() == null) {
                return null;
            }
            assert orderIdToCallsite == null;
            orderIdToCallsite = EconomicMap.create();
            InliningLog inliningLog = new InliningLog(null);
            if (encodedObject != null) {
                EncodedInliningLog instance = (EncodedInliningLog) encodedObject;
                assert instance.root != null;
                EconomicMap<EncodedCallsite, InliningLog.Callsite> replacements = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
                inliningLog.setRootCallsite(decodeSubtree(null, instance.root, replacements));
            }
            return inliningLog;
        }

        @Override
        public void registerNode(InliningLog inliningLog, Node node, int orderId) {
            if (inliningLog == null) {
                return;
            }
            if (!(node instanceof Invokable)) {
                return;
            }
            assert orderIdToCallsite != null;
            Invokable invokable = (Invokable) node;
            if (orderIdToCallsite.containsKey(orderId)) {
                InliningLog.Callsite callsite = orderIdToCallsite.get(orderId);
                callsite.setInvoke(invokable);
                inliningLog.addLeafCallsite(invokable, callsite);
            } else if (!inliningLog.containsLeafCallsite(invokable)) {
                inliningLog.trackNewCallsite(invokable);
            }
        }

        private InliningLog.Callsite decodeSubtree(InliningLog.Callsite parent, EncodedCallsite replacementSite, EconomicMap<EncodedCallsite, InliningLog.Callsite> replacements) {
            InliningLog.Callsite originalCallsite = replacementSite.originalCallsite == null ? null : replacements.get(replacementSite.originalCallsite);
            InliningLog.Callsite site = new InliningLog.Callsite(parent, originalCallsite, null, replacementSite.target,
                            replacementSite.bci, replacementSite.indirect);
            if (replacementSite.invokeOrderId != null) {
                orderIdToCallsite.put(replacementSite.invokeOrderId, site);
            }
            site.getDecisions().addAll(replacementSite.decisions);
            replacements.put(replacementSite, site);
            for (EncodedCallsite replacementChild : replacementSite.children) {
                decodeSubtree(site, replacementChild, replacements);
            }
            return site;
        }
    }

    public InliningLogCodec() {
        super(StructuredGraph::getInliningLog, new InliningLogEncoder());
    }

    @Override
    public Decoder<InliningLog> singleObjectDecoder() {
        return new InliningLogDecoder();
    }

    @Override
    public boolean verify(StructuredGraph originalGraph, StructuredGraph decodedGraph) {
        InliningLog original = originalGraph.getInliningLog();
        InliningLog decoded = decodedGraph.getInliningLog();
        if (original == null || decoded == null) {
            return true;
        }
        return subtreesEqual(original.getRootCallsite(), decoded.getRootCallsite());
    }

    private static boolean subtreesEqual(InliningLog.Callsite callsite1, InliningLog.Callsite callsite2) {
        if (callsite1 == null || callsite2 == null || !Objects.equals(callsite1.getTarget(), callsite2.getTarget()) ||
                        callsite1.getBci() != callsite2.getBci() || callsite1.isIndirect() != callsite2.isIndirect()) {
            return false;
        }
        Iterable<InliningLog.Callsite> children1 = () -> callsite1.getChildren().stream().iterator();
        Iterable<InliningLog.Callsite> children2 = () -> callsite2.getChildren().stream().iterator();
        return CollectionsUtil.allMatch(CollectionsUtil.zipLongest(children1, children2), (pair) -> subtreesEqual(pair.getLeft(), pair.getRight()));
    }
}
