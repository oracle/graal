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
package org.graalvm.component.installer.gds;

import org.graalvm.component.installer.CommandTestBase;
import org.graalvm.component.installer.IncompatibleException;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentStorage;
import org.graalvm.component.installer.persist.ProxyResource;
import org.graalvm.component.installer.remote.FileDownloader;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import org.junit.Rule;
import org.junit.Test;
import java.io.IOException;

/**
 *
 * @author sdedic
 */
public class GraalChannelBaseTest extends CommandTestBase {
    @Rule public ProxyResource proxyResource = new ProxyResource();

    GraalChannelBase channel;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        channel = new GraalChannelBase(this, this.withBundle(GraalChannel.class), this.getLocalRegistry()) {
            @Override
            public FileDownloader configureDownloader(ComponentInfo info, FileDownloader dn) {
                return dn;
            }

            @Override
            protected ComponentStorage loadStorage() throws IOException {
                return null;
            }
        };
// storage.graalInfo.put(CommonConstants.CAP_EDITION, "ee");
//
// storage.graalInfo.put(CommonConstants.CAP_OS_NAME, "linux");
// storage.graalInfo.put(BundleConstants.GRAAL_VERSION, "22.1.0");
// storage.graalInfo.put(CAP_JAVA_VERSION, "11");
    }

    @Test
    public void testThrowEmptyStorage() throws Exception {
        try {
            channel.throwEmptyStorage();
            fail("Exception expected");
        } catch (IncompatibleException ex) {
            // ok
        }
        ComponentStorage chStorage = channel.throwEmptyStorage();
        assertNotNull("Stub storage expected for 2nd time", chStorage);

        assertEquals(0, chStorage.listComponentIDs().size());
        assertEquals(0, chStorage.loadComponentMetadata("org.graalvm").size());
        assertEquals(0, chStorage.loadGraalVersionInfo().size());
    }
}
