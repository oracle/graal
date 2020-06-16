/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.option;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.options.OptionsParser;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.SubstrateOptionsParser.BooleanOptionFormat;
import com.oracle.svm.core.option.SubstrateOptionsParser.OptionParseResult;
import com.oracle.svm.core.properties.RuntimePropertyParser;

/**
 * Option parser to be used by an application that runs on Substrate VM. The list of options that
 * are available is collected during native image generation.
 *
 * There is no requirement to use this class, you can also implement your own option parsing and
 * then set the values of options manually.
 */
public final class RuntimeOptionParser {

    /**
     * The suggested prefix for all VM options available in an application based on Substrate VM.
     */
    private static final String DEFAULT_OPTION_PREFIX = "-XX:";

    /**
     * The prefix for Graal style options available in an application based on Substrate VM.
     */
    private static final String GRAAL_OPTION_PREFIX = "-Dgraal.";

    /**
     * Parse and consume all standard options and system properties supported by Substrate VM. The
     * returned array contains all arguments that were not consumed, i.e., were not recognized as
     * options.
     */
    public static String[] parseAndConsumeAllOptions(String[] initialArgs) {
        String[] args = initialArgs;
        if (SubstrateOptions.ParseRuntimeOptions.getValue()) {
            args = RuntimeOptionParser.singleton().parse(args, DEFAULT_OPTION_PREFIX, BooleanOptionFormat.PLUS_MINUS, true);
            args = RuntimeOptionParser.singleton().parse(args, GRAAL_OPTION_PREFIX, BooleanOptionFormat.NAME_VALUE, true);
            args = XOptions.singleton().parse(args, true);
            args = RuntimePropertyParser.parse(args);

            RuntimeOptionParser.singleton().notifyOptionsParsed();
        }
        return args;
    }

    /** All reachable options. */
    private final SortedMap<String, OptionDescriptor> sortedOptions;
    private ArrayList<OptionsParsedListener> optionsParsedListeners;

    public Optional<OptionDescriptor> getDescriptor(String optionName) {
        return Optional.ofNullable(sortedOptions.get(optionName));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public RuntimeOptionParser() {
        sortedOptions = new TreeMap<>();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public boolean updateRuntimeOptions(Set<OptionDescriptor> newRuntimeOptions) {
        boolean result = false;
        for (OptionDescriptor descriptor : newRuntimeOptions) {
            String name = descriptor.getName();
            if (!sortedOptions.containsKey(name)) {
                sortedOptions.put(name, descriptor);
                result = true;
            } else {
                assert descriptor == sortedOptions.get(name);
            }
        }
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerOptionsParsedListener(OptionsParsedListener listener) {
        if (optionsParsedListeners == null) {
            optionsParsedListeners = new ArrayList<>();
        }
        optionsParsedListeners.add(listener);
    }

    /**
     * Returns the singleton instance that is created during native image generation and stored in
     * the {@link ImageSingletons}.
     */
    public static RuntimeOptionParser singleton() {
        return ImageSingletons.lookup(RuntimeOptionParser.class);
    }

    /**
     * Parses {@code args} and sets/updates runtime option values for the elements matching a
     * runtime option. Arguments for boolean options are expected to be in
     * {@link BooleanOptionFormat#PLUS_MINUS} format.
     *
     * @param args arguments to be parsed
     * @param optionPrefix prefix for the options
     * @return elements in {@code args} that do not match any runtime options
     * @throws IllegalArgumentException if an element in {@code args} matches a runtime option but
     *             has an invalid format. The parse error is described by
     *             {@link Throwable#getMessage()}.
     */
    public String[] parse(String[] args, String optionPrefix) {
        return parse(args, optionPrefix, BooleanOptionFormat.PLUS_MINUS, false);
    }

    /**
     * Parses {@code args} and sets/updates runtime option values for the elements matching a
     * runtime option.
     *
     * @param args arguments to be parsed
     * @param optionPrefix prefix for the options
     * @param systemExitOnError determines whether to call {@link System#exit(int)} if any element
     *            in {@code args} matches a runtime option but has an invalid format
     * @return elements in {@code args} that do not match any runtime options
     * @throws IllegalArgumentException if an element in {@code args} is invalid and
     *             {@code systemExitOnError == false}. The parse error is described by
     *             {@link Throwable#getMessage()}.
     */
    public String[] parse(String[] args, String optionPrefix, BooleanOptionFormat booleanOptionFormat, boolean systemExitOnError) {
        int newIdx = 0;
        EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
        for (int oldIdx = 0; oldIdx < args.length; oldIdx++) {
            String arg = args[oldIdx];
            if (arg.startsWith(optionPrefix)) {
                parseOptionAtRuntime(arg, optionPrefix, booleanOptionFormat, values, systemExitOnError);
            } else {
                assert newIdx <= oldIdx;
                args[newIdx] = arg;
                newIdx += 1;
            }
        }
        if (!values.isEmpty()) {
            RuntimeOptionValues.singleton().update(values);
        }
        if (newIdx == args.length) {
            /* We can be allocation free and just return the original arguments. */
            return args;
        } else {
            return Arrays.copyOf(args, newIdx);
        }
    }

    /**
     * Parse one option at runtime and set its value.
     *
     * @param arg argument to be parsed
     * @param optionPrefix prefix for the runtime option
     * @param systemExitOnError determines whether to call {@link System#exit(int)} if {@code arg}
     *            is an invalid option
     * @throws IllegalArgumentException if {@code arg} is invalid and
     *             {@code systemExitOnError == false}. The parse error is described by
     *             {@link Throwable#getMessage()}.
     */
    private void parseOptionAtRuntime(String arg, String optionPrefix, BooleanOptionFormat booleanOptionFormat, EconomicMap<OptionKey<?>, Object> values, boolean systemExitOnError) {
        OptionParseResult parseResult = SubstrateOptionsParser.parseOption(sortedOptions, arg.substring(optionPrefix.length()), values, optionPrefix, booleanOptionFormat);
        if (parseResult.printFlags() || parseResult.printFlagsWithExtraHelp()) {
            SubstrateOptionsParser.printFlags(parseResult::matchesFlagsRuntime, sortedOptions, optionPrefix, Log.logStream(), parseResult.printFlagsWithExtraHelp());
            System.exit(0);
        }
        if (!parseResult.isValid()) {
            if (systemExitOnError) {
                Log.logStream().println("error: " + parseResult.getError());
                System.exit(1);
            }
            throw new IllegalArgumentException(parseResult.getError());
        }
    }

    public OptionKey<?> lookupOption(String name, Collection<OptionDescriptor> fuzzyMatches) {
        OptionDescriptor desc = sortedOptions.get(name);
        OptionKey<?> option;
        if (desc == null) {
            if (fuzzyMatches != null) {
                OptionsParser.collectFuzzyMatches(sortedOptions.values(), name, fuzzyMatches);
            }
            option = null;
        } else {
            option = desc.getOptionKey();
        }
        return option;
    }

    public Collection<OptionDescriptor> getDescriptors() {
        return sortedOptions.values();
    }

    private void notifyOptionsParsed() {
        if (optionsParsedListeners != null) {
            for (OptionsParsedListener listener : optionsParsedListeners) {
                listener.onOptionsParsed();
            }
        }
    }

    public interface OptionsParsedListener {
        void onOptionsParsed();
    }
}
