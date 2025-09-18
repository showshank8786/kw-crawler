package org.kwcrawler.teryt;


import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import org.kwcrawler.CourtCode;
import org.kwcrawler.analyser.AnalysedRegister;
import org.kwcrawler.analyser.AnalysedRegister.Parcel;
import org.kwcrawler.structure.Filenames;
import org.kwcrawler.teryt.TerytGuesser.GuessTerytResult.ParcelFound;
import org.kwcrawler.teryt.TerytGuesser.GuessTerytResult.ParcelNotFound;
import org.kwcrawler.teryt.TerytGuesser.GuessTerytResult.ParcelRemoved;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class TerytGuesser {
    private final Map<String, String> regionToRegionCode = new HashMap<>();
    private final Map<String, String> cityToRegionCode = new HashMap<>();
    private final Map<String, String> cityToCommuneCode = new HashMap<>();


    public TerytGuesser(CourtCode courtCode) {
        var dataDir = Filenames.getDataDir(courtCode);

        readRegionTeryt(dataDir);
        readCityTeryt(dataDir);
        readCommuneTerytByCity(dataDir);
    }

    private void readRegionTeryt(Path dataDir) {
        var inputFile = dataDir.resolve("region-teryt.csv");

        var mapper = new CsvMapper();
        var objectReader = mapper.readerFor(RegionTerytMapping.class).with(RegionTerytMapping.schema);

        try (var reader = Files.newBufferedReader(inputFile)) {
            MappingIterator<RegionTerytMapping> iterator = objectReader.readValues(reader);
            while (iterator.hasNext()) {
                var mapping = iterator.next();
                regionToRegionCode.put(mapping.region(), mapping.teryt());
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot read region-teryt.csv", e);
        }
    }

    private void readCityTeryt(Path dataDir) {
        var inputFile = dataDir.resolve("city-teryt.csv");

        var mapper = new CsvMapper();
        var objectReader = mapper.readerFor(CityTerytMapping.class).with(CityTerytMapping.schema);

        try (var reader = Files.newBufferedReader(inputFile)) {
            MappingIterator<CityTerytMapping> iterator = objectReader.readValues(reader);
            while (iterator.hasNext()) {
                var mapping = iterator.next();
                cityToRegionCode.put(mapping.city(), mapping.teryt());
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot read city-teryt.csv", e);
        }
    }

    private void readCommuneTerytByCity(Path dataDir) {
        var inputFile = dataDir.resolve("commune-teryt.csv");

        var mapper = new CsvMapper();
        var objectReader = mapper.readerFor(CityToCommuneTerytMapping.class).with(CityToCommuneTerytMapping.schema);

        try (var reader = Files.newBufferedReader(inputFile)) {
            MappingIterator<CityToCommuneTerytMapping> iterator = objectReader.readValues(reader);
            while (iterator.hasNext()) {
                var mapping = iterator.next();
                cityToCommuneCode.put(mapping.city(), mapping.teryt());
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot read commune-teryt.csv", e);
        }
    }


    public sealed interface GuessTerytResult permits ParcelFound, ParcelNotFound, ParcelRemoved {

        final class ParcelRemoved implements GuessTerytResult {
        }

        final class ParcelFound implements GuessTerytResult {
            private final ParcelTeryt parcel;

            public ParcelFound(ParcelTeryt parcel) {
                if (parcel == null) {
                    throw new IllegalArgumentException("Parcel cannot be null");
                }
                this.parcel = parcel;
            }

            public ParcelTeryt parcel() {
                return parcel;
            }
        }

        final class ParcelNotFound implements GuessTerytResult {
        }
    }



    public GuessTerytResult guessTeryt(AnalysedRegister register, Parcel parcel) {

        // 1. use parcelId if available
        var currentParcelId = parcel.parcelId().currentValue();
        if (currentParcelId != null) {
            var parcelTeryt = ParcelTeryt.fromParcelId(currentParcelId);
            if (parcelTeryt == null) {
                System.out.println("Cannot parse parcelId: " + currentParcelId + ", will try to guess from parcel number");
            } else {
                return new ParcelFound(parcelTeryt);
            }
        }

        var currentParcelNumber = parcel.parcelNumber().currentValue();

        if (currentParcelNumber == null) {
            return new ParcelRemoved(); // if both parcelId and parcelNumber are null, then this parcel was removed from the register
        }

        var currentLocations = TerytUtils.findLocationsForParcel(register, parcel);
        var currentCities = currentLocations.stream()
                .map(location -> location.city().currentValue())
                .distinct()
                .toList();

        // 2. use region number, city and parcel number if available
        var currentRegionNumber = parcel.regionNumber().currentValue();


        if (currentRegionNumber != null) {
            var communeCodes = currentCities.stream()
                    .map(cityToCommuneCode::get)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            if (communeCodes.size() == 1) {
                var communeTeryt = CommuneTeryt.fromCode(communeCodes.getFirst());
                var regionTeryt = RegionTeryt.fromCommune(communeTeryt, currentRegionNumber);
                if (regionTeryt != null) {
                    var parcelTeryt = ParcelTeryt.fromRegionAndParcelNumber(regionTeryt, currentParcelNumber);
                    return new ParcelFound(parcelTeryt);
                }
            } else if (communeCodes.size() > 1) {
                System.out.println("Multiple commune codes for cities: " + currentCities + ", commune codes: " + communeCodes + ", parcel number: " + currentParcelNumber);
            }
        }

        // 3. use region name and parcel number if available
        var currentRegion = parcel.region().currentValue();
        if (currentRegion != null) {
            var regionCode = regionToRegionCode.get(currentRegion);
            if (regionCode != null) {
                var regionTeryt = RegionTeryt.fromCode(regionCode);
                if (regionTeryt != null) {
                    return new ParcelFound(ParcelTeryt.fromRegionAndParcelNumber(regionTeryt, currentParcelNumber));
                }
            }
        }

        // 4. use city and parcel number if available
        var regionCodes = currentCities.stream()
                .map(cityToRegionCode::get)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (regionCodes.size() == 1) {
            var regionTeryt = RegionTeryt.fromCode(regionCodes.getFirst());
            if (regionTeryt != null) {
                return new ParcelFound(ParcelTeryt.fromRegionAndParcelNumber(regionTeryt, currentParcelNumber));
            }
        } else if (regionCodes.size() > 1) {
            //throw new IllegalStateException("Multiple region codes for cities: " + currentCities + ", region codes: " + regionCodes + ", parcel number: " + currentParcelNumber);
            System.out.println("Multiple region codes for cities: " + currentCities + ", region codes: " + regionCodes + ", parcel number: " + currentParcelNumber);
        }

        return new ParcelNotFound();
    }
}
