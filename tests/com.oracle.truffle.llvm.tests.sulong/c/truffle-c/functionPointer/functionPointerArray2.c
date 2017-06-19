int func() { return 13; }

int (*functionPointers[])() = { func, func };

int main() { return functionPointers[1](); }
