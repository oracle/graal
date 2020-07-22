/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.parser;

import static com.oracle.truffle.api.test.parser.PELexer.CLEAR;
import static com.oracle.truffle.api.test.parser.PELexer.COMMA;
import static com.oracle.truffle.api.test.parser.PELexer.CR;
import static com.oracle.truffle.api.test.parser.PELexer.DIV;
import static com.oracle.truffle.api.test.parser.PELexer.END;
import static com.oracle.truffle.api.test.parser.PELexer.EQUALS;
import static com.oracle.truffle.api.test.parser.PELexer.GOSUB;
import static com.oracle.truffle.api.test.parser.PELexer.GOTO;
import static com.oracle.truffle.api.test.parser.PELexer.IF;
import static com.oracle.truffle.api.test.parser.PELexer.INPUT;
import static com.oracle.truffle.api.test.parser.PELexer.LARGER_THAN;
import static com.oracle.truffle.api.test.parser.PELexer.LESS_THAN;
import static com.oracle.truffle.api.test.parser.PELexer.LET;
import static com.oracle.truffle.api.test.parser.PELexer.LIST;
import static com.oracle.truffle.api.test.parser.PELexer.MINUS;
import static com.oracle.truffle.api.test.parser.PELexer.MUL;
import static com.oracle.truffle.api.test.parser.PELexer.NAME;
import static com.oracle.truffle.api.test.parser.PELexer.NUMBER;
import static com.oracle.truffle.api.test.parser.PELexer.PLUS;
import static com.oracle.truffle.api.test.parser.PELexer.PRINT;
import static com.oracle.truffle.api.test.parser.PELexer.RETURN;
import static com.oracle.truffle.api.test.parser.PELexer.RUN;
import static com.oracle.truffle.api.test.parser.PELexer.STRING;
import static com.oracle.truffle.api.test.parser.PELexer.THEN;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.test.parser.PELexer.LexerList;
import com.oracle.truffle.api.test.parser.PEParser.Function3;
import com.oracle.truffle.api.test.parser.PEParser.Function4;
import com.oracle.truffle.api.test.parser.PEParser.TokenFunction;

abstract class Element<T> extends Node {

    @CompilationFinal protected long firstA;
    @CompilationFinal protected long firstB;
    @CompilationFinal protected int singleToken = -1;

    protected abstract void createFirstSet(Element<?> setHolder, HashSet<Rule<?>> rulesAdded);

    public abstract void initialize();

    public abstract T consume(PELexer lexer);

    public final boolean canStartWith(byte token) {
        if (singleToken == -1L) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (Long.bitCount(firstA) + Long.bitCount(firstB) == 1) {
                // if the "first" set consists of a single token, it can be checked more efficiently
                if (firstA == 0) {
                    singleToken = Long.numberOfTrailingZeros(firstB) + 64;
                } else {
                    singleToken = Long.numberOfTrailingZeros(firstA);
                }
            } else {
                singleToken = 0;
            }
        }
        if (singleToken != 0) {
            return token == singleToken;
        }

        if (token < 64) {
            assert token > 0;
            return (firstA & (1L << token)) != 0;
        } else {
            assert token < 128;
            return (firstB & (1L << (token - 64))) != 0;
        }
    }
}

class RuleRootNode extends RootNode {

    @Child private Rule<?> rule;

    RuleRootNode(Rule<?> rule) {
        super(null);
        this.rule = rule;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        PELexer lexer = (PELexer) frame.getArguments()[0];
        return rule.element.consume(lexer);
    }

    @Override
    public String getName() {
        return "parser rule " + rule.getName();
    }

    @Override
    public String toString() {
        return getName();
    }
}

final class CallRule<T> extends Element<T> {

    private final Rule<T> rule;
    @Child DirectCallNode call;

    CallRule(Rule<T> rule) {
        this.rule = rule;
    }

    @Override
    protected void createFirstSet(Element<?> setHolder, HashSet<Rule<?>> rulesAdded) {
        rule.element.createFirstSet(setHolder, rulesAdded);
    }

