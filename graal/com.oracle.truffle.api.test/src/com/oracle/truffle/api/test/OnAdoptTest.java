/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.api.test;

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/**
 * <h3>Inserting Extra Nodes into the AST Transparently</h3>
 * 
 * <p>
 * The {@link Node} class provides a callback that is invoked whenever a node is adopted in an AST
 * by insertion or replacement. Node classes can override the {@code onAdopt()} method to run extra
 * functionality upon adoption.
 * </p>
 * 
 * <p>
 * This test demonstrates how node instances of a specific class can be automatically wrapped in
 * extra nodes when they are inserted into the AST.
 * </p>
 */
public class OnAdoptTest {

    static class Root extends RootNode {

        @Child private Base child1;
        @Child private Base child2;

        public Root(Base child1, Base child2) {
            super(null);
            this.child1 = child1;
            this.child2 = child2;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return child1.executeInt(frame) + child2.executeInt(frame);
        }

    }

    abstract static class Base extends Node {
        public abstract int executeInt(VirtualFrame frame);
    }

    static class Wrapper extends Base {

        @Child private Base wrappee;

        public Wrapper(Base wrappee) {
            this.wrappee = wrappee;
        }

        @Override
        public int executeInt(VirtualFrame frame) {
            return 1 + wrappee.executeInt(frame);
        }

    }

    abstract static class GenBase extends Base {

        private final int k;

        public GenBase(int k) {
            this.k = k;
        }

        @Override
        public int executeInt(VirtualFrame frame) {
            return k;
        }

    }

    static class Gen extends GenBase {
        public Gen(int k) {
            super(k);
        }
    }

    static class GenWrapped extends GenBase {

        public GenWrapped(int k) {
            super(k);
        }

        @Override
        protected void onAdopt() {
            Wrapper w = new Wrapper(this);
            this.replace(w);
        }

    }

    @Test
    public void testOnInsert() {
        TruffleRuntime runtime = Truffle.getRuntime();
        Base b1 = new Gen(11);
        Base b2 = new GenWrapped(11);
        Root r = new Root(b1, b2);
        CallTarget ct = runtime.createCallTarget(r);
        Object result = ct.call();
        Assert.assertEquals(23, result);
    }

}
