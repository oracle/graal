/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.configure.test.config;

import java.io.IOException;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import org.graalvm.nativeimage.dynamicaccess.AccessCondition;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.configure.ConfigurationParserOption;
import com.oracle.svm.configure.ResourceConfigurationParser;
import com.oracle.svm.configure.ResourcesRegistry;
import com.oracle.svm.configure.UnresolvedAccessCondition;
import com.oracle.svm.configure.config.ResourceConfiguration;
import com.oracle.svm.configure.config.conditional.AccessConditionResolver;

import jdk.graal.compiler.util.json.JsonWriter;

public class ResourceConfigurationTest {

    @Test
    public void anyResourceMatches() {
        ResourceConfiguration rc = new ResourceConfiguration();
        UnresolvedAccessCondition defaultCond = UnresolvedAccessCondition.unconditional();
        rc.addResourcePattern(defaultCond, ".*/Resource.*txt$");

        Assert.assertTrue(rc.anyResourceMatches("com/my/app/Resource0.txt"));
        Assert.assertTrue(rc.anyResourceMatches("com/my/app/Resource1.txt"));
        Assert.assertTrue(rc.anyResourceMatches("/Resource2.txt"));
        Assert.assertTrue(rc.anyResourceMatches("/Resource3.txt"));

        rc.ignoreResourcePattern(defaultCond, ".*/Resource2.txt$");

        Assert.assertTrue(rc.anyResourceMatches("com/my/app/Resource0.txt"));
        Assert.assertTrue(rc.anyResourceMatches("com/my/app/Resource1.txt"));
        Assert.assertFalse(rc.anyResourceMatches("/Resource2.txt"));
        Assert.assertTrue(rc.anyResourceMatches("/Resource3.txt"));
    }

