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

import com.sun.max.*;
import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.risc.bitRange.*;
import com.sun.max.asm.gen.risc.field.*;
import com.sun.max.program.*;

/**
 */
public abstract class RiscInstructionDescriptionCreator extends InstructionDescriptionCreator<RiscInstructionDescription> {

    protected final RiscTemplateCreator templateCreator;

    protected RiscInstructionDescriptionCreator(Assembly assembly, RiscTemplateCreator templateCreator) {
        super(assembly);
        this.templateCreator = templateCreator;
    }

    @Override
    protected RiscInstructionDescription createInstructionDescription(List<Object> specifications) {
        return new RiscInstructionDescription(specifications);
    }

    private int firstStringIndex(List<Object> specifications) {
        for (int i = 0; i < specifications.size(); i++) {
            if (specifications.get(i) instanceof String) {
                return i;
            }
        }
        throw ProgramError.unexpected("template instruction description without name");
    }

    private void setFirstString(List<Object> specifications, String value) {
        specifications.set(firstStringIndex(specifications), value);
    }

    private void eliminateConstraintFor(Parameter parameter, List<Object> specifications) {
        for (final Iterator iterator = specifications.iterator(); iterator.hasNext();) {
            final Object s = iterator.next();
            if (s instanceof InstructionConstraint) {
                final InstructionConstraint constraint = (InstructionConstraint) s;
                if (constraint.referencesParameter(parameter)) {
                    iterator.remove();
                }
            }
        }
    }

    private boolean updateSpecifications(List<Object> specifications, Object pattern) {
        for (int i = 0; i < specifications.size(); i++) {
            final Object specification = specifications.get(i);
            if (specification.equals(pattern)) {
                specifications.set(i, pattern);
                return true;
            } else if (pattern instanceof RiscConstant && (specification instanceof OperandField || specification instanceof OptionField)) {
                final RiscConstant constant = (RiscConstant) pattern;
                final RiscField constantField = constant.field();
                final RiscField variableField = (RiscField) specification;
                if (variableField.equals(constantField)) {
                    specifications.set(i, pattern);
                    if (specification instanceof Parameter) {
                        eliminateConstraintFor((Parameter) specification, specifications);
                    }
                    return true;
                }
            } else if (pattern instanceof InstructionConstraint && !(pattern instanceof Parameter)) {
                specifications.add(pattern);
                return true;
            } else if (pattern instanceof RiscField) {
                if (((RiscField) pattern).bitRange() instanceof OmittedBitRange) {
                    specifications.add(pattern);
                    return true;
                }
            }
        }
        return false;
    }

    private RiscInstructionDescription createSyntheticInstructionDescription(String name, RiscTemplate template, Object[] patterns) {
        final List<Object> specifications = new ArrayList<Object>(template.instructionDescription().specifications());
        for (Object pattern : patterns) {
            if (!updateSpecifications(specifications, pattern)) {
                // InstructionDescription with the same name, but different specifications, skip it:
                Trace.line(3, name + " not updated with " + pattern + " in " + specifications);
                return null;
            }
        }
        setFirstString(specifications, name);
        final Class<List<Object>> type = null;
        return (RiscInstructionDescription) defineInstructionDescription(Utils.cast(type, specifications)).beSynthetic();
    }

    /**
     * Creates a synthetic instruction from a previously defined (raw or synthetic) instruction
     * by replacing one or more parameters of the instruction with a constant or alternative parameter.
     *
     * @param name          the internal (base) name of the new synthetic instruction
     * @param templateName  the internal name of the original instruction on which the synthetic instruction is based
     * @param patterns      the replacements for one or more parameters of the original instruction
     * @return the newly created instruction descriptions resulting from the substitution wrapped in a RiscInstructionDescriptionModifier
     */
    protected RiscInstructionDescriptionModifier synthesize(String name, String templateName, Object... patterns) {
        final List<RiscInstructionDescription> instructionDescriptions = new ArrayList<RiscInstructionDescription>();
        // Creating a new VariableSequence here prevents iterator comodification below:
        final List<? extends RiscTemplate> nameTemplates = templateCreator.nameToTemplates(templateName);
        if (!nameTemplates.isEmpty()) {
            final List<RiscTemplate> templates = new ArrayList<RiscTemplate>(nameTemplates);
            assert !templates.isEmpty();
            for (RiscTemplate template : templates) {
                final RiscInstructionDescription instructionDescription = createSyntheticInstructionDescription(name, template, patterns);
                if (instructionDescription != null) {
                    instructionDescriptions.add(instructionDescription);
                }
            }
        }
        ProgramError.check(!instructionDescriptions.isEmpty());
        return new RiscInstructionDescriptionModifier(instructionDescriptions);
    }
}
