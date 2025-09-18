package org.kwcrawler.parser;


import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.stream.Collectors;

public record ValueHistory(
        List<Value> values
) {
    public String toString() {
        return values().stream()
                .filter(value -> !value.isEmpty())
                .map(Value::toString)
                .collect(Collectors.joining(", "));
    }

    @JsonIgnore
    public String toStringOnlyCurrent() {
        return values().stream()
                .filter(value -> value.removedIndex() == null)
                .map(Value::value)
                .collect(Collectors.joining(", "));
    }

    @JsonIgnore
    public boolean isEmpty() {
        return values().isEmpty();
    }

    @JsonIgnore
    public boolean isCurrentNonEmpty() {
        return values().stream()
                .filter(value -> value.removedIndex() == null)
                .anyMatch(value -> !value.isEmpty());
    }

    @JsonIgnore
    public String currentValue() {
        var values = currentValues();
        if (values.isEmpty()) {
            return null;
        }
        if (values.size() != 1) {
            throw new IllegalStateException("Multiple values in ValueHistory.getCurrentValue(): " + values);
            //System.out.println("Multiple values in ValueHistory.getCurrentValue(): " + values);
        }

        return values.getFirst();
    }

    // mainly for '4. Położenie' field when parcel spans multiple locations
    @JsonIgnore
    public List<String> currentValues() {
        return values().stream()
                .filter(value -> value.removedIndex() == null)
                .map(Value::value)
                .collect(Collectors.toList());
    }

    @JsonIgnore
    public ValueHistory removeSpacesFromValues() {
        return new ValueHistory(values().stream()
                .map(Value::removeSpaces)
                .collect(Collectors.toList()));
    }
}
