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
package org.graalvm.visualizer.coordinator.actions;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.graalvm.visualizer.coordinator.impl.SessionManagerImpl;
import jdk.graal.compiler.graphio.parsing.model.*;
import jdk.graal.compiler.graphio.parsing.model.Properties;
import org.netbeans.api.progress.BaseProgressUtils;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Cancellable;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

import jdk.graal.compiler.graphio.parsing.DataBinaryWriter;

/**
 * @author sdedic
 */
class SaveOperation {
    /**
     * Provides a path to save the document into.
     */
    public interface DocumentPathProvider {
        Path createPath(Path suggestedPath, GraphDocument document);
    }

    private final SessionManagerImpl sessionMgr;
    private final boolean saveAs;
    private final Collection<Folder> folders = new ArrayList<>();

    /**
     * Selected data, grouped by the session.
     */
    private final Map<GraphDocument, List<Folder>> sessionData = new LinkedHashMap<>();

    /**
     * Explicitly selected session nodes.
     */
    private final Set<GraphDocument> selectedSessions = new HashSet<>();

    /**
     * Files associated with individual sessions, if available.
     */
    private final Map<GraphDocument, FileObject> files = new HashMap();

    private boolean nonSessionsSelected;

    private boolean prepared;

    /**
     * The path where the save should take place
     */
    private Path userPath;

    private Path lastPath;

    private Path userFolder;

    private Style saveStyle = Style.FILES;

    private String userTitle;

    private final List<GraphDocument> documentOrder = new ArrayList<>();

    /**
     * Maps graph documents meant for save to the original
     * session with the contents.
     */
    private final Map<GraphDocument, GraphDocument> documentOrigins = new HashMap<>();

    /**
     * List of documents to be saved.
     * The documents may be assembled from various sources.
     */
    private final List<GraphDocument> documentsToSave = new ArrayList<>();

    private DocumentPathProvider pathProvider = new IncrementPathProvider();

    private int documentsSaved;

    public enum Style {
        /**
         * Retain sessions in the dump, as groups.
         */
        SESSIONS,

        /**
         * Collects groups from all sessions into the dump.
         */
        GROUPS,

        /**
         * Save each session in a separate file.
         */
        FILES
    }

    public SaveOperation(boolean saveAs, Collection<Folder> initial, SessionManagerImpl sessionImpl) {
        if (initial != null) {
            this.folders.addAll(initial);
        }
        this.saveAs = saveAs;
        this.sessionMgr = sessionImpl;
    }

    public void addFolders(Collection<Folder> toAdd) {
        this.folders.addAll(toAdd);
    }

    public boolean hasMultipleSessions() {
        return sessionData.size() > 1;
    }

    public boolean isSaveAs() {
        return this.saveAs;
    }

    public boolean hasGroups() {
        return nonSessionsSelected;
    }

    public Style getSaveStyle() {
        if (saveAs) {
            return saveStyle;
        } else {
            return Style.FILES;
        }
    }

    public void setSaveStyle(Style saveStyle) {
        this.saveStyle = saveStyle;
    }

    public DocumentPathProvider getPathProvider() {
        return pathProvider;
    }

    public void setPathProvider(DocumentPathProvider pathProvider) {
        this.pathProvider = pathProvider;
    }

    public String getInitialFileComment() {
        if (sessionData.isEmpty()) {
            return null;
        }
        GraphDocument first = sessionData.keySet().iterator().next();
        return first.getProperties().getString(KnownPropertyNames.PROPNAME_USER_LABEL, null);
    }

    public Path getUserPath() {
        return userPath;
    }

    public Path getUserFolder() {
        return userFolder;
    }

    public void setUserPath(Path userPath) {
        this.userPath = userPath;
        userFolder = userPath.getParent();
    }

    public String getUserTitle() {
        return userTitle;
    }

    public void setUserTitle(String userTitle) {
        if (userTitle != null && userTitle.isEmpty()) {
            this.userTitle = null;
        } else {
            this.userTitle = userTitle;
        }
    }

    public int getDocumentsSaved() {
        return documentsSaved;
    }

    /**
     * Removes children of other items, retains the topmost parents.
     *
     * @param folders list to filter. Modifies the argument.
     */
    private void retainJustParents(List<Folder> folders) {
        for (Iterator<Folder> it = folders.iterator(); it.hasNext(); ) {
            Folder item = it.next();

            for (Iterator<Folder> it2 = folders.iterator(); it2.hasNext(); ) {
                Folder candidate = it2.next();
                if (candidate != item && candidate.isParentOf(item)) {
                    it.remove();
                    break;
                }
            }
        }
    }

