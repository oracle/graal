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
package com.oracle.truffle.api.instrumentation.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.test.ReflectionUtils;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

/**
 * Base class for instrumentation tests.
 */
public abstract class AbstractInstrumentationTest extends AbstractPolyglotTest {

    protected Engine engine;

    protected final ByteArrayOutputStream out = new ByteArrayOutputStream();
    protected final ByteArrayOutputStream err = new ByteArrayOutputStream();

    Context newContext() {
        Context.Builder builder = Context.newBuilder().allowAllAccess(true);
        builder.engine(getEngine());
        return builder.build();
    }

    @Before
    public void setup() {
        setupEnv(newContext());
    }

    protected final Engine getEngine() {
        if (engine == null) {
            engine = Engine.newBuilder().out(out).err(err).build();
        }
        return engine;
    }

    protected String run(Source source) throws IOException {
        this.out.reset();
        this.err.reset();
        context.eval(source);
        this.out.flush();
        this.err.flush();
        String outText = getOut();
        return outText;
    }

    protected static boolean containsTag(String[] tags, String tag) {
        for (int i = 0; i < tags.length; i++) {
            if (tags[i] == tag) {
                return true;
            }
        }
        return false;
    }

    protected String run(String source) throws IOException {
        return run(lines(source));
    }

    protected void assertEvalOut(String source, String output) throws IOException {
        assertEvalOut(lines(source), output);
    }

    protected void assertEvalOut(Source source, String output) throws IOException {
        String actual = run(source);
        String error = getErr();
        if (!error.isEmpty()) {
            throw new AssertionError("Unexpected error printed: %s" + error);
        }
        if (!actual.equals(output)) {
            Assert.assertEquals(output, actual);
        }
    }

    protected final String getOut() {
        return new String(out.toByteArray());
    }

    protected final String getErr() {
        return new String(err.toByteArray());
    }

    protected static com.oracle.truffle.api.source.Source linesImpl(String... lines) {
        StringBuilder b = new StringBuilder();
        for (String line : lines) {
            b.append(line);
            b.append("\n");
        }
        return com.oracle.truffle.api.source.Source.newBuilder(InstrumentationTestLanguage.ID, b.toString(), null).name("unknown").build();
    }

    protected static Source lines(String... lines) {
        StringBuilder b = new StringBuilder();
        for (String line : lines) {
            b.append(line);
            b.append("\n");
        }
        return Source.create(InstrumentationTestLanguage.ID, b.toString());
    }

    @SuppressWarnings("static-method")
    protected final boolean isInitialized(Instrument instrument) {
        Object instrumentImpl = ReflectionUtils.getField(instrument, "impl");
        return (Boolean) ReflectionUtils.getField(instrumentImpl, "initialized");
    }

    @SuppressWarnings("static-method")
    protected final boolean isCreated(Instrument instrument) {
        Object instrumentImpl = ReflectionUtils.getField(instrument, "impl");
        return (Boolean) ReflectionUtils.getField(instrumentImpl, "created");
    }

    protected final void assureEnabled(Instrument instrument) {
        instrument.lookup(Object.class);
        Assert.assertTrue("Not enabled, instrument does not provide service Object.class", isCreated(instrument));
    }

    @SuppressWarnings("static-method")
    protected final com.oracle.truffle.api.source.Source getSourceImpl(Source source) {
        return sourceToImpl(source);
    }

    static final com.oracle.truffle.api.source.Source sourceToImpl(Source source) {
        return (com.oracle.truffle.api.source.Source) ReflectionUtils.getField(source, "impl");
    }

    @SuppressWarnings("static-method")
    protected final com.oracle.truffle.api.source.SourceSection getSectionImpl(SourceSection sourceSection) {
        return (com.oracle.truffle.api.source.SourceSection) ReflectionUtils.getField(sourceSection, "impl");
    }

    protected final SourceSection createSection(Source source, int charIndex, int length) {
        com.oracle.truffle.api.source.Source sourceImpl = getSourceImpl(source);
        com.oracle.truffle.api.source.SourceSection sectionImpl = sourceImpl.createSection(charIndex, length);
        return TestAccessor.ACCESSOR.engineAccess().createSourceSection(getCurrentVM(), source, sectionImpl);
    }

    private Object getCurrentVM() {
        return ReflectionUtils.getField(engine, "impl");
    }

    @After
    public void teardown() {
        cleanup();
        if (engine != null) {
            engine.close();
            engine = null;
        }
        InstrumentationTestLanguage.envConfig = new HashMap<>();
    }

    private static final class TestAccessor extends Accessor {

        EngineSupport engineAccess() {
            return engineSupport();
        }

        static TestAccessor ACCESSOR = new TestAccessor();

    }
}
