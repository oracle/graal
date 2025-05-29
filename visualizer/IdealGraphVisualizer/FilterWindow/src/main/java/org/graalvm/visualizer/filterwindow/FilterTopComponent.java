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
package org.graalvm.visualizer.filterwindow;

import org.graalvm.visualizer.filter.Filter;
import org.graalvm.visualizer.filter.profiles.FilterProfile;
import org.graalvm.visualizer.filter.profiles.mgmt.ProfileService;
import org.graalvm.visualizer.filter.profiles.mgmt.ProfileStorage;
import org.graalvm.visualizer.filterwindow.actions.LinkExistingFilter;
import org.graalvm.visualizer.filterwindow.actions.ManageProfileAction;
import org.graalvm.visualizer.filterwindow.actions.MoveFilterAction;
import org.graalvm.visualizer.filterwindow.actions.NewFilterAction;
import org.graalvm.visualizer.filterwindow.actions.RemoveFilterAction;
import org.graalvm.visualizer.filterwindow.impl.FilterNode;
import org.graalvm.visualizer.filterwindow.impl.FilterProfileNode;
import org.netbeans.api.actions.Openable;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.ErrorManager;
import org.openide.awt.Actions;
import org.openide.awt.Toolbar;
import org.openide.awt.ToolbarPool;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.explorer.view.ChoiceView;
import org.openide.explorer.view.Visualizer;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.nodes.NodeAdapter;
import org.openide.nodes.NodeMemberEvent;
import org.openide.util.ContextAwareAction;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.openide.util.actions.Presenter;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.ProxyLookup;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

