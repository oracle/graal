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

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.*;

import org.graalvm.visualizer.coordinator.actions.RemoveCookie;
import org.graalvm.visualizer.data.serialization.lazy.ReaderErrors;
import org.graalvm.visualizer.util.ListenerSupport;
import org.graalvm.visualizer.util.PropertiesSheet;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.awt.StatusDisplayer;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.Cancellable;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

import jdk.graal.compiler.graphio.parsing.model.*;
import jdk.graal.compiler.graphio.parsing.model.Group.LazyContent;

public class FolderNode extends AbstractOutlineNode {
    /**
     * Configurable timeout for progress indicator, in milliseconds.
     */
    private static final int PROGRESS_TIMEOUT = Integer.getInteger(FolderNode.class.getName() + ".progressTimeout", 5000);  // NOI18N

    /**
     * Time threshold to pool changes before the folder children are refreshed,
     * in milliseconds.
     */
    private static final int POOL_THRESHOLD = Integer.getInteger(FolderNode.class.getName() + ".poolThreshold", 200);  // NOI18N

    private static final RequestProcessor REFRESH_RP = new RequestProcessor(FolderNode.class);
    private final InstanceContent content;
    protected final Folder folder;
    private boolean error;

    private final ChangedListener<FolderElement> l = new ChangedListener<>() {
        @Override
        public void changed(FolderElement source) {
            SwingUtilities.invokeLater(FolderNode.this::refreshError);
        }
    };

    /**
     * Marker value for "please wait" node
     */
    @SuppressWarnings("RedundantStringConstructorCall")
    private static final FolderElement WAIT_KEY = new FolderElement() {
        private final ChangedEvent<FolderElement> silentEvent = new ChangedEvent<FolderElement>(this) {
            @Override
            public void removeListener(ChangedListener<FolderElement> l) {
            }

            @Override
            public void addListener(ChangedListener<FolderElement> l) {
            }
        };

        @Override
        public Folder getParent() {
            return null;
        }

        @Override
        public String getName() {
            return "(wait)";
        }

        @Override
        public void setParent(Folder parent) {
        }

        @Override
        public Object getID() {
            return this;
        }
    };

    static class FolderChildren extends Children.Keys<FolderElement> implements ChangedListener<Folder> {

        private final Folder folder;
        private ChangedListener l;
        // delay refreshing UI for ~200ms to batch changes.
        private RequestProcessor.Task refreshTask;
        /**
         * Set to true, if the "Please wait..." feedback expires
         */
        private boolean feedbackGone;

        public FolderChildren(Folder folder) {
            this.folder = folder;
        }

        @Override
        protected Node[] createNodes(FolderElement e) {
            Node[] ret = new Node[1];
            Node n;

            if (e == WAIT_KEY) {
                n = new WaitNode();
            } else if (e instanceof InputGraph && GraphClassifier.DEFAULT_CLASSIFIER.knownGraphTypes().contains(((InputGraph) e).getGraphType())) {
                n = new GraphNode((InputGraph) e);
            } else if (e instanceof Folder) {
                n = new FolderNode((Folder) e);
            } else {
                return null;
            }
            ret[0] = n;
            return ret;
        }

        @Override
        public void addNotify() {
            this.l = ListenerSupport.addWeakListener(this, folder.getChangedEvent());
            refreshKeys();
        }

        @Override
        protected void removeNotify() {
            if (l != null) {
                folder.getChangedEvent().removeListener(l);
            }
            setKeys(Collections.emptyList());
            super.removeNotify();
        }

        @Override
        public void changed(Folder source) {
            synchronized (this) {
                if (refreshTask == null) {
                    // delay first refresh for 200ms
                    refreshTask = REFRESH_RP.post(this::refreshKeys, POOL_THRESHOLD);
                }
            }
        }

