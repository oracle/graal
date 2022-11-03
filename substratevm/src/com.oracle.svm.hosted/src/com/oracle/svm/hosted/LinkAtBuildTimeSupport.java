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

import java.io.File;
import java.lang.module.ModuleFinder;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Pair;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.svm.core.ClassLoaderSupport;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.option.OptionOrigin;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;

import jdk.vm.ci.meta.ResolvedJavaType;

public final class LinkAtBuildTimeSupport {

    static final class Options {
        @APIOption(name = "link-at-build-time", defaultValue = "")//
        @Option(help = "file:doc-files/LinkAtBuildTimeHelp.txt")//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> LinkAtBuildTime = new HostedOptionKey<>(new LocatableMultiOptionValue.Strings());

        @APIOption(name = "link-at-build-time-paths")//
        @Option(help = "file:doc-files/LinkAtBuildTimePathsHelp.txt")//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> LinkAtBuildTimePaths = new HostedOptionKey<>(new LocatableMultiOptionValue.Strings());
    }

    private final String javaIdentifier = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";
    private final Pattern validOptionValue = Pattern.compile(javaIdentifier + "(\\." + javaIdentifier + ")*");

    private final Set<OptionOrigin> reasonCommandLine = Collections.singleton(OptionOrigin.commandLineOptionOriginSingleton);

    private final Map<String, Set<OptionOrigin>> requireCompletePackageOrClass = new HashMap<>();
    private final Set<Module> requireCompleteModules = new HashSet<>();
    private boolean requireCompleteAll;

    private final ClassLoaderSupport classLoaderSupport;
    private final ImageClassLoader imageClassLoader;
    private final Map<URI, Module> uriModuleMap;

    public LinkAtBuildTimeSupport(ImageClassLoader imageClassLoader, ClassLoaderSupport classLoaderSupport) {
        this.classLoaderSupport = classLoaderSupport;
        this.imageClassLoader = imageClassLoader;

        uriModuleMap = ModuleFinder.of(imageClassLoader.applicationModulePath().toArray(Path[]::new)).findAll().stream()
                        .filter(mRef -> mRef.location().isPresent())
                        .collect(Collectors.toUnmodifiableMap(mRef -> mRef.location().get(), mRef -> imageClassLoader.findModule(mRef.descriptor().name()).get()));

        /*
         * SerializationBuilder.newConstructorForSerialization() creates synthetic
         * jdk/internal/reflect/GeneratedSerializationConstructorAccessor* classes that do not have
         * the synthetic modifier set (clazz.isSynthetic() returns false for such classes). Any
         * class with package-name jdk.internal.reflect should be treated as link-at-build-time.
         */
        requireCompletePackageOrClass.put("jdk.internal.reflect", null);

        Options.LinkAtBuildTime.getValue().getValuesWithOrigins().forEach(this::extractLinkAtBuildTimeOptionValue);
        Options.LinkAtBuildTimePaths.getValue().getValuesWithOrigins().forEach(this::extractLinkAtBuildTimePathsOptionValue);
    }

    public static LinkAtBuildTimeSupport singleton() {
        return ImageSingletons.lookup(LinkAtBuildTimeSupport.class);
    }

    private void extractLinkAtBuildTimeOptionValue(Pair<String, OptionOrigin> valueOrigin) {
        var value = valueOrigin.getLeft();
        OptionOrigin origin = valueOrigin.getRight();
        URI container = origin.container();
        if (value.isEmpty()) {
            if (origin.commandLineLike()) {
                requireCompleteAll = true;
                return;
            }
            var originModule = uriModuleMap.get(container);
            if (originModule != null) {
                requireCompleteModules.add(originModule);
                return;
            }
            throw UserError.abort("Using '%s' without args only allowed on module-path. %s not part of module-path.",
                            SubstrateOptionsParser.commandArgument(Options.LinkAtBuildTime, value), origin);
        } else {
            for (String entry : OptionUtils.resolveOptionValuesRedirection(Options.LinkAtBuildTime, value, origin)) {
                if (validOptionValue.matcher(entry).matches()) {
                    if (!origin.commandLineLike() && !imageClassLoader.classes(container).contains(entry) && !imageClassLoader.packages(container).contains(entry)) {
                        throw UserError.abort("Option '%s' provided by %s contains '%s'. No such package or class name found in '%s'.",
                                        SubstrateOptionsParser.commandArgument(Options.LinkAtBuildTime, value), origin, entry, container);
                    }
                    requireCompletePackageOrClass.computeIfAbsent(entry, unused -> new HashSet<>()).add(origin);
                } else {
                    throw UserError.abort("Entry '%s' in option '%s' provided by %s is neither a package nor a fully qualified classname.",
                                    entry, SubstrateOptionsParser.commandArgument(Options.LinkAtBuildTime, value), origin);
                }
            }
        }
    }

