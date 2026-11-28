/*
 * Copyright (c) 2011, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.replacements.SnippetTemplateCache;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.replacements.SnippetTemplateRegistry;

public class SnippetTemplateRegistryTest {
    private static final class Templates implements SnippetTemplateCache {
    }

    @Test
    public void sharedViewTracksRegistrationAndRemainsReadableAfterSealing() {
        var registration = SnippetTemplateRegistry.create();
        var view = registration.readOnlyView();
        Assert.assertNull(view.get(Templates.class));
        Assert.assertSame(view, view.readOnlyView());
        Assert.assertThrows(GraalError.class, () -> view.register(new Templates()));
        Assert.assertThrows(GraalError.class, view::seal);
        var templates = new Templates();
        registration.register(templates);
        Assert.assertSame(templates, view.get(Templates.class));
        registration.seal();
        Assert.assertSame(templates, view.get(Templates.class));
        Assert.assertThrows(GraalError.class, () -> registration.register(new Templates()));
        Assert.assertSame(templates, view.get(Templates.class));
    }
}
