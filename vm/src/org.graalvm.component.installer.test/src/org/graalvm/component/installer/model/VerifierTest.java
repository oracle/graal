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
package org.graalvm.component.installer.model;

import java.util.jar.JarFile;
import static org.graalvm.component.installer.CommonConstants.CAP_OS_NAME;
import org.graalvm.component.installer.DependencyException;
import org.graalvm.component.installer.TestBase;
import org.graalvm.component.installer.commands.MockStorage;
import org.graalvm.component.installer.jar.JarMetaLoader;
import org.graalvm.component.installer.persist.ComponentPackageLoader;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 *
 * @author sdedic
 */
public class VerifierTest extends TestBase {
    private MockStorage mockStorage = new MockStorage();
    private ComponentRegistry registry;
    private ComponentInfo rubyInfo;

    @Rule public ExpectedException exception = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        registry = new ComponentRegistry(this, mockStorage);
    }

    @Test
    public void testGraalCapabilitiesCaseInsensitive() throws Exception {
        try (JarFile jf = new JarFile(dataFile("truffleruby2.jar").toFile())) {
            ComponentPackageLoader ldr = new JarMetaLoader(jf, this);

            rubyInfo = ldr.createComponentInfo();
            ldr.loadPaths();
            ldr.loadSymlinks();
        }
        mockStorage.graalInfo.put(CAP_OS_NAME, "LiNuX");

        Verifier vfy = new Verifier(this, registry, registry);
        vfy.validateRequirements(rubyInfo);
    }

    @Test
    public void testGraalCapabilitiesMismatch() throws Exception {
        try (JarFile jf = new JarFile(dataFile("truffleruby2.jar").toFile())) {
            ComponentPackageLoader ldr = new JarMetaLoader(jf, this);

            rubyInfo = ldr.createComponentInfo();
            ldr.loadPaths();
            ldr.loadSymlinks();
        }
        mockStorage.graalInfo.put(CAP_OS_NAME, "LiNuy");

        Verifier vfy = new Verifier(this, registry, registry);
        exception.expect(DependencyException.Mismatch.class);
        exception.expectMessage("VERIFY_Dependency_Failed");
        vfy.validateRequirements(rubyInfo);
    }
}
