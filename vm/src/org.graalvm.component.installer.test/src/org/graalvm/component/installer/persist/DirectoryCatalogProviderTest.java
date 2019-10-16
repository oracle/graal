/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.graalvm.component.installer.TestBase;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author sdedic
 */
public class DirectoryCatalogProviderTest extends TestBase {
    @Test
    public void testLoadFromEmptyDirectory() throws Exception {
        Path nf = testFolder.newFolder().toPath();
        DirectoryCatalogProvider prov = new DirectoryCatalogProvider(nf, this);

        assertTrue(prov.listComponentIDs().isEmpty());
    }

    @Test
    public void testLoadFromNonDirectory() throws Exception {
        Path nf = testFolder.newFile().toPath();
        DirectoryCatalogProvider prov = new DirectoryCatalogProvider(nf, this);

        assertTrue(prov.listComponentIDs().isEmpty());
    }

    public void testLoadComponentsSimple() throws Exception {

    }

    public void testLoadComponentsJars() throws Exception {
        Path ruby033 = dataFile("data/truffleruby2.jar");
        Path ruby10 = dataFile("../remote/data/truffleruby2.jar");
        Path llvm = dataFile("data/llvm-toolchain.jar");

        Path nf = testFolder.newFolder().toPath();
        Files.copy(ruby033, nf.resolve(ruby033.getFileName()));
        Files.copy(ruby10, nf.resolve(ruby10.getFileName()));
        Files.copy(llvm, nf.resolve(llvm.getFileName()));

        DirectoryCatalogProvider prov = new DirectoryCatalogProvider(nf, this);
        Set<String> ids = prov.listComponentIDs();

        assertEquals(2, ids.size());
        assertTrue(ids.contains("org.graalvm.ruby"));
        assertTrue(ids.contains("org.graalvm.llvm-toolchain"));
    }
}
