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
package org.graalvm.component.installer.persist;

import org.graalvm.component.installer.persist.test.Handler;
import org.junit.rules.ExternalResource;

public class ProxyResource extends ExternalResource {

    @Override
    protected void after() {
        Handler.clear();

        if (proxyHost != null) {
            System.setProperty("http.proxyHost", proxyHost);
        } else {
            System.clearProperty("http.proxyHost");
        }
        if (proxyPort != null) {
            System.setProperty("http.proxyPort", proxyPort);
        } else {
            System.clearProperty("http.proxyPort");
        }
        super.after(); // To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected void before() throws Throwable {
        super.before();
        final String key = "java.protocol.handler.pkgs";

        String newValue = HANDLER_PACKAGE;
        if (System.getProperty(key) != null) {
            final String previousValue = System.getProperty(key);
            if (!previousValue.contains(newValue)) {
                newValue += "|" + previousValue;
            }
        }
        System.setProperty(key, newValue);

        proxyHost = System.getProperty("http.proxyHost");
        proxyPort = System.getProperty("http.proxyPort");
    }

    private static final String HANDLER_PACKAGE = "org.graalvm.component.installer.persist";

    private String proxyHost;
    private String proxyPort;
}
