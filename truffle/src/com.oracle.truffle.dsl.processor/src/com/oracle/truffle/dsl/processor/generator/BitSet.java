/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.generator;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.createReferenceName;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.getSimpleName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.FrameState;
import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.LocalVariable;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.parser.SpecializationGroup.TypeGuard;

class BitSet {

    private static final Object[] EMPTY_OBJECTS = new Object[0];

    private final int capacity;
    private final String name;
    private final Map<Object, Integer> offsets = new HashMap<>();
    private final Object[] objects;
    private final long allMask;
    private final TypeMirror type;

    BitSet(String name, Object[] objects) {
        this.name = name;
        this.objects = objects;
        this.capacity = intializeCapacity();
        if (capacity <= 32) {
            type = ProcessorContext.getInstance().getType(int.class);
        } else if (capacity <= 64) {
            type = ProcessorContext.getInstance().getType(long.class);
        } else {
            throw new UnsupportedOperationException("State space too big " + capacity + ". Only <= 64 supported.");
        }
        this.allMask = createMask(objects);
    }

    private int intializeCapacity() {
        if (objects.length == 0) {
            return 0;
        }
        int bitIndex = 0;
        for (Object o : objects) {
            int size = calculateRequiredBits(o);
            offsets.put(o, bitIndex);
            bitIndex += size;
        }
        return bitIndex;
    }

    public Object[] getObjects() {
        return objects;
    }

    protected int calculateRequiredBits(@SuppressWarnings("unused") Object object) {
        return 1;
    }

    public int getCapacity() {
        return capacity;
    }

    public TypeMirror getType() {
        return type;
    }

    public final boolean contains(Object element) {
        return offsets.containsKey(element);
    }

    private CodeTree createLocalReference(FrameState frameState) {
        LocalVariable var = frameState != null ? frameState.get(getName()) : null;
        if (var != null) {
            return var.createReference();
        } else {
            return null;
        }
    }

    public CodeTree createReference(FrameState frameState) {
        CodeTree ref = createLocalReference(frameState);
        if (ref == null) {
            ref = CodeTreeBuilder.createBuilder().string("this.", getName(), "_").build();
        }
        return ref;
    }

    /**
     * Filters passed elements to return only elements contained in this set.
     */
    public final Object[] filter(Object[] elements) {
        if (elements == null || elements.length == 0) {
            return elements;
        }
        List<Object> includedElements = null;
        for (int i = 0; i < elements.length; i++) {
            if (contains(elements[i])) {
                if (includedElements == null) {
                    includedElements = new ArrayList<>();
                }
                includedElements.add(elements[i]);
            }
        }
        if (includedElements == null || includedElements.isEmpty()) {
            return EMPTY_OBJECTS;
        } else if (includedElements.size() == elements.length) {
            return elements;
        } else {
            return includedElements.toArray();
        }
    }

    public CodeTree createLoad(FrameState frameState) {
        if (frameState.get(name) != null) {
            // already loaded
            return CodeTreeBuilder.singleString("");
        }
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        String fieldName = name + "_";
        LocalVariable var = new LocalVariable(type, name, null);
        CodeTreeBuilder init = builder.create();
        init.tree(CodeTreeBuilder.singleString(fieldName));
        builder.tree(var.createDeclaration(init.build()));
        frameState.set(name, var);
        return builder.build();
    }

    public CodeTree createContainsOnly(FrameState frameState, int offset, int length, Object[] selectedElements, Object[] allElements) {
        long mask = ~createMask(offset, length, selectedElements) & createMask(allElements);
        if (mask == 0) {
            return null;
        }
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.tree(createMaskedReference(frameState, mask));
        builder.string(" == 0");
        builder.string(" /* only-active ", toString(selectedElements, " && "), " */");
        return builder.build();
    }

    public CodeTree createContainsAny(FrameState frameState, Object[] selectedElements, Object[] allElements) {
        long mask = createMask(allElements);
        if (mask == 0) {
            return null;
        }
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.tree(createMaskedReference(frameState, mask));
        builder.string(" != 0");
        builder.string(" /* contains-any ", toString(selectedElements, " && "), " */");
        return builder.build();
    }

