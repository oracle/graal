/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Suspension filter that's composed of a main one and several others suspension filters.
 */
class SuspensionFilterCompound extends SuspensionFilter {

    private volatile SuspensionFilter filter;
    private volatile Set<SuspensionFilter> others;

    @Override
    public boolean isIgnoreLanguageContextInitialization() {
        boolean ignore = filter.isIgnoreLanguageContextInitialization();
        if (!ignore) {
            Set<SuspensionFilter> filters = others;
            if (filters != null) {
                for (SuspensionFilter f : filters) {
                    if (f.isIgnoreLanguageContextInitialization()) {
                        return true;
                    }
                }
            }
        }
        return ignore;
    }

    @Override
    public boolean isIgnoreInternal() {
        boolean ignore = filter.isIgnoreInternal();
        if (!ignore) {
            Set<SuspensionFilter> filters = others;
            if (filters != null) {
                for (SuspensionFilter f : filters) {
                    if (f.isIgnoreInternal()) {
                        return true;
                    }
                }
            }
        }
        return ignore;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Predicate<SourceSection> getSourceFilter() {
        Set<SuspensionFilter> filters = others;
        if (filters == null) {
            return filter.getSourceFilter();
        }
        List<Predicate<SourceSection>> predicates = new ArrayList<>();
        Predicate<SourceSection> p = filter.getSourceFilter();
        if (p != null) {
            predicates.add(p);
        }
        for (SuspensionFilter f : filters) {
            p = f.getSourceFilter();
            if (p != null) {
                predicates.add(p);
            }
        }
        if (predicates.isEmpty()) {
            return null;
        }
        if (predicates.size() == 1) {
            return predicates.get(0);
        }
        final Predicate<SourceSection>[] sourcePredicates = predicates.toArray(new Predicate[predicates.size()]);
        return new Predicate<SourceSection>() {
            @Override
            @ExplodeLoop
            public boolean test(SourceSection ss) {
                for (Predicate<SourceSection> p : sourcePredicates) {
                    if (!p.test(ss)) {
                        return false;
                    }
                }
                return true;
            }
        };
    }

    void setOthers(Set<SuspensionFilter> steppingFilters) {
        others = steppingFilters;
    }

    void setMain(SuspensionFilter steppingFilter) {
        filter = steppingFilter;
    }

}
