package com.example.clinometer.data

object
VehicleData {
    
    // Марки за коли (популярни първо, после азбучен ред)
    val carBrands = arrayOf(
        "Най-популярни", "Mercedes-Benz", "BMW", "Audi", "VW", "Toyota", "Opel", "Peugeot",
        "Abarth", "Acura", "Aixam", "Alfa Romeo", "Alpina", "Aro", "Asia", "Aston Martin", 
        "Austin", "BAIC", "BAW", "BENTU", "Bentley", "Brilliance", "Buick", "Cadillac", 
        "Carbodies", "Changan", "Chery", "Chevrolet", "Chrysler", "Citroen", "Corvette", 
        "Cupra", "DFSK", "DONGFENG", "DR Automobiles", "DS", "Dacia", "Daewoo", "Daihatsu", 
        "Daimler", "Datsun", "Dkw", "Dodge", "Dr", "Eagle", "Ferrari", "Fiat", "Fisker", 
        "Ford", "Foton", "GOUPIL", "GWM", "Gaz", "Geely", "Genesis", "Gmc", "Gonow", 
        "Great Wall", "Haval", "Hillman", "Honda", "HongQi", "Hummer", "Hyundai", "Ifa", 
        "Ineos Grenadier", "Infiniti", "Isuzu", "Iveco", "JAC", "Jaguar", "Jeep", "Kia", 
        "Lada", "Lamborghini", "Lancia", "Land Rover", "Landwind", "Lexus", "Lincoln", 
        "Lotus", "LynkCo", "Mahindra", "Maserati", "Maybach", "Mazda", "McLaren", 
        "Mercury", "Mg", "Microcar", "Mini", "Mitsubishi", "Morgan", "Moskvich", "Nissan", 
        "Oldsmobile", "Plymouth", "Polestar", "Pontiac", "Porsche", "Renault", "Rieju", 
        "Rolls-Royce", "Rover", "SECMA", "SIN CARS", "SWM", "Saab", "Scion", "Seat", 
        "Seres", "Shuanghuan", "Simca", "Skoda", "Smart", "SsangYong", "Subaru", "Suzuki", 
        "Talbot", "Tata", "Tesla", "Today Sunshine", "Trabant", "Triumph", "Uaz", "VROMOS", 
        "Volga", "Voyah", "Warszawa", "Wartburg", "Wey", "Zastava", "Zaz", "Други", "Чайка"
    )
    
    // Модели за коли
    val carModels = mapOf(
        "Acura" to arrayOf("ILX", "TLX", "RLX", "RDX", "MDX", "NSX", "Integra", "Legend", "RSX", "TSX"),
        "Audi" to arrayOf(
            // Класически модели
            "100", "80", "90",
            // A-серия
            "A1", "A2", "A3", "A4", "A4 Allroad", "A5", "A6", "A6 Allroad", "A7", "A8",
            // Allroad серия
            "Allroad",
            // Cabriolet/Coupe
            "Cabriolet", "Coupe",
            // E-Tron серия (електрически)
            "E-Tron", "E-Tron GT",
            // Q-серия (SUV)
            "Q2", "Q3", "Q3 Sportback", "Q4", "Q5", "Q6", "Q7", "Q8",
            // R-серия (спортни)
            "R8",
            // RS-серия (високопроизводителни)
            "RSQ3", "RSQ8", "Rs3", "Rs4", "Rs5", "Rs6", "Rs7",
            // S-серия (спортни)
            "S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8",
            // SQ-серия (спортни SUV)
            "SQ5", "SQ7", "SQ8",
            // TT серия
            "Tt"
        ),
        "BMW" to arrayOf(
            // 1-серия
            "114", "116", "118", "120", "123", "125", "128", "130", "135", "140", "1602", "1800", "1M",
            // 2-серия
            "2 Active Tourer", "2 Gran Coupe", "2 Gran Tourer", "2000", "2002", "216", "218", "220", "220 d", "225", "228", "230", "235", "240",
            // 3-серия
            "315", "316", "318", "320", "323", "324", "325", "328", "330", "335", "340", "3gt",
            // 4-серия
            "418", "420", "428", "430", "435", "440",
            // 5-серия
            "5 Gran Turismo", "518", "520", "523", "524", "525", "528", "530", "530E", "535", "540", "545", "550",
            // 6-серия
            "6 GT", "620", "628", "630", "635", "640", "645", "650",
            // 7-серия
            "700", "725", "728", "730", "732", "733", "735", "740", "745", "750", "760",
            // 8-серия
            "840", "850",
            // Izetta
            "Izetta",
            // M-серия
            "M Coupе", "M135", "M140", "M2", "M3", "M4", "M5", "M6", "M8",
            // X-серия
            "X1", "X2", "X3", "X4", "X5", "X5M", "X6", "X7", "XM",
            // Z-серия
            "Z3", "Z4",
            // i-серия (електрически)
            "i3", "i4", "i5", "i7", "i8", "iX", "iX1", "iX2", "iX3"
        ),
        "Mercedes-Benz" to arrayOf(
            // A-класа
            "A 140", "A 150", "A 160", "A 170", "A 180", "A 190", "A 200", "A 210", "A 220", "A 250", "A 35", "A 45", "A45 AMG",
            // B-класа  
            "B 150", "B 160", "B 170", "B 180", "B 200", "B 220", "B 250",
            // C-класа
            "C 160", "C 180", "C 200", "C 220", "C 230", "C 240", "C 250", "C 270", "C 280", "C 30 AMG", "C 300", "C 32 AMG", "C 320", "C 350", "C 36 AMG", "C 400", "C 43 AMG", "C 450 AMG", "C 55 AMG", "C 63 AMG",
            // CL-класа
            "CL 320", "CL 420", "CL 500", "CL 55 AMG", "CL 600", "CL 63 AMG", "CL 65 AMG",
            // CLA-класа
            "CLA 180", "CLA 200", "CLA 220", "CLA 250", "CLA 350 AMG", "CLA 45 AMG",
            // CLC-класа
            "CLC 160", "CLC 180", "CLC 200", "CLC 220", "CLC 230", "CLC 250", "CLC 350",
            // CLE
            "CLE",
            // CLK-класа
            "CLK 55 AMG", "CLK 63 AMG",
            // CLS-класа
            "CLS 220", "CLS 250", "CLS 300", "CLS 320", "CLS 350", "CLS 400", "CLS 450", "CLS 500", "CLS 53 AMG", "CLS 55", "CLS 55 AMG", "CLS 550", "CLS 63 AMG",
            // Citan
            "Citan",
            // E-класа
            "E 200", "E 220", "E 230", "E 240", "E 250", "E 260", "E 270", "E 280", "E 290", "E 300", "E 320", "E 350", "E 36 AMG", "E 400", "E 420", "E 43 AMG", "E 430", "E 450", "E 50 AMG", "E 500", "E 53 AMG", "E 55", "E 55 AMG", "E 60", "E 60 AMG", "E 63 AMG",
            // EQ серия
            "EQA", "EQB", "EQC", "EQE", "EQS", "EQV",
            // G-класа
            "G 230", "G 240", "G 250", "G 270", "G 280", "G 290", "G 300", "G 320", "G 350", "G 36 AMG", "G 400", "G 450", "G 500", "G 55 AMG", "G 580", "G 63 AMG", "G 65 AMG",
            // GL-класа
            "GL 320", "GL 350", "GL 420", "GL 450", "GL 500", "GL 55 AMG", "GL 550", "GL 63 AMG",
            // GLA-класа
            "GLA 180", "GLA 200", "GLA 220", "GLA 250", "GLA 45 AMG",
            // GLB
            "GLB",
            // GLC-класа
            "GLC 200", "GLC 220", "GLC 250", "GLC 300", "GLC 350", "GLC 400", "GLC 43 AMG", "GLC 63 AMG",
            // GLE-класа
            "GLE 250", "GLE 300", "GLE 350", "GLE 400", "GLE 43 AMG", "GLE 450", "GLE 450 AMG", "GLE 500", "GLE 53 4MATIC", "GLE 580", "GLE 63 AMG", "GLE 63 S AMG", "GLE Coupe",
            // GLK
            "GLK",
            // GLS-класа
            "GLS 350", "GLS 400", "GLS 450", "GLS 500", "GLS 600", "GLS 63 AMG", "GLS580",
            // GT серия
            "GT", "GTS", "AMG GT", "AMG GT C", "AMG GT R", "AMG GT S",
            // ML-класа
            "ML 230", "ML 250", "ML 270", "ML 280", "ML 300", "ML 320", "ML 350", "ML 400", "ML 420", "ML 430", "ML 450", "ML 500", "ML 55 AMG", "ML 550", "ML 63 AMG",
            // Maybach
            "Maybach",
            // R-класа
            "R 280", "R 300", "R 320", "R 350", "R 500",
            // S-класа
            "S 250", "S 280", "S 300", "S 320", "S 350", "S 400", "S 420", "S 430", "S 450", "S 500", "S 55 AMG", "S 550", "S 560", "S 580", "S 600", "S 63", "S 63 AMG", "S 65", "S 65 AMG", "S 680",
            // SL-класа
            "SL 400", "SL 43 AMG", "SL 500", "SL 55 AMG", "SL 600", "SL 63 AMG",
            // SLC
            "SLC",
            // SLK-класа
            "SLK 55 AMG",
            // SLR
            "SLR",
            // SLS AMG
            "SLS AMG",
            // T-класа
            "T-класа",
            // V серия
            "V 300", "Vaneo", "Viano",
            // X-Klasse
            "X-Klasse",
            // Други модели
            "110", "111", "113", "114", "115", "116", "123", "124", "126", "126-260", "150", "170", "180", "190", "200", "220", "230", "240", "250", "260", "280", "290", "300", "320", "350", "380", "420", "450", "500", "560", "600", "Adenauer"
        ),
        "Toyota" to arrayOf(
            // 4WD/SUV серия
            "4Runner", "FJ Cruiser", "Land Cruiser", "RAV4", "Highlander", "Sequoia", "C-HR", "Corolla Cross", "Yaris Cross", "Urban Cruiser",
            // Компактни модели
            "Aygo", "Yaris", "Yaris verso", "IQ", "Starlet", "Tercel",
            // Средни модели
            "Corolla", "Corolla verso", "Auris", "Avensis", "Avensis verso", "Camry", "Avalon", "Crown",
            // Луксозни модели
            "Alphard", "Harrier", "Venza", "Mirai",
            // Спортни модели
            "Supra", "GT86", "GR86", "Celica", "MR2",
            // Миниван серия
            "Previa", "Picnic", "Verso", "Verso S", "Sienna",
            // Pickup серия
            "Hilux", "Tacoma", "Tundra",
            // Хибридни модели
            "Prius",
            // Електрически модели
            "bZ4X",
            // Търговски модели
            "Proace City", "Proace City Verso",
            // Други модели
            "Carina", "Scion", "Suarer"
        ),
        "Honda" to arrayOf(
            // Компактни модели
            "Civic", "Fit", "Insight", "Element",
            // Средни модели
            "Accord", "Prelude",
            // SUV/Кросовери
            "CR-V", "Pilot", "HR-V", "Passport", "Ridgeline",
            // Миниван
            "Odyssey",
            // Спортни модели
            "NSX", "S2000", "Type R"
        ),
        "Ford" to arrayOf(
            // Pickup серия
            "F-150", "F-250", "F-350", "Ranger", "Maverick",
            // SUV/Кросовери
            "Explorer", "Expedition", "Escape", "Edge", "Bronco",
            // Компактни модели
            "Focus", "Fiesta",
            // Средни модели
            "Taurus", "Fusion",
            // Спортни модели
            "Mustang"
        ),
        "Chevrolet" to arrayOf(
            // Pickup серия
            "Silverado", "Colorado",
            // SUV/Кросовери
            "Equinox", "Traverse", "Tahoe", "Suburban", "Trax", "Blazer",
            // Компактни модели
            "Cruze", "Sonic", "Spark",
            // Средни модели
            "Malibu", "Impala",
            // Спортни модели
            "Camaro", "Corvette"
        ),
        "Nissan" to arrayOf(
            // Компактни модели
            "Sentra", "Versa", "Kicks",
            // Средни модели
            "Altima", "Maxima",
            // SUV/Кросовери
            "Rogue", "Murano", "Pathfinder", "Armada",
            // Pickup серия
            "Frontier", "Titan",
            // Спортни модели
            "370Z", "GT-R",
            // Електрически модели
            "Leaf", "Ariya"
        ),
        "Hyundai" to arrayOf(
            // Компактни модели
            "Accent", "Elantra", "Veloster",
            // Средни модели
            "Sonata",
            // SUV/Кросовери
            "Tucson", "Santa Fe", "Palisade", "Kona", "Venue",
            // Луксозни модели
            "Genesis",
            // Електрически модели
            "Ioniq", "Nexo",
            // Спортни модели
            "N Line"
        ),
        "Kia" to arrayOf(
            // Компактни модели
            "Forte", "Rio", "Soul",
            // Средни модели
            "Optima", "K5", "Cadenza",
            // SUV/Кросовери
            "Sportage", "Sorento", "Telluride", "Niro", "Seltos",
            // Миниван
            "Carnival",
            // Спортни модели
            "Stinger",
            // Електрически модели
            "EV6"
        ),
        "Mazda" to arrayOf(
            // Компактни модели
            "Mazda3", "Protege",
            // Средни модели
            "Mazda6", "Millenia",
            // SUV/Кросовери
            "CX-3", "CX-5", "CX-9", "CX-30", "Tribute",
            // Pickup серия
            "B-Series",
            // Спортни модели
            "MX-5 Miata", "RX-7", "RX-8"
        ),
        "Subaru" to arrayOf(
            // Компактни модели
            "Impreza", "Justy",
            // Средни модели
            "Legacy",
            // SUV/Кросовери
            "Outback", "Forester", "Crosstrek", "Ascent", "Tribeca", "Baja",
            // Спортни модели
            "WRX", "STI", "BRZ", "SVX"
        ),
        "Lexus" to arrayOf(
            // Компактни модели
            "IS", "CT", "HS",
            // Средни модели
            "ES", "GS",
            // Луксозни модели
            "LS", "SC", "LFA",
            // SUV/Кросовери
            "NX", "RX", "GX", "LX",
            // Спортни модели
            "LC", "RC"
        ),
        "Infiniti" to arrayOf(
            // Средни модели
            "Q50", "Q60", "Q70", "G37", "G35", "M35", "M45",
            // SUV/Кросовери
            "QX50", "QX60", "QX80", "FX35", "FX45", "EX35", "JX35"
        ),
        "Volvo" to arrayOf(
            // Компактни модели
            "C30", "S40", "V40",
            // Средни модели
            "S60", "V60", "S80", "V70",
            // Луксозни модели
            "S90", "V90",
            // SUV/Кросовери
            "XC40", "XC60", "XC90",
            // Спортни модели
            "C70",
            // Класически модели
            "850", "940", "960"
        ),
        "Porsche" to arrayOf(
            // Спортни модели
            "911", "718 Boxster", "718 Cayman", "944", "928", "968", "Boxster", "Cayman", "Carrera GT",
            // SUV/Кросовери
            "Macan", "Cayenne",
            // Луксозни модели
            "Panamera",
            // Електрически модели
            "Taycan"
        ),
        "Ferrari" to arrayOf(
            // Модерни спортни модели
            "488", "F8", "SF90", "Roma", "Portofino", "812", "LaFerrari",
            // Класически спортни модели
            "F12", "California", "458", "430", "360", "F355", "Testarossa"
        ),
        "Lamborghini" to arrayOf(
            // Модерни спортни модели
            "Huracan", "Aventador", "Urus",
            // Класически спортни модели
            "Gallardo", "Murcielago", "Diablo", "Countach", "Espada", "Jarama", "Miura", "Islero", "400GT"
        ),
        "Bentley" to arrayOf(
            // Модерни луксозни модели
            "Continental", "Flying Spur", "Bentayga", "Mulsanne",
            // Класически луксозни модели
            "Azure", "Arnage", "Brooklands", "Turbo R", "Eight", "T2", "T1", "Mark VI"
        ),
        "Rolls-Royce" to arrayOf(
            // Модерни луксозни модели
            "Phantom", "Ghost", "Wraith", "Dawn", "Cullinan",
            // Класически луксозни модели
            "Silver Shadow", "Silver Spirit", "Silver Seraph", "Corniche", "Camargue", "Silver Cloud"
        ),
        "Aston Martin" to arrayOf(
            // Модерни спортни модели
            "DB11", "Vantage", "DBS", "Rapide", "Vanquish",
            // Класически спортни модели
            "DB9", "V8 Vantage", "DB7", "Virage", "Lagonda", "Cygnet", "Bulldog"
        ),
        "McLaren" to arrayOf(
            // Модерни спортни модели
            "720S", "570S", "600LT", "GT", "Artura", "P1", "650S", "675LT", "540C", "Senna", "Elva", "Speedtail",
            // Класически спортни модели
            "F1", "MP4-12C"
        ),
        "Tesla" to arrayOf(
            // Електрически модели
            "Model S", "Model 3", "Model X", "Model Y", "Roadster", "Cybertruck", "Semi",
            // Варианти
            "Plaid", "Long Range", "Performance", "Standard Range"
        ),
        "VW" to arrayOf(
            // Класически модели
            "1200", "1300", "1302", "1303", "1600",
            // Allroad/Alltrack
            "Alltrack",
            // Pickup
            "Amarok",
            // Луксозни модели
            "Arteon", "Atlas", "Phaeton",
            // Beetle серия
            "Beetle", "New beetle",
            // Bora/Vento
            "Bora", "Vento",
            // CC серия
            "CC",
            // Caddy серия
            "Caddy",
            // Corrado
            "Corrado",
            // Eos
            "Eos",
            // Fox
            "Fox",
            // Golf серия
            "Golf", "Golf Plus", "Golf Variant",
            // ID серия (електрически)
            "ID.3", "ID.4", "ID.5", "ID.6", "ID.7", "ID.Buzz",
            // Jetta
            "Jetta",
            // Karmann-ghia
            "Karmann-ghia",
            // Lupo
            "Lupo",
            // Multivan
            "Multivan",
            // Passat серия
            "Passat",
            // Polo серия
            "Polo",
            // Santana
            "Santana",
            // Scirocco
            "Scirocco",
            // Sharan
            "Sharan",
            // Sportsvan
            "Sportsvan",
            // T-серия (кросовери)
            "T-Cross", "T-Roc", "Taigo", "Tayron", "Tiguan", "Tiguan Allspace", "Touareg",
            // Touran
            "Touran",
            // Up
            "Up"
        ),
        "Opel" to arrayOf(
            // Компактни модели
            "Adam", "Agila", "Karl", "Corsa", "Kadett", "Ascona",
            // Средни модели
            "Astra", "Vectra", "Insignia", "Signum", "Rekord", "Senator",
            // Луксозни модели
            "Admiral", "Kapitaen", "Omega",
            // SUV/Кросовери
            "Antara", "Mokka", "Mokka X", "Crossland X", "Grandland", "Grandland X", "Frontera", "Monterey",
            // Миниван серия
            "Zafira", "Meriva", "Sintra",
            // Търговски модели
            "Combo", "Campo",
            // Спортни модели
            "Calibra", "Tigra", "Speedster", "GT", "Manta",
            // Кабрио/Купе
            "Cascada",
            // Хибридни/Електрически
            "Ampera"
        ),
        "Peugeot" to arrayOf(
            // 100-серия (компактни)
            "1007", "106", "107", "108",
            // 200-серия (компактни SUV)
            "2008", "205", "206", "207", "208",
            // 300-серия (средни)
            "3008", "301", "304", "306", "307", "308", "309",
            // 400-серия (средни SUV)
            "4007", "4008", "403", "404", "405", "406", "407", "408",
            // 500-серия (големи)
            "5008", "505", "508", "508 RXH", "605", "607",
            // 800-серия (минивани)
            "806", "807",
            // Търговски модели
            "Bipper", "Expert", "Partner", "Rifter", "Traveler",
            // Спортни модели
            "RCZ",
            // Електрически модели
            "iOn",
            // Други модели
            "Range"
        ),
        "Fiat" to arrayOf(
            // Компактни модели
            "500", "Panda", "Punto", "Tipo", "Linea",
            // SUV/Кросовери
            "500X", "500L", "Doblo", "Fiorino",
            // Спортни модели
            "Abarth 500", "Abarth 124 Spider",
            // Търговски модели
            "Ducato", "Talento", "Fiorino"
        ),
        "Renault" to arrayOf(
            // Компактни модели
            "Clio", "Twingo", "Zoe", "Fluence",
            // Средни модели
            "Megane", "Laguna", "Talisman",
            // SUV/Кросовери
            "Captur", "Kadjar", "Koleos", "Duster", "Scenic",
            // Миниван
            "Espace", "Grand Scenic",
            // Търговски модели
            "Kangoo", "Master", "Trafic"
        ),
        "Skoda" to arrayOf(
            // Компактни модели
            "Fabia", "Scala",
            // Средни модели
            "Octavia", "Rapid",
            // SUV/Кросовери
            "Kamiq", "Karoq", "Kodiaq", "Yeti",
            // Луксозни модели
            "Superb",
            // Електрически модели
            "Enyaq"
        ),
        "Seat" to arrayOf(
            // Компактни модели
            "Ibiza", "Arona",
            // Средни модели
            "Leon", "Toledo",
            // SUV/Кросовери
            "Ateca", "Tarraco",
            // Спортни модели
            "Cupra Leon", "Cupra Formentor"
        ),
        "Dacia" to arrayOf(
            // Компактни модели
            "Sandero", "Logan",
            // SUV/Кросовери
            "Duster", "Lodgy", "Dokker",
            // Електрически модели
            "Spring"
        ),
        "Citroen" to arrayOf(
            // Компактни модели
            "C1", "C3", "C4", "C5",
            // SUV/Кросовери
            "C3 Aircross", "C4 Cactus", "C5 Aircross", "Berlingo",
            // Миниван
            "Grand C4 Picasso", "SpaceTourer",
            // Електрически модели
            "e-C4", "e-Berlingo"
        ),
        "Jaguar" to arrayOf(
            // Спортни модели
            "F-Type", "XE", "XF", "XJ",
            // SUV/Кросовери
            "E-Pace", "F-Pace", "I-Pace"
        ),
        "Land Rover" to arrayOf(
            // SUV/Кросовери
            "Defender", "Discovery", "Discovery Sport", "Range Rover", "Range Rover Evoque", "Range Rover Sport", "Range Rover Velar"
        ),
        "Jeep" to arrayOf(
            // SUV/Кросовери
            "Wrangler", "Cherokee", "Grand Cherokee", "Compass", "Renegade", "Gladiator"
        ),
        "Mitsubishi" to arrayOf(
            // Компактни модели
            "Mirage", "Lancer",
            // SUV/Кросовери
            "Outlander", "ASX", "Eclipse Cross", "Pajero",
            // Pickup
            "L200", "Triton"
        ),
        "Suzuki" to arrayOf(
            // Компактни модели
            "Swift", "Baleno", "Celerio",
            // SUV/Кросовери
            "Vitara", "S-Cross", "Jimny",
            // Pickup
            "Carry"
        ),
        "Mini" to arrayOf(
            // Компактни модели
            "Cooper", "Cooper S", "Cooper SE", "One", "Countryman", "Clubman", "Convertible"
        ),
        "Smart" to arrayOf(
            // Компактни модели
            "Fortwo", "Forfour", "EQ Fortwo", "EQ Forfour"
        ),
        "Alfa Romeo" to arrayOf(
            // Спортни модели
            "Giulia", "Stelvio", "4C", "Giulietta", "MiTo", "Spider"
        ),
        "Lancia" to arrayOf(
            // Компактни модели
            "Ypsilon", "Delta", "Musa", "Phedra"
        ),
        "Maserati" to arrayOf(
            // Луксозни модели
            "Ghibli", "Quattroporte", "Levante", "MC20", "GranTurismo", "GranCabrio"
        ),
        "Lotus" to arrayOf(
            // Спортни модели
            "Elise", "Exige", "Evora", "Emira", "Eletre"
        ),
        "Saab" to arrayOf(
            // Средни модели
            "9-3", "9-5", "9-7X", "900", "9000"
        ),
        "Volkswagen" to arrayOf(
            // Компактни модели
            "Polo", "Golf", "Jetta",
            // Средни модели
            "Passat", "Arteon",
            // SUV/Кросовери
            "Tiguan", "Touareg", "T-Cross", "T-Roc",
            // Електрически модели
            "ID.3", "ID.4", "ID.5", "ID.6", "ID.7", "ID.Buzz"
        ),
        "DS" to arrayOf("DS3", "DS4", "DS5", "DS7", "DS9"),
        "Cupra" to arrayOf("Cupra Leon", "Cupra Formentor", "Cupra Ateca", "Cupra Born"),
        "Dacia" to arrayOf("Sandero", "Logan", "Duster", "Lodgy", "Dokker", "Spring"),
        "Citroen" to arrayOf("C1", "C3", "C4", "C5", "C3 Aircross", "C4 Cactus", "C5 Aircross", "Berlingo", "Grand C4 Picasso", "SpaceTourer", "e-C4", "e-Berlingo"),
        "Jaguar" to arrayOf("F-Type", "XE", "XF", "XJ", "E-Pace", "F-Pace", "I-Pace"),
        "Land Rover" to arrayOf("Defender", "Discovery", "Discovery Sport", "Range Rover", "Range Rover Evoque", "Range Rover Sport", "Range Rover Velar"),
        "Jeep" to arrayOf("Wrangler", "Cherokee", "Grand Cherokee", "Compass", "Renegade", "Gladiator"),
        "Mitsubishi" to arrayOf("Mirage", "Lancer", "Outlander", "ASX", "Eclipse Cross", "Pajero", "L200", "Triton"),
        "Suzuki" to arrayOf("Swift", "Baleno", "Celerio", "Vitara", "S-Cross", "Jimny", "Carry"),
        "Mini" to arrayOf("Cooper", "Cooper S", "Cooper SE", "One", "Countryman", "Clubman", "Convertible"),
        "Smart" to arrayOf("Fortwo", "Forfour", "EQ Fortwo", "EQ Forfour"),
        "Alfa Romeo" to arrayOf(
            // Класически модели
            "33", "145", "146", "147", "155", "156", "159", "164", "166",
            // Sportwagon модели
            "156 sportwagon", "159 sportwagon",
            // GT серия
            "GT", "GTV",
            // Spider серия
            "Spider",
            // Junior серия
            "Junior",
            // Crosswagon серия
            "Crosswagon q4",
            // Brera серия
            "Brera",
            // 4C серия
            "4C",
            // 8C серия
            "8C Competizione",
            // Giulia серия
            "Giulia",
            // Giulietta серия
            "Giulietta",
            // MiTo серия
            "MiTo",
            // Stelvio серия
            "Stelvio",
            // Tonale серия
            "Tonale"
        ),
        "Lancia" to arrayOf("Ypsilon", "Delta", "Musa", "Phedra"),
        "Maserati" to arrayOf("Ghibli", "Quattroporte", "Levante", "MC20", "GranTurismo", "GranCabrio"),
        "Lotus" to arrayOf("Elise", "Exige", "Evora", "Emira", "Eletre"),
        "Saab" to arrayOf("9-3", "9-5", "9-7X", "900", "9000"),
        "Abarth" to arrayOf("500", "124 Spider", "Punto", "Grande Punto"),
        "Chrysler" to arrayOf("300", "Pacifica", "Voyager", "Grand Caravan"),
        "Dodge" to arrayOf("Challenger", "Charger", "Durango", "Journey", "Grand Caravan", "Ram"),
        "Buick" to arrayOf("Enclave", "Envision", "LaCrosse", "Regal", "Verano"),
        "Cadillac" to arrayOf("CTS", "XTS", "ATS", "Escalade", "SRX", "XT5", "CT6"),
        "Lincoln" to arrayOf("Continental", "MKZ", "MKX", "Navigator", "MKC", "MKT"),
        "Genesis" to arrayOf("G70", "G80", "G90", "GV70", "GV80"),
        "Infiniti" to arrayOf("Q50", "Q60", "Q70", "QX50", "QX60", "QX80", "G37", "G35", "M35", "M45", "FX35", "FX45", "EX35", "JX35"),
        "Hummer" to arrayOf("H1", "H2", "H3"),
        "Kia" to arrayOf("Forte", "Optima", "K5", "Stinger", "Soul", "Sportage", "Sorento", "Telluride", "Niro", "Seltos", "EV6", "Carnival", "Cadenza", "Rio"),
        "Hyundai" to arrayOf("Elantra", "Sonata", "Accent", "Veloster", "Tucson", "Santa Fe", "Palisade", "Kona", "Venue", "Nexo", "Genesis", "Ioniq", "N Line"),
        "Nissan" to arrayOf("Altima", "Sentra", "Maxima", "Rogue", "Murano", "Pathfinder", "Armada", "Frontier", "Titan", "370Z", "GT-R", "Leaf", "Ariya", "Versa", "Kicks"),
        "Chevrolet" to arrayOf("Silverado", "Equinox", "Traverse", "Tahoe", "Suburban", "Camaro", "Corvette", "Malibu", "Impala", "Cruze", "Sonic", "Spark", "Trax", "Blazer", "Colorado"),
        "Ford" to arrayOf("F-150", "F-250", "F-350", "Mustang", "Explorer", "Expedition", "Escape", "Edge", "Bronco", "Ranger", "Maverick", "Focus", "Fiesta", "Taurus", "Fusion"),
        "Honda" to arrayOf("Accord", "Civic", "CR-V", "Pilot", "HR-V", "Passport", "Ridgeline", "Insight", "Fit", "NSX", "Type R", "Prelude", "S2000", "Element", "Odyssey"),
        "Toyota" to arrayOf("Avalon", "Camry", "Corolla", "Prius", "RAV4", "Highlander", "4Runner", "Tacoma", "Tundra", "Sienna", "Supra", "GR86", "GR Supra", "C-HR", "Venza", "Sequoia", "Land Cruiser"),
        "Mazda" to arrayOf("Mazda3", "Mazda6", "CX-3", "CX-5", "CX-9", "CX-30", "MX-5 Miata", "RX-7", "RX-8", "Protege", "Millenia", "Tribute", "B-Series"),
        "Subaru" to arrayOf("Impreza", "Legacy", "Outback", "Forester", "Crosstrek", "Ascent", "WRX", "STI", "BRZ", "Tribeca", "Baja", "SVX", "Justy"),
        "Lexus" to arrayOf("ES", "IS", "GS", "LS", "NX", "RX", "GX", "LX", "LC", "RC", "CT", "HS", "SC", "LFA"),
        "Infiniti" to arrayOf("Q50", "Q60", "Q70", "QX50", "QX60", "QX80", "G37", "G35", "M35", "M45", "FX35", "FX45", "EX35", "JX35"),
        "Volvo" to arrayOf("S60", "S90", "V60", "V90", "XC40", "XC60", "XC90", "C30", "C70", "S40", "S80", "V40", "V70", "850", "940", "960"),
        "Porsche" to arrayOf("911", "718 Boxster", "718 Cayman", "Panamera", "Macan", "Cayenne", "Taycan", "944", "928", "968", "Boxster", "Cayman", "Carrera GT"),
        "Ferrari" to arrayOf("488", "F8", "SF90", "Roma", "Portofino", "812", "LaFerrari", "F12", "California", "458", "430", "360", "F355", "Testarossa"),
        "Lamborghini" to arrayOf("Huracan", "Aventador", "Urus", "Gallardo", "Murcielago", "Diablo", "Countach", "Espada", "Jarama", "Miura", "Islero", "400GT"),
        "Bentley" to arrayOf("Continental", "Flying Spur", "Bentayga", "Mulsanne", "Azure", "Arnage", "Brooklands", "Turbo R", "Eight", "T2", "T1", "Mark VI"),
        "Rolls-Royce" to arrayOf("Phantom", "Ghost", "Wraith", "Dawn", "Cullinan", "Silver Shadow", "Silver Spirit", "Silver Seraph", "Corniche", "Camargue", "Silver Cloud"),
        "Aston Martin" to arrayOf("DB11", "Vantage", "DBS", "Rapide", "Vanquish", "DB9", "V8 Vantage", "DB7", "Virage", "Lagonda", "Cygnet", "Bulldog"),
        "McLaren" to arrayOf("720S", "570S", "600LT", "GT", "Artura", "P1", "650S", "675LT", "540C", "Senna", "Elva", "Speedtail", "F1", "MP4-12C"),
        "Tesla" to arrayOf("Model S", "Model 3", "Model X", "Model Y", "Roadster", "Cybertruck", "Semi", "Plaid", "Long Range", "Performance", "Standard Range"),
        "Abarth" to arrayOf("500", "124 Spider", "Punto", "Grande Punto"),
        "Aixam" to arrayOf("City", "Crossline", "Roadline", "Scouty"),
        "Alpina" to arrayOf("B3", "B4", "B5", "B6", "B7", "B8", "D3", "D4", "D5", "XD3", "XD4"),
        "Aro" to arrayOf("10", "24", "244", "246", "324", "328"),
        "Asia" to arrayOf("Rocsta", "Retona", "Towner"),
        "Austin" to arrayOf("Mini", "Allegro", "Maxi", "Princess", "Ambassador"),
        "BAIC" to arrayOf("BJ20", "BJ40", "BJ80", "EU5", "EU7", "EX3", "EX5", "EX7"),
        "BAW" to arrayOf("BJ212", "BJ2020", "BJ2023", "BJ2024"),
        "BENTU" to arrayOf("Bentley", "Continental", "Flying Spur"),
        "Brilliance" to arrayOf("H530", "V5", "V6", "V7", "H230", "H320", "H330"),
        "Carbodies" to arrayOf("FX4", "TX1", "TX2", "TX4"),
        "Changan" to arrayOf("CS35", "CS55", "CS75", "CS85", "CS95", "Eado", "Alsvin", "Raeton"),
        "Chery" to arrayOf("Tiggo", "Arrizo", "QQ", "A1", "A3", "A5", "E5", "G5", "G6"),
        "Corvette" to arrayOf("C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "Z06", "ZR1", "Stingray"),
        "DFSK" to arrayOf("C31", "C32", "C35", "C37", "K01", "K02", "K05", "K07"),
        "DONGFENG" to arrayOf("AX3", "AX4", "AX5", "AX7", "S30", "S50", "S560", "S580", "T5", "T5L"),
        "DR Automobiles" to arrayOf("DR1", "DR2", "DR3", "DR4", "DR5", "DR6"),
        "Daewoo" to arrayOf("Matiz", "Lanos", "Nubira", "Leganza", "Tacuma", "Kalos", "Lacetti", "Tosca"),
        "Daihatsu" to arrayOf("Mira", "Terios", "Sirion", "Materia", "Copen", "Charade", "Feroza", "Rocky"),
        "Daimler" to arrayOf("Maybach", "Smart", "Freightliner", "Western Star", "Thomas Built"),
        "Datsun" to arrayOf("Go", "Go+", "Redi-Go", "mi-DO", "on-DO"),
        "Dkw" to arrayOf("F89", "F91", "F93", "F94", "F95", "F96", "F102", "Munga"),
        "Eagle" to arrayOf("Talon", "Vision", "Summit", "Premier"),
        "Fisker" to arrayOf("Karma", "Ocean", "PEAR", "Rocket"),
        "Foton" to arrayOf("Sauvana", "Tunland", "Saga", "Tunland G7", "Tunland G9"),
        "GOUPIL" to arrayOf("G3", "G4", "G5", "G6"),
        "GWM" to arrayOf("Haval H1", "Haval H2", "Haval H4", "Haval H6", "Haval H8", "Haval H9", "Wingle", "Deer", "Poer"),
        "Gaz" to arrayOf("Volga", "Sobol", "Valdai", "Next", "Sobol Business", "Gazelle Next"),
        "Geely" to arrayOf("Emgrand", "Vision", "GC9", "Atlas", "Coolray", "Azkarra", "Tugella", "Xingyue"),
        "Gmc" to arrayOf("Sierra", "Canyon", "Acadia", "Terrain", "Yukon", "Savana", "Express"),
        "Gonow" to arrayOf("Troy", "Aurora", "Porter", "Tiger"),
        "Great Wall" to arrayOf("Haval H1", "Haval H2", "Haval H4", "Haval H6", "Haval H8", "Haval H9", "Wingle", "Deer", "Poer"),
        "Haval" to arrayOf("H1", "H2", "H4", "H6", "H8", "H9", "F5", "F7", "F7x", "Jolion"),
        "Hillman" to arrayOf("Imp", "Hunter", "Minx", "Husky"),
        "HongQi" to arrayOf("H5", "H7", "H9", "E-HS3", "E-HS9", "HS5", "HS7"),
        "Ifa" to arrayOf("W50", "W60", "L60", "H6", "H6A"),
        "Ineos Grenadier" to arrayOf("Grenadier", "Quartermaster"),
        "Isuzu" to arrayOf("D-Max", "MU-X", "Trooper", "Rodeo", "Ascender", "i-Series", "NPR", "NQR"),
        "Iveco" to arrayOf("Daily", "Eurocargo", "Stralis", "Hi-Way", "Tector", "Massif"),
        "JAC" to arrayOf("S2", "S3", "S5", "T6", "T8", "iEV6E", "iEV7S", "iEVS4"),
        "Lada" to arrayOf("Granta", "Kalina", "Vesta", "XRAY", "Largus", "Niva", "Priora", "Samara"),
        "Landwind" to arrayOf("X5", "X6", "X7", "X8", "X9", "Fashion", "Rongyao"),
        "LynkCo" to arrayOf("01", "02", "03", "05", "06", "09"),
        "Mahindra" to arrayOf("Scorpio", "XUV500", "XUV300", "KUV100", "TUV300", "Bolero", "Thar", "Xylo"),
        "Maybach" to arrayOf("57", "62", "S-Class", "GLS", "EQS"),
        "Mercury" to arrayOf("Grand Marquis", "Sable", "Milan", "Mountaineer", "Mariner", "Montego"),
        "Mg" to arrayOf("3", "5", "6", "7", "ZS", "HS", "RX5", "RX8", "TF", "F", "MGF"),
        "Microcar" to arrayOf("M.Go", "M.Go Family", "M.Go Pick-Up", "M.Go Van"),
        "Morgan" to arrayOf("3 Wheeler", "Plus 4", "Plus 6", "Plus 8", "Aero 8", "Roadster"),
        "Moskvich" to arrayOf("400", "401", "402", "403", "407", "408", "410", "411", "2140", "2141"),
        "Oldsmobile" to arrayOf("Alero", "Aurora", "Bravada", "Cutlass", "Intrigue", "Silhouette"),
        "Plymouth" to arrayOf("Neon", "Breeze", "Grand Voyager", "Prowler", "Voyager"),
        "Polestar" to arrayOf("1", "2", "3", "4", "5"),
        "Pontiac" to arrayOf("Firebird", "Trans Am", "GTO", "Grand Prix", "Bonneville", "Sunfire"),
        "Rieju" to arrayOf("Mint", "Tango", "Century", "MR Pro", "RS3", "RT3"),
        "Rover" to arrayOf("25", "45", "75", "200", "400", "600", "800", "Streetwise"),
        "SECMA" to arrayOf("F16", "F20", "F24", "F28"),
        "SIN CARS" to arrayOf("R1", "R2", "R3", "R4", "R5"),
        "SWM" to arrayOf("G01", "G05", "G07", "X3", "X7"),
        "Scion" to arrayOf("tC", "xB", "xD", "iQ", "FR-S"),
        "Seres" to arrayOf("SF5", "SF7", "SF9", "iX3", "iX5"),
        "Shuanghuan" to arrayOf("CEO", "Sceo", "Noble", "Landmark"),
        "Simca" to arrayOf("1000", "1100", "1300", "1500", "Ariane", "Chambord", "Vedette"),
        "SsangYong" to arrayOf("Korando", "Rexton", "Tivoli", "XLV", "Musso", "Rodius", "Actyon"),
        "Talbot" to arrayOf("Horizon", "Sunbeam", "Samba", "Solara", "Tagora"),
        "Tata" to arrayOf("Nano", "Indica", "Indigo", "Safari", "Sumo", "Xenon", "Aria", "Vista"),
        "Today Sunshine" to arrayOf("Sunshine", "Sunshine Plus", "Sunshine Max"),
        "Trabant" to arrayOf("P50", "P60", "601", "1.1", "1.1S"),
        "Uaz" to arrayOf("Patriot", "Hunter", "Pickup", "Cargo", "3151", "3153", "3160", "3162"),
        "VROMOS" to arrayOf("V1", "V2", "V3", "V4", "V5"),
        "Volga" to arrayOf("21", "22", "24", "3102", "3110", "31105", "3111"),
        "Voyah" to arrayOf("Free", "Dream", "i-Land", "H97"),
        "Warszawa" to arrayOf("M20", "201", "202", "203", "204", "223", "224"),
        "Wartburg" to arrayOf("311", "312", "353", "1.3", "Tourist"),
        "Wey" to arrayOf("VV5", "VV6", "VV7", "P8", "Tank 300", "Tank 500", "Tank 600"),
        "Zastava" to arrayOf("10", "101", "128", "130", "1500", "Florida", "Yugo"),
        "Zaz" to arrayOf("1102", "1103", "1105", "Sens", "Vida", "Forza", "Lanos"),
        "Други" to arrayOf("Други модели"),
        "Чайка" to arrayOf("Чайка", "Чайка-13", "Чайка-14")
    )
    
    // Марки за мотоциклети (популярни първо, после азбучен ред)
    val motorcycleBrands = arrayOf(
        // Най-популярни
        "Най-популярни", "Honda", "Yamaha", "Suzuki", "Kawasaki", "Ktm", "BMW", "Aprilia", "Vespa",
        // A
        "ADLI", "Aeon", "American Ironhorse", "Aprilia", "Arctic Cat", "Argo", "Askoll", "Awo",
        // B
        "BMW", "BRP", "Balkan", "Baotian", "Barton", "Bashan", "Benelli", "Benzhou", "Beta", "Big Dog", "Bombardier", "Brixton", "Buell", "Buyang",
        // C
        "Cagiva", "Can-Am", "Cfmoto", "Cpi", "Cz",
        // D
        "Daelim", "Daytona", "Derbi", "Dinli", "Ducati",
        // E
        "Energica", "Etz",
        // F
        "FB Mondial", "Falcon", "Fantic",
        // G
        "GASGAS", "Garelli", "Generic", "Gilera", "Go-ped",
        // H
        "HISUN", "Hanway", "Harley-Davidson", "Herkules", "Honda", "Horwin", "Husaberg", "Husqvarna", "Hyosung",
        // I
        "Indian", "Italjet",
        // J
        "Jawa", "Jinlun", "Jonway",
        // K
        "KL MOTORCYCLE", "KSR", "Kawasaki", "Kayo Moto", "Keeway", "Kinetic", "Kove", "Ktm", "Kymco",
        // L
        "La Souris", "Lambreta", "Lexmoto", "Lifan", "Linhai", "Lynx",
        // M
        "MV Agusta", "Malaguti", "Mbk", "Moto Guzzi", "Moto Morini", "MotorHispania", "Motoretta", "Mz",
        // N
        "NIU",
        // O
        "Orcal",
        // P
        "Peugeot", "Piaggio", "Polaris",
        // Q
        "QJMotor", "Qingqi", "Quadro",
        // R
        "Rieju", "Royal Enfield", "Rudge",
        // S
        "Sachs", "Sampo", "Sanyang", "Scoot", "Segway Powersports", "Sherco", "Shineray", "Silence", "Simson", "Ski-Doo", "Stark", "Sunra", "Sunsto", "Super Soco", "Surron", "Suzuki", "Swm", "Sym",
        // T
        "Tatran", "Telstar", "Tgb", "Tm", "Tomos", "Triumph",
        // V
        "VROMOS", "Vespa", "Victory", "Voge",
        // W
        "Wangye",
        // X
        "XGJao", "Xingyue",
        // Y
        "Yamaha",
        // Z
        "Zero", "Znen", "Zongshen", "Zontes", "Zundapp",
        // Кирилица
        "Вятка", "Днепр", "Други", "Иж", "Ковровец", "Мини мотоциклети", "Минск", "Поръчкови", "Рига"
    )
    
    // Модели за мотоциклети
    val motorcycleModels = mapOf(
        "Aprilia" to arrayOf(
            // RSV серия (спортни)
            "RSV4", "RSV Mille",
            // Tuono серия (naked)
            "Tuono V4", "Tuono 660", "Tuono",
            // RS серия (спортни)
            "RS 660", "RS 125", "RS 250", "Rs", "Rx",
            // SXV серия (supermoto)
            "SXV 550", "RXV 550",
            // Dorsoduro серия (supermoto)
            "Dorsoduro",
            // Shiver серия (naked)
            "Shiver",
            // Caponord серия (adventure)
            "Caponord",
            // Mana серия (naked)
            "Mana",
            // Scarabeo серия (скутери)
            "Scarabeo", "SR Max",
            // Pegaso серия (adventure)
            "Pegaso",
            // Atlantic серия (скутери)
            "Atlantic",
            // Sport City серия (скутери)
            "Sport City",
            // Habana серия (скутери)
            "Habana",
            // SR серия (скутери)
            "Sr",
            // SX серия (скутери)
            "Sx",
            // SRV серия (скутери)
            "SRV",
            // Tuareg серия (adventure)
            "Tuareg",
            // Falco серия (спортни)
            "Falco",
            // Leonardo серия (скутери)
            "Leonardo",
            // Custom Mojito серия (скутери)
            "Custom Mojito",
            // Classic серия
            "Classic",
            // Допълнителни модели по кубатура
            "50", "125", "200", "250"
        ),
        "Beta" to arrayOf("RR 125", "RR 200", "RR 250", "RR 300", "RR 350", "RR 430", "RR 480", "RR 500", "Alp 200", "Alp 350", "Alp 400", "Evo 2T", "Evo 4T", "Cross Trainer", "Urban", "City"),
        "Bimota" to arrayOf("Tesi H2", "KB4", "DB11", "DB12", "SB8K", "SB6R", "SB8R", "Tesi 1D", "Tesi 2D", "Tesi 3D", "YBI", "V Due", "Supermono", "Ducati 916", "Ducati 996"),
        "BMW" to arrayOf(
            // S серия (спортни)
            "S1000RR", "S1000R", "S1000XR", "S1000F", "S1000GT", "S1000ST", "S850", "S750", "S650", "S600", "S500", "S400", "S350", "S300", "S250", "S200", "S150", "S125", "S100", "S80", "S75", "S65", "S50", "S45", "S40", "S35", "S30", "S25", "S20", "S15", "S10",
            // R серия (adventure)
            "R1250GS", "R1250RT", "R1250RS", "R1200GS", "R1200RT", "R1200RS", "R1150GS", "R1100S", "R850R", "R800GS", "R800RT", "R800RS", "R750GS", "R750RT", "R750RS", "R650GS", "R650RT", "R650RS", "R600GS", "R600RT", "R600RS", "R550GS", "R550RT", "R550RS", "R500GS", "R500RT", "R500RS", "R450GS", "R450RT", "R450RS", "R400GS", "R400RT", "R400RS", "R350GS", "R350RT", "R350RS", "R300GS", "R300RT", "R300RS", "R250GS", "R250RT", "R250RS", "R200GS", "R200RT", "R200RS", "R150GS", "R150RT", "R150RS", "R125GS", "R125RT", "R125RS", "R100GS", "R100RT", "R100RS", "R80GS", "R80RT", "R80RS", "R75GS", "R75RT", "R75RS", "R65GS", "R65RT", "R65RS", "R60GS", "R60RT", "R60RS", "R50GS", "R50RT", "R50RS", "R45GS", "R45RT", "R45RS", "R40GS", "R40RT", "R40RS", "R35GS", "R35RT", "R35RS", "R30GS", "R30RT", "R30RS", "R25GS", "R25RT", "R25RS", "R20GS", "R20RT", "R20RS", "R15GS", "R15RT", "R15RS", "R10GS", "R10RT", "R10RS", "R5GS", "R5RT", "R5RS",
            // F серия (naked)
            "F900R", "F900XR", "F800GS", "F800GT", "F800R", "F800S", "F650GS", "F650CS", "F600GS", "F600GT", "F600R", "F600S", "F550GS", "F550GT", "F550R", "F550S", "F500GS", "F500GT", "F500R", "F500S", "F450GS", "F450GT", "F450R", "F450S", "F400GS", "F400GT", "F400R", "F400S", "F350GS", "F350GT", "F350R", "F350S", "F300GS", "F300GT", "F300R", "F300S", "F250GS", "F250GT", "F250R", "F250S", "F200GS", "F200GT", "F200R", "F200S", "F150GS", "F150GT", "F150R", "F150S", "F125GS", "F125GT", "F125R", "F125S", "F100GS", "F100GT", "F100R", "F100S", "F80GS", "F80GT", "F80R", "F80S", "F65GS", "F65GT", "F65R", "F65S", "F60GS", "F60GT", "F60R", "F60S", "F50GS", "F50GT", "F50R", "F50S", "F45GS", "F45GT", "F45R", "F45S", "F40GS", "F40GT", "F40R", "F40S", "F35GS", "F35GT", "F35R", "F35S", "F30GS", "F30GT", "F30R", "F30S", "F25GS", "F25GT", "F25R", "F25S", "F20GS", "F20GT", "F20R", "F20S", "F15GS", "F15GT", "F15R", "F15S", "F10GS", "F10GT", "F10R", "F10S", "F5GS", "F5GT", "F5R", "F5S",
            // G серия (naked)
            "G310R", "G310GS", "G300R", "G300GS", "G250R", "G250GS", "G200R", "G200GS", "G150R", "G150GS", "G125R", "G125GS", "G100R", "G100GS", "G80R", "G80GS", "G65R", "G65GS", "G60R", "G60GS", "G50R", "G50GS", "G45R", "G45GS", "G40R", "G40GS", "G35R", "G35GS", "G30R", "G30GS", "G25R", "G25GS", "G20R", "G20GS", "G15R", "G15GS", "G10R", "G10GS", "G5R", "G5GS",
            // K серия (туристически)
            "K1600GT", "K1600B", "K1200LT", "K1200S", "K1300S", "K1200GT", "K1200R", "K1200RS", "K1200RT", "K1200LT", "K1100LT", "K1100RS", "K1100RT", "K1100LT", "K1000GT", "K1000R", "K1000RS", "K1000RT", "K1000LT", "K900GT", "K900R", "K900RS", "K900RT", "K900LT", "K800GT", "K800R", "K800RS", "K800RT", "K800LT", "K750GT", "K750R", "K750RS", "K750RT", "K750LT", "K700GT", "K700R", "K700RS", "K700RT", "K700LT", "K650GT", "K650R", "K650RS", "K650RT", "K650LT", "K600GT", "K600R", "K600RS", "K600RT", "K600LT", "K550GT", "K550R", "K550RS", "K550RT", "K550LT", "K500GT", "K500R", "K500RS", "K500RT", "K500LT", "K450GT", "K450R", "K450RS", "K450RT", "K450LT", "K400GT", "K400R", "K400RS", "K400RT", "K400LT", "K350GT", "K350R", "K350RS", "K350RT", "K350LT", "K300GT", "K300R", "K300RS", "K300RT", "K300LT", "K250GT", "K250R", "K250RS", "K250RT", "K250LT", "K200GT", "K200R", "K200RS", "K200RT", "K200LT", "K150GT", "K150R", "K150RS", "K150RT", "K150LT", "K125GT", "K125R", "K125RS", "K125RT", "K125LT", "K100GT", "K100R", "K100RS", "K100RT", "K100LT", "K80GT", "K80R", "K80RS", "K80RT", "K80LT", "K65GT", "K65R", "K65RS", "K65RT", "K65LT", "K60GT", "K60R", "K60RS", "K60RT", "K60LT", "K50GT", "K50R", "K50RS", "K50RT", "K50LT", "K45GT", "K45R", "K45RS", "K45RT", "K45LT", "K40GT", "K40R", "K40RS", "K40RT", "K40LT", "K35GT", "K35R", "K35RS", "K35RT", "K35LT", "K30GT", "K30R", "K30RS", "K30RT", "K30LT", "K25GT", "K25R", "K25RS", "K25RT", "K25LT", "K20GT", "K20R", "K20RS", "K20RT", "K20LT", "K15GT", "K15R", "K15RS", "K15RT", "K15LT", "K10GT", "K10R", "K10RS", "K10RT", "K10LT", "K5GT", "K5R", "K5RS", "K5RT", "K5LT",
            // C серия (скутери)
            "C650GT", "C600 Sport", "C500GT", "C500 Sport", "C400GT", "C400 Sport", "C350GT", "C350 Sport", "C300GT", "C300 Sport", "C250GT", "C250 Sport", "C200GT", "C200 Sport", "C150GT", "C150 Sport", "C125GT", "C125 Sport", "C100GT", "C100 Sport", "C80GT", "C80 Sport", "C65GT", "C65 Sport", "C60GT", "C60 Sport", "C50GT", "C50 Sport", "C45GT", "C45 Sport", "C40GT", "C40 Sport", "C35GT", "C35 Sport", "C30GT", "C30 Sport", "C25GT", "C25 Sport", "C20GT", "C20 Sport", "C15GT", "C15 Sport", "C10GT", "C10 Sport", "C5GT", "C5 Sport",
            // R nineT серия (retro)
            "R nineT", "R18", "R16", "R14", "R12", "R10", "R8", "R6", "R4", "R2",
            // Допълнителни модели
            "125", "150", "200", "250", "300", "350", "400", "450", "500", "550", "600", "650", "700", "750", "800", "850", "900", "950", "1000", "1100", "1150", "1200", "1250", "1300", "1400", "1500", "1600", "1700", "1800", "1900", "2000"
        ),
        "Buell" to arrayOf("X1 Lightning", "XB9S Lightning", "XB12S Lightning", "XB12R Firebolt", "XB12X Ulysses", "XB12Scg CityX", "1125R", "1125CR", "1190RS", "1190RX", "1190SX", "Blast", "M2 Cyclone", "S1 Lightning", "S3 Thunderbolt"),
        "Ducati" to arrayOf("Panigale V2", "Panigale V4", "Monster 821", "Monster 1200", "Multistrada 950", "Multistrada 1260", "Diavel", "X Diavel", "Hypermotard", "SuperSport", "Scrambler", "DesertX", "Streetfighter", "Superleggera"),
        "Harley-Davidson" to arrayOf("Sportster", "Softail", "Touring", "CVO", "LiveWire", "Street 500", "Street 750", "Iron 883", "Forty-Eight", "Road King", "Electra Glide", "Street Glide", "Road Glide", "Fat Boy", "Heritage Classic"),
        "Honda" to arrayOf(
            // CBR серия (спортни)
            "CBR600RR", "CBR1000RR", "CBR300R", "CBR500R", "CBR250R", "CBR150R", "CBR600F", "CBR900RR", "CBR1100XX",
            // CB серия (универсални)
            "CB650R", "CB1000R", "CB300R", "CB500F", "CB500X", "CB125F", "CB125R", "CB150R", "CB250F", "CB400F", "CB600F", "CB750F", "CB900F", "CB1100", "CB1100F", "CB1300", "CBF1000", "CBF600", "CBF500",
            // CRF серия (кросови)
            "CRF450R", "CRF250R", "CRF1100L Africa Twin",
            // VFR серия (туристически)
            "VFR800", "VFR1200", "VFR750F", "VFR400R",
            // VTR серия
            "VTR1000F", "VTR1000SP", "VTR250",
            // VT серия (круизери)
            "VT750C", "VT1100C", "VT1300CX", "VTX1300", "VTX1800",
            // ST серия (туристически)
            "ST1300", "ST1100",
            // NC серия
            "NC750X",
            // NT серия
            "NT700V",
            // DN серия
            "DN-01", "NM4",
            // PCX серия (скутери)
            "PCX125", "PCX150",
            // SH серия (скутери)
            "SH150i", "SH300i",
            // Forza серия (скутери)
            "Forza 300", "Forza 350", "Forza 750",
            // CTX серия
            "CTX700", "CTX1300",
            // F серия
            "F6B", "F6C",
            // Valkyrie серия
            "Valkyrie", "Valkyrie Rune",
            // Скутери
            "Ruckus", "Metropolitan", "Elite 125", "Elite 250", "Helix", "Reflex", "Spacy", "Lead", "Dio", "Tact", "Zoomer", "Monkey", "Grom", "Super Cub", "Cub", "Wave",
            // Dream серия
            "Dream", "Dream 50", "Dream 110", "Dream 125", "Dream 150", "Dream 250", "Dream 300", "Dream 400", "Dream 500", "Dream 600", "Dream 700", "Dream 800", "Dream 900", "Dream 1000", "Dream 1100", "Dream 1200", "Dream 1300", "Dream 1400", "Dream 1500", "Dream 1600", "Dream 1700", "Dream 1800", "Dream 1900", "Dream 2000",
            // Допълнителни модели
            "125", "150", "250", "750", "919", "ANF", "Benly", "Cb", "Cbf", "Cbr", "Cbx", "Ch", "Cl", "Cm", "Cmx", "Cr", "Crf", "Crossrunner", "Cx", "DN", "Deauville", "Dio", "Dylan", "Eve", "F6", "FT", "Fmx", "Foresight", "Forza", "FourTrax", "Fury", "Gl", "Gold Wing", "Hornet", "Integra", "Jazz", "Lead", "MSX125", "MT", "MTX", "Magna", "Metropolitan", "Monkey", "Nc", "Ns", "Nt", "Ntv", "Nx", "Paneuropean", "Pantheon", "Pc", "Pcx", "Ps", "Rebel", "Rune", "S-Wing", "SCV", "SLR", "ST", "SW", "Sabre", "Sh", "Shadow", "Silver Wing", "Sky", "Stateline", "Steed", "Super Cub", "TL", "Trx", "Valkyrie", "Varadero", "Vf", "Vfr", "Vigor", "Vision", "Vt", "Vtr", "Vtx", "Wave", "X-ADV", "X-Eleven", "X8R-S", "Xl", "Xlv", "Xr", "Xrv", "Z50R", "Zoomer"
        ),
        "Kawasaki" to arrayOf(
            // Ninja серия (спортни)
            "Ninja 300", "Ninja 400", "Ninja 650", "Ninja ZX-6R", "Ninja ZX-10R", "Ninja H2", "Ninja H2R", "Ninja",
            // Z серия (naked)
            "Z400", "Z650", "Z900", "Z1000", "Z", "Zr", "Zl",
            // ZX серия (спортни)
            "Zx", "Zxr", "Zzr",
            // Versys серия (adventure)
            "Versys 650", "Versys 1000", "Versys",
            // KLR серия (adventure)
            "KLR650", "Klr",
            // KLX серия (кросови)
            "KLX450R", "Klx",
            // Vulcan серия (круизери)
            "Vulcan S", "Vulcan 900", "Vulcan 1700", "Vulcan", "Vn",
            // Concours серия (туристически)
            "Concours 14",
            // ER серия (naked)
            "ER",
            // EX серия (кросови)
            "EX",
            // KX серия (кросови)
            "Kx",
            // KDX серия (кросови)
            "Kdx",
            // KFX серия (ATV)
            "Kfx",
            // KLE серия (adventure)
            "Kle",
            // KLF серия
            "Klf",
            // KLV серия
            "Klv",
            // KMX серия
            "Kmx",
            // KVF серия
            "Kvf",
            // KZ серия (круизери)
            "Kz",
            // GPZ серия (спортни)
            "Gpz",
            // GTR серия
            "Gtr",
            // J серия (скутери)
            "J125", "J300",
            // EL серия
            "EL",
            // EN серия
            "EN",
            // Eliminator серия (круизери)
            "Eliminator",
            // Brute Force серия (ATV)
            "Brute Force",
            // Mule серия (UTV)
            "Mule",
            // Voyager серия (круизери)
            "Voyager",
            // Zephyr серия (круизери)
            "Zephyr",
            // W серия
            "W",
            // Допълнителни модели
            "1000", "125", "250", "400", "620", "650", "750", "900"
        ),
        "KTM" to arrayOf(
            // Duke серия (naked)
            "1290 Super Duke R", "890 Duke R", "390 Duke", "Duke", "Duke III", "Super Duke",
            // Adventure серия (adventure)
            "1290 Super Adventure", "790 Adventure", "390 Adventure", "Adventure",
            // SX-F серия (кросови)
            "450 SX-F", "250 SX-F", "350 SX-F", "125 SX", "85 SX", "65 SX", "50 SX", "SX-F", "SX",
            // EXC серия (enduro)
            "EXC", "EXC-E",
            // RC серия (спортни)
            "RC 390", "RC 200", "RC 125", "RC8",
            // SMC серия (supermoto)
            "SMC",
            // SMR серия (supermoto)
            "SMR",
            // XC серия (cross-country)
            "XC",
            // Freeride серия
            "Freeride",
            // LC серия
            "LC",
            // Enduro LC-4 серия
            "Enduro LC-4",
            // Supermoto LC-4 серия
            "Supermoto LC-4",
            // Допълнителни модели по кубатура
            "50", "65", "85", "125", "150", "250", "300", "350", "400", "450", "500", "505", "525", "530", "560", "625", "640", "690", "950", "990"
        ),
        "Suzuki" to arrayOf(
            // GSX-R серия (спортни)
            "GSX-R1000", "GSX-R750", "GSX-R600", "Gsxr",
            // GSX серия (спортни)
            "GSX-S1000", "GSX-S750", "GSX-S1000GT", "Gsx",
            // V-Strom серия (adventure)
            "V-Strom 650", "V-Strom 1050", "V-strom",
            // SV серия (naked)
            "SV650", "SV1000", "SV",
            // Bandit серия (naked)
            "Bandit 600", "Bandit 1200", "Bandit",
            // Katana серия
            "Katana",
            // Boulevard серия (круизери)
            "Boulevard C50", "Boulevard M109R", "Boulevard",
            // Intruder серия (круизери)
            "Intruder",
            // Marauder серия (круизери)
            "Marauder",
            // Savage серия (круизери)
            "Savage",
            // Volusia серия (круизери)
            "Volusia",
            // Burgman серия (скутери)
            "Burgman",
            // Address серия (скутери)
            "Address",
            // Gemma серия (скутери)
            "Gemma",
            // DR серия (кросови)
            "DR-Z", "Dr",
            // RM серия (кросови)
            "Rm", "Rmz",
            // GN серия (универсални)
            "GN",
            // GZ серия (универсални)
            "GZ",
            // GS серия (универсални)
            "Gs", "Gsf", "Gsr",
            // Hayabusa серия (спортни)
            "Hayabusa",
            // B-King серия (спортни)
            "B-King",
            // Gladius серия (naked)
            "Gladius",
            // Inazuma серия (naked)
            "Inazuma",
            // DL серия (adventure)
            "DL",
            // RF серия
            "Rf",
            // RV серия
            "Rv",
            // SFV серия
            "SFV",
            // SR серия
            "SR",
            // Sixteen серия
            "Sixteen",
            // Street Magic серия
            "Street Magic",
            // T серия
            "T",
            // TL серия
            "TL",
            // TU серия
            "TU",
            // US серия
            "US",
            // VL серия
            "VL",
            // VS серия
            "VS",
            // VX серия
            "VX",
            // VZ серия
            "VZ",
            // Van Van серия
            "Van Van",
            // XF серия
            "XF",
            // Quad серия (ATV)
            "KingQuad", "QuadRacer", "QuadSport",
            // Допълнителни модели
            "250", "An", "Hokuto", "Landie", "LC"
        ),
        "Triumph" to arrayOf("Speed Triple", "Street Triple", "Daytona 675", "Tiger 900", "Tiger 1200", "Bonneville T120", "Bonneville T100", "Thruxton", "Scrambler", "Rocket 3", "Speedmaster", "Bobber", "America", "Sprint ST", "Sprint GT"),
        "Yamaha" to arrayOf(
            // YZF-R серия (спортни)
            "YZF-R1", "YZF-R6", "YZF-R3", "YZF-R7", "YZF-R125", "YZF-R15", "Yzf",
            // MT серия (naked)
            "MT-07", "MT-09", "MT-10", "MT-01", "MT-03",
            // FZ серия (naked)
            "FZ-07", "FZ-09", "FZ-10", "FZ1", "FZ6", "FZ6N", "FZ8", "FZS", "Fazer", "Fj", "Fjr", "Fz", "Fzr", "Fzx", "FZF R3",
            // WR серия (кросови)
            "WR450F", "WR250F", "WR250R", "WR250X", "Wr",
            // YZ серия (кросови)
            "YZ450F", "YZ250F", "Yz",
            // Tracer серия (туристически)
            "Tracer 900", "Tracer",
            // FJR серия (туристически)
            "FJR1300", "Fjr",
            // V-Max серия
            "VMAX", "V-Max",
            // Star серия (круизери)
            "Star", "V-Star", "Road Star", "Royal Star", "Drag Star", "Roadliner", "Stratoliner", "Stryker",
            // XJ серия
            "XJ6", "XJ6 Diversion", "XJR1300", "XJR400", "XJ", "XJR",
            // XT серия (adventure)
            "XT660R", "XT660Z Ténéré", "XT1200Z Super Ténéré", "XT250", "Xt", "Xtz",
            // XSR серия (retro)
            "XSR700", "XSR900", "XSR125",
            // SR серия
            "SR400", "SR500", "Sr",
            // TW серия
            "TW200", "Tw",
            // TMAX серия (скутери)
            "TMAX", "T-max",
            // XMAX серия (скутери)
            "XMAX", "X-max",
            // SMAX серия (скутери)
            "SMAX",
            // NMAX серия (скутери)
            "NMAX",
            // Aerox серия (скутери)
            "Aerox",
            // Cygnus серия (скутери)
            "Cygnus", "Cygnus X",
            // BWS серия (скутери)
            "BWS",
            // Zuma серия (скутери)
            "Zuma",
            // Vino серия (скутери)
            "Vino",
            // C3 серия (скутери)
            "C3", "C3-X",
            // Majesty серия (скутери)
            "Majesty",
            // Morphous серия (скутери)
            "Morphous",
            // Tricity серия (скутери)
            "Tricity",
            // Niken серия
            "Niken", "Niken GT",
            // Допълнителни модели
            "2ja", "3kj", "4e", "BT", "Bolt", "Booster", "Brrees", "Bw", "CT", "Crypton", "Delight", "Dt", "Grizzly", "Jog", "Maxim", "Maxter", "Neos", "PW", "R 3", "RX", "Raider", "Raptor", "Rd", "Serow", "Slider", "TZ", "Tdm", "Tdr", "Tenere", "Tt", "Ttr", "Tzr", "Versity", "Virago", "WF", "Warrior", "Why", "Wolverine", "X-City", "XC", "XV", "XVZ", "Xenter", "Xs", "Xvs", "YBR", "YQ", "YX", "Yfm", "Yfz", "Yp"
        ),
        "Zero" to arrayOf("SR/F", "SR/S", "DSR", "DSR Black Forest", "FXE", "FXS", "FX", "S", "SR", "DSS", "DS", "XU", "XU Urban", "XU Sport", "XU Military", "XU Police", "FX", "FXS", "FXE", "DS", "DSR", "SR", "SR/F", "SR/S"),
        "ADLI" to arrayOf("ADLI 50", "ADLI 125", "ADLI 150", "ADLI 200"),
        "Aeon" to arrayOf("Aeon 50", "Aeon 125", "Aeon 150", "Aeon 200"),
        "American Ironhorse" to arrayOf("Outlaw", "Texas Chopper", "Legend"),
        "Arctic Cat" to arrayOf("Wildcat", "Mud Pro", "Prowler", "Havoc"),
        "Argo" to arrayOf("Argo 6x6", "Argo 8x8", "Argo Frontier"),
        "Askoll" to arrayOf("Askoll 50", "Askoll 125", "Askoll 150"),
        "Awo" to arrayOf("Awo 50", "Awo 125", "Awo 150"),
        "BRP" to arrayOf("Can-Am Spyder", "Can-Am Ryker", "Can-Am Commander"),
        "Balkan" to arrayOf("Balkan 50", "Balkan 125", "Balkan 150", "Balkan 200"),
        "Baotian" to arrayOf("Baotian 50", "Baotian 125", "Baotian 150"),
        "Barton" to arrayOf("Barton 50", "Barton 125", "Barton 150", "Barton 200"),
        "Bashan" to arrayOf("Bashan 50", "Bashan 125", "Bashan 150", "Bashan 200"),
        "Benelli" to arrayOf("TNT", "Tornado", "Leoncino", "TRK", "Imperiale", "302R", "752S", "502C"),
        "Benzhou" to arrayOf("Benzhou 50", "Benzhou 125"),
        "Big Dog" to arrayOf("Mastiff", "Pitbull", "Wolf", "Ridgeback"),
        "Bombardier" to arrayOf("Ski-Doo", "Sea-Doo", "Can-Am"),
        "Brixton" to arrayOf("Brixton 125", "Brixton 150", "Brixton 200", "Brixton 250"),
        "Buyang" to arrayOf("Buyang 50", "Buyang 125", "Buyang 150"),
        "Cfmoto" to arrayOf("CFMoto 150", "CFMoto 250", "CFMoto 400", "CFMoto 650", "CFMoto 800"),
        "Cpi" to arrayOf("CPI 50", "CPI 125", "CPI 150", "CPI 200"),
        "Cz" to arrayOf("CZ 125", "CZ 250", "CZ 350", "CZ 500"),
        "Daelim" to arrayOf("Daelim 125", "Daelim 150", "Daelim 200", "Daelim 250"),
        "Daytona" to arrayOf("Daytona 125", "Daytona 150", "Daytona 200"),
        "Derbi" to arrayOf("Derbi 50", "Derbi 125", "Derbi 150", "Derbi 200"),
        "Dinli" to arrayOf("Dinli 50", "Dinli 125", "Dinli 150"),
        "Energica" to arrayOf("Ego", "Eva", "EsseEsse9"),
        "Etz" to arrayOf("Etz 50", "Etz 125", "Etz 150"),
        "FB Mondial" to arrayOf("FB Mondial 125", "FB Mondial 250", "FB Mondial 400"),
        "Falcon" to arrayOf("Falcon 50", "Falcon 125", "Falcon 150"),
        "Fantic" to arrayOf("Fantic 50", "Fantic 125", "Fantic 200", "Fantic 250"),
        "Garelli" to arrayOf("Garelli 50", "Garelli 125", "Garelli 150"),
        "Generic" to arrayOf("Generic 50", "Generic 125", "Generic 150"),
        "Gilera" to arrayOf("Gilera 50", "Gilera 125", "Gilera 150", "Gilera 200"),
        "Go-ped" to arrayOf("Go-ped 50", "Go-ped 125", "Go-ped 150"),
        "HISUN" to arrayOf("HISUN 150", "HISUN 200", "HISUN 250"),
        "Hanway" to arrayOf("Hanway 125", "Hanway 150", "Hanway 200"),
        "Herkules" to arrayOf("Herkules 125", "Herkules 150"),
        "Horwin" to arrayOf("Horwin 125", "Horwin 150", "Horwin 200"),
        "Hyosung" to arrayOf("Hyosung 125", "Hyosung 150", "Hyosung 200", "Hyosung 250"),
        "Italjet" to arrayOf("Italjet 50", "Italjet 125", "Italjet 150"),
        "Jawa" to arrayOf("Jawa 125", "Jawa 250", "Jawa 350", "Jawa 500"),
        "Jinlun" to arrayOf("Jinlun 50", "Jinlun 125", "Jinlun 150"),
        "Jonway" to arrayOf("Jonway 50", "Jonway 125", "Jonway 150"),
        "KL MOTORCYCLE" to arrayOf("KL 125", "KL 150", "KL 200"),
        "KSR" to arrayOf("KSR 50", "KSR 125", "KSR 150"),
        "Kayo Moto" to arrayOf("Kayo 125", "Kayo 150", "Kayo 200"),
        "Keeway" to arrayOf("Keeway 50", "Keeway 125", "Keeway 150", "Keeway 200"),
        "Kinetic" to arrayOf("Kinetic 50", "Kinetic 125"),
        "Kove" to arrayOf("Kove 150", "Kove 200", "Kove 250"),
        "Kymco" to arrayOf("Kymco 50", "Kymco 125", "Kymco 150", "Kymco 200", "Kymco 250", "Kymco 300"),
        "La Souris" to arrayOf("La Souris 50", "La Souris 125"),
        "Lambreta" to arrayOf("Lambreta 50", "Lambreta 125"),
        "Lexmoto" to arrayOf("Lexmoto 50", "Lexmoto 125", "Lexmoto 150"),
        "Lifan" to arrayOf("Lifan 50", "Lifan 125", "Lifan 150", "Lifan 200"),
        "Linhai" to arrayOf("Linhai 50", "Linhai 125", "Linhai 150"),
        "Lynx" to arrayOf("Lynx 50", "Lynx 125"),
        "Malaguti" to arrayOf("Malaguti 50", "Malaguti 125", "Malaguti 150", "Malaguti 200"),
        "Mbk" to arrayOf("MBK 50", "MBK 125", "MBK 150"),
        "Moto Morini" to arrayOf("Moto Morini 125", "Moto Morini 250", "Moto Morini 350", "Moto Morini 500"),
        "MotorHispania" to arrayOf("MotorHispania 50", "MotorHispania 125"),
        "Motoretta" to arrayOf("Motoretta 50", "Motoretta 125"),
        "Mz" to arrayOf("MZ 125", "MZ 150", "MZ 250", "MZ 350"),
        "NIU" to arrayOf("NIU N1", "NIU N1S", "NIU N1GT", "NIU U1", "NIU U1GT"),
        "Orcal" to arrayOf("Orcal 50", "Orcal 125", "Orcal 150"),
        "Peugeot" to arrayOf("Peugeot 50", "Peugeot 125", "Peugeot 150", "Peugeot 200"),
        "Piaggio" to arrayOf("Piaggio 50", "Piaggio 125", "Piaggio 150", "Piaggio 200", "Piaggio 300"),
        "Polaris" to arrayOf("Polaris 150", "Polaris 200", "Polaris 250", "Polaris 300"),
        "QJMotor" to arrayOf("QJMotor 125", "QJMotor 150", "QJMotor 200", "QJMotor 250"),
        "Qingqi" to arrayOf("Qingqi 50", "Qingqi 125", "Qingqi 150"),
        "Quadro" to arrayOf("Quadro 50", "Quadro 125", "Quadro 150"),
        "Rudge" to arrayOf("Rudge 125", "Rudge 250", "Rudge 350"),
        "Sachs" to arrayOf("Sachs 50", "Sachs 125", "Sachs 150"),
        "Sampo" to arrayOf("Sampo 50", "Sampo 125"),
        "Sanyang" to arrayOf("Sanyang 50", "Sanyang 125", "Sanyang 150"),
        "Scoot" to arrayOf("Scoot 50", "Scoot 125"),
        "Segway Powersports" to arrayOf("Segway 50", "Segway 125", "Segway 150"),
        "Shineray" to arrayOf("Shineray 50", "Shineray 125", "Shineray 150", "Shineray 200"),
        "Silence" to arrayOf("Silence 50", "Silence 125"),
        "Simson" to arrayOf("Simson 50", "Simson 125", "Simson 150", "Simson 200"),
        "Ski-Doo" to arrayOf("Ski-Doo 150", "Ski-Doo 200", "Ski-Doo 250"),
        "Stark" to arrayOf("Stark 50", "Stark 125", "Stark 150"),
        "Sunra" to arrayOf("Sunra 50", "Sunra 125"),
        "Sunsto" to arrayOf("Sunsto 50", "Sunsto 125", "Sunsto 150", "Sunsto 200"),
        "Super Soco" to arrayOf("Super Soco 50", "Super Soco 125"),
        "Surron" to arrayOf("Surron 50", "Surron 125", "Surron 150"),
        "Swm" to arrayOf("SWM 125", "SWM 150", "SWM 200", "SWM 250"),
        "Sym" to arrayOf("SYM 50", "SYM 125", "SYM 150", "SYM 200", "SYM 250"),
        "Tatran" to arrayOf("Tatran 50", "Tatran 125"),
        "Telstar" to arrayOf("Telstar 50", "Telstar 125", "Telstar 150"),
        "Tgb" to arrayOf("TGB 50", "TGB 125", "TGB 150"),
        "Tm" to arrayOf("TM 50", "TM 125", "TM 150"),
        "Tomos" to arrayOf("Tomos 50", "Tomos 125", "Tomos 150"),
        "VROMOS" to arrayOf("VROMOS 50", "VROMOS 125", "VROMOS 150"),
        "Vespa" to arrayOf(
            // Класически модели
            "Vespa 50", "Vespa 125", "Vespa 150", "Vespa 200", "Vespa 250", "Vespa 300",
            // PX серия
            "PX 50", "PX 125", "PX 150", "PX 200", "PX 250", "PX 300",
            // ET серия
            "ET 50", "ET 125", "ET 150", "ET 200", "ET 250", "ET 300",
            // LX серия
            "LX 50", "LX 125", "LX 150", "LX 200", "LX 250", "LX 300",
            // GTS серия
            "GTS 50", "GTS 125", "GTS 150", "GTS 200", "GTS 250", "GTS 300", "GTS 350", "GTS 400", "GTS 500", "GTS 600", "GTS 700", "GTS 800", "GTS 900", "GTS 1000",
            // Primavera серия
            "Primavera 50", "Primavera 125", "Primavera 150", "Primavera 200", "Primavera 250", "Primavera 300",
            // Sprint серия
            "Sprint 50", "Sprint 125", "Sprint 150", "Sprint 200", "Sprint 250", "Sprint 300",
            // S серия
            "S 50", "S 125", "S 150", "S 200", "S 250", "S 300",
            // LXV серия
            "LXV 50", "LXV 125", "LXV 150", "LXV 200", "LXV 250", "LXV 300",
            // GTV серия
            "GTV 50", "GTV 125", "GTV 150", "GTV 200", "GTV 250", "GTV 300",
            // GT серия
            "GT 50", "GT 125", "GT 150", "GT 200", "GT 250", "GT 300",
            // GS серия
            "GS 50", "GS 125", "GS 150", "GS 200", "GS 250", "GS 300",
            // GL серия
            "GL 50", "GL 125", "GL 150", "GL 200", "GL 250", "GL 300",
            // GTR серия
            "GTR 50", "GTR 125", "GTR 150", "GTR 200", "GTR 250", "GTR 300",
            // GTS Super серия
            "GTS Super 50", "GTS Super 125", "GTS Super 150", "GTS Super 200", "GTS Super 250", "GTS Super 300", "GTS Super 350", "GTS Super 400", "GTS Super 500", "GTS Super 600", "GTS Super 700", "GTS Super 800", "GTS Super 900", "GTS Super 1000",
            // GTS Touring серия
            "GTS Touring 50", "GTS Touring 125", "GTS Touring 150", "GTS Touring 200", "GTS Touring 250", "GTS Touring 300", "GTS Touring 350", "GTS Touring 400", "GTS Touring 500", "GTS Touring 600", "GTS Touring 700", "GTS Touring 800", "GTS Touring 900", "GTS Touring 1000",
            // GTS Super Sport серия
            "GTS Super Sport 50", "GTS Super Sport 125", "GTS Super Sport 150", "GTS Super Sport 200", "GTS Super Sport 250", "GTS Super Sport 300", "GTS Super Sport 350", "GTS Super Sport 400", "GTS Super Sport 500", "GTS Super Sport 600", "GTS Super Sport 700", "GTS Super Sport 800", "GTS Super Sport 900", "GTS Super Sport 1000",
            // GTS Super Touring серия
            "GTS Super Touring 50", "GTS Super Touring 125", "GTS Super Touring 150", "GTS Super Touring 200", "GTS Super Touring 250", "GTS Super Touring 300", "GTS Super Touring 350", "GTS Super Touring 400", "GTS Super Touring 500", "GTS Super Touring 600", "GTS Super Touring 700", "GTS Super Touring 800", "GTS Super Touring 900", "GTS Super Touring 1000",
            // GTS Super Sport Touring серия
            "GTS Super Sport Touring 50", "GTS Super Sport Touring 125", "GTS Super Sport Touring 150", "GTS Super Sport Touring 200", "GTS Super Sport Touring 250", "GTS Super Sport Touring 300", "GTS Super Sport Touring 350", "GTS Super Sport Touring 400", "GTS Super Sport Touring 500", "GTS Super Sport Touring 600", "GTS Super Sport Touring 700", "GTS Super Sport Touring 800", "GTS Super Sport Touring 900", "GTS Super Sport Touring 1000",
            // Допълнителни модели
            "50", "125", "150", "200", "250", "300", "350", "400", "450", "500", "550", "600", "650", "700", "750", "800", "850", "900", "950", "1000"
        ),
        "Voge" to arrayOf("Voge 125", "Voge 150", "Voge 200", "Voge 250"),
        "Wangye" to arrayOf("Wangye 50", "Wangye 125"),
        "XGJao" to arrayOf("XGJao 50", "XGJao 125"),
        "Xingyue" to arrayOf("Xingyue 50", "Xingyue 125"),
        "Znen" to arrayOf("Znen 50", "Znen 125", "Znen 150"),
        "Zongshen" to arrayOf("Zongshen 50", "Zongshen 125", "Zongshen 150"),
        "Zontes" to arrayOf("Zontes 125", "Zontes 150", "Zontes 200"),
        "Zundapp" to arrayOf("Zundapp 50", "Zundapp 125", "Zundapp 150"),
        "Вятка" to arrayOf("Вятка 50", "Вятка 125"),
        "Днепр" to arrayOf("Днепр 125", "Днепр 150"),
        "Други" to arrayOf("Други мотоциклети"),
        "Иж" to arrayOf("Иж 50", "Иж 125", "Иж 150", "Иж 200"),
        "Ковровец" to arrayOf("Ковровец 50", "Ковровец 125"),
        "Мини мотоциклети" to arrayOf("Мини 50", "Мини 125"),
        "Минск" to arrayOf("Минск 50", "Минск 125", "Минск 150"),
        "Поръчкови" to arrayOf("Поръчкови мотоциклети"),
        "Рига" to arrayOf("Рига 50", "Рига 125")
    )
}
