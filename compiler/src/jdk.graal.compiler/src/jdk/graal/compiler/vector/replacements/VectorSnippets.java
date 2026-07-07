/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.replacements;

import static jdk.graal.compiler.api.directives.GraalDirectives.LIKELY_PROBABILITY;
import static jdk.graal.compiler.api.directives.GraalDirectives.UNLIKELY_PROBABILITY;
import static jdk.graal.compiler.api.directives.GraalDirectives.injectBranchProbability;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_IGNORED;
import static jdk.graal.compiler.replacements.ReplacementsUtil.dynamicAssert;
import static jdk.graal.compiler.replacements.ReplacementsUtil.staticAssert;

import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.collections.UnmodifiableMapCursor;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.vector.nodes.VectorPolicies;
import jdk.graal.compiler.vector.nodes.consumer.FoldVectorNode;
import jdk.graal.compiler.vector.nodes.consumer.LowerableVectorConsumer;
import jdk.graal.compiler.vector.nodes.consumer.VectorGuardNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorLoopNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorReachabilityFenceNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorSafepointNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorWriteNode;
import jdk.graal.compiler.vector.nodes.lowered.CommitVectorConsumerNode;
import jdk.graal.compiler.vector.nodes.lowered.FinishVectorConsumerNode;
import jdk.graal.compiler.vector.nodes.lowered.PartialVectorConsumerNode;
import jdk.graal.compiler.vector.nodes.lowered.PartialVectorConsumerNode.Consumer;
import jdk.graal.compiler.vector.nodes.lowered.VectorAlignmentNode;
import jdk.graal.compiler.vector.nodes.lowered.VectorConsumerProxyNode;
import jdk.graal.compiler.vector.nodes.lowered.VectorConsumerProxyNode.ConsumerParameter;
import jdk.graal.compiler.vector.nodes.lowered.VectorHasNextNode;
import jdk.graal.compiler.vector.nodes.lowered.VectorInitialIteratorNode;
import jdk.graal.compiler.vector.nodes.lowered.VectorShiftNode;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorConsumerIterator;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.loop.phases.LoopTransformations;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetCounter;
import jdk.graal.compiler.replacements.SnippetIntegerHistogram;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.SnippetTemplate.AbstractTemplates;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;
import jdk.graal.compiler.replacements.nodes.ExplodeLoopNode;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.TargetDescription;

public class VectorSnippets implements Snippets {

    private static final int F_ALIGN_NONE = 0;
    private static final int F_ALIGN_UNROLL_LINEAR = 1;
    private static final int F_ALIGN_LOOP_LINEAR = 2;
    private static final int F_ALIGN_VECTOR_OVERLAPPING = 3;

    private static final int F_REST_NONE = 0;
    private static final int F_REST_UNROLL_LINEAR = 1;
    private static final int F_REST_LOOP_LINEAR = 2;
    private static final int F_REST_UNROLL_BINARY = 3;
    private static final int F_REST_VECTOR_OVERLAPPING = 4;

    private static final SnippetTemplate.UsageReplacer VECTOR_REPLACER = (oldNode, newNode) -> {
        if (newNode == null) {
            GraalError.guarantee(oldNode.hasNoUsages(), "old node should not have usages when the snippet returns no value: %s", oldNode);
        } else {
            /*
             * The snippet graph is parsed with an object-typed ConsumerParameter, so the duplicated
             * FinishVectorConsumerNode initially keeps an object stamp. After instantiation binds
             * ConsumerParameter to the actual vector consumer, recompute the stamps of the returned
             * node and its direct inputs before replacing uses of the placeholder node. For folds,
             * that placeholder can have a primitive stamp.
             */
            for (Node input : newNode.inputs()) {
                if (input instanceof ValueNode valueInput) {
                    valueInput.inferStamp();
                }
            }
            newNode.inferStamp();
            oldNode.replaceAtUsages(newNode);
        }
    };

    @Snippet
    public static Object scalarConsumerLoop(ConsumerParameter consumer, @ConstantParameter int supportedStepLengthsMask, long vectorLength, @ConstantParameter Counters counters) {
        counters.scalarVectorElements.inc(vectorLength);

        Consumer consumerProxy = VectorConsumerProxyNode.consumer(consumer);
        VectorConsumerIterator iterator = VectorInitialIteratorNode.iterator(consumerProxy);

        if (injectBranchProbability(LIKELY_PROBABILITY, VectorHasNextNode.hasNext(consumerProxy, iterator, 1))) {
            do {
                iterator = createVectorConsumer(consumerProxy, iterator, supportedStepLengthsMask, 1, F_ALIGN_NONE, false);
            } while (injectBranchProbability(LIKELY_PROBABILITY, VectorHasNextNode.hasNext(consumerProxy, iterator, 1)));
        }

        dynamicAssert(!VectorHasNextNode.hasNext(consumerProxy, iterator, 1), "not all elements were processed");
        return FinishVectorConsumerNode.finish(consumerProxy, iterator);
    }

