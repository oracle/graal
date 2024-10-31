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
package org.graalvm.visualizer.shell.list;

import org.graalvm.visualizer.shell.ShellUtils;
import org.graalvm.visualizer.shell.actions.CreateFilterAction;
import org.netbeans.api.actions.Editable;
import org.netbeans.api.actions.Openable;
import org.openide.cookies.EditCookie;
import org.openide.cookies.OpenCookie;
import org.openide.filesystems.FileObject;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

import javax.swing.Action;
import java.io.IOException;

/**
 * Represents node in the script list. Should provide all file-related
 * actions, but replaces Open/Edit by its own implementation.
 *
 * @author sdedic
 */
public class ScriptNode extends FilterNode {
    private ScriptNode(Node original, org.openide.nodes.Children children, Lookup lookup) {
        super(original, children, lookup);
    }

    static ScriptNode create(Node original, org.openide.nodes.Children children) {
        Lookup lkp = createLookup(original);
        return new ScriptNode(original, children, lkp);
    }

    @Override
    public Action[] getActions(boolean context) {
        Action[] ac = super.getActions(context);
        Action cfa = CreateFilterAction.createContextAction(getLookup());
        if (cfa == null) {
            return ac;
        }
        Action[] nac = new Action[ac.length + 1];
        System.arraycopy(ac, 0, nac, 0, ac.length);
        nac[ac.length] = cfa;
        return nac;
    }


    private static Lookup createLookup(Node original) {
        FileObject f = original.getLookup().lookup(FileObject.class);
        if (f == null) {
            return original.getLookup();
        }
        OC replaceCookie = new OC(f, original);
        Lookup myLkp = Lookups.fixed(new Class[]{
                OpenCookie.class, Openable.class, EditCookie.class, Editable.class
        }, new InstanceContent.Convertor<Class, Object>() {
            @Override
            public Object convert(Class obj) {
                return replaceCookie;
            }

            @Override
            public Class<? extends Object> type(Class obj) {
                return obj;
            }

            @Override
            public String id(Class obj) {
                return obj.getName();
            }

            @Override
            public String displayName(Class obj) {
                return obj.getName();
            }
        });
        Lookup excl = Lookups.exclude(original.getLookup(),
                OpenCookie.class, Openable.class, EditCookie.class, Editable.class
        );
        return new ProxyLookup(myLkp, excl);
    }

    /**
     * Opens the script in normal TopComponent, but marks it as 'graph script'
     * to be recognized as a potential filter.
     * <p>
     * PENDING: The script should open within the function declaration - plugins
     * for languages.
     */
    static class OC implements OpenCookie, EditCookie {
        private final FileObject file;
        private final Node original;

        private OpenCookie delOpenCookie;
        private EditCookie delEditCookie;

        public OC(FileObject file, Node original) {
            this.file = file;
            this.original = original;
        }

        private void markWrapper(Runnable r) {
            try {
                ShellUtils.markScriptObject(file, true);
                r.run();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        private synchronized void initCookies() {
            if (delOpenCookie != null) {
                return;
            }
            delOpenCookie = original.getLookup().lookup(OpenCookie.class);
            delEditCookie = original.getLookup().lookup(EditCookie.class);
        }

        @Override
        public void open() {
            initCookies();
            markWrapper(() -> delOpenCookie.open());
        }

        @Override
        public void edit() {
            initCookies();
            markWrapper(() -> delEditCookie.edit());
        }
    }
}
