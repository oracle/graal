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
package com.oracle.svm.hosted.option;

import static com.oracle.svm.common.option.CommonOptionParser.BooleanOptionFormat.PLUS_MINUS;
import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionDescriptors;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;

public class HostedOptionParser implements HostedOptionProvider {

    private final List<String> arguments;
    private final EconomicMap<OptionKey<?>, Object> hostedValues = OptionValues.newOptionMap();
    private final EconomicMap<OptionKey<?>, Object> runtimeValues = OptionValues.newOptionMap();
    private final EconomicMap<String, OptionDescriptor> allHostedOptions = EconomicMap.create();
    private final EconomicMap<String, OptionDescriptor> allRuntimeOptions = EconomicMap.create();

    public HostedOptionParser(ClassLoader imageClassLoader, List<String> arguments) {
        this.arguments = Collections.unmodifiableList(arguments);
        collectOptions(ServiceLoader.load(OptionDescriptors.class, imageClassLoader), allHostedOptions, allRuntimeOptions);
    }

    public static void collectOptions(ServiceLoader<OptionDescriptors> optionDescriptors, EconomicMap<String, OptionDescriptor> allHostedOptions,
                    EconomicMap<String, OptionDescriptor> allRuntimeOptions) {
        SubstrateOptionsParser.collectOptions(optionDescriptors, descriptor -> {
            String name = descriptor.getName();

            if (descriptor.getDeclaringClass().getAnnotation(Platforms.class) != null) {
                throw UserError.abort("Options must not be declared in a class that has a @%s annotation: option %s declared in %s",
                                Platforms.class.getSimpleName(), name, descriptor.getDeclaringClass().getTypeName());
            }

            if (!(descriptor.getOptionKey() instanceof RuntimeOptionKey)) {
                OptionDescriptor existing = allHostedOptions.put(name, descriptor);
                if (existing != null) {
                    throw shouldNotReachHere("Option name \"" + name + "\" has multiple definitions: " + existing.getLocation() + " and " + descriptor.getLocation());
                }
            }
            if (!(descriptor.getOptionKey() instanceof HostedOptionKey)) {
                OptionDescriptor existing = allRuntimeOptions.put(name, descriptor);
                if (existing != null) {
                    throw shouldNotReachHere("Option name \"" + name + "\" has multiple definitions: " + existing.getLocation() + " and " + descriptor.getLocation());
                }
            }
        });
    }

    public List<String> parse() {

        List<String> remainingArgs = new ArrayList<>();
        Set<String> errors = new HashSet<>();
        InterruptImageBuilding interrupt = null;
        for (String arg : arguments) {
            boolean isImageBuildOption = false;
            try {
                isImageBuildOption |= SubstrateOptionsParser.parseHostedOption(SubstrateOptionsParser.HOSTED_OPTION_PREFIX, allHostedOptions, hostedValues, PLUS_MINUS, errors, arg, System.out);
            } catch (InterruptImageBuilding e) {
                interrupt = e;
            }
            try {
                isImageBuildOption |= SubstrateOptionsParser.parseHostedOption(SubstrateOptionsParser.RUNTIME_OPTION_PREFIX, allRuntimeOptions, runtimeValues, PLUS_MINUS, errors, arg, System.out);
            } catch (InterruptImageBuilding e) {
                interrupt = e;
            }
            if (!isImageBuildOption) {
                remainingArgs.add(arg);
            }
        }
        if (interrupt != null) {
            throw interrupt;
        }
        if (!errors.isEmpty()) {
            throw UserError.abort(errors);
        }

        /*
         * We cannot prevent that runtime-only options are accessed during native image generation.
         * However, we set these options to null here, so that at least they do not have a sensible
         * value.
         */
        for (OptionDescriptor descriptor : allRuntimeOptions.getValues()) {
            if (!allHostedOptions.containsKey(descriptor.getName())) {
                hostedValues.put(descriptor.getOptionKey(), null);
            }
        }

        return remainingArgs;
    }

    public List<String> getArguments() {
        return arguments;
    }

    @Override
    public EconomicMap<OptionKey<?>, Object> getHostedValues() {
        return hostedValues;
    }

    @Override
    public EconomicMap<OptionKey<?>, Object> getRuntimeValues() {
        return runtimeValues;
    }

    public EconomicSet<String> getRuntimeOptionNames() {
        EconomicSet<String> res = EconomicSet.create(allRuntimeOptions.size());
        allRuntimeOptions.getKeys().forEach(res::add);
        return res;
    }
}
