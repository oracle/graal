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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import org.graalvm.component.installer.FileOperations;

/**
 * Windows-specific FileOperations, with delayed deletes and renames. {@link #setDelayDeletedList}
 * and {@link #setCopyContents} must be used prior to working with files so the failed operations
 * are recorded for the post-run batch processing. In SVM mode, these properties are unset, so any
 * file operation failure will be reported immediately.
 * 
 * @author sdedic
 */
public class WindowsFileOperations extends FileOperations {
    /**
     * File that contains list of delayed deletions.
     */
    private Path delayDeletedList;

    /**
     * File that contains move instructions.
     */
    private Path copyContents;

    /**
     * Paths which have been copied because of locked files.
     */
    private Map<Path, Path> copiedPaths = new HashMap<>();

    /**
     * Paths which should be deleted, but the operation must be delayed.
     */
    private NavigableSet<Path> delayDeletedPaths = new TreeSet<>();

    public void setDelayDeletedList(Path delayDeletedList) {
        this.delayDeletedList = delayDeletedList;
    }

    public void setCopyContents(Path copyContents) {
        this.copyContents = copyContents;
    }

    public Map<Path, Path> getCopiedPaths() {
        return copiedPaths;
    }

    public Set<Path> getDelayDeletedPaths() {
        return delayDeletedPaths;
    }

    @Override
    protected boolean doWithPermissions(Path p, Callable<Void> action) throws IOException {
        AclFileAttributeView aclView = Files.getFileAttributeView(p, AclFileAttributeView.class);
        UserPrincipalLookupService upls = p.getFileSystem().getUserPrincipalLookupService();
        String un = System.getProperty("user.name"); // NOI18N
        UserPrincipal up;
        List<AclEntry> save;

        try {
            up = upls.lookupPrincipalByName(un);
            save = aclView.getAcl();

            List<AclEntry> temp = new ArrayList<>(save);
            AclEntry en = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(up).setPermissions(AclEntryPermission.DELETE).build();
            temp.add(en);
            aclView.setAcl(temp);
        } catch (IOException ex) {
            // expected, bail out
            return false;
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
            try {
                aclView.setAcl(save);
            } catch (IOException ex) {
                // do not obscure the result with this exception
                feedback().error("FILE_ErrorRestoringPermissions", ex, p, ex.getLocalizedMessage());
                // expected, bail out
                ok = false;
            }

        }
        return ok;
    }

    @Override
    public boolean flush() throws IOException {
        boolean r = false;
        if (copyContents != null) {
            List<String> lines = new ArrayList<>(copiedPaths.size());
            for (Map.Entry<Path, Path> e : copiedPaths.entrySet()) {
                Path orig = e.getKey();
                if (Files.exists(orig)) {
                    String s = orig.toAbsolutePath().toString() + "|" + e.getValue().toAbsolutePath().toString();
                    lines.add(s);
                    // do not delete the directory's contents.
                    delayDeletedPaths.remove(orig);
                }
            }
            if (!lines.isEmpty()) {
                r = true;
            }
            Files.write(copyContents, lines,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING);
        }
        if (delayDeletedList != null) {
            List<String> lines = new ArrayList<>(delayDeletedPaths.size());
            for (Path p : delayDeletedPaths) {
                lines.add(p.toAbsolutePath().toString());
            }
            if (!lines.isEmpty()) {
                r = true;
            }
            Files.write(delayDeletedList, lines,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING);
        }
        return r;
    }

    @Override
    protected void handleUndeletableFile(IOException ex, Path p) throws IOException {
        if (delayDeletedList == null) {
            super.handleUndeletableFile(ex, p);
            return;
        }
        delayDeletedPaths.add(p);
        feedback().output("FILE_CannotDeleteFileTryDelayed", p, ex.getLocalizedMessage());
    }

    @Override
    protected Path handleUnmodifiableFile(IOException ex, Path p, InputStream content) throws IOException {
        if (copyContents == null) {
            return super.handleUnmodifiableFile(ex, p, content);
        }
        Path fn = p.getFileName();
        Path parentDir = p.getParent();
        assert parentDir != null;
        assert fn != null;
        Path pn = parentDir.getFileName();
        assert pn != null;
        Path copy = parentDir.resolveSibling(pn.toString() + ".new");
        copiedPaths.put(parentDir, copy);

        feedback().output("FILE_CannotInstallFileTryDelayed", p, ex.getLocalizedMessage());

        Files.createDirectories(copy);
        Path target = copy.resolve(fn);
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    /**
     * Materializes the path. The input path is relative to the root; the path will be resolved
     * against the root. Then, if copy-on-write was performed, the path will be redirected to a
     * sibling directory.
     * 
     * @param p the path to materialize
     * 
     * @return the target path or {@code null}
     */
    @Override
    public Path materialize(Path p, boolean write) {
        if (copyContents == null || delayDeletedList == null) {
            return super.materialize(p, write);
        }
        Path parentDir = p.getParent();
        Path copy = copiedPaths.get(parentDir);
        Path fn = p.getFileName();
        assert fn != null;
        assert parentDir != null;
        if (copy != null) {
            Path r = copy.resolve(fn);
            return r;
        }
        if (delayDeletedPaths.contains(p)) {
            if (write) {
                Path pn = parentDir.getFileName();
                assert pn != null;
                copy = parentDir.resolveSibling(pn.toString() + ".new");
                copiedPaths.put(parentDir, copy);
                return copy.resolve(fn);
            } else {
                // the file was deleted.
                return null;
            }
        }
        return p;
    }

    @Override
    public void setPermissions(Path target, Set<PosixFilePermission> perms) throws IOException {
        // ignore permissions on Windows.
    }

    @Override
    protected void performDelete(Path p) throws IOException {
        // check if something inside the subtree was scheduled for delay-delete.
        // If so, then schedule also the parent:
        Path next = delayDeletedPaths.ceiling(p);
        if (next != null && next.startsWith(p)) {
            feedback().output("FILE_CannotDeleteParentTryDelayed", p);
        } else {
            super.performDelete(p);
        }
    }
}
