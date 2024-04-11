/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.search;

import jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames;
import jdk.graal.compiler.graphio.parsing.model.Properties;
import jdk.graal.compiler.graphio.parsing.model.Properties.PropertyMatcher;
import org.openide.util.NbBundle;

/**
 * @author sdedic
 */
public class Criteria {
    private Properties.PropertyMatcher matcher;

    public Criteria() {
    }

    public Properties.PropertyMatcher getMatcher() {
        return matcher;
    }

    public Criteria setMatcher(PropertyMatcher matcher) {
        this.matcher = matcher;
        return this;
    }

    public String toQueryString() {
        if (matcher == PropertyMatcher.ALL) {
            return "";
        } else if (matcher instanceof Properties.RegexpPropertyMatcher) {
            String n = matcher.getName();
            Properties.RegexpPropertyMatcher rm = (Properties.RegexpPropertyMatcher) matcher;
            if (n.equals(KnownPropertyNames.PROPNAME_NAME)) {
                return rm.getRegexpValue();
            } else {
                return n + "=" + rm.getRegexpValue();
            }
        } else {
            return null;
        }
    }

    @NbBundle.Messages({
            "DISPLAY_CriteriaAll=Everything",
            "# {0} - the regexp pattern",
            "DISPLAY_CriteriaRegexpName={0}",
            "# {0} - the regexp pattern",
            "# {1} - the property name",
            "DISPLAY_CriteriaRegexpPropertyValue={0} in {1}",
    })
    public String toDisplayString(boolean allowHtml) {
        if (matcher == PropertyMatcher.ALL) {
            return Bundle.DISPLAY_CriteriaAll();
        } else if (matcher instanceof Properties.RegexpPropertyMatcher) {
            String n = matcher.getName();
            Properties.RegexpPropertyMatcher rm = (Properties.RegexpPropertyMatcher) matcher;
            String rtext = unquoteRegexpValue(rm.getRegexpValue());
            if (n.equals(KnownPropertyNames.PROPNAME_NAME)) {
                return Bundle.DISPLAY_CriteriaRegexpName(rtext);
            } else {
                return Bundle.DISPLAY_CriteriaRegexpPropertyValue(rtext, n);
            }
        } else {
            return null;
        }
    }

    private String unquoteRegexpValue(String val) {
        int start = val.indexOf("\\Q");
        if (start == -1) {
            return val;
        }
        int last = 0;
        StringBuilder res = new StringBuilder();
        while (start >= 0) {
            res.append(val.substring(last, start));
            int end = val.indexOf("\\E", start + 1);
            if (end < 0) {
                last = start;
            } else {
                res.append(val.substring(start + 2, end));
                last = end + 2;
            }
            start = val.indexOf("\\Q", last);
        }
        res.append(val.substring(last));
        return res.toString();
    }
}
