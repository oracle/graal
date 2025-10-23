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
package at.ssw.visualizer.nc.model;

import at.ssw.visualizer.model.Compilation;
import at.ssw.visualizer.model.cfg.BasicBlock;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import at.ssw.visualizer.nc.NCEditorSupport;
import at.ssw.visualizer.texteditor.model.BlockRegion;
import at.ssw.visualizer.texteditor.model.FoldingRegion;
import at.ssw.visualizer.texteditor.model.HoverParser;
import at.ssw.visualizer.texteditor.model.Text;
import at.ssw.visualizer.texteditor.model.TextBuilder;
import at.ssw.visualizer.texteditor.model.TextRegion;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.netbeans.api.editor.fold.FoldType;
import org.netbeans.editor.TokenID;

/**
 *
 * @author Alexander Reder
 */
public class NCTextBuilder extends TextBuilder {

    public static final FoldType KIND_BLOCK = new FoldType("...");
    public static final FoldType LIR_BLOCK = new FoldType("");
    private CodeBlock codeBlock = null;
    private CommentBlock commentBlock = null;

    public NCTextBuilder() {
        super();
        scanner = new NCScanner();
    }

    public Text buildDocument(ControlFlowGraph cfg) {
        Compilation compilation = cfg.getCompilation();
        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);

        text.append(compilation.getMethod()).append("\n");
        text.append(dateFormat.format(compilation.getDate())).append("\n\n");

