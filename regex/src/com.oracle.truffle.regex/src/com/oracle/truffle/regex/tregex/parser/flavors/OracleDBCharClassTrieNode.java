/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.flavors;

import java.util.ArrayList;

import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.charset.ImmutableSortedListOfRanges;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.RegexASTBuilder;
import com.oracle.truffle.regex.tregex.string.Encodings;

/**
 * This class implements an intermediate Prefix-Tree used to simulate the behavior of OracleDB on
 * character classes in ignore-case mode. Every path from the tree's root to a node with
 * {@code isEndOfString} represents a string contained in the character class, i.e. the following
 * exemplary tree contains strings {@code ["a", "b", "cd", "ce", "af", "bf"]}:
 * 
 * <pre>
 * {_root node_}
 * |           \__________
 * |                      \ 
 * {codepoints: [a,b],     {codepoints: [c],
 *  endOfString: true}      endOfString: false}
 *  |                       |
 *  |                       |
 * {codepoints: [f],       {codepoints: [d, e],
 *  endOfString: true}      endOfString: true}
 * </pre>
 * 
 * New strings are added in the order they are encountered in the parser, which is important to
 * correctly mimic OracleDB's behavior.
 */
public final class OracleDBCharClassTrieNode {

    /**
     * Codepoint set represented by this node.
     */
    private CodePointSet codepoints;
    private final ArrayList<OracleDBCharClassTrieNode> children = new ArrayList<>();
    private boolean isEndOfString;

    private OracleDBCharClassTrieNode(CodePointSet codepoints, boolean isEndOfString) {
        this.codepoints = codepoints;
        this.isEndOfString = isEndOfString;
    }

    public static OracleDBCharClassTrieNode createTreeRoot() {
        return new OracleDBCharClassTrieNode(null, false);
    }

    private static OracleDBCharClassTrieNode createNode(CodePointSet codepoints, boolean isEndOfString) {
        return new OracleDBCharClassTrieNode(codepoints, isEndOfString);
    }

    /**
     * Add a new set of codepoints as children of this node. Since the set of codepoints may
     * intersect with existing child nodes, this method may return an arbitrary amount of new or
     * existing child nodes.
     */
    public ArrayList<OracleDBCharClassTrieNode> getOrAddChildren(CodePointSet cps, boolean endOfString, CompilationBuffer compilationBuffer) {
        if (isEndOfString) {
            /*
             * If this node is already a string-terminating node, we prune any subsequent child
             * nodes that would be added to it, since the shorter strings represented by this node
             * will dominate any longer strings added later.
             * 
             * For example, consider character class [s\u00df] (unicode character 00df is
             * "Latin Small Letter Sharp S"). The literal character "s" will introduce a node with
             * codepoint set {s}, where isEndOfString == true. Character "\u00df" will introduce the
             * multi-character expansion "ss", which will be dominated by "s", so this character
             * class will never match "ss", so we can safely prune the second "s" from the tree. The
             * reason for short prefixes dominating longer strings later in the character class is
             * that OracleDB's regex engine essentially generates an if-else cascade for every
             * atomic element in the character class, accepting the first match, so [s\u00df] gets
             * compiled to:
             * 
             * if (input.matches("s")) { return true } else if (input.matches("ss")) { return true }
             */
            return null;
        }
        ArrayList<OracleDBCharClassTrieNode> ret = new ArrayList<>();
        CodePointSet remaining = cps;
        int length = children.size();
        OracleDBCharClassTrieNode leaf = null;
        for (int i = 0; i < length; i++) {
            OracleDBCharClassTrieNode child = children.get(i);
            if (child.children.isEmpty() && endOfString && child.isEndOfString) {
                // we merge all leaf nodes where isEndOfString == true
                leaf = child;
            }
            ImmutableSortedListOfRanges.IntersectAndSubtractResult<CodePointSet> result = remaining.intersectAndSubtract(child.codepoints, compilationBuffer);
            if (!result.intersection.isEmpty()) {
                remaining = result.subtractedA;
                if (result.subtractedB.isEmpty()) {
                    // current child is fully contained in the given codepoint set, add it to result
                    ret.add(child);
                } else {
                    // current child is partially contained in the given codepoint set, split it
                    // into two separate subtrees
                    OracleDBCharClassTrieNode copy = child.copySubtree();
                    child.codepoints = result.subtractedB;
                    copy.codepoints = result.intersection;
                    children.add(copy);
                    ret.add(copy);
                }
                if (remaining.isEmpty()) {
                    return ret;
                }
            }
        }
        assert !remaining.isEmpty();
        if (leaf != null) {
            leaf.codepoints = leaf.codepoints.union(remaining, compilationBuffer);
            ret.add(leaf);
        } else {
            // add the remaining non-intersecting characters as a new child node
            OracleDBCharClassTrieNode child = createNode(remaining, endOfString);
            children.add(child);
            ret.add(child);
        }
        return ret;
    }

