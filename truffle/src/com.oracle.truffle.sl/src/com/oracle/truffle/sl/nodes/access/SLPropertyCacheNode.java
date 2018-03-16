/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.nodes.access;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.sl.nodes.SLTypes;
import com.oracle.truffle.sl.nodes.expression.SLEqualNode;
import com.oracle.truffle.sl.runtime.SLBigNumber;
import com.oracle.truffle.sl.runtime.SLContext;
import com.oracle.truffle.sl.runtime.SLFunction;
import com.oracle.truffle.sl.runtime.SLNull;

@TypeSystemReference(SLTypes.class)
public abstract class SLPropertyCacheNode extends Node {
    protected static final int CACHE_LIMIT = 3;

    protected static boolean shapeCheck(Shape shape, DynamicObject receiver) {
        return shape != null && shape.check(receiver);
    }

    protected static Shape lookupShape(DynamicObject receiver) {
        CompilerAsserts.neverPartOfCompilation();
        assert SLContext.isSLObject(receiver);
        return receiver.getShape();
    }

    /**
     * Property names can be arbitrary SL objects. We could call {@link Object.equals}, but that is
     * generally a bad idea and therefore discouraged in Truffle.{@link Object.equals} is a virtual
     * call that can call possibly large methods that we do not want in compiled code. For example,
     * we do not want {@link SLBigNumber#equals} in compiled code but behind a
     * {@link TruffleBoundary). Therfore, we check types individually. The checks are semantically
     * the same as {@link SLEqualNode}.
     * <p>
     * Note that the {@code cachedName} is always a constant during compilation. Therefore, compiled
     * code is always reduced to a single {@code if} that only checks whether the {@code name} has
     * the same type.
     *
     */
    protected static boolean namesEqual(Object cachedName, Object name) {
        if (cachedName instanceof Long && name instanceof Long) {
            return (long) cachedName == (long) name;
        } else if (cachedName instanceof SLBigNumber && name instanceof SLBigNumber) {
            return ((SLBigNumber) cachedName).equals(name);
        } else if (cachedName instanceof Boolean && name instanceof Boolean) {
            return (boolean) cachedName == (boolean) name;
        } else if (cachedName instanceof String && name instanceof String) {
            return ((String) cachedName).equals(name);
        } else if (cachedName instanceof SLFunction && name instanceof SLFunction) {
            return cachedName == name;
        } else if (cachedName instanceof SLNull && name instanceof SLNull) {
            return cachedName == name;
        } else {
            assert !cachedName.equals(name);
            return false;
        }
    }

}
