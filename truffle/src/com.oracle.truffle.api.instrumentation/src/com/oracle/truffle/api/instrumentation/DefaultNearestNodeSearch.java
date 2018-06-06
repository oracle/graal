/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

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
            Node parent = findParentTaggedNode(node, tags);
            Node ch = findChildTaggedNode(node, offset, tags, parent != null, false);
            while (ch == null) {
                if (node == parent) {
                    return parent;
                }
                node = node.getParent();
                if (node == null) {
                    break;
                }
                ch = findChildTaggedNode(node, offset, tags, parent != null, false);
            }
            return ch;
        } else if (endIndex < offset) {
            return findLastNode(node, tags);
        } else { // offset < o1
            return findFirstNode(node, tags);
        }
    }

    /**
     * Finds the nearest tagged {@link Node node}. See the algorithm description at
     * {@link InstrumentableNode#findNearestNodeAt(int, Set)}.
     */
    private static Node findChildTaggedNode(Node node, int offset, Set<Class<? extends Tag>> tags, boolean haveOuterCandidate, boolean preferFirst) {
        Node[] highestLowerNode = new Node[]{null};
        Node[] highestLowerTaggedNode = new Node[]{null};
        Node[] lowestHigherNode = new Node[]{null};
        Node[] lowestHigherTaggedNode = new Node[]{null};
        final Node[] foundNode = new Node[]{null};
        NodeUtil.forEachChild(node, new NodeVisitor() {

            int highestLowerNodeIndex = 0;
            int highestLowerTaggedNodeIndex = 0;
            int lowestHigherNodeIndex = 0;
            int lowestHigherTaggedNodeIndex = 0;

            @Override
            public boolean visit(Node childNode) {
                Node ch = childNode;
                if (ch instanceof WrapperNode) {
                    ch = ((WrapperNode) ch).getDelegateNode();
                }
                SourceSection ss;
                if (ch instanceof InstrumentableNode && ((InstrumentableNode) ch).isInstrumentable()) {
                    ch = (Node) ((InstrumentableNode) ch).materializeInstrumentableNodes(tags);
                    ss = ch.getSourceSection();
                    if (ss == null) {
                        return true;
                    }
                } else {
                    // An unknown node, process its children on the same level.
                    NodeUtil.forEachChild(ch, this);
                    return foundNode[0] == null;
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
                    Node taggedNode = findChildTaggedNode(ch, offset, tags, isTagged || haveOuterCandidate, preferFirst);
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
                if (offset < i1) {
                    // We're after the offset
                    if (lowestHigherNode[0] == null || lowestHigherNodeIndex > i1) {
                        lowestHigherNode[0] = ch;
                        lowestHigherNodeIndex = i1;
                    }
                    if (isTagged) {
                        if (lowestHigherTaggedNode[0] == null || lowestHigherTaggedNodeIndex > i1) {
                            lowestHigherTaggedNode[0] = ch;
                            lowestHigherTaggedNodeIndex = i1;
                        }
                    }
                }
                if (i2 < offset) {
                    // We're before the offset
                    if (highestLowerNode[0] == null || (preferFirst ? i1 < highestLowerNodeIndex : highestLowerNodeIndex < i1)) {
                        highestLowerNode[0] = ch;
                        highestLowerNodeIndex = i1;
                    }
                    if (isTagged) {
                        if (highestLowerTaggedNode[0] == null || (preferFirst ? i1 < highestLowerTaggedNodeIndex : highestLowerTaggedNodeIndex < i1)) {
                            highestLowerTaggedNode[0] = ch;
                            highestLowerTaggedNodeIndex = i1;
                        }
                    }
                }
                return true;
            }
        });
        if (foundNode[0] != null) {
            return foundNode[0];
        }
        Node primaryNode;
        Node primaryTaggedNode;
        Node secondaryNode;
        Node secondaryTaggedNode;
        if (preferFirst) {
            // Prefer node before the offset:
            primaryNode = highestLowerNode[0];
            primaryTaggedNode = highestLowerTaggedNode[0];
            secondaryNode = lowestHigherNode[0];
            secondaryTaggedNode = lowestHigherTaggedNode[0];
        } else {
            // Prefer node after the offset:
            primaryNode = lowestHigherNode[0];
            primaryTaggedNode = lowestHigherTaggedNode[0];
            secondaryNode = highestLowerNode[0];
            secondaryTaggedNode = highestLowerTaggedNode[0];
        }

        if (isTaggedWith(primaryNode, tags)) {
            return primaryNode;
        }
        // Try to go in the preferred node:
        Node taggedNode = null;
        if (!haveOuterCandidate) {
            if (primaryNode != null) {
                taggedNode = findChildTaggedNode(primaryNode, offset, tags, haveOuterCandidate, true);
            }
        }
        if (taggedNode == null && primaryTaggedNode != null) {
            return primaryTaggedNode;
        }
        if (isTaggedWith(secondaryNode, tags)) {
            return secondaryNode;
        }
        // Try to go in a node before:
        if (!haveOuterCandidate) {
            if (taggedNode == null && secondaryNode != null) {
                taggedNode = findChildTaggedNode(secondaryNode, offset, tags, haveOuterCandidate, true);
            }
        }
        if (taggedNode == null && secondaryTaggedNode != null) {
            return secondaryTaggedNode;
        }
        return taggedNode;
    }

    /** Get the last character index (inclusive). */
    private static int getCharEndIndex(SourceSection ss) {
        if (ss.getCharLength() > 0) {
            return ss.getCharEndIndex() - 1;
        } else {
            return ss.getCharIndex();
        }
    }

    private static Node findParentTaggedNode(Node node, Set<Class<? extends Tag>> tags) {
        if (isTaggedWith(node, tags)) {
            return node;
        }
        Node parent = node.getParent();
        if (parent == null) {
            return null;
        }
        return findParentTaggedNode(parent, tags);
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

}
