# Некоректні дані для граничних випадків 1–3

| Тека | Випадок | Що зламано |
|---|---|---|
| `unknown-city/` | 1 | `routes.json`, запис 2: місто `ODESA` відсутнє в `cities.json` |
| `invalid-values/` | 2 | порожня назва міста, автобус на 0 місць, від'ємна багажна місткість, від'ємна вага багажу |
| `duplicates/` | 3 | дубль id автобуса й номера квитка |

`JsonStorage.openSkippingInvalid` завантажує решту записів і повертає список помилок, `JsonStorage.open` кидає `StorageException`. Перевірка: `EdgeDataTest`, демо: `check data/edge/<тека>` у CLI.
