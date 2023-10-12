/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.reflect;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.standalone.StandaloneAnalysisClassLoader;
import com.oracle.graal.pointsto.standalone.features.StandaloneAnalysisFeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.configure.ConditionalElement;
import com.oracle.svm.configure.ReflectionConfigurationParser;
import com.oracle.svm.configure.ReflectionConfigurationParserDelegate;
import com.oracle.svm.util.StringUtil;

public class StandaloneReflectionFeature implements Feature {
    private StandaloneReflectionRegistry reflectionRegistry;

    public static class Options {
        // @formatter:off
        @Option(help = "Reflect config file path for standalone pointsto analysis reflection registry", type = OptionType.User)
        public static final OptionKey<String> AnalysisReflectionConfigFiles = new OptionKey<>(null);
        // @formatter:on
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) a;
        reflectionRegistry = new StandaloneReflectionRegistry(access);
        BigBang bb = access.getBigBang();

        ReflectionConfigurationParserDelegate<ConditionalElement<Class<?>>> delegate = new StandaloneReflectionRegistryAdapter(reflectionRegistry,
                        (StandaloneAnalysisClassLoader) access.getApplicationClassLoader());
        ReflectionConfigurationParser<ConditionalElement<Class<?>>> parser = new ReflectionConfigurationParser<>(delegate, false, false);
        getReflectionPath(bb).stream().map(Path::toAbsolutePath).forEach(path -> {
            if (!Files.exists(path)) {
                AnalysisError.interruptAnalysis("The configuration file " + path + " does not exist.");
            }
            try {
                parser.parseAndRegister(path.toUri());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private static List<Path> getReflectionPath(BigBang bb) {
        return flatten(",", Options.AnalysisReflectionConfigFiles.getValue(bb.getOptions()))
                        .stream()
                        .map(Paths::get)
                        .collect(Collectors.toList());
    }

    private static List<String> flatten(String delimiter, String value) {
        List<String> result = new ArrayList<>();
        if (value != null && !value.isEmpty()) {
            for (String component : StringUtil.split(value, delimiter)) {
                String trimmed = component.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }
}
