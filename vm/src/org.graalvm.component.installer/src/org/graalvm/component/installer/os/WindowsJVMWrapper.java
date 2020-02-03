/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.FileOperations;
import org.graalvm.component.installer.SystemUtils;

/**
 *
 * @author sdedic
 */
public class WindowsJVMWrapper {
    private final Feedback fb;
    private final FileOperations fileOps;
    private final Path installPath;

    private String mainClass;
    private String jvmBinary;
    private List<String> args = Collections.emptyList();
    private List<String> jvmArgs = Collections.emptyList();
    private String classpath = "."; // NOI18N

    public WindowsJVMWrapper(Feedback fb, FileOperations fops, Path installPath) {
        this.fb = fb.withBundle(WindowsJVMWrapper.class);
        this.fileOps = fops;
        this.installPath = installPath;
    }

    public WindowsJVMWrapper vm(String path, List<String> vmArgs) {
        jvmBinary = path;
        jvmArgs = vmArgs;
        return this;
    }

    public WindowsJVMWrapper mainClass(String mc) {
        this.mainClass = mc;
        return this;
    }

    public WindowsJVMWrapper classpath(String cp) {
        this.classpath = cp;
        return this;
    }

    public WindowsJVMWrapper args(List<String> a) {
        this.args = a;
        return this;
    }

    public int execute() throws IOException {
        assert mainClass != null;
        assert jvmBinary != null;

        Path copyPath = Files.createTempFile("gu_copy_", ".lst"); // NOI18N
        Path deletePath = Files.createTempFile("gu_delete", ".lst"); // NOI18N
        copyPath.toFile().deleteOnExit();
        deletePath.toFile().deleteOnExit();

        ProcessBuilder builder = new ProcessBuilder();
        Map<String, String> env = builder.environment();
        env.put(CommonConstants.ENV_COPY_CONTENTS, copyPath.toAbsolutePath().toString());
        env.put(CommonConstants.ENV_DELETE_LIST, deletePath.toAbsolutePath().toString());

        List<String> all = new ArrayList<>(jvmArgs.size() + args.size() + 1);
        all.add(jvmBinary);
        if (classpath != null) {
            all.add("-classpath"); // NOI18N
            all.add(classpath);
        }
        all.addAll(jvmArgs);
        all.add(mainClass);
        all.addAll(args);

        Process proc = builder.command(all).inheritIO().start();

        try {
            proc.waitFor();
        } catch (InterruptedException ex) {
        }
        int exitValue = proc.exitValue();
        if (exitValue != CommonConstants.WINDOWS_RETCODE_DELAYED_OPERATION) {
            return exitValue;
        }
        deleteRecursively(deletePath);
        copyContents(copyPath);
        return 0;
    }

    void copyContents(Path listFile) throws IOException {
        boolean first = true;
        for (String desc : Files.readAllLines(listFile)) {
            int i = desc.indexOf('|');
            if (i == -1) {
                continue;
            }
            Path p = SystemUtils.fromUserString(desc.substring(0, i));
            Path q = SystemUtils.fromUserString(desc.substring(i + 1));
            if (first) {
                fb.message("MSG_CopyNewFiles");
                first = false;
            }
            SystemUtils.copySubtree(q, p);
            try {
                deleteFileRecursively(q);
            } catch (IOException ex) {
                fb.error("ERR_CannotDeletePath", ex, p, ex.getMessage());
            }
        }
    }

    void deleteRecursively(Path listFile) throws IOException {
        boolean first = true;
        for (String fn : Files.readAllLines(listFile)) {
            if (first) {
                fb.message("MSG_DeleteObsoleteFiles");
                first = false;
            }
            Path p = SystemUtils.fromUserString(fn);
            try {
                deleteFileRecursively(p);
            } catch (IOException ex) {
                fb.error("ERR_CannotDeletePath", ex, p, ex.getMessage());
            }
        }
    }

    /**
     * Also called from Uninstaller.
     * 
     * @param rootPath root path to delete (inclusive)
     * @throws IOException if the deletion fails.
     */
    void deleteFileRecursively(Path rootPath) throws IOException {
        try (Stream<Path> paths = Files.walk(rootPath)) {
            paths.sorted(Comparator.reverseOrder()).forEach((p) -> {
                if (!p.toAbsolutePath().startsWith(installPath)) {
                    return;
                }
                try {
                    fileOps.deleteFile(p);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }
}
