# Граничні випадки

## 1. Підхід

Некоректні дані й недопустимі дії не повинні валити програму: вони відкидаються з поясненням, а решта системи працює далі.

| Рівень | Як обробляється | Де в коді |
| --- | --- | --- |
| Завантаження JSON | Некоректний запис відкидається, решта завантажується. Причина зберігається як `LoadError(file, record, message)`, номер запису рахується з 1 | `JsonStorage.openSkippingInvalid`, `JsonRepository`, `LoadResult` |
| Строге завантаження | Будь-який некоректний запис кидає `StorageException`: у повідомленні перша причина і кількість решти (`(+3 more)`) | `JsonStorage.open` |
| Продаж і скасування квитків | Недопустима дія кидає підклас `TransportException`, стан сховища не змінюється | `TicketService`, `Ticket`, `Transitional` |
| Розклад | Недопустиме призначення не кидає виняток, а повертає список `Conflict(type, description)` | `ConflictChecker` |

## 2. Випадки 1–13

### Дані (1–3)

| # | Випадок | Очікувана поведінка | Фактично | Тест | Дані |
| --- | --- | --- | --- | --- | --- |
| 1 | Місто з маршруту відсутнє в `cities.json` | Помилка з номером запису, решта завантажується | `routes.json, record 2: unknown city 'ODESA'`, маршрути 1 і 3 завантажені | `EdgeDataTest.routeWithUnknownCityIsSkipped` | `data/edge/unknown-city/` |
| 2 | Порожня назва, місткість 0, від'ємні значення | Помилка валідації, запис відкидається | 4 записи відкинуто (місто без назви, автобус на 0 місць, від'ємна багажна місткість, від'ємна вага багажу), коректні завантажені | `EdgeDataTest.invalidValuesAreSkipped` | `data/edge/invalid-values/` |
| 3 | Дубль ID автобуса чи номера квитка | Помилка, перший запис лишається | `duplicate Bus id AA1234AB, the first record is kept`, так само для квитка `T1-001` | `EdgeDataTest.duplicatesKeepTheFirstRecord` | `data/edge/duplicates/` |

Строгий режим на тих самих даних перевіряє `EdgeDataTest.strictOpenRejectsBadData`, а те, що основні дані в `data/` чисті, перевіряє `EdgeDataTest.seedDataHasNoErrors`.

### Квитки (4–8)

Місткість рахується окремо для кожної ділянки маршруту: квиток `from -> to` займає місце на всіх ділянках між ними, тому місце вільне, лише якщо воно вільне на кожній з цих ділянок.

| # | Випадок | Очікувана поведінка | Фактично | Тест |
| --- | --- | --- | --- | --- |
| 4 | Продаж останнього місця на ділянці | OK | Квиток продано, вільних місць 0 | `TicketServiceTest.sellsLastSeatOnSegment` |
| 5 | Ще один квиток на ту саму ділянку | Відмова | `NoSeatsAvailableException`, квиток не створюється | `TicketServiceTest.rejectsTicketWhenSegmentIsFull` |
| 6 | Kyiv → Lutsk, переповнена лише Rivne → Lutsk | Відмова, перевіряються всі ділянки | `NoSeatsAvailableException` | `TicketServiceTest.checksEverySegmentOfTheLeg` |
| 7 | `from == to`, `from` після `to`, місто поза маршрутом | Відмова | `ValidationException` (`trip ... does not go LUTSK -> KYIV`), квитків 0 | `TicketServiceTest.rejectsWrongDirection` |
| 8 | Скасування вже скасованого квитка | Ідемпотентно або помилка (зафіксувати) | Помилка `InvalidStatusTransitionException`; місце звільняється після першого скасування | `TicketServiceTest.cancelFreesSeatAndSecondCancelFails` |

Суміжні перевірки: місце повторно використовується на ділянках, що не перетинаються (`seatIsReusedOnDisjointLegs`), номери квитків не повторюються після скасування (`ticketNumbersAreNotReusedAfterCancel`), використаний квиток не скасовується (`usedTicketCannotBeCancelled`), продаж можливий лише на рейс зі статусом `SCHEDULED` і призначеним автобусом (`sellsOnlyForScheduledTripWithBus`).

