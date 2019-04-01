/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.graalvm.component.installer.commands;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.graalvm.component.installer.CommonConstants;
import static org.graalvm.component.installer.CommonConstants.WARN_REBUILD_IMAGES;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.model.ComponentInfo;

/**
 *
 * @author sdedic
 */
public class PreRemoveProcess {
    private static final Set<PosixFilePermission> ALL_WRITE_PERMS = PosixFilePermissions.fromString("rwxrwxrwx");

    private final Path installPath;
    private final Feedback feedback;
    private final List<ComponentInfo> infos = new ArrayList<>();
    private boolean rebuildPolyglot;
    
    private boolean dryRun;
    private boolean ignoreFailedDeletions;
    private boolean removeBaseDir;

    public PreRemoveProcess(Path instPath, Feedback fb) {
        this.feedback = fb;
        
        installPath = instPath;
    }

    public boolean isRemoveBaseDir() {
        return removeBaseDir;
    }

    public PreRemoveProcess setRemoveBaseDir(boolean removeBaseDir) {
        this.removeBaseDir = removeBaseDir;
        return this;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public PreRemoveProcess setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
        return this;
    }

    public boolean isIgnoreFailedDeletions() {
        return ignoreFailedDeletions;
    }

    public PreRemoveProcess setIgnoreFailedDeletions(boolean ignoreFailedDeletions) {
        this.ignoreFailedDeletions = ignoreFailedDeletions;
        return this;
    }

    public void addComponentInfo(ComponentInfo info) {
        this.infos.add(info);
    }
    
    /**
     * Process all the components, prints message.
     * @throws IOException if deletion fails
     */
    void run() throws IOException {
        for (ComponentInfo ci : infos) {
            processComponent(ci);
        }
        if (rebuildPolyglot && WARN_REBUILD_IMAGES) {
            Path p = SystemUtils.fromCommonString(CommonConstants.PATH_JRE_BIN);
            feedback.output("INSTALL_RebuildPolyglotNeeded", File.separator, installPath.resolve(p).normalize());
        }
    }
    
    /**
     * Called also from Uninstaller. Will delete one single file, possibly with altering
     * permissions on the parent so the file can be deleted.
     * @param p file to delete
     * @param rootPath root path to the graalVM
     * @throws IOException 
     */
    void deleteOneFile(Path p) throws IOException {
        try {
            deleteOneFile0(p);
        } catch (IOException ex) {
            if (ignoreFailedDeletions) {
                if (Files.isDirectory(p)) {
                    feedback.error("INSTALL_FailedToDeleteDirectory", ex, p, ex.getLocalizedMessage());
                } else {
                    feedback.error("INSTALL_FailedToDeleteFile", ex, p, ex.getLocalizedMessage());
                }
                return;
            }
            throw ex;
//            throw new UncheckedIOException(ex);
        }
    }

    private void deleteOneFile0(Path p) throws IOException {
        try {
            if (p.equals(installPath)) {
                return;
            }
            Files.deleteIfExists(p);
        } catch (AccessDeniedException ex) {
            // try again to adjust permissions for the file AND the containing
            // directory AND try again:
            PosixFileAttributeView attrs = Files.getFileAttributeView(
                            p, PosixFileAttributeView.class);
            Set<PosixFilePermission> restoreDirPermissions = null;
            if (attrs != null) {
                Files.setPosixFilePermissions(p, ALL_WRITE_PERMS);
                Path d = p.getParent();
                // set the parent directory's permissions, but do not
                // alter permissions outside the to-be-deleted tree:
                if (d == null) {
                    throw new IOException("Cannot determine parent of " + p);
                }
                if (d.startsWith(installPath) && !d.equals(installPath)) {
                    restoreDirPermissions = Files.getPosixFilePermissions(d);
                    Files.setPosixFilePermissions(d, ALL_WRITE_PERMS);
                }
                try {
                    // 2nd try
                    Files.deleteIfExists(p);
                } catch (IOException ex2) {
                    // report the original access denied
                    throw ex;
                } finally {
                    if (restoreDirPermissions != null) {
                        try {
                            Files.setPosixFilePermissions(d, restoreDirPermissions);
                        } catch (IOException ex2) {
                            // do not obscure the result with this exception
                            feedback.error("UNINSTALL_ErrorRestoringPermissions", ex2, p, ex2.getLocalizedMessage());
                        }
                    }
                }
            }
        }
    }

    /**
     * Also called from Uninstaller.
     * @param rootPath root path to delete (inclusive)
     * @throws IOException if the deletion fails.
     */
    void deleteContentsRecursively(Path rootPath) throws IOException {
        if (dryRun) {
            return;
        }
        try (Stream<Path> paths = Files.walk(rootPath)) {
            paths.sorted(Comparator.reverseOrder()).forEach((p) -> {
                try {
                    deleteOneFile(p);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }
    
    void processComponent(ComponentInfo ci) throws IOException {
        Path langParentPath = SystemUtils.fromCommonRelative(CommonConstants.LANGUAGE_PARENT);
        rebuildPolyglot |= ci.isPolyglotRebuild();
        for (String s : ci.getWorkingDirectories()) {
            Path relPath = SystemUtils.fromCommonRelative(s);
            if (langParentPath.equals(relPath.getParent()) && !removeBaseDir) {
                return;
            }
            Path p = installPath.resolve(SystemUtils.fromCommonRelative(s));
            feedback.verboseOutput("UNINSTALL_DeletingDirectoryRecursively", p);
            if (ci.getWorkingDirectories().contains(s)) {
                deleteContentsRecursively(p);
            }
        }
    }
}
