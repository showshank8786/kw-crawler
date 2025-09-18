package org.kwcrawler;

import org.kwcrawler.analyser.AnalysedRegister;
import org.kwcrawler.parser.ParsedRegister;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class OwnerTypeSelector {
    private final Map<String, String> typeMapByPesel = new HashMap<>();
    private final Map<String, String> typeMapByName = new HashMap<>();

    public OwnerTypeSelector() {
        var yaml = new Yaml();

        // Load the YAML file
        try (var inputStream = Files.newInputStream(Path.of("groups.yaml"))) {
            var yamlData = yaml.<Map<String, Map<String, Map<String, Object>>>>load(inputStream);

            // Navigate the YAML structure
            var groups = yamlData.get("groups");
            groups.forEach((groupName, groupDetails) -> {
                var names = (List<String>) groupDetails.get("names");
                if (names != null) {
                    for (var name : names) {
                        typeMapByName.put(name, groupName);
                    }
                }

                var pesels = (List<String>) groupDetails.get("pesels");
                if (pesels != null) {
                    for (var pesel : pesels) {
                        typeMapByPesel.put(pesel, groupName);
                    }
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to load the YAML file", e);
        }
    }

    public String selectOwnerType(KWNumber kwNumber, AnalysedRegister analysed, AnalysedRegister.Parcel parcel) {
        var owners = analysed.owners();
        var authorizeds = analysed.authorizeds();

        var ownersAndAuthorizeds = Stream.concat(owners.stream(), authorizeds.stream()).toList();

        for (var owner : ownersAndAuthorizeds) {
            var pesel = owner.pesel().currentValue();
            if (pesel != null && typeMapByPesel.containsKey(pesel)) {
                return typeMapByPesel.get(pesel);
            }

            var name = owner.name().currentValue() + " " + owner.surname().currentValue() + " (" + owner.fatherName().currentValue() + ", " + owner.motherName().currentValue() + ")";
            if (typeMapByName.containsKey(name)) {
                return typeMapByName.get(name);
            }
        }

        return "default";
    }
}
