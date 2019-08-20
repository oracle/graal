/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer.commands;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.CommandTestBase;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.model.CatalogContents;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.remote.RemotePropertiesStorage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 *
 * @author sdedic
 */
public class ListTest extends CommandTestBase {
    @Rule public TestName name = new TestName();

    private RemotePropertiesStorage remoteStorage;
    private Properties catalogContents = new Properties();

    private void initRemoteStorage() throws MalformedURLException {
        this.remoteStorage = new RemotePropertiesStorage(
                        this, getLocalRegistry(), catalogContents,
                        "linux_amd64",
                        Version.fromString("1.0.0-rc3-dev"),
                        new URL("http://go.to/graalvm"));
        this.registry = new CatalogContents(this, remoteStorage, localRegistry);
    }

    private StringBuilder outb = new StringBuilder();

    @Test
    public void testAvailablePrintAll() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("catalog.rcb1")) {
            catalogContents.load(is);
        }
        initRemoteStorage();

        AvailableCommand avC = new AvailableCommand();
        avC.init(this, this.withBundle(AvailableCommand.class));

        delegateFeedback(new FeedbackAdapter() {
            @Override
            public String l10n(String key, Object... params) {
                if ("LIST_ComponentShortList".equals(key)) {
                    return reallyl10n(key, params);
                }
                return null;
            }

            @Override
            public void output(String bundleKey, Object... params) {
                outb.append(bundleKey);
                if (params != null && params.length > 0) {
                    outb.append("{");
                    for (Object o : params) {
                        outb.append(Objects.toString(o));
                    }
                    outb.append("}");
                }
                outb.append("\n");
            }

            @Override
            public boolean verbatimOut(String msg, boolean beVerbose) {
                outb.append(msg).append("\n");
                return super.verbatimOut(msg, beVerbose);
            }
        });
        avC.execute();

        assertOutputContents(null);
    }

    private void assertOutputContents(String aMsg) throws Exception {
        String msg = aMsg != null ? aMsg : "Contents must match";
        String n = name.getMethodName();
        if (n.startsWith("test") && n.length() > 6) {
            n = Character.toLowerCase(n.charAt(4)) + n.substring(5);
        }
        URL u = getClass().getResource(n + ".golden");
        if (u == null) {
            return;
        }
        StringBuilder check = new StringBuilder();
        try (InputStream is = u.openStream();
                        BufferedReader rdr = new BufferedReader(new InputStreamReader(is))) {
            String s = null;

            while ((s = rdr.readLine()) != null) {
                if (check.length() > 0) {
                    check.append("\n");
                }
                check.append(s);
            }
        }
        assertEquals(msg, check.toString(), outb.toString());
    }

    /**
     * Tests that 'list' will print matching components.
     */
    @Test
    public void testListSpecifiedComponents() throws Exception {
        storage.installed.add(
                        new ComponentInfo("org.graalvm.R", "FastR", Version.fromString("1.0.0")));
        storage.installed.add(
                        new ComponentInfo("org.graalvm.ruby", "Ruby", Version.fromString("1.0.0")));
        storage.installed.add(
                        new ComponentInfo("org.graalvm.python", "Python", Version.fromString("1.0.0")));

        ListInstalledCommand inst = new ListInstalledCommand() {
            @Override
            boolean process() {
                super.process();
                // block the actual print
                return false;
            }
        };
        textParams.add("ruby");
        textParams.add("r");
        textParams.add("python");
        inst.init(this, this);

        inst.execute();

        Set<String> found = new HashSet<>();
        assertEquals(3, inst.getComponents().size());
        for (ComponentInfo ci : inst.getComponents()) {
            assertTrue(found.add(ci.getId().toLowerCase()));
        }
        assertTrue(found.contains("org.graalvm.r"));
        assertTrue(found.contains("org.graalvm.ruby"));
        assertTrue(found.contains("org.graalvm.python"));
    }

    /**
     * Tests that 'list' will print components but just those newer than us.
     */
    @Test
    public void testListSpecifiedNewerComponents() throws Exception {
        Version v = Version.fromString("1.1.0");
        storage.graalInfo.put(BundleConstants.GRAAL_VERSION, v.originalString());
        assert110Components(v, v);
    }

    /**
     * Checks that compatible components will be listed if 1st parameter is the version.
     */
    @Test
    public void testListSpecifiedNewerComponentsExplicit() throws Exception {
        Version v = Version.fromString("1.0.0");
        storage.graalInfo.put(BundleConstants.GRAAL_VERSION, v.originalString());
        textParams.add("+1.1.0");
        assert110Components(v, Version.fromString("1.1.0"));
    }

    private void assert110Components(Version v, Version min) throws Exception {
        Path p = dataFile("../repo/catalog.properties");
        try (InputStream is = new FileInputStream(p.toFile())) {
            catalogContents.load(is);
        }
        this.remoteStorage = new RemotePropertiesStorage(
                        this, getLocalRegistry(), catalogContents,
                        "linux_amd64",
                        v,
                        new URL("http://go.to/graalvm"));
        this.registry = new CatalogContents(this, remoteStorage, localRegistry);

        AvailableCommand inst = new AvailableCommand() {
            @Override
            boolean process() {
                super.process();
                // block the actual print
                return false;
            }
        };
        textParams.add("r");
        textParams.add("ruby");
        textParams.add("python");
        inst.init(this, this.withBundle(ListInstalledCommand.class));

        inst.execute();

        Set<String> found = new HashSet<>();
        for (ComponentInfo ci : inst.getComponents()) {
            if (ci.getId().equals(BundleConstants.GRAAL_COMPONENT_ID)) {
                continue;
            }
            assertTrue(found.add(ci.getId().toLowerCase()));
            assertTrue(min.compareTo(ci.getVersion()) <= 0);
        }
        // ruby not present
        assertFalse(found.contains("org.graalvm.ruby"));
        assertTrue(found.contains("org.graalvm.r"));
        assertTrue(found.contains("org.graalvm.python"));
    }
}
