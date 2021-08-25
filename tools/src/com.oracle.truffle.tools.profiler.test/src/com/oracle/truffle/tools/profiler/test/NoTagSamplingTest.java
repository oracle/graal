/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.profiler.test;

import java.util.Collection;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.tools.profiler.CPUSampler;
import com.oracle.truffle.tools.profiler.CPUSamplerData;
import com.oracle.truffle.tools.profiler.ProfilerNode;

public class NoTagSamplingTest {

    @Test
    public void testNoTagSampling() {
        Context context = Context.create(NoTagLanguage.ID);
        CPUSampler sampler = CPUSampler.find(context.getEngine());
        sampler.setCollecting(true);
        Source source = Source.newBuilder(NoTagLanguage.ID, "", "").buildLiteral();
        // Eval twice so that we compile on first call when running with compile immediately.
        context.eval(source);
        context.eval(source);
        sampler.setCollecting(false);
        final CPUSamplerData data = sampler.getData().values().iterator().next();
        final Collection<ProfilerNode<CPUSampler.Payload>> profilerNodes = data.getThreadData().values().iterator().next();
        Assert.assertEquals(1, profilerNodes.size());

    }

    public static class LILRootNode extends RootNode {

        protected LILRootNode(TruffleLanguage<?> language) {
            super(language);
        }

        @TruffleBoundary
        private static void sleepSomeTime() {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Assert.fail();
            }
        }

        @Override
        public Object execute(VirtualFrame frame) {
            for (int i = 0; i < 100; i++) {
                sleepSomeTime();
                TruffleSafepoint.poll(this);
            }
            return 42;
        }
    }

    @TruffleLanguage.Registration(id = NoTagLanguage.ID, name = NoTagLanguage.ID, version = "0.0.1")
    public static class NoTagLanguage extends ProxyLanguage {
        static final String ID = "NoTagSamplingTest_NoTagLanguage";

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return newTarget();
        }

        private RootCallTarget newTarget() {
            return Truffle.getRuntime().createCallTarget(new LILRootNode(this));
        }
    }
}
