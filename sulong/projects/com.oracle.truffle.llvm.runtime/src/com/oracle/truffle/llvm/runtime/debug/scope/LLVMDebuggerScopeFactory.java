/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMScopeChain;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceContext;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObjectBuilder;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMInstrumentableNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMDispatchBasicBlockNode;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.symbols.LLVMIdentifier;
import com.oracle.truffle.llvm.runtime.types.symbols.LocalVariableDebugInfo;
import com.oracle.truffle.llvm.runtime.types.symbols.SSAValue;

public final class LLVMDebuggerScopeFactory {

    private static LLVMSourceLocation findSourceLocation(Node suspendedNode) {
        for (Node node = suspendedNode; node != null; node = node.getParent()) {
            if (node instanceof LLVMInstrumentableNode) {
                final LLVMSourceLocation sourceLocation = ((LLVMInstrumentableNode) node).getSourceLocation();
                if (sourceLocation != null) {
                    return sourceLocation;
                }
            } else if (node instanceof RootNode) {
                return null;
            }
        }
        return null;
    }

    @TruffleBoundary
    private static LLVMDebuggerScopeEntries getIRLevelEntries(Frame frame, LLVMContext context, DataLayout dataLayout) {
        FrameDescriptor desc = frame.getFrameDescriptor();
        if (frame == null || desc.getNumberOfSlots() == 0) {
            return LLVMDebuggerScopeEntries.EMPTY_SCOPE;
        }

        final LLVMDebuggerScopeEntries entries = new LLVMDebuggerScopeEntries();
        for (int slot = 0; slot < desc.getNumberOfSlots(); slot++) {
            if (desc.getSlotInfo(slot) instanceof SSAValue) {
                SSAValue stackValue = (SSAValue) desc.getSlotInfo(slot);
                String identifier = stackValue.getName();
                Object slotValue = frame.getValue(slot);
                if (slotValue == null) { // slots are null if they are cleared by LLVMFrameNuller
                    slotValue = "<unavailable>";
                }
                Object value = CommonNodeFactory.toGenericDebuggerValue(stackValue.getType(), slotValue, dataLayout);
                entries.add(convertIdentifier(identifier, context), value);
            }
        }

        return entries;
    }

    @TruffleBoundary
    private static LLVMDebuggerScopeEntries toDebuggerScope(LLVMScopeChain scope, DataLayout dataLayout, LLVMContext context) {
        final LLVMDebuggerScopeEntries entries = new LLVMDebuggerScopeEntries();
        LLVMScopeChain next = scope;
        while (next != null) {
            for (LLVMSymbol symbol : next.getScope().values()) {
                if (symbol.isGlobalVariable()) {
                    LLVMGlobal global = symbol.asGlobalVariable();
                    Object value = CommonNodeFactory.toGenericDebuggerValue(global.getPointeeType(), context.getSymbolUncached(global), dataLayout);
                    entries.add(LLVMIdentifier.toGlobalIdentifier(global.getName()), value);
                }
            }
            next = next.getNext();
        }
        return entries;
    }

    @TruffleBoundary
    private static LLVMDebuggerScopeEntries toDebuggerScope(LLVMSourceLocation.TextModule irScope, DataLayout dataLayout, LLVMContext context) {
        final LLVMDebuggerScopeEntries entries = new LLVMDebuggerScopeEntries();
        for (LLVMGlobal global : irScope) {
            if (global.hasValidIndexAndID()) {
                Object value = CommonNodeFactory.toGenericDebuggerValue(new PointerType(global.getPointeeType()), context.getSymbolUncached(global), dataLayout);
                entries.add(LLVMIdentifier.toGlobalIdentifier(global.getName()), value);
            }
        }
        return entries;
    }

    @TruffleBoundary
    private static LLVMDebuggerScopeEntries getIRLevelEntries(Node node, LLVMContext context, DataLayout dataLayout) {
        for (LLVMSourceLocation location = findSourceLocation(node); location != null; location = location.getParent()) {
            if (location instanceof LLVMSourceLocation.TextModule) {
                return toDebuggerScope((LLVMSourceLocation.TextModule) location, dataLayout, context);
            }
        }
        return toDebuggerScope(context.getGlobalScopeChain(), dataLayout, context);
    }

    @TruffleBoundary
    public static Object createIRLevelScope(Node node, Frame frame, LLVMContext context) {
        DataLayout dataLayout = LLVMNode.findDataLayout(node);
        final LLVMDebuggerScopeEntries localScope = getIRLevelEntries(frame, context, dataLayout);
        final LLVMDebuggerScopeEntries globalScope = getIRLevelEntries(node, context, dataLayout);
        localScope.setParentScope(globalScope);
        return localScope;
    }

