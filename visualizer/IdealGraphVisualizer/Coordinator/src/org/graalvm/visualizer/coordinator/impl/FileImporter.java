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
package org.graalvm.visualizer.coordinator.impl;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import org.graalvm.visualizer.coordinator.OutlineTopComponent;
import org.graalvm.visualizer.coordinator.actions.ImportAction;
import org.graalvm.visualizer.coordinator.actions.SaveAsAction;
import org.graalvm.visualizer.data.FolderElement;
import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.KnownPropertyNames;
import org.graalvm.visualizer.data.serialization.BinaryReader;
import org.graalvm.visualizer.data.serialization.FileContent;
import org.graalvm.visualizer.data.serialization.GraphParser;
import org.graalvm.visualizer.data.serialization.ModelBuilder;
import org.graalvm.visualizer.data.serialization.ParseMonitor;
import org.graalvm.visualizer.data.serialization.ZipFileContent;
import org.graalvm.visualizer.data.serialization.lazy.CachedContent;
import org.graalvm.visualizer.data.serialization.lazy.CancelableSource;
import org.graalvm.visualizer.data.serialization.lazy.ScanningModelBuilder;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Cancellable;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.util.Utilities;

/**
 *
 * @author sdedic
 */
public class FileImporter {

    private static final Logger LOG = Logger.getLogger(FileImporter.class.getName());
    private static final int WORKUNITS = 10000;
    private static final RequestProcessor LOADER_RP = new RequestProcessor(ImportAction.class.getName(), 10);

    public static FileFilter getFileFilter() {
        return SaveAsAction.getFileFilter();
    }

    static class Mon implements ParseMonitor, Cancellable {

        final SeekableByteChannel channel;
        final AtomicBoolean cancelFlag = new AtomicBoolean();
        ProgressHandle handle;

        public Mon(SeekableByteChannel channel) {
            this.channel = channel;
        }

        synchronized void setHandle(ProgressHandle h) {
            this.handle = h;
        }

        @Override
        public void updateProgress() {
            try {
                int prog = (int) (WORKUNITS * (double) channel.position() / (double) channel.size());
                if (prog > WORKUNITS) {
                    prog = WORKUNITS;
                }
                synchronized (this) {
                    handle.progress(prog);
                }
            } catch (IOException ex) {
                // ignore
            }
        }

        @Override
        public void setState(String state) {
            updateProgress();
            handle.progress(state);
        }

        @Override
        public boolean isCancelled() {
            return cancelFlag.get();
        }

        @Override
        public boolean cancel() {
            cancelFlag.set(true);
            return true;
        }

        @Override
        public void reportError(List<FolderElement> parents, List<String> parentNames, String name, String errorMessage) {
            SessionManagerImpl.reportLoadingError(parents, parentNames, name, errorMessage);
        }
    }

    @NbBundle.Messages({
        "# {0} - file name",
        "# {1} - error message",
        "ERR_ReadingFile=Error importing from file {0}: {1}",
        "# {0} - file name",
        "MSG_LoadCancelled=Load of {0} was cancelled",
        "# {0} - file extension",
        "WAR_WrongExtension=Wrong file extension: {0}",
        "WAR_PropperExtension=Extension has to be: bgv"

    })
    public static void asyncImportDocument(Path path, boolean reportErrors, 
            boolean addToMainDocument,
            BiConsumer<GraphDocument, IOException> callback) throws IOException {
        Callable<GraphDocument> c = createDocumentImporter(path, reportErrors, addToMainDocument, callback);
        if (c == null) {
            return;
        }
        LOADER_RP.post(() -> {
            try {
                c.call();
                SwingUtilities.invokeLater(() -> {
                    final OutlineTopComponent component = OutlineTopComponent.findInstance();
                    if (component != null) {
                        component.requestActive();
                    }
                });
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
        });
    }
    
    static Callable<GraphDocument> createDocumentImporter(Path path, boolean reportErrors, 
            boolean addToMainDocument,
            BiConsumer<GraphDocument, IOException> callback) throws IOException {

        Path fname = path.getFileName();
        if (fname == null) {
            throw new IllegalArgumentException(path.toString());
        }
        final GraphParser parser;
        
        final CachedContent content;
        final Channel closeChannel;
        final Object id = path.toFile();
        String fn = fname.toString().toLowerCase(Locale.ENGLISH);
        Mon monitor;
        if (fn.endsWith(".zip")) {
            ZipFileContent zc = new ZipFileContent(path, Utilities.activeReferenceQueue());
            monitor = new Mon(zc);
            content = zc;
            closeChannel = null;
        } else {
            final FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
            content = new FileContent(path, channel);
            monitor = new Mon(channel);
            closeChannel = channel;
        }
        FileObject fo = FileUtil.toFileObject(path.toFile());
        if (fo == null) {
            // FIXME: report
            return null;
        }
        final ProgressHandle handle = ProgressHandleFactory.createHandle("Opening file " + fname.toString(), monitor);
        monitor.setHandle(handle);
        handle.start(WORKUNITS);

        CancelableSource src = new CancelableSource(id, monitor, content);
        
        ManagedSessionImpl targetDocument;
        targetDocument = new ManagedSessionImpl(fo);
        ModelBuilder bld = new ScanningModelBuilder(
                src,
                content,
                targetDocument, monitor,
                LOADER_RP).
                setDocumentId(id);
        
        parser = new BinaryReader(src, bld);
        
        final long startTime = System.currentTimeMillis();
        return () -> {
            IOException exc = null;
            try (GraphDocument.DocumentlLock lock = targetDocument.writeLock(null, null)) {
                lock.trackModifications(false);
                parser.parse();
            } catch (AssertionError e) {
                if (reportErrors) {
                    reportException(path, e);
                }
                exc = new IOException(e);
            } catch (IOException ex) {
                if (reportErrors) {
                    reportException(path, ex);
                }
                exc = ex;
            } finally {
                try {
                    if (closeChannel != null) {
                        closeChannel.close();
                    }
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
                handle.finish();
            }
            if (exc != null && !reportErrors) {
                throw exc;
            }
            if (callback != null) {
                callback.accept(targetDocument, exc);
            }
            if (targetDocument.getProperties().getString(KnownPropertyNames.PROPNAME_NAME, null) == null) {
                targetDocument.getProperties().setProperty(
                    KnownPropertyNames.PROPNAME_NAME, 
                    SessionManagerImpl.getInstance().getSessionDisplayName(targetDocument, false));
            }
            SessionManagerImpl.getInstance().addSession(targetDocument);
            long stop = System.currentTimeMillis();
            LOG.log(Level.INFO, "Loaded in " + path + " in " + ((stop - startTime) / 1000.0) + " seconds");
            return targetDocument;
        };
    }

    private static void reportException(Path path, Throwable ex) {
        if (ex instanceof InterruptedIOException) {
            DialogDisplayer.getDefault().notifyLater(
                            new NotifyDescriptor.Message(
                            Bundle.MSG_LoadCancelled(path),
                            NotifyDescriptor.INFORMATION_MESSAGE));
        } else {
            Exceptions.printStackTrace(
                Exceptions.attachLocalizedMessage(ex, Bundle.ERR_ReadingFile(path, ex.toString()))
            );
        }
        
    }
}
