# KW Crawler

## Downloading

```bash
java -jar ../kw-crawler/target/kw-crawler-1.0-SNAPSHOT.jar download --max 160000 WL1A/00XXXXXX/X
```

## Searching

First index the data for a given district:

```bash
java -jar ../kw-crawler/target/kw-crawler-1.0-SNAPSHOT.jar index WL1A 
```

Then you can search the index:

```bash
java -jar ../kw-crawler/target/kw-crawler-1.0-SNAPSHOT.jar search 'text'
```

or

```bash
java -jar ../kw-crawler/target/kw-crawler-1.0-SNAPSHOT.jar search '+lokalizacja:Miedźno +właściciel:Jan'
```

## Mapping

1. Start Docker compose with PostGis and GeoServer:
    ```bash
    docker-compose up -d
    ````
2. Create a table in PostGis:
    ```bash
    pgcli -h localhost -p 15432 -U postgres -d postgres
    ```

    ```sql
    CREATE TABLE my_geometries (id text, description text, geom GEOMETRY(Geometry, 2180));
    ````
3. Map the data:
    ```bash
    java -jar ../kw-crawler/target/kw-crawler-1.0-SNAPSHOT.jar map WL1A
    ```
4. Add layer into GeoServer:
    - Go to http://localhost:8080/geoserver/
    - Log in with admin:geoserver
    - Add new workspace: local
    - Add new store: PostGIS:
        - Workspace: local
        - Data Source Name: kw
        - Host: postgis
        - User: postgres
        - Password: postgres
        - Schema: public
        - Table: my_geometries
    - Add new layer: my_geometries
5. Define the following style from the file `geoserver-style.xml` and make it a default for a layer

6. Open index.html in browser
