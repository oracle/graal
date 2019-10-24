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
package com.oracle.svm.core.jdk11;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.jdk.SystemPropertiesSupport;

/**
 * This class provides JDK-internal access to values that are also available via system properties.
 * However, it must not return values changes by the user. We do not want to query the values during
 * VM startup, because doing that is expensive. So we perform lazy initialization by calling the
 * same methods also used to initialize the system properties.
 */
@Substitute
@TargetClass(className = "jdk.internal.util.StaticProperty", onlyWith = JDK11OrLater.class)
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
}
