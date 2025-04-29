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

package org.graalvm.wasm.debugging.data.types;

import org.graalvm.wasm.debugging.DebugLocation;
import org.graalvm.wasm.debugging.data.DebugContext;
import org.graalvm.wasm.debugging.data.DebugObject;
import org.graalvm.wasm.debugging.data.DebugType;

import com.oracle.truffle.api.interop.InteropLibrary;

/**
 * Represents a debug type that represents a type definition.
 */
public class DebugTypeDef extends DebugType {
    private final String name;
    private final DebugType type;

    public DebugTypeDef(String name, DebugType type) {
        this.name = name;
        this.type = type;
    }

    @Override
    public String asTypeName() {
        return name;
    }

    @Override
    public int valueLength() {
        if (type == null) {
            return 0;
        }
        return type.valueLength();
    }

    @Override
    public boolean isValue() {
        if (type == null) {
            return super.isValue();
        }
        return type.isValue();
    }

    @Override
    public Object asValue(DebugContext context, DebugLocation location) {
        if (type == null) {
            return super.asValue(context, location);
        }
        return type.asValue(context, location);
    }

    @Override
    public boolean isModifiableValue() {
        if (type == null) {
            return super.isModifiableValue();
        }
        return type.isModifiableValue();
    }

    @Override
    public void setValue(DebugContext context, DebugLocation location, Object value, InteropLibrary lib) {
        if (type == null) {
            super.setValue(context, location, value, lib);
        } else {
            type.setValue(context, location, value, lib);
        }
    }

    @Override
    public boolean isDebugObject() {
        if (type == null) {
            return super.isDebugObject();
        }
        return type.isDebugObject();
    }

    @Override
    public DebugObject asDebugObject(DebugContext context, DebugLocation location) {
        if (type == null) {
            return super.asDebugObject(context, location);
        }
        return type.asDebugObject(context, location);
    }

    @Override
    public boolean isLocation() {
        if (type == null) {
            return super.isLocation();
        }
        return type.isLocation();
    }

    @Override
    public DebugLocation asLocation(DebugContext context, DebugLocation location) {
        if (type == null) {
            return super.asLocation(context, location);
        }
        return type.asLocation(context, location);
    }

    @Override
    public boolean fitsIntoInt() {
        if (type == null) {
            return super.fitsIntoInt();
        }
        return type.fitsIntoInt();
    }

    @Override
    public int asInt(DebugContext context, DebugLocation location) {
        if (type == null) {
            return super.asInt(context, location);
        }
        return type.asInt(context, location);
    }

    @Override
    public boolean fitsIntoLong() {
        if (type == null) {
            return super.fitsIntoLong();
        }
        return type.fitsIntoLong();
    }

    @Override
    public long asLong(DebugContext context, DebugLocation location) {
        if (type == null) {
            return super.asLong(context, location);
        }
        return type.asLong(context, location);
    }

    @Override
    public boolean hasMembers() {
        if (type == null) {
            return super.hasMembers();
        }
        return type.hasMembers();
    }

    @Override
    public int memberCount() {
        if (type == null) {
            return super.memberCount();
        }
        return type.memberCount();
    }

    @Override
    public DebugObject readMember(DebugContext context, DebugLocation location, int index) {
        if (type == null) {
            return super.readMember(context, location, index);
        }
        return type.readMember(context, location, index);
    }

    @Override
    public boolean hasArrayElements() {
        if (type == null) {
            return super.hasArrayElements();
        }
        return type.hasArrayElements();
    }

    @Override
    public int arrayDimensionCount() {
        if (type == null) {
            return super.arrayDimensionCount();
        }
        return type.arrayDimensionCount();
    }

    @Override
    public int arrayDimensionSize(int dimension) {
        if (type == null) {
            return super.arrayDimensionSize(dimension);
        }
        return type.arrayDimensionSize(dimension);
    }

    @Override
    public DebugObject readArrayElement(DebugContext context, DebugLocation location, int index) {
        if (type == null) {
            return super.readArrayElement(context, location, index);
        }
        return type.readArrayElement(context, location, index);
    }
}
