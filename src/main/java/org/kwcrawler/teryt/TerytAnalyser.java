package org.kwcrawler.teryt;


import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import org.kwcrawler.CourtCode;
import org.kwcrawler.KWNumber;
import org.kwcrawler.analyser.AnalysedRegister;
import org.kwcrawler.analyser.AnalysedRegister.Location;
import org.kwcrawler.analyser.AnalysedRegister.Parcel;
import org.kwcrawler.parser.ValueHistory;
import org.kwcrawler.structure.Filenames;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class TerytAnalyser {

    private final ConcurrentHashMap<String, String> districtByCode = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<RegionTeryt>> regionByName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<RegionTeryt>> regionByCity = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<CommuneTeryt>> communeByCityName = new ConcurrentHashMap<>();

    public void learn(KWNumber kwNumber, AnalysedRegister register) {
        if (register.locations().size() != 1) {
            return;
        }

        for (var parcel : register.parcels()) {
            var locations = TerytUtils.findLocationsForParcel(register, parcel);

            learnParcel(locations, parcel, kwNumber);
        }
    }

    private void learnParcel(List<Location> locations, Parcel parcel, KWNumber kwNumber) {
        var parcelId = parcel.parcelId().currentValue();
        if (parcelId == null) {
            return;
        }
        var parcelTeryt = ParcelTeryt.fromParcelId(parcelId);
        if (parcelTeryt == null) {
            return;
        }

        //learnDistrict(locations, kwNumber, parcelTeryt);
        learnRegionByName(parcel, parcelTeryt);
        learnRegionByCityName(locations, parcelTeryt);
        learnCommuneByCityName(locations, parcelTeryt);
    }

    private String fixVoivodeshipName(KWNumber kwNumber, String voivodeshipName) {
        for (var entry : VOIVODESHIP_CORRECTIONS.entrySet()) {
            if (voivodeshipName.equals(entry.getKey())) {
                System.out.println("Fixing voivodeship name for " + kwNumber + " from " + voivodeshipName + " to " + entry.getValue()); voivodeshipName = entry.getValue();
                break;
            }
        }
        return voivodeshipName;
    }

    private static final Map<String, String> VOIVODESHIP_CORRECTIONS = Map.of(
            "KUJAWSKO- POMORSKIE", "KUJAWSKO-POMORSKIE",
            "WŁOCŁAWSKIE", "KUJAWSKO-POMORSKIE",
            "KUJAWSKO - POMORSKIE", "KUJAWSKO-POMORSKIE"
            // Add other corrections here
    );

    private void learnDistrict(Location location, KWNumber kwNumber, ParcelTeryt parcelTeryt) {
        var districtName = location.district().currentValue();
        if (districtName == null) {
            return;
        }

        var existingDistrictName = districtByCode.get(parcelTeryt.districtCode());

        if (existingDistrictName == null) {
            districtByCode.put(parcelTeryt.districtCode(), districtName);
        } else if (!existingDistrictName.equals(districtName)) {
            System.out.println("District mismatch: " + kwNumber + " 1:" + districtName + " 2:" + existingDistrictName);
        }
    }

    private void learnRegionByName(Parcel parcel, ParcelTeryt parcelTeryt) {
        var regionName = parcel.region().currentValue();
        if (regionName == null) {
            return;
        }

        var regionList = regionByName.get(regionName);

        if (regionList == null) {
            regionList = Collections.synchronizedList(new ArrayList<>());
            regionList.add(parcelTeryt.toRegion());
            regionByName.put(regionName, regionList);
        } else {
            // Synchronize on the list to avoid concurrent modification exception,
            // because stream() is not synchronized
            synchronized (regionList) {
                var existingRegion = regionList.stream()
                        .filter(region -> region.equals(parcelTeryt.toRegion()))
                        .findAny();
                if (existingRegion.isEmpty()) {
                    regionList.add(parcelTeryt.toRegion());
                } else {
                    existingRegion.get().verify();
                }
            }
        }
    }

    private void learnRegionByCityName(List<Location> locations, ParcelTeryt parcelTeryt) {
        locations.stream()
                .map(location -> location.city().currentValue())
                .filter(Objects::nonNull)
                .forEach(cityName -> learnRegionByCityName(cityName, parcelTeryt));
    }

    private void learnRegionByCityName(String cityName, ParcelTeryt parcelTeryt) {
        if (cityName == null) {
            return;
        }

        var regionList = regionByCity.get(cityName);
        if (regionList == null) {
            regionList = Collections.synchronizedList(new ArrayList<>());
            regionList.add(parcelTeryt.toRegion());
            regionByCity.put(cityName, regionList);
        } else {
            synchronized (regionList) {
                var existingRegion = regionList.stream()
                        .filter(region -> region.equals(parcelTeryt.toRegion()))
                        .findAny();
                if (existingRegion.isEmpty()) {
                    regionList.add(parcelTeryt.toRegion());
                } else {
                    existingRegion.get().verify();
                }
            }
        }
    }

    private void learnCommuneByCityName(List<Location> location, ParcelTeryt parcelTeryt) {
        location.stream()
                .map(Location::city)
                .map(ValueHistory::currentValue)
                .filter(Objects::nonNull)
                .forEach(cityName -> learnCommuneByCityName(cityName, parcelTeryt));
    }

    private void learnCommuneByCityName(String cityName, ParcelTeryt parcelTeryt) {
        if (cityName == null) {
            return;
        }

        var communeList = communeByCityName.get(cityName);
        if (communeList == null) {
            communeList = Collections.synchronizedList(new ArrayList<>());
            communeList.add(parcelTeryt.toCommune());
            communeByCityName.put(cityName, communeList);
        } else {
            synchronized (communeList) {
                var existingCommune = communeList.stream()
                        .filter(commune -> commune.equals(parcelTeryt.toCommune()))
                        .findAny();
                if (existingCommune.isEmpty()) {
                    communeList.add(parcelTeryt.toCommune());
                } else {
                    existingCommune.get().verify();
                }
            }
        }

    }

    public void writeMappings(CourtCode courtCode) {
        writeRegionMapping(courtCode);
        writeCityMapping(courtCode);
        writeCommuneMapping(courtCode);
    }

    public void writeRegionMapping(CourtCode courtCode) {
        var mapper = new CsvMapper();
        var dataDir = Filenames.getDataDir(courtCode);
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        var outputFile = dataDir.resolve("region-teryt.csv");
        var objectWriter = mapper.writerFor(RegionTerytMapping.class).with(RegionTerytMapping.schema);

        try(var stream = Files.newOutputStream(outputFile);
            var writer = objectWriter.writeValues(stream)) {

            for (var entry : regionByName.entrySet()) {
                var regionName = entry.getKey();
                var codes = entry.getValue();

                // Sort the codes list based on getVerificationCount in descending order
                codes.sort(Comparator.comparingInt(RegionTeryt::getVerificationCount).reversed());

                // Get the max verification count
                var max = codes.getFirst();

                if (max == null || max.getVerificationCount() < 100) {
                    continue;
                }

                if (codes.size() >= 2) {
                    var secondMax = codes.get(1);

                    if (max.getVerificationCount() < 5 * secondMax.getVerificationCount()) {
                        continue;
                    }
                }


                writer.write(new RegionTerytMapping(regionName, max.toString()));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void writeCityMapping(CourtCode courtCode) {
        var mapper = new CsvMapper();
        var dataDir = Filenames.getDataDir(courtCode);
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        var outputFile = dataDir.resolve("city-teryt.csv");
        var objectWriter = mapper.writerFor(CityTerytMapping.class).with(CityTerytMapping.schema);

        try(var stream = Files.newOutputStream(outputFile);
            var writer = objectWriter.writeValues(stream)) {

            for (var entry : regionByCity.entrySet()) {
                var cityName = entry.getKey();
                var codes = entry.getValue();

                // Sort the codes list based on getVerificationCount in descending order
                codes.sort(Comparator.comparingInt(RegionTeryt::getVerificationCount).reversed());

                // Get the max verification count
                var max = codes.getFirst();

                if (max == null || max.getVerificationCount() < 50) {
                    continue;
                }

                if (codes.size() >= 2) {
                    var secondMax = codes.get(1);

                    if (max.getVerificationCount() < 10 * secondMax.getVerificationCount()) {
                        continue;
                    }
                }
                if (codes.size() != 1) {
                    System.out.println("City " + cityName + " has " + codes.size() + " regions: ");
                    for (var code : codes) {
                        System.out.println("    " + code + " - " + code.getVerificationCount());
                    }
                }

                System.out.println("City " + cityName + " selected region " + codes.getFirst());
                writer.write(new CityTerytMapping(cityName, codes.getFirst().toCode()));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void writeCommuneMapping(CourtCode courtCode) {
        var mapper = new CsvMapper();
        var dataDir = Filenames.getDataDir(courtCode);
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        var outputFile = dataDir.resolve("commune-teryt.csv");
        var objectWriter = mapper.writerFor(CityToCommuneTerytMapping.class).with(CityToCommuneTerytMapping.schema);

        try(var stream = Files.newOutputStream(outputFile);
            var writer = objectWriter.writeValues(stream)) {

            for (var entry : communeByCityName.entrySet()) {
                var cityName = entry.getKey();
                var codes = entry.getValue();

                // Sort the codes list based on getVerificationCount in descending order
                codes.sort(Comparator.comparingInt(CommuneTeryt::getVerificationCount).reversed());

                // Get the max verification count
                var max = codes.getFirst();

                if (max == null || max.getVerificationCount() < 50) {
                    continue;
                }

                if (codes.size() >= 2) {
                    var secondMax = codes.get(1);

                    System.out.println("City " + cityName + " has " + codes.size() + " communes: ");
                    for (var code : codes) {
                        System.out.println("    " + code + " - " + code.getVerificationCount());
                    }

                    if (max.getVerificationCount() < 10 * secondMax.getVerificationCount()) {
                        continue;
                    }
                }

                System.out.println("City " + cityName + " selected commune " + codes.getFirst());
                writer.write(new CityToCommuneTerytMapping(cityName, codes.getFirst().toString()));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
