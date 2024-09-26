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
package org.graalvm.visualizer.shell;

import jdk.graal.compiler.graphio.parsing.model.Group;
import org.graalvm.visualizer.script.UserScriptEngine;
import org.graalvm.visualizer.util.FileHelpers;
import org.openide.filesystems.FileAttributeEvent;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileStateInvalidException;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.loaders.DataShadow;
import org.openide.modules.Places;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.LookupListener;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.util.WeakListeners;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author sdedic
 */
public class ShellUtils {
    private static final Logger LOG = Logger.getLogger(ShellUtils.class.getName());

    public static final String SCRIPT_ROOT_FOLDER = "scripts"; // NOI18N
    public static final String SCRIPT_TEMPLATES_FOLDER = "Templates/Scripts"; // NOI18N

    public static final String SCRAP_FOLDER_PATH = "igv-shell"; // NOI18N
    public static final String SHELL_EXTENSION = "shell";

    public static final String ATTR_GRAPH_SCRIPT = "igv.diagramScript"; // NOI18N
    public static final String ATTR_HIDDEN = "igv.hidden"; // NOI18N

    private static final String PREF_LAST_TEMPLATE = "lastScriptTemplate";
    private static final String DEF_LAST_TEMPLATE = "Templates/Scripts/JSFilter.js";

    private static EngineCache engCache;

    /**
     * Returns the script root. Since the folder is present in the default configuration,
     * it is assumed it always exists; if the user has removed the folder, the
     * method will throw an {@link IOException}.
     *
     * @return script root folder
     * @throws IOException
     */
    public static FileObject getScriptRoot() throws IOException {
        FileObject f = FileUtil.getConfigFile(SCRIPT_ROOT_FOLDER);
        if (f == null) {
            throw new FileNotFoundException(SHELL_EXTENSION);
        } else {
            return f;
        }
    }

    @NbBundle.Messages({
            "ERR_NoScrapTemplate=Could not find simple template",
    })

    /**
     * Creates a 'scrap' script, which is not visible in the scripts folder.
     * @param template
     * @return Created 'scrap' script
     * @throws IOException
     */
    public static DataObject createScrapScript(DataObject template) throws IOException {
        DataObject selected = template;
        if (template == null) {
            DataFolder templates = ShellUtils.getTemplatesFolder();

            for (DataObject tmpl : templates.getChildren()) {
                if (tmpl.getPrimaryFile().getAttribute(ATTR_SCRAP_TEMPLATE) != null) {
                    selected = tmpl;
                    break;
                }
            }
        }

        if (selected == null) {
            throw new IOException(Bundle.ERR_NoScrapTemplate());
        }
        final DataObject fSelected = selected;
        FileObject scriptRoot = ensureScriptRoot();
        DataFolder fld = DataFolder.findFolder(scriptRoot);
        DataObject[] result = new DataObject[1];
        fld.getPrimaryFile().getFileSystem().runAtomicAction(new FileSystem.AtomicAction() {
            @Override
            public void run() throws IOException {
                DataObject scriptData = fSelected.createFromTemplate(fld);
                FileObject sc = scriptData.getPrimaryFile();
                sc.setAttribute(ATTR_HIDDEN, true);
                sc.setAttribute(ATTR_SCRAP_TEMPLATE, null);
                result[0] = scriptData;
            }
        });
        return result[0];
    }

    private static final String ATTR_SCRAP_TEMPLATE = "scrapTemplate";

    public static FileObject ensureScriptRoot() throws IOException {
        return FileHelpers.ensureConfigWritable(SCRIPT_ROOT_FOLDER);
        /*
        FileObject f = FileUtil.getConfigFile(SCRIPT_ROOT_FOLDER);
        
        if (f == null) {
            return FileUtil.getConfigRoot().createFolder(SCRIPT_ROOT_FOLDER);
        }
        File ff = FileUtil.toFile(f);
        if (ff != null) {
            return f;
        }
        // attemp to create the file on disk
        File rf = FileUtil.toFile(FileUtil.getConfigRoot());
        if (rf == null || !rf.exists() || !rf.isDirectory()) {
            throw new FileNotFoundException("Could not create script folder");
        }
        File nf = new File(rf, SCRIPT_ROOT_FOLDER);
        if (!nf.mkdirs()) {
            throw new FileNotFoundException("Could not create script folder");
        }
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
        */
    }

