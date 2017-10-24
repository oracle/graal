/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.debug.scope;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.metadata.ScopeProvider;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugValue;
import com.oracle.truffle.llvm.runtime.debug.LLVMDebugObject;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceContext;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceSymbol;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public final class LLVMSourceScope extends ScopeProvider.AbstractScope {

    @TruffleBoundary
    public static LLVMSourceScope create(Node node, LLVMContext context) {
        final RootNode rootNode = node.getRootNode();
        final SourceSection sourceSection = node.getSourceSection();

        if (rootNode == null) {
            return new LLVMSourceScope(node);
        }

        // TODO map scope against rootnode instead of possibly ambiguous name
        final String functionName = rootNode.getName();

        LLVMSourceLocation scope = context.getSourceContext().getSourceScope(functionName);
        if (scope != null) {
            scope = scope.findScope(node.getSourceSection());
        }

        if (scope == null) {
            return new LLVMSourceScope(rootNode);
        }

        final LLVMSourceContext sourceContext = context.getSourceContext();
        LLVMSourceScope baseScope = new LLVMSourceScope(new LinkedList<>(), new LinkedList<>(), rootNode);
        LLVMSourceScope currentScope = baseScope;
        LLVMSourceScope staticScope = null;

        for (boolean isLocalScope = true; isLocalScope && scope != null; scope = scope.getParent()) {
            final LLVMSourceScope next = toScope(scope, sourceContext, rootNode, sourceSection);
            copySymbols(next, currentScope);
            if (scope.getKind() == LLVMSourceLocation.Kind.FUNCTION) {
                currentScope.setName(next.getName());
                if (scope.getCompileUnit() != null) {
                    staticScope = toScope(scope.getCompileUnit(), sourceContext, null, sourceSection);
                }
                isLocalScope = false;
            }
        }

        for (; scope != null; scope = scope.getParent()) {
            // e.g. lambdas are compiled to calls to a method in a locally defined class. We
            // cannot access the locals of the enclosing function since they do not lie on the
            // function's frame. They are still accessible from the calling function's frame, so
            // we can simply ignore this scope here. Also, any variables actually used in the
            // lambda would still be available as the members of the 'this' pointer.
            final LLVMSourceScope next = toScope(scope, sourceContext, null, sourceSection);
            switch (scope.getKind()) {
                case NAMESPACE:
                case FILE:
                case BLOCK:
                    if (!next.isEmpty()) {
                        currentScope.setParent(next);
                        currentScope = next;
                    }
                    break;

                case COMPILEUNIT:
                    if (staticScope == null) {
                        staticScope = next;
                    } else {
                        copySymbols(next, staticScope);
                    }
                    break;
            }
        }

        if (staticScope != null && !staticScope.isEmpty()) {
            currentScope.setParent(staticScope);
        }

        return baseScope;
    }

    private static void copySymbols(LLVMSourceScope source, LLVMSourceScope target) {
        if (!source.globals.isEmpty()) {
            target.globals.addAll(source.globals);
        }

        if (!source.locals.isEmpty()) {
            target.locals.addAll(source.locals);
        }
    }

    private static LLVMSourceScope toScope(LLVMSourceLocation scope, LLVMSourceContext context, Node node, SourceSection sourceSection) {
        if (!scope.hasSymbols()) {
            final LLVMSourceScope sourceScope = new LLVMSourceScope(node);
            sourceScope.setName(scope.getName());
            return sourceScope;
        }

        final List<LLVMSourceSymbol> locals = new LinkedList<>();
        final List<LLVMDebugValue> globals = new LinkedList<>();
        final LLVMSourceScope sourceScope = new LLVMSourceScope(locals, globals, node);
        sourceScope.setName(scope.getName());

        for (LLVMSourceSymbol symbol : scope.getSymbols()) {

            if (symbol.isGlobal()) {
                final LLVMDebugValue value = context.getGlobal(symbol);
                if (value != null) {
                    globals.add(value);
                }

            } else if (isDeclaredBefore(symbol, sourceSection)) {
                locals.add(symbol);
            }
        }

        return sourceScope;
    }

    private static boolean isDeclaredBefore(LLVMSourceSymbol symbol, SourceSection useLoc) {
        // we want to hide any locals that we definitely know are not in scope, we should display
        // any for which we can't tell
        if (useLoc == null) {
            return true;
        }

        LLVMSourceLocation symbolDecl = symbol.getLocation();
        if (symbolDecl == null) {
            return true;
        }

        SourceSection declLoc = symbolDecl.getSourceSection();
        if (declLoc == null) {
            return true;
        }

        if (declLoc.getSource().equals(useLoc.getSource())) {
            return declLoc.getCharIndex() <= useLoc.getCharIndex();
        }

        return true;
    }

    private static final String DEFAULT_NAME = "<scope>";

    private final List<LLVMSourceSymbol> locals;
    private final List<LLVMDebugValue> globals;
    private final Node node;

    private String name;
    private LLVMSourceScope parent;

    private LLVMSourceScope(Node node) {
        this(Collections.emptyList(), Collections.emptyList(), node);
    }

    private LLVMSourceScope(List<LLVMSourceSymbol> locals, List<LLVMDebugValue> globals, Node node) {
        this.locals = locals;
        this.globals = globals;
        this.node = node;
        this.name = DEFAULT_NAME;
        this.parent = null;
    }

    private void setParent(LLVMSourceScope parent) {
        this.parent = parent;
    }

    private void setName(String name) {
        this.name = name;
    }

    private boolean isEmpty() {
        return locals.isEmpty() && globals.isEmpty();
    }

    @Override
    protected String getName() {
        return name;
    }

    @Override
    protected Node getNode() {
        return node;
    }

    @Override
    @TruffleBoundary
    protected Object getVariables(Frame frame) {
        final Map<Object, LLVMDebugObject> vars = new HashMap<>();

        if (frame != null && !locals.isEmpty()) {
            for (FrameSlot slot : frame.getFrameDescriptor().getSlots()) {
                final Object value = frame.getValue(slot);

                if (value instanceof LLVMDebugValue) {
                    final LLVMDebugValue frameValue = (LLVMDebugValue) value;
                    final LLVMSourceSymbol variable = frameValue.getVariable();
                    if (locals.contains(variable)) {
                        vars.put(frameValue.getVariable().getName(), frameValue.getValue());
                    }
                }
            }
        }

        for (LLVMDebugValue global : globals) {
            vars.put(global.getVariable(), global.getValue());
        }

        return new LLVMSourceScopeVariables(vars);
    }

    @Override
    protected LLVMSourceScope findParent() {
        return parent;
    }

    @Override
    protected Object getArguments(Frame frame) {
        return null;
    }
}
