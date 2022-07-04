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

import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.ComponentCatalog;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.IncompatibleException;
import org.graalvm.component.installer.SoftwareChannel;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.model.ComponentStorage;
import org.graalvm.component.installer.remote.FileDownloader;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Abstracted common base for GDSChannel and GraalChannel.
 *
 * @author odouda
 */
public abstract class GraalChannelBase implements SoftwareChannel, ComponentCatalog.DownloadInterceptor {
    protected final ComponentRegistry localRegistry;
    protected final CommandInput input;
    protected final Feedback fb;

    /**
     * URL of the relases resource. Used for relative URL resolution.
     */
    private URL indexURL;

    /**
     * Delay-init storage. Make lazy, as the storage calls back to the toplevel registry. Prevents
     * stack overflow.
     */
    private final Delayed delayedStorage = new Delayed();

    /**
     * Cached initialized storage.
     */
    protected ComponentStorage storage;

    protected String edition;

    /**
     * If true, future versions are accepted as well.
     */
    private boolean allowUpdates = false;

    public GraalChannelBase(CommandInput aInput, Feedback aFeedback, ComponentRegistry aRegistry) {
        if (aInput == null) {
            throw new IllegalArgumentException("CommandInput cannot be null.");
        }
        this.input = aInput;
        if (aFeedback == null) {
            throw new IllegalArgumentException("Feedback cannot be null.");
        }
        this.fb = aFeedback;
        if (aRegistry == null) {
            throw new IllegalArgumentException("ComponentRegistry cannot be null.");
        }
        this.localRegistry = aRegistry;
    }

    public String getEdition() {
        return edition;
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    public boolean isAllowUpdates() {
        return allowUpdates;
    }

    public void setAllowUpdates(boolean allowUpdates) {
        this.allowUpdates = allowUpdates;
    }

    public URL getIndexURL() {
        return indexURL;
    }

    public final void setIndexURL(URL releasesIndexURL) {
        this.indexURL = releasesIndexURL;
    }

    @Override
    public ComponentStorage getStorage() throws IOException {
        return delayedStorage;
    }

    /**
     * Delay-init storage. As listing (downloading) the catalogs require access to the toplevel
     * registry, the toplevel needs to finish the initialization first.
     */
    class Delayed implements ComponentStorage {
        private ComponentStorage init() throws IOException {
            if (storage == null) {
                allowUpdates = input.getRegistry().isAllowDistUpdate();
                storage = loadStorage();
            }
            return storage;
        }

        @Override
        public Set<String> listComponentIDs() throws IOException {
            return init().listComponentIDs();
        }

        @Override
        public ComponentInfo loadComponentFiles(ComponentInfo ci) throws IOException {
            return init().loadComponentFiles(ci);
        }

        @Override
        public Set<ComponentInfo> loadComponentMetadata(String id) throws IOException {
            return init().loadComponentMetadata(id);
        }

        @Override
        public Map<String, String> loadGraalVersionInfo() {
            return Collections.emptyMap();
        }
    }

    static final class NullStorage implements ComponentStorage {
        @Override
        public Set<String> listComponentIDs() throws IOException {
            return Collections.emptySet();
        }

        @Override
        public ComponentInfo loadComponentFiles(ComponentInfo ci) throws IOException {
            return null;
        }

        @Override
        public Set<ComponentInfo> loadComponentMetadata(String id) throws IOException {
            return Collections.emptySet();
        }

        @Override
        public Map<String, String> loadGraalVersionInfo() {
            return Collections.emptyMap();
        }
    }

    /**
     * On first invocation, throws an exception (will be caught by catalog builder). On subsequent
     * invocations, returns a dummy no-component storage.
     *
     * @return empty storage
     */
    protected ComponentStorage throwEmptyStorage() {
        if (storage != null) {
            return storage;
        }
        Map<String, String> caps = localRegistry.getGraalCapabilities();
        String os = caps.get(CommonConstants.CAP_OS_NAME);
        String arch = caps.get(CommonConstants.CAP_OS_ARCH);
        storage = new NullStorage();
        throw new IncompatibleException(fb.l10n("OLDS_IncompatibleRelease",
                        SystemUtils.normalizeOSName(os, arch),
                        SystemUtils.normalizeArchitecture(os, arch),
                        localRegistry.getJavaVersion()));
    }

    /**
     * Filters mismatched releases. At this point, accepts only the exactly same release version.
     * Could be changed to accept future versions as well, allowing for upgrades.
     *
     * @param graalVersion current version
     * @param vers the version from the release entry
     * @return true, if the release matches the current installation
     */
    protected boolean acceptsVersion(Version graalVersion, Version vers) {
        int c = graalVersion.installVersion().compareTo(vers.installVersion());
        return isAllowUpdates() ? c <= 0 : c == 0;
    }

    /**
     * Initializes the component storage. Loads the releases index, selects matching releases and
     * creates {@link org.graalvm.component.installer.ce.WebCatalog} for each of the catalogs.
     * Merges using {@link org.graalvm.component.installer.remote.MergeStorage}.
     *
     * @return merged storage.
     * @throws IOException in case of an I/O error.
     */
    protected abstract ComponentStorage loadStorage() throws IOException;

    @Override
    public FileDownloader processDownloader(ComponentInfo info, FileDownloader dn) {
        return configureDownloader(info, dn);
    }
}
