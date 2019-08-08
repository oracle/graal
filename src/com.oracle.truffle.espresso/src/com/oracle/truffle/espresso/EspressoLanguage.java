/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.descriptors.Names;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.StaticSymbols;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Symbols;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.descriptors.Utf8ConstantTable;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.MainLauncherRootNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Substitutions;

@ProvidedTags(StandardTags.RootTag.class)
@Registration(id = EspressoLanguage.ID, name = EspressoLanguage.NAME, version = EspressoLanguage.VERSION, mimeType = EspressoLanguage.MIME_TYPE, contextPolicy = TruffleLanguage.ContextPolicy.EXCLUSIVE)
public final class EspressoLanguage extends TruffleLanguage<EspressoContext> {

    public static final String ID = "java";
    public static final String NAME = "Java";
    public static final String VERSION = "1.8";
    public static final String MIME_TYPE = "application/x-java";

    // Espresso VM info
    public static final String VM_SPECIFICATION_VERSION = "1.8";
    public static final String VM_SPECIFICATION_NAME = "Java Virtual Machine Specification";
    public static final String VM_SPECIFICATION_VENDOR = "Oracle Corporation";
    public static final String VM_VERSION = "1.8.0_212";
    public static final String VM_VENDOR = "Oracle Corporation";
    public static final String VM_NAME = "Espresso 64-Bit VM";
    public static final String VM_INFO = "mixed mode";

    public static final String FILE_EXTENSION = ".class";

    public static final String ESPRESSO_SOURCE_FILE_KEY = "EspressoSourceFile";

    private final Symbols symbols;
    private final Utf8ConstantTable utf8Constants;
    private final Names names;
    private final Types types;
    private final Signatures signatures;

    private long startupClock = 0;

    public EspressoLanguage() {
        Name.init();
        Type.init();
        Signature.init();
        Substitutions.init();
        this.symbols = new Symbols(StaticSymbols.freeze());
        this.utf8Constants = new Utf8ConstantTable(this.symbols);
        this.names = new Names(this.symbols);
        this.types = new Types(this.symbols);
        this.signatures = new Signatures(this.symbols, types);
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
        OptionValues options = env.getOptions();
        // TODO(peterssen): Redirect in/out to env.in()/out()
        // InputStream in = env.in();
        // OutputStream out = env.out();
        EspressoContext context = new EspressoContext(env, this);
        context.setMainArguments(env.getApplicationArguments());

        EspressoError.guarantee(options.hasBeenSet(EspressoOptions.Classpath), "classpath must be defined");

        Object sourceFile = env.getConfig().get(EspressoLanguage.ESPRESSO_SOURCE_FILE_KEY);
        if (sourceFile != null) {
            context.setMainSourceFile((Source) sourceFile);
        }

        return context;
    }

    @Override
    protected void initializeContext(final EspressoContext context) throws Exception {
        startupClock = System.currentTimeMillis();
        context.initializeContext();
    }

    @Override
    protected void finalizeContext(EspressoContext context) {
        long totalTime = System.currentTimeMillis() - startupClock;
        if (totalTime > 5000) {
            System.out.println("Time spent in Espresso: " + (totalTime / 1000) + "s");
        } else {
            System.out.println("Time spent in Espresso: " + (totalTime) + "ms");
        }
        context.interruptActiveThreads();
        // Shutdown.shutdown creates a Cleaner thread. At this point, Polyglot doesn't allow new
        // threads. We must perform shutdown before then, after main has finished.
    }

    @Override
    protected void disposeContext(final EspressoContext context) {
        context.disposeContext();
    }

    @Override
    protected CallTarget parse(final ParsingRequest request) throws Exception {
        final Source source = request.getSource();
        final EspressoContext context = getCurrentContext();

        assert context.isInitialized();

        String className = source.getName();

        Klass mainClass = loadMainClass(context, LaunchMode.LM_CLASS, className).getMirrorKlass();

        EspressoError.guarantee(mainClass != null, "Error: Could not find or load main class %s", className);

        Method mainMethod = mainClass.lookupDeclaredMethod(Name.main, Signature._void_String_array);

        EspressoError.guarantee(mainMethod != null && mainMethod.isStatic(),
                        "Error: Main method not found in class %s, please define the main method as:\n" +
                                        "            public static void main(String[] args)\n",
                        className);

        assert mainMethod != null && mainMethod.isPublic() && mainMethod.isStatic();
        return Truffle.getRuntime().createCallTarget(new MainLauncherRootNode(this, mainMethod));
    }

    /*
     * Loads a class and verifies that the main class is present and it is ok to call it for more
     * details refer to the java implementation.
     */
    private static StaticObject loadMainClass(EspressoContext context, LaunchMode mode, String name) {
        assert context.isInitialized();
        Meta meta = context.getMeta();
        Klass launcherHelperKlass = meta.loadKlass(Type.sun_launcher_LauncherHelper, StaticObject.NULL);
        Method checkAndLoadMain = launcherHelperKlass.lookupDeclaredMethod(Name.checkAndLoadMain, Signature.Class_boolean_int_String);
        return (StaticObject) checkAndLoadMain.invokeDirect(null, true, mode.ordinal(), meta.toGuestString(name));
    }

    @Override
    protected boolean isObjectOfLanguage(final Object object) {
        return object instanceof StaticObject;
    }

    public final Utf8ConstantTable getUtf8ConstantTable() {
        return utf8Constants;
    }

    public final Names getNames() {
        return names;
    }

    public final Types getTypes() {
        return types;
    }

    public final Signatures getSignatures() {
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
        // perform initialization actions for threads
    }

    @Override
    protected void disposeThread(EspressoContext context, Thread thread) {
        // perform disposal actions for threads
    }

}
