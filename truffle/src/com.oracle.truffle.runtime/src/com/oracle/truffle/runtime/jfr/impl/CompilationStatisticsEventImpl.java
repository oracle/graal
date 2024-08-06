/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime.jfr.impl;

import com.oracle.truffle.runtime.jfr.CompilationStatisticsEvent;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.DataAmount;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Period;
import jdk.jfr.StackTrace;
import jdk.jfr.Timespan;
import jdk.jfr.Unsigned;

@Name("jdk.graal.compiler.truffle.CompilerStatistics")
@Category("Truffle Compiler")
@Label("Compiler Statistics")
@Description("Truffe Compiler Statistics")
@Period("1s")
@StackTrace(false)
class CompilationStatisticsEventImpl extends Event implements CompilationStatisticsEvent {

    @Label("Compiled Methods") @Description("Compiled Methods") @Unsigned public long compiledMethods;

    @Label("Bailouts") @Description("Bailouts") @Unsigned public long bailouts;

    @Label("Invalidated Compilations") @Description("Invalidated Compilations") @Unsigned public long invalidations;

    @Label("Compilation Resulting Size") @Description("Compilation Resulting Size") @DataAmount @Unsigned public long compiledCodeSize;

    @Label("Total Time") @Description("Total Time") @Timespan(Timespan.MILLISECONDS) public long totalTime;

    @Label("Peak Time") @Description("Peak Time") @Timespan(Timespan.MILLISECONDS) public long peakTime;

    @Override
    public void setCompiledMethods(long compiledMethodsCount) {
        this.compiledMethods = compiledMethodsCount;
    }

    @Override
    public void setBailouts(long bailoutsCount) {
        this.bailouts = bailoutsCount;
    }

    @Override
    public void setInvalidations(long invalidationsCount) {
        this.invalidations = invalidationsCount;
    }

    @Override
    public void setCompiledCodeSize(long codeSize) {
        this.compiledCodeSize = codeSize;
    }

    @Override
    public void setTotalTime(long time) {
        this.totalTime = time;
    }

    @Override
    public void setPeakTime(long time) {
        this.peakTime = time;
    }

    @Override
    public void publish() {
        commit();
    }
}
