/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.graphio.parsing;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateParser {
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{([pi])#([a-zA-Z0-9$_]+)(/([lms]))?}");

    public static List<TemplatePart> parseTemplate(String template) {
        List<TemplatePart> parts = new ArrayList<>();
        Matcher m = TEMPLATE_PATTERN.matcher(template);
        int lastEnd = 0;
        while (m.find()) {
            if (m.start() > lastEnd) {
                parts.add(new TemplatePart(template.substring(lastEnd, m.start())));
            }
            String name = m.group(2);
            String type = m.group(1);
            switch (type) {
                case "i":
                    parts.add(new TemplatePart(name, type, null));
                    break;
                case "p":
                    String length = m.group(4);
                    parts.add(new TemplatePart(name, type, length));
                    break;
                default:
                    parts.add(new TemplatePart("#?#"));
                    break;
            }
            lastEnd = m.end();
        }
        if (lastEnd < template.length()) {
            parts.add(new TemplatePart(template.substring(lastEnd)));
        }
        return parts;
    }

    public static class TemplatePart {
        public final String value;
        public final boolean isReplacement;
        public final String name;
        public final String type;
        public final String length;

        public TemplatePart(String name, String type, String length) {
            this.name = name;
            this.type = type;
            this.length = length;
            this.value = null;
            this.isReplacement = true;
        }

        public TemplatePart(String value) {
            this.value = value;
            this.isReplacement = false;
            name = null;
            type = null;
            length = null;
        }
    }
}
