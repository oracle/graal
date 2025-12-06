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

import at.ssw.visualizer.model.cfg.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is a static helper class to create a InstructionSet out of
 * the BasicBlocks extraced from the ControlFlowGraph component. It is
 * light weight to be easy to adopt to changes in the structure of the
 * datasource (BasicBlock[]).
 *
 * @author Stefan Loidl
 */
public class InstructionSetGenerator {

    //characters that can occur as prefix for an operation
    //source for this information: see at.ssw.visualizer.ir.model.IRScanner.isHir()
    private static String idStart="ailfdv";

    private static char idBlock='B';

    /**
     * Generates a InstructionSet from an array of BasicBlocks
     */
    public static InstructionSet generateFromBlocks(List<BasicBlock> blocks){
        if(blocks==null) return null;
        InstructionSet iset=new InstructionSet();
        Instruction instr1, instr2;

        for(BasicBlock b : blocks){
            if(!b.hasHir()) continue;
            //Handle HIR Instructions for standard operations
            for(IRInstruction hi:b.getHirInstructions()){

                instr1= iset.getInstruction(hi.getValue(IRInstruction.HIR_NAME));
                instr1.setInstructionString(hi.getValue(IRInstruction.HIR_TEXT));
                instr1.setSourceBlock(b.getName());

                if(containsBlockIdentifier(hi.getValue(IRInstruction.HIR_TEXT)))
                    instr1.setInstructionType(Instruction.InstructionType.CONTROLFLOW);
                else{
                    if(startsLikeConstant(hi.getValue(IRInstruction.HIR_TEXT))) instr1.setInstructionType(Instruction.InstructionType.CONSTANT);
                    else instr1.setInstructionType(Instruction.InstructionType.OPERATION);
                }

                //Extract instructions from InstructionString
                for(String s : parseInstructionString(hi.getValue(IRInstruction.HIR_TEXT))){
                    instr2= iset.getInstruction(s);
                    if(instr1!=instr2) iset.addLink(instr2,instr1);
                    //If an instruction contains predecessors it's an operation
                    instr1.setInstructionType(Instruction.InstructionType.OPERATION);
                }
            }

            //Handle local state of the BasicBlock b
            for(State state: b.getStates()){
                for(StateEntry stentry: state.getEntries()){

                    instr1= iset.getInstruction(stentry.getName());

                    //First occurance of local state var without Phi is decided to be
                    //a method param
                    if(instr1.getInstructionString()==null && !stentry.hasPhiOperands()){
                        instr1.setInstructionString("");
                        instr1.setInstructionType(Instruction.InstructionType.PARAMETER);
                        instr1.setSourceBlock(b.getName());
                    }

                    //Add Phi operands if some exist
                    if(stentry.hasPhiOperands()){
                        instr1.setSourceBlock(b.getName());
                        instr1.setInstructionType(Instruction.InstructionType.PHI);
                        String instrString="Phi[";
                        for(String s : stentry.getPhiOperands()){
                            instr2=iset.getInstruction(s);
                            if(instr1!=instr2) iset.addLink(instr2,instr1);
                            instrString+=s+",";
                        }
                        instr1.setInstructionString(instrString.substring(0,instrString.length()-1)+"]");
                    }
                }
            }
        }
        return iset;
    }


    /**
     * Returns if an Blockidentifier is contained within the string. (Eg. B4, B56...)
     */
    private static boolean containsBlockIdentifier(String iString){
        int pos=iString.indexOf(idBlock);
        if(pos!=-1 && iString.length()>pos+1 && isNumber(iString.charAt(pos+1))) return true;
        else return false;
    }


    /**
     * This method extracts the identifiers from an instuction string.
     * These are returned as a collection of strings.
     */
    private static ArrayList<String> parseInstructionString(String iString){
        //In most cases the Instruction string will hold two identifiers
        ArrayList<String> list=new ArrayList<String>(2);

        if(iString!=null){
            int from=0, pos=0;

            while(pos<iString.length()){
                if(idStart.indexOf(iString.charAt(pos))!=-1){
                    //If following is numeric and previous is not alphanumeric
                    //an identifier has be found
                    if((pos+1<iString.length() && isNumber(iString.charAt(pos+1))) &&
                        (pos==0 || !isAlphaNumeric(iString.charAt(pos-1)))){

                        from=pos;
                        pos++;
                        while(pos < iString.length() && isNumber(iString.charAt(pos))) pos++;
                        //an alphanumeric character after identifier is not possible
                        if(iString.length()==pos || (!isAlphaNumeric(iString.charAt(pos))))
                            list.add(iString.substring(from,pos));
                    }
                    else pos++;
                }
                else pos++;
            }
        }
        return list;
    }

    /**
     * Returns if char c is a number
     */
    private static boolean isNumber(char c){
        return (c>='0' && c<='9');
    }

    /**
     * Returns if char c is alphanumeric
     */
    private static boolean isAlphaNumeric(char c){
        if(isNumber(c)) return true;
        return (c>='a' && c<='z') || (c>='A' && c<='Z');
    }

    /**
     * Returns if s starts like a constant:
     * - "<instance"
     * - Number
     * - "Class"
     * - "null"
     */
    private static boolean startsLikeConstant(String s){
        if(s==null || s.length()==0) return false;
        if(s.startsWith("<instance")  ||
                s.startsWith("Class") ||
                s.startsWith("null") ||
                isNumber(s.charAt(0)))
            return true;

        if((s.charAt(0)=='-' || s.charAt(0)=='+') && s.length()>1){
            return isNumber(s.charAt(1));
        }
        return false;
    }

}
