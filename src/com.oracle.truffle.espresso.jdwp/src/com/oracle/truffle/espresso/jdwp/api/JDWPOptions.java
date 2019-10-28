package com.oracle.truffle.espresso.jdwp.api;

public class JDWPOptions {
    public final String transport;
    public final String address;
    public final boolean server;
    public final boolean suspend;

    public JDWPOptions(String transport, String address, boolean server, boolean suspend) {
        this.transport = transport;
        this.address = address;
        this.server = server;
        this.suspend = suspend;
    }

    @Override
    public String toString() {
        return "JDWPOptions{" +
                        "transport=" + transport + "," +
                        "address=" + address + "," +
                        "server=" + server + "," +
                        "suspend=" + suspend + "}";
    }
}
