# Сторонние компоненты · Third-party notices

Приложение распространяется под GPL-3.0-or-later, см. [LICENSE](LICENSE). Внутри APK лежат и при работе
используются следующие сторонние компоненты.

## Xray-core

- Версия в релизах: v26.3.27, официальная сборка `Xray-android-arm64-v8a.zip`, файл `xray` → `libxray.so`.
- Лицензия: Mozilla Public License 2.0.
- Исходный код этой версии: https://github.com/XTLS/Xray-core/tree/v26.3.27
- Проект: https://github.com/XTLS/Xray-core

Запускается как отдельный процесс, в код приложения не встраивается и не модифицируется.

## sing-box

- Версия в релизах: v1.14.0, официальная сборка `sing-box-1.14.0-android-arm64.tar.gz`, файл `sing-box` → `libsingbox.so`.
- Лицензия: GNU General Public License v3.0 or later. Copyright (C) 2022 by nekohasekai.
  Дополнительное условие автора: производные работы не могут использовать имя sing-box или подразумевать связь с ним без согласия.
- Исходный код этой версии: https://github.com/SagerNet/sing-box/tree/v1.14.0
- Проект: https://github.com/SagerNet/sing-box

Запускается как отдельный процесс, в код приложения не встраивается и не модифицируется. Это приложение
не является частью проекта sing-box и не связано с его авторами.

## Списки конфигов

Подписки скачиваются во время работы из репозитория https://github.com/igareck/vpn-configs-for-russia
(GPL-3.0). Приложение их не хранит в своём коде и не перераспространяет; авторские права на списки и на
серверы принадлежат их владельцам.

## Сетевые сервисы

- https://2ip.io и https://api.2ip.io — проверка доступности и определение внешнего IP.
- https://ipwho.is — данные о провайдере по IP.
- https://ip-api.com — данные о провайдере по IP, бесплатный тариф только для некоммерческого использования.

## Библиотеки

Android Jetpack (Compose, Room, DataStore, Lifecycle), Kotlin и kotlinx.coroutines, OkHttp — Apache License 2.0.
Material Icons — Apache License 2.0. Их тексты лицензий входят в состав соответствующих артефактов.

---

The app is licensed under GPL-3.0-or-later, see [LICENSE](LICENSE). It bundles unmodified official builds of
Xray-core v26.3.27 (MPL-2.0, source: https://github.com/XTLS/Xray-core/tree/v26.3.27) and sing-box v1.14.0
(GPL-3.0-or-later, Copyright (C) 2022 by nekohasekai, source: https://github.com/SagerNet/sing-box/tree/v1.14.0),
both executed as separate processes. This app is not affiliated with the sing-box project. Config lists are
downloaded at runtime from https://github.com/igareck/vpn-configs-for-russia and are not redistributed.
