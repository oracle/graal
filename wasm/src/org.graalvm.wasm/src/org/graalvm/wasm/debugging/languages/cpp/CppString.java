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

package org.graalvm.wasm.debugging.languages.cpp;

import org.graalvm.wasm.debugging.DebugLocation;
import org.graalvm.wasm.debugging.data.DebugContext;
import org.graalvm.wasm.debugging.data.DebugType;
import org.graalvm.wasm.debugging.languages.c.CConstants;

/**
 * Represents the string type in C++. This allows to resolve the underlying values of the type and
 * return it as a {@link String}.
 */
public class CppString extends DebugType {
    private static final int STRING_REP_LENGTH = 12;
    private static final int LONG_INDICATOR_VALUE = 128;
    private static final int LENGTH_OFFSET = 11;
    private static final int LONG_LENGTH_OFFSET = 4;

    @Override
    public String asTypeName() {
        return CppConstants.STRING_TYPE;
    }

    @Override
    public int valueLength() {
        return STRING_REP_LENGTH;
    }

    @Override
    public boolean isValue() {
        return true;
    }

    @Override
    public Object asValue(DebugContext context, DebugLocation location) {
        final DebugLocation lengthLocation = location.addOffset(LENGTH_OFFSET);
        int length = lengthLocation.loadU8();
        final DebugLocation stringLocation;
        if (length == LONG_INDICATOR_VALUE) {
            stringLocation = location.loadAsLocation();
            if (stringLocation.isZero()) {
                return CConstants.NULL;
            }
            final long longLength = location.addOffset(LONG_LENGTH_OFFSET).loadU32();
            length = (int) longLength;
        } else {
            stringLocation = location;
        }
        return stringLocation.loadString(length);
    }
}
