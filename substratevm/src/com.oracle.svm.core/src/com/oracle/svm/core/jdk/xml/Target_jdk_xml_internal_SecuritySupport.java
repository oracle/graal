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
package com.oracle.svm.core.jdk.xml;

import java.util.Properties;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK21OrEarlier;
import com.oracle.svm.core.jdk.JDKInitializedAtBuildTime;
import com.oracle.svm.core.jdk.JDKLatest;

import jdk.graal.compiler.serviceprovider.JavaVersionUtil;

@TargetClass(className = "jdk.xml.internal.SecuritySupport", onlyWith = JDKInitializedAtBuildTime.class)
public final class Target_jdk_xml_internal_SecuritySupport {

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias, isFinal = true) //
    static Properties cacheProps = new Properties();
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.FromAlias) //
    private static volatile boolean firstTime = true;

    @Substitute
    public static String readConfig(String propName, boolean stax) {
        // always load the default configuration file
        if (firstTime) {
            synchronized (cacheProps) {
                if (firstTime) {
                    boolean found = XMLSupport.loadJaxpProperties();

                    // attempts to find stax.properties only if jaxp.properties is not available
                    if (stax && !found) {
                        XMLSupport.loadStaxProperties();
                    }

                    // load the custom configure on top of the default if any
                    String configFile = Target_jdk_xml_internal_SecuritySupport
                                    .getSystemProperty(JavaVersionUtil.JAVA_SPEC > 21 ? Target_jdk_xml_internal_JdkConstants.CONFIG_FILE_PROPNAME : Target_jdk_xml_internal_JdkConstants.CONFIG_FILE);
                    if (configFile != null) {
                        loadProperties(configFile);
                    }

                    firstTime = false;
                }
            }
        }

        return cacheProps.getProperty(propName);
    }

    @Alias
    static native boolean loadProperties(String resourceName);

    @Alias
    public static native String getSystemProperty(final String propName);
}

@TargetClass(className = "jdk.xml.internal.JdkConstants", onlyWith = JDKInitializedAtBuildTime.class)
final class Target_jdk_xml_internal_JdkConstants {
    @Alias //
    @TargetElement(onlyWith = JDKLatest.class) //
    public static String CONFIG_FILE_PROPNAME;

    @Alias //
    @TargetElement(onlyWith = JDK21OrEarlier.class) //
    public static String CONFIG_FILE;
}