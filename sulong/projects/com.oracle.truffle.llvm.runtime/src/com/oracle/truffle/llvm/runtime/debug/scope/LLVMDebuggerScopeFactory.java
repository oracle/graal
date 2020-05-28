/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.AssumedValue;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceContext;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObjectBuilder;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMInstrumentableNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMDispatchBasicBlockNode;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
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
        if (frame == null || frame.getFrameDescriptor().getSlots().isEmpty()) {
            return LLVMDebuggerScopeEntries.EMPTY_SCOPE;
        }

        final LLVMDebuggerScopeEntries entries = new LLVMDebuggerScopeEntries();
        for (final FrameSlot slot : frame.getFrameDescriptor().getSlots()) {
            if (slot.getInfo() instanceof SSAValue) {
                SSAValue stackValue = (SSAValue) slot.getInfo();
                String identifier = stackValue.getName();
                Object slotValue = frame.getValue(slot);
                if (slotValue == null) { // slots are null if they are cleared by LLVMFrameNuller
                    slotValue = "<unavailable>";
                }
                TruffleObject value = CommonNodeFactory.toGenericDebuggerValue(stackValue.getType(), slotValue, dataLayout);
                entries.add(convertIdentifier(identifier, context), value);
            }
        }

        return entries;
    }

    @TruffleBoundary
    private static LLVMDebuggerScopeEntries toDebuggerScope(LLVMScope scope, DataLayout dataLayout, LLVMContext context) {
        final LLVMDebuggerScopeEntries entries = new LLVMDebuggerScopeEntries();
        for (LLVMSymbol symbol : scope.values()) {
            if (symbol.isGlobalVariable()) {
                final LLVMGlobal global = symbol.asGlobalVariable();
                int id = global.getBitcodeID(false);
                int index = global.getSymbolIndex(false);
                AssumedValue<LLVMPointer>[] globals = context.findSymbolTable(id);
                final TruffleObject value = CommonNodeFactory.toGenericDebuggerValue(global.getPointeeType(), globals[index].get(), dataLayout);
                entries.add(LLVMIdentifier.toGlobalIdentifier(global.getName()), value);
            }
        }
        return entries;
    }

    @TruffleBoundary
    private static LLVMDebuggerScopeEntries toDebuggerScope(LLVMSourceLocation.TextModule irScope, DataLayout dataLayout, LLVMContext context) {
        final LLVMDebuggerScopeEntries entries = new LLVMDebuggerScopeEntries();
        for (LLVMGlobal global : irScope) {
            if (global.hasValidIndexAndID()) {
                int id = global.getBitcodeID(false);
                int index = global.getSymbolIndex(false);
                AssumedValue<LLVMPointer>[] globals = context.findSymbolTable(id);
                final TruffleObject value = CommonNodeFactory.toGenericDebuggerValue(new PointerType(global.getPointeeType()), globals[index].get(), dataLayout);
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
        return toDebuggerScope(context.getGlobalScope(), dataLayout, context);
    }

    @TruffleBoundary
    public static Iterable<Scope> createIRLevelScope(Node node, Frame frame, LLVMContext context) {
        DataLayout dataLayout = LLVMNode.findDataLayout(node);
        final Scope localScope = Scope.newBuilder("function", getIRLevelEntries(frame, context, dataLayout)).node(node).build();
        final Scope globalScope = Scope.newBuilder("module", getIRLevelEntries(node, context, dataLayout)).build();
        return Arrays.asList(localScope, globalScope);
    }

    @TruffleBoundary
    public static Collection<Scope> createSourceLevelScope(Node node, Frame frame, LLVMContext context) {
        final LLVMSourceContext sourceContext = context.getSourceContext();
        final RootNode rootNode = node.getRootNode();
        LLVMSourceLocation scope = findSourceLocation(node);

        if (rootNode == null || scope == null) {
            return Collections.singleton(new LLVMDebuggerScopeFactory(context, sourceContext, node).toScope(frame));
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

        List<Scope> scopeList = new ArrayList<>();
        scopeList.add(baseScope.toScope(frame));
        for (; scope != null; scope = scope.getParent()) {
            // e.g. lambdas are compiled to calls to a method in a locally defined class. We
            // cannot access the locals of the enclosing function since they do not lie on the
            // function's frame. They are still accessible from the calling function's frame, so
            // we can simply ignore this scope here. Also, any variables actually used in the
            // lambda would still be available as the members of the 'this' pointer.
            final LLVMDebuggerScopeFactory next = toScope(context, scope, sourceContext, null, sourceSection);
            switch (scope.getKind()) {
                case NAMESPACE:
                case FILE:
                case BLOCK:
                    if (next.hasSymbols()) {
                        scopeList.add(next.toScope(frame));
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
            scopeList.add(staticScope.toScope(frame));
        }

        return Collections.unmodifiableList(scopeList);
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

    private static final String DEFAULT_RECEIVER_NAME = "this";
    private static final String DEFAULT_RECEIVER = "<none>";

    private Scope toScope(Frame frame) {
        final LLVMDebuggerScopeEntries variables = getVariables(frame);
        final Scope.Builder scopeBuilder = Scope.newBuilder(name, variables);

        // while the Truffle API allows any name for the receiver, the chrome inspector protocol
        // requires "this" as member of the local scope. the current chrome inspector implementation
        // will thus always show such a member, defaulting to "null" if the receiver is not
        // explicitly set or has a different name. we make sure it has a value that does not confuse
        // the user.
        if (variables.contains(DEFAULT_RECEIVER_NAME)) {
            scopeBuilder.receiver(DEFAULT_RECEIVER_NAME, variables.getElementForDebugger(DEFAULT_RECEIVER_NAME));

            // the receiver should not be a scope member too, otherwise the debugger would display
            // it twice
            variables.removeElement(DEFAULT_RECEIVER_NAME);
        } else {
            scopeBuilder.receiver(DEFAULT_RECEIVER_NAME, DEFAULT_RECEIVER);
        }

        scopeBuilder.node(node);

        return scopeBuilder.build();
    }
}
