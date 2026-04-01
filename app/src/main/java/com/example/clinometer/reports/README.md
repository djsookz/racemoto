# Reports System - Firebase Integration

## Преглед

Професионална система за докладване и споделяне на информация на картата (полиция, камери, инциденти, etc.) подобна на Waze. Използва Firebase Firestore за realtime синхронизация между потребители без нужда от собствен сървър.

## Структура на файлове

```
reports/
├── data/
│   ├── ReportType.kt              - Enum с типове доклади
│   ├── PoliceReport.kt            - Data model за доклад
│   └── FirebaseReportsRepository.kt - Firebase CRUD операции
├── ui/
│   ├── ReportsMapManager.kt       - Управление на Mapbox markers
│   └── ReportBottomSheet.kt       - UI за докладване/гласуване
└── ReportsIntegration.kt          - Main entry point
```

## Функционалност

### ✅ Имплементирано:
- 📍 Докладване на 6 типа събития (Полиция, Камера, Инцидент, Опасност, Трафик, Ремонт)
- 🔄 Realtime синхронизация между всички потребители
- 👍👎 Upvote/Downvote система за потвърждаване/оспорване
- ⏰ Автоматично изтичане след 30 минути
- 🚫 Rate limiting - макс 10 доклада на час на потребител
- 🗺️ Geo query - показва доклади в радиус (default 50 км)
- 🎨 Custom цветни икони според тип доклад
- 🔒 Anonymous authentication - без регистрация
- 🧹 Автоматично изчистване на изтекли доклади

### 🎯 Безопасност:
- Anonymous Firebase Auth за анонимност
- Rate limiting срещу spam
- Geo validation - координатите трябва да са валидни
- Voting limits - 1 глас на потребител на доклад
- Автоматично изтриване при много downvotes (score < -2)

## Как да използваш

### 1. Инициализация (в TrackSessionActivity)

```kotlin
class TrackSessionActivity : AppCompatActivity() {
    private lateinit var reportsIntegration: ReportsIntegration
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Инициализирай след MapView е ready
        mapView = findViewById(R.id.mapView)
        
        reportsIntegration = ReportsIntegration(this, mapView)
        reportsIntegration.initialize()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        reportsIntegration.cleanup()
    }
}
```

### 2. Показване на доклади около текуща позиция

```kotlin
// Когато получиш GPS координати
fun onLocationUpdate(latitude: Double, longitude: Double) {
    reportsIntegration.startObservingReports(
        centerLatitude = latitude,
        centerLongitude = longitude,
        radiusKm = 50.0  // optional, default е 50
    )
}
```

### 3. Създаване на доклад (бутон на UI)

```kotlin
// Добави бутон "Report" на картата
reportButton.setOnClickListener {
    val currentLat = getCurrentLatitude()
    val currentLon = getCurrentLongitude()
    
    reportsIntegration.showCreateReportDialog(currentLat, currentLon)
}
```

### 4. Гласуване за съществуващ доклад (click на marker)

```kotlin
// TODO: Имплементирай click detection на markers
// След като имаш reportId от кликнат marker:
val report = getReportById(reportId)
reportsIntegration.showVoteDialog(report)
```

## Firestore структура

### Collection: `reports`

```json
{
  "report_id_generated_by_firestore": {
    "type": "POLICE",
    "location": {
      "latitude": 42.6977,
      "longitude": 23.3219
    },
    "timestamp": "2026-03-27T14:30:00Z",
    "reporterUserId": "anonymous_user_id",
    "upvotes": 5,
    "downvotes": 1,
    "votedUserIds": ["user1", "user2", "user3"],
    "expiresAt": "2026-03-27T15:00:00Z",
    "description": null
  }
}
```

## Firestore правила (Security Rules)

За production трябва да добавиш security rules във Firebase Console:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /reports/{reportId} {
      // Всички могат да четат доклади
      allow read: if true;
      
      // Само authenticated могат да пишат
      allow create: if request.auth != null
                   && request.resource.data.reporterUserId == request.auth.uid;
      
      // Може да update само за voting
      allow update: if request.auth != null
                   && !request.resource.data.diff(resource.data).affectedKeys()
                      .hasAny(['type', 'location', 'reporterUserId', 'timestamp']);
      
      // Може да delete ако е собственик или има много downvotes
      allow delete: if request.auth != null
                   && (resource.data.reporterUserId == request.auth.uid
                       || resource.data.downvotes - resource.data.upvotes > 2);
    }
  }
}
```

## TODO / Следващи стъпчки

1. **UI Integration:**
   - Добави FAB (Floating Action Button) за бърз достъп до докладване
   - Имплементирай click detection на markers за voting
   - Добави notification badge ако има нови доклади наблизо

2. **Подобрения:**
   - Push notifications при нов доклад наблизо
   - Филтриране по тип доклад (показвай само Police, само Camera, etc.)
   - История на доклади от потребителя
   - Distance indicator (показвай "2.5 км" на marker)

3. **Performance:**
   - Caching на доклади локално (Room database)
   - Batch updates вместо individual marker changes
   - Lazy loading на отдалечени доклади

4. **Production Ready:**
   - Добави Firestore security rules
   - Setup Firebase App Check за защита от злоупотреба
   - Analytics за tracking на usage
   - Crash reporting (Firebase Crashlytics)

## За изтриване на системата

Ако решиш да махнеш reports системата:

1. Изтрий цялата `reports/` папка
2. Премахни Firebase dependencies от `app/build.gradle.kts`
3. Премахни Firebase plugin от root `build.gradle.kts`
4. Изтрий `google-services.json`
5. Премахни всички references към `ReportsIntegration` в Activities

## Цена (Firebase Free Tier)

- **Firestore:** 50K reads/day, 20K writes/day - безплатно
- **Authentication:** Unlimited anonymous auth - безплатно
- **Cloud Functions:** 2M invocations/month - безплатно

За standard usage (100-500 потребителя) ще остане в free tier.

## Поддръжка

Системата е self-contained в `reports/` папката. Всичко е документирано и може лесно да се модифицира или изтрие.

---

**Created:** March 27, 2026  
**Status:** Ready for integration and testing
