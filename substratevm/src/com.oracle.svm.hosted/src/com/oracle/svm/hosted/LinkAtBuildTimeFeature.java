/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.ClassLoaderSupport;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.option.OptionOrigin;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;

@AutomaticFeature
public final class LinkAtBuildTimeFeature implements Feature {

    static final class Options {
        @APIOption(name = "link-at-build-time", defaultValue = "")//
        @Option(help = "Require types to be fully defined at image build-time. If used without args, all classes in scope of the option are required to be fully defined.")//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> LinkAtBuildTime = new HostedOptionKey<>(new LocatableMultiOptionValue.Strings());
    }

    private final String javaIdentifier = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";
    private final Pattern validOptionValue = Pattern.compile(javaIdentifier + "(\\." + javaIdentifier + ")*");

    private final Set<String> requireCompletePackageOrClass = new HashSet<>();
    private final Set<Module> requireCompleteModules = new HashSet<>();
    private boolean requireCompleteAll;

    private ClassLoaderSupport classLoaderSupport;
    private Map<URI, Module> uriModuleMap;

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        classLoaderSupport = ImageSingletons.lookup(ClassLoaderSupport.class);

        var loader = ((FeatureImpl.BeforeAnalysisAccessImpl) access).getImageClassLoader();
        uriModuleMap = ModuleFinder.of(loader.applicationModulePath().toArray(Path[]::new)).findAll().stream()
                        .filter(mRef -> mRef.location().isPresent())
                        .collect(Collectors.toUnmodifiableMap(mRef -> mRef.location().get(), mRef -> loader.findModule(mRef.descriptor().name()).get()));

        /*
         * SerializationBuilder.newConstructorForSerialization() creates synthetic
         * jdk/internal/reflect/GeneratedSerializationConstructorAccessor* classes that do not have
         * the synthetic modifier set (clazz.isSynthetic() returns false for such classes). Any
         * class with package-name jdk.internal.reflect should be treated as link-at-build-time.
         */
        requireCompletePackageOrClass.add("jdk.internal.reflect");

        Options.LinkAtBuildTime.getValue().getValuesWithOrigins().forEach(this::extractOptionValue);
    }

    private void extractOptionValue(Pair<String, OptionOrigin> valueOrigin) {
        var value = valueOrigin.getLeft();
        OptionOrigin origin = valueOrigin.getRight();
        if (value.isEmpty()) {
            if (origin.commandLineLike()) {
                requireCompleteAll = true;
                return;
            }
            var originModule = uriModuleMap.get(origin.container());
            if (originModule != null) {
                requireCompleteModules.add(originModule);
                return;
            }
            throw UserError.abort("Using '%s' without args only allowed on module-path. %s not part of module-path.",
                            SubstrateOptionsParser.commandArgument(Options.LinkAtBuildTime, value), origin);
        } else {
            for (String entry : OptionUtils.resolveOptionValueRedirection(Options.LinkAtBuildTime, value, origin)) {
                if (validOptionValue.matcher(entry).matches()) {
                    requireCompletePackageOrClass.add(entry);
                } else {
                    throw UserError.abort("Entry '%s' in option '%s' provided by '%s' is neither a package nor a fully qualified classname.",
                                    entry, SubstrateOptionsParser.commandArgument(Options.LinkAtBuildTime, value), origin);
                }
            }
        }
    }

    boolean requiresCompleteDefinition(Class<?> clazz) {
        if (requireCompleteAll || clazz.isArray()) {
            return true;
        }

        if (!classLoaderSupport.isNativeImageClassLoader(clazz.getClassLoader())) {
            return true;
        }
        assert !clazz.isPrimitive() : "Primitive classes are not loaded via NativeImageClassLoader";

        var module = clazz.getModule();
        if (module.isNamed() && (requireCompleteModules.contains(module) || isModuleSynthetic(module))) {
            return true;
        }

        return requireCompletePackageOrClass.contains(clazz.getName()) || requireCompletePackageOrClass.contains(clazz.getPackageName()) || clazz.isSynthetic();
    }

    public static boolean isModuleSynthetic(Module m) {
        return m.getDescriptor().modifiers().contains(ModuleDescriptor.Modifier.SYNTHETIC);
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess a) {
        if (a != null) {
            return;
        }

        // Test results of requiresCompleteDefinition(Class<?> clazz) on whole AnalysisUniverse
        FeatureImpl.AfterAnalysisAccessImpl access = (FeatureImpl.AfterAnalysisAccessImpl) a;
        for (AnalysisType analysisType : access.getUniverse().getTypes()) {
            System.out.print(analysisType.toJavaName());
            Class<?> analysisClass = analysisType.getJavaClass();
            if (analysisClass == null) {
                System.out.print(" (no Class)");
            } else {
                boolean requiresCompleteDefinition = requiresCompleteDefinition(analysisClass);
                System.out.print(" requiresCompleteDefinition: " + requiresCompleteDefinition);
            }
            System.out.println();
        }
    }
}