    /**
     * Generate all code for processing the consumer with vectorized code. This includes optional
     * alignment, a main vector loop, and a tail consumer. All elements of the consumer will be
     * processed. This snippet is for "stand-alone" vector consumers produced by intrinsics or by
     * the loop vectorizer, completely eliminating the original loop.
     *
     * @implNote keep in sync with {@link #vectorConsumerOnlyMainLoop}
     */
    @Snippet
    public static Object vectorConsumerLoop(ConsumerParameter consumer, @ConstantParameter int supportedStepLengthsMask, @ConstantParameter int alignMode, @ConstantParameter int stepLengthPrimary,
                    @ConstantParameter int maxMissingPrimaryAlignment, @ConstantParameter boolean assertAlignment, @ConstantParameter int minPrimary, @ConstantParameter int stepLengthSecondary,
                    @ConstantParameter int maxMissingSecondaryAlignment, @ConstantParameter int tailMode, long vectorLength,
                    @ConstantParameter Counters counters) {
        counters.vectorConsumerLoopElements.inc(vectorLength);
        counters.enteredVectorConsumer.inc();

        Consumer consumerProxy = VectorConsumerProxyNode.consumer(consumer);
        VectorConsumerIterator iterator = VectorInitialIteratorNode.iterator(consumerProxy);

        if (injectBranchProbability(LIKELY_PROBABILITY, vectorLength != 0)) {
            if (minPrimary <= 1 || injectBranchProbability(LIKELY_PROBABILITY, VectorHasNextNode.hasNext(consumerProxy, iterator, minPrimary))) {
                iterator = align(consumerProxy, iterator, supportedStepLengthsMask, alignMode, stepLengthPrimary, maxMissingPrimaryAlignment);
                /*
                 * The probability of entering the vector loop, and the frequency of the vector
                 * loop, can be adjusted after snippet expansion based on profiling information. See
                 * Templates.fixUpVectorLoopProbabilities for discussion of this entry probability.
                 */
                if (injectBranchProbability(LIKELY_PROBABILITY, VectorHasNextNode.hasNext(consumerProxy, iterator, stepLengthPrimary))) {
                    counters.enteredPrimaryVectorConsumerLoop.inc();
                    do {
                        iterator = createVectorConsumer(consumerProxy, iterator, supportedStepLengthsMask, stepLengthPrimary, alignMode, assertAlignment);
                    } while (injectBranchProbability(LIKELY_PROBABILITY, VectorHasNextNode.hasNext(consumerProxy, iterator, stepLengthPrimary)));
                }
                iterator = tailConsumer(consumerProxy, iterator, supportedStepLengthsMask, stepLengthPrimary - 1, vectorLength, tailMode, alignMode, assertAlignment, counters);
            } else {
                iterator = align(consumerProxy, iterator, supportedStepLengthsMask, alignMode, stepLengthSecondary, maxMissingSecondaryAlignment);
                if (injectBranchProbability(LIKELY_PROBABILITY, VectorHasNextNode.hasNext(consumerProxy, iterator, stepLengthSecondary))) {
                    counters.enteredSecondaryVectorConsumerLoop.inc();
                    do {
                        iterator = createVectorConsumer(consumerProxy, iterator, supportedStepLengthsMask, stepLengthSecondary, alignMode, assertAlignment);
                    } while (injectBranchProbability(LIKELY_PROBABILITY, VectorHasNextNode.hasNext(consumerProxy, iterator, stepLengthSecondary)));
                }
                iterator = tailConsumer(consumerProxy, iterator, supportedStepLengthsMask, stepLengthSecondary - 1, vectorLength, tailMode, alignMode, assertAlignment, counters);
            }
        }

        dynamicAssert(!VectorHasNextNode.hasNext(consumerProxy, iterator, 1), "not all elements were processed");
        return FinishVectorConsumerNode.finish(consumerProxy, iterator);
    }

    /**
     * Generate only the main vector consumer loop. Alignment and tail mode must both be NONE. This
     * may process fewer than {@code vectorLength} elements; separate tail processing must be done
     * by other parts of the program. This snippet is for use with vector consumers produced by the
     * loop vectorizer while keeping the original loop as the tail loop to pick up the remaining
     * elements.
     *
     * @implNote keep in sync with {@link #vectorConsumerLoop}
     */
    @Snippet
    public static Object vectorConsumerOnlyMainLoop(ConsumerParameter consumer, @ConstantParameter int supportedStepLengthsMask, @ConstantParameter int alignMode,
                    @ConstantParameter int stepLengthPrimary, @ConstantParameter int maxMissingPrimaryAlignment, @ConstantParameter boolean assertAlignment, @ConstantParameter int minPrimary,
                    @ConstantParameter int stepLengthSecondary, @ConstantParameter int maxMissingSecondaryAlignment, @ConstantParameter int tailMode, long vectorLength,
                    @ConstantParameter Counters counters) {
        counters.enteredVectorConsumer.inc();

        Consumer consumerProxy = VectorConsumerProxyNode.consumer(consumer);
        VectorConsumerIterator iterator = VectorInitialIteratorNode.iterator(consumerProxy);

        staticAssert(alignMode == F_ALIGN_NONE && maxMissingPrimaryAlignment == 0 && maxMissingSecondaryAlignment == 0, "vectorConsumerOnlyMainLoop cannot be used with alignment");
        staticAssert(tailMode == F_REST_NONE, "vectorConsumerOnlyMainLoop does not emit the tail consumer");

        long consumedElements = 0;

        if (stepLengthPrimary == 1) {
            /* Only scalar code is possible. Do nothing; let the post loop do all the work. */
        } else {
            /* Generate a SIMD loop. */
            if (minPrimary <= 1 || injectBranchProbability(LIKELY_PROBABILITY, VectorHasNextNode.hasNext(consumerProxy, iterator, minPrimary))) {
                /*
                 * The probability of entering the vector loop, and the frequency of the vector
                 * loop, can be adjusted after snippet expansion based on profiling information. See
                 * Templates.fixUpVectorLoopProbabilities for discussion of this entry probability.
                 */
                if (injectBranchProbability(LIKELY_PROBABILITY, VectorHasNextNode.hasNext(consumerProxy, iterator, stepLengthPrimary))) {
                    counters.enteredPrimaryVectorConsumerLoop.inc();
                    do {
                        iterator = createVectorConsumer(consumerProxy, iterator, supportedStepLengthsMask, stepLengthPrimary, alignMode, assertAlignment);
                        consumedElements += stepLengthPrimary;
                    } while (injectBranchProbability(LIKELY_PROBABILITY, VectorHasNextNode.hasNext(consumerProxy, iterator, stepLengthPrimary)));
                }
            } else {
                if (injectBranchProbability(LIKELY_PROBABILITY, VectorHasNextNode.hasNext(consumerProxy, iterator, stepLengthSecondary))) {
                    counters.enteredSecondaryVectorConsumerLoop.inc();
                    do {
                        iterator = createVectorConsumer(consumerProxy, iterator, supportedStepLengthsMask, stepLengthSecondary, alignMode, assertAlignment);
                        consumedElements += stepLengthSecondary;
                    } while (injectBranchProbability(LIKELY_PROBABILITY, VectorHasNextNode.hasNext(consumerProxy, iterator, stepLengthSecondary)));
                }
            }
        }

        counters.vectorConsumerOnlyMainLoopElements.inc(consumedElements);
        dynamicAssert(consumedElements <= vectorLength, "tried to process too many elements");
        return FinishVectorConsumerNode.finish(consumerProxy, iterator, consumedElements);
    }

