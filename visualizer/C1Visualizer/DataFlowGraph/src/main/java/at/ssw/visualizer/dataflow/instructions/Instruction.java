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


/**
 * An Instruction represents a HIR operation. It has predecessors which are
 * the parameters of the function, and it has successors which are the
 * usages of the result later on. The instructionString component is the
 * string representation of the instruction behaviour. Each function is
 * unique and therefore identified with an id.
 * This class is not intended to be constructed outside of the package.
 *
 * @author Stefan Loidl
 */
public class Instruction {

    //unique identifier of the instruction
    private String id;

    //unique identifier of the source block
    private String sourceBlock;

    private String instructionString;
    private ArrayList<Instruction> predecessors;
    private ArrayList<Instruction> successors;

    private InstructionType type;


    /**
     * Internally used Constructor
     */
    protected Instruction(String id){
        this.id=id;
        predecessors=new ArrayList<Instruction>();
        successors=new ArrayList<Instruction>();
        instructionString=null;
        sourceBlock=null;
        type=InstructionType.UNKNOWN;
    }

    /**
     * Constructor
     */
    protected Instruction(String id, String instruction, String source,Instruction[] pre, Instruction[] succ, InstructionType t) {
        this.id=id;
        this.instructionString=instruction;

        predecessors=new ArrayList<Instruction>();
        successors=new ArrayList<Instruction>();
        sourceBlock=source;

        if(pre!=null)
            for(Instruction i : pre) predecessors.add(i);
        if(succ!=null)
            for(Instruction i :succ) successors.add(i);
        type=t;
    }

    /**
     * As we are in single static assignment form two instructions are
     * equal if their id is eqaul.
     */
    public boolean equals(Object o){
        if(o instanceof Instruction)
            return (((Instruction)o).getID().equals(id));
        return false;
    }


    // <editor-fold defaultstate="collapsed" desc=" getter and setter functions for ID, InstructionString, SourceBlock, InstructionType ">
    /**
     * Returns the id of the Instruction.
     */
    public String getID(){
        return id;
    }

    /**
     * Sets the instruction string defining the behaviour
     * of the instruction.
     */
    protected void setInstructionString(String is){
        instructionString=is;
    }

    /**
     * Returns the string defining the instruction
     */
    public String getInstructionString(){
        return instructionString;
    }

    /**
     * Returns the string identifying the source block
     */
    public String getSourceBlock(){
        return sourceBlock;
    }

    /**
     * Sets the source Block
     */
    protected void setSourceBlock(String s){
        sourceBlock=s;
    }

    /**
     * Sets the type of the Instruction
     */
    protected void setInstructionType(InstructionType t){
        type=t;
    }

    /**
     * Returns the type of the Instruction
     */
    public InstructionType getInstructionType(){
        return type;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc=" getter, adder and remover functions for prede- and successors ">
    /**
     * Returns an array with all predecessors
     */
    public Instruction[] getPredecessors(){
       Instruction[] ret=new Instruction[predecessors.size()];
       ret=predecessors.toArray(ret);
       return ret;
    }

    /**
     * Adds a Predecessor to the Instruction
     */
    protected void addPredecessor(Instruction i){
       predecessors.add(i);
    }

    /**
     * Removes a Predecessor from the Instruction
     */
    protected void removePredecessor(Instruction i){
       predecessors.remove(i);
    }

     /**
     * Returns an array with all successors
     */
    public Instruction[] getSuccessors(){
       Instruction[] ret=new Instruction[successors.size()];
       ret=successors.toArray(ret);
       return ret;
    }

    /**
     * Adds a successors to the Instruction
     */
    protected void addSuccessors(Instruction i){
       successors.add(i);
    }

    /**
     * Removes a successors from the Instruction
     */
    protected void removeSuccessors(Instruction i){
       successors.remove(i);
    }
    // </editor-fold>

    public enum InstructionType{
        UNKNOWN,
        CONSTANT,
        PHI,
        PARAMETER,
        OPERATION,
        CONTROLFLOW
    }

}
