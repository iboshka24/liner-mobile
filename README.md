# Lyra

Lyra is a small Android music player. It plays the songs already on the phone, and it can search two legal catalogs: tracks artists publish on [Audius](https://audius.co), and Creative Commons recordings from the [Internet Archive](https://archive.org). Word-synced lyrics come from [LRCLIB](https://lrclib.net) when a match exists. An `.lrc` file can be imported for anything else.

Lyra is an independent project. It does not include code, branding, or the interface of any other music player.

The interface is a dark listening screen: warm paper black, a serif for titles, and one accent taken from the album art. There is no decorative gradient.

## Install

The signed release build is `dist/lyra-0.1.0.apk` (Android 8.0 or newer).

On a computer with the phone connected:

```bash
adb install -r dist/lyra-0.1.0.apk
```

Or copy the APK to the phone and open it. Android will ask you to allow installs from that app. After Lyra opens, allow music access and notifications so the library can be read and playback can continue on the lock screen.

A GitHub release is published when a `v*` tag is pushed. The workflow file is `.github/workflows/release-apk.yml`.

## What it does

- Library from the device (`MediaStore`): songs, albums, artists.
- Playlists stored on the phone, including M3U import matched against local files.
- Queue, shuffle, repeat, play next.
- Background playback with a media notification and lock-screen controls (Media3 / ExoPlayer).
- Search on the device, on Audius, and on Internet Archive items that declare a Creative Commons license.
- Audius audio is streamed. It is written to the on-disk cache only when the artist marked the track as downloadable and the download is not gated.
- Internet Archive audio and LRCLIB responses are cached on disk. Clearing either cache is in About (tap the LYRA wordmark).
- Lyrics: enhanced LRC word times when the file has them, otherwise each line is spread across its words by length. Plain text is shown as plain text. A local `.lrc` import overrides the network result.

## Memory

The release APK is built with R8 and resource shrinking. Image requests are size-capped, RGB_565, with a 12 MB memory cache and a 32 MB artwork disk cache. ExoPlayer’s target buffer is 3 MB and it does not keep a back buffer. Notification artwork is decoded to at most 256 px.

`dumpsys meminfo dev.lyra.player` on an Android 14 emulator:

| Screen | Total PSS | Java heap | Native heap |
| --- | ---: | ---: | ---: |
| Home | 33.5 MB | 3.9 MB | 14.8 MB |
| Now playing, lyrics open, audio playing | 33.5 MB | 3.9 MB | 14.6 MB |

Graphics was reported as 0. RSS was about 156 MB. That guest had no KVM: creating a vCPU trips a host kernel fault in `kvm_arch_vcpu_create`, so the emulator ran on one software CPU. Frame times there were well above 16 ms. The seek bar and the active lyric line each run their own frame loop, so the song list is not recomposed on every tick.

Screenshots from that run are in `docs/screenshots/`.

## Build

JDK 17, Android SDK 35, and the keystore in `keystore/` (a public demo key so the CI build can be signed; replace it before any store listing).

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/android-sdk"
./gradlew test assembleRelease
```

Unit tests cover the lyrics parser, word interpolation, line seeking, M3U matching, and the cache policy: 17 tests, run for both debug and release, 0 failures. The signed APK is `dist/lyra-0.1.0.apk` (2,497,920 bytes) and also `app/build/outputs/apk/release/`.

## License

Apache License 2.0. See `LICENSE` and `NOTICE`.

Outfit and Fraunces are used under the SIL Open Font License. The license texts are in `third_party/fonts/`.

---

# Lyra

Lyra — небольшой музыкальный плеер для Android. Он играет файлы, которые уже лежат на телефоне, и умеет искать в двух легальных каталогах: треки, которые музыканты сами публикуют на [Audius](https://audius.co), и записи Creative Commons из [Internet Archive](https://archive.org). Пословные тексты запрашиваются в [LRCLIB](https://lrclib.net), если там есть совпадение. Для остальных песен можно импортировать файл `.lrc`.

Lyra — самостоятельный проект. В нём нет кода, названия и оформления чужих плееров.

Экран тёмный: тёплый чёрный фон, антиква для названий и один акцент, взятый из обложки. Декоративных градиентов нет.

## Установка

Подписанная сборка: `dist/lyra-0.1.0.apk`. Нужен Android 8.0 или новее.

С компьютера, к которому подключён телефон:

```bash
adb install -r dist/lyra-0.1.0.apk
```

Либо скопируйте APK на телефон и откройте его. Система попросит разрешить установку из этого источника. После запуска разрешите доступ к музыке и уведомления: без этого библиотека не читается, а на экране блокировки не будет кнопок плеера.

Релиз на GitHub собирается при отправке тега `v*`. Сценарий: `.github/workflows/release-apk.yml`.

## Возможности

- Библиотека телефона (`MediaStore`): песни, альбомы, исполнители.
- Плейлисты на устройстве и импорт M3U с сопоставлением с локальными файлами.
- Очередь, перемешивание, повтор, «играть следующим».
- Фоновое воспроизведение, уведомление и кнопки на экране блокировки (Media3 / ExoPlayer).
- Поиск по телефону, Audius и записям Internet Archive с лицензией Creative Commons.
- Звук Audius идёт потоком. На диск он попадает только если автор разрешил скачивание и оно не закрыто.
- Звук Internet Archive и ответы LRCLIB кэшируются. Очистка — в разделе «О программе» (нажмите слово LYRA на главном экране).
- Текст: если в LRC есть времена слов, они используются; иначе строка равномерно делится между словами по длине. Обычный текст без тайминга показывается как обычный текст. Импорт `.lrc` важнее сети.

## Память

Релизная сборка сжимается R8. Картинки запрашиваются нужного размера, в RGB_565, кэш в памяти — 12 МБ, кэш обложек на диске — 32 МБ. Буфер ExoPlayer — около 3 МБ, хвост за позицией не хранится. Обложка в уведомлении не больше 256 пикселей.

`dumpsys meminfo dev.lyra.player` на эмуляторе Android 14:

| Экран | Total PSS | Куча Java | Native heap |
| --- | ---: | ---: | ---: |
| Главный | 33,5 МБ | 3,9 МБ | 14,8 МБ |
| Плеер с текстом, звук идёт | 33,5 МБ | 3,9 МБ | 14,6 МБ |

Graphics в отчёте — 0. RSS около 156 МБ. У этого гостя не было KVM: создание vCPU падает в ядре хоста внутри `kvm_arch_vcpu_create`, поэтому эмулятор шёл на одном программном процессоре. Кадры там заметно длиннее 16 мс. Полоса перемотки и активная строка текста живут в своих циклах кадров, список песен от этого не пересобирается.

Снимки экрана с этого прогона лежат в `docs/screenshots/`.

## Сборка

Нужны JDK 17, Android SDK 35 и ключ из `keystore/`. Это публичный демонстрационный ключ, чтобы сборка в CI была подписанной. Перед публикацией в магазин его нужно заменить.

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/android-sdk"
./gradlew test assembleRelease
```

Тесты проверяют разбор текста, интерполяцию слов, поиск текущей строки, разбор M3U и правила кэша: 17 тестов, и для debug, и для release, без падений. Подписанный APK — `dist/lyra-0.1.0.apk` (2 497 920 байт), копия также в `app/build/outputs/apk/release/`.

## Лицензия

Apache License 2.0. Файлы `LICENSE` и `NOTICE`.

Шрифты Outfit и Fraunces распространяются по SIL Open Font License. Тексты лицензий: `third_party/fonts/`.
