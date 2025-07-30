/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.analysis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.Random;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.regex.AbstractConstantKeysObject;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.runtime.nodes.ToLongNode;
import com.oracle.truffle.regex.runtime.nodes.ToLongNodeGen;
import com.oracle.truffle.regex.tregex.buffer.IntArrayBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntRangesBuffer;
import com.oracle.truffle.regex.tregex.parser.Token;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.QuantifiableTerm;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;
import com.oracle.truffle.regex.tregex.parser.ast.Term;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.util.TruffleNull;
import com.oracle.truffle.regex.util.TruffleReadOnlyKeysArray;

public final class InputStringGenerator {

    public static final class InputString extends AbstractConstantKeysObject {

        private static final String PROP_INPUT = "input";
        private static final String PROP_FROM_INDEX = "fromIndex";
        private static final String PROP_MATCH_START = "matchStart";
        private static final TruffleReadOnlyKeysArray KEYS = new TruffleReadOnlyKeysArray(PROP_INPUT, PROP_FROM_INDEX, PROP_MATCH_START);

        private final TruffleString input;
        private final int fromIndex;
        private final int matchStart;

        public InputString(TruffleString input, int fromIndex, int matchStart) {
            this.input = input;
            this.fromIndex = fromIndex;
            this.matchStart = matchStart;
        }

        public TruffleString input() {
            return input;
        }

        public int fromIndex() {
            return fromIndex;
        }

        public int matchStart() {
            return matchStart;
        }

        @Override
        public TruffleReadOnlyKeysArray getKeys() {
            return KEYS;
        }

