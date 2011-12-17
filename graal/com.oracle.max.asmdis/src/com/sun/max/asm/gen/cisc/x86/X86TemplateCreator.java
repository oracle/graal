/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm.gen.cisc.x86;

import java.util.*;

import com.sun.max.asm.gen.*;
import com.sun.max.lang.*;

/**
 */
public abstract class X86TemplateCreator<Template_Type extends X86Template> {

    private final Assembly assembly;
    private final WordWidth addressWidth;
    private X86InstructionDescription instructionDescription;
    private InstructionAssessment instructionAssessment;
    private X86TemplateContext context;
    private int serial = 1;

    protected X86TemplateCreator(Assembly assembly, WordWidth addressWidth) {
        this.assembly = assembly;
        this.addressWidth = addressWidth;
    }

    private final List<Template_Type> templates = new ArrayList<Template_Type>();

    public List<Template_Type> templates() {
        return templates;
    }

    private final Map<String, List<Template_Type>> internalNameToTemplates = new HashMap<String, List<Template_Type>>();

    private void addTemplate(Template_Type template) {
        templates.add(template);
        List<Template_Type> t = internalNameToTemplates.get(template.internalName());
        if (t == null) {
            t = new LinkedList<Template_Type>();
            internalNameToTemplates.put(template.internalName(), t);
        }
        t.add(template);
    }

    private void computeRedundancy(X86Template template) {
        final List<Template_Type> t = internalNameToTemplates.get(template.internalName());
        if (t != null) {
            for (X86Template other : t) {
                if (template.computeRedundancyWith(other)) {
                    return;
                }
            }
        }
    }

    protected abstract Template_Type createTemplate(X86InstructionDescription description, int ser, InstructionAssessment instructionFamily, X86TemplateContext contxt);

    private void createTemplate() {
        final Template_Type template = createTemplate(instructionDescription, serial, instructionAssessment, context);
        if (X86InstructionDescriptionVisitor.Static.visitInstructionDescription(template, instructionDescription)) {
            final InstructionDescription modRMInstructionDescription = template.modRMInstructionDescription();
            if (modRMInstructionDescription != null && !X86InstructionDescriptionVisitor.Static.visitInstructionDescription(template, modRMInstructionDescription)) {
                return;
            }
            computeRedundancy(template);
            addTemplate(template);
            serial++;
        }
    }

    private void createTemplatesForSibBaseCases() {
        for (X86TemplateContext.SibBaseCase sibBaseCase : X86TemplateContext.SibBaseCase.VALUES) {
            if (sibBaseCase == X86TemplateContext.SibBaseCase.GENERAL_REGISTER || context.modCase() == X86TemplateContext.ModCase.MOD_0) {
                context = context.clone();
                context.sibBaseCase = sibBaseCase;
                createTemplate();
            }
        }
    }

    private void createTemplatesForSibIndexCases() {
        for (X86TemplateContext.SibIndexCase sibIndexCase : X86TemplateContext.SibIndexCase.VALUES) {
            context = context.clone();
            context.setSibIndexCase(sibIndexCase);
            createTemplatesForSibBaseCases();
        }
    }

    private void createTemplatesForRMCases() {
        for (X86TemplateContext.RMCase rmCase : X86TemplateContext.RMCase.VALUES) {
            context = context.clone();
            context.setRMCase(rmCase);
            switch (context.modCase()) {
                case MOD_3: {
                    if (rmCase == X86TemplateContext.RMCase.NORMAL) {
                        createTemplate();
                    }
                    break;
                }
                default: {
                    switch (rmCase) {
                        case SIB:
                            createTemplatesForSibIndexCases();
                            break;
                        default:
                            createTemplate();
                            break;
                    }
                }
            }
        }
    }

    private void createTemplatesForModRMGroups() {
        if (instructionAssessment.modRMGroup() != null) {
            for (ModRMGroup.Opcode modRMGroupOpcode : ModRMGroup.Opcode.VALUES) {
                context = context.clone();
                context.setModRMGroupOpcode(modRMGroupOpcode);
                createTemplatesForRMCases();
            }
        } else {
            createTemplatesForRMCases();
        }
    }

    private void createTemplatesForModCases(WordWidth operandSizeAttribute) {
        context = context.clone();
        context.setOperandSizeAttribute(operandSizeAttribute);

        if (instructionAssessment.hasModRMByte()) {
            for (X86TemplateContext.ModCase modCase : X86TemplateContext.ModCase.VALUES) {
                context = context.clone();
                context.setModCase(modCase);
                createTemplatesForModRMGroups();
            }
        } else {
            createTemplate();
        }
    }

    private void createTemplatesForOperandSizeAttribute(WordWidth addressSizeAttribute) {
        context = context.clone();
        context.setAddressSizeAttribute(addressSizeAttribute);

        if (instructionDescription.requiredOperandSize() != null) {
            createTemplatesForModCases(instructionDescription.requiredOperandSize());
        } else {
            if (instructionDescription.defaultOperandSize() != WordWidth.BITS_64) {
                createTemplatesForModCases(WordWidth.BITS_32);
            }
            if (addressWidth == WordWidth.BITS_64) {
                createTemplatesForModCases(WordWidth.BITS_64);
            }
            if (X86Assembly.are16BitOffsetsSupported() || !instructionAssessment.isJump()) {
                createTemplatesForModCases(WordWidth.BITS_16);
            }
        }
    }

    private void createTemplatesForAddressSizeAttribute() {
        if (instructionDescription.requiredAddressSize() != null) {
            if (X86Assembly.are16BitAddressesSupported() || instructionDescription.requiredAddressSize() == addressWidth) {
                createTemplatesForOperandSizeAttribute(instructionDescription.requiredAddressSize());
            }
        } else {
            createTemplatesForOperandSizeAttribute(addressWidth);
            if (X86Assembly.are16BitAddressesSupported() && instructionAssessment.hasAddressSizeVariants()) {
                createTemplatesForOperandSizeAttribute(WordWidth.fromInt(addressWidth.numberOfBits / 2));
            }
        }
    }

    public void createTemplates(InstructionDescriptionCreator<X86InstructionDescription> instructionDescriptionCreator) {
        for (X86InstructionDescription description : instructionDescriptionCreator.instructionDescriptions()) {
            this.instructionDescription = description;
            this.instructionAssessment = new InstructionAssessment();
            final OpcodeAssessor assessor = new OpcodeAssessor(instructionAssessment);
            X86InstructionDescriptionVisitor.Static.visitInstructionDescription(assessor, description);
            this.context = new X86TemplateContext();
            createTemplatesForAddressSizeAttribute();
        }
    }
}
