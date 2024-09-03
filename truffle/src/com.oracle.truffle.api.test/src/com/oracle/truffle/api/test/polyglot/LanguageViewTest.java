/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.graalvm.polyglot.Context;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.test.AbstractParametrizedLibraryTest;

public class LanguageViewTest extends AbstractParametrizedLibraryTest {

    @Parameters(name = "{0}")
    public static List<TestRun> data() {
        return Arrays.asList(TestRun.CACHED, TestRun.UNCACHED, TestRun.DISPATCHED_CACHED, TestRun.DISPATCHED_UNCACHED);
    }

    @ExportLibrary(InteropLibrary.class)
    static class ProxyLanguageObject implements TruffleObject {

        @ExportMessage
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return ProxyLanguage.class;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        final Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return "42";
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static class OtherLanguageObject implements TruffleObject {

        @ExportMessage
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return OtherTestLanguage.class;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        final Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
            return "other";
        }

    }

    public LanguageViewTest() {
        needsInstrumentEnv = true;
    }

    @Test
    public void testDefaultLanguageView() throws UnsupportedMessageException {
        setupEnv(Context.create(), new ProxyLanguage() {
        });
        LanguageInfo l = instrumentEnv.getLanguages().get(ProxyLanguage.ID);

        // test primitive
        Object view = instrumentEnv.getLanguageView(l, "42");
        InteropLibrary viewLib = createLibrary(InteropLibrary.class, view);
        assertTrue(viewLib.hasLanguage(view));
        assertFalse(viewLib.hasMetaObject(view));
        assertFalse(viewLib.hasSourceLocation(view));
        assertEquals("42", viewLib.toDisplayString(view));

        // test value of the current language
        Object o = new ProxyLanguageObject();
        view = instrumentEnv.getLanguageView(l, o);
        assertSame(view, o);
        viewLib = createLibrary(InteropLibrary.class, view);
        assertTrue(viewLib.hasLanguage(view));
        assertSame(ProxyLanguage.class, viewLib.getLanguage(view));
        assertFalse(viewLib.hasMetaObject(view));
        assertFalse(viewLib.hasSourceLocation(view));
        assertEquals("42", viewLib.toDisplayString(view));

        // test value of a foreign language
        o = new OtherLanguageObject();
        view = instrumentEnv.getLanguageView(l, o);
        assertNotSame(view, o);
        viewLib = createLibrary(InteropLibrary.class, view);
        assertTrue(viewLib.hasLanguage(view));
        assertSame(ProxyLanguage.class, viewLib.getLanguage(view));
        assertFalse(viewLib.hasMetaObject(view));
        assertFalse(viewLib.hasSourceLocation(view));
        assertEquals("other", viewLib.toDisplayString(view));
    }

    Function<Object, Object> getLanguageView;

    @Test
    public void testLanguageViewAssertions() {
        setupEnv(Context.create(), new ProxyLanguage() {
            @Override
            protected Object getLanguageView(LanguageContext c, Object value) {
                return getLanguageView.apply(value);
            }
        });
        LanguageInfo l = instrumentEnv.getLanguages().get(ProxyLanguage.ID);

        getLanguageView = (v) -> "";
        assertAssertionError(() -> instrumentEnv.getLanguageView(l, "42"));
        getLanguageView = (v) -> new OtherLanguageObject();
        assertAssertionError(() -> instrumentEnv.getLanguageView(l, "42"));

        getLanguageView = (v) -> new ProxyLanguageObject();
        instrumentEnv.getLanguageView(l, "42"); // allowed

        assertFails(() -> instrumentEnv.getLanguageView(l, null), NullPointerException.class);
        assertFails(() -> instrumentEnv.getLanguageView(l, new Object()), ClassCastException.class);
        assertFails(() -> instrumentEnv.getLanguageView(null, ""), NullPointerException.class);
    }

}
