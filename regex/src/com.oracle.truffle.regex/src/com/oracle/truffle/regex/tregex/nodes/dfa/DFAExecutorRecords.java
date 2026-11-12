/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodes.dfa;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.tregex.buffer.LongArrayBuffer;
import com.oracle.truffle.regex.tregex.parser.ast.InnerLiteral;

/**
 * Static operations on the immutable byte-encoded DFA graph owned by a
 * {@link TRegexDFAExecutorNode}. Every state and transition is a variable-size record in one
 * {@code byte[]} and every graph edge is the absolute byte offset of its target record. Negative
 * references denote absent or terminal edges. Records are concatenated without alignment or
 * padding, and all multi-byte fields use little-endian byte order.
 * <p>
 * Each record layout, its encoder, and its accessors are defined by the class that logically
 * represents that record, beginning with {@link DFAInitialState}, {@link DFAState}, and
 * {@link DFAAbstractTransition}. This class owns only the graph-wide storage and primitive codec.
 * Bounded-quantifier {@code long} values are stored separately in a {@code long[]}; references into
 * that array are element offsets.
 * <p>
 * Graph-reference accessors are early-inlined so the merge-exploded executor loop sees successor
 * offsets as partial-evaluation constants before it decides whether loop states can be merged.
 */
final class DFAExecutorRecords {

    static final int NO_REF = -1;
    static final int INITIAL_STATE_REF = 0;

    /** Declaration order is the encoded format. Do not reorder. */
    enum Kind {
        initialState,
        state,
        traceFinderState,
        simpleCGState,
        genericCGState,
        bqState,
        innerLiteralState,
        genericCGTransition,
        genericCGPreFinalTransition,
        genericCGAnchoredFinalTransition,
        simpleCGTransition,
        bqTransitionOps,
        bqTransitionConstraints
    }

    @CompilationFinal(dimensions = 1) private static final Kind[] KIND_VALUES = Kind.values();

    private static final ByteArraySupport BYTE_ARRAY_SUPPORT = ByteArraySupport.littleEndian();

    private static final int FIELD_KIND = 0;

    private DFAExecutorRecords() {
    }

    static final class Data {
        final byte[] records;
        final long[] bqRecords;
        final int numberOfNodes;
        final int numberOfTransitions;
        final InnerLiteral innerLiteral;

        private Data(byte[] records, long[] bqRecords, int numberOfNodes, int numberOfTransitions, InnerLiteral innerLiteral) {
            this.records = records;
            this.bqRecords = bqRecords;
            this.numberOfNodes = numberOfNodes;
            this.numberOfTransitions = numberOfTransitions;
            this.innerLiteral = innerLiteral;
        }
    }

    static Data create(DFAAbstractNode[] nodes, int[] sequentialMatcherRecords) {
        if (nodes.length == 0 || !(nodes[0] instanceof DFAInitialState)) {
            throw CompilerDirectives.shouldNotReachHere("DFA node zero must be the initial state");
        }
        int[] nodeRefs = new int[nodes.length];
        int recordsSize = 0;
        int numberOfTransitions = 0;
        InnerLiteral innerLiteral = null;
        try {
            for (int i = 0; i < nodes.length; i++) {
                DFAAbstractNode node = nodes[i];
                if (node == null || node.getId() != i) {
                    throw CompilerDirectives.shouldNotReachHere("invalid DFA node table");
                }
                nodeRefs[i] = recordsSize;
                recordsSize = Math.addExact(recordsSize, node.encodedSize());
                if (node instanceof DFAAbstractState state) {
                    numberOfTransitions = Math.addExact(numberOfTransitions, state.getSuccessors().length);
                }
                if (node instanceof DFAState state && !state.treeTransitionMatching() &&
                                SequentialMatchers.getNoMatchSuccessor(sequentialMatcherRecords, state.getSequentialMatchersRef()) >= 0) {
                    numberOfTransitions = Math.addExact(numberOfTransitions, 1);
                }
                if (node instanceof DFAFindInnerLiteralState innerLiteralState) {
                    if (innerLiteral != null) {
                        throw CompilerDirectives.shouldNotReachHere("more than one inner-literal DFA state");
                    }
                    innerLiteral = innerLiteralState.getInnerLiteral();
                }
            }
        } catch (ArithmeticException e) {
            throw new UnsupportedRegexException("DFA record data is too large");
        }
        byte[] records = new byte[recordsSize];
        Encoder encoder = new Encoder(records, nodeRefs);
        for (DFAAbstractNode node : nodes) {
            node.encode(encoder, nodeRefs[node.getId()]);
        }
        return new Data(records, encoder.getBQRecords(), nodes.length, numberOfTransitions, innerLiteral);
    }

