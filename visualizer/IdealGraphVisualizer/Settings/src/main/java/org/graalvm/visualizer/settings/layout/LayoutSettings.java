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
package org.graalvm.visualizer.settings.layout;

import org.graalvm.visualizer.settings.SettingsStore;
import org.graalvm.visualizer.settings.layout.LayoutSettings.LayoutSettingBean;

import java.util.function.BiConsumer;

/**
 * @author Ondřej Douda <ondrej.douda@oracle.com>
 */
public class LayoutSettings extends SettingsStore<LayoutSettings, LayoutSettingBean> {

    public static final String SPAN_BY_ANGLE = "SPAN_BY_ANGLE";// NOI18N
    public static final boolean SPAN_BY_ANGLE_DEFAULT = true;

    public static final String MIN_EDGE_ANGLE = "MIN_EDGE_ANGLE";// NOI18N
    public static final int MIN_EDGE_ANGLE_DEFAULT = 10;

    public static final String X_ASSIGN_SWEEP_COUNT = "X_ASSIGN_SWEEP_COUNT";// NOI18N
    public static final int X_ASSIGN_SWEEP_COUNT_DEFAULT = 1;

    public static final String DEFAULT_LAYOUT = "DEFAULT_LAYOUT";// NOI18N
    public static final boolean DEFAULT_LAYOUT_DEFAULT = false;

    public static final String BOTH_SORT = "BOTH_SORT";// NOI18N
    public static final boolean BOTH_SORT_DEFAULT = true;

    public static final String OPTIMAL_UP_VIP = "OPTIMAL_UP_VIP";// NOI18N
    public static final boolean OPTIMAL_UP_VIP_DEFAULT = true;

    public static final String IRRELEVANT_LAYOUT_CODE = "IRRELEVANT_LAYOUT_CODE";// NOI18N
    public static final boolean IRRELEVANT_LAYOUT_CODE_DEFAULT = false;

    public static final String NO_VIP = "NO_VIP";// NOI18N
    public static final boolean NO_VIP_DEFAULT = false;

    public static final String MEAN_NOT_MEDIAN = "MEAN_MEDIAN";// NOI18N
    public static final boolean MEAN_NOT_MEDIAN_DEFAULT = true;

    public static final String NO_DUMMY_LONG_EDGES = "NO_DUMMY_LONG_EDGES";// NOI18N
    public static final boolean NO_DUMMY_LONG_EDGES_DEFAULT = true;

    public static final String SQUASH_POSITION = "SQUASH_POSITION";// NOI18N
    public static final boolean SQUASH_POSITION_DEFAULT = true;

    public static final String REVERSE_SORT = "REVERSE_SORT";// NOI18N
    public static final boolean REVERSE_SORT_DEFAULT = true;

    public static final String CROSSING_SORT = "CROSSING_SORT";// NOI18N
    public static final boolean CROSSING_SORT_DEFAULT = true;

    public static final String PROPER_CROSSING_CLOSEST_NODE = "PROPER_CROSSING_CLOSEST_NODE";// NOI18N
    public static final boolean PROPER_CROSSING_CLOSEST_NODE_DEFAULT = true;

    public static final String CENTER_CROSSING_X = "CENTER_CROSSING_X";// NOI18N
    public static final boolean CENTER_CROSSING_X_DEFAULT = true;

    public static final String UNKNOWN_CROSSING_NUMBER = "UNKNOWN_CROSSING_NUMBER";// NOI18N
    public static final boolean UNKNOWN_CROSSING_NUMBER_DEFAULT = false;

    public static final String LAST_UP_CROSSING_SWEEP = "LAST_UP_CROSSING_SWEEP";// NOI18N
    public static final boolean LAST_UP_CROSSING_SWEEP_DEFAULT = false;

    public static final String NO_CROSSING_LAYER_REASSIGN = "NO_CROSSING_LAYER_REASSIGN";// NOI18N
    public static final boolean NO_CROSSING_LAYER_REASSIGN_DEFAULT = true;

