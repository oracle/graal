/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.thirdparty;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;

import com.oracle.svm.hosted.FeatureImpl.FeatureAccessImpl;

/**
 * ICU4JFeature enables ICU4J library ({@link "http://site.icu-project.org/"} to be used in SVM.
 * <p>
 * The main obstacle in using the ICU4J library as is was that the library relies on class loader to
 * fetch localization data from resource files included in the ICU4J jar archive. This feature is
 * not supported by SVM, so the next option was to read the resource files from the file system. The
 * following code addresses several issues that occurred when specifying
 * <code>com.ibm.icu.impl.ICUBinary.dataPath</code> system property in runtime (standard ICU4J
 * feature).
 */
@AutomaticFeature
public final class ICU4JFeature implements Feature {

    static final class IsEnabled implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return ImageSingletons.contains(ICU4JFeature.class);
        }
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return access.findClassByName("com.ibm.icu.impl.ClassLoaderUtil") != null;
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        registerShimClass(access, "com.ibm.icu.text.NumberFormatServiceShim");
        registerShimClass(access, "com.ibm.icu.text.CollatorServiceShim");
        registerShimClass(access, "com.ibm.icu.text.BreakIteratorFactory");
    }

    private static void registerShimClass(BeforeAnalysisAccess access, String shimClassName) {
        Class<?> shimClass = access.findClassByName(shimClassName);
        if (shimClass != null) {
            RuntimeReflection.registerForReflectiveInstantiation(shimClass);
        } else {
            throw VMError.shouldNotReachHere(shimClassName + " not found");
        }
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {

        // we should fail the native-image build, if any ICU4J class instance
        // made it to the build-time generated heap
        RuntimeClassInitialization.initializeAtRunTime(getIcu4jClasses(access));

        // ClassLoaderHelper is a utility class used in @TargetClass annotated substitutions bellow
        RuntimeClassInitialization.initializeAtBuildTime(ClassLoaderHelper.class);
        RuntimeClassInitialization.initializeAtBuildTime(ClassLoaderHelper.DummyClassLoader.class);
    }

    private static Class<?>[] getIcu4jClasses(FeatureAccess access) {
        List<Class<?>> allClasses = ((FeatureAccessImpl) access).findSubclasses(Object.class);
        return allClasses.stream().filter(clazz -> clazz.getName().startsWith("com.ibm.icu")).toArray(Class<?>[]::new);
    }
}

final class ClassLoaderHelper {

    static final class DummyClassLoader extends ClassLoader {
        DummyClassLoader(ClassLoader parent) {
            super(parent);
        }
    }

    /** Dummy ClassLoader used only for resource loading. */
    // Checkstyle: stop
    static final ClassLoader DUMMY_LOADER = new DummyClassLoader(null);
    // CheckStyle: resume
}

@TargetClass(className = "com.ibm.icu.impl.ClassLoaderUtil", onlyWith = ICU4JFeature.IsEnabled.class)
final class Target_com_ibm_icu_impl_ClassLoaderUtil {
    @Substitute
    // Checkstyle: stop
    public static ClassLoader getClassLoader() {
        return ClassLoaderHelper.DUMMY_LOADER;
    }
    // Checkstyle: resume
}

@TargetClass(className = "com.ibm.icu.impl.ICUBinary", onlyWith = ICU4JFeature.IsEnabled.class)
final class Target_com_ibm_icu_impl_ICUBinary {

    @Alias
    static native void addDataFilesFromPath(String dataPath, List<?> files);

    @Alias @InjectAccessors(IcuDataFilesAccessors.class) static List<?> icuDataFiles;

    static final class IcuDataFilesAccessors {

        private static final String ICU4J_DATA_PATH_SYS_PROP = "com.ibm.icu.impl.ICUBinary.dataPath";
        private static final String ICU4J_DATA_PATH_ENV_VAR = "ICU4J_DATA_PATH";

        private static final String NO_DATA_PATH_ERR_MSG = "No ICU4J data path was set or found. This will likely end up with a MissingResourceException. " +
                        "To take advantage of the ICU4J library, you should either set system property, " +
                        ICU4J_DATA_PATH_SYS_PROP +
                        ", or set environment variable, " +
                        ICU4J_DATA_PATH_ENV_VAR +
                        ", to contain path to your ICU4J icudt directory";

        // once fully populated, the data file list will be kept here
        private static volatile List<?> instance = null;
        // helper collection to which the list will be populated
        private static List<?> populatingDataFiles = null;

        private static final Object lock = new Object();

        @NeverInline("So the lock does not get eliminated.")
        static List<?> get() {

            if (instance == null) {
                // Checkstyle: allow synchronization
                synchronized (lock) {
                    if (instance == null) {
                        if (populatingDataFiles == null) {
                            // the very first call, from "outside"
                            populatingDataFiles = new ArrayList<>();
                        } else {
                            // second, re-entrant, call from the same thread
                            // comes from the addDataFilesFromPath method invoked bellow
                            return populatingDataFiles;
                        }

                        String dataPath = System.getProperty(ICU4J_DATA_PATH_SYS_PROP);
                        if (dataPath == null || dataPath.isEmpty()) {
                            dataPath = System.getenv(ICU4J_DATA_PATH_ENV_VAR);
                        }
                        if (dataPath != null && !dataPath.isEmpty()) {
                            addDataFilesFromPath(dataPath, populatingDataFiles);
                        } else {
                            System.err.println(NO_DATA_PATH_ERR_MSG);
                        }
                        instance = populatingDataFiles;
                    }
                }
                // Checkstyle: disallow synchronization
            }
            return instance;
        }

        /*
         * Any attempt to write to the list should be handled as no-op. The list of ICU4J resource
         * bundle files should be strictly defined by one of the above mentioned system property,
         * ICU4J_DATA_PATH_SYS_PROP, or environment variable, ICU4J_DATA_PATH_ENV_VAR.
         */
        static void set(@SuppressWarnings("unused") List<?> bummer) {
        }
    }
}

@TargetClass(className = "com.ibm.icu.impl.ICUResourceBundle", onlyWith = ICU4JFeature.IsEnabled.class)
final class Target_com_ibm_icu_impl_ICUResourceBundle {
    @Alias @RecomputeFieldValue(kind = Kind.FromAlias, isFinal = true)
    // Checkstyle: stop
    private static ClassLoader ICU_DATA_CLASS_LOADER = ClassLoaderHelper.DUMMY_LOADER;
    // Checkstyle: resume

    @SuppressWarnings("unused")
    @Substitute
    // Checkstyle: stop
    private static void addBundleBaseNamesFromClassLoader(final String bn, final ClassLoader root, final Set<String> names) {
    }
    // Checkstyle: resume
}

@TargetClass(className = "com.ibm.icu.impl.ICUResourceBundle$WholeBundle", onlyWith = ICU4JFeature.IsEnabled.class)
final class Target_com_ibm_icu_impl_ICUResourceBundle_WholeBundle {
    @Alias @RecomputeFieldValue(kind = Kind.Reset)
    // Checkstyle: stop
    ClassLoader loader;
    // Checkstyle: resume
}

@TargetClass(className = "com.ibm.icu.impl.ICUResourceBundle$AvailEntry", onlyWith = ICU4JFeature.IsEnabled.class)
final class Target_com_ibm_icu_impl_ICUResourceBundle_AvailEntry {
    @Alias @RecomputeFieldValue(kind = Kind.Reset)
    // Checkstyle: stop
    ClassLoader loader;
    // Checkstyle: resume
}
