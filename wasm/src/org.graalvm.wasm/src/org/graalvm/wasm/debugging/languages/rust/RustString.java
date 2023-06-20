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

import org.graalvm.wasm.debugging.DebugLocation;
import org.graalvm.wasm.debugging.data.DebugContext;
import org.graalvm.wasm.debugging.data.DebugObject;
import org.graalvm.wasm.debugging.data.DebugType;
import org.graalvm.wasm.debugging.representation.DebugConstantDisplayValue;

/**
 * Represents the string type in Rust. This allows to resolve the underlying values of the type and
 * return it as a {@link String}.
 */
public class RustString extends DebugType {
    public static final String STRING_LENGTH = "length";
    public static final String STRING_DATA = "data_ptr";

    private final String name;
    private final DebugObject lengthMember;
    private final DebugObject dataMember;

    public RustString(String name, DebugObject[] members) {
        assert members != null : "the members (length, buffer) of a rust string object must not be null";
        this.name = name;
        DebugObject lengthObject = null;
        DebugObject dataObject = null;
        for (final DebugObject member : members) {
            if (STRING_LENGTH.equals(member.toDisplayString())) {
                lengthObject = member;
            }
            if (STRING_DATA.equals(member.toDisplayString())) {
                dataObject = member;
            }
        }
        lengthMember = lengthObject;
        dataMember = dataObject;
    }

    @Override
    public String asTypeName() {
        return name;
    }

    @Override
    public int valueLength() {
        return lengthMember.valueLength() + dataMember.valueLength();
    }

    @Override
    public boolean isValue() {
        return true;
    }

    @Override
    public Object asValue(DebugContext context, DebugLocation location) {
        if (lengthMember == null || !lengthMember.fitsIntoLong()) {
            return DebugConstantDisplayValue.UNSUPPORTED;
        }
        final long length = lengthMember.asLong(context, lengthMember.getLocation(location));
        if (dataMember == null || !dataMember.isLocation() || length > Integer.MAX_VALUE) {
            return DebugConstantDisplayValue.UNSUPPORTED;
        }
        final DebugLocation address = dataMember.asLocation(context, dataMember.getLocation(location));
        return address.loadString((int) length);
    }
}
