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
package org.graalvm.visualizer.settings.ui;

import org.graalvm.visualizer.settings.Settings;
import org.graalvm.visualizer.settings.graal.GraalSettings;
import org.openide.filesystems.FileChooserBuilder;
import org.openide.filesystems.FileUtil;

import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.graalvm.visualizer.settings.graal.GraalSettings.ACCEPT_NETWORK;
import static org.graalvm.visualizer.settings.graal.GraalSettings.AUTO_SEPARATE_SESSIONS;
import static org.graalvm.visualizer.settings.graal.GraalSettings.CLEAN_CACHES;
import static org.graalvm.visualizer.settings.graal.GraalSettings.PORT_BINARY;
import static org.graalvm.visualizer.settings.graal.GraalSettings.REPOSITORY;
import static org.graalvm.visualizer.settings.graal.GraalSettings.SESSION_CLOSE_TIMEOUT;

/**
 * @author odouda
 */
public final class GraalPanel extends SettingsPanel<GraalPanel, GraalOptionsPanelController> {

    private final GraalSettings setting;
    private final DefaultListModel<String> fileListModel = new DefaultListModel<>();

    private final JFileChooser fc = FileChooserBuilder.create(
            FileUtil.createMemoryFileSystem()).setFileFilter(new FileFilter() {
        @Override
        public boolean accept(File f) {
            if (f.isDirectory()) {
                return !f.isHidden();
            }
            return f.getName().endsWith(".map");
        }

        @Override
        public String getDescription() {
            return "ProGuard Map Files";
        }
    }).setFilesOnly(true).createFileChooser();

    /**
     * Creates new form GraalPanel
     */
    public GraalPanel(GraalOptionsPanelController controller) {
        super(controller);
        setting = GraalSettings.obtain();

        initComponents();

        setMin(portFormattedTextField, 0);
        setMin(sessionCloseTimeoutFormattedTextField, 0);

        filesList.setModel(fileListModel);
        fc.setMultiSelectionEnabled(true);

        tieComponentsToProperties();

        SwingUtilities.invokeLater(() -> {
            setPreferredSizeRecursive(this.getParent());
        });
    }

    @Override
    protected Settings getSettings() {
        return setting;
    }

