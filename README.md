# Bitcoin Price

Android-приложение, показывающее курс биткоина к доллару (BTC/USD).

## Возможности

- Крупная цифра текущего курса BTC/USD и изменение за 24 часа
- График цены с 2010 года по сегодняшний день (логарифмическая шкала)
- Данные загружаются с публичного API [CryptoCompare](https://min-api.cryptocompare.com)

## Сборка

Проект не содержит Gradle wrapper — сборка через локально установленный Gradle 8.x или Android Studio:

```
gradle assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions (`.github/workflows/build-apk.yml`) собирает debug-APK при пуше в `main` и публикует его как артефакт сборки.
