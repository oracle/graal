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

package org.graalvm.visualizer.source.lang;

import org.graalvm.visualizer.source.Language;
import org.graalvm.visualizer.source.impl.ui.LanguageNode;
import org.graalvm.visualizer.source.spi.LanguageProvider;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.StatusDecorator;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.ServiceProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Hardcodes several languages as defined by GraalVM. This provider does not
 * inspect runtime what languages are executable.
 * <p>
 * Note that the provider has no instance data; so the default provider served from
 * {@link #INSTANCE} is a different instance than from Lookup, but its behaviour is consistent.
 *
 * @author sdedic
 */
@ServiceProvider(service = LanguageProvider.class)
public class DefaultLanguageProvider implements LanguageProvider, InstanceContent.Convertor {
    private static final String ATTR_MIME_TYPE = "mimetype"; // NOI18N
    private static final String UNKNOWN_MIME_PREFIX = "content/x-unknown-"; // NOI18N
    private static final String LANGUAGES_PATH = "IGV/Languages"; // NOI18N

    private static final DefaultLanguageProvider INSTANCE = new DefaultLanguageProvider();

    @Override
    public Object convert(Object t) {
        return new LanguageNode((Language) t);
    }

    @Override
    public Class type(Object t) {
        return Node.class;
    }

    @Override
    public String id(Object t) {
        return ((Language) t).getGraalID();
    }

    @Override
    public String displayName(Object t) {
        return ((Language) t).getDisplayName();
    }

    public DefaultLanguageProvider() {
    }

    public static DefaultLanguageProvider getInstance() {
        return INSTANCE;
    }

    public Language createLanguage(String graalID) {
        return createLanguage(graalID, graalID, UNKNOWN_MIME_PREFIX + graalID); // NOI18N
    }

    private Language createLanguage(String id, String disp, String mime) {
        InstanceContent ic = new InstanceContent();
        AbstractLookup lkp = new AbstractLookup(ic);
        Language lng = new Language(id, mime, disp, lkp);
        ic.add(lng, this);
        return lng;
    }

    @Override
    public Collection<Language> findSupportedLanguages() {
        FileObject folder = FileUtil.getConfigFile(LANGUAGES_PATH); // NOI18N
        if (folder == null) {
            return Collections.emptyList();
        }
        FileSystem fs;
        try {
            fs = folder.getFileSystem();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            return Collections.emptyList();
        }

        StatusDecorator decorator = fs.getDecorator();
        List<Language> result = new ArrayList<>();
        for (FileObject reg : folder.getChildren()) {
            if (!reg.isFolder()) {
                continue;
            }
            Object m = reg.getAttribute(ATTR_MIME_TYPE);
            if (!(m instanceof String)) {
                continue;
            }
            String mime = m.toString();
            String id = reg.getName();
            String disp = decorator.annotateName(id, Collections.singleton(reg));

            result.add(createLanguage(id, disp, mime));
        }

        return result;
    }

    @Override
    public boolean isExecutable(Language l) {
        return false;
    }

}
