# aiSys — Manuale d'uso (Italiano)

> Guida per **utenti** della libreria `aiSys.py`: funzioni di supporto (timestamp, variabili, file, stringhe, dizionari, log). Solo uso pratico, nessun dettaglio di sviluppo interno.

Import: `import aiSys`. Tutte le funzioni sono autonome e usano solo la libreria standard.

## 1. Base: errori e dizionari

```python
sErr = aiSys.ErrorProc(sResult, "mia_funzione")  # se sResult != "" → "mia_funzione: Errore <sResult>", altrimenti ""
v = aiSys.DictExist(d, "CHIAVE", "default")      # valore di d["CHIAVE"] o "default" (None se d non è un dict)
d = aiSys.DictMerge(dBase, dAggiunte)            # unisce; vince dAggiunte; None/vuoto ignorato
```

## 2. Timestamp `AAAAMMGG:HHMMSS[:suffisso]`

```python
sTs = aiSys.Timestamp()            # es. "20240125:143055"
sTs2 = aiSys.Timestamp("Test")     # es. "20240125:143055:test" (suffisso minuscolo)
aiSys.TimestampValidate(sTs)       # True/False
aiSys.TimestampConvert(sTs, "s")   # secondi da epoch (int); "d" → giorni (float)
aiSys.TimestampFromSeconds(123, "run")
aiSys.TimestampFromDays(20000.5)
aiSys.TimestampDiff(ts1, ts2, "s") # differenza assoluta in secondi ("d" → giorni); None se non validi
aiSys.TimestampAdd(ts, 90, "s")    # "s"=secondi, "m"=minuti, "h"=ore, "d"=giorni; "" se errore
aiSys.TimestampIsoFrom(ts)         # "20240125:143055" → "2024-01-25T14:30:55.000Z"
aiSys.TimestampIsoTo("2024-01-25T14:30:55.000Z")  # → "20240125:143055"
aiSys.format_timedelta(3661)       # "01:01:01"
```

## 3. Variabili ed espansione (`Expand`)

```python
s = aiSys.Expand("Ciao $USER", {"USER": "Mario"})       # "Ciao Mario"
d2 = aiSys.ExpandDict({"A": "$X", "B": "fisso"}, {"X": "1"})  # espande i valori con dict di appoggio
s2 = aiSys.ExpandConvert('dice "ciao"\n100% $')         # codifica inversa per poi usare Expand
```

Ordine di elaborazione di `Expand`: (1) escape `%` (`%##`→`#`, `%#`→`"`, `%%`→`%`, `%n`→a capo, `%$`→`$`); (2) `$ENV.NOME` (env, `""` se manca) e `$SYS.NOME` (`OS, OS2, USER, COMPUTER, CD, TEMP, YYYYMMDD, NOW`; sconosciuto → `NOTFOUND`); (3) `$NOME` cercato in `dictConfig` (se manca resta letterale). Nomi case-sensitive, senza spazi. Vedi anche il manuale `acJobsApp` §4 per l'uso nei file `.ini`.

## 4. Config: lettura, default, parsing

```python
v = aiSys.Config(dCfg, "CHIAVE")          # valore o "" (anche se dCfg None o chiave assente)
aiSys.ConfigDefault(dCfg, "CHIAVE", "x")  # scrive "x" solo se chiave assente o None/""
aiSys.ConfigSet(dCfg, "CHIAVE", "x")      # scrive sempre
d = aiSys.SplitSettings("A=1 B=%#due tre%#", dCfg)  # "A"→"1", "B"→"due tre" (virgolette/escape gestiti)
b = aiSys.isGroups(["a", "b"], ["x", "b"])  # True se almeno un elemento in comune (case sensitive)
```

`SplitSettings` accetta coppie `CHIAVE=VALORE` separate da spazi o a capo (`%n` = a capo dopo `Expand`); gli spazi attorno a `=` e al valore vengono rimossi; se passi `dictConfig`, i valori vengono espansi con `ExpandDict`.

## 5. File: path, INI, CSV, array di righe

```python
aiSys.NormalizePath("a/b\\c")          # "\\" su Windows, "/" altrove
aiSys.FileExists("dati.csv")           # True/False
aiSys.FileDelete("tmp.txt")            # "" = ok, altrimenti "…: Errore …"
aiSys.isValidPath(p)                   # True se path esistente
aiSys.isFilename("nome_file.txt")      # lettere/numeri/"_" + estensione valida
sFull = aiSys.PathMake("C:\\dati", "file", "csv")  # compone cartella+nome+estensione
```

