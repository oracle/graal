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
package at.ssw.visualizer.modelimpl.cfg;

import at.ssw.visualizer.model.bc.Bytecodes;
import at.ssw.visualizer.model.cfg.BasicBlock;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import at.ssw.visualizer.model.nc.NativeMethod;
import at.ssw.visualizer.modelimpl.CompilationElementImpl;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Christian Wimmer
 */
public class ControlFlowGraphImpl extends CompilationElementImpl implements ControlFlowGraph {
    private BasicBlock[] basicBlocks;
    private Map<String, BasicBlock> blockNames;
    private Bytecodes bytecodes;
    private NativeMethod nativeMethod;
    private boolean hasState;
    private boolean hasHir;
    private boolean hasLir;

    public ControlFlowGraphImpl(String shortName, String name, BasicBlockImpl[] basicBlocks) {
        super(shortName, name);
        this.basicBlocks = basicBlocks;

        blockNames = new HashMap<String, BasicBlock>(basicBlocks.length);
        nativeMethod = null;
        for (BasicBlockImpl block : basicBlocks) {
            block.setParent(this);
            blockNames.put(block.getName(), block);
            hasState |= block.hasState();
            hasHir |= block.hasHir();
            hasLir |= block.hasLir();
        }
    }

    public List<BasicBlock> getBasicBlocks() {
        return Collections.unmodifiableList(Arrays.asList(basicBlocks));
    }

    public BasicBlock getBasicBlockByName(String name) {
        return blockNames.get(name);
    }

    public Bytecodes getBytecodes() {
        return bytecodes;
    }

    public void setBytecodes(Bytecodes bytecodes) {
        this.bytecodes = bytecodes;
    }

    public NativeMethod getNativeMethod() {
        return nativeMethod;
    }

    public void setNativeMethod(NativeMethod nativeMethod) {
        this.nativeMethod = nativeMethod;
    }

    public boolean hasState() {
        return hasState;
    }

    public boolean hasHir() {
        return hasHir;
    }

    public boolean hasLir() {
        return hasLir;
    }

    @Override
    public String toString() {
        return "    CFG \"" + getName() + "\": " + basicBlocks.length + " blocks\n";
    }
}
