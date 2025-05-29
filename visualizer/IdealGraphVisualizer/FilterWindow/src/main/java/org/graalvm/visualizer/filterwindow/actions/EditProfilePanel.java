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

package org.graalvm.visualizer.filterwindow.actions;

import org.graalvm.visualizer.filter.profiles.FilterProfile;
import org.graalvm.visualizer.filter.profiles.mgmt.ProfileService;
import org.graalvm.visualizer.filter.profiles.mgmt.SimpleProfileSelector;
import org.graalvm.visualizer.util.GraphTypes;
import org.openide.NotificationLineSupport;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.view.NodeRenderer;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Component;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author sdedic
 */
public class EditProfilePanel extends javax.swing.JPanel implements ExplorerManager.Provider {
    private static final RequestProcessor DELAYER_RP = new RequestProcessor(EditProfilePanel.class);

    private final ProfileService profiles;
    private final ExplorerManager typeEM = new ExplorerManager();

    private FilterProfile profile;
    private NotificationLineSupport notifier;
    private boolean inputValid;
    private SimpleProfileSelector selector;

    private RequestProcessor.Task delayedInputValidation;
    private final DocumentListener dl = new DocumentListener() {
        public void insertUpdate(DocumentEvent e) {
            inputChanged();
        }

        public void removeUpdate(DocumentEvent e) {
            inputChanged();
        }

        public void changedUpdate(DocumentEvent e) {
        }
    };

    /**
     * Creates new form EditProfilePanel
     */
    @NbBundle.Messages({
            "PROFILE_AllGraphTypes=<html><i>&nbsp;&nbsp;all graph types</i></html>"
    })
    public EditProfilePanel(ProfileService profiles) {
        this.profiles = profiles;
        initComponents();
        GraphTypes types = Lookup.getDefault().lookup(GraphTypes.class);

        DefaultComboBoxModel model = new DefaultComboBoxModel();
        model.addElement(Bundle.PROFILE_AllGraphTypes());
        for (Node n : types.getCategoryNode().getChildren().getNodes(true)) {
            model.addElement(n);
        }
        typeChooser.setModel(model);
        typeChooser.setRenderer(new R());

        nameText.getDocument().addDocumentListener(dl);
        groupNameText.getDocument().addDocumentListener(dl);
        priorityNumber.addChangeListener(e -> inputChanged());
    }

    private void inputChanged() {
        if (delayedInputValidation != null) {
            delayedInputValidation.cancel();
        }
        delayedInputValidation = DELAYER_RP.post(() -> SwingUtilities.invokeLater(this::validateInputs), 200);
    }

