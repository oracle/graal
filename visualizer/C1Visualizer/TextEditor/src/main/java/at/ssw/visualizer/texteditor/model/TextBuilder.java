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

import at.ssw.visualizer.model.cfg.BasicBlock;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Alexander Reder
 */
public abstract class TextBuilder {

    protected StringBuilder text;
    protected Scanner scanner;
    protected List<FoldingRegion> foldingRegions;
    protected Map<String, TextRegion> hyperlinks;
    protected Map<String, String> stringHovers;
    protected Map<TextRegion, String> regionHovers;
    protected Map<String, TextRegion[]> highlighting;
    protected Map<BasicBlock, BlockRegion> blocks;
    protected Set<String> hoverKeys;
    protected Map<String, String> hoverDefinitions;
    protected Map<String, List<String>> hoverReferences;
    
    public TextBuilder() {
        text = new StringBuilder(4 * 1024);
        foldingRegions = new ArrayList<FoldingRegion>();
        hyperlinks = new HashMap<String, TextRegion>();
        stringHovers = new HashMap<String, String>();
        regionHovers = new HashMap<TextRegion, String>();
        highlighting = new HashMap<String, TextRegion[]>();
        blocks = new HashMap<BasicBlock, BlockRegion>();
        hoverKeys = new HashSet<String>();
        hoverDefinitions = new HashMap<String, String>();
        hoverReferences = new HashMap<String, List<String>>();
    }
    
    public abstract Text buildDocument(ControlFlowGraph cfg);
    
    protected abstract void buildHighlighting();
    
    protected Text buildText(ControlFlowGraph cfg, String mimeType) {
        buildHovers();
        buildHighlighting();
        return new Text(cfg, text.toString(), foldingRegions.toArray(new FoldingRegion[foldingRegions.size()]), hyperlinks, stringHovers, regionHovers, highlighting, blocks, scanner, mimeType);
    }
    
    protected void appendBlockDetails(BasicBlock block) {
        text.append(blockDetails(block));
    }
        
    protected String blockDetails(BasicBlock block) {
        StringBuilder sb = new StringBuilder();
        sb.append(block.getName());
        hoverKeys.add(block.getName());
        appendBlockList(sb, " <- ", block.getPredecessors());
        appendBlockList(sb, " -> ", block.getSuccessors());
        appendBlockList(sb, " xh ", block.getXhandlers());
        if (block.getDominator() != null) {
            sb.append(" dom ").append(block.getDominator().getName());
        }
        sb.append(" [").append(block.getFromBci()).append(", ").append(block.getToBci()).append("]");
        appendList(sb, " ", block.getFlags());

        if (block.getLoopDepth() > 0) {
            sb.append(" (loop ").append(block.getLoopIndex()).append(" depth ").append(block.getLoopDepth()).append(")");
        }
        return sb.toString();
    }
    
    protected void appendBlockList(StringBuilder sb, String prefix, List<BasicBlock> blocks) {
        for (BasicBlock block : blocks) {
            sb.append(prefix);
            prefix = ",";
            sb.append(block.getName());
        }
    }
    
    private void appendList(StringBuilder sb, String prefix, List<String> values) {
        for (String value : values) {
            sb.append(prefix).append(value);
            prefix = ",";
        }
    }
    
    protected void buildHovers() {
        StringBuilder sb;
        for(String key : hoverKeys) {
            sb = new StringBuilder();
            if(hoverDefinitions.containsKey(key) && hoverReferences.containsKey(key)) {
                sb.append("Definition;\n");
                sb.append(hoverDefinitions.get(key));
                sb.append("\n");
            }
            if(hoverReferences.containsKey(key)) {
                sb.append("References:\n");
                for(String ref : hoverReferences.get(key)) {
                    sb.append(ref);
                    sb.append("\n");
                }
            }
            if(sb.length() > 0) {
                stringHovers.put(key, sb.toString().substring(0, sb.length() - 1));
            }            
        }
    }
    
}