    @Snippet(allowMissingProbabilities = true)
    public static Object vectorConsumerUnrolled(ConsumerParameter consumer, @ConstantParameter int supportedStepLengthsMask, @ConstantParameter int alignMode, @ConstantParameter int stepLengthPrimary,
                    @ConstantParameter int maxMissingPrimaryAlignment, @ConstantParameter boolean assertAlignment, @ConstantParameter int lowerBound, @ConstantParameter int upperBound,
                    @ConstantParameter int stepLengthShift, long vectorLength, @ConstantParameter Counters counters) {
        counters.unrolledVectorElements.inc(vectorLength);

        Consumer consumerProxy = VectorConsumerProxyNode.consumer(consumer);
        VectorConsumerIterator iterator = VectorInitialIteratorNode.iterator(consumerProxy);

        int remainingLowerBound = lowerBound;
        if (alignMode != F_ALIGN_NONE) {
            iterator = align(consumerProxy, iterator, supportedStepLengthsMask, alignMode, stepLengthPrimary, maxMissingPrimaryAlignment);
            remainingLowerBound = Math.max(0, lowerBound - maxMissingPrimaryAlignment);
        }

        int stepLengthModuloMask = stepLengthPrimary - 1;

        // unconditional vector consumers with full step length
        int unconditionalFullStepLengthIterations = remainingLowerBound >> stepLengthShift;
        if (unconditionalFullStepLengthIterations > 0) {
            ExplodeLoopNode.explodeLoop();
            for (int i = 0; injectBranchProbability(LIKELY_PROBABILITY, i < unconditionalFullStepLengthIterations); i++) {
                dynamicAssert(VectorHasNextNode.hasNext(consumerProxy, iterator, stepLengthPrimary), "not enough elements available");
                iterator = createVectorConsumer(consumerProxy, iterator, supportedStepLengthsMask, stepLengthPrimary, alignMode, assertAlignment);
            }
        }

        // we want to minimize changes in step length and we must ensure that we don't destroy the
        // alignment, e.g. by switching the vector length from (8 -> 4 -> 8).
        if (remainingLowerBound == upperBound) {
            iterator = createUnrolledBinaryVectorConsumers(consumerProxy, iterator, supportedStepLengthsMask, remainingLowerBound & stepLengthModuloMask, false, alignMode, assertAlignment);
        } else if (remainingLowerBound + 1 == upperBound) {
            iterator = createUnrolledBinaryVectorConsumers(consumerProxy, iterator, supportedStepLengthsMask, remainingLowerBound & stepLengthModuloMask, false, alignMode, assertAlignment);
            if (injectBranchProbability(LIKELY_PROBABILITY, VectorHasNextNode.hasNext(consumerProxy, iterator, 1))) {
                iterator = createVectorConsumer(consumerProxy, iterator, supportedStepLengthsMask, 1, alignMode, assertAlignment);
            }
        } else {
            // we directly want to continue with full step length instructions, so we assume that
            // the remaining lower bound elements are part of the upper bound
            int remainingUpperBound = upperBound - remainingLowerBound + (remainingLowerBound & stepLengthModuloMask);
            int conditionalFullStepLengthIterations = remainingUpperBound >> stepLengthShift;
            if (conditionalFullStepLengthIterations > 0) {
                ExplodeLoopNode.explodeLoop();
                for (int i = 0; injectBranchProbability(LIKELY_PROBABILITY, i < conditionalFullStepLengthIterations); i++) {
                    if (injectBranchProbability(UNLIKELY_PROBABILITY, !VectorHasNextNode.hasNext(consumerProxy, iterator, stepLengthPrimary))) {
                        break;
                    }
                    iterator = createVectorConsumer(consumerProxy, iterator, supportedStepLengthsMask, stepLengthPrimary, alignMode, assertAlignment);
                }
            }

            // we don't know how many elements were consumed by the conditional consumers above, but
            // there are at most stepLengthPrimary - 1 remaining
            remainingUpperBound = ((Integer.highestOneBit(remainingUpperBound) << 1) - 1) & stepLengthModuloMask;
            iterator = createUnrolledBinaryVectorConsumers(consumerProxy, iterator, supportedStepLengthsMask, remainingUpperBound, true, alignMode, assertAlignment);
        }

        dynamicAssert(!VectorHasNextNode.hasNext(consumerProxy, iterator, 1), "not all elements were processed");
        return FinishVectorConsumerNode.finish(consumerProxy, iterator);
    }

    @Snippet
    public static Object vectorConsumerEmpty(ConsumerParameter consumer, @ConstantParameter Counters counters) {
        counters.staticZeroVectorElements.inc();

        Consumer consumerProxy = VectorConsumerProxyNode.consumer(consumer);
        VectorConsumerIterator iterator = VectorInitialIteratorNode.iterator(consumerProxy);
        dynamicAssert(!VectorHasNextNode.hasNext(consumerProxy, iterator, 1), "not all elements were processed");
        return FinishVectorConsumerNode.finish(consumerProxy, iterator);
    }

    private static VectorConsumerIterator createVectorConsumer(Consumer consumerProxy, VectorConsumerIterator initialIterator, int supportedStepLengthsMask, int stepLength,
                    int alignMode, boolean assertAlignment) {
        staticAssert(CodeUtil.isPowerOf2(stepLength), "step length must be a power of 2");
        int maxSupportedStepLength = getMaxSupportedStepLength(supportedStepLengthsMask);
        if (stepLength <= maxSupportedStepLength) {
            if (assertAlignment && alignMode != F_ALIGN_NONE) {
                dynamicAssert(VectorAlignmentNode.getAlignCount(consumerProxy, initialIterator, stepLength) == 0, "must be aligned if alignment was required");
            }
            staticAssert((supportedStepLengthsMask & stepLength) != 0, "step length must be supported");
            return PartialVectorConsumerNode.consume(consumerProxy, initialIterator, stepLength);
        } else {
            VectorConsumerIterator iterator = initialIterator;
            int remainingStepLength = stepLength;
            ExplodeLoopNode.explodeLoop();
            while (remainingStepLength > 0) {
                if (assertAlignment && alignMode != F_ALIGN_NONE) {
                    dynamicAssert(VectorAlignmentNode.getAlignCount(consumerProxy, initialIterator, stepLength) == 0, "must be aligned if alignment was required");
                }
                iterator = PartialVectorConsumerNode.consume(consumerProxy, iterator, maxSupportedStepLength);
                remainingStepLength -= maxSupportedStepLength;
            }
            return iterator;
        }
    }

