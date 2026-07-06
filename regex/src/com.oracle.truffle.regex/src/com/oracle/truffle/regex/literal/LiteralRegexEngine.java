/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.literal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.StringSet;
import com.oracle.truffle.api.strings.TruffleString.WithMask;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.literal.LiteralRegexExecNode.EmptyEndsWith;
import com.oracle.truffle.regex.literal.LiteralRegexExecNode.EmptyEquals;
import com.oracle.truffle.regex.literal.LiteralRegexExecNode.EmptyIndexOf;
import com.oracle.truffle.regex.literal.LiteralRegexExecNode.EmptyStartsWith;
import com.oracle.truffle.regex.literal.LiteralRegexExecNode.EndsWith;
import com.oracle.truffle.regex.literal.LiteralRegexExecNode.Equals;
import com.oracle.truffle.regex.literal.LiteralRegexExecNode.IndexOfString;
import com.oracle.truffle.regex.literal.LiteralRegexExecNode.RegionMatches;
import com.oracle.truffle.regex.literal.LiteralRegexExecNode.StartsWith;
import com.oracle.truffle.regex.result.PreCalculatedResultFactory;
import com.oracle.truffle.regex.tregex.parser.RegexProperties;
import com.oracle.truffle.regex.tregex.parser.ast.AtomicGroup;
import com.oracle.truffle.regex.tregex.parser.ast.BackReference;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;
import com.oracle.truffle.regex.tregex.parser.ast.SubexpressionCall;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.DepthFirstTraversalRegexASTVisitor;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.PreCalcResultVisitor;
import com.oracle.truffle.regex.tregex.string.AbstractStringBuffer;

/**
 * This regex engine is designed for very simple cases, where the regular expression can be directly
 * translated to common string operations. It will map expressions to simple index checks (
 * {@link EmptyStartsWith}, {@link EmptyEndsWith}, {@link EmptyIndexOf}) or to the following methods
 * of {@link String} (or equivalent nodes in {@link com.oracle.truffle.regex.tregex.nodes.input})
 * whenever possible:
 * <ul>
 * <li>{@link String#isEmpty()}: {@link EmptyEquals}</li>
 * <li>{@link String#indexOf(String)}: {@link IndexOfString}</li>
 * <li>{@link String#startsWith(String)}: {@link StartsWith}</li>
 * <li>{@link String#endsWith(String)}: {@link EndsWith}</li>
 * <li>{@link String#equals(Object)}: {@link Equals}</li>
 * <li>{@link String#regionMatches(int, String, int, int)}: {@link RegionMatches}</li>
 * </ul>
 */
public final class LiteralRegexEngine {

    public static LiteralRegexExecNode createNode(RegexLanguage language, RegexAST ast) {
        /*
         * Bail out if the search string would be huge. This can occur with expressions like
         * /a{1000000}/.
         */
        RegexProperties props = ast.getProperties();
        if (!props.isFixedCodePointWidth() || props.hasLoneSurrogates() || (ast.getRoot().hasQuantifiers() && ast.getRoot().getMinPath() > Short.MAX_VALUE)) {
            return null;
        }
        if (ast.isLiteralString()) {
            return createLiteralNode(language, ast);
        }
        return ExtractMultiLiteralVisitor.run(language, ast);
    }

    private static LiteralRegexExecNode createLiteralNode(RegexLanguage language, RegexAST ast) {
        PreCalcResultVisitor preCalcResultVisitor = PreCalcResultVisitor.run(ast, true);
        boolean caret = ast.getRoot().startsWithCaret();
        boolean dollar = ast.getRoot().endsWithDollar();
        if (ast.getRoot().getMinPath() == 0) {
            if (caret) {
                if (dollar) {
                    return LiteralRegexExecNode.create(language, ast, new EmptyEquals(preCalcResultVisitor, ast.getOptions().isMustAdvance()));
                }
                return LiteralRegexExecNode.create(language, ast, new EmptyStartsWith(preCalcResultVisitor, ast.getOptions().isMustAdvance()));
            }
            if (dollar) {
                return LiteralRegexExecNode.create(language, ast, new EmptyEndsWith(preCalcResultVisitor, ast.getFlags().isSticky(), ast.getOptions().isMustAdvance()));
            }
            return LiteralRegexExecNode.create(language, ast, new EmptyIndexOf(preCalcResultVisitor, ast.getOptions().isMustAdvance()));
        }
        if (caret) {
            if (dollar) {
                return LiteralRegexExecNode.create(language, ast, LiteralRegexExecNodeGen.EqualsNodeGen.create(preCalcResultVisitor));
            }
            return LiteralRegexExecNode.create(language, ast, LiteralRegexExecNodeGen.StartsWithNodeGen.create(preCalcResultVisitor));
        }
        if (dollar) {
            return LiteralRegexExecNode.create(language, ast, LiteralRegexExecNodeGen.EndsWithNodeGen.create(preCalcResultVisitor, ast.getFlags().isSticky()));
        }
        if (ast.getFlags().isSticky()) {
            return LiteralRegexExecNode.create(language, ast, LiteralRegexExecNodeGen.RegionMatchesNodeGen.create(preCalcResultVisitor));
        }
        if (preCalcResultVisitor.getLiteral().length() <= 64) {
            return LiteralRegexExecNode.create(language, ast, LiteralRegexExecNodeGen.IndexOfStringNodeGen.create(preCalcResultVisitor));
        }
        return null;
    }

