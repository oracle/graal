/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.benchmark;

import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.runtime.SLFunction;

public class SLPartialEvaluationBenchmarks {

    public static class ExampleOneBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected OptimizedCallTarget createCallTarget() {
            PolyglotEngine engine = PolyglotEngine.newBuilder().build();
            engine.eval(Source.newBuilder("function plus(x, y) { return x + y; }").name("plus.sl").mimeType(SLLanguage.MIME_TYPE).build());
            SLFunction function = engine.findGlobalSymbol("plus").as(SLFunction.class);
            return (OptimizedCallTarget) function.getCallTarget();
        }

    }

    public static class ExampleTwoBenchmark extends PartialEvaluationBenchmark {

        @Override
        protected OptimizedCallTarget createCallTarget() {
            PolyglotEngine engine = PolyglotEngine.newBuilder().build();
            engine.eval(Source.newBuilder("function minus(x, y) { return x - y; }").name("minus.sl").mimeType(SLLanguage.MIME_TYPE).build());
            SLFunction function = engine.findGlobalSymbol("minus").as(SLFunction.class);
            return (OptimizedCallTarget) function.getCallTarget();
        }

    }

}
