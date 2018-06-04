/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.dsl.UnsupportedSpecializationException;
import com.oracle.truffle.api.dsl.test.IdentityComparisonTestFactory.EqualityComparison1NodeGen;
import com.oracle.truffle.api.dsl.test.IdentityComparisonTestFactory.IdentityComparison1NodeGen;
import com.oracle.truffle.api.dsl.test.examples.ExampleTypes;
import com.oracle.truffle.api.nodes.Node;

public class IdentityComparisonTest {

    @Test
    public void testIdentityComparison() {
        IdentityComparison1Node node = IdentityComparison1NodeGen.create();
        Assert.assertEquals(true, node.execute(true));
        Assert.assertEquals((byte) 1, node.execute((byte) 1));
        Assert.assertEquals((short) 1, node.execute((short) 1));
        Assert.assertEquals((char) 1, node.execute((char) 1));
        Assert.assertEquals(1, node.execute(1));
        Assert.assertEquals(1L, node.execute(1L));
        Assert.assertEquals(1f, node.execute(1f));
        Assert.assertEquals(1d, node.execute(1d));

        // Double.NaN must fail because NaN must not be identity equal to NaN.
        // The DSL can optimize the == operator but must be careful with NaN values.
        // If the DSL optimizes the == operator it would not throw an unsupported
        // specialization exception
        try {
            node.execute(Double.NaN);
            Assert.fail();
        } catch (UnsupportedSpecializationException e) {
        }
        try {
            node.execute(Float.NaN);
            Assert.fail();
        } catch (UnsupportedSpecializationException e) {
        }
    }

    @SuppressWarnings("unused")
    @TypeSystemReference(ExampleTypes.class)
    abstract static class IdentityComparison1Node extends Node {

        public abstract Object execute(Object arg0);

        @Specialization(guards = "value == cachedValue", limit = "3")
        boolean s0(boolean value, @Cached("value") boolean cachedValue) {
            return value;
        }

        @Specialization(guards = "value == cachedValue", limit = "3")
        byte s1(byte value, @Cached("value") byte cachedValue) {
            return value;
        }

        @Specialization(guards = "value == cachedValue", limit = "3")
        short s2(short value, @Cached("value") short cachedValue) {
            return value;
        }

        @Specialization(guards = "value == cachedValue", limit = "3")
        char s3(char value, @Cached("value") char cachedValue) {
            return value;
        }

        @Specialization(guards = "value == cachedValue", limit = "3")
        int s4(int value, @Cached("value") int cachedValue) {
            return value;
        }

        @Specialization(guards = "value == cachedValue", limit = "3")
        long s5(long value, @Cached("value") long cachedValue) {
            return value;
        }

        @Specialization(guards = "value == cachedValue", limit = "3")
        float s6(float value, @Cached("value") float cachedValue) {
            return value;
        }

        @Specialization(guards = "value == cachedValue", limit = "3")
        double s7(double value, @Cached("value") double cachedValue) {
            return value;
        }

        @Specialization(guards = "value == cachedValue", limit = "3")
        String s8(String value, @Cached("value") String cachedValue) {
            return value;
        }
    }

    @Test
    public void testEqualityComparison() {
        EqualityComparison1Node node = EqualityComparison1NodeGen.create();
        Assert.assertEquals(true, node.execute(true));
        Assert.assertEquals((byte) 1, node.execute((byte) 1));
        Assert.assertEquals((short) 1, node.execute((short) 1));
        Assert.assertEquals((char) 1, node.execute((char) 1));
        Assert.assertEquals(1, node.execute(1));
        Assert.assertEquals(1L, node.execute(1L));
        Assert.assertEquals(1f, node.execute(1f));
        Assert.assertEquals(1d, node.execute(1d));

        /*
         * Equality for equals comparison of NaN can fold.
         */
        Assert.assertEquals(Double.NaN, node.execute(Double.NaN));
        Assert.assertEquals(Float.NaN, node.execute(Float.NaN));

        MyClass myClassValue = new MyClass();

        Assert.assertSame(myClassValue, node.execute(myClassValue));
        // for the first execution we never need to call equals
        Assert.assertEquals(0, myClassValue.equalsCalled);

        Assert.assertSame(myClassValue, node.execute(myClassValue));
        // the second iteration requires one equals call for the cache
        Assert.assertEquals(1, myClassValue.equalsCalled);
    }

    @SuppressWarnings("unused")
    @TypeSystemReference(ExampleTypes.class)
    abstract static class EqualityComparison1Node extends Node {

        public abstract Object execute(Object arg0);

        @Specialization(guards = "value.equals(cachedValue)", limit = "3")
        Boolean s0(Boolean value, @Cached("value") Boolean cachedValue) {
            return value;
        }

        @Specialization(guards = "value.equals(cachedValue)", limit = "3")
        Byte s1(Byte value, @Cached("value") Byte cachedValue) {
            return value;
        }

        @Specialization(guards = "value.equals(cachedValue)", limit = "3")
        Short s2(Short value, @Cached("value") Short cachedValue) {
            return value;
        }

        @Specialization(guards = "value.equals(cachedValue)", limit = "3")
        Character s3(Character value, @Cached("value") Character cachedValue) {
            return value;
        }

        @Specialization(guards = "value.equals(cachedValue)", limit = "3")
        Integer s4(Integer value, @Cached("value") Integer cachedValue) {
            return value;
        }

        @Specialization(guards = "value.equals(cachedValue)", limit = "3")
        Long s5(Long value, @Cached("value") Long cachedValue) {
            return value;
        }

        @Specialization(guards = "value.equals(cachedValue)", limit = "3")
        Float s6(Float value, @Cached("value") Float cachedValue) {
            return value;
        }

        @Specialization(guards = "value.equals(cachedValue)", limit = "3")
        Double s7(Double value, @Cached("value") Double cachedValue) {
            return value;
        }

        @Specialization(guards = "value.equals(cachedValue)", limit = "3")
        String s8(String value, @Cached("value") String cachedValue) {
            return value;
        }

        @Specialization(guards = "value.equals(cachedValue)", limit = "3")
        MyClass s8(MyClass value, @Cached("value") MyClass cachedValue) {
            return value;
        }

    }

    static class MyClass {

        int equalsCalled;

        @Override
        public boolean equals(Object obj) {
            equalsCalled++;
            return super.equals(obj);
        }

        // override also hashCode to make javac happy
        @Override
        public int hashCode() {
            return super.hashCode();
        }

    }

}
