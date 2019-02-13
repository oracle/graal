/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.objectfile.macho;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.Element;
import com.oracle.objectfile.io.AssemblyBuffer;
import com.oracle.objectfile.io.OutputAssembler;
import com.oracle.objectfile.macho.MachOObjectFile.LinkEditSegment64Command;

/**
 * This element implements a prefix trie mapping symbol names (strings) to their definitions. It's a
 * nicer analogue of the ELF hash section. This implementation seems to work, but it is currently
 * NOT used, since we avoid "new-style" Mach-O features and instead generate only "classic" Mach-O
 * files.
 */
class ExportTrieElement extends MachOObjectFile.LinkEditElement {

    /**
         *
         */
    private final MachOObjectFile owner;

    ExportTrieElement(String name, MachOObjectFile owner) {
        owner.super(name, owner.getLinkEditSegment());
        this.owner = owner;
        root = new TrieNode();
        root.suffix = "";
    }

    @Override
    public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
        return ObjectFile.defaultDependencies(decisions, this);
    }

    int totalNodesInTree;
    TrieNode root;

    class TrieTerminal {

        byte terminalFlags;
        long address;

        public void write(OutputAssembler oa) {
            oa.writeByte(terminalFlags);
            oa.writeLEB128(address);
        }
    }

    class TrieNode {

        {
            ++totalNodesInTree;
        }

        TrieTerminal terminal;
        String suffix;
        List<TrieNode> children = new ArrayList<>();
        TrieNode parent;

        private int encodedTerminalSize() {
            return (terminal == null) ? 0 : 1 + MachOObjectFile.encodedLengthLEB128(terminal.address);
        }

        private int worstCaseChildRecordSize(int childNum) {
            // our worst-case ULEB128 is a 64-bit number,
            // requiring 10 groups of 7 bits (9 * 7 is 63, one remaining)
            String childSuffix = children.get(childNum).suffix;
            int nullTerminatorSize = 1;
            return (childSuffix == null ? 0 : childSuffix.length()) + nullTerminatorSize + 10;
        }

        private int worstCaseNodeRecordSize() {
            /*
             * It's the length bytes plus encoded terminal size plus child-count byte plus
             * worst-case child records.
             */
            int childRecords = 0;
            for (int i = 0; i < children.size(); ++i) {
                childRecords += worstCaseChildRecordSize(i);
            }

            return 1 + encodedTerminalSize() + 1 + childRecords;
        }

        void writeRoot(OutputAssembler out) {
            assert this == root;

            Deque<TrieNode> nodes = new ArrayDeque<>();
            Deque<Integer> offsetUpperBounds = new ArrayDeque<>();
            /*
             * Invariant the union of the keysets of these two maps is the set of all nodes pulled
             * from the queue. As we add to the queue we take from the first map. As we take from
             * the queue, we move nodes into the second map.
             */
            Map<TrieNode, Integer> upperBoundsByNode = new HashMap<>();
            Map<TrieNode, Integer> offsetsByWrittenNode = new HashMap<>();

            // also remember where we've left a space for child pointers.
            Map<TrieNode, Integer> childPointerOffsets = new HashMap<>();
            Map<TrieNode, Integer> childPointerGapSizes = new HashMap<>();

            nodes.addLast(root);
            offsetUpperBounds.addLast(0);
            upperBoundsByNode.put(root, 0);

            while (!nodes.isEmpty()) {
                TrieNode node = nodes.removeFirst();
                int ourUpperBound = offsetUpperBounds.removeFirst();
                upperBoundsByNode.remove(node);

                int nodeStartPos = out.pos();
                assert ourUpperBound >= nodeStartPos;

                out.skip(1); // placeholder for terminal size
                int terminalStartPos = out.pos();
                if (node.terminal != null) {
                    node.terminal.write(out);
                }
                // go back and write the terminal size
                int terminalSize = out.pos() - terminalStartPos;
                out.pushSeek(nodeStartPos);
                out.writeByte((byte) terminalSize);
                out.pop();

                // store into the 'written nodes' map, to restore invariant
                offsetsByWrittenNode.put(node, nodeStartPos);

                /*
                 * For each child, write its suffix followed by its offset in the table. PROBLEM:
                 * how do we know what offsets we'll be writing these children at? Solution: 1.
                 * write nodes breadth-first -- KEEP A QUEUE. 2. when we enqueue something, compute
                 * a pessimistic upper bound on its offset in the table. This depends on the space
                 * taken by nodes earlier in the queue. So we can compute one upper bound from the
                 * last one, albeit drifting in accuracy.
                 *
                 * By computing an upper bound, we can be sure we leave enough space for the ULEB
                 * encoding of the eventual offset. Occasionally we'll leave an extra byte, because
                 * of the pessimistic nature of the upper bound. It takes a big drift just to cause
                 * one unnecessary byte of padding, so this isn't a big hit.
                 *
                 * If we go back and find that our ULEB128-encoded length doesn't fill up all the
                 * bytes we allocated for it, does it break things? NO, I don't think so. The ULEBs
                 * come at the end of a record, where an empty zero byte is harmless, because the
                 * next element will be reached by seeking to an explicit offset. In the case of the
                 * Terminal flags/addr, we do need to get their ULEB-encoded sizes right, but we
                 * don't have to pre-pad those; the terminal size field is a single byte which we
                 * patch up immediately after writing them, so we always know exactly how much space
                 * they take. By contrast, the problem with node records is that there might be a
                 * lot of intervening records of unknown size, before the referenced record, so we
                 * need to patch up its offset much later.
                 */
                // write the child count byte
                assert node.children.size() <= 255;
                out.writeByte((byte) node.children.size());
                for (TrieNode child : node.children) {
                    nodes.addLast(child);
                    /*
                     * What's the upper bound on its offset? We're still to write
                     *
                     * - the current node; if queue is empty, the current is the one that matters
                     *
                     * - all preceding nodes in the queue! use the upper bound of the last one to
                     * compute an upper bound for this one
                     */
                    int childUpperBound;
                    if (offsetUpperBounds.size() == 0) {
                        childUpperBound = nodeStartPos + node.worstCaseNodeRecordSize();
                    } else {
                        childUpperBound = offsetUpperBounds.peekLast() + nodes.peekLast().worstCaseNodeRecordSize();
                    }
                    offsetUpperBounds.addLast(childUpperBound);
                    upperBoundsByNode.put(child, childUpperBound);
                }

                /* Now write our current node's child records (leaving gaps for the offsets). */
                for (TrieNode child : node.children) {
                    out.writeString(child.suffix);
                    int childPointerPos = out.pos();
                    int gapSize = MachOObjectFile.encodedLengthLEB128(upperBoundsByNode.get(child));
                    out.skip(gapSize);
                    childPointerOffsets.put(child, childPointerPos);
                    childPointerGapSizes.put(child, gapSize);
                }

                /*
                 * Now go back and fill in the parent->child pointer for this node -- unless it's
                 * the root node, when it won't have one.
                 */
                if (node != root) {
                    int pointerOffset = childPointerOffsets.get(node);
                    int gapSize = childPointerGapSizes.get(node);
                    assert MachOObjectFile.encodedLengthLEB128(nodeStartPos) <= gapSize;
                    out.pushSeek(pointerOffset);
                    out.writeLEB128(nodeStartPos);
                    out.pop();
                    childPointerOffsets.remove(node);
                    childPointerGapSizes.remove(node);
                }
            }

            // assert that various maps are empty
            assert childPointerOffsets.isEmpty();
            assert childPointerGapSizes.isEmpty();
            assert nodes.isEmpty();
            assert offsetUpperBounds.isEmpty();
            assert upperBoundsByNode.isEmpty();
            assert offsetsByWrittenNode.size() == totalNodesInTree;
        }

        TrieNode() {
            // make root node
            suffix = null;
            terminal = null;
            parent = null;
        }

        TrieNode(TrieNode parent, TrieTerminal term) {
            parent.children.add(this);
            this.terminal = term;
            this.parent = parent;
        }

        TrieNode findLongestPrefixNode(String s, int startPos) {
            for (TrieNode child : children) {
                assert child.suffix != null;
                if (s.substring(startPos).startsWith(child.suffix)) {
                    /* recurse */
                    return child.findLongestPrefixNode(s, startPos + child.suffix.length());
                }
            }
            // else we're it
            return this;
        }

        String value() {
            List<TrieNode> ancestors = new ArrayList<>();
            TrieNode cur = this;
            do {
                ancestors.add(cur);
            } while ((cur = cur.parent) != null);
            // concatenate our ancestors in reverse order
            StringBuilder sb = new StringBuilder();
            for (int i = ancestors.size() - 1; i >= 0; --i) {
                String ancestorSuffix = ancestors.get(i).suffix;
                sb.append(ancestorSuffix == null ? "" : ancestorSuffix);
            }
            return sb.toString();
        }
    }

    int commonPrefixLength(String s1, String s2) {
        int i = 0;
        while (s1.length() > i && s2.length() > i && s1.charAt(i) == s2.charAt(i)) {
            ++i;
        }
        return i;
    }

    private void addSymbol(String s, long addr) {
        TrieNode longestPrefixNode = root.findLongestPrefixNode(s, 0);

        // we *might* have a duplicate in the sense of one symbol being a prefix of another...
        String longestPrefixValue = longestPrefixNode.value();
        if (longestPrefixValue.equals(s)) {
            // but the precise symbol should not exist already
            assert longestPrefixNode.terminal == null;
        }

        // if we share some more characters with a child of the longest prefix, split it
        TrieNode childWithPrefix = null;
        int childPrefixLength = 0;
        int childPosition = 0;
        for (TrieNode child : longestPrefixNode.children) {
            childPrefixLength = commonPrefixLength(child.suffix, s.substring(longestPrefixValue.length()));
            if (childPrefixLength > 0) {
                // assert that there's at most one such
                assert childWithPrefix == null;
                childWithPrefix = child;
                break; // so that childPosition points to correct child pos
            }
            ++childPosition;
        }
        // to be safe-ish...
        if (childWithPrefix == null) {
            childPosition = -1;
        }

        TrieNode nodeToInsertAt;

        // if we got a child, split that child...
        if (childWithPrefix != null) {
            assert childPrefixLength > 0;
            String suffixToInsert = childWithPrefix.suffix.substring(0, childPrefixLength);
            String newChildSuffix = childWithPrefix.suffix.substring(childPrefixLength);

            // remove before we make any changes to the child array, to keep offset valid
            assert longestPrefixNode.children.indexOf(childWithPrefix) == childPosition;
            assert longestPrefixNode.children.lastIndexOf(childWithPrefix) == childPosition;
            longestPrefixNode.children.remove(childPosition);
            assert longestPrefixNode.children.indexOf(childWithPrefix) == -1;

            // constructor will add the new child to longestPrefixNode's children array
            TrieNode newChild = new TrieNode(longestPrefixNode, null);
            // ... but the new node has no children
            assert newChild.children.size() == 0;
            // ... so move the old subtree down underneath the new child
            childWithPrefix.suffix = newChildSuffix;
            childWithPrefix.parent = newChild;
            newChild.children.add(childWithPrefix);
            assert newChild.children.size() == 1;
            // ... and set its suffix
            newChild.suffix = suffixToInsert;

            nodeToInsertAt = newChild;
        } else {
            // add a new child right where we are
            nodeToInsertAt = longestPrefixNode;
        }

        // if we have a nonempty prefix beyond our insertion node,
        // we create a child of that node and create the terminal
        // there; otherwise, we create the terminal right at the
        // node
        TrieNode whereToCreateTerminal;
        String stringToInsertAt = nodeToInsertAt.value();
        if (s.length() > stringToInsertAt.length()) {
            whereToCreateTerminal = new TrieNode(nodeToInsertAt, null);
            whereToCreateTerminal.suffix = s.substring(stringToInsertAt.length());
        } else {
            whereToCreateTerminal = nodeToInsertAt;
        }

        assert whereToCreateTerminal.terminal == null;
        whereToCreateTerminal.terminal = new TrieTerminal();
        whereToCreateTerminal.terminal.address = addr;
        whereToCreateTerminal.terminal.terminalFlags = 0;
    }

    @Override
    public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
        /* We need to build a prefix tree out of our exported symbols. */

        for (MachOSymtab.Entry ent : ((LinkEditSegment64Command) owner.getLinkEditSegment()).getSymtab().getSortedEntries()) {
            if (ent.isExternal() && ent.isDefined()) {
                // FIXME: do we really want the vaddr?
                long symbolVaddr = (int) alreadyDecided.get(ent.getDefinedSection()).getDecidedValue(LayoutDecision.Kind.VADDR) + ent.getDefinedOffset();
                addSymbol(ent.getNameInObject(), symbolVaddr);
            }
        }

        OutputAssembler oa = AssemblyBuffer.createOutputAssembler(getOwner().getByteOrder());
        root.writeRoot(oa);
        return oa.getBlob();
    }

    @Override
    public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
        return ((byte[]) alreadyDecided.get(this).getDecidedValue(LayoutDecision.Kind.CONTENT)).length;
    }
}
