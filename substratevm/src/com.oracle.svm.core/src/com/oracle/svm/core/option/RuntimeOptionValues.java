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
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.RuntimeOptions.Descriptor;
import org.graalvm.nativeimage.impl.RuntimeOptionsSupport;

import com.oracle.svm.core.annotate.AnnotateOriginal;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.util.ClassUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.ModifiableOptionValues;
import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.options.OptionsParser;

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

    record DescriptorImpl(String name, String help, Class<?> valueType, Object defaultValue, boolean deprecated, String deprecatedMessage) implements Descriptor {

        @Override
        public Object convertValue(String value) throws IllegalArgumentException {
            Optional<OptionDescriptor> descriptor = RuntimeOptionParser.singleton().getDescriptor(name);
            return OptionsParser.parseOptionValue(descriptor.get(), value);
        }

    }

    @Override
    public List<Descriptor> listDescriptors() {
        List<Descriptor> options = new ArrayList<>();
        Iterable<OptionDescriptor> descriptors = RuntimeOptionParser.singleton().getDescriptors();
        for (OptionDescriptor descriptor : descriptors) {
            DescriptorImpl option = asDescriptor(descriptor);
            if (option != null) {
                options.add(option);
            }
        }
        return options;
    }

    private static DescriptorImpl asDescriptor(OptionDescriptor descriptor) {
        if (descriptor == null) {
            return null;
        }
        String help = descriptor.getHelp().getFirst();
        int helpLen = help.length();
        if (helpLen > 0 && help.charAt(helpLen - 1) != '.') {
            help += '.';
        }
        return new DescriptorImpl(descriptor.getName(), help, descriptor.getOptionValueType(), descriptor.getOptionKey().getDefaultValue(), descriptor.isDeprecated(),
                        descriptor.getDeprecationMessage());
    }

    @Override
    public Descriptor getDescriptor(String optionName) {
        return asDescriptor(RuntimeOptionParser.singleton().getDescriptor(optionName).orElse(null));
    }
}

@TargetClass(OptionKey.class)
final class Target_jdk_graal_compiler_options_OptionKey {

    @AnnotateOriginal
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, reason = "Static analysis imprecision makes all hashCode implementations reachable from this method")
    native Object getValue(OptionValues values);
}
