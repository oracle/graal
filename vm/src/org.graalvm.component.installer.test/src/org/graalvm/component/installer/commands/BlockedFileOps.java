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
package org.graalvm.component.installer.commands;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.os.DefaultFileOperations;
import org.graalvm.component.installer.os.WindowsFileOperations;

/**
 *
 * @author sdedic
 */
class BlockedFileOps extends WindowsFileOperations {

    class DefDelegate extends DefaultFileOperations {

        @Override
        public Path handleUnmodifiableFile(IOException ex, Path p, InputStream content) throws IOException {
            return super.handleUnmodifiableFile(ex, p, content);
        }

        @Override
        public void handleUndeletableFile(IOException ex, Path p) throws IOException {
            super.handleUndeletableFile(ex, p);
        }

        @Override
        public boolean doWithPermissions(Path p, Callable<Void> action) throws IOException {
            return super.doWithPermissions(p, action);
        }
    }

    private DefDelegate defSupport;
    Set<Path> blockedPaths = new HashSet<>();
    Path delayDeletes;
    Path copiedFiles;

    @Override
    public void init(Feedback feedback) {
        super.init(feedback);
        defSupport = new DefDelegate();
        defSupport.init(feedback);
    }

    @Override
    protected void performInstall(Path target, InputStream contents) throws IOException {
        if (blockedPaths.contains(target)) {
            throw new FileSystemException("");
        }
        super.performInstall(target, contents);
    }

    @Override
    protected void performDelete(Path target) throws IOException {
        if (blockedPaths.contains(target)) {
            throw new FileSystemException("");
        }
        super.performDelete(target);
    }

    @Override
    public void setPermissions(Path target, Set<PosixFilePermission> perms) throws IOException {
        if (SystemUtils.isWindows()) {
            super.setPermissions(target, perms);
        } else {
            defSupport.setPermissions(target, perms);
        }
    }

    @Override
    protected boolean doWithPermissions(Path p, Callable<Void> action) throws IOException {
        if (SystemUtils.isWindows()) {
            return super.doWithPermissions(p, action);
        } else {
            // make it OS-dependent way
            return defSupport.doWithPermissions(p, action);
        }
    }

}
