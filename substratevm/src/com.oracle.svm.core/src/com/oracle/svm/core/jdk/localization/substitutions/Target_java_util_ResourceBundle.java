/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk.localization.substitutions;

import static sun.security.util.SecurityConstants.GET_CLASSLOADER_PERMISSION;

import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.localization.LocalizationSupport;
import com.oracle.svm.core.jdk.localization.substitutions.modes.OptimizedLocaleMode;
import com.oracle.svm.core.jdk.resources.MissingResourceRegistrationUtils;

import jdk.internal.loader.BootLoader;

@TargetClass(java.util.ResourceBundle.class)
@SuppressWarnings({"unused"})
final class Target_java_util_ResourceBundle {

    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias)//
    private static ConcurrentMap<?, ?> cacheList = new ConcurrentHashMap<>();

    @TargetElement(onlyWith = OptimizedLocaleMode.class)
    @Substitute
    private static ResourceBundle getBundle(String baseName) {
        return ImageSingletons.lookup(LocalizationSupport.class).asOptimizedSupport().getCached(baseName, Locale.getDefault());
    }

    @TargetElement(onlyWith = OptimizedLocaleMode.class)
    @Substitute
    private static ResourceBundle getBundle(String baseName, ResourceBundle.Control control) {
        return ImageSingletons.lookup(LocalizationSupport.class).asOptimizedSupport().getCached(baseName, Locale.getDefault());
    }

    @TargetElement(onlyWith = OptimizedLocaleMode.class)
    @Substitute
    private static ResourceBundle getBundle(String baseName, Locale locale) {
        return ImageSingletons.lookup(LocalizationSupport.class).asOptimizedSupport().getCached(baseName, locale);
    }

    @TargetElement(onlyWith = OptimizedLocaleMode.class)
    @Substitute
    private static ResourceBundle getBundle(String baseName, Locale targetLocale, ResourceBundle.Control control) {
        return ImageSingletons.lookup(LocalizationSupport.class).asOptimizedSupport().getCached(baseName, targetLocale);
    }

    @TargetElement(onlyWith = OptimizedLocaleMode.class)
    @Substitute
    private static ResourceBundle getBundle(String baseName, Locale locale, ClassLoader loader) {
        return ImageSingletons.lookup(LocalizationSupport.class).asOptimizedSupport().getCached(baseName, locale);
    }

    @TargetElement(onlyWith = OptimizedLocaleMode.class)
    @Substitute
    private static ResourceBundle getBundle(String baseName, Locale targetLocale, ClassLoader loader, ResourceBundle.Control control) {
        return ImageSingletons.lookup(LocalizationSupport.class).asOptimizedSupport().getCached(baseName, targetLocale);
    }

    /**
     * Currently there is no support for the module system at run time. Module arguments are
     * therefore ignored.
     */

    @Substitute
    @TargetElement(onlyWith = OptimizedLocaleMode.class)
    private static ResourceBundle getBundle(String baseName, @SuppressWarnings("unused") Module module) {
        return ImageSingletons.lookup(LocalizationSupport.class).asOptimizedSupport().getCached(baseName, Locale.getDefault());
    }

    @Substitute
    @TargetElement(onlyWith = OptimizedLocaleMode.class)
    private static ResourceBundle getBundle(String baseName, Locale targetLocale, @SuppressWarnings("unused") Module module) {
        return ImageSingletons.lookup(LocalizationSupport.class).asOptimizedSupport().getCached(baseName, targetLocale);
    }

    @Substitute
    private static ResourceBundle getBundleImpl(String baseName,
                    Locale locale,
                    Class<?> caller,
                    ClassLoader loader,
                    ResourceBundle.Control control) {
        Module callerModule = getCallerModule(caller);

        // get resource bundles for a named module only if loader is the module's class loader
        if (callerModule.isNamed() && loader == getLoader(callerModule)) {
            if (!ImageSingletons.lookup(LocalizationSupport.class).isRegisteredBundleLookup(baseName, locale, control)) {
                MissingResourceRegistrationUtils.missingResourceBundle(baseName);
            }
            return getBundleImpl(callerModule, callerModule, baseName, locale, control);
        }

        // find resource bundles from unnamed module of given class loader
        // Java agent can add to the bootclasspath e.g. via
        // java.lang.instrument.Instrumentation and load classes in unnamed module.
        // It may call RB::getBundle that will end up here with loader == null.
        Module unnamedModule = loader != null
                        ? loader.getUnnamedModule()
                        : BootLoader.getUnnamedModule();

        if (!ImageSingletons.lookup(LocalizationSupport.class).isRegisteredBundleLookup(baseName, locale, control)) {
            MissingResourceRegistrationUtils.missingResourceBundle(baseName);
        }
        return getBundleImpl(callerModule, unnamedModule, baseName, locale, control);
    }

    @Substitute
    @SuppressWarnings({"removal", "deprecation"})
    private static ResourceBundle getBundleFromModule(Class<?> caller,
                    Module module,
                    String baseName,
                    Locale locale,
                    ResourceBundle.Control control) {
        Objects.requireNonNull(module);
        Module callerModule = getCallerModule(caller);
        if (callerModule != module) {
            SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                sm.checkPermission(GET_CLASSLOADER_PERMISSION);
            }
        }
        if (!ImageSingletons.lookup(LocalizationSupport.class).isRegisteredBundleLookup(baseName, locale, control)) {
            MissingResourceRegistrationUtils.missingResourceBundle(baseName);
        }
        return getBundleImpl(callerModule, module, baseName, locale, control);
    }

    @Alias
    private static native Module getCallerModule(Class<?> caller);

    @Alias
    private static native ClassLoader getLoader(Module module);

    @Alias
    private static native ResourceBundle getBundleImpl(Module callerModule, Module module, String baseName, Locale locale, ResourceBundle.Control control);
}
