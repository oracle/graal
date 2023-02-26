/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodes;

import static com.oracle.truffle.api.CompilerDirectives.LIKELY_PROBABILITY;
import static com.oracle.truffle.api.CompilerDirectives.injectBranchProbability;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.regex.RegexExecNode;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.tregex.nodes.input.InputLengthNode;
import com.oracle.truffle.regex.tregex.nodes.input.InputReadNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.tregex.string.Encodings.Encoding.UTF16;

public abstract class TRegexExecutorNode extends TRegexExecutorBaseNode {

    public static final double CONTINUE_PROBABILITY = 0.99;
    public static final double EXIT_PROBABILITY = 1.0 - CONTINUE_PROBABILITY;
    public static final double LATIN1_PROBABILITY = 0.7;
    public static final double BMP_PROBABILITY = 0.2;
    public static final double ASTRAL_PROBABILITY = 0.1;
    private final RegexSource source;
    private final int numberOfCaptureGroups;
    private final int numberOfTransitions;
    private @Child InputLengthNode lengthNode;
    private @Child InputReadNode charAtNode;
    private final BranchProfile bmpProfile = BranchProfile.create();
    private final BranchProfile astralProfile = BranchProfile.create();

    protected TRegexExecutorNode(RegexAST ast, int numberOfTransitions) {
        this(ast.getSource(), ast.getNumberOfCaptureGroups(), numberOfTransitions);
    }

    protected TRegexExecutorNode(TRegexExecutorNode copy) {
        this(copy.source, copy.numberOfCaptureGroups, copy.numberOfTransitions);
    }

    protected TRegexExecutorNode(RegexSource source, int numberOfCaptureGroups, int numberOfTransitions) {
        this.source = source;
        this.numberOfCaptureGroups = numberOfCaptureGroups;
        this.numberOfTransitions = numberOfTransitions;
    }

    @Override
    public RegexSource getSource() {
        return source;
    }

    public final int getNumberOfCaptureGroups() {
        return numberOfCaptureGroups;
    }

    @Override
    public final int getNumberOfTransitions() {
        return numberOfTransitions;
    }

    public BranchProfile getBMPProfile() {
        return bmpProfile;
    }

    public BranchProfile getAstralProfile() {
        return astralProfile;
    }

    /**
     * The length of the {@code input} argument given to
     * {@link RegexExecNode#execute(VirtualFrame)}.
     *
     * @return the length of the {@code input} argument given to
     *         {@link RegexExecNode#execute(VirtualFrame)}.
     */
    public int getInputLength(TRegexExecutorLocals locals) {
        if (lengthNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            lengthNode = insert(InputLengthNode.create());
        }
        return lengthNode.execute(locals.getInput(), getEncoding());
    }

    /**
     * Returns {@code true} iff the index is at the beginning of the input string in respect to
     * {@link #isForward()}.
     */
    public boolean inputAtBegin(TRegexExecutorLocals locals) {
        return locals.getIndex() == (isForward() ? 0 : getInputLength(locals));
    }

    /**
     * Returns {@code true} iff the index is at the end of the input string in respect to
     * {@link #isForward()}.
     */
    public boolean inputAtEnd(TRegexExecutorLocals locals) {
        return locals.getIndex() == (isForward() ? getInputLength(locals) : 0);
    }

    public int getMinIndex(@SuppressWarnings("unused") TRegexExecutorLocals locals) {
        return 0;
    }

    public int getMaxIndex(TRegexExecutorLocals locals) {
        return locals.getMaxIndex();
    }

    public boolean inputHasNext(TRegexExecutorLocals locals) {
        return inputHasNext(locals, locals.getIndex());
    }

    public boolean inputHasNext(TRegexExecutorLocals locals, int index) {
        return inputHasNext(locals, index, isForward());
    }

    public boolean inputHasNext(TRegexExecutorLocals locals, boolean forward) {
        return inputHasNext(locals, locals.getIndex(), forward);
    }

    public boolean inputHasNext(TRegexExecutorLocals locals, int index, boolean forward) {
        return forward ? index < getMaxIndex(locals) : index > getMinIndex(locals);
    }

    public int inputReadAndDecode(TRegexExecutorLocals locals) {
        return inputReadAndDecode(locals, locals.getIndex());
    }

