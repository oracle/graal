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
package com.sun.max.asm.gen;

import java.lang.reflect.*;
import java.util.*;

import com.sun.max.asm.*;
import com.sun.max.asm.gen.risc.bitRange.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;

/**
 * An assembly framework, instantiated once per instruction set.
 */
public abstract class Assembly<Template_Type extends Template> {

    private final ISA isa;
    private final Class<Template_Type> templateType;

    protected Assembly(ISA isa, Class<Template_Type> templateType) {
        this.isa = isa;
        this.templateType = templateType;
    }

    public ISA isa() {
        return isa;
    }

    public Class<Template_Type> templateType() {
        return templateType;
    }

    protected abstract List<Template_Type> createTemplates();

    private List<Template_Type> templates;
    private List<Template_Type> labelTemplates;

    public final List<Template_Type> templates() {
        if (templates == null) {
            templates = createTemplates();
        }
        return templates;
    }

    public final List<Template_Type> labelTemplates() {
        if (labelTemplates == null) {
            labelTemplates = new LinkedList<Template_Type>();
            for (Template_Type template : templates()) {
                if (!template.isRedundant() && template.labelParameterIndex() >= 0) {
                    labelTemplates.add(template);
                }
            }
        }
        return labelTemplates;
    }

    public abstract BitRangeOrder bitRangeEndianness();

    private Object getBoxedJavaValue(Argument argument) {
        if (argument instanceof ImmediateArgument) {
            final ImmediateArgument immediateArgument = (ImmediateArgument) argument;
            return immediateArgument.boxedJavaValue();
        }
        return argument;
    }

    public final String createMethodCallString(Template template, List<Argument> argumentList) {
        assert argumentList.size() == template.parameters().size();
        String call = template.assemblerMethodName() + "(";
        for (int i = 0; i < argumentList.size(); i++) {
            call += ((i == 0) ? "" : ", ") + getBoxedJavaValue(argumentList.get(i));
        }
        return call + ")";
    }

    private Method getAssemblerMethod(Assembler assembler, Template template, Class[] parameterTypes) throws NoSuchAssemblerMethodError {
        try {
            return assembler.getClass().getMethod(template.assemblerMethodName(), parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new NoSuchAssemblerMethodError(e.getMessage(), template);
        }
    }

    private Method getAssemblerMethod(Assembler assembler, Template template, List<Argument> arguments) throws NoSuchAssemblerMethodError {
        final Class[] parameterTypes = template.parameterTypes();
        final int index = template.labelParameterIndex();
        if (index >= 0 && arguments.get(index) instanceof Label) {
            parameterTypes[index] = Label.class;
            return getAssemblerMethod(assembler, template, parameterTypes);
        }
        if (template.assemblerMethod == null) {
            template.assemblerMethod = getAssemblerMethod(assembler, template, parameterTypes);
        }
        return template.assemblerMethod;
    }

    public void assemble(Assembler assembler, Template template, List<Argument> arguments) throws AssemblyException, NoSuchAssemblerMethodError {
        assert arguments.size() == template.parameters().size();
        final Method assemblerMethod = getAssemblerMethod(assembler, template, arguments);
        final Object[] objects = new Object[arguments.size()];
        for (int i = 0; i < arguments.size(); i++) {
            objects[i] = getBoxedJavaValue(arguments.get(i));
        }
        try {
            assemblerMethod.invoke(assembler, objects);
        } catch (IllegalArgumentException illegalArgumentException) {
            throw ProgramError.unexpected("argument type mismatch", illegalArgumentException);
        } catch (IllegalAccessException illegalAccessException) {
            throw ProgramError.unexpected("illegal access to assembler method unexpected", illegalAccessException);
        } catch (InvocationTargetException invocationTargetException) {
            final Throwable target = invocationTargetException.getTargetException();
            if (target instanceof AssemblyException) {
                throw (AssemblyException) target;
            }
            if (target instanceof IllegalArgumentException) {
                throw (AssemblyException) new AssemblyException(target.getMessage()).initCause(target);
            }
            throw ProgramError.unexpected(invocationTargetException);
        }
    }
}
