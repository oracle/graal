/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.instrumentation.test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootBodyTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.Tag.Identifier;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.BlockTag;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.ConstantTag;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.DefineTag;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.FunctionsObject;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.LoopTag;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.library.ReflectionLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
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
@Registration(id = InstrumentationTestLanguage.ID, name = InstrumentationTestLanguage.NAME, version = "2.0", services = {SpecialService.class})
@ProvidedTags({StandardTags.ExpressionTag.class, DefineTag.class, LoopTag.class,
                StandardTags.StatementTag.class, StandardTags.CallTag.class, StandardTags.RootTag.class, StandardTags.RootBodyTag.class,
                StandardTags.TryBlockTag.class, BlockTag.class, ConstantTag.class})
public class InstrumentationTestLanguage extends TruffleLanguage<InstrumentContext> {

    public static final String ID = "instrumentation-test-language";
    public static final String NAME = "Instrumentation Test Language";
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
    public static final Class<? extends Tag> ROOT_BODY = StandardTags.RootBodyTag.class;
    public static final Class<? extends Tag> ROOT = StandardTags.RootTag.class;
    public static final Class<? extends Tag> BLOCK = BlockTag.class;
    public static final Class<? extends Tag> CONSTANT = ConstantTag.class;
    public static final Class<? extends Tag> TRY_CATCH = StandardTags.TryBlockTag.class;

    public static final Class<?>[] TAGS = new Class<?>[]{EXPRESSION, DEFINE, LOOP, STATEMENT, CALL, BLOCK, ROOT_BODY, ROOT, CONSTANT, TRY_CATCH};
    public static final String[] TAG_NAMES = new String[]{"EXPRESSION", "DEFINE", "CONTEXT", "LOOP", "STATEMENT", "CALL", "RECURSIVE_CALL", "CALL_WITH", "BLOCK", "ROOT_BODY", "ROOT", "CONSTANT",
                    "VARIABLE", "ARGUMENT", "PRINT", "ALLOCATION", "SLEEP", "SPAWN", "JOIN", "INVALIDATE", "INTERNAL", "INNER_FRAME", "MATERIALIZE_CHILD_EXPRESSION", "MATERIALIZE_CHILD_STMT_AND_EXPR",
                    "MATERIALIZE_CHILD_STMT_AND_EXPR_NC", "MATERIALIZE_CHILD_STMT_AND_EXPR_SEPARATELY", "MATERIALIZE_CHILD_STATEMENT", "BLOCK_NO_SOURCE_SECTION",
                    "TRY", "CATCH", "THROW", "UNEXPECTED_RESULT", "MULTIPLE"};

    // used to test that no getSourceSection calls happen in certain situations
    private static int rootSourceSectionQueryCount;

    public InstrumentationTestLanguage() {
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
        env.registerService(new SpecialServiceImpl());
        return new InstrumentContext(env, initSource, runInitAfterExec);
    }

    private CallTarget lastParsed;

    public static CallTarget getLastParsedCalltarget() {
        return getCurrentLanguage(InstrumentationTestLanguage.class).lastParsed;
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
        RootCallTarget afterTarget = getCurrentContext(getClass()).afterTarget;
        return lastParsed = Truffle.getRuntime().createCallTarget(new InstrumentationTestRootNode(this, "", outer, afterTarget, node));
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
        private int argumentIndex = 0;

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

            int numberOfIdents = 0;
            if (tag.equals("DEFINE") || tag.equals("ARGUMENT") || tag.equals("CALL") || tag.equals("LOOP") || tag.equals("CONSTANT") || tag.equals("UNEXPECTED_RESULT") || tag.equals("SLEEP") ||
                            tag.equals("SPAWN") | tag.equals("CATCH")) {
                numberOfIdents = 1;
            } else if (tag.equals("VARIABLE") || tag.equals("RECURSIVE_CALL") || tag.equals("CALL_WITH") || tag.equals("PRINT") || tag.equals("THROW")) {
                numberOfIdents = 2;
            }
            int stringLiteralIndex = -1;
            if (tag.equals("PRINT")) {
                stringLiteralIndex = 1;
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
                            idents[argIndex] = (argIndex == stringLiteralIndex) ? stringLiteral() : ident();
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
                case "CALL_WITH":
                    return new CallWithNode(idents[0], parseIdent(idents[1]), childArray);
                case "LOOP":
                    return new WhileLoopNode(parseIdent(idents[0]), childArray);
                case "BLOCK":
                    return new BlockNode(childArray);
                case "BLOCK_NO_SOURCE_SECTION":
                    return new BlockNoSourceSectionNode(childArray);
                case "EXPRESSION":
                    return new ExpressionNode(childArray);
                case "ROOT":
                    return new FunctionRootNode(childArray);
                case "ROOT_BODY":
                    return new FunctionBodyNode(childArray);
                case "STATEMENT":
                    return new StatementNode(childArray);
                case "INTERNAL":
                    return new InternalNode(childArray);
                case "CONSTANT":
                    return new ConstantNode(idents[0], childArray);
                case "VARIABLE":
                    return new VariableNode(idents[0], idents[1], childArray, currentEnv().lookup(AllocationReporter.class));
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
                case "MATERIALIZE_CHILD_STATEMENT":
                    return new MaterializeChildStatementNode(childArray);
                case "MATERIALIZE_CHILD_STMT_AND_EXPR":
                    return new MaterializeChildStatementAndExpressionNode(childArray);
                case "MATERIALIZE_CHILD_STMT_AND_EXPR_NC":
                    return new MaterializeChildStatementAndExpressionNode(childArray, false);
                case "MATERIALIZE_CHILD_STMT_AND_EXPR_SEPARATELY":
                    return new MaterializeChildStatementAndExpressionSeparatelyNode(childArray);
                case "TRY":
                    return new TryCatchNode(childArray);
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

        private String stringLiteral() {
            char c = current();
            if (c != '"') {
                return ident();
            }
            next();
            StringBuilder builder = new StringBuilder();
            while ((c = current()) != EOF && c != '"') {
                builder.append(c);
                next();
            }
            next();
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
        protected List<TruffleStackTraceElement> findAsynchronousFrames(Frame frame) {
            Object[] arguments = frame.getArguments();
            if (arguments.length == 0 || !(arguments[0] instanceof AsyncStackInfo)) {
                return null;
            }
            AsyncStackInfo asyncInfo = (AsyncStackInfo) arguments[0];
            return asyncInfo.createStackTraceElements();
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
                    b.append(InstrumentationTestLanguage.toString(value));
                }
            }
            b.append(")");
            return InstrumentationTestLanguage.toString(b);
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new ExpressionNode(cloneUninitialized(children, materializedTags));
        }

        @Override
        String getShortId() {
            return "E";
        }

        static boolean isExpressionNode(BaseNode node) {
            return node instanceof ExpressionNode || (node instanceof WrapperNode && ((WrapperNode) node).getDelegateNode() instanceof ExpressionNode);
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

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new BlockNoSourceSectionNode(cloneUninitialized(children, materializedTags));
        }
    }

