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

package org.graalvm.visualizer.source.impl.ui;

import org.graalvm.visualizer.source.impl.FileGroup;
import org.graalvm.visualizer.source.impl.SourceRepositoryImpl;
import org.graalvm.visualizer.source.impl.SourceRepositoryNode;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.view.ChoiceView;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileStateInvalidException;
import org.openide.filesystems.FileUtil;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;

import javax.swing.JFileChooser;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.io.File;
import java.util.regex.Pattern;

/**
 * @author sdedic
 */
public class RootPlacementUI extends javax.swing.JPanel implements PropertyChangeListener, ExplorerManager.Provider {
    private String autoDescriptionString;
    private final JFileChooser chooser;
    private final SourceRepositoryImpl impl;
    private final ExplorerManager mgr = new ExplorerManager();

    /**
     * Creates new form RootPlacementUI
     */
    public RootPlacementUI(JFileChooser chooser, SourceRepositoryImpl impl) throws FileStateInvalidException {
        this.impl = impl;
        this.chooser = chooser;
        initComponents();
        chooser.addPropertyChangeListener(this);

        SourceRepositoryNode rNode = new SourceRepositoryNode(impl, true);
        FilterNode.Children fChildren = new FilterNode.Children(rNode) {
            @Override
            protected Node[] createNodes(Node key) {
                if (key.getLookup().lookup(FileGroup.class) == null) {
                    return null;
                }
                return new Node[]{new FilterNode(key, org.openide.nodes.Children.LEAF)};
            }
        };
        AbstractNode an = new AbstractNode(fChildren);
        mgr.setRootContext(an);
        txDescription.setEditable(false);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        showHideGroupControls();
    }

    void setFileGroup(FileGroup g) {
        if (g == null) {
            Node[] nodes = mgr.getRootContext().getChildren().getNodes();
            if (nodes.length == 0) {
                return;
            }
            g = impl.getDefaultGroup();
        }
        for (Node n : mgr.getRootContext().getChildren().getNodes()) {
            FileGroup ng = n.getLookup().lookup(FileGroup.class);
            if (ng == g) {
                try {
                    mgr.setSelectedNodes(new Node[]{n});
                } catch (PropertyVetoException ex) {
                }
                break;
            }
        }
    }

    private void showHideGroupControls() {
        if (cbGroup.getModel().getSize() < 2) {
            cbGroup.setVisible(false);
            lblGroup.setVisible(false);
        } else {
            cbGroup.setVisible(true);
            lblGroup.setVisible(true);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (JFileChooser.SELECTED_FILE_CHANGED_PROPERTY.equals(evt.getPropertyName())) {
            updateDescriptionField();
        }
    }

    private static final Pattern EXCLUDE_FILENAMES = Pattern.compile("main|java|src.*|classes.*"); // NOI18N

    @NbBundle.Messages({
            "# {0} - parent directory name",
            "# {1} - nested path",
            "FMT_AutomaticComposedName={0} - {1}",
    })
    private void updateDescriptionField() {
        String s = txDescription.getText();
        if (autoDescriptionString != null && !autoDescriptionString.equals(s)) {
            // no update
            return;
        }
        File f = chooser.getSelectedFile();
        if (f == null) {
            return;
        }
        String fn = f.getName();
        if (!EXCLUDE_FILENAMES.matcher(fn).matches()) {
            autoDescriptionString = fn;
            txDescription.setText(fn);
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(f.getName());
        File parent = f.getParentFile();
        while (parent != null && EXCLUDE_FILENAMES.matcher(parent.getName()).matches()) {
            sb.insert(0, parent.getName() + "/");
            parent = parent.getParentFile();
        }
        if (parent == null || parent.getName().isEmpty()) {
            autoDescriptionString = sb.toString();
        } else {
            autoDescriptionString = Bundle.FMT_AutomaticComposedName(parent.getName(), sb.toString());
        }
        txDescription.setText(autoDescriptionString);
    }


    @Override
    public ExplorerManager getExplorerManager() {
        return mgr;
    }

    private FileObject getSelectedRoot() {
        File f = chooser.getSelectedFile();
        return f == null ? null : FileUtil.toFileObject(f);
    }

    public String getDescription() {
        String s = txDescription.getText();
        if (s.isEmpty()) {
            s = getSelectedRoot().getName();
        }
        return s;
    }

    public FileGroup getParentGroup() {
        Node[] n = mgr.getSelectedNodes();
        return n == null || n.length == 0 ? null : n[0].getLookup().lookup(FileGroup.class);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        txDescription = new javax.swing.JTextField();
        lblGroup = new javax.swing.JLabel();
        cbGroup = new ChoiceView();

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(RootPlacementUI.class, "RootPlacementUI.jLabel1.text")); // NOI18N

        txDescription.setText(org.openide.util.NbBundle.getMessage(RootPlacementUI.class, "RootPlacementUI.txDescription.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lblGroup, org.openide.util.NbBundle.getMessage(RootPlacementUI.class, "RootPlacementUI.lblGroup.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(jLabel1)
                                        .addComponent(lblGroup))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(txDescription)
                                        .addComponent(cbGroup, 0, 294, Short.MAX_VALUE)))
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(jLabel1)
                                        .addComponent(txDescription, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(cbGroup, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(lblGroup))
                                .addGap(0, 18, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox<String> cbGroup;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel lblGroup;
    private javax.swing.JTextField txDescription;
    // End of variables declaration//GEN-END:variables
}
