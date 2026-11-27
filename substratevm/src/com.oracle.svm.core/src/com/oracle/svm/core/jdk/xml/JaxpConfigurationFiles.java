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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.RuntimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.ApplicationLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.VMError;

/**
 * The JAXP configuration files {@code jaxp.properties} and {@code stax.properties} from the
 * {@code conf} directory of the JDK that builds the image, for an image without a {@code java.home}
 * at run time. A {@code null} content means the JDK would not load the file: it is missing or
 * cannot be read, or, for {@code stax.properties}, {@code jaxp.properties} was read. See
 * {@link Target_jdk_xml_internal_JdkXmlConfig}.
 */
@SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = ApplicationLayerOnly.class)
final class JaxpConfigurationFiles {
    static final String JAXP_PROPERTIES = "jaxp.properties";
    static final String STAX_PROPERTIES = "stax.properties";
    /**
     * {@code JdkConstants.CONFIG_FILE_PROPNAME}. As in the JDK, the compiler inlines the constant:
     * reading the field of {@code JdkConstants} would run its class initializer, which calls
     * {@code JdkXmlConfig.getInstance} while the first instance is still being created.
     */
    static final String CONFIG_FILE_PROPERTY = "java.xml.config.file";

    private final byte[] jaxpProperties;
    private final byte[] staxProperties;

    JaxpConfigurationFiles(byte[] jaxpProperties, byte[] staxProperties) {
        this.jaxpProperties = jaxpProperties;
        this.staxProperties = staxProperties;
    }

    boolean loadJaxpProperties(Properties properties) {
        return load(properties, jaxpProperties);
    }

    boolean loadStaxProperties(Properties properties) {
        return load(properties, staxProperties);
    }

    private static boolean load(Properties properties, byte[] content) {
        if (content == null) {
            return false;
        }
        try {
            properties.load(new ByteArrayInputStream(content));
            return true;
        } catch (IOException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }
}

@AutomaticallyRegisteredFeature
final class JaxpConfigurationFilesFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.lastImageBuild() && new JavaXmlModulePresent().getAsBoolean() && new XmlInitializedAtRunTime().getAsBoolean();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        Path conf = Path.of(System.getProperty("java.home"), "conf");
        byte[] jaxpProperties = read(conf.resolve(JaxpConfigurationFiles.JAXP_PROPERTIES));
        /* As the JDK, use stax.properties only if jaxp.properties cannot be read. */
        byte[] staxProperties = jaxpProperties == null ? read(conf.resolve(JaxpConfigurationFiles.STAX_PROPERTIES)) : null;
        ImageSingletons.add(JaxpConfigurationFiles.class, new JaxpConfigurationFiles(jaxpProperties, staxProperties));
    }

    /**
     * Returns the content of a configuration file, or {@code null} if it is missing or cannot be
     * read: the JDK treats both as an unsuccessful read.
     */
    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            return null;
        }
    }
}
