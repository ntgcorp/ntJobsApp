# acJobsApp — Manuale d'uso (Italiano)

> Guida per **utenti**: come usare una ntJobsApp e come scriverne una. Non descrive il codice interno della libreria, solo ciò che serve per usarla.

## 1. Cos'è e a cosa serve

Una **ntJobsApp** è un programma batch che:

1. legge un file **`.ini`** passato come parametro (es. `python mia_app.py lavoro.ini`),
2. esegue uno o più **job** (uno per sezione del file `.ini`),
3. scrive un file **`.end`** con lo stesso nome del file `.ini` e il resoconto di ogni job,
4. termina con un **codice di uscita**: `0` = OK, `1` = errore prima dei job, `2` = errore in uno o più job.

La classe **`acJobsApp`** (file `acJobsApp.py`) fa da orchestratore. Tu la usi tramite la variabile globale **`jData`**.

## 2. Il ciclo di vita (sempre uguale)

```python
from acJobsApp import acJobsApp
jData = acJobsApp()

def cbCommands(dJob):
    # dJob = dizionario del job corrente (chiavi MAIUSCOLE)
    # ... fai il tuo lavoro ...
    return jData.Return("", "fatto!")   # "" = nessun errore

if __name__ == "__main__":
    sResult = jData.Start()             # 1. legge il file .ini
    if sResult != "":
        jData.End(sResult)              # errore iniziale → scrive .end e esce con codice 1
    else:
        jData.End(jData.Run(cbCommands))  # 2. esegue i job, 3. scrive .end (codice 0 o 2)
```

- **`Start()`** — legge il primo parametro (il file `.ini`), lo valida, espande le variabili `$`, verifica i file richiesti, avvia il log. Ritorna `""` se tutto bene, altrimenti un messaggio di errore.
- **`Run(cbCommands)`** — per ogni sezione (tranne `[CONFIG]`) copia il job in `jData.dictJob`, legge `COMMAND` e chiama `cbCommands(dizionario)`. Se `EXIT=TRUE` in `[CONFIG]`, al primo errore si ferma.
- **`End(sResult)`** — aggiunge timbri ed esito alla sezione `[CONFIG]`, salva il file `.end`, scrive nel log e imposta l'exit code (`sys.exit(1/2)` in caso di errore).
- **`Return(sResult, sValue="", dictFiles=None)`** — da chiamare **dentro `cbCommands`** per registrare l'esito: `sResult=""` = OK, altrimenti errore; `sValue` = messaggio; `dictFiles={"ID": "percorso"}` = file restituiti (verificati: devono esistere, viene tenuto solo il nome file).

## 3. Il file `.ini` di ingresso

```ini
[CONFIG]
TYPE=NTJOBS.APP.1.0
NAME=MIA_APP
EXIT=TRUE
LOG=mia_app.log
BASE_DIR=C:\dati

[JOB1]
COMMAND=STAMPA
FILE.IN=$BASE_DIR\input.csv
PARAM.SALUTO=Ciao

[JOB2]
COMMAND=ELABORA
FILE.IN=$BASE_DIR\input.csv
FILE.OUT.risultato=output.csv
PARAM.OPZIONE=veloce
```

### 3.1 Sezione `[CONFIG]` (obbligatoria)

| Chiave | Significato |
|---|---|
| `TYPE` | Deve iniziare con `NTJOBS.APP.` (es. `NTJOBS.APP.1.0`) |
| `NAME` | Nome applicazione (obbligatorio, non vuoto) |
| `EXIT` | `TRUE` = fermati al primo job in errore; altro valore = continua con gli altri job |
| `LOG` | Nome del file di log. Se manca, `<nome_programma>.log` |
| altre chiavi | Variabili `$NOME` liberamente definibili (vedi §4) |

### 3.2 Sezioni job (`[JOB1]`, `[FATTURE]`, … — nome libero)

| Chiave | Significato |
|---|---|
| `COMMAND` | Obbligatorio. Nome dell'azione; la tua `cbCommands` decide cosa fare in base a questo valore |
| `FILE.*` | File in ingresso (es. `FILE.IN`, `FILE.ID1`). Devono **esistere nella cartella di lancio** (si confronta il solo nome file). **Eccezione:** `FILE.OUT.*` sono file di output e **non** devono pre-esistere |
| `FILE.OUT.*` | File prodotti dal job (verificati in `Return`, non in `Start`) |
| `PARAM.*` | Parametri liberi di tipo testo |

Chiavi **riservate** (non usarle in ingresso, le scrive il sistema): `TS.START`, `TS.END`, `RETURN.TYPE`, `RETURN.VALUE`, `RETURN.FILE.*`.

Note: sezioni e chiavi vengono convertite in **MAIUSCOLO** in lettura; i valori restano invariati. Le variabili d'ambiente `NTJ_USER` e `NTJ_USERG` (separate da virgola) sono disponibili come `jData.sUser` / `jData.asUsg`.

## 4. Le variabili `$NOME` (espansione)

Definisci una variabile in `[CONFIG]` e usala con `$NOME` in qualunque job:

```ini
[CONFIG]
BASE_DIR=C:\dati
FILE_COMUNE=$BASE_DIR\comune.csv
```

