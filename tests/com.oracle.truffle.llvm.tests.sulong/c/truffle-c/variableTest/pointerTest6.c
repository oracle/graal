int a = 3;
int *ptr = &a;
int **ptr2 = &ptr;

int main() { return **ptr2; }
