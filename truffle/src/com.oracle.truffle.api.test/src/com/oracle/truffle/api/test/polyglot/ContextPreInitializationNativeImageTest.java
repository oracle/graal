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
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.oracle.truffle.api.staticobject.DefaultStaticObjectFactory;
import com.oracle.truffle.api.staticobject.DefaultStaticProperty;
import com.oracle.truffle.api.staticobject.StaticProperty;
import com.oracle.truffle.api.staticobject.StaticShape;
import com.oracle.truffle.api.test.ReflectionUtils;
import org.graalvm.polyglot.Context;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ContextLocal;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

import java.lang.reflect.Field;

/**
 * Note this test class currently depends on being executed in its own SVM image as it uses the
 * context preinitialization which only works for the first context of a native-image. An execution
 * of this test on HotSpot is ignored. Use {@link ContextPreInitializationTest} for that purpose.
 *
 * This could potentially be improved using some white-box API that allows to explicitly store and
 * restore the preinitialized context.
 *
 * This test needs
 * -Dpolyglot.image-build-time.PreinitializeContexts=ContextPreintializationNativeImageLanguage
 * provided via com.oracle.truffle.api.test/src/META-INF/native-image/native-image.properties.
 * Setting the property programmatically in a static initializer via
 * System.setProperty("polyglot.image-build-time.PreinitializeContexts", LANGUAGE) is not reliable
 * as its publishing depends on when the class is initialized. The property needs to be available
 * before com.oracle.truffle.polyglot.PolyglotContextImpl#preInitialize() is invoked, i.e., before
 * com.oracle.svm.truffle.TruffleBaseFeature#beforeAnalysis().
 */
public class ContextPreInitializationNativeImageTest {

    static final String LANGUAGE = "ContextPreintializationNativeImageLanguage";
    private static Context ctx;

    @BeforeClass
    public static void beforeClass() {
        if (TruffleTestAssumptions.isAOT()) {
            TruffleTestAssumptions.assumeWeakEncapsulation();
            ctx = Context.create(LANGUAGE);
            ctx.initialize(LANGUAGE);
            ctx.enter();
        }
    }

