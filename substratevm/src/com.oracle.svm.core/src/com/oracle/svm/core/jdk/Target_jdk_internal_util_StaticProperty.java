/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import java.util.Objects;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * This class provides JDK-internal access to values that are also available via system properties.
 * However, it must not return values changed by the user. We do not want to query the values during
 * VM startup, because doing that is expensive. So we perform lazy initialization by accessing the
 * corresponding system properties.
 * <p>
 * We {@link Substitute substitute} the whole class so that it is possible to use a custom static
 * constructor at run-time. If this class is used before the system properties are fully parsed and
 * initialized, it can happen that we return or cache invalid values (see GR-64572).
 * <p>
 * Note for updating: use {@link Delete} for static fields that should be unreachable (e.g, because
 * we substituted an accessor and the field is therefore unused). Use {@link Alias} for static
 * fields that can be initialized in our custom static constructor. Use {@link Substitute} for
 * methods that access expensive lazily initialized system properties (see
 * {@link SystemPropertiesSupport} for a list of all lazily initialized properties).
 */
@Substitute
@TargetClass(jdk.internal.util.StaticProperty.class)
@SuppressWarnings({"unused", "FieldCanBeLocal"})
final class Target_jdk_internal_util_StaticProperty {
    // Checkstyle: stop
    @Delete//
    private static String JAVA_HOME;

    @Delete//
    private static String USER_HOME;

    @Delete//
    private static String USER_DIR;

    @Delete//
    private static String USER_NAME;

    @Delete//
    private static String JAVA_LIBRARY_PATH;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    private static String SUN_BOOT_LIBRARY_PATH;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    private static String JDK_SERIAL_FILTER;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    private static String JDK_SERIAL_FILTER_FACTORY;

    @Delete//
    private static String JAVA_IO_TMPDIR;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    private static String NATIVE_ENCODING;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    private static String FILE_ENCODING;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    private static String JAVA_PROPERTIES_DATE;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    private static String STDIN_ENCODING;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    private static String STDERR_ENCODING;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    private static String STDOUT_ENCODING;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    private static String SUN_JNU_ENCODING;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    private static String JAVA_LOCALE_USE_OLD_ISO_CODES;

    @Delete//
    private static String OS_NAME;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    private static String OS_ARCH;

    @Delete//
    private static String OS_VERSION;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    public static String USER_LANGUAGE;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    public static String USER_LANGUAGE_DISPLAY;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    public static String USER_LANGUAGE_FORMAT;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    public static String USER_SCRIPT;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    public static String USER_SCRIPT_DISPLAY;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    public static String USER_SCRIPT_FORMAT;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    public static String USER_COUNTRY;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    public static String USER_COUNTRY_DISPLAY;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    public static String USER_COUNTRY_FORMAT;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    public static String USER_VARIANT;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    public static String USER_VARIANT_DISPLAY;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    public static String USER_VARIANT_FORMAT;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    public static String USER_EXTENSIONS;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    public static String USER_EXTENSIONS_DISPLAY;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    public static String USER_EXTENSIONS_FORMAT;