        return buildDocument(cfg, false);
    }

    private Text buildDocument(ControlFlowGraph cfg, boolean skipLIR) {
        String text = cfg.getNativeMethod().getMethodText().trim();
        if (text.startsWith("<<<HexCodeFile") && text.endsWith("HexCodeFile>>>")) {
            text = HexCodeFileSupport.decode(text);
        }
        String[] methodText = text.split("\n");
        
        String l;
        for (String s : methodText) {
            l = s.trim();
            if (l.length() > 0) {
                switch (l.charAt(0)) {
                    case '[':
                        checkComment();
                        append(s);
                        append("\n");
                        break;
                    case ';':
                        parseComment(cfg, s, skipLIR);
                        break;
                    case '0':
                        checkComment();
                        parseCodeLine(l);
                        append(l);
                        append("\n");
                        break;
                }
            }
        }
        if (codeBlock != null) {
            foldingRegions.add(new FoldingRegion(KIND_BLOCK, codeBlock.codeStart + 1, codeBlock.start + codeBlock.length - 1, false));
        }

        return buildText(cfg, NCEditorSupport.MIME_TYPE);
    }

    public String buildView(ControlFlowGraph cfg, BasicBlock[] blocks) {
        if (cfg.getNativeMethod() == null) {
            return "No native code available\n";
        }
        Text t = buildDocument(cfg, true);
        String contents = t.getText();
        StringBuilder view = new StringBuilder();
        BlockRegion reg;
        for (BasicBlock bb : blocks) {
            reg = t.getBlocks().get(bb);
            if (reg != null) {
                view.append(contents.substring(reg.getStart(), reg.getEnd()));
            }
        }
        return view.toString();
    }

    private void parseComment(ControlFlowGraph cfg, String s, boolean skipComments) {
        int i = 0;
        String l = s.trim();
        while (i < l.length() && (l.charAt(i) == ';' || l.charAt(i) == ' ')) {
            i++;
        }
        if (l.length() > i + 5 && l.substring(i, i + 5).equals("block")) { // Block beginning
            if (codeBlock != null) { // append last parsed block
                appendBlock();
            }
            codeBlock = new CodeBlock(l.substring(i + 6, l.indexOf(' ', i + 6)), cfg);
        } else if (l.contains("slow case") && codeBlock != null && codeBlock.name != null) {
            appendBlock();
            codeBlock = new CodeBlock(null, cfg);
            append(s);
            append("\n");
        } else { // any other comment
            if (commentBlock == null) {
                commentBlock = new CommentBlock();
            }
            if (!skipComments) {
                append(s);
                append("\n");
                commentBlock.length += s.length() + 1;
            }
        }
    }

    private void parseCodeLine(String s) {
        int start = text.length() + 2;
        boolean refAdr = false;
        String addr = null;
        scanner.setText(s, 0, s.length());
        TokenID token = scanner.nextToken();
        while (token != null && token != NCTokenContext.EOF_TOKEN) {
            switch (token.getNumericID()) {
                case NCTokenContext.ADDRESS_TOKEN_ID:
                    addr = scanner.getTokenString();
                    if (refAdr) {
                        addReference(normalizeAddr(addr), s);
                        addReference(trimAddr(addr), s);
                    } else {
                        hyperlinks.put(trimAddr(addr), new TextRegion(start, start + addr.length()));
                        hoverKeys.add(normalizeAddr(addr));
                        hoverKeys.add(trimAddr(addr));
                        addDefinition(normalizeAddr(addr), s);
                        addDefinition(trimAddr(addr), s);
                        refAdr = true;
                    }
                    break;
                case NCTokenContext.REGISTER_TOKEN_ID:
                    String reg = scanner.getTokenString();
                    hoverKeys.add(reg);
                    addReference(reg, s);
                    break;
            }
            token = scanner.nextToken();
        }
    }

    private void addReference(String key, String s) {
        if (!hoverReferences.containsKey(key)) {
            hoverReferences.put(key, new ArrayList<String>());
        }
        String bn = codeBlock == null || codeBlock.name == null ? "" : codeBlock.name + ":\t";
        hoverReferences.get(key).add(bn + s.trim());
    }

    private void addDefinition(String key, String s) {
        String bn = codeBlock == null || codeBlock.name == null ? "" : codeBlock.name + ":\t";
        hoverDefinitions.put(key, bn + s.trim());
    }

    private static boolean isAddressParseableAsSignedLong(String addr) {
        return addr.length() < (16 + 2) && Character.isDigit(addr.charAt(2));
    }

    private String normalizeAddr(String addr) {
        if (!isAddressParseableAsSignedLong(addr)) {
            return addr;
        }
        StringBuilder addrString = new StringBuilder(Long.toString(Long.decode(addr), 16));
        for (int i = addrString.length(); i < 8; i++) {
            addrString.insert(0, 0);
        }
        addrString.insert(0, "0x");
        return addrString.toString();
    }

    private String trimAddr(String addr) {
        if (!isAddressParseableAsSignedLong(addr)) {
            return addr;
        }
        return "0x" + Long.toString(Long.decode(addr), 16);
    }

    private void appendBlock() {
        foldingRegions.add(new FoldingRegion(KIND_BLOCK, codeBlock.codeStart, codeBlock.start + codeBlock.length - 1, false));
        if (codeBlock != null && codeBlock.basicBlock != null) {
            blocks.put(codeBlock.basicBlock,
                    new BlockRegion(codeBlock.basicBlock,
                    codeBlock.start,
                    codeBlock.start + codeBlock.length,
                    codeBlock.start,
                    codeBlock.start + codeBlock.basicBlock.getName().length()));
        }
        hyperlinks.put(codeBlock.name, new TextRegion(codeBlock.start, codeBlock.start + codeBlock.name.length()));
        codeBlock = null;
    }

    private void checkComment() {
        if (commentBlock != null) {
            foldingRegions.add(new FoldingRegion(LIR_BLOCK, commentBlock.start, commentBlock.start + commentBlock.length + 1, false));
            commentBlock = null;
        }
    }

    private void append(String s) {
        HoverParser p = new HoverParser(s);
        while (p.hasNext()) {
            int start = text.length();
            String part = p.next();
            text.append(part);
            if (codeBlock != null) {
                codeBlock.length += part.length();
            }

            if (p.getHover() != null) {
                regionHovers.put(new TextRegion(start, text.length()), p.getHover());
            }
        }
    }

    protected void buildHighlighting() {
        scanner.setText(text.toString(), 0, text.length());
        TokenID token = scanner.nextToken();
        Map<String, List<TextRegion>> highlightings = new HashMap<String, List<TextRegion>>();
        String name;
        while (token != null && token != NCTokenContext.EOF_TOKEN) {
            switch (token.getNumericID()) {
                case NCTokenContext.ADDRESS_TOKEN_ID:
                    name = trimAddr(scanner.getTokenString());
                    if (!highlightings.containsKey(scanner.getTokenString()) && !highlightings.containsKey(name)) {
                        List<TextRegion> tr = new ArrayList<TextRegion>();
                        highlightings.put(normalizeAddr(name), tr);
                        highlightings.put(name, tr);
                    }
                    highlightings.get(normalizeAddr(name)).add(new TextRegion(scanner.getTokenOffset(), scanner.getOffset()));
                    highlightings.get(name).add(new TextRegion(scanner.getTokenOffset(), scanner.getOffset()));
                    break;
                case NCTokenContext.BLOCK_TOKEN_ID:
                case NCTokenContext.REGISTER_TOKEN_ID:
                    if (!highlightings.containsKey(scanner.getTokenString())) {
                        highlightings.put(scanner.getTokenString(), new ArrayList<TextRegion>());
                    }
                    highlightings.get(scanner.getTokenString()).add(new TextRegion(scanner.getTokenOffset(), scanner.getOffset()));
                    break;
            }
            token = scanner.nextToken();
        }
        for (String key : highlightings.keySet()) {
            List<TextRegion> regions = highlightings.get(key);
            highlighting.put(key, regions.toArray(new TextRegion[regions.size()]));
        }
    }

    private class CodeBlock {

        private String name;
        private BasicBlock basicBlock;
        private int start;
        private int codeStart;
        private int length;

        private CodeBlock(String name, ControlFlowGraph cfg) {
            this.name = name;
            basicBlock = name == null ? null : cfg.getBasicBlockByName(name);
            start = text.length();
            if (basicBlock != null) {
                appendBlockDetails(basicBlock);
                append("\n");
            }
            codeStart = text.length() - 1;
            length = text.length() - start;
        }
    }

    private class CommentBlock {

        int start;
        int length;

        private CommentBlock() {
            start = text.length();
            length = 0;
        }
    }
}
