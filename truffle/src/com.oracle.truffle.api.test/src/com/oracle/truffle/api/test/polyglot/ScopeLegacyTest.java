/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.test.AbstractParametrizedLibraryTest;
import static com.oracle.truffle.api.test.polyglot.ScopedViewLegacyTest.createRoot;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.graalvm.polyglot.Context;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runners.Parameterized;

@SuppressWarnings("deprecation")
public class ScopeLegacyTest extends AbstractParametrizedLibraryTest {

    @Parameterized.Parameters(name = "{0}")
    public static List<TestRun> data() {
        return Arrays.asList(TestRun.CACHED, TestRun.UNCACHED, TestRun.DISPATCHED_CACHED, TestRun.DISPATCHED_UNCACHED);
    }

    @Test
    public void testReceiver() throws Exception {
        setupScopes(com.oracle.truffle.api.Scope.newBuilder("test", createVariables()).receiver("testReceiver", 42).build());
        Node location = new ScopedViewLegacyTest.TestInstrumentableNode();
        ScopedViewLegacyTest.TestRootNode root = createRoot(language);
        root.setChild(location);
        NodeLibrary nodeLibrary = createLibrary(NodeLibrary.class, location);
        assertTrue(nodeLibrary.hasReceiverMember(location, null));
        Object receiverMember = nodeLibrary.getReceiverMember(location, null);
        assertEquals("testReceiver", InteropLibrary.getUncached().asString(receiverMember));
        Object scope = nodeLibrary.getScope(location, null, true);
        checkScope(scope, "test", "testReceiver", 42);
    }

    @Test
    public void testVariables() throws Exception {
        setupScopes(com.oracle.truffle.api.Scope.newBuilder("V1", createVariables("1a", 10, "1b", 11)).build(),
                        com.oracle.truffle.api.Scope.newBuilder("V2", createVariables("2a", 20, "2b", 21)).build(),
                        com.oracle.truffle.api.Scope.newBuilder("V31", createVariables("3a", 30, "1a", 31, "2b", 32)).build(),
                        com.oracle.truffle.api.Scope.newBuilder("V4", createVariables("4a", 40, "4b", 41)).build());
        Node location = new ScopedViewLegacyTest.TestInstrumentableNode();
        ScopedViewLegacyTest.TestRootNode root = createRoot(language);
        root.setChild(location);
        NodeLibrary nodeLibrary = createLibrary(NodeLibrary.class, location);
        Object scope = nodeLibrary.getScope(location, null, true);
        checkScope(scope, "V1", "1a", 10, "1b", 11, "2a", 20, "2b", 21, "3a", 30, "1a", 10, "2b", 21, "4a", 40, "4b", 41);

        InteropLibrary interop = InteropLibrary.getUncached();
        assertTrue(interop.hasScopeParent(scope));
        scope = interop.getScopeParent(scope);
        checkScope(scope, "V2", "2a", 20, "2b", 21, "3a", 30, "1a", 31, "2b", 21, "4a", 40, "4b", 41);

        assertTrue(interop.hasScopeParent(scope));
        scope = interop.getScopeParent(scope);
        checkScope(scope, "V31", "3a", 30, "1a", 31, "2b", 32, "4a", 40, "4b", 41);

        assertTrue(interop.hasScopeParent(scope));
        scope = interop.getScopeParent(scope);
        checkScope(scope, "V4", "4a", 40, "4b", 41);
        assertFalse(interop.hasScopeParent(scope));
    }

    @Test
    public void testVariablesWithReceiver() throws Exception {
        setupScopes(com.oracle.truffle.api.Scope.newBuilder("V1", createVariables("1a", 10, "1b", 11)).build(),
                        com.oracle.truffle.api.Scope.newBuilder("V2", createVariables("2a", 20, "2b", 21)).receiver("testReceiver", 42).build(),
                        com.oracle.truffle.api.Scope.newBuilder("V3", createVariables("3a", 30, "3b", 31)).build());
        Node location = new ScopedViewLegacyTest.TestInstrumentableNode();
        ScopedViewLegacyTest.TestRootNode root = createRoot(language);
        root.setChild(location);
        NodeLibrary nodeLibrary = createLibrary(NodeLibrary.class, location);
        assertTrue(nodeLibrary.hasReceiverMember(location, null));
        Object receiverMember = nodeLibrary.getReceiverMember(location, null);
        assertEquals("testReceiver", InteropLibrary.getUncached().asString(receiverMember));
        Object scope = nodeLibrary.getScope(location, null, true);
        checkScope(scope, "V1", "1a", 10, "1b", 11, "testReceiver", 42, "2a", 20, "2b", 21, "3a", 30, "3b", 31);

        InteropLibrary interop = InteropLibrary.getUncached();
        assertTrue(interop.hasScopeParent(scope));
        scope = interop.getScopeParent(scope);
        checkScope(scope, "V2", "2a", 20, "2b", 21, "3a", 30, "3b", 31);

        assertTrue(interop.hasScopeParent(scope));
        scope = interop.getScopeParent(scope);
        checkScope(scope, "V3", "3a", 30, "3b", 31);
        assertFalse(interop.hasScopeParent(scope));
    }

