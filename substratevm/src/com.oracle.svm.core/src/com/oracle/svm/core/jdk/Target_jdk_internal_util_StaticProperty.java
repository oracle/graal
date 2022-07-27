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

import java.nio.charset.Charset;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.localization.substitutions.Target_java_nio_charset_Charset;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.util.StaticProperty;

/**
 * This class provides JDK-internal access to values that are also available via system properties.
 * However, it must not return values changes by the user. We do not want to query the values during
 * VM startup, because doing that is expensive. So we perform lazy initialization by calling the
 * same methods also used to initialize the system properties.
 */
@Substitute
@TargetClass(jdk.internal.util.StaticProperty.class)
@SuppressWarnings("unused")
final class Target_jdk_internal_util_StaticProperty {

    @Substitute
    private static String javaHome() {
        /* Native images do not have a Java home directory. */
        return null;
    }

    @Substitute
    private static String userHome() {
        return ImageSingletons.lookup(SystemPropertiesSupport.class).userHome();
    }

    @Substitute
    private static String userDir() {
        return ImageSingletons.lookup(SystemPropertiesSupport.class).userDir();
    }

    @Substitute
    private static String userName() {
        return ImageSingletons.lookup(SystemPropertiesSupport.class).userName();
    }

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    private static String javaIoTmpDir() {
        return ImageSingletons.lookup(SystemPropertiesSupport.class).javaIoTmpDir();
    }

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    private static String javaLibraryPath() {
        return ImageSingletons.lookup(SystemPropertiesSupport.class).javaLibraryPath();
    }

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    private static String sunBootLibraryPath() {
        String value = ImageSingletons.lookup(SystemPropertiesSupport.class).savedProperties.get("sun.boot.library.path");
        return value == null ? "" : value;
    }

    @Substitute
    private static String jdkSerialFilter() {
        return ImageSingletons.lookup(SystemPropertiesSupport.class).savedProperties.get("jdk.serialFilter");
    }

    @Substitute
    @TargetElement(onlyWith = StaticPropertyJdkSerialFilterFactoryAvailable.class)
    private static String jdkSerialFilterFactory() {
        return ImageSingletons.lookup(SystemPropertiesSupport.class).savedProperties.get("jdk.serialFilterFactory");
    }

    private abstract static class StaticPropertyMethodAvailable implements BooleanSupplier {

        private final String methodName;

        protected StaticPropertyMethodAvailable(String methodName) {
            this.methodName = methodName;
        }

        @Override
        public boolean getAsBoolean() {
            return ReflectionUtil.lookupMethod(true, StaticProperty.class, methodName) != null;
        }
    }

    /*
     * Method jdkSerialFilterFactory is present in some versions of the JDK11 and not in the other.
     * It is always present in the JDK17. We need to check if this method should be substituted by
     * checking if it exists in the running JDK version.
     */
    private static class StaticPropertyJdkSerialFilterFactoryAvailable extends StaticPropertyMethodAvailable {
        protected StaticPropertyJdkSerialFilterFactoryAvailable() {
            super("jdkSerialFilterFactory");
        }
    }

    @Substitute
    @TargetElement(onlyWith = JDK17OrLater.class)
    public static String nativeEncoding() {
        return ImageSingletons.lookup(SystemPropertiesSupport.class).savedProperties.get("native.encoding");
    }

    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class)
    public static String fileEncoding() {
        return ImageSingletons.lookup(SystemPropertiesSupport.class).savedProperties.get("file.encoding");
    }

    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class)
    public static String javaPropertiesDate() {
        return ImageSingletons.lookup(SystemPropertiesSupport.class).savedProperties.getOrDefault("java.properties.date", null);
    }

    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class)
    public static String jnuEncoding() {
        return ImageSingletons.lookup(SystemPropertiesSupport.class).savedProperties.get("sun.jnu.encoding");
    }

    @Substitute
    @TargetElement(onlyWith = JDK19OrLater.class)
    public static Charset jnuCharset() {
        String jnuEncoding = ImageSingletons.lookup(SystemPropertiesSupport.class).savedProperties.get("sun.jnu.encoding");
        return Target_java_nio_charset_Charset.forName(jnuEncoding, Charset.defaultCharset());
    }
}
