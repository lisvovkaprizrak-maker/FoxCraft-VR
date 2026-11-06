# FoxCraft VR — игровая клавиатура и тестовый APK (Minecraft 1.21.5)

Это экспериментальный маршрут сборки **собственного приложения FoxCraft**, а не установка поверх официального QuestCraft. Исходники находятся в черновом PR #4.

## Что готово

- Fabric-клавиатура с оранжевым переливающимся свечением, RU/EN, цифровым рядом 1–0, символами при удержании 450 мс и поддержкой ё/Ё.
- Сборка Fabric 1.21.5 на Java 21, автоматические Java/Python-тесты, проверка структуры итогового JAR.
- Unity-лаунчер вызывает `Pojlib.API.prelaunch(...)` с возвращаемым **boolean**, проверяет успешность, затем устанавливает JAR **только если профиль использует Minecraft 1.21.5 и Fabric `KnotClient`**, и запускает Minecraft. Эти проверки есть и внутри установщика. Порядок важен: при `prelaunch` Pojlib обновляет моды.
- Важное отличие: в исходниках ветки QuestCraft-6.0.0 метод `prelaunch` возвращает `void`, но **приложенная к FoxCraft AAR содержит `boolean prelaunch`**. Это подтверждено `javap` на GitHub Actions 6 ноября 2026 г. Статический анализ и тесты привязаны именно к библиотеке, используемой в APK.
- На машине сборки готовый JAR кладётся в `Assets/StreamingAssets/foxcraft-keyboard-0.1.0-alpha.jar` после проверки версии, класса Fabric entrypoint и SHA-256.
- Отдельный Unity build method `FoxCraftKeyboardAndroidBuild.BuildTestApk` собирает Android ARM64 APK под package ID `com.foxcraftvr.quest`, чтобы не заменять обычный QuestCraft. Для **тестового APK** временно используется **отладочная подпись Unity** вместо недоступного оригинального QuestCraft keystore; прежние настройки подписи обязательно восстанавливаются после сборки.
- Отдельный workflow `.github/workflows/foxcraft-keyboard-apk.yml`: на PR выполняет только бесплатные проверки, а сам APK собирает **только по ручному запуску** с действующей Unity-лицензией.

## Известное ограничение версий

