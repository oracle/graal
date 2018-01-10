/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.option;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.options.ModifiableOptionValues;
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.RuntimeOptionsSupport;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionType;

import com.oracle.svm.core.annotate.AnnotateOriginal;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.MustNotAllocate;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;

/**
 * The singleton holder of runtime options.
 *
 * @see com.oracle.svm.core.option
 */
public class RuntimeOptionValues extends ModifiableOptionValues {
    private EconomicSet<String> allOptionNames;

    public RuntimeOptionValues(UnmodifiableEconomicMap<OptionKey<?>, Object> values, EconomicSet<String> allOptionNames) {
        super(values);
        this.allOptionNames = allOptionNames;
    }

    @Fold
    public static RuntimeOptionValues singleton() {
        return ImageSingletons.lookup(RuntimeOptionValues.class);
    }

    @Fold
    public EconomicSet<String> getAllOptionNames() {
        return allOptionNames;
    }
}

class RuntimeOptionsSupportImpl implements RuntimeOptionsSupport {
    @Override
    public void set(String optionName, Object value) {
        if (!RuntimeOptionValues.singleton().getAllOptionNames().contains(optionName)) {
            throw new RuntimeException("Unknown option: " + optionName);
        }
        Optional<OptionDescriptor> descriptor = RuntimeOptionParser.singleton().getDescriptor(optionName);
        if (descriptor.isPresent()) {
            OptionDescriptor desc = descriptor.get();
            Class<?> valueType = value.getClass();
            if (desc.getType().isAssignableFrom(valueType)) {
                RuntimeOptionValues.singleton().update(desc.getOptionKey(), value);
            } else {
                throw new RuntimeException("Invalid type of option '" + optionName + "': required " + desc.getType().getSimpleName() + ", provided " + valueType);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(String optionName) {
        if (!RuntimeOptionValues.singleton().getAllOptionNames().contains(optionName)) {
            throw new RuntimeException("Unknown option: " + optionName);
        }
        Optional<OptionDescriptor> descriptor = RuntimeOptionParser.singleton().getDescriptor(optionName);
        OptionKey<T> optionKey = (OptionKey<T>) descriptor
                        .orElseThrow(() -> new RuntimeException("Option " + optionName + " exists but it is not reachable in the application. It is not possible to get its value."))
                        .getOptionKey();
        return optionKey.getValue(RuntimeOptionValues.singleton());
    }

    @Override
    public OptionDescriptors getOptions() {
        Collection<OptionDescriptor> descriptors = RuntimeOptionParser.singleton().getDescriptors();
        List<org.graalvm.options.OptionDescriptor> graalvmDescriptors = new ArrayList<>(descriptors.size());
        for (OptionDescriptor descriptor : descriptors) {
            org.graalvm.options.OptionDescriptor.Builder builder = org.graalvm.options.OptionDescriptor.newBuilder(asGraalVMOptionKey(descriptor), descriptor.getName());
            builder.help(descriptor.getHelp());
            graalvmDescriptors.add(builder.build());
        }
        return OptionDescriptors.create(graalvmDescriptors);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> org.graalvm.options.OptionKey<T> asGraalVMOptionKey(OptionDescriptor descriptor) {
        Class<T> clazz = (Class<T>) descriptor.getType();
        OptionType<T> type;
        if (clazz.isEnum()) {
            type = (OptionType<T>) ENUM_TYPE_CACHE.computeIfAbsent(clazz, c -> new OptionType<>(c.getSimpleName(), null, s -> (T) Enum.valueOf((Class<? extends Enum>) c, s)));
        } else if (clazz == Long.class) {
            type = (OptionType<T>) LONG_OPTION_TYPE;
        } else {
            type = OptionType.defaultType(clazz);
            if (type == null) {
                throw VMError.shouldNotReachHere("unsupported type: " + clazz);
            }
        }
        OptionKey<T> optionKey = (OptionKey<T>) descriptor.getOptionKey();
        return new org.graalvm.options.OptionKey<>(optionKey.getDefaultValue(), type);
    }

    private static final Map<Class<?>, OptionType<?>> ENUM_TYPE_CACHE = new HashMap<>();
    private static final OptionType<Long> LONG_OPTION_TYPE = new OptionType<>("long", 0L, RuntimeOptionsSupportImpl::parseLong);

    private static long parseLong(String v) {
        String valueString = v.toLowerCase();
        long scale = 1;
        if (valueString.endsWith("k")) {
            scale = 1024L;
        } else if (valueString.endsWith("m")) {
            scale = 1024L * 1024L;
        } else if (valueString.endsWith("g")) {
            scale = 1024L * 1024L * 1024L;
        } else if (valueString.endsWith("t")) {
            scale = 1024L * 1024L * 1024L * 1024L;
        }

        if (scale != 1) {
            /* Remove trailing scale character. */
            valueString = valueString.substring(0, valueString.length() - 1);
        }

        return Long.parseLong(valueString) * scale;
    }
}

/**
 * Sets option access.
 */
@AutomaticFeature
class OptionAccessFeature implements Feature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(RuntimeOptionsSupport.class, new RuntimeOptionsSupportImpl());
    }
}

@TargetClass(org.graalvm.compiler.options.OptionKey.class)
final class Target_org_graalvm_compiler_options_OptionKey {

    @AnnotateOriginal
    @MustNotAllocate(list = MustNotAllocate.WHITELIST, reason = "Static analysis imprecision makes all hashCode implementations reachable from this method")
    native Object getValue(OptionValues values);
}
