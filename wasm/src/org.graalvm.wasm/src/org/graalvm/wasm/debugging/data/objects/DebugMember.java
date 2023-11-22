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
import org.graalvm.wasm.debugging.data.DebugType;
import org.graalvm.wasm.debugging.parser.DebugParser;

/**
 * Represents a debug object that is a member of a structure type like a struct or a variant type.
 */
public class DebugMember extends DebugBinding {
    private final String name;
    private final byte[] locationExpression;
    private final int offset;
    private final int bitOffset;
    private final int bitSize;

    /**
     * Creates a debug member.
     * 
     * @param name the name of the member
     * @param type the type of the member
     * @param locationExpression the location expression of the member. If the location expression
     *            is null, an offset must be provided.
     * @param offset the offset from the start of the structure type. If a location expression is
     *            provided, this value is ignored.
     * @param bitOffset the bit offset from the start of the structure type
     * @param bitSize the bit size used to represent this member
     */
    public DebugMember(String name, DebugType type, byte[] locationExpression, int offset, int bitOffset, int bitSize) {
        super(type);
        this.name = name;
        this.locationExpression = locationExpression;
        this.offset = offset;
        this.bitOffset = bitOffset;
        this.bitSize = bitSize;
    }

    private DebugLocation memberLocation(DebugLocation baseLocation) {
        if (locationExpression != null) {
            return DebugParser.readExpression(locationExpression, baseLocation);
        }
        return baseLocation.addOffset(offset);
    }

    @Override
    public String toDisplayString() {
        return name;
    }

    @Override
    public DebugLocation getLocation(DebugLocation location) {
        return memberLocation(location);
    }

    @Override
    public DebugContext getContext(DebugContext context) {
        return context.with(name, bitSize, bitOffset);
    }
}
