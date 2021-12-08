/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.graalvm.home.Version;
import org.graalvm.options.OptionDescriptors;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.staticobject.DefaultStaticProperty;
import com.oracle.truffle.api.staticobject.StaticProperty;
import com.oracle.truffle.api.staticobject.StaticShape;
import com.oracle.truffle.espresso.descriptors.Names;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.StaticSymbols;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Symbols;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.descriptors.Utf8ConstantTable;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.nodes.interop.DestroyVMNode;
import com.oracle.truffle.espresso.nodes.interop.ExitCodeNode;
import com.oracle.truffle.espresso.nodes.interop.GetBindingsNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoExitException;
import com.oracle.truffle.espresso.runtime.EspressoThreadLocalState;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObject.StaticObjectFactory;
import com.oracle.truffle.espresso.substitutions.Substitutions;

@Registration(id = EspressoLanguage.ID, //
                name = EspressoLanguage.NAME, //
                implementationName = EspressoLanguage.IMPLEMENTATION_NAME, //
                contextPolicy = TruffleLanguage.ContextPolicy.EXCLUSIVE, //
                dependentLanguages = "nfi")
@ProvidedTags({StandardTags.RootTag.class, StandardTags.RootBodyTag.class, StandardTags.StatementTag.class})
public final class EspressoLanguage extends TruffleLanguage<EspressoContext> {

    public static final String ID = "java";
    public static final String NAME = "Java";
    public static final String IMPLEMENTATION_NAME = "Espresso";

    // Espresso VM info
    public static final String VM_SPECIFICATION_NAME = "Java Virtual Machine Specification";
    public static final String VM_SPECIFICATION_VENDOR = "Oracle Corporation";
    public static final String VM_VERSION = /* 1.8|11 */ "espresso-" + Version.getCurrent();
    public static final String VM_VENDOR = "Oracle Corporation";
    public static final String VM_NAME = "Espresso 64-Bit VM";
    public static final String VM_INFO = "mixed mode";

    public static final String FILE_EXTENSION = ".class";

    private final Utf8ConstantTable utf8Constants;
    private final Names names;
    private final Types types;
    private final Signatures signatures;

    private long startupClockNanos = 0;

    private static final StaticProperty ARRAY_PROPERTY = new DefaultStaticProperty("array");
    // This field should be static final, but until we move the static object model we cannot have a
    // SubstrateVM feature which will allow us to set the right field offsets at image build time.
    @CompilerDirectives.CompilationFinal //
    private static StaticShape<StaticObjectFactory> arrayShape;

    private static final StaticProperty FOREIGN_PROPERTY = new DefaultStaticProperty("foreignObject");
    // This field should be static final, but until we move the static object model we cannot have a
    // SubstrateVM feature which will allow us to set the right field offsets at image build time.
    @CompilerDirectives.CompilationFinal //
    private static StaticShape<StaticObjectFactory> foreignShape;

    private final ContextThreadLocal<EspressoThreadLocalState> threadLocalState = createContextThreadLocal((context, thread) -> new EspressoThreadLocalState(context));

    public EspressoLanguage() {
        // Initialize statically defined symbols and substitutions.
        JavaKind.ensureInitialized();
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

    public EspressoThreadLocalState getThreadLocalState() {
        return threadLocalState.get();
    }

    @Override
    protected EspressoContext createContext(final TruffleLanguage.Env env) {
        // TODO(peterssen): Redirect in/out to env.in()/out()
        EspressoContext context = new EspressoContext(env, this);
        context.setMainArguments(env.getApplicationArguments());
        return context;
    }

    @Override
    protected void initializeContext(final EspressoContext context) throws Exception {
        startupClockNanos = System.nanoTime();
        context.initializeContext();
    }

    @Override
    protected void finalizeContext(EspressoContext context) {
        long elapsedTimeNanos = System.nanoTime() - startupClockNanos;
        long seconds = TimeUnit.NANOSECONDS.toSeconds(elapsedTimeNanos);
        if (seconds > 10) {
            context.getLogger().log(Level.FINE, "Time spent in Espresso: {0} s", seconds);
        } else {
            context.getLogger().log(Level.FINE, "Time spent in Espresso: {0} ms", TimeUnit.NANOSECONDS.toMillis(elapsedTimeNanos));
        }

        context.prepareDispose();
        try {
            context.doExit(0);
        } catch (EspressoExitException e) {
            // Expected. Suppress. We do not want to throw during context closing.
        }

        context.setFinalized();
    }

    @Override
    protected Object getScope(EspressoContext context) {
        return context.getBindings();
    }

    @Override
    protected void disposeContext(final EspressoContext context) {
        context.disposeContext();
    }

    @Override
    protected CallTarget parse(final ParsingRequest request) throws Exception {
        assert EspressoContext.get(null).isInitialized();
        String contents = request.getSource().getCharacters().toString();
        if (DestroyVMNode.EVAL_NAME.equals(contents)) {
            RootNode node = new DestroyVMNode(this);
            return node.getCallTarget();
        }
        if (ExitCodeNode.EVAL_NAME.equals(contents)) {
            RootNode node = new ExitCodeNode(this);
            return node.getCallTarget();
        }
        if (GetBindingsNode.EVAL_NAME.equals(contents)) {
            RootNode node = new GetBindingsNode(this);
            return node.getCallTarget();
        }
        throw new UnsupportedOperationException("Unsupported operation. Use the language bindings to load classes e.g. context.getBindings(\"" + ID + "\").getMember(\"java.lang.Integer\")");
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

    public static StaticProperty getArrayProperty() {
        return ARRAY_PROPERTY;
    }

    public StaticShape<StaticObjectFactory> getArrayShape() {
        if (arrayShape == null) {
            return initializeArrayShape();
        }
        return arrayShape;
    }

    @CompilerDirectives.TruffleBoundary
    private StaticShape<StaticObjectFactory> initializeArrayShape() {
        synchronized (EspressoLanguage.class) {
            if (arrayShape == null) {
                arrayShape = StaticShape.newBuilder(this).property(ARRAY_PROPERTY, Object.class, true).build(StaticObject.class, StaticObjectFactory.class);
            }
            return arrayShape;
        }
    }

    public static StaticProperty getForeignProperty() {
        return FOREIGN_PROPERTY;
    }

    public StaticShape<StaticObjectFactory> getForeignShape() {
        if (foreignShape == null) {
            return initializeForeignShape();
        }
        return foreignShape;
    }

    @CompilerDirectives.TruffleBoundary
    private StaticShape<StaticObjectFactory> initializeForeignShape() {
        synchronized (EspressoLanguage.class) {
            if (foreignShape == null) {
                foreignShape = StaticShape.newBuilder(this).property(FOREIGN_PROPERTY, Object.class, true).build(StaticObject.class, StaticObjectFactory.class);
            }
            return foreignShape;
        }
    }

    private static final LanguageReference<EspressoLanguage> REFERENCE = LanguageReference.create(EspressoLanguage.class);

    public static EspressoLanguage get(Node node) {
        return REFERENCE.get(node);
    }
}
