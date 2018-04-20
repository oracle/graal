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
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import static org.graalvm.component.installer.BundleConstants.GRAALVM_CAPABILITY;

public final class GenerateCatalog {
    private List<String> archives;
    private final String graalVersionPrefix;
    private final String urlPrefix;
    private final String label;
    private final StringBuilder catalogS = new StringBuilder();

    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            System.err.println("Invalid arguments");
            System.err.println("Usage: GenerateCatalog <version prefix> <label> <url prefix> file [ file...]");
            return;
        }
        new GenerateCatalog(args).run();
    }

    private GenerateCatalog(String[] args) {
        graalVersionPrefix = args[0];
        label = args[1];
        urlPrefix = args[2].trim();
        this.archives = Arrays.asList(args).subList(3, args.length);
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

    String digest2String(byte[] digest) {
        StringBuilder sb = new StringBuilder(digest.length * 3);
        for (int i = 0; i < digest.length; i++) {
            sb.append(String.format("%02x", (digest[i] & 0xff)));
        }
        return sb.toString();
    }

    public void run() throws IOException {
        catalogS.append(MessageFormat.format(
                        "{2}.{0}={1}\n", graalVersionPrefix, label, GRAALVM_CAPABILITY));
        for (String spec : archives) {
            File f = new File(spec);
            if (!f.exists()) {
                throw new IOException("File does not exist: " + f);
            }
            byte[] hash = computeHash(f);
            try (JarFile jf = new JarFile(f)) {
                Attributes atts = jf.getManifest().getMainAttributes();
                String bid = atts.getValue(BundleConstants.BUNDLE_ID).toLowerCase();
                String bl = atts.getValue(BundleConstants.BUNDLE_NAME);

                if (bid == null) {
                    throw new IOException("Missing bundle id in " + spec);
                }
                if (bl == null) {
                    throw new IOException("Missing bundle name in " + spec);
                }
                String url = (urlPrefix.isEmpty()) ? f.getName() : urlPrefix + "/" + f.getName();
                catalogS.append(MessageFormat.format(
                                "Component.{0}.{1}={2}\n", graalVersionPrefix, bid, url));
                catalogS.append(MessageFormat.format(
                                "Component.{0}.{1}-hash={2}\n", graalVersionPrefix, bid, digest2String(hash)));
                for (Object a : atts.keySet()) {
                    String key = a.toString();
                    String val = atts.getValue(key);

                    catalogS.append(MessageFormat.format(
                                    "Component.{0}.{1}-{2}={3}\n", graalVersionPrefix, bid, key, val));
                }
            }
        }
        System.out.println(catalogS);
    }
}
