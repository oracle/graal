/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.Callable;

@RunWith(Parameterized.class)
public class ClassLoaderTest extends StaticObjectModelTest {
    @Parameterized.Parameters(name = "{0}")
    public static TestConfiguration[] data() {
        return getTestConfigurations();
    }

    @Parameterized.Parameter public TestConfiguration config;

    public static class CustomStaticObject {
    }

    public interface CustomStaticObjectFactory {
        CustomStaticObject create();
    }

    /**
     * The implementation of the Static Object Model caches the class loader used to load static
     * object classes. This test makes sure that the cache takes into account the class loader that
     * loaded the factory interface.
     */
    @Test
    public void testClassLoader() {
        try (TestEnvironment te = new TestEnvironment(config)) {
            // Callable.class is loaded by the system class loader
            try {
                StaticShape.newBuilder(te.testLanguage).build(Object.class, Callable.class);
            } catch (IllegalArgumentException e) {
                Assert.assertTrue(e.getMessage().matches(
                                "The class loader of factory interface 'java.util.concurrent.Callable' \\(cl: '.*'\\) must have visibility of 'com.oracle.truffle.api.staticobject.StaticShape' \\(cl: '.*'\\)"));
            }
            // CustomStaticObjectFactory.class is loaded by the application class loader
            StaticShape.newBuilder(te.testLanguage).build(CustomStaticObject.class, CustomStaticObjectFactory.class);
        }
    }
}