    public CodeTree createIs(FrameState frameState, Object[] selectedElements, Object[] maskedElements) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.tree(createMaskedReference(frameState, maskedElements));
        builder.string(" == ").string(formatMask(createMask(selectedElements)));
        return builder.build();
    }

    private CodeTree createMaskedReference(FrameState frameState, long maskedElements) {
        if (maskedElements == this.allMask) {
            // no masking needed
            return createReference(frameState);
        } else {
            CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
            // masking needed we use the state bitset for guards as well
            builder.string("(").tree(createReference(frameState)).string(" & ").string(formatMask(maskedElements)).string(")");
            return builder.build();
        }
    }

    public CodeTree createMaskedReference(FrameState frameState, Object[] maskedElements) {
        return createMaskedReference(frameState, createMask(maskedElements));
    }

    public CodeTree createIsNotAny(FrameState frameState, Object[] elements) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.tree(createMaskedReference(frameState, elements));
        builder.string(" != 0 ");
        builder.string(" /* is-not ", toString(elements, " && "), " */");
        return builder.build();
    }

    public String formatMask(long mask) {
        if (mask == 0) {
            return "0";
        }
        int bitsUsed = 64 - Long.numberOfLeadingZeros(mask);
        if (bitsUsed <= 16) {
            return "0b" + Integer.toBinaryString((int) mask);
        } else {
            if (capacity <= 32) {
                return "0x" + Integer.toHexString((int) mask);
            } else {
                return "0x" + Long.toHexString(mask) + "L";
            }
        }
    }

    public CodeTree createIsOneBitOf(FrameState frameState, Object[] elements) {
        CodeTree masked = createMaskedReference(frameState, elements);
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();

        // use the calculation of power of two
        // (state & (state - 1L)) == 0L
        builder.startParantheses().tree(masked).string(" & ").startParantheses().tree(masked).string(" - 1").end().end().string(" == 0");

        builder.string(" /* ", label("is-single"), " */");
        return builder.build();
    }

    public CodeTree createContains(FrameState frameState, Object[] elements) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.tree(createMaskedReference(frameState, elements));
        builder.string(" != 0");
        builder.string(" /* ", label("is"), toString(elements, " || "), " */");
        return builder.build();
    }

    private static String toString(Object[] elements, String elementSep) {
        StringBuilder b = new StringBuilder();
        String sep = "";
        for (int i = 0; i < elements.length; i++) {
            b.append(sep).append(toString(elements[i]));
            sep = elementSep;
        }
        return b.toString();
    }

    private static String toString(Object element) {
        if (element instanceof SpecializationData) {
            SpecializationData specialization = (SpecializationData) element;
            if (specialization.isUninitialized()) {
                return "uninitialized";
            }
            return createReferenceName(specialization.getMethod());
        } else if (element instanceof TypeGuard) {
            int index = ((TypeGuard) element).getSignatureIndex();
            String simpleName = getSimpleName(((TypeGuard) element).getType());
            return index + ":" + simpleName;
        }
        return element.toString();
    }

    public CodeTree createNotContains(FrameState frameState, Object[] elements) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.startParantheses();
        builder.tree(createMaskedReference(frameState, elements));
        builder.end();
        builder.string(" == 0");
        builder.string(" /* ", label("is-not"), toString(elements, " && "), " */");
        return builder.build();
    }

    private String label(String message) {
        return message + "-" + getName() + " ";
    }

    public String getName() {
        return name;
    }

    public CodeTree createExtractInteger(FrameState frameState, Object element) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        if (capacity > 32) {
            builder.string("(int)(");
        }

        builder.tree(createMaskedReference(frameState, createMask(element)));
        builder.string(" >>> ", Integer.toString(getStateOffset(element)));
        if (capacity > 32) {
            builder.string(")");
        }
        builder.string(" /* ", label("extract-implicit"), toString(element), " */");
        return builder.build();
    }

    public CodeTree createSet(FrameState frameState, Object[] elements, boolean value, boolean persist) {
        CodeTreeBuilder valueBuilder = CodeTreeBuilder.createBuilder();
        boolean hasLocal = createLocalReference(frameState) != null;

        if (!hasLocal && elements.length == 0) {
            return valueBuilder.build();
        }
        valueBuilder.tree(createReference(frameState));
        if (elements.length > 0) {
            if (value) {
                valueBuilder.string(" | ");
                valueBuilder.string(formatMask(createMask(elements)));
                valueBuilder.string(" /* ", label("add"), toString(elements, ", "), " */");
            } else {
                valueBuilder.string(" & ");
                valueBuilder.string(formatMask(~createMask(elements)));
                valueBuilder.string(" /* ", label("remove"), toString(elements, ", "), " */");
            }
        }

        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.startStatement();
        if (persist) {
            builder.string("this.", name, "_ = ");

            // if there is a local variable we need to update it as well
            CodeTree localReference = createLocalReference(frameState);
            if (localReference != null && elements.length > 0) {
                builder.tree(localReference).string(" = ");
            }
        } else {
            builder.tree(createReference(frameState)).string(" = ");
        }
        builder.tree(valueBuilder.build());
        builder.end(); // statement

        return builder.build();
    }

    public CodeTree createSetInteger(FrameState frameState, Object element, CodeTree value) {
        int offset = getStateOffset(element);
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.startStatement();
        builder.tree(createReference(frameState)).string(" = ");
        builder.startParantheses();
        builder.tree(createReference(frameState));
        builder.string(" | (");
        if (capacity > 32) {
            builder.string("(long) ");
        }
        builder.tree(value).string(" << ", Integer.toString(offset), ")");
        builder.string(" /* ", label("set-implicit"), toString(element), " */");
        builder.end();
        builder.end();
        return builder.build();
    }

    private long createMask(Object e) {
        return createMask(new Object[]{e});
    }

    public long createMask(Object[] e) {
        return createMask(0, -1, e);
    }

    private long createMask(int offset, int length, Object[] e) {
        long mask = 0;
        for (Object element : e) {
            if (!offsets.containsKey(element)) {
                continue;
            }
            int stateOffset = getStateOffset(element);
            int stateLength = calculateRequiredBits(element);
            int realLength = length < 0 ? stateLength : Math.min(stateLength, offset + length);
            for (int i = offset; i < realLength; i++) {
                mask |= 1L << (stateOffset + i);
            }
        }
        return mask;
    }

    private int getStateOffset(Object object) {
        Integer value = offsets.get(object);
        if (value == null) {
            return 0;
        }
        return value;
    }

}
