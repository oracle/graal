/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.matchers;

import com.oracle.truffle.regex.tregex.util.MathUtil;

import static com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Character range matcher using a left-balanced tree of ranges.
 */
public final class RangeTreeMatcher extends ProfiledCharMatcher {

    /**
     * Constructs a new {@link RangeTreeMatcher}.
     * 
     * @param invert see {@link ProfiledCharMatcher}.
     * @param ranges a sorted array of character ranges in the form [lower inclusive bound of range
     *            0, higher inclusive bound of range 0, lower inclusive bound of range 1, higher
     *            inclusive bound of range 1, ...]. The array contents are not modified by this
     *            method.
     * @return a new {@link RangeTreeMatcher}.
     */
    public static RangeTreeMatcher fromRanges(boolean invert, char[] ranges) {
        char[] tree = new char[ranges.length];
        buildTree(tree, 0, ranges, 0, ranges.length / 2);
        return new RangeTreeMatcher(invert, tree);
    }

    /**
     * Adapted version of the algorithm given by J. Andreas Baerentzen in
     * <a href="http://www2.imm.dtu.dk/pubdb/views/edoc_download.php/2535/pdf/imm2535.pdf">"On
     * Left-balancing Binary Trees"</a>.
     * 
     * @param tree The array that will hold the tree. Its size must be equal to that of the
     *            parameter "ranges".
     * @param curTreeElement Index of the current tree node to be computed. Note that every tree
     *            element takes up two array slots, since every node corresponds to a character
     *            range.
     * @param ranges Sorted array of character ranges, in the form [lower bound of range 0, higher
     *            bound of range 0, lower bound of range 1, higher bound of range 1, ...]
     * @param offset Starting index of the current part of the list that shall be converted to a
     *            subtree.
     * @param nRanges Number of _ranges_ in the current part of the list that shall be converted to
     *            a subtree. Again, note that every range takes up two array slots!
     */
    private static void buildTree(char[] tree, int curTreeElement, char[] ranges, int offset, int nRanges) {
        if (nRanges == 0) {
            return;
        }
        if (nRanges == 1) {
            tree[curTreeElement] = ranges[offset];
            tree[curTreeElement + 1] = ranges[offset + 1];
            return;
        }
        int nearestPowerOf2 = Integer.highestOneBit(nRanges);
        int remainder = nRanges - (nearestPowerOf2 - 1);
        int nLeft;
        int nRight;
        if (remainder <= nearestPowerOf2 / 2) {
            nLeft = (nearestPowerOf2 - 2) / 2 + remainder;
            nRight = (nearestPowerOf2 - 2) / 2;
        } else {
            nLeft = nearestPowerOf2 - 1;
            nRight = remainder - 1;
        }
        int median = offset + nLeft * 2;
        tree[curTreeElement] = ranges[median];
        tree[curTreeElement + 1] = ranges[median + 1];
        buildTree(tree, leftChild(curTreeElement), ranges, offset, nLeft);
        buildTree(tree, rightChild(curTreeElement), ranges, median + 2, nRight);
    }

    private static int leftChild(int i) {
        return (i * 2) + 2;
    }

    private static int rightChild(int i) {
        return (i * 2) + 4;
    }

    @CompilationFinal(dimensions = 1) private final char[] tree;

    private RangeTreeMatcher(boolean invert, char[] tree) {
        super(invert);
        this.tree = tree;
    }

    @Override
    public boolean matchChar(char c) {
        int i = 0;
        while (i < tree.length) {
            final char lo = tree[i];
            final char hi = tree[i + 1];
            if (lo <= c) {
                if (hi >= c) {
                    return true;
                } else {
                    i = rightChild(i);
                }
            } else {
                i = leftChild(i);
            }
        }
        return false;
    }

    @Override
    public int estimatedCost() {
        // In every node of the tree, we perform two array loads (4) and two int comparisons (2).
        // The number of nodes in the tree is tree.length / 2, so the depth d of the tree will be
        // MathUtil.log2ceil(tree.length / 2).
        // The average depth of traversal is then d - 1.
        return 6 * (MathUtil.log2ceil(tree.length / 2) - 1);
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return "tree " + modifiersToString() + "[" + MatcherBuilder.rangesToString(tree) + "]";
    }
}
