/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;

@Registration(id = ThermometerInstrument.ID, name = ThermometerInstrument.NAME, version = ThermometerInstrument.VERSION, services = Thermometer.class)
public class ThermometerInstrument extends TruffleInstrument {

    public static final String ID = "thermometer";
    public static final String NAME = "Optimization Thermometer";
    static final String VERSION = "0.1.0";

    // @formatter:off
    @Option(name = "", help = "Enable Thermometer", category = OptionCategory.USER, stability = OptionStability.STABLE)
    static final OptionKey<Boolean> Enabled = new OptionKey<>(false);
    @Option(help = "Sampling period (ms)", category = OptionCategory.USER, stability = OptionStability.STABLE)
    static final OptionKey<Integer> SamplingPeriod = new OptionKey<>(10);
    @Option(help = "Reporting period (ms)", category = OptionCategory.USER, stability = OptionStability.STABLE)
    static final OptionKey<Integer> ReportingPeriod = new OptionKey<>(333);
    @Option(help = "Source location (file:line) to sample iterations per second", category = OptionCategory.USER, stability = OptionStability.STABLE)
    static final OptionKey<String> IterationPoint = new OptionKey<>("");
    @Option(help = "Log file", category = OptionCategory.USER, stability = OptionStability.STABLE)
    static final OptionKey<String> LogFile = new OptionKey<>("");
    // @formatter:on

    private Thermometer thermometer;

    @Override
    protected void onCreate(Env env) {
        final OptionValues options = env.getOptions();

        if (Enabled.getValue(options)) {
            thermometer = new Thermometer(env);
            env.registerService(thermometer);
            thermometer.start(new ThermometerConfig(
                    options.get(SamplingPeriod),
                    options.get(ReportingPeriod),
                    getStringOrNullOption(options, IterationPoint),
                    getStringOrNullOption(options, LogFile)));
        }
    }

    private static String getStringOrNullOption(OptionValues options, OptionKey<String> iterationPoint) {
        if (options.get(iterationPoint).isEmpty()) {
            return null;
        } else {
            return options.get(iterationPoint);
        }
    }

    @Override
    protected void onDispose(Env env) {
        if (thermometer != null) {
            thermometer.close();
        }
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new ThermometerInstrumentOptionDescriptors();
    }

}
