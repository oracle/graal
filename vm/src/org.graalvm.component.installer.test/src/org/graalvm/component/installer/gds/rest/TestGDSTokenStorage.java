/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.component.installer.gds.rest;

import org.graalvm.component.installer.CommandInput;
import static org.graalvm.component.installer.CommonConstants.PATH_GDS_CONFIG;
import static org.graalvm.component.installer.CommonConstants.PATH_USER_GU;
import static org.graalvm.component.installer.CommonConstants.SYSPROP_USER_HOME;
import org.graalvm.component.installer.Feedback;
import java.nio.file.Path;
import org.graalvm.component.installer.Version;

/**
 *
 * @author odouda
 */
class TestGDSTokenStorage extends GDSTokenStorage {
    static Path testPath = Path.of(System.getProperty(SYSPROP_USER_HOME), PATH_USER_GU, "test" + PATH_GDS_CONFIG);
    private Feedback fb;

    TestGDSTokenStorage(Feedback feedback, CommandInput input) {
        super(feedback, input);
        fb = feedback.withBundle(GDSChannel.class);
    }

    @Override
    public Path getPropertiesPath() {
        return testPath;
    }

    public boolean makeConn = true;

    @Override
    protected GDSRESTConnector makeConnector() {
        fb.verbatimOut("GDSTokenStorage.makeConnector", false);
        return makeConn ? new GDSRESTConnector("http://mock.url", fb, PATH_USER_GU, Version.fromString("1.0.0")) {
            @Override
            public void revokeToken(String token) {
                fb.verbatimOut("GDSRESTConnector.revokeToken:" + token, false);
            }

            @Override
            public void revokeTokens(String mail) {
                fb.verbatimOut("GDSRESTConnector.revokeTokens:" + mail, false);
            }
        } : null;
    }
}