    private boolean savingEntireDocument(GraphDocument doc) {
        List<Folder> items = sessionData.get(doc);
        if (items == null || items.size() != 1) {
            return false;
        }
        return doc == items.get(0);
    }

    public boolean isFinished() {
        return documentOrder.isEmpty();
    }

    /**
     * If saving separate sessions and NOT in "Save As" mode,
     * will save all sessions with files attached.
     */
    public void saveFileBoundSessions() {
        // if not session-style, will need to reshuffle to a new file.
        if (saveAs || nonSessionsSelected) {
            return;
        }

        List<GraphDocument> saved = new ArrayList<>();
        for (GraphDocument d : new ArrayList<>(documentOrder)) {
            if (savingEntireDocument(d)) {
                FileObject f = getFile(d);
                if (f != null && f.isValid()) {
                    // do not save what's not modified
                    if (d.isModified()) {
                        File jf = FileUtil.toFile(f);
                        DocumentSaver saver = new DocumentSaver(d, jf.toPath(), saveAs);
                        saver.save();
                        documentsSaved++;
                        sessionMgr.attachFile(d, f);
                    }
                    saved.add(d);
                }
            }
        }

        documentOrder.removeAll(saved);
        sessionData.keySet().removeAll(saved);
    }

    public GraphDocument firstDocumentToSave() {
        if (documentOrder.isEmpty()) {
            return null;
        }
        return documentOrder.get(0);
    }

    private void registerDocumentToSave(GraphDocument toSave, GraphDocument origin) {
        documentsToSave.add(toSave);
        documentOrigins.put(toSave, origin);
    }

    /**
     * Will be called from EDT and from a worker thread. The lock should ensure
     * proper write-read ordering between preparation in EDT and work in
     * background.
     */
    private synchronized void doPrepare() {
        if (prepared) {
            return;
        }
        for (Folder f : folders) {
            GraphDocument gd = f.getOwner();
            if (gd == null && (f instanceof GraphDocument)) {
                gd = (GraphDocument) f;
                selectedSessions.add(gd);
            } else {
                nonSessionsSelected = true;
            }
            sessionData.computeIfAbsent(gd, (d) -> new ArrayList<>()).add(f);
        }
        for (List<Folder> v : sessionData.values()) {
            retainJustParents(v);
        }
        for (GraphDocument gd : sessionData.keySet()) {
            if (gd instanceof Lookup.Provider) {
                Lookup.Provider lp = (Lookup.Provider) gd;
                FileObject storage = lp.getLookup().lookup(FileObject.class);
                files.put(gd, storage);
            }
        }

        // order the documents according to the SessionManager's order.
        documentOrder.addAll(sessionMgr.getSessions());
        documentOrder.retainAll(sessionData.keySet());
        if (!sessionData.isEmpty()) {
            sessionMgr.getSessionName(sessionData.keySet().iterator().next(), false);
        }
        prepared = true;
    }

    public boolean hasFile(GraphDocument gd) {
        return files.containsKey(gd);
    }

    public Set<GraphDocument> getSessions() {
        return new HashSet<>(sessionData.keySet());
    }

    public FileObject getFile(GraphDocument d) {
        if (d == null) {
            return null;
        }
        return files.get(d);
    }


    public void prepare() {
        doPrepare();
    }

    public void execute() {
        doPrepare();

        switch (saveStyle) {
            case GROUPS:
                createFlatDocument();
                break;
            case SESSIONS:
                createStructuredDocument();
                break;
            case FILES:
                cloneSessions();
        }

        boolean first = true;
        Path pathToSave = userPath;
        try {
            for (GraphDocument d : documentsToSave) {
                lastPath = pathToSave;
                if (first) {
                    setLabel(d, userTitle);
                    first = false;
                } else {
                    GraphDocument org = documentOrigins.get(d);
                    FileObject curFile = getFile(org);
                    pathToSave = null;
                    Path lp = lastPath;
                    if (curFile == null) {
                        // the original document has no file associated yet, suggest the document's own name, probably a better
                        // suggestion as something incremental.
                        lp = Paths.get(SessionManagerImpl.getInstance().getSessionName(org, false));
                        // ensure the path ends with BGV
                        Path parent = lastPath == null ? null : lastPath.getParent();
                        pathToSave = getUsableFileName(
                                parent == null ? lp : parent.resolve(lp), true);
                    }
                    // shortcut, the increment does not care about 
                    if (pathProvider instanceof IncrementPathProvider) {
                        if (pathToSave == null) {
                            pathToSave = pathProvider.createPath(lp, d);
                        }
                    } else {
                        if (pathToSave == null) {
                            IncrementPathProvider ipp = new IncrementPathProvider();
                            pathToSave = ipp.createPath(lastPath, d);
                        }
                        pathToSave = pathProvider.createPath(pathToSave, d);
                    }
                }
                if (pathToSave == null) {
                    return;
                }
                boolean shouldPrompt = true;

                FileObject f = getFile(d); // will be null for cloned or newly created documents.
                if (f != null && pathToSave.toFile().equals(FileUtil.toFile(f))) {
                    // the document is associated with the same file and we do NOT do save-as
                    shouldPrompt = saveAs;
                }

                DocumentSaver saver = new DocumentSaver(d, pathToSave, shouldPrompt);
                saver.save();
                documentsSaved++;
                if (selectedSessions.contains(d)) {
                    // entire document was saved to a file: associate the GraphDocument
                    // with the new storage.
                    FileObject fo = FileUtil.toFileObject(pathToSave.toFile());
                    sessionMgr.attachFile(d, fo);
                }
            }
        } catch (CancellationException ex) {
            // the user has terminated the operation sequence.
        }
    }

