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
import java.util.Iterator;
import java.util.jar.JarFile;
import org.graalvm.component.installer.persist.ComponentPackageLoader;
import org.graalvm.component.installer.persist.MetadataLoader;

public class FileIterable implements ComponentIterable {
    private final CommandInput input;
    private final Feedback feedback;
    private boolean verifyJars;

    public FileIterable(CommandInput input, Feedback fb) {
        this.input = input;
        this.feedback = fb;
    }

    public boolean isVerifyJars() {
        return verifyJars;
    }

    @Override
    public void setVerifyJars(boolean verifyJars) {
        this.verifyJars = verifyJars;
    }

    private File getFile(String pathSpec) {
        File f = new File(pathSpec);
        if (f.exists()) {
            return f;
        }
        throw feedback.failure("ERROR_MissingFile", null, pathSpec);
    }

    @Override
    public Iterator<ComponentParam> iterator() {
        return new Iterator<ComponentParam>() {
            @Override
            public boolean hasNext() {
                return input.hasParameter();
            }

            @Override
            public ComponentParam next() {
                return new FileComponent(getFile(input.requiredParameter()), verifyJars, feedback);
            }
        };
    }

    public static class FileComponent implements ComponentParam {
        private final File localFile;
        private ComponentPackageLoader loader;
        private JarFile jf;
        private final boolean verifyJars;
        private final Feedback feedback;

        public FileComponent(File localFile, boolean verifyJars, Feedback feedback) {
            this.localFile = localFile;
            this.verifyJars = verifyJars;
            this.feedback = feedback;
        }

        @Override
        public MetadataLoader createMetaLoader() throws IOException {
            if (loader == null) {
                if (jf == null) {
                    jf = new JarFile(localFile, verifyJars);
                }
                loader = new ComponentPackageLoader(jf, feedback);
            }
            return loader;
        }

        @Override
        public void close() throws IOException {
            if (loader != null) {
                loader.close();
            } else if (jf != null) {
                jf.close();
            }
        }

        @Override
        public MetadataLoader createFileLoader() throws IOException {
            return createMetaLoader();
        }

        @Override
        public JarFile getFile() throws IOException {
            if (loader != null) {
                return loader.getJarFile();
            } else {
                jf = new JarFile(localFile, verifyJars);
                return jf;
            }
        }

        @Override
        public boolean isComplete() {
            return true;
        }

        @Override
        public String getSpecification() {
            return localFile.toString();
        }

        @Override
        public String getDisplayName() {
            return localFile.toString();
        }

        @Override
        public String getFullPath() {
            return localFile.getAbsolutePath();
        }

        @Override
        public String getShortName() {
            return localFile.getName();
        }

    }
}
