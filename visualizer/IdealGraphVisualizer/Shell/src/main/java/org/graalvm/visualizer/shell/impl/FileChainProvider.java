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
package org.graalvm.visualizer.shell.impl;

import org.graalvm.visualizer.filter.CustomFilter;
import org.graalvm.visualizer.filter.Filter;
import org.graalvm.visualizer.filter.FilterProvider;
import org.graalvm.visualizer.script.ScriptDefinition;
import org.netbeans.api.io.InputOutput;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.ProxyLookup;

import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Converts a single specific file to a FilterProvider
 *
 * @author sdedic
 */
final class FileChainProvider implements FilterProvider {
    private final FileObject f;
    private CustomFilter editedFilter;
    private boolean editedInvalid;
    private List<ChangeListener> listeners = new ArrayList<>();
    private FileChangeListener fCh = new FileChangeAdapter() {
        @Override
        public void fileDeleted(FileEvent fe) {
            createFilter(true);
        }

        @Override
        public void fileChanged(FileEvent fe) {
            createFilter(false);
        }
    };

    public FileChainProvider(FileObject f) {
        assert f.isData();
        this.f = f;
        file().addFileChangeListener(WeakListeners.create(FileChangeListener.class, fCh, f));
    }

    public FileObject getFile() {
        return f;
    }

    private FileObject file() {
        FileObject e = f.getLookup().lookup(FileObject.class);
        return e != null ? e : f;
    }

    @Override
    public Filter getFilter() {
        synchronized (this) {
            if (editedFilter != null) {
                return editedFilter;
            }
        }
        return createFilter(false);
    }

    @Override
    public Filter createFilter(boolean createNew) {
        EditorCookie cake = f.getLookup().lookup(EditorCookie.class);
        Document doc = null;
        String filterText = null;
        FileObject target = file();

        if (cake != null) {
            doc = cake.getDocument();
        }
        if (doc != null) {
            AtomicReference<String> res = new AtomicReference<>("");
            final Document fdoc = doc;
            doc.render(() -> {
                try {
                    AtomicReference<String> text = new AtomicReference<>();
                    Runnable r = () -> {
                        JEditorPane[] panes = cake.getOpenedPanes();
                        String t = "";
                        if (panes != null) {
                            t = panes[0].getSelectedText();
                        }
                        if (t == null) {
                            try {
                                t = fdoc.getText(0, fdoc.getLength());
                            } catch (BadLocationException ex) {
                                Exceptions.printStackTrace(ex);
                                // PENDING - report an error
                            }
                        }
                        res.set(t);
                    };
                    if (SwingUtilities.isEventDispatchThread()) {
                        r.run();
                    } else {
                        SwingUtilities.invokeAndWait(r);
                    }
                } catch (InterruptedException | InvocationTargetException ex) {
                    Exceptions.printStackTrace(ex);
                }
            });
            filterText = res.get();
        } else {
            if (!target.isValid()) {
                if (!createNew) {
                    return null;
                }
            } else {
                try {
                    filterText = target.asText();
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                    return null;
                }
            }
        }

        CustomFilter existingFilter;
        synchronized (this) {
            existingFilter = editedFilter;
            if (editedInvalid) {
                return editedFilter;
            }
        }

        if (filterText != null && f.isValid()) {
            if (existingFilter != null && !createNew) {
                // only update existing filter
                existingFilter.setCode(filterText);
                return existingFilter;
            }
        } else {
            if (existingFilter != null) {
                existingFilter.setCode("");
            }
        }

        InstanceContent ic = new InstanceContent();
        CustomFilter cf = new ShellCustomFilter(f.getName(), filterText, target.getMIMEType(),
                new ProxyLookup(f.getLookup(), new AbstractLookup(ic)));
        ic.add(cf);
        synchronized (this) {
            if (editedFilter == null || !f.isValid()) {
                editedFilter = cf;
                editedInvalid = !f.isValid();
            }
        }
        if (cf != existingFilter) {
            fireChange();
        }
        return cf;
    }

    @Override
    public void addChangeListener(ChangeListener l) {
        synchronized (listeners) {
            listeners.add(l);
        }
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    void fireChange() {
        ChangeListener[] ll;
        synchronized (listeners) {
            if (listeners.isEmpty()) {
                return;
            }
            ll = listeners.toArray(new ChangeListener[listeners.size()]);
        }
        ChangeEvent e = new ChangeEvent(this);
        for (ChangeListener l : ll) {
            l.stateChanged(e);
        }
    }

    static class ShellCustomFilter extends CustomFilter {

        public ShellCustomFilter(String name, String code, String mimeType, Lookup lkp) {
            super(name, code, mimeType, lkp);
        }

        @Override
        protected ScriptDefinition customizeScriptDefinition(ScriptDefinition base, boolean userCode) {
            if (!userCode) {
                return base;
            }
            InputOutput io = getScriptingIO();
            base.error(new OpenIOPrintWriter(io, io.getErr()));
            base.output(new OpenIOPrintWriter(io, io.getOut()));

            ExecutionErrorReporter.init();
            return base;
        }
    }

    @NbBundle.Messages({
            "TITLE_ShellOutputName=Shell console"
    })
    static InputOutput getScriptingIO() {
        return InputOutput.get(Bundle.TITLE_ShellOutputName(), false);
    }

    static class OpenIOPrintWriter extends PrintWriter {
        private final InputOutput io;
        private boolean opened;

        public OpenIOPrintWriter(InputOutput io, Writer out) {
            super(out);
            this.io = io;
        }

        private void ensureOpen() {
            if (opened) {
                return;
            }
            io.show();
            opened = true;
        }

        @Override
        public void write(String s, int off, int len) {
            if (len > 0) {
                ensureOpen();
            }
            super.write(s, off, len);
        }

        @Override
        public void write(char[] buf, int off, int len) {
            if (len > 0) {
                ensureOpen();
            }
            super.write(buf, off, len);
        }

        @Override
        public void write(int c) {
            ensureOpen();
            super.write(c);
        }


    }
}
