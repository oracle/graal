package com.oracle.truffle.espresso.jdwp.impl;

import java.util.concurrent.Callable;

public class JDWPResult {

    private final PacketStream reply;
    private final Callable future;

    JDWPResult(PacketStream reply) {
        this(reply, null);
    }

    JDWPResult(PacketStream reply, Callable future) {
        this.reply = reply;
        this.future = future;
    }

    public PacketStream getReply() {
        return reply;
    }

    public Callable getFuture() {
        return future;
    }
}
