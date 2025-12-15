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
package at.ssw.visualizer.bc.model;

import at.ssw.visualizer.model.bc.Bytecodes;
import at.ssw.visualizer.bc.BCEditorSupport;
import at.ssw.visualizer.model.Compilation;
import at.ssw.visualizer.model.cfg.BasicBlock;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import at.ssw.visualizer.texteditor.model.BlockRegion;
import at.ssw.visualizer.texteditor.model.FoldingRegion;
import at.ssw.visualizer.texteditor.model.HoverParser;
import at.ssw.visualizer.texteditor.model.Text;
import at.ssw.visualizer.texteditor.model.TextBuilder;
import at.ssw.visualizer.texteditor.model.TextRegion;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.netbeans.api.editor.fold.FoldType;
import org.netbeans.editor.TokenID;
import org.openide.util.Lookup;

/**
 * Builds the text containing the bytecode.
 *
 * @author Alexander Reder
 */
public class BCTextBuilder extends TextBuilder {

    private static final FoldType KIND_BLOCK = new FoldType("...");

    private static final String BLOCK_LIST_PREFIX = "BlockListBuilder ";
    
    public BCTextBuilder() {
        super();
        scanner = new BCScanner();
    }

    @Override
    public Text buildDocument(ControlFlowGraph cfg) {
        BytecodeModel bcModel = Lookup.getDefault().lookup(BytecodeModel.class);
        Bytecodes bytecodes = bcModel.getBytecodes(cfg);
        bytecodes.parseBytecodes();
        
        Compilation compilation = cfg.getCompilation();
        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
        
        String name = cfg.getName();
        if (name.startsWith(BLOCK_LIST_PREFIX)) {
            name = name.substring(BLOCK_LIST_PREFIX.length());
        }
        text.append(name).append("\n");
        text.append(dateFormat.format(compilation.getDate())).append("\n\n");
        
        BasicBlock[] sortedBlocks = sortBlocks(cfg.getBasicBlocks());
        if (sortedBlocks.length == 0) {
            appendAll(bytecodes);
        } else {
            for(int i = 0; i < sortedBlocks.length; i++) {
                appendBlock(sortedBlocks, bytecodes, i);
            }
        }
        text.append("\n\n").append(bytecodes.getEpilogue());
        return buildText(cfg, BCEditorSupport.MIME_TYPE);
    }

    public String buildView(ControlFlowGraph cfg, BasicBlock[] basicBlocks) {
        BytecodeModel bcModel = Lookup.getDefault().lookup(BytecodeModel.class);
        Bytecodes bytecodes = bcModel.getBytecodes(cfg);
        if(bytecodes == null) {
            return "No bytecodes available\n";
        }
        StringBuilder view = new StringBuilder(1024);
        buildDocument(cfg);
        for(BasicBlock b : basicBlocks) {
            BlockRegion br = blocks.get(b);
            view.append(text.substring(br.getStart(), br.getEnd()));
        }
        return view.toString();
    }
    
    private void appendAll(Bytecodes bytecodes) {
        int fromBCI = 0;
        int toBCI = Integer.MAX_VALUE;
        String[] bcs = bytecodes.getBytecodes(fromBCI, toBCI).split("\n");
        for(String s : bcs) {
            appendBytecode(null, s);
        }
    }

    private void appendBlock(BasicBlock[] basicBlocks, Bytecodes bytecodes, int index) {
        int start = text.length();
        int fromBCI = basicBlocks[index].getFromBci();
        int toBCI = Integer.MAX_VALUE;
        if(basicBlocks.length > (index + 1) && basicBlocks[index + 1].getFromBci() >= 0) {
            toBCI = basicBlocks[index + 1].getFromBci();
        }
        appendBlockDetails(basicBlocks[index]);
        text.append("\n");
        int bytecodesStart = text.length();
        String[] bcs = bytecodes.getBytecodes(fromBCI, toBCI).split("\n");
        for(String s : bcs) {
            appendBytecode(basicBlocks[index], s);
        }
        blocks.put(basicBlocks[index], 
                new BlockRegion(basicBlocks[index], start, text.length(), start, start + basicBlocks[index].getName().length()));
        foldingRegions.add(new FoldingRegion(KIND_BLOCK, bytecodesStart - 1, text.length() - 1, false));
        hyperlinks.put(basicBlocks[index].getName(), new TextRegion(start, start + basicBlocks[index].getName().length()));
    }
    
