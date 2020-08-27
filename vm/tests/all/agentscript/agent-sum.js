function plus(a, b) {
    return a + b;
}

function sumRange(from, to) {
    let sum = from;
    for (let i = from + 1; i <= to; i++) {
        sum = plus(sum, i);
    }
    return sum;
}

print(sumRange(1, 6));