    @TruffleBoundary
    public static Object createSourceLevelScope(Node node, Frame frame, LLVMContext context) {
        final LLVMSourceContext sourceContext = context.getSourceContext();
        final RootNode rootNode = node.getRootNode();
        LLVMSourceLocation scope = findSourceLocation(node);

        if (rootNode == null || scope == null) {
            return new LLVMDebuggerScopeFactory(context, sourceContext, node).getVariables(frame);
        }

        final SourceSection sourceSection = scope.getSourceSection();
        LLVMDebuggerScopeFactory baseScope = new LLVMDebuggerScopeFactory(context, sourceContext, new ArrayList<>(), node);
        LLVMDebuggerScopeFactory staticScope = null;

        for (boolean isLocalScope = true; isLocalScope && scope != null; scope = scope.getParent()) {
            final LLVMDebuggerScopeFactory next = toScope(context, scope, sourceContext, node, sourceSection);
            copySymbols(next, baseScope);
            if (scope.getKind() == LLVMSourceLocation.Kind.FUNCTION) {
                baseScope.setName(next.getName());
                if (scope.getCompileUnit() != null) {
                    staticScope = toScope(context, scope.getCompileUnit(), sourceContext, null, sourceSection);
                }
                isLocalScope = false;
            }
        }

        LLVMDebuggerScopeEntries baseScopeEntry = baseScope.getVariables(frame);
        LLVMDebuggerScopeEntries currentParentScopeEntry = baseScopeEntry;
        for (; scope != null; scope = scope.getParent()) {
            // e.g. lambdas are compiled to calls to a method in a locally defined class. We
            // cannot access the locals of the enclosing function since they do not lie on the
            // function's frame. They are still accessible from the calling function's frame, so
            // we can simply ignore this scope here. Also, any variables actually used in the
            // lambda would still be available as the members of the 'this' pointer.
            final LLVMDebuggerScopeFactory next = toScope(context, scope, sourceContext, null, sourceSection);
            LLVMDebuggerScopeEntries nextScopeEntry = next.getVariables(frame);
            switch (scope.getKind()) {
                case NAMESPACE:
                case FILE:
                case BLOCK:
                    if (next.hasSymbols()) {
                        currentParentScopeEntry.setParentScope(nextScopeEntry);
                        currentParentScopeEntry = nextScopeEntry;
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

        if (staticScope != null && staticScope.hasSymbols()) {
            // this is top level scope, there is no scope above this scope.
            currentParentScopeEntry.setParentScope(staticScope.getVariables(frame));
        }

        return baseScopeEntry;
    }

    private static void copySymbols(LLVMDebuggerScopeFactory source, LLVMDebuggerScopeFactory target) {
        // always exclude shadowed symbols
        if (!source.symbols.isEmpty()) {
            final Set<String> names = target.symbols.stream().map(LLVMSourceSymbol::getName).collect(Collectors.toSet());
            source.symbols.stream().filter(s -> !names.contains(s.getName())).forEach(target.symbols::add);
        }
    }

    private static LLVMDebuggerScopeFactory toScope(LLVMContext context, LLVMSourceLocation scope, LLVMSourceContext sourceContext, Node node, SourceSection sourceSection) {
        if (!scope.hasSymbols()) {
            final LLVMDebuggerScopeFactory sourceScope = new LLVMDebuggerScopeFactory(context, sourceContext, node);
            sourceScope.setName(scope.getName());
            return sourceScope;
        }

        final ArrayList<LLVMSourceSymbol> symbols = new ArrayList<>();
        for (LLVMSourceSymbol symbol : scope.getSymbols()) {
            if (symbol.isStatic() || isDeclaredBefore(symbol, sourceSection)) {
                symbols.add(symbol);
            }
        }

        final LLVMDebuggerScopeFactory sourceScope = new LLVMDebuggerScopeFactory(context, sourceContext, symbols, node);
        sourceScope.setName(scope.getName());
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

    private final LLVMContext context;
    private final LLVMSourceContext sourceContext;
    private final ArrayList<LLVMSourceSymbol> symbols;
    private final Node node;

    private String name;

    private LLVMDebuggerScopeFactory(LLVMContext context, LLVMSourceContext sourceContext, Node node) {
        this(context, sourceContext, new ArrayList<>(), node);
    }

    private LLVMDebuggerScopeFactory(LLVMContext context, LLVMSourceContext sourceContext, ArrayList<LLVMSourceSymbol> symbols, Node node) {
        this.context = context;
        this.sourceContext = sourceContext;
        this.symbols = symbols;
        this.node = node;
        this.name = DEFAULT_NAME;
    }

    private void setName(String name) {
        this.name = name;
    }

    private boolean hasSymbols() {
        return !symbols.isEmpty();
    }

    protected String getName() {
        return name;
    }

    private static String convertIdentifier(String identifier, LLVMContext context) {
        if (context.getEnv().getOptions().get(SulongEngineOption.LL_DEBUG)) {
            // IR-level debugging always expects "%" prefix
            return LLVMIdentifier.toLocalIdentifier(identifier);
        }
        try {
            Integer.parseInt(identifier);
            return LLVMIdentifier.toLocalIdentifier(identifier);
        } catch (NumberFormatException e) {
            // don't prepend "%" for custom names
            return identifier;
        }
    }

    @TruffleBoundary
    private LLVMDebuggerScopeEntries getVariables(Frame frame) {
        if (symbols.isEmpty()) {
            return LLVMDebuggerScopeEntries.EMPTY_SCOPE;
        }

        final LLVMDebuggerScopeEntries vars = new LLVMDebuggerScopeEntries();
        vars.setScopeName(getName());

        LLVMDispatchBasicBlockNode dispatchBlock = LLVMNode.getParent(node, LLVMDispatchBasicBlockNode.class);

        if (frame != null && dispatchBlock != null) {
            LocalVariableDebugInfo debugInfo = dispatchBlock.getDebugInfo();
            Map<LLVMSourceSymbol, Object> localVariables = debugInfo.getLocalVariables(frame, node);
            for (Map.Entry<LLVMSourceSymbol, Object> entry : localVariables.entrySet()) {
                LLVMSourceSymbol symbol = entry.getKey();
                if (symbols.contains(symbol)) {
                    vars.add(symbol.getName(), entry.getValue());
                }
            }
        }

        for (LLVMSourceSymbol symbol : symbols) {
            if (!vars.contains(symbol.getName())) {
                LLVMDebugObjectBuilder dbgVal = sourceContext.getStatic(symbol);

                if (dbgVal == null) {
                    dbgVal = LLVMDebugObjectBuilder.UNAVAILABLE;
                }

                vars.add(convertIdentifier(symbol.getName(), context), dbgVal.getValue(symbol));
            }
        }

        return vars;
    }
}
