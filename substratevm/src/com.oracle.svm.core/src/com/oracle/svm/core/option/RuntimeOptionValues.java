/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.options.EnumMultiOptionKey;
import org.graalvm.compiler.options.ModifiableOptionValues;
import org.graalvm.compiler.options.NestedBooleanOptionKey;
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.RuntimeOptions.OptionClass;
import org.graalvm.nativeimage.impl.RuntimeOptionsSupport;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionType;

import com.oracle.svm.core.annotate.AnnotateOriginal;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ClassUtil;

/**
 * The singleton holder of runtime options.
 *
 * @see com.oracle.svm.core.option
 */
public class RuntimeOptionValues extends ModifiableOptionValues {
    private final EconomicSet<String> allOptionNames;

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

@AutomaticallyRegisteredImageSingleton(RuntimeOptionsSupport.class)
class RuntimeOptionsSupportImpl implements RuntimeOptionsSupport {

    @Override
    public void set(String optionName, Object value) {
        if (XOptions.setOption(optionName)) {
            return;
        }
        if (!RuntimeOptionValues.singleton().getAllOptionNames().contains(optionName)) {
            throw new RuntimeException("Unknown option: " + optionName);
        }
        Optional<OptionDescriptor> descriptor = RuntimeOptionParser.singleton().getDescriptor(optionName);
        if (descriptor.isPresent()) {
            OptionDescriptor desc = descriptor.get();
            Class<?> valueType = value.getClass();
            if (desc.getOptionValueType().isAssignableFrom(valueType)) {
                RuntimeOptionValues.singleton().update(desc.getOptionKey(), value);
            } else {
                throw new RuntimeException("Invalid type of option '" + optionName + "': required " + ClassUtil.getUnqualifiedName(desc.getOptionValueType()) + ", provided " + valueType);
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
    public OptionDescriptors getOptions(EnumSet<OptionClass> classes) {
        Iterable<OptionDescriptor> descriptors = RuntimeOptionParser.singleton().getDescriptors();
        List<org.graalvm.options.OptionDescriptor> graalvmDescriptors = new ArrayList<>();
        for (OptionDescriptor descriptor : descriptors) {
            if (classes.contains(getOptionClass(descriptor))) {
                org.graalvm.options.OptionDescriptor.Builder builder = org.graalvm.options.OptionDescriptor.newBuilder(asGraalVMOptionKey(descriptor), descriptor.getName());
                String helpMsg = descriptor.getHelp();
                int helpLen = helpMsg.length();
                if (helpLen > 0 && helpMsg.charAt(helpLen - 1) != '.') {
                    helpMsg += '.';
                }
                builder.help(helpMsg);
                builder.deprecated(descriptor.isDeprecated());
                builder.deprecationMessage(descriptor.getDeprecationMessage());
                graalvmDescriptors.add(builder.build());
            }
        }
        return OptionDescriptors.create(graalvmDescriptors);
    }

    private static OptionClass getOptionClass(OptionDescriptor descriptor) {
        if (descriptor.getOptionKey() instanceof RuntimeOptionKey) {
            return OptionClass.VM;
        }
        return OptionClass.Compiler;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> org.graalvm.options.OptionKey<T> asGraalVMOptionKey(OptionDescriptor descriptor) {
        Class<T> clazz = (Class<T>) descriptor.getOptionValueType();
        OptionType<T> type;
        if (clazz.isEnum()) {
            type = (OptionType<T>) ENUM_TYPE_CACHE.computeIfAbsent(clazz, c -> new OptionType<>(ClassUtil.getUnqualifiedName(c), s -> (T) Enum.valueOf((Class<? extends Enum>) c, s)));
        } else if (clazz == Long.class) {
            type = (OptionType<T>) LONG_OPTION_TYPE;
        } else if (clazz == EconomicSet.class) {
            EnumMultiOptionKey<?> multiOptionKey = (EnumMultiOptionKey<?>) descriptor.getOptionKey();
            type = (OptionType<T>) ENUM_MULTI_TYPE_CACHE.computeIfAbsent(multiOptionKey.getEnumClass(),
                            c -> new OptionType<>("Multi" + ClassUtil.getUnqualifiedName(multiOptionKey.getEnumClass()), s -> (T) multiOptionKey.valueOf(s)));
        } else {
            type = OptionType.defaultType(clazz);
            if (type == null) {
                throw VMError.shouldNotReachHere("unsupported type: " + clazz);
            }
        }
        OptionKey<T> optionKey = (OptionKey<T>) descriptor.getOptionKey();
        while (optionKey instanceof NestedBooleanOptionKey) {
            optionKey = (OptionKey<T>) ((NestedBooleanOptionKey) optionKey).getParentOption();
        }
        T defaultValue = optionKey.getDefaultValue();
        return new org.graalvm.options.OptionKey<>(defaultValue, type);
    }

    private static final Map<Class<?>, OptionType<?>> ENUM_TYPE_CACHE = new HashMap<>();

    private static final Map<Class<?>, OptionType<?>> ENUM_MULTI_TYPE_CACHE = new HashMap<>();

    private static final OptionType<Long> LONG_OPTION_TYPE = new OptionType<>("long", RuntimeOptionsSupportImpl::parseLong);

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

        try {
            return Long.parseLong(valueString) * scale;
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException(String.format("Invalid value \"%s\". Allowed values are [1, inf)(|<k>|<m>|<g>|<t>).", v));
        }
    }
}

@TargetClass(org.graalvm.compiler.options.OptionKey.class)
final class Target_org_graalvm_compiler_options_OptionKey {

    @AnnotateOriginal
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, reason = "Static analysis imprecision makes all hashCode implementations reachable from this method")
    native Object getValue(OptionValues values);
}
