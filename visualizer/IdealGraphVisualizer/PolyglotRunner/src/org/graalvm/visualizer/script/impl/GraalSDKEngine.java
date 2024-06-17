/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.script.impl;

import org.openide.util.lookup.ServiceProvider;
import java.io.Closeable;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import org.graalvm.visualizer.script.ScriptCancelledException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;

import org.graalvm.visualizer.script.PreparedScript;
import org.graalvm.visualizer.script.ScriptDefinition;
import org.graalvm.visualizer.script.ScriptEnvironment;
import org.graalvm.visualizer.script.UserScriptEngine;
import org.graalvm.visualizer.script.CancelExceptionMixin;
import org.graalvm.visualizer.script.spi.UserScriptProcessor;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.scripting.Scripting;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
/**
 *
 * @author sdedic
 */
@ServiceProvider(service = UserScriptEngine.class, position = 10000)
public class GraalSDKEngine implements UserScriptEngine {
    private static final Logger LOG = Logger.getLogger(GraalSDKEngine.class.getName());
    private static final String MIME_JAVASCRIPT = "text/javascript"; // NOI18N
    private static final String MIME_FASTR = "text/x-r"; // NOI18N
    private static final String MIME_RUBY = "text/x-ruby"; // NOI18N
    private static final String MIME_PYTHON = "text/x-python"; // NOI18N

    private static final String ID_JAVASCRIPT = "js"; // NOI18N
    private static final String ID_FASTR = "fastr"; // NOI18N
    private static final String ID_RUBY = "ruby"; // NOI18N
    private static final String ID_PYTHON = "python"; // NOI18N

    private static final Map<String, String>    GRAAL_MIME_MAP = new HashMap<>();
    private static final Map<String, String>    GRAAL_MIME_MAP_REVERSE = new HashMap<>();

    private final Set<String> supportedLanguages;

    static {
        // throw exception, if library is not present.
        GRAAL_MIME_MAP.put(ID_JAVASCRIPT, MIME_JAVASCRIPT);
        GRAAL_MIME_MAP.put(ID_FASTR, MIME_FASTR);
        GRAAL_MIME_MAP.put(ID_RUBY, MIME_RUBY);
        GRAAL_MIME_MAP.put(ID_PYTHON, MIME_PYTHON);

        for (Map.Entry<String, String> e : GRAAL_MIME_MAP.entrySet()) {
            GRAAL_MIME_MAP_REVERSE.put(e.getValue(), e.getKey());
        }
    }

    public GraalSDKEngine() {
        Set<String> mimeSet = new HashSet<>();
        supportedLanguages = Collections.unmodifiableSet(mimeSet);
        ScriptEngine graalEngine = Scripting.createManager().getEngineByName("GraalVM:js");
        if (graalEngine != null) {
            ScriptEngineFactory factory = graalEngine.getFactory();
            String langId = factory.getEngineName();
            mimeSet.addAll(factory.getMimeTypes());
            String mime = GRAAL_MIME_MAP.get(langId);
            if (mime != null) {
                mimeSet.add(mime);
            }
        }
    }

    private String toPolyglotSource(String mime, String script, String filename) throws ScriptException {
        String graalID = GRAAL_MIME_MAP_REVERSE.get(mime);
        if (graalID == null) {
            graalID = mime;
        }
        return script;
    }

    @Override
    public boolean acceptsLanguage(String mime) {
        return supportedLanguages().contains(mime);
    }

    @Override
    public String name() {
        return null;
    }

    @Override
    public Set<String> supportedLanguages() {
        return supportedLanguages;
    }

    private static final AtomicInteger uniq = new AtomicInteger(0);

