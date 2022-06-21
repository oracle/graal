/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.oracle.truffle.dsl.processor.model.MessageContainer;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

public class OperationDecisions {
    private final List<Quicken> quicken = new ArrayList<>();

    public OperationDecisions() {
    }

    public List<Quicken> getQuicken() {
        return quicken;
    }

    public OperationDecisions merge(OperationDecisions other, MessageContainer messager) {
        for (Quicken q : other.quicken) {
            if (quicken.contains(q)) {
                messager.addWarning("Duplicate optimization decision: %s", q);
            } else {
                quicken.add(q);
            }
        }

        return this;
    }

    public static final class Quicken {
        final String operation;
        final String[] specializations;

        public String getOperation() {
            return operation;
        }

        public String[] getSpecializations() {
            return specializations;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + Arrays.hashCode(specializations);
            result = prime * result + Objects.hash(operation);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Quicken other = (Quicken) obj;
            return Objects.equals(operation, other.operation) && Arrays.equals(specializations, other.specializations);
        }

        @Override
        public String toString() {
            return "Quicken [operation=" + operation + ", specializations=" + Arrays.toString(specializations) + "]";
        }

        private Quicken(String operation, String[] specializations) {
            this.operation = operation;

            Arrays.sort(specializations);
            this.specializations = specializations;
        }

        public static Quicken deserialize(JSONObject o) {
            String operation = o.getString("operation");

            JSONArray specs = o.getJSONArray("specializations");
            List<String> specializations = new ArrayList<>();

            for (int i = 0; i < specs.length(); i++) {
                specializations.add(specs.getString(i));
            }

            return new Quicken(operation, specializations.toArray(new String[specializations.size()]));
        }
    }

    public static OperationDecisions deserialize(JSONArray o, MessageContainer messager) {
        OperationDecisions decisions = new OperationDecisions();

        for (int i = 0; i < o.length(); i++) {
            if (o.get(i) instanceof String) {
                // strings are treated as comments
                continue;
            }

            JSONObject decision = o.getJSONObject(i);

            switch (decision.getString("type")) {
                case "Quicken":
                    Quicken q = Quicken.deserialize(decision);
                    decisions.quicken.add(q);
                    break;
                default:
                    messager.addWarning("Invalid optimization decision: '%s'", decision.getString("type"));
                    break;
            }
        }

        return decisions;
    }

    @Override
    public String toString() {
        return "OperationDecisions [quicken=" + quicken + "]";
    }
}
