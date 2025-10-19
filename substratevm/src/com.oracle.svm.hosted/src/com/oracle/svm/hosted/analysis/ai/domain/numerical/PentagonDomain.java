package com.oracle.svm.hosted.analysis.ai.domain.numerical;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pentagon abstract domain that combines intervals and variable relationships.
 * Based on the paper: <a href="https://doi.org/10.1016/j.scico.2009.04.004"></a>
 *
 * @param <Variable> the type used for identifying different variables in the pentagon domain (String, Access Path, ...)
 */
public class PentagonDomain<Variable> extends AbstractDomain<PentagonDomain<Variable>> {

    /* We could use something like MapDomain here, but this is just for demonstration and easier work */
    private final Map<Variable, IntInterval> intervals;
    private final Map<Variable, Set<Variable>> lessThan; // x < y: x -> {y}

    public PentagonDomain() {
        this.intervals = new HashMap<>();
        this.lessThan = new HashMap<>();
    }

    public PentagonDomain(PentagonDomain<Variable> other) {
        this.intervals = new HashMap<>();
        for (Map.Entry<Variable, IntInterval> entry : other.intervals.entrySet()) {
            this.intervals.put(entry.getKey(), entry.getValue().copyOf());
        }

        this.lessThan = new HashMap<>();
        for (Map.Entry<Variable, Set<Variable>> entry : other.lessThan.entrySet()) {
            this.lessThan.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
    }

    public IntInterval getInterval(Variable var) {
        return intervals.getOrDefault(var, new IntInterval());
    }

    public void setInterval(Variable var, IntInterval interval) {
        intervals.put(var, interval);
        applyReduction();
    }

    public void addLessThanRelation(Variable x, Variable y) {
        lessThan.computeIfAbsent(x, k -> new HashSet<>()).add(y);
        applyReduction();
    }

    public Set<Variable> getVariableNames() {
        return intervals.keySet();
    }

    /**
     * Checks if there exists a less-than relationship between variables (either direct or transitive).
     * For example, if x < y and y < z are in the domain, then x < z is also true.
     *
     * @param x the first variable
     * @param z the second variable
     * @return true if x < z, false otherwise
     */
    public boolean lessThan(Variable x, Variable z) {
        // Check direct relationship
        if (lessThan.getOrDefault(x, Set.of()).contains(z)) {
            return true;
        }

        // Check transitive relationship using DFS
        Set<Variable> visited = new HashSet<>();
        return findTransitiveRelation(x, z, visited);
    }

    private boolean findTransitiveRelation(Variable x, Variable z, Set<Variable> visited) {
        // Avoid cycles
        if (visited.contains(x)) {
            return false;
        }

        visited.add(x);

        // Check all variables y where x < y
        Set<Variable> directRelations = lessThan.getOrDefault(x, Set.of());
        for (Variable y : directRelations) {
            if (y.equals(z) || findTransitiveRelation(y, z, visited)) {
                return true;
            }
        }

        return false;
    }

    private void applyReduction() {
        boolean changed;
        do {
            changed = false;

            // Update intervals based on inequalities
            for (Map.Entry<Variable, Set<Variable>> entry : lessThan.entrySet()) {
                Variable x = entry.getKey();
                IntInterval xInterval = getInterval(x);
                if (xInterval.isBot()) continue;

                for (Variable y : entry.getValue()) {
                    IntInterval yInterval = getInterval(y);
                    if (yInterval.isBot()) continue;

                    // If x < y then x's upper bound must be less than y's lower bound
                    if (xInterval.getUpperBound() >= yInterval.getLowerBound()) {
                        long newXUpper = Math.min(xInterval.getUpperBound(), yInterval.getLowerBound() - 1);
                        long newYLower = Math.max(yInterval.getLowerBound(), xInterval.getUpperBound() + 1);

                        // Only create new interval if it would be valid
                        if (xInterval.getLowerBound() <= newXUpper) {
                            IntInterval newXInterval = new IntInterval(xInterval.getLowerBound(), newXUpper);
                            intervals.put(x, newXInterval);
                            changed = true;
                        } else {
                            // Cannot satisfy constraint - set to bottom
                            intervals.put(x, new IntInterval());
                            changed = true;
                            break; // No need to continue with this variable
                        }

                        if (newYLower <= yInterval.getUpperBound()) {
                            IntInterval newYInterval = new IntInterval(newYLower, yInterval.getUpperBound());
                            intervals.put(y, newYInterval);
                        } else {
                            // Cannot satisfy constraint - set to bottom
                            intervals.put(y, new IntInterval());
                        }
                    }
                }
            }

        } while (changed);
    }

    @Override
    public boolean isBot() {
        return intervals.values().stream().anyMatch(IntInterval::isBot);
    }

    @Override
    public boolean isTop() {
        return intervals.isEmpty() && lessThan.isEmpty();
    }

    @Override
    public boolean leq(PentagonDomain<Variable> other) {
        for (Map.Entry<Variable, IntInterval> entry : intervals.entrySet()) {
            IntInterval thisInterval = entry.getValue();
            IntInterval otherInterval = other.getInterval(entry.getKey());
            if (!thisInterval.leq(otherInterval)) {
                return false;
            }
        }

        for (Map.Entry<Variable, Set<Variable>> entry : lessThan.entrySet()) {
            Variable x = entry.getKey();
            Set<Variable> thisYVars = entry.getValue();
            Set<Variable> otherYVars = other.lessThan.getOrDefault(x, Set.of());

            for (Variable y : thisYVars) {
                if (!otherYVars.contains(y)) {
                    return false;
                }
            }
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setToBot() {
        intervals.clear();
        intervals.put((Variable) new Object(), new IntInterval());
        lessThan.clear();
    }

    @Override
    public void setToTop() {
        intervals.clear();
        lessThan.clear();
    }

    @Override
    public void joinWith(PentagonDomain<Variable> other) {
        // Join intervals
        Set<Variable> allVars = new HashSet<>(intervals.keySet());
        allVars.addAll(other.intervals.keySet());

        for (Variable var : allVars) {
            IntInterval thisInterval = getInterval(var);
            IntInterval otherInterval = other.getInterval(var);
            IntInterval joinedInterval = thisInterval.join(otherInterval);
            intervals.put(var, joinedInterval);
        }

        // Join inequalities (intersection)
        Map<Variable, Set<Variable>> newLessThan = new HashMap<>();

        for (Map.Entry<Variable, Set<Variable>> entry : lessThan.entrySet()) {
            Variable x = entry.getKey();
            Set<Variable> otherYVars = other.lessThan.get(x);

            if (otherYVars != null) {
                Set<Variable> newYVars = new HashSet<>(entry.getValue());
                newYVars.retainAll(otherYVars);

                if (!newYVars.isEmpty()) {
                    newLessThan.put(x, newYVars);
                }
            }
        }

        lessThan.clear();
        lessThan.putAll(newLessThan);
        applyReduction();
        filterContradictoryRelations();
    }

    @Override
    public void widenWith(PentagonDomain<Variable> other) {
        // Similar to join but with widening for intervals
        Set<Variable> allVars = new HashSet<>(intervals.keySet());
        allVars.addAll(other.intervals.keySet());

        for (Variable var : allVars) {
            IntInterval thisInterval = getInterval(var);
            IntInterval otherInterval = other.getInterval(var);
            IntInterval widenedInterval = thisInterval.widen(otherInterval);
            intervals.put(var, widenedInterval);
        }

        // Same as join for inequalities
        Map<Variable, Set<Variable>> newLessThan = new HashMap<>();

        for (Map.Entry<Variable, Set<Variable>> entry : lessThan.entrySet()) {
            Variable x = entry.getKey();
            Set<Variable> otherYVars = other.lessThan.get(x);

            if (otherYVars != null) {
                Set<Variable> newYVars = new HashSet<>(entry.getValue());
                newYVars.retainAll(otherYVars);

                if (!newYVars.isEmpty()) {
                    newLessThan.put(x, newYVars);
                }
            }
        }

        lessThan.clear();
        lessThan.putAll(newLessThan);
        applyReduction();
        filterContradictoryRelations();
    }

    // Language: java
    @Override
    public void meetWith(PentagonDomain<Variable> other) {
        // Compute the meet of intervals
        Set<Variable> allVars = new HashSet<>();
        allVars.addAll(this.intervals.keySet());
        allVars.addAll(other.intervals.keySet());
        Map<Variable, IntInterval> newIntervals = new HashMap<>();
        for (Variable var : allVars) {
            IntInterval interval1 = getInterval(var).copyOf();
            IntInterval interval2 = other.getInterval(var);
            interval1.meetWith(interval2);
            newIntervals.put(var, interval1);
        }
        this.intervals.clear();
        this.intervals.putAll(newIntervals);

        // Intersect less-than relations
        Map<Variable, Set<Variable>> newLessThan = new HashMap<>();
        for (Variable var : this.lessThan.keySet()) {
            if (other.lessThan.containsKey(var)) {
                Set<Variable> set1 = new HashSet<>(this.lessThan.get(var));
                Set<Variable> set2 = other.lessThan.get(var);
                set1.retainAll(set2);
                if (!set1.isEmpty()) {
                    newLessThan.put(var, set1);
                }
            }
        }

        this.lessThan.clear();
        this.lessThan.putAll(newLessThan);
        applyReduction();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PentagonDomain<?> that = (PentagonDomain<?>) o;
        return Objects.equals(intervals, that.intervals) &&
                Objects.equals(lessThan, that.lessThan);
    }

    @Override
    public int hashCode() {
        return Objects.hash(intervals, lessThan);
    }

    @Override
    public String toString() {
        return "PentagonDomain{intervals=" + intervals + ", lessThan=" + lessThan + '}';
    }

    @Override
    public PentagonDomain<Variable> copyOf() {
        return new PentagonDomain<>(this);
    }

    private void filterContradictoryRelations() {
        lessThan.entrySet().removeIf(entry -> {
            Variable x = entry.getKey();
            IntInterval xInterval = getInterval(x);

            Set<Variable> ys = entry.getValue();
            ys.removeIf(y -> {
                IntInterval yInterval = getInterval(y);
                return xInterval.getUpperBound() >= yInterval.getLowerBound(); // contradiction
            });

            return ys.isEmpty();
        });
    }
}
