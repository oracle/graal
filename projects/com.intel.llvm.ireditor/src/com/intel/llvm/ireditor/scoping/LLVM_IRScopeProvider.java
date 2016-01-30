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

package com.intel.llvm.ireditor.scoping;

import java.util.LinkedList;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.xtext.scoping.IScope;
import org.eclipse.xtext.scoping.Scopes;
import org.eclipse.xtext.scoping.impl.AbstractDeclarativeScopeProvider;

import com.intel.llvm.ireditor.lLVM_IR.BasicBlock;
import com.intel.llvm.ireditor.lLVM_IR.BasicBlockRef;
import com.intel.llvm.ireditor.lLVM_IR.FunctionDef;
import com.intel.llvm.ireditor.lLVM_IR.GlobalValue;
import com.intel.llvm.ireditor.lLVM_IR.Instruction;
import com.intel.llvm.ireditor.lLVM_IR.LocalValueRef;
import com.intel.llvm.ireditor.lLVM_IR.MiddleInstruction;
import com.intel.llvm.ireditor.lLVM_IR.Model;
import com.intel.llvm.ireditor.lLVM_IR.StartingInstruction;
import com.intel.llvm.ireditor.lLVM_IR.TerminatorInstruction;
import com.intel.llvm.ireditor.lLVM_IR.TopLevelElement;

/**
 * This class contains custom scoping description.
 *
 * see : http://www.eclipse.org/Xtext/documentation/latest/xtext.html#scoping on how and when to use
 * it
 *
 */
public class LLVM_IRScopeProvider extends AbstractDeclarativeScopeProvider {
    @Override
    public IScope getScope(EObject context, EReference reference) {
        if (reference.getContainerClass() == LocalValueRef.class) {
            // A local value must be a local instruction, a local parameter,
            // or a global value. It can't be local from another function.
            FunctionDef func = getAncestor(context, FunctionDef.class);
            if (func == null) {
                return super.getScope(context, reference);
            }
            LinkedList<EObject> inScope = new LinkedList<>();
            addLocalInstructions(inScope, func);
            addParameters(inScope, func);
            addGlobals(inScope, func);
            return Scopes.scopeFor(inScope);
        } else if (reference.getContainerClass() == BasicBlockRef.class) {
            // A basic block reference can only refer to blocks within the
            // enclosing method.
            FunctionDef def = getAncestor(context, FunctionDef.class);
            if (def == null) {
                return super.getScope(context, reference);
            }
            return Scopes.scopeFor(new LinkedList<EObject>(def.getBasicBlocks()));
        }
        return super.getScope(context, reference);
    }

    private static <U extends EObject> U getAncestor(EObject obj, Class<U> ancestor) {
        EObject currentObj = obj;
        while (!ancestor.isInstance(currentObj)) {
            if (currentObj == null) {
                return null;
            }
            currentObj = currentObj.eContainer();
        }
        return ancestor.cast(currentObj);
    }

    private static EObject getContainedInstruction(Instruction inst) {
        if (inst instanceof StartingInstruction) {
            return ((StartingInstruction) inst).getInstruction();
        }
        if (inst instanceof MiddleInstruction) {
            return ((MiddleInstruction) inst).getInstruction();
        }
        if (inst instanceof TerminatorInstruction) {
            return ((TerminatorInstruction) inst).getInstruction();
        }
        return null;
    }

    private static void addLocalInstructions(LinkedList<EObject> inScope, FunctionDef func) {
        for (BasicBlock block : func.getBasicBlocks()) {
            for (Instruction inst : block.getInstructions()) {
                EObject contained = getContainedInstruction(inst);
                if (contained != null) {
                    inScope.add(contained);
                }
            }
            inScope.addAll(block.getInstructions());
        }
    }

    private static void addParameters(LinkedList<EObject> inScope, FunctionDef func) {
        inScope.addAll(func.getHeader().getParameters().getParameters());
    }

    private static void addGlobals(LinkedList<EObject> inScope, FunctionDef func) {
        Model m = getAncestor(func, Model.class);
        if (m == null) {
            return;
        }
        for (TopLevelElement e : m.getElements()) {
            if (e instanceof GlobalValue) {
                inScope.add(e);
            }
        }
    }

}
