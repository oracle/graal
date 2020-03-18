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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import static org.graalvm.component.installer.BundleConstants.GRAALVM_CAPABILITY;
import org.graalvm.component.installer.jar.JarMetaLoader;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.persist.ComponentPackageLoader;
import org.graalvm.component.installer.remote.FileDownloader;

/**
 *
 * @author sdedic
 */
public final class GenerateCatalog {
    private List<String> params = new ArrayList<>();
    private List<String> locations = new ArrayList<>();
    private String graalVersionPrefix;
    private String graalVersionName;
    private String forceVersion;
    private String forceOS;
    private String forceArch;
    private String urlPrefix;
    private final StringBuilder catalogContents = new StringBuilder();
    private final StringBuilder catalogHeader = new StringBuilder();
    private Environment env;
    private String graalNameFormatString = "GraalVM %1s %2s/%3s";
    private String graalVersionFormatString;

    private static final Map<String, String> OPTIONS = new HashMap<>();

    private static final String OPT_FORMAT_1 = "1"; // NOI18N
    private static final String OPT_FORMAT_2 = "2"; // NOI18N
    private static final String OPT_VERBOSE = "v"; // NOI18N
    private static final String OPT_GRAAL_PREFIX = "g"; // NOI18N
    private static final String OPT_GRAAL_NAME = "n"; // NOI18N
    private static final String OPT_GRAAL_NAME_FORMAT = "f"; // NOI18N
    private static final String OPT_URL_BASE = "b"; // NOI18N
    private static final String OPT_PATH_BASE = "p"; // NOI18N
    private static final String OPT_FORCE_VERSION = "e"; // NO18N
    private static final String OPT_FORCE_OS = "o"; // NO18N
    private static final String OPT_FORCE_ARCH = "a"; // NO18N
    private static final String OPT_SEARCH_LOCATION = "l"; // NOI18N

    static {
        OPTIONS.put(OPT_FORMAT_1, "");  // format v1 < GraalVM 1.0.0-rc16+
        OPTIONS.put(OPT_FORMAT_2, "");  // format v2 = GraalVM 1.0.0-rc16+
        OPTIONS.put(OPT_VERBOSE, "");  // verbose
        OPTIONS.put(OPT_GRAAL_PREFIX, "s");
        OPTIONS.put(OPT_FORCE_VERSION, "s");
        OPTIONS.put(OPT_FORCE_OS, "s");
        OPTIONS.put(OPT_GRAAL_NAME_FORMAT, "s");
        OPTIONS.put(OPT_GRAAL_NAME, "s");
        OPTIONS.put(OPT_FORCE_ARCH, "s");
        OPTIONS.put(OPT_GRAAL_NAME, "");   // GraalVM release name
        OPTIONS.put(OPT_URL_BASE, "s");  // URL Base
        OPTIONS.put(OPT_PATH_BASE, "s");  // fileName base
        OPTIONS.put(OPT_SEARCH_LOCATION, "s");   // list files
    }

    private Map<String, GraalVersion> graalVMReleases = new LinkedHashMap<>();

    public static void main(String[] args) throws IOException {
        new GenerateCatalog(args).run();
        System.exit(0);
    }

    private GenerateCatalog(String[] args) {
        this.params = new ArrayList<>(Arrays.asList(args));
    }

    private static byte[] computeHash(File f) throws IOException {
        MessageDigest fileDigest;
        try {
            fileDigest = MessageDigest.getInstance("SHA-256"); // NOI18N
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("Cannot compute digest " + ex.getLocalizedMessage(), ex);
        }
        ByteBuffer bb = ByteBuffer.allocate(2048);
        boolean updated = false;
        try (
                        InputStream is = new FileInputStream(f);
                        ReadableByteChannel bch = Channels.newChannel(is)) {
            int read;
            while (true) {
                read = bch.read(bb);
                if (read < 0) {
                    break;
                }
                bb.flip();
                fileDigest.update(bb);
                bb.clear();
                updated = true;
            }
        }
        if (!updated) {
            fileDigest.update(new byte[0]);
        }

        return fileDigest.digest();
    }

    static String digest2String(byte[] digest) {
        StringBuilder sb = new StringBuilder(digest.length * 3);
        for (int i = 0; i < digest.length; i++) {
            sb.append(String.format("%02x", (digest[i] & 0xff)));
        }
        return sb.toString();
    }

    static class Spec {
        File f;
        String u;
        String relativePath;

        Spec(File f, String u) {
            this.f = f;
            this.u = u;
        }
    }

