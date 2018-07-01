/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;
import java.util.Properties;
import java.util.ResourceBundle;
import org.graalvm.component.installer.CommandTestBase;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.persist.RemoteStorage;
import static org.junit.Assert.assertEquals;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 *
 * @author sdedic
 */
public class ListTest extends CommandTestBase {
    @Rule public TestName name = new TestName();

    private RemoteStorage remoteStorage;
    private Properties catalogContents = new Properties();
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("org.graalvm.component.installer.commands.Bundle");

    private void initRemoteStorage() throws MalformedURLException {
        this.remoteStorage = new RemoteStorage(
                        this, localRegistry, catalogContents, "1.0.0-rc3-dev_linux_amd64", new URL("http://go.to/graalvm"));
        this.registry = new ComponentRegistry(this, remoteStorage);
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
                    return reallyl10n(BUNDLE, key, params);
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
}
