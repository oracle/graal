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
package org.graalvm.wasm.debugging.data;

import org.graalvm.wasm.debugging.DebugLocation;
import org.graalvm.wasm.debugging.data.objects.DebugConstantObject;

/**
 * Represents a reference to a {@link DebugType}. This is necessary to resolve circular dependencies
 * while parsing the debug information.
 */
public final class DebugTypeRef extends DebugType {
    private DebugType delegate;

    public void setDelegate(DebugType delegate) {
        this.delegate = delegate;
    }

    @Override
    public String asTypeName() {
        if (delegate == null) {
            return "";
        }
        return delegate.asTypeName();
    }

    @Override
    public int valueLength() {
        if (delegate == null) {
            return 0;
        }
        return delegate.valueLength();
    }

    @Override
    public boolean isValue() {
        if (delegate == null) {
            return false;
        }
        return delegate.isValue();
    }

    @Override
    public Object asValue(DebugContext context, DebugLocation location) {
        if (delegate == null) {
            return DebugConstantObject.UNDEFINED;
        }
        return delegate.asValue(context, location);
    }

    @Override
    public boolean fitsIntoInt() {
        if (delegate == null) {
            return false;
        }
        return delegate.fitsIntoInt();
    }

    @Override
    public int asInt(DebugContext context, DebugLocation location) {
        if (delegate == null) {
            return DebugConstants.DEFAULT_I32;
        }
        return delegate.asInt(context, location);
    }

    @Override
    public boolean fitsIntoLong() {
        if (delegate == null) {
            return false;
        }
        return delegate.fitsIntoLong();
    }

    @Override
    public long asLong(DebugContext context, DebugLocation location) {
        if (delegate == null) {
            return DebugConstants.DEFAULT_I64;
        }
        return delegate.asLong(context, location);
    }

    @Override
    public boolean hasMembers() {
        if (delegate == null) {
            return false;
        }
        return delegate.hasMembers();
    }

    @Override
    public int memberCount() {
        if (delegate == null) {
            return 0;
        }
        return delegate.memberCount();
    }

    @Override
    public DebugObject readMember(DebugContext context, DebugLocation location, int index) {
        if (delegate == null) {
            return DebugConstantObject.UNDEFINED;
        }
        return delegate.readMember(context, location, index);
    }

    @Override
    public boolean hasArrayElements() {
        if (delegate == null) {
            return false;
        }
        return delegate.hasArrayElements();
    }

    @Override
    public int arrayDimensionCount() {
        if (delegate == null) {
            return 0;
        }
        return delegate.arrayDimensionCount();
    }

    @Override
    public int arrayDimensionSize(int dimension) {
        if (delegate == null) {
            return 0;
        }
        return delegate.arrayDimensionSize(dimension);
    }

    @Override
    public DebugObject readArrayElement(DebugContext context, DebugLocation location, int index) {
        if (delegate == null) {
            return DebugConstantObject.UNDEFINED;
        }
        return delegate.readArrayElement(context, location, index);
    }
}