        @NbBundle.Messages({
                "# {0} - name of the loaded folder",
                "MSG_Loading=Loading contents of {0}",
                "# {0} - name of the loaded folder",
                "MSG_ExpansionFailed=Expansion of {0} failed, please see log for possible error.",
                "# {0} - name of the loaded folder",
                "MSG_ExpansionCancelled=Expansion of {0} cancelled"
        })
        class Feedback implements Group.Feedback, Cancellable, Runnable {
            final AtomicBoolean cancelled = new AtomicBoolean();
            ProgressHandle handle;
            Future f;
            boolean indeterminate;
            RequestProcessor.Task cancelIndeterminate = REFRESH_RP.create(this, true);
            boolean removed;
            int lastTotal;

            String name() {
                return folder.getName();
            }

            void setFuture(Future f) {
                this.f = f;
            }

            @Override
            public void run() {
                synchronized (this) {
                    if (indeterminate) {
                        init(1);
                        handle.finish();
                        removed = true;
                    } else {
                        return;
                    }
                }
                synchronized (FolderChildren.this) {
                    feedbackGone = true;
                }
                FolderChildren.this.refreshKeys();
            }

            private void init(int total) {
                if (removed) {
                    return;
                }
                if (handle == null) {
                    handle = ProgressHandle.createHandle(Bundle.MSG_Loading(name()), this);
                    if (total > 0) {
                        handle.start(total);
                    } else {
                        handle.start();
                        handle.switchToIndeterminate();
                        indeterminate = true;
                    }
                    lastTotal = total;
                } else if (indeterminate && total > 0) {
                    handle.switchToDeterminate(total);
                    lastTotal = total;
                } else if (lastTotal < total) {
                    handle.switchToDeterminate(total);
                    lastTotal = total;
                }
            }

            @Override
            public void reportProgress(int workDone, int totalWork, String description) {
                synchronized (this) {
                    init(totalWork);
                    if (removed) {
                        return;
                    }
                }
                if (description != null) {
                    if (totalWork > 0) {
                        handle.progress(description, Math.min(lastTotal, workDone));
                    } else {
                        handle.progress(description);
                    }
                } else if (totalWork > 0) {
                    handle.progress(Math.min(lastTotal, workDone));
                }
                if (indeterminate) {
                    // cancel indeterminate (for unfinished entries) progress after some time, so it does not obscur
                    cancelIndeterminate.schedule(PROGRESS_TIMEOUT);
                }
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }

            @Override
            public boolean cancel() {
                f.cancel(true);
                return !cancelled.getAndSet(true);
            }

            @Override
            public void finish() {
                synchronized (this) {
                    if (removed) {
                        return;
                    }
                    init(1);
                    handle.finish();
                }
                // same sync as in refreshKeys
                synchronized (FolderChildren.this) {
                    if (!f.isDone()) {
                        StatusDisplayer.getDefault().setStatusText(Bundle.MSG_ExpansionFailed(name()), StatusDisplayer.IMPORTANCE_ANNOTATION);
                    } else if (f.isCancelled()) {
                        StatusDisplayer.getDefault().setStatusText(Bundle.MSG_ExpansionCancelled(name()), StatusDisplayer.IMPORTANCE_ANNOTATION);
                    }
                }
            }

            @Override
            public void reportError(List<FolderElement> parents, List<String> parentNames, String name, String errorMessage) {
                SessionManagerImpl.reportLoadingError(parents, parentNames, name, errorMessage);
            }
        }

        private synchronized void refreshKeys() {
            refreshTask = null;
            List<FolderElement> elements;
            if (folder instanceof Group.LazyContent) {
                LazyContent<List<? extends FolderElement>> lazyFolder = (LazyContent) folder;
                Feedback feedback = new Feedback();
                Future<List<? extends FolderElement>> fContents = lazyFolder.completeContents(feedback);
                if (!fContents.isDone()) {
                    feedback.setFuture(fContents);
                    elements = new ArrayList<>(lazyFolder.partialData());
                    if (!feedbackGone) {
                        elements.add(WAIT_KEY);
                    }
                } else {
                    try {
                        elements = (List) fContents.get();
                    } catch (InterruptedException | ExecutionException | CancellationException ex) {
                        // ignore, reported elsewhere
                        return;
                    }
                }
            } else {
                elements = (List) folder.getElements();
            }
            this.setKeys(elements);
        }
    }

