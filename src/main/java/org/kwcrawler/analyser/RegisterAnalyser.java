package org.kwcrawler.analyser;


import org.kwcrawler.CourtCode;
import org.kwcrawler.KWNumber;
import org.kwcrawler.analyser.AnalysedRegister.Apartment;
import org.kwcrawler.analyser.AnalysedRegister.Area;
import org.kwcrawler.analyser.AnalysedRegister.BasicInfo;
import org.kwcrawler.analyser.AnalysedRegister.Change;
import org.kwcrawler.analyser.AnalysedRegister.CommuneOwner;
import org.kwcrawler.analyser.AnalysedRegister.LegalOwner;
import org.kwcrawler.analyser.AnalysedRegister.Location;
import org.kwcrawler.analyser.AnalysedRegister.MigrationComment;
import org.kwcrawler.analyser.AnalysedRegister.Owner;
import org.kwcrawler.analyser.AnalysedRegister.Parcel;
import org.kwcrawler.analyser.AnalysedRegister.TreasuryOwner;
import org.kwcrawler.parser.ParsedRegister;
import org.kwcrawler.parser.ParsedRegister.Page;
import org.kwcrawler.parser.ParsedRegister.Page.Section;
import org.kwcrawler.parser.ParsedRegisterSerialized;
import org.kwcrawler.structure.Filenames;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static java.util.Map.Entry.comparingByKey;

public class RegisterAnalyser {
    private final AnalysedRegisterSerialized registerSerialized = new AnalysedRegisterSerialized(false);

    public RegisterAnalyser(CourtCode courtCode) {
        Filenames.getAnalysedDir(courtCode).toFile().mkdirs();
    }

