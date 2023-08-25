/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.config;

import com.oracle.truffle.llvm.runtime.ContextExtension;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;

public final class Configurations {

    private static final ConfigurationFactory<?>[] factories;

    public static <S> Iterable<S> getService(Class<S> serviceClass) {
        Module polyglotModule = serviceClass.getModule();
        if (polyglotModule.isNamed()) {
            return ServiceLoader.load(polyglotModule.getLayer(), serviceClass);
        } else {
            return ServiceLoader.load(serviceClass, serviceClass.getClassLoader());
        }
    }

    static {
        ArrayList<ConfigurationFactory<?>> cfgs = new ArrayList<>();

        for (ConfigurationFactory<?> f : getService(ConfigurationFactory.class)) {
            cfgs.add(f);
        }

        cfgs.sort((o1, o2) -> {
            // higher priority first
            return o2.getPriority() - o1.getPriority();
        });

        factories = cfgs.toArray(new ConfigurationFactory<?>[cfgs.size()]);
    }

    private static <KEY> Configuration tryCreate(ConfigurationFactory<KEY> factory, LLVMLanguage language, ContextExtension.Registry ctxExtRegistry, OptionValues options) {
        KEY key = factory.parseOptions(options);
        if (key != null) {
            return factory.createConfiguration(language, ctxExtRegistry, key);
        } else {
            return null;
        }
    }

    /**
     * Create a configuration object for given options. This will use the highest priority
     * {@link ConfigurationFactory} that matches the given options.
     */
    public static Configuration createConfiguration(LLVMLanguage language, ContextExtension.Registry ctxExtRegistry, OptionValues options) {
        if (factories.length == 0) {
            throw new IllegalStateException("should not reach here: no configuration found");
        }
        for (ConfigurationFactory<?> factory : factories) {
            Configuration ret = tryCreate(factory, language, ctxExtRegistry, options);
            if (ret != null) {
                return ret;
            }
        }
        throw new LLVMPolyglotException(null, "No viable configuration found. " + formatHint());
    }

    private static String formatHint() {
        int maxNameSize = Arrays.stream(factories).mapToInt(c -> c.getName().length()).max().orElse(30);
        String format = "  %" + maxNameSize + "s:   %s (priority %d)";
        StringBuilder sb = new StringBuilder();
        sb.append("Known configurations:").append(System.lineSeparator());
        Arrays.stream(factories).//
                        map(c -> String.format(format, c.getName(), c.getHint(), c.getPriority())).//
                        forEach((String s) -> sb.append(s).append(System.lineSeparator()));
        return sb.toString();
    }

    /**
     * Check whether two option values would lead to the same {@link Configuration}. Code sharing
     * can only work if the {@link Configuration} is the same.
     */
    public static boolean areOptionsCompatible(OptionValues o1, OptionValues o2) {
        for (ConfigurationFactory<?> factory : factories) {
            Object key1 = factory.parseOptions(o1);
            Object key2 = factory.parseOptions(o2);
            if (key1 != null || key2 != null) {
                return Objects.equals(key1, key2);
            }
        }
        return false;
    }

    /**
     * Get all option descriptors that are supported by any of the installed configurations.
     */
    public static OptionDescriptors getOptionDescriptors() {
        List<OptionDescriptor> optionDescriptors = new ArrayList<>();
        // add core options
        optionDescriptors.addAll(SulongEngineOption.describeOptions());
        // add configuration specific options
        for (ConfigurationFactory<?> f : factories) {
            optionDescriptors.addAll(f.getOptionDescriptors());
        }
        return OptionDescriptors.create(optionDescriptors);
    }
}
