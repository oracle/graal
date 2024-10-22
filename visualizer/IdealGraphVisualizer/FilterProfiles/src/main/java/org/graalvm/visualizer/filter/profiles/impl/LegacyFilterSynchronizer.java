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

package org.graalvm.visualizer.filter.profiles.impl;

import jdk.graal.compiler.graphio.parsing.model.ChangedListener;
import org.graalvm.visualizer.filter.CustomFilter;
import org.graalvm.visualizer.filter.Filter;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileLock;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.util.Exceptions;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author sdedic
 */
public class LegacyFilterSynchronizer extends FileChangeAdapter implements ChangedListener<Filter> {
    private FileObject fileObject;
    private final CustomFilter filter;
    private final AtomicBoolean sync = new AtomicBoolean();

    public LegacyFilterSynchronizer(FileObject fo, CustomFilter cf) {
        fileObject = fo;
        filter = cf;
    }

    private <T extends FileEvent> void syncOnce(Runnable f) {
        if (!sync.compareAndSet(false, true)) {
            return;
        }
        try {
            f.run();
        } finally {
            sync.set(false);
        }
    }

    @Override
    public void fileRenamed(FileRenameEvent fe) {
        syncOnce(() -> {
            if (fe.getFile().getParent() != fileObject.getParent()) {
                return;
            }
            filter.setName(fe.getName());
        });
    }

    @Override
    public void fileChanged(FileEvent fe) {
        syncOnce(() -> {
            try {
                if (!fe.getFile().isValid()) {
                    return;
                }
                filter.setCode(fe.getFile().asText());
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        });
    }

    @Override
    public void changed(Filter source) {
        syncOnce(() -> {
            try {
                if (!fileObject.getName().equals(filter.getName())) {
                    FileLock lock = fileObject.lock();
                    FileObject newFileObject = fileObject.move(lock, fileObject.getParent(), filter.getName(), "");
                    lock.releaseLock();
                    fileObject = newFileObject;
                }
                if (!fileObject.asText().equals(filter.getCode())) {
                    try (FileLock lock = fileObject.lock();
                         OutputStream os = fileObject.getOutputStream(lock)) {
                        try (Writer w = new OutputStreamWriter(os)) {
                            String s = filter.getCode();
                            w.write(s);
                        }
                    }
                }
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        });
    }
}
