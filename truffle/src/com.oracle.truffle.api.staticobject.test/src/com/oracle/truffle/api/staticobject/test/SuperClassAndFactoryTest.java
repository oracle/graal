/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.staticobject.test;

import com.oracle.truffle.api.staticobject.StaticShape;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.regex.Pattern;

@RunWith(Parameterized.class)
public class SuperClassAndFactoryTest extends StaticObjectModelTest {
    @Parameterized.Parameters(name = "{0}")
    public static TestConfiguration[] data() {
        return getTestConfigurations();
    }

    @Parameterized.Parameter public TestConfiguration config;

    @Test
    public void correct() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticShape.Builder b = StaticShape.newBuilder(te.testLanguage);
            b.build(CustomStaticObject.class, CustomFactoryInterface.class);
        }
    }

    @Test
    public void wrongFinalClone() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticShape.Builder b = StaticShape.newBuilder(te.testLanguage);
            try {
                b.build(WrongCloneCustomStaticObject.class, WrongCloneFactoryInterface.class);
                Assert.fail();
            } catch (IllegalArgumentException e) {
                Assert.assertTrue(e.getMessage().matches("'.*' implements Cloneable and declares a final '" + Pattern.quote("clone()") + "' method"));
            }
        }
    }

    @Test
    public void wrongAbstractSuperClass() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticShape.Builder b = StaticShape.newBuilder(te.testLanguage);
            try {
                b.build(WrongAbstractStaticObject.class, WrongAbstractFactoryInterface.class);
                Assert.fail();
            } catch (IllegalArgumentException e) {
                Assert.assertEquals("'com.oracle.truffle.api.staticobject.test.SuperClassAndFactoryTest$WrongAbstractStaticObject' has abstract methods", e.getMessage());
            }
        }
    }

    @Test
    public void wrongFactoryArgs() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticShape.Builder b = StaticShape.newBuilder(te.testLanguage);
            try {
                b.build(CustomStaticObject.class, WrongArgsFactoryInterface.class);
                Assert.fail();
            } catch (IllegalArgumentException e) {
                Assert.assertTrue(e.getMessage().matches("Method '.*' does not match any constructor in '.*'"));
            }
        }
    }

    @Test
    public void wrongFactoryReturnType() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            StaticShape.Builder b = StaticShape.newBuilder(te.testLanguage);
            try {
                b.build(CustomStaticObject.class, WrongReturnTypeFactoryInterface.class);
                Assert.fail();
            } catch (IllegalArgumentException e) {
                Assert.assertTrue(e.getMessage().matches("The return type of '.*' is not assignable from '.*'"));
            }
        }
    }

    public static class CustomStaticObject implements Cloneable {
        final long l;
        final Object o;

        public CustomStaticObject(long l, Object o) {
            this.l = l;
            this.o = o;
        }
    }

    public static class WrongCloneCustomStaticObject implements Cloneable {
        final long l;
        final Object o;

        public WrongCloneCustomStaticObject(long l, Object o) {
            this.l = l;
            this.o = o;
        }

        @Override
        protected final Object clone() throws CloneNotSupportedException {
            return super.clone();
        }
    }

    public abstract static class WrongAbstractStaticObject {
        abstract void notImplemented();
    }

    public interface CustomFactoryInterface {
        CustomStaticObject create(long l, Object o);
    }

    public interface WrongCloneFactoryInterface {
        WrongCloneCustomStaticObject create(long l, Object o);
    }

    public interface WrongAbstractFactoryInterface {
        WrongAbstractStaticObject create();
    }

    public interface WrongArgsFactoryInterface {
        CustomStaticObject create(long l);
    }

    public interface WrongReturnTypeFactoryInterface {
        String create(long l, Object o);
    }
}