- `$BASE_DIR` → valore della chiave `BASE_DIR` di `[CONFIG]` (maiuscole/minuscole indifferenti: `$base_dir` = `$BASE_DIR`).
- Se la variabile **non esiste** in `[CONFIG]`, resta scritta così com'è (`$INESISTENTE` non dà errore, ma se è un `FILE.*` fallirà la verifica "file non presente").
- Le variabili di `[CONFIG]` possono richiamarsi tra loro fino a **2 livelli** (es. `A=$B`, `B=$C` si risolve).
- I riferimenti a chiavi della **stessa sezione job** non vengono espansi: solo `[CONFIG]` alimenta `$NOME`. Le sezioni job hanno un solo passaggio di espansione.

Variabili speciali:

- `$ENV.NOME` → variabile d'ambiente `NOME` (stringa vuota se manca). Es. `$ENV.HOME`.
- `$SYS.NOME` → `OS` (WINDOWS/LINUX), `OS2`, `USER`, `COMPUTER`, `CD` (cartella corrente), `TEMP`, `YYYYMMDD`, `NOW`. Nome sconosciuto → `NOTFOUND`.

Sequenze di escape con `%` (elaborate per prime):

| Scrivi | Ottieni |
|---|---|
| `%##` | `#` |
| `%#` | `"` (virgolette) |
| `%%` | `%` |
| `%n` | a capo |
| `%$` | `$` letterale (non espanso) |

Per scrivere un `$` letterale usa quindi `%$`. `%` finale o `%` + carattere non in lista resta invariato.

## 5. Il file `.end` di risultato

Stesso formato `.ini`, stesso nome del file di ingresso con estensione `.end`. Ogni sezione job contiene in più:

```ini
[JOB1]
COMMAND=STAMPA
...
RETURN.TYPE=S
RETURN.VALUE=Ciao Mario!
TS.START=20260920:101500
TS.END=20260920:101502
RETURN.FILE.01=output.csv
```

- `RETURN.TYPE`: `S` = successo (vuoto = successo senza `Return` esplicito), `E` = errore.
- `RETURN.VALUE`: messaggio di ritorno; se il job fallisce senza messaggio, contiene l'errore.
- `RETURN.FILE.nn`: file restituiti (solo nome file, senza percorso).
- `TS.START/TS.END`: timbri `AAAAMMGG:HHMMSS`.
- In `[CONFIG]` vengono aggiunti esito globale + timbri.

## 6. Log e console

- File di log (da `LOG` in `[CONFIG]` o `<programma>.log`): righe `timestamp:messaggio`.
- In console vedi: file letto, sezioni elaborate, `Esecuzione Command <SEZIONE>`, `Eseguo/Eseguito il comando …`, `Creato file <nome>.end`, e per ogni metodo principale `Eseguita ntjobsapp.<metodo>: <esito>`.
- Metodi utili dentro la tua app: `jData.Log("INFO", msg)`, `jData.Log0(err, ctx)`, `jData.Log1(msg)`, `jData.Config("CHIAVE")` (legge da `[CONFIG]`, `""` se manca).

## 7. Modalità sviluppo `MakeIni` (senza file `.ini`)

Se il primo parametro **non** termina con `.ini`, l'app crea da sola un `ntjobsapp.ini` di prova:

```
python mia_app.py SALUTA PARAM.NAME Mario
```

= comando `SALUTA` + coppie `chiave valore` → sezione `[JOB_01]` con `TYPE=NTJOBS.APP.1`. Il numero di parametri dopo il comando deve essere pari (coppie chiave/valore). Utile per prove veloci.

## 8. Esempio completo

```python
from acJobsApp import acJobsApp
jData = acJobsApp()

def cbCommands(dJob):
    sCmd = dJob.get("COMMAND", "")
    if sCmd == "SALUTA":
        sNome = dJob.get("PARAM.NAME", "mondo")
        return jData.Return("", f"Ciao {sNome}!")
    if sCmd == "COPIA":
        # ... leggi dJob["FILE.IN"], produci "risultato.csv" ...
        return jData.Return("", "copia ok", {"01": "risultato.csv"})
    return jData.Return(f"Comando sconosciuto: {sCmd}")

if __name__ == "__main__":
    sResult = jData.Start()
    if sResult != "":
        jData.End(sResult)
    else:
        jData.End(jData.Run(cbCommands))
```

## 9. Errori comuni

| Sintomo | Causa / soluzione |
|---|---|
| `File .ini non esistente …` | Il percorso del primo parametro è sbagliato |
| `Sezione CONFIG non trovata` | Manca `[CONFIG]` nel file `.ini` |
| `Usate chiavi riservate …` | Hai usato `RETURN.*` o `TS.*` in ingresso: toglile |
| `Type INI non NTJOBSAPP` / `NAME APP non precisato` | `TYPE` deve iniziare con `NTJOBS.APP.`; `NAME` non vuoto |
| `File richiesto non presente …` | Un `FILE.*` (non `FILE.OUT.*`) non è nella cartella di lancio — ricorda: si verifica il solo nome file, e `$VARIABILI` devono essere definite in `[CONFIG]` |
| Exit code 1 / 2 | 1 = errore in `Start` (ini/log/config); 2 = errore in uno o più job (vedi `RETURN.TYPE=E` nel `.end`) |
