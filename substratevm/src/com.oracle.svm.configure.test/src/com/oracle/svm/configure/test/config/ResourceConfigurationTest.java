/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.configure.config.ResourceConfiguration;
import com.oracle.svm.core.configure.ResourceConfigurationParser;
import com.oracle.svm.core.configure.ResourcesRegistry;
import com.oracle.svm.core.util.json.JsonWriter;

public class ResourceConfigurationTest {

    @Test
    public void anyResourceMatches() {
        ResourceConfiguration rc = new ResourceConfiguration();
        ConfigurationCondition defaultCond = ConfigurationCondition.alwaysTrue();
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
        ConfigurationCondition defaultCond = ConfigurationCondition.alwaysTrue();
        rc.addResourcePattern(defaultCond, ".*/Resource.*txt$");
        rc.ignoreResourcePattern(defaultCond, ".*/Resource2.txt$");
        PipedWriter pw = new PipedWriter();
        JsonWriter jw = new JsonWriter(pw);

        try (PipedReader pr = new PipedReader()) {
            pr.connect(pw);

            Thread writerThread = new Thread(() -> {
                try (JsonWriter w = jw) {
                    rc.printJson(w);
                } catch (IOException e) {
                    Assert.fail(e.getMessage());
                }
            });

            List<String> addedResources = new LinkedList<>();
            List<String> ignoredResources = new LinkedList<>();

            ResourcesRegistry registry = new ResourcesRegistry() {

                @Override
                public void addResources(ConfigurationCondition condition, String pattern) {
                    addedResources.add(pattern);
                }

                @Override
                public void injectResource(Module module, String resourcePath, byte[] resourceContent) {
                }

                @Override
                public void ignoreResources(ConfigurationCondition condition, String pattern) {
                    ignoredResources.add(pattern);
                }

                @Override
                public void addResourceBundles(ConfigurationCondition condition, String name) {
                }

                @Override
                public void addResourceBundles(ConfigurationCondition condition, String basename, Collection<Locale> locales) {

                }

                @Override
                public void addClassBasedResourceBundle(ConfigurationCondition condition, String basename, String className) {

                }
            };

            ResourceConfigurationParser rcp = ResourceConfigurationParser.create(false, registry, true);
            writerThread.start();
            rcp.parseAndRegister(pr);

            writerThread.join();

            Assert.assertTrue(addedResources.contains(".*/Resource.*txt$"));
            Assert.assertTrue(ignoredResources.contains(".*/Resource2.txt$"));
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }
}
