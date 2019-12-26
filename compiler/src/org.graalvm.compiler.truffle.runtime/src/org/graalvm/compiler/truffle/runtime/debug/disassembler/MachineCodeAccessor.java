package org.graalvm.compiler.truffle.runtime.debug.disassembler;

public interface MachineCodeAccessor {

    long getAddress();

    int getLength();

    byte getByte(int n);

    byte[] getBytes();

    String fileName(String extension);

}
