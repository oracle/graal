int a() { return 1; }

int b() { return a() + 1; }

int c() { return b() + 1; }

int d() { return c() + 1; }

int main() { return d(); }
