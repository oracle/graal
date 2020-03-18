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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import org.graalvm.component.installer.ComponentArchiveReader;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.MetadataException;
import org.graalvm.component.installer.SoftwareChannel;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentStorage;
import org.graalvm.component.installer.remote.FileDownloader;

/**
 * Implements ComponentStorage over a local directory. It assumes that the directory contains JAR
 * (or simply recognized formats) Components and turns them into a Storage.
 * <p/>
 * It is then used as a part of {@link org.graalvm.component.installer.remote.MergeStorage} to
 * provide source for a potential dependency of an installed Component.
 * <p/>
 * This local storages should be added last, so that 'official' distributions win.
 * 
 * @author sdedic
 */
public class DirectoryCatalogProvider implements ComponentStorage, SoftwareChannel {
    private final Path directory;
    private final Feedback feedback;
    private boolean verifyJars = true;
    private boolean reportErrors = true;

    /**
     * Map componentID -> ComponentInfo. Lazy populated from {@link #initComponents()}.
     */
    private Map<String, Set<ComponentInfo>> dirContents = null;

    public DirectoryCatalogProvider(Path directory, Feedback feedback) {
        this.directory = directory;
        this.feedback = feedback.withBundle(DirectoryCatalogProvider.class);
    }

    public boolean isReportErrors() {
        return reportErrors;
    }

    public void setReportErrors(boolean reportErrors) {
        this.reportErrors = reportErrors;
    }

    public void setVerifyJars(boolean verifyJars) {
        this.verifyJars = verifyJars;
    }

    @Override
    public Set<String> listComponentIDs() throws IOException {
        initComponents();
        return dirContents.keySet();
    }

    @Override
    public ComponentInfo loadComponentFiles(ComponentInfo ci) throws IOException {
        return ci;
    }

    @Override
    public Set<ComponentInfo> loadComponentMetadata(String id) throws IOException {
        initComponents();
        return dirContents.get(id);
    }

    @Override
    public Map<String, String> loadGraalVersionInfo() {
        initComponents();
        throw new UnsupportedOperationException("Not supported yet."); // To change body of
                                                                       // generated methods, choose
                                                                       // Tools | Templates.
    }

    private void initComponents() {
        if (dirContents != null) {
            return;
        }

        dirContents = new HashMap<>();
        if (!Files.isDirectory(directory)) {
            return;
        }
        try {
            Files.list(directory).forEach((p -> {
                try {
                    ComponentInfo info = maybeCreateComponent(p);
                    if (info != null) {
                        dirContents.computeIfAbsent(info.getId(), (id) -> new HashSet<>()).add(info);
                    }
                } catch (MetadataException ex) {
                    if (reportErrors) {
                        feedback.error("ERR_DirectoryComponentMetadata", ex, p.toString(), ex.getLocalizedMessage());
                    }
                } catch (IOException | FailedOperationException ex) {
                    if (reportErrors) {
                        feedback.error("ERR_DirectoryComponentError", ex, p.toString(), ex.getLocalizedMessage());
                    }
                }
            }));
        } catch (IOException ex) {
            // report error, produce an empty
        }
    }

    private ComponentInfo maybeCreateComponent(Path localFile) throws IOException {
        byte[] fileStart = null;
        String serial;

        if (Files.isRegularFile(localFile)) {
            try (ReadableByteChannel ch = FileChannel.open(localFile, StandardOpenOption.READ)) {
                ByteBuffer bb = ByteBuffer.allocate(8);
                ch.read(bb);
                fileStart = bb.array();
            }
            serial = SystemUtils.fingerPrint(SystemUtils.computeFileDigest(localFile, null));
        } else {
            fileStart = new byte[]{0, 0, 0, 0, 0, 0, 0, 0};
            serial = SystemUtils.digestString(localFile.toString(), false);
        }
        MetadataLoader ldr = null;
        try {
            for (ComponentArchiveReader provider : ServiceLoader.load(ComponentArchiveReader.class)) {
                ldr = provider.createLoader(localFile, fileStart, serial, feedback, verifyJars);
                if (ldr != null) {
                    ComponentInfo info = ldr.getComponentInfo();
                    info.setRemoteURL(localFile.toUri().toURL());
                    info.setOrigin(feedback.l10n("DIR_LocalFile"));
                    return info;
                }
            }
        } finally {
            // ignore, may be not a component...
            if (ldr != null) {
                ldr.close();
            }
        }
        return null;
    }

    @Override
    public ComponentStorage getStorage() throws IOException {
        return this;
    }

    @Override
    public FileDownloader configureDownloader(ComponentInfo info, FileDownloader dn) {
        return dn;
    }
}
