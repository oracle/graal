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
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.UnmodifiableMapCursor;
import org.graalvm.compiler.graph.Node;
import org.graalvm.util.CollectionsUtil;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class InliningLogCodec extends CompanionObjectCodec<InliningLog, InliningLogCodec.EncodedInliningLog> {
    private static final class EncodedCallsite {
        private final List<InliningLog.Decision> decisions;
        private final List<EncodedCallsite> children;
        private final int bci;
        private final EncodedCallsite originalCallsite;
        private final Integer invokableNode;
        private final ResolvedJavaMethod target;
        private final boolean indirect;

        private EncodedCallsite(List<InliningLog.Decision> decisions, List<EncodedCallsite> children, int bci,
                        EncodedCallsite originalCallsite, Integer invokableNode, ResolvedJavaMethod target,
                        boolean indirect) {
            this.decisions = decisions;
            this.children = children;
            this.bci = bci;
            this.originalCallsite = originalCallsite;
            this.invokableNode = invokableNode;
            this.target = target;
            this.indirect = indirect;
        }
    }

    protected static final class EncodedInliningLog implements EncodedObject {
        private EncodedCallsite root;

        private EconomicMap<Integer, EncodedCallsite> leaves;
    }

    private static final class InliningLogEncoder implements Encoder<InliningLog, EncodedInliningLog> {
        @Override
        public boolean shouldBeEncoded(InliningLog inliningLog) {
            return inliningLog != null && (!inliningLog.getRootCallsite().getDecisions().isEmpty() || !inliningLog.getRootCallsite().getChildren().isEmpty());
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
            encodedObject.leaves = EconomicMap.create(inliningLog.getLeaves().size());
            UnmodifiableMapCursor<Invokable, InliningLog.Callsite> cursor = inliningLog.getLeaves().getEntries();
            while (cursor.advance()) {
                Integer invokableNode = encodeInvokable(cursor.getKey(), mapper);
                if (invokableNode != null) {
                    encodedObject.leaves.put(invokableNode, replacements.get(cursor.getValue()));
                }
            }
        }

        private static Integer encodeInvokable(Invokable invokable, Function<Node, Integer> mapper) {
            if (invokable == null) {
                return null;
            }
            FixedNode fixedNode = invokable.asFixedNodeOrNull();
            if (fixedNode == null || !fixedNode.isAlive()) {
                return null;
            }
            return mapper.apply(fixedNode);
        }

        private static EncodedCallsite encodeSubtree(InliningLog.Callsite replacementSite, Function<Node, Integer> mapper,
                        EconomicMap<InliningLog.Callsite, EncodedCallsite> mapping) {
            Integer invokableNode = encodeInvokable(replacementSite.getInvoke(), mapper);
            EncodedCallsite originalCallsite = replacementSite.getOriginalCallsite() == null ? null : mapping.get(replacementSite.getOriginalCallsite());
            EncodedCallsite site = new EncodedCallsite(replacementSite.getDecisions(), new ArrayList<>(), replacementSite.getBci(),
                            originalCallsite, invokableNode, replacementSite.getTarget(), replacementSite.isIndirect());
            mapping.put(replacementSite, site);
            for (InliningLog.Callsite replacementChild : replacementSite.getChildren()) {
                site.children.add(encodeSubtree(replacementChild, mapper, mapping));
            }
            return site;
        }
    }

    private static final class InliningLogDecoder implements Decoder<InliningLog> {
        @Override
        public void decode(InliningLog inliningLog, Object encodedObject, Function<Integer, Node> mapper) {
            assert encodedObject instanceof EncodedInliningLog;
            EncodedInliningLog instance = (EncodedInliningLog) encodedObject;
            assert instance.root != null && instance.leaves != null;
            EconomicMap<EncodedCallsite, InliningLog.Callsite> replacements = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
            inliningLog.setRootCallsite(decodeSubtree(null, instance.root, mapper, replacements));
            MapCursor<Integer, EncodedCallsite> cursor = instance.leaves.getEntries();
            while (cursor.advance()) {
                Invokable invokable = (Invokable) mapper.apply(cursor.getKey());
                inliningLog.addLeafCallsite(invokable, replacements.get(cursor.getValue()));
            }
        }

        private static InliningLog.Callsite decodeSubtree(InliningLog.Callsite parent, EncodedCallsite replacementSite,
                        Function<Integer, Node> mapper, EconomicMap<EncodedCallsite, InliningLog.Callsite> replacements) {
            Invokable invokable = replacementSite.invokableNode == null ? null : (Invokable) mapper.apply(replacementSite.invokableNode);
            InliningLog.Callsite originalCallsite = replacementSite.originalCallsite == null ? null : replacements.get(replacementSite.originalCallsite);
            InliningLog.Callsite site = new InliningLog.Callsite(parent, originalCallsite, invokable, replacementSite.target,
                            replacementSite.bci, replacementSite.indirect);
            site.getDecisions().addAll(replacementSite.decisions);
            replacements.put(replacementSite, site);
            for (EncodedCallsite replacementChild : replacementSite.children) {
                decodeSubtree(site, replacementChild, mapper, replacements);
            }
            return site;
        }

        @Override
        public boolean verify(InliningLog original, InliningLog decoded) {
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

    public InliningLogCodec() {
        super(StructuredGraph::getInliningLog, new InliningLogEncoder(), new InliningLogDecoder());
    }
}
