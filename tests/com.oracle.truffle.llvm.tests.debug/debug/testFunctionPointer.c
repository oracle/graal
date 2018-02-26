int getFive() {
    return 5;
}

int main() {
    int (*funcPtr)() = &getFive;
    int res = funcPtr();
    printf("%d", res);
    return 0;
}
