/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugSupportException;
import com.oracle.truffle.api.debug.DebugSupportProvider;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.ExecutionEvent;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.EventConsumer;
import com.oracle.truffle.api.vm.TruffleVM;
import java.io.IOException;

/**
 * Bug report validating test.
 * <p>
 * It has been reported that calling {@link Env#importSymbol(java.lang.String)} in
 * {@link TruffleLanguage TruffleLanguage.createContext(env)} yields a {@link NullPointerException}.
 * <p>
 */
public class InitializationTest {
    @Test
    public void accessProbeForAbstractLanguage() throws IOException {
        final Debugger[] arr = {null};
        TruffleVM vm = TruffleVM.newVM().onEvent(new EventConsumer<ExecutionEvent>(ExecutionEvent.class) {
            @Override
            protected void on(ExecutionEvent event) {
                arr[0] = event.getDebugger();
            }
        }).build();

        Source source = Source.fromText("any text", "any text").withMimeType("application/x-abstrlang");

        vm.eval(source);

        assertNotNull("Debugger found", arr[0]);

        Debugger d = arr[0];
        Breakpoint b = d.setLineBreakpoint(0, source.createLineLocation(1), true);
        b.setCondition("true");

        vm.eval(source);
    }

    private static final class MMRootNode extends RootNode {
        @Child ANode node;

        MMRootNode() {
            super(AbstractLanguage.class, null, null);
            node = new ANode(42);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return node.constant();
        }
    }

    private static final class ANode extends Node {
        private final int constant;

        public ANode(int constant) {
            this.constant = constant;
        }

        Object constant() {
            return constant;
        }

    }

    private abstract static class AbstractLanguage extends TruffleLanguage<Object> {
    }

    @TruffleLanguage.Registration(mimeType = "application/x-abstrlang", name = "AbstrLang", version = "0.1")
    public static final class TestLanguage extends AbstractLanguage implements DebugSupportProvider {
        public static final TestLanguage INSTANCE = new TestLanguage();

        @Override
        protected Object createContext(Env env) {
            assertNull("Not defined symbol", env.importSymbol("unknown"));
            return env;
        }

        @Override
        protected CallTarget parse(Source code, Node context, String... argumentNames) throws IOException {
            return Truffle.getRuntime().createCallTarget(new MMRootNode());
        }

        @Override
        protected Object findExportedSymbol(Object context, String globalName, boolean onlyExplicit) {
            return null;
        }

        @Override
        protected Object getLanguageGlobal(Object context) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected ToolSupportProvider getToolSupport() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected DebugSupportProvider getDebugSupport() {
            return this;
        }

        @Override
        public Object evalInContext(Source source, Node node, MaterializedFrame mFrame) throws DebugSupportException {
            throw new UnsupportedOperationException();
        }

        @Override
        public AdvancedInstrumentRootFactory createAdvancedInstrumentRootFactory(String expr, AdvancedInstrumentResultListener resultListener) throws DebugSupportException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Visualizer getVisualizer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void enableASTProbing(ASTProber astProber) {
            throw new UnsupportedOperationException();
        }
    }
}
