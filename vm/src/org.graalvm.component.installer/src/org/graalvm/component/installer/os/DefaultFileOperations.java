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
package org.graalvm.component.installer.os;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.concurrent.Callable;
import org.graalvm.component.installer.FileOperations;

/**
 *
 * @author sdedic
 */
public class DefaultFileOperations extends FileOperations {

    private static final Set<PosixFilePermission> ALL_WRITE_PERMS = PosixFilePermissions.fromString("rwxrwxrwx"); // NOI18N

    @Override
    protected boolean doWithPermissions(Path p, Callable<Void> action) throws IOException {
        Set<PosixFilePermission> restoreDirPermissions = null;
        Files.setPosixFilePermissions(p, ALL_WRITE_PERMS);
        Path d = p.normalize().getParent();
        // set the parent directory's permissions, but do not
        // alter permissions outside the to-be-deleted tree:
        if (d == null) {
            throw new IOException("Cannot determine parent of " + p);
        }
        if (d.startsWith(rootPath()) && !d.equals(rootPath())) {
            restoreDirPermissions = Files.getPosixFilePermissions(d);
            try {
                Files.setPosixFilePermissions(d, ALL_WRITE_PERMS);
            } catch (IOException ex) {
                // mask out, but report failure
                return false;
            }
        }
        boolean ok = false;
        try {
            action.call();
            ok = true;
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            ok = false;
        } finally {
            if (restoreDirPermissions != null) {
                try {
                    Files.setPosixFilePermissions(d, restoreDirPermissions);
                } catch (IOException ex2) {
                    // do not obscure the result with this exception
                    feedback().error("FILE_ErrorRestoringPermissions", ex2, p, ex2.getLocalizedMessage());
                    ok = false;
                }
            }

        }
        return ok;
    }

    @Override
    public void setPermissions(Path target, Set<PosixFilePermission> perms) throws IOException {
        Files.setPosixFilePermissions(target, perms);
    }

    @Override
    public boolean flush() {
        return false;
    }
}