    static class GraalVersion {
        String version;
        String os;
        String arch;

        GraalVersion(String version, String os, String arch) {
            this.version = version;
            this.os = os;
            this.arch = arch;
        }

    }

    private List<Spec> componentSpecs = new ArrayList<>();

    private Path pathBase = null;

    public void run() throws IOException {
        readCommandLine();
        downloadFiles();
        generateCatalog();
        generateReleases();

        System.out.println(catalogHeader);
        System.out.println(catalogContents);
    }

    private void readCommandLine() throws IOException {
        SimpleGetopt getopt = new SimpleGetopt(OPTIONS) {
            @Override
            public RuntimeException err(String messageKey, Object... args) {
                ComponentInstaller.printErr(messageKey, args);
                System.exit(1);
                return null;
            }
        }.ignoreUnknownCommands(true);
        getopt.setParameters(new LinkedList<>(params));
        getopt.process();
        this.env = new Environment(null, getopt.getPositionalParameters(), getopt.getOptValues());
        this.env.setAllOutputToErr(true);

        String pb = env.optValue(OPT_PATH_BASE);
        if (pb != null) {
            pathBase = SystemUtils.fromUserString(pb).toAbsolutePath();
        }
        urlPrefix = env.optValue(OPT_URL_BASE);
        graalVersionPrefix = env.optValue(OPT_GRAAL_PREFIX);
        if (graalVersionPrefix != null) {
            graalVersionName = env.optValue(OPT_GRAAL_NAME);
            if (graalVersionName == null) {
                throw new IOException("Graal prefix specified, but no human-readable name");
            }
        }
        forceVersion = env.optValue(OPT_FORCE_VERSION);
        forceOS = env.optValue(OPT_FORCE_OS);
        forceArch = env.optValue(OPT_FORCE_ARCH);
        if (env.hasOption(OPT_FORMAT_1)) {
            formatVer = 1;
        } else if (env.hasOption(OPT_FORMAT_2)) {
            formatVer = 2;
        }
        String s = env.optValue(OPT_GRAAL_NAME_FORMAT);
        if (s != null) {
            graalNameFormatString = s;
        }

        switch (formatVer) {
            case 1:
                graalVersionFormatString = "%s_%s_%s";
                break;
            case 2:
                graalVersionFormatString = "%2$s_%3$s/%1$s";
                break;
            default:
                throw new IllegalStateException();
        }

        if (env.hasOption(OPT_SEARCH_LOCATION)) {
            Path listFrom = Paths.get(env.optValue("l"));
            Files.walk(listFrom).filter((p) -> p.toString().endsWith(".jar")).forEach(
                            (p) -> locations.add(p.toString()));
        } else {
            while (env.hasParameter()) {
                locations.add(env.nextParameter());
            }
        }

        for (String spec : locations) {
            File f = null;
            String u = null;

            int eq = spec.indexOf('=');
            if (eq != -1) {
                f = new File(spec.substring(0, eq));
                if (!f.exists()) {
                    throw new FileNotFoundException(f.toString());
                }
                String uriPart = spec.substring(eq + 1);
                u = uriPart;
            } else {
                f = new File(spec);
                if (!f.exists()) {
                    f = null;
                    u = spec;
                }
            }
            addComponentSpec(f, u);
        }
    }

    private void addComponentSpec(File f, String u) {
        Spec spc = new Spec(f, u);
        if (f != null) {
            if (pathBase != null) {
                spc.relativePath = pathBase.relativize(f.toPath().toAbsolutePath()).toString();
            }
        }
        componentSpecs.add(spc);
    }

    private URL createURL(String spec) throws MalformedURLException {
        if (urlPrefix != null) {
            return new URL(new URL(urlPrefix), spec);
        } else {
            return new URL(spec);
        }
    }

    private void downloadFiles() throws IOException {
        for (Spec spec : componentSpecs) {
            if (spec.f == null) {
                FileDownloader dn = new FileDownloader(spec.u, createURL(spec.u), env);
                dn.setDisplayProgress(true);
                dn.download();
                spec.f = dn.getLocalFile();
            }
        }
    }

    private String os;
    private String arch;
    private String version;
    private int formatVer = 1;

    private String findComponentPrefix(ComponentInfo info) {
        Map<String, String> m = info.getRequiredGraalValues();
        if (graalVersionPrefix != null) {
            arch = os = null;
            version = graalVersionPrefix;
            return graalVersionPrefix;
        }
        if (forceVersion != null) {
            version = forceVersion;
        } else {
            switch (formatVer) {
                case 1:
                    version = info.getVersionString();
                    break;
                case 2:
                    version = info.getVersion().toString();
                    break;
            }
        }
        return String.format(graalVersionFormatString,
                        version,
                        os = forceOS != null ? forceOS : m.get(CommonConstants.CAP_OS_NAME),
                        arch = forceArch != null ? forceArch : m.get(CommonConstants.CAP_OS_ARCH));
    }

