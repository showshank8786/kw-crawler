package org.kwcrawler.parser;

import com.fasterxml.jackson.annotation.JsonIgnore;

import static org.kwcrawler.parser.TextUtils.toSubScript;
import static org.kwcrawler.parser.TextUtils.toSuperScript;

public record Value(
    String addedIndex,
    String removedIndex,
    String value) {

    @JsonIgnore
    public boolean isEmpty() {
        return value.isEmpty() || value.equals("---");
    }

    public String toString() {
        var addedInfo = addedIndex() == null ? "" : toSuperScript(addedIndex().replaceAll("\\s+",""));

        String newResult;
        if (removedIndex() == null) {
            newResult = value() + addedInfo;
        } else {
            newResult = "\u001B[9m--" + value() + "--\u001B[0m" + addedInfo + toSubScript(removedIndex().replaceAll("\\s+",""));
        }
        return newResult;
    }

    public Value removeSpaces() {
        return new Value(addedIndex(), removedIndex(), value().replaceAll("\\s+",""));
    }
}
