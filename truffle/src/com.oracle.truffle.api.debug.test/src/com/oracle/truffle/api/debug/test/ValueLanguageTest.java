/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.graalvm.polyglot.Source;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Test of value association with language and language-specific view of values.
 */
public class ValueLanguageTest extends AbstractDebugTest {

    @Test
    public void testValueLanguage() {
        Source source1 = Source.create(ValuesLanguage1.ID,
                        "i=10\n" +
                                        "s=test\n" +
                                        "a=null\n" +
                                        "b={}\n" +
                                        "b.a={}\n" +
                                        "b.j=100\n" +
                                        "b.k=200\n");
        Source source2 = Source.create(ValuesLanguage2.ID,
                        "j=20\n" +
                                        "s=test2\n" +
                                        "d=null\n" +
                                        "e={}\n" +
                                        "b.c={}\n" +
                                        "e.d={}\n" +
                                        "e.k=200\n");
        try (DebuggerSession session = startSession()) {
            Breakpoint bp1 = Breakpoint.newBuilder(getSourceImpl(source1)).lineIs(7).build();
            session.install(bp1);
            startEval(source1);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();

                DebugValue value = frame.getScope().getDeclaredValue("i");
                assertNull(value.getOriginalLanguage());
                assertEquals("L1:10", value.as(String.class));

                value = frame.getScope().getDeclaredValue("s");
                assertNull(value.getOriginalLanguage());
                assertEquals("L1:test", value.as(String.class));

                value = frame.getScope().getDeclaredValue("a");
                assertNull(value.getOriginalLanguage());
                assertEquals("null", value.as(String.class));

                value = frame.getScope().getDeclaredValue("b");
                LanguageInfo lang = value.getOriginalLanguage();
                assertNotNull(lang);
                assertEquals(ValuesLanguage1.NAME, lang.getName());
                assertEquals("{a={}, j=100}", value.as(String.class));

                event.prepareContinue();
            });

            expectDone();

            Breakpoint bp2 = Breakpoint.newBuilder(getSourceImpl(source2)).lineIs(7).build();
            session.install(bp2);
            startEval(source2);

            expectSuspended((SuspendedEvent event) -> {
                DebugStackFrame frame = event.getTopStackFrame();

                DebugValue value = frame.getScope().getDeclaredValue("j");
                assertNull(value.getOriginalLanguage());
                assertEquals("L2:20", value.as(String.class));

                value = frame.getScope().getDeclaredValue("s");
                assertNull(value.getOriginalLanguage());
                assertEquals("L2:test2", value.as(String.class));

                value = frame.getScope().getDeclaredValue("e");
                LanguageInfo lang2 = value.getOriginalLanguage();
                assertNotNull(lang2);
                assertEquals(ValuesLanguage2.NAME, lang2.getName());
                assertEquals("{d={}}", value.as(String.class));

                value = frame.getScope().getDeclaredValue("b");
                LanguageInfo lang1 = value.getOriginalLanguage();
                assertNotNull(lang1);
                assertNotEquals(lang1, lang2);
                assertEquals(ValuesLanguage1.NAME, lang1.getName());
                // info from current lang2:
                assertEquals("Object", value.as(String.class));
                assertEquals("L2:Object", value.getMetaObject().as(String.class));
                // info from original lang1:
                value = value.asInLanguage(lang1);
                assertEquals("{a={}, j=100, k=200, c={}}", value.as(String.class));
                assertEquals("L1:Map", value.getMetaObject().as(String.class));
                assertEquals("L2:Map", value.getMetaObject().asInLanguage(lang2).as(String.class));

                // Properties are always in the original language:
                value = frame.getScope().getDeclaredValue("b");
                DebugValue a = value.getProperties().iterator().next();
                assertEquals(lang1, a.getOriginalLanguage());
                Iterator<DebugValue> it = value.getProperties().iterator();
                it.next();
                it.next();
                it.next();
                DebugValue c = it.next();
                assertEquals(lang2, c.getOriginalLanguage());
                value = value.asInLanguage(lang2);
                a = value.getProperties().iterator().next();
                assertEquals(lang1, a.getOriginalLanguage());
                it = value.getProperties().iterator();
                it.next();
                it.next();
                it.next();
                c = it.next();
                assertEquals(lang2, c.getOriginalLanguage());

                value = frame.getScope().getDeclaredValue("j");
                assertNull(value.getSourceLocation());
                value = value.asInLanguage(lang1);
                assertEquals("L1:20", value.as(String.class));
                assertNull(value.getSourceLocation());

                value = frame.getScope().getDeclaredValue("d");
                assertEquals("null", value.as(String.class));
                value = value.asInLanguage(lang1);
                assertEquals("null", value.as(String.class));

                value = frame.getScope().getDeclaredValue("e");
                assertEquals(getSourceImpl(source2).createSection(4, 3, 2), value.getSourceLocation());
                value = value.asInLanguage(lang1);
                assertNull(value.getSourceLocation());

                event.prepareContinue();
            });

