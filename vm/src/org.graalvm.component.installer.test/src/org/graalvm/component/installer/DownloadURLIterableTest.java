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
package org.graalvm.component.installer;

import java.util.Iterator;
import org.graalvm.component.installer.jar.JarArchive;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.persist.MetadataLoader;
import org.graalvm.component.installer.persist.ProxyResource;
import org.graalvm.component.installer.persist.test.Handler;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;

public class DownloadURLIterableTest extends CommandTestBase {
    @Rule public final ProxyResource proxyResource = new ProxyResource();

    @Test
    public void testConstructComponentParam() throws Exception {
        initURLComponent("persist/data/truffleruby2.jar", "test://graalvm.io/download/truffleruby.zip");
        assertEquals("test://graalvm.io/download/truffleruby.zip", param.getSpecification());
        assertEquals("test://graalvm.io/download/truffleruby.zip", param.getDisplayName());
        assertFalse(param.isComplete());
        assertFalse(Handler.isVisited(url));
    }

    @Test
    public void testURLParameter() throws Exception {
        initURLComponent("persist/data/truffleruby3.jar", "test://graalvm.io/download/truffleruby.zip");
        this.textParams.add("test://graalvm.io/download/truffleruby.zip");

        DownloadURLIterable iterable = new DownloadURLIterable(this, this);
        Iterator<ComponentParam> it = iterable.iterator();
        assertTrue(it.hasNext());

        ComponentParam p = it.next();

        assertEquals("test://graalvm.io/download/truffleruby.zip", p.getSpecification());
        MetadataLoader ldr = p.createMetaLoader();
        assertFalse(p.isComplete());

        ComponentInfo ci = ldr.getComponentInfo();
        assertTrue(p.isComplete());

        assertEquals("ruby", ci.getId());
        assertEquals("0.33-dev", ci.getVersionString());

        JarArchive jf = (JarArchive) ldr.getArchive();
        Archive.FileEntry je = jf.getJarEntry("META-INF/MANIFEST.MF");
        assertNotNull(je);
        jf.close();
    }

    @Test
    public void testMalformedURL() throws Exception {
        this.textParams.add("testx://graalvm.io/download/truffleruby.zip");

        DownloadURLIterable iterable = new DownloadURLIterable(this, this);
        Iterator<ComponentParam> it = iterable.iterator();
        assertTrue(it.hasNext());

        exception.expect(FailedOperationException.class);
        exception.expectMessage("URL_InvalidDownloadURL");

        it.next();
    }

    @Test
    public void testInstallFromURL() throws Exception {
        initURLComponent("persist/data/truffleruby2.jar", "test://graalvm.io/download/truffleruby.zip");

        components.add(rparam);

    }
}