    public static final String LAST_DOWN_SWEEP = "LAST_DOWN_SWEEP";// NOI18N
    public static final boolean LAST_DOWN_SWEEP_DEFAULT = true;

    public static final String DRAW_LONG_EDGES = "DRAW_LONG_EDGES";// NOI18N
    public static final boolean DRAW_LONG_EDGES_DEFAULT = false;

    public static final String ACTIVE_PREVIEW = "ACTIVE_PREVIEW";// NOI18N
    public static final boolean ACTIVE_PREVIEW_DEFAULT = false;

    public static final String DUMMY_CROSSING = "DUMMY_CROSSING";// NOI18N
    public static final boolean DUMMY_CROSSING_DEFAULT = false;

    public static final String EDGE_BENDING = "EDGE_BENDING";// NOI18N
    public static final boolean EDGE_BENDING_DEFAULT = true;

    public static final String DUMMY_FIRST_SORT = "DUMMY_FIRST_SORT";// NOI18N
    public static final boolean DUMMY_FIRST_SORT_DEFAULT = false;

    public static final String STABILIZED_LAYOUT = "STABILIZED_LAYOUT";// NOI18N
    public static final boolean STABILIZED_LAYOUT_DEFAULT = true;

    public static final String NODE_TEXT = "NODE_TEXT";// NOI18N
    public static final String NODE_TEXT_DEFAULT = "[idx] [name]"; // NOI18N

    public static final String DUMMY_WIDTH = "DUMMY_WIDTH";// NOI18N
    public static final int DUMMY_WIDTH_DEFAULT = 2;

    public static final String SHOW_BLOCKS = "SHOW_BLOCKS";// NOI18N
    public static final boolean SHOW_BLOCKS_DEFAULT = false;

    public static final String UNREVERSE_VIPS = "UNREVERSE_VIPS";// NOI18N
    public static final boolean UNREVERSE_VIPS_DEFAULT = true;

    public static final String MAX_LAYER_LENGTH = "MAX_LAYER_LENGTH";// NOI18N
    public static final int MAX_LAYER_LENGTH_DEFAULT = 10;

    public static final String MAX_BLOCK_LAYER_LENGTH = "MAX_BLOCK_LAYER_LENGTH";// NOI18N
    public static final int MAX_BLOCK_LAYER_LENGTH_DEFAULT = 3;

    public static final String CENTER_SIMPLE_NODES = "CENTER_SIMPLE_NODES";// NOI18N
    public static final boolean CENTER_SIMPLE_NODES_DEFAULT = true;

    public static final String CROSSING_SWEEP_COUNT = "CROSSING_SWEEP_COUNT";// NOI18N
    public static final int CROSSING_SWEEP_COUNT_DEFAULT = 2;

    public static final String CROSS_BY_CONN_DIFF = "CROSS_BY_CONN_DIFF";// NOI18N
    public static final boolean CROSS_BY_CONN_DIFF_DEFAULT = true;

    public static final String CROSS_RESET_X_FROM_NODE = "CROSS_RESET_X_FROM_NODE";// NOI18N
    public static final boolean CROSS_RESET_X_FROM_NODE_DEFAULT = true;

    public static final String CROSS_RESET_X_FROM_MIDDLE = "CROSS_RESET_X_FROM_MIDDLE";// NOI18N
    public static final boolean CROSS_RESET_X_FROM_MIDDLE_DEFAULT = true;

    public static final String CROSS_USE_FACTOR = "CROSS_USE_FACTOR";// NOI18N
    public static final boolean CROSS_USE_FACTOR_DEFAULT = true;

    public static final String CROSS_FACTOR = "CROSS_FACTOR";// NOI18N
    public static final float CROSS_FACTOR_DEFAULT = 0.9f;

    public static final String CROSS_POSITION_DURING = "CROSS_POSITION_DURING";// NOI18N
    public static final boolean CROSS_POSITION_DURING_DEFAULT = true;

