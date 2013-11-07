/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle;

import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

public class TimedCompilationPolicy extends DefaultCompilationPolicy {

    @Override
    public boolean shouldCompile(CompilationProfile profile) {
        if (super.shouldCompile(profile)) {
            long timestamp = System.nanoTime();
            long prevTimestamp = profile.getPreviousTimestamp();
            long timespan = (timestamp - prevTimestamp);
            if (timespan < (TruffleCompilationDecisionTime.getValue())) {
                return true;
            }
            // TODO shouldCompile should not modify the compilation profile
            // maybe introduce another method?
            profile.reportTiminingFailed(timestamp);
            if (TruffleCompilationDecisionTimePrintFail.getValue()) {
                // Checkstyle: stop
                System.out.println(profile.getName() + ": timespan  " + (timespan / 1000000) + " ms  larger than threshold");
                // Checkstyle: resume
            }
        }
        return false;
    }

}
