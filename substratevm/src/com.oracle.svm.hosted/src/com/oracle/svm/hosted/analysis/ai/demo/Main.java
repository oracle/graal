package com.oracle.svm.hosted.analysis.ai.demo;

public class Main {
    private static int globalX;
    private static int globalY;
    // TODO bytecode index possible keep line in log
    // TODO possible call-site sensitivity
    public static void main(String[] args) {
        globalX = 10;
        globalY = 20;

        Point p1 = new Point(1, 2);
        Point p2 = new Point(3, 4);

        Rectangle rect = new Rectangle(p1, p2);

        // Modify fields
        p1.x += 5;
        p1.y += 6;
        p2.x += 7;
        p2.y += 8;
//        p1.move(5, 6);
//        p2.move(7, 8);

        globalX = p1.x + p2.x;
        globalY = p1.y + p2.y;

        // Access fields
//        int area = rect.area();
//        System.out.println("Area: " + area);

        // Assertions
        assert globalX == 16; // p1.x = 6, p2.x = 10 → 6 + 10 = 16
        assert globalY == 20; // p1.y = 8, p2.y = 12 → 8 + 12 = 20
//        assert area == 8;     // width = 10 - 6 = 4, height = 12 - 8 = 4 → 4 * 4 = 16
    }
}