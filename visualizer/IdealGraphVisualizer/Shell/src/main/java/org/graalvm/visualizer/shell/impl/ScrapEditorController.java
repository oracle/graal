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
package org.graalvm.visualizer.shell.impl;

import org.graalvm.visualizer.shell.ShellUtils;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.URLMapper;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.loaders.DataShadow;
import org.openide.modules.OnStart;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitors 'scrap' shell editor. If the editor is closed (not saved), it will
 * delete the associated scrap file.
 * <p/>
 * In a sense, it emulates "Save new file" feature, which is not supported by NetBeans
 * right now. The shell document is initially considered "scrap" until the user saves
 * and names it.
 * <p/>
 * If the file is saved (timestamp changes, content changes), it will prompt the
 * user for the real name.
 *
 * @author sdedic
 */
public final class ScrapEditorController {
    private static final Logger LOG = Logger.getLogger(ScrapEditorController.class.getName());

    /**
     * Prevents to attach twice
     */
    private static final Map<FileObject, Reference<ScrapEditorController>> scraps = new WeakHashMap<>();

    private final FileObject file;
    private final DataObject data;
    private final EditorCookie.Observable editor;
    private final FA listener = new FA();

    public ScrapEditorController(DataObject data) {
        this.data = data;
        this.file = data.getPrimaryFile();
        this.editor = data.getLookup().lookup(EditorCookie.Observable.class);
    }

    static ScrapEditorController get(FileObject f) {
        synchronized (scraps) {
            Reference<ScrapEditorController> refScrap = scraps.get(f);
            return refScrap == null ? null : refScrap.get();
        }
    }

    public void attach() {
        synchronized (scraps) {
            Reference<ScrapEditorController> refScrap = scraps.get(file);
            if (refScrap != null && refScrap.get() != null) {
                return;
            }
            scraps.put(file, new WeakReference<>(this));
        }
        editor.addPropertyChangeListener(listener);
    }

    void detach() {
        synchronized (scraps) {
            Reference<ScrapEditorController> refScrap = scraps.get(file);
            if (refScrap != null && refScrap.get() == this) {
                scraps.remove(file);
            }
        }
        editor.removePropertyChangeListener(listener);
    }

