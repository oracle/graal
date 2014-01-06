/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.nodes.core;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.core.*;

@CoreClass(name = "Struct")
public abstract class StructNodes {

    @CoreMethod(names = "initialize", needsBlock = true, appendCallNode = true, isSplatted = true)
    public abstract static class InitalizeNode extends CoreMethodNode {

        public InitalizeNode(RubyContext context, SourceSection sourceSection) {
            super(context, sourceSection);
        }

        public InitalizeNode(InitalizeNode prev) {
            super(prev);
        }

        @Specialization
        public NilPlaceholder initialize(VirtualFrame frame, RubyClass struct, Object[] args, Object block, Node callSite) {
            CompilerDirectives.transferToInterpreter();

            final RubySymbol[] symbols = new RubySymbol[args.length];

            for (int n = 0; n < args.length; n++) {
                symbols[n] = (RubySymbol) args[n];
            }

            for (RubySymbol symbol : symbols) {
                ModuleNodes.AttrAccessorNode.attrAccessor(getContext(), callSite.getSourceSection(), struct, symbol.toString());
            }

            if (!RubyNilClass.isNil(block)) {
                ((RubyProc) block).callWithModifiedSelf(frame.pack(), struct);
            }

            return NilPlaceholder.INSTANCE;
        }

    }

}
