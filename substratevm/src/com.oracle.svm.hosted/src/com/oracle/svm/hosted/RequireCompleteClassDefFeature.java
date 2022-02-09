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

import java.lang.module.ModuleFinder;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;

@AutomaticFeature
public final class RequireCompleteClassDefFeature implements Feature {

    static final class Options {
        @APIOption(name = "require-complete-classdef", defaultValue = "")//
        @Option(help = "Require types to be fully defined at image build-time. If used without args, all classes in the scope the option are required to be fully defined.")//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> RequireCompleteClassDef = new HostedOptionKey<>(new LocatableMultiOptionValue.Strings());
    }

    private final Set<String> requireCompletePackageOrClass = new HashSet<>();
    private final Set<Module> requireCompleteModules = new HashSet<>();
    private boolean requireCompleteAll = false;

    Map<URI, Module> moduleFromURI;

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        var loader = ((FeatureImpl.BeforeAnalysisAccessImpl) access).getImageClassLoader();

        moduleFromURI = ModuleFinder.of(loader.applicationModulePath().toArray(Path[]::new)).findAll().stream()
                        .filter(mRef -> mRef.location().isPresent())
                        .collect(Collectors.toUnmodifiableMap(mRef -> mRef.location().get(), mRef -> loader.findModule(mRef.descriptor().name()).get()));

        Options.RequireCompleteClassDef.getValue().getValuesWithOrigins().forEach(this::extractRequireCompleteClasses);

        System.out.println("DONE");
    }

    private void extractRequireCompleteClasses(Pair<String, String> valueOrigin) {
        var value = valueOrigin.getLeft();
        if (value.isEmpty()) {
            String origin = valueOrigin.getRight();
            if (origin == null) {
                requireCompleteAll = true;
                return;
            }
            var originURI = originURI(origin);
            if (originURI == null) {
                throw notUsedInModulePath(origin);
            }
            if (originURI.getScheme().equals("jar")) {
                var specific = originURI.getSchemeSpecificPart();
                var specificJarFile = specific.substring(0, specific.lastIndexOf('!'));
                var specificInner = specific.substring(specific.lastIndexOf('!') + 1);
                originURI = originURI(specificJarFile);
            }
            var originModule = moduleFromURI.get(originURI);
            if (originModule == null) {
                throw notUsedInModulePath(origin);
            }
            requireCompleteModules.add(originModule);
        } else {
            requireCompletePackageOrClass.addAll(Arrays.asList(SubstrateUtil.split(value, ",")));
        }
    }

    private URI originURI(String origin) {
        Objects.requireNonNull(origin);
        try {
            return new URI(origin);
        } catch (URISyntaxException x) {
            return null;
        }
    }

    private UserError.UserException notUsedInModulePath(String origin) {
        return UserError.abort("Using %s without args is only allowed on module-path. Actual location %s",
                        SubstrateOptionsParser.commandArgument(Options.RequireCompleteClassDef, ""), origin);
    }
}
