/*
 Expected invariants:
 - Division by constant -> produce interval accordingly, division by zero handled conservatively
*/
public class DivisionByConstant {
    public static int main(String[] args) {
        int x = 20;
        int d = 4;
        int r = x / d; // expect 5
        return r;
    }
}

