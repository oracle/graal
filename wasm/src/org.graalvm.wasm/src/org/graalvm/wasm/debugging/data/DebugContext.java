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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * The debug context used for resolving values. Represents information like the current source code
 * location or the name of the variable that is resolved.
 */
public class DebugContext {
    private final int sourceCodeLocation;
    private String elementName = null;
    private int memberBitSize = -1;
    private int memberBitOffset = -1;

    @TruffleBoundary
    public DebugContext(int sourceCodeLocation) {
        this.sourceCodeLocation = sourceCodeLocation;
    }

    private DebugContext(String elementName, int memberBitSize, int memberBitOffset, int sourceCodeLocation) {
        this.elementName = elementName;
        this.memberBitSize = memberBitSize;
        this.memberBitOffset = memberBitOffset;
        this.sourceCodeLocation = sourceCodeLocation;
    }

    /**
     * Creates a duplicate of the context with a changed element name.
     * 
     * @param newElementName the new element name
     */
    public DebugContext with(String newElementName) {
        return new DebugContext(newElementName, memberBitSize, memberBitOffset, sourceCodeLocation);
    }

    /**
     * Creates a duplicate of the context with updated information.
     * 
     * @param newElementName the name element name
     * @param newBitSize the new bit size
     * @param newBitOffset the new bit offset
     */
    public DebugContext with(String newElementName, int newBitSize, int newBitOffset) {
        return new DebugContext(newElementName, newBitSize, newBitOffset, sourceCodeLocation);
    }

    /**
     * @return The current position in the source code.
     */
    public int sourceCodeLocation() {
        return sourceCodeLocation;
    }

    /**
     * @return The current element name or an empty string, if not name is present.
     */
    public String elementNameOrEmpty() {
        if (elementName == null) {
            return "";
        }
        return elementName;
    }

    /**
     * @return The current bit size.
     */
    public int memberBitSizeOrDefault(int defaultValue) {
        if (memberBitSize == -1) {
            return defaultValue;
        }
        return memberBitSize;
    }

    /**
     * @return The current bit offset.
     */
    public int memberBitOffsetOrDefault(int defaultValue) {
        if (memberBitOffset == -1) {
            return defaultValue;
        }
        return memberBitOffset;
    }
}
