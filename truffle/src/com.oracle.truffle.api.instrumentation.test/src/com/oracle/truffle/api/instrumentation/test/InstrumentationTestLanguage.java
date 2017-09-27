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
import java.io.PrintWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTest.ReturnLanguageEnv;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.BlockNode;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.DefineNode;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage.ExpressionNode;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
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
 * <p>
 * Other statements are:
 * <ul>
 * <li><code>ARGUMENT(name)</code> - copies a frame argument to the named slot</li>
 * <li><code>VARIABLE(name, value)</code> - defines a variable</li>
 * <li><code>CONSTANT(value)</code> - defines a constant value</li>
 * <li><code>PRINT(OUT, text)</code> or <code>PRINT(ERR, text)</code> - prints a text to standard
 * output resp. error output.</li>
 * </ul>
 * </p>
 */
@Registration(id = InstrumentationTestLanguage.ID, mimeType = InstrumentationTestLanguage.MIME_TYPE, name = "InstrumentTestLang", version = "2.0")
@ProvidedTags({ExpressionNode.class, DefineNode.class, LoopNode.class,
                StandardTags.StatementTag.class, StandardTags.CallTag.class, StandardTags.RootTag.class, BlockNode.class, StandardTags.RootTag.class})
public class InstrumentationTestLanguage extends TruffleLanguage<Context>
                implements SpecialService {

    public static final String ID = "instrumentation-test-language";
    public static final String MIME_TYPE = "application/x-truffle-instrumentation-test-language";
    public static final String FILENAME_EXTENSION = ".titl";

    public static final Class<?> EXPRESSION = ExpressionNode.class;
    public static final Class<?> DEFINE = DefineNode.class;
    public static final Class<?> LOOP = LoopNode.class;
    public static final Class<?> STATEMENT = StandardTags.StatementTag.class;
    public static final Class<?> CALL = StandardTags.CallTag.class;
    public static final Class<?> ROOT = StandardTags.RootTag.class;
    public static final Class<?> BLOCK = BlockNode.class;

    public static final Class<?>[] TAGS = new Class<?>[]{EXPRESSION, DEFINE, LOOP, STATEMENT, CALL, BLOCK, ROOT};
    public static final String[] TAG_NAMES = new String[]{"EXPRESSION", "DEFINE", "LOOP", "STATEMENT", "CALL", "BLOCK", "ROOT", "CONSTANT", "VARIABLE", "ARGUMENT", "PRINT"};

    // used to test that no getSourceSection calls happen in certain situations
    private static int rootSourceSectionQueryCount;

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
    public static Map<String, Object> envConfig;

    @Override
    protected Context createContext(TruffleLanguage.Env env) {
        Source initSource = null;
        Boolean runInitAfterExec = null;
        if (envConfig != null) {
            Object envReturner = envConfig.get(ReturnLanguageEnv.KEY);
            if (envReturner != null) {
                ((ReturnLanguageEnv) envReturner).env = env;
            }
            org.graalvm.polyglot.Source initPolyglotSource = (org.graalvm.polyglot.Source) envConfig.get("initSource");
            if (initPolyglotSource != null) {
                initSource = AbstractInstrumentationTest.sourceToImpl(initPolyglotSource);
            }
            runInitAfterExec = (Boolean) envConfig.get("runInitAfterExec");
        }
        return new Context(env.out(), env.err(), env.lookup(AllocationReporter.class), initSource, runInitAfterExec);
    }

    @Override
    protected void initializeContext(Context context) throws Exception {
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
            if (tag.equals("DEFINE") || tag.equals("ARGUMENT") || tag.equals("CALL") || tag.equals("LOOP") || tag.equals("CONSTANT")) {
                numberOfIdents = 1;
            } else if (tag.equals("VARIABLE") || tag.equals("PRINT")) {
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
            if (tag.equals("ARGUMENT")) {
                ((ArgumentNode) node).setIndex(argumentIndex++);
            }
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

        private BaseNode createNode(String tag, String[] idents, SourceSection sourceSection, BaseNode[] childArray) throws AssertionError {
            switch (tag) {
                case "DEFINE":
                    return new DefineNode(lang, idents[0], sourceSection, childArray);
                case "ARGUMENT":
                    return new ArgumentNode(idents[0], childArray);
                case "CALL":
                    return new CallNode(idents[0], childArray);
                case "LOOP":
                    return new LoopNode(parseIdent(idents[0]), childArray);
                case "BLOCK":
                    return new BlockNode(childArray);
                case "EXPRESSION":
                    return new ExpressionNode(childArray);
                case "ROOT":
                    return new FunctionRootNode(childArray);
                case "STATEMENT":
                    return new StatementNode(childArray);
                case "CONSTANT":
                    return new ConstantNode(idents[0], childArray);
                case "VARIABLE":
                    return new VariableNode(idents[0], idents[1], childArray, lang.getContextReference());
                case "PRINT":
                    return new PrintNode(idents[0], idents[1], childArray);
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
            this.functionRoot = new FunctionRootNode(expressions);
            functionRoot.setSourceSection(sourceSection);
        }

        @Override
        public SourceSection getSourceSection() {
            rootSourceSectionQueryCount++;
            return sourceSection;
        }

        @Override
        protected boolean isInstrumentable() {
            return true;
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
            Object returnValue = Null.INSTANCE;
            for (BaseNode child : children) {
                if (child != null) {
                    returnValue = child.execute(frame);
                }
            }
            return returnValue;
        }

        @Override
        protected final boolean isTaggedWith(Class<?> tag) {
            if (tag == StandardTags.RootTag.class) {
                return this instanceof FunctionRootNode;
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

    private static final class FunctionRootNode extends InstrumentedNode {

        FunctionRootNode(BaseNode[] children) {
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

        DefineNode(InstrumentationTestLanguage lang, String identifier, SourceSection source, BaseNode[] children) {
            this.identifier = identifier;
            String code = source.getCharacters().toString();
            int index = code.indexOf('(') + 1;
            while (Character.isWhitespace(code.charAt(index))) {
                index++;
            }
            SourceSection functionSection = source.getSource().createSection(source.getCharIndex() + index, source.getCharLength() - index - 1);
            this.target = Truffle.getRuntime().createCallTarget(new InstrumentationTestRootNode(lang, identifier, functionSection, children));
        }

        @Override
        public Object execute(VirtualFrame frame) {
            defineFunction();
            return Null.INSTANCE;
        }

        @TruffleBoundary
        private void defineFunction() {
            Context context = getRootNode().getLanguage(InstrumentationTestLanguage.class).getContextReference().get();
            if (context.callTargets.containsKey(identifier)) {
                if (context.callTargets.get(identifier) != target) {
                    throw new IllegalArgumentException("Identifier redefinition not supported.");
                }
            }
            context.callTargets.put(this.identifier, target);
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
                Context context = getRootNode().getLanguage(InstrumentationTestLanguage.class).getContextReference().get();
                CallTarget target = context.callTargets.get(identifier);
                callNode = insert(Truffle.getRuntime().createDirectCallNode(target));
            }
            return callNode.call(new Object[0]);
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

    private static final class VariableNode extends InstrumentedNode {

        private final String name;
        private final Object value;
        private final ContextReference<Context> contextRef;

        @CompilationFinal private FrameSlot slot;

        private VariableNode(String name, String value, BaseNode[] children, ContextReference<Context> contextRef) {
            super(children);
            this.name = name;
            this.value = parseIdent(value);
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
                Context context = getRootNode().getLanguage(InstrumentationTestLanguage.class).getContextReference().get();
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
    protected Object findExportedSymbol(Context context, String globalName, boolean onlyExplicit) {
        TruffleObject functionObject = context.callFunctionObjects.get(globalName);
        if (functionObject == null) {
            CallTarget ct = context.callTargets.get(globalName);
            if (ct == null) {
                return null;
            }
            functionObject = new Function(ct);
            context.callFunctionObjects.put(globalName, functionObject);
        }
        return functionObject;
    }

    @Override
    protected Object getLanguageGlobal(Context context) {
        return context;
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return false;
    }

    @Override
    protected Object findMetaObject(Context context, Object obj) {
        if (obj instanceof Integer || obj instanceof Long) {
            return "Integer";
        }
        if (obj instanceof Boolean) {
            return "Boolean";
        }
        if (obj != null && obj.equals(Double.POSITIVE_INFINITY)) {
            return "Infinity";
        }
        return null;
    }

    @Override
    protected SourceSection findSourceLocation(Context context, Object obj) {
        if (obj instanceof Integer || obj instanceof Long) {
            return Source.newBuilder("source integer").name("integer").mimeType(MIME_TYPE).build().createSection(1);
        }
        if (obj instanceof Boolean) {
            return Source.newBuilder("source boolean").name("boolean").mimeType(MIME_TYPE).build().createSection(1);
        }
        if (obj != null && obj.equals(Double.POSITIVE_INFINITY)) {
            return Source.newBuilder("source infinity").name("double").mimeType(MIME_TYPE).build().createSection(1);
        }
        return null;
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

}

class Context {

    final Map<String, CallTarget> callTargets = new HashMap<>();
    final Map<String, TruffleObject> callFunctionObjects = new HashMap<>();
    final OutputStream out;
    final OutputStream err;
    final AllocationReporter allocationReporter;
    final Source initSource;
    final boolean runInitAfterExec;
    RootCallTarget afterTarget;

    Context(OutputStream out, OutputStream err, AllocationReporter allocationReporter, Source initSource, Boolean runInitAfterExec) {
        this.out = out;
        this.err = err;
        this.allocationReporter = allocationReporter;
        this.initSource = initSource;
        this.runInitAfterExec = runInitAfterExec != null && runInitAfterExec;
    }
}
