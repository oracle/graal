/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;

public final class Configurations {

    private static final ConfigurationFactory<?>[] factories;

    static {
        ArrayList<ConfigurationFactory<?>> cfgs = new ArrayList<>();

        ClassLoader cl = LLVMLanguage.class.getClassLoader();
        for (ConfigurationFactory<?> f : ServiceLoader.load(ConfigurationFactory.class, cl)) {
            cfgs.add(f);
        }

        cfgs.sort((o1, o2) -> {
            // higher priority first
            return o2.getPriority() - o1.getPriority();
        });

        factories = cfgs.toArray(new ConfigurationFactory<?>[cfgs.size()]);
    }

    private static <KEY> Configuration tryCreate(ConfigurationFactory<KEY> factory, LLVMLanguage language, OptionValues options) {
        KEY key = factory.parseOptions(options);
        if (key != null) {
            return factory.createConfiguration(language, key);
        } else {
            return null;
        }
    }

    /**
     * Create a configuration object for given options. This will use the highest priority
     * {@link ConfigurationFactory} that matches the given options.
     */
    public static Configuration createConfiguration(LLVMLanguage language, OptionValues options) {
        for (ConfigurationFactory<?> factory : factories) {
            Configuration ret = tryCreate(factory, language, options);
            if (ret != null) {
                return ret;
            }
        }
        throw new IllegalStateException("should not reach here: no configuration found");
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
        for (ConfigurationFactory<?> f : factories) {
            optionDescriptors.addAll(f.getOptionDescriptors());
        }
        return OptionDescriptors.create(optionDescriptors);
    }
}