    public static final String DELAY_DANGLING_NODES = "DELAY_DANGLING_NODES";// NOI18N
    public static final boolean DELAY_DANGLING_NODES_DEFAULT = true;

    public static final String DECREASE_LAYER_WIDTH_DEVIATION = "DECREASE_LAYER_WIDTH_DEVIATION";// NOI18N
    public static final boolean DECREASE_LAYER_WIDTH_DEVIATION_DEFAULT = true;

    public static final String DECREASE_LAYER_WIDTH_DEVIATION_QUICK = "DECREASE_LAYER_WIDTH_DEVIATION_QUICK";// NOI18N
    public static final boolean DECREASE_LAYER_WIDTH_DEVIATION_QUICK_DEFAULT = true;

    public static final String DECREASE_LAYER_WIDTH_DEVIATION_UP = "DECREASE_LAYER_WIDTH_DEVIATION_UP";// NOI18N
    public static final boolean DECREASE_LAYER_WIDTH_DEVIATION_UP_DEFAULT = false;

    public static final String CROSS_REDUCE_ROUTING = "CROSS_REDUCE_ROUTING";// NOI18N
    public static final boolean CROSS_REDUCE_ROUTING_DEFAULT = true;

    public static final String BLOCKVIEW_AS_CONTROLFLOW = "BLOCKVIEW_AS_CONTROLFLOW";// NOI18N
    public static final boolean BLOCKVIEW_AS_CONTROLFLOW_DEFAULT = true;

    public static final String STANDALONES = "STANDALONES";// NOI18N
    public static final boolean STANDALONES_DEFAULT = true;

    public static LayoutSettings obtain() {
        return obtain(LayoutSettings.class, LayoutSettings::new);
    }

    public static LayoutSettingBean getBean() {
        return obtain().obtainBean();
    }

    private LayoutSettings() {
    }

