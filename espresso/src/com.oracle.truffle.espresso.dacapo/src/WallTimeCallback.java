/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.dacapo.harness.Callback;
import org.dacapo.harness.CommandLineArgs;
import org.dacapo.harness.TestHarness;

import java.io.PrintStream;

/**
 * For every iteration, prints the total elapsed wall time since the start of the run.
 */
public final class WallTimeCallback extends Callback {

    private long startMillis = Long.MIN_VALUE;
    private long currentIterationMillis = Long.MIN_VALUE;

    public WallTimeCallback(CommandLineArgs args) {
        super(args);
    }

    @Override
    public void start(String benchmark) {
        if (startMillis == Long.MIN_VALUE) {
            startMillis = System.currentTimeMillis();
        }
        super.start(benchmark);
    }

    @Override
    public void stop() {
        super.stop();
        currentIterationMillis = System.currentTimeMillis();
    }

    @Override
    public void complete(String benchmark, boolean valid) {
        super.complete(benchmark, valid);
        long sinceStartMillis = currentIterationMillis - startMillis;
        PrintStream err = System.err;
        err.print("===== DaCapo " + TestHarness.getBuildVersion() + " " + benchmark);
        err.print(" walltime " + (this.iterations + 1) + " : ");
        err.print(sinceStartMillis + " msec ");
        err.println("=====");
        err.flush();
    }
}
