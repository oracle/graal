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
package org.graalvm.visualizer.filter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import javax.script.*;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.graalvm.visualizer.script.PreparedScript;
import org.graalvm.visualizer.script.ScriptCancelledException;
import org.graalvm.visualizer.script.ScriptDefinition;
import org.graalvm.visualizer.script.UserScriptEngine;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/**
 * Custom filter represents a filter written in a scripting language. Javascript
 * is supported at the moment.
 * <p/>
 * Scripts are run using {@link UserScriptEngine} implementations registered in
 * {@link MIMELookup}. The first engine, which
 * {@link UserScriptEngine#acceptsLanguage} and succeeds with
 * {@link UserScriptEngine#prepare} of the script wins.
 * <p/>
 * Boilerplate / library functions, imports, or other setup can be provided by
 * modules. IGV reads script fragments from config directory
 * {@code ScriptingEnvironment/{mimeType}}, in the file order (use position file
 * attribute in layer). Fragments must be written so that concatenation of
 * fragments and finally the user script itself forms a code for the target
 * language.
 *
 * @author sdedic
 */
public class CustomFilter extends AbstractFilter {
    private static final String PROPNAME_LANGUAGE = "language"; // NOI18N
    private static final String PROPNAME_NAME = "name"; // NOI18N

    public static final String MIME_JAVASCRIPT = "text/javascript"; // NOI18N

    private static final String PARAM_GRAPH = "graph"; // NOI18N
    private static final String PARAM_IO = "IO"; // NOI18N

    private String mimeType;
    private String code;
    private String name;

    public CustomFilter(String name, String code) {
        this(name, code, MIME_JAVASCRIPT, Lookup.EMPTY);
    }

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public CustomFilter(String name, String code, String mimeType, Lookup lkp) {
        super(lkp);
        this.name = name;
        this.code = code;
        this.mimeType = mimeType;
        getProperties().setProperty(PROPNAME_NAME, name);
        getProperties().setProperty(PROPNAME_LANGUAGE, mimeType);
    }

    @Override
    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public void setName(String s) {
        if (Objects.equals(this.name, s)) {
            return;
        }
        name = s;
        fireChangedEvent();
    }

    public void setCode(String s) {
        if (Objects.equals(this.code, s)) {
            return;
        }
        code = s;
        fireChangedEvent();
    }

    @Override
    public OpenCookie getEditor() {
        return this::openInEditor;
    }

    public boolean openInEditor() {
        EditFilterDialog dialog = new EditFilterDialog(CustomFilter.this);
        dialog.setVisible(true);
        return dialog.wasAccepted();
    }

    @Override
    public String toString() {
        return getName();
    }

    @NbBundle.Messages({
        "# {0} - the script filename",
        "# {1} - the error message",
        "ERR_LoadingEnvironmentScript=Error loading environment script {0}: {1}"
    })
    private String loadDefaultScripts() throws ScriptException {
        FileObject scriptFolder = FileUtil.getConfigFile("ScriptingEnvironment/" + mimeType);
        if (scriptFolder == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (FileObject sf : FileUtil.getOrder(Arrays.asList(scriptFolder.getChildren()), false)) {
            if (!mimeType.equals(sf.getMIMEType())) {
                continue;
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(sf.getInputStream()))) {
                String s;
                while ((s = r.readLine()) != null) {
                    sb.append(s);
                    sb.append("\n");
                }
            } catch (IOException ex) {
                ScriptException scriptEx = new ScriptException(Bundle.ERR_LoadingEnvironmentScript(sf.getNameExt(), ex.toString()));
                scriptEx.initCause(ex);
                throw scriptEx;
            }
        }
        return sb.toString();
    }

    private static final List<String> parameters = Arrays.asList(
                    PARAM_IO, // NOI18N
                    PARAM_GRAPH // NOI18N
    );

    private static class H extends WeakReference<FilterEnvironment> {
        final PreparedScript prep;

        public H(FilterEnvironment referent, PreparedScript prep) {
            super(referent);
            this.prep = prep;
        }
    }

    /**
     * Executes helper scripts, but just once per FilterEnvironment.
     *
     * @param env
     * @param bindings
     * @return
     * @throws ScriptException
     */
    private PreparedScript executeHelpers(FilterEnvironment env, Map<String, Object> bindings,
                                          AtomicReference<UserScriptEngine> eng) throws ScriptException {
        String k = CustomFilter.class.getName() + "." + mimeType;
        H helpers = env.getScriptEnvironment().getValue(k);
        if (helpers != null && helpers.get() == env) {
            eng.set(helpers.prep.getEngine());
            return helpers.prep;
        }
        
        helpers = new H(env, executeHelperScripts(env, bindings, eng));
        env.getScriptEnvironment().setValue(k, helpers);
        return null;
    }

