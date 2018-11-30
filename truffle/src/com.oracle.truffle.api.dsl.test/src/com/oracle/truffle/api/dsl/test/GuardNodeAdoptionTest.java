/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.GuardNodeAdoptionTestFactory.UseCachedNodeGen;
import com.oracle.truffle.api.dsl.test.GuardNodeAdoptionTestFactory.UseNoCacheNodeGen;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public class GuardNodeAdoptionTest {

    abstract static class GuardNode extends Node {

        abstract boolean execute(String argument);

        @Specialization
        boolean s0(String argument) {
            assertNotNull(this.getRootNode());
            return argument.equals("42");
        }
    }

    @SuppressWarnings("unused")
    abstract static class UseCachedNode extends Node {

        abstract String execute(String argument);

        @Specialization(guards = "guardNode.execute(argument)")
        String s0(String argument, @Cached GuardNode guardNode) {
            assertNotNull(this.getRootNode());
            return "cached";
        }
    }

    @Test
    public void testDynamicParameterBoundAdopted() {
        UseCachedNode use = adopt(UseCachedNodeGen.create());
        assertEquals("cached", use.execute("42"));
    }

    @SuppressWarnings("unused")
    abstract static class UseNoCacheNode extends Node {

        static final String CONSTANT_42 = "42";

        abstract String execute(String argument);

        @Specialization(guards = "guardNode.execute(CONSTANT_42)")
        String s0(String argument, @Cached GuardNode guardNode) {
            assertNotNull(this.getRootNode());
            return "cached";
        }
    }

    @Test
    public void testStaticParameterAdopted() {
        UseNoCacheNode use = adopt(UseNoCacheNodeGen.create());
        assertEquals("cached", use.execute("42"));
    }

    private static <T extends Node> T adopt(T node) {
        RootNode root = new RootNode(null) {
            {
                insert(node);
            }

            @Override
            public Object execute(VirtualFrame frame) {
                return null;
            }
        };
        root.adoptChildren();
        return node;
    }

}