    /**
     * Extracts a bounded ordered word list from regexps that consist only of concatenation,
     * alternation, captures, and fixed-width single-character classes. Each extracted word is
     * paired with the capture-group result that would be produced when that word is matched, so the
     * resulting {@link StringSet} can be used as a literal fast path without changing
     * observable capture semantics.
     * <p>
     * The traversal keeps a stack of {@link GroupFrame}s. A frame contains the entries that existed
     * on group entry ({@code prefix}), the entries for the sequence currently being visited
     * ({@code current}), and the fully expanded alternatives seen so far ({@code alternatives}).
     * Conceptually, for a group this is equivalent to:
     *
     * <pre>
     * prefix = entries at group entry
     * alternatives = []
     * for sequence in group.alternatives:
     *     current = copy(prefix)
     *     visit sequence terms, appending characters and updating capture results in current
     *     alternatives.addAll(current)
     * return alternatives
     * </pre>
     *
     * Nested groups compose by replacing the parent's {@code current} with the nested group's
     * expansion. For example, {@code /foo|bar|baz(foo|bar)/} expands to:
     *
     * <pre>
     * foo
     * bar
     * bazfoo
     * bazbar
     * </pre>
     *
     * Character classes that were pre-checked to be maskable are represented as
     * {@link WithMask}: the literal stores one representative code unit and the mask
     * stores the one-bit difference that admits the other class member. For example, an ASCII
     * case-foldable class can be encoded like:
     *
     * <pre>
     * literal: 'a'
     * mask:    0x20     // matches 'a' and 'A'
     * </pre>
     *
     * Expansion is capped at {@link #MAX_WORD_LIST_EXPANSIONS}. After expansion, candidates are also
     * rejected if too many words share the same short prefix, avoiding cases where the generated
     * string set is unlikely to be profitable.
     */
    private static final class ExtractMultiLiteralVisitor extends DepthFirstTraversalRegexASTVisitor {

        private static final int MAX_WORD_LIST_EXPANSIONS = 64;
        private static final int MAX_WORD_LIST_PREFIX_LENGTH = 4;
        private static final int MAX_WORD_LIST_SAME_PREFIX_COUNT = 8;

        private final RegexAST ast;
        private final ArrayDeque<GroupFrame> groupStack = new ArrayDeque<>();
        private ArrayList<Entry> results;
        private boolean failed;

        private ExtractMultiLiteralVisitor(RegexAST ast) {
            this.ast = ast;
        }

