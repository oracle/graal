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
package at.ssw.visualizer.dataflow.graph;

import at.ssw.visualizer.dataflow.instructions.Instruction;
import java.awt.Color;
import java.awt.Font;
import org.netbeans.api.visual.border.BorderFactory;
import org.netbeans.api.visual.widget.LabelWidget;

/**
 *
 * @author Stefan Loidl
 */
public class InscribeNodeWidget extends LabelWidget{

    private final static int LEFTINSET=2;
    private final static int TOPINSET=1;


    /** Creates a new instance of InscribeNodeWidget */
    public InscribeNodeWidget(Instruction i, InstructionNodeGraphScene s) {
        super(s);

        if(i.getInstructionType()==Instruction.InstructionType.PARAMETER)
            setLabel(i.getID());
        else{
            setLabel(InstructionNodeWidget.modifiyStringLength(i.getID()+": "+i.getInstructionString(),
                    InstructionNodeWidget.MAXSTRINGLENGTH,InstructionNodeWidget.MAXSTRINGLENGTH,
                    InstructionNodeWidget.ABBREV));
        }

        setFont(Font.decode(InstructionNodeWidget.DEFAULTFONT).deriveFont(Font.BOLD));
        setForeground(InstructionNodeWidget.getLineColor());
        setOpaque(false);
        InstructionNodeWidget nw=s.getNodeWidget(i.getID());
        if(nw!=null){
            setToolTipText(nw.createToolTip());
        }

        Color c=InstructionNodeWidget.getColorByType(i.getInstructionType(),false);
        Color dc=InstructionNodeWidget.getLineColor();
        setBorder(BorderFactory.createRoundedBorder(0,0,LEFTINSET,TOPINSET,c,dc));

    }

}