        @Override
        public boolean isMemberReadableImpl(String symbol) {
            switch (symbol) {
                case PROP_INPUT:
                case PROP_FROM_INDEX:
                case PROP_MATCH_START:
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public Object readMemberImpl(String symbol) throws UnknownIdentifierException {
            switch (symbol) {
                case PROP_INPUT:
                    return input;
                case PROP_FROM_INDEX:
                    return fromIndex;
                case PROP_MATCH_START:
                    return matchStart;
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw UnknownIdentifierException.create(symbol);
            }
        }
    }

    private static final CodePointSet ALLOWED_CHARACTERS = CodePointSet.createNoDedup(0x1, 0x10ffff);

    private static final class LotteryBox implements Iterator<Integer> {
        private final Random rng;
        private final int[] choices;
        private int nChoices;

        private LotteryBox(Random rng, int nChoices) {
            this.rng = rng;
            this.choices = new int[nChoices];
            this.nChoices = nChoices;
            for (int i = 0; i < nChoices; i++) {
                this.choices[i] = i;
            }
        }

        @Override
        public boolean hasNext() {
            return nChoices > 0;
        }

        @Override
        public Integer next() {
            int i = rng.nextInt(nChoices);
            int ret = choices[i];
            nChoices--;
            choices[i] = choices[nChoices];
            return ret;
        }
    }

    private static final class InputStringBuilder {
        private final ArrayList<InputElement> elements = new ArrayList<>();
        private int nPrepended = 0;

        private void append(InputElement e, boolean forward) {
            if (forward) {
                elements.add(e);
            } else {
                elements.add(0, e);
                nPrepended++;
            }
        }

        private InputElement get(int index, boolean forward) {
            return elements.get(index + nPrepended - (forward ? 0 : 1));
        }

        private void removeLast(boolean forward) {
            if (forward) {
                elements.remove(elements.size() - 1);
            } else {
                nPrepended--;
                elements.remove(0);
            }
        }

        private void replace(int index, boolean forward, InputElement element) {
            elements.set(index + nPrepended - (forward ? 0 : 1), element);
        }

        private boolean hasNext(int index, boolean forward) {
            return forward ? index + nPrepended < elements.size() : index + nPrepended > 0;
        }

        private InputString toTString(Random rng, Encodings.Encoding encoding) {
            CodePointSet anyChar = ALLOWED_CHARACTERS.createIntersectionSingleRange(encoding.getFullSet());
            int prefixLength = (int) (clampedGauss(rng) * 20);
            int suffixLength = (int) (clampedGauss(rng) * 20);
            int[] codepoints = new int[elements.size() + prefixLength + suffixLength];
            for (int i = 0; i < elements.size(); i++) {
                InputElement e = elements.get(i);
                if (e instanceof CCElement) {
                    CodePointSet codePointSet = elements.get(i).getCodePointSet(this);
                    codepoints[prefixLength + i] = randChar(rng, codePointSet);
                } else if (e instanceof BackRefElement backRef) {
                    codepoints[prefixLength + i] = codepoints[prefixLength + backRef.ref + nPrepended];
                }
            }
            // random characters before and after match
            for (int i = 0; i < prefixLength; i++) {
                codepoints[i] = randPrefixSuffixChar(rng, codepoints, prefixLength, anyChar);
            }
            for (int i = prefixLength + elements.size(); i < codepoints.length; i++) {
                codepoints[i] = randPrefixSuffixChar(rng, codepoints, prefixLength, anyChar);
            }
            if (nPrepended < 0) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            int matchStart = prefixLength + nPrepended;
            // high chance to begin somewhere closely before the match start, and a small chance to
            // begin after the match start
            int iBeforeMatch = matchStart - (int) (clampedGauss(rng) * matchStart);
            int iAfterMatch = (int) (clampedGauss(rng, 0.05) * Math.min(10, Math.max(0, elements.size() - nPrepended)));
            int fromIndex = iBeforeMatch + iAfterMatch;
            if (encoding == Encodings.UTF_16_RAW) {
                char[] chars = new char[codepoints.length];
                for (int i = 0; i < codepoints.length; i++) {
                    chars[i] = (char) codepoints[i];
                }
                TruffleString string = TruffleString.fromCharArrayUTF16Uncached(chars);
                return new InputString(string, fromIndex, matchStart);
            }
            TruffleString string = TruffleString.fromIntArrayUTF32Uncached(codepoints).switchEncodingUncached(encoding.getTStringEncoding());
            return new InputString(string, translateIndex(string, encoding, codepoints, fromIndex), translateIndex(string, encoding, codepoints, matchStart));
        }

        private static int translateIndex(TruffleString string, Encodings.Encoding encoding, int[] codepoints, int index) {
            TruffleString.Encoding tsEncoding = encoding.getTStringEncoding();
            if (encoding == Encodings.UTF_32) {
                return index;
            } else if (encoding == Encodings.UTF_16) {
                int cpIndex = index;
                for (int i = 1; i < index; i++) {
                    int prevCp = codepoints[i - 1];
                    int cp = codepoints[i];
                    if (cp >= Character.MIN_LOW_SURROGATE && cp <= Character.MAX_LOW_SURROGATE && prevCp >= Character.MIN_HIGH_SURROGATE && prevCp <= Character.MAX_HIGH_SURROGATE) {
                        cpIndex--;
                    }
                }
                int indexEnc = cpIndex == string.codePointLengthUncached(tsEncoding) ? string.byteLength(tsEncoding) : string.codePointIndexToByteIndexUncached(0, cpIndex, tsEncoding);
                return indexEnc >> 1;
            } else {
                return index == string.codePointLengthUncached(tsEncoding) ? string.byteLength(tsEncoding) : string.codePointIndexToByteIndexUncached(0, index, tsEncoding);
            }
        }

        /**
         * Absolute value of gauss distribution of values between -1.0 and 1.0, so effectively
         * values from 0.0 to 1.0, with high bias towards 0.0.
         */
        private static double clampedGauss(Random rng) {
            return clampedGauss(rng, 0.33);
        }

        private static double clampedGauss(Random rng, double stddev) {
            return Math.min(1.0, Math.max(Math.abs(rng.nextGaussian(0, stddev)), 0.0));
        }

        private static int randChar(Random rng, CodePointSet codePointSet) {
            int iRange = rng.nextInt(codePointSet.size());
            return rng.nextInt(codePointSet.getLo(iRange), codePointSet.getHi(iRange) + 1);
        }

        private int randPrefixSuffixChar(Random rng, int[] codepoints, int prefixLength, CodePointSet anyChar) {
            if (!elements.isEmpty() && rng.nextInt(10) < 8) {
                // 80% chance to repeat a random character of the matching part
                return codepoints[prefixLength + rng.nextInt(elements.size())];
            } else {
                // 20% chance for a completely random character
                return randChar(rng, anyChar);
            }
        }
    }

    private abstract static class InputElement {

        abstract CodePointSet getCodePointSet(InputStringBuilder builder);

        abstract CodePointSet setCodePointSet(CodePointSet cps, InputStringBuilder builder);
    }

    private static final class CCElement extends InputElement {
        private CodePointSet cps;

        private CCElement(CodePointSet cps) {
            this.cps = cps;
        }

        @Override
        CodePointSet getCodePointSet(InputStringBuilder builder) {
            return cps;
        }

        @Override
        CodePointSet setCodePointSet(CodePointSet cps, InputStringBuilder builder) {
            return this.cps = cps;
        }
    }

    private static final class BackRefElement extends InputElement {
        private final int ref;

        private BackRefElement(int ref) {
            this.ref = ref;
        }

        @Override
        CodePointSet getCodePointSet(InputStringBuilder builder) {
            return builder.get(ref, true).getCodePointSet(builder);
        }

        @Override
        CodePointSet setCodePointSet(CodePointSet cps, InputStringBuilder builder) {
            return builder.get(ref, true).setCodePointSet(cps, builder);
        }
    }

    private abstract static class StackEntry {
        abstract void apply(InputStringGenerator gen);
    }

    private abstract static class BacktrackingEntry extends StackEntry {
        final LotteryBox choices;

        private BacktrackingEntry(LotteryBox choices) {
            this.choices = choices;
        }
    }

    private static final class BacktrackGroupEntry extends BacktrackingEntry {
        private final Group group;

        private BacktrackGroupEntry(LotteryBox choices, Group group) {
            super(choices);
            this.group = group;
        }

        @Override
        void apply(InputStringGenerator gen) {
            int i = choices.next();
            if (choices.hasNext()) {
                gen.backtrackStack.push(this);
            }
            gen.processSeq(group, group.getAlternatives().get(i));
        }
    }

    private abstract static class StateChange extends StackEntry {
    }

    private static final class SetGroupBoundaryAction extends StateChange {
        private final int boundaryIndex;
        private final int oldValue;

        private SetGroupBoundaryAction(int boundaryIndex, int oldValue) {
            this.boundaryIndex = boundaryIndex;
            this.oldValue = oldValue;
        }

        @Override
        void apply(InputStringGenerator gen) {
            gen.groupBoundaries[boundaryIndex] = oldValue;
        }
    }

    private static final class ChangeDirectionAction extends StateChange {

        @Override
        void apply(InputStringGenerator gen) {
            gen.forward = !gen.forward;
        }
    }

    private static final class PushLookAroundIndexReset extends StateChange {

        @Override
        void apply(InputStringGenerator gen) {
            gen.lookAroundIndexReset.removeLast();
        }
    }

    private static final class PopLookAroundIndexReset extends StateChange {

        private final int oldIndex;
        private final int lookAroundIndexReset;

        private PopLookAroundIndexReset(int oldIndex, int lookAroundIndexReset) {
            this.oldIndex = oldIndex;
            this.lookAroundIndexReset = lookAroundIndexReset;
        }

        @Override
        void apply(InputStringGenerator gen) {
            gen.index = oldIndex;
            gen.lookAroundIndexReset.add(lookAroundIndexReset);
        }
    }

    private static final class PushQuantifierStack extends StateChange {

        @Override
        void apply(InputStringGenerator gen) {
            assert !gen.quantifierStack.isEmpty();
            gen.quantifierStack.pop();
        }
    }

    private static final class PopQuantifierStack extends StateChange {

        private final QuantifiableTerm term;

        private PopQuantifierStack(QuantifiableTerm term) {
            this.term = term;
        }

        @Override
        void apply(InputStringGenerator gen) {
            gen.quantifierStack.push(new QuantifierStackEntry(term, 1));
        }
    }

    private static final class DecQuantifierStack extends StateChange {

        @Override
        void apply(InputStringGenerator gen) {
            assert !gen.quantifierStack.isEmpty();
            gen.quantifierStack.peek().count++;
        }
    }

    private static final class AppendElement extends StateChange {

        @Override
        void apply(InputStringGenerator gen) {
            gen.builder.removeLast(gen.forward);
            gen.decIndex();
        }
    }

    private static final class SetCCElement extends StateChange {

        private final int index;
        private final CodePointSet oldCPS;

        private SetCCElement(int index, CodePointSet oldCPS) {
            this.index = index;
            this.oldCPS = oldCPS;
        }

        @Override
        void apply(InputStringGenerator gen) {
            gen.builder.get(index, true).setCodePointSet(oldCPS, gen.builder);
            gen.decIndex();
        }
    }

    private static final class ReplaceElement extends StateChange {

        private final int index;
        private final InputElement oldElement;

        private ReplaceElement(int index, InputElement oldElement) {
            this.index = index;
            this.oldElement = oldElement;
        }

        @Override
        void apply(InputStringGenerator gen) {
            gen.builder.replace(index, true, oldElement);
            gen.decIndex();
        }
    }

    private static final class QuantifierStackEntry {
        private final QuantifiableTerm term;
        private int count;

        private QuantifierStackEntry(QuantifiableTerm term, int count) {
            this.term = term;
            this.count = count;
        }
    }

    enum State {
        advance,
        backtrack,
        done
    }

    private final RegexAST ast;
    private final Random rng;
    private final int[] groupBoundaries;
    private InputStringBuilder builder = new InputStringBuilder();
    private int index = 0;
    private boolean forward = true;
    private Term next;
    private State state = State.advance;
    private final IntArrayBuffer lookAroundIndexReset = new IntArrayBuffer();
    private final ArrayDeque<QuantifierStackEntry> quantifierStack = new ArrayDeque<>();
    private final ArrayDeque<StackEntry> backtrackStack = new ArrayDeque<>();
    private final IntRangesBuffer scratch = new IntRangesBuffer();

    private InputStringGenerator(RegexAST ast, long rngSeed) {
        this.ast = ast;
        rng = new Random(rngSeed);
        groupBoundaries = new int[ast.getNumberOfCaptureGroups() * 2];
        Arrays.fill(groupBoundaries, Integer.MIN_VALUE);
        next = ast.getRoot();
    }

    private void incIndex() {
        index += forward ? 1 : -1;
    }

    private void decIndex() {
        index += forward ? -1 : 1;
    }

    private void setGroupStart(Group group) {
        setGroupBoundary(forward ? groupBoundaryIndexStart(group) : groupBoundaryIndexEnd(group));
    }

    private void setGroupEnd(Group group) {
        setGroupBoundary(forward ? groupBoundaryIndexEnd(group) : groupBoundaryIndexStart(group));
    }

    private int getGroupStart(int groupNumber) {
        return groupBoundaries[groupNumber << 1];
    }

    private int getGroupEnd(int groupNumber) {
        return groupBoundaries[(groupNumber << 1) + 1];
    }

    private void setGroupBoundary(int boundaryIndex) {
        backtrackStack.push(new SetGroupBoundaryAction(boundaryIndex, groupBoundaries[boundaryIndex]));
        groupBoundaries[boundaryIndex] = index;
    }

    private static int groupBoundaryIndexStart(Group group) {
        return group.getGroupNumber() << 1;
    }

    private static int groupBoundaryIndexEnd(Group group) {
        return (group.getGroupNumber() << 1) + 1;
    }

    private void setDirection(RegexASTSubtreeRootNode rootNode) {
        if ((!rootNode.isLookBehindAssertion() && !forward) || (rootNode.isLookBehindAssertion() && forward)) {
            forward = !forward;
            backtrackStack.push(new ChangeDirectionAction());
        }
    }

    private void pushLookAroundIndexReset() {
        lookAroundIndexReset.add(index);
        backtrackStack.push(new PushLookAroundIndexReset());
    }

    private void popLookAroundIndexReset() {
        int oldIndex = index;
        index = lookAroundIndexReset.removeLast();
        backtrackStack.push(new PopLookAroundIndexReset(oldIndex, index));
    }

    private void pushQuantifierIterations(int repetitions) {
        assert next.isQuantifiableTerm();
        quantifierStack.push(new QuantifierStackEntry(next.asQuantifiableTerm(), repetitions));
        backtrackStack.push(new PushQuantifierStack());
    }

    private boolean popQuantifierIteration() {
        assert !quantifierStack.isEmpty();
        if (quantifierStack.peek().count == 1) {
            backtrackStack.push(new PopQuantifierStack(quantifierStack.pop().term));
            return false;
        } else {
            quantifierStack.peek().count--;
            backtrackStack.push(new DecQuantifierStack());
            return true;
        }
    }

    private static final class GeneratorRootNode extends RootNode {

        private final RegexAST ast;
        @Child private ToLongNode toLongNode = ToLongNodeGen.create();

        private GeneratorRootNode(RegexLanguage language, RegexAST ast) {
            super(language);
            this.ast = ast;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            long rngSeed = toLongNode.execute(frame.getArguments()[0]);
            return Objects.requireNonNullElse(InputStringGenerator.generate(ast, rngSeed), TruffleNull.INSTANCE);
        }
    }

    public static RootNode generateRootNode(RegexLanguage language, RegexAST ast) {
        return new GeneratorRootNode(language, ast);
    }

    @TruffleBoundary
    public static InputString generate(RegexAST ast, long rngSeed) {
        return new InputStringGenerator(ast, rngSeed).generate();
    }

    private InputString generate() {
        next = ast.getRoot();
        while (true) {
            switch (state) {
                case advance -> {
                    processTerm(next);
                }
                case backtrack -> {
                    if (backtrackStack.isEmpty()) {
                        return builder.toTString(rng, ast.getEncoding());
                    } else {
                        backtrackStack.pop().apply(this);
                    }
                }
                case done -> {
                    return builder.toTString(rng, ast.getEncoding());
                }
            }
        }
    }

    private void processGroup(Group group) {
        if (group.isCapturing()) {
            setGroupStart(group);
        }
        final Sequence seq;
        if (group.size() == 1) {
            seq = group.getFirstAlternative();
        } else {
            LotteryBox randSequenceNumber = new LotteryBox(rng, group.size());
            seq = group.getAlternatives().get(randSequenceNumber.next());
            assert randSequenceNumber.hasNext();
            backtrackStack.push(new BacktrackGroupEntry(randSequenceNumber, group));
        }
        processSeq(group, seq);
    }

    private void processSeq(Group group, Sequence seq) {
        if (seq.isEmpty()) {
            afterTerm(group);
        } else {
            next = forward ? seq.getFirstTerm() : seq.getLastTerm();
        }
    }

    private void afterTerm(Term t) {
        Term term = t;
        while (true) {
            if (term.isGroup() && term.asGroup().isCapturing()) {
                setGroupEnd(term.asGroup());
            }
            if (term.getParent().isSubtreeRoot()) {
                assert term.isGroup() && !term.asGroup().hasQuantifier();
                if (term.getParent().isRoot()) {
                    state = State.done;
                    return;
                }
                if (term.getParent().isLookAroundAssertion()) {
                    popLookAroundIndexReset();
                }
                setDirection(term.getParent().getSubTreeParent());
                term = term.getParent().asSubtreeRootNode();
            } else if (!quantifierStack.isEmpty() && term == quantifierStack.peek().term) {
                if (popQuantifierIteration()) {
                    next = term;
                    return;
                }
            } else {
                int seqIndex = term.getSeqIndex();
                Sequence parentSeq = term.getParent().asSequence();
                if (seqIndex == (forward ? parentSeq.size() - 1 : 0)) {
                    term = parentSeq.getParent();
                } else {
                    next = parentSeq.get(seqIndex + (forward ? 1 : -1));
                    return;
                }
            }
        }
    }

    private void processTerm(Term term) {
        if (term.isQuantifiableTerm() && term.asQuantifiableTerm().hasQuantifier() && (quantifierStack.isEmpty() || quantifierStack.peek().term != term)) {
            Token.Quantifier quantifier = term.asQuantifiableTerm().getQuantifier();
            if (quantifier.isDead() || quantifier.getMin() == Integer.MAX_VALUE) {
                state = State.backtrack;
                return;
            }
            int max = Integer.min(quantifier.getMin() + 10, quantifier.isInfiniteLoop() || quantifier.getMax() == Integer.MAX_VALUE ? Integer.MAX_VALUE : quantifier.getMax() + 1);
            int repetitions = rng.nextInt(quantifier.getMin(), max);
            if (repetitions == 0) {
                afterTerm(term);
                return;
            } else {
                pushQuantifierIterations(repetitions);
            }
        }
        if (term.isPositionAssertion()) {
            afterTerm(term);
        } else if (term.isGroup()) {
            processGroup(term.asGroup());
        } else if (term.isLookAroundAssertion()) {
            setDirection(term.asSubtreeRootNode());
            pushLookAroundIndexReset();
            processGroup(term.asLookAroundAssertion().getGroup());
        } else if (term.isAtomicGroup()) {
            processGroup(term.asAtomicGroup().getGroup());
        } else if (term.isCharacterClass()) {
            CodePointSet cps = term.asCharacterClass().getCharSet();

            if (!cps.isEmpty()) {
                cps = ALLOWED_CHARACTERS.createIntersectionSingleRange(cps);
            }

            if (cps.isEmpty()) {
                state = State.backtrack;
                return;
            }
            if (builder.hasNext(index, forward)) {
                CodePointSet old = builder.get(index, forward).getCodePointSet(builder);
                CodePointSet intersection = cps.createIntersection(builder.get(index, forward).getCodePointSet(builder), scratch);
                if (intersection.isEmpty()) {
                    state = State.backtrack;
                    return;
                }
                builder.get(index, forward).setCodePointSet(intersection, builder);
                backtrackStack.push(new SetCCElement(index - (forward ? 0 : 1), old));
            } else {
                builder.append(new CCElement(cps), forward);
                backtrackStack.push(new AppendElement());
            }
            incIndex();
            afterTerm(term);
        } else if (term.isBackReference()) {
            int[] groupNumbers = term.asBackReference().getGroupNumbers();
            int groupNumber = groupNumbers[rng.nextInt(groupNumbers.length)];
            int start = getGroupStart(groupNumber);
            int end = getGroupEnd(groupNumber);
            if (start == Integer.MIN_VALUE || end == Integer.MIN_VALUE) {
                state = State.backtrack;
                return;
            }
            for (int i = start; i < end; i++) {
                if (builder.hasNext(index, forward)) {
                    InputElement old = builder.get(index, forward);
                    int indexDirect = index - (forward ? 0 : 1);
                    if (i != indexDirect) {
                        builder.replace(index, forward, new BackRefElement(i));
                        backtrackStack.push(new ReplaceElement(indexDirect, old));
                    }
                } else {
                    builder.append(new BackRefElement(i), forward);
                    backtrackStack.push(new AppendElement());
                }
                incIndex();
            }
            afterTerm(term);
        } else if (term.isSubexpressionCall()) {
            processGroup(ast.getGroup(term.asSubexpressionCall().getGroupNr()).get(0));
        } else {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }
}
