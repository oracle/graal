/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package org.graalvm.visualizer.util;

import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author sdedic
 */
public class FileHelpers {
    private FileHelpers() {
    }

    public static FileObject ensureConfigWritable(String pathInRoot) throws IOException {
        // attemp to create the file on disk
        FileObject target = FileUtil.getConfigFile(pathInRoot);
        if (target == null) {
            return FileUtil.getConfigRoot().createFolder(pathInRoot);
        }

        File rf = FileUtil.toFile(FileUtil.getConfigRoot());
        if (rf == null || !rf.exists() || !rf.isDirectory()) {
            throw new FileNotFoundException("Could not create configuration folder");
        }
        Path targetPath = rf.toPath().resolve(Paths.get(pathInRoot));
        if (!Files.exists(targetPath)) {
            targetPath = Files.createDirectories(targetPath);
        }
        File nf = targetPath.toFile();
        FileObject ret = null;
        int count = 0;
        // wait until caches refresh, max 5 * 100ms
        do {
            if (count > 0) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
            FileUtil.getConfigRoot().refresh();
            count++;
            ret = FileUtil.toFileObject(nf);
        } while (ret == null && count < 5);
        return ret;
    }
}