    private void extractLinkAtBuildTimePathsOptionValue(Pair<String, OptionOrigin> valueOrigin) {
        var value = valueOrigin.getLeft();
        OptionOrigin origin = valueOrigin.getRight();
        if (!origin.commandLineLike()) {
            throw UserError.abort("Using '%s' is only allowed on command line.",
                            SubstrateOptionsParser.commandArgument(Options.LinkAtBuildTimePaths, value), origin);
        }
        if (value.isEmpty()) {
            throw UserError.abort("Using '%s' requires directory or jar-file path arguments.",
                            SubstrateOptionsParser.commandArgument(Options.LinkAtBuildTimePaths, value), origin);
        }
        for (String pathStr : SubstrateUtil.split(value, File.pathSeparator)) {
            Path path = Path.of(pathStr);
            EconomicSet<String> packages = imageClassLoader.packages(path.toAbsolutePath().normalize().toUri());
            if (imageClassLoader.noEntryForURI(packages)) {
                throw UserError.abort("Option '%s' provided by %s contains entry '%s'. No such entry exists on class or module-path.",
                                SubstrateOptionsParser.commandArgument(Options.LinkAtBuildTimePaths, value), origin, pathStr);
            }
            for (String pkg : packages) {
                requireCompletePackageOrClass.put(pkg, Collections.singleton(origin));
            }
        }
    }

    public boolean linkAtBuildTime(ResolvedJavaType type) {
        Class<?> clazz = ((OriginalClassProvider) type).getJavaClass();
        if (clazz == null) {
            /*
             * Some kind of synthetic class coming from a substitution. We assume all such classes
             * are linked at build time.
             */
            return true;
        }
        return linkAtBuildTime(clazz);
    }

    public boolean linkAtBuildTime(Class<?> clazz) {
        return linkAtBuildTimeImpl(clazz) != null;
    }

    private Object linkAtBuildTimeImpl(Class<?> clazz) {
        if (requireCompleteAll) {
            return reasonCommandLine;
        }

        if (clazz.isArray() || !classLoaderSupport.isNativeImageClassLoader(clazz.getClassLoader())) {
            return "system default";
        }
        assert !clazz.isPrimitive() : "Primitive classes are not loaded via NativeImageClassLoader";

        var module = clazz.getModule();
        if (module.isNamed() && (requireCompleteModules.contains(module))) {
            return module.toString();
        }

        Set<OptionOrigin> origins = requireCompletePackageOrClass.get(clazz.getName());
        if (origins != null) {
            return origins;
        }
        return requireCompletePackageOrClass.get(clazz.getPackageName());
    }

    public String errorMessageFor(ResolvedJavaType type) {
        Class<?> clazz = ((OriginalClassProvider) type).getJavaClass();
        if (clazz == null) {
            return "This error is reported at image build time because class " + type.toJavaName(true) + " is registered for linking at image build time.";
        }
        return errorMessageFor(clazz);
    }

    public String errorMessageFor(Class<?> clazz) {
        assert linkAtBuildTime(clazz);
        return "This error is reported at image build time because class " + clazz.getTypeName() + " is registered for linking at image build time by " + linkAtBuildTimeReason(clazz);
    }

    @SuppressWarnings("unchecked")
    private String linkAtBuildTimeReason(Class<?> clazz) {
        Object reason = linkAtBuildTimeImpl(clazz);
        if (reason == null) {
            return null;
        }
        if (reason instanceof String) {
            return (String) reason;
        }
        Set<OptionOrigin> origins = (Set<OptionOrigin>) reason;
        return origins.stream().map(OptionOrigin::toString).collect(Collectors.joining(" and "));
    }
}
