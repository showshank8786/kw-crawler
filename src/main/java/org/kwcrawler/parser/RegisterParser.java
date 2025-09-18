package org.kwcrawler.parser;


import org.kwcrawler.CourtCode;
import org.kwcrawler.KWNumber;
import org.kwcrawler.ParseException;
import org.kwcrawler.parser.ParsedRegister.Page;
import org.kwcrawler.parser.ParsedRegister.Page.Section;
import org.kwcrawler.parser.ParsedRegister.Page.Section.Entry;
import org.kwcrawler.structure.Chapter;
import org.kwcrawler.structure.Filenames;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

enum RowType {
    TITLE,
    BREAK,
    HEADER,
    NO_ENTRY,
    EMPTY_LINE,
    MARK,
    VALUE
}

public class RegisterParser {
    private final ParsedRegisterSerialized serializer = new ParsedRegisterSerialized(false);

    public RegisterParser(CourtCode courtCode) {
        //Filenames.getParsedDir(courtCode).toFile().mkdirs();
    }

    public ParsedRegister parse(KWNumber kwNumber) {
        // var parsedFilename = Filenames.getParsedFileName(kwNumber);
        // if (Files.exists(parsedFilename)) {
        //     try {
        //         var serialized = Files.readString(parsedFilename);
        //         return serializer.deserialize(serialized);
        //     } catch (IOException e) {
        //         throw new ParseException("Cannot read parsed register from file", e);
        //     }
        // }

        var pages = Arrays.stream(Chapter.all())
                .map(chapter -> {
                    var file = Filenames.getFilename(kwNumber, chapter);

                    return parse(file);
                }).toList();

        var parsedRegister = new ParsedRegister(pages);
        // var serialized = serializer.serialize(parsedRegister);
        // try {
        //     Files.writeString(parsedFilename, serialized);
        // } catch (IOException e) {
        //     throw new ParseException("Cannot write parsed register to file", e);
        // }
        return parsedRegister;
    }

    public Page parse(Path file) {
        Document document;
        try {
            document = Jsoup.parse(file);
        } catch (IOException e) {
            throw new ParseException("File not found", e);
        }

        var content = document.select("div#contentDzialu");

        var tables = content.select("table.tbOdpis");

        String mainTitle = "";
        List<Section> sections = new ArrayList<>();

        for (var table : tables) {

            // main title
            var topTitle = table.select("td.csTTytul").text();
            if (!topTitle.isEmpty()) {
                if (!topTitle.equals("WNIOSKI I PODSTAWY WPISÓW W KSIĘDZE WIECZYSTEJ")) {
                    mainTitle = topTitle;
                    continue;
                }
            }

            // title of the section
            var title = table.select("td.csTytul").text();

            if (!title.isEmpty()) {
                var section = parseSection(table);

                if (!section.entries().isEmpty()) {
                    sections.add(section);
                }
            }
        }

        return new Page(mainTitle, sections);
    }

    record RowSpan(int count, Element element) {}

    static class Headers {
        private Integer changeColspan;
        private Integer addColspan;
        private Integer removeColspan;
        private Integer valueColspan;

        public Headers(Integer changeColspan, Integer addColspan, Integer removeColspan, Integer valueColspan) {
            this.changeColspan = changeColspan;
            this.addColspan = addColspan;
            this.removeColspan = removeColspan;
            this.valueColspan = valueColspan;
        }

        public void clear() {
            changeColspan = null;
            addColspan = null;
            removeColspan = null;
            valueColspan = null;
        }

        public static Headers empty() {
            return new Headers(null, null, null, null);
        }
    }

    private Section parseSection(Element table) {
        var rows = table.select("tr");

        var rowIterator = rows.stream().iterator();
        var mainTitle = "";

        List<RowSpan> rowspans = List.of();
        var entries = new ArrayList<Entry>();
        var headers = Headers.empty();

        while (rowIterator.hasNext()) {
            var row = rowIterator.next();
            var newCells = row.select("td");

            // build cells with rowspans and new cells
            var cells = Stream.concat(
                rowspans.stream().map(rowspan -> rowspan.element),
                newCells.stream()
            ).toList();

            // update rowspans
            rowspans = Stream.concat(
                // old rowspans
                rowspans.stream(),
                // new cells which have rowspan attribute
                newCells.stream()
                    .filter(cell -> !cell.attr("rowspan").isEmpty())
                    .map(cell -> new RowSpan(Integer.parseInt(cell.attr("rowspan")), cell))
            )
                .map(rowspan -> new RowSpan(rowspan.count - 1, rowspan.element))
                .filter(rowspan -> rowspan.count > 0)
                .toList();

            var rowType = detectRowType(cells);

            switch (rowType) {
                case TITLE -> {
                    var title = parseTitleRow(cells);
                    if (mainTitle.isEmpty()) {
                        mainTitle = title;
                    }
                    //entries.add(new Entry(RowType.TITLE, null, null, List.of(title), null));
                }
                case BREAK -> {
                    var entry = parseBreakRow(cells);
                    //entries.add(entry);
                }
                case HEADER -> {
                    var entry = parseHeaderRow(cells, headers);
                    //entries.add(entry);
                }
                case NO_ENTRY -> {} //entries.add(new Entry(RowType.NO_ENTRY, null, null, List.of(), null));
                case EMPTY_LINE -> {}//entries.add(new Entry(RowType.EMPTY_LINE, null, null, List.of(), null));
                case VALUE -> parseValues(cells, headers, entries);
                case MARK -> {} // ignore
            }
        }

        return new Section(mainTitle, entries, table);
    }


