/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.api.instrumentation.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.BlockNode;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.DefineNode;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.ExpressionNode;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * <p>
 * Minimal test language for instrumentation that enables to define a hierarchy of nodes with one or
 * multiple {@link SourceSection#getTags() source section tags}. If the DEFINE tag is used then the
 * first argument is an identifier and all following arguments are contents of a function. If CALL
 * is used then the first argument is used as identifier for a previously defined target. For the
 * tag LOOP the first argument is used for the number of iterations all body nodes should get
 * executed.
 * </p>
 *
 * <p>
 * Can eval expressions with the following syntax:
 * </p>
 * <code>
 * Statement ::= ident {":" ident} ["(" Statement {"," Statement} ")"
 * </code>
 *
 * <p>
 * Example for calling to a defined function 'foo' that loops 100 times over a statement with two
 * sub expressions:
 * </p>
 * <code>
 * ROOT(
 *     DEFINE(foo,
 *         LOOP(100, STATEMENT(EXPRESSION,EXPRESSION))
 *     ),
 *     STATEMENT:CALL(foo)
 * )
 * </code>
 */
@Registration(mimeType = InstrumentationTestLanguage.MIME_TYPE, name = "InstrumentTestLang", version = "2.0")
@ProvidedTags({ExpressionNode.class, DefineNode.class, LoopNode.class,
                StandardTags.StatementTag.class, StandardTags.CallTag.class, StandardTags.RootTag.class, BlockNode.class, StandardTags.RootTag.class})
public class InstrumentationTestLanguage extends TruffleLanguage<Map<String, CallTarget>> {

    public static final String MIME_TYPE = "application/x-truffle-instrumentation-test-language";
    public static final String FILENAME_EXTENSION = ".titl";
    public static final InstrumentationTestLanguage INSTANCE = new InstrumentationTestLanguage();

    public static final Class<?> EXPRESSION = ExpressionNode.class;
    public static final Class<?> DEFINE = DefineNode.class;
    public static final Class<?> LOOP = LoopNode.class;
    public static final Class<?> STATEMENT = StandardTags.StatementTag.class;
    public static final Class<?> CALL = StandardTags.CallTag.class;
    public static final Class<?> ROOT = StandardTags.RootTag.class;
    public static final Class<?> BLOCK = BlockNode.class;

    public static final Class<?>[] TAGS = new Class<?>[]{EXPRESSION, DEFINE, LOOP, STATEMENT, CALL, BLOCK, ROOT};
    public static final String[] TAG_NAMES = new String[]{"EXPRESSION", "DEFINE", "LOOP", "STATEMENT", "CALL", "BLOCK", "ROOT", "CONSTANT", "VARIABLE"};

    // used to test that no getSourceSection calls happen in certain situations
    private static int rootSourceSectionQueryCount;

    @Override
    protected Map<String, CallTarget> createContext(TruffleLanguage.Env env) {
        return new HashMap<>();
    }

    public Node createFindContextNode0() {
        return super.createFindContextNode();
    }

    public Map<String, CallTarget> findContext0(Node contextNode) {
        return findContext(contextNode);
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        Source code = request.getSource();
        SourceSection outer = code.createSection(0, code.getLength());
        BaseNode node;
        try {
            node = parse(code);
        } catch (LanguageError e) {
            throw new IOException(e);
        }
        return Truffle.getRuntime().createCallTarget(new InstrumentationTestRootNode("", outer, node));
    }

    public static BaseNode parse(Source code) {
        return new Parser(code).parse();
    }

    private static final class Parser {

        private static final char EOF = (char) -1;

        private final Source source;
        private final String code;
        private int current;

        Parser(Source source) {
            this.source = source;
            this.code = source.getCode();
        }

        public BaseNode parse() {
            BaseNode statement = statement();
            if (follows() != EOF) {
                error("eof expected");
            }
            return statement;
        }

        private BaseNode statement() {
            skipWhiteSpace();

            int startIndex = current;

            if (current() == EOF) {
                return null;
            }

            skipWhiteSpace();
            String tag = ident().trim().intern();
            if (!isValidTag(tag)) {
                throw new LanguageError(String.format("Illegal tag \"%s\".", tag));
            }

            int numberOfIdents = 0;
            if (tag.equals("DEFINE") || tag.equals("CALL") || tag.equals("LOOP") || tag.equals("CONSTANT")) {
                numberOfIdents = 1;
            } else if (tag.equals("VARIABLE")) {
                numberOfIdents = 2;
            }
            String[] idents = new String[numberOfIdents];
            List<BaseNode> children = new ArrayList<>();

            if (follows() == '(') {

                skipWhiteSpace();

                if (current() == '(') {
                    next();
                    skipWhiteSpace();
                    int argIndex = 0;
                    while (current() != ')') {
                        if (argIndex < numberOfIdents) {
                            skipWhiteSpace();
                            idents[argIndex] = ident();
                        } else {
                            children.add(statement());
                        }
                        skipWhiteSpace();
                        if (current() != ',') {
                            break;
                        }
                        next();
                        argIndex++;
                    }
                    if (current() != ')') {
                        error("missing closing bracket");
                    }
                    next();
                    skipWhiteSpace();
                }

            }
            for (String ident : idents) {
                if (ident == null) {
                    throw new LanguageError(numberOfIdents + " non-child parameters required for " + tag);
                }
            }
            SourceSection sourceSection = source.createSection(startIndex, current - startIndex);
            BaseNode[] childArray = children.toArray(new BaseNode[children.size()]);
            BaseNode node = createNode(tag, idents, sourceSection, childArray);
            node.setSourceSection(sourceSection);
            return node;
        }

        private static boolean isValidTag(String tag) {
            for (int i = 0; i < TAG_NAMES.length; i++) {
                String allowedTag = TAG_NAMES[i];
                if (tag == allowedTag) {
                    return true;
                }
            }
            return false;
        }

        private static BaseNode createNode(String tag, String[] idents, SourceSection sourceSection, BaseNode[] childArray) throws AssertionError {
            switch (tag) {
                case "DEFINE":
                    return new DefineNode(idents[0], sourceSection, childArray);
                case "CALL":
                    return new CallNode(idents[0], childArray);
                case "LOOP":
                    return new LoopNode(parseIdent(idents[0]), childArray);
                case "BLOCK":
                    return new BlockNode(childArray);
                case "EXPRESSION":
                    return new ExpressionNode(childArray);
                case "ROOT":
                    return new InstrumentableRootNode(childArray);
                case "STATEMENT":
                    return new StatementNode(childArray);
                case "CONSTANT":
                    return new ConstantNode(idents[0], childArray);
                case "VARIABLE":
                    return new VariableNode(idents[0], idents[1], childArray);
                default:
                    throw new AssertionError();
            }
        }

        private void error(String message) {
            throw new LanguageError(String.format("error at %s. char %s: %s", current, current(), message));
        }

        private String ident() {
            StringBuilder builder = new StringBuilder();
            char c;
            while ((c = current()) != EOF && Character.isJavaIdentifierPart(c)) {
                builder.append(c);
                next();
            }
            if (builder.length() == 0) {
                error("expected ident");
            }
            return builder.toString();
        }

        private void skipWhiteSpace() {
            while (Character.isWhitespace(current())) {
                next();
            }
        }

        private char follows() {
            for (int i = current; i < code.length(); i++) {
                if (!Character.isWhitespace(code.charAt(i))) {
                    return code.charAt(i);
                }
            }
            return EOF;
        }

        private void next() {
            current++;
        }

        private char current() {
            if (current >= code.length()) {
                return EOF;
            }
            return code.charAt(current);
        }

    }

    private static class InstrumentationTestRootNode extends RootNode {

        private final String name;
        @Children private final BaseNode[] expressions;

        protected InstrumentationTestRootNode(String name, SourceSection sourceSection, BaseNode... expressions) {
            super(InstrumentationTestLanguage.class, sourceSection, null);
            this.name = name;
            this.expressions = expressions;
        }

        @Override
        public SourceSection getSourceSection() {
            rootSourceSectionQueryCount++;
            return super.getSourceSection();
        }

        @Override
        protected boolean isInstrumentable() {
            return true;
        }

        @Override
        @ExplodeLoop
        public Object execute(VirtualFrame frame) {
            Object returnValue = null;
            for (int i = 0; i < expressions.length; i++) {
                BaseNode baseNode = expressions[i];
                if (baseNode != null) {
                    returnValue = baseNode.execute(frame);
                }
            }
            return returnValue;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "Root[" + name + "]";
        }
    }

    static final class ExpressionNode extends InstrumentedNode {

        ExpressionNode(BaseNode[] children) {
            super(children);
        }

    }

    @Instrumentable(factory = InstrumentedNodeWrapper.class)
    public abstract static class InstrumentedNode extends BaseNode {

        @Children private final BaseNode[] children;

        public InstrumentedNode(BaseNode[] children) {
            this.children = children;
        }

        public InstrumentedNode(@SuppressWarnings("unused") InstrumentedNode delegate) {
            this.children = null;
        }

        @Override
        @ExplodeLoop
        public Object execute(VirtualFrame frame) {
            Object returnValue = null;
            for (BaseNode child : children) {
                returnValue = child.execute(frame);
            }
            return returnValue;
        }

        @Override
        protected final boolean isTaggedWith(Class<?> tag) {
            if (tag == StandardTags.RootTag.class) {
                return this instanceof InstrumentableRootNode;
            } else if (tag == StandardTags.CallTag.class) {
                return this instanceof CallNode;
            } else if (tag == StandardTags.StatementTag.class) {
                return this instanceof StatementNode;
            }
            return getClass() == tag;
        }

    }

    static final class BlockNode extends InstrumentedNode {

        BlockNode(BaseNode[] children) {
            super(children);
        }

    }

    private static final class InstrumentableRootNode extends InstrumentedNode {

        InstrumentableRootNode(BaseNode[] children) {
            super(children);
        }

    }

    private static final class StatementNode extends InstrumentedNode {

        StatementNode(BaseNode[] children) {
            super(children);
        }
    }

    static class DefineNode extends BaseNode {

        private final String identifier;
        private final CallTarget target;

        @Child private Node contextNode;

        DefineNode(String identifier, SourceSection source, BaseNode[] children) {
            this.identifier = identifier;
            this.target = Truffle.getRuntime().createCallTarget(new InstrumentationTestRootNode(identifier, source, children));
            this.contextNode = InstrumentationTestLanguage.INSTANCE.createFindContextNode0();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            defineFunction();
            return null;
        }

        @TruffleBoundary
        private void defineFunction() {
            Map<String, CallTarget> context = InstrumentationTestLanguage.INSTANCE.findContext(contextNode);
            if (context.containsKey(identifier)) {
                if (context.get(identifier) != target) {
                    throw new IllegalArgumentException("Identifier redefinition not supported.");
                }
            }
            context.put(this.identifier, target);
        }

    }

    private static class CallNode extends InstrumentedNode {

        @Child private DirectCallNode callNode;
        @Child private Node contextNode;

        private final String identifier;

        CallNode(String identifier, BaseNode[] children) {
            super(children);
            this.contextNode = InstrumentationTestLanguage.INSTANCE.createFindContextNode0();
            this.identifier = identifier;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                Map<String, CallTarget> context = InstrumentationTestLanguage.INSTANCE.findContext(contextNode);
                CallTarget target = context.get(identifier);
                callNode = insert(Truffle.getRuntime().createDirectCallNode(target));
            }
            return callNode.call(frame, new Object[0]);
        }
    }

    private static class ConstantNode extends InstrumentedNode {

        private final Object constant;

        ConstantNode(String identifier, BaseNode[] children) {
            super(children);
            this.constant = parseIdent(identifier);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return constant;
        }

    }

    private static Object parseIdent(String identifier) {
        if (identifier.equals("infinity")) {
            return Double.POSITIVE_INFINITY;
        }
        if (identifier.equals("true")) {
            return true;
        } else if (identifier.equals("false")) {
            return false;
        }
        return Integer.parseInt(identifier);
    }

    private static class VariableNode extends InstrumentedNode {

        private final String name;
        private final Object value;

        @CompilationFinal private FrameSlot slot;

        VariableNode(String name, String value, BaseNode[] children) {
            super(children);
            this.name = name;
            this.value = parseIdent(value);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (slot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                slot = frame.getFrameDescriptor().findOrAddFrameSlot(name);
            }
            frame.setObject(slot, value);
            super.execute(frame);
            return value;
        }

    }

    static class LoopNode extends InstrumentedNode {

        private final int loopCount;
        private final boolean infinite;

        LoopNode(Object loopCount, BaseNode[] children) {
            super(children);
            boolean inf = false;
            if (loopCount instanceof Double) {
                if (((Double) loopCount).isInfinite()) {
                    inf = true;
                }
                this.loopCount = ((Double) loopCount).intValue();
            } else if (loopCount instanceof Integer) {
                this.loopCount = (int) loopCount;
            } else {
                throw new LanguageError("Invalid loop count " + loopCount);
            }
            this.infinite = inf;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object returnValue = null;
            for (int i = 0; infinite || i < loopCount; i++) {
                returnValue = super.execute(frame);
            }
            return returnValue;
        }
    }

    public abstract static class BaseNode extends Node {

        private SourceSection sourceSection;

        public void setSourceSection(SourceSection sourceSection) {
            this.sourceSection = sourceSection;
        }

        @Override
        public SourceSection getSourceSection() {
            return sourceSection;
        }

        public abstract Object execute(VirtualFrame frame);

    }

    @SuppressWarnings("serial")
    private static class LanguageError extends RuntimeException {

        LanguageError(String format) {
            super(format);
        }
    }

    @Override
    protected Object findExportedSymbol(Map<String, CallTarget> context, String globalName, boolean onlyExplicit) {
        return context.get(globalName);
    }

    @Override
    protected Object getLanguageGlobal(Map<String, CallTarget> context) {
        return context;
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return false;
    }

    public static int getRootSourceSectionQueryCount() {
        return rootSourceSectionQueryCount;
    }

}
