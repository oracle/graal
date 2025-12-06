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
package at.ssw.visualizer.texteditor.model;

import at.ssw.visualizer.model.Compilation;
import at.ssw.visualizer.model.cfg.BasicBlock;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import java.util.Map;

/**
 * @author Christian Wimmer
 * @author Alexander Reder
 */
public class Text {

    private Compilation compilation;
    private ControlFlowGraph cfg;

    private String text;
    private FoldingRegion[] foldings;
    private Map<String, TextRegion> hyperlinks;
    private Map<String, String> stringHovers;
    private Map<TextRegion, String> regionHovers;
    private Map<String, TextRegion[]> highlighting;
    private Map<BasicBlock, BlockRegion> blocks;
    private Scanner scanner;
    private String mimeType;

    
    public Text(ControlFlowGraph cfg, String text, FoldingRegion[] foldings, Map<String, TextRegion> hyperlinks, Map<String, String> stringHovers, Map<TextRegion, String> regionHovers, Map<String, TextRegion[]> highlighting, Map<BasicBlock, BlockRegion> blocks, Scanner scanner, String mimeType) {
        this.compilation = cfg.getCompilation();
        this.cfg = cfg;
        this.text = text;
        this.foldings = foldings;
        this.hyperlinks = hyperlinks;
        this.stringHovers = stringHovers;
        this.regionHovers = regionHovers;
        this.highlighting = highlighting;
        this.blocks = blocks;
        this.scanner = scanner;
        this.mimeType = mimeType;
    }


    public Compilation getCompilation() {
        return compilation;
    }

    public ControlFlowGraph getCfg() {
        return cfg;
    }

    public String getText() {
        return text;
    }

    public FoldingRegion[] getFoldings() {
        return foldings;
    }

    public TextRegion getHyperlinkTarget(String key) {
        return hyperlinks.get(key);
    }

    public String getStringHover(String key) {
        return stringHovers.get(key);
    }

    public String getRegionHover(int position) {
        for (TextRegion r : regionHovers.keySet()) {
            if (r.getStart() <= position && r.getEnd() >= position) {
                return regionHovers.get(r);
            }
        }
        return null;
    }

    public TextRegion[] getHighlighting(String key) {
        return highlighting.get(key);
    }

    public Map<BasicBlock, BlockRegion> getBlocks() {
        return blocks;
    }
    
    public Scanner getScanner() {
        return scanner;
    }
    
    public String getMimeType() {
        return mimeType;
    }
}
