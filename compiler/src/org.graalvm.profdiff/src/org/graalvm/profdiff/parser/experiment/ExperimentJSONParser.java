/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.parser.experiment;

import java.io.IOException;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.profdiff.core.ExperimentId;
import org.graalvm.util.json.JSONParser;
import org.graalvm.util.json.JSONParserException;

/**
 * A wrapper around {@link JSONParser}, which aids the parsing of the Java representation of a JSON
 * related to an experiment.
 *
 * The source file view is read and converted to a Java representation of the JSON, using the
 * {@link JSONParser}. The returned (root) literal is then wrapped as a {@link JSONLiteral}, which
 * provides type checks and wraps its properties (if the literal is a map). This allows us to both
 * have an efficient JSON representation (using plain Java types) and also provide helper methods on
 * top of it. These abstractions should be easily optimized away by the compiler.
 */
public class ExperimentJSONParser {
    /**
     * A wrapper around the Java representation of a JSON literal. Provides type conversions, which
     * throw a parser error if the types don't match.
     */
    public final class JSONLiteral {
        /**
         * The Java value of the literal.
         */
        private final Object object;

        /**
         * The name of the literal.
         */
        private final String objectName;

        private JSONLiteral(Object object, String objectName) {
            this.object = object;
            this.objectName = objectName;
        }

        @SuppressWarnings("unchecked")
        private <T> T asInstanceOf(Class<T> clazz) throws ExperimentParserTypeError {
            if (clazz.isInstance(object)) {
                return (T) object;
            }
            throw new ExperimentParserTypeError(experimentId, file.getSymbolicPath(), objectName, clazz, object);
        }

        private <T> T asNullableInstanceOf(Class<T> clazz) throws ExperimentParserTypeError {
            if (object == null) {
                return null;
            }
            return asInstanceOf(clazz);
        }

        public boolean isNull() {
            return object == null;
        }

        public String asString() throws ExperimentParserTypeError {
            return asInstanceOf(String.class);
        }

        public boolean asBoolean() throws ExperimentParserTypeError {
            return asInstanceOf(Boolean.class);
        }

        public int asInt() throws ExperimentParserTypeError {
            return asInstanceOf(Integer.class);
        }

        public long asLong() throws ExperimentParserTypeError {
            return asInstanceOf(Number.class).longValue();
        }

        @SuppressWarnings("unchecked")
        public JSONMap asMap() throws ExperimentParserTypeError {
            return new JSONMap(asInstanceOf(EconomicMap.class));
        }

        @SuppressWarnings("unchecked")
        public Iterable<JSONLiteral> asList() throws ExperimentParserTypeError {
            List<Object> objects = asInstanceOf(List.class);
            return () -> objects.stream().map(item -> new JSONLiteral(item, null)).iterator();
        }

        public String asNullableString() throws ExperimentParserTypeError {
            return asNullableInstanceOf(String.class);
        }

        public Integer asNullableInteger() throws ExperimentParserTypeError {
            return asNullableInstanceOf(Integer.class);
        }
    }

    /**
     * A wrapper around a JSON map.
     */
    public final class JSONMap {
        private final EconomicMap<String, Object> map;

        private JSONMap(EconomicMap<String, Object> map) {
            this.map = map;
        }

        /**
         * Gets a Java representation of the map.
         */
        public EconomicMap<String, Object> getInnerMap() {
            return map;
        }

        /**
         * Gets a property of the map by key. If the property with the given key does not exist,
         * returns a literal representing {@code null}.
         *
         * @param key the name of the property
         * @return a JSON literal
         */
        public JSONLiteral property(String key) {
            return new JSONLiteral(map.get(key), key);
        }
    }

    /**
     * The experiment ID to which this JSON belongs.
     */
    private final ExperimentId experimentId;

    /**
     * The file view which contains the source string.
     */
    private final FileView file;

    public ExperimentJSONParser(ExperimentId experimentId, FileView file) {
        this.experimentId = experimentId;
        this.file = file;
    }

    /**
     * Parses the source string as a JSON literal.
     *
     * @return the parsed JSON literal
     * @throws ExperimentParserError failed to parse the experiment
     */
    public JSONLiteral parse() throws ExperimentParserError, IOException {
        JSONParser jsonParser = new JSONParser(file.readFully());
        try {
            return new JSONLiteral(jsonParser.parse(), null);
        } catch (JSONParserException parserException) {
            throw new ExperimentParserError(experimentId, file.getSymbolicPath(), parserException.getMessage());
        }
    }
}
