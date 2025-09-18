package org.kwcrawler.parser;


import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public record ParsedRegister(
    List<Page> pages
) {
    public record Page (
        String title,
        List<Section> sections
    ){
        public record Section(
            String title,
            List<Entry> entries,

            @JsonIgnore
            Element element
        ){
            public record Entry(
                    RowType rowType,
                    List<String> keys,
                    Value value
            ) {}

            public ValueHistory getIndexedValue(int index, String key) {
                var indexString = index + ".";

                var results = new ArrayList<Value>();


                for (var entry : entries) {
                    if (entry.keys().size() < 3
                            || !entry.keys().getFirst().equals(indexString)
                            || !entry.keys().get(1).equals(key)
                            || entry.value() == null
                            || entry.value.isEmpty()
                    ) {
                        continue;
                    }

                    results.add(entry.value());
                }

                return new ValueHistory(results);
            }

            public ValueHistory getIndexedValue(int index, String key1, String key2) {
                var indexString = index + ".";

                var results = new ArrayList<Value>();
                for (var entry : entries) {
                    if (entry.keys().size() < 4
                            || !entry.keys().getFirst().equals(indexString)
                            || !entry.keys().get(1).equals(key1)
                            || !entry.keys().get(2).equals(key2)
                            || entry.value() == null
                            || entry.value.isEmpty()
                    ) {
                        continue;
                    }

                    results.add(entry.value());
                }
                return new ValueHistory(results);
            }

            public ValueHistory getValue(String key) {
                var results = new ArrayList<Value>();

                for (var entry : entries) {
                    if (entry.keys().size() < 2
                            || !entry.keys().getFirst().equals(key)
                            || entry.value() == null
                            || entry.value.isEmpty()
                    ) {
                        continue;
                    }

                    results.add(entry.value());
                }

                return new ValueHistory(results);
            }

            public String getChangeValue(int index, String[] keys) {
                var indexString = Integer.toString(index);
                var keyList = List.of(keys);

                return entries.stream()
                        .filter(entry -> entry.keys().size() >= 3
                                && entry.keys().getFirst().equals(indexString)
                                && entry.keys().get(1).equals("1.")
                                && keyList.contains(entry.keys().get(2))
                                && !entry.value().isEmpty())
                        .map(entry -> entry.value().value())
                        .collect(Collectors.joining(", "));
            }
        }
    }
}
