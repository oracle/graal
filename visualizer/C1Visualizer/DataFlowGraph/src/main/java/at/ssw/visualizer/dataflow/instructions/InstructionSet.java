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
package at.ssw.visualizer.dataflow.instructions;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedList;

/**
 * This class can be seen as an infinite set of instructions. It contains
 * a number of instructions, but if an instruction is queried that is not
 * within the set, an instruction is added to the set and is returned.
 * Using this behaviour a simple out of order construction of the instruction
 * graph is possible.
 *
 * @author Stefan Loidl
 */
public class InstructionSet {

    private Hashtable<String,Instruction> instructions;

    /**
     * Default Constructor
     */
    public InstructionSet() {
        instructions=new Hashtable<String,Instruction>();
    }

    /**
     * Returns the instruction with id if its within the set, or a new
     * instruction if not (the new instruction is then added to the set too)
     */
    public Instruction getInstruction(String id){
        if(instructions.containsKey(id))
            return instructions.get(id);
        else{
            Instruction i=new Instruction(id);
            instructions.put(id,i);
            return i;
        }
    }

    /**
     * Simply adds a link between two instructions by adding
     * prede- and successor to the corresponding elements.
     */
    public boolean addLink(Instruction from,Instruction to){
        if(from!=null && to!= null){
            from.addSuccessors(to);
            to.addPredecessor(from);
            return true;
        }
        else return false;
    }

    /**
     * Returns an array of Instructions contained in the set,
     * no assumptions about the ordering should be maid.
     */
    public Instruction[] getInstructions(){
        Instruction[] inst=new Instruction[instructions.size()];
        inst=instructions.values().toArray(inst);
        return inst;
    }

     /**
      * Removes all nodes of the specified type from the set.
      * Currently no Control Flow Instrucitons are shown within the
      * Graph. For this purpose this method is used.
      */
    public static Instruction[] filterInstructionType(Instruction[] iset,Instruction.InstructionType type){
        LinkedList<Instruction> list=new LinkedList<Instruction>();

        for(Instruction i : iset){
            if(i.getInstructionType()!=type) list.add(i);
        }

        Instruction[] ret=new Instruction[list.size()];

        return list.toArray(ret);
    }

}