    @Alias//
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset)//
    public static String USER_REGION;
    // Checkstyle: resume

    /*
     * This static constructor is executed at run-time. Be careful that it only initializes lazy
     * system properties that are reasonably cheap to initialize (e.g., everything related to
     * locale).
     */
    static {
        if (!SubstrateUtil.HOSTED) {
            SystemPropertiesSupport p = SystemPropertiesSupport.singleton();
            SUN_BOOT_LIBRARY_PATH = p.getInitialProperty("sun.boot.library.path", "");
            JDK_SERIAL_FILTER = p.getInitialProperty("jdk.serialFilter");
            JDK_SERIAL_FILTER_FACTORY = p.getInitialProperty("jdk.serialFilterFactory");
            NATIVE_ENCODING = p.getInitialProperty("native.encoding");
            FILE_ENCODING = p.getInitialProperty("file.encoding");
            JAVA_PROPERTIES_DATE = p.getInitialProperty("java.properties.date");
            STDIN_ENCODING = p.getInitialProperty("stdin.encoding");
            STDERR_ENCODING = p.getInitialProperty("stderr.encoding");
            STDOUT_ENCODING = p.getInitialProperty("stdout.encoding");

            SUN_JNU_ENCODING = p.getInitialProperty("sun.jnu.encoding");
            JAVA_LOCALE_USE_OLD_ISO_CODES = p.getInitialProperty("java.locale.useOldISOCodes", "");

            OS_ARCH = p.getInitialProperty("os.arch");

            USER_LANGUAGE = p.getInitialProperty(UserSystemProperty.LANGUAGE, "en");
            USER_LANGUAGE_DISPLAY = p.getInitialProperty(UserSystemProperty.LANGUAGE_DISPLAY, USER_LANGUAGE);
            USER_LANGUAGE_FORMAT = p.getInitialProperty(UserSystemProperty.LANGUAGE_FORMAT, USER_LANGUAGE);
            // for compatibility, check for old user.region property
            USER_REGION = p.getInitialProperty(UserSystemProperty.REGION, "");
            if (!USER_REGION.isEmpty()) {
                // region can be of form country, country_variant, or _variant
                int i = USER_REGION.indexOf('_');
                if (i >= 0) {
                    USER_COUNTRY = USER_REGION.substring(0, i);
                    USER_VARIANT = USER_REGION.substring(i + 1);
                } else {
                    USER_COUNTRY = USER_REGION;
                    USER_VARIANT = "";
                }
                USER_SCRIPT = "";
            } else {
                USER_SCRIPT = p.getInitialProperty(UserSystemProperty.SCRIPT, "");
                USER_COUNTRY = p.getInitialProperty(UserSystemProperty.COUNTRY, "");
                USER_VARIANT = p.getInitialProperty(UserSystemProperty.VARIANT, "");
            }
            USER_SCRIPT_DISPLAY = p.getInitialProperty(UserSystemProperty.SCRIPT_DISPLAY, USER_SCRIPT);
            USER_SCRIPT_FORMAT = p.getInitialProperty(UserSystemProperty.SCRIPT_FORMAT, USER_SCRIPT);
            USER_COUNTRY_DISPLAY = p.getInitialProperty(UserSystemProperty.COUNTRY_DISPLAY, USER_COUNTRY);
            USER_COUNTRY_FORMAT = p.getInitialProperty(UserSystemProperty.COUNTRY_FORMAT, USER_COUNTRY);
            USER_VARIANT_DISPLAY = p.getInitialProperty(UserSystemProperty.VARIANT_DISPLAY, USER_VARIANT);
            USER_VARIANT_FORMAT = p.getInitialProperty(UserSystemProperty.VARIANT_FORMAT, USER_VARIANT);
            USER_EXTENSIONS = p.getInitialProperty(UserSystemProperty.EXTENSIONS, "");
            USER_EXTENSIONS_DISPLAY = p.getInitialProperty(UserSystemProperty.EXTENSIONS_DISPLAY, USER_EXTENSIONS);
            USER_EXTENSIONS_FORMAT = p.getInitialProperty(UserSystemProperty.EXTENSIONS_FORMAT, USER_EXTENSIONS);
        }
    }

    @Substitute
    private static String javaHome() {
        return SystemPropertiesSupport.singleton().getInitialProperty("java.home");
    }

    @Substitute
    private static String userHome() {
        return SystemPropertiesSupport.singleton().getInitialProperty(UserSystemProperty.HOME);
    }

    @Substitute
    private static String userDir() {
        return SystemPropertiesSupport.singleton().getInitialProperty(UserSystemProperty.DIR);
    }

    @Substitute
    private static String userName() {
        return SystemPropertiesSupport.singleton().getInitialProperty(UserSystemProperty.NAME);
    }

    @Substitute
    private static String javaLibraryPath() {
        return SystemPropertiesSupport.singleton().getInitialProperty("java.library.path", "");
    }

    @Substitute
    private static String javaIoTmpDir() {
        return SystemPropertiesSupport.singleton().getInitialProperty("java.io.tmpdir");
    }

    @Substitute
    public static String sunBootLibraryPath() {
        assert Objects.equals(SUN_BOOT_LIBRARY_PATH, SystemPropertiesSupport.singleton().getInitialProperty("sun.boot.library.path", ""));
        return SUN_BOOT_LIBRARY_PATH;
    }

    @Substitute
    public static String jdkSerialFilter() {
        assert Objects.equals(JDK_SERIAL_FILTER, SystemPropertiesSupport.singleton().getInitialProperty("jdk.serialFilter"));
        return JDK_SERIAL_FILTER;
    }

    @Substitute
    public static String jdkSerialFilterFactory() {
        assert Objects.equals(JDK_SERIAL_FILTER_FACTORY, SystemPropertiesSupport.singleton().getInitialProperty("jdk.serialFilterFactory"));
        return JDK_SERIAL_FILTER_FACTORY;
    }

    @Substitute
    public static String nativeEncoding() {
        assert Objects.equals(NATIVE_ENCODING, SystemPropertiesSupport.singleton().getInitialProperty("native.encoding"));
        return NATIVE_ENCODING;
    }

    @Substitute
    public static String fileEncoding() {
        assert Objects.equals(FILE_ENCODING, SystemPropertiesSupport.singleton().getInitialProperty("file.encoding"));
        return FILE_ENCODING;
    }

    @Substitute
    public static String javaPropertiesDate() {
        assert Objects.equals(JAVA_PROPERTIES_DATE, SystemPropertiesSupport.singleton().getInitialProperty("java.properties.date"));
        return JAVA_PROPERTIES_DATE;
    }

    @Substitute
    public static String stdinEncoding() {
        assert Objects.equals(STDIN_ENCODING, SystemPropertiesSupport.singleton().getInitialProperty("stdin.encoding"));
        return STDIN_ENCODING;
    }

    @Substitute
    public static String stdoutEncoding() {
        assert Objects.equals(STDOUT_ENCODING, SystemPropertiesSupport.singleton().getInitialProperty("stdout.encoding"));
        return STDOUT_ENCODING;
    }

    @Substitute
    public static String stderrEncoding() {
        assert Objects.equals(STDERR_ENCODING, SystemPropertiesSupport.singleton().getInitialProperty("stderr.encoding"));
        return STDERR_ENCODING;
    }

    @Substitute
    public static String jnuEncoding() {
        assert Objects.equals(SUN_JNU_ENCODING, SystemPropertiesSupport.singleton().getInitialProperty("sun.jnu.encoding"));
        return SUN_JNU_ENCODING;
    }

    @Substitute
    public static String javaLocaleUseOldISOCodes() {
        assert Objects.equals(JAVA_LOCALE_USE_OLD_ISO_CODES, SystemPropertiesSupport.singleton().getInitialProperty("java.locale.useOldISOCodes", ""));
        return JAVA_LOCALE_USE_OLD_ISO_CODES;
    }

    @Substitute
    public static String osName() {
        return SystemPropertiesSupport.singleton().getInitialProperty("os.name");
    }

    @Substitute
    public static String osArch() {
        assert Objects.equals(OS_ARCH, SystemPropertiesSupport.singleton().getInitialProperty("os.arch"));
        return OS_ARCH;
    }

    @Substitute
    public static String osVersion() {
        return SystemPropertiesSupport.singleton().getInitialProperty("os.version");
    }
}