        static LiteralRegexExecNode run(RegexLanguage language, RegexAST ast) {
            RegexProperties props = ast.getProperties();
            Group root = ast.getRoot();
            if (ast.getFlags().isSticky() ||
                            !props.charClassesCanBeMatchedWithMask() ||
                            root.hasCaret() ||
                            root.hasDollar() ||
                            root.hasBackReferences() ||
                            props.hasConditionalBackReferences() ||
                            root.hasAtomicGroups() ||
                            root.hasLookArounds() ||
                            root.hasLoops() ||
                            root.getMinPath() == 0 ||
                            root.getMaxPath() == 1) {
                return null;
            }
            ExtractMultiLiteralVisitor visitor = new ExtractMultiLiteralVisitor(ast);
            visitor.run(root);
            if (visitor.failed || visitor.results == null || visitor.results.size() <= 1 || visitor.results.size() > MAX_WORD_LIST_EXPANSIONS) {
                return null;
            }
            for (Entry entry : visitor.results) {
                entry.result.setLength(entry.index);
                if (ast.getOptions().getFlavor().usesLastGroupResultField()) {
                    entry.result.setLastGroup(entry.lastGroup);
                }
            }
            if (hasTooManyEqualPrefixes(visitor.results)) {
                return null;
            }
            TruffleString.Encoding encoding = ast.getEncoding().getTStringEncoding();
            StringSet stringSet;
            if (visitor.hasMasks()) {
                WithMask[] literalsWithMask = visitor.getLiteralsWithMask(encoding);
                if (literalsWithMask == null) {
                    return null;
                }
                stringSet = StringSet.fromArray(literalsWithMask, encoding);
            } else {
                TruffleString[] literals = visitor.getLiterals(encoding);
                if (literals == null) {
                    return null;
                }
                stringSet = StringSet.fromArray(literals, encoding);
            }
            if (!stringSet.isIntrinsicCandidate(TruffleString.CodeRange.BROKEN)) {
                return null;
            }
            PreCalculatedResultFactory[] preCalculatedResults = ast.getOptions().isBooleanMatch() ? null : visitor.getPreCalculatedResults();
            return LiteralRegexExecNode.create(language, ast, LiteralRegexExecNodeGen.IndexOfStringSetNodeGen.create(stringSet, preCalculatedResults));
        }

        boolean hasMasks() {
            return ast.getProperties().hasCharClasses();
        }

        TruffleString[] getLiterals(TruffleString.Encoding encoding) {
            TruffleString[] ret = new TruffleString[results.size()];
            for (int i = 0; i < ret.length; i++) {
                ret[i] = results.get(i).literal.asTString();
                if (!ret[i].isValidUncached(encoding)) {
                    return null;
                }
            }
            return ret;
        }

        WithMask[] getLiteralsWithMask(TruffleString.Encoding encoding) {
            WithMask[] ret = new WithMask[results.size()];
            for (int i = 0; i < ret.length; i++) {
                Entry entry = results.get(i);
                TruffleString tString = entry.literal.asTString();
                if (!tString.isValidUncached(encoding)) {
                    return null;
                }
                ret[i] = entry.mask.asTStringMask(tString);
            }
            return ret;
        }

        PreCalculatedResultFactory[] getPreCalculatedResults() {
            PreCalculatedResultFactory[] ret = new PreCalculatedResultFactory[results.size()];
            for (int i = 0; i < ret.length; i++) {
                ret[i] = results.get(i).result;
            }
            return ret;
        }

        private static boolean hasTooManyEqualPrefixes(ArrayList<Entry> entries) {
            long[] prefixHashes = new long[entries.size()];
            int[] prefixCounts = new int[entries.size()];
            int nPrefixes = 0;
            for (Entry entry : entries) {
                long prefixHash = entry.literal.prefixHash(MAX_WORD_LIST_PREFIX_LENGTH);
                int i = 0;
                while (i < nPrefixes && prefixHashes[i] != prefixHash) {
                    i++;
                }
                if (i == nPrefixes) {
                    prefixHashes[nPrefixes] = prefixHash;
                    prefixCounts[nPrefixes] = 1;
                    nPrefixes++;
                } else if (++prefixCounts[i] > MAX_WORD_LIST_SAME_PREFIX_COUNT) {
                    return true;
                }
            }
            return false;
        }

        @Override
        protected void visit(Group group) {
            if (group.isDead()) {
                return;
            }
            ArrayList<Entry> prefix = groupStack.isEmpty() ? initialEntry() : copyAllPreservePrefixIndex(groupStack.peek().current);
            if (group.isCapturing()) {
                for (Entry entry : prefix) {
                    entry.result.setStart(group.getGroupNumber(), entry.index);
                }
            }
            groupStack.push(new GroupFrame(prefix));
        }

        @Override
        protected void visit(Sequence sequence) {
            if (!ast.getOptions().isBooleanMatch() && sequence.isEmpty()) {
                fail();
                return;
            }
            if (sequence.isDead()) {
                return;
            }
            GroupFrame frame = groupStack.peek();
            assert frame.current == null;
            frame.current = copyAll(frame.prefix);
        }

        @Override
        protected void visit(CharacterClass characterClass) {
            if (characterClass.isDead()) {
                return;
            }
            assert !characterClass.hasQuantifier() || characterClass.getQuantifier().getMin() == characterClass.getQuantifier().getMax();
            int repetitions = characterClass.hasNotUnrolledQuantifier() ? characterClass.getQuantifier().getMin() : 1;
            ArrayList<Entry> current = groupStack.peek().current;
            for (Entry entry : current) {
                for (int i = 0; i < repetitions; i++) {
                    int cp = characterClass.getCharSet().getMin();
                    if (entry.mask == null) {
                        entry.literal.append(cp);
                    } else {
                        characterClass.extractSingleChar(entry.literal, entry.mask);
                    }
                    entry.index += ast.getEncoding().getEncodedSize(cp);
                }
            }
        }