    @Override
    public void initialize() {
        rule.initialize();
    }

    @SuppressWarnings("unchecked")
    @Override
    public T consume(PELexer lexer) {
        if (call == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            call = insert(Truffle.getRuntime().createDirectCallNode(rule.getCallTarget()));
        }
        if (PEParser.PEPARSER_DIRECT_CALL) {
            return rule.element.consume(lexer);
        } else {
            return (T) call.call(lexer.asArgumentsArray()); // do not create a new array every time
        }
    }
}

final class Rule<T> extends Element<T> {

    private final String name;
    @Child Element<T> element;
    CallTarget target;

    Rule(String name) {
        this.name = name;
    }

    public CallTarget getCallTarget() {
        if (target == null) {
            target = Truffle.getRuntime().createCallTarget(new RuleRootNode(this));
        }
        return target;
    }

    public void define(Element<T> newElement) {
        this.element = newElement;
    }

    @Override
    protected void createFirstSet(Element<?> setHolder, HashSet<Rule<?>> rulesAdded) {
        if (!rulesAdded.contains(this)) {
            rulesAdded.add(this);
            if (firstA != 0 || firstB != 0) {
                setHolder.firstA |= firstA;
                setHolder.firstB |= firstB;
            } else {
                element.createFirstSet(setHolder, rulesAdded);
            }
        }
    }

    void initializeRule() {
        CompilerAsserts.neverPartOfCompilation();
        createFirstSet(this, new HashSet<>());
    }

    @Override
    public void initialize() {
        // do nothing - already initialized
    }

    public void printFirst() {
        System.out.print(name + ": ");
        for (int i = 0; i < 128; i++) {
            if (canStartWith((byte) i)) {
                System.out.print(PELexer.tokenNames[i] + " ");
            }
        }
        System.out.println();
    }

    public String getName() {
        return name;
    }

    static int level = 0;

    @Override
    public T consume(PELexer lexer) {
        throw new IllegalStateException();
    }
}

abstract class SequenceBase<T> extends Element<T> {

    protected abstract Element<?>[] elements();

    @Override
    protected void createFirstSet(Element<?> setHolder, HashSet<Rule<?>> rulesAdded) {
        int i = 0;
        Element<?>[] elements = elements();
        while (i < elements.length && elements[i] instanceof OptionalElement<?, ?>) {
            // add all optional prefixes
            ((OptionalElement<?, ?>) elements[i]).element.createFirstSet(setHolder, rulesAdded);
            i++;
        }
        assert i < elements.length : "non-optional element needed in sequence";
        // add the first non-optional element
        elements[i].createFirstSet(setHolder, rulesAdded);
    }

    @Override
    public void initialize() {
        for (Element<?> element : elements()) {
            element.initialize();
        }
    }
}

final class Sequence2<T, A, B> extends SequenceBase<T> {
    @Child private Element<A> a;
    @Child private Element<B> b;
    private final BiFunction<A, B, T> action;

    Sequence2(BiFunction<A, B, T> action, Element<A> a, Element<B> b) {
        this.action = action;
        this.a = a;
        this.b = b;
    }

    @Override
    protected Element<?>[] elements() {
        return new Element<?>[]{a, b};
    }

    @Override
    public T consume(PELexer lexer) {
        return action.apply(a.consume(lexer), b.consume(lexer));
    }
}

final class Sequence3<T, A, B, C> extends SequenceBase<T> {
    @Child private Element<A> a;
    @Child private Element<B> b;
    @Child private Element<C> c;
    private final Function3<A, B, C, T> action;

    Sequence3(Function3<A, B, C, T> action, Element<A> a, Element<B> b, Element<C> c) {
        this.action = action;
        this.a = a;
        this.b = b;
        this.c = c;
    }

    @Override
    protected Element<?>[] elements() {
        return new Element<?>[]{a, b, c};
    }

    @Override
    public T consume(PELexer lexer) {
        return action.apply(a.consume(lexer), b.consume(lexer), c.consume(lexer));
    }
}

