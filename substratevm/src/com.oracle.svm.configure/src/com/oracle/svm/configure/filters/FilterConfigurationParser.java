/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.filters;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Map;

import com.oracle.svm.core.configure.ConfigurationParser;
import com.oracle.svm.core.util.json.JSONParser;
import com.oracle.svm.core.util.json.JSONParserException;

public class FilterConfigurationParser extends ConfigurationParser {
    private final RuleNode rootNode;

    public FilterConfigurationParser(RuleNode rootNode) {
        assert rootNode != null;
        this.rootNode = rootNode;
    }

    @Override
    public void parseAndRegister(Reader reader) throws IOException {
        Object json = new JSONParser(reader).parse();
        parseTopLevelObject(asMap(json, "First level of document must be an object"));
    }

    private void parseTopLevelObject(Map<String, Object> top) {
        Object rulesObject = null;
        for (Map.Entry<String, Object> pair : top.entrySet()) {
            if ("rules".equals(pair.getKey())) {
                rulesObject = pair.getValue();
            } else {
                throw new JSONParserException("Unknown attribute '" + pair.getKey() + "' (supported attributes: name) in resource definition");
            }
        }
        if (rulesObject != null) {
            List<Object> rulesList = asList(rulesObject, "Attribute 'list' must be a list of rule objects");
            for (Object entryObject : rulesList) {
                parseEntry(entryObject);
            }
        }
    }

    private void parseEntry(Object entryObject) {
        Map<String, Object> entry = asMap(entryObject, "Filter entries must be objects");
        Object qualified = null;
        RuleNode.Inclusion inclusion = null;
        String exactlyOneMessage = "Exactly one of attributes 'includeClasses' and 'excludeClasses' must be specified for a filter entry";
        for (Map.Entry<String, Object> pair : entry.entrySet()) {
            if (qualified != null) {
                throw new JSONParserException(exactlyOneMessage);
            }
            qualified = pair.getValue();
            if ("includeClasses".equals(pair.getKey())) {
                inclusion = RuleNode.Inclusion.Include;
            } else if ("excludeClasses".equals(pair.getKey())) {
                inclusion = RuleNode.Inclusion.Exclude;
            } else {
                throw new JSONParserException("Unknown attribute '" + pair.getKey() + "' (supported attributes: 'includeClasses', 'excludeClasses') in filter");
            }
        }
        if (qualified == null) {
            throw new JSONParserException(exactlyOneMessage);
        }
        rootNode.addOrGetChildren(asString(qualified), inclusion);
    }
}
