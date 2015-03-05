/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.source;

/**
 * An observer of events related to {@link Source}s: creating and tagging.
 */
public interface SourceListener {

    /**
     * Notifies that a new {@link Source} has just been created.
     */
    void sourceCreated(Source source);

    /**
     * Notifies that a {@link SourceTag} has been newly added to the set of tags associated with a
     * {@link Source} via {@link Source#tagAs(SourceTag)}.
     * <p>
     * The {@linkplain SourceTag tags} at a {@link Source} are a <em>set</em>; this notification
     * will only be delivered the first time a particular {@linkplain SourceTag tag} is added at a
     * {@link Source}.
     *
     * @param source where a tag has been added
     * @param tag the tag that has been newly added (subsequent additions of the tag are
     *            unreported).
     */
    void sourceTaggedAs(Source source, SourceTag tag);

}
