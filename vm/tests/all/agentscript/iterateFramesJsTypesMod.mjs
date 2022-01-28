export function retA() {
    let a = 3;
    return a;
}
export function retAB() {
    let b = 2;
    let ab = b + retA();
    return ab;
}
export function retABC() {
    let c = 1;
    let abc = c + retAB();
    return abc;
}
