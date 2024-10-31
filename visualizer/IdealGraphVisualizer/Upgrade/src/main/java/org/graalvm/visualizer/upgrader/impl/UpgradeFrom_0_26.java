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

package org.graalvm.visualizer.upgrader.impl;

import org.graalvm.visualizer.upgrader.Upgrader;
import org.openide.util.NbPreferences;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Migrates LayoutSettings and Setting from java Preferences to NbPreferences.
 *
 * @author odouda
 */
public class UpgradeFrom_0_26 extends Upgrader {
    private static final String IMPORTING_VERSION = "0.26"; // NOI18N
    private static final String LAYOUT_SETTINGS_PATH = "/org/graalvm/visualizer/settings/ui"; // NOI18N
    private static final String SETTINGS_PATH = "/org/graalvm/visualizer/settings"; // NOI18N

    @Override
    protected void doVersionImport() {
        doImport();
    }

    public static void doImport() {
        try {
            transferPreferences(LAYOUT_SETTINGS_PATH);
            transferPreferences(SETTINGS_PATH);
        } catch (BackingStoreException ex) {
            ex.printStackTrace();
        }
    }

    private static void transferPreferences(String path) throws BackingStoreException {
        Preferences oldPrefs = Preferences.userRoot().node(path);
        Preferences newNbPrefs = NbPreferences.root().node(SETTINGS_PATH);
        for (String key : oldPrefs.keys()) {
            newNbPrefs.put(key, oldPrefs.get(key, null));
        }
        newNbPrefs.flush();
    }

    @Override
    protected String getImportingVersion() {
        return IMPORTING_VERSION;
    }

    @Override
    protected String getChangesInfo() {
        return "Migrates LayoutSettings and Setting from java Preferences to NbPreferences.";
    }
}