    private void tieComponentsToProperties() {
        tie(BOOL, acceptNetworkCheckBox, ACCEPT_NETWORK);
        tie(BOOL, autoSeparateSessionsCheckBox, AUTO_SEPARATE_SESSIONS);
        tie(INT_FORM, portFormattedTextField, PORT_BINARY);
        tie(INT_FORM, sessionCloseTimeoutFormattedTextField, SESSION_CLOSE_TIMEOUT);
        tie(STRING, mavenTextField, REPOSITORY);
        tie(BOOL, cleanCachesCheckBox, CLEAN_CACHES);

        // TODO: solve files mapping
        addLoad(() -> setting.getFileMap().forEach(fn -> fileListModel.addElement(fn)));

        addFileButton.addActionListener(e -> {
            fc.setCurrentDirectory(new File(setting.getDirectory()));
            if (JFileChooser.APPROVE_OPTION == fc.showDialog(this, "Add file")) {
                List<String> fileNames = Arrays.stream(fc.getSelectedFiles()).map(f -> f.getAbsolutePath()).collect(Collectors.toList());
                fileNames.forEach(fn -> fileListModel.addElement(fn));
                setting.addFilesToMap(fileNames);
                settingsChanged();
            }
        });
        removeFileButton.addActionListener(e -> {
            List<String> fileNames = filesList.getSelectedValuesList();
            fileNames.forEach(s -> fileListModel.removeElement(s));
            setting.removeFilesFromMap(fileNames);
            settingsChanged();
        });
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        jPanel1 = new javax.swing.JPanel();
        portFormattedTextField = new javax.swing.JFormattedTextField();
        jLabel1 = new javax.swing.JLabel();
        acceptNetworkCheckBox = new javax.swing.JCheckBox();
        autoSeparateSessionsCheckBox = new javax.swing.JCheckBox();
        sessionCloseTimeoutFormattedTextField = new javax.swing.JFormattedTextField();
        jLabel2 = new javax.swing.JLabel();
        cleanCachesCheckBox = new javax.swing.JCheckBox();
        mavenTextField = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        addFileButton = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        filesList = new javax.swing.JList<>();
        removeFileButton = new javax.swing.JButton();
        strippingMapLocationsLabel = new javax.swing.JLabel();

        portFormattedTextField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(new java.text.DecimalFormat("#0"))));
        portFormattedTextField.setText(org.openide.util.NbBundle.getMessage(GraalPanel.class, "GraalPanel.portFormattedTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(GraalPanel.class, "GraalPanel.jLabel1.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(acceptNetworkCheckBox, org.openide.util.NbBundle.getMessage(GraalPanel.class, "GraalPanel.acceptNetworkCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(autoSeparateSessionsCheckBox, org.openide.util.NbBundle.getMessage(GraalPanel.class, "GraalPanel.autoSeparateSessionsCheckBox.text")); // NOI18N

        sessionCloseTimeoutFormattedTextField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(new java.text.DecimalFormat("#0"))));
        sessionCloseTimeoutFormattedTextField.setText(org.openide.util.NbBundle.getMessage(GraalPanel.class, "GraalPanel.sessionCloseTimeoutFormattedTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(GraalPanel.class, "GraalPanel.jLabel2.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(cleanCachesCheckBox, org.openide.util.NbBundle.getMessage(GraalPanel.class, "GraalPanel.cleanCachesCheckBox.text")); // NOI18N

        mavenTextField.setText(org.openide.util.NbBundle.getMessage(GraalPanel.class, "GraalPanel.mavenTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel3, org.openide.util.NbBundle.getMessage(GraalPanel.class, "GraalPanel.jLabel3.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(addFileButton, org.openide.util.NbBundle.getMessage(GraalPanel.class, "GraalPanel.addFileButton.text")); // NOI18N

        jScrollPane2.setPreferredSize(new java.awt.Dimension(22, 22));

        jScrollPane2.setViewportView(filesList);

        org.openide.awt.Mnemonics.setLocalizedText(removeFileButton, org.openide.util.NbBundle.getMessage(GraalPanel.class, "GraalPanel.removeFileButton.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(strippingMapLocationsLabel, org.openide.util.NbBundle.getMessage(GraalPanel.class, "GraalPanel.strippingMapLocationsLabel.text")); // NOI18N

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
                jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(addFileButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(removeFileButton))
                        .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                        .addGroup(jPanel1Layout.createSequentialGroup()
                                                .addComponent(portFormattedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 77, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(jLabel1))
                                        .addComponent(acceptNetworkCheckBox)
                                        .addComponent(autoSeparateSessionsCheckBox)
                                        .addGroup(jPanel1Layout.createSequentialGroup()
                                                .addComponent(sessionCloseTimeoutFormattedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 78, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(jLabel2))
                                        .addComponent(cleanCachesCheckBox)
                                        .addComponent(mavenTextField))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel3))
                        .addComponent(strippingMapLocationsLabel)
                        .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addGap(0, 0, 0))
        );
        jPanel1Layout.setVerticalGroup(
                jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(portFormattedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(jLabel1))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(acceptNetworkCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(autoSeparateSessionsCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(sessionCloseTimeoutFormattedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(jLabel2))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(cleanCachesCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(mavenTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(jLabel3))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(strippingMapLocationsLabel)
                                .addGap(1, 1, 1)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(addFileButton)
                                        .addComponent(removeFileButton))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jScrollPane1.setViewportView(jPanel1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(jScrollPane1)
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(jScrollPane1)
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox acceptNetworkCheckBox;
    private javax.swing.JButton addFileButton;
    private javax.swing.JCheckBox autoSeparateSessionsCheckBox;
    private javax.swing.JCheckBox cleanCachesCheckBox;
    private javax.swing.JList<String> filesList;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTextField mavenTextField;
    private javax.swing.JFormattedTextField portFormattedTextField;
    private javax.swing.JButton removeFileButton;
    private javax.swing.JFormattedTextField sessionCloseTimeoutFormattedTextField;
    private javax.swing.JLabel strippingMapLocationsLabel;
    // End of variables declaration//GEN-END:variables
}
