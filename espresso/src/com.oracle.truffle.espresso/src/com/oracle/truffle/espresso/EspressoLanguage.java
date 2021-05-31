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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.nodes.interop.GetBindingsNode;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObject.StaticObjectFactory;
import com.oracle.truffle.espresso.staticobject.ClassLoaderCache;
import com.oracle.truffle.espresso.staticobject.DefaultStaticProperty;
import com.oracle.truffle.espresso.staticobject.StaticProperty;
import com.oracle.truffle.espresso.staticobject.StaticPropertyKind;
import com.oracle.truffle.espresso.staticobject.StaticShape;
import org.graalvm.home.Version;
import org.graalvm.options.OptionDescriptors;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.descriptors.Names;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.StaticSymbols;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Symbols;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.descriptors.Utf8ConstantTable;
import com.oracle.truffle.espresso.nodes.interop.DestroyVMNode;
import com.oracle.truffle.espresso.nodes.interop.ExitCodeNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoExitException;
import com.oracle.truffle.espresso.substitutions.Substitutions;

@Registration(id = EspressoLanguage.ID, //
                name = EspressoLanguage.NAME, //
                implementationName = EspressoLanguage.IMPLEMENTATION_NAME, //
                version = EspressoLanguage.VERSION, //
                contextPolicy = TruffleLanguage.ContextPolicy.EXCLUSIVE, //
                dependentLanguages = {"nfi", "llvm"})
@ProvidedTags({StandardTags.RootTag.class, StandardTags.RootBodyTag.class, StandardTags.StatementTag.class})
public final class EspressoLanguage extends TruffleLanguage<EspressoContext> implements ClassLoaderCache {

    public static final String ID = "java";
    public static final String NAME = "Java";
    public static final String IMPLEMENTATION_NAME = "Espresso";
    public static final String VERSION = "1.8|11";

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

    private ClassLoader cl;

    private static final StaticClassLoaderCache staticCLC = new StaticClassLoaderCache();

    private static final StaticProperty ARRAY_PROPERTY = new DefaultStaticProperty("array", StaticPropertyKind.Object, true);
    // This field should be static final, but until we move the static object model we cannot have a
    // SubstrateVM feature which will allow us to set the right field offsets at image build time.
    @CompilerDirectives.CompilationFinal //
    private static StaticShape<StaticObjectFactory> arrayShape;

    private static final StaticProperty FOREIGN_PROPERTY = new DefaultStaticProperty("foreignObject", StaticPropertyKind.Object, true);
    // This field should be static final, but until we move the static object model we cannot have a
    // SubstrateVM feature which will allow us to set the right field offsets at image build time.
    @CompilerDirectives.CompilationFinal //
    private static StaticShape<StaticObjectFactory> foreignShape;

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
        assert getCurrentContext().isInitialized();
        String contents = request.getSource().getCharacters().toString();
        if (DestroyVMNode.EVAL_NAME.equals(contents)) {
            RootNode node = new DestroyVMNode(this);
            return Truffle.getRuntime().createCallTarget(node);
        }
        if (ExitCodeNode.EVAL_NAME.equals(contents)) {
            RootNode node = new ExitCodeNode(this);
            return Truffle.getRuntime().createCallTarget(node);
        }
        if (GetBindingsNode.EVAL_NAME.equals(contents)) {
            RootNode node = new GetBindingsNode(this);
            return Truffle.getRuntime().createCallTarget(node);
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

    public static EspressoContext getCurrentContext() {
        return getCurrentContext(EspressoLanguage.class);
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

    @Override
    public void setClassLoader(ClassLoader cl) {
        this.cl = cl;
    }

    @Override
    public ClassLoader getClassLoader() {
        return cl;
    }

    public static StaticProperty getArrayProperty() {
        return ARRAY_PROPERTY;
    }

    public static StaticShape<StaticObjectFactory> getArrayShape() {
        if (arrayShape == null) {
            initializeArrayShape();
        }
        return arrayShape;
    }

    @CompilerDirectives.TruffleBoundary
    private static synchronized void initializeArrayShape() {
        if (arrayShape == null) {
            arrayShape = StaticShape.newBuilder(staticCLC).property(ARRAY_PROPERTY).build(StaticObject.class, StaticObjectFactory.class);
        }
    }

    public static StaticProperty getForeignProperty() {
        return FOREIGN_PROPERTY;
    }

    public static StaticShape<StaticObjectFactory> getForeignShape() {
        if (foreignShape == null) {
            initializeForeignShape();
        }
        return foreignShape;
    }

    @CompilerDirectives.TruffleBoundary
    private static synchronized void initializeForeignShape() {
        if (foreignShape == null) {
            foreignShape = StaticShape.newBuilder(staticCLC).property(FOREIGN_PROPERTY).build(StaticObject.class, StaticObjectFactory.class);
        }
    }

    private static class StaticClassLoaderCache implements ClassLoaderCache {
        private ClassLoader cl;

        @Override
        public void setClassLoader(ClassLoader cl) {
            this.cl = cl;
        }

        @Override
        public ClassLoader getClassLoader() {
            return cl;
        }
    }
}