final class Sequence4<T, A, B, C, D> extends SequenceBase<T> {
    @Child private Element<A> a;
    @Child private Element<B> b;
    @Child private Element<C> c;
    @Child private Element<D> d;
    private final Function4<A, B, C, D, T> action;

    Sequence4(Function4<A, B, C, D, T> action, Element<A> a, Element<B> b, Element<C> c, Element<D> d) {
        this.action = action;
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
    }

    @Override
    protected Element<?>[] elements() {
        return new Element<?>[]{a, b, c};
    }

    @Override
    public T consume(PELexer lexer) {
        return action.apply(a.consume(lexer), b.consume(lexer), c.consume(lexer), d.consume(lexer));
    }
}

final class Alternative<T> extends Element<T> {
    @Children private final Element<? extends T>[] options;
    private final ConditionProfile seenEof = ConditionProfile.create();

    Alternative(Element<? extends T>[] options) {
        this.options = options;
    }

    @Override
    protected void createFirstSet(Element<?> setHolder, HashSet<Rule<?>> rulesAdded) {
        assert options.length > 0;

        for (Element<?> option : options) {
            option.createFirstSet(setHolder, rulesAdded);
        }
    }

    @Override
    public void initialize() {
        for (Element<?> element : options) {
            element.createFirstSet(element, new HashSet<>());
            element.initialize();
        }
    }

    @Override
    @ExplodeLoop
    public T consume(PELexer lexer) {
        byte lookahead = lexer.peek(seenEof);
        for (Element<? extends T> element : options) {
            if (element.canStartWith(lookahead)) {
                // matched
                return element.consume(lexer);
            }
        }
        CompilerDirectives.transferToInterpreter();
        throw PELexer.error("no alternative found at " + lexer.position());
    }
}

final class Repetition<T, ListT, R> extends Element<R> {
    @Child private Element<T> element;
    private final Supplier<ListT> createList;
    private final BiFunction<ListT, T, ListT> addToList;
    private final Function<ListT, R> createResult;
    private final ConditionProfile seenEof = ConditionProfile.create();

    Repetition(Element<T> element, Supplier<ListT> createList, BiFunction<ListT, T, ListT> addToList, Function<ListT, R> createResult) {
        this.element = element;
        this.createList = createList;
        this.addToList = addToList;
        this.createResult = createResult;
    }

    @Override
    protected void createFirstSet(Element<?> setHolder, HashSet<Rule<?>> rulesAdded) {
        throw new IllegalStateException("should not reach here");
    }

    @Override
    public void initialize() {
        element.createFirstSet(element, new HashSet<>());
        element.initialize();
    }

    @Override
    public R consume(PELexer lexer) {
        ListT list = createList.get();
        while (true) {
            byte lookahead = lexer.peek(seenEof);
            if (!element.canStartWith(lookahead)) {
                return createResult.apply(list);
            }
            list = addToList.apply(list, element.consume(lexer));
        }
    }
}

final class StackRepetition<T> extends Element<LexerList<T>> {
    @Child private Element<T> element;
    private final ConditionProfile seenEof = ConditionProfile.create();

    StackRepetition(Element<T> element) {
        this.element = element;
    }

    @Override
    protected void createFirstSet(Element<?> setHolder, HashSet<Rule<?>> rulesAdded) {
        throw new IllegalStateException("should not reach here");
    }

    @Override
    public void initialize() {
        element.createFirstSet(element, new HashSet<>());
        element.initialize();
    }

    @Override
    public LexerList<T> consume(PELexer lexer) {
        int pointer = lexer.getStackPointer();
        while (true) {
            byte lookahead = lexer.peek(seenEof);
            if (!element.canStartWith(lookahead)) {
                LexerList<T> list = lexer.getStackList(pointer);
                lexer.resetStackPoiner(pointer);
                return list;
            }
            lexer.push(element.consume(lexer));
        }
    }
}

final class OptionalElement<T, R> extends Element<R> {
    @Child Element<T> element;
    private final Function<T, R> hasValueAction;
    private final Supplier<R> hasNoValueAction;
    private final ConditionProfile seenEof = ConditionProfile.create();