    private void cloneSessions() {
        for (GraphDocument origin : sessionData.keySet()) {
            GraphDocument nd = new GraphDocument();
            nd.getProperties().add(origin.getProperties());

            List<Folder> groups = sessionData.get(origin);
            if (groups.get(0) == origin) {
                nd = origin;
            } else {
                for (Folder f : groups) {
                    nd.addElement(f);
                }
            }
            registerDocumentToSave(nd, origin);
        }
    }

    void setLabel(Folder f, String label) {
        if (label == null || label.isEmpty()) {
            return;
        }
        if (!(f instanceof Properties.MutableOwner)) {
            return;
        }
        Properties.MutableOwner mo = (Properties.MutableOwner) f;
        Properties p = mo.writableProperties();
        p.setProperty(KnownPropertyNames.PROPNAME_USER_LABEL, label);
        mo.updateProperties(p);
    }

    private void createFlatDocument() {
        GraphDocument nd = new GraphDocument();
        setLabel(nd, userTitle);
        if (!hasMultipleSessions()) {
            GraphDocument singleDoc = sessionData.keySet().iterator().next();
            if (savingEntireDocument(singleDoc)) {
                registerDocumentToSave(singleDoc, singleDoc);
                return;
            }
        }
        for (GraphDocument doc : documentOrder) {
            for (Folder g : sessionData.get(doc)) {
                if (g instanceof GraphDocument) {
                    GraphDocument gd = (GraphDocument) g;
                    g.getElements().forEach(item -> {
                        if (item instanceof Folder) {
                            nd.addElement(item);
                        }
                    });
                } else {
                    nd.addElement(g);
                }
            }
        }
        if (!hasMultipleSessions()) {
            GraphDocument original = sessionData.keySet().iterator().next();
            nd.getProperties().add(original.getProperties());
            registerDocumentToSave(nd, original);
        } else {
            registerDocumentToSave(nd, nd);
        }
    }

    private void createStructuredDocument() {
        boolean first = true;
        GraphDocument nd = new GraphDocument();
        for (GraphDocument origin : documentOrder) {
            List<Folder> groups = sessionData.get(origin);
            if (groups.size() == 1 && groups.get(0) == origin) {
                groups = new ArrayList<>();
                for (FolderElement fe : origin.getElements()) {
                    if (fe instanceof Folder) {
                        groups.add((Folder) fe);
                    }
                }
            }
            Group container = new Group(nd);
            nd.addElement(container);
            container.getProperties().add(origin.getProperties());
            container.addElements(groups);
            setLabel(container, sessionMgr.getSessionName(origin, true));
            if (first) {
                first = false;
                nd.getProperties().add(container.getProperties());
            }
        }
        setLabel(nd, userTitle);
        registerDocumentToSave(nd, nd);
    }

    @NbBundle.Messages({
            "# {0} - filename",
            "TEXT_FileExistsOverwrite=File {0} already exists. Overwrite ?",
            "TITLE_FileExistsOverwrite=Confirm file overwrite",
            "# {0} - root groups count",
            "# {1} - first group name",
            "MSG_Saving=Saving {0,choice,1#|1<{0}} group{0,choice,1#|1<s}{0,choice,1#: {1}|1<.}",
            "MSG_SavingEmpty=Saving empty document",
            "# {0} - error description",
            "ERR_Save=Error during save: {0}"
    })
    private static class DocumentSaver {
        private final GraphDocument doc;
        private final Path toFile;
        private final boolean toNewFile;

