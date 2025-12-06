/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package at.ssw.visualizer.cfg.preferences;

import java.awt.Color;
import java.awt.Font;

/**
 * Default Configuration for options panel
 * 
 * @author Rumpfhuber Stefan
 */
public final class CfgPreferencesDefaults {    
    public static final String DEFAULT_FLAGSTRING = "std(224,224,128);osr(224,224,0);ex(128,128,224);sr(128,224,128);llh(224,128,128);lle(224,192,192);plh(128,224,128);bb(160,0,0);ces(192,192,192)";
    public static final Color DEFAUT_NODE_COLOR = new Color(208, 208, 208);
    public static final Color DEFAULT_BACKGROUND_COLOR =  new Color(255, 255, 255);
    public static final Color DEFAULT_BACKEDGE_COLOR = new Color(160, 0, 0);
    public static final Color DEFAULT_EDGE_COLOR = new Color(0, 0, 0);
    public static final Color DEFAULT_SELECTION_COLOR_FOREGROUND = Color.BLUE;
    public static final Color DEFAULT_SELECTION_COLOR_BACKGROUND = Color.BLUE;
    public static final Color DEFAULT_BORDER_COLOR = new Color(0, 0, 0);
    public static final Color DEFAULT_EXCEPTIONEDGE_COLOR = new Color(0, 0, 160);
    public static final Color DEFAULT_TEXT_COLOR = new Color(0, 0, 0);
    public static final Font  DEFAULT_TEXT_FONT = new Font("Dialog", Font.PLAIN, 18);
}
