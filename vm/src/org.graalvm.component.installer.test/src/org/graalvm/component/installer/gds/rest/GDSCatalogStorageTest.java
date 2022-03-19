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

import org.graalvm.component.installer.CommandTestBase;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.model.ComponentInfo;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author odouda
 */
public class GDSCatalogStorageTest extends CommandTestBase {
    static final String MOCK_URL = "https://mock.url/";
    static final String ID1 = "id1";
    static final String ID2 = "id2";
    URL mockUrl;

    List<ComponentInfo> infos = getComps();

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mockUrl = new URL(MOCK_URL);
    }

    @Test
    public void test() throws IOException {
        GDSCatalogStorage cs = getGDSCatalogStorage();
        HashMap<String, Set<ComponentInfo>> map = getMapComps();
        assertEquals(map, cs.getComponents());
        assertEquals(map.get(ID1), cs.loadComponentMetadata(ID1));
        assertEquals(map.get(ID2), cs.loadComponentMetadata(ID2));
        assertEquals(map.keySet(), cs.listComponentIDs());
    }

    private GDSCatalogStorage getGDSCatalogStorage() {
        return new GDSCatalogStorage(localRegistry, this, mockUrl, infos);
    }

    List<ComponentInfo> getComps() {
        return List.of(new ComponentInfo(ID1, "name11", Version.NO_VERSION),
                        new ComponentInfo(ID1, "name12", Version.NO_VERSION),
                        new ComponentInfo(ID2, "name21", Version.NO_VERSION),
                        new ComponentInfo(ID2, "name22", Version.NO_VERSION));
    }

    HashMap<String, Set<ComponentInfo>> getMapComps() {
        HashMap<String, Set<ComponentInfo>> map = new HashMap<>();
        for (ComponentInfo c : infos) {
            map.computeIfAbsent(c.getId(), id -> new HashSet<>()).add(c);
        }
        return map;
    }
}