    @GenerateWrapper
    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
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
        @Ignore
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

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        final Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            if (this instanceof ConstantNode) {
                return new KeysObject(new String[]{"simpleName", "constant"});
            } else {
                return new KeysObject(new String[]{"simpleName"});
            }
        }

        @ExportMessage
        final boolean isMemberReadable(@SuppressWarnings("unused") String member) {
            return true;
        }

        @ExportMessage
        Object readMember(String key) throws UnknownIdentifierException {
            switch (key) {
                case "simpleName":
                    return getClass().getSimpleName();
            }
            if (this instanceof ConstantNode) {
                switch (key) {
                    case "constant":
                        return ((ConstantNode) this).constant;
                }
            }
            throw UnknownIdentifierException.create(key);
        }

        public InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
            return new InstrumentedNodeWrapper(this, probe);
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            if (tag == StandardTags.RootTag.class) {
                return this instanceof FunctionRootNode;
            } else if (tag == StandardTags.RootBodyTag.class) {
                return this instanceof FunctionBodyNode || (this instanceof FunctionRootNode && !((FunctionRootNode) this).hasBodyNode);
            } else if (tag == StandardTags.CallTag.class) {
                return this instanceof CallNode || this instanceof RecursiveCallNode || this instanceof CallWithNode;
            } else if (tag == StandardTags.StatementTag.class) {
                return this instanceof StatementNode;
            } else if (tag == StandardTags.ExpressionTag.class) {
                return this instanceof ExpressionNode;
            } else if (tag == StandardTags.TryBlockTag.class) {
                return this instanceof TryNode;
            } else if (tag == LOOP) {
                return this instanceof WhileLoopNode;
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

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new BlockNode(cloneUninitialized(children, materializedTags));
        }

        @Override
        String getShortId() {
            return "BL";
        }
    }

    static class TryCatchNode extends InstrumentedNode {

        @Child InstrumentedNode tryNode;
        @Children private final CatchNode[] catchNodes;

        TryCatchNode(BaseNode[] children) {
            super();
            BaseNode[] tryNodes = selectTryBlock(children);
            int tn = tryNodes.length;
            int cn = children.length - tn;
            catchNodes = new CatchNode[cn];
            System.arraycopy(children, tn, catchNodes, 0, cn);
            tryNode = new TryNode(tryNodes, catchNodes);
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
                    Object exceptionObject = getExceptionObject((TruffleException) ex);
                    if (exceptionObject != null) {
                        String type = InstrumentationTestLanguage.toString(exceptionObject);
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

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new TryCatchNode(cloneUninitialized(children, materializedTags));
        }
    }

    static class TryNode extends BlockNode {

        private final TruffleObject catchesInfoNode;

        TryNode(BaseNode[] children, CatchNode[] catches) {
            super(children);
            this.catchesInfoNode = new CatchesInfoObject(catches);
        }

        @Override
        public Object getNodeObject() {
            return catchesInfoNode;
        }

        @SuppressWarnings("static-method")
        @ExportLibrary(InteropLibrary.class)
        static class CatchesInfoObject implements TruffleObject {

            private final CatchNode[] catches;

            CatchesInfoObject(CatchNode[] catches) {
                this.catches = catches;
            }

            public static boolean isInstance(TruffleObject obj) {
                return obj instanceof CatchesInfoObject;
            }

            @ExportMessage
            boolean hasMembers() {
                return true;
            }

            @ExportMessage
            @TruffleBoundary
            final Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
                return new KeysObject(new String[]{"catches"});
            }

            @ExportMessage
            final boolean isMemberInvocable(@SuppressWarnings("unused") String member) {
                return "catches".equals(member);
            }

            @ExportMessage
            @TruffleBoundary
            final Object invokeMember(String member, Object[] arguments) throws UnknownIdentifierException {
                if ("catches".equals(member)) {
                    String type = arguments[0].toString();
                    for (CatchNode c : catches) {
                        if (type.startsWith(c.getExceptionName())) {
                            return true;
                        }
                    }
                    return false;
                } else {
                    throw UnknownIdentifierException.create(member);
                }
            }
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new TryNode(cloneUninitialized(children, materializedTags), cloneUninitialized(((CatchesInfoObject) catchesInfoNode).catches, materializedTags));
        }
    }

    @GenerateWrapper
    static class CatchNode extends BlockNode {

        private final String exceptionName;

        CatchNode(String exceptionName, BaseNode[] children) {
            super(children);
            this.exceptionName = exceptionName;
        }

        CatchNode() {
            super(null);
            this.exceptionName = null;
        }

        String getExceptionName() {
            return exceptionName;
        }

        @Override
        public WrapperNode createWrapper(ProbeNode probe) {
            return new CatchNodeWrapper(this, probe);
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new CatchNode(exceptionName, cloneUninitialized(children, materializedTags));
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
                    case "ROOT_BODY":
                        resolvedTags.add(RootBodyTag.class);
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid tag " + tag);
                }
            }

        }

        MultipleNode(BaseNode[] children, Collection<Class<? extends Tag>> tags) {
            super(children);
            this.resolvedTags = new HashSet<>(tags);
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return resolvedTags.contains(tag);
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new MultipleNode(cloneUninitialized(children, materializedTags), resolvedTags);
        }
    }

    public static class ThrowNode extends InstrumentedNode {

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

        public static class TestLanguageException extends RuntimeException implements TruffleException {

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

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new ThrowNode(type, message);
        }
    }

    private static final class FunctionRootNode extends InstrumentedNode {

        final boolean hasBodyNode;

        FunctionRootNode(BaseNode[] children) {
            super(children);
            hasBodyNode = hasBodyNode(children);
        }

        private static boolean hasBodyNode(BaseNode[] children) {
            if (children == null) {
                return false;
            }
            for (BaseNode node : children) {
                if (node instanceof FunctionBodyNode ||
                                node instanceof InstrumentedNode && hasBodyNode(((InstrumentedNode) node).children)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        String getShortId() {
            return "R";
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new FunctionRootNode(cloneUninitialized(children, materializedTags));
        }
    }

    private static final class FunctionBodyNode extends InstrumentedNode {

        FunctionBodyNode(BaseNode[] children) {
            super(children);
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new FunctionBodyNode(cloneUninitialized(children, materializedTags));
        }
    }

    static class StatementNode extends InstrumentedNode {

        StatementNode(BaseNode[] children) {
            super(children);
        }

        @Override
        String getShortId() {
            return "S";
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new StatementNode(cloneUninitialized(children, materializedTags));
        }
    }

    static class DefineNode extends InstrumentedNode {
        private final InstrumentationTestLanguage language;
        private final String identifier;
        private final RootCallTarget target;

        DefineNode(InstrumentationTestLanguage lang, String identifier, SourceSection source, BaseNode[] children) {
            this.language = lang;
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
            InstrumentContext context = lookupContextReference(InstrumentationTestLanguage.class).get();
            if (context.callFunctions.callTargets.containsKey(identifier)) {
                if (context.callFunctions.callTargets.get(identifier) != target) {
                    throw new IllegalArgumentException("Identifier redefinition not supported.");
                }
            }
            context.callFunctions.callTargets.put(this.identifier, target);
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            InstrumentationTestRootNode rootNode = (InstrumentationTestRootNode) target.getRootNode();
            return new DefineNode(language, identifier, rootNode.getSourceSection(), new BaseNode[]{cloneUninitialized(rootNode.functionRoot, materializedTags)});
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

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new ContextNode(cloneUninitialized(children, materializedTags));
        }

        @TruffleBoundary
        private TruffleContext createInnerContext() {
            InstrumentContext context = lookupContextReference(InstrumentationTestLanguage.class).get();
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
                InstrumentContext context = lookupContextReference(InstrumentationTestLanguage.class).get();
                CallTarget target = context.callFunctions.callTargets.get(identifier);
                callNode = insert(Truffle.getRuntime().createDirectCallNode(target));
            }
            return callNode.call(new Object[0]);
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new CallNode(identifier, cloneUninitialized(children, materializedTags));
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
                InstrumentContext context = lookupContextReference(InstrumentationTestLanguage.class).get();
                CallTarget target = context.callFunctions.callTargets.get(identifier);
                callNode = Truffle.getRuntime().createDirectCallNode(target);
            }
            spawnCall();
            return Null.INSTANCE;
        }

        @TruffleBoundary
        private void spawnCall() {
            InstrumentationTestLanguage language = lookupLanguageReference(InstrumentationTestLanguage.class).get();
            int asyncDepth = language.getAsynchronousStackDepth();
            AsyncStackInfo asyncInfo;
            if (asyncDepth > 0) {
                asyncInfo = new AsyncStackInfo();
                asyncInfo.addCall(this, null);
                if (asyncDepth > 1) {
                    Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Boolean>() {
                        @Override
                        public Boolean visitFrame(FrameInstance frameInstance) {
                            Node node = frameInstance.getCallNode();
                            if (node == null) {
                                return null;
                            }
                            Frame frame = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                            asyncInfo.addCall(node, frame);
                            if (asyncInfo.callNodes.size() < asyncDepth) {
                                return null;
                            } else {
                                return Boolean.FALSE;
                            }
                        }
                    });
                }
            } else {
                asyncInfo = null;
            }
            InstrumentContext context = lookupContextReference(InstrumentationTestLanguage.class).get();
            Thread t = context.env.createThread(new Runnable() {
                @Override
                public void run() {
                    if (asyncInfo != null) {
                        callNode.call(new Object[]{asyncInfo});
                    } else {
                        callNode.call(new Object[0]);
                    }
                }
            });
            synchronized (context.spawnedThreads) {
                context.spawnedThreads.add(t);
            }
            t.start();
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new SpawnNode(identifier, cloneUninitialized(children, materializedTags));
        }
    }

    private static final class AsyncStackInfo {

        private final List<Node> callNodes = new ArrayList<>();
        private AsyncStackInfo previous;

        AsyncStackInfo() {
        }

        private void addCall(Node callNode, Frame frame) {
            assert callNode != null;
            this.callNodes.add(callNode);
            if (frame != null) {
                Object[] arguments = frame.getArguments();
                if (arguments.length > 0 && arguments[0] instanceof AsyncStackInfo) {
                    previous = (AsyncStackInfo) arguments[0];
                }
            }
        }

        private List<TruffleStackTraceElement> createStackTraceElements() {
            List<TruffleStackTraceElement> elements = new ArrayList<>(callNodes.size());
            for (Node callNode : callNodes) {
                RootCallTarget callTarget = callNode.getRootNode().getCallTarget();
                Frame frame;
                if (previous != null) {
                    frame = Truffle.getRuntime().createMaterializedFrame(new Object[]{previous});
                } else {
                    frame = null;
                }
                elements.add(TruffleStackTraceElement.create(callNode, callTarget, frame));
            }
            return elements;
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
            InstrumentContext context = lookupContextReference(InstrumentationTestLanguage.class).get();
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

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new JoinNode(cloneUninitialized(children, materializedTags));
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
                    InstrumentContext context = lookupContextReference(InstrumentationTestLanguage.class).get();
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

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new RecursiveCallNode(identifier, depth, cloneUninitialized(children, materializedTags));
        }
    }

    private static final class ThisArg {

        final Object thisElement;

        ThisArg(Object thisElement) {
            this.thisElement = thisElement;
        }
    }

    private static class CallWithNode extends InstrumentedNode {

        @Child private DirectCallNode callNode;
        private final String identifier;
        @CompilationFinal(dimensions = 1) private final Object[] thisArg;

        CallWithNode(String identifier, Object thisObj, BaseNode[] children) {
            super(children);
            this.identifier = identifier;
            this.thisArg = new Object[]{new ThisArg(thisObj)};
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                InstrumentContext context = lookupContextReference(InstrumentationTestLanguage.class).get();
                CallTarget target = context.callFunctions.callTargets.get(identifier);
                callNode = insert(Truffle.getRuntime().createDirectCallNode(target));
            }
            Object retval = callNode.call(thisArg);
            return retval;
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new CallWithNode(identifier, ((ThisArg) thisArg[0]).thisElement, cloneUninitialized(children, materializedTags));
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class AllocatedObject implements TruffleObject {

        final Object metaObject;

        AllocatedObject(String name) {
            this.metaObject = new InstrumentationMetaObject(this, name);
        }

        @ExportMessage
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return InstrumentationTestLanguage.class;
        }

        @ExportMessage
        Object toDisplayString(@SuppressWarnings("unused") boolean config) {
            return "AllocatedObject";
        }

        @ExportMessage
        boolean hasMetaObject() {
            return true;
        }

        @ExportMessage
        Object getMetaObject() {
            return metaObject;
        }

    }

    private static class AllocationNode extends InstrumentedNode {

        AllocationNode(BaseNode[] children) {
            super(children);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            AllocationReporter reporter = getCurrentContext(InstrumentationTestLanguage.class).allocationReporter;
            Object allocatedObject = new AllocatedObject("Integer");
            reporter.onEnter(null, 0, 1);
            reporter.onReturnValue(allocatedObject, 0, 1);
            return allocatedObject;
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new AllocationNode(cloneUninitialized(children, materializedTags));
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
                throw new AssertionError();
            }
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new SleepNode(timeToSleep, cloneUninitialized(children, materializedTags));
        }
    }

    private static class ConstantNode extends InstrumentedNode {

        private final Object constant;

        ConstantNode(String identifier, BaseNode[] children) {
            super(children);
            this.constant = parseIdent(identifier);
        }

        ConstantNode(Object constant, BaseNode[] children) {
            super(children);
            this.constant = constant;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return constant;
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new ConstantNode(constant, cloneUninitialized(children, materializedTags));
        }

        @Override
        String getShortId() {
            return "C";
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

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new InvalidateNode(cloneUninitialized(children, materializedTags));
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
            CompilerDirectives.transferToInterpreter();
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

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new TypeSpecializedNode(value);
        }
    }

    private static class UnexpectedResultNode extends InstrumentedNode {

        UnexpectedResultNode(String value) {
            super(new BaseNode[]{new TypeSpecializedNode(value)});
        }

        UnexpectedResultNode(BaseNode[] children) {
            super(children);
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

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new UnexpectedResultNode(cloneUninitialized(children, materializedTags));
        }
    }

    static class MaterializeChildExpressionNode extends StatementNode {

        MaterializeChildExpressionNode(BaseNode[] children) {
            super(children);
        }

        public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
            if (materializedTags.contains(StandardTags.ExpressionTag.class)) {
                MaterializedChildExpressionNode materializedNode = new MaterializedChildExpressionNode(getSourceSection(), cloneUninitialized(children, materializedTags));
                materializedNode.setSourceSection(getSourceSection());
                return materializedNode;
            }
            return this;
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new MaterializeChildExpressionNode(cloneUninitialized(children, materializedTags));
        }
    }

    static class MaterializedChildExpressionNode extends StatementNode {

        @Child private InstrumentedNode expressionNode;

        MaterializedChildExpressionNode(SourceSection sourceSection, BaseNode[] children) {
            super(children);
            this.expressionNode = new ExpressionNode(null);
            this.expressionNode.setSourceSection(sourceSection);
        }

        MaterializedChildExpressionNode(InstrumentedNode expressionNode, BaseNode[] children) {
            super(children);
            this.expressionNode = expressionNode;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            expressionNode.execute(frame);
            return super.execute(frame);
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new MaterializedChildExpressionNode(cloneUninitialized(expressionNode, materializedTags), cloneUninitialized(children, materializedTags));
        }
    }

    static class MaterializeChildStatementNode extends StatementNode {

        MaterializeChildStatementNode(BaseNode[] children) {
            super(children);
        }

        public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
            if (materializedTags.contains(StandardTags.StatementTag.class)) {
                MaterializedChildStatementNode materializedNode = new MaterializedChildStatementNode(getSourceSection(), cloneUninitialized(children, materializedTags));
                materializedNode.setSourceSection(getSourceSection());
                return materializedNode;
            }
            return this;
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new MaterializeChildStatementNode(cloneUninitialized(children, materializedTags));
        }
    }

    static class MaterializedChildStatementNode extends StatementNode {

        @Child private InstrumentedNode statementNode;

        MaterializedChildStatementNode(SourceSection sourceSection, BaseNode[] children) {
            super(children);
            this.statementNode = new StatementNode(new BaseNode[0]);
            this.statementNode.setSourceSection(sourceSection);
        }

        MaterializedChildStatementNode(InstrumentedNode statementNode, BaseNode[] children) {
            super(children);
            this.statementNode = statementNode;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            statementNode.execute(frame);
            return super.execute(frame);
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new MaterializedChildStatementNode(cloneUninitialized(statementNode, materializedTags), cloneUninitialized(children, materializedTags));
        }
    }

    static class MaterializeChildStatementAndExpressionSeparatelyNode extends StatementNode {

        MaterializeChildStatementAndExpressionSeparatelyNode(BaseNode[] children) {
            super(children);
        }

        public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
            InstrumentedNode materializedNode = this;
            if (materializedTags.contains(StandardTags.StatementTag.class) && materializedTags.contains(StandardTags.ExpressionTag.class)) {
                materializedNode = new MaterializedChildStatementAndExpressionSeparatelyNode(getSourceSection(), cloneUninitialized(children, materializedTags));
            } else if (materializedTags.contains(StandardTags.StatementTag.class)) {
                materializedNode = new MaterializedChildStatementMaterializeChildExpressionNode(getSourceSection(), cloneUninitialized(children, materializedTags));
            } else if (materializedTags.contains(StandardTags.ExpressionTag.class)) {
                materializedNode = new MaterializedChildExpressionMaterializeChildStatementNode(getSourceSection(), cloneUninitialized(children, materializedTags));
            }
            if (materializedNode != this) {
                materializedNode.setSourceSection(getSourceSection());
            }
            return materializedNode;
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new MaterializeChildExpressionNode(cloneUninitialized(children, materializedTags));
        }
    }

    static class MaterializedChildStatementMaterializeChildExpressionNode extends StatementNode {

        @Child private InstrumentedNode statementNode;

        MaterializedChildStatementMaterializeChildExpressionNode(SourceSection sourceSection, BaseNode[] children) {
            super(children);
            this.statementNode = new StatementNode(new BaseNode[0]);
            this.statementNode.setSourceSection(sourceSection);
        }

        MaterializedChildStatementMaterializeChildExpressionNode(InstrumentedNode statementNode, BaseNode[] children) {
            super(children);
            this.statementNode = statementNode;
        }

        @Override
        public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
            if (materializedTags.contains(StandardTags.ExpressionTag.class)) {
                MaterializedChildStatementAndExpressionSeparatelyNode materializedNode = new MaterializedChildStatementAndExpressionSeparatelyNode(getSourceSection(), statementNode, true,
                                cloneUninitialized(children, materializedTags));
                materializedNode.setSourceSection(getSourceSection());
                return materializedNode;
            }
            return this;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            statementNode.execute(frame);
            return super.execute(frame);
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new MaterializedChildStatementMaterializeChildExpressionNode(cloneUninitialized(statementNode, materializedTags), cloneUninitialized(children, materializedTags));
        }
    }

    static class MaterializedChildExpressionMaterializeChildStatementNode extends StatementNode {

        @Child private InstrumentedNode expressionNode;

        MaterializedChildExpressionMaterializeChildStatementNode(SourceSection sourceSection, BaseNode[] children) {
            super(children);
            this.expressionNode = new ExpressionNode(null);
            this.expressionNode.setSourceSection(sourceSection);
        }

        MaterializedChildExpressionMaterializeChildStatementNode(InstrumentedNode expressionNode, BaseNode[] children) {
            super(children);
            this.expressionNode = expressionNode;
        }

        @Override
        public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
            if (materializedTags.contains(StandardTags.StatementTag.class)) {
                MaterializedChildStatementAndExpressionSeparatelyNode materializedNode = new MaterializedChildStatementAndExpressionSeparatelyNode(getSourceSection(), expressionNode, false,
                                cloneUninitialized(children, materializedTags));
                materializedNode.setSourceSection(getSourceSection());
                return materializedNode;
            }
            return this;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            expressionNode.execute(frame);
            return super.execute(frame);
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new MaterializedChildExpressionMaterializeChildStatementNode(cloneUninitialized(expressionNode, materializedTags), cloneUninitialized(children, materializedTags));
        }
    }

    static class MaterializedChildStatementAndExpressionSeparatelyNode extends StatementNode {

        @Child private InstrumentedNode statementNode;
        @Child private InstrumentedNode expressionNode;

        MaterializedChildStatementAndExpressionSeparatelyNode(SourceSection sourceSection, BaseNode[] children) {
            super(children);
            this.statementNode = new StatementNode(new BaseNode[0]);
            this.statementNode.setSourceSection(sourceSection);
            this.expressionNode = new ExpressionNode(null);
            this.expressionNode.setSourceSection(sourceSection);
        }

        MaterializedChildStatementAndExpressionSeparatelyNode(InstrumentedNode statemetNode, InstrumentedNode expressionNode, BaseNode[] children) {
            super(children);
            this.statementNode = statemetNode;
            this.expressionNode = expressionNode;
        }

        MaterializedChildStatementAndExpressionSeparatelyNode(SourceSection sourceSection, InstrumentedNode statemetOrExpressionNode, boolean isStatement, BaseNode[] children) {
            super(children);
            if (isStatement) {
                this.statementNode = statemetOrExpressionNode;
                this.expressionNode = new ExpressionNode(null);
                this.expressionNode.setSourceSection(sourceSection);
            } else {
                this.statementNode = new StatementNode(new BaseNode[0]);
                this.statementNode.setSourceSection(sourceSection);
                this.expressionNode = statemetOrExpressionNode;
            }
        }

        @Override
        public Object execute(VirtualFrame frame) {
            statementNode.execute(frame);
            expressionNode.execute(frame);
            return super.execute(frame);
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new MaterializedChildStatementAndExpressionSeparatelyNode(cloneUninitialized(statementNode, materializedTags), cloneUninitialized(expressionNode, materializedTags),
                            cloneUninitialized(children, materializedTags));
        }
    }

    static class MaterializeChildStatementAndExpressionNode extends StatementNode {
        private final boolean cloneSubTreeOnMaterialization;

        MaterializeChildStatementAndExpressionNode(BaseNode[] children) {
            this(children, true);
        }

        MaterializeChildStatementAndExpressionNode(BaseNode[] children, boolean cloneSubTreeOnMaterialization) {
            super(children);
            this.cloneSubTreeOnMaterialization = cloneSubTreeOnMaterialization;
        }

        public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
            if (materializedTags.contains(StandardTags.ExpressionTag.class) || materializedTags.contains(StandardTags.StatementTag.class)) {
                BaseNode[] newChildren = children != null ? new BaseNode[children.length] : null;
                int skippedExpressionsCount = 0;
                if (newChildren != null) {
                    for (int i = 0; i < newChildren.length; i++) {
                        if (children[i] instanceof ExpressionNode) {
                            ExpressionNode expr = (ExpressionNode) children[i];
                            if (expr.children != null && expr.children.length == 1 && ExpressionNode.isExpressionNode(expr.children[0])) {
                                // use nested expression
                                newChildren[i] = expr.children[0];
                                skippedExpressionsCount++;
                            }
                        } else {
                            newChildren[i] = children[i];
                        }
                    }
                }
                BaseNode[] replacementForSkippedExpressions = new BaseNode[skippedExpressionsCount];
                for (int i = 0; i < skippedExpressionsCount; i++) {
                    replacementForSkippedExpressions[i] = new ExpressionNode(null);
                    replacementForSkippedExpressions[i].setSourceSection(getSourceSection());
                }
                MaterializedChildStatementAndExpressionNode materializedNode = new MaterializedChildStatementAndExpressionNode(getSourceSection(), replacementForSkippedExpressions,
                                cloneSubTreeOnMaterialization ? cloneUninitialized(newChildren, materializedTags) : newChildren);
                materializedNode.setSourceSection(getSourceSection());
                return materializedNode;
            }
            return this;
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new MaterializeChildStatementAndExpressionNode(cloneUninitialized(children, materializedTags));
        }
    }

    static class MaterializedChildStatementAndExpressionNode extends StatementNode {

        @Child private InstrumentedNode statementNode;

        MaterializedChildStatementAndExpressionNode(SourceSection sourceSection, BaseNode[] expressions, BaseNode[] children) {
            super(children);
            this.statementNode = new StatementNode(expressions);
            this.statementNode.setSourceSection(sourceSection);
        }

        MaterializedChildStatementAndExpressionNode(InstrumentedNode statementNode, BaseNode[] children) {
            super(children);
            this.statementNode = statementNode;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            statementNode.execute(frame);
            return super.execute(frame);
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new MaterializedChildStatementAndExpressionNode(cloneUninitialized(statementNode, materializedTags), cloneUninitialized(children, materializedTags));
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

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new InnerFrameNode(cloneUninitialized(children, materializedTags));
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

        @CompilationFinal private FrameSlot slot;
        final AllocationReporter allocationReporter;

        private VariableNode(String name, String identifier, BaseNode[] children, AllocationReporter allocationReporter) {
            super(children);
            this.name = name;
            this.value = parseIdent(identifier);
            this.allocationReporter = allocationReporter;
        }

        private VariableNode(String name, Object value, BaseNode[] children, AllocationReporter allocationReporter) {
            super(children);
            this.name = name;
            this.value = value;
            this.allocationReporter = allocationReporter;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (allocationReporter.isActive()) {
                // Pretend we're allocating the value, for tests
                allocationReporter.onEnter(null, 0, getValueSize());
            }
            if (slot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                slot = frame.getFrameDescriptor().findOrAddFrameSlot(name);
            }
            frame.setObject(slot, value);
            if (allocationReporter.isActive()) {
                allocationReporter.onReturnValue(value, 0, getValueSize());
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

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new VariableNode(name, value, cloneUninitialized(children, materializedTags), allocationReporter);
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

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new ArgumentNode(name, cloneUninitialized(children, materializedTags));
        }
    }

    static final class WhileLoopNode extends InstrumentedNode {

        @Child private LoopNode loop;

        @CompilationFinal FrameSlot loopIndexSlot;
        @CompilationFinal FrameSlot loopResultSlot;

        WhileLoopNode(Object loopCount, BaseNode[] children) {
            this.loop = Truffle.getRuntime().createLoopNode(new LoopConditionNode(loopCount, children));
        }

        WhileLoopNode(int loopCount, boolean infinite, BaseNode[] children) {
            this.loop = Truffle.getRuntime().createLoopNode(new LoopConditionNode(loopCount, infinite, children));
        }

        FrameSlot getLoopIndex() {
            if (loopIndexSlot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                loopIndexSlot = getRootNode().getFrameDescriptor().findOrAddFrameSlot("loopIndex" + getLoopDepth());
            }
            return loopIndexSlot;
        }

        FrameSlot getResult() {
            if (loopResultSlot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                loopResultSlot = getRootNode().getFrameDescriptor().findOrAddFrameSlot("loopResult" + getLoopDepth());
            }
            return loopResultSlot;
        }

        private int getLoopDepth() {
            Node node = getParent();
            int count = 0;
            while (node != null) {
                if (node instanceof WhileLoopNode) {
                    count++;
                }
                node = node.getParent();
            }
            return count;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            frame.setObject(getResult(), Null.INSTANCE);
            frame.setInt(getLoopIndex(), 0);
            loop.execute(frame);
            try {
                return frame.getObject(loopResultSlot);
            } catch (FrameSlotTypeException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
            }
        }

        @Override
        String getShortId() {
            return "L";
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            LoopConditionNode repeatingNode = (LoopConditionNode) loop.getRepeatingNode();
            return new WhileLoopNode(repeatingNode.loopCount, repeatingNode.infinite, cloneUninitialized(repeatingNode.children, materializedTags));
        }

        final class LoopConditionNode extends InstrumentedNode implements RepeatingNode {

            private final int loopCount;
            private final boolean infinite;

            LoopConditionNode(Object loopCount, BaseNode[] children) {
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

            LoopConditionNode(int loopCount, boolean infinite, BaseNode[] children) {
                super(children);
                this.loopCount = loopCount;
                this.infinite = infinite;
            }

            @Override
            public boolean isInstrumentable() {
                return false;
            }

            public boolean executeRepeating(VirtualFrame frame) {
                int i;
                try {
                    i = frame.getInt(loopIndexSlot);
                } catch (FrameSlotTypeException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new AssertionError(e);
                }
                if (infinite || i < loopCount) {
                    Object resultValue = super.execute(frame);
                    frame.setInt(loopIndexSlot, i + 1);
                    frame.setObject(loopResultSlot, resultValue);
                    return true;
                } else {
                    return false;
                }
            }
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

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new InternalNode(cloneUninitialized(children, materializedTags));
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

        PrintNode(Output where, String what, BaseNode[] children) {
            super(children);
            this.where = where;
            this.what = what;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (writer == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                InstrumentContext context = lookupContextReference(InstrumentationTestLanguage.class).get();
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

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new PrintNode(where, what, cloneUninitialized(children, materializedTags));
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

        String getShortId() {
            return "BS";
        }

        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            if (this instanceof InstrumentableNode.WrapperNode) {
                InstrumentableNode.WrapperNode wrapperNode = (InstrumentableNode.WrapperNode) this;
                return cloneUninitialized((BaseNode) wrapperNode.getDelegateNode(), materializedTags);
            }

            throw new UnsupportedOperationException();
        }

        @SuppressWarnings("unchecked")
        public static <T extends BaseNode> T cloneUninitialized(T node, Set<Class<? extends Tag>> materializedTags) {
            if (node == null) {
                return null;
            } else {
                T copy = node;
                if (node instanceof InstrumentableNode && ((InstrumentableNode) node).isInstrumentable() && materializedTags != null) {
                    copy = (T) ((InstrumentableNode) node).materializeInstrumentableNodes(materializedTags);
                }
                if (node == copy) {
                    copy = (T) node.copyUninitialized(materializedTags);
                    if (copy.getSourceSection() == null && node.getSourceSection() != null) {
                        copy.setSourceSection(node.getSourceSection());
                    }
                }
                return copy;
            }
        }

        public static <T extends BaseNode> T[] cloneUninitialized(T[] nodeArray, Set<Class<? extends Tag>> materializedTags) {
            if (nodeArray == null) {
                return null;
            } else {
                T[] copy = nodeArray.clone();
                for (int i = 0; i < copy.length; i++) {
                    copy[i] = cloneUninitialized(copy[i], materializedTags);
                }
                return copy;
            }
        }
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
    protected Object getLanguageView(InstrumentContext context, Object value) {
        return new InstrumentationLanguageView(value);
    }

    @Override
    protected Iterable<Scope> findLocalScopes(InstrumentContext context, Node node, Frame frame) {
        Iterable<Scope> scopes = super.findLocalScopes(context, node, frame);
        // arguments[0] contains 'this'. Add it to the default scope:
        Object[] arguments;
        Object thisObject;
        if (frame != null && (arguments = frame.getArguments()) != null && arguments.length > 0 && arguments[0] instanceof ThisArg) {
            thisObject = ((ThisArg) arguments[0]).thisElement;
        } else {
            thisObject = null;
        }
        // Find the current root instance - function.
        Object function = context.callFunctions.findFunction(node.getRootNode().getName());
        return new Iterable<Scope>() {
            @Override
            public Iterator<Scope> iterator() {
                Iterator<Scope> iterator = scopes.iterator();
                return new Iterator<Scope>() {
                    @Override
                    public boolean hasNext() {
                        return iterator.hasNext();
                    }

                    @Override
                    public Scope next() {
                        Scope scope = iterator.next();
                        return Scope.newBuilder(scope.getName(), scope.getVariables()).node(scope.getNode()).arguments(scope.getArguments()).receiver("THIS", thisObject).rootInstance(
                                        function).build();
                    }
                };
            }
        };
    }

    public static int getRootSourceSectionQueryCount() {
        return rootSourceSectionQueryCount;
    }

    @SuppressWarnings("static-method")
    @ExportLibrary(InteropLibrary.class)
    static final class Function implements TruffleObject {

        private final CallTarget ct;

        Function(CallTarget ct) {
            this.ct = ct;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        Object execute(Object[] arguments) {
            return ct.call(arguments);
        }

        @ExportMessage
        @TruffleBoundary
        boolean hasSourceLocation() {
            return ((RootCallTarget) ct).getRootNode().getSourceSection() != null;
        }

        @ExportMessage
        @TruffleBoundary
        SourceSection getSourceLocation() {
            return ((RootCallTarget) ct).getRootNode().getSourceSection();
        }

        @ExportMessage
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return InstrumentationTestLanguage.class;
        }

        @ExportMessage
        @TruffleBoundary
        Object toDisplayString(@SuppressWarnings("unused") boolean config) {
            return "Function:" + ct;
        }

        @ExportMessage
        boolean hasMetaObject() {
            return true;
        }

        @ExportMessage
        Object getMetaObject() {
            return new InstrumentationMetaObject(this, "Function");
        }

    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    public static final class Null implements TruffleObject {

        public static final Null INSTANCE = new Null();

        private Null() {
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof Null;
        }

        @Override
        public String toString() {
            return "Null";
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isNull() {
            return true;
        }

        @ExportMessage
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return InstrumentationTestLanguage.class;
        }

        @ExportMessage
        @TruffleBoundary
        Object toDisplayString(@SuppressWarnings("unused") boolean config) {
            return "Null";
        }
    }

    @ExportLibrary(InteropLibrary.class)
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

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        final Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new KeysObject(callTargets.keySet().toArray(new String[0]));
        }

        @ExportMessage
        @TruffleBoundary
        final boolean isMemberReadable(@SuppressWarnings("unused") String member) {
            return callTargets.containsKey(member);
        }

        @ExportMessage
        @TruffleBoundary
        Object readMember(String key) {
            return findFunction(key);
        }

        @ExportMessage
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return InstrumentationTestLanguage.class;
        }

        @ExportMessage
        @TruffleBoundary
        Object toDisplayString(@SuppressWarnings("unused") boolean config) {
            return "Functions:" + functions;
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static class KeysObject implements TruffleObject {

        private final String[] keys;

        KeysObject(String[] keys) {
            this.keys = keys;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index >= 0 && index < keys.length;
        }

        @ExportMessage
        long getArraySize() {
            return keys.length;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            try {
                return keys[(int) index];
            } catch (IndexOutOfBoundsException e) {
                CompilerDirectives.transferToInterpreter();
                throw InvalidArrayIndexException.create(index);
            }
        }

        @ExportMessage
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return InstrumentationTestLanguage.class;
        }

        @ExportMessage
        @TruffleBoundary
        Object toDisplayString(@SuppressWarnings("unused") boolean config) {
            return "Keys" + Arrays.toString(keys);
        }

    }

    @ExportLibrary(InteropLibrary.class)
    @SuppressWarnings("static-method")
    static final class InstrumentationMetaObject implements TruffleObject {

        private final Object original;
        private final String name;

        InstrumentationMetaObject(Object original, String name) {
            this.original = original;
            this.name = name;
        }

        @ExportMessage
        boolean isMetaObject() {
            return true;
        }

        @ExportMessage
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return InstrumentationTestLanguage.class;
        }

        @ExportMessage
        Object getMetaQualifiedName() {
            return name;
        }

        @ExportMessage
        Object getMetaSimpleName() {
            return name;
        }

        @ExportMessage
        @TruffleBoundary
        boolean isMetaInstance(Object instance) {
            return instance.equals(original) || (original instanceof InstrumentationLanguageView && instance.equals(((InstrumentationLanguageView) original).delegate));
        }

        @ExportMessage
        Object toDisplayString(@SuppressWarnings("unused") boolean config) {
            return name;
        }

    }

    /**
     * Default implementation for the instrumentation view in {@link TruffleLanguage}. Should be
     * removed with deprecated methods in {@link TruffleLanguage}.
     */
    @ExportLibrary(value = InteropLibrary.class, delegateTo = "delegate")
    @ExportLibrary(value = ReflectionLibrary.class, delegateTo = "delegate")
    @SuppressWarnings("static-method")
    static final class InstrumentationLanguageView implements TruffleObject {

        protected final Object delegate;

        InstrumentationLanguageView(Object delegate) {
            this.delegate = delegate;
        }

        @ExportMessage
        boolean hasLanguage() {
            return true;
        }

        @ExportMessage
        Class<? extends TruffleLanguage<?>> getLanguage() {
            return InstrumentationTestLanguage.class;
        }

        @ExportMessage
        @TruffleBoundary
        boolean hasSourceLocation() {
            if (delegate instanceof Integer || delegate instanceof Long) {
                return true;
            } else if (delegate instanceof Boolean) {
                return true;
            } else if (delegate != null && delegate.equals(Double.POSITIVE_INFINITY)) {
                return true;
            }
            return false;
        }

        @ExportMessage
        @TruffleBoundary
        SourceSection getSourceLocation() throws UnsupportedMessageException {
            if (delegate instanceof Integer || delegate instanceof Long) {
                return Source.newBuilder(ID, "source integer", "integer").build().createSection(1);
            } else if (delegate instanceof Boolean) {
                return Source.newBuilder(ID, "source boolean", "boolean").build().createSection(1);
            } else if (delegate != null && delegate.equals(Double.POSITIVE_INFINITY)) {
                return Source.newBuilder(ID, "source infinity", "double").build().createSection(1);
            }
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        @TruffleBoundary
        Object toDisplayString(@SuppressWarnings("unused") boolean config) {
            return Objects.toString(delegate);
        }

        @ExportMessage
        boolean hasMetaObject() {
            return true;
        }

        @TruffleBoundary
        private String getTypeName() {
            if (delegate instanceof Integer || delegate instanceof Long) {
                return "Integer";
            }
            if (delegate instanceof Boolean) {
                return "Boolean";
            }
            if (delegate.equals(Double.POSITIVE_INFINITY)) {
                return "Infinity";
            }
            return null;
        }

        @ExportMessage
        @TruffleBoundary
        Object getMetaObject() {
            return new InstrumentationMetaObject(this, getTypeName());
        }

    }

    @TruffleBoundary
    private static String toString(Object object) {
        return object.toString();
    }

    @TruffleBoundary
    private static Object getExceptionObject(TruffleException ex) {
        return ex.getExceptionObject();
    }

    public static final class SpecialServiceImpl implements SpecialService {
        @Override
        public String fileExtension() {
            return FILENAME_EXTENSION;
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
