/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.collections.EconomicMap;
import org.graalvm.wasm.debugging.DebugLocation;
import org.graalvm.wasm.debugging.representation.DebugConstantDisplayValue;
import org.graalvm.wasm.debugging.data.DebugContext;
import org.graalvm.wasm.debugging.data.DebugType;

/**
 * Represents a debug type that is an enum with a base type as the discriminator.
 */
public class DebugEnumType extends DebugType {
    private final String typeName;
    private final DebugType baseType;
    private final EconomicMap<Long, String> values;

    public DebugEnumType(String typeName, DebugType baseType, EconomicMap<Long, String> values) {
        assert baseType != null : "the base type (underlying representation) of an enum type must not be null";
        assert values != null : "the possible values of an enum type must not be null";
        this.typeName = typeName;
        this.baseType = baseType;
        this.values = values;
    }

    @Override
    public String asTypeName() {
        return typeName;
    }

    @Override
    public int valueLength() {
        return baseType.valueLength();
    }

    @Override
    public boolean isValue() {
        return true;
    }

    @Override
    public Object asValue(DebugContext context, DebugLocation location) {
        if (!baseType.fitsIntoLong()) {
            return DebugConstantDisplayValue.UNSUPPORTED;
        }
        final long index = baseType.asLong(context, location);
        if (values.containsKey(index)) {
            return new DebugConstantDisplayValue(values.get(index));
        }
        return new DebugConstantDisplayValue("undefined(" + index + ")");
    }
}