    private void discardFile() {
        editor.removePropertyChangeListener(listener);
        if (data.isValid() && !ShellUtils.visibleScriptObjects().test(data.getPrimaryFile())) {
            try {
                data.delete();
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "Could not delete scrap file {0}: {1}", new Object[]{ // NOI18N
                        file.getPath(), ex.getMessage()
                });
            }
        }
    }

    // copied from SaveAsAction

    /**
     * Show file 'save as' dialog window to ask user for a new file name.
     *
     * @return File selected by the user or null if no file was selected.
     */
    private File getNewFileName() {
        File newFile = null;
        FileObject currentFileObject = file;
        if (null != currentFileObject) {
            newFile = FileUtil.toFile(currentFileObject);
            if (null == newFile) {
                newFile = new File(currentFileObject.getNameExt());
            }
        }
        // FIXME: do not allow to change extension
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(NbBundle.getMessage(DataObject.class, "LBL_SaveAsTitle")); //NOI18N
        chooser.setMultiSelectionEnabled(false);
        if (null != newFile) {
            chooser.setSelectedFile(newFile);
            chooser.setCurrentDirectory(newFile.getParentFile());
        }
        File initialFolder;

        try {
            FileObject f = ShellUtils.ensureScriptRoot();
            initialFolder = FileUtil.toFile(f);
        } catch (IOException ex) {
            // ignore, do not save, the folder does not exist at all
            return null;
        }
        if (null != initialFolder) {
            chooser.setCurrentDirectory(initialFolder);
        }
        File origFile = newFile;
        while (true) {
            if (JFileChooser.APPROVE_OPTION != chooser.showSaveDialog(WindowManager.getDefault().getMainWindow())) {
                return null;
            }
            newFile = chooser.getSelectedFile();
            if (null == newFile) {
                break;
            }
            if (newFile.equals(origFile)) {
                // accept the same name - no rename will be done.
                return newFile;
            } else if (newFile.exists()) {
                NotifyDescriptor nd = new NotifyDescriptor(
                        NbBundle.getMessage(DataObject.class, "MSG_SaveAs_OverwriteQuestion", newFile.getName()), //NOI18N
                        NbBundle.getMessage(DataObject.class, "MSG_SaveAs_OverwriteQuestion_Title"), //NOI18N
                        NotifyDescriptor.YES_NO_OPTION,
                        NotifyDescriptor.QUESTION_MESSAGE,
                        new Object[]{NotifyDescriptor.NO_OPTION, NotifyDescriptor.YES_OPTION}, NotifyDescriptor.NO_OPTION);
                if (NotifyDescriptor.YES_OPTION == DialogDisplayer.getDefault().notify(nd)) {
                    break;
                }
            } else {
                break;
            }
        }
        return newFile;
    }

    @NbBundle.Messages({
            "TITLE_SaveScrapFile=Save as...",
            "LABEL_SaveScrapFile=File name",
            "TITLE_SaveFileError=Save error",
            "# {0} - I/O error message",
            "ERR_SaveFile=Could not save file: {0}"
    })
    private boolean promptAndRename() {
        FileObject scriptRoot;
        try {
            scriptRoot = ShellUtils.ensureScriptRoot();
        } catch (IOException ex) {
            LOG.log(Level.INFO, "Could not obtain script root");
            return true;
        }
        File ff = getNewFileName();
        if (ff == null) {
            return false;
        }
        Path dirPath = ff.getParentFile().toPath();
        Path cfgPath = FileUtil.toFile(FileUtil.getConfigRoot()).toPath();
        final FileObject tf;
        FileObject newFileO;
        if (dirPath.startsWith(cfgPath)) {
            String rel = cfgPath.relativize(dirPath).toString();
            tf = FileUtil.getConfigRoot().getFileObject(rel);
        } else {
            tf = FileUtil.toFileObject(ff.getParentFile());
        }
        newFileO = tf == null ? null : tf.getFileObject(ff.getName());
        // sometimes the file comes from the config fs, sometimes from the master fs
        // compare URLs rather than file objects.
        URL u1 = URLMapper.findURL(newFileO, URLMapper.EXTERNAL);
        URL u2 = URLMapper.findURL(file, URLMapper.EXTERNAL);
        if (u1 != null && u2 != null && Objects.equals(u1.getPath(), u2.getPath())) {
            ShellUtils.materializeScrapFile(file);
            return true;
        }
        try {
            // rename / move the file
            scriptRoot.getFileSystem().runAtomicAction(new FileSystem.AtomicAction() {
                @Override
                public void run() throws IOException {
                    if (tf == null) {
                        throw new FileNotFoundException(ff.getParentFile().getPath());
                    }
                    DataFolder target = DataFolder.findFolder(tf);
                    if (target == null) {
                        throw new FileNotFoundException(ff.getParentFile().getPath());
                    }
                    String n = ff.getName();
                    int dotIndex = n.lastIndexOf('.');
                    if (dotIndex != -1) {
                        String suffix = n.substring(dotIndex + 1);
                        if (!suffix.equals(file.getExt())) {
                            data.setModified(true);
                            JEditorPane[] panes = editor.getOpenedPanes();
                            if (panes == null) {
                                // reopen
                                editor.open();
                            }
                            throw new IOException("Extension change not permitted");
                        }
                        n = n.substring(0, dotIndex);
                    }
                    data.rename(n);
                    if (tf != data.getPrimaryFile().getParent()) {
                        data.move(target);
                    }
                    ShellUtils.materializeScrapFile(data.getPrimaryFile());
                    // if the new file is outside of the script root, make a shadow to it.
                    if (tf != scriptRoot && !FileUtil.isParentOf(scriptRoot, tf)) {
                        DataShadow.create(DataFolder.findFolder(scriptRoot), data);
                    }
                }
            });
            return true;
        } catch (IOException ex) {
            NotifyDescriptor d = new NotifyDescriptor.Message(
                    Bundle.ERR_SaveFile(ff.getName()),
                    NotifyDescriptor.ERROR_MESSAGE
            );
            DialogDisplayer.getDefault().notifyLater(d);
            return false;
        }
    }

    class FA extends FileChangeAdapter implements PropertyChangeListener {
        @Override
        public void fileChanged(FileEvent fe) {
            super.fileChanged(fe);
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getSource() == editor) {
                switch (evt.getPropertyName()) {
                    case EditorCookie.Observable.PROP_OPENED_PANES:
                        if (editor.getOpenedPanes() == null) {
                            discardFile();
                        }
                        break;
                    case EditorCookie.Observable.PROP_MODIFIED:
                        if (Boolean.FALSE.equals(evt.getNewValue())) {
                            if (promptAndRename()) {
                                editor.removePropertyChangeListener(this);
                            }
                        }
                        break;
                }
            }
        }
    }

    @OnStart
    public static class RegistryObserver implements PropertyChangeListener, Runnable {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (TopComponent.Registry.PROP_TC_OPENED.equals(evt.getPropertyName())) {
                if (!(evt.getNewValue() instanceof TopComponent)) {
                    return;
                }
                TopComponent tc = (TopComponent) evt.getNewValue();
                if (tc != null) {
                    FileObject f = tc.getLookup().lookup(FileObject.class);
                    if (ShellUtils.isScriptObject(f) && !ShellUtils.visibleScriptObjects().test(f)) {
                        ScrapEditorController ctrl = ScrapEditorController.get(f);
                        if (ctrl == null) {
                            try {
                                new ScrapEditorController(DataObject.find(f)).attach();
                            } catch (DataObjectNotFoundException ex) {
                                Exceptions.printStackTrace(ex);
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void run() {
            TopComponent.getRegistry().addPropertyChangeListener(this);
        }
    }
}
