/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.image;

import static com.oracle.graal.pointsto.api.PointstoOptions.UseConservativeUnsafeAccess;
import static com.oracle.svm.core.SubstrateOptions.Preserve;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeJNIAccessSupport;
import org.graalvm.nativeimage.impl.RuntimeProxyCreationSupport;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;

import com.oracle.graal.pointsto.ClassInclusionPolicy;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.NativeImageClassLoaderSupport;
import com.oracle.svm.hosted.driver.IncludeOptionsSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;

public class PreserveOptionsSupport extends IncludeOptionsSupport {

    public static final String PRESERVE_ALL = "all";
    public static final String PRESERVE_NONE = "none";

    /**
     * All Java modules, except:
     * <ul>
     * <li><code>jdk.localedata</code> that pulls in 250 MB of code into the image.</li>
     * <li>All internal modules.</li>
     * <li>All tooling modules such as the java.compiler.</li>
     * <li>Modules that are currently not supported with Native Image (e.g.,
     * <code>java.management</code>.</li>
     * </ul>
     */
    public static final Set<String> JDK_MODULES_TO_PRESERVE = Set.of(
                    "java.base",
                    "java.desktop",
                    "java.xml",
                    "java.xml.crypto",
                    "jdk.xml.dom",
                    "java.rmi",
                    "jdk.net",
                    "java.smartcardio",
                    "jdk.charsets",
                    "java.sql",
                    "java.sql.rowset",
                    "java.transaction.xa",
                    "java.datatransfer",
                    "java.security.sasl",
                    "jdk.security.jgss",
                    "jdk.security.auth",
                    "jdk.crypto.cryptoki",
                    "java.logging",
                    "java.naming",
                    "jdk.naming.dns",
                    "jdk.httpserver",
                    "jdk.zipfs",
                    "jdk.nio.mapmode",
                    "java.instrument",
                    "java.prefs",
                    "jdk.unsupported",
                    "jdk.unsupported.desktop",
                    "jdk.accessibility");

    private static String preservePossibleOptions() {
        return String.format("[%s, %s, %s]",
                        PRESERVE_ALL, PRESERVE_NONE, IncludeOptionsSupport.possibleExtendedOptions());

    }

    public static void parsePreserveOption(EconomicMap<OptionKey<?>, Object> hostedValues, NativeImageClassLoaderSupport classLoaderSupport) {
        OptionValues optionValues = new OptionValues(hostedValues);
        AccumulatingLocatableMultiOptionValue.Strings preserve = SubstrateOptions.Preserve.getValue(optionValues);
        Stream<LocatableMultiOptionValue.ValueWithOrigin<String>> valuesWithOrigins = preserve.getValuesWithOrigins();
        valuesWithOrigins.forEach(valueWithOrigin -> {
            String optionArgument = SubstrateOptionsParser.commandArgument(SubstrateOptions.Preserve, valueWithOrigin.value(), true, false);
            if (!valueWithOrigin.origin().commandLineLike()) {
                throw UserError.abort("Using %s is only allowed on command line. The option was used from %s", optionArgument, valueWithOrigin.origin());
            }

            var options = Arrays.stream(valueWithOrigin.value().split(",")).toList();
            for (String option : options) {
                UserError.guarantee(!option.isEmpty(), "Option %s from %s cannot be passed an empty string. The possible options are: %s",
                                optionArgument, valueWithOrigin.origin(), preservePossibleOptions());
                switch (option) {
                    case PRESERVE_ALL -> classLoaderSupport.setPreserveAll(valueWithOrigin);
                    case PRESERVE_NONE -> classLoaderSupport.clearPreserveSelectors();
                    default -> parseIncludeSelector(optionArgument, valueWithOrigin, classLoaderSupport.getPreserveSelectors(), ExtendedOption.parse(option), preservePossibleOptions());
                }
            }
        });
        if (classLoaderSupport.isPreserveMode()) {
            if (UseConservativeUnsafeAccess.hasBeenSet(optionValues)) {
                UserError.guarantee(UseConservativeUnsafeAccess.getValue(optionValues), "%s can not be used together with %s. Please unset %s.",
                                SubstrateOptionsParser.commandArgument(UseConservativeUnsafeAccess, "-"),
                                SubstrateOptionsParser.commandArgument(Preserve, "<value>"),
                                SubstrateOptionsParser.commandArgument(UseConservativeUnsafeAccess, "-"));
            }
            UseConservativeUnsafeAccess.update(hostedValues, true);
        }
    }

