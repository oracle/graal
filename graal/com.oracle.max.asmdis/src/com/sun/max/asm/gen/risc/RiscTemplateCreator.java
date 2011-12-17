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
package com.sun.max.asm.gen.risc;

import java.util.*;

import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.risc.field.*;
import com.sun.max.program.*;

/**
 */
public class RiscTemplateCreator {

    public RiscTemplateCreator() {
    }

    private List<RiscTemplate> templates = new LinkedList<RiscTemplate>();

    public List<RiscTemplate> templates() {
        return templates;
    }

    protected RiscTemplate createTemplate(InstructionDescription instructionDescription) {
        return new RiscTemplate(instructionDescription);
    }

    public List<RiscTemplate> createOptionTemplates(List<RiscTemplate> templateList, OptionField optionField) {
        final List<RiscTemplate> newTemplates = new LinkedList<RiscTemplate>();
        for (RiscTemplate template : templateList) {
            RiscTemplate canonicalRepresentative = null;
            if (optionField.defaultOption() != null) {
                canonicalRepresentative = (RiscTemplate) template.clone();
                canonicalRepresentative.organizeOption(optionField.defaultOption(), null);
            }
            for (Option option : optionField.options()) {
                if (option.equals(optionField.defaultOption())) {
                    newTemplates.add(canonicalRepresentative);
                } else {
                    final RiscTemplate templateWithOption = (RiscTemplate) template.clone();
                    templateWithOption.organizeOption(option, canonicalRepresentative);
                    newTemplates.add(templateWithOption);
                }
            }
        }
        return newTemplates;
    }

    private int serial;
    private HashMap<String, List<RiscTemplate>> nameToTemplates = new HashMap<String, List<RiscTemplate>>() {
        @Override
        public List<RiscTemplate> get(Object key) {
            List<RiscTemplate> list = super.get(key);
            if (list == null) {
                list = new ArrayList<RiscTemplate>();
                put((String) key, list);
            }
            return list;
        }
    };

    public List<RiscTemplate> nameToTemplates(String name) {
        return nameToTemplates.get(name);
    }

    public void createTemplates(RiscInstructionDescriptionCreator instructionDescriptionCreator) {
        final List<RiscTemplate> initialTemplates = new LinkedList<RiscTemplate>();
        for (InstructionDescription instructionDescription : instructionDescriptionCreator.instructionDescriptions()) {
            final RiscTemplate template = createTemplate(instructionDescription);
            initialTemplates.add(template);
            RiscInstructionDescriptionVisitor.Static.visitInstructionDescription(template, instructionDescription);
        }
        for (RiscTemplate initialTemplate : initialTemplates) {
            List<RiscTemplate> newTemplates = new LinkedList<RiscTemplate>();
            newTemplates.add(initialTemplate);
            for (OptionField optionField : initialTemplate.optionFields()) {
                newTemplates = createOptionTemplates(newTemplates, optionField);
            }
            for (RiscTemplate template : newTemplates) {
                serial++;
                template.setSerial(serial);
                templates.add(template);
                nameToTemplates.get(template.internalName()).add(template);

                // Create the link to the non-synthetic instruction from which a synthetic instruction is derived.
                if (template.instructionDescription().isSynthetic()) {
                    boolean found = false;
                outerLoop:
                    for (List<RiscTemplate> list : nameToTemplates.values()) {
                        final Iterator<RiscTemplate> iterator = list.iterator();
                        while (iterator.hasNext()) {
                            final RiscTemplate rawTemplate = iterator.next();
                            if (!rawTemplate.instructionDescription().isSynthetic() && (template.opcodeMask() & rawTemplate.opcodeMask()) == rawTemplate.opcodeMask() &&
                                            (template.opcode() & rawTemplate.opcodeMask()) == rawTemplate.opcode()) {
                                template.setSynthesizedFrom(rawTemplate);
                                found = true;
                                break outerLoop;
                            }
                        }
                    }
                    ProgramError.check(found);
                }
            }
        }
    }

}
