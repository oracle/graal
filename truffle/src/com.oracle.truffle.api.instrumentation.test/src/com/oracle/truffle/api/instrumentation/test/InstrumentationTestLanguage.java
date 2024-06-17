/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.AbstractSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.graalvm.collections.Pair;
import org.junit.Assert;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
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
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.AsyncStackInfo;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.BlockTag;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.ConstantTag;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.DefineTag;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.FunctionsObject;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.LoopTag;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.library.ReflectionLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.TestAPIAccessor;

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
 * <li><code>CALL(name)</code> - call a function</li>
 * <li><code>CALL_WITH(name, receiver)</code> - call a function with a receiver object</li>
 * <li><code>CALL_SPLIT(name)</code> - split the function and call it, returns <code>false</code>
 * when the split is not allowed</li>
 * </ul>
 * </p>
 * <p>
 * The language uses shared context policy, because of the CONTEXT statement that creates and enters
 * inner context. The code executed in the inner context is parsed in the outer context, so the
 * context cannot be stored in the nodes, because the nodes can be shared by may different contexts.
 * </p>
 */
@Registration(id = InstrumentationTestLanguage.ID, name = InstrumentationTestLanguage.NAME, version = "2.0", services = {SpecialService.class}, contextPolicy = TruffleLanguage.ContextPolicy.SHARED)
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
    public static final Set<String> TAG_NAMES = Set.of("EXPRESSION", "DEFINE", "CONTEXT", "LOOP", "STATEMENT", "CALL", "RECURSIVE_CALL", "CALL_WITH", "BLOCK", "ROOT_BODY", "ROOT", "CONSTANT",
                    "VARIABLE", "ARGUMENT", "READ_VAR", "PRINT", "ALLOCATION", "SLEEP", "SPAWN", "JOIN", "INVALIDATE", "INTERNAL", "INNER_FRAME", "MATERIALIZE_CHILD_EXPRESSION",
                    "MATERIALIZE_CHILD_STMT_AND_EXPR", "MATERIALIZE_CHILD_STMT_AND_EXPR_NC", "MATERIALIZE_CHILD_STMT_AND_EXPR_SEPARATELY", "MATERIALIZE_CHILD_STATEMENT", "BLOCK_NO_SOURCE_SECTION",
                    "TRY", "CATCH", "THROW", "UNEXPECTED_RESULT", "MULTIPLE", "EXIT", "CANCEL", "RETURN", "INVOKE_MEMBER", "CALL_SPLIT", "ASYNC_CALL", "ASYNC_RESUME");

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

    public final Map<Object, CallTarget> codeCache = new ConcurrentHashMap<>();

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

    private static FrameDescriptor getDefaultFrameDescriptor() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.addSlot(FrameSlotKind.Static, null, null);
        builder.addSlot(FrameSlotKind.Int, null, null);
        return builder.build();
    }

    public static CallTarget getLastParsedCalltarget() {
        return InstrumentationTestLanguage.get(null).lastParsed;
    }

    @Override
    protected void initializeContext(InstrumentContext context) throws Exception {
        super.initializeContext(context);
        Source code = context.initSource;
        if (code != null) {
            SourceSection outer = code.createSection(0, code.getLength());
            BaseNode node = parse(code);
            RootCallTarget rct = new InstrumentationTestRootNode(this, "", outer, node).getCallTarget();
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
        if (node instanceof AsyncResumeNode) {
            // Unavailable source section, so that it can be filtered out by AsyncStackSamplingTest.
            outer = Source.newBuilder(outer.getSource()).internal(true).build().createUnavailableSection();
        }
        RootCallTarget afterTarget = InstrumentContext.get(null).afterTarget;
        return lastParsed = new InstrumentationTestRootNode(this, "", outer, afterTarget, node).getCallTarget();
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

    private static final LanguageReference<InstrumentationTestLanguage> REFERENCE = LanguageReference.create(InstrumentationTestLanguage.class);

    static InstrumentationTestLanguage get(Node node) {
        return REFERENCE.get(node);
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
        return new Parser(code).parse();
    }

    private static final class Parser {

        private static final char EOF = (char) -1;

        private final Source source;
        private final String code;
        private int current;
        private int argumentIndex = 0;

        Parser(Source source) {
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

            int numberOfIdents = switch (tag) {
                case "DEFINE", "ARGUMENT", "READ_VAR", "CALL", "CALL_SPLIT", "LOOP", "CONSTANT", "SLEEP", "SPAWN" -> 1;
                case "EXIT", "RETURN", "PRINT", "INVOKE_MEMBER", "UNEXPECTED_RESULT", "ASYNC_CALL", "ASYNC_RESUME" -> 1;
                case "VARIABLE", "RECURSIVE_CALL", "CALL_WITH", "THROW", "CATCH" -> 2;
                default -> 0;
            };
            int maybeQuotedStringLiteralIndex = -1;
            if (tag.equals("CONSTANT")) {
                maybeQuotedStringLiteralIndex = 0;
            }
            if (tag.equals("THROW")) {
                maybeQuotedStringLiteralIndex = 1;
            }
            List<String> multipleTags = null;
            if (tag.equals("MULTIPLE")) {
                multipleTags = multipleTags();
            }
            String[] idents = new String[numberOfIdents];
            boolean[] quotedIdents = new boolean[numberOfIdents];
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
                            if (argIndex == maybeQuotedStringLiteralIndex) {
                                boolean quoted = current() == '"';
                                quotedIdents[argIndex] = quoted;
                                idents[argIndex] = quoted ? quotedStringLiteral() : ident();
                            } else {
                                idents[argIndex] = ident();
                            }
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
            BaseNode node = createNode(tag, idents, quotedIdents, sourceSection, childArray, multipleTags);
            if (tag.equals("DEFINE")) {
                argumentIndex = 0;
            } else if (tag.equals("ARGUMENT")) {
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
            return TAG_NAMES.contains(tag);
        }

        private static BaseNode createNode(String tag, String[] idents, boolean[] quotedIdents, SourceSection sourceSection, BaseNode[] childArray, List<String> multipleTags) throws AssertionError {
            switch (tag) {
                case "DEFINE":
                    return new DefineNode(idents[0], sourceSection, childArray);
                case "CONTEXT":
                    return new ContextNode(childArray);
                case "ARGUMENT":
                    return new ArgumentNode(idents[0], childArray);
                case "CALL":
                    return new CallNode(idents[0], childArray);
                case "CALL_SPLIT":
                    return new CallCloneNode(idents[0], childArray);
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
                    return new ConstantNode(idents[0], quotedIdents[0], childArray);
                case "VARIABLE":
                    return new VariableNode(idents[0], idents[1], childArray, InstrumentContext.get(null).env.lookup(AllocationReporter.class));
                case "READ_VAR":
                    return new ReadVariableNode(idents[0], childArray);
                case "PRINT":
                    return new PrintNode(idents[0], childArray);
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
                    return new CatchNode(idents[0], idents[1], childArray);
                case "THROW":
                    return new ThrowNode(idents[0], idents[1]);
                case "UNEXPECTED_RESULT":
                    return new UnexpectedResultNode(idents[0]);
                case "MULTIPLE":
                    return new MultipleNode(childArray, multipleTags);
                case "EXIT":
                    return new ExitNode(idents[0]);
                case "CANCEL":
                    return new CancelNode();
                case "RETURN":
                    return new ReturnNode(idents[0]);
                case "INVOKE_MEMBER":
                    return new ExecuteMemberNode(idents[0], childArray);
                case "ASYNC_CALL":
                    return AsyncCallNode.create(idents[0], childArray, sourceSection);
                case "ASYNC_RESUME":
                    return new AsyncResumeNode(idents[0], childArray);
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

        private String quotedStringLiteral() {
            char c = current();
            assert c == '"';
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

        private final InstrumentationTestLanguage language;
        private final String name;
        private final SourceSection sourceSection;
        private final RootCallTarget afterTarget;
        @Child private InstrumentedNode functionRoot;

        protected InstrumentationTestRootNode(InstrumentationTestLanguage lang, String name, SourceSection sourceSection, BaseNode... expressions) {
            this(lang, name, sourceSection, null, expressions);
        }

        protected InstrumentationTestRootNode(InstrumentationTestLanguage lang, String name, SourceSection sourceSection, RootCallTarget afterTarget, BaseNode... expressions) {
            super(lang, getDefaultFrameDescriptor());
            this.language = lang;
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
        public boolean isCloningAllowed() {
            return true;
        }

        @Override
        protected boolean isCloneUninitializedSupported() {
            return true;
        }

        @Override
        protected RootNode cloneUninitialized() {
            BaseNode[] children = BaseNode.cloneUninitialized(functionRoot.children, Set.of());
            return new InstrumentationTestRootNode(language, name, sourceSection, afterTarget, children);
        }

        @Override
        public SourceSection getSourceSection() {
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
            StringBuilderWrapper b = new StringBuilderWrapper();
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
            return b.toString();
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

    public static class InstrumentedBaseNode extends BaseNode implements InstrumentableNode {

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        public WrapperNode createWrapper(ProbeNode probe) {
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @ExportLibrary(NodeLibrary.class)
    @GenerateWrapper
    @SuppressWarnings("static-method")
    public abstract static class InstrumentedNode extends InstrumentedBaseNode implements InstrumentableNode, TruffleObject {

        private static final String THIS = "THIS";

        @Children final BaseNode[] children;

        public InstrumentedNode() {
            this.children = null;
        }

        public InstrumentedNode(BaseNode[] children) {
            this.children = children;
        }

        @Override
        public boolean isInstrumentable() {
            return getSourceSection() != null;
        }

        @Override
        public InstrumentableNode.WrapperNode createWrapper(ProbeNode probe) {
            return new InstrumentedNodeWrapper(this, probe);
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
                    return classSimpleName(getClass());
            }
            if (this instanceof ConstantNode) {
                switch (key) {
                    case "constant":
                        return ((ConstantNode) this).constant;
                }
            }
            throw UnknownIdentifierException.create(key);
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

        @Override
        public Object getNodeObject() {
            return this;
        }

        public static boolean isInstance(TruffleObject o) {
            return o instanceof InstrumentedNode;
        }

        // NodeLibrary

        @ExportMessage
        final boolean hasScope(@SuppressWarnings("unused") Frame frame) {
            return true;
        }

        @ExportMessage
        final Object getScope(Frame frame, boolean nodeEnter) {
            return getScopeSlowPath(frame != null ? frame.materialize() : null, nodeEnter);
        }

        @TruffleBoundary
        private Object getScopeSlowPath(MaterializedFrame frame, boolean nodeEnter) {
            // Delegates to the default implementation
            if (this instanceof FunctionRootNode) {
                // has RootTag, is at function root, provide arguments
                Object[] arguments;
                if (frame != null) {
                    arguments = frame.getArguments();
                    if (arguments.length > 0 && arguments[0] instanceof ThisArg) {
                        arguments = Arrays.copyOf(arguments, arguments.length);
                        arguments[0] = ((ThisArg) arguments[0]).thisElement;
                    }
                } else {
                    arguments = new Object[0];
                }
                return new ArgumentsArrayObject(arguments);
            } else {
                Object variables = getDefaultScope(frame, nodeEnter);
                Object[] arguments;
                if (frame != null && (arguments = frame.getArguments()) != null && arguments.length > 0 && arguments[0] instanceof ThisArg) {
                    variables = new VariablesWithThis(variables, ((ThisArg) arguments[0]).thisElement);
                }
                return variables;
            }
        }

        private Object getDefaultScope(Frame frame, boolean nodeEnter) {
            try {
                // the dummy trigger creating the default scope implementation
                InstrumentedBaseNode dummy = insert(new InstrumentedBaseNode());
                return NodeLibrary.getUncached().getScope(dummy, frame, nodeEnter);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @ExportMessage()
        final boolean hasReceiverMember(@SuppressWarnings("unused") Frame frame) {
            if (frame == null) {
                return false;
            }
            Object[] args = frame.getArguments();
            return args.length > 0 && args[0] instanceof ThisArg;
        }

        @ExportMessage
        final Object getReceiverMember(@SuppressWarnings("unused") Frame frame) throws UnsupportedMessageException {
            if (frame != null) {
                Object[] args = frame.getArguments();
                if (args.length > 0 && args[0] instanceof ThisArg) {
                    return THIS;
                }
            }
            throw UnsupportedMessageException.create();
        }

        @ExportMessage
        final boolean hasRootInstance(@SuppressWarnings("unused") Frame frame) {
            return hasRootInstanceSlowPath();
        }

        @TruffleBoundary
        private boolean hasRootInstanceSlowPath() {
            return InstrumentContext.get(null).callFunctions.callTargets.containsKey(getRootNode().getName());
        }

        @ExportMessage
        final Object getRootInstance(@SuppressWarnings("unused") Frame frame) throws UnsupportedMessageException {
            return getRootInstanceSlowPath();
        }

        @TruffleBoundary
        private Object getRootInstanceSlowPath() throws UnsupportedMessageException {
            Object function = InstrumentContext.get(null).callFunctions.findFunction(getRootNode().getName());
            if (function != null) {
                return function;
            }
            throw UnsupportedMessageException.create();
        }

        @ExportLibrary(InteropLibrary.class)
        @SuppressWarnings("static-method")
        static final class ArgumentsArrayObject implements TruffleObject {

            final Object[] args;

            ArgumentsArrayObject(Object[] args) {
                this.args = args;
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
            boolean isScope() {
                return true;
            }

            @ExportMessage
            boolean hasMembers() {
                return true;
            }

            @ExportMessage
            Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
                return new ArgumentNamesObject(args.length);
            }

            @ExportMessage
            @TruffleBoundary
            boolean isMemberReadable(String member) {
                try {
                    int index = Integer.parseInt(member);
                    if (0 <= index && index < args.length) {
                        return InteropLibrary.isValidValue(args[index]);
                    } else {
                        return false;
                    }
                } catch (NumberFormatException e) {
                    return false;
                }
            }

            @ExportMessage(name = "isMemberModifiable")
            @ExportMessage(name = "isMemberInsertable")
            boolean isMemberModifiable(@SuppressWarnings("unused") String member) {
                return false;
            }

            @ExportMessage
            @TruffleBoundary
            Object readMember(String member) throws UnsupportedMessageException {
                try {
                    int index = Integer.parseInt(member);
                    if (0 <= index && index < args.length) {
                        return args[index];
                    }
                } catch (NumberFormatException e) {
                }
                throw UnsupportedMessageException.create();
            }

            @ExportMessage
            void writeMember(@SuppressWarnings("unused") String member, @SuppressWarnings("unused") Object value) throws UnsupportedMessageException {
                throw UnsupportedMessageException.create();
            }

            @ExportMessage
            Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
                return "local";
            }
        }

        @ExportLibrary(InteropLibrary.class)
        static final class ArgumentNamesObject implements TruffleObject {

            private final int n;

            ArgumentNamesObject(int n) {
                this.n = n;
            }

            @SuppressWarnings("static-method")
            @ExportMessage
            boolean hasArrayElements() {
                return true;
            }

            @ExportMessage
            @TruffleBoundary
            long getArraySize() {
                return n;
            }

            @ExportMessage
            @TruffleBoundary
            Object readArrayElement(long index) throws InvalidArrayIndexException {
                if (!isArrayElementReadable(index)) {
                    throw InvalidArrayIndexException.create(index);
                }
                return Long.toString(index);
            }

            @ExportMessage
            @TruffleBoundary
            boolean isArrayElementReadable(long index) {
                return index >= 0 && index < n;
            }
        }

        @ExportLibrary(InteropLibrary.class)
        static final class VariablesWithThis implements TruffleObject {

            private final Object variables;
            private final Object receiver;

            VariablesWithThis(Object variables, Object receiver) {
                this.variables = variables;
                this.receiver = receiver;
            }

            @ExportMessage
            @SuppressWarnings("static-method")
            boolean hasLanguage() {
                return true;
            }

            @ExportMessage
            @SuppressWarnings("static-method")
            Class<? extends TruffleLanguage<?>> getLanguage() {
                return InstrumentationTestLanguage.class;
            }

            @ExportMessage
            @SuppressWarnings("static-method")
            boolean isScope() {
                return true;
            }

            @ExportMessage
            boolean hasScopeParent(@Shared("interop") @CachedLibrary(limit = "1") InteropLibrary interopLibrary) {
                return interopLibrary.hasScopeParent(variables);
            }

            @ExportMessage
            Object getScopeParent(@Shared("interop") @CachedLibrary(limit = "1") InteropLibrary interopLibrary) throws UnsupportedMessageException {
                return interopLibrary.getScopeParent(variables);
            }

            @ExportMessage
            @SuppressWarnings("static-method")
            boolean hasMembers() {
                return true;
            }

            @ExportMessage
            Object getMembers(boolean includeInternal, @Shared("interop") @CachedLibrary(limit = "1") InteropLibrary interopLibrary) throws UnsupportedMessageException {
                return new MembersWithReceiver(interopLibrary.getMembers(variables, includeInternal));
            }

            @ExportMessage
            Object readMember(String member, @Shared("interop") @CachedLibrary(limit = "1") InteropLibrary interopLibrary) throws UnknownIdentifierException, UnsupportedMessageException {
                if (THIS.equals(member)) {
                    return receiver;
                }
                return interopLibrary.readMember(variables, member);
            }

            @ExportMessage
            boolean isMemberReadable(String member, @Shared("interop") @CachedLibrary(limit = "1") InteropLibrary interopLibrary) {
                if (THIS.equals(member)) {
                    assert !interopLibrary.isMemberReadable(variables, member);
                    return true;
                }
                return interopLibrary.isMemberReadable(variables, member);
            }

            @ExportMessage
            void writeMember(String member, Object value, @Shared("interop") @CachedLibrary(limit = "1") InteropLibrary interopLibrary)
                            throws UnknownIdentifierException, UnsupportedTypeException, UnsupportedMessageException {
                if (THIS.equals(member)) {
                    throw UnknownIdentifierException.create(member);
                }
                interopLibrary.writeMember(variables, member, value);
            }

            @ExportMessage
            boolean isMemberModifiable(String member, @Shared("interop") @CachedLibrary(limit = "1") InteropLibrary interopLibrary) {
                if (THIS.equals(member)) {
                    return false;
                }
                return interopLibrary.isMemberModifiable(variables, member);
            }

            @ExportMessage
            @SuppressWarnings({"static-method", "unused"})
            boolean isMemberInsertable(String member) {
                return false;
            }

            @ExportMessage
            Object toDisplayString(boolean allowSideEffects, @Shared("interop") @CachedLibrary(limit = "1") InteropLibrary interopLibrary) {
                return interopLibrary.toDisplayString(variables, allowSideEffects);
            }

            @ExportMessage
            boolean hasSourceLocation(@Shared("interop") @CachedLibrary(limit = "1") InteropLibrary interopLibrary) {
                return interopLibrary.hasSourceLocation(variables);
            }

            @ExportMessage
            SourceSection getSourceLocation(@Shared("interop") @CachedLibrary(limit = "1") InteropLibrary interopLibrary) throws UnsupportedMessageException {
                return interopLibrary.getSourceLocation(variables);
            }

            @ExportLibrary(InteropLibrary.class)
            final class MembersWithReceiver implements TruffleObject {

                private final Object members;

                private MembersWithReceiver(Object members) {
                    this.members = members;
                }

                @ExportMessage
                boolean hasArrayElements() {
                    return true;
                }

                @ExportMessage
                Object readArrayElement(long index, @Shared("interop") @CachedLibrary(limit = "1") InteropLibrary interopLibrary) throws UnsupportedMessageException, InvalidArrayIndexException {
                    if (index == 0) {
                        return THIS;
                    } else {
                        return interopLibrary.readArrayElement(members, index - 1);
                    }
                }

                @ExportMessage
                long getArraySize(@Shared("interop") @CachedLibrary(limit = "1") InteropLibrary interopLibrary) throws UnsupportedMessageException {
                    return 1 + interopLibrary.getArraySize(members);
                }

                @ExportMessage
                boolean isArrayElementReadable(long index, @Shared("interop") @CachedLibrary(limit = "1") InteropLibrary interopLibrary) {
                    if (index == 0) {
                        return true;
                    } else {
                        return interopLibrary.isArrayElementReadable(members, index - 1);
                    }
                }
            }
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
        @Child InteropLibrary interop;

        TryCatchNode(BaseNode[] children) {
            super();
            BaseNode[] tryNodes = selectTryBlock(children);
            int tn = tryNodes.length;
            int cn = children.length - tn;
            catchNodes = new CatchNode[cn];
            System.arraycopy(children, tn, catchNodes, 0, cn);
            tryNode = new TryNode(tryNodes, catchNodes);
            interop = InteropLibrary.getFactory().createDispatched(5);
        }

        private TryCatchNode(InstrumentedNode tryNode, CatchNode[] catchNodes) {
            this.tryNode = tryNode;
            this.catchNodes = catchNodes;
            this.interop = InteropLibrary.getFactory().createDispatched(5);
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
                if (interop.isException(ex)) {
                    Object meta = null;
                    if (interop.hasMetaObject(ex)) {
                        try {
                            meta = interop.getMetaObject(ex);
                        } catch (UnsupportedMessageException e) {
                            throw CompilerDirectives.shouldNotReachHere(e);
                        }
                    }
                    for (CatchNode cn : catchNodes) {
                        if (isExceptionType(ex, meta, cn.getExceptionName())) {
                            if (cn.slot == null) {
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                cn.slot = frame.getFrameDescriptor().findOrAddAuxiliarySlot(cn.getExceptionVariableName());
                            }
                            frame.setAuxiliarySlot(cn.slot, ex);
                            return cn.execute(frame);
                        }
                    }
                }
                throw ex;
            }
        }

        boolean isExceptionType(Object value, Object metaObject, String exceptionTypeName) {
            try {
                Object actualTypeName;
                if (metaObject != null) {
                    actualTypeName = interop.getMetaSimpleName(metaObject);
                } else if (interop.isString(value)) {
                    actualTypeName = value;
                } else {
                    throw CompilerDirectives.shouldNotReachHere("invalid exception type");
                }
                return checkStringyType(interop.asString(actualTypeName), exceptionTypeName);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @TruffleBoundary
        private static boolean checkStringyType(String actualType, String exceptionName) {
            return actualType.startsWith(exceptionName);
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new TryCatchNode(cloneUninitialized(tryNode, materializedTags), cloneUninitialized(catchNodes, materializedTags));
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
                    InteropLibrary interop = InteropLibrary.getUncached();
                    if (interop.isString(arguments[0])) {
                        String type;
                        try {
                            type = interop.asString(arguments[0]);
                        } catch (UnsupportedMessageException ume) {
                            throw CompilerDirectives.shouldNotReachHere(ume);
                        }
                        for (CatchNode c : catches) {
                            if (type.startsWith(c.getExceptionName())) {
                                return true;
                            }
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

        @CompilationFinal private Integer slot;
        private final String exceptionName;
        private final String exceptionVariableName;

        CatchNode(String exceptionName, String exceptionVariableName, BaseNode[] children) {
            super(children);
            this.exceptionName = exceptionName;
            this.exceptionVariableName = exceptionVariableName;
        }

        CatchNode() {
            super(null);
            this.exceptionName = null;
            this.exceptionVariableName = null;
        }

        String getExceptionName() {
            return exceptionName;
        }

        public String getExceptionVariableName() {
            return exceptionVariableName;
        }

        @Override
        public WrapperNode createWrapper(ProbeNode probe) {
            return new CatchNodeWrapper(this, probe);
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new CatchNode(exceptionName, exceptionVariableName, cloneUninitialized(children, materializedTags));
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
        private RuntimeException createException() {
            // Internal exceptions are normal Java exception for which the
            // InteropLibrary#isException returns false
            return "internal".equals(type) ? new RuntimeException(message) : new TestLanguageException(type, message, this);
        }

        @ExportLibrary(InteropLibrary.class)
        public static class TestLanguageException extends AbstractTruffleException {

            private static final long serialVersionUID = 2709459650157465163L;

            private final String type;

            TestLanguageException(String type, String message, ThrowNode throwNode) {
                super(message, throwNode);
                this.type = type;
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
            ExceptionType getExceptionType() {
                return ExceptionType.RUNTIME_ERROR;
            }

            @ExportMessage
            @SuppressWarnings("unused")
            Object toDisplayString(boolean allowSideEffects) {
                return asString();
            }

            @ExportMessage
            boolean isString() {
                return true;
            }

            @ExportMessage
            @TruffleBoundary
            String asString() {
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

        private final String identifier;
        private final BaseNode[] children;
        private final SourceSection source;

        private RootCallTarget target;

        DefineNode(String identifier, SourceSection source, BaseNode[] children) {
            this.source = source;
            this.identifier = identifier;
            this.children = children;

            String code = source.getCharacters().toString();
            int index = code.indexOf('(') + 1;
            index = code.indexOf(',', index) + 1;
            SourceSection functionSection = source.getSource().createSection(source.getCharIndex() + index, source.getCharLength() - index - 1);
            this.target = new InstrumentationTestRootNode(InstrumentationTestLanguage.get(this), identifier, functionSection, children).getCallTarget();
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
            InstrumentContext context = InstrumentContext.get(this);
            synchronized (context.callFunctions.callTargets) {
                if (context.callFunctions.callTargets.containsKey(identifier)) {
                    if (context.callFunctions.callTargets.get(identifier) != target) {
                        throw new IllegalArgumentException("Identifier redefinition not supported.");
                    }
                }
                context.callFunctions.callTargets.put(this.identifier, target);
            }
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new DefineNode(identifier, source, cloneUninitialized(children, materializedTags));
        }
    }

    static class ContextNode extends BaseNode {

        /**
         * Children are only allowed because of the shared context policy.
         */
        @Children private final BaseNode[] children;

        ContextNode(BaseNode[] children) {
            this.children = children;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            InstrumentationTestLanguage outerLanguage = InstrumentationTestLanguage.get(this);
            TruffleContext inner = createInnerContext();
            Object prev = inner.enter(this);
            try {
                if (InstrumentationTestLanguage.get(null) == outerLanguage) {
                    return executeImpl(frame, children);
                } else {
                    /*
                     * If there is no code sharing we need to make sure we do not perform invalid
                     * sharing between contexts. We recreate a call target for each inner language
                     * to avoid this.
                     */
                    return lookupAsCallTarget().call();
                }
            } finally {
                inner.leave(this, prev);
                inner.close();
            }
        }

        @ExplodeLoop
        private static Object executeImpl(VirtualFrame f, BaseNode[] c) {
            Object v = Null.INSTANCE;
            for (BaseNode child : c) {
                if (child != null) {
                    v = child.execute(f);
                }
                /*
                 * Normally we would go through call target here which guarantees poll at the end.
                 * This language doesn't use call target here, for simplicity, so we need to poll
                 * manually.
                 */
                TruffleSafepoint.pollHere(child);
            }
            return v;
        }

        @TruffleBoundary
        private CallTarget lookupAsCallTarget() {
            Map<Object, CallTarget> codeCache = InstrumentationTestLanguage.get(null).codeCache;
            CallTarget target = codeCache.get(children);
            if (target == null) {
                target = new RootNode(InstrumentationTestLanguage.get(null)) {
                    @Children private final BaseNode[] innerChildren = BaseNode.cloneUninitialized(children, null);

                    @Override
                    public Object execute(VirtualFrame f) {
                        return executeImpl(f, innerChildren);
                    }

                    @Override
                    public boolean isInternal() {
                        return false;
                    }

                    @Override
                    public SourceSection getSourceSection() {
                        return ContextNode.this.getSourceSection();
                    }

                }.getCallTarget();
                codeCache.put(children, target);
            }
            return target;
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new ContextNode(cloneUninitialized(children, materializedTags));
        }

        @TruffleBoundary
        private static TruffleContext createInnerContext() {
            return InstrumentContext.get(null).env.newInnerContextBuilder().inheritAllAccess(true).initializeCreatorContext(true).build();
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
                CallTarget target = InstrumentContext.get(this).callFunctions.callTargets.get(identifier);
                callNode = insert(Truffle.getRuntime().createDirectCallNode(target));
            }
            Object[] arguments = new Object[children.length];
            for (int i = 0; i < children.length; i++) {
                arguments[i] = children[i].execute(frame);
            }
            return callNode.call(arguments);
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new CallNode(identifier, cloneUninitialized(children, materializedTags));
        }
    }

    private static class CallCloneNode extends InstrumentedNode {

        @Child private DirectCallNode callNode;
        private final String identifier;

        CallCloneNode(String identifier, BaseNode[] children) {
            super(children);
            this.identifier = identifier;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                CallTarget target = InstrumentContext.get(this).callFunctions.callTargets.get(identifier);
                callNode = insert(Truffle.getRuntime().createDirectCallNode(target));
            }
            if (!callNode.isCallTargetCloningAllowed()) {
                return false;
            }
            CallTarget target = getClonedTarget();
            Object[] arguments = new Object[children.length];
            for (int i = 0; i < children.length; i++) {
                arguments[i] = children[i].execute(frame);
            }
            return target.call(arguments);
        }

        @TruffleBoundary
        private CallTarget getClonedTarget() {
            callNode.cloneCallTarget();
            return callNode.getClonedCallTarget();
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new CallNode(identifier, cloneUninitialized(children, materializedTags));
        }
    }

    private static class ReturnNode extends InstrumentedNode {

        private final String identifier;

        ReturnNode(String identifier) {
            this.identifier = identifier;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            InstrumentContext ctx = InstrumentContext.get(this);
            return returnFunction(ctx);
        }

        @TruffleBoundary
        private Object returnFunction(InstrumentContext ctx) {
            return ctx.callFunctions.findFunction(identifier);
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new ReturnNode(identifier);
        }
    }

    static class ExitNode extends InstrumentedNode {
        private final int exitCode;

        ExitNode(String exitCodeStr) {
            this.exitCode = Integer.parseInt(exitCodeStr);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            throwExitException();
            return null;
        }

        @TruffleBoundary
        private void throwExitException() {
            InstrumentContext context = InstrumentContext.get(this);
            context.env.getContext().closeExited(this, exitCode);
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new ExitNode(String.valueOf(exitCode));
        }
    }

    static class CancelNode extends InstrumentedNode {

        CancelNode() {
        }

        @Override
        public Object execute(VirtualFrame frame) {
            throwCancelException();
            return null;
        }

        @TruffleBoundary
        private void throwCancelException() {
            TestAPIAccessor.engineAccess().getCurrentCreatorTruffleContext().closeCancelled(this, null);
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new CancelNode();
        }
    }

    private static class SpawnNode extends InstrumentedNode {

        private final String identifier;

        SpawnNode(String identifier, BaseNode[] children) {
            super(children);
            this.identifier = identifier;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            spawnCall();
            return Null.INSTANCE;
        }

        @TruffleBoundary
        private void spawnCall() {
            InstrumentationTestLanguage language = InstrumentationTestLanguage.get(this);
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
            final InstrumentContext context = InstrumentContext.get(this);
            Thread t = context.env.newTruffleThreadBuilder(new Runnable() {
                @Override
                public void run() {
                    RootCallTarget target = InstrumentContext.get(null).callFunctions.callTargets.get(identifier);
                    if (asyncInfo != null) {
                        target.call(new Object[]{asyncInfo});
                    } else {
                        target.call(new Object[0]);
                    }
                }
            }).build();
            t.setUncaughtExceptionHandler(getPolyglotThreadUncaughtExceptionHandler(context));
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

    private static Thread.UncaughtExceptionHandler getPolyglotThreadUncaughtExceptionHandler(InstrumentContext context) {
        return (t, e) -> {
            InteropLibrary interop = InteropLibrary.getUncached();
            boolean interrupted;
            boolean cancelledOrExited = false;
            if (interop.isException(e)) {
                try {
                    ExceptionType exceptionType = interop.getExceptionType(e);
                    interrupted = exceptionType == ExceptionType.INTERRUPT;
                } catch (UnsupportedMessageException ume) {
                    throw CompilerDirectives.shouldNotReachHere(ume);
                }
            } else {
                interrupted = e != null && e.getCause() instanceof InterruptedException;
                cancelledOrExited = e instanceof ThreadDeath;
            }
            if (e != null && !interrupted && !cancelledOrExited) {
                Env currentEnv = context.env;
                try {
                    e.printStackTrace(new PrintStream(currentEnv.err()));
                } catch (Throwable exc) {
                    // Still show the original error if printing on Env.err() fails for some
                    // reason
                    e.printStackTrace();
                }
            }
        };
    }

    static final class AsyncStackInfo {

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

        @Override
        public String toString() {
            return createStackTraceElements().stream().map(tste -> tste.getTarget().toString()).collect(Collectors.joining(", ", "AsyncStackInfo[", "]"));
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
            InstrumentationTestLanguage.joinSpawnedThreads(InstrumentContext.get(this), false);
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new JoinNode(cloneUninitialized(children, materializedTags));
        }
    }

    private static void joinSpawnedThreads(InstrumentContext context, boolean noInterrupt) {
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
                    if (!noInterrupt) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        } while (!threads.isEmpty());
    }

    private static class AsyncCallNode extends InstrumentedNode {

        private final CallTarget resumeCallTarget;

        AsyncCallNode(CallTarget resumeCallTarget) {
            this.resumeCallTarget = resumeCallTarget;
        }

        static AsyncCallNode create(String identifier, BaseNode[] children, SourceSection callSourceSection) {
            SourceSection sourceSectionAsInternal = Source.newBuilder(callSourceSection.getSource()).internal(true).build().createSection(
                            callSourceSection.getCharIndex(), callSourceSection.getCharLength());
            FunctionRootNode functionRoot = new FunctionRootNode(new BaseNode[]{new CallNode(identifier, children)});
            CallTarget resumeCallTarget = new InstrumentationTestRootNode(
                            InstrumentationTestLanguage.get(null),
                            inferName(callSourceSection, identifier),
                            sourceSectionAsInternal,
                            functionRoot).getCallTarget();
            return new AsyncCallNode(resumeCallTarget);
        }

        private static String inferName(SourceSection sourceSection, String calleeIdent) {
            String sourceText = sourceSection.getSource().getCharacters().toString();
            String identPrefix = "DEFINE(";
            String identSuffix = ",";
            int identBegin = sourceText.lastIndexOf(identPrefix, sourceSection.getCharIndex());
            if (identBegin >= 0) {
                identBegin += identPrefix.length();
                int identEnd = sourceText.indexOf(identSuffix, identBegin);
                if (identEnd >= 0) {
                    String callerIdent = sourceText.substring(identBegin, identEnd);
                    return callerIdent + "->" + calleeIdent;
                }
            }
            return "ASYNC_CALL_RESUME";
        }

        @Override
        public Object execute(VirtualFrame frame) {
            // enqueue and suspend
            enqueueAwaitCall();
            return Null.INSTANCE;
        }

        @TruffleBoundary
        private void enqueueAwaitCall() {
            AsyncStackInfo asyncInfo = new AsyncStackInfo();
            asyncInfo.addCall(this, null);
            InstrumentationTestLanguage language = InstrumentationTestLanguage.get(this);
            InstrumentContext context = InstrumentContext.get(this);
            int asyncDepth = language.getAsynchronousStackDepth();
            if (asyncDepth > 1) {
                // Add full (sync) stack trace up to asyncDepth.
                Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Boolean>() {
                    @Override
                    public Boolean visitFrame(FrameInstance frameInstance) {
                        Node node = frameInstance.getCallNode();
                        if (node == null) {
                            return null;
                        }
                        Frame frame = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                        asyncInfo.addCall(node, frame);
                        boolean done = asyncInfo.callNodes.size() >= asyncDepth;
                        return done ? Boolean.FALSE : null;
                    }
                });
            } else {
                assert asyncInfo.previous == null : asyncInfo.previous;
                // At least try to link the previous AsyncStackInfo (may be null).
                asyncInfo.previous = context.previousAsyncCallInfo;
                context.previousAsyncCallInfo = null;
            }
            // Enqueue resumption.
            context.asyncCallFifoQueue.add(Pair.create(resumeCallTarget, asyncInfo));
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new AsyncCallNode(resumeCallTarget);
        }
    }

    private static class AsyncResumeNode extends InstrumentedNode {

        private final String identifier;
        @Child private IndirectCallNode callNode = IndirectCallNode.create();

        AsyncResumeNode(String identifier, BaseNode[] children) {
            super(children);
            this.identifier = identifier;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            // dequeue and resume
            var pair = dequeueAwaitCall(identifier);
            CallTarget resumeCallTarget = pair.getLeft();
            AsyncStackInfo asyncStackInfo = pair.getRight();
            return callNode.call(resumeCallTarget, asyncStackInfo);
        }

        @TruffleBoundary
        private Pair<CallTarget, AsyncStackInfo> dequeueAwaitCall(String expectedIdentifier) {
            InstrumentContext context = InstrumentContext.get(this);
            var pair = context.asyncCallFifoQueue.poll();
            Assert.assertNotNull("Expected ASYNC_CALL(" + expectedIdentifier + ") in the queue", pair);
            context.previousAsyncCallInfo = pair.getRight();
            return pair;
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new AsyncResumeNode(identifier, cloneUninitialized(children, materializedTags));
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
                    CallTarget target = InstrumentContext.get(this).callFunctions.callTargets.get(identifier);
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
                CallTarget target = InstrumentContext.get(this).callFunctions.callTargets.get(identifier);
                callNode = insert(Truffle.getRuntime().createDirectCallNode(target));
            }
            Object[] arguments;
            if (children.length == 0) {
                arguments = thisArg;
            } else {
                arguments = new Object[1 + children.length];
                arguments[0] = thisArg[0];
                for (int i = 0; i < children.length; i++) {
                    arguments[i + 1] = children[i].execute(frame);
                }
            }
            Object retval = callNode.call(arguments);
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
            AllocationReporter reporter = InstrumentContext.get(this).allocationReporter;
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
                throw new Interrupted();
            }
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new SleepNode(timeToSleep, cloneUninitialized(children, materializedTags));
        }
    }

    @SuppressWarnings("serial")
    @ExportLibrary(InteropLibrary.class)
    static class Interrupted extends AbstractTruffleException {

        @ExportMessage
        public ExceptionType getExceptionType() {
            return ExceptionType.INTERRUPT;
        }

    }

    private static class ConstantNode extends InstrumentedNode {

        private final Object constant;

        ConstantNode(String identifier, boolean quoted, BaseNode[] children) {
            super(children);
            this.constant = quoted ? identifier : parseIdent(identifier);
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
                MaterializedChildStatementAndExpressionNode materializedNode = new MaterializedChildStatementAndExpressionNode(this, getSourceSection(), replacementForSkippedExpressions,
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
        /*
         * Keep the reference to the original node this node is materialization of in order for it
         * not to be collected by GC so that it is still kept in retired nodes in the instrumented
         * AST.
         */
        private final Node retiredNode;

        MaterializedChildStatementAndExpressionNode(Node retiredNode, SourceSection sourceSection, BaseNode[] expressions, BaseNode[] children) {
            super(children);
            this.retiredNode = retiredNode;
            this.statementNode = new StatementNode(expressions);
            this.statementNode.setSourceSection(sourceSection);
        }

        MaterializedChildStatementAndExpressionNode(Node retiredNode, InstrumentedNode statementNode, BaseNode[] children) {
            super(children);
            this.statementNode = statementNode;
            this.retiredNode = retiredNode;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            statementNode.execute(frame);
            return super.execute(frame);
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new MaterializedChildStatementAndExpressionNode(retiredNode, cloneUninitialized(statementNode, materializedTags), cloneUninitialized(children, materializedTags));
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

        @CompilationFinal private Integer slot;
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
                slot = frame.getFrameDescriptor().findOrAddAuxiliarySlot(name);
            }
            frame.setAuxiliarySlot(slot, value);
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

    private static final class ReadVariableNode extends InstrumentedNode {

        private final String name;
        @CompilationFinal private Integer slot;

        private ReadVariableNode(String name, BaseNode[] children) {
            super(children);
            this.name = name;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (slot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                slot = frame.getFrameDescriptor().findOrAddAuxiliarySlot(name);
                if (slot == null) {
                    throw new IllegalStateException("Unknown variable " + name);
                }
            }
            super.execute(frame);
            return frame.getAuxiliarySlot(slot);
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new ReadVariableNode(name, cloneUninitialized(children, materializedTags));
        }
    }

    private static final class ExecuteMemberNode extends InstrumentedNode {

        private final String memberName;
        @Child InteropLibrary interop;

        private ExecuteMemberNode(String memberName, BaseNode[] children) {
            super(children);
            this.memberName = memberName;
            interop = InteropLibrary.getFactory().createDispatched(5);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object obj = super.execute(frame);
            try {
                return interop.invokeMember(obj, memberName);
            } catch (UnsupportedMessageException | ArityException | UnknownIdentifierException | UnsupportedTypeException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new ExecuteMemberNode(memberName, cloneUninitialized(children, materializedTags));
        }
    }

    private static class ArgumentNode extends InstrumentedNode {

        private final String name;

        @CompilationFinal private Integer slot;
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
                slot = frame.getFrameDescriptor().findOrAddAuxiliarySlot(name);
            }
            Object[] args = frame.getArguments();
            Object value;
            if (args.length <= index) {
                value = Null.INSTANCE;
            } else {
                if (args[0] instanceof ThisArg) {
                    value = args[index + 1];
                } else {
                    value = args[index];
                }
            }
            frame.setAuxiliarySlot(slot, value);
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

        @CompilationFinal Integer loopIndexSlot;
        @CompilationFinal Integer loopResultSlot;

        WhileLoopNode(Object loopCount, BaseNode[] children) {
            this.loop = Truffle.getRuntime().createLoopNode(new LoopConditionNode(loopCount, children));
        }

        WhileLoopNode(int loopCount, boolean infinite, BaseNode[] children) {
            this.loop = Truffle.getRuntime().createLoopNode(new LoopConditionNode(loopCount, infinite, children));
        }

        Integer getLoopIndex() {
            if (loopIndexSlot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                loopIndexSlot = getRootNode().getFrameDescriptor().findOrAddAuxiliarySlot("loopIndex" + getLoopDepth());
            }
            return loopIndexSlot;
        }

        Integer getResult() {
            if (loopResultSlot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                loopResultSlot = getRootNode().getFrameDescriptor().findOrAddAuxiliarySlot("loopResult" + getLoopDepth());
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
            frame.setAuxiliarySlot(getResult(), Null.INSTANCE);
            frame.setAuxiliarySlot(getLoopIndex(), 0);
            loop.execute(frame);
            try {
                return frame.getAuxiliarySlot(loopResultSlot);
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
                    i = (int) frame.getAuxiliarySlot(loopIndexSlot);
                } catch (ClassCastException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new AssertionError(e);
                }
                if (infinite || i < loopCount) {
                    Object resultValue = super.execute(frame);
                    frame.setAuxiliarySlot(loopIndexSlot, i + 1);
                    frame.setAuxiliarySlot(loopResultSlot, resultValue);
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
        @Child InteropLibrary interop;
        @CompilationFinal private PrintWriter writer;

        PrintNode(String where, BaseNode[] children) {
            super(children);
            this.where = Output.valueOf(where);
            interop = InteropLibrary.getFactory().createDispatched(5);
        }

        PrintNode(Output where, BaseNode[] children) {
            super(children);
            this.where = where;
            interop = InteropLibrary.getFactory().createDispatched(5);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if (writer == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                InstrumentContext context = InstrumentContext.get(this);
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
            Object what = super.execute(frame);
            if (interop.isString(what)) {
                try {
                    writeAndFlush(writer, interop.asString(what));
                } catch (UnsupportedMessageException ume) {
                    CompilerDirectives.shouldNotReachHere(ume);
                }
            }
            return Null.INSTANCE;
        }

        @TruffleBoundary
        private static void writeAndFlush(PrintWriter writer, String what) {
            writer.write(what);
            writer.flush();
        }

        @Override
        protected BaseNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
            return new PrintNode(where, cloneUninitialized(children, materializedTags));
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
    protected Object getScope(InstrumentContext context) {
        return context.callFunctions;
    }

    @Override
    protected Object getLanguageView(InstrumentContext context, Object value) {
        return new InstrumentationLanguageView(value);
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

        final Map<String, RootCallTarget> callTargets = Collections.synchronizedMap(new LinkedHashMap<>());
        final Map<String, TruffleObject> functions = new ConcurrentHashMap<>();

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
                functions.putIfAbsent(name, functionObject);
            }
            return functionObject;
        }

        @ExportMessage
        boolean isScope() {
            return true;
        }

        @ExportMessage
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        @TruffleBoundary
        final Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            synchronized (callTargets) {
                return new KeysObject(callTargets.keySet().toArray(new String[0]));
            }
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
            return "global";
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
            return "Object";
        }

        @ExportMessage
        @TruffleBoundary
        Object getMetaObject() {
            return new InstrumentationMetaObject(this, getTypeName());
        }

    }

    private static class StringBuilderWrapper {
        private final StringBuilder delegate = new StringBuilder();

        @TruffleBoundary
        StringBuilderWrapper append(String s) {
            delegate.append(s);
            return this;
        }

        @TruffleBoundary
        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    @TruffleBoundary
    private static String toString(Object object) {
        return object.toString();
    }

    @TruffleBoundary
    private static String classSimpleName(Class<?> clazz) {
        return clazz.getSimpleName();
    }

    public static final class SpecialServiceImpl implements SpecialService {
        @Override
        public String fileExtension() {
            return FILENAME_EXTENSION;
        }
    }

    @Override
    protected void finalizeContext(InstrumentContext context) {
        joinSpawnedThreads(context, true);
    }

    @Override
    protected void exitContext(InstrumentContext context, ExitMode exitMode, int exitCode) {
        if (exitMode == ExitMode.HARD) {
            CallTarget callTarget = context.env.parseInternal(Source.newBuilder(ID, "CONSTANT(42)", "ConstandFortyTwo").build());
            Object result = callTarget.call();
            Assert.assertEquals(42, result);
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
    final Queue<Pair<CallTarget, InstrumentationTestLanguage.AsyncStackInfo>> asyncCallFifoQueue = new ArrayDeque<>();
    AsyncStackInfo previousAsyncCallInfo;

    InstrumentContext(Env env, Source initSource, Boolean runInitAfterExec) {
        this.env = env;
        this.out = env.out();
        this.err = env.err();
        this.allocationReporter = env.lookup(AllocationReporter.class);
        this.initSource = initSource;
        this.runInitAfterExec = runInitAfterExec != null && runInitAfterExec;
    }

    private static final ContextReference<InstrumentContext> REFERENCE = ContextReference.create(InstrumentationTestLanguage.class);

    static InstrumentContext get(Node node) {
        return REFERENCE.get(node);
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
