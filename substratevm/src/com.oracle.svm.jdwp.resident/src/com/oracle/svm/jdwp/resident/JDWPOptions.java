/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.resident;

import com.oracle.svm.interpreter.InterpreterOptions;

import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;

import com.oracle.svm.interpreter.debug.DebuggerEventsFeature;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import org.graalvm.collections.EconomicMap;

public final class JDWPOptions {

    @Option(help = "Include JDWP support in the native executable", type = OptionType.Expert)//
    public static final HostedOptionKey<Boolean> JDWP = new HostedOptionKey<>(false) {

        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, Boolean oldValue, Boolean newValue) {
            super.onValueUpdate(values, oldValue, newValue);
            if (newValue) {
                InterpreterOptions.DebuggerWithInterpreter.update(values, true);
                DebuggerEventsFeature.DebuggerOptions.DebuggerEvents.update(values, true);
            }
        }
    };

    @Option(help = "Specify JDWP options")//
    public static final RuntimeOptionKey<String> JDWPOptions = new RuntimeOptionKey<>(null);

    @Option(help = "Enable JDWP specifc logging", type = OptionType.Expert) //
    public static final RuntimeOptionKey<Boolean> JDWPTrace = new RuntimeOptionKey<>(false);
}
