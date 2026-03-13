/*
 Expected invariants:
 - constant result: local 'x' should be exactly 42
 - arithmetic folding: x = 40 + 2 => [42,42]
*/
public class ConstPropagation {
    public static int main(String[] args) {
        int a = 40;
        int b = 2;
        int x = a + b;
        return x;
    }
}

