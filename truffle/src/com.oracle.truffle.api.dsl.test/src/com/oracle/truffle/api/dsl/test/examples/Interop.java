/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
