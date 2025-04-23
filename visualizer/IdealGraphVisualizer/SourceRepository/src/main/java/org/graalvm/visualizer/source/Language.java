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

package org.graalvm.visualizer.source;

import org.graalvm.visualizer.source.impl.LanguageRegistryImpl;
import org.openide.util.Lookup;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Defines a Graal language basic description and provides access to language's services,
 * such as {@link org.openide.nodes.Node} for presentation.
 *
 * @author sdedic
 */
public final class Language {
    private final String graalID;
    private final String mimeType;
    private final String displayName;
    private final Lookup lookup;

    private static final Registry REGISTRY_INSTANCE = new Registry();
    private static final String HOST_LANGUAGE_GRAALID = "Java";  // NOI8N

    public Language(String graalID, String mimeType, String displayName, Lookup lkp) {
        this.graalID = graalID;
        this.mimeType = mimeType;
        this.displayName = displayName;
        this.lookup = lkp == null ? Lookup.EMPTY : lkp;
    }

    public String getGraalID() {
        return graalID;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isRunnable() {
        return REGISTRY_INSTANCE.getRunnableLanguages().contains(this);
    }

    public Lookup getLookup() {
        return lookup;
    }

    public boolean isHostLanguage() {
        return HOST_LANGUAGE_GRAALID.equals(getGraalID());
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 83 * hash + Objects.hashCode(this.graalID);
        return hash;
    }

    public static Registry getRegistry() {
        return REGISTRY_INSTANCE;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Language other = (Language) obj;
        if (!Objects.equals(this.graalID, other.graalID)) {
            return false;
        }
        return true;
    }

    public String toString() {
        return displayName + "[" + mimeType + " : " + graalID + "]";
    }

    public static final class Registry {
        private final LanguageRegistryImpl impl = new LanguageRegistryImpl();

        public Language findLanguageByMime(String mimeType) {
            return getSupportedLanguages().stream().filter(
                            (l) -> l.getMimeType().equals(mimeType))
                    .findAny()
                    .orElse(null);
        }

        public Language makeLanguage(String graalID) {
            Language lng = findLanguageByID(graalID);
            if (lng != null) {
                return lng;
            }

            return impl.createLanguage(graalID);
        }

        public Language findLanguageByID(String id) {
            return impl.finLanguageId(id);
        }

        public Collection<Language> getSupportedLanguages() {
            return impl.getLanguages();
        }

        public Collection<Language> getRunnableLanguages() {
            return impl.getRunnableLanguages();
        }

        public Collection<String> getLanguageIDs() {
            return getSupportedLanguages().stream().map((l) -> l.getGraalID()).collect(Collectors.toList());
        }

        public Collection<String> getMimeTypes() {
            return getSupportedLanguages().stream().map((l) -> l.getMimeType()).collect(Collectors.toList());
        }
    }
}