    static final class Encoder {
        private final byte[] records;
        private final int[] nodeRefs;
        private final LongArrayBuffer bqRecords = new LongArrayBuffer();

        private Encoder(byte[] records, int[] nodeRefs) {
            this.records = records;
            this.nodeRefs = nodeRefs;
        }

        long[] getBQRecords() {
            return bqRecords.isEmpty() ? null : bqRecords.toArray();
        }

        void putBQValues(int refOffset, int countOffset, long[] values) {
            int bqRef = bqRecords.length();
            try {
                Math.addExact(bqRef, values.length);
            } catch (ArithmeticException e) {
                throw new UnsupportedRegexException("DFA bounded-quantifier data is too large");
            }
            putInt(refOffset, bqRef);
            putUnsignedShort(countOffset, values.length);
            bqRecords.addAll(values);
        }

        void putNodeRef(int offset, short nodeId) {
            putInt(offset, translate(nodeId));
        }

        void putKind(int recordRef, Kind kind) {
            records[recordRef + FIELD_KIND] = (byte) kind.ordinal();
        }

        void putByte(int offset, int value) {
            records[offset] = (byte) value;
        }

        void putUnsignedShort(int offset, int value) {
            if (value < 0 || value > 0xffff) {
                throw new UnsupportedRegexException("DFA record field is too large");
            }
            putShort(offset, (short) value);
        }

        void putShort(int offset, short value) {
            BYTE_ARRAY_SUPPORT.putShort(records, offset, value);
        }

        void putInt(int offset, int value) {
            BYTE_ARRAY_SUPPORT.putInt(records, offset, value);
        }

        private int translate(short nodeId) {
            if (nodeId == -1) {
                return NO_REF;
            }
            if (nodeId < 0 || nodeId >= nodeRefs.length) {
                throw CompilerDirectives.shouldNotReachHere("invalid DFA record reference");
            }
            return nodeRefs[nodeId];
        }
    }

    static Kind getKind(byte[] records, int ref) {
        int kind = Byte.toUnsignedInt(records[ref + FIELD_KIND]);
        CompilerAsserts.partialEvaluationConstant(kind);
        if (kind >= KIND_VALUES.length) {
            throw CompilerDirectives.shouldNotReachHere("invalid DFA record kind");
        }
        return KIND_VALUES[kind];
    }

    static boolean isTransition(Kind kind) {
        return kind.ordinal() >= Kind.genericCGTransition.ordinal();
    }

    static boolean isDFAState(Kind kind) {
        return switch (kind) {
            case state, traceFinderState, simpleCGState, genericCGState, bqState -> true;
            default -> false;
        };
    }

    static int getRecordSize(byte[] records, int ref, Kind kind) {
        return switch (kind) {
            case initialState -> DFAInitialState.getEncodedSize(records);
            case state -> DFAState.getEncodedSize(records, ref);
            case traceFinderState -> TraceFinderDFAState.getEncodedSize(records, ref);
            case simpleCGState -> DFASimpleCGTrackingState.getEncodedSize(records, ref);
            case genericCGState -> CGTrackingDFAState.getEncodedSize(records, ref);
            case bqState -> DFABQTrackingState.getEncodedSize(records, ref);
            case innerLiteralState -> DFAFindInnerLiteralState.getEncodedSize();
            case genericCGTransition -> CGTrackingTransition.getEncodedSize();
            case genericCGPreFinalTransition -> CGTrackingPreFinalTransition.getEncodedSize();
            case genericCGAnchoredFinalTransition -> CGTrackingAnchoredFinalTransition.getEncodedSize();
            case simpleCGTransition -> DFASimpleCGTransition.getEncodedSize();
            case bqTransitionOps -> DFABQTrackingTransitionOps.getEncodedSize();
            case bqTransitionConstraints -> DFABQTrackingTransitionConstraints.getEncodedSize(records, ref);
        };
    }

    static int getUnsignedShort(byte[] records, int offset) {
        int value = Short.toUnsignedInt(BYTE_ARRAY_SUPPORT.getShortUnaligned(records, offset));
        CompilerAsserts.partialEvaluationConstant(value);
        return value;
    }

    static int getShort(byte[] records, int offset) {
        int value = BYTE_ARRAY_SUPPORT.getShortUnaligned(records, offset);
        CompilerAsserts.partialEvaluationConstant(value);
        return value;
    }

    @CompilerDirectives.EarlyInline
    static int getInt(byte[] records, int offset) {
        int value = BYTE_ARRAY_SUPPORT.getIntUnaligned(records, offset);
        CompilerAsserts.partialEvaluationConstant(value);
        return value;
    }
}
