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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.graalvm.component.installer.Archive;
import static org.graalvm.component.installer.BundleConstants.META_INF_PATH;
import static org.graalvm.component.installer.BundleConstants.META_INF_PERMISSIONS_PATH;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.model.ComponentInfo;
import org.junit.Assert;

/**
 *
 * @author sdedic
 */
public final class DirectoryMetaLoader extends ComponentPackageLoader {
    private final Path rootDir;
    private Manifest mani;

    private DirectoryMetaLoader(Path rootDir, Function<String, String> supplier, Feedback feedback) {
        super(supplier, feedback);
        this.rootDir = rootDir;
    }

    private Manifest getManifest() {
        if (mani != null) {
            return mani;
        }
        try (InputStream istm = Files.newInputStream(rootDir.resolve(Paths.get("META-INF", "MANIFEST.MF")))) {
            mani = new Manifest(istm);
        } catch (IOException ex) {
            Assert.fail("Failure reading manifest");
        }
        return mani;
    }

    public static DirectoryMetaLoader create(Path rotoDir, Feedback feedback) {
        ManifestValues vv = new ManifestValues();
        DirectoryMetaLoader ldr = new DirectoryMetaLoader(rotoDir, vv, feedback);
        vv.loader = ldr;
        return ldr;
    }

    private static class ManifestValues implements Function<String, String> {
        private DirectoryMetaLoader loader;

        ManifestValues() {
        }

        @Override
        public String apply(String t) {
            return loader.getManifest().getMainAttributes().getValue(t);
        }
    }

    @Override
    public void loadPaths() {
        ComponentInfo cinfo = getComponentInfo();
        Set<String> emptyDirectories = new HashSet<>();
        List<String> files = new ArrayList<>();

        try {
            Files.walk(rootDir).forEachOrdered((Path en) -> {
                String eName = SystemUtils.toCommonPath(en);
                if (eName.startsWith(META_INF_PATH)) {
                    return;
                }
                int li = eName.lastIndexOf("/", Files.isDirectory(en) ? eName.length() - 2 : eName.length() - 1);
                if (li > 0) {
                    emptyDirectories.remove(eName.substring(0, li + 1));
                }
                if (Files.isDirectory(en)) {
                    // directory names always come first
                    emptyDirectories.add(eName);
                } else {
                    files.add(eName);
                }
            });
            addFiles(new ArrayList<>(emptyDirectories));
            // sort empty directories first
            Collections.sort(files);
            cinfo.addPaths(files);
            addFiles(files);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public Map<String, String> loadSymlinks() throws IOException {
        return super.loadSymlinks(); // To change body of generated methods, choose Tools |
                                     // Templates.
    }

    @Override
    public Map<String, String> loadPermissions() throws IOException {
        Path permissionsPath = rootDir.resolve(SystemUtils.fromCommonRelative(META_INF_PERMISSIONS_PATH));
        if (!Files.exists(permissionsPath)) {
            return Collections.emptyMap();
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                        Files.newInputStream(permissionsPath), "UTF-8"))) {
            Map<String, String> permissions = parsePermissions(r);
            return permissions;
        }
    }

    @Override
    public String getLicenseID() {
        return super.getLicenseID(); // To change body of generated methods, choose Tools |
                                     // Templates.
    }

    public class FE implements Archive.FileEntry {
        private final Path path;

        public FE(Path p) {
            this.path = p;
        }

        @Override
        public String getName() {
            return SystemUtils.toCommonPath(rootDir.relativize(path));
        }

        @Override
        public boolean isDirectory() {
            return Files.isDirectory(path);
        }

        @Override
        public boolean isSymbolicLink() {
            return Files.isSymbolicLink(path);
        }

        @Override
        public String getLinkTarget() throws IOException {
            return SystemUtils.toCommonPath(Files.readSymbolicLink(path));
        }

        @Override
        public long getSize() {
            try {
                return Files.size(path);
            } catch (IOException ex) {
                return -1;
            }
        }

    }

    public class DirArchive implements Archive {
        public DirArchive() {
        }

        @Override
        public InputStream getInputStream(FileEntry e) throws IOException {
            return Files.newInputStream(((FE) e).path);
        }

        @Override
        public boolean checkContentsMatches(ReadableByteChannel bc, FileEntry entry) throws IOException {
            return true;
        }

        @Override
        public boolean verifyIntegrity(CommandInput input) throws IOException {
            return true;
        }

        @Override
        public void completeMetadata(ComponentInfo info) throws IOException {
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public Iterator<FileEntry> iterator() {
            Stream<Path> stream;
            try {
                stream = Files.walk(rootDir).sequential();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            return stream.filter((t) -> !rootDir.resolve(t).equals(rootDir)).map((p) -> (Archive.FileEntry) new FE(p)).collect(Collectors.toList()).iterator();
        }

    }

    @Override
    public Archive getArchive() {
        return new DirArchive();
    }
}
