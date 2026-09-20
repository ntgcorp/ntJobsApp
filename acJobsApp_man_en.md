# acJobsApp — User Manual (English)

> Guide for **users**: how to use an ntJobsApp and how to write one. It does not describe the library internals, only what you need to use it.

## 1. What it is and what it is for

An **ntJobsApp** is a batch program that:

1. reads an **`.ini`** file passed as a parameter (e.g. `python my_app.py work.ini`),
2. runs one or more **jobs** (one per section of the `.ini` file),
3. writes an **`.end`** file with the same name as the `.ini` file, reporting each job,
4. exits with an **exit code**: `0` = OK, `1` = error before the jobs, `2` = error in one or more jobs.

The **`acJobsApp`** class (`acJobsApp.py`) is the orchestrator. You use it through the global variable **`jData`**.

## 2. The lifecycle (always the same)

```python
from acJobsApp import acJobsApp
jData = acJobsApp()

def cbCommands(dJob):
    # dJob = current job dictionary (UPPERCASE keys)
    # ... do your work ...
    return jData.Return("", "done!")   # "" = no error

if __name__ == "__main__":
    sResult = jData.Start()             # 1. read the .ini file
    if sResult != "":
        jData.End(sResult)              # startup error → write .end, exit code 1
    else:
        jData.End(jData.Run(cbCommands))  # 2. run jobs, 3. write .end (code 0 or 2)
```

- **`Start()`** — reads the first parameter (the `.ini` file), validates it, expands `$` variables, checks required files, starts the log. Returns `""` on success, otherwise an error message.
- **`Run(cbCommands)`** — for each section (except `[CONFIG]`) copies the job into `jData.dictJob`, reads `COMMAND` and calls `cbCommands(dictionary)`. If `EXIT=TRUE` in `[CONFIG]`, it stops at the first failing job.
- **`End(sResult)`** — adds timestamps and outcome to `[CONFIG]`, saves the `.end` file, writes to the log and sets the exit code (`sys.exit(1/2)` on error).
- **`Return(sResult, sValue="", dictFiles=None)`** — call it **inside `cbCommands`** to record the outcome: `sResult=""` = OK, otherwise an error; `sValue` = message; `dictFiles={"ID": "path"}` = returned files (checked: they must exist; only the file name is kept).

## 3. The input `.ini` file

```ini
[CONFIG]
TYPE=NTJOBS.APP.1.0
NAME=MY_APP
EXIT=TRUE
LOG=my_app.log
BASE_DIR=C:\data

[JOB1]
COMMAND=PRINT
FILE.IN=$BASE_DIR\input.csv
PARAM.GREETING=Hello

[JOB2]
COMMAND=PROCESS
FILE.IN=$BASE_DIR\input.csv
FILE.OUT.result=output.csv
PARAM.OPTION=fast
```

### 3.1 `[CONFIG]` section (mandatory)

| Key | Meaning |
|---|---|
| `TYPE` | Must start with `NTJOBS.APP.` (e.g. `NTJOBS.APP.1.0`) |
| `NAME` | Application name (mandatory, non-empty) |
| `EXIT` | `TRUE` = stop at the first failing job; any other value = continue with the remaining jobs |
| `LOG` | Log file name. If missing, `<program_name>.log` |
| other keys | Freely definable `$NAME` variables (see §4) |

### 3.2 Job sections (`[JOB1]`, `[INVOICES]`, … — free names)

| Key | Meaning |
|---|---|
| `COMMAND` | Mandatory. Action name; your `cbCommands` decides what to do based on this value |
| `FILE.*` | Input files (e.g. `FILE.IN`, `FILE.ID1`). They must **exist in the launch folder** (only the base name is checked). **Exception:** `FILE.OUT.*` are output files and must **not** pre-exist |
| `FILE.OUT.*` | Files produced by the job (checked in `Return`, not in `Start`) |
| `PARAM.*` | Free text parameters |

