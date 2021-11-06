/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.thermometer;

public class ThermometerConfig {

    private final int samplingPeriod;
    private final int reportingPeriod;
    private final int interruptAfterStable;
    private final String iterationLocation;
    private final String logFile;

    public ThermometerConfig(int samplingPeriod, int reportingPeriod, int interruptAfterStable, String iterationLocation, String logFile) {
        this.samplingPeriod = samplingPeriod;
        this.reportingPeriod = reportingPeriod;
        this.interruptAfterStable = interruptAfterStable;
        this.iterationLocation = iterationLocation;
        this.logFile = logFile;
    }

    public int getSamplingPeriod() {
        return samplingPeriod;
    }

    public int getReportingPeriod() {
        return reportingPeriod;
    }

    public int getInterruptAfterStable() {
        return interruptAfterStable;
    }

    public String getIterationLocation() {
        return iterationLocation;
    }

    public String getLogFile() {
        return logFile;
    }
}
