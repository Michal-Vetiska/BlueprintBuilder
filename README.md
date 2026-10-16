# Blueprint Builder

Survival mod pro Minecraft Java 1.21.1. Hráč si v knihovně vybere plán, položí průhledný náhled a materiály nosí do stavební truhly. Stavba nečeká, až bude všechno pohromadě: jakmile je v truhle použitelný předmět, server z něj položí odpovídající blok a pokračuje po etapách (základy, podlaha, podpěry, stěny, střecha, okna a dveře, dekorace). Když materiál dojde, stavba se zastaví a po doplnění zase naváže.

## Požadavky

- Minecraft **1.21.1**
- NeoForge **21.1.256** (verze z oficiálního MDK ModDevGradle)
- Java **21**

Mod musí být nainstalovaný na klientu i na serveru. Ve hře pro jednoho hráče stačí jedna instalace.

## Instalace

1. Nainstaluj NeoForge pro Minecraft 1.21.1 z [neoforged.net](https://neoforged.net/).
2. Sestav mod, nebo použij hotový soubor `build/libs/blueprintbuilder-1.0.0.jar`.
3. Zkopíruj JAR do složky `mods` v instanci Minecraftu.
4. Spusť hru s profilem NeoForge.

## Ovládání

- **B** otevře knihovnu plánů.
- Karta ukazuje název, kategorii, rozměry, část materiálů, miniaturu a postup, pokud už stavba v načtených chuncích běží.
- Zaškrtávátko **Generate Construction Camp** zkusí vedle stavby postavit malý tábor (stan z vlny, postel, crafting table, pec, ohniště, lucernu). Když se nevejde, hra to napíše a stavba domu pokračuje bez tábora.
- Kolečko myši otáčí náhled po 90°.
- Červený obrys znamená, že buňka není volná. Vzduch a nízká vegetace se nahradit smí. Voda, láva a pevné bloky ne. Terén se sám nečistí.
- **Pravé tlačítko** položí staveniště. Server umístění znovu ověří.
- **Esc** umísťování zruší.
- Do stavební truhly dávej předměty ručně nebo hopperem. Výchozí rychlost jsou 3 bloky za sekundu. Strop za jeden tick je v server configu `config/blueprintbuilder-server.toml`.

Rozbití truhly vysype zbylé materiály a zbývající stavbu zruší. Bloky, které už stojí, zůstanou.

## Vývoj

V kořeni projektu:

```bash
./gradlew test
./gradlew build
./gradlew runClient
```

`build` vytvoří `build/libs/blueprintbuilder-1.0.0.jar`. Testy pokrývají výpočet materiálů (dveře a postel = jeden předmět) a pořadí fronty včetně závislostí.

## Nový plán ze Structure Blocku

Konstrukční kód konkrétní bloky domu nezná. Čte vanilla structure NBT.

1. Postav stavbu ve světě.
2. Structure Block nastav na **Save**, ulož šablonu. Hra zapíše `.nbt` (často do `generated/<svět>/structures` nebo do složky struktur ve světě).
3. Soubor zkopíruj do

   `src/main/resources/data/blueprintbuilder/structure/moje_stavba.nbt`

4. Přidej popis

   `src/main/resources/data/blueprintbuilder/blueprint/moje_stavba.json`

```json
{
  "id": "moje_stavba",
  "name": "Moje stavba",
  "description": "Krátký popis.",
  "category": "Houses",
  "structure": "blueprintbuilder:moje_stavba"
}
```

5. Doplň texty do `assets/blueprintbuilder/lang/en_us.json` a `cs_cz.json`:

- `blueprint.blueprintbuilder.moje_stavba.name`
- `blueprint.blueprintbuilder.moje_stavba.description`
- `category.blueprintbuilder.houses` (kategorie se bere z pole `category`, malými písmeny)

6. Znovu sestav JAR.

Registr načte každý JSON ve složce `blueprint`, který nekončí na `.blocks.json`. Poškozený soubor se přeskočí a důvod se zapíše do logu.

Když `.nbt` chybí, loader složí stejný structure tag z `blueprint/<id>.blocks.json` a pošle ho do `StructureTemplate.load`. Ukázková chalupa a tábor vznikají skriptem `tools/generate_structures.py`, aby šly upravovat bez spuštěné hry. Ve hře uložené Structure Block NBT je preferovaný formát.

### Co se počítá jako materiál

- Vzduch, `structure_void`, structure block a jigsaw nic nestojí.
- Horní půlka dveří a hlava postele se položí spolu s druhou půlkou a stojí jeden předmět.
- Z NBT block entity se zahazují klíče `Items`, `LootTable`, `Book` a podobné, aby plán nerozdával předměty zdarma.

### Pořadí stavby

Fronta je seřazená podle etapy, pak podle Y, Z a X. Blok se položí, až když je hotová pevná buňka pod ním. Přesah (okraj střechy) čeká na hotového souseda. Zavěšená lucerna čeká na blok nad sebou. Když chybí jeden materiál, staví se jiné části, jejichž podpora už stojí.

## Omezení této verze

- Claim módy jiných autorů se nekontrolují. Ochrana je „nepřepisuj blok, který není vzduch ani nízká vegetace“.
- Ručně položená stavební truhla z kreativního inventáře nemá přiřazený plán a sama nestaví. Plán vzniká potvrzením náhledu.
- Náhled ve světě je mírně zmenšený model bloku. Neplatné buňky jsou červený obrys. Miniatura na kartě je izometrický náčrt z barvy mapy, ne 3D render v GUI.
- Dveře a postele se pokládají jako jeden krok. Jiné dvojbloky se stejným `half=upper` / `part=head` se chovají stejně, pokud jsou v šabloně obě půlky.

## Ověření

`./gradlew test` a `./gradlew build` prošly. Jednotkové testy nepouští Minecraft. Spuštění herního klienta v tomhle prostředí není v README potvrzené, dokud log `runClient` opravdu nedoběhne do menu.
