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
package org.graalvm.component.installer;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import org.graalvm.component.installer.commands.MockStorage;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.persist.ComponentPackageLoader;
import org.graalvm.component.installer.persist.test.Handler;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class CommandTestBase extends TestBase implements CommandInput {
    @Rule public ExpectedException exception = ExpectedException.none();
    protected JarFile componentJarFile;
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    protected Path targetPath;

    protected MockStorage storage;
    protected ComponentRegistry registry;
    protected ComponentRegistry localRegistry;

    protected List<File> files = new ArrayList<>();
    protected List<ComponentParam> components = new ArrayList<>();
    protected List<String> textParams = new ArrayList<>();
    protected Map<String, String> options = new HashMap<>();

    ComponentParam param;
    CatalogIterable.RemoteComponentParam rparam;
    URL url;
    URL clu;
    ComponentInfo info;

    public CommandTestBase() {
    }

    protected void initRemoteComponent(String relativeJar, String u, String disp, String spec) throws IOException {
        clu = getClass().getResource(relativeJar);
        url = new URL(u);
        Handler.bind(url.toString(), clu);

        File f = dataFile(relativeJar).toFile();
        JarFile jf = new JarFile(f, false);
        ComponentPackageLoader cpl = new ComponentPackageLoader(jf, this);
        info = cpl.getComponentInfo();
        // unknown in catalog metadata
        info.setLicensePath(null);
        info.setRemoteURL(url);
        param = rparam = new CatalogIterable.RemoteComponentParam(info, disp, spec, this, false);
    }

    protected void initURLComponent(String relativeJar, String spec) throws IOException {
        clu = getClass().getResource(relativeJar);
        url = new URL(spec);
        Handler.bind(url.toString(), clu);

        File f = dataFile(relativeJar).toFile();
        JarFile jf = new JarFile(f, false);
        ComponentPackageLoader cpl = new ComponentPackageLoader(jf, this);
        info = cpl.getComponentInfo();
        // unknown in catalog metadata
        info.setLicensePath(null);
        info.setRemoteURL(url);
        param = rparam = new CatalogIterable.RemoteComponentParam(url, spec, spec, this, false);
    }

    protected Iterable<ComponentParam> paramIterable;

    @Override
    public Iterable<ComponentParam> existingFiles() throws FailedOperationException {
        if (paramIterable != null) {
            return paramIterable;
        }
        return new Iterable<ComponentParam>() {
            @Override
            public Iterator<ComponentParam> iterator() {
                return new Iterator<ComponentParam>() {
                    private Iterator<ComponentParam> fit = new FileIterable(CommandTestBase.this, CommandTestBase.this).iterator();
                    private Iterator<ComponentParam> pit = components.iterator();

                    @Override
                    public boolean hasNext() {
                        return fit.hasNext() || pit.hasNext();
                    }

                    @Override
                    public ComponentParam next() {
                        ComponentParam p = null;

                        if (pit.hasNext()) {
                            p = pit.next();
                            // discard one parameter from files:
                            files.remove(0);
                            if (p != null) {
                                return p;
                            }
                        }
                        return fit.next();
                    }

                };
            }

        };
    }

    @Override
    public String requiredParameter() throws FailedOperationException {
        if (!textParams.isEmpty()) {
            return textParams.remove(0);
        }
        return files.remove(0).toString();
    }

    @Override
    public String nextParameter() {
        if (hasParameter()) {
            return requiredParameter();
        }
        return null;
    }

    @Override
    public boolean hasParameter() {
        return (!textParams.isEmpty() || !files.isEmpty());
    }

    @Override
    public Path getGraalHomePath() {
        return targetPath;
    }

    @Override
    public ComponentRegistry getRegistry() {
        return registry;
    }

    @Override
    public ComponentRegistry getLocalRegistry() {
        return localRegistry;
    }

    @Override
    public String optValue(String option) {
        return options.get(option);
    }

    @Before
    public void setUp() throws Exception {
        targetPath = folder.newFolder("inst").toPath();
        storage = new MockStorage();
        localRegistry = registry = new ComponentRegistry(this, storage);
    }
}
