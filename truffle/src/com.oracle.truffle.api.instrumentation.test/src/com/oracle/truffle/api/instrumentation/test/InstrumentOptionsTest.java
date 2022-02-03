/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Test;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.api.test.polyglot.ProxyInstrument;

public class InstrumentOptionsTest extends AbstractPolyglotTest {

    static final String DEFAULT_OPTION_VALUE = "default";

    public InstrumentOptionsTest() {
        cleanupOnSetup = false; // allow multiple contexts active
        needsLanguageEnv = true;
        needsInstrumentEnv = true;
    }

    static class TestInstrument extends ProxyInstrument {

        private boolean created;

        @Override
        protected void onCreate(Env env) {
            super.onCreate(env);
            created = true;
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new EngineOptionsOptionDescriptors();
        }

        @Override
        protected OptionDescriptors getContextOptionDescriptors() {
            return new ContextOptionsOptionDescriptors();
        }
    }

    @Test
    public void testOptions() {
        TruffleContext tc;
        // no options
        setupEnv(Context.newBuilder(), new TestInstrument());
        tc = languageEnv.getContext();
        assertEquals(DEFAULT_OPTION_VALUE, instrumentEnv.getOptions().get(EngineOptions.EngineOption1));
        assertEquals(DEFAULT_OPTION_VALUE, instrumentEnv.getOptions(tc).get(ContextOptions.ContextOption1));
        context.close();

        // engine + context option
        setupEnv(Context.newBuilder().//
                        option(ProxyInstrument.ID + ".EngineOption1", "engineValue").//
                        option(ProxyInstrument.ID + ".ContextOption1", "contextValue"), new TestInstrument());
        tc = languageEnv.getContext();
        assertEquals("engineValue", instrumentEnv.getOptions().get(EngineOptions.EngineOption1));
        assertEquals("contextValue", instrumentEnv.getOptions(tc).get(ContextOptions.ContextOption1));
        context.close();

        // context option only
        setupEnv(Context.newBuilder().option(ProxyInstrument.ID + ".ContextOption1", "contextValue"), new TestInstrument());
        tc = languageEnv.getContext();
        assertEquals("default", instrumentEnv.getOptions().get(EngineOptions.EngineOption1));
        assertEquals("contextValue", instrumentEnv.getOptions(tc).get(ContextOptions.ContextOption1));
        context.close();

        // engine option only
        setupEnv(Context.newBuilder().option(ProxyInstrument.ID + ".EngineOption1", "engineValue"), new TestInstrument());
        tc = languageEnv.getContext();
        assertEquals("engineValue", instrumentEnv.getOptions().get(EngineOptions.EngineOption1));
        assertEquals("default", instrumentEnv.getOptions(tc).get(ContextOptions.ContextOption1));
        context.close();

        // engine option + multi context values
        Engine engine = Engine.newBuilder().option(ProxyInstrument.ID + ".EngineOption1", "engineValue").build();
        setupEnv(Context.newBuilder().engine(engine).option(ProxyInstrument.ID + ".ContextOption1", "contextValue1"), new TestInstrument());
        tc = languageEnv.getContext();
        assertEquals("engineValue", instrumentEnv.getOptions().get(EngineOptions.EngineOption1));
        assertEquals("contextValue1", instrumentEnv.getOptions(tc).get(ContextOptions.ContextOption1));
        context.close();

        setupEnv(Context.newBuilder().engine(engine).option(ProxyInstrument.ID + ".ContextOption1", "contextValue2"), new TestInstrument());
        tc = languageEnv.getContext();
        assertEquals("engineValue", instrumentEnv.getOptions().get(EngineOptions.EngineOption1));
        assertEquals("contextValue2", instrumentEnv.getOptions(tc).get(ContextOptions.ContextOption1));
        context.close();
        engine.close();

        // context option in engine
        engine = Engine.newBuilder().//
                        option(ProxyInstrument.ID + ".ContextOption1", "contextValue").//
                        option(ProxyInstrument.ID + ".EngineOption1", "engineValue").build();
        // no override
        setupEnv(Context.newBuilder().engine(engine), new TestInstrument());
        tc = languageEnv.getContext();
        assertEquals("engineValue", instrumentEnv.getOptions().get(EngineOptions.EngineOption1));
        assertEquals("contextValue", instrumentEnv.getOptions(tc).get(ContextOptions.ContextOption1));
        context.close();

        // override
        setupEnv(Context.newBuilder().engine(engine).option(ProxyInstrument.ID + ".ContextOption1", "contextValueOverride"), new TestInstrument());
        tc = languageEnv.getContext();
        assertEquals("engineValue", instrumentEnv.getOptions().get(EngineOptions.EngineOption1));
        assertEquals("contextValueOverride", instrumentEnv.getOptions(tc).get(ContextOptions.ContextOption1));
        context.close();

        engine.close();
    }

    @Test
    public void testInitializeByContextOption() {
        TestInstrument instrument = new TestInstrument();
        ProxyInstrument.setDelegate(instrument);
        Engine engine = Engine.create();
        Context c = Context.newBuilder().engine(engine).option(ProxyInstrument.ID + ".ContextOption1", "contextValue").build();
        assertTrue(instrument.created);
        c.close();
        engine.close();
    }

    @Option.Group(ProxyInstrument.ID)
    static class EngineOptions {

        @Option(category = OptionCategory.USER, help = "HelpText", stability = OptionStability.STABLE) //
        static final OptionKey<String> EngineOption1 = new OptionKey<>(DEFAULT_OPTION_VALUE);

    }

    @Option.Group(ProxyInstrument.ID)
    static class ContextOptions {

        @Option(category = OptionCategory.USER, help = "HelpText", stability = OptionStability.STABLE) //
        static final OptionKey<String> ContextOption1 = new OptionKey<>(DEFAULT_OPTION_VALUE);
    }

    @Test
    public void testInvalidOptions() {
        ProxyInstrument.setDelegate(new InvalidInstrument());
        Context.Builder contextBuilder = Context.newBuilder().option(ProxyInstrument.ID + ".ContextOption1", "contextValue");
        assertFails(() -> contextBuilder.build(), PolyglotException.class, (e) -> {
            assertTrue(e.isInternalError());
        });
    }

    /*
     * Instrument is invalid because it specifies duplicated options between engine and context
     * options.
     */
    static class InvalidInstrument extends ProxyInstrument {

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new ContextOptionsOptionDescriptors();
        }

        @Override
        protected OptionDescriptors getContextOptionDescriptors() {
            return new ContextOptionsOptionDescriptors();
        }
    }

}
