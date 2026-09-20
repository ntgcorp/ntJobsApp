# aiSys — User Manual (English)

> Guide for **users** of the `aiSys.py` library: helper functions (timestamps, variables, files, strings, dictionaries, logging). Practical use only, no internal development details.

Import: `import aiSys`. All functions are self-contained and use only the standard library.

## 1. Basics: errors and dictionaries

```python
sErr = aiSys.ErrorProc(sResult, "my_function")  # if sResult != "" → "my_function: Errore <sResult>", else ""
v = aiSys.DictExist(d, "KEY", "default")        # value of d["KEY"] or "default" (None if d is not a dict)
d = aiSys.DictMerge(dBase, dExtra)              # merge; dExtra wins; None/empty ignored
```

## 2. `YYYYMMDD:HHMMSS[:suffix]` timestamps

```python
sTs = aiSys.Timestamp()            # e.g. "20240125:143055"
sTs2 = aiSys.Timestamp("Test")     # e.g. "20240125:143055:test" (lowercased suffix)
aiSys.TimestampValidate(sTs)       # True/False
aiSys.TimestampConvert(sTs, "s")   # seconds since epoch (int); "d" → days (float)
aiSys.TimestampFromSeconds(123, "run")
aiSys.TimestampFromDays(20000.5)
aiSys.TimestampDiff(ts1, ts2, "s") # absolute difference in seconds ("d" → days); None if invalid
aiSys.TimestampAdd(ts, 90, "s")    # "s"=seconds, "m"=minutes, "h"=hours, "d"=days; "" on error
aiSys.TimestampIsoFrom(ts)         # "20240125:143055" → "2024-01-25T14:30:55.000Z"
aiSys.TimestampIsoTo("2024-01-25T14:30:55.000Z")  # → "20240125:143055"
aiSys.format_timedelta(3661)       # "01:01:01"
```

## 3. Variables and expansion (`Expand`)

```python
s = aiSys.Expand("Hello $USER", {"USER": "Mario"})       # "Hello Mario"
d2 = aiSys.ExpandDict({"A": "$X", "B": "fixed"}, {"X": "1"})
s2 = aiSys.ExpandConvert('says "hi"\n100% $')            # inverse encoding, for later use with Expand
```

`Expand` processing order: (1) `%` escapes (`%##`→`#`, `%#`→`"`, `%%`→`%`, `%n`→newline, `%$`→`$`); (2) `$ENV.NAME` (environment, `""` if missing) and `$SYS.NAME` (`OS, OS2, USER, COMPUTER, CD, TEMP, YYYYMMDD, NOW`; unknown → `NOTFOUND`); (3) `$NAME` looked up in `dictConfig` (left literal if missing). Names are case-sensitive, no spaces. See also the `acJobsApp` manual §4 for `.ini` usage.

## 4. Config: reading, defaults, parsing

```python
v = aiSys.Config(dCfg, "KEY")          # value or "" (also if dCfg is None or key missing)
aiSys.ConfigDefault(dCfg, "KEY", "x")  # writes "x" only if key missing or None/""
aiSys.ConfigSet(dCfg, "KEY", "x")      # always writes
d = aiSys.SplitSettings("A=1 B=%#two three%#", dCfg)  # "A"→"1", "B"→"two three"
b = aiSys.isGroups(["a", "b"], ["x", "b"])  # True if at least one common element (case sensitive)
```

`SplitSettings` accepts `KEY=VALUE` pairs separated by spaces or newlines (`%n` = newline after `Expand`); spaces around `=` and the value are stripped; if you pass `dictConfig`, values are expanded with `ExpandDict`.

## 5. Files: paths, INI, CSV, line arrays

```python
aiSys.NormalizePath("a/b\\c")          # "\\" on Windows, "/" elsewhere
aiSys.FileExists("data.csv")           # True/False
aiSys.FileDelete("tmp.txt")            # "" = ok, otherwise "…: Errore …"
aiSys.isValidPath(p)                   # True if existing path
aiSys.isFilename("file_name.txt")      # letters/digits/"_" + valid extension
sFull = aiSys.PathMake("C:\\data", "file", "csv")  # folder+name+extension
```

