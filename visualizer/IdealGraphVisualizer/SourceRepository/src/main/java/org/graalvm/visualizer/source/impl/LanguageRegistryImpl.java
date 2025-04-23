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

package org.graalvm.visualizer.source.impl;

import org.graalvm.visualizer.source.Language;
import org.graalvm.visualizer.source.lang.DefaultLanguageProvider;
import org.graalvm.visualizer.source.spi.LanguageProvider;
import org.openide.util.Lookup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * @author sdedic
 */
public class LanguageRegistryImpl {
    private Map<String, Language> supported = new HashMap<>();
    private Collection<Language> executable = new HashSet<>();
    private volatile boolean initialized;

    private Map<String, Language> created = new HashMap<>();

    private void initialize() {
        if (!initialized) {
            loadProviders();
        }
    }

    public Language createLanguage(String graalID) {
        synchronized (this) {
            Language l = created.get(graalID);
            if (l != null) {
                return l;
            }
            l = DefaultLanguageProvider.getInstance().createLanguage(graalID);
            created.put(graalID, l);
            return l;
        }
    }

    private void loadProviders() {
        Map<String, Language> langIds = new HashMap<>();
        Collection<Language> executables = new ArrayList<>();

        Collection<? extends LanguageProvider> providers = Lookup.getDefault().lookupAll(LanguageProvider.class);
        for (LanguageProvider prov : providers) {
            Collection<Language> langs = prov.findSupportedLanguages();
            for (Language l : langs) {
                if (langIds.containsKey(l.getGraalID())) {
                    continue;
                }
                langIds.put(l.getGraalID(), l);
            }
        }

        for (Language l : langIds.values()) {
            for (LanguageProvider p : providers) {
                if (p.isExecutable(l)) {
                    executables.add(l);
                }
            }
        }

        synchronized (this) {
            if (initialized) {
                return;
            }
            created.keySet().removeAll(langIds.keySet());
            supported = Collections.unmodifiableMap(langIds);
            executable = Collections.unmodifiableCollection(executables);
            initialized = true;
        }
    }

    public Language finLanguageId(String id) {
        initialize();
        return supported.get(id);
    }

    public Collection<Language> getLanguages() {
        initialize();
        return supported.values();
    }

    public Collection<Language> getRunnableLanguages() {
        return executable;
    }
}