    OptionalElement(Element<T> element, Function<T, R> hasValueAction, Supplier<R> hasNoValueAction) {
        this.element = element;
        this.hasValueAction = hasValueAction;
        this.hasNoValueAction = hasNoValueAction;
    }

    @Override
    protected void createFirstSet(Element<?> setHolder, HashSet<Rule<?>> rulesAdded) {
        throw new IllegalStateException("should not reach here");
    }

    @Override
    public void initialize() {
        element.createFirstSet(element, new HashSet<>());
        element.initialize();
    }

    @Override
    public R consume(PELexer lexer) {
        byte lookahead = lexer.peek(seenEof);
        if (element.canStartWith(lookahead)) {
            return hasValueAction.apply(element.consume(lexer));
        }
        return hasNoValueAction.get();
    }
}

final class TokenReference<T> extends Element<T> {
    private final byte token;
    private final TokenFunction<T> action;
    private final ConditionProfile seenEof = ConditionProfile.create();

    TokenReference(byte token, TokenFunction<T> action) {
        this.token = token;
        this.action = action;
    }

    @Override
    protected void createFirstSet(Element<?> setHolder, HashSet<Rule<?>> rulesAdded) {
        if (token < 64) {
            assert token > 0;
            setHolder.firstA |= 1L << token;
        } else {
            assert token < 128;
            setHolder.firstB |= 1L << (token - 64);
        }
    }

    @Override
    public void initialize() {
        // nothing to do
    }

    @Override
    public T consume(PELexer lexer) {
        int tokenId = lexer.currentTokenId();
        byte actualToken;
        if ((actualToken = lexer.nextToken(seenEof)) != token) {
            CompilerDirectives.transferToInterpreter();
            PELexer.error("expecting " + PELexer.tokenNames[token] + ", got " + PELexer.tokenNames[actualToken] + " at " + lexer.position());
        }
        return action.apply(tokenId);
    }
}

@SuppressWarnings("unchecked")
public final class PEParser {

    static final boolean PEPARSER_DIRECT_CALL = Boolean.getBoolean("PEParser.directcall");

    private final ArrayList<Rule<?>> rules = new ArrayList<>();
    @CompilationFinal private Rule<?> root;

    private static <T> void replaceRules(Element<T>[] elements) {
        for (int i = 0; i < elements.length; i++) {
            if (elements[i] instanceof Rule) {
                elements[i] = new CallRule<>((Rule<T>) elements[i]);
            }
        }
    }

    private static <T> Element<T> replaceRule(Element<T> element) {
        if (element instanceof Rule<?>) {
            return new CallRule<>((Rule<T>) element);
        } else {
            return element;
        }
    }

    public static <T> Alternative<T> alt(Element<T>... options) {
        replaceRules(options);
        return new Alternative<>(options);
    }

    public static <A, B, R> Element<R> seq(Element<A> a, Element<B> b, BiFunction<A, B, R> action) {
        return new Sequence2<>(action, replaceRule(a), replaceRule(b));
    }

    public static <A, B, C, R> Element<R> seq(Element<A> a, Element<B> b, Element<C> c, Function3<A, B, C, R> action) {
        return new Sequence3<>(action, replaceRule(a), replaceRule(b), replaceRule(c));
    }

    public static <A, B, C, D, R> Element<R> seq(Element<A> a, Element<B> b, Element<C> c, Element<D> d, Function4<A, B, C, D, R> action) {
        return new Sequence4<>(action, replaceRule(a), replaceRule(b), replaceRule(c), replaceRule(d));
    }

    public static <T> Element<LexerList<T>> rep(Element<T> element) {
        return new StackRepetition<>(replaceRule(element));
    }

    public static <T, ListT, R> Repetition<T, ListT, R> rep(Element<T> element, Supplier<ListT> createList, BiFunction<ListT, T, ListT> addToList, Function<ListT, R> createResult) {
        return new Repetition<>(replaceRule(element), createList, addToList, createResult);
    }

    public static <T> Element<Optional<T>> opt(Element<T> element) {
        return new OptionalElement<>(replaceRule(element), v -> Optional.of(v), () -> Optional.empty());
    }

