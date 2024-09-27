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
package org.graalvm.visualizer.settings.graal;

import org.graalvm.visualizer.settings.SettingsStore;
import org.graalvm.visualizer.settings.graal.GraalSettings.GraalSettingBean;
import org.openide.util.NbBundle;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Class which encapsulates the settings storage.
 * <p/>
 * Implementation note: this is only a wrapper class, without its own identity.
 * It can be GCed so that more {@link GraalSettings} instances will use the same
 * Preferences, though just one at a time.
 */
public final class GraalSettings extends SettingsStore<GraalSettings, GraalSettingBean> {

    public final static String PORT_BINARY = "portBinary";// NOI18N
    public final static int PORT_BINARY_DEFAULT = 4445;
    public final static String DIRECTORY = "directory";// NOI18N
    public final static String DIRECTORY_DEFAULT = System.getProperty("user.dir");
    public final static String MAP = "map";// NOI18N
    public final static String MAP_DEFAULT = "";// NOI18N
    public final static String REPOSITORY = "repository";// NOI18N
    public final static String REPOSITORY_DEFAULT = NbBundle.getMessage(GraalSettings.class, "DefaultMavenRepository");
    public final static String CLEAN_CACHES = "cleanCaches"; // NOI18N
    public final static String ACCEPT_NETWORK = "acceptNetwork"; // NOI18N
    public final static boolean ACCEPT_NETWORK_DEFAULT = false; // NOI18
    public final static String SESSION_CLOSE_TIMEOUT = "sessionCloseTimeout"; // NOI18N
    public final static int SESSION_CLOSE_TIMEOUT_DEFAULT = 10; // NOI18N
    public final static String AUTO_SEPARATE_SESSIONS = "autoSeparateSessions"; // NOI18N
    public final static boolean AUTO_SEPARATE_SESSIONS_DEFAULT = false; // NOI18N

    public static GraalSettings obtain() {
        return SettingsStore.obtain(GraalSettings.class, GraalSettings::new);
    }

    private GraalSettings() {
    }

    @Override
    protected void fillDefaults(BiConsumer<String, Object> filler) {
        filler.accept(PORT_BINARY, PORT_BINARY_DEFAULT);
        filler.accept(REPOSITORY, REPOSITORY_DEFAULT);
        filler.accept(CLEAN_CACHES, true);
        filler.accept(ACCEPT_NETWORK, ACCEPT_NETWORK_DEFAULT);
        filler.accept(SESSION_CLOSE_TIMEOUT, SESSION_CLOSE_TIMEOUT_DEFAULT);
        filler.accept(AUTO_SEPARATE_SESSIONS, AUTO_SEPARATE_SESSIONS_DEFAULT);
        filler.accept(MAP, MAP_DEFAULT);
        filler.accept(DIRECTORY, DIRECTORY_DEFAULT);
    }

    public String getDirectory() {
        return get(String.class, DIRECTORY);
    }

    public void setDirectory(String dir) {
        set(DIRECTORY, dir != null && !dir.isEmpty() ? dir : DIRECTORY_DEFAULT);
    }

    public List<String> getFileMap() {
        String s = get(String.class, MAP);
        if (s.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(s.split(Pattern.quote(File.pathSeparator))).collect(Collectors.toList());
    }

    public void setFileMap(List<String> items) {
        if (items == null || items.isEmpty()) {
            set(MAP, MAP_DEFAULT);
        } else {
            set(MAP, items.stream().collect(Collectors.joining(File.pathSeparator)));
        }
    }

    public void addFilesToMap(List<String> fileNames) {
        List<String> files = getFileMap();
        files.addAll(fileNames);
        setFileMap(files);
    }

    public void removeFilesFromMap(List<String> fileNames) {
        List<String> files = getFileMap();
        files.removeAll(fileNames);
        setFileMap(files);
    }

    @Override
    protected GraalSettingBean makeBean() {
        return new GraalSettingBean(this);
    }

    public static final class GraalSettingBean extends SettingsBean<GraalSettings, GraalSettingBean> {

        GraalSettingBean(GraalSettings store) {
            super(store);
        }

        GraalSettingBean(GraalSettingBean bean) {
            super(bean);
        }

        @Override
        public GraalSettingBean copy() {
            return new GraalSettingBean(this);
        }
    }
}
