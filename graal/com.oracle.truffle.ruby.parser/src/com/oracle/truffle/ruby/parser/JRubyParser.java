/*
 * Copyright (c) 2013, 2014 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package com.oracle.truffle.ruby.parser;

import java.io.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.ruby.nodes.*;
import com.oracle.truffle.ruby.nodes.control.*;
import com.oracle.truffle.ruby.nodes.literal.*;
import com.oracle.truffle.ruby.nodes.methods.*;
import com.oracle.truffle.ruby.runtime.*;
import com.oracle.truffle.ruby.runtime.control.*;
import com.oracle.truffle.ruby.runtime.core.*;
import com.oracle.truffle.ruby.runtime.methods.*;

public class JRubyParser implements RubyParser {

    private long nextReturnID = 0;

    private final RubyNodeInstrumenter instrumenter;

    public JRubyParser() {
        this(new DefaultRubyNodeInstrumenter());
    }

    public JRubyParser(RubyNodeInstrumenter instrumenter) {
        assert instrumenter != null;
        this.instrumenter = instrumenter;
    }

    @Override
    public RubyParserResult parse(RubyContext context, Source source, ParserContext parserContext, MaterializedFrame parentFrame) {

        // Set up the JRuby parser

        final org.jrubyparser.Parser parser = new org.jrubyparser.Parser();

        // TODO(cs) should this get a new unique method identifier or not?
        final TranslatorEnvironment environment = new TranslatorEnvironment(context, environmentForFrame(context, parentFrame), this, allocateReturnID(), true, true, new UniqueMethodIdentifier());

        // All parsing contexts have a visibility slot at their top level

        environment.addMethodDeclarationSlots();

        final org.jrubyparser.LocalStaticScope staticScope = new org.jrubyparser.LocalStaticScope(null);

        if (parentFrame != null) {
            /*
             * Note that jruby-parser will be mistaken about how deep the existing variables are,
             * but that doesn't matter as we look them up ourselves after being told their in some
             * parent scope.
             */

            MaterializedFrame frame = parentFrame;

            while (frame != null) {
                for (FrameSlot slot : frame.getFrameDescriptor().getSlots()) {
                    if (slot.getIdentifier() instanceof String) {
                        final String name = (String) slot.getIdentifier();
                        if (staticScope.exists(name) == -1) {
                            staticScope.assign(null, name, null);
                        }
                    }
                }

                frame = frame.getArguments(RubyArguments.class).getDeclarationFrame();
            }
        }

        final org.jrubyparser.parser.ParserConfiguration parserConfiguration = new org.jrubyparser.parser.ParserConfiguration(0, org.jrubyparser.CompatVersion.RUBY2_0, staticScope);

        // Parse to the JRuby AST

        org.jrubyparser.ast.RootNode node;

        try {
            node = (org.jrubyparser.ast.RootNode) parser.parse(source.getName(), new StringReader(source.getCode()), parserConfiguration);
        } catch (UnsupportedOperationException | org.jrubyparser.lexer.SyntaxException e) {
            String message = e.getMessage();

            if (message == null) {
                message = "(no message)";
            }

            throw new RaiseException(new RubyException(context.getCoreLibrary().getSyntaxErrorClass(), message));
        }

        if (context.getConfiguration().getPrintParseTree()) {
            System.err.println(node);
        }

        // Translate to Ruby Truffle nodes

        final Translator translator;

        if (parserContext == RubyParser.ParserContext.MODULE) {
            translator = new ModuleTranslator(context, null, environment, source);
        } else {
            translator = new Translator(context, null, environment, source);
        }

        RubyNode truffleNode;

        final DebugManager debugManager = context.getDebugManager();
        try {
            if (debugManager != null) {
                debugManager.notifyStartLoading(source);
            }

            if (node.getBody() == null) {
                truffleNode = new NilNode(context, null);
            } else {
                truffleNode = (RubyNode) node.getBody().accept(translator);
            }

            // Load flip-flop states

            if (environment.getFlipFlopStates().size() > 0) {
                truffleNode = new SequenceNode(context, truffleNode.getSourceSection(), translator.initFlipFlopStates(truffleNode.getSourceSection()), truffleNode);
            }

            // Catch next

            truffleNode = new CatchNextNode(context, truffleNode.getSourceSection(), truffleNode);

            // Catch return

            truffleNode = new CatchReturnAsErrorNode(context, truffleNode.getSourceSection(), truffleNode);

            // Shell result

            if (parserContext == RubyParser.ParserContext.SHELL) {
                truffleNode = new ShellResultNode(context, truffleNode.getSourceSection(), truffleNode);
            }

            // Root Node

            String indicativeName;

            switch (parserContext) {
                case TOP_LEVEL:
                    indicativeName = "(main)";
                    break;
                case SHELL:
                    indicativeName = "(shell)";
                    break;
                case MODULE:
                    indicativeName = "(module)";
                    break;
                default:
                    throw new UnsupportedOperationException();
            }

            final RootNode root = new RubyRootNode(truffleNode.getSourceSection(), environment.getFrameDescriptor(), indicativeName, truffleNode);

            // Return the root and the frame descriptor
            return new RubyParserResult(root);
        } finally {
            if (debugManager != null) {
                debugManager.notifyFinishedLoading(source);
            }
        }
    }

    public long allocateReturnID() {
        if (nextReturnID == Long.MAX_VALUE) {
            throw new RuntimeException("Return IDs exhausted");
        }

        final long allocated = nextReturnID;
        nextReturnID++;
        return allocated;
    }

    public RubyNodeInstrumenter getNodeInstrumenter() {
        return instrumenter;
    }

    private TranslatorEnvironment environmentForFrame(RubyContext context, MaterializedFrame frame) {
        if (frame == null) {
            return null;
        } else {
            final MaterializedFrame parent = frame.getArguments(RubyArguments.class).getDeclarationFrame();
            return new TranslatorEnvironment(context, environmentForFrame(context, parent), frame.getFrameDescriptor(), this, allocateReturnID(), true, true, new UniqueMethodIdentifier());
        }
    }

}
