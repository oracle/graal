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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.graalvm.visualizer.connection.Server;
import org.graalvm.visualizer.settings.graal.GraalSettings;
import org.netbeans.api.io.IOProvider;
import org.netbeans.api.io.InputOutput;
import org.openide.filesystems.FileObject;
import org.openide.modules.OnStart;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakSet;

import jdk.graal.compiler.graphio.parsing.DocumentFactory;
import jdk.graal.compiler.graphio.parsing.ParseMonitor;
import jdk.graal.compiler.graphio.parsing.model.*;

/**
 * Currently the class holds the one and only Document in IGV. It should evolve
 * into a session manager to separate individual Graal runs into separate Documents.
 *
 * @author sdedic
 */
@OnStart
public class SessionManagerImpl implements Folder, Runnable, DocumentFactory {
    private static SessionManagerImpl INSTANCE;

    private final GraphDocument singleDocument = new ManagedSessionImpl(null);

    private Server binaryServer;

    private final List<GraphDocument> sessions = new ArrayList<>();

    private final ChangedEvent<SessionManagerImpl> changedEvent = new ChangedEvent<>(this);

    /**
     * Documents closed forever. Current documents are added to the closed set if
     * all network connections are closed.
     */
    private final Set<GraphDocument> closedDocuments = new WeakSet<>();

    private volatile boolean separateSessions;

    @NbBundle.Messages({
            "LABEL_DefaultDocumentName=Unknown Dump"
    })
    public SessionManagerImpl() {
        INSTANCE = this;
        separateSessions = GraalSettings.obtain().get(Boolean.class, GraalSettings.AUTO_SEPARATE_SESSIONS);
        singleDocument.getProperties().setProperty(KnownPropertyNames.PROPNAME_NAME, Bundle.LABEL_DefaultDocumentName());

    }

    GraphDocument getSingleDocument() {
        return singleDocument;
    }

    @Override
    public Object getID() {
        return "<session-manager>"; // NOI18N
    }

    @Override
    public List<? extends FolderElement> getElements() {
        return getSessions();
    }

    @Override
    public void removeElement(FolderElement element) {
        if (!(element instanceof GraphDocument)) {
            throw new IllegalArgumentException();
        }
        GraphDocument gd = (GraphDocument) element;
        synchronized (this) {
            if (!sessions.remove(gd)) {
                return;
            }
            closedDocuments.remove(gd);
        }
        changedEvent.fire();
    }

    @Override
    public void addElement(FolderElement group) {
        if (!(group instanceof GraphDocument)) {
            throw new IllegalArgumentException();
        }
        addSession((GraphDocument) group);
    }

    @Override
    public ChangedEvent<SessionManagerImpl> getChangedEvent() {
        return changedEvent;
    }

    @Override
    public boolean isParentOf(FolderElement child) {
        GraphDocument owner = child.getOwner();
        synchronized (this) {
            return sessions.contains(owner);
        }
    }

    public List<GraphDocument> getSessions() {
        synchronized (this) {
            return new ArrayList<>(sessions);
        }
    }

    public List<GraphDocument> getAppendableSessions() {
        List<GraphDocument> s = getSessions();
        s.removeAll(getUnappendableDocuments());
        return s;
    }

    private static FileObject getAssociatedFile(GraphDocument gd) {
        if (!(gd instanceof Lookup.Provider)) {
            return null;
        }
        Lookup.Provider lp = (Lookup.Provider) gd;
        return lp.getLookup().lookup(FileObject.class);
    }

    void addSession(GraphDocument session) {
        synchronized (this) {
            if (sessions.contains(session)) {
                return;
            }
            sessions.add(session);
            if (getAssociatedFile(session) != null) {
                // do not allow appending to a file-based document
                closedDocuments.add(session);
            }
        }
        changedEvent.fire();
    }

    synchronized Collection<GraphDocument> getUnappendableDocuments() {
        return new ArrayList<>(closedDocuments);
    }

    public synchronized void freezeCurrentDocuments() {
        closedDocuments.addAll(getSessions());
    }

    class SImpl extends Server implements Runnable {
        RequestProcessor.Task scheduledSessionClose;

        public SImpl(DocumentFactory rootDocumentFactory, ParseMonitor monitor) {
            super(rootDocumentFactory, monitor);
        }

        @Override
        protected synchronized void onAllClientsClosed() {
            super.onAllClientsClosed();
            if (scheduledSessionClose != null) {
                if (!scheduledSessionClose.cancel()) {
                    return;
                }
            }
            if (separateSessions) {
                scheduledSessionClose = RequestProcessor.getDefault().post(this, GraalSettings.obtain().get(Integer.class, GraalSettings.SESSION_CLOSE_TIMEOUT) * 1000);
            }
        }

        @Override
        protected DocumentFactory onNewClient() {
            synchronized (this) {

                if (scheduledSessionClose != null) {
                    scheduledSessionClose.cancel();
                }
                scheduledSessionClose = null;
            }
            return SessionManagerImpl.this;
        }

        @Override
        public void run() {
            synchronized (this) {
                if (scheduledSessionClose == null) {
                    return;
                }
                scheduledSessionClose = null;
            }
            freezeCurrentDocuments();
        }
    }