    private static VectorConsumerIterator tailConsumer(Consumer consumerProxy, VectorConsumerIterator restIterator, int supportedStepLengthsMask, int remainingStepLength, long vectorLength,
                    int tailMode,
                    int alignMode, boolean assertAlignment, Counters counters) {
        VectorConsumerIterator iterator = restIterator;
        if (remainingStepLength > 0) {
            counters.tailProcessingEntered.inc();
            if (tailMode == F_REST_LOOP_LINEAR) {
                while (injectBranchProbability(LIKELY_PROBABILITY, VectorHasNextNode.hasNext(consumerProxy, iterator, 1))) {
                    iterator = createVectorConsumer(consumerProxy, iterator, supportedStepLengthsMask, 1, alignMode, assertAlignment);
                }
            } else if (tailMode == F_REST_UNROLL_LINEAR) {
                ExplodeLoopNode.explodeLoop();
                for (int i = 0; i < remainingStepLength; i++) {
                    if (injectBranchProbability(UNLIKELY_PROBABILITY, !VectorHasNextNode.hasNext(consumerProxy, iterator, 1))) {
                        break;
                    }
                    iterator = createVectorConsumer(consumerProxy, iterator, supportedStepLengthsMask, 1, alignMode, assertAlignment);
                }
            } else if (tailMode == F_REST_UNROLL_BINARY) {
                iterator = createUnrolledBinaryVectorConsumers(consumerProxy, iterator, supportedStepLengthsMask, remainingStepLength, true, alignMode, assertAlignment);
            } else if (tailMode == F_REST_VECTOR_OVERLAPPING) {
                iterator = createVectorConsumerAtEnd(consumerProxy, iterator, supportedStepLengthsMask, remainingStepLength + 1, vectorLength);
            }
        }
        return iterator;
    }

    private static VectorConsumerIterator createUnrolledBinaryVectorConsumers(Consumer consumerProxy, VectorConsumerIterator initialIterator, int supportedStepLengthsMask, int stepLength,
                    boolean checkVectorLength, int alignMode, boolean assertAlignment) {
        VectorConsumerIterator iterator = initialIterator;
        if (stepLength > 0) {
            int remainingStepLength = stepLength;
            int highestBitValue = Integer.highestOneBit(remainingStepLength);
            int emitVectorSize = 0;

            ExplodeLoopNode.explodeLoop();
            while (highestBitValue > 0) {
                boolean isStepLengthRequested = (remainingStepLength & highestBitValue) != 0;
                if (isStepLengthRequested) {
                    remainingStepLength = remainingStepLength - highestBitValue;
                    emitVectorSize++;
                }

                boolean isStepLengthSupported = (supportedStepLengthsMask & highestBitValue) != 0;
                if (isStepLengthSupported) {
                    if (emitVectorSize > 0) {
                        ExplodeLoopNode.explodeLoop();
                        for (int i = 0; i < emitVectorSize; i++) {
                            if (injectBranchProbability(LIKELY_PROBABILITY, checkVectorLength) &&
                                            injectBranchProbability(UNLIKELY_PROBABILITY, !VectorHasNextNode.hasNext(consumerProxy, iterator, highestBitValue))) {
                                break;
                            }
                            dynamicAssert(checkVectorLength || VectorHasNextNode.hasNext(consumerProxy, iterator, highestBitValue), "not enough elements available");
                            iterator = createVectorConsumer(consumerProxy, iterator, supportedStepLengthsMask, highestBitValue, alignMode, assertAlignment);
                        }
                        emitVectorSize = 0;
                    }
                } else {
                    // if a vector size is not supported, we need to emit the next available vector
                    // size multiple times
                    emitVectorSize = emitVectorSize << 1;
                }

                highestBitValue = highestBitValue >> 1;
            }
            staticAssert(emitVectorSize == 0 && remainingStepLength == 0, "no unprocessed elements must be remaining");
        }
        return iterator;
    }

    private static VectorConsumerIterator createVectorConsumerAtEnd(Consumer consumerProxy, VectorConsumerIterator initialIterator, int supportedStepLengthsMask, int stepLength, long vectorLength) {
        VectorConsumerIterator iterator = initialIterator;
        if (injectBranchProbability(LIKELY_PROBABILITY, vectorLength >= stepLength)) {
            CommitVectorConsumerNode.commit(consumerProxy, iterator);

            iterator = VectorInitialIteratorNode.iterator(consumerProxy);
            iterator = VectorShiftNode.shift(consumerProxy, iterator, vectorLength - stepLength);
            iterator = createVectorConsumer(consumerProxy, iterator, supportedStepLengthsMask, stepLength, F_ALIGN_NONE, false);
        } else {
            while (injectBranchProbability(UNLIKELY_PROBABILITY, VectorHasNextNode.hasNext(consumerProxy, iterator, 1))) {
                iterator = createVectorConsumer(consumerProxy, iterator, supportedStepLengthsMask, 1, F_ALIGN_NONE, false);
            }
        }
        return iterator;
    }

    private static VectorConsumerIterator align(Consumer consumerProxy, VectorConsumerIterator startIterator, int supportedStepLengthsMask, int alignMode, int alignTo, int maxMissingAlignment) {
        VectorConsumerIterator iterator = startIterator;
        if (maxMissingAlignment > 0) {
            if (alignMode == F_ALIGN_LOOP_LINEAR) {
                iterator = alignLoopLinear(consumerProxy, iterator, supportedStepLengthsMask, alignTo);
            } else if (alignMode == F_ALIGN_UNROLL_LINEAR) {
                iterator = alignUnrolled(consumerProxy, iterator, supportedStepLengthsMask, alignTo, maxMissingAlignment);
            } else if (alignMode == F_ALIGN_VECTOR_OVERLAPPING) {
                iterator = alignVectorOverlapping(consumerProxy, iterator, supportedStepLengthsMask, alignTo);
            }
        }
        return iterator;
    }