    private OracleDBCharClassTrieNode copySubtree() {
        OracleDBCharClassTrieNode copy = createNode(codepoints, isEndOfString);
        for (OracleDBCharClassTrieNode child : children) {
            copy.children.add(child.copySubtree());
        }
        return copy;
    }

    public boolean isEndOfString() {
        return isEndOfString;
    }

    public void setEndOfString() {
        isEndOfString = true;
    }

    public void clear() {
        children.clear();
    }

    public void generateAST(RegexASTBuilder astBuilder, boolean negate) {
        if (needsGroupWrapper(negate)) {
            astBuilder.pushGroup();
        }
        if (negate) {
            CodePointSetAccumulator acc = new CodePointSetAccumulator();
            for (OracleDBCharClassTrieNode child : children) {
                acc.addSet(child.codepoints);
                if (!child.isEndOfString) {
                    // negated character classes match the beginning of multi-character expansions
                    // iff the multi-character expansion doesn't fully match
                    astBuilder.addCharClass(child.codepoints);
                    astBuilder.pushLookAheadAssertion(true);
                    child.generateASTInner(astBuilder, true);
                    astBuilder.popGroup();
                    astBuilder.nextSequence();
                }
            }
            acc.invert(Encodings.UTF_8);
            astBuilder.addCharClass(acc.toCodePointSet());
        } else {
            for (int i = 0; i < children.size(); i++) {
                OracleDBCharClassTrieNode child = children.get(i);
                if (i > 0) {
                    astBuilder.nextSequence();
                }
                astBuilder.addCharClass(child.codepoints);
                if (child.needsGroupWrapper(false)) {
                    astBuilder.pushGroup();
                }
                child.generateASTInner(astBuilder, false);
                if (child.needsGroupWrapper(false)) {
                    astBuilder.popGroup();
                }
            }
        }
        if (needsGroupWrapper(negate)) {
            astBuilder.popGroup();
        }
    }

    private void generateASTInner(RegexASTBuilder astBuilder, boolean negate) {
        for (int i = 0; i < children.size(); i++) {
            OracleDBCharClassTrieNode child = children.get(i);
            if (i > 0) {
                astBuilder.nextSequence();
            }
            astBuilder.addCharClass(child.codepoints);
            if (!child.children.isEmpty() && !(negate && child.isEndOfString())) {
                if (child.needsGroupWrapper(negate)) {
                    astBuilder.pushGroup();
                    child.generateASTInner(astBuilder, negate);
                    astBuilder.popGroup();
                } else {
                    child.generateASTInner(astBuilder, negate);
                }
            }
        }
        if (!negate && isEndOfString() && !children.isEmpty()) {
            /*
             * If the tree contains a multi-character expansion as well as a prefix thereof, the
             * prefix must have occurred later in the character class than the expanded string, e.g.
             * [\u00dfs]. In this case, the prefix may match iff the multi-character expansion
             * doesn't.
             * 
             * The code generated by OracleDB for this example is:
             * 
             * if (input.matches("ss")) { return true } else if (input.matches("s")) { return true }
             * 
             * We could mimic this behavior by wrapping the generated subexpression in an atomic
             * group, but this isn't supported in the DFA yet; Therefore we generate a negative
             * lookahead that prevents backtracking into the single character match: [\u00dfs] gets
             * translated to (simplified): (s(s|(?!s)))
             */
            astBuilder.nextSequence();
            astBuilder.pushLookAheadAssertion(true);
            generateASTInner(astBuilder, true);
            astBuilder.popGroup();
        }
    }

    private boolean needsGroupWrapper(boolean negate) {
        return children.size() > 1 || !negate && isEndOfString() && !children.isEmpty();
    }
}
