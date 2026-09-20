# ntJobsApp — README

> User guide index / Indice delle guide utente.

**English version below — scroll down.**

---

## Italiano — Cos'è una ntJobsApp e a cosa serve

Una **ntJobsApp** è una **micro-applicazione batch** guidata da un file `.ini`.

- **Ingresso:** un file `.ini` passato come parametro sulla riga di comando, es. `mia_app.py lavoro.ini`
- **Uscita:** un file `.end` con lo **stesso nome** del file `.ini` (es. `lavoro.end`), in formato `.ini`, con i risultati di ogni job + un **codice di uscita**:
  - `0` = tutto OK
  - `1` = errore prima dell'esecuzione dei job (file `.ini` illeggibile / non valido)
  - `2` = uno o più job terminati con errore

A orchestrare tutto c'è la classe **`acJobsApp`** (file `acJobsApp.py`, file unico senza dipendenze esterne), usata tramite l'istanza globale **`jData`**. Lo schema di ogni app è sempre lo stesso:

1. `jData.Start()` — legge e valida il file `.ini`
2. `jData.Run(cbCommands)` — esegue in sequenza ogni sezione-job chiamando la tua funzione `cbCommands`
3. `jData.End(sResult)` — scrive il file `.end`, scrive nel log, imposta il codice di uscita

Ogni sezione del file `.ini` (tranne `[CONFIG]`) è un **job**: contiene un `COMMAND=` + parametri `PARAM.*` / file `FILE.*`. La tua funzione `cbCommands` riceve il dizionario del job, fa il lavoro, e chiama `jData.Return(...)` per registrare esito, messaggio e file restituiti (`RETURN.TYPE`, `RETURN.VALUE`, `RETURN.FILE.*`, `TS.START/TS.END`).

La sezione `[CONFIG]` contiene le impostazioni globali (`TYPE`, `NAME`, `EXIT`, `LOG`) e le **variabili `$NOME`** riutilizzabili in tutti i job (es. `FILE.IN=$BASE_DIR\input.csv`).

**Non devi conoscere i dettagli interni:** ti basta scrivere il file `.ini` e la funzione `cbCommands`.

### Dove andare adesso (manuali utente)

| File | Contenuto |
|---|---|
| [acJobsApp_man_it.md](acJobsApp_man_it.md) | Manuale d'uso italiano di `acJobsApp`: file `.ini`/`.end`, ciclo di vita, variabili `$`, esempi |
| [acJobsApp_man_en.md](acJobsApp_man_en.md) | Same user manual in English |
| [aiSys_man_it.md](aiSys_man_it.md) | Manuale d'uso italiano di `aiSys`: timestamp, `Expand`, file CSV/INI, stringhe, log |
| [aiSys_man_en.md](aiSys_man_en.md) | Same `aiSys` user manual in English |

### Esempio minimo (30 secondi)

`lavoro.ini`:
```ini
[CONFIG]
TYPE=NTJOBS.APP.1.0
NAME=DEMO
EXIT=TRUE
LOG=demo.log

[JOB1]
COMMAND=HELLO
PARAM.NAME=Mario
```

`demo_app.py`:
```python
from acJobsApp import acJobsApp
jData = acJobsApp()

def cbCommands(dJob):
    if dJob.get("COMMAND") == "HELLO":
        sName = dJob.get("PARAM.NAME", "mondo")
        return jData.Return("", f"Ciao {sName}!")
    return jData.Return(f"Comando sconosciuto: {dJob.get('COMMAND')}")

if __name__ == "__main__":
    sResult = jData.Start()
    if sResult != "":
        jData.End(sResult)
    else:
        jData.End(jData.Run(cbCommands))
```

Esegui: `python demo_app.py lavoro.ini` → produce `lavoro.end` + `demo.log`, exit code `0/1/2`.

---

## English — What is an ntJobsApp and what is it for

An **ntJobsApp** is a **batch micro-application** driven by an `.ini` file.

- **Input:** an `.ini` file passed as a command-line parameter, e.g. `my_app.py work.ini`
- **Output:** an `.end` file with the **same name** as the `.ini` file (e.g. `work.end`), in `.ini` format, with per-job results + an **exit code**:
  - `0` = all OK
  - `1` = error before job execution (unreadable / invalid `.ini` file)
  - `2` = one or more jobs ended with an error

Everything is orchestrated by the **`acJobsApp`** class (single self-contained `acJobsApp.py` file), used through the global instance **`jData`**. Every app follows the same pattern:

1. `jData.Start()` — reads and validates the `.ini` file
2. `jData.Run(cbCommands)` — runs each job section in sequence by calling your `cbCommands` function
3. `jData.End(sResult)` — writes the `.end` file, writes the log, sets the exit code

Each section of the `.ini` file (except `[CONFIG]`) is a **job**: it holds a `COMMAND=` plus `PARAM.*` parameters / `FILE.*` files. Your `cbCommands` function receives the job dictionary, does the work, and calls `jData.Return(...)` to record outcome, message and returned files (`RETURN.TYPE`, `RETURN.VALUE`, `RETURN.FILE.*`, `TS.START/TS.END`).

The `[CONFIG]` section holds global settings (`TYPE`, `NAME`, `EXIT`, `LOG`) and reusable **`$NAME` variables** usable in every job (e.g. `FILE.IN=$BASE_DIR\input.csv`).

**You don't need to know the internals:** just write the `.ini` file and the `cbCommands` function.

### Where to go next (user manuals)

| File | Contents |
|---|---|
| [acJobsApp_man_it.md](acJobsApp_man_it.md) | Manuale d'uso italiano di `acJobsApp` |
| [acJobsApp_man_en.md](acJobsApp_man_en.md) | English user manual for `acJobsApp`: `.ini`/`.end` files, lifecycle, `$` variables, examples |
| [aiSys_man_it.md](aiSys_man_it.md) | Manuale d'uso italiano di `aiSys` |
| [aiSys_man_en.md](aiSys_man_en.md) | English user manual for `aiSys`: timestamps, `Expand`, CSV/INI files, strings, logging |

### Minimal example (30 seconds)

`work.ini`:
```ini
[CONFIG]
TYPE=NTJOBS.APP.1.0
NAME=DEMO
EXIT=TRUE
LOG=demo.log

[JOB1]
COMMAND=HELLO
PARAM.NAME=Mario
```

`demo_app.py`: same as the Italian example above — `Start()` → `Run(cbCommands)` → `End(sResult)`.

Run: `python demo_app.py work.ini` → produces `work.end` + `demo.log`, exit code `0/1/2`.