    private static VectorConsumerIterator alignLoopLinear(Consumer consumerProxy, VectorConsumerIterator startIterator, int supportedStepLengthsMask, int alignTo) {
        VectorConsumerIterator iterator = startIterator;
        long alignCount = VectorAlignmentNode.getAlignCount(consumerProxy, iterator, alignTo);
        if (injectBranchProbability(LIKELY_PROBABILITY, VectorHasNextNode.hasNext(consumerProxy, iterator, 1, alignCount))) {
            do {
                iterator = createVectorConsumer(consumerProxy, iterator, supportedStepLengthsMask, 1, F_ALIGN_NONE, false);
            } while (injectBranchProbability(LIKELY_PROBABILITY, VectorHasNextNode.hasNext(consumerProxy, iterator, 1, alignCount)));
        }
        return iterator;
    }

    private static VectorConsumerIterator alignUnrolled(Consumer consumerProxy, VectorConsumerIterator startIterator, int supportedStepLengthsMask, int alignTo, int maxMissingAlignment) {
        VectorConsumerIterator iterator = startIterator;
        long alignCount = VectorAlignmentNode.getAlignCount(consumerProxy, iterator, alignTo);
        if (injectBranchProbability(LIKELY_PROBABILITY, VectorHasNextNode.hasNext(consumerProxy, iterator, 1, alignCount))) {
            iterator = createVectorConsumer(consumerProxy, iterator, supportedStepLengthsMask, 1, F_ALIGN_NONE, false);
            ExplodeLoopNode.explodeLoop();
            for (int i = 1; i < maxMissingAlignment; i++) {
                if (injectBranchProbability(UNLIKELY_PROBABILITY, !VectorHasNextNode.hasNext(consumerProxy, iterator, 1, alignCount))) {
                    break;
                }
                iterator = createVectorConsumer(consumerProxy, iterator, supportedStepLengthsMask, 1, F_ALIGN_NONE, false);
            }
        }
        return iterator;
    }

    private static VectorConsumerIterator alignVectorOverlapping(Consumer consumerProxy, VectorConsumerIterator startIterator, int supportedStepLengthsMask, int alignTo) {
        VectorConsumerIterator iterator = startIterator;
        if (injectBranchProbability(LIKELY_PROBABILITY, VectorHasNextNode.hasNext(consumerProxy, startIterator, alignTo))) {
            iterator = createVectorConsumer(consumerProxy, startIterator, supportedStepLengthsMask, alignTo, F_ALIGN_NONE, false);
            CommitVectorConsumerNode.commit(consumerProxy, iterator);
            iterator = VectorShiftNode.shift(consumerProxy, startIterator, VectorAlignmentNode.getAlignCount(consumerProxy, startIterator, alignTo));
        }
        return iterator;
    }

    private static int getMaxSupportedStepLength(int supportedStepLengthsMask) {
        return Integer.highestOneBit(supportedStepLengthsMask);
    }

    @NodeInfo(size = SIZE_IGNORED, cycles = CYCLES_IGNORED)
    static final class DummyNode extends FixedWithNextNode implements SingleMemoryKill {
        public static final NodeClass<DummyNode> TYPE = NodeClass.create(DummyNode.class);

        protected DummyNode(Stamp stamp) {
            super(TYPE, stamp);
        }

        @Override
        public LocationIdentity getKilledLocationIdentity() {
            return LocationIdentity.any();
        }

    }

    @NodeInfo(size = SIZE_IGNORED, cycles = CYCLES_IGNORED)
    static final class DummyMemoryKill extends FixedWithNextNode implements SingleMemoryKill {
        public static final NodeClass<DummyMemoryKill> TYPE = NodeClass.create(DummyMemoryKill.class);
        private LocationIdentity identity;

        protected DummyMemoryKill(Stamp stamp, LocationIdentity identity) {
            super(TYPE, stamp);
            this.identity = identity;
        }

        @Override
        public LocationIdentity getKilledLocationIdentity() {
            return identity;
        }
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo scalarConsumerLoop;
        private final SnippetInfo vectorConsumerLoop;
        private final SnippetInfo vectorConsumerOnlyMainLoop;
        private final SnippetInfo vectorConsumerUnrolled;
        private final SnippetInfo vectorConsumerEmpty;

        private final VectorArchitecture arch;
        private final Counters counters;
        private final TargetDescription target;

        @SuppressWarnings("this-escape")
        public Templates(OptionValues options, SnippetCounter.Group.Factory factory, Providers providers, TargetDescription target, VectorArchitecture arch) {
            super(options, providers);
            this.arch = arch;
            this.target = target;
            this.counters = new Counters(factory);

            this.scalarConsumerLoop = snippet(providers, VectorSnippets.class, "scalarConsumerLoop");
            this.vectorConsumerLoop = snippet(providers, VectorSnippets.class, "vectorConsumerLoop");
            this.vectorConsumerOnlyMainLoop = snippet(providers, VectorSnippets.class, "vectorConsumerOnlyMainLoop");
            this.vectorConsumerUnrolled = snippet(providers, VectorSnippets.class, "vectorConsumerUnrolled");
            this.vectorConsumerEmpty = snippet(providers, VectorSnippets.class, "vectorConsumerEmpty");
        }

