package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

public class OperationDecisions {
    private final List<Quicken> quicken = new ArrayList<>();

    public OperationDecisions() {
    }

    public List<Quicken> getQuicken() {
        return quicken;
    }

    public OperationDecisions merge(OperationDecisions other) {
        for (Quicken q : other.quicken) {
            if (quicken.contains(q)) {
                throw new AssertionError("duplicate decision");
            }
            quicken.add(q);
        }

        return this;
    }

    public static class Quicken {
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
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
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

    public static OperationDecisions deserialize(JSONArray o) {
        OperationDecisions decisions = new OperationDecisions();

        for (int i = 0; i < o.length(); i++) {
            JSONObject decision = o.getJSONObject(i);

            switch (decision.getString("type")) {
                case "Quicken":
                    Quicken q = Quicken.deserialize(decision);
                    decisions.quicken.add(q);
                    break;
                default:
                    // TODO error handling
                    throw new AssertionError("invalid decision type" + decision.getString("type"));
            }
        }

        return decisions;
    }

    @Override
    public String toString() {
        return "OperationDecisions [quicken=" + quicken + "]";
    }
}