    public static Element<Integer> ref(byte token) {
        Integer value = (int) token;
        return new TokenReference<>(token, t -> value);
    }

    public static <T> Element<T> ref(byte token, TokenFunction<T> action) {
        return new TokenReference<>(token, action);
    }

    public <T> Rule<T> rule(String name) {
        Rule<T> rule = new Rule<>(name);
        rules.add(rule);
        return rule;
    }

    public interface Function3<A, B, C, R> {
        R apply(A a, B b, C c);
    }

    public interface Function4<A, B, C, D, R> {
        R apply(A a, B b, C c, D d);
    }

    public interface Function5<A, B, C, D, E, R> {
        R apply(A a, B b, C c, D d, E e);
    }

    public interface TokenFunction<R> {
        R apply(int tokenId);
    }

    private PEParser() {
        // private constructor
    }

    static class BasicNode {
        private final String name;
        private final BasicNode[] children;

        BasicNode(String name, BasicNode... children) {
            this.name = name;
            this.children = children;
        }

        BasicNode(String name, List<BasicNode> children) {
            this.name = name;
            this.children = children.toArray(new BasicNode[children.size()]);
        }

        public void print(int level) {
            for (int i = 0; i < level; i++) {
                System.out.print("  ");
            }
            System.out.println(name);
            for (BasicNode child : children) {
                child.print(level + 1);
            }
        }
    }

    public enum RelOp {
        LessThan,
        LessThanEquals,
        LargerThan,
        LargerThanEquals,
        Equals,
        NotEquals,
        Plus,
        Minus;

        static RelOp choose(RelOp a, Optional<RelOp> b) {
            return b.orElse(a);
        }
    }

    static class TermFactor {
        private final String op;
        private final BasicNode operand;

        TermFactor(String op, BasicNode operand) {
            this.op = op;
            this.operand = operand;
        }
    }

    public static <A, B> A selectFirst(A a, @SuppressWarnings("unused") B b) {
        return a;
    }

    public static <A, B> B selectSecond(@SuppressWarnings("unused") A a, B b) {
        return b;
    }

    public static BasicNode[] concat(BasicNode first, LexerList<BasicNode> rest) {
        BasicNode[] result = new BasicNode[rest.size() + 1];
        result[0] = first;
        for (int i = 0; i < rest.size(); i++) {
            result[i + 1] = rest.get(i);
        }
        return result;
    }

