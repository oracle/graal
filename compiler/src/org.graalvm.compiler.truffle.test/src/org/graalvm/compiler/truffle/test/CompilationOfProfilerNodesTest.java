/*
 * Copyright (c) 2011, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;


import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.tools.profiler.CPUSampler;
import org.graalvm.compiler.truffle.TruffleCompilerOptions;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;

public class CompilationOfProfilerNodesTest extends TestWithSynchronousCompiling {

    private Context context;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private CPUSampler sampler;

    @Before
    public void setup() {
        context = Context.newBuilder().out(out).err(err).build();
        sampler = context.getEngine().getInstruments().get("cpusampler").lookup(CPUSampler.class);
    }

    String defaultSourceForSampling = "ROOT(" +
            "DEFINE(foo,ROOT(BLOCK(SLEEP(1),INVALIDATE)))," +
            "DEFINE(bar,ROOT(BLOCK(STATEMENT,CALL(foo))))," +
            "LOOP(20, CALL(bar))" +
            ")";

    @Test
    @SuppressWarnings("try")
    public void testInvalidationsDontPoluteShadowStack() {
        // Test assumes that foo will be inlined into bar
        try (TruffleCompilerOptions.TruffleOptionsOverrideScope inline = TruffleCompilerOptions.overrideOptions(TruffleCompilerOptions.TruffleFunctionInlining, true)) {
            sampler.setStackLimit(3);
            sampler.setCollecting(true);
            final Source source = Source.create(InstrumentationTestLanguage.ID, defaultSourceForSampling);
            context.eval(InstrumentationTestLanguage.ID, source.getCharacters());

            Assert.assertFalse(sampler.hasStackOverflowed());
        }
    }
}
