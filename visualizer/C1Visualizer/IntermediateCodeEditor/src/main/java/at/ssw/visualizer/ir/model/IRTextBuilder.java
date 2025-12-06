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
package at.ssw.visualizer.ir.model;

import at.ssw.visualizer.ir.IREditorSupport;
import at.ssw.visualizer.model.Compilation;
import at.ssw.visualizer.model.cfg.BasicBlock;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import at.ssw.visualizer.model.cfg.IRInstruction;
import at.ssw.visualizer.model.cfg.State;
import at.ssw.visualizer.model.cfg.StateEntry;
import at.ssw.visualizer.texteditor.model.BlockRegion;
import at.ssw.visualizer.texteditor.model.FoldingRegion;
import at.ssw.visualizer.texteditor.model.HoverParser;
import at.ssw.visualizer.texteditor.model.Text;
import at.ssw.visualizer.texteditor.model.TextBuilder;
import at.ssw.visualizer.texteditor.model.TextRegion;
import org.netbeans.api.editor.fold.FoldType;
import org.netbeans.editor.TokenID;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Christian Wimmer
 */
public class IRTextBuilder extends TextBuilder {

    public static final FoldType KIND_BLOCK = new FoldType("...");
    public static final FoldType KIND_STATE_WITH_PHIS = new FoldType("(State)");
    public static final FoldType KIND_STATE_WITHOUT_PHIS = new FoldType("(State)");
    public static final FoldType KIND_HIR = new FoldType("(HIR)");
    public static final FoldType KIND_LIR = new FoldType("(LIR)");
    public static final FoldType KIND_MULTILINE = new FoldType("...");
    private String[] hirColumnNames;
    private int[] hirColumnStarts;
    private String[] lirColumnNames;
    private int[] lirColumnStarts;

    public IRTextBuilder() {
        super();
        scanner = new IRScanner();
    }

    public String buildState(ControlFlowGraph cfg, BasicBlock[] blocks) {
        defineColumns(cfg);

        Set<BasicBlock> blockSet = new HashSet<BasicBlock>(Arrays.asList(blocks));
        for (BasicBlock block : cfg.getBasicBlocks()) {
            if (block.hasState() && blockSet.contains(block)) {
                appendBlockDetails(block);
                text.append("\n");
                appendStates(block);
                text.append("\n");
            }
        }
        if (text.length() == 0) {
            return "No State available\n";
        }
        return text.toString();
    }

    public String buildHir(ControlFlowGraph cfg, BasicBlock[] blocks) {
        defineColumns(cfg);

        Set<BasicBlock> blockSet = new HashSet<BasicBlock>(Arrays.asList(blocks));
        for (BasicBlock block : cfg.getBasicBlocks()) {
            if (block.hasHir() && blockSet.contains(block)) {
                appendBlockDetails(block);
                text.append("\n");
                appendHir(block);
                text.append("\n");
            }
        }
        if (text.length() == 0) {
            return "No HIR available\n";
        }
        return text.toString();
    }

    public String buildLir(ControlFlowGraph cfg, BasicBlock[] blocks) {
        defineColumns(cfg);

        Set<BasicBlock> blockSet = new HashSet<BasicBlock>(Arrays.asList(blocks));
        for (BasicBlock block : cfg.getBasicBlocks()) {
            if (block.hasLir() && blockSet.contains(block)) {
                appendBlockDetails(block);
                text.append("\n");
                appendLir(block);
                text.append("\n");
            }
        }
        if (text.length() == 0) {
            return "No LIR available\n";
        }
        return text.toString();
    }

    public Text buildDocument(ControlFlowGraph cfg) {
        Compilation compilation = cfg.getCompilation();
        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);

        text.append(compilation.getMethod()).append("\n");
        text.append(dateFormat.format(compilation.getDate())).append("\n");
        text.append(cfg.getName()).append("\n\n");

        defineColumns(cfg);

