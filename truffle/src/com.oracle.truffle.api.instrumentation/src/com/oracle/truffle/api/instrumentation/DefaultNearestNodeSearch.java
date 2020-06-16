/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.api.instrumentation.InstrumentableNode.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Implements the default logic of searching for the nearest {@link Node node} to the given source
 * character index according to the execution flow, that is tagged with the given tag.
 * <p>
 * <b>This code assumes that the Truffle Node hierarchy corresponds with the logical guest language
 * AST structure.</b>
 * <p>
 * If this is not the case for a particular guest language,
 * {@link InstrumentableNode#findNearestNodeAt(int, Set)} needs to be implemented, possibly with
 * logic in this class applied to the language logical AST nodes.
 */
class DefaultNearestNodeSearch {

    /**
     * We have a source offset (the character index) and a context node given. The offset is in
     * node's source. The context node is an instrumentable node, but may not have any particular
     * tags. We're searching for an instrumentable node tagged with at least one of the given tags,
     * which is nearest to the given offset according to the guest language execution flow. We find
     * the nearest tagged {@link Node node} in following steps:
     * <ul>
     * <li>When the offset is inside the context node, we search for the first tagged parent node
     * and call {@link #findChildTaggedNode(Node, int, Set, boolean)} to find a tagged child. If we
     * find it, we return it, if not, we continue to search recursively for a tagged child of node's
     * parent. If we encounter the first tagged parent node, return it, otherwise return tagged
     * child found during the recursive process, if any.</li>
     * <li>When the offset is behind the context node, we return the last tagged child of the
     * context node.</li>
     * <li>When the offset is before the context node, we return the first tagged child of the
     * context node.</li>
     * </ul>
     */
    static Node findNearestNodeAt(int offset, Node contextNode, Set<Class<? extends Tag>> tags) {
        Node node = (Node) ((InstrumentableNode) contextNode).materializeInstrumentableNodes(tags);
        SourceSection section = node.getSourceSection();
        int startIndex = section.getCharIndex();
        int endIndex = getCharEndIndex(section);
        if (startIndex <= offset && offset <= endIndex) {
            Node ch;
            while ((ch = findChildTaggedNode(node, offset, tags)) == null) {
                node = node.getParent();
                if (node == null) {
                    break;
                }
            }
            return ch;
        } else if (endIndex < offset) {
            return findLastNode(node, tags);
        } else { // offset < startIndex
            return findFirstNode(node, tags);
        }
    }

    private static void forEachInstrumentableChild(Node parent, NodeVisitor visitor, Set<Class<? extends Tag>> tags) {
        NodeUtil.forEachChild(parent, new NodeVisitor() {

            private boolean keepVisiting = true;

            @Override
            public boolean visit(Node childNode) {
                Node ch = childNode;
                if (ch instanceof WrapperNode) {
                    ch = ((WrapperNode) ch).getDelegateNode();
                }
                if (ch instanceof InstrumentableNode && ((InstrumentableNode) ch).isInstrumentable()) {
                    ch = (Node) ((InstrumentableNode) ch).materializeInstrumentableNodes(tags);
                } else {
                    // An unknown node, process its children on the same level.
                    NodeUtil.forEachChild(ch, this);
                    return keepVisiting;
                }
                return (keepVisiting = visitor.visit(ch));
            }
        });
    }

    /**
     * Finds the nearest tagged {@link Node node}. See the algorithm description at
     * {@link InstrumentableNode#findNearestNodeAt(int, Set)}.
     */
    private static Node findChildTaggedNode(Node node, int offset, Set<Class<? extends Tag>> tags) {
        // Nodes with lower offset that we search for, inspected from the highest one.
        SortedNodes lowerNodes = new SortedNodes();
        // Nodes with higher offset that we search for, inspected from the smallest one.
        SortedNodes higherNodes = new SortedNodes();
        final Node[] foundNode = new Node[]{null};
        forEachInstrumentableChild(node, new NodeVisitor() {

            int highestLowerTaggedNodeStart = -1; // The nearest tagged node with lower offset
            int highestLowerTaggedNodeEnd = -1;
            int lowestHigherTaggedNodeStart = -1; // The nearest tagged node with higher offset
            int lowestHigherTaggedNodeEnd = -1;

            @Override
            public boolean visit(Node ch) {
                SourceSection ss = ch.getSourceSection();
                if (ss == null || !ss.isAvailable()) {
                    return true;
                }
                boolean isTagged = isTaggedWith((InstrumentableNode) ch, tags);
                int i1 = ss.getCharIndex();
                int i2 = getCharEndIndex(ss);
                if (isTagged && offset == i1) {
                    // We're at it
                    foundNode[0] = ch;
                    return false;
                }
                if (i1 <= offset && offset <= i2) {
                    // In an encapsulating source section
                    Node taggedNode = findChildTaggedNode(ch, offset, tags);
                    if (taggedNode != null) {
                        foundNode[0] = taggedNode;
                        return false;
                    }
                    if (isTagged) {
                        // If nothing in and is tagged, return it
                        foundNode[0] = ch;
                        return false;
                    }
                }
                if (offset < i1 && !(lowestHigherTaggedNodeStart <= i1 && i2 <= lowestHigherTaggedNodeEnd)) {
                    // We're after the offset
                    if (lowestHigherTaggedNodeStart == -1 || lowestHigherTaggedNodeStart > i1) {
                        higherNodes.add(ch, i1);
                        if (isTagged) {
                            lowestHigherTaggedNodeStart = i1;
                            lowestHigherTaggedNodeEnd = i2;
                            higherNodes.cutHigherThan(i1);
                        }
                    }
                }
                if (i2 < offset && !(highestLowerTaggedNodeStart <= i1 && i2 <= highestLowerTaggedNodeEnd)) {
                    // We're before the offset
                    if (highestLowerTaggedNodeStart == -1 || (highestLowerTaggedNodeStart < i1)) {
                        lowerNodes.add(ch, i1);
                        if (isTagged) {
                            highestLowerTaggedNodeStart = i1;
                            highestLowerTaggedNodeEnd = i2;
                            lowerNodes.cutLowerThan(i1);
                        }
                    }
                }
                return true;
            }
        }, tags);
        if (foundNode[0] != null) {
            return foundNode[0];
        }
        Node taggedNode = findChildTaggedNode(higherNodes.nodes, higherNodes.size, offset, tags, false);
        if (taggedNode == null) {
            taggedNode = findChildTaggedNode(lowerNodes.nodes, lowerNodes.size, offset, tags, true);
        }
        return taggedNode;
    }

    private static Node findChildTaggedNode(Node[] nodes, int size, int offset, Set<Class<? extends Tag>> tags, boolean reverse) {
        if (nodes == null) {
            return null;
        }
        for (int i = (reverse ? size - 1 : 0); reverse ? i >= 0 : i < size; i = (reverse) ? i - 1 : i + 1) {
            Node node = nodes[i];
            if (isTaggedWith(node, tags)) {
                return node;
            }
            Node taggedNode = findChildTaggedNode(node, offset, tags);
            if (taggedNode != null) {
                return taggedNode;
            }
        }
        return null;
    }

    /** Get the last character index (inclusive). */
    private static int getCharEndIndex(SourceSection ss) {
        if (ss.getCharLength() > 0) {
            return ss.getCharEndIndex() - 1;
        } else {
            return ss.getCharIndex();
        }
    }

    private static Node findFirstNode(Node contextNode, Set<Class<? extends Tag>> tags) {
        Node[] first = new Node[]{null};
        contextNode.accept(new NodeVisitor() {
            @Override
            public boolean visit(Node node) {
                if (isTaggedWith(node, tags)) {
                    first[0] = node;
                    return false;
                }
                return true;
            }
        });
        return first[0];
    }

    private static Node findLastNode(Node contextNode, Set<Class<? extends Tag>> tags) {
        if (isTaggedWith(contextNode, tags)) {
            return contextNode;
        }
        List<Node> children = NodeUtil.findNodeChildren(contextNode);
        for (int i = children.size() - 1; i >= 0; i--) {
            Node ch = children.get(i);
            if (ch instanceof WrapperNode) {
                ch = ((WrapperNode) ch).getDelegateNode();
            }
            Node last = findLastNode(ch, tags);
            if (last != null) {
                return last;
            }
        }
        return null;
    }

    private static boolean isTaggedWith(Node node, Set<Class<? extends Tag>> tags) {
        if (node instanceof InstrumentableNode && ((InstrumentableNode) node).isInstrumentable()) {
            InstrumentableNode inode = ((InstrumentableNode) node).materializeInstrumentableNodes(tags);
            return isTaggedWith(inode, tags);
        }
        return false;
    }

    private static boolean isTaggedWith(InstrumentableNode inode, Set<Class<? extends Tag>> tags) {
        for (Class<? extends Tag> tag : tags) {
            if (inode.hasTag(tag)) {
                return true;
            }
        }
        return false;
    }

    private static final class SortedNodes {

        private static final int DEFAULT_SIZE = 10;

        private Node[] nodes = null;
        int[] nodeOffsets = null;
        int size = 0;

        void add(Node node, int offset) {
            if (nodes == null) {
                nodes = new Node[DEFAULT_SIZE];
                nodeOffsets = new int[DEFAULT_SIZE];
                nodes[0] = node;
                nodeOffsets[0] = offset;
                size++;
            } else {
                ensureCapacity(size + 1);
                if (nodeOffsets[size - 1] < offset) { // common case
                    nodes[size] = node;
                    nodeOffsets[size] = offset;
                } else {
                    int index = Arrays.binarySearch(nodeOffsets, 0, size, offset);
                    if (index < 0) {
                        index = -index - 1;
                    }
                    System.arraycopy(nodes, index, nodes, index + 1, size - index);
                    nodes[index] = node;
                    System.arraycopy(nodeOffsets, index, nodeOffsets, index + 1, size - index);
                    nodeOffsets[index] = offset;
                }
                size++;
            }
        }

        void ensureCapacity(int capacity) {
            if (nodes.length < capacity) {
                int newCapacity = capacity + (capacity >> 1);
                if (newCapacity < capacity) {
                    newCapacity = capacity;
                }
                nodes = Arrays.copyOf(nodes, newCapacity);
                nodeOffsets = Arrays.copyOf(nodeOffsets, newCapacity);
            }
        }

        void cutHigherThan(int offset) {
            int index = Arrays.binarySearch(nodeOffsets, 0, size, offset);
            if (index < 0) {
                index = -index - 1;
            } else {
                index++;
            }
            if (index < size) {
                size = index;
            }
        }

        void cutLowerThan(int offset) {
            int index = Arrays.binarySearch(nodeOffsets, 0, size, offset);
            if (index < 0) {
                index = -index - 1;
            }
            if (index > 0) {
                System.arraycopy(nodes, index, nodes, 0, size - index);
                System.arraycopy(nodeOffsets, index, nodeOffsets, 0, size - index);
                size -= index;
            }
        }
    }

}
