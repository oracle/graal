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

import org.graalvm.visualizer.filter.Filter;
import org.graalvm.visualizer.filter.FilterCanceledException;
import org.graalvm.visualizer.filter.FilterEvent;
import org.graalvm.visualizer.filter.FilterExecution;
import org.graalvm.visualizer.filter.FilterListener;
import org.graalvm.visualizer.shell.ShellUtils;
import org.netbeans.api.io.Hyperlink;
import org.netbeans.api.io.InputOutput;
import org.netbeans.api.io.OutputColor;
import org.netbeans.api.io.OutputWriter;
import org.openide.cookies.LineCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.text.Line;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;

import javax.script.ScriptException;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reports error from file-based FilterExecutions
 *
 * @author sdedic
 */
class ExecutionErrorReporter implements FilterListener {
    private static ExecutionErrorReporter INSTANCE;

    static void init() {
        synchronized (ExecutionErrorReporter.class) {
            if (INSTANCE != null) {
                return;
            }
            INSTANCE = new ExecutionErrorReporter();
        }
        FilterExecution.getExecutionService().addFilterListener(
                INSTANCE
        );
    }

    @Override
    public void filterStart(FilterEvent e) {
    }

    @Override
    public void filterEnd(FilterEvent e) {
        Throwable err = e.getExecutionError();
        if (err == null || err instanceof FilterCanceledException) {
            return;
        }

        Filter errFilter = e.getFilter();
        FileObject file = ShellUtils.lookupFilterFile(errFilter.getLookup());
        if (file == null) {
            return;
        }

        // hack, get the scripting IO:
        InputOutput io = FileChainProvider.getScriptingIO();
        String msg = err.getMessage();

        boolean printed = false;
        if (err instanceof RuntimeException) {
            Throwable c = err.getCause();
            if (c instanceof ScriptException) {
                // wrapped exception. Try to unroll one level more
                ScriptException sc = (ScriptException) c;
                msg = sc.getMessage();
                Throwable p = c.getCause();
                if (p != null && p != c) {
                    // get message from the underlying exception
                    msg = p.getMessage();
                }

                printed = prettyPrintScriptException(io, sc, msg, file);
            }
        }
        if (msg == null) {
            return;
        }
        if (!printed) {
            io.getErr().println(msg);
        }
        io.show();
    }

    private static final String[] FILE_PATTERNS = {
            "^({0}):{1}:{2}:?",
            "^({0}).*\\[{1}[,:]{2}\\]:?",
    };

    @NbBundle.Messages({
            "# {0} - relative filename",
            "FILE_ScriptFile=Script {0}"
    })
    private static String findPrettyFileName(FileObject f) {
        FileObject scriptRoot;
        try {
            scriptRoot = ShellUtils.getScriptRoot();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            return f.getPath();
        }

        String relPath = FileUtil.getRelativePath(scriptRoot, f);
        if (relPath == null) {
            return f.getPath();
        }
        return Bundle.FILE_ScriptFile(relPath);
    }

    private static boolean prettyPrintScriptException(InputOutput io, ScriptException ex, String msg, FileObject f) {
        String fn = ex.getFileName();
        if (fn == null) {
            return false;
        }
        int fi = msg.indexOf(fn);
        if (fi == -1) {
            return false;
        }
        int nl = msg.indexOf('\n');
        if (nl == -1) {
            nl = msg.length() - 1;
        }
        String line = msg.substring(0, nl + 1);
        int s = -1;
        int e = -1;
        Matcher m = null;
        for (String format : FILE_PATTERNS) {
            Pattern p = Pattern.compile(
                    MessageFormat.format(format, Pattern.quote(fn), ex.getLineNumber(), ex.getColumnNumber()), Pattern.CASE_INSENSITIVE);
            m = p.matcher(line);
            if (m.find()) {
                s = m.start();
                e = m.end();
                break;
            }
        }

        if (m == null || s == -1) {
            return false;
        }
        int fs = m.start(1);
        int fe = m.end(1);

        OutputWriter ow = io.getErr();
        if (fs > 0) {
            ow.print(line.substring(0, s), OutputColor.failure());
        }
        Runnable r = new GotoScriptFile(f, ex.getLineNumber() - 1, ex.getColumnNumber() - 1);
        ow.print(findPrettyFileName(f) + line.substring(fe, e), Hyperlink.from(r, true), null);

        if (e < line.length()) {
            ow.print(line.substring(e), OutputColor.failure());
        }
        if (nl < msg.length()) {
            ow.print(msg.substring(nl));
        }
        return true;
    }

    private static class GotoScriptFile implements Runnable {
        private final FileObject file;
        private final int line;
        private final int column;

        GotoScriptFile(FileObject f, int line, int col) {
            this.file = f;
            this.line = line;
            this.column = col;
        }

        @Override
        public void run() {
            LineCookie cake = file.getLookup().lookup(LineCookie.class);
            Line lineInstance = cake.getLineSet().getOriginal(line);

            if (lineInstance != null) {
                lineInstance.show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS, column);
            }
        }
    }

    @Override
    public void executionStart(FilterEvent e) {
    }

    @Override
    public void executionEnd(FilterEvent e) {
    }
}
