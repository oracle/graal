/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;

import org.junit.Test;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NonIdempotent;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.SingleSpecializationStateTestFactory.BasicConstantNodeGen;
import com.oracle.truffle.api.dsl.test.SingleSpecializationStateTestFactory.CachedInlinedNodeGen;
import com.oracle.truffle.api.dsl.test.SingleSpecializationStateTestFactory.CachedSimpleNodeGen;
import com.oracle.truffle.api.dsl.test.SingleSpecializationStateTestFactory.InlineMultiInstanceNodeGen;
import com.oracle.truffle.api.dsl.test.SingleSpecializationStateTestFactory.InlineSingleInstanceNodeGen;
import com.oracle.truffle.api.dsl.test.SingleSpecializationStateTestFactory.MethodGuardNodeGen;
import com.oracle.truffle.api.dsl.test.SingleSpecializationStateTestFactory.TypeGuardNodeGen;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedCountingConditionProfile;

@DisableStateBitWidthModfication
public class SingleSpecializationStateTest {

    @GenerateInline(false)
    abstract static class BasicConstantNode extends BaseNode {
        @Specialization
        static Object doDefault(Object a) {
            return a;
        }
    }

    @Test
    public void testBasicConstant() {
        assertEquals(0, countStateFields(BasicConstantNodeGen.class));
    }

    @GenerateInline(false)
    abstract static class TypeGuardNode extends BaseNode {
        @Specialization
        static Object doDefault(int a) {
            return a;
        }
    }

    @Test
    public void testTypeGuard() {
        assertEquals(0, countStateFields(TypeGuardNodeGen.class));
    }

    @GenerateInline(false)
    abstract static class MethodGuardNode extends BaseNode {
        @Specialization(guards = "true")
        static Object doDefault(Object a) {
            return a;
        }
    }

    @Test
    public void testMethodGuardNode() {
        assertEquals(0, countStateFields(MethodGuardNodeGen.class));
    }

    @GenerateInline(false)
    @SuppressWarnings("truffle-neverdefault")
    abstract static class CachedSimpleNode extends BaseNode {
        @Specialization
        static Object doDefault(Object a,
                        // all cached state needs to be initialized so we need a state bitset
                        @SuppressWarnings("unused") @Cached("true") boolean cached) {
            return a;
        }
    }

    @Test
    public void testCachedNode() {
        assertEquals(1, countStateFields(CachedSimpleNodeGen.class));
    }

    @GenerateInline(false)
    @SuppressWarnings("truffle-neverdefault")
    abstract static class CachedInlinedNode extends BaseNode {
        @Specialization
        static Object doDefault(Object a,
                        // generates fields but no state bitset
                        @SuppressWarnings("unused") @Cached InlinedCountingConditionProfile cached) {
            return a;
        }
    }

    @Test
    public void testCachedInlinedNode() {
        assertEquals(0, countStateFields(CachedInlinedNodeGen.class));
    }

    @GenerateInline(false)
    @SuppressWarnings("unused")
    abstract static class InlineSingleInstanceNode extends BaseNode {

        @Specialization(guards = "myGuard(a, cached1)", limit = "1")
        static Object doDefault(Object a,
                        @Cached InlinedCountingConditionProfile cached1) {
            return a;
        }

        @NonIdempotent
        static boolean myGuard(Object a, InlinedCountingConditionProfile profile) {
            return false;
        }
    }

    @Test
    public void testInlineSingleInstanceNode() {
        assertEquals(0, countStateFields(InlineSingleInstanceNodeGen.class));
    }

    @GenerateInline(false)
    @SuppressWarnings("unused")
    abstract static class InlineMultiInstanceNode extends BaseNode {

        @Specialization(guards = "myGuard(node, a, cached1)", limit = "2")
        static Object doDefault(Object a,
                        @Bind Node node,
                        // inline condition profiles in guards need state
                        // as it may have multiple instances
                        @Cached InlinedCountingConditionProfile cached1) {
            return a;
        }

        @NonIdempotent
        static boolean myGuard(Node node, Object a, InlinedCountingConditionProfile profile) {
            return false;
        }
    }

    @Test
    public void testIInlineMultiInstanceNode() {
        assertEquals(1, countStateFields(InlineMultiInstanceNodeGen.class));
    }

    private static int countStateFields(Class<?> c) {
        int fieldCount = 0;
        for (Field field : c.getDeclaredFields()) {
            if (field.getName().startsWith("state")) {
                fieldCount++;
            }
        }
        return fieldCount;
    }

    abstract static class BaseNode extends Node {

        abstract Object execute(Object a);

    }

}
