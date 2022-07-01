/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.operations.instructions;

import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.ProcessorContext;

public enum FrameKind {
    // order must be the same as FrameSlotKind
    OBJECT("Object", "Object"),
    LONG("long", "Long"),
    INT("int", "Int", "Integer"),
    DOUBLE("double", "Double"),
    FLOAT("float", "Float"),
    BOOLEAN("boolean", "Boolean"),
    BYTE("byte", "Byte");

    private final String typeName;
    private final String frameName;
    private final String typeNameBoxed;

    FrameKind(String typeName, String frameName) {
        this(typeName, frameName, frameName);
    }

    FrameKind(String typeName, String frameName, String typeNameBoxed) {
        this.typeName = typeName;
        this.frameName = frameName;
        this.typeNameBoxed = typeNameBoxed;
    }

    public boolean isSingleByte() {
        return this == BOOLEAN || this == BYTE;
    }

    public boolean isBoxed() {
        return this == OBJECT;
    }

    public String getFrameName() {
        return frameName;
    }

    public String getTypeName() {
        return typeName;
    }

    public String getTypeNameBoxed() {
        return typeNameBoxed;
    }

    public TypeMirror getType() {
        ProcessorContext context = ProcessorContext.getInstance();
        switch (this) {
            case BOOLEAN:
                return context.getType(boolean.class);
            case BYTE:
                return context.getType(byte.class);
            case DOUBLE:
                return context.getType(double.class);
            case FLOAT:
                return context.getType(float.class);
            case INT:
                return context.getType(int.class);
            case LONG:
                return context.getType(long.class);
            case OBJECT:
                return context.getType(Object.class);
            default:
                throw new UnsupportedOperationException();

        }
    }

    public static FrameKind valueOfPrimitive(TypeKind typeKind) {
        switch (typeKind) {
            case BOOLEAN:
                return BOOLEAN;
            case BYTE:
                return BYTE;
            case DOUBLE:
                return DOUBLE;
            case FLOAT:
                return FLOAT;
            case INT:
                return INT;
            case LONG:
                return LONG;
            default:
                throw new UnsupportedOperationException();
        }
    }
}
