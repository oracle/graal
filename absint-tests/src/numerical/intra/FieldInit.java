/*
 Expected invariants:
 - static field S = 7 after initialization
 - instance field f for new object at alloc site is unknown unless written
*/
public class FieldInit {
    static int S;
    int f;
    public static void main(String[] args) {
        FieldInit x = new FieldInit();
        S = 7;
        x.f = 3;
        int a = S; // expect [7,7]
        int b = x.f; // expect [3,3]
    }
}

