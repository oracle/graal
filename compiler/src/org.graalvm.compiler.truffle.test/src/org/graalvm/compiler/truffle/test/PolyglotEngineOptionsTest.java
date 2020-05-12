/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionStability;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.runtime.SLFunction;

public class PolyglotEngineOptionsTest extends TestWithSynchronousCompiling {

    @Test
    public void testVisibleOptions() {
        Engine engine = Engine.create();
        OptionDescriptor compilationThreshold = engine.getOptions().get("engine.CompilationThreshold");
        Assert.assertNotNull(compilationThreshold);
        engine.close();
    }

    @Test
    public void testCompilationThreshold() {
        // does not work with a different inline cache size.
        Assert.assertEquals(2, SLFunction.INLINE_CACHE_SIZE);

        // doWhile must run isolated and should not affect other compilation thresholds
        OptimizedCallTarget target = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(42));
        Runnable doWhile = () -> testCompilationThreshold(50, "50", null);
        testCompilationThreshold(42, "42", doWhile); // test default value
        testCompilationThreshold(target.getOptionValue(PolyglotCompilerOptions.CompilationThreshold), null, doWhile);
        testCompilationThreshold(2, "2", doWhile); // test default value
    }

    @Test
    public void testPolyglotCompilerOptionsAreUsed() {
        setupContext("engine.CompilationThreshold", "27", //
                        "engine.TraceCompilation", "true", //
                        "engine.TraceCompilationDetails", "true", //
                        "engine.Inlining", "false", //
                        "engine.Splitting", "false", //
                        "engine.Mode", "latency");
        OptimizedCallTarget target = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(42));
        Assert.assertEquals(27, (int) target.getOptionValue(PolyglotCompilerOptions.CompilationThreshold));
        Assert.assertEquals(true, target.getOptionValue(PolyglotCompilerOptions.TraceCompilation));
        Assert.assertEquals(true, target.getOptionValue(PolyglotCompilerOptions.TraceCompilationDetails));
        Assert.assertEquals(false, target.getOptionValue(PolyglotCompilerOptions.Inlining));
        Assert.assertEquals(false, target.getOptionValue(PolyglotCompilerOptions.Splitting));
        Assert.assertEquals(PolyglotCompilerOptions.EngineModeEnum.LATENCY, target.getOptionValue(PolyglotCompilerOptions.Mode));
    }

    @Test
    public void testEngineModeLatency() {
        Assert.assertEquals(OptionStability.STABLE, Engine.create().getOptions().get("engine.Mode").getStability());

        setupContext("engine.Mode", "latency");
        OptimizedCallTarget target = (OptimizedCallTarget) Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(42));
        Assert.assertEquals(PolyglotCompilerOptions.EngineModeEnum.LATENCY, target.getOptionValue(PolyglotCompilerOptions.Mode));
        Assert.assertEquals(false, target.engine.inlining);
        Assert.assertEquals(false, target.engine.splitting);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseUnknownMode() {
        Context.newBuilder() //
                        .allowExperimentalOptions(true) //
                        .option("engine.Mode", "anUnknownMode").build();
    }

    private void testCompilationThreshold(int iterations, String compilationThresholdOption, Runnable doWhile) {
        Context ctx = setupContext(compilationThresholdOption == null ? new String[0] : new String[]{"engine.CompilationThreshold", compilationThresholdOption});
        ctx.eval("sl", "function test() {}");
        SLFunction test = SLLanguage.getCurrentContext().getFunctionRegistry().getFunction("test");

        Assert.assertFalse(isExecuteCompiled(test));
        for (int i = 0; i < iterations - 1; i++) {
            Assert.assertFalse(isExecuteCompiled(test));
            test.getCallTarget().call();
        }
        if (doWhile != null) {
            doWhile.run();
        }
        Assert.assertFalse(isExecuteCompiled(test));
        test.getCallTarget().call();
        Assert.assertTrue(isExecuteCompiled(test));
        test.getCallTarget().call();
        Assert.assertTrue(isExecuteCompiled(test));
    }

    private static boolean isExecuteCompiled(SLFunction value) {
        return ((OptimizedCallTarget) value.getCallTarget()).isValid();
    }

}
