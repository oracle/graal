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
package com.oracle.truffle.api.debug.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.CallTag;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInterface;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * A language for testing instruments on various positions of instrumentable nodes.
 * <p>
 * The language constructs consist of pairs of braces or brackets that determine the positions of
 * source sections and single letter keywords that mark {@link InstrumentableNode instrumentable}
 * nodes and the root. The keywords have following meaning:
 * <ul>
 * <li><b>F</b> - a function, {@link RootNode}</li>
 * <li><b>B</b> - a function body, instrumentable node that is tagged with {@link RootTag}</li>
 * <li><b>C</b> - a call node, instrumentable node that is tagged with {@link CallTag}</li>
 * <li><b>E</b> - an expression, instrumentable node that is tagged with {@link ExpressionTag}</li>
 * <li><b>S</b> - a statement, instrumentable node that is tagged with {@link StatementTag}</li>
 * <li><b>I</b> - an {@link InstrumentableNode} without any tag</li>
 * </ul>
 * When the opening brace is not followed by any keyword, it represents a {@link Node} which is not
 * {@link InstrumentableNode}. Multiple keywords can be used. Braces represent a single node (which
 * have multiple tags when multiple keywords are specified), brackets represent a list of nodes, one
 * for each keyword. An example of a possible source code: <code>
 * {F
 *   {B
 *     {S}
 *     {SE  }
 *     {S{E}{E}}
 *     [SFB{E}]
 *     { }
 *   }
 * }
 * </code>
 */
@TruffleLanguage.Registration(id = InstrumentablePositionsTestLanguage.ID, name = "", version = "1.0")
@ProvidedTags({StandardTags.CallTag.class, StandardTags.ExpressionTag.class, StandardTags.RootTag.class, StandardTags.StatementTag.class})
public class InstrumentablePositionsTestLanguage extends TruffleLanguage<Context> {

    public static final String ID = "instrumentable-positions-test-language";