INI (sezioni → dizionario di dizionari):

```python
sErr, d = aiSys.read_ini_to_dict("conf.ini")  # tupla (errore, dati); chiavi con case originale
sErr = aiSys.save_dict_to_ini({"S": {"K": "V"}}, "out.ini")  # solo stringa errore; crea cartelle se servono
```

CSV (prima colonna = chiave esterna; header `;`-separato):

```python
sErr, d = aiSys.read_csv_to_dict("dati.csv", ["ID", "NOME"])  # verifica header se passato; errori: chiavi duplicate/nulle, n.campi
sErr = aiSys.save_dict_to_csv("out.csv", ["ID", "NOME"], d, "w")  # "w"=sovrascrive, "a"=accoda; valori con spazi tra virgolette
```

Testo riga per riga (UTF-8):

```python
sErr, righe = aiSys.read_array_file("note.txt")     # righe separate da "\n"
sErr = aiSys.save_array_file("note.txt", ["a", "b"])  # "" sovrascrive; "a" accoda
```

## 6. Stringhe

```python
aiSys.StringAppend("a", "b")           # "a,b" (terzo param = delimitatore, default ",")
aiSys.StringBool("True")               # True solo per "true" (case-insensitive); alias StringBoolean
aiSys.isBool("1")                      # True per true/false/1/0
aiSys.isEmail("nome.cognome@gmail.com")# True/False (regex media + controlli)
aiSys.StringToArray("a, b,,c")         # ["a", "b", "c"] (strip, salta vuoti)
aiSys.StringToNum("12,5")              # 12.5 (virgola→punto; int se senza decimali; 0 se non convertibile)
aiSys.StringWash('à "x"')              # solo ASCII, senza virgolette → " x"
aiSys.isValidPassword("Abc_1.!")       # solo lettere/numeri/_/./!
aiSys.isLettersOnly("Mario Rossi")     # solo lettere e spazi
```

## 7. Dizionari → stringhe (JSON / INI / XML) e stampa

```python
sErr, sJson = aiSys.DictToString(d, "json")      # JSON indentato 2 spazi; (errore, testo)
sErr, sIni = aiSys.DictToString(d, "ini")        # solo 1° livello chiave=valore
sErr, sSect = aiSys.DictToString(d, "ini.sect")  # 2° livello → sezioni [chiave]; oltre ignorato
sXml = aiSys.DictToXml({"user": {"@id": "1", "name": "Mario"}})  # <root><user id="1">…; opzioni: root_tag, attr_prefix="@", text_key, cdata_key, item_name, pretty, type_convert
sErr = aiSys.DictPrint(d)              # stampa JSON a video; DictPrint(d, "log.txt") accoda anche su file
```

Note: `ini`/`ini.sect` — booleani → `true/false`, `None` → `""`, array di semplici → `a,b,c`, altri tipi ignorati, caratteri speciali `[ ] ; # \n =` rimossi, non-ASCII → `_`. Formato JSON → non-ASCII in escape standard.

## 8. Log con `acLog`

```python
oLog = aiSys.acLog()
oLog.Start(sLogfile="app", sLogFolder=".")  # default: <nome_programma>.log nella cartella dell'app
oLog.Log("INFO", "avvio")    # riga: Timestamp() + ":avvio" su file (append) + console
oLog.Log0("", "tutto bene")  # sResult "" → Log("INFO", sValue)
oLog.Log0("boom", "fase X")  # sResult != "" → Log("ERR", "boom: fase X")
oLog.Log1("avvio")           # scorciatoia per Log("INFO", …)
```

Se il log non è inizializzato (`sLog == ""`), `Log` scrive solo in console.

## 9. Esempio riepilogativo

```python
import aiSys
oLog = aiSys.acLog(); oLog.Start(sLogfile="demo", sLogFolder=".")
sErr, dCfg = aiSys.read_ini_to_dict("conf.ini")
sOut = aiSys.Expand("Output in $OUT_DIR", dCfg.get("CONFIG", {}))
oLog.Log1(f"Avvio {aiSys.Timestamp()} → {sOut}")
```