    public static PEParser create() {
        PEParser parser = new PEParser();
        // create the rules
        Rule<BasicNode> program = parser.rule("program");
        Rule<BasicNode> line = parser.rule("line");
        Rule<BasicNode> statement = parser.rule("statement");
        Rule<BasicNode[]> exprlist = parser.rule("exprlist");
        Rule<BasicNode[]> varlist = parser.rule("varlist");
        Rule<BasicNode> expression = parser.rule("expression");
        Rule<BasicNode> term = parser.rule("term");
        Rule<BasicNode> factor = parser.rule("factor");
        Rule<BasicNode> vara = parser.rule("vara");
        Rule<BasicNode> string = parser.rule("string");
        Rule<RelOp> relop = parser.rule("relop");

        // define the rules

        // program: line {line}

        program.define(seq(line, rep(line),
                        (l, r) -> new BasicNode("program", concat(l, r))));

        line.define(seq(opt(ref(NUMBER)), statement, ref(CR),
                        (n, s, c) -> s));

        Element<BasicNode> printStatement = seq(ref(PRINT), exprlist,
                        (p, e) -> new BasicNode("print", e));
        Element<BasicNode> ifCondition = seq(expression, relop, expression,
                        (a, r, b) -> new BasicNode(r.toString(), a, b));
        Element<BasicNode> ifStatement = seq(ref(IF), ifCondition, opt(ref(THEN)), statement,
                        (i, cond, t, s) -> new BasicNode("if", cond, s));
        Element<BasicNode> gotoStatement = seq(ref(GOTO), ref(NUMBER),
                        (g, n) -> new BasicNode("goto"));
        Element<BasicNode> inputStatement = seq(ref(INPUT), varlist,
                        (i, v) -> new BasicNode("input", v));
        Element<BasicNode> assignStatement = seq(opt(ref(LET)), vara, ref(EQUALS), expression,
                        (l, v, s, e) -> new BasicNode(l.isPresent() ? "let" : "assing", v, e));
        Element<BasicNode> gosubStatement = seq(ref(GOSUB), expression,
                        (g, e) -> new BasicNode("gosub", e));
        Element<BasicNode> returnStatement = ref(RETURN,
                        t -> new BasicNode("return"));
        Element<BasicNode> clearStatement = ref(CLEAR,
                        t -> new BasicNode("clear"));
        Element<BasicNode> listStatement = ref(LIST,
                        t -> new BasicNode("list"));
        Element<BasicNode> runStatement = ref(RUN,
                        t -> new BasicNode("run"));
        Element<BasicNode> endStatement = ref(END,
                        t -> new BasicNode("end"));
        statement.define(alt(printStatement, ifStatement, gotoStatement, inputStatement, assignStatement, gosubStatement, returnStatement, clearStatement, listStatement, runStatement, endStatement));

        Element<LexerList<BasicNode>> exprlistRep = rep(seq(ref(COMMA), alt(string, expression), PEParser::selectSecond));
        exprlist.define(seq(alt(string, expression), exprlistRep, PEParser::concat));

        Element<LexerList<BasicNode>> varlistRep = rep(seq(ref(COMMA), vara, PEParser::selectSecond));
        varlist.define(seq(vara, varlistRep, PEParser::concat));

        Element<LexerList<TermFactor>> expressionRep = rep(seq(alt(ref(PLUS, t -> "plus"), ref(MINUS, t -> "minus")), term, TermFactor::new));
        Element<Optional<Boolean>> plusOrMinus = opt(alt(ref(PLUS, t -> false), ref(MINUS, t -> true)));
        expression.define(seq(plusOrMinus, term, expressionRep,
                        (pm, first, additionalTerms) -> {
                            BasicNode result = first;
                            if (pm.orElse(false)) {
                                result = new BasicNode("unaryMinus", result);
                            }
                            for (TermFactor tf : additionalTerms) {
                                result = new BasicNode(tf.op, result, tf.operand);
                            }
                            return result;
                        }));

        term.define(seq(factor, rep(seq(alt(ref(MUL, t -> "mul"), ref(DIV, t -> "div")), factor, TermFactor::new)),
                        (first, additionalFactors) -> {
                            BasicNode result = first;
                            for (TermFactor tf : additionalFactors) {
                                result = new BasicNode(tf.op, result, tf.operand);
                            }
                            return result;
                        }));
        factor.define(alt(vara, ref(NUMBER, t -> new BasicNode("number"))));
        vara.define(alt(ref(NAME, t -> new BasicNode("name")), string));
        string.define(ref(STRING, t -> new BasicNode("string")));
        relop.define(alt(
                        seq(ref(LESS_THAN, t -> RelOp.LessThan), opt(alt(ref(LARGER_THAN, t -> RelOp.NotEquals), ref(EQUALS, t -> RelOp.LessThanEquals))), RelOp::choose),
                        seq(ref(LARGER_THAN, t -> RelOp.LargerThan), opt(alt(ref(LESS_THAN, t -> RelOp.NotEquals), ref(EQUALS, t -> RelOp.LargerThanEquals))), RelOp::choose),
                        ref(EQUALS, t -> RelOp.Equals),
                        ref(PLUS, t -> RelOp.Plus),
                        ref(MINUS, t -> RelOp.Minus)));

        parser.initialize(program);
        return parser;
    }

    private void initialize(Rule<?> newRoot) {
        this.root = newRoot;
        for (Rule<?> rule : rules) {
            rule.initializeRule();
        }
        for (Rule<?> rule : rules) {
            rule.element.initialize();
        }

    }

    public Object parse(PELexer lexer) {
        return root.getCallTarget().call(lexer.asArgumentsArray());
    }
}
