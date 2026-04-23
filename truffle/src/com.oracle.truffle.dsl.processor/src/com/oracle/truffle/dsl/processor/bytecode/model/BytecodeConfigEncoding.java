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
package com.oracle.truffle.dsl.processor.bytecode.model;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.getQualifiedName;

import java.util.IdentityHashMap;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;

import com.oracle.truffle.dsl.processor.java.ElementUtils;

/**
 * Represents the encoding of a bytecode config into a long. A config has the following encoding:
 *
 * <pre>
 * [padding/internal metadata][tags][instrumentations][2 source bits]
 * </pre>
 *
 * When tracing is enabled, the least significant bit of instrumentations is used to encode the
 * tracing bit.
 */
public record BytecodeConfigEncoding(int numTags, IdentityHashMap<OperationModel, Integer> instrumentationToIndex, boolean trackSourceContent, boolean tracingEnabled, boolean hasYieldOperation) {

    // NOTE: These should be kept in sync with BytecodeConfig.java
    private static final long SOURCE_MASK = 0b1;
    private static final long SOURCE_CONTENT_MASK = 0b10;
    private static final int NUM_SOURCE_BITS = 2;

    // We limit the number of tags/instrumentations so that tag and instrumentation states can be
    // encoded in an int. When enabled, we stuff a tracing bit in the instrumentation int.
    private static final int MAX_TAGS = 32;
    private static final int MAX_INSTRUMENTATIONS = 31;
    // We limit the total number of tags and instrumentations so that tag and instrumentation
    // states, in addition to bits for other (possibly future) features, can fit in a long.
    private static final int MAX_TAGS_AND_INSTRUMENTATIONS = 50;

    public static BytecodeConfigEncoding fromModel(BytecodeDSLModel model, AnnotationMirror generateBytecodeMirror) {
        List<CustomOperationModel> instrumentations = model.getInstrumentations();
        int numInstrumentations = instrumentations.size();
        int numTags = model.getProvidedTags().size();

        if (numInstrumentations > MAX_INSTRUMENTATIONS) {
            model.addError("Too many @Instrumentation annotated operations specified. The number of instrumentations is %d. The maximum number of instrumentations is %d.", numInstrumentations,
                            MAX_INSTRUMENTATIONS);
            return null;
        } else if (numTags > MAX_TAGS) {
            AnnotationValue taginstrumentationValue = ElementUtils.getAnnotationValue(generateBytecodeMirror, "enableTagInstrumentation");
            model.addError(generateBytecodeMirror, taginstrumentationValue,
                            "Tag instrumentation is currently limited to a maximum of %d tags. The language '%s' provides %s tags. Reduce the number of tags to resolve this.", MAX_TAGS,
                            getQualifiedName(model.languageClass), numTags);
            return null;
        } else if (numInstrumentations + numTags > MAX_TAGS_AND_INSTRUMENTATIONS) {
            model.addError("Too many @Instrumentation annotated operations and provided tags specified. The number of instrumentations is %d and provided tags is %s. " +
                            "The maximum number of instrumentations and provided tags is %d.", numInstrumentations, numTags, MAX_TAGS_AND_INSTRUMENTATIONS);
            return null;
        }

        int instrumentationIndex = 0;
        if (model.enableInstructionTracing) {
            // Reserve LSB for tracing.
            instrumentationIndex++;
        }
        IdentityHashMap<OperationModel, Integer> instrumentationToIndex = new IdentityHashMap<>();
        for (CustomOperationModel instrumentation : instrumentations) {
            instrumentationToIndex.put(instrumentation.operation, instrumentationIndex++);
        }
        if (instrumentationIndex > 32) {
            throw new AssertionError();
        }

        return new BytecodeConfigEncoding(numTags, instrumentationToIndex, model.sourceContentSupplier != null, model.enableInstructionTracing, model.hasYieldOperation());
    }

    public int numInstrumentations() {
        return instrumentationToIndex.size();
    }

    public int numInstrumentationBits() {
        return numInstrumentations() + (tracingEnabled ? 1 : 0);
    }

    /**
     * A bitmask representing the source information bit.
     * <p>
     * If {@code config & sourceMask() != 0}, source information is enabled.
     */
    @SuppressWarnings("static-method")
    public long sourceMask() {
        return SOURCE_MASK;
    }

