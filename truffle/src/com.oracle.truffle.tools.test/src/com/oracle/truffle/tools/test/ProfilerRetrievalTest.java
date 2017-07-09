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
package com.oracle.truffle.tools.test;

import java.util.Collections;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.tools.Profiler;

/**
 * Test that languages and other instruments are able to retrieve the Profiler instance.
 */
public class ProfilerRetrievalTest {

    @Test
    public void testFromLanguage() {
        Value profilerValue = Context.create(LanguageThatNeedsProfiler.ID).lookup(LanguageThatNeedsProfiler.ID, "profiler");
        Assert.assertTrue(profilerValue.asBoolean());
    }

    @Test
    public void testFromInstrument() {
        InstrumentThatNeedsProfiler.haveProfiler = false;
        Engine.newBuilder().option(InstrumentThatNeedsProfiler.ID + ".prof", "").build();
        Assert.assertTrue(InstrumentThatNeedsProfiler.haveProfiler);
    }

    @TruffleLanguage.Registration(id = LanguageThatNeedsProfiler.ID, mimeType = LanguageThatNeedsProfiler.MIME_TYPE, name = "Language That Needs Profiler", version = "1.0")
    public static class LanguageThatNeedsProfiler extends TruffleLanguage<Profiler> {

        static final String ID = "language-that-needs-profiler";
        static final String MIME_TYPE = "application/x-language-that-needs-profiler";

        @Override
        protected Profiler createContext(TruffleLanguage.Env env) {
            InstrumentInfo profilerInfo = env.getInstruments().get("profiler");
            Profiler profiler = env.lookup(profilerInfo, Profiler.class);
            Assert.assertNotNull(profiler);
            return profiler;
        }

        @Override
        protected Object getLanguageGlobal(Profiler context) {
            return null;
        }

        @Override
        protected Object lookupSymbol(Profiler context, String symbolName) {
            return "profiler".equals(symbolName) && context != null;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

    }

    @TruffleInstrument.Registration(id = InstrumentThatNeedsProfiler.ID, name = "Instrument That Needs Profiler")
    public static class InstrumentThatNeedsProfiler extends TruffleInstrument {

        static final String ID = "instrument-that-needs-profiler";
        static boolean haveProfiler;

        private final OptionKey<String> profilerKey = new OptionKey<>("");

        @Override
        protected void onCreate(TruffleInstrument.Env env) {
            Profiler profiler = env.lookup(env.getInstruments().get("profiler"), Profiler.class);
            env.getOptions().set(profilerKey, profiler.toString());
            haveProfiler = true;
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return OptionDescriptors.create(Collections.singletonList(OptionDescriptor.newBuilder(profilerKey, ID + ".prof").build()));
        }

    }

}