    @NbBundle.Messages({
        "# {0} - filename with type definitions",
        "# {1} - exception message",
        "ERR_LoadingGlobalTypes=Error loading global types from {0}: {1}",
        "# {0} - filename with type definitions",
        "# {1} - exception message",
        "# {2} - fully qualified class name",
        "ERR_CreatingGlobaType=Error creating class {2} defined in {0}: {1}",
    })
    private Map<String, Object> exportJavaTypes(Map<String, Object> bindings, ScriptDefinition def) throws ScriptException {
        FileObject scriptFolder = FileUtil.getConfigFile("ScriptingEnvironment/" + def.getMimeType());
        if (scriptFolder == null) {
            return Collections.emptyMap();
        }
        for (FileObject sf : FileUtil.getOrder(Arrays.asList(scriptFolder.getChildren()), false)) {
            if ("classes".equals(sf.getExt())) { // NOI18N
                java.util.Properties props = new java.util.Properties();
                try (InputStream is = sf.getInputStream()) {
                    props.load(is);
                } catch (IOException ex) {
                    ScriptException scriptEx = new ScriptException(
                            Bundle.ERR_LoadingGlobalTypes(sf.getNameExt(), ex.toString()));
                    scriptEx.initCause(ex);
                    printException(def, scriptEx);
                }
//                Bindings bindings = ctx.getBindings(ScriptContext.GLOBAL_SCOPE);
                for (String global : props.stringPropertyNames()) {
                    String className = props.getProperty(global).trim();
                    
                    if ("".equals(className)) {
                        bindings.remove(global);
                        continue;
                    }
                    if (bindings.containsKey(global)) {
                        continue;
                    }
                    try {
                        Class clazz = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
                        bindings.put(global, clazz);
                    } catch (ClassNotFoundException ex) {
                        ScriptException scriptEx = new ScriptException(
                                Bundle.ERR_CreatingGlobaType(sf.getNameExt(), ex.toString(), className));
                        scriptEx.initCause(ex);
                        printException(def, scriptEx);
                    }
                }
            }
        }
        return bindings;
    }
    
    private void printException(ScriptDefinition def, Throwable ex) {
        if (def.getOutput() == null) {
            Exceptions.printStackTrace(Exceptions.attachSeverity(ex, Level.INFO));
        } else {
            PrintWriter pw = def.getOutput();
            pw.println(ex.toString());
            ex.printStackTrace(pw);
        }
    }

