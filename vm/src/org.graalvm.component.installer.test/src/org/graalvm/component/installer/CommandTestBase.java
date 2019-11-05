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
import org.graalvm.component.installer.DownloadURLIterable.DownloadURLParam;
import org.graalvm.component.installer.commands.MockStorage;
import org.graalvm.component.installer.jar.JarMetaLoader;
import org.graalvm.component.installer.model.CatalogContents;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.model.ComponentStorage;
import org.graalvm.component.installer.os.DefaultFileOperations;
import org.graalvm.component.installer.os.WindowsFileOperations;
import org.graalvm.component.installer.persist.ComponentPackageLoader;
import org.graalvm.component.installer.remote.FileDownloader;
import org.graalvm.component.installer.persist.test.Handler;
import org.graalvm.component.installer.remote.RemoteComponentParam;
import org.graalvm.component.installer.remote.CatalogIterable.CatalogItemParam;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class CommandTestBase extends TestBase implements CommandInput, SoftwareChannel, ComponentCatalog.DownloadInterceptor {
    @Rule public ExpectedException exception = ExpectedException.none();
    protected JarFile componentJarFile;
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    protected Path targetPath;

    protected FileOperations fileOps;
    protected MockStorage storage;
    protected MockStorage catalogStorage;
    protected ComponentCatalog registry;
    protected ComponentRegistry localRegistry;

    protected List<File> files = new ArrayList<>();
    protected List<ComponentParam> components = new ArrayList<>();
    protected List<String> textParams = new ArrayList<>();
    protected Map<String, String> options = new HashMap<>();

    protected Map<String, String> propParameters = new HashMap<>();
    protected Map<String, String> envParameters = new HashMap<>();

    ComponentParam param;
    RemoteComponentParam rparam;
    URL url;
    URL clu;
    ComponentInfo info;

    public CommandTestBase() {
        fileOps = SystemUtils.isWindows() ? new WindowsFileOperations() : new DefaultFileOperations();
        fileOps.init(this);
    }

    protected void initRemoteComponent(String relativeJar, String u, String disp, String spec) throws IOException {
        clu = getClass().getResource(relativeJar);
        url = new URL(u);
        Handler.bind(url.toString(), clu);

        File f = dataFile(relativeJar).toFile();
        JarFile jf = new JarFile(f, false);
        ComponentPackageLoader cpl = new JarMetaLoader(jf, this);
        info = cpl.getComponentInfo();
        // unknown in catalog metadata
        info.setLicensePath(null);
        info.setRemoteURL(url);
        param = rparam = new CatalogItemParam(this, info, disp, spec, this, false);
    }

    protected void initURLComponent(String relativeJar, String spec) throws IOException {
        clu = getClass().getResource(relativeJar);
        url = new URL(spec);
        Handler.bind(url.toString(), clu);

        File f = dataFile(relativeJar).toFile();
        JarFile jf = new JarFile(f, false);
        ComponentPackageLoader cpl = new JarMetaLoader(jf, this);
        info = cpl.getComponentInfo();
        // unknown in catalog metadata
        info.setLicensePath(null);
        info.setRemoteURL(url);
        param = rparam = new DownloadURLParam(url, spec, spec, this, false);
    }

    protected ComponentIterable paramIterable;

    boolean verifyJars;

    @Override
    public FileOperations getFileOperations() {
        return fileOps;
    }

    protected class CompIterableImpl implements ComponentIterable {
        @Override
        public void setVerifyJars(boolean verify) {
            verifyJars = verify;
        }

        @Override
        public ComponentParam createParam(String cmdString, ComponentInfo nfo) {
            return null;
        }

        @Override
        public Iterator<ComponentParam> iterator() {
            return new Iterator<ComponentParam>() {
                private Iterator<ComponentParam> pit = components.iterator();
                private Iterator<ComponentParam> fit;
                {
                    FileIterable ff = new FileIterable(CommandTestBase.this, CommandTestBase.this);
                    ff.setVerifyJars(verifyJars);
                    fit = ff.iterator();
                }

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

        @Override
        public ComponentIterable matchVersion(Version.Match m) {
            return this;
        }

        @Override
        public ComponentIterable allowIncompatible() {
            return this;
        }
    }

    protected ComponentIterable iterableInstance;

    protected ComponentIterable createComponentIterable() {
        if (iterableInstance != null) {
            return iterableInstance;
        }
        return new CompIterableImpl();
    }

    @Override
    public ComponentIterable existingFiles() throws FailedOperationException {
        if (paramIterable != null) {
            return paramIterable;
        }
        return createComponentIterable();
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
    public String peekParameter() {
        if (!textParams.isEmpty()) {
            return textParams.get(0);
        }
        return files.isEmpty() ? null : files.get(0).toString();
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
    public ComponentCatalog getRegistry() {
        if (registry == null) {
            registry = getCatalogFactory().createComponentCatalog(this, getLocalRegistry());
        }
        return registry;
    }

    @Override
    public ComponentRegistry getLocalRegistry() {
        if (localRegistry == null) {
            localRegistry = new ComponentRegistry(this, storage);
        }
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
        catalogStorage = new MockStorage();
        fileOps.setRootPath(targetPath);
    }

    @Override
    public FileDownloader processDownloader(ComponentInfo ci, FileDownloader dn) {
        return dn;
    }

    @Override
    public FileDownloader configureDownloader(ComponentInfo ci, FileDownloader dn) {
        return dn;
    }

    @Override
    public ComponentStorage getStorage() {
        return catalogStorage;
    }

    @Override
    public CatalogFactory getCatalogFactory() {
        if (registry != null) {
            return (a, b) -> registry;
        } else {
            return (a, b) -> new CatalogContents(this, catalogStorage, getLocalRegistry());
        }
    }

    @Override
    public String getParameter(String key, boolean cmdLine) {
        return (cmdLine ? propParameters : envParameters).get(key);
    }

    @Override
    public Map<String, String> parameters(boolean cmdLine) {
        return (cmdLine ? propParameters : envParameters);
    }

}