    @ExplodeLoop
    public int inputReadAndDecode(TRegexExecutorLocals locals, int index) {
        if (getEncoding() == Encodings.UTF_16) {
            locals.setNextIndex(inputIncRaw(index));
            int c = inputReadRaw(locals);
            if (injectBranchProbability(ASTRAL_PROBABILITY, inputUTF16IsHighSurrogate(c) && inputHasNext(locals, locals.getNextIndex()))) {
                int c2 = inputReadRaw(locals, locals.getNextIndex());
                if (inputUTF16IsLowSurrogate(c2)) {
                    locals.setNextIndex(inputIncRaw(locals.getNextIndex()));
                    return inputUTF16ToCodePoint(c, c2);
                }
            }
            return c;
        } else if (getEncoding() == Encodings.UTF_8) {
            int c = inputReadRaw(locals);
            if (injectBranchProbability(LATIN1_PROBABILITY, c < 0x80)) {
                locals.setNextIndex(inputIncRaw(index));
                return c;
            }
            int codepoint = c & 0x3f;
            if (!isForward()) {
                assert c >> 6 == 2;
                for (int i = 1; i < 4; i++) {
                    c = inputReadRaw(locals, locals.getIndex() - i);
                    if (injectBranchProbability(LIKELY_PROBABILITY, i < 3 && c >> 6 == 2)) {
                        codepoint |= (c & 0x3f) << (6 * i);
                    } else {
                        break;
                    }
                }
            }
            int nBytes = inputUTF8NumberOfLeadingOnes(c);
            assert 1 < nBytes && nBytes < 5 : nBytes;
            if (isForward()) {
                locals.setNextIndex(inputIncRaw(index));
                codepoint = c & (0xff >>> nBytes);
                // Checkstyle: stop
                switch (nBytes) {
                    case 4:
                        codepoint = codepoint << 6 | (inputReadRaw(locals, locals.getNextIndex()) & 0x3f);
                        locals.setNextIndex(inputIncRaw(locals.getNextIndex()));
                    case 3:
                        codepoint = codepoint << 6 | (inputReadRaw(locals, locals.getNextIndex()) & 0x3f);
                        locals.setNextIndex(inputIncRaw(locals.getNextIndex()));
                    default:
                        codepoint = codepoint << 6 | (inputReadRaw(locals, locals.getNextIndex()) & 0x3f);
                        locals.setNextIndex(inputIncRaw(locals.getNextIndex()));
                }
                // Checkstyle: resume
                return codepoint;
            } else {
                locals.setNextIndex(inputIncRaw(index, nBytes));
                return codepoint | (c & (0xff >>> nBytes)) << (6 * (nBytes - 1));
            }
        } else {
            assert getEncoding() == Encodings.UTF_16_RAW || getEncoding() == Encodings.UTF_32 || getEncoding() == Encodings.LATIN_1 || getEncoding() == Encodings.BYTES ||
                            getEncoding() == Encodings.ASCII;
            locals.setNextIndex(inputIncRaw(index));
            return inputReadRaw(locals);
        }
    }

    public boolean inputUTF16IsHighSurrogate(int c) {
        return UTF16.isHighSurrogate(c, isForward());
    }

    public boolean inputUTF16IsLowSurrogate(int c) {
        return UTF16.isLowSurrogate(c, isForward());
    }

    public int inputUTF16ToCodePoint(int highSurrogate, int lowSurrogate) {
        return isForward() ? Character.toCodePoint((char) highSurrogate, (char) lowSurrogate) : Character.toCodePoint((char) lowSurrogate, (char) highSurrogate);
    }

    private static boolean inputUTF8IsTrailingByte(int c) {
        return (c >> 6) == 2;
    }

    private static int inputUTF8NumberOfLeadingOnes(int c) {
        return Integer.numberOfLeadingZeros(~(c << 24));
    }

    public int inputReadRaw(TRegexExecutorLocals locals) {
        return inputReadRaw(locals, locals.getIndex());
    }

    public int inputReadRaw(TRegexExecutorLocals locals, int index) {
        return inputReadRaw(locals, index, isForward());
    }

    public int inputReadRaw(TRegexExecutorLocals locals, boolean forward) {
        return inputReadRaw(locals, locals.getIndex(), forward);
    }