        @SuppressWarnings("try")
        public int lower(CoreProviders context, LowerableVectorConsumer consumer) {

            ValueNode length = consumer.getLength();
            IntegerStamp lengthStamp = (IntegerStamp) length.stamp(NodeView.DEFAULT);
            int upperBound = getRoughBound(lengthStamp, lengthStamp.upperBound());
            int lowerBound = getRoughBound(lengthStamp, lengthStamp.lowerBound());
            assert upperBound >= lowerBound : upperBound + " vs " + lowerBound;
            assert lengthStamp.getBits() == 64 : lengthStamp.getBits();

            StructuredGraph graph = consumer.asNode().graph();
            OptionValues localOptions = graph.getOptions();
            int supportedStepLengthsMask = 1;
            if (arch != null) {
                // for best performance, we should extend the primary step length and do partial
                // vector unrolling, see GR-4857
                supportedStepLengthsMask = VectorPolicies.getSupportedLengthMask(consumer, upperBound, arch, target);
                assert (supportedStepLengthsMask & 1) == 1 : "a step length of 1 must always be supported";
            }

            int maxSupportedStepLength = getMaxSupportedStepLength(supportedStepLengthsMask);
            int stepLengthPrimary = maxSupportedStepLength * VectorIntrinsics.Options.VectorUnroll.getValue(localOptions);
            int stepLengthSecondary = 1;
            SnippetInfo info = getSnippet(consumer, lowerBound, upperBound, stepLengthPrimary, localOptions, consumer.allowUnrolling());
            Arguments args = new Arguments(info, graph, LoweringTool.StandardLoweringStage.LOW_TIER);
            args.add("consumer", consumer);
            if (!info.equals(vectorConsumerEmpty)) {
                args.add("supportedStepLengthsMask", supportedStepLengthsMask);
                if (!info.equals(scalarConsumerLoop)) {
                    int maxMissingAlignment = computeMaxMissingAlignment(consumer, stepLengthPrimary);
                    int alignmentMode = computeAlignmentMode(consumer, info, maxSupportedStepLength, maxMissingAlignment, localOptions);
                    args.add("alignMode", alignmentMode);
                    args.add("stepLengthPrimary", stepLengthPrimary);
                    args.add("maxMissingPrimaryAlignment", alignmentMode == F_ALIGN_NONE ? 0 : maxMissingAlignment);
                    args.add("assertAlignment", Assertions.detailedAssertionsEnabled(graph.getOptions()));
                    if (info.equals(vectorConsumerLoop) || info.equals(vectorConsumerOnlyMainLoop)) {
                        int tailMode = computeTailMode(consumer);
                        int minPrimary = 1;
                        if (tailMode == F_REST_VECTOR_OVERLAPPING) {
                            minPrimary = stepLengthPrimary;
                        }
                        args.add("minPrimary", minPrimary);
                        args.add("stepLengthSecondary", stepLengthSecondary);
                        args.add("maxMissingSecondaryAlignment", stepLengthSecondary <= 1 || alignmentMode == F_ALIGN_NONE ? 0 : computeMaxMissingAlignment(consumer, stepLengthSecondary));
                        args.add("tailMode", computeTailMode(consumer));
                    } else if (info.equals(vectorConsumerUnrolled)) {
                        args.add("lowerBound", lowerBound);
                        args.add("upperBound", upperBound);
                        args.add("stepLengthShift", CodeUtil.log2(stepLengthPrimary));
                    }
                }
                args.add("vectorLength", length);
            }
            args.add("counters", counters);

            consumer.asNode().getDebug().log(DebugContext.VERBOSE_LEVEL, "lower %s at length %d (secondary: %d) with %s", consumer, stepLengthPrimary, stepLengthSecondary, info);

            try (DebugCloseable position = consumer.asNode().withNodeSourcePosition()) {
                SnippetTemplate template = template(context, consumer.asFixedNode(), args);

                FixedNode dummy = graph.add(new DummyNode(consumer.asNode().stamp(NodeView.DEFAULT)));
                graph.addAfterFixed(consumer.asFixedWithNextNode(), dummy);
                consumer.asNode().replaceAtUsages(dummy, InputType.Value);
                UnmodifiableEconomicMap<Node, Node> duplicates = template.instantiate(context.getMetaAccess(), dummy, VECTOR_REPLACER, args);

                if (info.equals(vectorConsumerLoop) || info.equals(vectorConsumerOnlyMainLoop) || info.equals(scalarConsumerLoop)) {
                    fixUpVectorLoopProbabilities(duplicates, consumer, stepLengthPrimary);
                }
            }

            return stepLengthPrimary;
        }

        private static int computeTailMode(LowerableVectorConsumer consumer) {
            boolean treatConsumerAsFold = consumer instanceof FoldVectorNode;
            if (consumer instanceof VectorLoopNode) {
                VectorLoopNode vectorLoop = (VectorLoopNode) consumer;
                if (vectorLoop.hasPostLoop()) {
                    return F_REST_NONE;
                }
                treatConsumerAsFold = CollectionsUtil.anyMatch(vectorLoop.getConsumers(), groupMember -> groupMember instanceof FoldVectorNode);
            }
            if (treatConsumerAsFold || !consumer.allowUnrolling()) {
                // any kind of tail unrolling should be avoided for FoldVectorNodes until GR-5196 is
                // resolved
                return F_REST_LOOP_LINEAR;
            } else if (consumer.isIdempotent()) {
                return F_REST_VECTOR_OVERLAPPING;
            } else if (consumer instanceof VectorWriteNode || consumer instanceof VectorLoopNode || consumer instanceof VectorGuardNode || consumer instanceof VectorSafepointNode ||
                            consumer instanceof VectorReachabilityFenceNode) {
                return F_REST_UNROLL_BINARY;
            } else {
                throw GraalError.shouldNotReachHere("Unknown vector consumer type: " + consumer.getClass()); // ExcludeFromJacocoGeneratedReport
            }
        }

        private static int getRoughBound(IntegerStamp lengthStamp, long bound) {
            // the exact bounds are only important when they are small enough - for all other cases,
            // we just need to make sure that we return reasonable values so that no
            // underflow/overflow happens
            if (lengthStamp.isEmpty()) {
                return 0;
            } else if (bound < 0) {
                return 0;
            } else if (bound <= Integer.MAX_VALUE) {
                return (int) bound;
            } else {
                return Integer.MAX_VALUE;
            }
        }

        /**
         * Ratio between cycles taken up by aligned vs. unaligned vector operations in the best
         * case, determined from benchmarks.
         */
        private static final double ALIGNED_OPERATIONS_COST_FACTOR = 0.8;

        /**
         * Determines whether producing a scalar alignment pre-loop would be beneficial for
         * performance, based on profiling data.
         * <p>
         * If we assume that:
         * <ul>
         * <li>Profiling has determined that the vectorized loop will process {@code n} elements on
         * average.</li>
         * <li>Scalar alignment iterations are {@code stepLength} times as expensive as vectorized
         * iterations.</li>
         * <li>At most {@code maxMissingAlignment} scalar iterations need to be performed.</li>
         * <li>Aligned vector operations are {@code costFactor} times as expensive as unaligned
         * ones.</li>
         * </ul>
         * We can approximate the cost of emitting an alignment pre-loop as follows:
         *
         * <pre>
         * stepLength * maxMissingAlignment + costFactor * (n - maxMissingAlignment)
         * </pre>
         *
         * Therefore, the decision for whether alignment is beneficial for performance is given by
         * the formula:
         *
         * <pre>
         *     stepLength * maxMissingAlignment + costFactor * (n - maxMissingAlignment) <= n
         * :: (stepLength - costFactor) * maxMissingAlignment <= (1 - costFactor) * n
         * </pre>
         *
         * This is a very approximate calculation, mainly meant to discourage alignment when there
         * are few elements to be processed, or many alignment iterations are required, e.g. with
         * small byte arrays.
         */
        private static boolean isScalarAlignmentBeneficial(LowerableVectorConsumer consumer, int stepLength, int maxMissingAlignment) {
            double iters = consumer.trustedBodyIterations();
            if (iters < 0.0) {
                return false;
            }
            return (stepLength - ALIGNED_OPERATIONS_COST_FACTOR) * maxMissingAlignment <= (1.0 - ALIGNED_OPERATIONS_COST_FACTOR) * iters;
        }

