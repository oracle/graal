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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.Tag.Identifier;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.BlockTag;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.ConstantTag;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.DefineTag;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.FunctionsObject;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.LoopTag;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
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
 * <p>
 * Other statements are:
 * <ul>
 * <li><code>ARGUMENT(name)</code> - copies a frame argument to the named slot</li>
 * <li><code>VARIABLE(name, value)</code> - defines a variable</li>
 * <li><code>CONSTANT(value)</code> - defines a constant value</li>
 * <li><code>PRINT(OUT, text)</code> or <code>PRINT(ERR, text)</code> - prints a text to standard
 * output resp. error output.</li>
 * <li><code>SPAWN(&lt;function&gt;)</code> - calls the function in a new thread</li>
 * <li><code>JOIN()</code> - waits for all spawned threads</li>
 * </ul>
 * </p>
 */
@Registration(id = InstrumentationTestLanguage.ID, name = "", version = "2.0")
@ProvidedTags({StandardTags.ExpressionTag.class, DefineTag.class, LoopTag.class,
                StandardTags.StatementTag.class, StandardTags.CallTag.class, StandardTags.RootTag.class,
                StandardTags.TryBlockTag.class, BlockTag.class, ConstantTag.class})
public class InstrumentationTestLanguage extends TruffleLanguage<InstrumentContext>
                implements SpecialService {

    public static final String ID = "instrumentation-test-language";
    public static final String FILENAME_EXTENSION = ".titl";

    @Identifier("DEFINE")
    static class DefineTag extends Tag {

    }

    @Identifier("LOOP")
    static class LoopTag extends Tag {

    }

    @Identifier("BLOCK")
    static class BlockTag extends Tag {

    }

    @Identifier("CONSTANT")
    static class ConstantTag extends Tag {

    }

    public static final Class<? extends Tag> EXPRESSION = StandardTags.ExpressionTag.class;
    public static final Class<? extends Tag> DEFINE = DefineTag.class;
    public static final Class<? extends Tag> LOOP = LoopTag.class;
    public static final Class<? extends Tag> STATEMENT = StandardTags.StatementTag.class;
    public static final Class<? extends Tag> CALL = StandardTags.CallTag.class;
    public static final Class<? extends Tag> ROOT = StandardTags.RootTag.class;
    public static final Class<? extends Tag> BLOCK = BlockTag.class;
    public static final Class<? extends Tag> CONSTANT = ConstantTag.class;
    public static final Class<? extends Tag> TRY_CATCH = StandardTags.TryBlockTag.class;

    public static final Class<?>[] TAGS = new Class<?>[]{EXPRESSION, DEFINE, LOOP, STATEMENT, CALL, BLOCK, ROOT, CONSTANT, TRY_CATCH};
    public static final String[] TAG_NAMES = new String[]{"EXPRESSION", "DEFINE", "CONTEXT", "LOOP", "STATEMENT", "CALL", "RECURSIVE_CALL", "BLOCK", "ROOT", "CONSTANT", "VARIABLE", "ARGUMENT",
                    "PRINT", "ALLOCATION", "SLEEP", "SPAWN", "JOIN", "INVALIDATE", "INTERNAL", "INNER_FRAME", "MATERIALIZE_CHILD_EXPRESSION", "BLOCK_NO_SOURCE_SECTION",
                    "TRY", "CATCH", "THROW", "UNEXPECTED_RESULT", "MULTIPLE"};

    // used to test that no getSourceSection calls happen in certain situations
    private static int rootSourceSectionQueryCount;
    private final FunctionMetaObject functionMetaObject = new FunctionMetaObject();

    public InstrumentationTestLanguage() {
    }

    @Override
    public String fileExtension() {
        return FILENAME_EXTENSION;
    }

    /**
     * Set configuration data to the language. Possible values are:
     * <ul>
     * <li>{@link ReturnLanguageEnv#KEY} with a {@link ReturnLanguageEnv} value,</li>
     * <li>"initSource" with a {@link org.graalvm.polyglot.Source} value,</li>
     * <li>"runInitAfterExec" with a {@link Boolean} value.</li>
     * </ul>
     */
    public static Map<String, Object> envConfig = new HashMap<>();

    @Override
    protected InstrumentContext createContext(TruffleLanguage.Env env) {
        Source initSource = null;
        Boolean runInitAfterExec = null;
        if (envConfig != null) {
            org.graalvm.polyglot.Source initPolyglotSource = (org.graalvm.polyglot.Source) envConfig.get("initSource");
            if (initPolyglotSource != null) {
                initSource = AbstractInstrumentationTest.sourceToImpl(initPolyglotSource);
            }
            runInitAfterExec = (Boolean) envConfig.get("runInitAfterExec");
        }
        return new InstrumentContext(env, initSource, runInitAfterExec);
    }

    @Override
    protected void initializeContext(InstrumentContext context) throws Exception {
        super.initializeContext(context);
        Source code = context.initSource;
        if (code != null) {
            SourceSection outer = code.createSection(0, code.getLength());
            BaseNode node = parse(code);
            RootCallTarget rct = Truffle.getRuntime().createCallTarget(new InstrumentationTestRootNode(this, "", outer, node));
            rct.call();
            if (context.runInitAfterExec) {
                context.afterTarget = rct;
            }
        }
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
        return true;
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
        RootCallTarget afterTarget = getContextReference().get().afterTarget;
        return Truffle.getRuntime().createCallTarget(new InstrumentationTestRootNode(this, "", outer, afterTarget, node));
    }

    public static RootNode parse(String code) {
        InstrumentationTestLanguage testLanguage = getCurrentLanguage(InstrumentationTestLanguage.class);
        Source source = Source.newBuilder(ID, code, "test").build();
        SourceSection outer = source.createSection(0, source.getLength());
        BaseNode base = testLanguage.parse(source);
        return new InstrumentationTestRootNode(testLanguage, "", outer, base);
    }

    @Override
    protected ExecutableNode parse(InlineParsingRequest request) throws Exception {
        Source code = request.getSource();
        Node location = request.getLocation();
        if (location == null) {
            throw new IllegalArgumentException("Location must not be null.");
        }
        BaseNode node;
        try {
            node = parse(code);
        } catch (LanguageError e) {
            throw new IOException(e);
        }
        return new InlineExecutableNode(this, node);
    }

    public static InstrumentationTestLanguage current() {
        return InstrumentationTestLanguage.getCurrentLanguage(InstrumentationTestLanguage.class);
    }

    public static Env currentEnv() {
        return getCurrentContext(InstrumentationTestLanguage.class).env;
    }

    protected final ExecutableNode parseOriginal(InlineParsingRequest request) throws Exception {
        return super.parse(request);
    }

    private static class InlineExecutableNode extends ExecutableNode {

        @Child private BaseNode node;

        InlineExecutableNode(TruffleLanguage<?> language, BaseNode node) {
            super(language);
            this.node = node;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            assert getParent() != null;
            return node.execute(frame);
        }

    }

    public BaseNode parse(Source code) {
        return new Parser(this, code).parse();
    }

    private static final class Parser {

        private static final char EOF = (char) -1;

        private final InstrumentationTestLanguage lang;
        private final Source source;
        private final String code;
        private int current;

        Parser(InstrumentationTestLanguage lang, Source source) {
            this.lang = lang;
            this.source = source;
            this.code = source.getCharacters().toString();
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

            int argumentIndex = 0;
            int numberOfIdents = 0;
            if (tag.equals("DEFINE") || tag.equals("ARGUMENT") || tag.equals("CALL") || tag.equals("LOOP") || tag.equals("CONSTANT") || tag.equals("UNEXPECTED_RESULT") || tag.equals("SLEEP") ||
                            tag.equals("SPAWN") | tag.equals("CATCH")) {
                numberOfIdents = 1;
            } else if (tag.equals("VARIABLE") || tag.equals("RECURSIVE_CALL") || tag.equals("PRINT") || tag.equals("THROW")) {
                numberOfIdents = 2;
            }
            List<String> multipleTags = null;
            if (tag.equals("MULTIPLE")) {
                multipleTags = multipleTags();
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
                }

            }
            for (String ident : idents) {
                if (ident == null) {
                    throw new LanguageError(numberOfIdents + " non-child parameters required for " + tag);
                }
            }
            SourceSection sourceSection = source.createSection(startIndex, current - startIndex);
            BaseNode[] childArray = children.toArray(new BaseNode[children.size()]);
            BaseNode node = createNode(tag, idents, sourceSection, childArray, multipleTags);
            if (tag.equals("ARGUMENT")) {
                ((ArgumentNode) node).setIndex(argumentIndex++);
            }
            node.setSourceSection(sourceSection);
            return node;
        }

        private List<String> multipleTags() {
            List<String> multipleTags = new ArrayList<>();
            if (follows() == '[') {
                skipWhiteSpace();

                if (current() == '[') {
                    next();
                    skipWhiteSpace();
                    while (current() != ']') {
                        skipWhiteSpace();
                        multipleTags.add(ident());
                        skipWhiteSpace();
                        if (current() != ',') {
                            break;
                        }
                        next();
                    }
                    if (current() != ']') {
                        error("missing closing bracket");
                    }
                    next();
                }

            }
            return multipleTags;
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

        private BaseNode createNode(String tag, String[] idents, SourceSection sourceSection, BaseNode[] childArray, List<String> multipleTags) throws AssertionError {
            switch (tag) {
                case "DEFINE":
                    return new DefineNode(lang, idents[0], sourceSection, childArray);
                case "CONTEXT":
                    return new ContextNode(childArray);
                case "ARGUMENT":
                    return new ArgumentNode(idents[0], childArray);
                case "CALL":
                    return new CallNode(idents[0], childArray);
                case "RECURSIVE_CALL":
                    return new RecursiveCallNode(idents[0], (Integer) parseIdent(idents[1]), childArray);
                case "LOOP":
                    return new LoopNode(parseIdent(idents[0]), childArray);
                case "BLOCK":
                    return new BlockNode(childArray);
                case "BLOCK_NO_SOURCE_SECTION":
                    return new BlockNoSourceSectionNode(childArray);
                case "EXPRESSION":
                    return new ExpressionNode(childArray);
                case "ROOT":
                    return new FunctionRootNode(childArray);
                case "STATEMENT":
                    return new StatementNode(childArray);
                case "INTERNAL":
                    return new InternalNode(childArray);
                case "CONSTANT":
                    return new ConstantNode(idents[0], childArray);
                case "VARIABLE":
                    return new VariableNode(idents[0], idents[1], childArray, lang.getContextReference());
                case "PRINT":
                    return new PrintNode(idents[0], idents[1], childArray);
                case "ALLOCATION":
                    return new AllocationNode(new BaseNode[0]);
                case "SLEEP":
                    return new SleepNode(parseIdent(idents[0]), new BaseNode[0]);
                case "SPAWN":
                    return new SpawnNode(idents[0], childArray);
                case "JOIN":
                    return new JoinNode(childArray);
                case "INVALIDATE":
                    return new InvalidateNode(childArray);
                case "INNER_FRAME":
                    return new InnerFrameNode(childArray);
                case "MATERIALIZE_CHILD_EXPRESSION":
                    return new MaterializeChildExpressionNode(childArray);
                case "TRY":
                    return new TryCatchNode(childArray, lang.getContextReference());
                case "CATCH":
                    return new CatchNode(idents[0], childArray);
                case "THROW":
                    return new ThrowNode(idents[0], idents[1]);
                case "UNEXPECTED_RESULT":
                    return new UnexpectedResultNode(idents[0]);
                case "MULTIPLE":
                    return new MultipleNode(childArray, multipleTags);
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
        private final SourceSection sourceSection;
        private final RootCallTarget afterTarget;
        @Child private InstrumentedNode functionRoot;

        protected InstrumentationTestRootNode(InstrumentationTestLanguage lang, String name, SourceSection sourceSection, BaseNode... expressions) {
            this(lang, name, sourceSection, null, expressions);
        }

        protected InstrumentationTestRootNode(InstrumentationTestLanguage lang, String name, SourceSection sourceSection, RootCallTarget afterTarget, BaseNode... expressions) {
            super(lang);
            this.name = name;
            this.sourceSection = sourceSection;
            this.afterTarget = afterTarget;
            if (expressions.length == 1 && expressions[0] instanceof FunctionRootNode) {
                // It contains just a ROOT
                this.functionRoot = (FunctionRootNode) expressions[0];
            } else {
                this.functionRoot = new FunctionRootNode(expressions);
            }
            this.functionRoot.setSourceSection(sourceSection);
        }

        @Override
        public SourceSection getSourceSection() {
            rootSourceSectionQueryCount++;
            return sourceSection;
        }

        @Override
        @ExplodeLoop
        public Object execute(VirtualFrame frame) {
            Object returnValue = Null.INSTANCE;
            returnValue = functionRoot.execute(frame);
            if (afterTarget != null) {
                afterTarget.call();
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

    static class ExpressionNode extends InstrumentedNode {

        ExpressionNode(BaseNode[] children) {
            super(children);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            StringBuilder b = new StringBuilder();
            b.append("(");
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    BaseNode child = children[i];
                    Object value = Null.INSTANCE;
                    if (child != null) {
                        value = child.execute(frame);
                    }
                    if (i > 0) {
                        b.append("+");
                    }
                    b.append(value);
                }
            }
            b.append(")");
            return b.toString();
        }

    }

    static class BlockNoSourceSectionNode extends BlockNode {

        BlockNoSourceSectionNode(BaseNode[] children) {
            super(children);
        }

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public SourceSection getSourceSection() {
            return null;
        }

    }

    @GenerateWrapper
    public abstract static class InstrumentedNode extends BaseNode implements InstrumentableNode, TruffleObject {

        @Children final BaseNode[] children;

        public InstrumentedNode() {
            this.children = null;
        }

        public InstrumentedNode(BaseNode[] children) {
            this.children = children;
        }

        public boolean isInstrumentable() {
            return getSourceSection() != null;
        }

        @Override
        @ExplodeLoop
        public Object execute(VirtualFrame frame) {
            Object returnValue = Null.INSTANCE;
            for (BaseNode child : children) {
                if (child != null) {
                    Object value = child.execute(frame);
                    if (value != null && value != Null.INSTANCE) {
                        returnValue = value;
                    }
                }
            }
            return returnValue;
        }

        public ForeignAccess getForeignAccess() {
            return InstrumentedNodeAttributesForeign.ACCESS;
        }

        public InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
            return new InstrumentedNodeWrapper(this, probe);
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            if (tag == StandardTags.RootTag.class) {
                return this instanceof FunctionRootNode;
            } else if (tag == StandardTags.CallTag.class) {
                return this instanceof CallNode || this instanceof RecursiveCallNode;
            } else if (tag == StandardTags.StatementTag.class) {
                return this instanceof StatementNode;
            } else if (tag == StandardTags.ExpressionTag.class) {
                return this instanceof ExpressionNode;
            } else if (tag == StandardTags.TryBlockTag.class) {
                return this instanceof TryNode;
            } else if (tag == LOOP) {
                return this instanceof LoopNode;
            } else if (tag == BLOCK) {
                return this instanceof BlockNode;
            } else if (tag == DEFINE) {
                return this instanceof DefineNode;
            } else if (tag == CONSTANT) {
                return this instanceof ConstantNode;
            }
            return false;
        }

        public Object getNodeObject() {
            return this;
        }

        public static boolean isInstance(TruffleObject o) {
            return o instanceof InstrumentedNode;
        }

    }

    static class BlockNode extends InstrumentedNode {

        BlockNode(BaseNode[] children) {
            super(children);
        }

    }

    static class TryCatchNode extends InstrumentedNode {

        @Child TryNode tryNode;
        @Children private final CatchNode[] catchNodes;

        TryCatchNode(BaseNode[] children, ContextReference<InstrumentContext> contextRef) {
            super();
            BaseNode[] tryNodes = selectTryBlock(children);
            int tn = tryNodes.length;
            int cn = children.length - tn;
            catchNodes = new CatchNode[cn];
            System.arraycopy(children, tn, catchNodes, 0, cn);
            tryNode = new TryNode(tryNodes, catchNodes, contextRef);
        }

        @Override
        public void setSourceSection(SourceSection sourceSection) {
            super.setSourceSection(sourceSection);
            int start = sourceSection.getCharIndex();
            int end = catchNodes.length > 0 ? catchNodes[0].getSourceSection().getCharIndex() : sourceSection.getCharEndIndex();
            SourceSection trySection = sourceSection.getSource().createSection(start, end - start);
            CharSequence characters = trySection.getCharacters();
            int lastChar = trySection.getCharLength() - 1;
            char c;
            while (Character.isWhitespace(c = characters.charAt(lastChar)) || c == ',') {
                lastChar--;
            }
            trySection = sourceSection.getSource().createSection(start, lastChar + 1);
            tryNode.setSourceSection(trySection);
        }

        static BaseNode[] selectTryBlock(BaseNode[] children) {
            for (int i = 0; i < children.length; i++) {
                if (children[i] instanceof CatchNode) {
                    BaseNode[] tryBlock = new BaseNode[i];
                    System.arraycopy(children, 0, tryBlock, 0, i);
                    return tryBlock;
                }
            }
            return children;
        }

        @Override
        @ExplodeLoop
        public Object execute(VirtualFrame frame) {
            try {
                return tryNode.execute(frame);
            } catch (Exception ex) {
                if (ex instanceof TruffleException) {
                    Object exceptionObject = ((TruffleException) ex).getExceptionObject();
                    if (exceptionObject != null) {
                        String type = exceptionObject.toString();
                        for (CatchNode cn : catchNodes) {
                            if (type.startsWith(cn.getExceptionName())) {
                                return cn.execute(frame);
                            }
                        }
                    }
                }
                throw ex;
            }
        }

    }

    static class TryNode extends BlockNode {

        private final TruffleObject catchesInfoNode;

        TryNode(BaseNode[] children, CatchNode[] catches, ContextReference<InstrumentContext> contextRef) {
            super(children);
            this.catchesInfoNode = new CatchesInfoObject(catches, contextRef);
        }

        @Override
        public Object getNodeObject() {
            return catchesInfoNode;
        }

        static class CatchesInfoObject implements TruffleObject {

            private final CatchNode[] catches;
            private final ContextReference<InstrumentContext> contextRef;

            CatchesInfoObject(CatchNode[] catches, ContextReference<InstrumentContext> contextRef) {
                this.catches = catches;
                this.contextRef = contextRef;
            }

            @Override
            public ForeignAccess getForeignAccess() {
                return CatchesInfoObjectMessageResolutionForeign.ACCESS;
            }

            public static boolean isInstance(TruffleObject obj) {
                return obj instanceof CatchesInfoObject;
            }

            @MessageResolution(receiverType = CatchesInfoObject.class)
            static class CatchesInfoObjectMessageResolution {

                @Resolve(message = "HAS_KEYS")
                abstract static class HasKeysNode extends Node {

                    @SuppressWarnings("unused")
                    public Object access(CatchesInfoObject info) {
                        return true;
                    }
                }

                @Resolve(message = "KEYS")
                abstract static class KeysNode extends Node {

                    @TruffleBoundary
                    public Object access(CatchesInfoObject info) {
                        return info.contextRef.get().env.asGuestValue(new String[]{"catches"});
                    }
                }

                @Resolve(message = "KEY_INFO")
                abstract static class KeyInfoNode extends Node {

                    @TruffleBoundary
                    public Object access(CatchesInfoObject info, String name) {
                        assert info != null;
                        if ("catches".equals(name)) {
                            return KeyInfo.INVOCABLE;
                        } else {
                            return 0;
                        }
                    }
                }

                @Resolve(message = "INVOKE")
                abstract static class InvokeNode extends Node {

                    @TruffleBoundary
                    public Object access(CatchesInfoObject info, String name, Object[] arguments) {
                        if ("catches".equals(name)) {
                            String type = arguments[0].toString();
                            for (CatchNode c : info.catches) {
                                if (type.startsWith(c.getExceptionName())) {
                                    return true;
                                }
                            }
                            return false;
                        } else {
                            throw UnknownIdentifierException.raise(name);
                        }
                    }
                }
            }
        }
    }

    static class CatchNode extends BlockNode {

        private final String exceptionName;

        CatchNode(String exceptionName, BaseNode[] children) {
            super(children);
            this.exceptionName = exceptionName;
        }

        String getExceptionName() {
            return exceptionName;
        }
    }

    static class MultipleNode extends BlockNode {

        private final Set<Class<? extends Tag>> resolvedTags;

        MultipleNode(BaseNode[] children, List<String> tags) {
            super(children);
            this.resolvedTags = new HashSet<>();
            for (String tag : tags) {
                // add support for more tags as needed
                switch (tag) {
                    case "EXPRESSION":
                        resolvedTags.add(ExpressionTag.class);
                        break;
                    case "STATEMENT":
                        resolvedTags.add(StatementTag.class);
                        break;
                    case "ROOT":
                        resolvedTags.add(RootTag.class);
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid tag " + tag);
                }
            }

        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return resolvedTags.contains(tag);
        }

    }

    static class ThrowNode extends InstrumentedNode {

        private final String type;
        private final String message;

        ThrowNode(String exceptionType, String message) {
            this.type = exceptionType;
            this.message = message;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            throw createException();
        }

        @TruffleBoundary
        private TestLanguageException createException() {
            return new TestLanguageException(type, message, this);
        }

        private static class TestLanguageException extends RuntimeException implements TruffleException {

            private static final long serialVersionUID = 2709459650157465163L;

            private final String type;
            private final ThrowNode throwNode;

            TestLanguageException(String type, String message, ThrowNode throwNode) {
                super(message);
                this.type = type;
                this.throwNode = throwNode;
            }

            @Override
            public Node getLocation() {
                return throwNode;
            }

            public boolean isInternalError() {
                return type.equals("internal");
            }

            @Override
            public Object getExceptionObject() {
                return type + ": " + getMessage();
            }

        }
    }

    private static final class FunctionRootNode extends InstrumentedNode {

        FunctionRootNode(BaseNode[] children) {
            super(children);
        }

    }

    private static class StatementNode extends InstrumentedNode {

        StatementNode(BaseNode[] children) {
            super(children);
        }
    }

    static class DefineNode extends InstrumentedNode {

        private final String identifier;
        private final CallTarget target;

        DefineNode(InstrumentationTestLanguage lang, String identifier, SourceSection source, BaseNode[] children) {
            this.identifier = identifier;
            String code = source.getCharacters().toString();
            int index = code.indexOf('(') + 1;
            index = code.indexOf(',', index) + 1;
            SourceSection functionSection = source.getSource().createSection(source.getCharIndex() + index, source.getCharLength() - index - 1);
            this.target = Truffle.getRuntime().createCallTarget(new InstrumentationTestRootNode(lang, identifier, functionSection, children));
        }

        @Override
        public boolean isInstrumentable() {
            return false;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            defineFunction();
            return Null.INSTANCE;
        }

        @TruffleBoundary
        private void defineFunction() {
            InstrumentContext context = getRootNode().getLanguage(InstrumentationTestLanguage.class).getContextReference().get();
            if (context.callFunctions.callTargets.containsKey(identifier)) {
                if (context.callFunctions.callTargets.get(identifier) != target) {
                    throw new IllegalArgumentException("Identifier redefinition not supported.");
                }
            }
            context.callFunctions.callTargets.put(this.identifier, target);
        }

    }

    static class ContextNode extends BaseNode {

        @Children private final BaseNode[] children;

        ContextNode(BaseNode[] children) {
            this.children = children;
        }

        @Override
        @ExplodeLoop
        public Object execute(VirtualFrame frame) {
            Object returnValue = Null.INSTANCE;
            TruffleContext inner = createInnerContext();
            Object prev = inner.enter();
            try {
                for (BaseNode child : children) {
                    if (child != null) {
                        returnValue = child.execute(frame);
                    }
                }
            } finally {
                inner.leave(prev);
                inner.close();
            }
            return returnValue;
        }

        @TruffleBoundary
        private TruffleContext createInnerContext() {
            InstrumentContext context = getRootNode().getLanguage(InstrumentationTestLanguage.class).getContextReference().get();
            return context.env.newContextBuilder().build();
        }
    }

    private static class CallNode extends InstrumentedNode {

        @Child private DirectCallNode callNode;
        private final String identifier;

        CallNode(String identifier, BaseNode[] children) {
            super(children);
            this.identifier = identifier;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                InstrumentContext context = getRootNode().getLanguage(InstrumentationTestLanguage.class).getContextReference().get();
                CallTarget target = context.callFunctions.callTargets.get(identifier);
                callNode = insert(Truffle.getRuntime().createDirectCallNode(target));
            }
            return callNode.call(new Object[0]);
        }
    }

    private static class SpawnNode extends InstrumentedNode {

        @Child private DirectCallNode callNode;
        private final String identifier;

        SpawnNode(String identifier, BaseNode[] children) {
            super(children);
            this.identifier = identifier;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                InstrumentContext context = getRootNode().getLanguage(InstrumentationTestLanguage.class).getContextReference().get();
                CallTarget target = context.callFunctions.callTargets.get(identifier);
                callNode = Truffle.getRuntime().createDirectCallNode(target);
            }
            spawnCall();
            return Null.INSTANCE;
        }

        @TruffleBoundary
        private void spawnCall() {
            InstrumentContext context = getRootNode().getLanguage(InstrumentationTestLanguage.class).getContextReference().get();
            Thread t = context.env.createThread(new Runnable() {
                @Override
                public void run() {
                    callNode.call(new Object[0]);
                }
            });
            t.start();
            synchronized (context.spawnedThreads) {
                context.spawnedThreads.add(t);
            }
        }
    }

    private static class JoinNode extends InstrumentedNode {

        JoinNode(BaseNode[] children) {
            super(children);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            joinSpawnedThreads();
            return Null.INSTANCE;
        }

        @TruffleBoundary
        private void joinSpawnedThreads() {
            InstrumentContext context = getRootNode().getLanguage(InstrumentationTestLanguage.class).getContextReference().get();
            List<Thread> threads;
            do {
                threads = new ArrayList<>();
                synchronized (context.spawnedThreads) {
                    for (Thread t : context.spawnedThreads) {
                        if (t.isAlive()) {
                            threads.add(t);
                        }
                    }
                }
                for (Thread t : threads) {
                    try {
                        t.join();
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            } while (!threads.isEmpty());
        }
    }

    private static class RecursiveCallNode extends InstrumentedNode {

        @Child private DirectCallNode callNode;
        private final String identifier;
        private final int depth;
        private int currentDepth = 0;

        RecursiveCallNode(String identifier, int depth, BaseNode[] children) {
            super(children);
            this.identifier = identifier;
            this.depth = depth;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (currentDepth < depth) {
                currentDepth++;
                if (callNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    InstrumentContext context = getRootNode().getLanguage(InstrumentationTestLanguage.class).getContextReference().get();
                    CallTarget target = context.callFunctions.callTargets.get(identifier);
                    callNode = insert(Truffle.getRuntime().createDirectCallNode(target));
                }
                Object retval = callNode.call(new Object[0]);
                currentDepth--;
                return retval;
            } else {
                return null;
            }
        }
    }

    private static class AllocationNode extends InstrumentedNode {

        AllocationNode(BaseNode[] children) {
            super(children);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            getCurrentContext(InstrumentationTestLanguage.class).allocationReporter.onEnter(null, 0, 1);
            getCurrentContext(InstrumentationTestLanguage.class).allocationReporter.onReturnValue("Not Important", 0, 1);
            return null;
        }
    }

    private static class SleepNode extends InstrumentedNode {

        private final int timeToSleep;

        SleepNode(Object timeToSleep, BaseNode[] children) {
            super(children);
            this.timeToSleep = (Integer) timeToSleep;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            sleep();
            return null;
        }

        @TruffleBoundary
        private void sleep() {
            try {
                Thread.sleep(timeToSleep);
            } catch (InterruptedException e) {
            }
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

    private static class InvalidateNode extends InstrumentedNode {

        InvalidateNode(BaseNode[] children) {
            super(children);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (CompilerDirectives.inCompiledCode()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            return 1;
        }
    }

    @GenerateWrapper
    public static class TypeSpecializedNode extends InstrumentedNode implements InstrumentableNode {

        private String value;

        public TypeSpecializedNode() {
            super(null);
        }

        public TypeSpecializedNode(String value) {
            super(null);
            this.value = value;
        }

        @SuppressWarnings("unused")
        public int executeInt(VirtualFrame frame) throws UnexpectedResultException {
            throw new UnexpectedResultException(value);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            throw new AssertionError();
        }

        @Override
        public InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
            return new TypeSpecializedNodeWrapper(this, probe);
        }

        @Override
        public final boolean hasTag(Class<? extends Tag> tag) {
            if (tag.equals(StandardTags.StatementTag.class)) {
                return true;
            }
            return super.hasTag(tag);
        }

        @Override
        public SourceSection getSourceSection() {
            return Source.newBuilder(ID, "UnexpectedResultException(" + value + ")", "unexpected").build().createSection(1);
        }
    }

    private static class UnexpectedResultNode extends InstrumentedNode {

        UnexpectedResultNode(String value) {
            super(new BaseNode[]{new TypeSpecializedNode(value)});
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                return ((TypeSpecializedNode) children[0]).executeInt(frame);
            } catch (UnexpectedResultException e) {
                return e.getResult();
            }
        }

        @Override
        public final boolean hasTag(Class<? extends Tag> tag) {
            if (tag.equals(StandardTags.StatementTag.class)) {
                return true;
            }
            return super.hasTag(tag);
        }
    }

    static class MaterializeChildExpressionNode extends StatementNode {

        MaterializeChildExpressionNode(BaseNode[] children) {
            super(children);
        }

        public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
            if (materializedTags.contains(StandardTags.ExpressionTag.class)) {
                MaterializedChildExpressionNode materializedNode = new MaterializedChildExpressionNode(getSourceSection(), children);
                materializedNode.setSourceSection(getSourceSection());
                return materializedNode;
            }
            return this;
        }
    }

    static class MaterializedChildExpressionNode extends StatementNode {

        @Child private InstrumentedNode expressionNode;

        MaterializedChildExpressionNode(SourceSection sourceSection, BaseNode[] children) {
            super(children);
            this.expressionNode = new ExpressionNode(null);
            this.expressionNode.setSourceSection(sourceSection);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            expressionNode.execute(frame);
            return super.execute(frame);
        }

    }

    private static class InnerFrameNode extends InstrumentedNode {

        private final FrameDescriptor innerFrameDescriptor = new FrameDescriptor();

        InnerFrameNode(BaseNode[] children) {
            super(children);
        }

        @Override
        public SourceSection getSourceSection() {
            return null;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return super.execute(Truffle.getRuntime().createVirtualFrame(frame.getArguments(), innerFrameDescriptor));
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

    private static final class VariableNode extends InstrumentedNode {

        private final String name;
        private final Object value;
        private final ContextReference<InstrumentContext> contextRef;

        @CompilationFinal private FrameSlot slot;

        private VariableNode(String name, String identifier, BaseNode[] children, ContextReference<InstrumentContext> contextRef) {
            super(children);
            this.name = name;
            this.value = parseIdent(identifier);
            this.contextRef = contextRef;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (contextRef.get().allocationReporter.isActive()) {
                // Pretend we're allocating the value, for tests
                contextRef.get().allocationReporter.onEnter(null, 0, getValueSize());
            }
            if (slot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                slot = frame.getFrameDescriptor().findOrAddFrameSlot(name);
            }
            frame.setObject(slot, value);
            if (contextRef.get().allocationReporter.isActive()) {
                contextRef.get().allocationReporter.onReturnValue(value, 0, getValueSize());
            }
            super.execute(frame);
            return value;
        }

        private long getValueSize() {
            if (value instanceof Byte || value instanceof Boolean) {
                return 1;
            }
            if (value instanceof Character) {
                return 2;
            }
            if (value instanceof Short) {
                return 2;
            }
            if (value instanceof Integer || value instanceof Float) {
                return 4;
            }
            if (value instanceof Long || value instanceof Double) {
                return 8;
            }
            return AllocationReporter.SIZE_UNKNOWN;
        }

    }

    private static class ArgumentNode extends InstrumentedNode {

        private final String name;

        @CompilationFinal private FrameSlot slot;
        @CompilationFinal private int index;

        ArgumentNode(String name, BaseNode[] children) {
            super(children);
            this.name = name;
        }

        void setIndex(int index) {
            this.index = index;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (slot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                slot = frame.getFrameDescriptor().findOrAddFrameSlot(name);
            }
            Object[] args = frame.getArguments();
            Object value;
            if (args.length <= index) {
                value = Null.INSTANCE;
            } else {
                value = args[index];
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
            Object returnValue = Null.INSTANCE;
            for (int i = 0; infinite || i < loopCount; i++) {
                returnValue = super.execute(frame);
            }
            return returnValue;
        }
    }

    static final class InternalNode extends InstrumentedNode {

        InternalNode(BaseNode[] children) {
            super(children);
        }

        @Override
        public SourceSection getSourceSection() {
            return null;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return super.execute(frame);
        }

    }

    static class PrintNode extends InstrumentedNode {

        enum Output {
            OUT,
            ERR
        }

        private final Output where;
        private final String what;
        @CompilationFinal private PrintWriter writer;

        PrintNode(String where, String what, BaseNode[] children) {
            super(children);
            if (what == null) {
                this.where = Output.OUT;
                this.what = where;
            } else {
                this.where = Output.valueOf(where);
                this.what = what;
            }
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (writer == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                InstrumentContext context = getRootNode().getLanguage(InstrumentationTestLanguage.class).getContextReference().get();
                switch (where) {
                    case OUT:
                        writer = new PrintWriter(new OutputStreamWriter(context.out));
                        break;
                    case ERR:
                        writer = new PrintWriter(new OutputStreamWriter(context.err));
                        break;
                    default:
                        throw new AssertionError(where);
                }
            }
            writeAndFlush(writer, what);
            return Null.INSTANCE;
        }

        @TruffleBoundary
        private static void writeAndFlush(PrintWriter writer, String what) {
            writer.write(what);
            writer.flush();
        }
    }

    public abstract static class BaseNode extends Node {

        private SourceSection sourceSection;
        private int index;

        public void setSourceSection(SourceSection sourceSection) {
            this.sourceSection = sourceSection;
        }

        public int getIndex() {
            return index;
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
    protected Iterable<Scope> findTopScopes(InstrumentContext context) {
        return Arrays.asList(Scope.newBuilder("global", context.callFunctions).build());
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return object instanceof Function || object == functionMetaObject;
    }

    @Override
    protected Object findMetaObject(InstrumentContext context, Object obj) {
        if (obj instanceof Integer || obj instanceof Long) {
            return "Integer";
        }
        if (obj instanceof Boolean) {
            return "Boolean";
        }
        if (obj != null && obj.equals(Double.POSITIVE_INFINITY)) {
            return "Infinity";
        }
        if (obj instanceof Function) {
            return functionMetaObject;
        }
        return null;
    }

    @Override
    protected SourceSection findSourceLocation(InstrumentContext context, Object obj) {
        if (obj instanceof Integer || obj instanceof Long) {
            return Source.newBuilder(ID, "source integer", "integer").build().createSection(1);
        }
        if (obj instanceof Boolean) {
            return Source.newBuilder(ID, "source boolean", "boolean").build().createSection(1);
        }
        if (obj != null && obj.equals(Double.POSITIVE_INFINITY)) {
            return Source.newBuilder(ID, "source infinity", "double").build().createSection(1);
        }
        return null;
    }

    @Override
    protected String toString(InstrumentContext context, Object value) {
        if (value == functionMetaObject) {
            return "Function";
        } else {
            return Objects.toString(value);
        }
    }

    public static int getRootSourceSectionQueryCount() {
        return rootSourceSectionQueryCount;
    }

    static final class Function implements TruffleObject {

        private final CallTarget ct;

        Function(CallTarget ct) {
            this.ct = ct;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return FunctionMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof Function;
        }

        @MessageResolution(receiverType = Function.class)
        static final class FunctionMessageResolution {

            @Resolve(message = "EXECUTE")
            public abstract static class FunctionExecuteNode extends Node {

                public Object access(Function receiver, Object[] arguments) {
                    return receiver.ct.call(arguments);
                }
            }

            @Resolve(message = "IS_EXECUTABLE")
            public abstract static class FunctionIsExecutableNode extends Node {
                public Object access(Object receiver) {
                    return receiver instanceof Function;
                }
            }
        }
    }

    static final class Null implements TruffleObject {

        static final Null INSTANCE = new Null();

        private Null() {
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof Null;
        }

        @Override
        public String toString() {
            return "Null";
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return NullMessageResolutionForeign.ACCESS;
        }

        @MessageResolution(receiverType = Null.class)
        static final class NullMessageResolution {

            @Resolve(message = "IS_NULL")
            public abstract static class NullIsNullNode extends Node {

                public boolean access(Null aNull) {
                    return Null.INSTANCE == aNull;
                }
            }
        }
    }

    static class FunctionsObject implements TruffleObject {

        final Map<String, CallTarget> callTargets = new LinkedHashMap<>();
        final Map<String, TruffleObject> functions = new HashMap<>();

        FunctionsObject() {
        }

        TruffleObject findFunction(String name) {
            TruffleObject functionObject = functions.get(name);
            if (functionObject == null) {
                CallTarget ct = callTargets.get(name);
                if (ct == null) {
                    return null;
                }
                functionObject = new Function(ct);
                functions.put(name, functionObject);
            }
            return functionObject;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return FunctionsObjectMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof FunctionsObject;
        }

        @MessageResolution(receiverType = FunctionsObject.class)
        static final class FunctionsObjectMessageResolution {

            @Resolve(message = "KEYS")
            abstract static class FunctionsObjectKeysNode extends Node {

                @TruffleBoundary
                public Object access(FunctionsObject fo) {
                    return new FunctionNamesObject(fo.callTargets.keySet());
                }
            }

            @Resolve(message = "KEY_INFO")
            abstract static class FunctionsObjectKeyInfoNode extends Node {

                @TruffleBoundary
                public Object access(FunctionsObject fo, String name) {
                    if (fo.callTargets.containsKey(name)) {
                        return KeyInfo.READABLE;
                    } else {
                        return KeyInfo.NONE;
                    }
                }
            }

            @Resolve(message = "READ")
            abstract static class FunctionsObjectReadNode extends Node {

                @TruffleBoundary
                public Object access(FunctionsObject fo, String name) {
                    return fo.findFunction(name);
                }
            }
        }

        static final class FunctionNamesObject implements TruffleObject {

            private final Set<String> names;

            private FunctionNamesObject(Set<String> names) {
                this.names = names;
            }

            @Override
            public ForeignAccess getForeignAccess() {
                return FunctionNamesMessageResolutionForeign.ACCESS;
            }

            public static boolean isInstance(TruffleObject obj) {
                return obj instanceof FunctionNamesObject;
            }

            @MessageResolution(receiverType = FunctionNamesObject.class)
            static final class FunctionNamesMessageResolution {

                @Resolve(message = "HAS_SIZE")
                abstract static class FunctionNamesHasSizeNode extends Node {

                    @SuppressWarnings("unused")
                    public Object access(FunctionNamesObject namesObject) {
                        return true;
                    }
                }

                @Resolve(message = "GET_SIZE")
                abstract static class FunctionNamesGetSizeNode extends Node {

                    public Object access(FunctionNamesObject namesObject) {
                        return namesObject.names.size();
                    }
                }

                @Resolve(message = "READ")
                abstract static class FunctionNamesReadNode extends Node {

                    @CompilerDirectives.TruffleBoundary
                    public Object access(FunctionNamesObject namesObject, int index) {
                        if (index >= namesObject.names.size()) {
                            throw UnknownIdentifierException.raise(Integer.toString(index));
                        }
                        Iterator<String> iterator = namesObject.names.iterator();
                        int i = index;
                        while (i-- > 0) {
                            iterator.next();
                        }
                        return iterator.next();
                    }
                }

            }
        }

    }

    @MessageResolution(receiverType = InstrumentedNode.class)
    static final class InstrumentedNodeAttributes {

        @Resolve(message = "HAS_KEYS")
        abstract static class HasKeysNode extends Node {

            @SuppressWarnings("unused")
            public Object access(InstrumentedNode namesObject) {
                return true;
            }
        }

        @Resolve(message = "READ")
        abstract static class ReadNode extends Node {

            @CompilerDirectives.TruffleBoundary
            public Object access(InstrumentedNode namesObject, String name) {
                switch (name) {
                    case "simpleName":
                        return namesObject.getClass().getSimpleName();
                }
                if (namesObject instanceof ConstantNode) {
                    switch (name) {
                        case "constant":
                            return ((ConstantNode) namesObject).constant;
                    }
                }
                throw UnknownIdentifierException.raise(name);
            }
        }

        @Resolve(message = "KEYS")
        abstract static class KeyNode extends Node {

            @CompilerDirectives.TruffleBoundary
            public Object access(InstrumentedNode namesObject) {
                if (namesObject instanceof ConstantNode) {
                    return new KeysObject(new String[]{"simpleName", "constant"});
                } else {
                    return new KeysObject(new String[]{"simpleName"});
                }
            }
        }

        @Resolve(message = "KEY_INFO")
        abstract static class KeyInfoNode extends Node {

            @SuppressWarnings("unused")
            public int access(TruffleObject obj, String prop) {
                return KeyInfo.READABLE;
            }
        }

    }

    static class KeysObject implements TruffleObject {

        final String[] keys;

        KeysObject(String[] keys) {
            this.keys = keys;
        }

        @Override
        public ForeignAccess getForeignAccess() {
            return KeyObjectResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof KeysObject;
        }

        @MessageResolution(receiverType = KeysObject.class)
        static final class KeyObjectResolution {

            @Resolve(message = "HAS_SIZE")
            abstract static class HasSizeNode extends Node {

                public Object access(@SuppressWarnings("unused") KeysObject obj) {
                    return true;
                }
            }

            @Resolve(message = "GET_SIZE")
            abstract static class GetSizeNode extends Node {

                public Object access(KeysObject obj) {
                    return obj.keys.length;
                }
            }

            @Resolve(message = "READ")
            abstract static class ReadNode extends Node {

                public Object access(KeysObject obj, Number index) {
                    return obj.keys[index.intValue()];
                }
            }
        }

    }

    static class FunctionMetaObject implements TruffleObject {

        @Override
        public ForeignAccess getForeignAccess() {
            return FunctionMetaObjectMessageResolutionForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof FunctionMetaObject;
        }

        @MessageResolution(receiverType = FunctionMetaObject.class)
        static final class FunctionMetaObjectMessageResolution {
        }
    }
}

class InstrumentContext {

    final FunctionsObject callFunctions = new FunctionsObject();
    final Env env;
    final OutputStream out;
    final OutputStream err;
    final AllocationReporter allocationReporter;
    final Source initSource;
    final boolean runInitAfterExec;
    RootCallTarget afterTarget;
    final Set<Thread> spawnedThreads = new WeakSet<>();

    InstrumentContext(Env env, Source initSource, Boolean runInitAfterExec) {
        this.env = env;
        this.out = env.out();
        this.err = env.err();
        this.allocationReporter = env.lookup(AllocationReporter.class);
        this.initSource = initSource;
        this.runInitAfterExec = runInitAfterExec != null && runInitAfterExec;
    }

    private static class WeakSet<T> extends AbstractSet<T> {

        private final Map<T, Void> map = new WeakHashMap<>();

        @Override
        public Iterator<T> iterator() {
            return map.keySet().iterator();
        }

        @Override
        public synchronized int size() {
            return map.size();
        }

        @Override
        public synchronized boolean add(T e) {
            return map.put(e, null) == null;
        }

        @Override
        public synchronized void clear() {
            map.clear();
        }

    }
}