    @AfterClass
    public static void afterClass() {
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    public void patchedContext() {
        // only supported in AOT
        TruffleTestAssumptions.assumeAOT();
        assertTrue(Language.CONTEXT_REF.get(null).patched);
    }

    @Test
    public void threadLocalActions() {
        // only supported in AOT
        TruffleTestAssumptions.assumeAOT();
        assertTrue(String.valueOf(Language.CONTEXT_REF.get(null).threadLocalActions), Language.CONTEXT_REF.get(null).threadLocalActions > 0);
    }

    @Test
    public void somShapeAllocatedOnContextPreInit() throws Exception {
        // only supported in AOT
        TruffleTestAssumptions.assumeAOT();
        StaticObjectModelTest somTest = Language.CONTEXT_REF.get(null).staticObjectModelTest;
        somTest.testShapeAllocatedOnContextPreInit();
    }

    /**
     * This test checks that the Static Object Model can be used at image built time for context
     * pre-initialization.
     */
    @Test
    public void somObjectAllocatedOnContextPreInit() throws Exception {
        // only supported in AOT
        TruffleTestAssumptions.assumeAOT();
        StaticObjectModelTest somTest = Language.CONTEXT_REF.get(null).staticObjectModelTest;
        somTest.testObjectAllocatedOnContextPreInit();
    }

    static class StaticObjectModelTest {

        private static final Object OBJECT_VALUE = "value";

        private final StaticProperty booleanProperty;
        private final StaticProperty byteProperty;
        private final StaticProperty shortProperty;
        private final StaticProperty charProperty;
        private final StaticProperty intProperty;
        private final StaticProperty longProperty;
        private final StaticProperty floatProperty;
        private final StaticProperty doubleProperty;
        private final StaticProperty objectProperty;

        private final StaticShape<DefaultStaticObjectFactory> staticShape;
        private final Object staticObject;

        StaticObjectModelTest(Env env, Language language) {
            assertTrue(env.isPreInitialization());

            StaticShape.Builder builder = StaticShape.newBuilder(language);
            booleanProperty = new DefaultStaticProperty("booleanProperty");
            byteProperty = new DefaultStaticProperty("byteProperty");
            shortProperty = new DefaultStaticProperty("shortProperty");
            charProperty = new DefaultStaticProperty("charProperty");
            intProperty = new DefaultStaticProperty("intProperty");
            longProperty = new DefaultStaticProperty("longProperty");
            floatProperty = new DefaultStaticProperty("floatProperty");
            doubleProperty = new DefaultStaticProperty("doubleProperty");
            objectProperty = new DefaultStaticProperty("objectProperty");

            builder.property(booleanProperty, boolean.class, false);
            builder.property(byteProperty, byte.class, false);
            builder.property(shortProperty, short.class, false);
            builder.property(charProperty, char.class, false);
            builder.property(intProperty, int.class, false);
            builder.property(longProperty, long.class, false);
            builder.property(floatProperty, float.class, false);
            builder.property(doubleProperty, double.class, false);
            builder.property(objectProperty, Object.class, false);

            staticShape = builder.build();
            staticObject = staticShape.getFactory().create();

            setVolatileBooleanProperty(booleanProperty, staticObject, Boolean.TRUE);
            setVolatileByteProperty(byteProperty, staticObject, Byte.MAX_VALUE);
            setVolatileShortProperty(shortProperty, staticObject, Short.MAX_VALUE);
            setVolatileCharProperty(charProperty, staticObject, Character.MAX_VALUE);
            setVolatileIntProperty(intProperty, staticObject, Integer.MAX_VALUE);
            setVolatileLongProperty(longProperty, staticObject, Long.MAX_VALUE);
            setVolatileFloatProperty(floatProperty, staticObject, Float.MAX_VALUE);
            setVolatileDoubleProperty(doubleProperty, staticObject, Double.MAX_VALUE);
            setVolatileObjectProperty(objectProperty, staticObject, OBJECT_VALUE);

            try {
                Field primitive = staticObject.getClass().getDeclaredField("primitive");
                assertEquals(byte[].class, primitive.getType());
            } catch (NoSuchFieldException e) {
                fail(e.getMessage());
            }
        }

        void testShapeAllocatedOnContextPreInit() throws Exception {
            Object newStaticObject = staticShape.getFactory().create();
            setVolatileBooleanProperty(booleanProperty, newStaticObject, Boolean.TRUE);
            setVolatileByteProperty(byteProperty, newStaticObject, Byte.MAX_VALUE);
            setVolatileShortProperty(shortProperty, newStaticObject, Short.MAX_VALUE);
            setVolatileCharProperty(charProperty, newStaticObject, Character.MAX_VALUE);
            setVolatileIntProperty(intProperty, newStaticObject, Integer.MAX_VALUE);
            setVolatileLongProperty(longProperty, newStaticObject, Long.MAX_VALUE);
            setVolatileFloatProperty(floatProperty, newStaticObject, Float.MAX_VALUE);
            setVolatileDoubleProperty(doubleProperty, newStaticObject, Double.MAX_VALUE);
            setVolatileObjectProperty(objectProperty, newStaticObject, OBJECT_VALUE);
            getVolatileBooleanProperty(booleanProperty, newStaticObject);
            getVolatileByteProperty(byteProperty, newStaticObject);
            getVolatileShortProperty(shortProperty, newStaticObject);
            getVolatileCharProperty(charProperty, newStaticObject);
            getVolatileIntProperty(intProperty, newStaticObject);
            getVolatileLongProperty(longProperty, newStaticObject);
            getVolatileFloatProperty(floatProperty, newStaticObject);
            getVolatileDoubleProperty(doubleProperty, newStaticObject);
            getVolatileObjectProperty(objectProperty, newStaticObject);
            alignedOffset(longProperty);
        }

        void testObjectAllocatedOnContextPreInit() throws Exception {
            getVolatileBooleanProperty(booleanProperty, staticObject);
            getVolatileByteProperty(byteProperty, staticObject);
            getVolatileShortProperty(shortProperty, staticObject);
            getVolatileCharProperty(charProperty, staticObject);
            getVolatileIntProperty(intProperty, staticObject);
            getVolatileLongProperty(longProperty, staticObject);
            getVolatileFloatProperty(floatProperty, staticObject);
            getVolatileDoubleProperty(doubleProperty, staticObject);
            getVolatileObjectProperty(objectProperty, staticObject);
            alignedOffset(longProperty);
        }

        static void setVolatileBooleanProperty(StaticProperty booleanProperty, Object staticObject, boolean value) {
            booleanProperty.setBooleanVolatile(staticObject, value);
        }

        static void setVolatileByteProperty(StaticProperty byteProperty, Object staticObject, byte value) {
            byteProperty.setByteVolatile(staticObject, value);
        }

        static void setVolatileShortProperty(StaticProperty shortProperty, Object staticObject, short value) {
            shortProperty.setShortVolatile(staticObject, value);
        }

        static void setVolatileCharProperty(StaticProperty charProperty, Object staticObject, char value) {
            charProperty.setCharVolatile(staticObject, value);
        }

        static void setVolatileIntProperty(StaticProperty intProperty, Object staticObject, int value) {
            intProperty.setIntVolatile(staticObject, value);
        }

        static void setVolatileLongProperty(StaticProperty longProperty, Object staticObject, long value) {
            longProperty.setLongVolatile(staticObject, value);
        }

        static void setVolatileFloatProperty(StaticProperty floatProperty, Object staticObject, float value) {
            floatProperty.setFloatVolatile(staticObject, value);
        }

        static void setVolatileDoubleProperty(StaticProperty doubleProperty, Object staticObject, double value) {
            doubleProperty.setDoubleVolatile(staticObject, value);
        }

        static void setVolatileObjectProperty(StaticProperty objectProperty, Object staticObject, Object value) {
            objectProperty.setObjectVolatile(staticObject, value);
        }

        static void getVolatileBooleanProperty(StaticProperty booleanProperty, Object staticObject) {
            assertEquals(Boolean.TRUE, booleanProperty.getBooleanVolatile(staticObject));
        }

        static void getVolatileByteProperty(StaticProperty byteProperty, Object staticObject) {
            assertEquals(Byte.MAX_VALUE, byteProperty.getByteVolatile(staticObject));
        }

        static void getVolatileShortProperty(StaticProperty shortProperty, Object staticObject) {
            assertEquals(Short.MAX_VALUE, shortProperty.getShortVolatile(staticObject));
        }

        static void getVolatileCharProperty(StaticProperty charProperty, Object staticObject) {
            assertEquals(Character.MAX_VALUE, charProperty.getCharVolatile(staticObject));
        }

        static void getVolatileIntProperty(StaticProperty intProperty, Object staticObject) {
            assertEquals(Integer.MAX_VALUE, intProperty.getIntVolatile(staticObject));
        }

        static void getVolatileLongProperty(StaticProperty longProperty, Object staticObject) {
            assertEquals(Long.MAX_VALUE, longProperty.getLongVolatile(staticObject));
        }

        static void getVolatileFloatProperty(StaticProperty floatProperty, Object staticObject) {
            assertEquals(Float.MAX_VALUE, floatProperty.getFloatVolatile(staticObject), 0);
        }

        static void getVolatileDoubleProperty(StaticProperty doubleProperty, Object staticObject) {
            assertEquals(Double.MAX_VALUE, doubleProperty.getDoubleVolatile(staticObject), 0);
        }

        static void getVolatileObjectProperty(StaticProperty objectProperty, Object staticObject) {
            assertEquals(OBJECT_VALUE, objectProperty.getObjectVolatile(staticObject));
        }

        static void alignedOffset(StaticProperty longProperty) throws Exception {
            Field offset = StaticProperty.class.getDeclaredField("offset");
            ReflectionUtils.setAccessible(offset, true);
            assertEquals(0, ((int) offset.get(longProperty)) % 8);
        }
    }

    static class TestContext {

        final Env env;
        final StaticObjectModelTest staticObjectModelTest;
        boolean patched;
        int threadLocalActions;

        TestContext(Env env, Language language) {
            this.env = env;
            staticObjectModelTest = new StaticObjectModelTest(env, language);
        }

    }

    @TruffleLanguage.Registration(id = LANGUAGE, name = LANGUAGE, version = "1.0", contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
    public static final class Language extends TruffleLanguage<TestContext> {

        final ContextThreadLocal<Integer> threadLocal = locals.createContextThreadLocal((c, t) -> 42);
        final ContextLocal<Integer> contextLocal = locals.createContextLocal((c) -> 42);
        private static final ContextReference<TestContext> CONTEXT_REF = ContextReference.create(Language.class);
        private static final LanguageReference<Language> LANGUAGE_REF = LanguageReference.create(Language.class);

        @Override
        protected TestContext createContext(Env env) {
            assertTrue(env.isPreInitialization());
            return new TestContext(env, this);
        }

        @Override
        protected void initializeContext(TestContext context) throws Exception {
            assertTrue(context.env.isPreInitialization());

            context.env.submitThreadLocal(null, new ThreadLocalAction(true, false) {
                @Override
                protected void perform(Access access) {
                    context.threadLocalActions++;
                }
            });

            new RootNode(this) {
                @Override
                public Object execute(VirtualFrame frame) {
                    /*
                     * This should simulate common things that languages might do during context
                     * pre-intialization.
                     */
                    TruffleSafepoint.setBlockedThreadInterruptible(this, (t) -> {
                    }, null);
                    TruffleSafepoint.poll(this);
                    TruffleSafepoint.pollHere(this);

                    boolean prev = TruffleSafepoint.getCurrent().setAllowSideEffects(false);
                    TruffleSafepoint.getCurrent().setAllowSideEffects(prev);

                    if (threadLocal.get().intValue() != 42) {
                        CompilerDirectives.shouldNotReachHere("invalid context thread local");
                    }
                    if (contextLocal.get().intValue() != 42) {
                        CompilerDirectives.shouldNotReachHere("invalid context local");
                    }

                    if (CONTEXT_REF.get(null) != context) {
                        CompilerDirectives.shouldNotReachHere("invalid context reference");
                    }

                    if (LANGUAGE_REF.get(null) != Language.this) {
                        CompilerDirectives.shouldNotReachHere("invalid language reference");
                    }

                    return null;
                }

            }.getCallTarget().call();

            // No need to call `testShapeAllocatedOnContextPreInit()` here.
            // During context pre-init, it is equivalent to `testObjectAllocatedOnContextPreInit()`.
            context.staticObjectModelTest.testObjectAllocatedOnContextPreInit();
        }

        @Override
        protected boolean patchContext(TestContext context, Env newEnv) {
            assertFalse(context.patched);
            context.patched = true;

            if (CONTEXT_REF.get(null) != context) {
                CompilerDirectives.shouldNotReachHere("invalid context reference");
            }

            if (LANGUAGE_REF.get(null) != this) {
                CompilerDirectives.shouldNotReachHere("invalid language reference");
            }
            return true;
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }
    }

}