        @Override
        protected void leave(Sequence sequence) {
            if (sequence.isDead()) {
                return;
            }
            GroupFrame frame = groupStack.peek();
            frame.alternatives.addAll(frame.current);
            frame.current = null;
            if (frame.alternatives.size() > MAX_WORD_LIST_EXPANSIONS) {
                fail();
            }
        }

        @Override
        protected void leave(Group group) {
            if (group.isDead()) {
                return;
            }
            GroupFrame frame = groupStack.pop();
            ArrayList<Entry> expanded = frame.alternatives;
            if (expanded.size() > MAX_WORD_LIST_EXPANSIONS) {
                fail();
                return;
            }
            expanded.sort(Comparator.comparingInt(a -> a.prefixIndex));
            if (group.isCapturing()) {
                for (Entry entry : expanded) {
                    entry.result.setEnd(group.getGroupNumber(), entry.index);
                    if (group.getGroupNumber() != 0) {
                        entry.lastGroup = group.getGroupNumber();
                    }
                }
            }
            if (groupStack.isEmpty()) {
                results = expanded;
            } else {
                for (Entry entry : expanded) {
                    entry.prefixIndex = frame.prefix.get(entry.prefixIndex).prefixIndex;
                }
                groupStack.peek().current = expanded;
            }
        }

        @Override
        protected void visit(BackReference backReference) {
            shouldNotReachHere(backReference);
        }

        @Override
        protected void visit(PositionAssertion assertion) {
            shouldNotReachHere(assertion);
        }

        @Override
        protected void visit(LookBehindAssertion assertion) {
            shouldNotReachHere(assertion);
        }

        @Override
        protected void visit(LookAheadAssertion assertion) {
            shouldNotReachHere(assertion);
        }

        @Override
        protected void visit(AtomicGroup atomicGroup) {
            shouldNotReachHere(atomicGroup);
        }

        @Override
        protected void visit(SubexpressionCall subexpressionCall) {
            shouldNotReachHere(subexpressionCall);
        }

        private static void shouldNotReachHere(RegexASTNode node) {
            if (!node.getParent().isDead()) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        private void fail() {
            failed = true;
            done();
        }

        private ArrayList<Entry> initialEntry() {
            ArrayList<Entry> ret = new ArrayList<>();
            Entry entry = new Entry();
            entry.literal = ast.getEncoding().createStringBuffer(ast.getRoot().getMinPath());
            entry.mask = hasMasks() ? ast.getEncoding().createStringBuffer(ast.getRoot().getMinPath()) : null;
            entry.result = new PreCalculatedResultFactory(ast.getNumberOfCaptureGroups(), ast.getOptions().getFlavor().usesLastGroupResultField());
            ret.add(entry);
            return ret;
        }

        private static ArrayList<Entry> copyAll(ArrayList<Entry> entries) {
            ArrayList<Entry> ret = new ArrayList<>(entries.size());
            for (int i = 0; i < entries.size(); i++) {
                Entry copy = entries.get(i).copy();
                copy.prefixIndex = i;
                ret.add(copy);
            }
            return ret;
        }

        private static ArrayList<Entry> copyAllPreservePrefixIndex(ArrayList<Entry> entries) {
            ArrayList<Entry> ret = new ArrayList<>(entries.size());
            for (Entry entry : entries) {
                ret.add(entry.copy());
            }
            return ret;
        }

        private static final class GroupFrame {
            final ArrayList<Entry> prefix;
            final ArrayList<Entry> alternatives = new ArrayList<>();
            ArrayList<Entry> current;

            GroupFrame(ArrayList<Entry> prefix) {
                this.prefix = prefix;
            }
        }

        private static final class Entry {
            AbstractStringBuffer literal;
            AbstractStringBuffer mask;
            PreCalculatedResultFactory result;
            int index;
            int lastGroup = -1;
            int prefixIndex;

            Entry copy() {
                Entry ret = new Entry();
                ret.literal = literal.copy();
                ret.mask = mask == null ? null : mask.copy();
                ret.result = result.copy();
                ret.index = index;
                ret.lastGroup = lastGroup;
                ret.prefixIndex = prefixIndex;
                return ret;
            }
        }
    }
}