    public static FileObject createGrouShellDirectory(Group compilationGroup) throws IOException {
        String n = compilationGroup.getName();
        File f = Places.getCacheSubdirectory(SCRAP_FOLDER_PATH);
        FileObject fo = FileUtil.toFileObject(f);
        String path = n + "." + SHELL_EXTENSION;
        FileObject dir = fo.getFileObject(path);
        if (dir == null) {
            dir = fo.createFolder(path);
        }
        return dir;
    }

    public static String getLanguageName(String mimeType) {
        FileObject f = FileUtil.getConfigFile("Editors/" + mimeType);
        if (f == null) {
            return null;
        }
        try {
            String dispName = f.getFileSystem().getDecorator().annotateName(null, Collections.singleton(f));
            if (dispName != null) {
                return dispName;
            }
            Object[] bundleInfo = findResourceBundle(f);
            if (bundleInfo[1] != null) {
                try {
                    return ((ResourceBundle) bundleInfo[1]).getString(mimeType);
                } catch (MissingResourceException ex) {
                }
            }
            return mimeType;
        } catch (FileStateInvalidException ex) {
            Exceptions.printStackTrace(ex);
        }
        return mimeType;
    }

    private static Object[] findResourceBundle(FileObject fo) {
        assert fo != null : "FileObject can't be null"; //NOI18N

        Object[] bundleInfo = null;
        String bundleName = null;
        Object attrValue = fo.getAttribute("SystemFileSystem.localizingBundle"); //NOI18N
        if (attrValue instanceof String) {
            bundleName = (String) attrValue;
        }

        if (bundleName != null) {
            try {
                bundleInfo = new Object[]{bundleName, NbBundle.getBundle(bundleName)};
            } catch (MissingResourceException ex) {
            }
        } else {
        }

        if (bundleInfo == null) {
            bundleInfo = new Object[]{bundleName, null};
        }
        return bundleInfo;
    }

    public static DataObject getLastScriptTemplate() {
        String x = NbPreferences.forModule(ShellUtils.class).get(PREF_LAST_TEMPLATE, DEF_LAST_TEMPLATE);
        FileObject ft = FileUtil.getConfigFile(x);
        if (ft == null || !ft.isValid()) {
            return null;
        }
        DataObject d;
        try {
            d = DataObject.find(ft);
        } catch (DataObjectNotFoundException ex) {
            return null;
        }
        return d.isTemplate() ? d : null;
    }

    public static void setLastScriptTemplate(DataObject selected) {
        NbPreferences.forModule(ShellUtils.class).put(PREF_LAST_TEMPLATE, selected.getPrimaryFile().getPath());
    }

    public static DataFolder getTemplatesFolder() throws IOException {
        FileObject f = FileUtil.getConfigFile(SCRIPT_TEMPLATES_FOLDER);
        if (f == null || !f.isFolder()) {
            throw new FileNotFoundException(f == null ? "Not found" : f.getPath());
        }
        return (DataFolder) DataObject.find(f);
    }

    /**
     * Marks the data object as graph script.
     *
     * @param f the target file
     * @throws IOException
     */
    public static void markScriptObject(FileObject f, boolean markOn) throws IOException {
        DataObject d = DataObject.find(f);
        FileObject orig = DataShadow.findOriginal(f);
        if (orig == null) {
            orig = d.getPrimaryFile();
        }
        if (orig == null) {
            throw new FileNotFoundException(f.getPath());
        }
        orig.setAttribute(ATTR_GRAPH_SCRIPT, markOn ? Boolean.TRUE : null);
    }

    public static boolean isScriptObject(FileObject f) {
        return f != null && (
                Boolean.TRUE.equals(f.getAttribute(ATTR_GRAPH_SCRIPT)) ||
                        f.getPath().startsWith("Filters/") ||
                        f.getPath().startsWith("IGV/") ||
                        f.getPath().startsWith("_")
        );
    }

    private static final Predicate<FileObject> VISIBLE_PREDICATE = new Predicate<FileObject>() {
        @Override
        public boolean test(FileObject t) {
            return t != null && t.getAttribute(ATTR_HIDDEN) != Boolean.TRUE;

        }
    };

    public static Predicate<FileObject> visibleScriptObjects() {
        return VISIBLE_PREDICATE;
    }

