/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Paths;
import java.util.Properties;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * Loads the JAXP configuration when the {@code JdkXmlConfig} singleton is created at run time.
 *
 * The JDK reads {@code $java.home/conf/jaxp.properties}, or {@code stax.properties} for StAX when
 * {@code jaxp.properties} cannot be read. An image with a {@code java.home} at run time, such as a
 * {@code libjvm} image or one started with {@code -Djava.home}, reads the files of that JDK, as the
 * JDK does. An image has no {@code java.home} by default, and the JDK code fails without it, so
 * such an image reads the files of the JDK that built it instead (see
 * {@link JaxpConfigurationFiles}). In both cases, the file named by the
 * {@code java.xml.config.file} system property is read last, from the file system at run time, as
 * in the JDK.
 */
@TargetClass(className = "jdk.xml.internal.JdkXmlConfig", onlyWith = {JavaXmlModulePresent.class, XmlInitializedAtRunTime.class})
final class Target_jdk_xml_internal_JdkXmlConfig {
    @Alias //
    private Properties jaxpConfig;

    @Alias
    private native boolean loadProperties(String file);

    @Substitute
    private void loadConfig(boolean stax) {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            boolean found = loadProperties(Paths.get(javaHome, "conf", JaxpConfigurationFiles.JAXP_PROPERTIES).toAbsolutePath().normalize().toString());

            // attempts to find stax.properties only if jaxp.properties is not available
            if (stax && !found) {
                loadProperties(Paths.get(javaHome, "conf", JaxpConfigurationFiles.STAX_PROPERTIES).toAbsolutePath().normalize().toString());
            }
        } else {
            JaxpConfigurationFiles files = ImageSingletons.lookup(JaxpConfigurationFiles.class);
            boolean found = files.loadJaxpProperties(jaxpConfig);
            if (stax && !found) {
                files.loadStaxProperties(jaxpConfig);
            }
        }

        // load the custom configure on top of the default if any
        String configFile = System.getProperty(JaxpConfigurationFiles.CONFIG_FILE_PROPERTY);
        if (configFile != null) {
            loadProperties(configFile);
        }
    }
}
