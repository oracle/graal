package jdk.graal.compiler.hotspot.meta.joonhwan;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class InstrumentationBuffer {
    private static final int BUFFER_SIZE = 1_000_000; // Size of each buffer
    private static final int BUFFER_COUNT = 5; // Number of buffers to rotate through
    private static final ConcurrentHashMap<Long, ThreadLocalBuffers> threadBuffers = new ConcurrentHashMap<>();
    private static final String EXPERIMENTS_DIR = "/experiments";

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(InstrumentationBuffer::processAllDataOnShutdown));
    }

    private static class ThreadLocalBuffers {
        private final long[][] buffers;
        private final AtomicInteger currentBuffer;
        private final int[] pointers;

        ThreadLocalBuffers() {
            this.buffers = new long[BUFFER_COUNT][BUFFER_SIZE];
            this.currentBuffer = new AtomicInteger(0);
            this.pointers = new int[BUFFER_COUNT];
        }

        void add(long id, long time) {
            int bufferIndex = currentBuffer.get();
            int pointer = pointers[bufferIndex];

            if (pointer >= BUFFER_SIZE - 1) {
                bufferIndex = rotateBuffer();
                pointer = 0;
            }

            buffers[bufferIndex][pointer++] = id;
            buffers[bufferIndex][pointer++] = time;
            pointers[bufferIndex] = pointer;
        }

        private int rotateBuffer() {
            return currentBuffer.updateAndGet(current -> (current + 1) % BUFFER_COUNT);
        }
    }

    public static void add(long id, long time) {
        long threadId = Thread.currentThread().getId();
        ThreadLocalBuffers buffers = threadBuffers.computeIfAbsent(threadId, k -> new ThreadLocalBuffers());
        buffers.add(id, time);
    }

    private static void processAllDataOnShutdown() {
        System.out.println("Processing all thread data on shutdown...");
        String fileName = generateFileName();
        Path filePath = Paths.get(EXPERIMENTS_DIR, fileName);

        try {
            Files.createDirectories(filePath.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                threadBuffers.forEach((threadId, buffers) -> {
                    try {
                        writer.write("Thread " + threadId + " data:\n");
                        for (int i = 0; i < BUFFER_COUNT; i++) {
                            int pointer = buffers.pointers[i];
                            long[] buffer = buffers.buffers[i];
                            for (int j = 0; j < pointer; j += 2) {
                                writer.write(buffer[j] + "," + buffer[j + 1] + "\n");
                            }
                        }
                        writer.write("\n");
                    } catch (IOException e) {
                        System.err.println("Error writing data for thread " + threadId + ": " + e.getMessage());
                    }
                });
            }
            System.out.println("Data written to file: " + filePath);
        } catch (IOException e) {
            System.err.println("Error creating or writing to file: " + e.getMessage());
        }
    }

    private static String generateFileName() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        return "vincent_buffer_data_" + now.format(formatter) + ".csv";
    }
}