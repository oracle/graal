/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.model;

import static com.oracle.truffle.dsl.processor.ProcessorContext.types;
import static com.oracle.truffle.dsl.processor.java.ElementUtils.typeEquals;

import java.util.Arrays;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;

public final class InlineFieldData {

    final Element sourceElement;
    final String name;
    final Integer bits;
    final TypeMirror type;
    final TypeMirror fieldType;
    final int dimensions;

    public InlineFieldData(Element sourceElement, String name, TypeMirror fieldType, Integer bits, TypeMirror type, int dimensions) {
        this.sourceElement = sourceElement;
        this.name = name;
        this.fieldType = fieldType;
        this.bits = bits;
        this.dimensions = dimensions;
        if (isPrimitive()) {
            this.type = resolvePrimitiveValueType(fieldType);
        } else {
            this.type = type;
        }
    }

    public static TypeMirror resolvePrimitiveFieldType(TypeMirror valueType) {
        ProcessorContext context = ProcessorContext.getInstance();
        if (typeEquals(valueType, context.getType(boolean.class))) {
            return types().InlineSupport_BooleanField;
        } else if (typeEquals(valueType, context.getType(byte.class))) {
            return types().InlineSupport_ByteField;
        } else if (typeEquals(valueType, context.getType(short.class))) {
            return types().InlineSupport_ShortField;
        } else if (typeEquals(valueType, context.getType(char.class))) {
            return types().InlineSupport_CharField;
        } else if (typeEquals(valueType, context.getType(int.class))) {
            return types().InlineSupport_IntField;
        } else if (typeEquals(valueType, context.getType(float.class))) {
            return types().InlineSupport_FloatField;
        } else if (typeEquals(valueType, context.getType(long.class))) {
            return types().InlineSupport_LongField;
        } else if (typeEquals(valueType, context.getType(double.class))) {
            return types().InlineSupport_DoubleField;
        } else {
            throw new AssertionError("Invalid primitive field type.");
        }
    }

    private static TypeMirror resolvePrimitiveValueType(TypeMirror fieldType) {
        ProcessorContext context = ProcessorContext.getInstance();
        if (typeEquals(fieldType, types().InlineSupport_BooleanField)) {
            return context.getType(boolean.class);
        } else if (typeEquals(fieldType, types().InlineSupport_ByteField)) {
            return context.getType(byte.class);
        } else if (typeEquals(fieldType, types().InlineSupport_ShortField)) {
            return context.getType(short.class);
        } else if (typeEquals(fieldType, types().InlineSupport_CharField)) {
            return context.getType(char.class);
        } else if (typeEquals(fieldType, types().InlineSupport_IntField)) {
            return context.getType(int.class);
        } else if (typeEquals(fieldType, types().InlineSupport_FloatField)) {
            return context.getType(float.class);
        } else if (typeEquals(fieldType, types().InlineSupport_LongField)) {
            return context.getType(long.class);
        } else if (typeEquals(fieldType, types().InlineSupport_DoubleField)) {
            return context.getType(double.class);
        } else {
            throw new AssertionError("Invalid primitive field type.");
        }
    }

    public int getDimensions() {
        return dimensions;
    }

    public String getName() {
        return name;
    }

    public Element getSourceElement() {
        return sourceElement;
    }

    public TypeMirror getFieldType() {
        if (isReference()) {
            return new CodeTypeMirror.DeclaredCodeTypeMirror(ElementUtils.castTypeElement(fieldType), Arrays.asList(getType()));
        }
        return fieldType;
    }

    public boolean isState() {
        TruffleTypes types = ProcessorContext.getInstance().getTypes();
        return typeEquals(fieldType, types.InlineSupport_StateField);
    }

    public boolean isPrimitive() {
        return !isState() && !isReference();
    }

    public boolean isReference() {
        TruffleTypes types = ProcessorContext.getInstance().getTypes();
        return typeEquals(fieldType, types.InlineSupport_ReferenceField);
    }

    public boolean hasBits() {
        return bits != null;
    }

    public int getBits() {
        return bits;
    }

    public TypeMirror getType() {
        return type;
    }

    public InlineFieldData copy() {
        return new InlineFieldData(sourceElement, name, fieldType, bits, type, dimensions);
    }

    public boolean isCompatibleWith(InlineFieldData declared) {
        if (!ElementUtils.typeEquals(fieldType, declared.fieldType)) {
            return false;
        }
        if (!ElementUtils.typeEquals(type, declared.type)) {
            return false;
        }
        if (this.dimensions != declared.dimensions) {
            return false;
        }
        if (declared.bits == null ^ bits == null) {
            return false;
        }
        if (declared.bits != null) {
            if (declared.bits < bits) {
                return false;
            }
        }
        return true;
    }

}