    public AnalysedRegister getCached(KWNumber kwNumber) {
        var path = Filenames.getAnalysedFilename(kwNumber);
        if (path.toFile().exists()) {
            String json;
            try {
                json = Files.readString(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return registerSerialized.deserialize(json);
        }
        return null;
    }

    public AnalysedRegister analyse(KWNumber kwNumber, ParsedRegister parsedRegister) {
        BasicInfo basicInfo = null;
        List<Location> locations = List.of();
        List<Apartment> apartments = List.of();
        List<Owner> owners = List.of();
        List<LegalOwner> legalOwners = List.of();
        List<TreasuryOwner> treasuryOwners = List.of();
        List<Owner> authorizeds = List.of();
        List<CommuneOwner> communeOwners = List.of();
        List<Parcel> parcels = List.of();
        MigrationComment migrationComment = null;
        Area area = null;
        Map<Integer, Change> changes = new HashMap<>();

        for (Page page : parsedRegister.pages()) {
            for (Section section : page.sections()) {
                switch (section.title()) {
                    case "Rubryka 0.1 - Informacje podstawowe" -> basicInfo = parseBasicInfo(section);
                    case "Rubryka 1.3 - Położenie" -> locations = parseLocations(section);
                    case "Podrubryka 1.4.4 - Lokal" -> apartments = parseApartments(section);
                    case "Podrubryka 2.2.2 - Skarb Państwa" -> treasuryOwners = parseTreasuryOwners(section);
                    case "Podrubryka 2.2.3 - Jednostka samorządu terytorialnego (związek międzygminny)" -> communeOwners = parseCommuneOwners(section);
                    case "Podrubryka 2.2.4 - Inna osoba prawna lub jednostka organizacyjna niebędąca osobą prawną" -> legalOwners = parseLegalOwners(section);
                    case "Podrubryka 2.2.5 - Osoba fizyczna" -> owners = parseOwners(section);
                    case "Podrubryka 2.5.5 - Osoba fizyczna" -> authorizeds = parseOwners(section);
                    case "Rubryka 1.4 - Oznaczenie" -> parcels = parseParcels(section);
                    case "Rubryka 1.5 - Obszar" -> area = parseArea(section);
                    case "Rubryka 1.9 - Komentarz" -> migrationComment = parseMigrationComments(section);
                    case "WNIOSKI I PODSTAWY WPISÓW W KSIĘDZE WIECZYSTEJ" -> parseChanges(section, changes);
                    default -> {
                        //System.out.println("Skipped section: " + section.title());
                    }
                }
            }
        }

        // convert changes to list sorted by number
        var changeList = changes.entrySet().stream()
                .sorted(comparingByKey())
                .map(Map.Entry::getValue)
                .toList();

        var analysedRegister = new AnalysedRegister(basicInfo, locations, apartments, parcels, area, owners, legalOwners, treasuryOwners, communeOwners, authorizeds, changeList, migrationComment);

        var json = registerSerialized.serialize(analysedRegister);
        try {
            Files.writeString(Filenames.getAnalysedFilename(kwNumber), json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return analysedRegister;
    }

    private static BasicInfo parseBasicInfo(Section section) {
        return new BasicInfo(
                section.getIndexedValue(3, "Typ księgi")
        );
    }

    private static List<Location> parseLocations(Section section) {
        return IntStream.iterate(1, i -> i + 1)
                .mapToObj(i -> new Location(
                        section.getIndexedValue(i, "1. Numer porządkowy"),
                        section.getIndexedValue(i, "2. Województwo"),
                        section.getIndexedValue(i, "3. Powiat"),
                        section.getIndexedValue(i, "4. Gmina"),
                        section.getIndexedValue(i, "5. Miejscowość")
                ))
                .takeWhile(location -> !location.isEmpty())
                .toList();
    }

    private static List<Apartment> parseApartments(Section section) {
        return IntStream.iterate(1, i -> i + 1)
                .mapToObj(i -> new Apartment(
                        section.getIndexedValue(i, "2. Ulica"),
                        section.getIndexedValue(i, "3. Numer budynku"),
                        section.getIndexedValue(i, "4. Numer lokalu"),
                        section.getIndexedValue(i, "5. Przeznaczenie lokalu"),
                        section.getIndexedValue(i, "8. Kondygnacja")

                ))
                .takeWhile(apartment -> !apartment.isEmpty())
                .toList();
    }

    private static List<Owner> parseOwners(Section section) {
        return IntStream.iterate(1, i -> i + 1)
                .mapToObj(i -> new Owner(
                        section.getIndexedValue(i, "2. Imię pierwsze"),
                        section.getIndexedValue(i, "4. Nazwisko / pierwszy człon nazwiska złożonego"),
                        section.getIndexedValue(i, "6. Imię ojca"),
                        section.getIndexedValue(i, "7. Imię matki"),
                        section.getIndexedValue(i, "8. PESEL")
                ))
                .takeWhile(owner -> !owner.isEmpty())
                .toList();
    }

    private static List<LegalOwner> parseLegalOwners(Section section) {
        return IntStream.iterate(1, i -> i + 1)
                .mapToObj(i -> new LegalOwner(
                        section.getIndexedValue(i, "2. Nazwa"),
                        section.getIndexedValue(i, "3. Siedziba"),
                        section.getIndexedValue(i, "4. REGON")
                ))
                .takeWhile(owner -> !owner.isEmpty())
                .toList();
    }

    private static List<TreasuryOwner> parseTreasuryOwners(Section section) {
        return IntStream.iterate(1, i -> i + 1)
                .mapToObj(i -> new TreasuryOwner(
                        section.getIndexedValue(i, "2. Nazwa"),
                        section.getIndexedValue(i, "3. Siedziba"),
                        section.getIndexedValue(i, "4. REGON"),
                        section.getIndexedValue(i, "5. Rola instytucji")
                ))
                .takeWhile(owner -> !owner.isEmpty())
                .toList();
    }

    private static List<CommuneOwner> parseCommuneOwners(Section section) {
        return IntStream.iterate(1, i -> i + 1)
                .mapToObj(i -> new CommuneOwner(
                        section.getIndexedValue(i, "2. Nazwa"),
                        section.getIndexedValue(i, "3. Siedziba"),
                        section.getIndexedValue(i, "4. REGON")
                ))
                .takeWhile(owner -> !owner.isEmpty())
                .toList();
    }

    private static List<Parcel> parseParcels(Section section) {
        return IntStream.iterate(1, i -> i + 1)
                .mapToObj(i -> new Parcel(
                        section.getIndexedValue(i, "1. Identyfikator działki").removeSpacesFromValues(),
                        section.getIndexedValue(i, "2. Numer działki"),
                        section.getIndexedValue(i, "3. Obręb ewidencyjny", "A: numer obrębu ewidencyjnego"),
                        section.getIndexedValue(i, "3. Obręb ewidencyjny", "B: nazwa obrębu ewidencyjnego"),
                        section.getIndexedValue(i, "4. Położenie"),
                        section.getIndexedValue(i, "5. Ulica"),
                        section.getIndexedValue(i, "6. Sposób korzystania")
                ))
                .takeWhile(parcel -> !parcel.isEmpty())
                .toList();
    }

    private static Area parseArea(Section section) {
        return new Area(section.getValue("1. Obszar"));
    }

    private static void parseChanges(Section section, Map<Integer, Change> changes) {
        // collect change numbers
        var changeNumbers = section.entries().stream()
                .map(entry -> Integer.parseInt(entry.keys().getFirst()))
                .distinct()
                .toList();

        changeNumbers.stream()
                .map(i -> new Change(
                        i,
                        section.getChangeValue(i, new String[]{"3. Data sporządzenia", "2. Data sporządzenia", "3. Data wydania", "3. Data wydania orzeczenia"}),
                        section.getChangeValue(i, new String[]{"1. Tytuł aktu", "1. Podstawa oznaczenia (sprostowania)", "1. Wskazanie podstawy", "1. Rodzaj i przedmiot orzeczenia"}),
                        section.getChangeValue(i, new String[]{"3. Nazwa organu", "Notariusz", "4. Wystawca", "4. Nazwa sądu"})
                ))
                .filter(change -> change.date() != null || change.description() != null || change.entity() != null)
                .forEach(change -> changes.put(change.number(), change));
    }

    private static MigrationComment parseMigrationComments(Section section) {
        return new MigrationComment(section.getValue("A: Wpisy lub części wpisów, ujawnione w księdze wieczystej w toku migracji, które zawierają treść nie objętą strukturą księgi wieczystej lub projekty wpisów przeniesione z dotychczasowej księgi wieczystej"));
    }
}
