/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest.assertFails;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.InlineSupport.InlineTarget;
import com.oracle.truffle.api.dsl.test.GenerateCachedTestFactory.AlwaysInlineNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateCachedTestFactory.DefaultEnabledNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateCachedTestFactory.EnabledInheritInheritNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateCachedTestFactory.EnabledInheritNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateCachedTestFactory.EnabledSubNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateCachedTestFactory.OnlyInliningNodeGen;
import com.oracle.truffle.api.dsl.test.GenerateCachedTestFactory.OnlyUncachedNodeGen;
import com.oracle.truffle.api.nodes.Node;

@SuppressWarnings("truffle")
public class GenerateCachedTest {

    @Test
    public void testEnabled() {
        // just tests that all nodes are generated as expected by calling the create method
        DefaultEnabledNodeGen.create();
        EnabledSubNodeGen.create();
        EnabledInheritNodeGen.create();
        EnabledInheritInheritNodeGen.create();
    }

    @Test
    public void testDisabled() {
        assertFails(() -> loadGeneratedClass("DisabledNodeGen"), ClassNotFoundException.class);
        assertFails(() -> loadGeneratedClass("DisabledInheritSubNodeGen"), ClassNotFoundException.class);
    }

    abstract static class DefaultEnabledNode extends Node {

        abstract void execute();

        @Specialization
        void s0() {
        }
    }

    static Class<?> loadGeneratedClass(String name) throws ClassNotFoundException {
        try {
            return Class.forName(DefaultEnabledNodeGen.class.getEnclosingClass().getName() + "." + name);
        } catch (SecurityException e) {
            throw new AssertionError("unexpected error", e);
        }
    }

    @GenerateCached(value = false, inherit = false)
    abstract static class DisabledNode extends Node {

        abstract void execute();

        @Specialization
        void s0() {
        }

    }

    abstract static class EnabledSubNode extends DisabledNode {

        @Override
        @Specialization
        void s0() {
        }

    }

    @GenerateCached(value = false, inherit = true)
    abstract static class DisabledInheritNode extends Node {

        abstract void execute();

        @Specialization
        void s0() {
        }

    }

    abstract static class DisabledInheritSubNode extends DisabledNode {

        @Override
        @Specialization
        void s0() {
        }

    }

    @GenerateCached(value = true, inherit = true)
    abstract static class EnabledInheritNode extends DisabledInheritNode {
        @Override
        @Specialization
        void s0() {
        }
    }

    abstract static class EnabledInheritInheritNode extends EnabledInheritNode {

        @Override
        @Specialization
        void s0() {
        }

    }

    @Test
    public void testInliningOnly() throws NoSuchMethodException {
        assertNotNull(findInlineMethod(OnlyInliningNodeGen.class));
        assertFails(() -> findCreateMethod(OnlyInliningNodeGen.class), NoSuchMethodException.class);
        assertFails(() -> findUncachedMethod(OnlyInliningNodeGen.class), NoSuchMethodException.class);
    }

    @GenerateCached(false)
    @GenerateInline(true)
    abstract static class OnlyInliningNode extends Node {

        abstract void execute(Node node);

        @Specialization
        void s0() {
        }
    }

    private static Method findInlineMethod(Class<?> c) throws NoSuchMethodException {
        try {
            return c.getDeclaredMethod("inline", InlineTarget.class);
        } catch (SecurityException e) {
            throw new AssertionError("unexpected error", e);
        }
    }

    private static Method findCreateMethod(Class<?> c) throws NoSuchMethodException {
        try {
            return c.getDeclaredMethod("create");
        } catch (SecurityException e) {
            throw new AssertionError("unexpected error", e);
        }
    }

    private static Method findUncachedMethod(Class<?> c) throws NoSuchMethodException {
        try {
            return c.getDeclaredMethod("getUncached");
        } catch (SecurityException e) {
            throw new AssertionError("unexpected error", e);
        }
    }

    @Test
    public void testUncachedOnly() throws NoSuchMethodException {
        assertFails(() -> findInlineMethod(OnlyUncachedNodeGen.class), NoSuchMethodException.class);
        assertFails(() -> findCreateMethod(OnlyUncachedNodeGen.class), NoSuchMethodException.class);
        assertNotNull(findUncachedMethod(OnlyUncachedNodeGen.class));
    }

    @GenerateCached(false)
    @GenerateUncached(true)
    abstract static class OnlyUncachedNode extends Node {

        abstract void execute(Node node);

        @Specialization
        void s0() {
        }
    }

    @GenerateCached(false)
    @GenerateInline(true)
    abstract static class OnlyInlineNode extends Node {

        abstract void execute(Node node);

        @Specialization
        void s0() {
        }
    }

    @GenerateCached(alwaysInlineCached = true)
    @SuppressWarnings("unused")
    abstract static class AlwaysInline extends Node {

        abstract void execute(Node node);

        /*
         * No warning should appear here.
         */
        @Specialization
        void s0(
                        @ExpectError("Redundant specification of @Cached(... inline=true). %") //
                        @Cached(inline = true) OnlyInlineNode node) {
        }
    }

    @Test
    public void testAlwaysInline() {
        AlwaysInline node = AlwaysInlineNodeGen.create();
        for (Field f : node.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            // assert no instance fields with type InlineableNode
            assertNotEquals(OnlyInlineNode.class, f.getType());
        }
    }

}
