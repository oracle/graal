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

import org.netbeans.api.options.OptionsDisplayer;
import org.netbeans.modules.autoupdate.ui.api.PluginManager;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.util.Exceptions;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;
import org.openide.windows.OnShowing;

import java.awt.Dialog;
import java.awt.GraphicsEnvironment;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Checks for Javascript support presence. Offers the user to download the javascript plugins.
 *
 * @author sdedic
 */
@OnShowing
public class InstallJavascriptHelper implements Runnable {
    private static final String PROP_JAVASCRIPT_WARNING = "javascriptDownloadWarning"; // NOI18N
    private static final Logger LOG = Logger.getLogger(InstallJavascriptHelper.class.getName());

    boolean isWarned() {
        return NbPreferences.forModule(InstallJavascriptHelper.class).getBoolean(PROP_JAVASCRIPT_WARNING, false);
    }

    final String install = Bundle.BTN_Install();
    final String defer = Bundle.BTN_Defer();
    final String proxy = Bundle.BTN_ProxySettings();

    @NbBundle.Messages({
            "TITLE_SourceFeaturesLimited=Source Features Limited",
            "DESC_SourceFeaturesLimited=<html><b>JavaScript editing features are severely limited.</b><p/> Scripting editors do not provide " +
                    "syntax highlighting or code assist. The Source View for JavaScript sources provides only basic editing features. <p/>" +
                    "Please download and install <b>JavaScript support modules</b> from the Update Center. <p/><hr/>Note: You may need to configure proxy, if " +
                    "your network requires it.</html>",
            "BTN_Install=Install now",
            "BTN_ProxySettings=Proxy settings...",
            "BTN_Defer=Defer, remind again",
            "DESC_InstallJavascript=Install JavaScript"
    })
    public void checkJavascript() {
        if (isWarned()) {
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        ClassLoader ldr = Lookup.getDefault().lookup(ClassLoader.class);
        try {
            ldr.loadClass("org.netbeans.modules.javascript2.editor.api.FrameworksUtils"); // NOI18N
            return;
        } catch (ClassNotFoundException ex) {
            // expected
            LOG.info("JavaScript support modules not installed."); // NOI18N
        }
        Dialog[] d = new Dialog[1];
        DialogDescriptor nd = new DialogDescriptor(
                Bundle.DESC_SourceFeaturesLimited(), Bundle.TITLE_SourceFeaturesLimited(),
                true, new Object[]{
                install, proxy, defer,
                DialogDescriptor.CANCEL_OPTION
        }, install, DialogDescriptor.DEFAULT_ALIGN, HelpCtx.DEFAULT_HELP, ev -> {
            perform(ev.getActionCommand(), d[0]);
        }
        );

        d[0] = DialogDisplayer.getDefault().createDialog(nd);
        d[0].setVisible(true);
    }

    private void perform(String command, Dialog dlg) {
        Preferences prefs = NbPreferences.forModule(InstallJavascriptHelper.class);
        Boolean w = null;
        if (install.equals(command)) {
            dlg.setVisible(false);
            w = true;
            PluginManager.installSingle("org.netbeans.modules.javascript2.kit", Bundle.DESC_InstallJavascript()); // NOI18N
        } else if (defer.equals(command)) {
            dlg.setVisible(false);
        } else if (proxy.equals(command)) {
            OptionsDisplayer.getDefault().open("General"); // NOI18N
            return;
        } else {
            w = true;
            dlg.setVisible(false);
        }
        if (w != null) {
            try {
                prefs.putBoolean(PROP_JAVASCRIPT_WARNING, w);
                prefs.flush();
            } catch (BackingStoreException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    @Override
    public void run() {
        checkJavascript();
    }
}