    public void run() {
        binaryServer = new SImpl(this, new ParseMonitor() {
            @Override
            public void updateProgress() {
            }

            @Override
            public void setState(String state) {
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public void reportError(List<FolderElement> parents, List<String> parentNames, String name, String errorMessage) {
                reportLoadingError(parents, parentNames, name, errorMessage);
            }
        });
    }

    /**
     * Returns the manager. An interface will be extracted later and placed into Lookup. Do not
     * expose this method outside the module.
     *
     * @return
     */
    public static SessionManagerImpl getInstance() {
        return INSTANCE;
    }

    public GraphDocument getCurrentDocument() {
        boolean a = true;
        boolean b = false;
        a = !b;
        return singleDocument;
    }

    @NbBundle.Messages({
            "WARNING_ErrorDuringLoadSeparator= / ",
            "# {0} - path to the object with error",
            "WARNING_ErrorDuringLoadTitle=Error loading {0}:",
            "# {0} - error message",
            "WARNING_ErrorDuringLoadMessage=    {0}",
            "TITLE_ErrorLoadingData=Loading errors"
    })
    public static void reportLoadingError(List<FolderElement> parents, List<String> parentNames, String name, String errorMessage) {
        if (parentNames == null) {
            // in the case that Feedback was used to report progress
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parentNames.size(); i++) {
            if (sb.length() > 0) {
                sb.append(Bundle.WARNING_ErrorDuringLoadSeparator());
            }
            FolderElement p = null;
            if (i < parents.size()) {
                p = parents.get(i);
            }
            if (p != null) {
                sb.append(p.getName());
            } else {
                sb.append(parentNames.get(i));
            }
        }
        if (sb.length() > 0) {
            sb.append(Bundle.WARNING_ErrorDuringLoadSeparator());
            sb.append(name);
        }
        InputOutput io = IOProvider.getDefault().getIO(Bundle.TITLE_ErrorLoadingData(), false);
        io.show();
        PrintWriter pw = new PrintWriter(io.getErr());
        pw.println(Bundle.WARNING_ErrorDuringLoadTitle(sb.toString()));
        pw.println(Bundle.WARNING_ErrorDuringLoadMessage(errorMessage));
        pw.flush();
    }

    private List<DocumentFactory> factories;

    @Override
    public GraphDocument documentFor(Object id, Properties props, Group g) {
        synchronized (this) {
            if (factories == null) {
                factories = new ArrayList<>(Lookup.getDefault().lookupAll(DocumentFactory.class));
            }
        }
        for (DocumentFactory f : factories) {
            GraphDocument gd = f.documentFor(id, props, g);
            if (gd != null) {
                return gd;
            }
        }
        GraphDocument doc = getSingleDocument();
        if (doc != null) {
            addSession(doc);
        }
        return doc;
    }

    @Override
    public Folder getParent() {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public void setParent(Folder parent) {
    }

    @NbBundle.Messages({
            "# {0} - user-defined label or name",
            "# {1} - filename",
            "FMT_SessionLabelFile={0} (from {1})",
            "# {0} - user-defined label or name",
            "# {1} - filename",
            "FMT_SessionFileName={1} ({0})",
            "# {0} - some unique string",
            "FMT_SessionDumpName=Dump {0}",
            "# {0} - some unique string",
            "# {1} - timestamp",
            "FMT_SessionSynthetic={0} ({1})",
    })
    public String getSessionDisplayName(GraphDocument doc) {
        return getSessionDisplayName(doc, true);
    }

    public String getSessionDisplayName(GraphDocument doc, boolean annotate) {
        if (doc == null) {
            return null;
        }
        FileObject f = getAssociatedFile(doc);
        Properties docProperties = doc.getProperties();
        String name = docProperties.getString(KnownPropertyNames.PROPNAME_NAME, null);
        if (name != null && name.isEmpty()) {
            name = null;
        }
        String label = docProperties.getString(KnownPropertyNames.PROPNAME_USER_LABEL, null);

        if (name == null) {
            String id = docProperties.getString(KnownPropertyNames.PROPNAME_VM_UUID, null);
            if (id == null) {
                // if there's a compilation Id, do not add the "Dump " label.
                String compilationId = docProperties.getString("truffle.compilation.id", null);
                if (compilationId == null) {
                    compilationId = docProperties.getString("compilationId", null);
                }
                if (compilationId != null) {
                    name = compilationId;
                } else {
                    name = Bundle.FMT_SessionDumpName(Integer.toHexString(System.identityHashCode(doc)));
                }
            } else {
                name = Bundle.FMT_SessionDumpName(id);
            }
        }
        if (!annotate) {
            return label != null ? label : name;
        }
        if (f != null) {
            if (label == null) {
                if (f.getName().equals(name)) {
                    return f.getNameExt();
                }
                return Bundle.FMT_SessionFileName(name, f.getNameExt());
            } else {
                return Bundle.FMT_SessionLabelFile(label, f.getNameExt());
            }
        } else {
            if (label != null) {
                return label;
            }
            String dateTime = docProperties.getString("date", null); // NOI18N
            if (dateTime == null) {
                return name;
            } else {
                return Bundle.FMT_SessionSynthetic(name, dateTime);
            }
        }
    }

    public String getSessionName(GraphDocument doc, boolean fillDefault) {
        String name = doc.getName();
        String label = doc.getProperties().getString(KnownPropertyNames.PROPNAME_USER_LABEL, null);
        if (label != null || fillDefault) {
            return label;
        }
        return name;
    }

    public void attachFile(GraphDocument doc, FileObject file) {
        if (!(doc instanceof ManagedSessionImpl)) {
            throw new IllegalArgumentException();
        }

        ManagedSessionImpl session = (ManagedSessionImpl) doc;
        session.setSaveAs(file);
    }

    /**
     * Automatically separate sessions.
     *
     * @return true, if sessions should be separated atuomatically
     */
    public boolean isSeparateSessions() {
        return separateSessions;
    }

    public void setSeparateSessions(boolean separateSessions) {
        this.separateSessions = separateSessions;
    }
}