    @Test
    public void testArgsAndVarsAtRoot() throws Exception {
        setupScopes(com.oracle.truffle.api.Scope.newBuilder("V1", createVariables("1v", 1)).build());
        Node location = new ScopedViewLegacyTest.TestInstrumentableNode(StandardTags.RootTag.class);
        ScopedViewLegacyTest.TestRootNode root = createRoot(language);
        root.setChild(location);
        NodeLibrary nodeLibrary = createLibrary(NodeLibrary.class, location);
        // No arguments
        assertFalse(nodeLibrary.hasScope(location, null));

        setupScopes(com.oracle.truffle.api.Scope.newBuilder("V1", createVariables("1v", 1)).arguments(createVariables("1a", 10, "1b", 11)).build());
        location = new ScopedViewLegacyTest.TestInstrumentableNode(StandardTags.RootTag.class);
        root = createRoot(language);
        root.setChild(location);
        Object scope = nodeLibrary.getScope(location, null, true);
        // Just arguments
        checkScope(scope, "V1", "1a", 10, "1b", 11);

        // Variables when with a non-root node
        setupScopes(com.oracle.truffle.api.Scope.newBuilder("V1", createVariables("1v", 1)).arguments(createVariables("1a", 10, "1b", 11)).node(new Node() {
        }).build());
        location = new ScopedViewLegacyTest.TestInstrumentableNode(StandardTags.RootTag.class);
        root = createRoot(language);
        root.setChild(location);
        scope = nodeLibrary.getScope(location, null, true);
        checkScope(scope, "V1", "1v", 1);
    }

    @Test
    public void testEmptyScopes() {
        Context cntx = Context.create();
        setupEnv(cntx, new ProxyLanguage() {
            @Override
            protected Iterable<com.oracle.truffle.api.Scope> findTopScopes(ProxyLanguage.LanguageContext c) {
                return Collections.emptyList();
            }

            @Override
            protected Iterable<com.oracle.truffle.api.Scope> findLocalScopes(ProxyLanguage.LanguageContext c, Node node, Frame frame) {
                return Collections.emptyList();
            }
        });
        cntx.getBindings(ProxyLanguage.ID).getMemberKeys().size();
        Node location = new ScopedViewLegacyTest.TestInstrumentableNode(StandardTags.StatementTag.class);
        ScopedViewLegacyTest.TestRootNode root = createRoot(language);
        root.setChild(location);
        NodeLibrary nodeLibrary = createLibrary(NodeLibrary.class, location);
        assertFalse(nodeLibrary.hasScope(location, null));
    }

    private void setupScopes(com.oracle.truffle.api.Scope... scopes) {
        setupEnv(Context.create(), new ProxyLanguage() {
            @Override
            protected Iterable<com.oracle.truffle.api.Scope> findLocalScopes(ProxyLanguage.LanguageContext c, Node node, Frame frame) {
                return Arrays.asList(scopes);
            }
        });
    }

    private static void checkScope(Object scope, String scopeName, Object... members) throws InteropException {
        assert members.length % 2 == 0;
        InteropLibrary interop = InteropLibrary.getUncached();
        assertTrue(interop.isScope(scope));
        assertEquals(scopeName, interop.toDisplayString(scope));
        Object memberNames = interop.getMembers(scope);
        assertEquals("Wrong number of member names", members.length / 2, interop.getArraySize(memberNames));
        for (int i = 0; i < members.length; i += 2) {
            String name = (String) members[i];
            assertEquals(name, interop.readArrayElement(memberNames, i / 2));
            assertTrue(name, interop.isMemberExisting(scope, name));
            assertEquals(name, members[i + 1], interop.readMember(scope, name));
        }
    }

    private static Object createVariables(Object... variables) {
        return new VariablesObject(variables);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class VariablesObject implements TruffleObject {

        private final Object[] variables;

        VariablesObject(Object[] variables) {
            assert variables.length % 2 == 0;
            this.variables = variables;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasMembers() {
            return true;
        }

        @ExportMessage
        Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
            return new VariableNames(variables);
        }

        @ExportMessage
        boolean isMemberReadable(String member) {
            for (int i = 0; i < variables.length; i += 2) {
                if (member.equals(variables[i])) {
                    return true;
                }
            }
            return false;
        }

        @ExportMessage
        Object readMember(String member) throws UnknownIdentifierException {
            for (int i = 0; i < variables.length; i += 2) {
                if (member.equals(variables[i])) {
                    return variables[i + 1];
                }
            }
            throw UnknownIdentifierException.create(member);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
            return false;
        }

        @ExportMessage
        boolean isMemberModifiable(String member) {
            return isMemberReadable(member);
        }

        @ExportMessage
        void writeMember(String member, Object value) throws UnknownIdentifierException {
            for (int i = 0; i < variables.length; i += 2) {
                if (member.equals(variables[i])) {
                    variables[i + 1] = value;
                    return;
                }
            }
            throw UnknownIdentifierException.create(member);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class VariableNames implements TruffleObject {

        private final Object[] variables;

        VariableNames(Object[] variables) {
            this.variables = variables;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return variables.length / 2;
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return 0 <= index && index <= variables.length / 2;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (isArrayElementReadable(index)) {
                return variables[2 * ((int) index)];
            } else {
                throw InvalidArrayIndexException.create(index);
            }
        }
    }
}