    private void generateReleases() {
        for (String prefix : graalVMReleases.keySet()) {
            GraalVersion ver = graalVMReleases.get(prefix);
            String vprefix;
            String n;
            if (ver.os == null) {
                vprefix = graalVersionPrefix;
                n = graalVersionName;
            } else {
                // do not use serial for releases.
                vprefix = String.format(graalVersionFormatString, ver.version, ver.os, ver.arch, "");
                n = String.format(graalNameFormatString, ver.version, ver.os, ver.arch, "");
            }
            catalogHeader.append(GRAALVM_CAPABILITY).append('.').append(vprefix).append('=').append(n).append('\n');
            if (ver.os == null) {
                break;
            }
        }
    }

    private void generateCatalog() throws IOException {
        for (Spec spec : componentSpecs) {
            File f = spec.f;
            byte[] hash = computeHash(f);
            String hashString = digest2String(hash);
            try (JarFile jf = new JarFile(f)) {
                ComponentPackageLoader ldr = new JarMetaLoader(jf, hashString, env);
                ComponentInfo info = ldr.createComponentInfo();
                String prefix = findComponentPrefix(info);
                if (!graalVMReleases.containsKey(prefix)) {
                    graalVMReleases.put(prefix, new GraalVersion(version, os, arch));
                }
                Manifest mf = jf.getManifest();
                if (mf == null) {
                    throw new IOException("No manifest in " + spec);
                }
                String tagString;

                if (formatVer < 2 || info.getTag() == null || info.getTag().isEmpty()) {
                    tagString = "";
                } else {
                    // include hash of the file in property prefix.
                    tagString = "/" + hashString; // NOI18N
                }
                Attributes atts = mf.getMainAttributes();
                String bid = atts.getValue(BundleConstants.BUNDLE_ID).toLowerCase().replace("-", "_");
                String bl = atts.getValue(BundleConstants.BUNDLE_NAME);

                if (bid == null) {
                    throw new IOException("Missing bundle id in " + spec);
                }
                if (bl == null) {
                    throw new IOException("Missing bundle name in " + spec);
                }
                String name;
                prefix += tagString;
                if (spec.u != null) {
                    name = spec.u.toString();
                } else {
                    name = spec.relativePath != null ? spec.relativePath : f.getName();
                }
                int pos;
                while ((pos = name.indexOf("${")) != -1) {
                    int endPos = name.indexOf("}", pos + 1);
                    if (endPos == -1) {
                        break;
                    }
                    String key = name.substring(pos + 2, endPos);
                    String repl = info.getRequiredGraalValues().get(key);
                    if (repl == null) {
                        switch (key) {
                            case "version":
                                repl = version;
                                break;
                            case "os":
                                repl = os;
                                break;
                            case "arch":
                                repl = arch;
                                break;
                            case "comp_version":
                                repl = info.getVersionString();
                                break;
                            default:
                                throw new IllegalArgumentException(key);
                        }
                    }
                    if (repl == null) {
                        throw new IllegalArgumentException(key);
                    }
                    String toReplace = "${" + key + "}";
                    name = name.replace(toReplace, repl);
                }
                String url = (urlPrefix == null || urlPrefix.isEmpty()) ? name : urlPrefix + "/" + name;
                String sel;
                String hashSuffix;

                switch (formatVer) {
                    case 1:
                        sel = "Component.{0}.{1}";
                        hashSuffix = "-hash";
                        break;
                    case 2:
                        sel = "Component.{0}/{1}";
                        hashSuffix = "-hash";
                        break;
                    default:
                        throw new IllegalStateException();
                }
                catalogContents.append(MessageFormat.format(
                                sel + "={2}\n", prefix, bid, url));
                catalogContents.append(MessageFormat.format(
                                sel + hashSuffix + "={2}\n", prefix, bid, hashString));

                for (Object a : atts.keySet()) {
                    String key = a.toString();
                    String val = atts.getValue(key);
                    if (key.startsWith("x-GraalVM-Message-")) { // NOI18N
                        continue;
                    }
                    catalogContents.append(MessageFormat.format(
                                    sel + "-{2}={3}\n", prefix, bid, key, val));
                }
            }
        }
    }
}
