package jdk.graal.compiler.hotspot.meta.joonhwan;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;


 public class MethodCallTargetBuffer {

        public static long[] Buffer = new long[9_000_000];
        public static AtomicLong bufferIndex = new AtomicLong(0);
        public static AtomicLong sampleCounter = new AtomicLong(0);
        public static final int SAMPLE_RATE = 100;

        public static void sampleTime(long ID) {
                if(sampleCounter.getAndIncrement() % SAMPLE_RATE == 0){
                        timeBuffer.get().add(ID);
                        timeBuffer.get().add(System.nanoTime());
                }
        }

        static {
                System.out.println("CACHE INITIALIZATION");
        }


        public static void print() {
                String fileName = "./data/buffer_dump.csv";
                try (FileWriter writer = new FileWriter(fileName)) {
                        writer.append("CompID,Start\n"); 
                        long currentIndex = bufferIndex.get();
                
                        if (currentIndex % 2 != 0) {
                                currentIndex--; // Adjust to the last complete pair
                        }
                
                        writer.append(String.format("pointer %d\n", currentIndex));
                
                        for (int i = 0; i < currentIndex; i += 2) {
                                long compID = Buffer[i];
                                long startTime = Buffer[i + 1];
                                writer.append(String.format("%d,%d\n", compID, startTime));
                        }
                
                        System.out.println("CSV file '" + fileName + "' has been created successfully.");
                } catch (IOException e) {
                        System.err.println("An error occurred while writing to the CSV file: " + e.getMessage());
                }
        }
        
}