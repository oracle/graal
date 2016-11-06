int foo(int a, int b) { return a + b; }

int main() { return 1 + foo(foo(1, 2), 3); }
