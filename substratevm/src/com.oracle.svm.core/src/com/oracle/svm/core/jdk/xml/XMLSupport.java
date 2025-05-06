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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeResourceSupport;

import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.util.BasedOnJDKClass;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

@BasedOnJDKClass(className = "jdk.xml.internal.SecuritySupport")
public final class XMLSupport {

    private static final Module xmlModule = ReflectionUtil.lookupClass("jdk.xml.internal.SecuritySupport").getModule();
    private static final String JAXP_PROPERTIES = "jaxp.properties";
    private static final String STAX_PROPERTIES = "stax.properties";

    static boolean loadJaxpProperties() {
        return loadProperties(JAXP_PROPERTIES);
    }

    static void loadStaxProperties() {
        loadProperties(STAX_PROPERTIES);
    }

    private static boolean loadProperties(String resource) {
        try (InputStream in = Resources.createInputStream(xmlModule, resource)) {
            if (in == null) {
                return false;
            }
            Target_jdk_xml_internal_SecuritySupport.cacheProps.load(in);
            return true;
        } catch (IOException e) {
            // shouldn't happen, but required by method Properties.load
            throw VMError.shouldNotReachHere(e);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void readProperties() {
        byte[] jaxpProperty = readProperties(Paths.get(System.getProperty("java.home"), "conf", JAXP_PROPERTIES)
                        .toAbsolutePath().normalize());
        byte[] staxProperty = jaxpProperty != null ? null
                        : readProperties(Paths.get(System.getProperty("java.home"), "conf", STAX_PROPERTIES)
                                        .toAbsolutePath().normalize());
        addResource(xmlModule, JAXP_PROPERTIES, jaxpProperty);
        addResource(xmlModule, STAX_PROPERTIES, staxProperty);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void addResource(Module module, String resourcePath, byte[] resourceContent) {
        if (resourceContent != null) {
            RuntimeResourceSupport.singleton().injectResource(module, resourcePath, resourceContent, "Added via " + XMLSupport.class.getName());
        } else {
            Resources.currentLayer().registerNegativeQuery(module, resourcePath);
        }
        RuntimeResourceSupport.singleton().addCondition(ConfigurationCondition.alwaysTrue(), module, resourcePath);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static byte[] readProperties(Path path) {
        File f = path.toFile();
        if (f.exists()) {
            try {
                return Files.readAllBytes(path);
            } catch (IOException e) {
                // shouldn't happen, but required by method getFileInputStream
            }
        }
        return null;
    }
}
