/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.graalvm.component.installer.jar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.graalvm.component.installer.Archive;
import static org.graalvm.component.installer.BundleConstants.META_INF_PATH;
import static org.graalvm.component.installer.BundleConstants.META_INF_PERMISSIONS_PATH;
import static org.graalvm.component.installer.BundleConstants.META_INF_SYMLINKS_PATH;
import static org.graalvm.component.installer.BundleConstants.PATH_LICENSE;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.persist.ComponentPackageLoader;

/**
 *
 * @author sdedic
 */
public class JarMetaLoader extends ComponentPackageLoader {
    private final JarFile jarFile;
    private final Feedback fb;
    public JarMetaLoader(JarFile jarFile, Feedback feedback) throws IOException {
        super(new ManifestValues(jarFile), feedback);
        this.jarFile = jarFile;
        this.fb = feedback.withBundle(JarMetaLoader.class);
    }
    
    private static class ManifestValues implements Function<String, String> {
        private final JarFile jf;
        private final Manifest mf;

        public ManifestValues(JarFile jf) throws IOException {
            this.jf = jf;
            this.mf = jf.getManifest();
        }
        
        
        
        @Override
        public String apply(String t) {
            return mf.getMainAttributes().getValue(t);
        }
        
    }

    @Override
    public void close() throws IOException {
        super.close();
        jarFile.close();
    }


    @Override
    public Archive getArchive() {
        return new JarArchive(jarFile);
    }

    @Override
    public Map<String, String> loadPermissions() throws IOException {
        JarEntry permEntry = jarFile.getJarEntry(META_INF_PERMISSIONS_PATH);
        if (permEntry == null) {
            return Collections.emptyMap();
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                        jarFile.getInputStream(permEntry), "UTF-8"))) {
            Map<String, String> permissions = parsePermissions(r);
            return permissions;
        }
    }

    @Override
    public void loadPaths() {
        ComponentInfo cinfo = getComponentInfo();
        Set<String> emptyDirectories = new HashSet<>();
        List<String> files = new ArrayList<>();
        for (JarEntry en : Collections.list(jarFile.entries())) {
            String eName = en.getName();
            if (eName.startsWith(META_INF_PATH)) {
                continue;
            }
            int li = eName.lastIndexOf("/", en.isDirectory() ? eName.length() - 2 : eName.length() - 1);
            if (li > 0) {
                emptyDirectories.remove(eName.substring(0, li + 1));
            }
            if (PATH_LICENSE.equals(eName)) {
                String lp = fb.l10n("LICENSE_Path_translation",
                                cinfo.getId(),
                                cinfo.getVersionString());
                files.add(lp);
                super.setLicensePath(lp);
                continue;
            }
            if (en.isDirectory()) {
                // directory names always come first
                emptyDirectories.add(eName);
            } else {
                files.add(eName);
            }
        }
        addFiles(new ArrayList<>(emptyDirectories));
        // sort empty directories first
        Collections.sort(files);
        cinfo.addPaths(files);
        addFiles(files);
    }

    @Override
    public Map<String, String> loadSymlinks() throws IOException {
        JarEntry symEntry = jarFile.getJarEntry(META_INF_SYMLINKS_PATH);
        if (symEntry == null) {
            return Collections.emptyMap();
        }
        Properties links = new Properties();
        try (InputStream istm = jarFile.getInputStream(symEntry)) {
            links.load(istm);
        }
        return parseSymlinks(links);
    }

}
