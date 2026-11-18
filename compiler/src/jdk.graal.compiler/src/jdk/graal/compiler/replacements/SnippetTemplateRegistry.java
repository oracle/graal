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
package jdk.graal.compiler.replacements;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.api.replacements.SnippetTemplateCache;
import jdk.graal.compiler.debug.GraalError;

/**
 * Registry for snippet template caches shared by replacements instances. Register caches before
 * concurrent lookups. A read-only view permits lookup but rejects registration and sealing. The
 * registry does not make registered cache objects immutable; the caches' contracts still apply.
 */
public abstract sealed class SnippetTemplateRegistry permits SnippetTemplateRegistry.RegistrationOwner, SnippetTemplateRegistry.ReadOnlyView {
    /** Creates a registry whose owner may register templates and seal registration. */
    public static SnippetTemplateRegistry create() {
        return new RegistrationOwner();
    }

    /** Returns the registered cache for {@code templatesClass}, or {@code null} if none is registered. */
    public abstract <T extends SnippetTemplateCache> T get(Class<T> templatesClass);

    /**
     * Returns an idempotent live view that permits lookup but rejects registration and sealing. Calling
     * this method on a read-only view returns that view.
     */
    public abstract SnippetTemplateRegistry readOnlyView();

    public void register(@SuppressWarnings("unused") SnippetTemplateCache templates) {
        throw GraalError.shouldNotReachHere("Only the owning replacements may register snippet templates");
    }

    /** Ends registration; existing views remain readable. Only the owner may seal the registry. */
    public void seal() {
        throw GraalError.shouldNotReachHere("Only the owning replacements may seal snippet templates");
    }

    private static final class RegistrationOwner extends SnippetTemplateRegistry {
        // Class names keep keys stable across VM executions for replay compilation.
        private final EconomicMap<String, SnippetTemplateCache> templates = EconomicMap.create(Equivalence.DEFAULT);
        private boolean sealed;

        @Override
        public <T extends SnippetTemplateCache> T get(Class<T> templatesClass) {
            return templatesClass.cast(templates.get(templatesClass.getName()));
        }

        @Override
        public SnippetTemplateRegistry readOnlyView() {
            return new ReadOnlyView(this);
        }

        @Override
        public void register(SnippetTemplateCache template) {
            GraalError.guarantee(!sealed, "Snippet template registration is sealed");
            String name = template.getClass().getName();
            assert !templates.containsKey(name) : "Snippet templates already registered: " + name;
            templates.put(name, template);
        }

        @Override
        public void seal() {
            sealed = true;
        }
    }

    private static final class ReadOnlyView extends SnippetTemplateRegistry {
        private final SnippetTemplateRegistry owner;

        private ReadOnlyView(SnippetTemplateRegistry owner) {
            this.owner = owner;
        }

        @Override
        public <T extends SnippetTemplateCache> T get(Class<T> templatesClass) {
            return owner.get(templatesClass);
        }

        @Override
        public SnippetTemplateRegistry readOnlyView() {
            return this;
        }
    }
}
