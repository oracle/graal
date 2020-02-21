/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso;

import java.util.Collections;
import java.util.logging.Level;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.espresso.classfile.constantpool.Utf8Constant;
import com.oracle.truffle.espresso.descriptors.ByteSequence;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.nodes.interop.LoadKlassNode;
import org.graalvm.options.OptionDescriptors;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.classfile.attributes.Local;
import com.oracle.truffle.espresso.descriptors.Names;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.StaticSymbols;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Symbols;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.descriptors.Utf8ConstantTable;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.EspressoStatementNode;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Substitutions;

@ProvidedTags({StandardTags.RootTag.class, StandardTags.StatementTag.class})
@Registration(id = EspressoLanguage.ID, name = EspressoLanguage.NAME, version = EspressoLanguage.VERSION, contextPolicy = TruffleLanguage.ContextPolicy.EXCLUSIVE)
public final class EspressoLanguage extends TruffleLanguage<EspressoContext> {

    public static final TruffleLogger EspressoLogger = TruffleLogger.getLogger(EspressoLanguage.ID);

    public static final String ID = "java";
    public static final String NAME = "Java";
    public static final String VERSION = "1.8";

    // Espresso VM info
    public static final String VM_SPECIFICATION_VERSION = "1.8";
    public static final String VM_SPECIFICATION_NAME = "Java Virtual Machine Specification";
    public static final String VM_SPECIFICATION_VENDOR = "Oracle Corporation";
    public static final String VM_VERSION = "1.8.0_241";
    public static final String VM_VENDOR = "Oracle Corporation";
    public static final String VM_NAME = "Espresso 64-Bit VM";
    public static final String VM_INFO = "mixed mode";

    public static final String FILE_EXTENSION = ".class";

    private static final String SCOPE_NAME = "block";

    private final Utf8ConstantTable utf8Constants;
    private final Names names;
    private final Types types;
    private final Signatures signatures;

    private long startupClock = 0;

    public EspressoLanguage() {
        // Initialize statically defined symbols and substitutions.
        Name.ensureInitialized();
        Type.ensureInitialized();
        Signature.ensureInitialized();
        Substitutions.ensureInitialized();

        // Raw symbols are not exposed directly, use the typed interfaces: Names, Types and
        // Signatures instead.
        Symbols symbols = new Symbols(StaticSymbols.freeze());
        this.utf8Constants = new Utf8ConstantTable(symbols);
        this.names = new Names(symbols);
        this.types = new Types(symbols);
        this.signatures = new Signatures(symbols, types);
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new EspressoOptionsOptionDescriptors();
    }

    // cf. sun.launcher.LauncherHelper
    enum LaunchMode {
        LM_UNKNOWN,
        LM_CLASS,
        LM_JAR,
        // LM_MODULE,
        // LM_SOURCE
    }

    @Override
    protected EspressoContext createContext(final TruffleLanguage.Env env) {
        // TODO(peterssen): Redirect in/out to env.in()/out()
        EspressoContext context = new EspressoContext(env, this);
        context.setMainArguments(env.getApplicationArguments());
        return context;
    }

