package com.revix.app.data

internal object CarCatalog {
    val models: Map<String, Array<String>> = mapOf(
        "Abarth" to arrayOf(
            "500", "595", "695", "124 Spider", "Punto", "Grande Punto",
            "Punto Evo",        ),
        "Acura" to arrayOf(
            "ILX", "TLX", "RLX", "RDX", "MDX", "NSX",
            "Integra", "Legend", "RSX", "TSX", "TL", "RL",
            "ZDX",        ),
        "Aixam" to arrayOf(
            "City", "Crossline", "Crossover", "Coupe", "D-Truck",        ),
        "Alfa Romeo" to arrayOf(
            "145", "146", "147", "155", "156", "156 Sportwagon",
            "159", "159 Sportwagon", "164", "166", "GT", "GTV",
            "Spider", "Brera", "MiTo", "Giulietta", "Giulia", "Stelvio",
            "Tonale", "Junior", "4C", "8C Competizione", "Crosswagon Q4", "33",        ),
        "Alpina" to arrayOf(
            "B3", "B4", "B5", "B6", "B7", "B8",
            "D3", "D4", "D5", "XD3", "XD4",        ),
        "Alpine" to arrayOf(
            "A110", "A290", "A390",        ),
        "Aston Martin" to arrayOf(
            "DB7", "DB9", "DB11", "DB12", "DBS", "Vantage",
            "V8 Vantage", "Vanquish", "Rapide", "Valkyrie", "DBX", "Cygnet",        ),
        "Audi" to arrayOf(
            "A1", "A2", "A3", "A4", "A4 Allroad", "A5",
            "A6", "A6 Allroad", "A7", "A8", "Allroad", "TT",
            "TTS", "TT RS", "R8", "Q2", "Q3", "Q3 Sportback",
            "Q4 e-tron", "Q5", "Q5 Sportback", "Q6 e-tron", "Q7", "Q8",
            "Q8 e-tron", "e-tron", "e-tron GT", "RS e-tron GT", "S1", "S3",
            "S4", "S5", "S6", "S7", "S8", "SQ2",
            "SQ5", "SQ7", "SQ8", "RS3", "RS4", "RS5",
            "RS6", "RS7", "RS Q3", "RS Q8", "80", "90",
            "100", "Cabriolet", "Coupe",        ),
        "BAIC" to arrayOf(
            "BJ40", "X55", "EU5", "X7",        ),
        "Bentley" to arrayOf(
            "Continental GT", "Continental Flying Spur", "Flying Spur", "Bentayga", "Mulsanne", "Arnage",
            "Azure", "Brooklands",        ),
        "BMW" to arrayOf(
            "1 Series", "114", "116", "118", "120", "123",
            "125", "128", "130", "135", "1M", "M135i",
            "M140i", "2 Series", "2 Active Tourer", "2 Gran Coupe", "2 Gran Tourer", "216",
            "218", "220", "225", "228", "230", "M2",
            "3 Series", "3 GT", "316", "318", "320", "323",
            "325", "328", "330", "335", "340", "M3",
            "4 Series", "418", "420", "428", "430", "435",
            "440", "M4", "5 Series", "5 GT", "518", "520",
            "523", "525", "528", "530", "530e", "535",
            "540", "545", "550", "M5", "6 Series", "6 GT",
            "630", "635", "640", "645", "650", "M6",
            "7 Series", "730", "735", "740", "745", "750",
            "760", "8 Series", "840", "850", "M8", "X1",
            "X2", "X3", "X3 M", "X4", "X4 M", "X5",
            "X5 M", "X6", "X6 M", "X7", "XM", "Z3",
            "Z4", "Z8", "i3", "i4", "i5", "i7",
            "i8", "iX", "iX1", "iX2", "iX3",        ),
        "Bugatti" to arrayOf(
            "Veyron", "Chiron", "Divo", "Tourbillon",        ),
        "Buick" to arrayOf(
            "Enclave", "Envision", "LaCrosse", "Regal", "Verano", "Encore",        ),
        "BYD" to arrayOf(
            "Atto 2", "Atto 3", "Dolphin", "Dolphin Surf", "Han", "Seal",
            "Seal U", "Sealion 7", "Tang", "Song",        ),
        "Cadillac" to arrayOf(
            "CTS", "ATS", "XTS", "CT4", "CT5", "CT6",
            "SRX", "XT4", "XT5", "XT6", "Escalade", "Lyriq",
            "BLS",        ),
        "Changan" to arrayOf(
            "CS35", "CS55", "CS75", "Deepal", "Alsvin", "UNI-T",
            "UNI-K",        ),
        "Chery" to arrayOf(
            "Tiggo 2", "Tiggo 4", "Tiggo 7", "Tiggo 8", "Arrizo", "QQ",
            "Omoda 5",        ),
        "Chevrolet" to arrayOf(
            "Spark", "Aveo", "Kalos", "Lacetti", "Cruze", "Orlando",
            "Captiva", "Trax", "Tracker", "Malibu", "Impala", "Camaro",
            "Corvette", "Silverado", "Colorado", "Equinox", "Traverse", "Tahoe",
            "Suburban", "Blazer", "Volt", "Bolt", "Epica", "Evanda",
            "Matiz", "Nubira", "Tacuma",        ),
        "Chrysler" to arrayOf(
            "300", "300C", "Pacifica", "Voyager", "Grand Voyager", "PT Cruiser",
            "Neon", "Sebring", "Crossfire", "Delta", "Ypsilon",        ),
        "Citroen" to arrayOf(
            "C1", "C2", "C3", "C3 Picasso", "C3 Aircross", "C4",
            "C4 Picasso", "Grand C4 Picasso", "C4 Cactus", "C4 X", "C5", "C5 Aircross",
            "C5 X", "C6", "C8", "Berlingo", "e-Berlingo", "SpaceTourer",
            "Jumper", "Jumpy", "Saxo", "Xsara", "Xsara Picasso", "Xantia",
            "XM", "Ami", "e-C3", "e-C4", "Nemo", "C-Elysee",
            "C-Zero", "DS3", "DS4", "DS5", "Berlingo Multispace",        ),
        "Corvette" to arrayOf(
            "C5", "C6", "C7", "C8", "Z06", "ZR1",
            "Stingray",        ),
        "Cupra" to arrayOf(
            "Ateca", "Born", "Formentor", "Leon", "Leon Sportstourer", "Raval",
            "Tavascan", "Terramar",        ),
        "Dacia" to arrayOf(
            "Logan", "Logan MCV", "Logan Pickup", "Sandero", "Sandero Stepway", "Duster",
            "Lodgy", "Dokker", "Dokker Van", "Jogger", "Spring", "Bigster",
            "Solenza", "Nova",        ),
        "Daewoo" to arrayOf(
            "Matiz", "Lanos", "Nubira", "Leganza", "Tacuma", "Kalos",
            "Lacetti", "Evanda", "Espero", "Tico", "Nexia",        ),
        "Daihatsu" to arrayOf(
            "Sirion", "Terios", "Cuore", "Charade", "Materia", "Copen",
            "Mira", "Feroza", "Rocky", "YRV",        ),
        "DFSK" to arrayOf(
            "Glory 500", "Glory 580", "C35", "K01",        ),
        "Dodge" to arrayOf(
            "Caliber", "Avenger", "Journey", "Nitro", "Durango", "Charger",
            "Challenger", "Ram", "Grand Caravan", "Viper", "Hornet", "Dakota",
            "Magnum",        ),
        "DONGFENG" to arrayOf(
            "Shine", "T5", "AX7", "Boxer",        ),
        "Dr" to arrayOf(
            "DR 1.0", "DR 3", "DR 4", "DR 5.0", "DR 6.0",        ),
        "DR Automobiles" to arrayOf(
            "DR 1.0", "DR 3", "DR 4", "DR 5.0", "DR 6.0", "DR 7.0",        ),
        "DS" to arrayOf(
            "DS 3", "DS 4", "DS 5", "DS 7", "DS 9", "DS 3 Crossback",
            "DS 4 Crossback", "DS3", "DS4", "DS5", "DS7", "DS9",        ),
        "Ferrari" to arrayOf(
            "360", "430", "458", "488", "F8", "SF90",
            "Roma", "Portofino", "812", "LaFerrari", "F12", "California",
            "F355", "599", "612", "296 GTB", "Purosangue", "12Cilindri",        ),
        "Fiat" to arrayOf(
            "Panda", "Punto", "Grande Punto", "Punto Evo", "Tipo", "500",
            "500C", "500L", "500X", "500e", "600", "600e",
            "Bravo", "Brava", "Stilo", "Croma", "Multipla", "Doblo",
            "Fiorino", "Ducato", "Scudo", "Talento", "Qubo", "Linea",
            "Sedici", "Freemont", "Fullback", "Idea", "Marea", "Palio",
            "Seicento", "Cinquecento", "Barchetta", "Coupe", "124 Spider", "Topolino",
            "Fastback", "Albea", "Ulysse", "Doblo Cargo",        ),
        "Fisker" to arrayOf(
            "Karma", "Ocean",        ),
        "Ford" to arrayOf(
            "Fiesta", "Focus", "Focus C-Max", "Mondeo", "Puma", "Kuga",
            "EcoSport", "Escape", "Explorer", "Explorer EV", "Edge", "Mustang",
            "Mustang Mach-E", "Ranger", "Maverick", "Transit", "Transit Custom", "Tourneo",
            "Tourneo Connect", "Tourneo Custom", "S-Max", "Galaxy", "C-Max", "B-Max",
            "Ka", "Ka+", "StreetKa", "Capri", "Courier", "Fusion",
            "Taurus", "F-150", "F-250", "Bronco", "Cougar", "Probe",
            "Scorpio", "Sierra", "Orion", "Windstar", "Tourneo Courier",        ),
        "Foton" to arrayOf(
            "Tunland", "Sauvana", "View",        ),
        "Gaz" to arrayOf(
            "Volga", "Gazelle", "Sobol", "Next",        ),
        "Geely" to arrayOf(
            "Coolray", "Atlas", "Emgrand", "Tugella", "Geometry C", "Xingyue",
            "Okavango",        ),
        "Genesis" to arrayOf(
            "G70", "G80", "G90", "GV60", "GV70", "GV80",
            "Coupe",        ),
        "Gmc" to arrayOf(
            "Sierra", "Canyon", "Acadia", "Terrain", "Yukon", "Savana",        ),
        "Great Wall" to arrayOf(
            "Wingle", "Poer", "Steed", "Hover", "Haval H6", "Ora 03",        ),
        "GWM" to arrayOf(
            "Ora 03", "Poer", "Wingle", "Haval Jolion", "Haval H6", "Tank 300",        ),
        "Haval" to arrayOf(
            "Jolion", "H6", "H2", "H4", "H8", "H9",
            "F7", "Dargo",        ),
        "Honda" to arrayOf(
            "Civic", "Civic Type R", "Civic Tourer", "Jazz", "Fit", "Accord",
            "CR-V", "HR-V", "ZR-V", "FR-V", "Stream", "Prelude",
            "S2000", "NSX", "Legend", "Insight", "CR-Z", "e",
            "e:Ny1", "Shuttle", "Odyssey", "Pilot", "Passport", "Ridgeline",
            "City", "Integra", "Element", "CRX", "Logo", "Concerto",        ),
        "HongQi" to arrayOf(
            "H5", "H9", "E-HS9", "HS5",        ),
        "Hummer" to arrayOf(
            "H1", "H2", "H3", "EV SUV", "EV Pickup",        ),
        "Hyundai" to arrayOf(
            "i10", "i20", "i20 N", "i30", "i30 N", "i40",
            "ix20", "ix35", "Tucson", "Santa Fe", "Kona", "Kona Electric",
            "Bayon", "Getz", "Accent", "Elantra", "Sonata", "Ioniq",
            "Ioniq 5", "Ioniq 6", "Ioniq 9", "Staria", "H-1", "Palisade",
            "Venue", "Nexo", "Veloster", "Matrix", "Trajet", "Coupe",
            "Genesis", "Atos", "Santa Cruz", "iLoad",        ),
        "Ineos Grenadier" to arrayOf(
            "Grenadier", "Quartermaster", "Station Wagon",        ),
        "Infiniti" to arrayOf(
            "Q30", "Q50", "Q60", "Q70", "QX30", "QX50",
            "QX55", "QX60", "QX70", "QX80", "G35", "G37",
            "M35", "M45", "FX35", "FX45", "EX35", "JX35",        ),
        "Isuzu" to arrayOf(
            "D-Max", "MU-X", "Trooper", "Rodeo", "D-Max V-Cross", "NPR",        ),
        "Iveco" to arrayOf(
            "Daily", "Eurocargo", "Massif",        ),
        "JAC" to arrayOf(
            "T8", "T6", "JS4", "E30X", "iEV7S",        ),
        "Jaecoo" to arrayOf(
            "5", "7", "8",        ),
        "Jaguar" to arrayOf(
            "XE", "XF", "XJ", "X-Type", "S-Type", "F-Type",
            "E-Pace", "F-Pace", "I-Pace", "XK", "XKR", "XJR",        ),
        "Jeep" to arrayOf(
            "Wrangler", "Cherokee", "Grand Cherokee", "Compass", "Renegade", "Avenger",
            "Gladiator", "Patriot", "Commander", "Wagoneer", "Grand Wagoneer", "Liberty",        ),
        "Kia" to arrayOf(
            "Picanto", "Rio", "Ceed", "ProCeed", "XCeed", "Sportage",
            "Sorento", "Niro", "Stonic", "Stinger", "EV3", "EV4",
            "EV6", "EV9", "Carnival", "Carens", "Soul", "Venga",
            "Optima", "Magentis", "Cerato", "Pride", "Sephia", "Shuma",
            "K5", "Seltos", "Telluride", "PV5", "Ceed SW", "Pro_cee'd",
            "Carnival / Sedona",        ),
        "Lada" to arrayOf(
            "Niva", "Niva Travel", "Granta", "Kalina", "Vesta", "XRAY",
            "Largus", "Priora", "Samara", "2110", "2107", "21099",        ),
        "Lamborghini" to arrayOf(
            "Huracan", "Aventador", "Urus", "Gallardo", "Murcielago", "Diablo",
            "Revuelto", "Countach LPI 800-4",        ),
        "Lancia" to arrayOf(
            "Ypsilon", "Delta", "Musa", "Phedra", "Thesis", "Lybra",
            "Kappa", "Voyager", "Thema", "Flavia", "Dedra",        ),
        "Land Rover" to arrayOf(
            "Defender", "Discovery", "Discovery Sport", "Freelander", "Freelander 2", "Range Rover",
            "Range Rover Sport", "Range Rover Evoque", "Range Rover Velar", "Range Rover Sport SVR",        ),
        "Leapmotor" to arrayOf(
            "T03", "C10", "B10",        ),
        "Lexus" to arrayOf(
            "IS", "IS F", "IS 200", "IS 250", "IS 300", "ES",
            "GS", "LS", "NX", "RX", "RX 300", "RX 350",
            "RX 400h", "RX 450h", "GX", "LX", "LC", "RC",
            "CT", "HS", "SC", "LFA", "UX", "LBX",
            "RZ",        ),
        "Lincoln" to arrayOf(
            "Continental", "MKZ", "MKX", "Navigator", "MKC", "MKT",
            "Aviator", "Nautilus", "Corsair",        ),
        "Lotus" to arrayOf(
            "Elise", "Exige", "Evora", "Emira", "Eletre", "Europa",
            "Evija",        ),
        "Lucid" to arrayOf(
            "Air", "Gravity",        ),
        "LynkCo" to arrayOf(
            "01", "02", "03", "05", "06", "08",
            "09",        ),
        "Mahindra" to arrayOf(
            "Thar", "Scorpio", "XUV700", "XUV500", "Bolero",        ),
        "Maserati" to arrayOf(
            "Ghibli", "Quattroporte", "Levante", "MC20", "GranTurismo", "GranCabrio",
            "Grecale", "Spyder", "3200 GT", "Coupe", "Gransport",        ),
        "Maxus" to arrayOf(
            "Deliver 3", "Deliver 7", "Deliver 9", "eDeliver 3", "eDeliver 9", "Mifa 9",
            "T90", "Euniq 5",        ),
        "Maybach" to arrayOf(
            "57", "62", "S 580", "S 680", "GLS 600",        ),
        "Mazda" to arrayOf(
            "2", "3", "5", "6", "323", "626",
            "CX-3", "CX-30", "CX-5", "CX-50", "CX-60", "CX-7",
            "CX-80", "CX-9", "MX-5", "MX-30", "RX-7", "RX-8",
            "Premacy", "Tribute", "BT-50", "Xedos 6", "Xedos 9", "MPV",
            "Demio",        ),
        "McLaren" to arrayOf(
            "MP4-12C", "650S", "675LT", "570S", "540C", "720S",
            "600LT", "GT", "Artura", "P1", "Senna", "Elva",
            "Speedtail", "765LT",        ),
        "Mercedes-Benz" to arrayOf(
            "A-Class", "A 140", "A 150", "A 160", "A 170", "A 180",
            "A 200", "A 220", "A 250", "A 35 AMG", "A 45 AMG", "B-Class",
            "B 150", "B 160", "B 170", "B 180", "B 200", "B 220",
            "B 250", "C-Class", "C 160", "C 180", "C 200", "C 220",
            "C 230", "C 240", "C 250", "C 270", "C 280", "C 300",
            "C 320", "C 350", "C 400", "C 32 AMG", "C 43 AMG", "C 55 AMG",
            "C 63 AMG", "E-Class", "E 200", "E 220", "E 240", "E 250",
            "E 270", "E 280", "E 300", "E 320", "E 350", "E 400",
            "E 420", "E 430", "E 450", "E 500", "E 55 AMG", "E 63 AMG",
            "S-Class", "S 280", "S 320", "S 350", "S 400", "S 430",
            "S 450", "S 500", "S 550", "S 560", "S 580", "S 600",
            "S 63 AMG", "S 65 AMG", "CLA", "CLA 180", "CLA 200", "CLA 220",
            "CLA 250", "CLA 35 AMG", "CLA 45 AMG", "CLS", "CLS 320", "CLS 350",
            "CLS 400", "CLS 450", "CLS 500", "CLS 53 AMG", "CLS 63 AMG", "CLK",
            "CLC", "CLE", "CL", "CL 500", "CL 600", "CL 63 AMG",
            "GLA", "GLB", "GLC", "GLC Coupe", "GLE", "GLE Coupe",
            "GLS", "GLK", "GL", "ML", "G-Class", "G 320",
            "G 350", "G 400", "G 500", "G 55 AMG", "G 63 AMG", "G 65 AMG",
            "SL", "SLK", "SLC", "SLR", "SLS AMG", "AMG GT",
            "AMG GT 4-Door", "EQA", "EQB", "EQC", "EQE", "EQS",
            "EQV", "EQE SUV", "EQS SUV", "Vito", "Viano", "V-Class",
            "Vaneo", "Sprinter", "Citan", "T-Class", "X-Class", "Maybach S-Class",
            "Maybach GLS",        ),
        "Mercury" to arrayOf(
            "Grand Marquis", "Sable", "Milan", "Mountaineer", "Mariner",        ),
        "Mg" to arrayOf(
            "3", "4", "5", "6", "7", "ZS",
            "HS", "HS PHEV", "Marvel R", "Cyberster", "TF", "F",
            "ZR", "ZT", "RX5",        ),
        "Microcar" to arrayOf(
            "M.Go", "Dué", "Virgo",        ),
        "Mini" to arrayOf(
            "Cooper", "Cooper S", "Cooper SE", "Cooper JCW", "One", "Countryman",
            "Clubman", "Cabrio", "Convertible", "Paceman", "Coupe", "Roadster",
            "Aceman",        ),
        "Mitsubishi" to arrayOf(
            "Lancer", "Lancer Evo", "Colt", "Carisma", "Galant", "Space Star",
            "ASX", "Eclipse Cross", "Outlander", "Outlander PHEV", "Pajero", "Pajero Sport",
            "Pajero Pinin", "L200", "Triton", "i-MiEV", "Grandis", "Eclipse",
            "3000GT",        ),
        "Morgan" to arrayOf(
            "4/4", "Plus 4", "Plus 6", "Plus 8", "Aero 8", "3 Wheeler",
            "Super 3",        ),
        "Nio" to arrayOf(
            "ET5", "ET7", "EL6", "EL8", "ES8", "Firefly",        ),
        "Nissan" to arrayOf(
            "Micra", "Note", "Almera", "Almera Tino", "Primera", "Qashqai",
            "Qashqai+2", "X-Trail", "Juke", "Leaf", "Ariya", "Navara",
            "350Z", "370Z", "GT-R", "Pulsar", "Tiida", "Cube",
            "Pathfinder", "Murano", "Townstar", "Primastar", "NV200", "e-NV200",
            "Terrano", "Patrol", "Skyline", "Maxima", "Altima", "Sentra",
            "Kicks", "Rogue", "Sunny", "Interstar", "Cabstar", "Armada",
            "Titan", "Frontier", "Evalia",        ),
        "Oldsmobile" to arrayOf(
            "Alero", "Aurora", "Bravada", "Intrigue",        ),
        "Omoda" to arrayOf(
            "5", "C5", "E5", "9",        ),
        "Opel" to arrayOf(
            "Corsa", "Astra", "Vectra", "Insignia", "Meriva", "Zafira",
            "Zafira Life", "Combo", "Combo Life", "Mokka", "Mokka-e", "Crossland",
            "Grandland", "Antara", "Frontera", "Adam", "Karl", "Agila",
            "Cascada", "Ampera", "Ampera-e", "GT", "Tigra", "Speedster",
            "Calibra", "Omega", "Signum", "Vivaro", "Movano", "Kadett",
            "Ascona", "Rekord", "Monterey", "Campo", "Rocks-e", "Astra GTC",
            "Astra Sports Tourer", "Insignia Sports Tourer",        ),
        "Ora" to arrayOf(
            "03", "Funky Cat", "Good Cat", "07",        ),
        "Peugeot" to arrayOf(
            "106", "107", "108", "205", "206", "206 CC",
            "207", "207 CC", "208", "e-208", "306", "307",
            "307 CC", "308", "308 SW", "e-308", "309", "405",
            "406", "407", "408", "508", "508 SW", "508 PSE",
            "605", "607", "806", "807", "1007", "2008",
            "e-2008", "3008", "e-3008", "4007", "4008", "5008",
            "301", "RCZ", "Partner", "Rifter", "Expert", "Traveller",
            "Boxer", "Bipper", "iOn", "e-Rifter", "e-Traveller",        ),
        "Plymouth" to arrayOf(
            "Neon", "Voyager", "Prowler", "Breeze",        ),
        "Polestar" to arrayOf(
            "1", "2", "3", "4", "5",        ),
        "Pontiac" to arrayOf(
            "Firebird", "Trans Am", "GTO", "Grand Prix", "Vibe", "Solstice",
            "G6", "G8",        ),
        "Porsche" to arrayOf(
            "911", "911 Carrera", "911 Turbo", "911 GT3", "Boxster", "Cayman",
            "718 Boxster", "718 Cayman", "Panamera", "Macan", "Cayenne", "Taycan",
            "944", "968", "928", "Carrera GT",        ),
        "Renault" to arrayOf(
            "Clio", "Clio RS", "Twingo", "Megane", "Megane RS", "Scenic",
            "Grand Scenic", "Captur", "Kadjar", "Koleos", "Austral", "Arkana",
            "Rafale", "Laguna", "Talisman", "Espace", "Scenic E-Tech", "5 E-Tech",
            "4 E-Tech", "Zoe", "Twizy", "Kangoo", "Trafic", "Master",
            "Fluence", "Modus", "Wind", "Vel Satis", "Safrane", "Symbol",
            "Thalia", "Express", "Symbioz", "19", "21", "Megane Scenic",
            "Megane CC", "Latitude", "Avantime",        ),
        "Rolls-Royce" to arrayOf(
            "Phantom", "Ghost", "Wraith", "Dawn", "Cullinan", "Spectre",
            "Silver Seraph",        ),
        "Rover" to arrayOf(
            "25", "45", "75", "200", "400", "600",
            "800", "Streetwise", "400 Tourer",        ),
        "Saab" to arrayOf(
            "900", "9000", "9-3", "9-5", "9-4X", "9-7X",        ),
        "Saturn" to arrayOf(
            "Vue", "Ion", "Aura", "Sky",        ),
        "Scion" to arrayOf(
            "tC", "xB", "xD", "iQ", "FR-S",        ),
        "Seat" to arrayOf(
            "Ibiza", "Ibiza FR", "Leon", "Leon FR", "Leon Cupra", "Toledo",
            "Cordoba", "Altea", "Altea XL", "Alhambra", "Ateca", "Arona",
            "Tarraco", "Mii", "Exeo", "Arosa", "Inca",        ),
        "Seres" to arrayOf(
            "3", "5", "SF5",        ),
        "Skoda" to arrayOf(
            "Fabia", "Fabia Combi", "Fabia RS", "Octavia", "Octavia Combi", "Octavia RS",
            "Octavia Scout", "Superb", "Superb Combi", "Superb iV", "Rapid", "Rapid Spaceback",
            "Roomster", "Yeti", "Karoq", "Kodiaq", "Kamiq", "Scala",
            "Citigo", "Enyaq", "Enyaq Coupe", "Elroq", "Felicia", "Favorit",        ),
        "Smart" to arrayOf(
            "Fortwo", "Forfour", "Roadster", "EQ Fortwo", "EQ Forfour", "#1",
            "#3",        ),
        "SsangYong" to arrayOf(
            "Korando", "Rexton", "Tivoli", "XLV", "Musso", "Rodius",
            "Actyon", "Kyron", "Chairman",        ),
        "Subaru" to arrayOf(
            "Impreza", "Impreza WRX", "WRX", "STI", "Legacy", "Outback",
            "Forester", "XV", "Crosstrek", "Ascent", "Tribeca", "Baja",
            "BRZ", "SVX", "Justy", "Levorg", "Solterra",        ),
        "Suzuki" to arrayOf(
            "Swift", "Swift Sport", "Baleno", "Celerio", "Ignis", "Vitara",
            "Grand Vitara", "S-Cross", "SX4", "Jimny", "Alto", "Wagon R+",
            "Liana", "Splash", "Kizashi", "Carry", "Samurai", "Jimmy",        ),
        "Tank" to arrayOf(
            "300", "500", "700",        ),
        "Tata" to arrayOf(
            "Safari", "Nexon", "Punch", "Tiago", "Harrier",        ),
        "Tesla" to arrayOf(
            "Model S", "Model 3", "Model X", "Model Y", "Roadster", "Cybertruck",
            "Semi",        ),
        "Toyota" to arrayOf(
            "Aygo", "Aygo X", "Yaris", "Yaris Cross", "Yaris Verso", "IQ",
            "Starlet", "Corolla", "Corolla Verso", "Corolla Cross", "Auris", "Avensis",
            "Avensis Verso", "Camry", "Prius", "Prius+", "Prius Plug-in", "C-HR",
            "RAV4", "Highlander", "Land Cruiser", "Land Cruiser Prado", "Hilux", "Proace",
            "Proace City", "Proace City Verso", "Proace Verso", "bZ4X", "GR Yaris", "GR86",
            "GR Supra", "Supra", "GT86", "Celica", "MR2", "Previa",
            "Picnic", "Verso", "Verso-S", "Urban Cruiser", "Carina E", "Crown",
            "Mirai", "FJ Cruiser", "4Runner", "Tacoma", "Tundra", "Sienna",
            "Sequoia", "Alphard", "Harrier", "Venza", "Avalon",        ),
        "Uaz" to arrayOf(
            "Patriot", "Hunter", "Pickup", "452", "469", "3163",        ),
        "VinFast" to arrayOf(
            "VF 3", "VF 6", "VF 7", "VF 8", "VF 9",        ),
        "Volkswagen" to arrayOf(
            "Golf", "Polo", "Passat", "Jetta", "Tiguan", "Touareg",
            "T-Roc", "T-Cross", "Arteon", "ID.3", "ID.4", "ID.5",
            "ID.7", "ID.Buzz",        ),
        "Volvo" to arrayOf(
            "S40", "S60", "S70", "S80", "S90", "V40",
            "V40 Cross Country", "V50", "V60", "V60 Cross Country", "V70", "V90",
            "V90 Cross Country", "XC40", "XC60", "XC70", "XC90", "C30",
            "C40", "C70", "EX30", "EX90", "850", "940",
            "960", "440", "460", "480", "740", "760",
            "780", "240",        ),
        "Voyah" to arrayOf(
            "Free", "Dream",        ),
        "VW" to arrayOf(
            "Golf", "Golf Plus", "Golf Sportsvan", "Golf Variant", "Golf GTI", "Golf R",
            "Golf GTE", "Polo", "Polo GTI", "Passat", "Passat CC", "Passat Alltrack",
            "Passat Variant", "Jetta", "Bora", "Vento", "Beetle", "New Beetle",
            "Scirocco", "Eos", "CC", "Arteon", "Sharan", "Touran",
            "Tiguan", "Tiguan Allspace", "Touareg", "T-Roc", "T-Cross", "Taigo",
            "Tayron", "Up", "Lupo", "Fox", "Caddy", "Caddy Life",
            "Caddy Maxi", "Multivan", "Caravelle", "Transporter", "Crafter", "Amarok",
            "Phaeton", "ID.3", "ID.4", "ID.5", "ID.7", "ID.Buzz",
            "Corrado", "Santana", "Taro", "LT",        ),
        "Wey" to arrayOf(
            "Coffee 01", "Coffee 02", "VV7",        ),
        "XPeng" to arrayOf(
            "G6", "G9", "P7", "P7+", "X9",        ),
        "Zeekr" to arrayOf(
            "001", "7X", "X", "009",        ),
    )
}
