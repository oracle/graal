/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.vm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;

public class ImplicitExplicitExportTest {
    private static Thread mainThread;
    private PolyglotEngine vm;

    @Before
    public void initializeVM() {
        mainThread = Thread.currentThread();
        vm = PolyglotEngine.newBuilder().executor(Executors.newSingleThreadExecutor()).build();
        assertTrue("Found " + L1 + " language", vm.getLanguages().containsKey(L1));
        assertTrue("Found " + L2 + " language", vm.getLanguages().containsKey(L2));
        assertTrue("Found " + L3 + " language", vm.getLanguages().containsKey(L3));
    }

    @After
    public void cleanThread() {
        mainThread = null;
        if (vm != null) {
            vm.dispose();
        }
    }

    @Test
    public void explicitExportFound() {
        // @formatter:off
        vm.eval(Source.newBuilder("explicit.ahoj=42").name("Fourty two").mimeType(L1).build());
        Object ret = vm.eval(Source.newBuilder("return=ahoj").name("Return").mimeType(L3).build()
        ).get();
        // @formatter:on
        assertEquals("42", ret);
    }

    @Test
    public void implicitExportFound() {
        // @formatter:off
        vm.eval(Source.newBuilder("implicit.ahoj=42").name("Fourty two").mimeType(L1).build()
        );
        Object ret = vm.eval(Source.newBuilder("return=ahoj").name("Return").mimeType(L3).build()
        ).get();
        // @formatter:on
        assertEquals("42", ret);
    }

    @Test
    public void implicitExportFoundDirect() throws Exception {
        // @formatter:off
        vm.eval(
            Source.newBuilder("implicit.ahoj=42").
                name("Fourty two").
                mimeType(L1).
                build()
        );
        Object ret = vm.findGlobalSymbol("ahoj").get();
        // @formatter:on
        assertEquals("42", ret);
    }

    @Test
    public void explicitExportPreferred2() throws Exception {
        // @formatter:off
        vm.eval(Source.newBuilder("implicit.ahoj=42").name("Fourty two").mimeType(L1).build()
        );
        vm.eval(Source.newBuilder("explicit.ahoj=43").name("Fourty three").mimeType(L2).build()
        );
        Object ret = vm.eval(Source.newBuilder("return=ahoj").name("Return").mimeType(L3).build()
        ).get();
        // @formatter:on
        assertEquals("Explicit import from L2 is used", "43", ret);
        assertEquals("Global symbol is also 43", "43", vm.findGlobalSymbol("ahoj").get());
    }

    @Test
    public void explicitExportPreferredInIterator() throws Exception {
        vm.eval(Source.newBuilder("implicit.ahoj=42").name("Fourty two").mimeType(L1).build());
        vm.eval(Source.newBuilder("explicit.ahoj=43").name("Fourty three").mimeType(L2).build());
        Iterable<PolyglotEngine.Value> iterable = vm.findGlobalSymbols("ahoj");
        assertExplicitOverImplicit(iterable);
        assertExplicitOverImplicit(iterable);
        assertExplicitOverImplicit(iterable);
    }

    @Test
    public void explicitExportPreferredInEnvIterator() throws Exception {
        vm.eval(Source.newBuilder("implicit.ahoj=42").name("Fourty two").mimeType(L1).build());
        vm.eval(Source.newBuilder("explicit.ahoj=43").name("Fourty three").mimeType(L2).build());
        Object ret = vm.eval(Source.newBuilder("returnall=ahoj").name("Return").mimeType(L3).build()).get();
        assertEquals("Explicit import from L2 is used first, then L1 value", "4342", ret);
    }

    private static void assertExplicitOverImplicit(Iterable<PolyglotEngine.Value> iterable) {
        Iterator<PolyglotEngine.Value> it = iterable.iterator();
        assertTrue("Has more", it.hasNext());
        assertEquals("Explicit first", "43", it.next().get());
        assertTrue("Has one more", it.hasNext());
        assertEquals("Implicit next first", "42", it.next().get());
        assertFalse("No more elements", it.hasNext());
    }

    @Test
    public void explicitExportPreferredDirect() throws Exception {
        // @formatter:off
        vm.eval(Source.newBuilder("implicit.ahoj=42").name("Fourty two").mimeType(L1).build());
        vm.eval(Source.newBuilder("explicit.ahoj=43").name("Fourty three").mimeType(L2).build());
        Object ret = vm.findGlobalSymbol("ahoj").get();
        // @formatter:on
        assertEquals("Explicit import from L2 is used", "43", ret);
        assertEquals("Global symbol is also 43", "43", vm.findGlobalSymbol("ahoj").get());
    }

    @Test
    public void explicitExportPreferred1() throws Exception {
        // @formatter:off
        vm.eval(Source.newBuilder("explicit.ahoj=43").name("Fourty three").mimeType(L1).build()
        );
        vm.eval(Source.newBuilder("implicit.ahoj=42").name("Fourty two").mimeType(L2).build()
        );
        Object ret = vm.eval(Source.newBuilder("return=ahoj").name("Return").mimeType(L3).build()
        ).get();
        // @formatter:on
        assertEquals("Explicit import from L2 is used", "43", ret);
        assertEquals("Global symbol is also 43", "43", vm.findGlobalSymbol("ahoj").execute().get());
    }

