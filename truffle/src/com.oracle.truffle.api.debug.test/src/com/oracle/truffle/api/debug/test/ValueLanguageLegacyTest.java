/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Test of value association with language and language-specific view of values.
 */
/*
 * To be removed with old meta-data APIs in TruffleLanguage.
 */
@SuppressWarnings({"static-method", "deprecation"})
public class ValueLanguageLegacyTest extends AbstractDebugTest {

    @Test
    public void testValueLanguage() {
        Source source1 = Source.create(LegacyValuesLanguage1.ID,
                        "i=10\n" +
                                        "s=test\n" +
                                        "a=null\n" +
                                        "b={}\n" +
                                        "b.a={}\n" +
                                        "b.j=100\n" +
                                        "b.k=200\n");
        Source source2 = Source.create(LegacyValuesLanguage2.ID,
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
                assertEquals(LegacyValuesLanguage1.NAME, lang.getName());
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
                assertEquals(LegacyValuesLanguage2.NAME, lang2.getName());
                assertEquals("{d={}}", value.as(String.class));

                value = frame.getScope().getDeclaredValue("b");
                LanguageInfo lang1 = value.getOriginalLanguage();
                assertNotNull(lang1);
                assertNotEquals(lang1, lang2);
                assertEquals(LegacyValuesLanguage1.NAME, lang1.getName());
                // info from current lang2:
                assertEquals("Object", value.as(String.class));
                assertEquals("L2:Object", value.getMetaObject().as(String.class));
                // info from original lang1:
                value = value.asInLanguage(lang1);
                assertEquals("{a={}, j=100, k=200, c={}}", value.as(String.class));
                assertEquals("L1:Map", value.getMetaObject().as(String.class));
                // The String value of meta object can not be changed by a different language
                assertEquals("L1:Map", value.getMetaObject().asInLanguage(lang2).as(String.class));

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
                // we are not using language views for source location.
                assertNotNull(value.getSourceLocation());

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
    @TruffleLanguage.Registration(id = LegacyValuesLanguage1.ID, name = LegacyValuesLanguage1.NAME, version = "1.0")
    @ProvidedTags({StandardTags.RootTag.class, StandardTags.StatementTag.class})
    public static class LegacyValuesLanguage1 extends LegacyValuesLanguage {

        static final String NAME = "Test Values Language 1";
        static final String ID = "legacy-truffle-test-values-language1";

        public LegacyValuesLanguage1() {
            super("1");
        }
    }

    @TruffleLanguage.Registration(id = LegacyValuesLanguage2.ID, name = LegacyValuesLanguage2.NAME, version = "1.0")
    @ProvidedTags({StandardTags.RootTag.class, StandardTags.StatementTag.class})
    public static class LegacyValuesLanguage2 extends LegacyValuesLanguage {

        static final String NAME = "Test Values Language 2";
        static final String ID = "legacy-truffle-test-values-language2";

        public LegacyValuesLanguage2() {
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

    public static class LegacyValuesLanguage extends TruffleLanguage<Context> {

        private final String id;

        LegacyValuesLanguage(String id) {
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
            LegacyVarNode[] nodes = new LegacyVarNode[n];
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

        private LegacyVarNode parseVar(String variable, SourceSection sourceSection) {
            char var = variable.charAt(0);
            char op = variable.charAt(1);
            if (op == '=') {
                String valueStr = variable.substring(2);
                Object value = getValue(var, valueStr, sourceSection.getSource().createSection(sourceSection.getCharIndex() + 2, valueStr.length()));
                return new LegacyVarNode(this, new String(new char[]{var}), value, sourceSection);
            } else {
                char p = variable.charAt(2);
                assert variable.charAt(3) == '=';
                String valueStr = variable.substring(4);
                Object value = getValue(p, valueStr, sourceSection.getSource().createSection(sourceSection.getCharIndex() + 4, valueStr.length()));
                return new LegacyPropNode(this, new String(new char[]{var}), new String(new char[]{p}), value, sourceSection);
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
                        value = new LegacyNullObject();
                    } else {
                        value = new LegacyPropertiesMapObject(id, sourceSection);
                    }
            }
            return value;
        }

        @Override
        protected boolean isObjectOfLanguage(Object object) {
            if (!(object instanceof LegacyPropertiesMapObject)) {
                return false;
            }
            LegacyPropertiesMapObject pmo = (LegacyPropertiesMapObject) object;
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
            if (InteropLibrary.getFactory().getUncached().isNull(value)) {
                return "null";
            }
            LegacyPropertiesMapObject pmo = (LegacyPropertiesMapObject) value;
            if (id.equals(pmo.getLanguageId())) {
                return toString(pmo);
            } else {
                return "Object";
            }
        }

        private static String toString(LegacyPropertiesMapObject pmo) {
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
                if (value instanceof LegacyPropertiesMapObject) {
                    if (value == pmo) {
                        value = "(this Map)";
                    } else {
                        value = toString((LegacyPropertiesMapObject) value);
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
                return "L" + id + ":Number";
            }
            if (value instanceof String) {
                return "L" + id + ":String";
            }
            if (InteropLibrary.getFactory().getUncached().isNull(value)) {
                return "Null";
            }
            LegacyPropertiesMapObject pmo = (LegacyPropertiesMapObject) value;
            if (id.equals(pmo.getLanguageId())) {
                return "L" + id + ":Map";
            } else {
                return "L" + id + ":Object";
            }
        }

        @Override
        protected SourceSection findSourceLocation(Context context, Object value) {
            if (!(value instanceof TruffleObject)) {
                return null;
            }
            LegacyPropertiesMapObject pmo = (LegacyPropertiesMapObject) value;
            if (id.equals(pmo.getLanguageId())) {
                return pmo.getSourceSection();
            } else {
                return null;
            }
        }

        private static final class BlockNode extends Node {

            private final SourceSection sourceSection;
            @Children private final LegacyVarNode[] children;

            BlockNode(LegacyVarNode[] children, SourceSection sourceSection) {
                this.children = children;
                this.sourceSection = sourceSection;
            }

            public Object execute(VirtualFrame frame) {
                return doExec(frame.materialize());
            }

            @CompilerDirectives.TruffleBoundary
            private Object doExec(MaterializedFrame frame) {
                for (LegacyVarNode ch : children) {
                    ch.execute(frame);
                }
                return new LegacyNullObject();
            }

            @Override
            public SourceSection getSourceSection() {
                return sourceSection;
            }
        }

        @GenerateWrapper
        public static class LegacyVarNode extends Node implements InstrumentableNode {

            private final SourceSection sourceSection;
            private final LegacyValuesLanguage language;
            private final String name;
            protected final Object value;
            @CompilationFinal private ContextReference<Context> contextReference;
            @Child private InteropLibrary interop = InteropLibrary.getFactory().createDispatched(5);
            @CompilationFinal protected FrameSlot slot;

            LegacyVarNode(LegacyValuesLanguage language, String name, Object value, SourceSection sourceSection) {
                this.language = language;
                this.name = name;
                this.value = value;
                this.sourceSection = sourceSection;
            }

            public LegacyVarNode(LegacyVarNode node) {
                this.language = node.language;
                this.name = node.name;
                this.value = node.value;
                this.sourceSection = node.sourceSection;
            }

            public WrapperNode createWrapper(ProbeNode probe) {
                return new LegacyVarNodeWrapper(this, this, probe);
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
                    interop.writeMember(getContextReference().get().getEnv().getPolyglotBindings(), name, value);
                } catch (UnknownIdentifierException | UnsupportedTypeException | UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    // should not happen for polyglot bindings.
                    throw new AssertionError(e);
                }
                return value;
            }

            protected final ContextReference<Context> getContextReference() {
                if (contextReference == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    this.contextReference = lookupContextReference(language.getClass());
                }
                return this.contextReference;
            }

            @Override
            public SourceSection getSourceSection() {
                return sourceSection;
            }
        }

        @GenerateWrapper
        public static class LegacyPropNode extends LegacyValuesLanguage.LegacyVarNode {

            private final String var;
            private final String prop;
            @Child private InteropLibrary interop = InteropLibrary.getFactory().createDispatched(5);

            LegacyPropNode(LegacyValuesLanguage language, String var, String prop, Object value, SourceSection sourceSection) {
                super(language, null, value, sourceSection);
                this.var = var;
                this.prop = prop;
            }

            public LegacyPropNode(LegacyPropNode node) {
                super(node);
                this.var = node.var;
                this.prop = node.prop;
            }

            @Override
            public WrapperNode createWrapper(ProbeNode probeNode) {
                return new LegacyPropNodeWrapper(this, this, probeNode);
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
                            varObj = interop.readMember(getContextReference().get().getEnv().getPolyglotBindings(), var);
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
                LegacyPropertiesMapObject props = (LegacyPropertiesMapObject) varObj;
                props.map.put(prop, value);
                return value;
            }
        }

        @ExportLibrary(InteropLibrary.class)
        static final class LegacyPropertiesMapObject implements TruffleObject {

            private final Map<String, Object> map = new LinkedHashMap<>();
            private final String languageId;
            private final SourceSection sourceSection;

            private LegacyPropertiesMapObject(String languageId, SourceSection sourceSection) {
                this.languageId = languageId;
                this.sourceSection = sourceSection;
            }

            public String getLanguageId() {
                return languageId;
            }

            public SourceSection getSourceSection() {
                return sourceSection;
            }

            @SuppressWarnings("static-method")
            @ExportMessage
            boolean hasMembers() {
                return true;
            }

            @ExportMessage
            @TruffleBoundary
            Object getMembers(@SuppressWarnings("unused") boolean internal) {
                return new LegacyPropertyNamesObject(map.keySet());
            }

            @ExportMessage
            @TruffleBoundary
            boolean isMemberReadable(String member) {
                return map.containsKey(member);
            }

            @ExportMessage
            @TruffleBoundary
            boolean isMemberModifiable(String member) {
                return map.containsKey(member);
            }

            @ExportMessage
            @TruffleBoundary
            boolean isMemberInsertable(String member) {
                return !map.containsKey(member);
            }

            @ExportMessage
            @TruffleBoundary
            void writeMember(String member, Object value) {
                map.put(member, value);
            }

            @ExportMessage
            @TruffleBoundary
            Object readMember(String member) throws UnknownIdentifierException {
                Object object = map.get(member);
                if (object == null) {
                    throw UnknownIdentifierException.create(member);
                } else {
                    return object;
                }
            }

        }

        @ExportLibrary(InteropLibrary.class)
        static final class LegacyPropertyNamesObject implements TruffleObject {

            private final Set<String> names;

            private LegacyPropertyNamesObject(Set<String> names) {
                this.names = names;
            }

            @ExportMessage
            boolean hasArrayElements() {
                return true;
            }

            @ExportMessage
            @TruffleBoundary
            Object readArrayElement(long index) throws InvalidArrayIndexException {
                if (index >= names.size()) {
                    throw InvalidArrayIndexException.create(index);
                }
                Iterator<String> iterator = names.iterator();
                long i = index;
                while (i-- > 0) {
                    iterator.next();
                }
                return iterator.next();
            }

            @ExportMessage
            @TruffleBoundary
            long getArraySize() {
                return names.size();
            }

            @ExportMessage
            boolean isArrayElementReadable(long index) {
                return index >= 0 && index < getArraySize();
            }

        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class LegacyNullObject implements TruffleObject {

        @ExportMessage
        boolean isNull() {
            return true;
        }

    }

}
