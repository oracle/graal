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

package org.graalvm.wasm.debugging.languages.rust;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.wasm.debugging.data.DebugObject;
import org.graalvm.wasm.debugging.data.DebugObjectFactory;
import org.graalvm.wasm.debugging.data.DebugType;

/**
 * Represents a factory that creates the internal representation of debug values for the Rust
 * programming language.
 */
public class RustObjectFactory extends DebugObjectFactory {

    @Override
    protected DebugType createStructType(String name, DebugObject[] members, DebugType[] superTypes) {
        if (RustConstants.STRING_TYPE.equals(name)) {
            return new RustString(name, members);
        }
        return super.createStructType(name, members, superTypes);
    }

    @Override
    protected DebugType createArrayType(String name, DebugType elementType, int[] dimensionLengths) {
        return new RustArrayType(name, elementType, dimensionLengths);
    }

    @Override
    protected DebugType createSubroutineType(String name, DebugType returnType, DebugType[] parameterTypes) {
        return new RustFunctionType(name, returnType, parameterTypes);
    }

    @Override
    protected DebugType createEnumType(String name, DebugType baseType, EconomicMap<Long, String> values) {
        final EconomicMap<Long, String> updatedValues = EconomicMap.create(values.size());
        final MapCursor<Long, String> cursor = values.getEntries();
        while (cursor.advance()) {
            updatedValues.put(cursor.getKey(), name + "::" + cursor.getValue());
        }
        return super.createEnumType(name, baseType, updatedValues);
    }

    @Override
    protected DebugType createPointerType(DebugType baseType) {
        return new RustPointer(baseType);
    }

    @Override
    public String languageName() {
        return "rust";
    }

    @Override
    protected String namespaceSeparator() {
        return "::";
    }
}
