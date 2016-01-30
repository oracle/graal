/*
Copyright (c) 2013, Intel Corporation

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice,
      this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice,
      this list of conditions and the following disclaimer in the documentation
      and/or other materials provided with the distribution.
    * Neither the name of Intel Corporation nor the names of its contributors
      may be used to endorse or promote products derived from this software
      without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.intel.llvm.ireditor.names;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.emf.ecore.EObject;

import com.intel.llvm.ireditor.lLVM_IR.Alias;
import com.intel.llvm.ireditor.lLVM_IR.BasicBlock;
import com.intel.llvm.ireditor.lLVM_IR.FunctionDecl;
import com.intel.llvm.ireditor.lLVM_IR.FunctionDef;
import com.intel.llvm.ireditor.lLVM_IR.GlobalVariable;
import com.intel.llvm.ireditor.lLVM_IR.MiddleInstruction;
import com.intel.llvm.ireditor.lLVM_IR.NamedMetadata;
import com.intel.llvm.ireditor.lLVM_IR.NamedMiddleInstruction;
import com.intel.llvm.ireditor.lLVM_IR.NamedTerminatorInstruction;
import com.intel.llvm.ireditor.lLVM_IR.Parameter;
import com.intel.llvm.ireditor.lLVM_IR.StartingInstruction;
import com.intel.llvm.ireditor.lLVM_IR.TerminatorInstruction;
import com.intel.llvm.ireditor.lLVM_IR.TypeDef;
import com.intel.llvm.ireditor.lLVM_IR.util.LLVM_IRSwitch;

public class NameResolver extends LLVM_IRSwitch<String> {
    private static final Pattern NUMBERED_NAME_PATTERN = Pattern.compile("([%@!])(\\d+)(\\s*=\\s*)?");
    private static final Pattern NUMBERED_BB_PATTERN = Pattern.compile("(\\d+):");

    public String resolveName(EObject element) {
        return doSwitch(element);
    }

    public NumberedName resolveNumberedName(EObject element) {
        if (element == null) {
            return null;
        }
        String name = resolveName(element);
        // No name:
        if (name == null) {
            return null;
        }

        Matcher m = NUMBERED_NAME_PATTERN.matcher(name);
        if (m.matches()) {
            // Numbered non-bb name:
            return new NumberedName(m.group(1), Integer.parseInt(m.group(2)));
        }
        m = NUMBERED_BB_PATTERN.matcher(name);
        if (m.matches()) {
            // Numbered bb name:
            return new NumberedName("%", Integer.parseInt(m.group(1)));
        }

        // Non-numbered name:
        return null;
    }

    @Override
    public String caseAlias(Alias object) {
        return object.getName();
    }

    @Override
    public String caseBasicBlock(BasicBlock object) {
        return object.getName();
    }

    @Override
    public String caseGlobalVariable(GlobalVariable object) {
        return object.getName();
    }

    @Override
    public String caseFunctionDecl(FunctionDecl object) {
        return object.getHeader().getName();
    }

    @Override
    public String caseFunctionDef(FunctionDef object) {
        return object.getHeader().getName();
    }

    @Override
    public String caseNamedMetadata(NamedMetadata object) {
        return object.getName();
    }

    @Override
    public String caseStartingInstruction(StartingInstruction object) {
        return object.getName();
    }

    @Override
    public String caseNamedMiddleInstruction(NamedMiddleInstruction object) {
        return object.getName();
    }

    @Override
    public String caseNamedTerminatorInstruction(NamedTerminatorInstruction object) {
        return object.getName();
    }

    @Override
    public String caseMiddleInstruction(MiddleInstruction object) {
        EObject inner = object.getInstruction();
        if (inner instanceof NamedMiddleInstruction) {
            return ((NamedMiddleInstruction) inner).getName();
        }
        return null;
    }

    @Override
    public String caseTerminatorInstruction(TerminatorInstruction object) {
        EObject inner = object.getInstruction();
        if (inner instanceof NamedTerminatorInstruction) {
            return ((NamedTerminatorInstruction) inner).getName();
        }
        return null;
    }

    @Override
    public String caseParameter(Parameter object) {
        return object.getName();
    }

    @Override
    public String caseTypeDef(TypeDef object) {
        return object.getName();
    }
}
