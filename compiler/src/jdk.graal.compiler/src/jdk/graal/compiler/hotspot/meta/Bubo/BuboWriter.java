package jdk.graal.compiler.hotspot.meta.Bubo;


import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;



public class BuboWriter {

    public static void main(String[] args) {

    }

    public static void Write(String fileName, long[] data) {

        try {
            // Create FileWriter with append mode set to true
            FileWriter fileWriter = new FileWriter(fileName, true);

            // Create BufferedWriter for efficient writing
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

            for (int i = 0; i < data.length; i++) {
                bufferedWriter.write(data[i] + "," +data[i+1]);
                bufferedWriter.newLine();  // Move to the next line for the next entry
                i++;
        }

            // Close the BufferedWriter
            bufferedWriter.close();

            System.out.println("Data written to the file successfully.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}