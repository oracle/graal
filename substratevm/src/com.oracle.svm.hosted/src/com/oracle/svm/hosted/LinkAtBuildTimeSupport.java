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

import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.svm.core.ClassLoaderSupport;
import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.option.OptionClassFilter;
import com.oracle.svm.core.option.OptionOrigin;

import jdk.graal.compiler.options.Option;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class LinkAtBuildTimeSupport {

    static final class Options {
        @APIOption(name = "link-at-build-time", defaultValue = "")//
        @Option(help = "file:doc-files/LinkAtBuildTimeHelp.txt")//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> LinkAtBuildTime = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.build());

        @APIOption(name = "link-at-build-time-paths")//
        @Option(help = "file:doc-files/LinkAtBuildTimePathsHelp.txt")//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> LinkAtBuildTimePaths = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.build());
    }

    private final ClassLoaderSupport classLoaderSupport;
    private final OptionClassFilter classFilter;

    public LinkAtBuildTimeSupport(ImageClassLoader imageClassLoader, ClassLoaderSupport classLoaderSupport) {
        this.classLoaderSupport = classLoaderSupport;
        this.classFilter = OptionClassFilterBuilder.createFilter(imageClassLoader, Options.LinkAtBuildTime, Options.LinkAtBuildTimePaths);

        /*
         * SerializationBuilder.newConstructorForSerialization() creates synthetic
         * jdk/internal/reflect/GeneratedSerializationConstructorAccessor* classes that do not have
         * the synthetic modifier set (clazz.isSynthetic() returns false for such classes). Any
         * class with package-name jdk.internal.reflect should be treated as link-at-build-time.
         */
        classFilter.addPackageOrClass("jdk.internal.reflect", null);
    }

    public static LinkAtBuildTimeSupport singleton() {
        return ImageSingletons.lookup(LinkAtBuildTimeSupport.class);
    }

    public boolean linkAtBuildTime(ResolvedJavaType type) {
        return linkAtBuildTime(OriginalClassProvider.getJavaClass(type));
    }

    public boolean linkAtBuildTime(Class<?> clazz) {
        return isIncluded(clazz) != null;
    }

    public boolean moduleLinkAtBuildTime(String module) {
        return classFilter.isModuleIncluded(module) != null;
    }

    public boolean packageOrClassAtBuildTime(String packageName) {
        return classFilter.isPackageOrClassIncluded(packageName) != null;
    }

    private Object isIncluded(Class<?> clazz) {
        if (clazz.isArray() || !classLoaderSupport.isNativeImageClassLoader(clazz.getClassLoader())) {
            return "system default";
        }
        assert !clazz.isPrimitive() : "Primitive classes are not loaded via NativeImageClassLoader";
        return classFilter.isIncluded(clazz);
    }

    public String errorMessageFor(ResolvedJavaType type) {
        return errorMessageFor(OriginalClassProvider.getJavaClass(type));
    }

    public String errorMessageFor(Class<?> clazz) {
        assert linkAtBuildTime(clazz);
        return "This error is reported at image build time because class " + clazz.getTypeName() + " is registered for linking at image build time by " + linkAtBuildTimeReason(clazz) + ".";
    }

    @SuppressWarnings("unchecked")
    private String linkAtBuildTimeReason(Class<?> clazz) {
        Object reason = isIncluded(clazz);
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
