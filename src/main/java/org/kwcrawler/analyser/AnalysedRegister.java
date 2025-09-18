package org.kwcrawler.analyser;


import com.fasterxml.jackson.annotation.JsonIgnore;
import org.kwcrawler.parser.ValueHistory;

import java.util.List;

public record AnalysedRegister(
        BasicInfo basicInfo,
        List<Location> locations,
        List<Apartment> apartments,
        List<Parcel> parcels,
        Area area,
        List<Owner> owners,
        List<LegalOwner> legalOwners,
        List<TreasuryOwner> treasuryOwners,
        List<CommuneOwner> communeOwners,
        List<Owner> authorizeds,
        List<Change> changes,
        MigrationComment migrationComment
) {

    public record BasicInfo(
            ValueHistory registerType
    ) {
        @JsonIgnore
        public boolean isEmpty() {
            return registerType.isEmpty();
        }
    }

    public record Location(
            ValueHistory number,
            ValueHistory voivodeship,
            ValueHistory district,
            ValueHistory commune,
            ValueHistory city
    ) {
        @JsonIgnore
        public boolean isEmpty() {
            return number().isEmpty() && voivodeship.isEmpty() && district.isEmpty() && commune.isEmpty() && city.isEmpty();
        }
    }

    public record Apartment(
            ValueHistory street,
            ValueHistory buildingNumber,
            ValueHistory apartmentNumber,
            ValueHistory purpose,
            ValueHistory floor
    ) {
        @JsonIgnore
        public boolean isEmpty() {
            return street.isEmpty() && buildingNumber.isEmpty() && apartmentNumber.isEmpty() && purpose.isEmpty() && floor.isEmpty();
        }
    }

    public record Owner(
            ValueHistory name,
            ValueHistory surname,
            ValueHistory fatherName,
            ValueHistory motherName,
            ValueHistory pesel
    ) {
        @JsonIgnore
        public boolean isEmpty() {
            return name.isEmpty() && surname.isEmpty() && fatherName.isEmpty() && motherName.isEmpty() && pesel.isEmpty();
        }
    }

    public record LegalOwner(
            ValueHistory name,
            ValueHistory place,
            ValueHistory regon
    ) {
        @JsonIgnore
        public boolean isEmpty() {
            return name.isEmpty() && place.isEmpty() && regon.isEmpty();
        }
    }

    public record TreasuryOwner(
            ValueHistory name,
            ValueHistory place,
            ValueHistory regon,
            ValueHistory role
    ) {
        @JsonIgnore
        public boolean isEmpty() {
            return name.isEmpty() && place.isEmpty() && regon.isEmpty() && role.isEmpty();
        }
    }

    public record CommuneOwner(
            ValueHistory name,
            ValueHistory place,
            ValueHistory regon
    ) {
        @JsonIgnore
        public boolean isEmpty() {
            return name.isEmpty() && place.isEmpty() && regon.isEmpty();
        }
    }

    public record Parcel(
            ValueHistory parcelId,
            ValueHistory parcelNumber,
            ValueHistory regionNumber, // numer obrębu
            ValueHistory region, // obręb
            ValueHistory location,
            ValueHistory street,
            ValueHistory usageType
    ) {
        @JsonIgnore
        public boolean isEmpty() {
            return parcelId.isEmpty() && parcelNumber.isEmpty() && regionNumber.isEmpty() && region.isEmpty() && location.isEmpty() && street.isEmpty() && usageType.isEmpty();
        }
    }

    public record Area(
            ValueHistory area
    ) {
        @JsonIgnore
        public boolean isEmpty() {
            return area.isEmpty();
        }
    }

    public record Change(
            Integer number,
            String date,
            String description,
            String entity
    ) {}

    public record MigrationComment(
            ValueHistory comment
    ) {}
}
