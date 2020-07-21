/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @since 19.0
 */
public final class OptionDescriptor {

    private final OptionKey<?> key;
    private final String name;
    private final String help;
    private final OptionCategory category;
    private final OptionStability stability;
    private final boolean deprecated;
    private final String deprecationMessage;

    OptionDescriptor(OptionKey<?> key, String name, String help, OptionCategory category, OptionStability stability, boolean deprecated, String deprecationMessage) {
        this.key = key;
        this.name = name;
        this.help = help;
        this.category = category;
        this.stability = stability;
        this.deprecated = deprecated;
        this.deprecationMessage = deprecationMessage;
    }

    /**
     * Returns the name of the option that this descriptor represents.
     *
     * @since 19.0
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the key for this option.
     *
     * @since 19.0
     */
    public OptionKey<?> getKey() {
        return key;
    }

    /**
     * Returns <code>true</code> if this option was marked deprecated. This indicates that the
     * option is going to be removed in a future release or its use is not recommended.
     *
     * @since 19.0
     */
    public boolean isDeprecated() {
        return deprecated;
    }

    /**
     * Returns the deprecation reason and the recommended fix.
     *
     * @since 20.1.0
     */
    public String getDeprecationMessage() {
        return deprecationMessage;
    }

    /**
     * Returns <code>true</code> if this option is an option map. Options maps allow to collect
     * key=value pairs whose keys are unknown beforehand e.g. user defined properties.
     *
     * @since 19.2
     */
    public boolean isOptionMap() {
        return getKey().getType().isOptionMap();
    }

    /**
     * Returns the user category of this option.
     *
     * @since 19.0
     */
    public OptionCategory getCategory() {
        return category;
    }

    /**
     * Returns the stability of this option.
     *
     * @since 19.0
     */
    public OptionStability getStability() {
        return stability;
    }

    /**
     * Returns a human-readable description on how to use the option.
     *
     * @since 19.0
     */
    public String getHelp() {
        return help;
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public String toString() {
        return "OptionDescriptor [key=" + key + ", help=" + help + ", category=" + category + ", deprecated=" + deprecated + ", optionMap=" + isOptionMap() + "]";
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (deprecated ? 1231 : 1237);
        result = prime * result + ((help == null) ? 0 : help.hashCode());
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        result = prime * result + ((category == null) ? 0 : category.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * @since 19.0
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
                        Objects.equals(category, other.category);
    }

    /**
     * Creates a new option descriptor builder by key. The option group and name is inferred by the
     * key.
     *
     * @since 19.0
     */
    public static <T> Builder newBuilder(OptionKey<T> key, String name) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(name);
        return EMPTY.new Builder(key, name);
    }

    private static final OptionDescriptor EMPTY = new OptionDescriptor(null, null, null, null, null, false, null);

    /**
     * Represents an option descriptor builder.
     *
     * @since 19.0
     */
    public final class Builder {

        private final OptionKey<?> key;
        private final String name;
        private boolean deprecated = false;
        private String deprecationMessage = "";
        private OptionCategory category = OptionCategory.INTERNAL;
        private OptionStability stability = OptionStability.EXPERIMENTAL;
        private String help = "";

        Builder(OptionKey<?> key, String name) {
            this.key = key;
            this.name = name;
        }

        /**
         * Defines the user category for this option. The default value is
         * {@link OptionCategory#INTERNAL}.
         *
         * @since 19.0
         */
        public Builder category(@SuppressWarnings("hiding") OptionCategory category) {
            Objects.requireNonNull(category);
            this.category = category;
            return this;
        }

        /**
         * Defines the stability of this option. The default value is
         * {@link OptionStability#EXPERIMENTAL}.
         *
         * @since 19.0
         */
        public Builder stability(@SuppressWarnings("hiding") OptionStability stability) {
            Objects.requireNonNull(stability);
            this.stability = stability;
            return this;
        }

        /**
         * Defines if this option is deprecated. The default value for deprecated is
         * <code>false</code>. This can be used to evolve options between releases.
         *
         * @since 19.0
         */
        public Builder deprecated(@SuppressWarnings("hiding") boolean deprecated) {
            this.deprecated = deprecated;
            return this;
        }

        /**
         * Specifies a human-readable description on how to use the option.
         *
         * @since 19.0
         */
        public Builder help(@SuppressWarnings("hiding") String help) {
            Objects.requireNonNull(help);
            this.help = help;
            return this;
        }

        /**
         * Specifies a human-readable deprecation reason and the recommended fix.
         *
         * @since 20.1.0
         */
        public Builder deprecationMessage(@SuppressWarnings("hiding") String deprecationMessage) {
            Objects.requireNonNull(deprecationMessage);
            this.deprecationMessage = deprecationMessage;
            return this;
        }

        /**
         * Builds and returns a new option descriptor.
         *
         * @since 19.0
         */
        public OptionDescriptor build() {
            return new OptionDescriptor(key, name, help, category, stability, deprecated, deprecationMessage);
        }
    }

}