### Розклад (9–13)

| # | Випадок | Очікувана поведінка | Фактично | Тест |
| --- | --- | --- | --- | --- |
| 9 | Рейс через північ | Правильна тривалість, без хибного конфлікту | Прибуття переходить на наступну дату; перетин і розворот рахуються через північ | `TripTest.overnightTripEndsNextDay`, `ConflictCheckerTest.nightTripOverlapsNextDay`, `turnaroundAfterNightTrip`, `afterNightTripBusIsNotBackInDepot` |
| 10 | Розворот рівно мінімальний / на 1 хв менше | OK / `TURNAROUND` | 10:30 без конфлікту, 10:29 `TURNAROUND` (розворот 30 хв) | `ConflictCheckerTest.secondTrip [exact turnaround]`, `[short turnaround]` |
| 11 | Автобус у `MAINTENANCE` | `BUS_UNAVAILABLE` | `BUS_UNAVAILABLE` | `ConflictCheckerTest.busInMaintenance` |
| 12 | Рейсів більше, ніж автобусів | Не падає, список непокритих рейсів | — | — |
| 13 | 0 рейсів або 0 автобусів | Порожній розклад, без винятку | — | — |

Випадки 12 і 13 перевіряються на рівні оптимізатора (Greedy, Local Search). Рядки заповнюються разом із PR оптимізатора.

## 3. Прийняті рішення

| Питання | Рішення | Чому |
| --- | --- | --- |
| Повторне скасування (8) | Помилка, а не ідемпотентність | Переходи статусів квитка проходять через `Transitional`, і `CANCELLED -> CANCELLED` не є допустимим переходом; так повторна дія не губиться мовчки |
| Дублікати (3) | Лишається перший запис | Результат не залежить від того, скільки дублікатів далі у файлі, а номер запису в помилці вказує на зайвий |
| Некоректний запис у JSON | Відкидається весь запис, а не окреме поле | Частково заповнений об'єкт порушив би інваріанти моделі |
| Квиток із JSON з містом поза маршрутом | Не ламає продаж і підрахунок по ділянках, але займає своє місце | Зауваження на рев'ю #14; перевіряє `TicketServiceTest.brokenTicketDoesNotBreakSalesAndKeepsItsSeat` |
| Строгий і м'який режими | `open` для тестів і перевірки даних, `openSkippingInvalid` для CLI | У CLI користувач бачить список помилок і працює з рештою даних |

## 4. Як перевірити

| Що | Команда |
| --- | --- |
| Усі тести | `mvn verify` |
| Один набір некоректних даних | `Main check data/edge/<тека>` |
| Випадки 1–8 разом із таблицею-підсумком | `Main demo` |

Демо-команда не змінює файли в `data/`: продаж квитків іде на демо-рейсі в пам'яті. Код виходу `demo` дорівнює 1, якщо хоча б один випадок не пройшов.

## 5. Вивід `demo`

```text
#   Case                           Expected                         Actual
-   Seed data                      no invalid records               no invalid records               OK
1   Unknown city in a route        route skipped, rest loaded       1 skipped, 2 routes loaded       OK
2   Blank, zero, negative values   4 skipped, valid loaded          4 skipped, 3 loaded              OK
3   Duplicate bus id and ticket    duplicates skipped               skipped in buses, tickets        OK
4   Sell the last seat             sold, 0 seats left               sold, 0 seats left               OK
5   Seat on a full segment         refused: NoSeatsAvailable        refused: NoSeatsAvailable        OK
6   Full segment inside the leg    refused: NoSeatsAvailable        refused: NoSeatsAvailable        OK
7   from after to                  refused: Validation              refused: Validation              OK
8   Cancel a cancelled ticket      refused: InvalidStatusTransition refused: InvalidStatusTransition OK
Result: 8/8 cases OK
```
