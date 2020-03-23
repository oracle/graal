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
package org.graalvm.component.installer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.graalvm.component.installer.persist.DirectoryMetaLoader;
import org.graalvm.component.installer.persist.MetadataLoader;

/**
 *
 * @author sdedic
 */
public class DirectoryArchiveReader implements ComponentArchiveReader {
    @Override
    public MetadataLoader createLoader(Path p, byte[] fileStart, String serial, Feedback feedback, boolean verify) throws IOException {
        if (!Files.isDirectory(p)) {
            return null;
        }
        if (Files.isReadable(p.resolve("META-INF/MANIFEST.MF"))) {
            return DirectoryMetaLoader.create(p, feedback);
        } else {
            return null;
        }
    }
}
