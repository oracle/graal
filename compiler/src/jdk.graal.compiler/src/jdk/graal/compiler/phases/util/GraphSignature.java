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
package jdk.graal.compiler.phases.util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.util.Digest;

/**
 * A utility class that computes graph signatures and canonical node identity which can be useful
 * for comparing graphs produced by different compilations.
 */
public class GraphSignature {

    private int nextId;
    private final NodeMap<Integer> canonicalId;
    private final byte[] signature;

    @SuppressWarnings("this-escape")
    public GraphSignature(StructuredGraph graph) {
        canonicalId = graph.createNodeMap();
        signature = computeSignature(graph);
    }

    public int getId(Node node) {
        return canonicalId.get(node);
    }

    public String getSignatureString() {
        return getSignatureString(signature);
    }

    public byte[] getSignature() {
        return signature;
    }

    enum Tag {
        Block,
        Node,
        BlockEnd
    }

    protected byte[] computeSignature(StructuredGraph graph) {
        ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(byteArray);
        computeFromSchedule(graph, dos);
        return getSignature(byteArray);
    }

    private static byte[] getSignature(ByteArrayOutputStream byteArray) {
        byte[] data = byteArray.toByteArray();
        if (data.length == 0) {
            return null;
        }
        return Digest.digestAsByteArray(data, 0, data.length);
    }

    private static String getSignatureString(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @SuppressWarnings("try")
    private void computeFromSchedule(StructuredGraph graph, DataOutputStream dos) {
        try {
            StructuredGraph.ScheduleResult scheduleResult;
            DebugContext debug = graph.getDebug();
            try (DebugContext.Scope scope = debug.disable()) {
                SchedulePhase.runWithoutContextOptimizations(graph);
                scheduleResult = graph.getLastSchedule();
            } catch (GraalError e) {
                return;
            }
            assignNumbers(scheduleResult);
            for (HIRBlock block : scheduleResult.getCFG().getBlocks()) {
                List<Node> nodes = scheduleResult.getBlockToNodesMap().get(block);
                writeBlock(dos, block, nodes);
            }
        } catch (IOException e) {
            throw new GraalError(e);
        }
    }

    private void assignNumbers(StructuredGraph.ScheduleResult scheduleResult) {
        for (HIRBlock block : scheduleResult.getCFG().getBlocks()) {
            for (Node node : scheduleResult.getBlockToNodesMap().get(block)) {
                if (node.isAlive()) {
                    setId(node);
                    if (node instanceof AbstractMergeNode) {
                        // Phis aren't part of the schedule
                        AbstractMergeNode merge = (AbstractMergeNode) node;
                        for (PhiNode phi : merge.phis()) {
                            setId(phi);
                        }
                    } else if (node instanceof LoopExitNode) {
                        LoopExitNode exit = (LoopExitNode) node;
                        for (ProxyNode proxy : exit.proxies()) {
                            setId(proxy);
                        }
                    }
                }
            }
        }
    }

    /**
     * Write the block identity with the successor edges and then writes all nodes in order.
     */
    private void writeBlock(DataOutputStream dos, HIRBlock block, List<Node> nodes) throws IOException {
        dos.writeByte(Tag.Block.ordinal());
        dos.writeInt(block.getId());
        dos.writeInt(block.getSuccessorCount());
        for (int i = 0; i < block.getSuccessorCount(); i++) {
            HIRBlock successor = block.getSuccessorAt(i);
            dos.writeInt(successor.getId());
        }
        for (Node node : nodes) {
            if (node.isAlive()) {
                writeNode(dos, node);
                if (node instanceof AbstractMergeNode) {
                    // Phis aren't part of the schedule
                    AbstractMergeNode merge = (AbstractMergeNode) node;
                    for (PhiNode phi : merge.phis()) {
                        writeNode(dos, phi);
                    }
                } else if (node instanceof LoopExitNode) {
                    LoopExitNode exit = (LoopExitNode) node;
                    // Proxies aren't part of the schedule
                    for (ProxyNode proxy : exit.proxies()) {
                        writeNode(dos, proxy);
                    }
                }
            }
        }
        dos.writeByte(Tag.BlockEnd.ordinal());
    }

    private int getCanonicalId(Node node) {
        Integer id = canonicalId.get(node);
        GraalError.guarantee(id != null, "must have been numbered");
        return id;
    }

    /**
     * Assign the next sequential id to {@ocde node}.
     */
    private int setId(Node node) {
        assert canonicalId.get(node) == null;
        int id = nextId++;
        canonicalId.set(node, id);
        return id;
    }

    /**
     * Write a node with its inputs.
     */
    private void writeNode(DataOutputStream dos, Node node) throws IOException {
        dos.writeByte(Tag.Node.ordinal());
        dos.writeInt(getCanonicalId(node));
        dos.writeUTF(node.getClass().getSimpleName());
        dos.writeInt(node.getUsageCount());
        for (Node input : node.inputs()) {
            dos.writeInt(getCanonicalId(input));
        }
    }
}
