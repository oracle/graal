/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.*;

import java.io.*;
import java.util.*;

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.debug.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.vm.*;

public class ImplicitExplicitExportTest {
    private TruffleVM vm;

    @Before
    public void initializeVM() {
        vm = TruffleVM.newVM().build();
        assertTrue("Found " + L1 + " language", vm.getLanguages().containsKey(L1));
        assertTrue("Found " + L2 + " language", vm.getLanguages().containsKey(L2));
        assertTrue("Found " + L3 + " language", vm.getLanguages().containsKey(L3));
    }

    @Test
    public void explicitExportFound() throws IOException {
        // @formatter:off
        vm.eval(L1,
            "explicit.ahoj=42"
        );
        Object ret = vm.eval(L3,
            "return=ahoj"
        );
        // @formatter:on
        assertEquals("42", ret);
    }

    @Test
    public void implicitExportFound() throws IOException {
        // @formatter:off
        vm.eval(L1,
            "implicit.ahoj=42"
        );
        Object ret = vm.eval(L3,
            "return=ahoj"
        );
        // @formatter:on
        assertEquals("42", ret);
    }

    @Test
    public void explicitExportPreferred2() throws IOException {
        // @formatter:off
        vm.eval(L1,
            "implicit.ahoj=42"
        );
        vm.eval(L2,
            "explicit.ahoj=43"
        );
        Object ret = vm.eval(L3,
            "return=ahoj"
        );
        // @formatter:on
        assertEquals("Explicit import from L2 is used", "43", ret);
        assertEquals("Global symbol is also 43", "43", vm.findGlobalSymbol("ahoj").invoke(null));
    }

    @Test
    public void explicitExportPreferred1() throws IOException {
        // @formatter:off
        vm.eval(L1,
            "explicit.ahoj=43"
        );
        vm.eval(L2,
            "implicit.ahoj=42"
        );
        Object ret = vm.eval(L3,
            "return=ahoj"
        );
        // @formatter:on
        assertEquals("Explicit import from L2 is used", "43", ret);
        assertEquals("Global symbol is also 43", "43", vm.findGlobalSymbol("ahoj").invoke(null));
    }

    private abstract static class AbstractExportImportLanguage extends TruffleLanguage {
        protected AbstractExportImportLanguage(Env env) {
            super(env);
        }

        private final Map<String, String> explicit = new HashMap<>();
        private final Map<String, String> implicit = new HashMap<>();

        @Override
        protected Object eval(Source code) throws IOException {
            Properties p = new Properties();
            try (Reader r = code.getReader()) {
                p.load(r);
            }
            Enumeration<Object> en = p.keys();
            while (en.hasMoreElements()) {
                Object n = en.nextElement();
                if (n instanceof String) {
                    String k = (String) n;
                    if (k.startsWith("explicit.")) {
                        explicit.put(k.substring(9), p.getProperty(k));
                    }
                    if (k.startsWith("implicit.")) {
                        implicit.put(k.substring(9), p.getProperty(k));
                    }
                    if (k.equals("return")) {
                        return env().importSymbol(p.getProperty(k));
                    }
                }
            }
            return null;
        }

        @Override
        protected Object findExportedSymbol(String globalName, boolean onlyExplicit) {
            if (explicit.containsKey(globalName)) {
                return explicit.get(globalName);
            }
            if (!onlyExplicit && implicit.containsKey(globalName)) {
                return implicit.get(globalName);
            }
            return null;
        }

        @Override
        protected Object getLanguageGlobal() {
            return null;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            return false;
        }

        @Override
        protected ToolSupportProvider getToolSupport() {
            return null;
        }

        @Override
        protected DebugSupportProvider getDebugSupport() {
            return null;
        }
    }

    private static final String L1 = "application/x-test-import-export-1";
    private static final String L2 = "application/x-test-import-export-2";
    private static final String L3 = "application/x-test-import-export-3";

    @TruffleLanguage.Registration(mimeType = L1, name = "ImportExport1", version = "0")
    public static final class ExportImportLanguage1 extends AbstractExportImportLanguage {
        public ExportImportLanguage1(Env env) {
            super(env);
        }
    }

    @TruffleLanguage.Registration(mimeType = L2, name = "ImportExport2", version = "0")
    public static final class ExportImportLanguage2 extends AbstractExportImportLanguage {
        public ExportImportLanguage2(Env env) {
            super(env);
        }
    }

    @TruffleLanguage.Registration(mimeType = L3, name = "ImportExport3", version = "0")
    public static final class ExportImportLanguage3 extends AbstractExportImportLanguage {
        public ExportImportLanguage3(Env env) {
            super(env);
        }
    }

}