    @Override
    public PreparedScript prepare(ScriptEnvironment env, ScriptDefinition def) throws ScriptException {
        ContextHolder h = prepareContext(env);
        ScriptEngine ctx = h.get();
        List<String> globals = new ArrayList<>();

        String transformed = def.getCode();
        Collection<? extends UserScriptProcessor> processors = MimeLookup.getLookup(def.getMimeType()).lookupAll(UserScriptProcessor.class);
        
        ScriptDefinition customDef = def;
        
        if (!h.initialized) {
            h.initialized = true;
            Map<String, Object> types = exportJavaTypes(new HashMap<>(), def);
            if (!types.isEmpty()) {
                customDef = def.copy();
                for (String s : types.keySet()) {
                    customDef.global(s, types.get(s));
                }
            }
        }
        for (UserScriptProcessor proc : processors) {
            if (!def.getGlobals().isEmpty()) {
                String globs = proc.assignGlobals(customDef);
                if (globs != null) {
                    globals.add(toPolyglotSource(def.getMimeType(), globs, "<globals>"));
                }
            }
            String s = proc.processUserCode(customDef, transformed);
            if (s != null) {
                transformed = s;
            }
        }
        for (String b : def.getGlobals().keySet()) {
            ctx.getBindings(ScriptContext.GLOBAL_SCOPE).put(b, def.getGlobals().get(b));
        }
        String src;
        try {
            src = toPolyglotSource(def.getMimeType(), transformed, def.getScriptFilename());
        } catch (RuntimeException ex) {
            Logger.getLogger(GraalSDKEngine.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
        return new PolyglotExecutable(env, def, customDef, transformed, src, globals);
    }

    private static final Writer defaultOutWriter = new OutputStreamWriter(System.out);
    private static final Writer defaultErrWriter = new OutputStreamWriter(System.err);

    private static class ContextHolder implements Closeable {
        private final ScriptEngine ctx;
        private final DelegatingWriter outStream = new DelegatingWriter(defaultOutWriter);
        private final DelegatingWriter errStream = new DelegatingWriter(defaultErrWriter);
        private boolean initialized;
        private Map<String, Object> types = new HashMap<>();


        public ContextHolder(ScriptEngine builder) {
            this.ctx = builder;
            builder.getContext().setWriter(outStream);
            builder.getContext().setErrorWriter(errStream);
        }

        @Override
        public void close() throws IOException {
            outStream.flush();
            errStream.flush();
            if (ctx instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) ctx).close();
                } catch (Exception ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }

        public ScriptEngine get() {
            return ctx;
        }
    }

    private ContextHolder prepareContext(ScriptEnvironment env) {
        ContextHolder ctx = env.getValue(this);
        if (ctx != null) {
            return ctx;
        }
        ScriptEngine engine = Scripting.createManager().getEngineByName("GraalVM:js");
        // Context.newBuilder().allowHostAccess(true).option("js.nashorn-compat", "true").option("js.syntax-extensions", "true"));
        ctx = new ContextHolder(engine);
        env.setValue(this, ctx);
        return ctx;
    }
    
    private void clearContext(ScriptEnvironment env) {
        env.setValue(this, null);
    }

    /**
     * Wraps the polyglot execution. Execution of each instance's user code is synchronized on {@link #engineLock}.
     */
    private final class PolyglotExecutable implements PreparedScript {
        private final ScriptEnvironment env;
        private final ScriptDefinition def;
        private final ScriptDefinition origDef;
        private final String source;
        private final List<String> globals;
        private final String wrappedCode;

        /**
         * Protects internal state variables
         */
        private final Object lock = new Object();

        /**
         * Protects execution of the Context
         */
        private final Object engineLock = new Object();

        /**
         * Token for a pending evaluate operation
         */
        // @GuardedBy(lock)
        private Object pendingToken;

        /**
         * The thread currently processing the context
         */
        // @GuardedBy(lock)
        private Thread  executionThread;

        /**
         * Canceled flag; the flag is reset at the start of {@link #evaluate} and
         * set by cancel for the pending operation
         */
        // @GuardedBy(lock)
        private boolean canceled;

        // @GuardedBy(lock)
        private ScriptEngine polyContext;

        public PolyglotExecutable(ScriptEnvironment env, ScriptDefinition def, ScriptDefinition customDef,
               String wrappedCode, String source, List<String> globals) {
            this.origDef = def;
            this.def = customDef;
            this.env = env;
            this.wrappedCode = wrappedCode;
            this.source = source;
            this.globals = globals;
        }

        private Object doEvaluate2(Map<String, Object> allValues) throws ScriptException {
            clear();

            ContextHolder h = prepareContext(env);
            ScriptEngine ctx = h.get();

            Writer saveErrWriter = h.errStream;
            Writer saveOutWriter = h.outStream;
            Object srcValue;

            synchronized (lock) {
                polyContext = ctx;
            }
            try {
                if (def.getOutput() != null) {
                    h.outStream.setWriter(def.getOutput());
                }
                if (def.getError() != null) {
                    h.outStream.setWriter(def.getError());
                }
                if (globals != null && !globals.isEmpty()) {
                    Map<String, Object> bindings = def.getGlobals();
                    Object[] args = new Object[bindings.size()];
                    int i = 0;
                    for (String n : bindings.keySet()) {
                        args[i++] = bindings.get(n);
                    }
                    for (String s : globals) {
                        Object gFunction = ctx.eval(s);
                        Invoke invoke = ((Invocable)ctx).getInterface(gFunction, Invoke.class);
                        if (invoke != null) {
                            invoke.execute(args);
                        }
                    }
                }

                srcValue = ctx.eval(source);
                Invoke srcValueInv = ((Invocable)ctx).getInterface(srcValue, Invoke.class);
                if (!def.getParamNames().isEmpty() && srcValueInv != null) {
                    Object[] args = new Object[def.getParamNames().size()];
                    int i = 0;
                    for (String n : def.getParamNames()) {
                        args[i++] = allValues.get(n);
                    }
                    return srcValueInv.execute(args);
                }
            } catch (RuntimeException | Error ex) {
                // nashorn throws RuntimeException 
                checkCancelled(ex);
                throw ex;
            } catch (ScriptException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            } finally {
                synchronized (lock) {
                    polyContext = null;
                }
                if (def.getOutput() != null) {
                    h.outStream.setWriter(saveOutWriter);
                }
                if (def.getError() != null) {
                    h.outStream.setWriter(saveErrWriter);
                }
            }

            return srcValue;
        }
        
        private void checkCancelled(Throwable ex) throws ScriptException {
            for (Throwable item = ex; item != null; item = item.getCause()) {
                if (item instanceof CancelExceptionMixin) {
                    throw new ScriptCancelledException(env);
                }
            }
        }

        @Override
        public String getOriginalCode() {
            return origDef.getCode();
        }

        @Override
        public String getExecutableCode() {
            return wrappedCode;
        }

        @Override
        public Iterable<String> parameterNames() {
            return origDef.getParamNames();
        }

        /**
         * Executes the custom filter. The method is synchronized so that just one thread can execute the
         * filter instance. It may not be strictly required as some filter's language might allow multi threading, but
         * at least JS is single-threaded and would throw ISE.
         */
        @Override
        public Object evaluate(Map<String, Object> argValues) throws ScriptException {
            // ensure that just a single evaluate can happen
            synchronized (engineLock) {
                return doEvaluate(argValues);
            }
        }

        @SuppressWarnings("ThrowFromFinallyBlock")
        private Object doEvaluate(Map<String, Object> argValues) throws ScriptException {
            Map<String, Object> allValues = new HashMap<>(def.getParameters());
            allValues.putAll(argValues);
            // clear interrupted status, just in case
            ScriptEnvironment token = env;
            long t = System.currentTimeMillis();
            ScriptException annotateCancel = null;
            String name = def.getScriptFilename();
            boolean softCancelled = false;
            try {
                try {
                    synchronized (lock) {
                        pendingToken = token;
                        if (canceled) {
                            // release obsolete stuff
                            clear();
                        }
                        canceled = false;
                        executionThread = Thread.currentThread();
                    }
                    if (LOG.isLoggable(Level.FINER)) {
                        LOG.log(Level.FINER, "Executing script: {0}, token: {1}", new Object[] { name, Integer.toHexString(System.identityHashCode(token)) });
                    }
                    Object v = doEvaluate2(allValues);
                    return v;
                } catch (ScriptException ex) {
                    Throwable hostT = ex;
                    if (hostT instanceof CancelExceptionMixin) {
                        softCancelled = true;
                    }
                    synchronized (lock) {
                        if (softCancelled || canceled) {
                            // the ex can be implied by either Thread.interrupt, or Context.close
                            annotateCancel = ex;
                        } else {
                            // wrap into normal exception
                            throw new ScriptException(ex);
                        }
                    }
                    LOG.log(Level.FINER, "Execution of " + name + " failed with exception", ex);
                }
            } finally {
                synchronized (lock) {
                    assert pendingToken == token;
                    executionThread = null;
                    pendingToken = null;
                    boolean c = canceled;
                    canceled = false;
                    if (c) {
                        clear();
                    }
                    if (c || softCancelled) {
                        clear();
                        if (annotateCancel != null) {
                            LOG.log(Level.FINER, "Execution of {0} was cancelled", name);
                            ScriptCancelledException xx;
                            if (annotateCancel instanceof ScriptCancelledException) {
                                xx = (ScriptCancelledException)annotateCancel;
                            } else {
                                xx = new ScriptCancelledException(token, annotateCancel);
                            }
                            throw xx;
                        } else {
                            LOG.log(Level.FINER, "Execution of {0} was cancelled without PolyglotException", name);
                            throw new ScriptCancelledException(token);
                        }
                    }
                }
            }
            // never reached
            assert false;
            return null;
        }

        // @GuardedBy(lock)
        private void clear() {
            polyContext = null;
        }

        @Override
        public boolean cancel() {
            ScriptEngine c;
            synchronized (lock) {
                canceled = true;
                if (executionThread == null) {
                    return false;
                }
                assert executionThread != Thread.currentThread();
                if (pendingToken != env || polyContext == null) {
                    return false;
                }
                c = polyContext;
                // just in case the thread waited on something.
                executionThread.interrupt();
            }
            try {
                if (c instanceof AutoCloseable) {
                    ((AutoCloseable) c).close();
                }
            } catch (Exception ex) {
                // Ignore, see GR-7113
            }
            return true;
        }

        @Override
        public UserScriptEngine getEngine() {
            return GraalSDKEngine.this;
        }

        @Override
        public ScriptEnvironment getEnvironment() {
            return env;
        }
    }

    private static final class DelegatingWriter extends FilterWriter {
        DelegatingWriter(Writer out) {
            super(out);
        }

        void setWriter(Writer w) {
            this.out = w;
        }
    }

    @FunctionalInterface
    public interface Invoke {
        public Object execute(Object... args);
    }
}

