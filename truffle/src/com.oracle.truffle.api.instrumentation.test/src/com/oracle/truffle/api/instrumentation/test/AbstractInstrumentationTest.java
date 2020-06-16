/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
        return TestAccessor.ACCESSOR.engineAccess().createSourceSection(getPolyglotEngine(), source, sectionImpl);
    }

    private Object getPolyglotEngine() {
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

        static final TestAccessor ACCESSOR = new TestAccessor();

    }
}