        public DocumentSaver(GraphDocument doc, Path toFile, boolean toNewFile) {
            this.doc = doc;
            this.toFile = toFile;
            this.toNewFile = toNewFile;
        }

        public void save() {
            try {
                boolean exists = Files.exists(toFile);
                if (exists) {
                    if (toNewFile) {
                        DialogDescriptor.Confirmation dd = new DialogDescriptor.Confirmation(
                                Bundle.TEXT_FileExistsOverwrite(toFile.toString()),
                                Bundle.TITLE_FileExistsOverwrite(),
                                DialogDescriptor.YES_NO_CANCEL_OPTION
                        );
                        Object outcome = DialogDisplayer.getDefault().notify(dd);
                        if (outcome != DialogDescriptor.YES_OPTION) {

                            if (outcome != DialogDescriptor.NO_OPTION) {
                                // just a way how to get out from the whole multi-save
                                // process.
                                throw new CancellationException();
                            } else {
                                return;
                            }
                        }
                    }
                }
                Path target = toFile;

                try {
                    if (exists) {
                        Path fn = toFile.getFileName();
                        Path parent = toFile.getParent();
                        assert fn != null;
                        assert parent != null;
                        target = Files.createTempFile(parent, "temp_", fn.toString());
                    }
                    performSave(target.toFile());
                    if (toFile != target) {
                        Files.move(target, toFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    }
                } finally {
                    if (target != toFile) {
                        Files.deleteIfExists(target);
                    }
                }
            } catch (InterruptedIOException ex) {
                // no op            }
            } catch (IOException ex) {
                DialogDisplayer.getDefault().notifyLater(new NotifyDescriptor.Message(
                        Bundle.ERR_Save(ex.toString()),
                        NotifyDescriptor.ERROR_MESSAGE));
            }
        }

        void performSave(final File f) throws IOException {
            AtomicBoolean cHandle = new AtomicBoolean();
            List<? extends FolderElement> allItems = doc.getElements();
            ProgressBridge bridge = new ProgressBridge(allItems, cHandle);
            String msg = allItems.isEmpty() ?
                    Bundle.MSG_SavingEmpty() :
                    Bundle.MSG_Saving(allItems.size(), allItems.get(0).getName());
            ProgressHandle handle = ProgressHandle.createHandle(msg, bridge);

            AtomicReference<IOException> reportException = new AtomicReference<>();
            BaseProgressUtils.showProgressDialogAndRun(
                    new CancellableRunnable() {
                        @Override
                        public void run() {
                            bridge.setHandle(handle);
                            try {
                                DataBinaryWriter.export(f, doc, bridge, cHandle);
                            } catch (IOException e) {
                                reportException.set(e);
                            } catch (CancellationException e) {
                                reportException.set(new InterruptedIOException());
                                if (cHandle.get()) {
                                    f.delete();
                                } else {
                                    DialogDisplayer.getDefault().notifyLater(new NotifyDescriptor.Message(
                                            Bundle.ERR_Save(e.toString()),
                                            NotifyDescriptor.ERROR_MESSAGE));
                                }
                            }
                        }

                        @Override
                        public boolean cancel() {
                            return bridge.cancel();
                        }
                    }, handle, true);
            if (reportException.get() != null) {
                throw reportException.get();
            }
        }
    }

    public interface CancellableRunnable extends Runnable, Cancellable {
    }

    private static final int PROGRESS_THRESHOLD = 500;

    @NbBundle.Messages({
            "# {0} - item number",
            "# {1} - total items",
            "# {2} - group name",
            "# {3} - spacing",
            "PROGRESS_SaveGroup={3}Saving group {0} of {1}: {2}",
            "# {0} - item number",
            "# {1} - total items",
            "# {2} - graph name",
            "# {3} - spacing",
            "PROGRESS_SaveGraph={3}Saving graph {0} of {1}: {2}",
            "PROGRESS_CountingGraphs=Counting graphs and finalizing partial data."
    })
    private static class ProgressBridge implements Consumer<FolderElement>, Cancellable {
        private ProgressHandle handle;
        private final Collection<? extends FolderElement> allItems;
        private int graphsCount;
        private final AtomicBoolean cancelHandle;
        private int counter;
        private final Deque<Group> parentGroups = new ArrayDeque<>();
        private long lastTimeRepored;

        public ProgressBridge(Collection<? extends FolderElement> allItems, AtomicBoolean cancelHandle) {
            this.allItems = allItems;
            this.cancelHandle = cancelHandle;
        }