    private RowType detectRowType(List<Element> rowCells) {
        for (var cell : rowCells) {
            if (cell.className().equals("csTytul") || cell.className().equals("csTTytul")) {
                return RowType.TITLE;
            }
            if (cell.className().equals("csTBreak") || cell.className().equals("csBreak")) {
                return RowType.BREAK;
            }
            if (cell.className().equals("csEmptyLine")) {
                return RowType.EMPTY_LINE;
            }
            if (cell.className().equals("csCDane")) {
                return RowType.NO_ENTRY;
            }
            if (cell.className().equals("csMark")) {
                return RowType.MARK;
            }
            if (cell.className().equals("csDane") || cell.className().equals("csBDDane") || cell.className().equals("csBDane")) {
                return RowType.VALUE;
            }
        }
        return RowType.HEADER;
    }

    private String parseTitleRow(List<Element> cells) {
        String title = null;

        for (var cell: cells) {
            if (cell.className().equals("csTytul") || cell.className().equals("csTTytul")) {
                title = cell.text();
            } else if (cell.className().equals("csCOpis") || cell.className().equals("csOpis")) {
                // ignore
            } else {
                throw new RuntimeException("Invalid title row: " + cells);
            }
        }

        if (title == null) {
            throw new RuntimeException("Invalid title row: " + cells);
        }

        return title;
    }

    private Entry parseBreakRow(List<Element> cells) {
        var cellCount = cells.size();
        if (cellCount != 1) {
            throw new RuntimeException("Invalid count of cells in break row: " + cells);
        }
        if (!cells.getFirst().className().equals("csTBreak") && !cells.getFirst().className().equals("csBreak")) {
            throw new RuntimeException("Invalid cell in break row: " + cells);
        }
        return new Entry(RowType.BREAK, null, null);
    }

    private Entry parseHeaderRow(List<Element> cells, Headers headers) {
        var colspan = 0;
        for (var cell : cells) {
            if (!cell.className().equals("csCOpis")
                    && !cell.className().equals("csCMOpis")
                    && !cell.className().equals("csMOpis")
                    && !cell.className().equals("csOpis")) {
                throw new ParseException("Invalid header row cell: " + cell + " in row: " + cells);
            }

            if (cell.text().equals("Indeks zmiany")) {
                headers.changeColspan = colspan;
            } else if (cell.text().equals("Wpisu")) {
                headers.addColspan = colspan;
            } else if (cell.text().equals("Wykr.")) {
                headers.removeColspan = colspan;
            } else if (cell.text().equals("Treść pola")) {
                headers.valueColspan = colspan;
            }

            var attr = cell.attr("colspan");
            if (!attr.isEmpty()) {
                colspan += Integer.parseInt(attr);
            } else {
                colspan++;
            }
        }

        var keys = cells.stream().map(Element::text).toList();
        return new Entry(RowType.HEADER, keys, null);
    }

    private String parseIndex(String index) {
        if (index == null || index.isEmpty() || index.equals("---")) {
            return null;
        }
        return index;
    }

    private void parseValues(List<Element> cells, Headers headers, List<Entry> entries) {
        var colspan = 0;
        String addIndex = null;
        String removeIndex = null;

        List<String> keys = new ArrayList<>();
        String value = null;

        for (var cell : cells) {
            if (headers.changeColspan != null && colspan == headers.changeColspan) {
                addIndex = parseIndex(cell.text());
            } else if (headers.addColspan != null && colspan == headers.addColspan) {
                addIndex = parseIndex(cell.text());
            } else if (headers.removeColspan != null && colspan == headers.removeColspan) {
                removeIndex = parseIndex(cell.text());
            } else if (headers.valueColspan != null && colspan >= headers.valueColspan) {
                if (value == null) {
                    value = cell.text();
                } else {
                    value += " " + cell.text();
                }
            } else {
                keys.add(cell.text());
            }

            var attr = cell.attr("colspan");
            if (!attr.isEmpty()) {
                colspan += Integer.parseInt(attr);
            } else {
                colspan++;
            }
        }

        if (value == null || value.isEmpty()) {
            return;
        }
        if ((value.equals("---") || value.equals("/ /"))
                && addIndex == null
                && removeIndex == null) {
            return;
        }

        entries.add(new Entry(RowType.VALUE, keys, new Value(addIndex, removeIndex, value)));
    }
}