    static final class Ctx implements TruffleObject {
        static final Set<Ctx> disposed = new HashSet<>();

        final Map<String, String> explicit = new HashMap<>();
        final Map<String, String> implicit = new HashMap<>();
        final Env env;

        Ctx(Env env) {
            this.env = env;
        }

        void dispose() {
            assertFalse("No prior dispose", disposed.contains(this));
            disposed.add(this);
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return null;
        }
    }

    private abstract static class AbstractExportImportLanguage extends TruffleLanguage<Ctx> {

        @Override
        protected Ctx createContext(Env env) {
            if (mainThread != null) {
                assertNotEquals("Should run asynchronously", Thread.currentThread(), mainThread);
            }
            return new Ctx(env);
        }

        @Override
        protected void disposeContext(Ctx context) {
            context.dispose();
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            Source code = request.getSource();
            if (code.getCodeSequence().toString().startsWith("parse=")) {
                throw new IOException(code.getCodeSequence().toString().substring(6));
            }
            return Truffle.getRuntime().createCallTarget(new ValueRootNode(code, this));
        }

        @Override
        protected Object findExportedSymbol(Ctx context, String globalName, boolean onlyExplicit) {
            assertNotEquals("Should run asynchronously", Thread.currentThread(), mainThread);
            if (onlyExplicit && context.explicit.containsKey(globalName)) {
                return context.explicit.get(globalName);
            }
            if (!onlyExplicit && context.implicit.containsKey(globalName)) {
                return context.implicit.get(globalName);
            }
            return null;
        }

        @Override
        protected Object getLanguageGlobal(Ctx context) {
            return context;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

        @SuppressWarnings("deprecation")
        @TruffleBoundary
        private Object importExport(Source code) {
            assertNotEquals("Should run asynchronously", Thread.currentThread(), mainThread);
            Ctx ctx = getContextReference().get();
            Properties p = new Properties();
            try (Reader r = code.getReader()) {
                p.load(r);
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
            Enumeration<Object> en = p.keys();
            while (en.hasMoreElements()) {
                Object n = en.nextElement();
                if (n instanceof String) {
                    String k = (String) n;
                    if (k.startsWith("explicit.")) {
                        ctx.explicit.put(k.substring(9), p.getProperty(k));
                    }
                    if (k.startsWith("implicit.")) {
                        ctx.implicit.put(k.substring(9), p.getProperty(k));
                    }
                    if (k.equals("return")) {
                        return ctx.env.importSymbol(p.getProperty(k));
                    }
                    if (k.equals("returnall")) {
                        StringBuilder sb = new StringBuilder();
                        for (Object obj : ctx.env.importSymbols(p.getProperty(k))) {
                            sb.append(obj);
                        }
                        return sb.toString();
                    }
                    if (k.equals("throwInteropException")) {
                        throw UnsupportedTypeException.raise(new Object[0]);
                    }

                }
            }
            return null;
        }
    }

    private static final class ValueRootNode extends RootNode {
        private final Source code;
        private final AbstractExportImportLanguage language;

        private ValueRootNode(Source code, AbstractExportImportLanguage language) {
            super(language);
            this.code = code;
            this.language = language;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return language.importExport(code);
        }
    }

    public static final String L1 = "application/x-test-import-export-1";
    public static final String L1_ALT = "application/alt-test-import-export-1";
    static final String L2 = "application/x-test-import-export-2";
    static final String L3 = "application/x-test-import-export-3";

    @TruffleLanguage.Registration(mimeType = {L1, L1_ALT}, name = "ImportExport1", version = "0")
    public static final class ExportImportLanguage1 extends AbstractExportImportLanguage {

        public ExportImportLanguage1() {
        }

        @Override
        protected String toString(Ctx ctx, Object value) {
            if (value instanceof String) {
                try {
                    int number = Integer.parseInt((String) value);
                    return number + ": Int";
                } catch (NumberFormatException ex) {
                    // go on
                }
            }
            return Objects.toString(value);
        }
    }

    @TruffleLanguage.Registration(mimeType = L2, name = "ImportExport2", version = "0")
    public static final class ExportImportLanguage2 extends AbstractExportImportLanguage {

        public ExportImportLanguage2() {
        }

        @Override
        protected String toString(Ctx ctx, Object value) {
            if (value instanceof String) {
                try {
                    double number = Double.parseDouble((String) value);
                    return number + ": Double";
                } catch (NumberFormatException ex) {
                    // go on
                }
            }
            return Objects.toString(value);
        }
    }

    @TruffleLanguage.Registration(mimeType = {L3, L3 + "alt"}, name = "ImportExport3", version = "0")
    public static final class ExportImportLanguage3 extends AbstractExportImportLanguage {

        public ExportImportLanguage3() {
        }
    }

}
