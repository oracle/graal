package com.oracle.truffle.api.operation.introspection;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class Instruction {

    // [int index, String name, short[] bytes, Object[][] arguments, Object[][] subinstructions?]
    private final Object[] data;

    Instruction(Object[] data) {
        this.data = data;
    }

    public int getIndex() {
        return (int) data[0];
    }

    public String getName() {
        return (String) data[1];
    }

    public byte[] getBytes() {
        short[] shorts = (short[]) data[2];
        byte[] result = new byte[shorts.length * 2];
        for (int i = 0; i < shorts.length; i++) {
            result[2 * i] = (byte) (shorts[i] & 0xff);
            result[2 * i + 1] = (byte) ((shorts[i] >> 8) & 0xff);
        }

        return result;
    }

    public List<Argument> getArgumentValues() {
        if (data[3] == null) {
            return List.of();
        }
        return Arrays.stream((Object[]) data[3]).map(x -> new Argument((Object[]) x)).collect(Collectors.toUnmodifiableList());
    }

    public List<Instruction> getSubInstructions() {
        if (data.length >= 5) {
            return Arrays.stream((Object[]) data[4]).map(x -> new Instruction((Object[]) x)).collect(Collectors.toUnmodifiableList());
        } else {
            return List.of();
        }
    }

    private static final int REASONABLE_INSTRUCTION_LENGTH = 16;

    @Override
    public String toString() {
        return toString("");
    }

    private String toString(String prefix) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s[%04x] ", prefix, getIndex()));

        byte[] bytes = getBytes();
        for (int i = 0; i < REASONABLE_INSTRUCTION_LENGTH; i++) {
            if (i < bytes.length) {
                sb.append(String.format("%02x ", bytes[i]));
            } else {
                sb.append("   ");
            }
        }

        for (int i = REASONABLE_INSTRUCTION_LENGTH; i < bytes.length; i++) {
            sb.append(String.format("%02x ", bytes[i]));
        }

        sb.append(String.format("%-20s", getName()));

        for (Argument a : getArgumentValues()) {
            sb.append(' ').append(a.toString());
        }

        for (Instruction instr : getSubInstructions()) {
            sb.append('\n').append(instr.toString(prefix + "      "));
        }

        return sb.toString();
    }
}