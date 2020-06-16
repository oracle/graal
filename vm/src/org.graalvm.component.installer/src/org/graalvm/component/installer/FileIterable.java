/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.ServiceLoader;
import org.graalvm.component.installer.persist.MetadataLoader;

public class FileIterable extends AbstractIterable {
    public FileIterable(CommandInput input, Feedback fb) {
        super(input, fb);
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
                return new FileComponent(getFile(input.requiredParameter()), isVerifyJars(), null, feedback);
            }
        };
    }

    public static class FileComponent implements ComponentParam {
        private final File localFile;
        private MetadataLoader loader;
        private final boolean verifyJars;
        private final Feedback feedback;
        private final String serial;

        /**
         * Creates a file-based component.
         * 
         * @param localFile local file to load from
         * @param verifyJars true, if the jar signature should be verified
         * @param serial serial / tag of the component; if {@code null}, will be computed as sha256
         *            hash.
         * @param feedback output
         */
        public FileComponent(File localFile, boolean verifyJars, String serial, Feedback feedback) {
            this.localFile = localFile;
            this.verifyJars = verifyJars;
            this.serial = serial;
            this.feedback = feedback.withBundle(FileComponent.class);
        }

        @Override
        public MetadataLoader createMetaLoader() throws IOException {
            if (loader != null) {
                return loader;
            }
            byte[] fileStart = null;
            String ser;

            if (localFile.isFile()) {
                try (ReadableByteChannel ch = FileChannel.open(localFile.toPath(), StandardOpenOption.READ)) {
                    ByteBuffer bb = ByteBuffer.allocate(8);
                    ch.read(bb);
                    fileStart = bb.array();
                }
                if (serial != null) {
                    ser = serial;
                } else {
                    ser = SystemUtils.fingerPrint(SystemUtils.computeFileDigest(localFile.toPath(), null), false);
                }
            } else {
                fileStart = new byte[]{0, 0, 0, 0, 0, 0, 0, 0};
                ser = "";
            }

            for (ComponentArchiveReader provider : ServiceLoader.load(ComponentArchiveReader.class)) {
                MetadataLoader ldr = provider.createLoader(localFile.toPath(), fileStart, ser, feedback, verifyJars);
                if (ldr != null) {
                    loader = ldr;
                    return ldr;
                }
            }
            throw feedback.failure("ERROR_UnknownFileFormat", null, localFile.toString());
        }

        @Override
        public void close() throws IOException {
            if (loader != null) {
                loader.close();
            }
        }

        @Override
        public MetadataLoader createFileLoader() throws IOException {
            return createMetaLoader();
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