Другая экспериментальная ветка FoxCraft (PR #1) содержит мост Meta-клавиатуры для **Minecraft 1.21.1**. В этой ветке мод нацелен на **Minecraft 1.21.5**. Это несовместимые пакеты. Не переносите JAR из одного профиля в другой. В новую APK-сборку должен попадать именно `foxcraft_keyboard` для 1.21.5.

## Новые проверки перед созданием APK

Автоматический preflight в GitHub Actions теперь включает три независимых уровня:

1. **Java/JNI:** через `javap` читается **фактически вложенная** `Pojlib-release.aar`. Проверяются `boolean prelaunch`, методы запуска, управления профилями, доступа к файлам, а также поля `gameReady`, `model`, `gameDir`, `mainClass`, `versionName`. Это выявляет несовместимые изменения Java API.
2. **C# компиляция:** реальный исходный код `InstanceButton`, `FoxCraftInGameKeyboardInstaller` и `FoxCraftKeyboardAndroidBuild` компилируется средствами .NET 8 с *тестовыми заглушками* Unity API (`tests/unity-compile/`). Проверяет синтаксис и часть типов, **но не эквивалентна компиляции Unity Editor**.
3. **APK preflight:** проверяет `Assets/Scenes/Main.unity` в списке сцен, `pojlib.UnityPlayerActivity`, Meta Quest/OpenXR в AndroidManifest, раздельный Android package ID, безопасную установку и тесты ADB.

Установщик теперь использует уникальный временный файл `.pending-<идентификатор>`, записывает туда JAR, повторно проверяет SHA-256 и только после этого атомарно переносит его в `mods`. Существующие моды, файлы сохранений и другие ожидающие файлы не перезаписываются. В логах с префиксом `[FoxCraft]` отображаются начало `prelaunch`, установка мода, начало запуска и `gameReady`, без токенов аккаунта.

**Важно:** ни одна из этих проверок не создаёт рабочий APK, пока вручную не запущена сборка в настоящем Unity с лицензией и Android Build Support.

## Как собрать APK (после ревью и подготовки окружения)

1. Выполнить проверку PR #4 и убедиться в зелёном статусе `FoxCraft Minecraft keyboard` и `FoxCraft 1.21.5 keyboard test APK (manual)` (последний пока проверяет только настройки, без Unity).
2. Обеспечить доступность workflow в основной ветке GitHub после осознанного объединения PR. GitHub обычно не предоставляет ручной `workflow_dispatch` для workflow, отсутствующего в ветке по умолчанию. Самостоятельно PR не объединяется.
3. Настроить в GitHub Actions Secrets **напрямую в интерфейсе GitHub**, не присылая секреты в чат: `UNITY_LICENSE` или `UNITY_EMAIL`, `UNITY_PASSWORD`, `UNITY_SERIAL`.
4. В Actions вручную запустить `FoxCraft 1.21.5 keyboard test APK (manual)`. Pipeline собирает Fabric 1.21.5 JAR, укладывает его в временный checkout Unity, проверяет Pojlib AAR, запускает Unity 2022.3.62f3 для Android и проверяет package ID получившегося APK.
5. Только если **все задания** завершатся успешно, появится артефакт `FoxCraft-Quest-1.21.5-UNTESTED-APK` (7 суток). Слово UNTESTED означает, что запуск на Meta Quest и работа контроллеров ещё не подтверждены.

Локальная альтернатива при наличии Unity 2022.3.62f3 и Android Build Support: собрать Fabric мод, выполнить `python3 tools/stage_foxcraft_ingame_keyboard.py`, переключить Unity на Android, затем выбрать `FoxCraft → Build 1.21.5 keyboard test APK`.

## Проверка ADB без изменения пользовательских данных

После **успешной ручной Unity-сборки** и получения `FoxCraft-1.21.5-UNTESTED.apk` можно проверить APK и гарнитуру инструментом `tools/foxcraft_quest_smoke.py` (нужны Android SDK platform-tools `adb` и `aapt`). Он никогда не удаляет QuestCraft, не очищает игровые данные и не заменяет моды.

```bash
# Только чтение: проверить подключённый Quest и наличие FoxCraft
python3 tools/foxcraft_quest_smoke.py

# Проверить, что APK имеет package ID com.foxcraftvr.quest
python3 tools/foxcraft_quest_smoke.py --apk Builds/FoxCraft-1.21.5-UNTESTED.apk

# ЯВНО установить отдельное приложение (без очистки данных)
python3 tools/foxcraft_quest_smoke.py --apk Builds/FoxCraft-1.21.5-UNTESTED.apk --install

# ЯВНО запустить FoxCraft и посмотреть его недавние сообщения в Unity logcat
python3 tools/foxcraft_quest_smoke.py --launch --logs
```

Если несколько ADB-устройств, указать `--device SERIAL`. Любая установка возможна **только при флаге `--install` и успешной проверке package ID через `aapt`**. Запуск также требует явного `--launch`. Это помощь при ручном тестировании, а не автоматический тест поведения Minecraft/Vivecraft в VR.

## Проверка на гарнитуре

Для теста обязательно проверить: запуск Minecraft, появление клавиатуры в чате и полях адреса сервера; управление лучом VR-контроллера; короткое нажатие/удержание 450 мс; ввод кириллицы/команд; ввод Ё; закрытие чата; работу после обновления модов; повторный запуск. Пока реального запуска Meta Quest **не было**.

Не удаляйте существующий QuestCraft и миры. Не передавайте Unity-лицензию, токены или ключи подписи в репозиторий/чат. Зелёная сборка JAR и успешная упаковка APK сами по себе не доказывают работу VR-контроллеров.
