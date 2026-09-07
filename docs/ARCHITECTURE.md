# Устройство проекта

Заметки для тех, кто хочет разобраться в коде или что-то доработать.

## Общая идея

Приложение не поднимает VPN. Для каждого конфига оно запускает ядро (Xray-core или sing-box) как обычный
процесс с локальным HTTP-прокси и делает через этот прокси запрос к `https://api.2ip.io/`. Пришёл ответ —
конфиг рабочий, из ответа известны внешний IP, страна и ASN. Так можно проверять несколько конфигов
параллельно и не нужны права VpnService.

Бинарники ядер лежат в `app/src/main/jniLibs/arm64-v8a/` под именами `libxray.so` и `libsingbox.so`.
Android распаковывает содержимое `jniLibs` в `nativeLibraryDir` с правом на исполнение, поэтому их можно
запускать через `ProcessBuilder` даже на Android 10+, где exec из каталога данных запрещён. Для этого в
Gradle стоит `useLegacyPackaging = true`. В git бинарники не хранятся, их скачивает `scripts/fetch-cores.sh`.

## Модули

```
app/src/main/kotlin/app/vpncheck/
  core/parser/       разбор ссылок vless/vmess/trojan/ss/hysteria2 → ProxyConfig
  core/engine/       CoreRunner (процесс, свободный порт, ожидание готовности),
                     XrayConfigBuilder и SingBoxConfigBuilder (JSON для ядер)
  core/check/        ConnectivityChecker: OkHttp через локальный прокси → api.2ip.io, запасной 2ip.io
  core/network/      NetworkTypeDetector: MOBILE / WIFI, активен ли сторонний VPN
  core/provider/     ProviderResolver: ipwho.is → ip-api.com → AsnWebsiteTable
  data/subscription/ SubscriptionSource (файлы и зеркала), SubscriptionFetcher, SubscriptionParser,
                     SourceMembership, RetentionPolicy
  data/db/           Room: configs, check_results (ключ configId + networkType), providers, source_status
  data/settings/     DataStore: источники, параллельность, таймаут, прочие настройки
  data/repo/         CheckRunner (оркестрация прогона), RunOrder, RunState
  service/           CheckService: foreground-сервис с уведомлением о прогрессе
  ui/                Compose: главный экран с вкладками, карточка конфига, настройки
```

## Какое ядро для какого конфига

- hysteria2 — только sing-box, Xray его не умеет.
- shadowsocks — sing-box: Xray выбросил старые шифры вроде aes-256-cfb и chacha20-ietf, которые встречаются в подписках.
- транспорт xhttp / splithttp — только Xray.
- TLS с allowInsecure=1 — sing-box: Xray-core начиная с v26.2.6 отказывается загружать конфиг с этим параметром.
- всё остальное — Xray.

Перед запуском ядра адрес сервера резолвится системным резолвером Android, и в конфиг подставляется IP,
а SNI и Host остаются доменными. Иначе Go-резолвер внутри ядра на Android не находит DNS.

## Прогон проверки

`CheckRunner.run(mode, ids?)`:

1. Проверяется, что активная сеть совпадает с выбранной вкладкой и не включён сторонний VPN.
2. Берутся конфиги из Room (все или переданный список). Перекачивание подписок перед проверкой выключено
   по умолчанию и включается в настройках.
3. Конфиги сортируются `RunOrder`: на мобильной сети сначала белые списки, дальше по прошлому статусу
   (рабочие → непроверенные → нерабочие). На Wi‑Fi только по статусу.
4. Пул воркеров (по умолчанию 4) забирает конфиги из очереди. На каждый: `CoreRunner.start` → ожидание
   порта до 10 с → `ConnectivityChecker.check` → для рабочего `ProviderResolver` → запись результата → стоп ядра.
5. Завершение и отмена обрабатываются в `NonCancellable`, иначе после «Остановить» состояние зависало бы в Running.

Прогон живёт в `CheckService`, чтобы не прерываться при сворачивании приложения.

## Подписки и хранение

`SubscriptionFetcher` дёргает все зеркала одного файла параллельно и берёт первый валидный ответ.
Валидный — HTTP 200 и либо ссылки, либо шапка `# profile-title`. HTML-заглушка провайдера считается ошибкой.

Конфиг помнит все подписки, в которых встречается (`sourceIds`), отсюда метки «чёрные» и «белые».
`RetentionPolicy` после скачивания решает судьбу конфигов, которых в списках больше нет: если все его
подписки скачались успешно и он ни разу не работал — удалить; если работает хоть в одной сети — оставить;
если подписка не скачалась — не трогать. Конфиги из выключенных подписок удаляются.

## Релизы

Тег `v*` запускает workflow, который собирает `assembleRelease`, подписывает ключом из секретов
(`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) и публикует релиз с двумя APK:
версионным и стабильным `vpn-config-checker.apk`, на который ведёт ссылка из README. Локальная сборка без
этих переменных подписывается debug-ключом.

## Тесты

JVM-тесты в `app/src/test`: парсер ссылок, генераторы JSON для обоих ядер, парсер подписок, загрузчик
(на MockWebServer), политика хранения, порядок проверки, резолвер провайдера. `ConfigDumpTool` — не тест,
а утилита: при заданных переменных окружения выгружает JSON для всех ссылок подписки, чтобы прогнать их
на десктопных сборках ядер.
