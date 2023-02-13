package com.oracle.svm.test.jfr;

import jdk.jfr.consumer.RecordingStream;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import com.oracle.svm.test.jfr.events.EndStreamEvent;

import static org.junit.Assert.assertTrue;

abstract class StreamingTest extends JfrTest {
    protected static final int TIMEOUT_MILLIS = 3 * 1000;
    protected Path dumpLocation;
    protected AtomicLong emittedEvents = new AtomicLong(0);
    private RecordingStream rs;
    private volatile boolean streamEndedSuccessfully = false;

    protected RecordingStream createStream() {
        rs = new RecordingStream();
        rs.enable("com.jfr.EndStream");
        // close stream once we get the signal
        rs.onEvent("com.jfr.EndStream", e -> {
            rs.close();
            streamEndedSuccessfully = true;
        });
        return rs;
    }

    protected void closeStream() throws InterruptedException {
        // We require a signal to close the stream, because if we close the stream immediately after
        // dumping, the dump may not have had time to finish.
        EndStreamEvent endStreamEvent = new EndStreamEvent();
        endStreamEvent.commit();
        rs.awaitTermination(Duration.ofMillis(TIMEOUT_MILLIS));
        assertTrue("unable to find stream end event signal in stream", streamEndedSuccessfully);
    }
}
