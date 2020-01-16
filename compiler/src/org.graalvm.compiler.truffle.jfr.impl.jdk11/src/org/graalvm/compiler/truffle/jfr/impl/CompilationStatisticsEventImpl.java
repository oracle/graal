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
package org.graalvm.compiler.truffle.jfr.impl;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.DataAmount;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Period;
import jdk.jfr.StackTrace;
import jdk.jfr.Timespan;
import jdk.jfr.Unsigned;
import org.graalvm.compiler.truffle.jfr.CompilationStatisticsEvent;

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