    @NbBundle.Messages({
            "TITLE_Versions=Versions"
    })
    @Override
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        if (folder instanceof Properties.Entity) {
            Properties.Entity pen = (Properties.Entity) folder;
            Sheet.Set props = Sheet.createPropertiesSet();
            Sheet.Set versions = new Sheet.Set();
            versions.setName("versions"); // NOI18N
            versions.setDisplayName(Bundle.TITLE_Versions());
            List<jdk.graal.compiler.graphio.parsing.model.Property<?>> versionProps = new ArrayList<>();
            pen.getProperties().forEach((p) -> {
                if (p.getName().startsWith("igv.")) { // NOI18N
                    return;
                }
                if (p.getName().startsWith(PREFIX_VERSION)) {
                    versionProps.add(p);
                } else {
                    props.put(PropertiesSheet.createSheetProperty(p.getName(), pen.getProperties()));
                }
            });

            s.put(props);
            if (!versionProps.isEmpty()) {
                Collections.sort(versionProps, (a, b) -> a.getName().compareTo(b.getName()));
                for (jdk.graal.compiler.graphio.parsing.model.Property<?> p : versionProps) {
                    versions.put(PropertiesSheet.createSheetProperty(p.getName(),
                            p.getName().substring(PREFIX_VERSION.length()), pen.getProperties()));
                }
                versions.setExpert(true);
                s.put(versions);
            }
        }
        return s;
    }

    private static final String PREFIX_VERSION = "version.";

    @Override
    public Image getIcon(int i) {
        if (error) {
            return ImageUtilities.mergeImages(
                    super.getIcon(i),
                    ImageUtilities.loadImage("org/graalvm/visualizer/coordinator/images/error-glyph.gif"), // NOI18N
                    10, 6
            );
        } else {
            return super.getIcon(i);
        }
    }

    public FolderNode(Folder folder) {
        this(folder, new FolderChildren(folder), new InstanceContent());
    }

    public FolderNode(Folder folder, Children ch) {
        this(folder, ch, new InstanceContent());
    }

    private FolderNode(final Folder folder, Children children, InstanceContent content) {
        super(folder, children, new AbstractLookup(content));
        this.folder = folder;
        this.content = content;
        if (folder != null) {
            final FolderElement folderElement = folder;
            content.add(new RemoveCookie() {
                @Override
                public void remove() {
                    if (folderElement instanceof GraphDocument) {
                        SessionManagerImpl.getInstance().removeElement(folderElement);
                    } else {
                        folderElement.getParent().removeElement(folderElement);
                    }
                }
            });

            content.add(folder);
        }
        if (folder instanceof ChangedEventProvider) {
            ChangedEvent<FolderElement> ev = ((ChangedEventProvider) folder).getChangedEvent();
            ListenerSupport.addWeakListener(l, ev);
        }
        setIconBaseWithExtension("org/graalvm/visualizer/coordinator/images/folder.png");
        refreshError();
    }

    private void refreshError() {
        if (!(folder instanceof FolderElement)) {
            return;
        }
        FolderElement el = folder;
        boolean newError = ReaderErrors.containsError(el, true);
        if (this.error != newError) {
            this.error = newError;
            fireIconChange();
        }
    }

    @Override
    public Image getOpenedIcon(int i) {
        return getIcon(i);
    }

    @NbBundle.Messages({
            "TITLE_PleaseWait=Please wait, loading data..."
    })
    static class WaitNode extends AbstractNode {
        public WaitNode() {
            super(Children.LEAF);
            setDisplayName(Bundle.TITLE_PleaseWait());
            setIconBaseWithExtension("org/graalvm/visualizer/coordinator/images/wait.gif"); // NOI18N
        }
    }
}