**Reserved** keys (do not use them in input; the system writes them): `TS.START`, `TS.END`, `RETURN.TYPE`, `RETURN.VALUE`, `RETURN.FILE.*`.

Notes: sections and keys are converted to **UPPERCASE** when read; values are left intact. The `NTJ_USER` and `NTJ_USERG` (comma-separated) environment variables are available as `jData.sUser` / `jData.asUsg`.

## 4. `$NAME` variables (expansion)

Define a variable in `[CONFIG]` and use it with `$NAME` in any job:

```ini
[CONFIG]
BASE_DIR=C:\data
COMMON_FILE=$BASE_DIR\common.csv
```

- `$BASE_DIR` → value of the `BASE_DIR` key in `[CONFIG]` (case-insensitive: `$base_dir` = `$BASE_DIR`).
- If the variable does **not** exist in `[CONFIG]`, it is left as-is (`$UNKNOWN` is no error, but as a `FILE.*` it will fail the "file not present" check).
- `[CONFIG]` variables may reference each other up to **2 levels** (e.g. `A=$B`, `B=$C` resolves).
- References to keys of the **same job section** are not expanded: only `[CONFIG]` feeds `$NAME`. Job sections get a single expansion pass.

Special variables:

- `$ENV.NAME` → environment variable `NAME` (empty string if missing). E.g. `$ENV.HOME`.
- `$SYS.NAME` → `OS` (WINDOWS/LINUX), `OS2`, `USER`, `COMPUTER`, `CD` (current folder), `TEMP`, `YYYYMMDD`, `NOW`. Unknown name → `NOTFOUND`.

`%` escape sequences (processed first):

| Write | You get |
|---|---|
| `%##` | `#` |
| `%#` | `"` (quote) |
| `%%` | `%` |
| `%n` | newline |
| `%$` | literal `$` (not expanded) |

So use `%$` for a literal `$`. A trailing `%`, or `%` + a character not in the list, is left unchanged.

## 5. The result `.end` file

Same `.ini` format, same name as the input file with extension `.end`. Each job section additionally contains:

```ini
[JOB1]
COMMAND=PRINT
...
RETURN.TYPE=S
RETURN.VALUE=Hello Mario!
TS.START=20260920:101500
TS.END=20260920:101502
RETURN.FILE.01=output.csv
```

- `RETURN.TYPE`: `S` = success (empty = success without an explicit `Return`), `E` = error.
- `RETURN.VALUE`: return message; if a job fails without a message, it holds the error.
- `RETURN.FILE.nn`: returned files (base name only, no path).
- `TS.START/TS.END`: `YYYYMMDD:HHMMSS` stamps.
- Global outcome + stamps are added to `[CONFIG]`.

## 6. Log and console

- Log file (from `LOG` in `[CONFIG]`, or `<program>.log`): `timestamp:message` lines.
- On console you see: file read, processed sections, `Esecuzione Command <SECTION>`, `Eseguo/Eseguito il comando …`, `Creato file <name>.end`, and for each main method `Eseguita ntjobsapp.<method>: <outcome>`.
- Handy methods inside your app: `jData.Log("INFO", msg)`, `jData.Log0(err, ctx)`, `jData.Log1(msg)`, `jData.Config("KEY")` (reads from `[CONFIG]`, `""` if missing).

## 7. `MakeIni` development mode (no `.ini` file)

If the first parameter does **not** end with `.ini`, the app builds a trial `ntjobsapp.ini` by itself:

```
python my_app.py GREET PARAM.NAME Mario
```

= command `GREET` + `key value` pairs → `[JOB_01]` section with `TYPE=NTJOBS.APP.1`. The number of parameters after the command must be even (key/value pairs). Useful for quick tests.

## 8. Running external ntJobsApps (`Exec` / `ExecReturn`)

An ntJobsApp can launch another one in the **background** and collect its result later:

```python
sErr = jData.Exec(
    sScript="C:/apps/child.py",          # script of the external ntJobsApp
    dictConfig={"NAME": "CHILD", "EXIT": "TRUE"},  # extra config (TYPE defaults to NTJOBS.APP.1)
    dictJobs={"JOB1": {"COMMAND": "GREET", "PARAM.NAME": "Mario"}},  # key=section, value=job dict…
    sID="batch1",                        # launch ID (letters/digits/_/-)
)
# …or several jobs per section: dictJobs={"BATCH": [{"COMMAND": "A"}, {"COMMAND": "B"}]}
#   → generates BATCH_01, BATCH_02 sections

import time
dictResult = {}
deadline = time.time() + 120
while not dictResult and time.time() < deadline:
    dictResult = jData.ExecReturn("batch1", nTimeout=30)  # {} = not finished yet
    # ... do something else meanwhile ...
print(dictResult.get("CONFIG", {}).get("RETURN.TYPE"))  # "E" or "" on global outcome
```

- **`Exec(sScript, dictConfig, dictJobs, sID)`** — returns `""` on successful launch, otherwise an error message. It creates **`ntjobsapp_[sID].ini`** in the **script's folder** (e.g. `ntjobsapp_batch1.ini`) and passes it as the script parameter: `python child.py ntjobsapp_batch1.ini`. The launch is non-blocking. Sections/keys are uppercased, values stringified. Any stale previous `.end` is deleted.
- **`ExecReturn(sID, nTimeout=30)`** — waits up to `nTimeout` seconds for **`ntjobsapp_[sID].end`** (polled every 0.5 s):
  - `{}` (0 keys) = **not finished** within the timeout → call it again;
  - dictionary with > 0 keys = **finished**: holds the `.end` file just read (same structure as §5). The `.end` and `.ini` files are **deleted** after reading;
  - `{"ERROR": "..."}` = invalid `sID` or unreadable `.end` file (files are kept in this case).
- Launch state lives in `jData.dictExec[sID]` (`INI`, `END`, `SCRIPT`, `TS`); if missing (e.g. program restarted), `ExecReturn` looks for the `.end` file in the current folder.

## 9. Full example

```python
from acJobsApp import acJobsApp
jData = acJobsApp()

def cbCommands(dJob):
    sCmd = dJob.get("COMMAND", "")
    if sCmd == "GREET":
        sName = dJob.get("PARAM.NAME", "world")
        return jData.Return("", f"Hello {sName}!")
    if sCmd == "COPY":
        # ... read dJob["FILE.IN"], produce "result.csv" ...
        return jData.Return("", "copy ok", {"01": "result.csv"})
    return jData.Return(f"Unknown command: {sCmd}")

if __name__ == "__main__":
    sResult = jData.Start()
    if sResult != "":
        jData.End(sResult)
    else:
        jData.End(jData.Run(cbCommands))
```

## 10. Common errors

| Symptom | Cause / fix |
|---|---|
| `File .ini non esistente …` | Wrong first-parameter path |
| `Sezione CONFIG non trovata` | `[CONFIG]` missing in the `.ini` file |
| `Usate chiavi riservate …` | You used `RETURN.*` or `TS.*` in input: remove them |
| `Type INI non NTJOBSAPP` / `NAME APP non precisato` | `TYPE` must start with `NTJOBS.APP.`; `NAME` non-empty |
| `File richiesto non presente …` | A `FILE.*` (not `FILE.OUT.*`) is not in the launch folder — remember: only the base name is checked, and `$VARIABLES` must be defined in `[CONFIG]` |
| Exit code 1 / 2 | 1 = `Start` error (ini/log/config); 2 = error in one or more jobs (see `RETURN.TYPE=E` in the `.end`) |
| `sID non valido …` / `Script non esistente …` (`Exec`) | `sID` letters/digits/`_`/`-` only; `sScript` must exist |
| `ExecReturn` always returns `{}` | The child has not written the `.end` yet: call again with a fresh timeout; check the child started and `TYPE`/`NAME` in `dictConfig` are valid (see the child `.log`) |