        for (BasicBlock block : cfg.getBasicBlocks()) {
            appendBlock(cfg, block);
        }
        return buildText(cfg, IREditorSupport.MIME_TYPE);
    }

    protected void defineColumns(ControlFlowGraph cfg) {
        ArrayList<String> hirColumnNames = new ArrayList<String>();
        ArrayList<Integer> hirColumnWidths = new ArrayList<Integer>();
        ArrayList<String> lirColumnNames = new ArrayList<String>();
        ArrayList<Integer> lirColumnWidths = new ArrayList<Integer>();

        for (BasicBlock block : cfg.getBasicBlocks()) {
            if (cfg.hasHir()) {
                defineColumns(block.getHirInstructions(), hirColumnNames, hirColumnWidths);
            }
            if (cfg.hasLir()) {
                defineColumns(block.getLirOperations(), lirColumnNames, lirColumnWidths);
            }
        }

        this.hirColumnNames = hirColumnNames.toArray(new String[hirColumnNames.size()]);
        this.hirColumnStarts = new int[this.hirColumnNames.length];
        int start = 1;
        for (int i = 0; i < this.hirColumnStarts.length; i++) {
            this.hirColumnStarts[i] = start;
            start += hirColumnWidths.get(i) + 2;
        }
        this.lirColumnNames = lirColumnNames.toArray(new String[lirColumnNames.size()]);
        this.lirColumnStarts = new int[this.lirColumnNames.length];
        start = 1;
        for (int i = 0; i < this.lirColumnStarts.length; i++) {
            this.lirColumnStarts[i] = start;
            start += lirColumnWidths.get(i) + 2;
        }

    }

    protected void defineColumns(List<IRInstruction> instructions, List<String> columnNames, List<Integer> columnWidths) {
        for (IRInstruction instr : instructions) {
            int prevIdx = -1;
            for (String name : instr.getNames()) {
                int idx = columnNames.indexOf(name);

                int width = Math.min(HoverParser.firstLine(instr.getValue(name)).length(), 15);

                if (idx == -1) {
                    width = Math.max(width, name.length());
                    columnNames.add(prevIdx + 1, name);
                    columnWidths.add(prevIdx + 1, width);
                    prevIdx = prevIdx + 1;
                } else {
                    int oldWidth = columnWidths.get(idx);
                    if (width > oldWidth) {
                        columnWidths.set(idx, width);
                    }
                    prevIdx = idx;
                }
            }
        }
    }

    protected void buildHighlighting() {
        // scan the entire content string because it is easier than
        // computing information during construction of the string
        HashMap<String, List<TextRegion>> highlightingLists = new HashMap<String, List<TextRegion>>();
        scanner.setText(text.toString(), 0, text.length());
        TokenID token = scanner.nextToken();
        while (token != null && token.getNumericID() != IRTokenContext.EOF_TOKEN_ID) {
            if (token.getNumericID() == IRTokenContext.HIR_TOKEN_ID || token.getNumericID() == IRTokenContext.LIR_TOKEN_ID || token.getNumericID() == IRTokenContext.BLOCK_TOKEN_ID) {
                String key = scanner.getTokenString();
                List<TextRegion> list = highlightingLists.get(key);
                if (list == null) {
                    list = new ArrayList<TextRegion>();
                    highlightingLists.put(key, list);
                }
                list.add(new TextRegion(scanner.getTokenOffset(), scanner.getOffset())); //getTokenStart() gteTokenEnd()
            }
            token = scanner.nextToken();
        }

        for (String key : highlightingLists.keySet()) {
            List<TextRegion> list = highlightingLists.get(key);
            highlighting.put(key, list.toArray(new TextRegion[list.size()]));
        }
    }

    private void appendBlock(ControlFlowGraph cfg, BasicBlock block) {
        int start = text.length();
        List<FoldingRegion> blockFoldings = new ArrayList<FoldingRegion>();

        appendBlockDetails(block);
        int bodyStart = text.length();
        text.append("\n");

        if (block.hasState()) {
            blockFoldings.add(appendStates(block));
        }
        if (block.hasHir()) {
            blockFoldings.add(appendHir(block));
        }
        if (block.hasLir()) {
            blockFoldings.add(appendLir(block));
        }

        // record foldings (no nested foldings if only one detail block is present)
        if (blockFoldings.size() > 0) {
            text.append("  \n");
            foldingRegions.add(new FoldingRegion(KIND_BLOCK, bodyStart, text.length() - 1, false));
            if (blockFoldings.size() > 1) {
                for (FoldingRegion folding : blockFoldings) {
                    foldingRegions.add(folding);
                }
            }
        }

        // record definition and hyperlink target
        recordBlock(block, new TextRegion(start, start + block.getName().length()));

        // record block boundary information
        blocks.put(block, new BlockRegion(block, start, text.length(), start, start + block.getName().length()));
    }

    private void appendBlockDetails(StringBuilder sb, BasicBlock block) {
        sb.append(blockDetails(block));
    }

    private void recordBlock(BasicBlock block, TextRegion hyperlinkTarget) {
        StringBuilder blockText = new StringBuilder();
        appendBlockDetails(blockText, block);
        recordDefinition(block.getName(), blockText.toString(), hyperlinkTarget);
    }

    private FoldingRegion appendStates(BasicBlock block) {
        boolean hasPhiOperands = false;
        int start = text.length();

        if (block.hasState()) {
            for (State state : block.getStates()) {
                hasPhiOperands |= appendState(block, state);
            }
        }

        return new FoldingRegion(hasPhiOperands ? KIND_STATE_WITH_PHIS : KIND_STATE_WITHOUT_PHIS, start, text.length(), !hasPhiOperands);
    }

    private boolean appendState(BasicBlock block, State state) {
        boolean hasPhiOperands = false;

        text.append("  ").append(state.getKind()).append(" size ").append(state.getSize());
        if (state.getMethod().length() > 0) {
            text.append(" [").append(state.getMethod()).append("]");
        }
        text.append("\n");

        for (StateEntry entry : state.getEntries()) {
            int lineStart = text.length();
            text.append("    ");
            append(entry.getIndex(), 5);
            append(entry.getName(), 5);

            if (appendPhiOperands(text, block, entry)) {
                if (entry.getOperand() != null) {
                    text.append(" - ").append(entry.getOperand());
                }

                hasPhiOperands = true;
                TextRegion hyperlinkTarget = new TextRegion(lineStart, text.length());
                recordPhiFunction(block, entry, hyperlinkTarget);
            }
            text.append("\n");
        }

        return hasPhiOperands;
    }

    private void recordPhiFunction(BasicBlock block, StateEntry entry, TextRegion hyperlinkTarget) {
        StringBuilder sb = new StringBuilder();
        sb.append(block.getName()).append(" - ").append(entry.getName());
        if (entry.getOperand() != null) {
            sb.append(" ").append(entry.getOperand());
        }
        sb.append(" : ");

        int start = sb.length();
        appendPhiOperands(sb, block, entry);
        String s = sb.toString();

        recordDefinition(entry.getName(), s, hyperlinkTarget);
        if (entry.getOperand() != null) {
            recordDefinition(entry.getOperand().substring(1, entry.getOperand().length() - 1), s, hyperlinkTarget);
        }
        recordUses(s, start, s.length() - start);
    }

    private boolean appendPhiOperands(StringBuilder sb, BasicBlock block, StateEntry entry) {
        if (entry.hasPhiOperands()) {
            sb.append("[");
            appendList(sb, "", entry.getPhiOperands());
            sb.append("]");
            return true;
        } else if (block.getPredecessors().size() == 0) {
            sb.append("[method parameter]");
            return true;
        } else {
            return false;
        }
    }

    private FoldingRegion appendHir(BasicBlock block) {
        int start = text.length();
        appendColumnHeader(hirColumnNames, hirColumnStarts, " (HIR)");
        for (IRInstruction instruction : block.getHirInstructions()) {
            int lineStart = text.length();
            appendColumn(instruction, hirColumnNames, hirColumnStarts);
            TextRegion hyperlinkTarget = new TextRegion(lineStart, text.length() - 1);
            recordHir(block, instruction, hyperlinkTarget);
        }

        return new FoldingRegion(KIND_HIR, start, text.length(), false);
    }

    protected void appendColumnHeader(String[] columnNames, int[] columnStarts, String descr) {
        int lineStart = text.length();
        for (int i = 0; i < columnNames.length; i++) {
            fillTo(columnStarts[i], lineStart, '_');
            text.append(columnNames[i]);
        }
        fillTo(80, lineStart, '_');
        text.append(descr).append("\n");
    }

    protected void appendColumn(IRInstruction instruction, String[] columnNames, int[] columnStarts) {
        int lineStart = text.length();
        int foldStart = -1;
        for (int i = 0; i < columnNames.length; i++) {
            fillTo(columnStarts[i], lineStart, ' ');
            String val = instruction.getValue(columnNames[i]);
            if (val != null) {
                HoverParser p = new HoverParser(val);

                while (p.hasNext()) {
                    int start = text.length();
                    text.append(p.next());
                    if (p.getHover() != null) {
                        regionHovers.put(new TextRegion(start, text.length()), p.getHover());
                    }
                    if (p.isNewLine()) {
                        if (foldStart == -1) {
                            foldStart = text.length() - 1;
                        }
                        lineStart = text.length();
                        fillTo(columnStarts[i], lineStart, ' ');
                    }
                }
            }
        }
        if (foldStart != -1) {
            foldingRegions.add(new FoldingRegion(KIND_MULTILINE, foldStart, text.length(), false));
        }
        text.append("\n");
    }

    private void recordHir(BasicBlock block, IRInstruction hir, TextRegion hyperlinkTarget) {
        String irName = hir.getValue(IRInstruction.HIR_NAME);
        String irText = HoverParser.firstLine(hir.getValue(IRInstruction.HIR_TEXT));
        String irOperand = hir.getValue(IRInstruction.HIR_OPERAND);

        StringBuilder sb = new StringBuilder();
        sb.append(block.getName()).append(" - ").append(irName);
        if (irOperand != null) {
            sb.append(" ").append(irOperand);
        }
        sb.append(" : ");

        int start = sb.length();
        sb.append(irText);
        String s = sb.toString();

        recordDefinition(irName, s, hyperlinkTarget);
        if (irOperand != null) {
            recordDefinition(irOperand.substring(1, irOperand.length() - 1), s, hyperlinkTarget);
        }
        recordUses(s, start, s.length() - start);
    }

    private FoldingRegion appendLir(BasicBlock block) {
        int start = text.length();
        appendColumnHeader(lirColumnNames, lirColumnStarts, " (LIR)");
        for (IRInstruction instruction : block.getLirOperations()) {
            appendColumn(instruction, lirColumnNames, lirColumnStarts);
            recordLir(block, instruction);
        }

        return new FoldingRegion(KIND_LIR, start, text.length() - 1, false);
    }

    private void recordLir(BasicBlock block, IRInstruction lir) {
        String irNumber = lir.getValue(IRInstruction.LIR_NUMBER);
        String irText = HoverParser.firstLine(lir.getValue(IRInstruction.LIR_TEXT));
        StringBuilder sb = new StringBuilder();
        sb.append(block.getName()).append(" - ").append(irNumber).append(" ");

        int start = sb.length();
        sb.append(irText);

        recordUses(sb.toString(), start, sb.length() - start);
    }

    private void fillTo(int pos, int lineStart, char ch) {
        for (int i = pos + lineStart - text.length(); i > 0; i--) {
            text.append(ch);
        }
    }

    private void append(int value, int minLen) {
        int oldLen = text.length();
        text.append(value);

        text.append(' ');
        for (int i = oldLen + minLen - text.length(); i > 0; i--) {
            text.append(' ');
        }
    }

    private void append(String value, int minLen) {
        int oldLen = text.length();
        text.append(value);
        text.append(' ');
        for (int i = oldLen + minLen - text.length(); i > 0; i--) {
            text.append(' ');
        }
    }

    private void appendList(StringBuilder sb, String prefix, List<String> values) {
        for (String value : values) {
            sb.append(prefix).append(value);
            prefix = ",";
        }
    }

    private void recordDefinition(String key, String value, TextRegion hyperlinkTarget) {
        if (hoverDefinitions.containsKey(key)) {
            System.out.println("WARNING: duplicate definition of '" + key + "': '" + value + "' and '" + hoverDefinitions.get(key) + "'");
        }

        hoverKeys.add(key);
        hoverDefinitions.put(key, value);
        hyperlinks.put(key, hyperlinkTarget);
    }

    private void recordUse(String key, String value) {
        hoverKeys.add(key);
        List<String> list = hoverReferences.get(key);
        if (list == null) {
            list = new ArrayList<String>();
            hoverReferences.put(key, list);
        }
        if (!list.contains(value)) {
            list.add(value);
        }
    }

    private void recordUses(String value, int offset, int length) {
        scanner.setText(value, offset, length);
        TokenID token = scanner.nextToken();
        while (token != null && token.getNumericID() != IRTokenContext.EOF_TOKEN_ID) {
            if (token.getNumericID() == IRTokenContext.HIR_TOKEN_ID || token.getNumericID() == IRTokenContext.LIR_TOKEN_ID || token.getNumericID() == IRTokenContext.BLOCK_TOKEN_ID) {
                recordUse(scanner.getTokenString(), value);
            }
            token = scanner.nextToken();
        }
    }
}