    /**
     * A bitmask representing the source content bit. Returns 0 if source content is not tracked.
     * <p>
     * If {@code config & sourceContentMask() != 0}, source content is enabled.
     */
    @SuppressWarnings("static-method")
    public long sourceContentMask() {
        if (trackSourceContent) {
            return SOURCE_CONTENT_MASK;
        } else {
            return 0L;
        }
    }

    /**
     * A bitmask representing instrumentations.
     * <p>
     * {@code (config >> instrumentationShift()) & instrumentationMask()} computes a bit sequence
     * representing the enabled state of instrumentations.
     */
    public long instrumentationMask() {
        return (1L << numInstrumentationBits()) - 1;
    }

    /**
     * A bitmask for the given instrumentation.
     * <p>
     * If {@code ((config >> instrumentationShift()) & instrumentationMask(instr)) != 0}, the given
     * instrumentation is enabled.
     */
    public int instrumentationMask(OperationModel instrumentation) {
        Integer index = instrumentationToIndex.get(instrumentation);
        if (index == null) {
            throw new AssertionError("No instrumentation index allocated for %s".formatted(instrumentation));
        }
        return (1 << index);
    }

    /**
     * @see #instrumentationMask()
     */
    @SuppressWarnings("static-method")
    public int instrumentationShift() {
        return NUM_SOURCE_BITS;
    }

    /**
     * A bitmask representing the trace instruction state in an instrumentations int.
     * <p>
     * If {@code (config >> instrumentationShift()) & traceInstructionMask() != 0}, tracing is
     * enabled.
     */
    public int traceInstructionMask() {
        if (!tracingEnabled) {
            throw new IllegalStateException("Tracing is not enabled.");
        }
        return 0b1;
    }

    /**
     * A bitmask representing tags.
     * <p>
     * {@code (config >> tagShift()) & tagMask()} computes a bit sequence representing the enabled
     * state of tags.
     */
    public long tagMask() {
        return (1L << numTags) - 1;
    }

    /**
     * A bitmask for the given tag.
     * <p>
     * If {@code ((config >> tagShift()) & tagMask(tagIndex)) != 0}, the given tag is enabled.
     */
    public int tagMask(int tagIndex) {
        if (tagIndex < 0 || tagIndex >= numTags) {
            throw new IllegalArgumentException("Invalid tag index %d".formatted(tagIndex));
        }
        return (1 << tagIndex);
    }

    /**
     * @see #tagMask()
     */
    public int tagShift() {
        return NUM_SOURCE_BITS + numInstrumentationBits();
    }

    /**
     * A bitmask representing whether the interpreter has yields.
     * <p>
     * If {@code config & hasYieldsMask() != 0}, the node contains yields.
     */
    public long hasYieldsMask() {
        if (!hasYieldOperation) {
            return 0L;
        }
        return 1L << hasYieldsShift();
    }

    /**
     * @see #hasYieldsMask()
     */
    public int hasYieldsShift() {
        return tagShift() + numTags;
    }

    /**
     * A bitmask for all source bits used by this encoding.
     */
    public long sourceBitsMask() {
        return sourceMask() | sourceContentMask();
    }

    /**
     * A bitmask for all public {@link com.oracle.truffle.api.bytecode.BytecodeConfig} bits used by
     * this encoding.
     */
    public long completeBitsMask() {
        return sourceBitsMask() | (instrumentationMask() << instrumentationShift()) | (tagMask() << tagShift());
    }

    /**
     * A bitmask for all bits used by a bytecode node encoding, including internal metadata bits.
     */
    public long nodeBitsMask() {
        return completeBitsMask() | hasYieldsMask();
    }

    /**
     * A human-readable representation of the bits in the encoding.
     */
    public String description() {
        StringBuilder sb = new StringBuilder();
        int numPaddingBits = 64 - (hasYieldsShift() + (hasYieldOperation ? 1 : 0));
        if (numPaddingBits > 0) {
            sb.append("[unused: ").append(numPaddingBits).append("]");
        }
        if (hasYieldOperation) {
            sb.append("[has yields: 1]");
        }
        if (numTags > 0) {
            sb.append("[tags: ").append(numTags).append("]");
        }
        if (numInstrumentations() > 0) {
            sb.append("[instrumentations: ").append(numInstrumentations()).append("]");
        }
        if (tracingEnabled) {
            sb.append("[tracing: 1]");
        }
        if (trackSourceContent) {
            sb.append("[source content: 1]");
        } else {
            sb.append("[unused: 1]");
        }
        sb.append("[source info: 1]");
        return sb.toString();
    }
}