import javax.swing.Action;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class FilterTopComponent extends TopComponent implements ExplorerManager.Provider {

    private static FilterTopComponent instance;
    public static final String FOLDER_ID = "Filters";
    public static final String PREFERRED_ID = "FilterTopComponent";

    private final CheckListView view;
    private final ExplorerManager manager;
    private final ExplorerManager profileEM;
    private final JPanel northPanel;
    private final InstanceContent lookupContent;
    private final ChangeListener muxListener;
    private final ChoiceView profileView;

    private ProfileService rootFilterManager;
    private FilterProfile curProfile;

    /**
     * Workaround for NB Listview selection bug.
     */
    private void updateSelection() {
        Node[] nodes = this.getExplorerManager().getSelectedNodes();
        int[] arr = new int[nodes.length];
        List<Filter> seq = rootFilterManager.getSelectedProfile().getProfileFilters();
        for (int i = 0; i < nodes.length; i++) {
            int index = seq.indexOf(((FilterNode) nodes[i]).getFilter());
            arr[i] = index;
        }
        view.showSelection(arr);
    }

    private void refreshContentLookup() {
        List<Object> l = new ArrayList<>();
        l.add(rootFilterManager);

        for (Node n : manager.getSelectedNodes()) {
            Filter f = n.getLookup().lookup(Filter.class);
            if (f != null) {
                l.add(f);
            }
        }
        Node[] sel = profileEM.getSelectedNodes();
        if (sel.length > 0) {
            FilterProfile p = sel[0].getLookup().lookup(FilterProfile.class);
            if (p != null) {
                l.add(p);
            }
            synchronized (this) {
                curProfile = p;
            }
        }
        lookupContent.set(l, null);

    }

    private FilterTopComponent() {
        initComponents();
        setName(NbBundle.getMessage(FilterTopComponent.class, "CTL_FilterTopComponent"));
        setToolTipText(NbBundle.getMessage(FilterTopComponent.class, "HINT_FilterTopComponent"));
        // setIcon(Utilities.loadImage(ICON_PATH, true));
        lookupContent = new InstanceContent();

        rootFilterManager = Lookup.getDefault().lookup(ProfileService.class);
        curProfile = rootFilterManager.getDefaultProfile();
        manager = new ExplorerManager();
        profileEM = new ExplorerManager();
        profileEM.setRootContext(FilterProfileNode.createProfileParent());
        Node n = findProfileNode(rootFilterManager.getSelectedProfile());
        if (n != null) {
            try {
                profileEM.setSelectedNodes(new Node[]{n});
            } catch (PropertyVetoException ex) {
                Exceptions.printStackTrace(ex);
            }
            manager.setRootContext(n);
        }
        associateLookup(
                new ProxyLookup(
                        ExplorerUtils.createLookup(manager, getActionMap()),
                        new AbstractLookup(lookupContent)
                )
        );
        muxListener = new ChangeListener();
        manager.addPropertyChangeListener(muxListener);

        // maybe should be attached in componentOpened, removed in componentClosed.
        rootFilterManager.addPropertyChangeListener(WeakListeners.propertyChange(muxListener, rootFilterManager));
        view = new CheckListView();

        profileView = new ChoiceView();
        profileView.addActionListener(muxListener);

        // need to separate the choice view to a different EM
        northPanel = new PanelWithEM();
        northPanel.setLayout(new BorderLayout());
        this.add(northPanel, BorderLayout.NORTH);

        ToolbarPool.getDefault().setPreferredIconSize(16);
        Toolbar toolBar = new Toolbar();
        Border b = (Border) UIManager.get("Nb.Editor.Toolbar.border"); // NOI18N
        toolBar.setBorder(b);
        toolBar.add(profileView);
        northPanel.add(toolBar, BorderLayout.CENTER);
        addToolbarAction(toolBar,
                Actions.forID(ManageProfileAction.ID_CATEGORY, ManageProfileAction.ID_ACTION)
        );
        toolBar.addSeparator();
        addToolbarAction(toolBar, Actions.forID(LinkExistingFilter.ID_CATEGORY, LinkExistingFilter.ID_LINK_EXISTING_FILTER));
        toolBar.add(NewFilterAction.get(NewFilterAction.class));
        addToolbarAction(toolBar,
                Actions.forID(RemoveFilterAction.CATEGORY, RemoveFilterAction.ID)
        );
        addToolbarAction(toolBar,
                Actions.forID(MoveFilterAction.CATEGORY, MoveFilterAction.ID_MOVE_DOWN)
        );
        addToolbarAction(toolBar,
                Actions.forID(MoveFilterAction.CATEGORY, MoveFilterAction.ID_MOVE_UP)
        );
        this.add(view, BorderLayout.CENTER);

        refreshContentLookup();

        // workaround for NetBeans listview bug: when items are reordered,
        // internal indices cache is not invalidated on time; selection has to 
        // be fixed in the next swing event after ListDataEvent is dispatched.
        view.getModel().addListDataListener(new ListDataListener() {
            @Override
            public void intervalAdded(ListDataEvent e) {
            }

            @Override
            public void intervalRemoved(ListDataEvent e) {
            }

            @Override
            public void contentsChanged(ListDataEvent e) {
                updateSelection();
            }
        });
    }

    private class ChangeListener implements PropertyChangeListener, ActionListener {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            // selection of filter will add filters and profile to the TC lookup.
            if (evt.getSource() == manager) {
                if (ExplorerManager.PROP_SELECTED_NODES.equals(evt.getPropertyName())) {
                    setActivatedNodes(manager.getSelectedNodes());
                    refreshContentLookup();
                }
            }
            // profile change sets the selected profile
            if (evt.getSource() == profileEM) {
            }
            // change in selected profile changes the view and profile choice.
            if (evt.getSource() == rootFilterManager) {
                if (ProfileService.PROP_SELECTED_PROFILE.equals(evt.getPropertyName())) {
                    Node pn = findProfileNode(rootFilterManager.getSelectedProfile());
                    try {
                        if (pn != null) {
                            profileEM.setSelectedNodes(new Node[]{pn});
                            manager.setRootContext(pn);
                            refreshContentLookup();
                        }
                    } catch (PropertyVetoException ex) {
                        // should not happen
                        Exceptions.printStackTrace(ex);
                    }
                }
            }
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (e.getSource() == profileView) {
                Node[] sel = profileEM.getSelectedNodes();
                if (sel.length != 1) {
                    return;
                }
                FilterProfile choice = sel[0].getLookup().lookup(FilterProfile.class);
                rootFilterManager.setSelectedProfile(choice);
            }
        }

    }

    private Action addToolbarAction(Toolbar toolbar, Action orig) {
        Action a = orig;
        if (a instanceof ContextAwareAction) {
            a = ((ContextAwareAction) orig).createContextAwareInstance(getLookup());
        }

        if (a instanceof Presenter.Toolbar) {
            toolbar.add(((Presenter.Toolbar) a).getToolbarPresenter());
        } else {
            toolbar.add(a);
        }
        return a;
    }

    private FilterProfile getSelectedProfile() {
        return curProfile;
    }

    @NbBundle.Messages({
            "LABEL_NewFilterName=Filter name:",
            "TITLE_CreateNewFilter=Create new Filter",
    })
    public void newFilter() throws IOException {
        // TODO: should use some New from Template wizard to allow template reuse. 
        // But the standard TW would display Filters folder as the target, which is an implementation detail
        DialogDescriptor.InputLine nameInput = new DialogDescriptor.InputLine(Bundle.LABEL_NewFilterName(), Bundle.TITLE_CreateNewFilter(),
                DialogDescriptor.OK_CANCEL_OPTION, DialogDescriptor.QUESTION_MESSAGE);

        if (DialogDisplayer.getDefault().notify(nameInput) != DialogDescriptor.OK_OPTION) {
            return;
        }
        FileObject templateFile = FileUtil.getConfigFile("Templates/Filters/empty.js"); // NOI18N
        DataObject template = DataObject.find(templateFile);
        DataFolder fld = DataFolder.findFolder(FileUtil.getConfigFile(ProfileStorage.DEFAULT_PROFILE_FOLDER));
        DataObject target = template.createFromTemplate(fld, nameInput.getInputText());
        if (target != null) {
            FileObject storage = target.getPrimaryFile();
            // ask the profile service to create the filter ASAP:
            // attempt to find an appropriate node; force a refresh:
            FilterProfile prof = getSelectedProfile();

            Filter f = rootFilterManager.getLookup().lookup(ProfileStorage.class).createFilter(storage, prof);
            if (f == null) {
                return;
            }
            AtomicReference<Filter> linkedFilter = new AtomicReference<>();

            // the nodes MAY be created "later", and the VisualizerNode even one event 
            // later, so need to phase the selection after the visnode is created
            // note that nodes may be also created during addSharedFilter, if executing in EDT,
            // so even the childrenAdded handling is postponed.
            manager.getExploredContext().addNodeListener(new NodeAdapter() {
                @Override
                public void childrenAdded(NodeMemberEvent ev) {
                    SwingUtilities.invokeLater(() -> {
                        for (Node n : manager.getExploredContext().getChildren().getNodes()) {
                            Filter check = n.getLookup().lookup(Filter.class);
                            Filter select = linkedFilter.get();
                            if (check == select) {
                                SwingUtilities.invokeLater(new DelayedSelector(select));
                            }
                        }
                        ((Node) ev.getSource()).removeNodeListener(this);
                    });
                }
            });
            Filter newFilter = prof.addSharedFilter(f);
            linkedFilter.set(newFilter);

            Openable cake = newFilter.getLookup().lookup(Openable.class);
            if (cake != null) {
                cake.open();
            }
        }
    }

    class DelayedSelector implements Runnable {
        private final Filter selectFilter;
        private int attempts;

        public DelayedSelector(Filter selectFilter) {
            this.selectFilter = selectFilter;
        }

        @Override
        public void run() {
            int size = view.getModel().getSize();
            for (int i = 0; i < size; i++) {
                Node n = Visualizer.findNode(view.getModel().getElementAt(i));
                Filter f = n.getLookup().lookup(Filter.class);
                if (f == selectFilter) {
                    try {
                        manager.setSelectedNodes(new Node[]{n});
                        return;
                    } catch (PropertyVetoException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }
            attempts++;
            if (attempts < 5) {
                SwingUtilities.invokeLater(this);
            }
        }
    }

    /**
     * This method is called from within the constructor to initialize the form. WARNING: Do NOT
     * modify this code. The content of this method is always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setLayout(new java.awt.BorderLayout());
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables

    /**
     * Gets default instance. Do not use directly: reserved for *.settings files only, i.e.
     * deserialization routines; otherwise you could get a non-deserialized instance. To obtain the
     * singleton instance, use {@link findInstance}.
     */
    public static synchronized FilterTopComponent getDefault() {
        if (instance == null) {
            instance = new FilterTopComponent();
        }
        return instance;
    }

    /**
     * Obtain the FilterTopComponent instance. Never call {@link #getDefault} directly!
     */
    public static synchronized FilterTopComponent findInstance() {
        TopComponent win = WindowManager.getDefault().findTopComponent(PREFERRED_ID);
        if (win == null) {
            ErrorManager.getDefault().log(ErrorManager.WARNING, "Cannot find Filter component. It will not be located properly in the window system.");
            return getDefault();
        }
        if (win instanceof FilterTopComponent) {
            return (FilterTopComponent) win;
        }
        ErrorManager.getDefault().log(ErrorManager.WARNING, "There seem to be multiple components with the '" + PREFERRED_ID + "' ID. That is a potential source of errors and unexpected behavior.");
        return getDefault();
    }

    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_ALWAYS;
    }

    @Override
    protected String preferredID() {
        return PREFERRED_ID;
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return manager;
    }

    @Override
    public boolean requestFocus(boolean temporary) {
        view.requestFocus();
        return super.requestFocus(temporary);
    }

    @Override
    protected boolean requestFocusInWindow(boolean temporary) {
        view.requestFocus();
        return super.requestFocusInWindow(temporary);
    }

    @Override
    public void requestActive() {
        super.requestActive();
        view.requestFocus();
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
    }

    private Node findProfileNode(FilterProfile p) {
        Node[] nodes = profileEM.getExploredContext().getChildren().getNodes();
        for (Node n : nodes) {
            if (n.getLookup().lookup(FilterProfile.class) == p) {
                return n;
            }
        }
        return nodes[0];
    }

    final static class ResolvableHelper implements Serializable {

        private static final long serialVersionUID = 1L;

        public Object readResolve() {
            return FilterTopComponent.getDefault();
        }
    }

    private class PanelWithEM extends JPanel implements ExplorerManager.Provider {
        public PanelWithEM() {
            setBorder(new EmptyBorder(0, 0, 0, 0));
        }

        @Override
        public ExplorerManager getExplorerManager() {
            return profileEM;
        }
    }

    private static class PL extends ProxyLookup {
        public PL() {
        }

        public void setLoookups(Lookup... lkps) {
            super.setLookups(lkps);
        }
    }
}