    @Override
    protected Iterable<Scope> findLocalScopes(EspressoContext context, Node node, Frame frame) {
        int currentBci;

        Node espressoNode = findKnownEspressoNode(node);

        Method method;
        Node scopeNode;
        if (espressoNode instanceof QuickNode) {
            QuickNode quick = (QuickNode) espressoNode;
            currentBci = quick.getBCI();
            method = quick.getBytecodesNode().getMethod();
            scopeNode = quick.getBytecodesNode();
        } else if (espressoNode instanceof EspressoStatementNode) {
            EspressoStatementNode statementNode = (EspressoStatementNode) espressoNode;
            currentBci = statementNode.getBci();
            method = statementNode.getBytecodesNode().getMethod();
            scopeNode = statementNode.getBytecodesNode();
        } else if (espressoNode instanceof BytecodeNode) {
            BytecodeNode bytecodeNode = (BytecodeNode) espressoNode;
            try {
                currentBci = bytecodeNode.readBCI(frame);
            } catch (Throwable t) {
                // fall back to entry of method then
                currentBci = 0;
            }
            method = bytecodeNode.getMethod();
            scopeNode = bytecodeNode;
        } else {
            return super.findLocalScopes(context, espressoNode, frame);
        }
        // construct the current scope with valid local variables information
        Local[] liveLocals = method.getLocalVariableTable().getLocalsAt(currentBci);
        if (liveLocals.length == 0) {
            // class was compiled without a local variable table
            // include "this" in method arguments throughout the method
            int localCount = !method.isStatic() ? 1 : 0;
            localCount += method.getParameterCount();
            liveLocals = new Local[localCount];
            Klass[] parameters = (Klass[]) method.getParameters();
            if (!method.isStatic()) {
                // include 'this' and method arguments
                liveLocals[0] = new Local(utf8Constants.getOrCreate(Name.thiz), utf8Constants.getOrCreate(method.getDeclaringKlass().getType()), 0, 65536, 0);
                for (int i = 1; i < localCount; i++) {
                    Klass param = parameters[i - 1];
                    Utf8Constant name = utf8Constants.getOrCreate(ByteSequence.create("" + (i - 1)));
                    Utf8Constant type = utf8Constants.getOrCreate(param.getType());
                    liveLocals[i] = new Local(name, type, 0, 65536, i);
                }
            } else {
                // only include method arguments
                for (int i = 0; i < localCount; i++) {
                    Klass param = parameters[i];
                    liveLocals[i] = new Local(utf8Constants.getOrCreate(ByteSequence.create("" + (i - 1))), utf8Constants.getOrCreate(param.getType()), 0, 65536, i);
                }
            }
        }
        Object variables = EspressoScope.createVariables(liveLocals, frame);
        Object receiver = null;
        if (!method.isStatic()) {
            // get the receiver/'this'
            try {
                receiver = InteropLibrary.getFactory().getUncached().readMember(variables, "0");
            } catch (Exception e) {
                // wasn't able to get 'this'. Defer handling to lookup location
            }
        }
        Scope scope = Scope.newBuilder(SCOPE_NAME, variables).node(scopeNode).receiver("0", receiver).build();
        return Collections.singletonList(scope);
    }

    private static Node findKnownEspressoNode(Node input) {
        Node currentNode = input;
        boolean known = false;
        while (currentNode != null && !known) {
            if (currentNode instanceof QuickNode || currentNode instanceof BytecodeNode || currentNode instanceof EspressoStatementNode) {
                known = true;
            } else if (currentNode instanceof EspressoRootNode) {
                EspressoRootNode rootNode = (EspressoRootNode) currentNode;
                if (rootNode.isBytecodeNode()) {
                    return rootNode.getBytecodeNode();
                }
            } else {
                currentNode = currentNode.getParent();
            }
        }
        return currentNode;
    }

    @Override
    protected void initializeContext(final EspressoContext context) throws Exception {
        startupClock = System.currentTimeMillis();
        context.initializeContext();
    }

    @Override
    protected void finalizeContext(EspressoContext context) {
        long totalTime = System.currentTimeMillis() - startupClock;
        if (totalTime > 10000) {
            EspressoLogger.log(Level.FINE, "Time spent in Espresso: {0} s", (totalTime / 1000));
        } else {
            EspressoLogger.log(Level.FINE, "Time spent in Espresso: {0} ms", totalTime);
        }

        context.prepareDispose();

        // Shutdown.shutdown creates a Cleaner thread. At this point, Polyglot doesn't allow new
        // threads. We must perform shutdown before then, after main has finished.
        context.interruptActiveThreads();
    }

    @Override
    protected void disposeContext(final EspressoContext context) {
        context.disposeContext();
    }

    @Override
    protected CallTarget parse(final ParsingRequest request) throws Exception {
        final EspressoContext context = getCurrentContext();
        assert context.isInitialized();
        context.begin();
        String className = request.getSource().getCharacters().toString();
        return Truffle.getRuntime().createCallTarget(new LoadKlassNode(this, className));
    }

    public Utf8ConstantTable getUtf8ConstantTable() {
        return utf8Constants;
    }

    public Names getNames() {
        return names;
    }

    public Types getTypes() {
        return types;
    }

    public Signatures getSignatures() {
        return signatures;
    }

    public static EspressoContext getCurrentContext() {
        return getCurrentContext(EspressoLanguage.class);
    }

    public String getEspressoHome() {
        return getLanguageHome();
    }

    @Override
    protected boolean isThreadAccessAllowed(Thread thread,
                    boolean singleThreaded) {
        // allow access from any thread instead of just one
        return true;
    }

    @Override
    protected void initializeMultiThreading(EspressoContext context) {
        // perform actions when the context is switched to multi-threading
        // context.singleThreaded.invalidate();
    }

    @Override
    protected void initializeThread(EspressoContext context, Thread thread) {
        context.createThread(thread);
    }

    @Override
    protected void disposeThread(EspressoContext context, Thread thread) {
        context.disposeThread(thread);
    }

}
