/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage.codegen.compatibility;

import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.hosted.webimage.codegen.JSCodeGenTool;
import com.oracle.svm.hosted.webimage.codegen.LowerableResources;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.webimage.hightiercodegen.CodeBuffer;

import jdk.graal.compiler.options.OptionValues;

/**
 * Class supports operations for measuring the time from a start to an end point and print the spent
 * time.
 *
 * Multiple timers can be started, {@link #lowerMeasuringResult()} will emit JS code to print the
 * measured results.
 */
public class JSBenchmarkingCode {
    private static int timeBenchIndexS;

    private final List<Timer> timers = new ArrayList<>();

    private final JSCodeGenTool jsLTools;

    /**
     * Whether any timer code should be emitted.
     */
    private final boolean genTimer;

    public JSBenchmarkingCode(JSCodeGenTool jsLTools, OptionValues options) {
        this.jsLTools = jsLTools;
        this.genTimer = WebImageOptions.DebugOptions.GenTimingCode.getValue(options);
    }

    /**
     * Create a new timer.
     *
     * Depending on whether GenTimingCode is enabled, this returns a timer that actually emits timer
     * code or a timer that does nothing.
     *
     * @param name The name of the timer. This will be printed at the end of the program execution
     *            together with the timer value.
     */
    public Timer getTimer(String name) {
        Timer t = genTimer ? new RealTimer(jsLTools, name) : new NopTimer();
        timers.add(t);
        return t;
    }

    /**
     * Emit the print message of the time measurement.
     */
    public void lowerMeasuringResult() {
        timers.forEach(t -> t.lowerMeasuringResult("console.error"));
    }

    public void lowerInitialDefinition() {
        if (genTimer) {
            jsLTools.lowerFile(LowerableResources.TIMER);
        }
    }

    public abstract static class Timer implements AutoCloseable {
        public abstract Timer start();

        @Override
        public abstract void close();

        public abstract void lowerMeasuringResult(String logfunName);
    }

    public static final class NopTimer extends Timer {

        @Override
        public Timer start() {
            return this;
        }

        @Override
        public void close() {
        }

        @Override
        public void lowerMeasuringResult(String logfunName) {
        }
    }

    public static final class RealTimer extends Timer {
        public static final String TIME_BENCH_VAR = "timeBench_";

        public final String name;
        public final String varName;

        private final JSCodeGenTool jsLTools;

        private RealTimer(JSCodeGenTool jsLTools, String name) {
            this.jsLTools = jsLTools;
            this.name = name;
            this.varName = TIME_BENCH_VAR + timeBenchIndexS++;
        }

        private void lowerMeasuringStart() {
            CodeBuffer masm = jsLTools.getCodeBuffer();
            masm.emitText("let " + varName + " = new Timer(");
            masm.emitStringLiteral(name);
            masm.emitText(");");
            masm.emitNewLine();
            masm.emitText(varName + ".start();\n");
        }

        private void lowerMeasuringEnd() {
            jsLTools.getCodeBuffer().emitText(varName + ".stop();\n");
        }

        @Override
        public void lowerMeasuringResult(String logfunName) {
            jsLTools.getCodeBuffer().emitText(varName + ".print(" + logfunName + ");\n");
        }

        @Override
        public Timer start() {
            lowerMeasuringStart();
            return this;
        }

        @Override
        public void close() {
            this.lowerMeasuringEnd();
        }
    }
}