    public static Collection<String> getSupportedLanguages() {
        return engCache().supportedMimes();
    }

    private static EngineCache engCache() {
        EngineCache c;
        synchronized (ShellUtils.class) {
            if ((c = engCache) == null) {
                c = new EngineCache();
                engCache = c;
            }
        }
        return c;
    }

    public static boolean isScriptMimeType(String mime) {
        return engCache().supportedMimes().contains(mime);
    }

    static class EngineCache {
        private final Lookup.Result<UserScriptEngine> engines;
        private Set<String> mimes = null;
        private final LookupListener ll;
        private final AtomicInteger version = new AtomicInteger(0);

        public EngineCache() {
            engines = Lookup.getDefault().lookupResult(UserScriptEngine.class);
            engines.addLookupListener(WeakListeners.create(
                    LookupListener.class,
                    ll = (ev) -> invalidate(),
                    engines));
        }

        private synchronized void invalidate() {
            this.mimes = null;
            version.incrementAndGet();
        }

        private Collection<String> supportedMimes() {
            int v = version.get();
            synchronized (this) {
                if (mimes != null) {
                    return Collections.unmodifiableCollection(mimes);
                }
            }
            Set<String> m = new HashSet<>();

            for (UserScriptEngine e : engines.allInstances()) {
                m.addAll(e.supportedLanguages());
            }

            synchronized (this) {
                if (version.get() == v) {
                    mimes = Collections.unmodifiableSet(m);
                }
            }
            return m;
        }
    }

    static final Map<FileObject, List<Consumer<FileObject>>> scrapNotifyList = new WeakHashMap<>();
    static final List<Consumer<FileObject>> globalScrapNotifyList = new ArrayList<>();

    static class ScrapListener extends FileChangeAdapter {

        @Override
        public void fileAttributeChanged(FileAttributeEvent fe) {
            if (!ATTR_HIDDEN.equals(fe.getName())) {
                return;
            }
            FileObject file = fe.getFile();
            file.removeFileChangeListener(this);
            notifyScriptMaterialized(file);
        }
    }

    public static void materializeScrapFile(FileObject script) {
        if (script == null || !script.isValid()) {
            return;
        }
        try {
            script.setAttribute(ATTR_HIDDEN, null);
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Could not un-hide file {0}", script);
        }
    }

    private static void notifyScriptMaterialized(FileObject script) {
        List<Consumer<FileObject>> cons;

        synchronized (scrapNotifyList) {
            cons = new ArrayList<>(Optional.ofNullable(scrapNotifyList.remove(script)).orElse(Collections.emptyList()));
            cons.addAll(globalScrapNotifyList);
        }
        for (Consumer<FileObject> c : cons) {
            c.accept(script);
        }
    }

    private static final FileChangeListener SCRAP_SHARED_LISTENER = new ScrapListener();

    public static void onScrapMaterialize(FileObject scrap, Consumer<FileObject> scrapCallback) {
        List<Consumer<FileObject>> refs;
        synchronized (scrapNotifyList) {
            if (scrap == null) {
                globalScrapNotifyList.add(scrapCallback);
                return;
            }
            refs = scrapNotifyList.computeIfAbsent(scrap, (f) -> new ArrayList<>());
        }
        if (refs.isEmpty()) {
            scrap.addFileChangeListener(SCRAP_SHARED_LISTENER);
        }
        refs.add(scrapCallback);
    }

    public static void removeMaterializeCallback(FileObject scrap, Consumer<FileObject> listener) {
        synchronized (scrapNotifyList) {
            if (scrap == null) {
                globalScrapNotifyList.remove(listener);
                return;
            }
            List<Consumer<FileObject>> refs = scrapNotifyList.get(scrap);
            if (refs == null) {
                return;
            }
            refs.remove(listener);
        }
    }

    public static FileObject lookupFilterFile(Lookup lkp) {
        try {
            FileObject f = lkp.lookup(FileObject.class);
            if (f == null || !f.isValid()) {
                return null;
            }
            FileObject linked = DataShadow.findOriginal(f);
            if (linked != null) {
                f = linked;
            }
            return isScriptObject(f) ? f : null;
        } catch (IOException ex) {
            return null;
        }
    }

    private ShellUtils() {
    }
}
