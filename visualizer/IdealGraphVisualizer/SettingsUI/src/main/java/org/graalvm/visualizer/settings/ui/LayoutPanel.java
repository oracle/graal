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
import org.graalvm.visualizer.settings.layout.LayoutSettings;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

import static org.graalvm.visualizer.settings.layout.LayoutSettings.ACTIVE_PREVIEW;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.BLOCKVIEW_AS_CONTROLFLOW;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.BOTH_SORT;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.CENTER_CROSSING_X;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.CENTER_SIMPLE_NODES;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.CROSSING_SORT;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.CROSSING_SWEEP_COUNT;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.CROSS_BY_CONN_DIFF;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.CROSS_FACTOR;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.CROSS_POSITION_DURING;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.CROSS_REDUCE_ROUTING;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.CROSS_RESET_X_FROM_MIDDLE;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.CROSS_RESET_X_FROM_NODE;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.CROSS_USE_FACTOR;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.DECREASE_LAYER_WIDTH_DEVIATION;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.DECREASE_LAYER_WIDTH_DEVIATION_UP;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.DEFAULT_LAYOUT;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.DELAY_DANGLING_NODES;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.DRAW_LONG_EDGES;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.DUMMY_CROSSING;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.DUMMY_FIRST_SORT;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.DUMMY_WIDTH;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.EDGE_BENDING;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.IRRELEVANT_LAYOUT_CODE;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.LAST_DOWN_SWEEP;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.LAST_UP_CROSSING_SWEEP;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.LayoutSettingBean;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.MAX_BLOCK_LAYER_LENGTH;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.MAX_LAYER_LENGTH;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.MEAN_NOT_MEDIAN;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.MIN_EDGE_ANGLE;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.NODE_TEXT;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.NO_CROSSING_LAYER_REASSIGN;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.NO_DUMMY_LONG_EDGES;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.NO_VIP;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.NO_VIP_DEFAULT;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.OPTIMAL_UP_VIP;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.PROPER_CROSSING_CLOSEST_NODE;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.REVERSE_SORT;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.SPAN_BY_ANGLE;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.SQUASH_POSITION;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.STABILIZED_LAYOUT;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.STANDALONES;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.UNKNOWN_CROSSING_NUMBER;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.UNREVERSE_VIPS;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.X_ASSIGN_SWEEP_COUNT;

final class LayoutPanel extends SettingsPanel<LayoutPanel, LayoutOptionsPanelController> {

    private final LayoutSettingBean setting;
    private final List<Runnable> bases = new ArrayList<>();

    LayoutPanel(LayoutOptionsPanelController controller) {
        super(controller);
        this.setting = LayoutSettings.getBean();

        initComponents();

        group(defaultLayoutGroup, defaultRadioB, advancedRadioB);
        group(crossingResetGroup, crossingResetXLeftRadioButton, crossingResetXMiddleRadioButton);
        group(decreaseDeviationGroup, decreaseDeviationUpRadioButton, decreaseDeviationDownRadioButton);
        setMin(dummyWidthFormattedTextField, 1);
        setMin(minEdgeAngleFormattedTextField, 1);
        setMax(minEdgeAngleFormattedTextField, 89);
        setMin(maxLayerLengthFormattedTextField, 3);
        setMin(maxBlockLayerLengthFormattedTextField, 3);
        setMin(crossFactorFormattedTextField, 0);

        tieComponentsToProperties();

        SwingUtilities.invokeLater(() -> {
            setPreferredSizeRecursive(this.getParent());
        });
    }

