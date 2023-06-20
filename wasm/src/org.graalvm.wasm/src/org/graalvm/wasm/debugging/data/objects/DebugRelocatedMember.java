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

package org.graalvm.wasm.debugging.data.objects;

import org.graalvm.wasm.debugging.DebugLocation;
import org.graalvm.wasm.debugging.data.DebugContext;
import org.graalvm.wasm.debugging.data.DebugObject;

/**
 * Represents a debug object that maps the location of a member to a different location. This is
 * useful when a class inherits members from another class to load them from the correct location.
 */
public class DebugRelocatedMember extends DebugObject {
    private final DebugLocation objectLocation;
    private final DebugObject object;

    public DebugRelocatedMember(DebugLocation objectLocation, DebugObject object) {
        assert objectLocation != null : "the location of a relocatable debug member must not be null";
        assert object != null : "the object reference of a relocatable debug member must not be null";
        this.objectLocation = objectLocation;
        this.object = object;
    }

    @Override
    public String toDisplayString() {
        return object.toDisplayString();
    }

    @Override
    public DebugLocation getLocation(DebugLocation location) {
        return objectLocation;
    }

    @Override
    public DebugContext getContext(DebugContext context) {
        return context;
    }

    @Override
    public String asTypeName() {
        return object.asTypeName();
    }

    @Override
    public int valueLength() {
        return object.valueLength();
    }

    @Override
    public boolean isDebugObject() {
        return true;
    }

    @Override
    public DebugObject asDebugObject(DebugContext context, DebugLocation location) {
        return object;
    }

}
