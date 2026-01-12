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
package at.ssw.visualizer.bc.modelimpl;

import at.ssw.visualizer.model.bc.Bytecodes;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import java.util.SortedMap;

/**
 * This class holds the bytecode of a method and provides severel methods
 * accessing the details.
 *
 * @author Alexander Reder
 * @author Christian Wimmer
 */
public class BytecodesImpl implements Bytecodes {

    private ControlFlowGraph controlFlowGraph;
    private String name;
    private String shortName;

    private String prolog;
    private String attributes;
    private SortedMap<Integer, String> byteCodes;

    public BytecodesImpl(ControlFlowGraph controlFlowGraph, String name, String shortName, String prolog, String attributes, SortedMap<Integer, String> byteCodes) {
        this.controlFlowGraph = controlFlowGraph;
        this.name = name;
        this.shortName = shortName;
        this.prolog = prolog;
        this.attributes = attributes;
        this.byteCodes = byteCodes;
    }

    @Override
    public ControlFlowGraph getControlFlowGraph() {
        return controlFlowGraph;
    }

    @Override
    public void parseBytecodes() {
        // Nothing to parse
    }
    
    @Override
    public String getBytecodes(int fromBCI, int toBCI) {
        StringBuilder sb = new StringBuilder();
        for (Integer key : byteCodes.subMap(fromBCI, toBCI).keySet()) {
            String keyString = Integer.toString(key);
            sb.append(keyString).append(":");
            sb.append("     ".substring(Math.min(keyString.length(), 4)));
            sb.append(byteCodes.get(key));
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public String getEpilogue() {
        return attributes;
    }
    
    @Override
    public String toString() {
        return name;
    }
}
