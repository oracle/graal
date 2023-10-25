/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.test.polyglot.ProxyInstrument;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;

/**
 * Test that scope variables are accessed as efficiently as language implemented variables.
 */
public class NodeLibrarySLCompilerTest extends PartialEvaluationTest {

    @Before
    public void setup() {
        setupContext(Context.newBuilder());
        getContext().initialize("sl");
    }

    /**
     * Test that store of a sum of two variables in the language implementation and in an instrument
     * using scope, is PE equal.
     */
    @Test
    public void testScopeSum() {
        doInstrument(getContext().getEngine());
    }

    private void doInstrument(Engine engine) {
        ProxyInstrument instrument = new ProxyInstrument();
        ProxyInstrument.setDelegate(instrument);
        instrument.setOnCreate((env) -> {
            env.getInstrumenter().attachExecutionEventFactory(
                            SourceSectionFilter.newBuilder().tagIs(DebuggerTags.AlwaysHalt.class).build(),
                            context -> new SumVarsExecNode(context.getInstrumentedNode()));
            try {
                CallTarget langSumTarget = env.parse(Source.newBuilder("sl", "function test(x, y) {z = 0; z = x + y; return z;}", "sumLang.sl").build());
                RootNode langSum = ((RootCallTarget) langSumTarget).getRootNode();

                CallTarget instrSumTarget = env.parse(Source.newBuilder("sl", "function test(x, y) {z = 0; debugger; return z;}", "sumInstr.sl").build());
                RootNode instrSum = ((RootCallTarget) instrSumTarget).getRootNode();

                assertPartialEvalEquals(langSum, instrSum, new Object[]{1, 2});
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        engine.getInstruments().get(ProxyInstrument.ID).lookup(ProxyInstrument.Initialize.class);
    }

    private static final class SumVarsExecNode extends ExecutionEventNode {

        private final Node instrumentedNode;
        @Node.Child private NodeLibrary nodeLibrary;
        @Node.Child private InteropLibrary interop = InteropLibrary.getFactory().createDispatched(3);

        SumVarsExecNode(Node instrumentedNode) {
            this.instrumentedNode = instrumentedNode;
            this.nodeLibrary = NodeLibrary.getFactory().create(instrumentedNode);
        }

        @Override
        protected void onEnter(VirtualFrame frame) {
            try {
                Object scope = nodeLibrary.getScope(instrumentedNode, frame, true);
                assert interop.hasMembers(scope);
                long x = interop.asLong(interop.readMember(scope, "x"));
                long y = interop.asLong(interop.readMember(scope, "y"));
                interop.writeMember(scope, "z", x + y);
            } catch (UnsupportedMessageException | UnknownIdentifierException | UnsupportedTypeException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

    }

}
