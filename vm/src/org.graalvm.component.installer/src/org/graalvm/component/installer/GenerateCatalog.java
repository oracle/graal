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
import java.nio.file.Path;
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
import static org.graalvm.component.installer.BundleConstants.GRAALVM_CAPABILITY;
import static org.graalvm.component.installer.CommonConstants.CAP_GRAALVM_VERSION;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.persist.ComponentPackageLoader;
import org.graalvm.component.installer.persist.FileDownloader;

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
    private String graalVersionFormatString = "%s_%s_%s";

    private static final Map<String, String> OPTIONS = new HashMap<>();

    static {
        OPTIONS.put("v", "");  // verbose
        OPTIONS.put("g", "s");
        OPTIONS.put("e", "s");
        OPTIONS.put("o", "s");
        OPTIONS.put("f", "s");
        OPTIONS.put("n", "s");
        OPTIONS.put("a", "s");
        OPTIONS.put("n", "");  // GraalVM release name
        OPTIONS.put("b", "s");  // URL Base
        OPTIONS.put("p", "s");  // fileName base
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
            }
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
            RuntimeException err(String messageKey, Object... args) {
                ComponentInstaller.printErr(messageKey, args);
                System.exit(1);
                return null;
            }
        }.ignoreUnknownCommands(true);
        getopt.setParameters(new LinkedList<>(params));
        getopt.process();
        this.env = new Environment(null, null, getopt.getPositionalParameters(), getopt.getOptValues());
        this.env.setAllOutputToErr(true);

        String pb = env.optValue("p");
        if (pb != null) {
            pathBase = SystemUtils.fromUserString(pb).toAbsolutePath();
        }
        urlPrefix = env.optValue("b");
        graalVersionPrefix = env.optValue("g");
        if (graalVersionPrefix != null) {
            graalVersionName = env.optValue("n");
            if (graalVersionName == null) {
                throw new IOException("Graal prefix specified, but no human-readable name");
            }
        }
        forceVersion = env.optValue("e");
        forceOS = env.optValue("o");
        forceArch = env.optValue("a");
        String s = env.optValue("f");
        if (s != null) {
            graalNameFormatString = s;
        }

        // catalogContents.append(MessageFormat.format(
        // "{2}.{0}={1}\n", graalVersionPrefix, label, GRAALVM_CAPABILITY
        // ));

        while (env.hasParameter()) {
            locations.add(env.nextParameter());
        }
        /*
         * try (BufferedReader rr = new BufferedReader(new InputStreamReader(System.in))) { String
         * l;
         * 
         * while ((l = rr.readLine()) != null) { locations.add(l); } }
         */
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

    private String findComponentPrefix(ComponentInfo info) {
        Map<String, String> m = info.getRequiredGraalValues();
        if (graalVersionPrefix != null) {
            arch = os = null;
            version = graalVersionPrefix;
            return graalVersionPrefix;
        }
        return String.format("%s_%s_%s",
                        version = forceVersion != null ? forceVersion : m.get(CAP_GRAALVM_VERSION),
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
                vprefix = String.format(graalVersionFormatString, ver.version, ver.os, ver.arch);
                n = String.format(graalNameFormatString, ver.version, ver.os, ver.arch);
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
            try (JarFile jf = new JarFile(f)) {
                ComponentPackageLoader ldr = new ComponentPackageLoader(jf, env);
                ComponentInfo info = ldr.createComponentInfo();
                String prefix = findComponentPrefix(info);
                if (!graalVMReleases.containsKey(prefix)) {
                    graalVMReleases.put(prefix, new GraalVersion(version, os, arch));
                }
                Attributes atts = jf.getManifest().getMainAttributes();
                String bid = atts.getValue(BundleConstants.BUNDLE_ID).toLowerCase();
                String bl = atts.getValue(BundleConstants.BUNDLE_NAME);

                if (bid == null) {
                    throw new IOException("Missing bundle id in " + spec);
                }
                if (bl == null) {
                    throw new IOException("Missing bundle name in " + spec);
                }
                String name;

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
                catalogContents.append(MessageFormat.format(
                                "Component.{0}.{1}={2}\n", prefix, bid, url));
                catalogContents.append(MessageFormat.format(
                                "Component.{0}.{1}-hash={2}\n", prefix, bid, digest2String(hash)));
                for (Object a : atts.keySet()) {
                    String key = a.toString();
                    String val = atts.getValue(key);
                    if (key.startsWith("x-GraalVM-Message-")) { // NOI18N
                        continue;
                    }
                    catalogContents.append(MessageFormat.format(
                                    "Component.{0}.{1}-{2}={3}\n", prefix, bid, key, val));
                }
            }
        }
    }
}
