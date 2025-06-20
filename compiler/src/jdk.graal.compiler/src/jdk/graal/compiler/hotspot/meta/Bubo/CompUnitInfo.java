package jdk.graal.compiler.hotspot.meta.Bubo;

public class CompUnitInfo {
    private String methodName;
    private Double ratio;

    public CompUnitInfo(String methodName, Double ratio) {
        this.methodName = methodName;
        this.ratio = ratio;
    }

    public String getMethodName() {
        return methodName;
    }

    public Double getRatio() {
        return ratio;
    }

    @Override
    public String toString() {
        return "CompUnitInfo{" +
                "methodName='" + methodName + '\'' +
                ", ratio=" + ratio +
                '}';
    }
}