        private int computeAlignmentMode(LowerableVectorConsumer consumer, SnippetInfo consumerSnippet, int stepLength, int maxMissingAlignment, OptionValues localOptions) {
            if (stepLength <= 1 || !consumer.getSupportsAlignment() || !VectorIntrinsics.Options.VectorAlignment.getValue(localOptions)) {
                return F_ALIGN_NONE;
            }
            if (target.arch.supportsUnalignedMemoryAccess()) {
                if (consumerSnippet.equals(vectorConsumerUnrolled)) {
                    /*
                     * Do not align vector loops that we will fully unroll. Such loops have a
                     * relatively small number of iterations and few or no control flow checks.
                     * Alignment would add many checks and is not beneficial for small numbers of
                     * iterations.
                     */
                    return F_ALIGN_NONE;
                }
                if (consumer.isIdempotent()) {
                    return F_ALIGN_VECTOR_OVERLAPPING;
                }
                if (!isScalarAlignmentBeneficial(consumer, stepLength, maxMissingAlignment)) {
                    return F_ALIGN_NONE;
                }
            }
            if (maxMissingAlignment <= VectorIntrinsics.Options.MaxVectorAlignmentUnroll.getValue(localOptions)) {
                return F_ALIGN_UNROLL_LINEAR;
            }
            return F_ALIGN_LOOP_LINEAR;
        }

        private int computeMaxMissingAlignment(LowerableVectorConsumer consumer, int stepLength) {
            if (stepLength == 1) {
                return 0;
            } else if (consumer instanceof VectorWriteNode) {
                VectorWriteNode vectorWriteNode = (VectorWriteNode) consumer;
                int alignmentInElements = VectorPolicies.getAlignmentInElements(vectorWriteNode, arch);
                assert alignmentInElements >= 1 : alignmentInElements;
                return Math.max(stepLength - 1, alignmentInElements);
            } else if (consumer instanceof VectorLoopNode) {
                int maxMissingAlignment = 1;
                for (ValueNode consumerValue : ((VectorLoopNode) consumer).getConsumers()) {
                    maxMissingAlignment = Integer.max(maxMissingAlignment, computeMaxMissingAlignment((LowerableVectorConsumer) consumerValue, stepLength));
                }
                return maxMissingAlignment;
            } else if (consumer instanceof VectorSafepointNode || consumer instanceof VectorReachabilityFenceNode) {
                return 0;
            } else {
                assert !consumer.getSupportsAlignment() : "not supported vector consumer";
                return stepLength - 1;
            }
        }

        private SnippetInfo getSnippet(LowerableVectorConsumer consumer, int lowerBound, int upperBound, long maxStepLength, OptionValues localOptions, boolean allowUnrolling) {
            if (consumer instanceof VectorLoopNode && ((VectorLoopNode) consumer).hasPostLoop()) {
                /*
                 * If we have a vector loop with a post loop, we must only emit a main SIMD loop but
                 * no tail processing.
                 */
                return vectorConsumerOnlyMainLoop;
            }
            if (upperBound == 0) {
                return vectorConsumerEmpty;
            } else if (maxStepLength == 1) {
                return scalarConsumerLoop;
            } else {
                int conditionalElements = Math.max(0, upperBound - lowerBound);
                long unconditionalInstructions = lowerBound / maxStepLength + CodeUtil.log2(lowerBound % maxStepLength + 1);
                long conditionalInstructions = conditionalElements / maxStepLength + CodeUtil.log2(Math.min(conditionalElements, maxStepLength - 1) + 1);
                if (unconditionalInstructions + conditionalInstructions * 2 > VectorIntrinsics.Options.MaxVectorUnroll.getValue(localOptions) || !allowUnrolling) {
                    return vectorConsumerLoop;
                } else {
                    return vectorConsumerUnrolled;
                }
            }
        }

        /**
         * Check if the given {@code node} is the {@link IfNode} in a code pattern of the following
         * form:
         *
         * <pre>
         * if (!VectorHasNext(...)) {
         *     break;
         * }
         * </pre>
         *
         * This is the explicit form of the loop exit check used in {@link #scalarConsumerLoop} and
         * {@link #vectorConsumerLoop}:
         *
         * <pre>
         * do {
         *    ...
         * } while (VectorHasNextNode.hasNext(consumerProxy, iterator, stepLengthPrimary));
         * </pre>
         *
         * @return the {@code node} cast to {@link IfNode} if it is such a vector loop exit check,
         *         {@code null} for any other node
         */
        private static IfNode isConsumerLoopExitCheck(Node node) {
            if (node instanceof IfNode && ((IfNode) node).condition() instanceof VectorHasNextNode && ((IfNode) node).falseSuccessor() instanceof LoopExitNode) {
                return (IfNode) node;
            }
            return null;
        }

