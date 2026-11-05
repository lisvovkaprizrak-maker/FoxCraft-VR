# 🦊 FoxCraft VR — состояние разработки

FoxCraft VR — экспериментальная сборка на базе QCXR-XR-Wrapper. Цель — системная клавиатура Meta Quest при вводе текста в чат Minecraft 1.21.1.

## Что действительно готово

- В форке исходный Unity-проект с Unity **2022.3.62f3** и OpenXR **1.14.3**.
- На реальном Quest 3 прежняя Fabric-часть смогла обнаружить чат и вставить тестовый русский текст. **Meta Keyboard этим не проверена.**
- В этой ветке есть Unity-мост: `Assets/Scripts/QuestChatKeyboardOverlayBridge.cs`.
- Добавлен сам Fabric-мод 0.6.0: `Fabric/foxchatkeyboard/`. Он компилируется на Java 21 без локальной установки Minecraft, а Java-тесты моделируют Fabric/Minecraft API.
- GitHub Actions **FoxCraft Fabric JAR (Java 21)** собирает JAR, выполняет тесты и публикует его как артефакт GitHub, не требуя Unity-лицензии.
- Новый `FoxCraftFabricModInstaller.cs` предназначен для копирования JAR в `mods` **собственного профиля FoxCraft** из Unity `StreamingAssets` при первом запуске. Его работа на Quest 3 пока не проверена.
- В исходный AndroidManifest.xml добавлена необязательная функция `oculus.software.overlay_keyboard`. Существующие Activity и запись `com.oculus.vr.focusaware` сохранены.
- Workflow **FoxCraft preflight** проверяет структуру проекта, манифест и части протокола без Unity-лицензии. Эти проверки **не компилируют Unity**.

## Протокол между Unity и Fabric: QCKB1

Папка: `<Minecraft gameDir>/config/questchatkeyboard-bridge`.

Fabric создаёт атомарно `request.txt`:
```text
QCKB1
OPEN
<UUID>
```
или `CLOSE` с тем же UUID.

Unity отвечает через `response.tmp` → `response.txt`:
```text
QCKB1
COMMIT
<UUID>
<UTF-8 text encoded with Base64>
```

Действия: `COMMIT` — вставить без отправки, `SUBMIT` — вставить и попробовать Enter, `CANCEL` — отмена, `ERROR` — клавиатура недоступна. Ответы без совпадающего UUID должны игнорироваться. В журнал не записывается текст чата. Потеря фокуса приложения при появлении Meta-оверлея не должна автоматически означать отмену.

Ограничение текста — **256 единиц UTF-16**. Unity и Fabric обрезают слишком длинный текст, не разрывая суррогатную пару (например, emoji); Java-тесты включают проверки этого граничного случая. Это защищает Unicode в протоколе, но ещё не подтверждает ввод с системной клавиатуры Quest 3.

**Важно:** данный Unity-код ищет уже существующую папку `instances/1.21.1` в `Application.persistentDataPath` или `Android/data/<packageId>/files`. Расположение **не подтверждено** в FoxCraft APK. Подлинная папка `FabricLoader.getGameDir()` должна совпадать.

## Оставшиеся препятствия

1. Связать артефакт Fabric JAR с будущей Unity-сборкой. Java-код и автоматическая сборка JAR уже в репозитории; скрипт `tools/foxcraft_stage_fabric.py` копирует проверенный JAR в `Assets/StreamingAssets/FoxChatKeyboard-bridge-0.6.0.jar`. **JAR не сохраняется в Git после каждой сборки; он доступен в GitHub Actions Artifacts.**
2. Получить Pojlib AAR для QuestCraft 6.0.0 и интегрировать его в Unity-проект. Добавлен экспериментальный workflow `.github/workflows/foxcraft-pojlib-aar.yml`, который пробует собрать AAR в GitHub Actions без Unity-лицензии. Его успешность должна быть проверена отдельно; AAR пока нельзя считать готовым.
3. Подготовить отдельный идентификатор приложения `com.foxcraftvr.quest`, название/иконку FoxCraft и подпись тестового APK, не затронув оригинальную QuestCraft.
4. Обеспечить Unity-лицензию для облачной Android-сборки и проверить конкретные версии Unity/Android SDK.
5. Проверить совместимость системной клавиатуры с OpenXR **1.14.3**. Специальный флаг Enable System Keyboard появился в OpenXR **1.17.0**; обновлять пакет вслепую нельзя.
6. Установить APK на Quest 3 и проверить открытие оверлея, русский/английский, руки, Enter, удаление текста и закрытие.

**Сейчас работоспособного APK FoxCraft нет.** Успешная GitHub-сборка Fabric JAR и Python/Java-тесты не доказывают работу Meta-оверлея или Unity-моста на Quest 3.

## Автоматическая сборка Fabric-мода

- Workflow: `.github/workflows/foxcraft-fabric-build.yml`.
- Выходной JAR: `FoxChatKeyboard-bridge-0.6.0-fabric-1.21.1.jar` внутри артефакта `FoxChatKeyboard-Fabric-0.6.0-1.21.1`.
- Для локальной сборки: `bash Fabric/foxchatkeyboard/build.sh`, затем `bash Fabric/foxchatkeyboard/tests/run-tests.sh`.
- Для помещения JAR в Unity: `python3 tools/foxcraft_stage_fabric.py` (из корня репозитория после сборки).
- Идентификатор Fabric-мода остаётся `questchatkeyboardbridge`. **Не устанавливайте его одновременно с тестовой версией 0.5.1 в один профиль Minecraft.**
- Fabric-мод 0.6.0 **не** заполняет чат тестовым русским текстом автоматически; это сделано только в диагностической версии 0.5.1.
- В установленной FoxCraft потребуется перезапустить Minecraft, если его запуск опередил копирование Fabric JAR.

**Автоматическая сборка JAR не означает готовый APK.**

## Безопасность

- Не публикуйте Unity-лицензию, пароли, токены или ключи подписи.
- Оригинальную QuestCraft и сохранения миров не удаляйте.
- Не пытайтесь устанавливать ZIP исходников как APK или Fabric-мод.
- FoxCraft не является официальным проектом Meta или QuestCraft; соблюдайте условия лицензий исходных компонентов.