INI (sections → dictionary of dictionaries):

```python
sErr, d = aiSys.read_ini_to_dict("conf.ini")  # tuple (error, data); keys keep original case
sErr = aiSys.save_dict_to_ini({"S": {"K": "V"}}, "out.ini")  # error string only; creates folders if needed
```

CSV (first column = outer key; `;`-separated header):

```python
sErr, d = aiSys.read_csv_to_dict("data.csv", ["ID", "NAME"])  # header checked if passed; errors: duplicate/empty keys, field count
sErr = aiSys.save_dict_to_csv("out.csv", ["ID", "NAME"], d, "w")  # "w"=overwrite, "a"=append; values with spaces get quoted
```

Line-by-line text (UTF-8):

```python
sErr, lines = aiSys.read_array_file("notes.txt")     # lines split on "\n"
sErr = aiSys.save_array_file("notes.txt", ["a", "b"])  # "" overwrites; "a" appends
```

## 6. Strings

```python
aiSys.StringAppend("a", "b")           # "a,b" (3rd param = delimiter, default ",")
aiSys.StringBool("True")               # True only for "true" (case-insensitive); alias StringBoolean
aiSys.isBool("1")                      # True for true/false/1/0
aiSys.isEmail("name.surname@gmail.com")# True/False (medium regex + checks)
aiSys.StringToArray("a, b,,c")         # ["a", "b", "c"] (stripped, empties skipped)
aiSys.StringToNum("12,5")              # 12.5 (comma→dot; int if no decimals; 0 if not convertible)
aiSys.StringWash('à "x"')              # ASCII only, quotes removed → " x"
aiSys.isValidPassword("Abc_1.!")       # only letters/digits/_/./!
aiSys.isLettersOnly("Mario Rossi")     # only letters and spaces
```

## 7. Dictionaries → strings (JSON / INI / XML) and printing

```python
sErr, sJson = aiSys.DictToString(d, "json")      # JSON indented 2 spaces; (error, text)
sErr, sIni = aiSys.DictToString(d, "ini")        # 1st level only, key=value
sErr, sSect = aiSys.DictToString(d, "ini.sect")  # 2nd level → [key] sections; deeper ignored
sXml = aiSys.DictToXml({"user": {"@id": "1", "name": "Mario"}})  # options: root_tag, attr_prefix="@", text_key, cdata_key, item_name, pretty, type_convert
sErr = aiSys.DictPrint(d)              # print JSON to screen; DictPrint(d, "log.txt") also appends to file
```

Notes: `ini`/`ini.sect` — booleans → `true/false`, `None` → `""`, arrays of scalars → `a,b,c`, other types ignored, special chars `[ ] ; # \n =` removed, non-ASCII → `_`. JSON format → non-ASCII in standard escapes.

## 8. Logging with `acLog`

```python
oLog = aiSys.acLog()
oLog.Start(sLogfile="app", sLogFolder=".")  # default: <program_name>.log in the app folder
oLog.Log("INFO", "start")    # line: Timestamp() + ":start" to file (append) + console
oLog.Log0("", "all good")    # sResult "" → Log("INFO", sValue)
oLog.Log0("boom", "phase X") # sResult != "" → Log("ERR", "boom: phase X")
oLog.Log1("start")           # shortcut for Log("INFO", …)
```

If the log is not initialized (`sLog == ""`), `Log` writes to console only.

## 9. Summary example

```python
import aiSys
oLog = aiSys.acLog(); oLog.Start(sLogfile="demo", sLogFolder=".")
sErr, dCfg = aiSys.read_ini_to_dict("conf.ini")
sOut = aiSys.Expand("Output to $OUT_DIR", dCfg.get("CONFIG", {}))
oLog.Log1(f"Start {aiSys.Timestamp()} -> {sOut}")
```
