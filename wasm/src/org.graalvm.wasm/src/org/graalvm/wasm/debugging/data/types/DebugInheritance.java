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

import org.graalvm.wasm.debugging.DebugLocation;
import org.graalvm.wasm.debugging.data.DebugContext;
import org.graalvm.wasm.debugging.data.DebugObject;
import org.graalvm.wasm.debugging.data.DebugType;
import org.graalvm.wasm.debugging.parser.DebugParser;

/**
 * Represents an inheritance relationship in the debug information. Forwards all method calls except
 * for the location resolution to the underlying reference type.
 */
public class DebugInheritance extends DebugType {
    private final DebugType referenceType;
    private final byte[] locationExpression;
    private final int memberOffset;

    /**
     * Creates an inheritance relation.
     * 
     * @param referenceType the type that is inherited.
     * @param locationExpression the location of the members in the base type. If the location
     *            expression is null, a member offset must be provided.
     * @param memberOffset the member offset from the start of the base type. If a location
     *            expression is provided, this value is ignored.
     */
    public DebugInheritance(DebugType referenceType, byte[] locationExpression, int memberOffset) {
        assert referenceType != null : "the reference type (super class) of a debug inheritance must not be null";
        this.referenceType = referenceType;
        this.locationExpression = locationExpression;
        this.memberOffset = memberOffset;
    }

    private DebugLocation offsetLocation(DebugLocation baseLocation) {
        if (locationExpression != null) {
            return DebugParser.readExpression(locationExpression, baseLocation);
        }
        return baseLocation.addOffset(memberOffset);
    }

    @Override
    public String asTypeName() {
        return referenceType.asTypeName();
    }

    @Override
    public int valueLength() {
        return referenceType.valueLength();
    }

    @Override
    public boolean isValue() {
        return referenceType.isValue();
    }

    @Override
    public Object asValue(DebugContext context, DebugLocation location) {
        return referenceType.asValue(context, offsetLocation(location));
    }

    @Override
    public boolean isDebugObject() {
        return referenceType.isDebugObject();
    }

    @Override
    public DebugObject asDebugObject(DebugContext context, DebugLocation location) {
        return referenceType.asDebugObject(context, offsetLocation(location));
    }

    @Override
    public boolean isLocation() {
        return referenceType.isLocation();
    }

    @Override
    public DebugLocation asLocation(DebugContext context, DebugLocation location) {
        return referenceType.asLocation(context, offsetLocation(location));
    }

    @Override
    public boolean fitsIntoInt() {
        return referenceType.fitsIntoInt();
    }

    @Override
    public int asInt(DebugContext context, DebugLocation location) {
        return referenceType.asInt(context, offsetLocation(location));
    }

    @Override
    public boolean fitsIntoLong() {
        return referenceType.fitsIntoLong();
    }

    @Override
    public long asLong(DebugContext context, DebugLocation location) {
        return referenceType.asLong(context, offsetLocation(location));
    }

    @Override
    public boolean hasMembers() {
        return referenceType.hasMembers();
    }

    @Override
    public int memberCount() {
        return referenceType.memberCount();
    }

    @Override
    public DebugObject readMember(DebugContext context, DebugLocation location, int index) {
        return referenceType.readMember(context, offsetLocation(location), index);
    }

    @Override
    public boolean hasArrayElements() {
        return referenceType.hasArrayElements();
    }

    @Override
    public int arrayDimensionCount() {
        return referenceType.arrayDimensionCount();
    }

    @Override
    public int arrayDimensionSize(int dimension) {
        return referenceType.arrayDimensionSize(dimension);
    }

    @Override
    public DebugObject readArrayElement(DebugContext context, DebugLocation location, int index) {
        return referenceType.readArrayElement(context, offsetLocation(location), index);
    }
}
