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

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.common.option.CommonOptionParser.BooleanOptionFormat;
import com.oracle.svm.common.option.CommonOptionParser.OptionParseResult;
import com.oracle.svm.core.IsolateArgumentParser;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.properties.RuntimePropertyParser;
import com.oracle.svm.core.util.ImageHeapMap;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.options.OptionsParser;

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
    private static final String NORMAL_OPTION_PREFIX = "-XX:";

    /**
     * The prefix for Graal style options available in an application based on Substrate VM.
     */
    private static final String GRAAL_OPTION_PREFIX = "-Djdk.graal.";

    /**
     * The legacy prefix for Graal style options available in an application based on Substrate VM.
     */
    private static final String LEGACY_GRAAL_OPTION_PREFIX = "-Dgraal.";

    /**
     * The prefix for XOptions available in an application based on Substrate VM.
     */
    static final String X_OPTION_PREFIX = "-X";

    /**
     * Parse and consume all standard options and system properties supported by Substrate VM. The
     * returned array contains all arguments that were not consumed, i.e., were not recognized as
     * options.
     *
     * Note that this logic must be in sync with {@link IsolateArgumentParser#shouldParseArguments}.
     */
    public static String[] parseAndConsumeAllOptions(String[] initialArgs, boolean ignoreUnrecognized) {
        String[] args = initialArgs;
        if (SubstrateOptions.ParseRuntimeOptions.getValue()) {
            args = RuntimeOptionParser.singleton().parse(args, NORMAL_OPTION_PREFIX, GRAAL_OPTION_PREFIX, LEGACY_GRAAL_OPTION_PREFIX, X_OPTION_PREFIX, ignoreUnrecognized);
            args = RuntimePropertyParser.parse(args);
        } else if (RuntimeCompilation.isEnabled() && SubstrateOptions.supportCompileInIsolates() && IsolateArgumentParser.isCompilationIsolate()) {
            /*
             * Compilation isolates always need to parse the Native Image options that the main
             * isolate passes to them.
             */
            args = RuntimeOptionParser.singleton().parse(args, NORMAL_OPTION_PREFIX, null, null, X_OPTION_PREFIX, ignoreUnrecognized);
        }
        return args;
    }

    /** All reachable options. */
    public EconomicMap<String, OptionDescriptor> options = ImageHeapMap.create();

    @Platforms(Platform.HOSTED_ONLY.class)
    public void addDescriptor(OptionDescriptor optionDescriptor) {
        options.putIfAbsent(optionDescriptor.getName(), optionDescriptor);
    }

    public Optional<OptionDescriptor> getDescriptor(String optionName) {
        return Optional.ofNullable(options.get(optionName));
    }

    /**
     * Returns the singleton instance that is created during native image generation and stored in
     * the {@link ImageSingletons}.
     */
    @Fold
    public static RuntimeOptionParser singleton() {
        return ImageSingletons.lookup(RuntimeOptionParser.class);
    }

    /**
     * Parses {@code args} and sets/updates runtime option values for the elements matching a
     * runtime option.
     *
     * @param args arguments to be parsed
     * @param normalOptionPrefix prefix for normal Native Image runtime options
     * @param graalOptionPrefix prefix for Graal-style options
     * @param xOptionPrefix prefix for X-options
     * @return elements in {@code args} that do not match any runtime options
     * @throws IllegalArgumentException if an element in {@code args} is invalid. The parse error is
     *             described by {@link Throwable#getMessage()}.
     */
    public String[] parse(String[] args, String normalOptionPrefix, String graalOptionPrefix, String legacyGraalOptionPrefix, String xOptionPrefix, boolean ignoreUnrecognized) {
        int newIdx = 0;
        EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
        for (int oldIdx = 0; oldIdx < args.length; oldIdx++) {
            String arg = args[oldIdx];
            if (arg.startsWith(normalOptionPrefix)) {
                parseOptionAtRuntime(arg, normalOptionPrefix, BooleanOptionFormat.PLUS_MINUS, values, ignoreUnrecognized);
            } else if (graalOptionPrefix != null && arg.startsWith(graalOptionPrefix)) {
                parseOptionAtRuntime(arg, graalOptionPrefix, BooleanOptionFormat.NAME_VALUE, values, ignoreUnrecognized);
            } else if (legacyGraalOptionPrefix != null && arg.startsWith(legacyGraalOptionPrefix)) {
                parseOptionAtRuntime(arg, legacyGraalOptionPrefix, BooleanOptionFormat.NAME_VALUE, values, ignoreUnrecognized);
            } else if (xOptionPrefix != null && arg.startsWith(xOptionPrefix) && XOptions.parse(arg.substring(xOptionPrefix.length()), values)) {
                // option value was already parsed and added to the map
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
     * @throws IllegalArgumentException if {@code arg} is invalid. The parse error is described by
     *             {@link Throwable#getMessage()}.
     */
    private void parseOptionAtRuntime(String arg, String optionPrefix, BooleanOptionFormat booleanOptionFormat, EconomicMap<OptionKey<?>, Object> values, boolean ignoreUnrecognized) {
        Predicate<OptionKey<?>> isHosted = optionKey -> false;
        OptionParseResult parseResult = SubstrateOptionsParser.parseOption(options, isHosted, arg.substring(optionPrefix.length()), values, optionPrefix, booleanOptionFormat);
        if (parseResult.printFlags() || parseResult.printFlagsWithExtraHelp()) {
            SubstrateOptionsParser.printFlags(d -> parseResult.matchesFlags(d, d.getOptionKey() instanceof RuntimeOptionKey),
                            options, optionPrefix, Log.logStream(), parseResult.printFlagsWithExtraHelp());
            System.exit(0);
        }
        if (!parseResult.isValid()) {
            if (parseResult.optionUnrecognized() && ignoreUnrecognized) {
                return;
            }
            throw new IllegalArgumentException(parseResult.getError());
        }

        // Print a warning if the option is deprecated.
        OptionKey<?> option = parseResult.getOptionKey();
        OptionDescriptor descriptor = option.getDescriptor();
        if (descriptor != null && descriptor.isDeprecated()) {
            Log log = Log.log();
            // Checkstyle: Allow raw info or warning printing - begin
            log.string("Warning: Option '").string(descriptor.getName()).string("' is deprecated and might be removed from future versions");
            // Checkstyle: Allow raw info or warning printing - end
            String deprecationMessage = descriptor.getDeprecationMessage();
            if (deprecationMessage != null && !deprecationMessage.isEmpty()) {
                log.string(": ").string(deprecationMessage);
            }
            log.newline();
        }
    }

    public OptionKey<?> lookupOption(String name, Collection<OptionDescriptor> fuzzyMatches) {
        OptionDescriptor desc = options.get(name);
        OptionKey<?> option;
        if (desc == null) {
            if (fuzzyMatches != null) {
                OptionsParser.collectFuzzyMatches(options.getValues(), name, fuzzyMatches);
            }
            option = null;
        } else {
            option = desc.getOptionKey();
        }
        return option;
    }

    public Iterable<OptionDescriptor> getDescriptors() {
        return options.getValues();
    }
}
