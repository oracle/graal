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
package com.oracle.truffle.api.vm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.lang.reflect.Field;

import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrument.ASTProber;
import com.oracle.truffle.api.instrument.EventHandlerNode;
import com.oracle.truffle.api.instrument.Instrumenter;
import com.oracle.truffle.api.instrument.Probe;
import com.oracle.truffle.api.instrument.StandardSyntaxTag;
import com.oracle.truffle.api.instrument.Visualizer;
import com.oracle.truffle.api.instrument.WrapperNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

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

    @Test
    public void accessProbeForAbstractLanguage() throws IOException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        PolyglotEngine vm = PolyglotEngine.newBuilder().build();

        final Field field = PolyglotEngine.class.getDeclaredField("instrumenter");
        field.setAccessible(true);
        final Instrumenter instrumenter = (Instrumenter) field.get(vm);
        instrumenter.registerASTProber(new ASTProber() {

            public void probeAST(final Instrumenter inst, RootNode startNode) {
                startNode.accept(new NodeVisitor() {

                    public boolean visit(Node node) {

                        if (node instanceof ANode) {
                            inst.probe(node).tagAs(StandardSyntaxTag.STATEMENT, null);
                        }
                        return true;
                    }
                });
            }
        });

        Source source = Source.fromText("accessProbeForAbstractLanguage text", "accessProbeForAbstractLanguage").withMimeType("application/x-abstrlang");

        assertEquals(vm.eval(source).get(), 1);

        vm.dispose();
    }

    private static final class MMRootNode extends RootNode {
        @Child ANode node;

        MMRootNode(SourceSection ss) {
            super(AbstractLanguage.class, ss, null);
            node = new ANode(42);
            adoptChildren();
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

    private static class ANodeWrapper extends ANode implements WrapperNode {
        @Child ANode child;
        @Child private EventHandlerNode eventHandlerNode;

        ANodeWrapper(ANode node) {
            super(1);  // dummy
            this.child = node;
        }

        @Override
        public Node getChild() {
            return child;
        }

        @Override
        public Probe getProbe() {
            return eventHandlerNode.getProbe();
        }

        @Override
        public void insertEventHandlerNode(EventHandlerNode eventHandler) {
            this.eventHandlerNode = eventHandler;
        }

        @Override
        public String instrumentationInfo() {
            throw new UnsupportedOperationException();
        }
    }

    private abstract static class AbstractLanguage extends TruffleLanguage<Object> {
    }

    @TruffleLanguage.Registration(mimeType = "application/x-abstrlang", name = "AbstrLang", version = "0.1")
    public static final class TestLanguage extends AbstractLanguage {
        public static final TestLanguage INSTANCE = new TestLanguage();

        @Override
        protected Object createContext(Env env) {
            assertNull("Not defined symbol", env.importSymbol("unknown"));
            return env;
        }

        @Override
        protected CallTarget parse(Source code, Node context, String... argumentNames) throws IOException {
            return Truffle.getRuntime().createCallTarget(new MMRootNode(code.createSection("1st line", 1)));
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
        public Object evalInContext(Source source, Node node, MaterializedFrame mFrame) {
            throw new UnsupportedOperationException();
        }

        @SuppressWarnings("deprecation")
        @Override
        public Visualizer getVisualizer() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected boolean isInstrumentable(Node node) {
            return node instanceof ANode;
        }

        @Override
        protected WrapperNode createWrapperNode(Node node) {
            return node instanceof ANode ? new ANodeWrapper((ANode) node) : null;
        }
    }
}
