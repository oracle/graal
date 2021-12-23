/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.test.jfr;

import java.io.DataInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class MetadataDescriptor {

    static final class Attribute {
        final String name;
        final String value;

        private Attribute(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    static final class Element {
        final String name;
        final List<Element> elements = new ArrayList<>();
        final List<Attribute> attributes = new ArrayList<>();

        Element(String name) {
            this.name = name;
        }

        String attribute(String attrName) {
            for (Attribute a : attributes) {
                if (a.name.equals(attrName)) {
                    return a.value;
                }
            }
            return null;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            try {
                prettyPrintXML(sb, "", this);
            } catch (IOException e) {
                // should not happen
            }
            return sb.toString();
        }

        long attribute(String attrName, long defaultValue) {
            String text = attribute(attrName);
            if (text == null) {
                return defaultValue;
            }
            return Long.parseLong(text);
        }

        String attribute(String attrName, String defaultValue) {
            String text = attribute(attrName);
            if (text == null) {
                return defaultValue;
            }
            return text;
        }

        List<Element> elements(String... names) {
            List<Element> filteredElements = new ArrayList<>();
            for (String elementName : names) {
                for (Element e : elements) {
                    if (e.name.equals(elementName)) {
                        filteredElements.add(e);
                    }
                }
            }
            return filteredElements;
        }

        void add(Element element) {
            elements.add(element);
        }

        void addAttribute(String attrName, Object value) {
            attributes.add(new Attribute(attrName, String.valueOf(value)));
        }
    }

    static final String ATTRIBUTE_ID = "id";
    static final String ATTRIBUTE_SIMPLE_TYPE = "simpleType";
    static final String ATTRIBUTE_GMT_OFFSET = "gmtOffset";
    static final String ATTRIBUTE_LOCALE = "locale";
    static final String ELEMENT_TYPE = "class";
    static final String ELEMENT_SETTING = "setting";
    static final String ELEMENT_ANNOTATION = "annotation";
    static final String ELEMENT_FIELD = "field";
    static final String ATTRIBUTE_SUPER_TYPE = "superType";
    static final String ATTRIBUTE_TYPE_ID = "class";
    static final String ATTRIBUTE_DIMENSION = "dimension";
    static final String ATTRIBUTE_NAME = "name";
    static final String ATTRIBUTE_CONSTANT_POOL = "constantPool";
    static final String ATTRIBUTE_DEFAULT_VALUE = "defaultValue";

    long gmtOffset;
    String locale;
    Element root;

    // package private
    MetadataDescriptor() {
    }

    private static void prettyPrintXML(Appendable sb, String indent, Element e) throws IOException {
        sb.append(indent + "<" + e.name);
        for (Attribute a : e.attributes) {
            sb.append(" ").append(a.name).append("=\"").append(a.value).append("\"");
        }
        if (e.elements.size() == 0) {
            sb.append("/");
        }
        sb.append(">\n");
        for (Element child : e.elements) {
            prettyPrintXML(sb, indent + "  ", child);
        }
        if (e.elements.size() != 0) {
            sb.append(indent).append("</").append(e.name).append(">\n");
        }
    }

    public int getGMTOffset() {
        return (int) gmtOffset;
    }

    public String getLocale() {
        return locale;
    }

    public static MetadataDescriptor read(DataInput input) throws IOException {
        MetadataReader r = new MetadataReader(input);
        return r.getDescriptor();
    }

    @Override
    public String toString() {
        return root.toString();
    }
}