    private void appendBytecode(BasicBlock block, String bytecode) {
        int start = text.length();
        HoverParser p = new HoverParser(bytecode);
        while (p.hasNext()) {
            int pstart = text.length();
            text.append(p.next());
            if (p.getHover() != null) {
                regionHovers.put(new TextRegion(pstart, text.length()), p.getHover());
            }
        }
        text.append("\n");
        scanner.setText(bytecode, 0, bytecode.length());
        TokenID token = scanner.nextToken();
        String tokenString;
        while(token != null && token.getNumericID() != BCTokenContext.EOF_TOKEN_ID) {
            tokenString = scanner.getTokenString();
            if(token.getNumericID() == BCTokenContext.BCI_TOKEN_ID) {
                if(scanner.getTokenString().startsWith("#")) {
                    addReference(block, tokenString, bytecode);
                    addReference(block, tokenString.substring(1, tokenString.length()), bytecode);
                } else {
                    hoverKeys.add(tokenString);
                    hoverKeys.add('#' + tokenString);
                    addDefinition(block, tokenString, bytecode);
                    addDefinition(block, '#' + tokenString, bytecode);
                    hyperlinks.put('#' + tokenString, new TextRegion(start + scanner.getTokenOffset(), start + scanner.getOffset()));
                }
            } else if(token.getNumericID() == BCTokenContext.VAR_REFERENCE_TOKEN_ID) {
                hoverKeys.add(tokenString);
                addReference(block, tokenString, bytecode);
            }
            token = scanner.nextToken();
        }
    }
    
    private void addReference(BasicBlock block, String key, String s) {
        if (block == null) {
            return;
        }
        if(!hoverReferences.containsKey(key)) {
            hoverReferences.put(key, new ArrayList<String>());
        }
        hoverReferences.get(key).add(block.getName() + ":\t" + s);
    }
    
    private void addDefinition(BasicBlock block, String key, String s) {
        if (block == null) {
            return;
        }
        hoverDefinitions.put(key, block.getName() + ":\t" + s);
    }
    
    @Override
    protected void buildHighlighting() {
        scanner.setText(text.toString(), 0, text.length());
        TokenID token = scanner.nextToken();
        Map<String, List<TextRegion>> highlightings = new HashMap<String, List<TextRegion>>();
        while(token != null && token.getNumericID() != BCTokenContext.EOF_TOKEN_ID) {
            String key = scanner.getTokenString();
            switch(token.getNumericID()) {
                case BCTokenContext.BCI_TOKEN_ID:
                    if(key.startsWith("#")) {
                        key = key.substring(1, key.length());
                    }
                    List<TextRegion> tr = new ArrayList<TextRegion>();
                    if(!highlightings.containsKey(key)) {
                        highlightings.put(key, tr);
                        highlightings.put('#' + key, tr);
                    }
                    highlightings.get(key).add(new TextRegion(scanner.getTokenOffset(), scanner.getOffset()));
                break;
                case BCTokenContext.BLOCK_TOKEN_ID:
                case BCTokenContext.VAR_REFERENCE_TOKEN_ID:
                    if(!highlightings.containsKey(key)) {
                        highlightings.put(key, new ArrayList<TextRegion>());
                    }
                    highlightings.get(key).add(new TextRegion(scanner.getTokenOffset(), scanner.getOffset()));
                break;
            }
            token = scanner.nextToken();
        }
        for(String key : highlightings.keySet()) {
            List<TextRegion> regions = highlightings.get(key);
            highlighting.put(key, regions.toArray(new TextRegion[regions.size()]));
        }
    }
    
    /**
     * Sorts the blocks on the basis of the starting BCI. BCIs &lt 0 will be
     * placed at the end of the array.
     *
     * @param   blocks  array containing the unsorted blocks
     * @return          sorted array of blocks
     */
    protected BasicBlock[] sortBlocks(List<BasicBlock> blocks) {
        List<BasicBlock> blockList = new ArrayList<BasicBlock>(blocks);
        Collections.sort(blockList, new Comparator<BasicBlock>() {

            public int compare(BasicBlock b1, BasicBlock b2) {
                if (b1.getFromBci() >= 0 && b2.getFromBci() < 0) {
                    return -1;
                }
                if (b1.getFromBci() < 0 && b2.getFromBci() >= 0) {
                    return 1;
                }
                if(b1 != b2 && b1.getFromBci() == b2.getFromBci()) {
                    return -1;
                }
                return b1.getFromBci() - b2.getFromBci();
            }
        });
        return blockList.toArray(new BasicBlock[blocks.size()]);
    }
    
}
