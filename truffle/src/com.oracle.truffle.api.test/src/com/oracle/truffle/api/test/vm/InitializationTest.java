/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertNull;

import org.junit.After;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.vm.PolyglotEngine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Bug report validating test.
 * <p>
 * It has been reported that calling {@link Env#importSymbol(java.lang.String)} in
 * {@link TruffleLanguage TruffleLanguage.createContext(env)} yields a {@link NullPointerException}.
 * <p>
 * The other report was related to specifying an abstract language class in the RootNode and
 * problems with debugging later on. That is what the other part of this test - once it obtains
 * Debugger instance simulates.
 */
public class InitializationTest {

    private PolyglotEngine vm;

    @After
    public void dispose() {
        if (vm != null) {
            vm.dispose();
            vm = null;
        }
    }

    @Test
    public void checkPostInitializationInRunMethod() throws Exception {
        vm = PolyglotEngine.newBuilder().build();

        Source source = Source.newBuilder("accessProbeForAbstractLanguage text").mimeType("application/x-abstrlang").name("sample.txt").build();

        assertEquals("Properly evaluated", 42, vm.eval(source).get());

        Object global = vm.findGlobalSymbol("MyEnv").get();
        assertNotNull(global);
        assertTrue(global instanceof MyEnv);
        MyEnv env = (MyEnv) global;
        assertEquals("Post initialization hook called", 1, env.cnt);
    }

    private static final class MMRootNode extends RootNode {
        @Child ANode node;

        private final SourceSection sourceSection;

        MMRootNode(TestLanguage lang, SourceSection ss) {
            super(lang);
            node = new ANode(42);
            this.sourceSection = ss;
            adoptChildren();
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.constant();
        }
    }

    private static class ANode extends Node {
        private final int constant;

        ANode(int constant) {
            this.constant = constant;
        }

        @Override
        public SourceSection getSourceSection() {
            return getRootNode().getSourceSection();
        }

        Object constant() {
            return constant;
        }
    }

    private static final class MyEnv {
        int cnt;

        void doInit() {
            cnt++;
        }
    }

    private abstract static class AbstractLanguage extends TruffleLanguage<MyEnv> {
    }

    @TruffleLanguage.Registration(mimeType = "application/x-abstrlang", name = "AbstrLang", version = "0.1")
    public static final class TestLanguage extends AbstractLanguage {

        @Override
        protected MyEnv createContext(Env env) {
            assertNull("Not defined symbol", env.importSymbol("unknown"));
            MyEnv myEnv = new MyEnv();
            env.exportSymbol("MyEnv", myEnv);
            return myEnv;
        }

        @Override
        protected void initializeContext(MyEnv context) throws Exception {
            context.doInit();
        }

        @Override
        protected CallTarget parse(ParsingRequest env) {
            return Truffle.getRuntime().createCallTarget(new MMRootNode(this, env.getSource().createSection(1)));
        }

        @Override
        protected Object getLanguageGlobal(MyEnv context) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            throw new UnsupportedOperationException();
        }

    }
}
