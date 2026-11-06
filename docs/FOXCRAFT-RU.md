# 🦊 FoxCraft VR — состояние разработки

FoxCraft VR — экспериментальная сборка на базе QCXR-XR-Wrapper. Цель — системная клавиатура Meta Quest при вводе текста в чат Minecraft 1.21.5.

**Внимание (6 ноября 2026):** исправлен блокирующий запуск конфликт метаданных Minecraft 1.21.1 / 1.21.5. Сборка 0.6.1 ориентирована на 1.21.5, но её работа на Quest 3 пока не проверена.

## Что действительно готово

- В форке исходный Unity-проект с Unity **2022.3.62f3** и OpenXR **1.14.3**.
- На реальном Quest 3 прежняя Fabric-часть смогла обнаружить чат и вставить тестовый русский текст. **Meta Keyboard этим не проверена.**
- В этой ветке есть Unity-мост: `Assets/Scripts/QuestChatKeyboardOverlayBridge.cs`.
- Добавлен сам Fabric-мод 0.6.1: `Fabric/foxchatkeyboard/`. Он компилируется на Java 21 без локальной установки Minecraft, а Java-тесты моделируют Fabric/Minecraft API.
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

**Важно:** данный Unity-код ищет уже существующую папку `instances/1.21.5` в `Application.persistentDataPath` или `Android/data/<packageId>/files`. Расположение **не подтверждено** в FoxCraft APK. Подлинная папка `FabricLoader.getGameDir()` должна совпадать.

## Оставшиеся препятствия

1. Связать артефакт Fabric JAR с будущей Unity-сборкой. Java-код и автоматическая сборка JAR уже в репозитории; скрипт `tools/foxcraft_stage_fabric.py` копирует проверенный JAR в `Assets/StreamingAssets/FoxChatKeyboard-bridge-0.6.1.jar`. **JAR не сохраняется в Git после каждой сборки; он доступен в GitHub Actions Artifacts.**
2. **Pojlib AAR уже успешно собран в отдельном GitHub Actions workflow.** Введён `tools/foxcraft_stage_pojlib.py`: он проверяет AAR, перед заменой сравнивает SHA-256 и по умолчанию не перезаписывает существующую библиотеку. Для APK-пайплайна Pojlib и Fabric компилируются заново в одном запуске, затем передаются в Unity. Совместимость новой AAR с Unity и устройством ещё не проверена.
3. Отдельный идентификатор `com.foxcraftvr.quest` и название приложения выставляются тестовым Unity build-method во время сборки и затем восстанавливаются в исходных настройках. Сам APK и его установку ещё необходимо проверить; отдельная иконка и подпись выпуска остаются открытыми задачами.
4. Обеспечить Unity-лицензию для облачной Android-сборки и проверить конкретные версии Unity/Android SDK.
5. Проверить совместимость системной клавиатуры с OpenXR **1.14.3**. Специальный флаг Enable System Keyboard появился в OpenXR **1.17.0**; обновлять пакет вслепую нельзя.
6. Установить APK на Quest 3 и проверить открытие оверлея, русский/английский, руки, Enter, удаление текста и закрытие.

**Сейчас работоспособного APK FoxCraft нет.** Успешная GitHub-сборка Fabric JAR и Python/Java-тесты не доказывают работу Meta-оверлея или Unity-моста на Quest 3.

## Автоматическая сборка Fabric-мода

- Workflow: `.github/workflows/foxcraft-fabric-build.yml`.
- Выходной JAR: `FoxChatKeyboard-bridge-0.6.1-fabric-1.21.5.jar` внутри артефакта `FoxChatKeyboard-Fabric-0.6.1-1.21.5`.
- Для локальной сборки: `bash Fabric/foxchatkeyboard/build.sh`, затем `bash Fabric/foxchatkeyboard/tests/run-tests.sh`.
- Для помещения JAR в Unity: `python3 tools/foxcraft_stage_fabric.py` (из корня репозитория после сборки).
- Идентификатор Fabric-мода остаётся `questchatkeyboardbridge`. **Не устанавливайте его одновременно с тестовой версией 0.5.1 в один профиль Minecraft.**
- Fabric-мод 0.6.1 **не** заполняет чат тестовым русским текстом автоматически; это сделано только в диагностической версии 0.5.1.
- В установленной FoxCraft потребуется перезапустить Minecraft, если его запуск опередил копирование Fabric JAR.

**Автоматическая сборка JAR не означает готовый APK.**

## Тестовая Android-сборка (ещё не проведена)

- Workflow: `.github/workflows/foxcraft-android-apk.yml`. При pull request он запускает только безопасные Python preflight-тесты; ресурсоёмкие задачи Pojlib, Fabric и Unity выполняются **только при ручном запуске**.
- GitHub поддерживает ручной запуск (`workflow_dispatch`) только после появления workflow в ветке по умолчанию (`master`). До этого изменения остаются в черновике PR; **сливать ветку лишь ради APK без просмотра тестов не нужно**.
- Для ручной сборки настройте в GitHub Actions Secrets активную Unity-лицензию: `UNITY_LICENSE` или `UNITY_EMAIL`, `UNITY_PASSWORD`, `UNITY_SERIAL`. Не публикуйте их в коде, issue, логах или сообщениях.
- Пайплайн собирает Java 21 Fabric-мод и Windows Pojlib AAR, скачивает созданные артефакты в текущий CI-run, валидирует и помещает их в **временный checkout**, а затем вызывает `FoxCraftAndroidBuild.BuildTestApk` через GameCI Unity Builder v4 с Unity 2022.3.62f3.
- Только при успешной Unity-сборке и проверке наличия APK публикуется GitHub Actions artifact `FoxCraft-VR-Quest3-UNTESTED-APK` (хранение 7 дней). **Это не релиз**; запуск и Meta-клавиатура на Quest 3 всё ещё требуют проверки.
- Скрипт локальной подготовки AAR: `python3 tools/foxcraft_stage_pojlib.py --source path/to/Pojlib-release.aar`. Для сознательной замены уже существующего файла укажите `--replace`; в CI это применяется исключительно во временной копии проекта.

**Для установки на Quest 3 сначала должен появиться именно файл `.apk`, не JAR и не ZIP с исходниками.**

## Безопасность

- Не публикуйте Unity-лицензию, пароли, токены или ключи подписи.
- Оригинальную QuestCraft и сохранения миров не удаляйте.
- Не пытайтесь устанавливать ZIP исходников как APK или Fabric-мод.
- FoxCraft не является официальным проектом Meta или QuestCraft; соблюдайте условия лицензий исходных компонентов.
