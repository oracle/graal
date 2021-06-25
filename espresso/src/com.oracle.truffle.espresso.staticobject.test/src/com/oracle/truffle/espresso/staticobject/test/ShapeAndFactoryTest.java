/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.staticobject.test;

import com.oracle.truffle.espresso.staticobject.StaticShape;
import org.junit.Assert;
import org.junit.Test;

import java.util.regex.Pattern;

public class ShapeAndFactoryTest extends StaticObjectTest {

    @Test
    public void correct() {
        StaticShape.Builder b = StaticShape.newBuilder(this);
        b.build(CustomStaticObject.class, CustomFactoryInterface.class);
    }

    @Test
    public void wrongFinalClone() {
        StaticShape.Builder b = StaticShape.newBuilder(this);
        try {
            b.build(WrongCloneCustomStaticObject.class, WrongCloneFactoryInterface.class);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().matches("'.*' implements Cloneable and declares a final '" + Pattern.quote("clone()") + "' method"));
        }
    }

    @Test
    public void wrongFactoryArgs() {
        StaticShape.Builder b = StaticShape.newBuilder(this);
        try {
            b.build(CustomStaticObject.class, WrongArgsFactoryInterface.class);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().matches("Method '.*' does not match any constructor in '.*'"));
        }
    }

    @Test
    public void wrongFactoryReturnType() {
        StaticShape.Builder b = StaticShape.newBuilder(this);
        try {
            b.build(CustomStaticObject.class, WrongReturnTypeFactoryInterface.class);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().matches("The return type of '.*' is not assignable from '.*'"));
        }
    }

    public static class CustomStaticObject implements Cloneable {
        final int i;
        final Object o;

        public CustomStaticObject(int i, Object o) {
            this.i = i;
            this.o = o;
        }
    }

    public static class WrongCloneCustomStaticObject implements Cloneable {
        final int i;
        final Object o;

        public WrongCloneCustomStaticObject(int i, Object o) {
            this.i = i;
            this.o = o;
        }

        @Override
        protected final Object clone() throws CloneNotSupportedException {
            return super.clone();
        }
    }

    public interface CustomFactoryInterface {
        CustomStaticObject create(int i, Object o);
    }

    public interface WrongCloneFactoryInterface {
        WrongCloneCustomStaticObject create(int i, Object o);
    }

    public interface WrongArgsFactoryInterface {
        CustomStaticObject create(int i);
    }

    public interface WrongReturnTypeFactoryInterface {
        String create(int i, Object o);
    }
}
