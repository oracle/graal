package com.oracle.truffle.api.operation.tracing;

import static com.oracle.truffle.api.operation.tracing.XmlUtils.readAttribute;
import static com.oracle.truffle.api.operation.tracing.XmlUtils.readAttributeInt;
import static com.oracle.truffle.api.operation.tracing.XmlUtils.readCharactersInt;
import static com.oracle.truffle.api.operation.tracing.XmlUtils.readCharactersLong;
import static com.oracle.truffle.api.operation.tracing.XmlUtils.readEndElement;
import static com.oracle.truffle.api.operation.tracing.XmlUtils.readStartElement;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.operation.OperationsNode;

public class ExecutionTracer {
    private static final Map<String, ExecutionTracer> TRACERS = new HashMap<>();

    public static ExecutionTracer get(String key) {
        return TRACERS.computeIfAbsent(key, ExecutionTracer::new);
    }

    private final String key;

    public ExecutionTracer(String key) {
        assert TRACERS.get(key) == null;
        this.key = key;
    }

    static {

        // deser
        String stateFile = "/tmp/state.xml";

        try {
            FileInputStream fi = new FileInputStream(new File(stateFile));
            XMLStreamReader rd = XMLInputFactory.newDefaultFactory().createXMLStreamReader(fi);

            readStartElement(rd, "STATE");

            int state = rd.next();
            while (state == XMLStreamReader.START_ELEMENT) {
                assert rd.getName().getLocalPart().equals("T");

                ExecutionTracer tr = ExecutionTracer.deserializeState(rd);
                TRACERS.put(tr.key, tr);

                readEndElement(rd, "T");

                state = rd.next();
            }
        } catch (FileNotFoundException ex) {
            // we ignore FNFE since that is true on the first run
        } catch (Exception e1) {
            throw new RuntimeException("error deserializing tracer state", e1);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {

            // ser
            try {
                FileOutputStream fo = new FileOutputStream(new File(stateFile));
                XMLStreamWriter wr = XMLOutputFactory.newDefaultFactory().createXMLStreamWriter(fo);

                wr.writeStartDocument();
                wr.writeStartElement("STATE");
                for (Map.Entry<String, ExecutionTracer> ent : TRACERS.entrySet()) {
                    ExecutionTracer tracer = ent.getValue();
                    wr.writeStartElement("T");
                    wr.writeAttribute("key", tracer.key);
                    tracer.serializeState(wr);
                    wr.writeEndElement();

                    tracer.dump(new PrintWriter(System.out));
                }
                wr.writeEndElement();
                wr.writeEndDocument();

            } catch (Exception e) {
                e.printStackTrace();
                System.err.flush();
            }
        }));
    }

    public static final int INSTRUCTION_TYPE_OTHER = 0;
    public static final int INSTRUCTION_TYPE_BRANCH = 1;
    public static final int INSTRUCTION_TYPE_BRANCH_COND = 2;
    public static final int INSTRUCTION_TYPE_LOAD_LOCAL = 3;
    public static final int INSTRUCTION_TYPE_STORE_LOCAL = 4;
    public static final int INSTRUCTION_TYPE_LOAD_ARGUMENT = 5;
    public static final int INSTRUCTION_TYPE_LOAD_CONSTANT = 6;
    public static final int INSTRUCTION_TYPE_RETURN = 7;
    public static final int INSTRUCTION_TYPE_CUSTOM = 8;

    private static final int TRACE_LENGTH = 8;

    private static class InstructionSequence {
        final int hash;
        final int[] instrs;

        public InstructionSequence(int[] instrs) {
            this.instrs = instrs;
            int h = 0;
            for (int i : instrs) {
                h = h * 31 + i;
            }
            hash = h;
        }

        public InstructionSequence add(int next) {
            int[] created = new int[instrs.length];
            System.arraycopy(instrs, 1, created, 0, instrs.length - 1);
            created[created.length - 1] = next;
            return new InstructionSequence(created);
        }

        public boolean isValid() {
            for (int i : instrs) {
                if (i == 0) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof InstructionSequence))
                return false;
            InstructionSequence other = (InstructionSequence) obj;
            if (other.hash != hash || instrs.length != other.instrs.length) {
                return false;
            }
            for (int i = 0; i < instrs.length; i++) {
                if (instrs[i] != other.instrs[i]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            for (int i = 0; i < instrs.length; i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                sb.append(String.format("%3d", instrs[i]));
            }
            sb.append(']');
            return sb.toString();
        }
    }

    final Map<InstructionSequence, Long> occurences = new HashMap<>();
    InstructionSequence[] last = new InstructionSequence[TRACE_LENGTH - 1];

    private static class SpecializationOccurence {
        final int instructionId;
        final int specializationId;

        SpecializationOccurence(int instructionId, int specializationId) {
            this.instructionId = instructionId;
            this.specializationId = specializationId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(instructionId, specializationId);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            SpecializationOccurence other = (SpecializationOccurence) obj;
            return instructionId == other.instructionId && specializationId == other.specializationId;
        }

        @Override
        public String toString() {
            return "SpecializationOccurence [instructionId=" + instructionId + ", specializationId=" + specializationId + "]";
        }
    }

    private static class ActiveSpecializationOccurence {
        final String instructionId;
        final boolean[] activeSpecializations;

        ActiveSpecializationOccurence(String instructionId, boolean... activeSpecializations) {
            this.instructionId = instructionId;
            this.activeSpecializations = activeSpecializations;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + Arrays.hashCode(activeSpecializations);
            result = prime * result + Objects.hash(instructionId);
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
            ActiveSpecializationOccurence other = (ActiveSpecializationOccurence) obj;
            return Arrays.equals(activeSpecializations, other.activeSpecializations) && instructionId.equals(other.instructionId);
        }

        @Override
        public String toString() {
            return "ActiveSpecializationOccurence[instructionId=" + instructionId + ", activeSpecializations=" + Arrays.toString(activeSpecializations) + "]";
        }

        void serializeState(XMLStreamWriter wr) throws XMLStreamException {
            wr.writeAttribute("i", instructionId);
            wr.writeAttribute("n", "" + activeSpecializations.length);

            wr.writeStartElement("A");
            int i = 0;
            for (boolean act : activeSpecializations) {
                if (act) {
                    wr.writeStartElement("a");
                    wr.writeCharacters("" + i);
                    wr.writeEndElement();
                }
                i++;
            }
            wr.writeEndElement();
        }

        static ActiveSpecializationOccurence deserializeState(XMLStreamReader rd) throws XMLStreamException {
            String instructionId = readAttribute(rd, "i");
            int count = readAttributeInt(rd, "n");

            boolean[] activeSpecializations = new boolean[count];

            readStartElement(rd, "A");

            int state = rd.next();
            while (state == XMLStreamReader.START_ELEMENT) {
                assert rd.getName().getLocalPart().equals("a");

                int index = readCharactersInt(rd);
                activeSpecializations[index] = true;

                readEndElement(rd, "a");
                state = rd.next();
            }
            // end ASs

            assert state == XMLStreamReader.END_ELEMENT;

            return new ActiveSpecializationOccurence(instructionId, activeSpecializations);
        }
    }

    private final Map<ActiveSpecializationOccurence, Long> activeSpecializationsMap = new HashMap<>();

    private final void resetLast() {
        for (int i = 2; i <= TRACE_LENGTH; i++) {
            last[i - 2] = new InstructionSequence(new int[i]);
        }
    }

    @SuppressWarnings("unused")
    @TruffleBoundary
    public final void startFunction(OperationsNode node) {
        resetLast();
    }

    @TruffleBoundary
    public final void endFunction() {
        resetLast();
    }

    @SuppressWarnings("unused")
    @TruffleBoundary
    public final void traceInstruction(int bci, String id, int instructionType, Object... arguments) {
        if (instructionType == INSTRUCTION_TYPE_CUSTOM) {
            assert arguments.length == 1;
            boolean[] activeSpecs = (boolean[]) arguments[0];

            ActiveSpecializationOccurence occ = new ActiveSpecializationOccurence(id, activeSpecs);
            Long cur = activeSpecializationsMap.get(occ);
            long next = cur == null ? 1 : cur + 1;
            activeSpecializationsMap.put(occ, next);
        }
    }

    @TruffleBoundary
    public final void traceSpecialization(int bci, String id, int specializationId, Object... arguments) {
        // System.out.printf(" [TS] %04x %d %d %s%n", bci, id, specializationId,
        // List.of(arguments));
    }

    @SuppressWarnings({"unused", "static-method"})
    public final Object tracePop(Object value) {
        return value;
    }

    @SuppressWarnings({"unused", "static-method"})
    public final Object tracePush(Object value) {
        return value;
    }

    @SuppressWarnings("unused")
    public final void traceException(Throwable ex) {
    }

    private static final long score(Map.Entry<InstructionSequence, Long> ent) {
        return ent.getValue() * ent.getKey().instrs.length;
    }

    public final void dump(PrintWriter writer) {
        writer.println("-------------------------------------------------------------");
        activeSpecializationsMap.entrySet().stream() //
                        .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue())) //
                        .limit(50) //
                        .forEachOrdered(e -> {
                            writer.printf(" %s: %d%n", e.getKey(), e.getValue());
                        });
        writer.println("-------------------------------------------------------------");
        writer.flush();
        // occurences.entrySet().stream()//
        // .sorted((e1, e2) -> Long.compare(score(e2), score(e1)))//
        // .limit(30)//
        // .forEachOrdered(e -> {
        // writer.printf(" %s : %d%n", e.getKey(), e.getValue());
        // });
    }

    public void serializeState(XMLStreamWriter wr) throws XMLStreamException {
        wr.writeStartElement("As");

        for (Map.Entry<ActiveSpecializationOccurence, Long> ent : activeSpecializationsMap.entrySet()) {
            wr.writeStartElement("A");
            wr.writeAttribute("c", "" + ent.getValue());

            ent.getKey().serializeState(wr);
            wr.writeEndElement();
        }

        wr.writeEndElement();
    }

    public static ExecutionTracer deserializeState(XMLStreamReader rd) throws XMLStreamException {

        String key = readAttribute(rd, "key");

        ExecutionTracer result = new ExecutionTracer(key);

        readStartElement(rd, "As");

        int state = rd.next();
        while (state == XMLStreamReader.START_ELEMENT) {
            assert rd.getName().getLocalPart().equals("A");

            long value = XmlUtils.readAttributeLong(rd, "c");

            ActiveSpecializationOccurence k = ActiveSpecializationOccurence.deserializeState(rd);

            result.activeSpecializationsMap.put(k, value);

            readEndElement(rd, "A");

            state = rd.next();
        }
        // end ASOs
        assert state == XMLStreamReader.END_ELEMENT;

        return result;
    }
}