            expectDone();
        }
    }

    /**
     * A test language for values. Parses variable commands separated by white spaces.
     * <ul>
     * <li>Only statements in the form of &lt;name&gt;=&lt;value&gt; are expected, when name is one
     * letter</li>
     * <li>i, j, k - integers</li>
     * <li>l - long</li>
     * <li>s - String</li>
     * <li>a different letter - object</li>
     * <li>a.b - object property</li>
     * </ul>
     */
    @TruffleLanguage.Registration(id = ValuesLanguage1.ID, name = ValuesLanguage1.NAME, version = "1.0")
    @ProvidedTags({StandardTags.RootTag.class, StandardTags.StatementTag.class})
    public static class ValuesLanguage1 extends ValuesLanguage {

        static final String NAME = "Test Values Language 1";
        static final String ID = "truffle-test-values-language1";

        public ValuesLanguage1() {
            super("1");
        }
    }

    @TruffleLanguage.Registration(id = ValuesLanguage2.ID, name = ValuesLanguage2.NAME, version = "1.0")
    @ProvidedTags({StandardTags.RootTag.class, StandardTags.StatementTag.class})
    public static class ValuesLanguage2 extends ValuesLanguage {

        static final String NAME = "Test Values Language 2";
        static final String ID = "truffle-test-values-language2";

        public ValuesLanguage2() {
            super("2");
        }
    }

    static class Context {

        private final TruffleLanguage.Env env;

        Context(TruffleLanguage.Env env) {
            this.env = env;
        }

        TruffleLanguage.Env getEnv() {
            return env;
        }
    }

    public static class ValuesLanguage extends TruffleLanguage<Context> {

        private final String id;

        ValuesLanguage(String id) {
            this.id = id;
        }

        @Override
        protected Context createContext(TruffleLanguage.Env env) {
            return new Context(env);
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            final com.oracle.truffle.api.source.Source source = request.getSource();
            return Truffle.getRuntime().createCallTarget(new RootNode(this) {

                @Node.Child private BlockNode variables = parse(source);

                @Override
                public Object execute(VirtualFrame frame) {
                    return variables.execute(frame);
                }

            });
        }

        private BlockNode parse(com.oracle.truffle.api.source.Source source) {
            String code = source.getCharacters().toString();
            String[] variables = code.split("\\s");
            int n = variables.length;
            VarNode[] nodes = new VarNode[n];
            int index = 0;
            for (int i = 0; i < n; i++) {
                String varStr = variables[i];
                index = code.indexOf(varStr, index);
                SourceSection sourceSection = source.createSection(index, varStr.length());
                nodes[i] = parseVar(varStr, sourceSection);
                index += varStr.length();
            }
            return new BlockNode(nodes, source.createSection(0, code.length()));
        }

        private VarNode parseVar(String variable, SourceSection sourceSection) {
            char var = variable.charAt(0);
            char op = variable.charAt(1);
            if (op == '=') {
                String valueStr = variable.substring(2);
                Object value = getValue(var, valueStr, sourceSection.getSource().createSection(sourceSection.getCharIndex() + 2, valueStr.length()));
                return new VarNode(new String(new char[]{var}), value, sourceSection, getContextReference());
            } else {
                char p = variable.charAt(2);
                assert variable.charAt(3) == '=';
                String valueStr = variable.substring(4);
                Object value = getValue(p, valueStr, sourceSection.getSource().createSection(sourceSection.getCharIndex() + 4, valueStr.length()));
                return new PropNode(new String(new char[]{var}), new String(new char[]{p}), value, sourceSection, getContextReference());
            }
        }

        private Object getValue(char var, String valueStr, SourceSection sourceSection) {
            Object value;
            switch (var) {
                case 'i':
                case 'j':
                case 'k':
                    value = Integer.parseInt(valueStr);
                    break;
                case 'l':
                    value = Long.parseLong(valueStr);
                    break;
                case 's':
                    value = valueStr;
                    break;
                default:
                    if ("null".equals(valueStr)) {
                        value = new NullObject();
                    } else {
                        value = new PropertiesMapObject(id, sourceSection);
                    }
            }
            return value;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            if (!(object instanceof PropertiesMapObject)) {
                return false;
            }
            PropertiesMapObject pmo = (PropertiesMapObject) object;
            return id.equals(pmo.getLanguageId());
        }

        @Override
        protected String toString(Context context, Object value) {
            if (value instanceof Number) {
                return "L" + id + ":" + ((Number) value).toString();
            }
            if (value instanceof String) {
                return "L" + id + ":" + value.toString();
            }
            if (ForeignAccess.sendIsNull(Message.IS_NULL.createNode(), (TruffleObject) value)) {
                return "null";
            }
            PropertiesMapObject pmo = (PropertiesMapObject) value;
            if (id.equals(pmo.getLanguageId())) {
                return toString(pmo);
            } else {
                return "Object";
            }
        }

        private static String toString(PropertiesMapObject pmo) {
            Iterator<Map.Entry<String, Object>> i = pmo.map.entrySet().iterator();
            if (!i.hasNext()) {
                return "{}";
            }
            StringBuilder sb = new StringBuilder();
            sb.append('{');
            for (;;) {
                Map.Entry<String, Object> e = i.next();
                String key = e.getKey();
                Object value = e.getValue();
                if (value instanceof PropertiesMapObject) {
                    if (value == pmo) {
                        value = "(this Map)";
                    } else {
                        value = toString((PropertiesMapObject) value);
                    }
                }
                sb.append(key);
                sb.append('=');
                sb.append(value);
                if (!i.hasNext()) {
                    return sb.append('}').toString();
                }
                sb.append(',').append(' ');
            }
        }

        @Override
        protected Object findMetaObject(Context context, Object value) {
            if (value instanceof Number) {
                return "L" + id + ": Number";
            }
            if (value instanceof String) {
                return "L" + id + ": String";
            }
            if (ForeignAccess.sendIsNull(Message.IS_NULL.createNode(), (TruffleObject) value)) {
                return "Null";
            }
            PropertiesMapObject pmo = (PropertiesMapObject) value;
            if (id.equals(pmo.getLanguageId())) {
                return "Map";
            } else {
                return "Object";
            }
        }

        @Override
        protected SourceSection findSourceLocation(Context context, Object value) {
            if (!(value instanceof TruffleObject)) {
                return null;
            }
            PropertiesMapObject pmo = (PropertiesMapObject) value;
            if (id.equals(pmo.getLanguageId())) {
                return pmo.getSourceSection();
            } else {
                return null;
            }
        }

        private static final class BlockNode extends Node {

            private final SourceSection sourceSection;
            @Children private final VarNode[] children;

            BlockNode(VarNode[] children, SourceSection sourceSection) {
                this.children = children;
                this.sourceSection = sourceSection;
            }

            public Object execute(VirtualFrame frame) {
                for (VarNode ch : children) {
                    ch.execute(frame);
                }
                return new NullObject();
            }

            @Override
            public SourceSection getSourceSection() {
                return sourceSection;
            }
        }

        @GenerateWrapper
        public static class VarNode extends Node implements InstrumentableNode {

            private final SourceSection sourceSection;
            private final String name;
            protected final Object value;
            protected final ContextReference<Context> contextReference;
            @Child private Node writeNode = Message.WRITE.createNode();
            @CompilerDirectives.CompilationFinal protected FrameSlot slot;

            VarNode(String name, Object value, SourceSection sourceSection, ContextReference<Context> contextReference) {
                this.name = name;
                this.value = value;
                this.sourceSection = sourceSection;
                this.contextReference = contextReference;
            }

            public VarNode(VarNode node) {
                this.name = node.name;
                this.value = node.value;
                this.sourceSection = node.sourceSection;
                this.contextReference = node.contextReference;
            }

            public WrapperNode createWrapper(ProbeNode probe) {
                return new VarNodeWrapper(this, this, probe);
            }

            public boolean isInstrumentable() {
                return sourceSection != null;
            }

            @Override
            public boolean hasTag(Class<? extends Tag> tag) {
                if (tag == StandardTags.StatementTag.class) {
                    return true;
                }
                return false;
            }

            public Object execute(VirtualFrame frame) {
                if (slot == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    slot = frame.getFrameDescriptor().findOrAddFrameSlot(name);
                }
                if (value instanceof Integer) {
                    frame.setInt(slot, (Integer) value);
                } else if (value instanceof Long) {
                    frame.setLong(slot, (Long) value);
                } else {
                    frame.setObject(slot, value);
                }
                try {
                    ForeignAccess.sendWrite(writeNode, (TruffleObject) contextReference.get().getEnv().getPolyglotBindings(), name, value);
                } catch (UnknownIdentifierException | UnsupportedTypeException | UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    // should not happen for polyglot bindings.
                    throw new AssertionError(e);
                }
                return value;
            }

            @Override
            public SourceSection getSourceSection() {
                return sourceSection;
            }
        }

        @GenerateWrapper
        public static class PropNode extends ValuesLanguage.VarNode {

            private final String var;
            private final String prop;
            @Child private Node readNode = Message.READ.createNode();

            PropNode(String var, String prop, Object value, SourceSection sourceSection, ContextReference<Context> contextReference) {
                super(null, value, sourceSection, contextReference);
                this.var = var;
                this.prop = prop;
            }

            public PropNode(PropNode node) {
                super(node);
                this.var = node.var;
                this.prop = node.prop;
            }

            @Override
            public WrapperNode createWrapper(ProbeNode probeNode) {
                return new PropNodeWrapper(this, this, probeNode);
            }

            @Override
            public boolean isInstrumentable() {
                return getSourceSection() != null;
            }

            @Override
            public boolean hasTag(Class<? extends Tag> tag) {
                if (tag == StandardTags.StatementTag.class) {
                    return true;
                }
                return false;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                Object varObj = null;
                if (slot == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    slot = frame.getFrameDescriptor().findFrameSlot(var);
                    if (slot == null) {
                        try {
                            varObj = ForeignAccess.sendRead(readNode, (TruffleObject) contextReference.get().getEnv().getPolyglotBindings(), var);
                        } catch (UnknownIdentifierException e) {
                            varObj = null;
                        } catch (UnsupportedMessageException e) {
                            CompilerDirectives.transferToInterpreter();
                            throw new AssertionError(e);
                        }
                        slot = frame.getFrameDescriptor().addFrameSlot(var);
                        frame.setObject(slot, varObj);
                    }
                }
                if (varObj == null) {
                    varObj = frame.getValue(slot);
                    if (varObj == null) {
                        throw new IllegalStateException("Unknown var " + var);
                    }
                }
                PropertiesMapObject props = (PropertiesMapObject) varObj;
                props.map.put(prop, value);
                return value;
            }
        }

        static final class PropertiesMapObject implements TruffleObject {

            private final Map<String, Object> map = new LinkedHashMap<>();
            private final String languageId;
            private final SourceSection sourceSection;

            private PropertiesMapObject(String languageId, SourceSection sourceSection) {
                this.languageId = languageId;
                this.sourceSection = sourceSection;
            }

            public String getLanguageId() {
                return languageId;
            }

            public SourceSection getSourceSection() {
                return sourceSection;
            }

            @Override
            public ForeignAccess getForeignAccess() {
                return PropertiesMapMessageResolutionForeign.ACCESS;
            }

            public static boolean isInstance(TruffleObject obj) {
                return obj instanceof PropertiesMapObject;
            }

            @MessageResolution(receiverType = PropertiesMapObject.class)
            static class PropertiesMapMessageResolution {

                @Resolve(message = "HAS_KEYS")
                abstract static class PropsMapHasKeysNode extends Node {

                    @SuppressWarnings("unused")
                    public Object access(PropertiesMapObject varMap) {
                        return true;
                    }
                }

                @Resolve(message = "KEYS")
                abstract static class PropsMapKeysNode extends Node {

                    @CompilerDirectives.TruffleBoundary
                    public Object access(PropertiesMapObject varMap) {
                        return new PropertyNamesObject(varMap.map.keySet());
                    }
                }

                @Resolve(message = "READ")
                abstract static class PropsMapReadNode extends Node {

                    @CompilerDirectives.TruffleBoundary
                    public Object access(PropertiesMapObject varMap, String name) {
                        Object object = varMap.map.get(name);
                        if (object == null) {
                            throw UnknownIdentifierException.raise(name);
                        } else {
                            return object;
                        }
                    }
                }

                @Resolve(message = "WRITE")
                abstract static class PropsMapWriteNode extends Node {

                    @CompilerDirectives.TruffleBoundary
                    public Object access(PropertiesMapObject varMap, String name, Object value) {
                        varMap.map.put(name, value);
                        return value;
                    }
                }

            }
        }

        static final class PropertyNamesObject implements TruffleObject {

            private final Set<String> names;

            private PropertyNamesObject(Set<String> names) {
                this.names = names;
            }

            @Override
            public ForeignAccess getForeignAccess() {
                return PropertyNamesMessageResolutionForeign.ACCESS;
            }

            public static boolean isInstance(TruffleObject obj) {
                return obj instanceof PropertyNamesObject;
            }

            @MessageResolution(receiverType = PropertyNamesObject.class)
            static final class PropertyNamesMessageResolution {

                @Resolve(message = "HAS_SIZE")
                abstract static class PropNamesHasSizeNode extends Node {

                    @SuppressWarnings("unused")
                    public Object access(PropertyNamesObject varNames) {
                        return true;
                    }
                }

                @Resolve(message = "GET_SIZE")
                abstract static class PropNamesGetSizeNode extends Node {

                    public Object access(PropertyNamesObject varNames) {
                        return varNames.names.size();
                    }
                }

                @Resolve(message = "READ")
                abstract static class PropNamesReadNode extends Node {

                    @CompilerDirectives.TruffleBoundary
                    public Object access(PropertyNamesObject varNames, int index) {
                        if (index >= varNames.names.size()) {
                            throw UnknownIdentifierException.raise(Integer.toString(index));
                        }
                        Iterator<String> iterator = varNames.names.iterator();
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

    @MessageResolution(receiverType = NullObject.class)
    static final class NullObject implements TruffleObject {

        @Override
        public ForeignAccess getForeignAccess() {
            return NullObjectForeign.ACCESS;
        }

        public static boolean isInstance(TruffleObject obj) {
            return obj instanceof NullObject;
        }

        @Resolve(message = "IS_NULL")
        abstract static class PropertyKeysHasSizeNode extends Node {

            @SuppressWarnings("unused")
            public boolean access(NullObject ato) {
                return true;
            }
        }

    }

}
