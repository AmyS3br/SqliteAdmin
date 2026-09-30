# Sqlite Admin

A free, offline SQLite database manager for Android.

No ads. No in-app purchases. No account. Open a `.db` file, edit it, run SQL, export, save — done.
<img width="360" height="800" alt="001" src="https://github.com/user-attachments/assets/31449d82-74bc-4273-a7c7-c4943016b1ac" />

---

## Why this exists

Android has plenty of half-finished or paywalled SQLite tools. The goal here is a **practical admin app**: the things people usually unlock with money (schema changes, dumps, indexes, PRAGMAs) are included, without clutter.

Think of it as a simplified “phpMyAdmin-style” workflow, but for **SQLite files on the device**.

---

## Features

### Databases & files
- Open and create SQLite databases (`.db`, `.sqlite`, `.sqlite3`) via the system file picker (Storage Access Framework)
- Recent databases on the home screen
- Explicit **Save** back to the original file (plus optional auto-save)
- Unsaved-changes prompts when leaving or switching databases

### Tables & schema
- Browse tables; create, rename, and drop tables
- Add, rename, drop, and **modify columns** (including UNIQUE) even when the table already has data
- Primary key, autoincrement, NOT NULL, UNIQUE, and DEFAULT values
- Index management

### Data
- View and edit rows with search, sort, and pagination
- Insert rows with default-value controls and type-based value presets
- BLOB preview (size + hex)

### SQL console
- Run arbitrary SQL
- Query history and **named favorites**
- **EXPLAIN QUERY PLAN**
- Schema/keyword suggestions via **long-press** on the editor
- Load `.sql` files into the editor
- Copy results / errors

### Import / export & maintenance
- Export table as **CSV** or **JSON**
- Full database **SQL dump** (and re-run dumps in the console)
- Import CSV into a table (or create a table from CSV)
- VACUUM, ANALYZE, integrity check
- Attach / detach another database
- Common **PRAGMA** viewer/editor

### App
- Languages: English, Portuguese, Spanish, German, French, Russian, Chinese, Japanese, Hindi
- Theme: system / light / dark
- Configurable page size, recent list size, confirmations, query-history limit
- About screen with feature overview

---

## Requirements

- Android 7.0+ (API 24)
- No root required for normal use (open files you can access through the file picker)

---

<img width="360" height="800" alt="002" src="https://github.com/user-attachments/assets/be8b2102-4a61-4c18-bc20-314363cdf06c" />
<img width="360" height="800" alt="003" src="https://github.com/user-attachments/assets/a5c0e455-7f73-4b17-b5d5-9761317063fc" />
<img width="360" height="800" alt="004" src="https://github.com/user-attachments/assets/d4501cef-7b2e-4ad7-8052-307fc804c9e1" />
<img width="360" height="800" alt="005" src="https://github.com/user-attachments/assets/86b1b471-d774-4947-a333-a6ab349a73d9" />
<img width="360" height="800" alt="006" src="https://github.com/user-attachments/assets/fe3c972b-49bd-42b3-b8c8-a2a55aa25bd8" />
<img width="360" height="800" alt="011" src="https://github.com/user-attachments/assets/b28b982c-71e5-43f1-aa1a-214b4d606c5d" />
<img width="360" height="800" alt="007" src="https://github.com/user-attachments/assets/170cd468-69ae-4b07-bfdc-3fd6f3e7c1ea" />
<img width="360" height="800" alt="008" src="https://github.com/user-attachments/assets/27d52ae5-4475-4f20-9cb9-46937c0e54b7" />
<img width="360" height="800" alt="009" src="https://github.com/user-attachments/assets/d9976f6f-f8c4-4453-9ded-0f02ee99ba6a" />
<img width="360" height="800" alt="010" src="https://github.com/user-attachments/assets/c9bff71f-ca51-4944-b61c-46947e74feda" />

---

## Building

### Debug

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

### Release (signed)

1. Generate a keystore **once** (project root):

```bash
keytool -genkey -v -keystore sqliteadmin-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias sqliteadmin
```

2. Copy and edit signing config:

```bash
cp keystore.properties.example keystore.properties
```

Set `storePassword`, `keyPassword`, and keep:

```properties
storeFile=sqliteadmin-release.jks
keyAlias=sqliteadmin
```

3. Build:

```bash
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`

`*.jks` and `keystore.properties` are gitignored — **back them up**; losing the keystore means you cannot sign updates with the same certificate.

---

## License

This project is released under the **MIT License** (see [LICENSE](LICENSE)).

You may use, copy, modify, merge, publish, distribute, and sublicense the software, subject to the license terms.

Copyright notices also appear in the app’s About screen.

---

## Author

**AmyS3** — [AmyS3.br@gmail.com](mailto:AmyS3.br@gmail.com)

Built with assistance from Grok (xAI).

---

## Contributing / feedback

Bug reports and feature ideas are welcome (issues or email).  
Please include Android version, what you tried, and any error text from the SQL console when relevant.