    public static void registerPreservedClasses(NativeImageClassLoaderSupport classLoaderSupport) {
        var classesOrPackagesToIgnore = ignoredClassesOrPackagesForPreserve();
        var classesToPreserve = classLoaderSupport.getClassesToPreserve()
                        .filter(ClassInclusionPolicy::isClassIncludedBase)
                        .filter(c -> !(classesOrPackagesToIgnore.contains(c.getPackageName()) || classesOrPackagesToIgnore.contains(c.getName())))
                        .sorted(Comparator.comparing(ReflectionUtil::getClassHierarchyDepth).reversed())
                        .toList();

        final RuntimeReflectionSupport reflection = ImageSingletons.lookup(RuntimeReflectionSupport.class);
        final RuntimeProxyCreationSupport proxy = ImageSingletons.lookup(RuntimeProxyCreationSupport.class);
        final ConfigurationCondition always = ConfigurationCondition.alwaysTrue();

        /*
         * Sort descending by class hierarchy depth to avoid complexity related to field
         * registration.
         */
        classesToPreserve.forEach(c -> {
            reflection.register(always, false, c);

            reflection.registerAllDeclaredFields(always, c);
            reflection.registerAllDeclaredMethodsQuery(always, false, c);
            reflection.registerAllDeclaredConstructorsQuery(always, false, c);
            reflection.registerAllConstructorsQuery(always, false, c);
            reflection.registerAllClassesQuery(always, c);
            reflection.registerAllDeclaredClassesQuery(always, c);
            reflection.registerAllNestMembersQuery(always, c);
            reflection.registerAllPermittedSubclassesQuery(always, c);
            reflection.registerAllRecordComponentsQuery(always, c);
            reflection.registerAllSignersQuery(always, c);

            /* Register every single-interface proxy */
            // GR-62293 can't register proxies from jdk modules.
            if (c.getModule() == null && c.isInterface()) {
                proxy.addProxyClass(always, c);
            }

            try {
                for (Field declaredField : c.getDeclaredFields()) {
                    reflection.register(always, false, declaredField);
                }
            } catch (LinkageError e) {
                /* If we can't link we can not register for reflection */
            }
            if (SubstrateOptions.JNI.getValue()) {
                final RuntimeJNIAccessSupport jni = ImageSingletons.lookup(RuntimeJNIAccessSupport.class);
                jni.register(always, c);
                try {
                    for (Method declaredMethod : c.getDeclaredMethods()) {
                        jni.register(always, false, declaredMethod);
                    }
                    for (Constructor<?> declaredConstructor : c.getDeclaredConstructors()) {
                        jni.register(always, false, declaredConstructor);
                    }
                    for (Field declaredField : c.getDeclaredFields()) {
                        jni.register(always, false, declaredField);
                    }
                } catch (LinkageError e) {
                    /* If we can't link we can not register for JNI and reflection */
                }
            }

            // if we register as unsafe allocated earlier there are build-time
            // initialization errors
            reflection.register(always, !(c.isArray() || c.isInterface() || c.isPrimitive() || Modifier.isAbstract(c.getModifiers())), c);
        });

        /*
         * We now register super-type methods and fields in a separate pass--when all subtypes have
         * been fully registered. We do it the opposite order to avoid crawling the hierarchy
         * upwards multiple times when caching is implemented.
         */
        classesToPreserve.reversed().forEach(c -> {
            reflection.registerAllFields(always, c);
            reflection.registerAllMethodsQuery(always, false, c);
            // RuntimeSerialization.register(c);
        });

        for (String className : classLoaderSupport.getClassNamesToPreserve()) {
            reflection.registerClassLookup(always, className);
        }
    }

    private static Set<String> ignoredClassesOrPackagesForPreserve() {
        Set<String> ignoredClassesOrPackages = new HashSet<>(SubstrateOptions.IgnorePreserveForClasses.getValue().valuesAsSet());
        // GR-63360: Parsing of constant_ lambda forms fails
        ignoredClassesOrPackages.add("java.lang.invoke.LambdaForm$Holder");
        return Collections.unmodifiableSet(ignoredClassesOrPackages);
    }
}