    private void tieComponentsToProperties() {
        tie(BOOL, lastDownSweepCheckBox, LAST_DOWN_SWEEP, false);
        tie(BOOL, squashNodesCheckBox, SQUASH_POSITION, false);
        tie(BOOL, defaultRadioB, DEFAULT_LAYOUT);
        tie(NOT, advancedRadioB, DEFAULT_LAYOUT);
        disables(defaultRadioB, settingsPane);
        enables(advancedRadioB, settingsPane);
        tie(BOOL, previewCheckBox, ACTIVE_PREVIEW);
        tie(BOOL, longEdgesCheckBox, DRAW_LONG_EDGES);
        tie(BOOL, noCrossingLayerReassignCheckBox, NO_CROSSING_LAYER_REASSIGN, false);
        tie(BOOL, crossingSortCheckBox, CROSSING_SORT, false);
        tie(BOOL, centerCrossingCheckBox, CENTER_CROSSING_X, false);
        tie(BOOL, irrelevantCodeCheckBox, IRRELEVANT_LAYOUT_CODE);
        tie(BOOL, lastUpCrossingSweepCheckBox, LAST_UP_CROSSING_SWEEP, false);
        tie(BOOL, properCrossingClosestNodeCheckBox, PROPER_CROSSING_CLOSEST_NODE, false);
        tie(BOOL, unknownCrossingNumberChangeCheckBox, UNKNOWN_CROSSING_NUMBER, false);
        tie(INT_COMB, sweepCountComboBox, X_ASSIGN_SWEEP_COUNT, 1);
        tie(BOOL, noLongDummyCheckBox, NO_DUMMY_LONG_EDGES, false);
        tie(BOOL, reverseSortCheckBox, REVERSE_SORT, false);
        tie(BOOL, bothSortCheckBox, BOTH_SORT, false);
        tie(BOOL, meanCheckBox, MEAN_NOT_MEDIAN, false);
        tie(BOOL, optimalUpVIPCheckBox, OPTIMAL_UP_VIP, false);
        tie(BOOL, noVIPCheckBox, NO_VIP, NO_VIP_DEFAULT);
        tie(BOOL, edgeBendingCheckBox, EDGE_BENDING, false);
        tie(BOOL, dummyFirstSortCheckBox, DUMMY_FIRST_SORT, true);
        tie(BOOL, stabilizedLayoutCheckBox, STABILIZED_LAYOUT, false);
        tie(INT_FORM, dummyWidthFormattedTextField, DUMMY_WIDTH, 1);

        tie(BOOL, spanByAngleCheckBox, SPAN_BY_ANGLE, false);
        tie(INT_FORM, minEdgeAngleFormattedTextField, MIN_EDGE_ANGLE, 2);
        enables(spanByAngleCheckBox, minEdgeAngleFormattedTextField);
        tie(INT_COMB, crossingSweepCountComboBox, CROSSING_SWEEP_COUNT, 2);
        tie(BOOL, dummyCrossingCheckBox, DUMMY_CROSSING, false);
        tie(STRING, nodeNameTextField, NODE_TEXT);
        tie(BOOL, unreverseVIPsCheckBox, UNREVERSE_VIPS, false);
        tie(INT_FORM, maxLayerLengthFormattedTextField, MAX_LAYER_LENGTH, 10);
        tie(INT_FORM, maxBlockLayerLengthFormattedTextField, MAX_BLOCK_LAYER_LENGTH, 3);
        tie(BOOL, centerSimpleNodesCheckBox, CENTER_SIMPLE_NODES, false);
        tie(BOOL, crossingByConnDiffCheckBox, CROSS_BY_CONN_DIFF, false);
        tie(BOOL, crossingResetXFromNodeCheckBox, CROSS_RESET_X_FROM_NODE, false);
        tie(BOOL, crossingResetXMiddleRadioButton, CROSS_RESET_X_FROM_MIDDLE);
        tie(NOT, crossingResetXLeftRadioButton, CROSS_RESET_X_FROM_MIDDLE);
        enables(crossingResetXFromNodeCheckBox, crossingResetXMiddleRadioButton);
        enables(crossingResetXFromNodeCheckBox, crossingResetXLeftRadioButton);
        tie(BOOL, useCrossFactorCheckBox, CROSS_USE_FACTOR, false);
        tie(FLOAT, crossFactorFormattedTextField, CROSS_FACTOR);
        enables(useCrossFactorCheckBox, crossFactorFormattedTextField);
        tie(BOOL, assignDuringCrossingCheckBox, CROSS_POSITION_DURING, false);
        tie(BOOL, delayDanglingNodesCheckBox, DELAY_DANGLING_NODES, false);
        tie(BOOL, decreaseLayerWidthDeviationCheckBox, DECREASE_LAYER_WIDTH_DEVIATION, false);
        tie(BOOL, decreaseDeviationUpRadioButton, DECREASE_LAYER_WIDTH_DEVIATION_UP);
        tie(NOT, decreaseDeviationDownRadioButton, DECREASE_LAYER_WIDTH_DEVIATION_UP);
        tie(BOOL, decreaseLayerWidthDeviationQuickCheckBox, DECREASE_LAYER_WIDTH_DEVIATION);
        enables(decreaseLayerWidthDeviationCheckBox, decreaseDeviationUpRadioButton);
        enables(decreaseLayerWidthDeviationCheckBox, decreaseDeviationDownRadioButton);
        enables(decreaseLayerWidthDeviationCheckBox, decreaseLayerWidthDeviationQuickCheckBox);
        tie(BOOL, crossingReductionDuringRoutingCheckBox, CROSS_REDUCE_ROUTING, false);
        tie(BOOL, blockViewAsControlFlowCheckBox, BLOCKVIEW_AS_CONTROLFLOW, false);
        tie(BOOL, standAlonesCheckBox, STANDALONES, false);
    }

    private <T, C extends JComponent> void tie(Connector<T, C> type, C comp, String name, T base) {
        tie(type, comp, name);
        bases.add(() -> setting.set(name, base));
    }

    @Override
    protected Settings getSettings() {
        return setting;
    }

