package org.kwcrawler.teryt;


import org.kwcrawler.analyser.AnalysedRegister;
import org.kwcrawler.analyser.AnalysedRegister.Parcel;

import java.util.List;

public class TerytUtils {
    static List<AnalysedRegister.Location> findLocationsForParcel(AnalysedRegister register, Parcel parcel) {
        var currentLocationNumbers = parcel.location().currentValues();

        return register.locations().stream()
                .filter(location -> currentLocationNumbers.isEmpty() || currentLocationNumbers.contains(location.number().currentValue()))
                .toList();
    }
}
