/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test.examples;

import static com.oracle.truffle.api.dsl.test.examples.ExampleNode.createArguments;
import static com.oracle.truffle.api.dsl.test.examples.ExampleNode.createTarget;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.examples.InteropFactory.UseInteropNodeGen;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * This example aims to illustrate how the {@link Cached} annotation can be used to implement a
 * cache for a simplified language interoperability pattern.
 */
public class Interop {

    @Test
    public void testInterop() {
        UseInterop node = UseInteropNodeGen.create(createArguments(2));
        CallTarget target = createTarget(node);
        TruffleObject o1 = new TruffleObject();
        TruffleObject o2 = new TruffleObject();
        TruffleObject o3 = new TruffleObject();
        TruffleObject o4 = new TruffleObject();
        assertEquals(42, target.call(o1, 42));
        assertEquals(43, target.call(o2, 43));
        assertEquals(44, target.call(o3, 44));
        assertEquals(3, node.cached);
        assertEquals(0, node.generic);
        assertEquals(45, target.call(o4, 45)); // operation gets generic
        assertEquals(42, target.call(o1, 42));
        assertEquals(43, target.call(o2, 43));
        assertEquals(44, target.call(o3, 44));
        assertEquals(3, node.cached);
        assertEquals(4, node.generic);
    }

    public static class UseInterop extends ExampleNode {

        int cached = 0;
        int generic = 0;

        @Specialization(guards = "operation.accept(target)")
        protected Object interopCached(VirtualFrame frame, TruffleObject target, Object value, //
                        @Cached("target.createOperation()") TruffleObjectOperation operation) {
            cached++;
            return operation.execute(frame, target, value);
        }

        @Specialization(replaces = "interopCached")
        protected Object interopGeneric(VirtualFrame frame, TruffleObject target, Object value) {
            generic++;
            return target.createOperation().execute(frame, target, value);
        }
    }

    public abstract static class TruffleObjectOperation extends Node {

        public abstract boolean accept(TruffleObject object);

        public abstract Object execute(VirtualFrame frame, Object target, Object value);

    }

    public static class TruffleObject {

        @TruffleBoundary
        public TruffleObjectOperation createOperation() {
            return new TruffleObjectOperation() {
                @Override
                public Object execute(VirtualFrame frame, Object target, Object value) {
                    return value;
                }

                @Override
                public boolean accept(TruffleObject object) {
                    return TruffleObject.this == object;
                }
            };
        }
    }

}