    @Test
    public void printJson() {
        ResourceConfiguration rc = new ResourceConfiguration();
        UnresolvedAccessCondition defaultCond = UnresolvedAccessCondition.unconditional();
        rc.addResourcePattern(defaultCond, ".*/Resource.*txt$");
        rc.ignoreResourcePattern(defaultCond, ".*/Resource2.txt$");
        PipedWriter pw = new PipedWriter();
        JsonWriter jw = new JsonWriter(pw);

        try (PipedReader pr = new PipedReader()) {
            pr.connect(pw);

            Thread writerThread = new Thread(() -> {
                try (JsonWriter w = jw) {
                    rc.printLegacyJson(w);
                } catch (IOException e) {
                    Assert.fail(e.getMessage());
                }
            });

            List<String> addedResources = new LinkedList<>();
            List<String> ignoredResources = new LinkedList<>();

            ResourcesRegistry<UnresolvedAccessCondition> registry = new ResourcesRegistry<>() {

                @Override
                public void addResources(UnresolvedAccessCondition condition, String pattern, Object origin) {
                    addedResources.add(pattern);
                }

                @Override
                public void addGlob(UnresolvedAccessCondition condition, String module, String glob, Object origin) {
                    throw new AssertionError("Unused function.");
                }

                @Override
                public void addResourceEntry(Module module, String resourcePath, Object origin) {
                    throw new AssertionError("Unused function.");
                }

                @Override
                public void injectResource(Module module, String resourcePath, byte[] resourceContent, Object origin) {
                }

                @Override
                public void ignoreResources(UnresolvedAccessCondition condition, String pattern, Object origin) {
                    ignoredResources.add(pattern);
                }

                @Override
                public void addResourceBundles(UnresolvedAccessCondition condition, boolean preserved, String name) {
                }

                @Override
                public void addResourceBundles(UnresolvedAccessCondition condition, String basename, Collection<Locale> locales) {

                }

                @Override
                public void addCondition(AccessCondition accessCondition, Module module, String resourcePath) {

                }

                @Override
                public void addClassBasedResourceBundle(UnresolvedAccessCondition condition, String basename, String className) {

                }
            };

            ResourceConfigurationParser<UnresolvedAccessCondition> rcp = ResourceConfigurationParser.create(false, AccessConditionResolver.identityResolver(), registry,
                            EnumSet.of(ConfigurationParserOption.STRICT_CONFIGURATION));
            writerThread.start();
            rcp.parseAndRegister(pr);

            writerThread.join();

            Assert.assertTrue(addedResources.contains(".*/Resource.*txt$"));
            Assert.assertTrue(ignoredResources.contains(".*/Resource2.txt$"));
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void quotedRegexExcludeRegistersAsRegex() throws IOException {
        List<String> ignoredResources = new ArrayList<>();
        ResourceConfigurationParser<UnresolvedAccessCondition> parser = createParser(newTestRegistry(new LinkedList<>(), ignoredResources));
        String config = "{ \"resources\": { \"includes\": [], \"excludes\": [ " +
                        "{ \"pattern\": \"\\\\QMETA-INF/services/com.alibaba.csp.sentinel.cluster.TokenService\\\\E\" } ] } }";

        parser.parseAndRegister(new StringReader(config));

        Assert.assertEquals(List.of("\\QMETA-INF/services/com.alibaba.csp.sentinel.cluster.TokenService\\E"), ignoredResources);
    }

    @Test
    public void moduleQualifiedBundlesRoundTripInLegacyConfig() throws IOException {
        String json = """
                        {
                          "resources": {
                            "includes": []
                          },
                          "bundles": [
                            {
                              "module": "first.module",
                              "name": "com.example.Messages",
                              "locales": ["en-US"]
                            },
                            {
                              "module": "second.module",
                              "name": "com.example.Messages",
                              "classNames": ["com.example.MessagesBundle"]
                            }
                          ],
                          "globs": []
                        }
                        """;

        ResourceConfiguration rc = new ResourceConfiguration();
        rc.createParser(false, EnumSet.of(ConfigurationParserOption.STRICT_CONFIGURATION)).parseAndRegister(new StringReader(json));

        UnresolvedAccessCondition condition = UnresolvedAccessCondition.unconditional();
        Assert.assertTrue(rc.anyBundleMatches(condition, "first.module", "com.example.Messages"));
        Assert.assertTrue(rc.anyBundleMatches(condition, "second.module", "com.example.Messages"));
        Assert.assertFalse(rc.anyBundleMatches(condition, "com.example.Messages"));

        String printed = printLegacyJson(rc);
        Assert.assertTrue(printed.contains("\"module\":\"first.module\""));
        Assert.assertTrue(printed.contains("\"module\":\"second.module\""));
        Assert.assertTrue(printed.contains("\"name\":\"com.example.Messages\""));
        Assert.assertTrue(printed.contains("\"classNames\":[\"com.example.MessagesBundle\"]"));
        Assert.assertTrue(printed.contains("\"locales\":[\"en-US\"]"));
    }

    @Test
    public void moduleQualifiedBundlesUseQualifiedNamesForLegacyRegistryCallbacks() throws IOException {
        String json = """
                        {
                          "bundles": [
                            {
                              "module": "first.module",
                              "name": "com.example.Messages",
                              "locales": ["en-US"]
                            },
                            {
                              "module": "second.module",
                              "name": "com.example.Messages",
                              "classNames": ["com.example.MessagesBundle"]
                            },
                            {
                              "module": "third.module",
                              "name": "com.example.Messages"
                            }
                          ]
                        }
                        """;

        List<String> localizedBundles = new LinkedList<>();
        List<String> classBasedBundles = new LinkedList<>();
        List<String> allLocaleBundles = new LinkedList<>();

        ResourcesRegistry<UnresolvedAccessCondition> registry = new ResourcesRegistry<>() {
            @Override
            public void addResources(UnresolvedAccessCondition condition, String pattern, Object origin) {
                throw new AssertionError("Unused function.");
            }

            @Override
            public void addGlob(UnresolvedAccessCondition condition, String module, String glob, Object origin) {
                throw new AssertionError("Unused function.");
            }

            @Override
            public void addResourceEntry(Module module, String resourcePath, Object origin) {
                throw new AssertionError("Unused function.");
            }

            @Override
            public void injectResource(Module module, String resourcePath, byte[] resourceContent, Object origin) {
            }

            @Override
            public void ignoreResources(UnresolvedAccessCondition condition, String pattern, Object origin) {
                throw new AssertionError("Unused function.");
            }

            @Override
            public void addResourceBundles(UnresolvedAccessCondition condition, boolean preserved, String name) {
                allLocaleBundles.add(name);
            }

            @Override
            public void addResourceBundles(UnresolvedAccessCondition condition, String basename, Collection<Locale> locales) {
                localizedBundles.add(basename);
            }

            @Override
            public void addCondition(AccessCondition accessCondition, Module module, String resourcePath) {
            }

            @Override
            public void addClassBasedResourceBundle(UnresolvedAccessCondition condition, String basename, String className) {
                classBasedBundles.add(basename + "=" + className);
            }
        };

        ResourceConfigurationParser<UnresolvedAccessCondition> parser = ResourceConfigurationParser.create(false, AccessConditionResolver.identityResolver(), registry,
                        EnumSet.of(ConfigurationParserOption.STRICT_CONFIGURATION));
        parser.parseAndRegister(new StringReader(json));

        Assert.assertEquals(List.of("first.module:com.example.Messages"), localizedBundles);
        Assert.assertEquals(List.of("second.module:com.example.Messages=com.example.MessagesBundle"), classBasedBundles);
        Assert.assertEquals(List.of("third.module:com.example.Messages"), allLocaleBundles);
    }

    @Test
    public void moduleQualifiedBundlesRemainDistinctInCombinedConfig() throws IOException {
        String json = """
                        {
                          "resources": [
                            {
                              "module": "first.module",
                              "bundle": "com.example.Messages"
                            },
                            {
                              "module": "second.module",
                              "bundle": "com.example.Messages"
                            }
                          ]
                        }
                        """;

        ResourceConfiguration rc = new ResourceConfiguration();
        rc.createParser(true, EnumSet.of(ConfigurationParserOption.STRICT_CONFIGURATION)).parseAndRegister(new StringReader(json));

        UnresolvedAccessCondition condition = UnresolvedAccessCondition.unconditional();
        Assert.assertTrue(rc.anyBundleMatches(condition, "first.module", "com.example.Messages"));
        Assert.assertTrue(rc.anyBundleMatches(condition, "second.module", "com.example.Messages"));
        Assert.assertFalse(rc.anyBundleMatches(condition, "com.example.Messages"));
        Assert.assertTrue(rc.supportsCombinedFile());

        String printed = printJson(rc);
        Assert.assertTrue(printed.contains("\"module\":\"first.module\""));
        Assert.assertTrue(printed.contains("\"module\":\"second.module\""));
        Assert.assertTrue(printed.contains("\"bundle\":\"com.example.Messages\""));
    }

    private static String printLegacyJson(ResourceConfiguration rc) {
        StringWriter out = new StringWriter();
        try (JsonWriter writer = new JsonWriter(out)) {
            rc.printLegacyJson(writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return out.toString();
    }

    private static String printJson(ResourceConfiguration rc) {
        StringWriter out = new StringWriter();
        try (JsonWriter writer = new JsonWriter(out)) {
            rc.printJson(writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return out.toString();
    }

    private static ResourceConfigurationParser<UnresolvedAccessCondition> createParser(ResourcesRegistry<UnresolvedAccessCondition> registry) {
        return ResourceConfigurationParser.create(false, AccessConditionResolver.identityResolver(), registry, EnumSet.of(ConfigurationParserOption.STRICT_CONFIGURATION));
    }

    private static ResourcesRegistry<UnresolvedAccessCondition> newTestRegistry(List<String> addedResources, List<String> ignoredResources) {
        return new ResourcesRegistry<>() {

            @Override
            public void addResources(UnresolvedAccessCondition condition, String pattern, Object origin) {
                addedResources.add(pattern);
            }

            @Override
            public void addGlob(UnresolvedAccessCondition condition, String module, String glob, Object origin) {
                throw new AssertionError("Unused function.");
            }

            @Override
            public void addResourceEntry(Module module, String resourcePath, Object origin) {
                throw new AssertionError("Unused function.");
            }

            @Override
            public void injectResource(Module module, String resourcePath, byte[] resourceContent, Object origin) {
            }

            @Override
            public void ignoreResources(UnresolvedAccessCondition condition, String pattern, Object origin) {
                ignoredResources.add(pattern);
            }

            @Override
            public void addResourceBundles(UnresolvedAccessCondition condition, boolean preserved, String name) {
            }

            @Override
            public void addResourceBundles(UnresolvedAccessCondition condition, String basename, Collection<Locale> locales) {
            }

            @Override
            public void addCondition(AccessCondition accessCondition, Module module, String resourcePath) {
            }

            @Override
            public void addClassBasedResourceBundle(UnresolvedAccessCondition condition, String basename, String className) {
            }
        };
    }
}