    static class R extends DefaultListCellRenderer {
        private final ListCellRenderer delegate = new NodeRenderer();

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            if (value instanceof Node) {
                return delegate.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }
            return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        }
    }

    public boolean isInputValid() {
        return inputValid;
    }

    public SimpleProfileSelector getSelector() {
        return selector;
    }

    public void setSelector(SimpleProfileSelector selector) {
        this.selector = selector;
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return typeEM;
    }

    public void setProfile(FilterProfile profile) {
        this.profile = profile;
    }

    public void setNotifier(NotificationLineSupport notifier) {
        this.notifier = notifier;
    }

    public void editOnly() {
        profileText.setEditable(false);
    }

    public String getProfileName() {
        return profileText.getText();
    }

    public String getNameRegexp() {
        return nameText.getText();
    }

    public int getPriority() {
        return (Integer) priorityNumber.getValue();
    }

    @Override
    public void addNotify() {
        super.addNotify();

        profileText.setText(profile.getName());

        boolean selEnable = selector.isValid();
        typeChooser.setEnabled(selEnable);
        nameText.setEnabled(selEnable);
        groupNameText.setEnabled(selEnable);
        priorityNumber.setEnabled(selEnable);
        graphRegexp.setEnabled(selEnable);
        groupRegexp.setEnabled(selEnable);

        graphRegexp.setSelected(selector.isGraphNameRegexp());
        groupRegexp.setSelected(selector.isOwnerNameRegexp());

        if (!selEnable) {
            return;
        }
        nameText.setText(selector.getGraphName());
        groupNameText.setText(selector.getOwnerName());
        priorityNumber.setValue(selector.getOrder());

        if (selector.getGraphType() == null) {
            typeChooser.setSelectedIndex(0);
        } else for (int i = 1; i < typeChooser.getItemCount(); i++) {
            Object o = typeChooser.getItemAt(i);
            if (o instanceof Node) {
                Node n = (Node) o;
                if (n.getName().equals(selector.getGraphType())) {
                    typeChooser.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    void updateSelector() {
        String s = groupNameText.getText();
        selector.setOwnerName(s.trim());
        s = nameText.getText();
        selector.setGraphName(s.trim());
        Object o = priorityNumber.getValue();
        if (o instanceof Integer) {
            selector.setOrder((Integer) o);
        } else {
            selector.setOrder(0);
        }
        o = typeChooser.getSelectedItem();
        if (o instanceof Node) {
            selector.setGraphType(((Node) o).getName());
        } else {
            selector.setGraphType(null);
        }
        selector.setGraphNameRegexp(graphRegexp.isSelected());
        selector.setOwnerNameRegexp(groupRegexp.isSelected());
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
        jLabel2 = new javax.swing.JLabel();
        typeChooser = new javax.swing.JComboBox();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        priorityNumber = new javax.swing.JSpinner();
        profileText = new javax.swing.JTextField();
        nameText = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        groupNameText = new javax.swing.JTextField();
        graphRegexp = new javax.swing.JCheckBox();
        groupRegexp = new javax.swing.JCheckBox();

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(EditProfilePanel.class, "EditProfilePanel.jLabel1.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(EditProfilePanel.class, "EditProfilePanel.jLabel2.text")); // NOI18N

        typeChooser.setToolTipText(org.openide.util.NbBundle.getMessage(EditProfilePanel.class, "EditProfilePanel.typeChooser.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel3, org.openide.util.NbBundle.getMessage(EditProfilePanel.class, "EditProfilePanel.jLabel3.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel4, org.openide.util.NbBundle.getMessage(EditProfilePanel.class, "EditProfilePanel.jLabel4.text")); // NOI18N

        priorityNumber.setFont(new java.awt.Font("Dialog", 0, 12)); // NOI18N
        priorityNumber.setModel(new javax.swing.SpinnerNumberModel(1, 0, null, 1));
        priorityNumber.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                priorityNumberFocusLost(evt);
            }
        });

        profileText.setText(org.openide.util.NbBundle.getMessage(EditProfilePanel.class, "EditProfilePanel.profileText.text")); // NOI18N

        nameText.setText(org.openide.util.NbBundle.getMessage(EditProfilePanel.class, "EditProfilePanel.nameText.text")); // NOI18N
        nameText.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                nameTextFocusLost(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabel5, org.openide.util.NbBundle.getMessage(EditProfilePanel.class, "EditProfilePanel.jLabel5.text")); // NOI18N

        groupNameText.setText(org.openide.util.NbBundle.getMessage(EditProfilePanel.class, "EditProfilePanel.groupNameText.text")); // NOI18N
        groupNameText.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                groupNameTextFocusLost(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(graphRegexp, org.openide.util.NbBundle.getMessage(EditProfilePanel.class, "EditProfilePanel.graphRegexp.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(groupRegexp, org.openide.util.NbBundle.getMessage(EditProfilePanel.class, "EditProfilePanel.groupRegexp.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(jLabel2)
                                        .addComponent(jLabel1)
                                        .addComponent(jLabel3)
                                        .addComponent(jLabel4)
                                        .addComponent(jLabel5))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(typeChooser, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(profileText)
                                        .addComponent(nameText)
                                        .addComponent(groupNameText)
                                        .addGroup(layout.createSequentialGroup()
                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addComponent(groupRegexp)
                                                        .addComponent(graphRegexp)
                                                        .addComponent(priorityNumber, javax.swing.GroupLayout.PREFERRED_SIZE, 47, javax.swing.GroupLayout.PREFERRED_SIZE))
                                                .addGap(0, 106, Short.MAX_VALUE)))
                                .addContainerGap())
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(jLabel1)
                                        .addComponent(profileText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(18, 18, 18)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(jLabel2)
                                        .addComponent(typeChooser, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(jLabel3)
                                        .addComponent(nameText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(graphRegexp)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(groupNameText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(jLabel5))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(groupRegexp)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 14, Short.MAX_VALUE)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(jLabel4)
                                        .addComponent(priorityNumber, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void nameTextFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_nameTextFocusLost
        validateInputs();
    }//GEN-LAST:event_nameTextFocusLost

    private void groupNameTextFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_groupNameTextFocusLost
        validateInputs();
    }//GEN-LAST:event_groupNameTextFocusLost

    private void priorityNumberFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_priorityNumberFocusLost
        validateInputs();
    }//GEN-LAST:event_priorityNumberFocusLost


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox graphRegexp;
    private javax.swing.JTextField groupNameText;
    private javax.swing.JCheckBox groupRegexp;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JTextField nameText;
    private javax.swing.JSpinner priorityNumber;
    private javax.swing.JTextField profileText;
    private javax.swing.JComboBox typeChooser;
    // End of variables declaration//GEN-END:variables

    private void setInputValid(boolean b) {
        boolean old = this.inputValid;
        this.inputValid = b;
        firePropertyChange("inputValid", old, b);
    }

    void reportError(String e) {
        setInputValid(false);
        notifier.setErrorMessage(e);
    }

    @NbBundle.Messages({
            "# {0} - profile name",
            "ERROR_ProfileNameUsed=The name ''{0}'' is already used.",
            "# {0} - regexp parsing message",
            "ERROR_ProfileBadNamePattern=Bad regular expression for name: {0}",
            "ERROR_ProfileBadPriority=Invalid priority, must at least 0."
    })
    void validateInputs() {
        String profName = profileText.getText();
        if (!profName.trim().equals(profile.getName())) {
            if (profiles.getProfiles().stream().anyMatch(
                    p -> p.getName().equalsIgnoreCase(profName) && p != profile)) {
                reportError(Bundle.ERROR_ProfileNameUsed(profName));
                return;
            }
        }

        String exprText = nameText.getText().trim();
        if (!exprText.isEmpty() && graphRegexp.isSelected()) {
            try {
                Pattern.compile(exprText, Pattern.CASE_INSENSITIVE);
            } catch (PatternSyntaxException ex) {
                reportError(Bundle.ERROR_ProfileBadNamePattern(ex.toString()));
                return;
            }
        }
        exprText = groupNameText.getText().trim();
        if (!exprText.isEmpty() && groupRegexp.isSelected()) {
            try {
                Pattern.compile(exprText, Pattern.CASE_INSENSITIVE);
            } catch (PatternSyntaxException ex) {
                reportError(Bundle.ERROR_ProfileBadNamePattern(ex.toString()));
                return;
            }
        }

        Integer n = (Integer) priorityNumber.getModel().getValue();
        if (n != null && n < 0) {
            reportError(Bundle.ERROR_ProfileBadPriority());
            return;
        }
        setInputValid(true);
        notifier.clearMessages();
    }

    static class TypeChildren extends FilterNode.Children {
        public TypeChildren(Node or) {
            super(or);
        }


    }
}
