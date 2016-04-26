int a = 2;

int func1() { return a--; }

int main() { return func1() && func1(); }
