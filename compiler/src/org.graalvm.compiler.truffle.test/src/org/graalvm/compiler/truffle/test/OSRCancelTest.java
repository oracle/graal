/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import static org.junit.Assert.assertTrue;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;

public class OSRCancelTest {
    @Test
    public void testCancelWithMaterialization() throws InterruptedException {
        Context context = Context.create();
        ExecutorService service = Executors.newFixedThreadPool(1);
        Semaphore semaphore = new Semaphore(0);
        // we submit a harmful infinite script to the executor
        Future<Value> future = service.submit(() -> {
            context.enter();
            semaphore.release();
            try {
                return context.eval(LANGUAGE, "");
            } finally {
                context.leave();
            }
        });
        reachedCompiledCode = false;

        /* OSR should happen here. */
        int time = 0;
        while (!reachedCompiledCode) {
            Thread.sleep(10);
            if (time > 2000) {
                // timeout 20 seconds should be enough to compile something.
                throw new AssertionError("compilation timeout");
            }
            time++;
        }
        semaphore.acquireUninterruptibly();
        context.close(true);

        try {
            future.get();
        } catch (ExecutionException e) {
            PolyglotException pe = (PolyglotException) e.getCause();
            assertTrue(pe.isCancelled());
        }
    }

    private static volatile boolean reachedCompiledCode;

    private static final String LANGUAGE = "ForceCloseTestLanguage";

    @Registration(id = LANGUAGE, name = LANGUAGE)
    public static class TestLanguage extends TruffleLanguage<Env> {

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return Truffle.getRuntime().createCallTarget(new RootNode(this) {

                @Child TestInstrumentableNode loop = new TestLoopNode();

                @Override
                public Object execute(VirtualFrame frame) {
                    loop.execute(frame);
                    return "";
                }
            });
        }

    }

    @GenerateWrapper
    abstract static class TestInstrumentableNode extends Node implements InstrumentableNode {

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        abstract void execute(VirtualFrame frame);

        @Override
        public WrapperNode createWrapper(ProbeNode probe) {
            return new TestInstrumentableNodeWrapper(this, probe);
        }

    }

    @GenerateWrapper
    static class TestExpression extends TestInstrumentableNode implements RepeatingNode {

        @Override
        void execute(VirtualFrame frame) {
        }

        @Override
        public WrapperNode createWrapper(ProbeNode probe) {
            return new TestExpressionWrapper(this, probe);
        }

        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            if (CompilerDirectives.inCompiledCode()) {
                reachedCompiledCode = true;
            }
            return true;
        }
    }

    static class TestMaterializedLoopNode extends TestInstrumentableNode {

        @Child private LoopNode loop;

        TestMaterializedLoopNode() {
            this.loop = Truffle.getRuntime().createLoopNode(new TestExpression());
        }

        @Override
        void execute(VirtualFrame frame) {
            loop.execute(frame);
        }

    }

    static class TestLoopNode extends TestInstrumentableNode {

        @Child private LoopNode loop;

        TestLoopNode() {
            this.loop = Truffle.getRuntime().createLoopNode(new TestExpression());
        }

        @Override
        public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
            return new TestMaterializedLoopNode();
        }

        @Override
        void execute(VirtualFrame frame) {
            loop.execute(frame);
        }

    }

}
