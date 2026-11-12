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

import com.oracle.svm.core.ClassLoaderSupport;
import com.oracle.svm.shared.option.APIOption;
import com.oracle.svm.shared.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.shared.option.OptionClassFilter;
import com.oracle.svm.shared.option.OptionOrigin;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.HostedModuleSupport;
import com.oracle.svm.util.JVMCIReflectionUtil;
import com.oracle.svm.util.OriginalClassProvider;

import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.vmaccess.ResolvedJavaModule;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaType;

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
public final class LinkAtBuildTimeSupport {

    public static final class Options {
        @APIOption(name = "link-at-build-time", defaultValue = "")//
        @Option(help = "file:doc-files/LinkAtBuildTimeHelp.txt")//
        public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> LinkAtBuildTime = new HostedOptionKey<>(AccumulatingLocatableMultiOptionValue.Strings.build());

        @APIOption(name = "link-at-build-time-paths")//
        @Option(help = "file:doc-files/LinkAtBuildTimePathsHelp.txt")//
        public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> LinkAtBuildTimePaths = new HostedOptionKey<>(AccumulatingLocatableMultiOptionValue.Strings.build());
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
        ResolvedJavaType originalType = OriginalClassProvider.getOriginalType(type);
        return isIncluded(originalType) != null;
    }

    public boolean moduleLinkAtBuildTime(String module) {
        return classFilter.isModuleIncluded(module) != null;
    }

    public boolean packageOrClassAtBuildTime(String packageName) {
        return classFilter.isPackageOrClassIncluded(packageName) != null;
    }

    /**
     * Returns the reason why the canonical guest {@code type} is included for linking at image
     * build time, or {@code null} if it is not included.
     */
    private Object isIncluded(ResolvedJavaType type) {
        GuestAccess guestAccess = GuestAccess.get();
        if (!guestAccess.isFullyIsolated()) {
            Class<?> clazz = OriginalClassProvider.getJavaClass(type);
            if (clazz.isArray() || !classLoaderSupport.isNativeImageClassLoader(clazz.getClassLoader())) {
                return "system default";
            }
            assert !clazz.isPrimitive() : "Primitive classes are not loaded via NativeImageClassLoader";
            ResolvedJavaModule module = guestAccess.getModule(type);
            return classFilter.isIncluded(module.getName(), JVMCIReflectionUtil.getPackageName(type), type.toClassName());
        }

        if (type.isArray()) {
            return "system default";
        }

        /*
         * GR-79838: Espresso currently loads application classes and Native Image guest runtime
         * modules through the same espresso-owned system class loader. Keep the configured guest modules outside
         * the application class space until Espresso provides separate runtime and application
         * loader hierarchies. Once all application classes and modules are loaded by a dedicated
         * application loader or one of its children, replace this exception and the system-loader
         * approximation below with that loader.
         */
        ResolvedJavaModule module = guestAccess.getModule(type);
        String moduleName = module.getName();
        if (moduleName != null && HostedModuleSupport.GUEST_MODULES.contains(moduleName)) {
            return "system default";
        }

        JavaConstant javaClass = guestAccess.getProviders().getConstantReflection().asJavaClass(type);
        JavaConstant loader = guestAccess.invoke(guestAccess.elements.java_lang_Class_getClassLoader, javaClass);
        JavaConstant applicationLoader = guestAccess.invokeStatic(guestAccess.elements.java_lang_ClassLoader_getSystemClassLoader);
        while (!loader.isNull()) {
            if (loader.equals(applicationLoader)) {
                assert !type.isPrimitive() : "Primitive classes are not loaded via NativeImageClassLoader";
                return classFilter.isIncluded(module.getName(), JVMCIReflectionUtil.getPackageName(type), type.toClassName());
            }
            loader = guestAccess.invoke(guestAccess.elements.java_lang_ClassLoader_getParent, loader);
        }
        return "system default";
    }

    public String errorMessageFor(ResolvedJavaType type) {
        ResolvedJavaType originalType = OriginalClassProvider.getOriginalType(type);
        Object reason = isIncluded(originalType);
        assert reason != null;
        return "This error is reported at image build time because class " + JVMCIReflectionUtil.getTypeName(originalType) + " is registered for linking at image build time by " +
                        linkAtBuildTimeReason(reason) + ".";
    }

    @SuppressWarnings("unchecked")
    private static String linkAtBuildTimeReason(Object reason) {
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
