# Nothing Blur Panels

Форк [NothingBlurShortcuts](https://github.com/Ak1o-T/NothingBlurShortcuts) для Nothing OS 5 и
Android 17. Дополняет исходный модуль поддержкой раздельных панелей. Использует фоновое
размытие SystemUI и добавляет полупрозрачную подложку с тонким кантом на обычные карточки
уведомлений и плитки быстрых настроек. Карточки с цветом приложения сохраняют свой цвет.

Хуки сверены с SystemUI из `Asteroids C5.0-261023-2146`. Для эффекта требуется, чтобы на
устройстве было включено системное межоконное размытие. Проверка на телефоне ещё не проводилась.

При раскрытии и сворачивании панели быстрых настроек плитки плавно размываются у её
движущегося нижнего края.

1. Установите `NothingBlurPanels-0.1.5.apk` как приложение.
2. В LSPosed/Vector включите **Nothing Blur Panels** и отметьте **System UI**
   (`com.android.systemui`). Для SukiSU нужна рабочая связка Zygisk и LSPosed/Vector.
3. Если включён оригинальный **NothingBlurShortcuts** или другой модуль, который меняет
   стекло в SystemUI, отключите его для проверки форка и перезагрузите телефон.
4. Проверьте оба свайпа отдельно. Для отката выключите форк в LSPosed и перезагрузитесь.

Для сборки на Windows нужны JDK 17+, Android SDK Platform 37.0 и Build Tools 36.0.0.
Команда `./build.ps1` собирает `bin/NothingBlurPanels.apk`. Локальный ключ хранится в
`.build/local-debug.p12`. Сборка на Linux использует исходный `build.sh` и путь
`BUILD_ENV` к инструментам, включая Android 37 `android.jar`.

---

## NothingBlurShortcuts 🪟🔒✨

Ниже приведена документация оригинального модуля.

[![Nothing OS](https://img.shields.io/badge/Nothing%20OS-5.0-black?style=for-the-badge&logo=android)](https://nothing.tech)
[![Android](https://img.shields.io/badge/Android-17-green?style=for-the-badge&logo=android)](https://nothing.tech)
[![LSPosed](https://img.shields.io/badge/LSPosed-Supported-5C6BC0?style=for-the-badge)](https://github.com/LSPosed/LSPosed)
[![GitHub Downloads](https://img.shields.io/github/downloads/Ak1o-T/NothingBlurShortcuts/total?style=for-the-badge)](https://github.com/Ak1o-T/NothingBlurShortcuts/releases/latest)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue?style=for-the-badge)](LICENSE)

An **Xposed / LSPosed** module for **Nothing OS 5.0 (Android 17)** that brings authentic **Frosted Glass Wallpaper Blur** to the lock screen shortcut buttons, lock screen notifications (including group summaries and shelf icon collages), and floating heads-up alerts — matching official Nothing OS 5.0 widgets 1:1, while preserving a clean, unblurred stock system background for the notification shade.

[English](#english) | [Русский](#русский)

---

# English

## 🔗 Recommended Companion: NothingBlurIcons

> [!TIP]
> Complete your Nothing OS frosted glass aesthetic with [**NothingBlurIcons**](https://github.com/Ak1o-T/NothingBlurIcons)!
> While **NothingBlurShortcuts** brings real-time wallpaper blur to lockscreen shortcut buttons, lockscreen notifications, and floating heads-up alerts, **NothingBlurIcons** extends this authentic Nothing OS frosted glass blur to all home screen and folder app icons. Together, they create a 100% unified Nothing OS visual experience.

## 📸 Screenshots

### 🔒 Lockscreen (Dark vs Light)
| Lockscreen (Dark Mode) | Lockscreen (Light Mode) |
|:---:|:---:|
| <img src="screenshots/lockscreen_dark.png" width="300" alt="Lockscreen Dark Mode"/> | <img src="screenshots/lockscreen_light.png" width="300" alt="Lockscreen Light Mode"/> |

### 🚀 Floating Heads-Up Alerts (Dark vs Light)
| Floating Heads-Up (Dark Mode) | Floating Heads-Up (Light Mode) |
|:---:|:---:|
| <img src="screenshots/heads_up_dark.png" width="300" alt="Floating Heads-Up Dark Mode"/> | <img src="screenshots/heads_up_light.png" width="300" alt="Floating Heads-Up Light Mode"/> |

### 🔊 Volume Dialog Menu (Dark vs Light)
| Volume Dialog (Dark Mode) | Volume Dialog (Light Mode) |
|:---:|:---:|
| <img src="screenshots/volume_dialog_dark.png" width="300" alt="Volume Dialog Dark Mode"/> | <img src="screenshots/volume_dialog_light.png" width="300" alt="Volume Dialog Light Mode"/> |

### 🛡️ Notification Shade Menu (Stock System Background, Zero Blur)
| Notification Shade (Dark Mode) | Notification Shade (Light Mode) |
|:---:|:---:|
| <img src="screenshots/shade_dark.png" width="300" alt="Notification Shade Dark Mode"/> | <img src="screenshots/shade_light.png" width="300" alt="Notification Shade Light Mode"/> |

---

## 💡 Concept & Architecture

On stock Nothing OS:
- **Lockscreen Widgets** (Clock, Weather, Media Player, Quick Actions) feature authentic Frosted Glass wallpaper blur with a delicate specular ring and subtle tint.
- **Lockscreen Shortcut Buttons** (Bottom left & bottom right quick affordances: Flashlight, Camera, Wallet, Controls) by default only have a static, flat, semi-opaque circular background without any wallpaper blur.
- **Lockscreen Notifications** are rendered with a flat background rather than the signature Nothing OS frosted glass effect.

### Why a KernelSU / RRO Overlay is NOT enough:
A pure Magisk or KernelSU overlay (Runtime Resource Overlay) can only modify static drawables, colors, or XML vectors. True dynamic wallpaper blur in Android requires:
1. Real-time access to the lockscreen wallpaper backdrop provider (`FrostedBackdropProvider` / `NTKeyguardFrostedBackdrop`).
2. Coordinate projection (`updateOffset(targetX, targetY)`) tracking view positions across screen coordinates.
3. Live shader rendering through Nothing OS's proprietary `FrostedBackdropDrawable` directly on the view canvas.

### How NothingBlurShortcuts Works:
- **Target Process:** `com.android.systemui`
- **Lockscreen Shortcuts:** Injects `FrostedBackdropDrawable` under `@id/start_button` and `@id/end_button` (`LaunchableImageView`), perfectly matching widget tone, corner curves, and 1px specular contour in both Dark and Light themes.
- **Lockscreen Notifications & Shelf:** Replaces the notification card background with Nothing OS's native `FrostedBackdropDrawable`, supporting single notifications, grouped notification summaries (e.g. 4PDA), and the bottom shelf icon collage pill (`NotificationShelf`).
- **Floating Heads-Up Notifications (HUN):** Direct HardwareCanvas frosted glass rendering for 120 FPS motion without SurfaceFlinger hole-punch lag.
- **Notification Shade Menu (Zero Blur):** When expanding the notification shade menu (either unlocked or from the lockscreen), notifications seamlessly render with 100% stock system backgrounds without blur.
- **Smooth Bouncer & Easing:** Synchronized with Compose `PredictiveBack` cubic-bezier easing curve (`0.1f, 0.1f, 0.0f, 1.0f`) for smooth fade-out during PIN/pattern entry.

---

## ✨ Features

- 🪟 **Authentic Frosted Glass Blur:** Leverages Nothing OS's native lockscreen backdrop engine for 100% parity with stock lockscreen widgets.
- 🔘 **Lockscreen Shortcuts Parity:** Renders bottom affordance buttons with identical glass plate tone and subtle 1px border.
- 📦 **Grouped Notifications & Shelf Support:** Renders single cards, multi-row notification groups, and shelf pill collages.
- 🚀 **120 FPS Floating Alerts (HUN):** Hardware-accelerated heads-up notifications that smoothly track entrance, exit, and swipe gestures.
- 🛡️ **Stock Shade Menu Isolation:** Keeps notifications in the pulled-down shade menu 100% stock solid background for maximum legibility.
- 🌓 **Dynamic Theme Sync:** Instantly adapts to Dark Mode / Light Mode and wallpaper color shifts.
- 🔒 **Seamless Bouncer Transition:** Fades out gracefully when unlocking or invoking the PIN/pattern bouncer.

---

## 📱 Compatibility

Requires a Nothing smartphone running **Nothing OS 5.0 (Android 17)**:

- **Nothing Phone (3)** (`A024`) — Tested and confirmed working ✅

> [!WARNING]
> **Important Notice:**
> This module is designed **strictly for Nothing OS smartphones**. It relies directly on Nothing OS's proprietary system classes (`FrostedBackdropProvider`, `FrostedBackdropDrawable`, etc.).
> 
> Testing was conducted exclusively on **Nothing Phone (3)**. Other models have not been verified. **Install on any other model at your own risk!**

---

## 🛠️ Installation

1. Download or build `NothingBlurShortcuts.apk`.
2. Install `NothingBlurShortcuts.apk` on your device.
3. Open **LSPosed Manager** -> **Modules** -> Enable **NothingBlurShortcuts**.
4. Ensure the module scope has **System UI** (`com.android.systemui`) checked.
5. Restart SystemUI:
   ```bash
   adb shell su -c killall -9 com.android.systemui
   ```
   (or restart your device).

---

## 🏗️ Building from Source

```bash
./build.sh
```
Output signed APK: `bin/NothingBlurShortcuts.apk`.

---

# Русский

## 🔗 Рекомендуемое дополнение: NothingBlurIcons

> [!TIP]
> Создайте полноценный единый стиль матового стекла Nothing OS с помощью [**NothingBlurIcons**](https://github.com/Ak1o-T/NothingBlurIcons)!
> В то время как **NothingBlurShortcuts** добавляет аутентичное размытие обоев для кнопок быстрого доступа и уведомлений на экране блокировки, **NothingBlurIcons** расширяет этот фирменный блюр Nothing OS на все иконки приложений рабочего стола и внутри папок. Вместе они идеально дополняют друг друга.

## 📸 Скриншоты

### 🔒 Экран блокировки (Тёмная vs Светлая тема)
| Экран блокировки (Тёмная тема) | Экран блокировки (Светлая тема) |
|:---:|:---:|
| <img src="screenshots/lockscreen_dark.png" width="300" alt="Экран блокировки — Тёмная тема"/> | <img src="screenshots/lockscreen_light.png" width="300" alt="Экран блокировки — Светлая тема"/> |

### 🚀 Всплывающие уведомления Heads-Up (Тёмная vs Светлая тема)
| Всплывающее уведомление (Тёмная тема) | Всплывающее уведомление (Светлая тема) |
|:---:|:---:|
| <img src="screenshots/heads_up_dark.png" width="300" alt="Всплывающее уведомление — Тёмная тема"/> | <img src="screenshots/heads_up_light.png" width="300" alt="Всплывающее уведомление — Светлая тема"/> |

### 🔊 Меню громкости Volume Dialog (Тёмная vs Светлая тема)
| Меню громкости (Тёмная тема) | Меню громкости (Светлая тема) |
|:---:|:---:|
| <img src="screenshots/volume_dialog_dark.png" width="300" alt="Меню громкости — Тёмная тема"/> | <img src="screenshots/volume_dialog_light.png" width="300" alt="Меню громкости — Светлая тема"/> |

### 🛡️ Меню шторки уведомлений (Стоковый системный фон, без блюра)
| Меню шторки (Тёмная тема) | Меню шторки (Светлая тема) |
|:---:|:---:|
| <img src="screenshots/shade_dark.png" width="300" alt="Меню уведомлений — Тёмная тема"/> | <img src="screenshots/shade_light.png" width="300" alt="Меню уведомлений — Светлая тема"/> |

---

## 💡 Концепция и архитектура

В стандартной Nothing OS:
- **Виджеты экрана блокировки** (часы, погода, плеер, быстрые действия) выполнены в фирменном стиле «матового стекла» (Frosted Glass) с живым аппаратным размытием обоев и тонким контуром.
- **Нижние ярлыки** (кнопки в нижних углах: камера, фонарик, кошелек и т.д.) по умолчанию отображаются с плоским серым кругом без какого-либо размытия.
- **Уведомления на экране блокировки** имеют стандартный фон без матового стекла.

### Почему чистый модуль KernelSU / Overlay не подходит:
Через RRO (Runtime Resource Overlay) в KernelSU можно подменять только статичные ресурсы (цвета, векторные XML). Настоящее динамическое размытие обоев в Android требует:
1. Прямого доступа к системному поставщику подложки обоев (`FrostedBackdropProvider` / `NTKeyguardFrostedBackdrop`).
2. Динамической проекции экранных координат (`updateOffset(targetX, targetY)`).
3. Отрисовки нативного шейдера Nothing OS `FrostedBackdropDrawable` непосредственно на холсте View.

### Как работает LSPosed-модуль:
- **Целевой процесс:** `com.android.systemui` (SystemUI).
- **Нижние кнопки быстрого доступа:** Модуль внедряет `FrostedBackdropDrawable` под `@id/start_button` и `@id/end_button` (`LaunchableImageView`), добиваясь 100% совпадения тона матового стекла и контура с виджетами как в тёмной, так и в светлой теме.
- **Уведомления экрана блокировки:** Одиночные карточки, групповые уведомления (например, группа 4PDA с несколькими сообщениями) и плашка-коллаж иконок (`NotificationShelf`) отрисовываются с аутентичным Nothing Frosted Glass блюром.
- **Всплывающие уведомления (Heads-Up):** Размытие рендерится аппаратно прямо на холсте для плавной анимации 120 FPS без лагов SurfaceFlinger.
- **Меню шторки уведомлений (без блюра):** При раскрытии шторки (как с экрана блокировки, так и на разблокированном устройстве) карточки уведомлений сохраняют 100% стоковый системный фон без размытия, гарантируя максимальную читаемость.
- **Плавный переход в Bouncer:** Поддержка фирменной кривой интерполяции Compose `PredictiveBack` (`0.1f, 0.1f, 0.0f, 1.0f`) для бесшовного затухания размытия при вводе пароля/PIN-кода.

---

## ✨ Возможности

- 🪟 **Настоящий Nothing Frosted Glass Blur:** Использование нативного движка обоев Nothing OS для идеальной гармонии с виджетами.
- 🔘 **Стилизация нижних кнопок:** Матовое стекло и аккуратный контур 1px для кнопок быстрого доступа.
- 📦 **Поддержка групп и коллажа иконок:** Блюр аккуратно обрезается по форме карточек и пилюли полочки `NotificationShelf`.
- 🚀 **120 FPS всплывающие уведомления (HUN):** Плавное отслеживание появления, скрытия и свайпов.
- 🛡️ **Изоляция шторки уведомлений:** Внутри раскрытого меню уведомлений отображается чистый стоковый фон без размытия.
- 🌓 **Автоматическая смена тем:** Моментальная синхронизация с тёмной и светлой темами.
- 🔒 **Гладкий переход к разблокировке:** Красивое угасание при переходе к окну ввода PIN-кода.

---

## 📱 Совместимость с устройствами

Требуется смартфон Nothing с оболочкой **Nothing OS 5.0 (Android 17)**:

- **Nothing Phone (3)** (`A024`) — Протестировано и подтверждено ✅

> [!WARNING]
> **Важное предупреждение:**
> Этот модуль предназначен **исключительно для смартфонов Nothing**. Он напрямую взаимодействует с фирменными системными классами Nothing OS (`FrostedBackdropProvider`, `FrostedBackdropDrawable` и др.).
> 
> Тестирование проводилось исключительно на **Nothing Phone (3)**. Работоспособность на других моделях не проверялась. **Устанавливайте на любые другие модели исключительно на свой страх и риск!**

---

## 🛠️ Установка

1. Скачайте или соберите `NothingBlurShortcuts.apk`.
2. Установите APK на устройство.
3. Откройте **LSPosed Manager** -> **Модули** -> Включите **NothingBlurShortcuts**.
4. Убедитесь, что в области действия выбран **System UI** (`com.android.systemui`).
5. Перезапустите SystemUI:
   ```bash
   adb shell su -c killall -9 com.android.systemui
   ```
   (или перезагрузите телефон).

---

## 🏗️ Сборка

```bash
./build.sh
```
Готовый подписанный APK формируется по пути: `bin/NothingBlurShortcuts.apk`.
