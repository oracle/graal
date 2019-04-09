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
package com.oracle.svm.agent.restrict;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.oracle.svm.hosted.ResourcesFeature.ResourcesRegistry;

public class ResourceConfiguration {
    public static class ParserAdapter implements ResourcesRegistry {
        private final ResourceConfiguration configuration;

        public ParserAdapter(ResourceConfiguration configuration) {
            this.configuration = configuration;
        }

        @Override
        public void addResources(String pattern) {
            configuration.add(Pattern.compile(pattern));
        }

        @Override
        public void addResourceBundles(String name) {
        }
    }

    private List<Pattern> patterns = new ArrayList<>();

    public void add(Pattern p) {
        patterns.add(p);
    }

    public boolean anyMatches(String s) {
        /*
         * Naive -- if the need arises, we could match in the order of most frequently matched
         * patterns, or somehow merge the patterns into a single big pattern.
         */
        for (Pattern pattern : patterns) {
            if (pattern.matcher(s).matches()) {
                return true;
            }
        }
        return false;
    }
}
