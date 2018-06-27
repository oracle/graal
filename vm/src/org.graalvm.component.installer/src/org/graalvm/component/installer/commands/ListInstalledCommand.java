/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.component.installer.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.graalvm.component.installer.Commands;
import org.graalvm.component.installer.MetadataException;

/**
 * Command to lists installed components.
 */
public class ListInstalledCommand extends QueryCommandBase {
    private List<String> expressions = Collections.emptyList();
    private Pattern filterPattern;

    public List<String> getExpressions() {
        return expressions;
    }

    public void setExpressions(List<String> expressions) {
        this.expressions = expressions;
    }

    @Override
    public Map<String, String> supportedOptions() {
        Map<String, String> m = new HashMap<>(super.supportedOptions());
        m.put(Commands.OPTION_URLS, "X"); // mask out
        m.put(Commands.OPTION_FILES, "X"); // mask out
        return m;
    }

    private void makeRegularExpression() {
        StringBuilder sb = new StringBuilder();
        for (String s : expressions) {
            if (sb.length() > 0) {
                sb.append("|");
            }
            sb.append(Pattern.quote(s));
        }
        if (sb.length() == 0) {
            sb.append(".*");
        }
        filterPattern = Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE);
    }

    List<String> findComponentIds() {
        Collection<String> comps = registry.getComponentIDs();
        List<String> ids = new ArrayList<>(comps.size());
        for (String s : comps) {
            if (filterPattern.matcher(s).find()) {
                ids.add(s);
            }
        }
        Collections.sort(ids);
        return ids;
    }

    @Override
    public int execute() throws IOException {
        if (input.optValue(Commands.OPTION_HELP) != null) {
            feedback.output("LIST_Help");
            return 0;
        }
        init(input, feedback);
        expressions = new ArrayList<>();
        while (input.hasParameter()) {
            expressions.add(input.nextParameter());
        }
        if (process()) {
            printComponents();
        }
        return 0;
    }

    boolean process() {
        makeRegularExpression();
        List<String> ids = findComponentIds();
        List<MetadataException> exceptions = new ArrayList<>();
        if (ids.isEmpty()) {
            feedback.message("LIST_NoComponentsFound");
            return false;
        }
        for (String id : ids) {
            try {
                addComponent(null, registry.loadSingleComponent(id, listFiles));
            } catch (MetadataException ex) {
                exceptions.add(ex);
            }
        }
        if (!exceptions.isEmpty()) {
            feedback.error("LIST_ErrorInComponentMetadata", null);
            for (Exception e : exceptions) {
                feedback.error("LIST_ErrorInComponentMetadataItem", e, e.getLocalizedMessage());
            }
        }
        return true;
    }
}