        void setHandle(ProgressHandle handle) {
            this.handle = handle;
            handle.setDisplayName(Bundle.PROGRESS_CountingGraphs());
            handle.start();
            graphsCount = countGraphs();
            handle.switchToDeterminate(graphsCount);
        }

        @Override
        public void accept(FolderElement t) {
            if (handle == null) {
                return;
            }
            ++counter;
            long now = System.currentTimeMillis();
            // do not report everything, may slow down the process...
            if (lastTimeRepored + PROGRESS_THRESHOLD < now) {
                lastTimeRepored = now;
                handle.progress(makeInfo((InputGraph) t), counter);
                handle.setDisplayName(Bundle.PROGRESS_SaveGraph(counter, graphsCount, t.getName(), ""));
            }
        }

        @Override
        public boolean cancel() {
            cancelHandle.set(true);
            return true;
        }

        public AtomicBoolean getCancelHandle() {
            return cancelHandle;
        }

        public int countGraphs() {
            int count = 0;
            Queue<FolderElement> toProcess = new ArrayDeque<>(allItems);
            FolderElement el;
            Group g;
            while (!toProcess.isEmpty()) {
                el = toProcess.poll();
                if (el instanceof Group) {
                    g = (Group) el;
                    count += g.getGraphsCount();
                    toProcess.addAll(g.getElements());
                }
            }
            return count;
        }

        private String makeInfo(InputGraph g) {
            Folder f = g.getParent();
            while (!(f instanceof GraphDocument)) {
                parentGroups.push((Group) f);
                f = parentGroups.peek().getParent();
            }
            StringBuilder sb = new StringBuilder("<html><nobr>");
            String spacing = "";
            Group actual = parentGroups.peek();
            assert actual != null;
            List<? extends FolderElement> elms;
            while (!parentGroups.isEmpty()) {
                actual = parentGroups.pop();
                elms = actual.getParent().getElements();
                sb.append(Bundle.PROGRESS_SaveGroup(elms.indexOf(actual) + 1, elms.size(), actual.getName(), spacing)).append("<br>");
                spacing += "&emsp;";
            }
            elms = actual.getElements();
            sb.append(Bundle.PROGRESS_SaveGraph(elms.indexOf(g) + 1, elms.size(), g.getName(), spacing));
            sb.append("</nobr></html>");
            return sb.toString();
        }
    }

    File getUsableFileName(File file) {
        return getUsableFileName(file.toPath(), true).toFile();
    }

    Path getUsableFileName(Path path, boolean mustNotExist) {
        IncrementPathProvider provider = new IncrementPathProvider();
        provider.initFilename(path);

        Path p = provider.getInitialFile();
        if (!mustNotExist) {
            return p;
        }

        while (Files.exists(p)) {
            p = provider.createPath(null, null);
        }
        return p;
    }

    final class IncrementPathProvider implements DocumentPathProvider {
        private int counter;
        private String baseFilename;
        private String initialBasename;
        private String initialFilename;
        private Path folder;

        public IncrementPathProvider() {
        }

        public Path getCurrentPath() {
            return folder.resolve(Paths.get(initialFilename));
        }

        void initFilename(Path suggested) {
            if (suggested == null) {
                suggested = lastPath;
            }
            folder = suggested.getParent();
            if (folder == null) {
                folder = Paths.get("");
            }
            Path p = suggested.getFileName();
            assert p != null;
            String s = p.toString();
            boolean digitsFound = false;

            if (s.toLowerCase(Locale.ENGLISH).endsWith(".bgv")) {
                s = s.substring(0, s.length() - 4);
                initialBasename = s;
            } else {
                initialBasename = s;

            }
            initialFilename = initialBasename + ".bgv";

            int index = s.length();
            while (index > 0) {
                char c = s.charAt(index - 1);
                if (!Character.isDigit(c)) {
                    break;
                }
                index--;
                digitsFound = true;
            }
            if (digitsFound) {
                counter = Integer.parseInt(s.substring(index));
            } else {
                counter = 0;
            }
            if (index > 1 && s.charAt(index - 1) == '-') {
                index--;
            }
            baseFilename = s.substring(0, index);
        }

        Path getInitialFile() {
            return folder.resolve(initialFilename);
        }

        @Override
        public Path createPath(Path suggestedPath, GraphDocument document) {
            Path fn = suggestedPath == null ? null : suggestedPath.getFileName();
            if (baseFilename == null ||
                    (fn != null && !fn.toString().startsWith(initialFilename))) {
                initFilename(suggestedPath);
            }
            if (document != null) {
                setLabel(document, userTitle);
            }
            return folder.resolve(baseFilename + "-" + (++counter) + ".bgv");
        }
    }
}
