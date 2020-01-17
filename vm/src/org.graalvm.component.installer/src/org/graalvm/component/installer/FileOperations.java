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
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.concurrent.Callable;
import org.graalvm.component.installer.os.DefaultFileOperations;
import org.graalvm.component.installer.os.WindowsFileOperations;
import org.graalvm.nativeimage.ImageInfo;

/**
 *
 * @author sdedic
 */
public abstract class FileOperations {
    private Feedback feedback;
    private Path rootPath;

    public void init(Feedback fb) {
        this.feedback = fb;
    }

    protected final Feedback feedback() {
        return feedback;
    }

    protected final Path rootPath() {
        return rootPath;
    }

    public abstract boolean flush() throws IOException;

    /**
     * Materializes the path. The input path is relative to the root; the path will be resolved
     * against the root. Then, if copy-on-write was performed, the path will be redirected to a
     * sibling directory.
     * 
     * @param p the path to materialize
     * 
     * @return the target path or {@code null}
     */
    @SuppressWarnings("unused")
    public Path materialize(Path p, boolean write) {
        return p;
    }

    protected void performDelete(Path p) throws IOException {
        Files.deleteIfExists(p);
    }

    protected abstract boolean doWithPermissions(Path p, Callable<Void> action) throws IOException;

    @SuppressWarnings("unused")
    protected void handleUndeletableFile(IOException ex, Path p) throws IOException {
        throw ex;
    }

    @SuppressWarnings("unused")
    protected Path handleUnmodifiableFile(IOException ex, Path p, InputStream content) throws IOException {
        throw ex;
    }

    private void deleteOneFile(Path p, Path rp) throws IOException {
        try {
            if (p.equals(rp)) {
                return;
            }
            performDelete(p);
        } catch (AccessDeniedException ex) {
            if (!doWithPermissions(p, () -> {
                performDelete(p);
                return null;
            })) {
                throw ex;
            }
        } catch (FileSystemException ex) {
            handleUndeletableFile(ex, p);
        }
    }

    /**
     * Deletes a path relative to the installation root.
     * <p>
     * If the operation fails, the implementation will create a <b>sibling</b> of the owning
     * directory and <b>copies</b> all files into it, preserving timestamps and access rights, if
     * possible. The file to be deleted will <b>not</b> be added to the copied directory.
     * </p>
     * <p>
     * Although the delete fails, if the copy-on-write succeeds, the operation reports overall
     * success.
     * 
     * @param p path to delete
     * @throws IOException
     */
    public void deleteFile(Path p) throws IOException {
        deleteOneFile(p, rootPath);
    }

    protected void performInstall(Path target, InputStream contents) throws IOException {
        Files.copy(contents, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Installs a file, potentially replacing an existing file.
     * 
     * @param target
     * @throws IOException
     */
    public Path installFile(Path target, InputStream contents) throws IOException {
        Path ret = target;
        try {
            performInstall(target, contents);
        } catch (AccessDeniedException ex) {
            doWithPermissions(target, () -> {
                performInstall(target, contents);
                return null;
            });
        } catch (FileSystemException ex) {
            ret = handleUnmodifiableFile(ex, target, contents);
        }
        return ret;
    }

    public Path getRootPath() {
        return rootPath;
    }

    public void setRootPath(Path rootPath) {
        this.rootPath = rootPath;
    }

    public abstract void setPermissions(Path target, Set<PosixFilePermission> perms) throws IOException;

    public static FileOperations createPlatformInstance(Feedback f, Path rootPath) {
        FileOperations inst;
        if (SystemUtils.isWindows()) {
            WindowsFileOperations w = new WindowsFileOperations();
            inst = w;
            if (!ImageInfo.inImageCode()) {
                w.setDelayDeletedList(SystemUtils.fromUserString(System.getenv(CommonConstants.ENV_DELETE_LIST)));
                w.setCopyContents(SystemUtils.fromUserString(System.getenv(CommonConstants.ENV_COPY_CONTENTS)));
            }
        } else {
            inst = new DefaultFileOperations();
        }
        inst.init(f);
        inst.setRootPath(rootPath);
        return inst;
    }
}