        /**
         * Fix up the loop entry probability and the default loop frequency from the
         * {@link #vectorConsumerLoop} snippet: If we have trusted information on the number of
         * iterations of the original loop, set the vector loop's frequency according to those
         * iterations and the vector length.
         */
        private static void fixUpVectorLoopProbabilities(UnmodifiableEconomicMap<Node, Node> duplicates, LowerableVectorConsumer consumer, int mainVectorLength) {
            double trustedBodyIterations = consumer.trustedBodyIterations();
            if (trustedBodyIterations < 0) {
                return;
            }
            UnmodifiableMapCursor<Node, Node> cursor = duplicates.getEntries();
            while (cursor.advance()) {
                Node replacement = cursor.getValue();
                IfNode ifNode = isConsumerLoopExitCheck(replacement);
                if (ifNode != null) {
                    VectorHasNextNode hasNext = (VectorHasNextNode) ifNode.condition();
                    LoopExitNode loopExit = (LoopExitNode) ifNode.falseSuccessor();
                    int loopVectorLength = hasNext.constantLength();

                    GraalError.guarantee(loopVectorLength == 1 || loopVectorLength == mainVectorLength, "loop must be scalar or match main vector length, got: %s / %s", loopVectorLength,
                                    mainVectorLength);
                    if (loopVectorLength == 1 && mainVectorLength > 1) {
                        // this is the scalar cleanup loop, keep the default frequency
                        continue;
                    }
                    /*
                     * We found the if node that controls the exit from the main (vector or scalar)
                     * loop. Set its exit frequency. Note that the main loop generated by the
                     * snippets is always inverted and has a separate entry guard, so if we enter
                     * it, we execute its body at least once.
                     */
                    double trustedVectorIterations = Math.max(Math.floor(trustedBodyIterations / mainVectorLength), 1.0);
                    LoopTransformations.adaptCountedLoopExitProbability(loopExit, trustedVectorIterations);
                    consumer.asNode().getDebug().log(DebugContext.VERBOSE_LEVEL, "%s: set %s iterations for main loop %s (trusted body iterations %s, main vector length %s)", consumer,
                                    trustedVectorIterations, loopExit.loopBegin(), trustedBodyIterations, mainVectorLength);

                    EndNode forwardEnd = loopExit.loopBegin().forwardEnd();
                    Node beforeLoop = forwardEnd.predecessor().predecessor();
                    if (beforeLoop instanceof IfNode && ((IfNode) beforeLoop).condition() instanceof VectorHasNextNode) {
                        IfNode loopEntryCheck = (IfNode) beforeLoop;
                        VectorHasNextNode loopEntryCondition = (VectorHasNextNode) loopEntryCheck.condition();
                        ValueNode consumerOrProxy = loopEntryCondition.getConsumerOrProxy();
                        ValueNode consumerOrProxy2 = hasNext.getConsumerOrProxy();
                        assert consumerOrProxy == consumerOrProxy2 : consumerOrProxy + " vs " + consumerOrProxy2;
                        int constantLength = loopEntryCondition.constantLength();
                        assert constantLength == loopVectorLength : "ConstantLength=" + constantLength + " vs loopVectorLength=" + loopVectorLength;
                        FixedNode next = loopEntryCheck.trueSuccessor().next();
                        assert next == forwardEnd : "Next node " + next + " should be fwd end " + forwardEnd;
                        /**
                         * We found the if node controlling the entry to the main loop, adjust its
                         * probability.
                         *
                         * The decision on this probability can be wrong if the trusted information
                         * is injected by the compiler for cases where no profiling information is
                         * available (for example, arraycopy) but the injected frequency doesn't
                         * match the application's actual behavior. For example:
                         *
                         * <pre>
                         * int opaqueLength = opaque(5); // some very small value, not a constant in the graph
                         * System.arraycopy(src, dest, opaqueLength); // uses injected loop frequency > opaqueLength
                         * </pre>
                         *
                         * Apart from such corner cases this decision doesn't make a difference to
                         * execution times, but the code layout is more natural if we consider it
                         * likely to enter the loop.
                         */
                        boolean expectToEnterLoop = trustedBodyIterations >= loopVectorLength;
                        loopEntryCheck.setProbability(loopEntryCheck.trueSuccessor(), expectToEnterLoop ? BranchProbabilityNode.LIKELY_PROFILE : BranchProbabilityNode.NOT_LIKELY_PROFILE);
                        consumer.asNode().getDebug().log(DebugContext.VERBOSE_LEVEL, "%s: set %s entry probability to %s", consumer, loopEntryCheck, loopEntryCheck.getTrueSuccessorProbability());
                    }
                }
            }
        }
    }

    static class Counters {
        final SnippetIntegerHistogram scalarVectorElements;
        final SnippetIntegerHistogram vectorConsumerLoopElements;
        final SnippetIntegerHistogram vectorConsumerOnlyMainLoopElements;
        final SnippetIntegerHistogram unrolledVectorElements;
        final SnippetCounter staticZeroVectorElements;

        final SnippetCounter enteredVectorConsumer;
        final SnippetCounter tailProcessingEntered;
        final SnippetCounter enteredPrimaryVectorConsumerLoop;
        final SnippetCounter enteredSecondaryVectorConsumerLoop;
        final SnippetCounter enteredPrimaryMainVectorConsumerLoop;
        final SnippetCounter enteredSecondaryMainVectorConsumerLoop;

        Counters(SnippetCounter.Group.Factory factory) {
            final SnippetCounter.Group vectorLength = factory.createSnippetCounterGroup("Vector length");
            final SnippetCounter.Group vectorConsumerLoop = factory.createSnippetCounterGroup("Vector consumer loop");

            scalarVectorElements = new SnippetIntegerHistogram(vectorLength, 2, "scalar loop", "vector length observed in scalar loops");
            vectorConsumerLoopElements = new SnippetIntegerHistogram(vectorLength, 2, "vector loop", "vector length observed in the vector consumer loops");
            vectorConsumerOnlyMainLoopElements = new SnippetIntegerHistogram(vectorLength, 2, "vector loop", "vector length observed in the vector consumer main-only loops");
            unrolledVectorElements = new SnippetIntegerHistogram(vectorLength, 2, "unrolled vector loop", "vector length observed in unrolled vector loop");
            staticZeroVectorElements = new SnippetCounter(vectorLength, "static 0-length", "statically known zero length vector");

            enteredVectorConsumer = new SnippetCounter(vectorConsumerLoop, "entered vector consumer", "how often we enter the vectorized code snippet");
            tailProcessingEntered = new SnippetCounter(vectorConsumerLoop, "entered tail processing", "how often we entered the tail processing code");
            enteredPrimaryVectorConsumerLoop = new SnippetCounter(vectorConsumerLoop, "entered primary loop", "how often we entered the loop with the primary step length");
            enteredSecondaryVectorConsumerLoop = new SnippetCounter(vectorConsumerLoop, "entered secondary loop", "how often we entered the loop with the secondary step length");
            enteredPrimaryMainVectorConsumerLoop = new SnippetCounter(vectorConsumerLoop, "entered primary main loop", "how often we entered the main-only loop with the primary step length");
            enteredSecondaryMainVectorConsumerLoop = new SnippetCounter(vectorConsumerLoop, "entered secondary main loop", "how often we entered the main-only loop with the secondary step length");
        }
    }
}