    @Option(help = "Preform pre-materialization of AST nodes. (default:0, 1 - materialize in head recursion order, 2 - materialize in tail recursion order)", category = OptionCategory.EXPERT) //
    static final OptionKey<Integer> PreMaterialize = new OptionKey<>(0);

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new InstrumentablePositionsTestLanguageOptionDescriptors();
    }

    @Override
    protected Context createContext(Env env) {
        return new Context(env);
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        Source code = request.getSource();
        TestNode node;
        node = parse(code);
        return ((CallNode) node).getTarget();
    }

    public TestNode parse(Source code) {
        int preMaterialization = getCurrentContext(InstrumentablePositionsTestLanguage.class).getPreMaterialization();
        return new Parser(this, code, preMaterialization).parse();
    }

    private static final class Parser {

        private static final char EOF = (char) -1;

        private final InstrumentablePositionsTestLanguage lang;
        private final Source source;
        private final String code;
        private final int preMaterialization;
        private int current;

        Parser(InstrumentablePositionsTestLanguage lang, Source source, int preMaterialization) {
            this.lang = lang;
            this.source = source;
            this.preMaterialization = preMaterialization;
            this.code = source.getCharacters().toString();
        }

        public TestNode parse() {
            NodeDescriptor sourceDescriptor = new NodeDescriptor(lang, "F", source, 0, source.getLength() - 1);
            NodeDescriptor nd;
            while ((nd = nextNode()) != null) {
                sourceDescriptor.addChild(nd);
            }
            if (preMaterialization > 0) {
                preMaterialize(sourceDescriptor);
            }
            return sourceDescriptor.getNode();
        }

        NodeDescriptor nextNode() {
            skipWhiteSpace();
            int startIndex = current;

            if (current() == EOF) {
                return null;
            }
            if (current() != '{' && current() != '[') {
                throw new IllegalStateException("Expecting '{' or '[' at position " + current + " character: " + current());
            }
            boolean nodesArray = (current() == '[');
            next();
            List<NodeDescriptor> descriptors;
            NodeDescriptor ndFirst = null;
            NodeDescriptor ndLast = null;
            if (!nodesArray) {
                StringBuilder tags = new StringBuilder();
                while (Character.isAlphabetic(current())) {
                    tags.append(current());
                    next();
                }
                ndFirst = ndLast = new NodeDescriptor(lang, tags.toString(), source, startIndex, -1);
                descriptors = Collections.singletonList(ndFirst);
            } else {
                descriptors = new ArrayList<>();
                while (Character.isAlphabetic(current())) {
                    NodeDescriptor d = new NodeDescriptor(lang, Character.toString(current()), source, startIndex, -1);
                    descriptors.add(d);
                    if (ndFirst == null) {
                        ndFirst = d;
                    } else {
                        descriptors.get(descriptors.size() - 2).addChild(d);
                    }
                    ndLast = d;
                    next();
                }
            }
            skipWhiteSpace();
            while (current() == '{' || current() == '[') {
                NodeDescriptor child = nextNode();
                ndLast.addChild(child);
                skipWhiteSpace();
            }
            if (nodesArray && current() != ']') {
                throw new IllegalStateException("Expecting ']' at position " + current + " character: " + current());
            }
            if (!nodesArray && current() != '}') {
                throw new IllegalStateException("Expecting '}' at position " + current + " character: " + current());
            }
            for (NodeDescriptor d : descriptors) {
                d.setEndPos(current);
            }
            next();
            return ndFirst;
        }

        private void skipWhiteSpace() {
            while (Character.isWhitespace(current())) {
                next();
            }
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

        private void preMaterialize(NodeDescriptor nd) {
            if (preMaterialization == 1) {
                nd.getNode();
            }
            if (nd.children != null) {
                for (NodeDescriptor ch : nd.children) {
                    preMaterialize(ch);
                }
            }
            if (preMaterialization == 2) {
                nd.getNode();
            }
        }
    }

    private static final class NodeDescriptor {

        private InstrumentablePositionsTestLanguage lang;
        private final char[] tags;
        private final Source source;
        private final int startPos;
        private int endPos;
        private List<NodeDescriptor> children;
        private volatile TestNode node;

        NodeDescriptor(InstrumentablePositionsTestLanguage lang, String tags, Source source, int startPos, int endPos) {
            this.lang = lang;
            this.tags = tags.toCharArray();
            this.source = source;
            this.startPos = startPos;
            this.endPos = endPos;
        }

        private void setEndPos(int pos) {
            this.endPos = pos;
        }

        void addChild(NodeDescriptor child) {
            if (children == null) {
                children = new ArrayList<>();
            }
            children.add(child);
        }

        TestNode getNode() {
            if (node == null) {
                synchronized (this) {
                    if (node == null) {
                        if (hasTag('F')) {
                            RootCallTarget taget = Truffle.getRuntime().createCallTarget(new TestRootNode(lang, this));
                            node = new CallNode(taget);
                        } else {
                            node = new BaseNode(this);
                        }
                    }
                }
            }
            return node;
        }

        @ExplodeLoop
        private boolean hasTag(char c) {
            for (char t : tags) {
                if (t == c) {
                    return true;
                }
            }
            return false;
        }

        private boolean isInstrumentable() {
            return tags.length > 0;
        }

        SourceSection getSourceSection() {
            return source.createSection(startPos, endPos - startPos + 1);
        }

        @Override
        public String toString() {
            return "NodeDescriptor(" + new String(tags) + " <" + startPos + " - " + endPos + ">)";
        }

        private NodeDescriptor cloneShallow() {
            return new NodeDescriptor(lang, new String(tags), source, startPos, endPos);
        }

    }

    private interface TestNode extends NodeInterface {

        Object execute(VirtualFrame frame);

    }

    private static final class TestRootNode extends RootNode implements TestNode {

        private final NodeDescriptor nodeDescriptor;
        @CompilationFinal private ContextReference<Context> contextRef;
        @Children private TestNode[] children;

        TestRootNode(InstrumentablePositionsTestLanguage lang, NodeDescriptor nodeDescriptor) {
            super(lang);
            this.nodeDescriptor = nodeDescriptor;
            children = resolveChildren(nodeDescriptor, false);
        }

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public SourceSection getSourceSection() {
            return nodeDescriptor.getSourceSection();
        }

        @Override
        @ExplodeLoop
        public Object execute(VirtualFrame frame) {
            if (contextRef == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.contextRef = lookupContextReference(InstrumentablePositionsTestLanguage.class);
            }
            Object returnValue = contextRef.get().nul;
            for (TestNode child : children) {
                if (child != null) {
                    Object value = child.execute(frame);
                    if (value != null && value != returnValue) {
                        returnValue = value;
                    }
                }
            }
            return returnValue;
        }
    }

    private static final class CallNode extends Node implements TestNode {

        private final CallTarget target;

        CallNode(CallTarget target) {
            this.target = target;
        }

        CallTarget getTarget() {
            return target;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return target.call();
        }

    }

    @GenerateWrapper
    static class BaseNode extends Node implements TestNode, InstrumentableNode {

        private final NodeDescriptor nodeDescriptor;
        private final boolean instrumentable;
        private @Children TestNode[] children;
        @CompilationFinal private ContextReference<Context> contextRef;

        BaseNode(NodeDescriptor nodeDescriptor) {
            this.nodeDescriptor = nodeDescriptor;
            this.instrumentable = nodeDescriptor.isInstrumentable();
        }

        BaseNode(BaseNode node) {
            assert node.instrumentable;
            this.nodeDescriptor = node.nodeDescriptor.cloneShallow();
            this.instrumentable = true;
            this.contextRef = node.contextRef;
        }

        @Override
        public boolean isInstrumentable() {
            return instrumentable;
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            if (tag == CallTag.class) {
                return nodeDescriptor.hasTag('C');
            } else if (tag == ExpressionTag.class) {
                return nodeDescriptor.hasTag('E');
            } else if (tag == RootTag.class) {
                return nodeDescriptor.hasTag('B');
            } else if (tag == StatementTag.class) {
                return nodeDescriptor.hasTag('S');
            }
            return false;
        }

        @Override
        public SourceSection getSourceSection() {
            return nodeDescriptor.getSourceSection();
        }

        @Override
        public WrapperNode createWrapper(ProbeNode probe) {
            assert instrumentable;
            return new BaseNodeWrapper(this, this, probe);
        }

        @Override
        public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
            assureChildrenResolved(true);
            return this;
        }

        @ExplodeLoop
        @Override
        public Object execute(VirtualFrame frame) {
            if (contextRef == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.contextRef = lookupContextReference(InstrumentablePositionsTestLanguage.class);
            }
            assureChildrenResolved(false);
            Object returnValue = contextRef.get().nul;
            for (TestNode child : children) {
                if (child != null) {
                    Object value = child.execute(frame);
                    if (value != null && value != returnValue) {
                        returnValue = value;
                    }
                }
            }
            return returnValue;
        }

        private void assureChildrenResolved(boolean recursively) {
            if (children == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                children = resolveChildren(nodeDescriptor, recursively);
                adoptChildren();
            }
        }

        @Override
        public String toString() {
            return "BaseNode with " + nodeDescriptor;
        }

    }

    @CompilerDirectives.TruffleBoundary
    private static TestNode[] resolveChildren(NodeDescriptor nodeDescriptor, boolean recursively) {
        if (nodeDescriptor.children == null) {
            return new BaseNode[]{};
        }
        List<TestNode> chList = new ArrayList<>();
        for (NodeDescriptor nd : nodeDescriptor.children) {
            TestNode ch = nd.getNode();
            chList.add(ch);
            if (recursively && (ch instanceof BaseNode) && !((BaseNode) ch).isInstrumentable()) {
                ((BaseNode) ch).assureChildrenResolved(recursively);
            }
        }
        return chList.toArray(new TestNode[chList.size()]);
    }
}

final class Context {

    final Object nul;
    final int preMaterialization;

    Context(TruffleLanguage.Env env) {
        nul = env.asGuestValue(null);
        preMaterialization = env.getOptions().get(InstrumentablePositionsTestLanguage.PreMaterialize);
    }

    int getPreMaterialization() {
        return preMaterialization;
    }

}
