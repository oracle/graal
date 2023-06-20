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
 * Represents a debug object that assigns a different type name to a given object. This allows for
 * type definitions to be represented. All calls except for the type name are forwarded to the
 * underlying reference.
 */
public class DebugLink extends DebugObject {
    private final String typeName;
    private final DebugObject reference;

    public DebugLink(String typeName, DebugObject reference) {
        assert reference != null : "the reference of a debug link (type reference) must not be null";
        this.typeName = typeName;
        this.reference = reference;
    }

    @Override
    public String toDisplayString() {
        return reference.toDisplayString();
    }

    @Override
    public DebugLocation getLocation(DebugLocation location) {
        return reference.getLocation(location);
    }

    @Override
    public DebugContext getContext(DebugContext context) {
        return context;
    }

    @Override
    public String asTypeName() {
        return typeName;
    }

    @Override
    public int valueLength() {
        return reference.valueLength();
    }

    @Override
    public boolean isVisible(int sourceCodeLocation) {
        return reference.isVisible(sourceCodeLocation);
    }

    @Override
    public boolean isValue() {
        return reference.isValue();
    }

    @Override
    public Object asValue(DebugContext context, DebugLocation location) {
        return reference.asValue(context, location);
    }

    @Override
    public boolean isDebugObject() {
        return reference.isDebugObject();
    }

    @Override
    public DebugObject asDebugObject(DebugContext context, DebugLocation location) {
        return reference.asDebugObject(context, location);
    }

    @Override
    public boolean isLocation() {
        return reference.isLocation();
    }

    @Override
    public DebugLocation asLocation(DebugContext context, DebugLocation location) {
        return reference.asLocation(context, location);
    }

    @Override
    public boolean fitsIntoInt() {
        return reference.fitsIntoInt();
    }

    @Override
    public int asInt(DebugContext context, DebugLocation location) {
        return reference.asInt(context, location);
    }

    @Override
    public boolean fitsIntoLong() {
        return reference.fitsIntoLong();
    }

    @Override
    public long asLong(DebugContext context, DebugLocation location) {
        return reference.asLong(context, location);
    }

    @Override
    public boolean hasMembers() {
        return reference.hasMembers();
    }

    @Override
    public int memberCount() {
        return reference.memberCount();
    }

    @Override
    public DebugObject readMember(DebugContext context, DebugLocation location, int index) {
        return reference.readMember(context, location, index);
    }

    @Override
    public boolean hasArrayElements() {
        return reference.hasArrayElements();
    }

    @Override
    public int arrayDimensionCount() {
        return reference.arrayDimensionCount();
    }

    @Override
    public int arrayDimensionSize(int dimension) {
        return reference.arrayDimensionSize(dimension);
    }

    @Override
    public DebugObject readArrayElement(DebugContext context, DebugLocation location, int index) {
        return reference.readArrayElement(context, location, index);
    }

    @Override
    public String toString() {
        return typeName;
    }
}
