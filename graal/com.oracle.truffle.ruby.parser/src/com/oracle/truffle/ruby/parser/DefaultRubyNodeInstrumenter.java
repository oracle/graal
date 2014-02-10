/*
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.parser;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.instrument.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.nodes.debug.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.debug.*;
import com.oracle.truffle.ruby.runtime.methods.*;

/**
 * Utility for instrumenting Ruby AST nodes to support the language's built-in <A
 * href="http://www.ruby-doc.org/core-2.0.0/Kernel.html#method-i-set_trace_func">tracing
 * facility</A>. It ignores nodes other than {@linkplain NodePhylum#STATEMENT statements}.
 */
final class DefaultRubyNodeInstrumenter implements RubyNodeInstrumenter {

    public DefaultRubyNodeInstrumenter() {
    }

    public RubyNode instrumentAsStatement(RubyNode node) {
        assert node != null;

        final RubyContext context = node.getContext();

        RubyProxyNode proxy;

        if (node instanceof RubyProxyNode) {
            proxy = (RubyProxyNode) node;
        } else {
            proxy = new RubyProxyNode(node.getContext(), node);
            proxy.markAs(NodePhylum.STATEMENT);
            proxy.clearSourceSection();
            proxy.assignSourceSection(node.getSourceSection());
        }

        if (context.getConfiguration().getTrace()) {
            proxy.getProbeChain().appendProbe(new RubyTraceProbe(context));
        }

        if (context.getConfiguration().getDebug()) {
            final SourceSection sourceSection = proxy.getChild().getSourceSection();
            final SourceLineLocation sourceLine = new SourceLineLocation(sourceSection.getSource(), sourceSection.getStartLine());
            proxy.getProbeChain().appendProbe(new InactiveLineDebugProbe(context, sourceLine, context.getRubyDebugManager().getAssumption(sourceLine)));
        }

        return proxy;
    }

    public RubyNode instrumentAsCall(RubyNode node, String callName) {
        return node;
    }

    public RubyNode instrumentAsLocalAssignment(RubyNode node, UniqueMethodIdentifier methodIdentifier, String localName) {
        assert node != null;

        final RubyContext context = node.getContext();

        RubyProxyNode proxy;

        if (node instanceof RubyProxyNode) {
            proxy = (RubyProxyNode) node;
        } else {
            proxy = new RubyProxyNode(node.getContext(), node);
            proxy.markAs(NodePhylum.STATEMENT);
            proxy.clearSourceSection();
            proxy.assignSourceSection(node.getSourceSection());
        }

        if (context.getConfiguration().getDebug()) {
            final MethodLocal methodLocal = new MethodLocal(methodIdentifier, localName);
            proxy.getProbeChain().appendProbe(new InactiveLocalDebugProbe(context, methodLocal, context.getRubyDebugManager().getAssumption(methodLocal)));
        }

        return proxy;
    }

}