    /**
     * Allows subclasses to customize script definition object before it is
     * executed. The decorator may alter parameters, globals, I/O ...
     *
     * @param base the base object to be decorated
     * @param userCode {@code true}, if the user code is decorated,
     * {@code false} if a wrapper code that e.g. creates globals is processed.
     * @return the new ScriptDefinition instance.
     */
    protected ScriptDefinition customizeScriptDefinition(ScriptDefinition base, boolean userCode) {
        return base;
    }
    
    private Object evalScript(FilterEnvironment e, PreparedScript p, Map<String, Object> parameters) throws ScriptException {
        synchronized (this) {
            if (FilterExecution.get().isCancelled()) {
                p.cancel();
            }
            executingScript.put(e, p);
        }
        try {
            return p.evaluate(parameters);
        } finally {
            synchronized (this) {
                executingScript.put(e, null);
            }
        }
    }

    private PreparedScript executeHelperScripts(FilterEnvironment env, Map<String, Object> bindings,
                                                AtomicReference<UserScriptEngine> eng) throws ScriptException {
        Collection<? extends UserScriptEngine> engs = Lookup.getDefault().lookupAll(UserScriptEngine.class);
        ScriptException exception = null;
        for (UserScriptEngine e : engs) {
            if (!e.acceptsLanguage(mimeType)) {
                continue;
            }
            eng.set(e);
            String toCompile = loadDefaultScripts();
            try {
                ScriptDefinition def = new ScriptDefinition(mimeType)
                                .code(toCompile)
                                .filename(name)
                                .globals(bindings);
                def = customizeScriptDefinition(def, false);
                PreparedScript res = e.prepare(env.getScriptEnvironment(), def);
                evalScript(env, res, Collections.emptyMap());
                return res;
            } catch (ScriptException ex) {
                if (exception == null) {
                    exception = ex;
                }
            }
        }
        if (exception != null) {
            throw exception;
        }
        return null;
    }

    @NbBundle.Messages({
        "# {0} - script name",
        "# {1} - MIME type of the language",
        "ERR_CannotRunScript=Cannot run script {0} (language: {1})"
    })
    @Override
    public void applyWith(FilterEnvironment env) {
        PreparedScript previous;
        synchronized (this) {
            previous = executingScript.put(env, null);
        }
        try {
            applyWith0(env);
        } finally {
            synchronized (this) {
                if (previous != null) {
                    executingScript.put(env, previous);
                } else {
                    executingScript.remove(env);
                }
            }
        }
    }

    void applyWith0(FilterEnvironment env) {
        Map<String, Object> b = new HashMap<>();
        b.put(PARAM_GRAPH, env.getDiagram()); //NOI18N
        b.put(PARAM_IO, System.out); //NOI18N
        for (String s : env.globals().keySet()) {
            b.put(s, env.globals().get(s));
        }
        try {
            AtomicReference<UserScriptEngine> eng = new AtomicReference<>();
            ScriptDefinition def = new ScriptDefinition(mimeType)
                            .code(getCode())
                            .filename(name);
            customizeScriptDefinition(def, true);
            // execute helpers AFTER the definition has been customized; allows to redirect
            // potential errors to the script's I/O
            executeHelpers(env, b, eng);
            UserScriptEngine e = eng.get();
            PreparedScript prep = e.prepare(env.getScriptEnvironment(), def);
            if (prep == null) {
                throw new IllegalStateException(Bundle.ERR_CannotRunScript(name, mimeType));
            } else {
                evalScript(env, prep, b);
            }
        } catch (ScriptCancelledException ex) {
            throw new FilterCanceledException(env, ex.getCause());
        } catch (ScriptException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Temporarily registers executing environments and scripts. Entry present
     * in the map means that applyWith() is executing for that environment.
     * Boolean.TRUE value means the env execution was cancelled and new run
     * should not be permitted.
     */
    private final Map<FilterEnvironment, PreparedScript> executingScript = new HashMap<>();

    @Override
    public boolean cancel(FilterEnvironment d) {
        PreparedScript prep;

        synchronized (this) {
            prep = executingScript.get(d);
            if (prep == null) {
                // no task was ever executed, OK
                return true;
            }
            return prep.cancel();
        }
    }

    public String getMimeType() {
        return mimeType;
    }
}
