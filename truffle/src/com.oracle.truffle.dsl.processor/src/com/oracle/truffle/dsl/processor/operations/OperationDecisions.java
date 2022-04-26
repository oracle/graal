package com.oracle.truffle.dsl.processor.operations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public class OperationDecisions {
    private final List<Quicken> quicken = new ArrayList<>();

    private OperationDecisions() {
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

        public static Quicken deserialize(XMLStreamReader rd) throws XMLStreamException {
            assert rd.getName().getLocalPart().equals("Quicken");
            String operation = rd.getAttributeValue(null, "operation");

            int state = rd.nextTag();
            if (state == XMLStreamReader.END_ELEMENT) {
                return new Quicken(operation, new String[0]);
            }

            assert state == XMLStreamReader.START_ELEMENT;
            assert rd.getName().getLocalPart().equals("Specializations");

            List<String> specializations = new ArrayList<>();

            state = rd.nextTag();
            while (state == XMLStreamReader.START_ELEMENT) {
                assert rd.getName().getLocalPart().equals("Specialization");

                String id = rd.getAttributeValue(null, "id");
                assert id != null;

                specializations.add(id);

                state = rd.nextTag();
                if (state != XMLStreamReader.END_ELEMENT) {
                    throw new AssertionError();
                }

                state = rd.nextTag();
            }

            assert state == XMLStreamReader.END_ELEMENT; // end of Specializations

            return new Quicken(operation, specializations.toArray(new String[specializations.size()]));
        }
    }

    public static OperationDecisions deserialize(XMLStreamReader rd) throws XMLStreamException {
        if (!rd.getName().getLocalPart().equals("Decisions")) {
            throw new AssertionError();
        }

        OperationDecisions decisions = new OperationDecisions();

        int state = rd.nextTag();
        while (state == XMLStreamReader.START_ELEMENT) {
            switch (rd.getLocalName()) {
                case "Quicken":
                    Quicken quicken = Quicken.deserialize(rd);
                    decisions.quicken.add(quicken);

                    state = rd.nextTag();
                    if (!(state == XMLStreamReader.END_ELEMENT && rd.getLocalName().equals("Quicken"))) {
                        throw new AssertionError(state);
                    }
                    break;
                default:
                    // TODO error handling
                    throw new AssertionError("invalid decision: " + rd.getLocalName());
            }

            state = rd.nextTag();
        }

        return decisions;
    }

    @Override
    public String toString() {
        return "OperationDecisions [quicken=" + quicken + "]";
    }
}