    @Override
    protected boolean isFireChanged() {
        return setting.get(Boolean.class, ACTIVE_PREVIEW);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        defaultLayoutGroup = new javax.swing.ButtonGroup();
        crossingResetGroup = new javax.swing.ButtonGroup();
        decreaseDeviationGroup = new javax.swing.ButtonGroup();
        jScrollBar1 = new javax.swing.JScrollBar();
        settingsPane = new javax.swing.JTabbedPane();
        routingScrollPane = new javax.swing.JScrollPane();
        routingPanel = new javax.swing.JPanel();
        crossingReductionDuringRoutingCheckBox = new javax.swing.JCheckBox();
        layeringScrollPane = new javax.swing.JScrollPane();
        layeringPanel = new javax.swing.JPanel();
        unreverseVIPsCheckBox = new javax.swing.JCheckBox();
        maxLayerLengthFormattedTextField = new javax.swing.JFormattedTextField();
        jLabel4 = new javax.swing.JLabel();
        maxBlockLayerLengthFormattedTextField = new javax.swing.JFormattedTextField();
        jLabel5 = new javax.swing.JLabel();
        decreaseLayerWidthDeviationCheckBox = new javax.swing.JCheckBox();
        decreaseDeviationUpRadioButton = new javax.swing.JRadioButton();
        decreaseDeviationDownRadioButton = new javax.swing.JRadioButton();
        decreaseLayerWidthDeviationQuickCheckBox = new javax.swing.JCheckBox();
        dummyScrollPane = new javax.swing.JScrollPane();
        dummyPanel = new javax.swing.JPanel();
        noLongDummyCheckBox = new javax.swing.JCheckBox();
        dummyWidthFormattedTextField = new javax.swing.JFormattedTextField();
        dummyWidthLabel = new javax.swing.JLabel();
        crossingScrollPane = new javax.swing.JScrollPane();
        crossingPanel = new javax.swing.JPanel();
        noCrossingLayerReassignCheckBox = new javax.swing.JCheckBox();
        crossingSortCheckBox = new javax.swing.JCheckBox();
        centerCrossingCheckBox = new javax.swing.JCheckBox();
        lastUpCrossingSweepCheckBox = new javax.swing.JCheckBox();
        properCrossingClosestNodeCheckBox = new javax.swing.JCheckBox();
        unknownCrossingNumberChangeCheckBox = new javax.swing.JCheckBox();
        dummyCrossingCheckBox = new javax.swing.JCheckBox();
        crossingSweepCountComboBox = new javax.swing.JComboBox<>();
        jLabel2 = new javax.swing.JLabel();
        crossingByConnDiffCheckBox = new javax.swing.JCheckBox();
        crossingResetXFromNodeCheckBox = new javax.swing.JCheckBox();
        crossingResetXMiddleRadioButton = new javax.swing.JRadioButton();
        crossingResetXLeftRadioButton = new javax.swing.JRadioButton();
        useCrossFactorCheckBox = new javax.swing.JCheckBox();
        crossFactorFormattedTextField = new javax.swing.JFormattedTextField();
        jLabel6 = new javax.swing.JLabel();
        assignDuringCrossingCheckBox = new javax.swing.JCheckBox();
        jLabel7 = new javax.swing.JLabel();
        xAssignScrollPane = new javax.swing.JScrollPane();
        xAssignPanel = new javax.swing.JPanel();
        sweepCountComboBox = new javax.swing.JComboBox<>();
        sweepCountLabel = new javax.swing.JLabel();
        lastDownSweepCheckBox = new javax.swing.JCheckBox();
        squashNodesCheckBox = new javax.swing.JCheckBox();
        reverseSortCheckBox = new javax.swing.JCheckBox();
        bothSortCheckBox = new javax.swing.JCheckBox();
        meanCheckBox = new javax.swing.JCheckBox();
        optimalUpVIPCheckBox = new javax.swing.JCheckBox();
        dummyFirstSortCheckBox = new javax.swing.JCheckBox();
        centerSimpleNodesCheckBox = new javax.swing.JCheckBox();
        delayDanglingNodesCheckBox = new javax.swing.JCheckBox();
        yAssignScrollPane = new javax.swing.JScrollPane();
        yAsignPanel = new javax.swing.JPanel();
        spanByAngleCheckBox = new javax.swing.JCheckBox();
        minEdgeAngleFormattedTextField = new javax.swing.JFormattedTextField();
        jLabel1 = new javax.swing.JLabel();
        writeScrollPane = new javax.swing.JScrollPane();
        writePanel = new javax.swing.JPanel();
        previewCheckBox = new javax.swing.JCheckBox();
        defaultRadioB = new javax.swing.JRadioButton();
        advancedRadioB = new javax.swing.JRadioButton();
        presetComboBox = new javax.swing.JComboBox<>();
        presetLabel = new javax.swing.JLabel();
        generalScrollPane = new javax.swing.JScrollPane();
        generalPanel = new javax.swing.JPanel();
        resetAdvancedButton = new javax.swing.JButton();
        advancedAsDefaultButton = new javax.swing.JButton();
        longEdgesCheckBox = new javax.swing.JCheckBox();
        irrelevantCodeCheckBox = new javax.swing.JCheckBox();
        noVIPCheckBox = new javax.swing.JCheckBox();
        edgeBendingCheckBox = new javax.swing.JCheckBox();
        stabilizedLayoutCheckBox = new javax.swing.JCheckBox();
        nodeNameTextField = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        blockViewAsControlFlowCheckBox = new javax.swing.JCheckBox();
        standAlonesCheckBox = new javax.swing.JCheckBox();

        setMinimumSize(new java.awt.Dimension(200, 200));
        setPreferredSize(new java.awt.Dimension(500, 601));

        settingsPane.setMinimumSize(new java.awt.Dimension(100, 250));

        routingPanel.setPreferredSize(new java.awt.Dimension(0, 0));

        org.openide.awt.Mnemonics.setLocalizedText(crossingReductionDuringRoutingCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.crossingReductionDuringRoutingCheckBox.text")); // NOI18N

        javax.swing.GroupLayout routingPanelLayout = new javax.swing.GroupLayout(routingPanel);
        routingPanel.setLayout(routingPanelLayout);
        routingPanelLayout.setHorizontalGroup(
                routingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(routingPanelLayout.createSequentialGroup()
                                .addComponent(crossingReductionDuringRoutingCheckBox)
                                .addGap(0, 0, Short.MAX_VALUE))
        );
        routingPanelLayout.setVerticalGroup(
                routingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(routingPanelLayout.createSequentialGroup()
                                .addComponent(crossingReductionDuringRoutingCheckBox)
                                .addGap(0, 0, Short.MAX_VALUE))
        );

        routingScrollPane.setViewportView(routingPanel);

        settingsPane.addTab(org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.routingScrollPane.TabConstraints.tabTitle"), routingScrollPane); // NOI18N

        layeringPanel.setPreferredSize(new java.awt.Dimension(0, 0));

        org.openide.awt.Mnemonics.setLocalizedText(unreverseVIPsCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.unreverseVIPsCheckBox.text")); // NOI18N

        maxLayerLengthFormattedTextField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(new java.text.DecimalFormat("#0"))));
        maxLayerLengthFormattedTextField.setText(org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.maxLayerLengthFormattedTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel4, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.jLabel4.text")); // NOI18N

        maxBlockLayerLengthFormattedTextField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(new java.text.DecimalFormat("#0"))));
        maxBlockLayerLengthFormattedTextField.setText(org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.maxBlockLayerLengthFormattedTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel5, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.jLabel5.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(decreaseLayerWidthDeviationCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.decreaseLayerWidthDeviationCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(decreaseDeviationUpRadioButton, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.decreaseDeviationUpRadioButton.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(decreaseDeviationDownRadioButton, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.decreaseDeviationDownRadioButton.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(decreaseLayerWidthDeviationQuickCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.decreaseLayerWidthDeviationQuickCheckBox.text")); // NOI18N

        javax.swing.GroupLayout layeringPanelLayout = new javax.swing.GroupLayout(layeringPanel);
        layeringPanel.setLayout(layeringPanelLayout);
        layeringPanelLayout.setHorizontalGroup(
                layeringPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layeringPanelLayout.createSequentialGroup()
                                .addGroup(layeringPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(unreverseVIPsCheckBox)
                                        .addGroup(layeringPanelLayout.createSequentialGroup()
                                                .addGroup(layeringPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                                        .addComponent(maxBlockLayerLengthFormattedTextField, javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addComponent(maxLayerLengthFormattedTextField, javax.swing.GroupLayout.Alignment.LEADING))
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addGroup(layeringPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addComponent(jLabel4, javax.swing.GroupLayout.PREFERRED_SIZE, 284, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                        .addComponent(jLabel5)))
                                        .addComponent(decreaseLayerWidthDeviationCheckBox)
                                        .addGroup(layeringPanelLayout.createSequentialGroup()
                                                .addGap(21, 21, 21)
                                                .addComponent(decreaseDeviationUpRadioButton)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(decreaseDeviationDownRadioButton)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(decreaseLayerWidthDeviationQuickCheckBox)))
                                .addGap(0, 0, Short.MAX_VALUE))
        );
        layeringPanelLayout.setVerticalGroup(
                layeringPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layeringPanelLayout.createSequentialGroup()
                                .addComponent(unreverseVIPsCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layeringPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(maxLayerLengthFormattedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(jLabel4))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layeringPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(maxBlockLayerLengthFormattedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(jLabel5))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(decreaseLayerWidthDeviationCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layeringPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(decreaseDeviationUpRadioButton)
                                        .addComponent(decreaseDeviationDownRadioButton)
                                        .addComponent(decreaseLayerWidthDeviationQuickCheckBox))
                                .addGap(0, 0, Short.MAX_VALUE))
        );

        layeringScrollPane.setViewportView(layeringPanel);

        settingsPane.addTab(org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.layeringScrollPane.TabConstraints.tabTitle"), layeringScrollPane); // NOI18N

        dummyPanel.setPreferredSize(new java.awt.Dimension(0, 0));

        org.openide.awt.Mnemonics.setLocalizedText(noLongDummyCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.noLongDummyCheckBox.text")); // NOI18N

        dummyWidthFormattedTextField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(new java.text.DecimalFormat("#0"))));
        dummyWidthFormattedTextField.setHorizontalAlignment(javax.swing.JTextField.RIGHT);

        org.openide.awt.Mnemonics.setLocalizedText(dummyWidthLabel, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.dummyWidthLabel.text")); // NOI18N

        javax.swing.GroupLayout dummyPanelLayout = new javax.swing.GroupLayout(dummyPanel);
        dummyPanel.setLayout(dummyPanelLayout);
        dummyPanelLayout.setHorizontalGroup(
                dummyPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(dummyPanelLayout.createSequentialGroup()
                                .addGroup(dummyPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(noLongDummyCheckBox)
                                        .addGroup(dummyPanelLayout.createSequentialGroup()
                                                .addComponent(dummyWidthFormattedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(dummyWidthLabel)))
                                .addGap(0, 0, Short.MAX_VALUE))
        );
        dummyPanelLayout.setVerticalGroup(
                dummyPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(dummyPanelLayout.createSequentialGroup()
                                .addComponent(noLongDummyCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(dummyPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(dummyWidthFormattedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(dummyWidthLabel))
                                .addGap(0, 0, Short.MAX_VALUE))
        );

        dummyScrollPane.setViewportView(dummyPanel);

        settingsPane.addTab(org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.dummyScrollPane.TabConstraints.tabTitle"), dummyScrollPane); // NOI18N

        crossingScrollPane.setMinimumSize(new java.awt.Dimension(100, 100));
        crossingScrollPane.setViewportView(crossingPanel);

        crossingPanel.setMinimumSize(new java.awt.Dimension(100, 100));
        crossingPanel.setPreferredSize(new java.awt.Dimension(100, 100));

        org.openide.awt.Mnemonics.setLocalizedText(noCrossingLayerReassignCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.noCrossingLayerReassignCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(crossingSortCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.crossingSortCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(centerCrossingCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.centerCrossingCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lastUpCrossingSweepCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.lastUpCrossingSweepCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(properCrossingClosestNodeCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.properCrossingClosestNodeCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(unknownCrossingNumberChangeCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.unknownCrossingNumberChangeCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(dummyCrossingCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.dummyCrossingCheckBox.text")); // NOI18N

        crossingSweepCountComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[]{"1", "2", "3", "4", "5"}));

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.jLabel2.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(crossingByConnDiffCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.crossingByConnDiffCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(crossingResetXFromNodeCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.crossingResetXFromNodeCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(crossingResetXMiddleRadioButton, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.crossingResetXMiddleRadioButton.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(crossingResetXLeftRadioButton, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.crossingResetXLeftRadioButton.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(useCrossFactorCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.useCrossFactorCheckBox.text")); // NOI18N

        crossFactorFormattedTextField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(new java.text.DecimalFormat("#0.00"))));
        crossFactorFormattedTextField.setText(org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.crossFactorFormattedTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel6, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.jLabel6.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(assignDuringCrossingCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.assignDuringCrossingCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel7, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.jLabel7.text")); // NOI18N

        javax.swing.GroupLayout crossingPanelLayout = new javax.swing.GroupLayout(crossingPanel);
        crossingPanel.setLayout(crossingPanelLayout);
        crossingPanelLayout.setHorizontalGroup(
                crossingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(crossingPanelLayout.createSequentialGroup()
                                .addGroup(crossingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(crossingPanelLayout.createSequentialGroup()
                                                .addComponent(crossingSweepCountComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(jLabel2))
                                        .addComponent(crossingSortCheckBox)
                                        .addComponent(centerCrossingCheckBox)
                                        .addComponent(lastUpCrossingSweepCheckBox)
                                        .addComponent(properCrossingClosestNodeCheckBox)
                                        .addComponent(unknownCrossingNumberChangeCheckBox)
                                        .addComponent(dummyCrossingCheckBox)
                                        .addComponent(noCrossingLayerReassignCheckBox)
                                        .addComponent(crossingByConnDiffCheckBox)
                                        .addComponent(crossingResetXFromNodeCheckBox)
                                        .addComponent(useCrossFactorCheckBox)
                                        .addComponent(assignDuringCrossingCheckBox)
                                        .addGroup(crossingPanelLayout.createSequentialGroup()
                                                .addGap(21, 21, 21)
                                                .addGroup(crossingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addGroup(crossingPanelLayout.createSequentialGroup()
                                                                .addComponent(crossFactorFormattedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 72, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                                .addComponent(jLabel6))
                                                        .addGroup(crossingPanelLayout.createSequentialGroup()
                                                                .addComponent(crossingResetXMiddleRadioButton)
                                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                                .addComponent(crossingResetXLeftRadioButton))
                                                        .addComponent(jLabel7))))
                                .addGap(0, 0, Short.MAX_VALUE))
        );
        crossingPanelLayout.setVerticalGroup(
                crossingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(crossingPanelLayout.createSequentialGroup()
                                .addGroup(crossingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(crossingSweepCountComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(jLabel2))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(noCrossingLayerReassignCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(crossingSortCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(centerCrossingCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(lastUpCrossingSweepCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(properCrossingClosestNodeCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(unknownCrossingNumberChangeCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(dummyCrossingCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(crossingByConnDiffCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(crossingResetXFromNodeCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(crossingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(crossingResetXMiddleRadioButton)
                                        .addComponent(crossingResetXLeftRadioButton))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(useCrossFactorCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(crossingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(crossFactorFormattedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(jLabel6))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(assignDuringCrossingCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel7)
                                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        crossingScrollPane.setViewportView(crossingPanel);

        settingsPane.addTab(org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.crossingScrollPane.TabConstraints.tabTitle"), crossingScrollPane); // NOI18N

        xAssignPanel.setPreferredSize(new java.awt.Dimension(0, 0));

        sweepCountComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[]{"1", "2", "3", "4", "5"}));

        org.openide.awt.Mnemonics.setLocalizedText(sweepCountLabel, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.sweepCountLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lastDownSweepCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.lastDownSweepCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(squashNodesCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.squashNodesCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(reverseSortCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.reverseSortCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bothSortCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.bothSortCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(meanCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.meanCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(optimalUpVIPCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.optimalUpVIPCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(dummyFirstSortCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.dummyFirstSortCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(centerSimpleNodesCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.centerSimpleNodesCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(delayDanglingNodesCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.delayDanglingNodesCheckBox.text")); // NOI18N

        javax.swing.GroupLayout xAssignPanelLayout = new javax.swing.GroupLayout(xAssignPanel);
        xAssignPanel.setLayout(xAssignPanelLayout);
        xAssignPanelLayout.setHorizontalGroup(
                xAssignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(xAssignPanelLayout.createSequentialGroup()
                                .addGroup(xAssignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(xAssignPanelLayout.createSequentialGroup()
                                                .addComponent(sweepCountComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(sweepCountLabel))
                                        .addComponent(optimalUpVIPCheckBox)
                                        .addComponent(lastDownSweepCheckBox)
                                        .addComponent(bothSortCheckBox)
                                        .addComponent(squashNodesCheckBox)
                                        .addComponent(reverseSortCheckBox)
                                        .addComponent(meanCheckBox)
                                        .addComponent(dummyFirstSortCheckBox)
                                        .addComponent(centerSimpleNodesCheckBox)
                                        .addComponent(delayDanglingNodesCheckBox))
                                .addGap(0, 0, Short.MAX_VALUE))
        );
        xAssignPanelLayout.setVerticalGroup(
                xAssignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(xAssignPanelLayout.createSequentialGroup()
                                .addGroup(xAssignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(sweepCountComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(sweepCountLabel))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(optimalUpVIPCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(lastDownSweepCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(squashNodesCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(bothSortCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(reverseSortCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(meanCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(dummyFirstSortCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(centerSimpleNodesCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(delayDanglingNodesCheckBox)
                                .addGap(0, 0, Short.MAX_VALUE))
        );

        xAssignScrollPane.setViewportView(xAssignPanel);

        settingsPane.addTab(org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.xAssignScrollPane.TabConstraints.tabTitle"), xAssignScrollPane); // NOI18N

        yAsignPanel.setMaximumSize(new java.awt.Dimension(0, 0));
        yAsignPanel.setPreferredSize(new java.awt.Dimension(0, 0));

        org.openide.awt.Mnemonics.setLocalizedText(spanByAngleCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.spanByAngleCheckBox.text")); // NOI18N

        minEdgeAngleFormattedTextField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(new java.text.DecimalFormat("#0"))));
        minEdgeAngleFormattedTextField.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        minEdgeAngleFormattedTextField.setText(org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.minEdgeAngleFormattedTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.jLabel1.text")); // NOI18N

        javax.swing.GroupLayout yAsignPanelLayout = new javax.swing.GroupLayout(yAsignPanel);
        yAsignPanel.setLayout(yAsignPanelLayout);
        yAsignPanelLayout.setHorizontalGroup(
                yAsignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(yAsignPanelLayout.createSequentialGroup()
                                .addComponent(spanByAngleCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(minEdgeAngleFormattedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 48, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(1, 1, 1)
                                .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 17, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(0, 0, Short.MAX_VALUE))
        );
        yAsignPanelLayout.setVerticalGroup(
                yAsignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(yAsignPanelLayout.createSequentialGroup()
                                .addGroup(yAsignPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(spanByAngleCheckBox)
                                        .addComponent(minEdgeAngleFormattedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(jLabel1))
                                .addGap(0, 0, Short.MAX_VALUE))
        );

        yAssignScrollPane.setViewportView(yAsignPanel);

        settingsPane.addTab(org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.yAssignScrollPane.TabConstraints.tabTitle"), yAssignScrollPane); // NOI18N

        writePanel.setPreferredSize(new java.awt.Dimension(0, 0));

        javax.swing.GroupLayout writePanelLayout = new javax.swing.GroupLayout(writePanel);
        writePanel.setLayout(writePanelLayout);
        writePanelLayout.setHorizontalGroup(
                writePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGap(0, 0, Short.MAX_VALUE)
        );
        writePanelLayout.setVerticalGroup(
                writePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGap(0, 0, Short.MAX_VALUE)
        );

        writeScrollPane.setViewportView(writePanel);

        settingsPane.addTab(org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.writeScrollPane.TabConstraints.tabTitle"), writeScrollPane); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(previewCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.previewCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(defaultRadioB, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.defaultRadioB.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(advancedRadioB, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.advancedRadioB.text")); // NOI18N

        presetComboBox.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(presetLabel, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.presetLabel.text")); // NOI18N

        generalPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.generalPanel.border.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(resetAdvancedButton, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.resetAdvancedButton.text")); // NOI18N
        resetAdvancedButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetAdvancedButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(advancedAsDefaultButton, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.advancedAsDefaultButton.text")); // NOI18N
        advancedAsDefaultButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                advancedToDefault(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(longEdgesCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.longEdgesCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(irrelevantCodeCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.irrelevantCodeCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(noVIPCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.noVIPCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(edgeBendingCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.edgeBendingCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(stabilizedLayoutCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.stabilizedLayoutCheckBox.text")); // NOI18N

        nodeNameTextField.setText(org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.nodeNameTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel3, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.jLabel3.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(blockViewAsControlFlowCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.blockViewAsControlFlowCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(standAlonesCheckBox, org.openide.util.NbBundle.getMessage(LayoutPanel.class, "LayoutPanel.standAlonesCheckBox.text")); // NOI18N

        javax.swing.GroupLayout generalPanelLayout = new javax.swing.GroupLayout(generalPanel);
        generalPanel.setLayout(generalPanelLayout);
        generalPanelLayout.setHorizontalGroup(
                generalPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(generalPanelLayout.createSequentialGroup()
                                .addGroup(generalPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(longEdgesCheckBox)
                                        .addComponent(irrelevantCodeCheckBox)
                                        .addComponent(noVIPCheckBox))
                                .addGap(18, 18, 18)
                                .addGroup(generalPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                        .addComponent(resetAdvancedButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(advancedAsDefaultButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                .addGap(0, 0, Short.MAX_VALUE))
                        .addGroup(generalPanelLayout.createSequentialGroup()
                                .addGroup(generalPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(edgeBendingCheckBox)
                                        .addGroup(generalPanelLayout.createSequentialGroup()
                                                .addComponent(nodeNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 142, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addGap(0, 0, 0)
                                                .addComponent(jLabel3))
                                        .addComponent(stabilizedLayoutCheckBox)
                                        .addComponent(blockViewAsControlFlowCheckBox)
                                        .addComponent(standAlonesCheckBox))
                                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        generalPanelLayout.setVerticalGroup(
                generalPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(generalPanelLayout.createSequentialGroup()
                                .addGroup(generalPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(longEdgesCheckBox)
                                        .addComponent(resetAdvancedButton, javax.swing.GroupLayout.PREFERRED_SIZE, 18, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(generalPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(advancedAsDefaultButton, javax.swing.GroupLayout.PREFERRED_SIZE, 17, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(irrelevantCodeCheckBox))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(noVIPCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(edgeBendingCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(stabilizedLayoutCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(generalPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(nodeNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(jLabel3))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(blockViewAsControlFlowCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(standAlonesCheckBox))
        );

        generalScrollPane.setViewportView(generalPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(settingsPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(previewCheckBox)
                                .addGap(18, 18, 18)
                                .addComponent(defaultRadioB)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(advancedRadioB)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(presetLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(presetComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addComponent(generalScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 500, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(previewCheckBox)
                                        .addComponent(defaultRadioB)
                                        .addComponent(advancedRadioB)
                                        .addComponent(presetComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(presetLabel))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(generalScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 207, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(settingsPane, javax.swing.GroupLayout.DEFAULT_SIZE, 359, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void advancedToDefault(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_advancedToDefault
        bases.forEach(action -> action.run());
        load();
        settingsChanged();
    }//GEN-LAST:event_advancedToDefault

    private void resetAdvancedButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetAdvancedButtonActionPerformed
        setting.reset();
        load();
        settingsChanged();
    }//GEN-LAST:event_resetAdvancedButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton advancedAsDefaultButton;
    private javax.swing.JRadioButton advancedRadioB;
    private javax.swing.JCheckBox assignDuringCrossingCheckBox;
    private javax.swing.JCheckBox blockViewAsControlFlowCheckBox;
    private javax.swing.JCheckBox bothSortCheckBox;
    private javax.swing.JCheckBox centerCrossingCheckBox;
    private javax.swing.JCheckBox centerSimpleNodesCheckBox;
    private javax.swing.JFormattedTextField crossFactorFormattedTextField;
    private javax.swing.JCheckBox crossingByConnDiffCheckBox;
    private javax.swing.JPanel crossingPanel;
    private javax.swing.JCheckBox crossingReductionDuringRoutingCheckBox;
    private javax.swing.ButtonGroup crossingResetGroup;
    private javax.swing.JCheckBox crossingResetXFromNodeCheckBox;
    private javax.swing.JRadioButton crossingResetXLeftRadioButton;
    private javax.swing.JRadioButton crossingResetXMiddleRadioButton;
    private javax.swing.JScrollPane crossingScrollPane;
    private javax.swing.JCheckBox crossingSortCheckBox;
    private javax.swing.JComboBox<String> crossingSweepCountComboBox;
    private javax.swing.JRadioButton decreaseDeviationDownRadioButton;
    private javax.swing.ButtonGroup decreaseDeviationGroup;
    private javax.swing.JRadioButton decreaseDeviationUpRadioButton;
    private javax.swing.JCheckBox decreaseLayerWidthDeviationCheckBox;
    private javax.swing.JCheckBox decreaseLayerWidthDeviationQuickCheckBox;
    private javax.swing.ButtonGroup defaultLayoutGroup;
    private javax.swing.JRadioButton defaultRadioB;
    private javax.swing.JCheckBox delayDanglingNodesCheckBox;
    private javax.swing.JCheckBox dummyCrossingCheckBox;
    private javax.swing.JCheckBox dummyFirstSortCheckBox;
    private javax.swing.JPanel dummyPanel;
    private javax.swing.JScrollPane dummyScrollPane;
    private javax.swing.JFormattedTextField dummyWidthFormattedTextField;
    private javax.swing.JLabel dummyWidthLabel;
    private javax.swing.JCheckBox edgeBendingCheckBox;
    private javax.swing.JPanel generalPanel;
    private javax.swing.JScrollPane generalScrollPane;
    private javax.swing.JCheckBox irrelevantCodeCheckBox;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JScrollBar jScrollBar1;
    private javax.swing.JCheckBox lastDownSweepCheckBox;
    private javax.swing.JCheckBox lastUpCrossingSweepCheckBox;
    private javax.swing.JPanel layeringPanel;
    private javax.swing.JScrollPane layeringScrollPane;
    private javax.swing.JCheckBox longEdgesCheckBox;
    private javax.swing.JFormattedTextField maxBlockLayerLengthFormattedTextField;
    private javax.swing.JFormattedTextField maxLayerLengthFormattedTextField;
    private javax.swing.JCheckBox meanCheckBox;
    private javax.swing.JFormattedTextField minEdgeAngleFormattedTextField;
    private javax.swing.JCheckBox noCrossingLayerReassignCheckBox;
    private javax.swing.JCheckBox noLongDummyCheckBox;
    private javax.swing.JCheckBox noVIPCheckBox;
    private javax.swing.JTextField nodeNameTextField;
    private javax.swing.JCheckBox optimalUpVIPCheckBox;
    private javax.swing.JComboBox<String> presetComboBox;
    private javax.swing.JLabel presetLabel;
    private javax.swing.JCheckBox previewCheckBox;
    private javax.swing.JCheckBox properCrossingClosestNodeCheckBox;
    private javax.swing.JButton resetAdvancedButton;
    private javax.swing.JCheckBox reverseSortCheckBox;
    private javax.swing.JPanel routingPanel;
    private javax.swing.JScrollPane routingScrollPane;
    private javax.swing.JTabbedPane settingsPane;
    private javax.swing.JCheckBox spanByAngleCheckBox;
    private javax.swing.JCheckBox squashNodesCheckBox;
    private javax.swing.JCheckBox stabilizedLayoutCheckBox;
    private javax.swing.JCheckBox standAlonesCheckBox;
    private javax.swing.JComboBox<String> sweepCountComboBox;
    private javax.swing.JLabel sweepCountLabel;
    private javax.swing.JCheckBox unknownCrossingNumberChangeCheckBox;
    private javax.swing.JCheckBox unreverseVIPsCheckBox;
    private javax.swing.JCheckBox useCrossFactorCheckBox;
    private javax.swing.JPanel writePanel;
    private javax.swing.JScrollPane writeScrollPane;
    private javax.swing.JPanel xAssignPanel;
    private javax.swing.JScrollPane xAssignScrollPane;
    private javax.swing.JPanel yAsignPanel;
    private javax.swing.JScrollPane yAssignScrollPane;
    // End of variables declaration//GEN-END:variables
}
