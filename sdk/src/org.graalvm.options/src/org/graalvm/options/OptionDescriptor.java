/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.options;

import java.util.Objects;

/**
 * Represents metadata for a single option.
 *
 * @since 1.0
 */
public final class OptionDescriptor {

    private final OptionKey<?> key;
    private final String name;
    private final String help;
    private final OptionCategory kind;
    private final boolean deprecated;

    OptionDescriptor(OptionKey<?> key, String name, String help, OptionCategory kind, boolean deprecated) {
        this.key = key;
        this.name = name;
        this.help = help;
        this.kind = kind;
        this.deprecated = deprecated;
    }

    /**
     * Returns the name of the option that this descriptor represents.
     *
     * @since 1.0
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the key for this option.
     *
     * @since 1.0
     */
    public OptionKey<?> getKey() {
        return key;
    }

    /**
     * Returns <code>true</code> if this option was marked deprecated. This indicates that the
     * option is going to be removed in a future release or its use is not recommended.
     *
     * @since 1.0
     */
    public boolean isDeprecated() {
        return deprecated;
    }

    /**
     * Returns the user category of this option.
     *
     * @since 1.0
     */
    public OptionCategory getCategory() {
        return kind;
    }

    /**
     * Returns a human-readable description on how to use the option.
     *
     * @since 1.0
     */
    public String getHelp() {
        return help;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0
     */
    @Override
    public String toString() {
        return "OptionDescriptor [key=" + key + ", help=" + help + ", kind=" + kind + ", deprecated=" + deprecated + "]";
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (deprecated ? 1231 : 1237);
        result = prime * result + ((help == null) ? 0 : help.hashCode());
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        result = prime * result + ((kind == null) ? 0 : kind.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * @since 1.0
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (getClass() != obj.getClass()) {
            return false;
        }
        OptionDescriptor other = (OptionDescriptor) obj;
        return Objects.equals(name, other.name) &&
                        Objects.equals(deprecated, other.deprecated) &&
                        Objects.equals(help, other.help) &&
                        Objects.equals(key, other.key) &&
                        Objects.equals(kind, other.kind);
    }

    /**
     * Creates a new option descriptor builder by key. The option group and name is inferred by the
     * key.
     *
     * @since 1.0
     */
    public static <T> Builder newBuilder(OptionKey<T> key, String name) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(name);
        return EMPTY.new Builder(key, name);
    }

    private static final OptionDescriptor EMPTY = new OptionDescriptor(null, null, null, null, false);

    /**
     * Represents an option descriptor builder.
     *
     * @since 1.0
     */
    public final class Builder {

        private final OptionKey<?> key;
        private final String name;
        private boolean deprecated;
        private OptionCategory category;
        private String help;

        Builder(OptionKey<?> key, String name) {
            this.key = key;
            this.name = name;
        }

        /**
         * Defines the user category for this option. The default value is
         * {@link OptionCategory#DEBUG}.
         *
         * @since 1.0
         */
        public Builder category(@SuppressWarnings("hiding") OptionCategory category) {
            Objects.requireNonNull(category);
            this.category = category;
            return this;
        }

        /**
         * Defines if this option is deprecated. The default value for deprecated is
         * <code>false</code>. This can be used to evolve options between releases.
         *
         * @since 1.0
         */
        public Builder deprecated(@SuppressWarnings("hiding") boolean deprecated) {
            this.deprecated = deprecated;
            return this;
        }

        /**
         * Specifies a human-readable description on how to use the option.
         *
         * @since 1.0
         */
        public Builder help(@SuppressWarnings("hiding") String help) {
            Objects.requireNonNull(help);
            this.help = help;
            return this;
        }

        /**
         * Builds and returns a new option descriptor.
         *
         * @since 1.0
         */
        public OptionDescriptor build() {
            return new OptionDescriptor(key, name, help == null ? "" : help, category == null ? OptionCategory.DEBUG : category, deprecated);
        }

    }

}