    public int inputReadRaw(TRegexExecutorLocals locals, int index, boolean forward) {
        if (charAtNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            charAtNode = insert(InputReadNode.create());
        }
        return charAtNode.execute(locals.getInput(), forward ? index : index - 1, getEncoding());
    }

    public void inputAdvance(TRegexExecutorLocals locals) {
        locals.setIndex(locals.getNextIndex());
    }

    public void inputSkip(TRegexExecutorLocals locals) {
        inputSkipIntl(locals, isForward());
    }

    public void inputSkipReverse(TRegexExecutorLocals locals) {
        inputSkipIntl(locals, !isForward());
    }

    protected void inputSkipIntl(TRegexExecutorLocals locals, boolean forward) {
        if (isUTF16()) {
            int c = inputReadRaw(locals, forward);
            inputIncRaw(locals, forward);
            if (UTF16.isHighSurrogate(c, forward) && inputHasNext(locals, forward) && UTF16.isLowSurrogate(inputReadRaw(locals, forward), forward)) {
                inputIncRaw(locals, forward);
            }
        } else if (isUTF8()) {
            if (forward) {
                int c = inputReadRaw(locals, true);
                if (c < 128) {
                    inputIncRaw(locals, true);
                } else {
                    getBMPProfile().enter();
                    inputIncRaw(locals, inputUTF8NumberOfLeadingOnes(c), true);
                }
            } else {
                int c;
                do {
                    c = inputReadRaw(locals, false);
                    inputIncRaw(locals, false);
                } while (inputHasNext(locals, false) && inputUTF8IsTrailingByte(c));
            }
        } else {
            assert getEncoding() == Encodings.UTF_16_RAW || getEncoding() == Encodings.UTF_32 || getEncoding() == Encodings.LATIN_1 || getEncoding() == Encodings.BYTES ||
                            getEncoding() == Encodings.ASCII;
            inputIncRaw(locals, forward);
        }
    }

    public void inputIncRaw(TRegexExecutorLocals locals) {
        inputIncRaw(locals, 1);
    }

    public void inputIncRaw(TRegexExecutorLocals locals, int offset) {
        inputIncRaw(locals, offset, isForward());
    }

    public void inputIncRaw(TRegexExecutorLocals locals, boolean forward) {
        inputIncRaw(locals, 1, forward);
    }

    public void inputIncRaw(TRegexExecutorLocals locals, int offset, boolean forward) {
        locals.setIndex(inputIncRaw(locals.getIndex(), offset, forward));
    }

    public int inputIncRaw(int index) {
        return inputIncRaw(index, 1, isForward());
    }

    public int inputIncRaw(int index, int offset) {
        return inputIncRaw(index, offset, isForward());
    }

    public static int inputIncRaw(int index, boolean forward) {
        return inputIncRaw(index, 1, forward);
    }

    public static int inputIncRaw(int index, int offset, boolean forward) {
        assert offset > 0;
        return forward ? index + offset : index - offset;
    }

    public void inputIncNextIndexRaw(TRegexExecutorLocals locals) {
        inputIncNextIndexRaw(locals, 1);
    }

    public void inputIncNextIndexRaw(TRegexExecutorLocals locals, int offset) {
        locals.setNextIndex(inputIncRaw(locals.getIndex(), offset, isForward()));
    }

    public int countUpTo(TRegexExecutorLocals locals, int max, int nCodePoints) {
        CompilerAsserts.partialEvaluationConstant(nCodePoints);
        if (nCodePoints > 0) {
            assert isForward();
            int i = 0;
            int index = locals.getIndex();
            while (locals.getIndex() < max && i < nCodePoints) {
                inputSkipIntl(locals, true);
                i++;
            }
            locals.setIndex(index);
            return i;
        }
        return 0;
    }

    public int rewindUpTo(TRegexExecutorLocals locals, int min, int nCodePoints) {
        CompilerAsserts.partialEvaluationConstant(nCodePoints);
        if (nCodePoints > 0) {
            assert isForward();
            int i = 0;
            while (locals.getIndex() > min && i < nCodePoints) {
                inputSkipIntl(locals, false);
                i++;
            }
            return i;
        }
        return 0;
    }
}
