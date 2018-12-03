/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;

import java.util.ArrayList;
import java.util.List;

public class RegexLanguageOptions {

    public static final String DUMP_AUTOMATA_NAME = RegexLanguage.ID + ".dump-automata";
    private static final OptionKey<Boolean> DUMP_AUTOMATA = new OptionKey<>(false);
    private static final String DUMP_AUTOMATA_HELP = helpWithDefault("Produce ASTs and automata in JSON, DOT (GraphViz) and LaTeX formats.", DUMP_AUTOMATA);

    public static final String STEP_EXECUTION_NAME = RegexLanguage.ID + ".step-execution";
    private static final OptionKey<Boolean> STEP_EXECUTION = new OptionKey<>(false);
    private static final String STEP_EXECUTION_HELP = helpWithDefault("Trace the execution of automata in JSON files.", STEP_EXECUTION);

    public static final String ALWAYS_EAGER_NAME = RegexLanguage.ID + ".always-eager";
    private static final OptionKey<Boolean> ALWAYS_EAGER = new OptionKey<>(false);
    private static final String ALWAYS_EAGER_HELP = helpWithDefault("Always match capture groups eagerly.", ALWAYS_EAGER);

    public static final RegexLanguageOptions DEFAULT = new RegexLanguageOptions();
    public static final OptionDescriptors OPTION_DESCRIPTORS = describeOptions();

    private final boolean dumpAutomata;
    private final boolean stepExecution;
    private final boolean alwaysEager;

    private RegexLanguageOptions() {
        this.dumpAutomata = DUMP_AUTOMATA.getDefaultValue();
        this.stepExecution = STEP_EXECUTION.getDefaultValue();
        this.alwaysEager = ALWAYS_EAGER.getDefaultValue();
    }

    public RegexLanguageOptions(OptionValues optionValues) {
        this.dumpAutomata = optionValues.get(DUMP_AUTOMATA);
        this.stepExecution = optionValues.get(STEP_EXECUTION);
        this.alwaysEager = optionValues.get(ALWAYS_EAGER);
    }

    public boolean isDumpAutomata() {
        return dumpAutomata;
    }

    public boolean isStepExecution() {
        return stepExecution;
    }

    public boolean isAlwaysEager() {
        return alwaysEager;
    }

    private static String helpWithDefault(String helpMessage, OptionKey<? extends Object> key) {
        return helpMessage + " (default:" + key.getDefaultValue() + ")";
    }

    private static OptionDescriptors describeOptions() {
        List<OptionDescriptor> options = new ArrayList<>();
        options.add(OptionDescriptor.newBuilder(DUMP_AUTOMATA, DUMP_AUTOMATA_NAME).category(OptionCategory.DEBUG).help(DUMP_AUTOMATA_HELP).build());
        options.add(OptionDescriptor.newBuilder(STEP_EXECUTION, STEP_EXECUTION_NAME).category(OptionCategory.DEBUG).help(STEP_EXECUTION_HELP).build());
        options.add(OptionDescriptor.newBuilder(ALWAYS_EAGER, ALWAYS_EAGER_NAME).category(OptionCategory.DEBUG).help(ALWAYS_EAGER_HELP).build());
        return OptionDescriptors.create(options);
    }
}