    @Override
    protected void fillDefaults(BiConsumer<String, Object> filler) {
        //Master
        filler.accept(DEFAULT_LAYOUT, DEFAULT_LAYOUT_DEFAULT);
        filler.accept(ACTIVE_PREVIEW, ACTIVE_PREVIEW_DEFAULT);
        //General
        filler.accept(DRAW_LONG_EDGES, DRAW_LONG_EDGES_DEFAULT);
        filler.accept(IRRELEVANT_LAYOUT_CODE, IRRELEVANT_LAYOUT_CODE_DEFAULT);
        filler.accept(NO_VIP, NO_VIP_DEFAULT);
        filler.accept(NODE_TEXT, NODE_TEXT_DEFAULT);
        filler.accept(EDGE_BENDING, EDGE_BENDING_DEFAULT);
        //@Property(name="showBlocks", type=boolean.class),
        filler.accept(BLOCKVIEW_AS_CONTROLFLOW, BLOCKVIEW_AS_CONTROLFLOW_DEFAULT);
        filler.accept(STANDALONES, STANDALONES_DEFAULT);
        filler.accept(STABILIZED_LAYOUT, STABILIZED_LAYOUT_DEFAULT);//Enabled routing
        //Routing
        filler.accept(CROSS_REDUCE_ROUTING, CROSS_REDUCE_ROUTING_DEFAULT);
        //Layering
        filler.accept(UNREVERSE_VIPS, UNREVERSE_VIPS_DEFAULT);
        filler.accept(MAX_LAYER_LENGTH, MAX_LAYER_LENGTH_DEFAULT);
        filler.accept(MAX_BLOCK_LAYER_LENGTH, MAX_BLOCK_LAYER_LENGTH_DEFAULT);
        filler.accept(DECREASE_LAYER_WIDTH_DEVIATION, DECREASE_LAYER_WIDTH_DEVIATION_DEFAULT);
        filler.accept(DECREASE_LAYER_WIDTH_DEVIATION_QUICK, DECREASE_LAYER_WIDTH_DEVIATION_QUICK_DEFAULT);
        filler.accept(DECREASE_LAYER_WIDTH_DEVIATION_UP, DECREASE_LAYER_WIDTH_DEVIATION_UP_DEFAULT);
        //Dummy
        filler.accept(NO_DUMMY_LONG_EDGES, NO_DUMMY_LONG_EDGES_DEFAULT);
        filler.accept(DUMMY_CROSSING, DUMMY_CROSSING_DEFAULT);
        filler.accept(DUMMY_WIDTH, DUMMY_WIDTH_DEFAULT);
        //Crossing
        filler.accept(NO_CROSSING_LAYER_REASSIGN, NO_CROSSING_LAYER_REASSIGN_DEFAULT);
        filler.accept(CROSSING_SORT, CROSSING_SORT_DEFAULT);
        filler.accept(CENTER_CROSSING_X, CENTER_CROSSING_X_DEFAULT);
        filler.accept(LAST_UP_CROSSING_SWEEP, LAST_UP_CROSSING_SWEEP_DEFAULT);
        filler.accept(PROPER_CROSSING_CLOSEST_NODE, PROPER_CROSSING_CLOSEST_NODE_DEFAULT);
        filler.accept(UNKNOWN_CROSSING_NUMBER, UNKNOWN_CROSSING_NUMBER_DEFAULT);
        filler.accept(CROSS_BY_CONN_DIFF, CROSS_BY_CONN_DIFF_DEFAULT);
        filler.accept(CROSSING_SWEEP_COUNT, CROSSING_SWEEP_COUNT_DEFAULT);
        filler.accept(CROSS_RESET_X_FROM_NODE, CROSS_RESET_X_FROM_NODE_DEFAULT);
        filler.accept(CROSS_RESET_X_FROM_MIDDLE, CROSS_RESET_X_FROM_MIDDLE_DEFAULT);
        filler.accept(CROSS_FACTOR, CROSS_FACTOR_DEFAULT);
        filler.accept(CROSS_USE_FACTOR, CROSS_USE_FACTOR_DEFAULT);
        filler.accept(CROSS_POSITION_DURING, CROSS_POSITION_DURING_DEFAULT);
        //Xassign
        filler.accept(X_ASSIGN_SWEEP_COUNT, X_ASSIGN_SWEEP_COUNT_DEFAULT);
        filler.accept(OPTIMAL_UP_VIP, OPTIMAL_UP_VIP_DEFAULT);
        filler.accept(LAST_DOWN_SWEEP, LAST_DOWN_SWEEP_DEFAULT);
        filler.accept(SQUASH_POSITION, SQUASH_POSITION_DEFAULT);
        filler.accept(BOTH_SORT, BOTH_SORT_DEFAULT);
        filler.accept(REVERSE_SORT, REVERSE_SORT_DEFAULT);
        filler.accept(MEAN_NOT_MEDIAN, MEAN_NOT_MEDIAN_DEFAULT);
        filler.accept(DUMMY_FIRST_SORT, DUMMY_FIRST_SORT_DEFAULT);
        filler.accept(CENTER_SIMPLE_NODES, CENTER_SIMPLE_NODES_DEFAULT);
        filler.accept(DELAY_DANGLING_NODES, DELAY_DANGLING_NODES_DEFAULT);
        //Yassign
        filler.accept(SPAN_BY_ANGLE, SPAN_BY_ANGLE_DEFAULT);
        filler.accept(MIN_EDGE_ANGLE, MIN_EDGE_ANGLE_DEFAULT);
        //Write
        //Experimental
        //Other
        filler.accept(SHOW_BLOCKS, SHOW_BLOCKS_DEFAULT);
    }

    @Override
    protected LayoutSettingBean makeBean() {
        return new LayoutSettingBean(this);
    }

    public static final class LayoutSettingBean extends SettingsBean<LayoutSettings, LayoutSettingBean> {

        protected LayoutSettingBean(LayoutSettings store) {
            super(store);
        }

        private LayoutSettingBean(LayoutSettingBean bean) {
            super(bean);
        }

        @Override
        public LayoutSettingBean copy() {
            return new LayoutSettingBean(this);
        }
    }
}
