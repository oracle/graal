/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.RuntimeReflection;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.core.util.VMError;

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
    }

    private static void registerShimClass(BeforeAnalysisAccess access, String shimClassName) {
        Class<?> numberFormatServiceShim = access.findClassByName(shimClassName);
        if (numberFormatServiceShim != null) {
            RuntimeReflection.register(numberFormatServiceShim.getDeclaredConstructors());
            ClassForNameSupport.registerClass(numberFormatServiceShim);
        } else {
            throw VMError.shouldNotReachHere(shimClassName + " not found");
        }
    }

    static class Helper {
        /** Dummy ClassLoader used only for resource loading. */
        // Checkstyle: stop
        static final ClassLoader DUMMY_LOADER = new ClassLoader(null) {
        };
        // CheckStyle: resume
    }
}

@TargetClass(className = "com.ibm.icu.impl.ClassLoaderUtil", onlyWith = ICU4JFeature.IsEnabled.class)
final class Target_com_ibm_icu_impl_ClassLoaderUtil {
    @Substitute
    // Checkstyle: stop
    public static ClassLoader getClassLoader() {
        return ICU4JFeature.Helper.DUMMY_LOADER;
    }
    // Checkstyle: resume
}

@TargetClass(className = "com.ibm.icu.impl.ICUBinary", onlyWith = ICU4JFeature.IsEnabled.class)
final class Target_com_ibm_icu_impl_ICUBinary {

    @Alias
    static native void addDataFilesFromPath(String dataPath, List<?> files);

    @Alias @InjectAccessors(IcuDataFilesAccessors.class) static List<?> icuDataFiles;

    static final class IcuDataFilesAccessors {

        private static volatile List<?> instance;

        static List<?> get() {

            if (instance == null) {
                // Checkstyle: allow synchronization
                synchronized (IcuDataFilesAccessors.class) {
                    if (instance == null) {
                        instance = new ArrayList<>();
                        String dataPath = System.getProperty("com.ibm.icu.impl.ICUBinary.dataPath");
                        if (dataPath != null && !dataPath.isEmpty()) {
                            addDataFilesFromPath(dataPath, instance);
                        }
                    }
                }
                // Checkstyle: disallow synchronization
            }
            return instance;
        }
    }
}

@TargetClass(className = "com.ibm.icu.impl.ICUResourceBundle", onlyWith = ICU4JFeature.IsEnabled.class)
final class Target_com_ibm_icu_impl_ICUResourceBundle {
    @Alias @RecomputeFieldValue(kind = Kind.FromAlias, isFinal = true)
    // Checkstyle: stop
    private static final ClassLoader ICU_DATA_CLASS_LOADER = ICU4JFeature.Helper.DUMMY_LOADER;
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

// this whole class substitution was made to work around the original
// locking on int[] m_utilIntBuffer array, as array locks are not supported in svm
// the orginal copyright notice follows:
// Checkstyle: stop
// © 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License
/*
 *******************************************************************************
 * Copyright (C) 1996-2014, International Business Machines Corporation and others. All Rights
 * Reserved.
 *******************************************************************************
 */
// Checkstyle: resume
@TargetClass(className = "com.ibm.icu.impl.UCharacterName$AlgorithmName", onlyWith = ICU4JFeature.IsEnabled.class)
final class Target_com_ibm_icu_impl_UCharacterName_AlgorithmName {

    // Checkstyle: stop fieldname check
    // this is our new lock (locking on arrays is not supported)
    @Inject @RecomputeFieldValue(kind = Kind.NewInstance, declClass = Object.class) private final Object m_utilIntBufferLock_ = new Object();

    @Alias private int[] m_utilIntBuffer_;

    @Alias private int m_rangestart_;
    @Alias private int m_rangeend_;
    @Alias private byte m_type_;
    @Alias private byte m_variant_;
    @Alias private char[] m_factor_;
    @Alias private String m_prefix_;
    // Checkstyle: resume fieldname check

    @Alias
    private native String getFactorString(int[] index, int length);

    @Alias
    private native boolean compareFactorString(int[] index, int length, String str, int offset);

    @Substitute
    void appendName(int ch, StringBuffer str) {
        str.append(m_prefix_);
        switch (m_type_) {
            case 0:
                // prefix followed by hex digits indicating variants
                str.append(Target_com_ibm_icu_impl_Utility.hex(ch, m_variant_));
                break;
            case 1:
                // prefix followed by factorized-elements
                int offset = ch - m_rangestart_;
                int[] indexes = m_utilIntBuffer_;
                int factor;

                // write elements according to the factors
                // the factorized elements are determined by modulo
                // arithmetic
                // Checkstyle: allow synchronization
                synchronized (m_utilIntBufferLock_) {
                    for (int i = m_variant_ - 1; i > 0; i--) {
                        factor = m_factor_[i] & 0x00FF;
                        indexes[i] = offset % factor;
                        offset /= factor;
                    }

                    // we don't need to calculate the last modulus because
                    // start <= code <= end guarantees here that
                    // code <= factors[0]
                    indexes[0] = offset;

                    // joining up the factorized strings
                    str.append(getFactorString(indexes, m_variant_));
                }
                // Checkstyle: disallow synchronization
                break;
        }
    }

    @Substitute
    int getChar(String name) {
        int prefixlen = m_prefix_.length();
        if (name.length() < prefixlen ||
                        !m_prefix_.equals(name.substring(0, prefixlen))) {
            return -1;
        }

        switch (m_type_) {
            case 0:
                try {
                    int result = Integer.parseInt(name.substring(prefixlen),
                                    16);
                    // does it fit into the range?
                    if (m_rangestart_ <= result && result <= m_rangeend_) {
                        return result;
                    }
                } catch (NumberFormatException e) {
                    return -1;
                }
                break;
            case 1:
                // repetitative suffix name comparison done here
                // offset is the character code - start
                for (int ch = m_rangestart_; ch <= m_rangeend_; ch++) {
                    int offset = ch - m_rangestart_;
                    int[] indexes = m_utilIntBuffer_;
                    int factor;

                    // write elements according to the factors
                    // the factorized elements are determined by modulo
                    // arithmetic
                    // Checkstyle: stop
                    synchronized (m_utilIntBufferLock_) {
                        // Checkstyle: resume
                        for (int i = m_variant_ - 1; i > 0; i--) {
                            factor = m_factor_[i] & 0x00FF;
                            indexes[i] = offset % factor;
                            offset /= factor;
                        }

                        // we don't need to calculate the last modulus
                        // because start <= code <= end guarantees here that
                        // code <= factors[0]
                        indexes[0] = offset;

                        // joining up the factorized strings
                        if (compareFactorString(indexes, m_variant_, name,
                                        prefixlen)) {
                            return ch;
                        }
                    }
                }
        }

        return -1;
    }

}

@TargetClass(className = "com.ibm.icu.impl.Utility", onlyWith = ICU4JFeature.IsEnabled.class)
final class Target_com_ibm_icu_impl_Utility {
    @Alias
    public static native String hex(long i, int places);
}

@TargetClass(className = "com.ibm.icu.util.TimeZone", onlyWith = ICU4JFeature.IsEnabled.class)
final class Target_com_ibm_icu_util_TimeZone {
    // clear default time zone to force reinitialization at run time
    @Alias @RecomputeFieldValue(kind = Kind.Reset) private static Target_com_ibm_icu_util_TimeZone defaultZone;
}
