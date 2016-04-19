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

package com.intel.llvm.ireditor;

import java.util.Iterator;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.AbstractRule;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.RuleCall;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import com.intel.llvm.ireditor.lLVM_IR.BasicBlock;
import com.intel.llvm.ireditor.lLVM_IR.FunctionDef;
import com.intel.llvm.ireditor.lLVM_IR.FunctionHeader;
import com.intel.llvm.ireditor.lLVM_IR.GlobalValue;
import com.intel.llvm.ireditor.lLVM_IR.LLVM_IRPackage.Literals;
import com.intel.llvm.ireditor.lLVM_IR.MiddleInstruction;
import com.intel.llvm.ireditor.lLVM_IR.Parameter;
import com.intel.llvm.ireditor.lLVM_IR.StartingInstruction;
import com.intel.llvm.ireditor.lLVM_IR.TerminatorInstruction;

public class ReverseElementIterable implements Iterable<EObject> {
    public enum Mode {
        STARTING_INST(StartingInstruction.class),
        MIDDLE_INST(MiddleInstruction.class),
        TERMINATOR_INST(TerminatorInstruction.class),
        BB(BasicBlock.class),
        PARAM(Parameter.class),
        GLOBAL(GlobalValue.class);

        private final Class<? extends EObject> eobjectType;
        private final String ruleName;

        Mode(Class<? extends EObject> eobjectType) {
            this.eobjectType = eobjectType;
            ruleName = eobjectType.getSimpleName();
        }

        public Class<? extends EObject> getEObjectType() {
            return eobjectType;
        }

        public Object getRuleName() {
            return ruleName;
        }
    }

    private final INode initialNode;
    private final ReverseElementIterable.Mode initialMode;

    public ReverseElementIterable(EObject object) {
        for (Mode mode : Mode.values()) {
            EObject container = EcoreUtil2.getContainerOfType(object, mode.getEObjectType());
            if (container != null) {
                initialMode = mode;
                if (initialMode != Mode.GLOBAL) {
                    initialNode = NodeModelUtils.findActualNodeFor(container);
                } else {
                    // FIXME this returns the node associated with the TopLevelElement, though I
                    // don't
                    // understand why.
                    initialNode = NodeModelUtils.findActualNodeFor(container).getFirstChild();
                }
                return;
            }
        }

        throw new IllegalArgumentException("Can only reverse iterate from a basic block, a paremeter, a global or an instruction");
    }

    public ReverseElementIterable(INode node) {
        INode currentNode = node;
        while (currentNode != null) {
            EObject obj = currentNode.getGrammarElement();
            if (obj instanceof RuleCall) {
                AbstractRule rule = ((RuleCall) obj).getRule();
                String ruleName = rule.getName();
                for (Mode mode : Mode.values()) {
                    if (mode.getRuleName().equals(ruleName)) {
                        initialMode = mode;
                        initialNode = currentNode;
                        return;
                    }
                }
            }
            currentNode = currentNode.getParent();
        }

        throw new IllegalArgumentException("Can only reverse iterate from a basic block, a paremeter, a global or an instruction");
    }

    @Override
    public Iterator<EObject> iterator() {
        return new ReverseElementIterator();
    }

    class ReverseElementIterator implements Iterator<EObject> {
        INode currNode = initialNode;
        INode nextNode;
        Mode currMode = initialMode;
        Mode nextMode;

        @Override
        public boolean hasNext() {
            try {
                switch (currMode) {
                    case STARTING_INST:
                    case MIDDLE_INST:
                    case TERMINATOR_INST: {
                        nextNode = inst2inst(currNode); // This also sets nextMode if it doesn't
                        // return null
                        if (nextNode == null) {
                            nextNode = inst2bb(currNode);
                            nextMode = Mode.BB;
                        }
                        break;
                    }
                    case BB: {
                        nextNode = bb2inst(currNode);
                        nextMode = Mode.TERMINATOR_INST;
                        if (nextNode == null) {
                            nextNode = bb2param(currNode);
                            nextMode = Mode.PARAM;
                        }
                        break;
                    }
                    case PARAM: {
                        nextNode = param2param(currNode);
                        nextMode = currMode;
                        break;
                    }
                    case GLOBAL: {
                        nextNode = global2global(currNode);
                        nextMode = currMode;
                        break;
                    }
                }
                return nextNode != null;
            } catch (RuntimeException e) {
                // There's likely another error involved here. We don't find to report
                // an error from the reverse element iterator in that case, so we just give up.
                return false;
            }
        }

        @Override
        public EObject next() {
            currMode = nextMode;
            currNode = nextNode;
            return NodeModelUtils.findActualSemanticObjectFor(currNode);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private INode inst2inst(INode node) {
            // Previous instruction of the same type (terminator, middle, starting)
            INode prev = node.getPreviousSibling();
            String ruleName = ((RuleCall) prev.getGrammarElement()).getRule().getName();
            for (Mode mode : Mode.values()) {
                if (mode.getRuleName().equals(ruleName)) {
                    nextMode = mode;
                    return prev;
                }
            }

            return null;
        }

        private INode inst2bb(INode node) {
            return node.getParent();
        }

        private INode bb2inst(INode node) {
            INode prev = node.getPreviousSibling();
            if (!(prev instanceof ICompositeNode)) {
                return null;
            }
            return ((ICompositeNode) prev).getLastChild();
        }

        private INode global2global(INode node) {
            INode toplevel = node.getParent();
            INode prev = toplevel.getPreviousSibling();
            if (!(prev instanceof ICompositeNode)) {
                return null;
            }
            return ((ICompositeNode) prev).getFirstChild();
        }

        private INode param2param(INode node) {
            return node.getPreviousSibling();
        }

        private INode bb2param(INode node) {
            // This method is full of sub-checks because the source file may be malformed, so
            // the rest of the tree, including the header, may have missing components.
            ICompositeNode functionDefNode = node.getParent();

            // Get header node from function def:
            if (!(functionDefNode.hasDirectSemanticElement())) {
                return null;
            }
            FunctionDef functionDef = (FunctionDef) functionDefNode.getSemanticElement();
            List<INode> headerNodes = NodeModelUtils.findNodesForFeature(functionDef, Literals.FUNCTION_DEF.getEStructuralFeature("header"));
            if (headerNodes.isEmpty()) {
                return null;
            }
            INode headerNode = headerNodes.get(0);

            // Get parameters node from function header:
            if (!(headerNode.hasDirectSemanticElement())) {
                return null;
            }
            FunctionHeader header = (FunctionHeader) headerNode.getSemanticElement();
            List<INode> paramNodes = NodeModelUtils.findNodesForFeature(header, Literals.FUNCTION_HEADER__PARAMETERS);
            if (paramNodes.isEmpty()) {
                return null;
            }
            INode paramNode = paramNodes.get(0);

            // Get last parameter from parameter node:
            if (!(paramNode instanceof ICompositeNode)) {
                return null;
            }
            return ((ICompositeNode) paramNode).getLastChild(); // The last parameter
        }
    }
}
