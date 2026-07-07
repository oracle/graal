/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.guest.staging.option;

import java.io.PrintStream;
import java.util.Optional;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.guest.staging.util.ImageHeapMap;
import com.oracle.svm.shared.meta.GuestFold;
import com.oracle.svm.shared.option.CommonOptionParser.BooleanOptionFormat;
import com.oracle.svm.shared.option.CommonOptionParser.OptionParseResult;
import com.oracle.svm.shared.option.SubstrateOptionsParser;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionKey;

/**
 * Option parser to be used by an application that runs on Substrate VM. The list of options that
 * are available is collected during native image generation.
 *
 * There is no requirement to use this class, you can also implement your own option parsing and
 * then set the values of options manually.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
public final class RuntimeOptionParser {
    public static final String X_OPTION_PREFIX = "-X";

    /** All reachable options. */
    private final EconomicMap<String, OptionDescriptor> options = ImageHeapMap.createNonLayeredMap();

    /**
     * Returns the singleton instance that is created during native image generation and stored in
     * the {@link ImageSingletons}.
     */
    @GuestFold
    public static RuntimeOptionParser singleton() {
        return ImageSingletons.lookup(RuntimeOptionParser.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void addDescriptor(OptionDescriptor optionDescriptor) {
        options.putIfAbsent(optionDescriptor.getName(), optionDescriptor);
    }

    public Optional<OptionDescriptor> getDescriptor(String optionName) {
        return Optional.ofNullable(options.get(optionName));
    }

    public Iterable<OptionDescriptor> getDescriptors() {
        return options.getValues();
    }

    /**
     * Parses one runtime option into {@code values}. Startup policy and side effects are handled by
     * the caller.
     */
    public OptionParseResult parseOption(String arg, String optionPrefix, BooleanOptionFormat booleanOptionFormat, EconomicMap<OptionKey<?>, Object> values) {
        Predicate<OptionKey<?>> isHosted = optionKey -> false;
        return SubstrateOptionsParser.parseOption(options, isHosted, arg.substring(optionPrefix.length()), values, optionPrefix, booleanOptionFormat);
    }

    /** Prints the flags selected by {@code parseResult}. Process exit policy belongs to the caller. */
    public void printFlags(OptionParseResult parseResult, String optionPrefix, PrintStream out) {
        SubstrateOptionsParser.printFlags(d -> parseResult.matchesFlags(d, d.getOptionKey() instanceof RuntimeOptionKey),
                        options, optionPrefix, out, parseResult.printFlagsWithExtraHelp());
    }

    /** Applies a batch of parsed option values to the guest-owned runtime option state. */
    public void update(EconomicMap<OptionKey<?>, Object> values) {
        if (!values.isEmpty()) {
            RuntimeOptionValues.singleton().update(values);
        }
    }
}
