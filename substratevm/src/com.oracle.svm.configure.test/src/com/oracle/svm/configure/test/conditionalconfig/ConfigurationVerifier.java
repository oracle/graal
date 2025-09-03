/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.test.conditionalconfig;

import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.configure.ConfigurationBase;
import com.oracle.svm.configure.ConfigurationFile;
import com.oracle.svm.configure.config.ConfigurationFileCollection;
import com.oracle.svm.configure.config.ConfigurationSet;
import com.oracle.svm.configure.test.AddExports;

import jdk.graal.compiler.util.json.JsonWriter;

/**
 * Test that validates the configuration metadata produced by the agent. This test is invoked
 * manually from mx.
 */
@AddExports({"org.graalvm.nativeimage/org.graalvm.nativeimage.impl", "jdk.graal.compiler/jdk.graal.compiler.util", "jdk.graal.compiler/jdk.graal.compiler.util.json"})
public class ConfigurationVerifier {

    public static final String CONFIG_PATH_PROPERTY = ConfigurationVerifier.class.getName() + ".configpath";

    @Test
    public void testConfig() throws Exception {
        assumeTrue("Test must be explicitly enabled because it is not designed for regular execution", Boolean.getBoolean(ConfigurationVerifier.class.getName() + ".enabled"));
        ConfigurationSet actualConfig = loadActualConfig();
        ConfigurationSet expectedConfig = loadExpectedConfig();

        ConfigurationSet missingConfig = expectedConfig.copyAndSubtract(actualConfig);
        ConfigurationSet extraConfig = actualConfig.copyAndSubtract(expectedConfig);

        if (!missingConfig.isEmpty()) {
            System.err.println("The following configuration was expected but not found:");
            System.err.println(getConfigurationJSON(missingConfig));
        }
        if (!extraConfig.isEmpty()) {
            System.err.println("The following configuration was not expected: ");
            System.err.println(getConfigurationJSON(extraConfig));
        }
        Assert.assertTrue(missingConfig.isEmpty() && extraConfig.isEmpty());
    }

    private static String getConfigurationJSON(ConfigurationSet config) throws IOException {
        StringWriter sw = new StringWriter();
        try (JsonWriter writer = new JsonWriter(sw)) {
            for (ConfigurationFile file : ConfigurationFile.agentGeneratedFiles()) {
                ConfigurationBase<?, ?> configuration = config.getConfiguration(file);
                if (!configuration.isEmpty()) {
                    sw.append("\n").append(file.getName()).append("\n");
                    if (configuration.supportsCombinedFile()) {
                        configuration.printJson(writer);
                    } else {
                        configuration.printLegacyJson(writer);
                    }
                }
            }
            return sw.toString();
        }
    }

    private static ConfigurationSet loadActualConfig() throws Exception {
        String configurationPath = System.getProperty(CONFIG_PATH_PROPERTY);
        ConfigurationFileCollection configurationFileCollection = new ConfigurationFileCollection();
        configurationFileCollection.addDirectory(Paths.get(configurationPath));
        return configurationFileCollection.loadConfigurationSet(e -> e, null, null);
    }

    private static ConfigurationSet loadExpectedConfig() throws Exception {
        ConfigurationFileCollection configurationFileCollection = new ConfigurationFileCollection();
        configurationFileCollection.addDirectory(resourceFileName -> {
            try {
                String resourceName = "config-dir/" + resourceFileName;
                URL resourceURL = ConfigurationVerifier.class.getResource(resourceName);
                return resourceURL == null ? null : resourceURL.toURI();
            } catch (Exception e) {
                throw new AssertionError("Unexpected error while locating the configuration files.", e);
            }
        });
        return configurationFileCollection.loadConfigurationSet(e -> e, null, null);
    }

}
