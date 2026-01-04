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
package at.ssw.visualizer.modelimpl.bc;

import at.ssw.visualizer.model.bc.Bytecodes;
import at.ssw.visualizer.model.cfg.BasicBlock;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import at.ssw.visualizer.modelimpl.cfg.BasicBlockImpl;
import java.util.Arrays;

/**
 * This class holds the bytecode of a method and provides severel methods
 * accessing the details.
 *
 * @author Christian Wimmer
 */
public class BytecodesImpl implements Bytecodes {
    private ControlFlowGraph controlFlowGraph;
    private String bytecodeString;

    private String[] bytecodes;
    private String epilogue;
    

    public BytecodesImpl(ControlFlowGraph controlFlowGraph, String bytecodeString) {
        this.controlFlowGraph = controlFlowGraph;
        this.bytecodeString = bytecodeString;
    }
    
    public void parseBytecodes() {
        String[] lines = bytecodeString.split("\n");

        boolean inPrologue = true;
        String[] result = new String[lines.length * 3];
        int lastBci = -1;
        int lnr = 0; 
        for (; lnr < lines.length; lnr++) {
            String line = lines[lnr];

            line = line.trim();
            if (line.startsWith("[")) {
                int end = line.indexOf(']');
                if (end != -1) {
                    line = line.substring(end + 1, line.length());
                }
            }
            
            line = line.trim();
            int space1 = line.indexOf(' ');
            if (space1 <= 0) {
                if (inPrologue) {
                    continue;
                } else {
                    break;
                }
            }
            String bciStr = line.substring(0, space1);
            if (bciStr.endsWith(":")) {
                bciStr = bciStr.substring(0, bciStr.length() - 1);
            }

            int bci;
            try {
                bci = Integer.parseInt(bciStr);
            } catch (NumberFormatException ex) {
                // Ignore invalid lines.
                if (inPrologue) {
                    continue;
                } else {
                    break;
                }
            }

            String opcode = line.substring(space1 + 1);
            String params = "";
            int space2 = opcode.indexOf(' ');
            if (space2 > 0) {
                params = opcode.substring(space2 + 1).trim();
                opcode = opcode.substring(0, space2);
            }
            String tail = "";
            int space3 = params.indexOf('|');
            if (space3 >= 0) {
                tail = params.substring(space3);
                params = params.substring(0, space3);
            }

//            if (!"ldc".equals(opcode) || !params.startsWith("\"")) {
//                // Separate packages with "." instead of "/"
//                params = params.replace('/', '.');
//            }
            
            String printLine = bciStr + ":" + "    ".substring(Math.min(bciStr.length(), 3)) +
                    opcode + "              ".substring(Math.min(opcode.length(), 13)) +
                    params + "        ".substring(Math.min(params.length(), 8)) +
                    tail;

            
            if (bci >= result.length) {
                result = Arrays.copyOf(result, Math.max(bci + 1, result.length * 2));
            }
            result[bci] = printLine;
            inPrologue = false;
            lastBci = Math.max(lastBci, bci);
        }

        StringBuilder epilogueBuilder = new StringBuilder();
        for (; lnr < lines.length; lnr++) {
            epilogueBuilder.append(lines[lnr]).append("\n");
        }
        epilogue = epilogueBuilder.toString();
        bytecodes = Arrays.copyOf(result, lastBci + 1);
    
        
        BasicBlockImpl[] blocks = new BasicBlockImpl[bytecodes.length];
        for (BasicBlock b : controlFlowGraph.getBasicBlocks()) {
            if (b instanceof BasicBlockImpl) {
                BasicBlockImpl block = (BasicBlockImpl) b;
                if (block.getToBci() != -1) {
                    // Do not override existing values.
                    return;
                }
                if (block.getFromBci() >= 0 && block.getFromBci() < blocks.length) {
                    blocks[block.getFromBci()] = block;
                }
            }
        }
        
        int curToBci = -1;
        for (int i = blocks.length - 1; i >= 0; i--) {
            if (bytecodes[i] != null && curToBci == -1) {
                curToBci = i;
            }
            if (blocks[i] != null) {
                blocks[i].setToBci(curToBci);
                curToBci = -1;
            }
        }
    }
    
    public ControlFlowGraph getControlFlowGraph() {
        return controlFlowGraph;
    }

    public String getBytecodes(int fromBCI, int toBCI) {
        if (fromBCI < 0) {
            return "";
        }
        toBCI = Math.min(toBCI, bytecodes.length);
        StringBuilder sb = new StringBuilder();
        for (int i = fromBCI; i < toBCI; i++) {
            if (bytecodes[i] != null) {
                sb.append(bytecodes[i]).append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    public String getEpilogue() {
        return epilogue;
    }

    @Override
    public String toString() {
        return "Bytecodes " + getControlFlowGraph().getName();
    }
}
