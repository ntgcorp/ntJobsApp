"""
acJobsApp.py - Orchestratore per applicazioni ntJobsApp
File unico e autocontenuto. Zero dipendenze esterne.
"""
import os
import sys
import re
import json
import configparser
import subprocess
import time
from datetime import datetime
from typing import Dict, List, Any, Optional, Tuple, Union

# =============================================================================
# COSTANTI E VARIABILI GLOBALI
# =============================================================================
NTJOBSAPP_VER = "2024102412"  # Formato YYYYMMDDHH (statico, come da specifica)

# =============================================================================
# FUNZIONI DI SUPPORTO INGLOBATE (ex-aiSys)
# =============================================================================

def NormalizePath(sPath: str) -> str:
    """Sostituisce i separatori di path in base al sistema operativo."""
    if not sPath: return ""
    if sys.platform == "win32":
        return sPath.replace("/", "\\")
    return sPath.replace("\\", "/")

def ErrorProc(sResult: str, sProc: str) -> str:
    if sResult:
        return f"{sProc}: Errore {sResult}"
    return sResult

def Timestamp(sPostfix: str = "") -> str:
    try:
        now = datetime.now()
        sResult = now.strftime("%Y%m%d:%H%M%S")
        if sPostfix:
            sResult = f"{sResult}:{sPostfix.lower()}"
        return sResult
    except Exception:
        return ""

def TimestampConvert(sTimestamp: str, sMode: str = "s") -> Union[int, float, None]:
    """Converte timestamp in secondi o giorni dall'epoch."""
    try:
        if not sTimestamp:
            sTimestamp = Timestamp("")
        if ':' not in sTimestamp:
            return None
        parts = sTimestamp.split(':')
        if len(parts) < 2:
            return None
        date_part, time_part = parts[0], parts[1]
        if len(date_part) != 8 or len(time_part) != 6:
            return None
        dt = datetime(
            int(date_part[0:4]), int(date_part[4:6]), int(date_part[6:8]),
            int(time_part[0:2]), int(time_part[2:4]), int(time_part[4:6])
        )
        epoch = datetime(1970, 1, 1)
        delta = (dt - epoch).total_seconds()
        return int(delta) if sMode.lower() == "s" else delta / 86400.0
    except Exception:
        return None

def TimestampValidate(sTimestamp: str) -> bool:
    """Valida formato timestamp."""
    try:
        if not sTimestamp or ':' not in sTimestamp:
            return False
        parts = sTimestamp.split(':')
        if len(parts) < 2:
            return False
        d, t = parts[0], parts[1]
        if len(d) != 8 or len(t) != 6:
            return False
        y, m, day = int(d[:4]), int(d[4:6]), int(d[6:8])
        h, mi, s = int(t[:2]), int(t[2:4]), int(t[4:6])
        if not (1 <= m <= 12 and 0 <= h <= 23 and 0 <= mi <= 59 and 0 <= s <= 59):
            return False
        days_in_month = [31, 29 if (y%4==0 and (y%100!=0 or y%400==0)) else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
        return 1 <= day <= days_in_month[m-1]
    except Exception:
        return False

def TimestampDiff(sTimestamp1: str, sTimestamp2: str, sMode: str = "s") -> Union[int, float, None]:
    """Differenza tra due timestamp."""
    try:
        if not (TimestampValidate(sTimestamp1) and TimestampValidate(sTimestamp2)):
            return None
        sec1, sec2 = TimestampConvert(sTimestamp1, "s"), TimestampConvert(sTimestamp2, "s")
        if sec1 is None or sec2 is None:
            return None
        diff = abs(sec1 - sec2)
        return int(diff) if sMode.lower() == "s" else diff / 86400.0
    except Exception:
        return None

def Expand(sText: str, dictConfig: Dict[str, str]) -> str:
    """Espande variabili e sequenze di escape."""
    sProc = "Expand"
    try:
        # Fase 1: Escape (ordine critico)
        sText = sText.replace('%##', '#')
        sText = sText.replace('%#', '"')
        sText = sText.replace('%%', '%')
        sText = sText.replace('%n', '\n')
        sText = sText.replace('%$', '$')

        # Fase 2: $SYS. e $ENV.
        def repl_sys_env(match):
            prefix = match.group(1)
            varname = match.group(2)
            if prefix == "ENV.":
                return os.environ.get(varname, "")
            elif prefix == "SYS.":
                if os.name == "nt":
                    sOS = "WINDOWS"
                    try:
                        wv = sys.getwindowsversion()
                        if wv.major == 10:
                            sOS2 = "WINDOWS11" if wv.build >= 22000 else "WINDOWS10"
                        elif wv.major == 6 and wv.minor == 1:
                            sOS2 = "WINDOWS7"
                        elif wv.major == 6 and wv.minor == 2:
                            sOS2 = "WINDOWS8"
                        elif wv.major == 6 and wv.minor == 3:
                            sOS2 = "WINDOWS8.1"
                        else:
                            sOS2 = f"WINDOWS{wv.major}.{wv.minor}"
                    except Exception:
                        sOS2 = "WINDOWS"
                elif sys.platform.startswith("linux"):
                    sOS = "LINUX"
                    sOS2 = "LINUX"
                else:
                    sOS = os.name.upper()
                    sOS2 = sys.platform
                sys_vars = {
                    "OS": sOS, "OS2": sOS2,
                    "USER": os.environ.get("USERNAME", os.environ.get("USER", "")),
                    "COMPUTER": os.environ.get("COMPUTERNAME", ""),
                    "CD": os.getcwd(), "TEMP": os.environ.get("TEMP", ""),
                    "YYYYMMDD": datetime.now().strftime("%Y%m%d"),
                    "NOW": Timestamp()
                }
                return sys_vars.get(varname, "NOTFOUND")
            return match.group(0)
        sText = re.sub(r'\$(SYS\.|ENV\.)([A-Za-z0-9_]+)', repl_sys_env, sText)

        # Fase 3: $NOMEVAR da dictConfig
        def repl_config(match):
            varname = match.group(1)
            return dictConfig.get(varname, match.group(0))
        sText = re.sub(r'\$([A-Za-z0-9_]+)', repl_config, sText)

        return sText
    except Exception as e:
        return ErrorProc(str(e), sProc)

def ExpandDict(dictExpand: Dict, dictParam: Dict) -> Dict:
    """Espande valori di un dizionario usando Expand(). Max 1 livello."""
    sProc = "ExpandDict"
    try:
        result = dictExpand.copy()
        for k, v in result.items():
            if isinstance(v, dict):
                result[k] = {sk: Expand(sv, dictParam) for sk, sv in v.items()}
            elif isinstance(v, str):
                result[k] = Expand(v, dictParam)
        return result
    except Exception as e:
        print(f"{sProc}: {e}")
        return dictExpand

def StringToNum(sNumber: str) -> Union[int, float]:
    try:
        sNumber = sNumber.replace(',', '.')
        return float(sNumber) if '.' in sNumber else int(sNumber)
    except: return 0

def StringBool(sText: str) -> bool:
    return str(sText).strip().lower() == "true"

def DictExist(dictParam: Any, sKey: str, xDefault: Any = None) -> Any:
    """Restituisce valore di chiave o default; None se dictParam non è un dict."""
    if not isinstance(dictParam, dict):
        return None
    return dictParam.get(sKey, xDefault)

def DictMerge(dictSource: Dict, dictAdd: Dict) -> Dict:
    """Unisce dictAdd a dictSource. Priorità a dictAdd."""
    if not dictAdd:
        return dictSource
    if not dictSource:
        return dictAdd.copy()
    for k, v in dictAdd.items():
        dictSource[k] = v
    return dictSource

def DictToString(dictParam: Dict, sFormat: str = "json") -> Tuple[str, str]:
    sProc = "DictToString"
    try:
        if not isinstance(dictParam, dict): dictParam = {}
        sOut = ""

        def ini_val(v: Any) -> str:
            if isinstance(v, (list, tuple)):
                parts = []
                for e in v:
                    if isinstance(e, bool):
                        parts.append("true" if e else "false")
                    elif isinstance(e, (int, float, str)):
                        parts.append(str(e))
                    else:
                        return ""
                val = ",".join(parts)
            elif v is True:
                val = "true"
            elif v is False:
                val = "false"
            elif v is None:
                val = ""
            elif isinstance(v, str):
                val = v
            else:
                val = str(v)
            for ch in ('[', ']', ';', '#', '\n', '='):
                val = val.replace(ch, '')
            return ''.join(c if ord(c) < 128 else '_' for c in val)

        if sFormat == "json":
            sOut = json.dumps(dictParam, indent=2, ensure_ascii=True)
        elif sFormat == "ini":
            lines = []
            for k, v in dictParam.items():
                if isinstance(v, dict): continue
                lines.append(f"{k}={ini_val(v)}")
            sOut = "\n".join(lines)
        elif sFormat == "ini.sect":
            sections = []
            top = []
            for k, v in dictParam.items():
                if isinstance(v, dict):
                    sect_lines = []
                    for sk, sv in v.items():
                        sect_lines.append(f"{sk}={ini_val(sv)}")
                    sections.append(f"[{k}]\n" + "\n".join(sect_lines))
                else:
                    top.append(f"{k}={ini_val(v)}")
            sOut = "\n".join(top + sections)
        else:
            sOut = ""
        return ("", sOut)
    except Exception as e:
        return (ErrorProc(str(e), sProc), "")

def DictPrint(dictParam: Dict, sFile: Optional[str] = None) -> str:
    sProc = "DictPrint"
    try:
        if not isinstance(dictParam, dict): dictParam = {}
        sText = DictToString(dictParam, "json")[1]
        print(sText)
        if sFile:
            with open(sFile, 'a', encoding='utf-8') as f:
                f.write(sText + '\n')
        return ""
    except Exception as e:
        return ErrorProc(str(e), sProc)

def FileExists(sFile: str) -> bool:
    return os.path.isfile(sFile)

def isValidPath(sPath: str) -> bool:
    """True se sPath è un path valido ed esiste (file o directory)."""
    return os.path.exists(sPath)

def read_ini_to_dict(ini_file_path: str) -> Tuple[str, Dict[str, Dict[str, str]]]:
    sProc = "read_ini_to_dict"
    try:
        if not FileExists(ini_file_path):
            return (f"File non esistente: {ini_file_path}", {})
        config = configparser.ConfigParser(interpolation=None, comment_prefixes=(";",), inline_comment_prefixes=())
        config.optionxform = str
        config.read(ini_file_path, encoding='utf-8')
        result = {section: dict(config[section]) for section in config.sections()}
        print(f"Letto file .ini {ini_file_path}, Numero Sezioni: {len(result)}")
        return ("", result)
    except Exception as e:
        return (ErrorProc(str(e), sProc), {})

def save_dict_to_ini(data_dict: Dict[str, Dict[str, str]], ini_file_path: str) -> str:
    sProc = "save_dict_to_ini"
    try:
        config = configparser.ConfigParser(interpolation=None)
        config.optionxform = str
        for section, items in data_dict.items():
            if not config.has_section(section):
                config.add_section(section)
            for k, v in items.items():
                config.set(section, k, str(v))
        sDir = os.path.dirname(ini_file_path)
        if sDir:
            os.makedirs(sDir, exist_ok=True)
        with open(ini_file_path, 'w', encoding='utf-8') as f:
            config.write(f)
        return ""
    except Exception as e:
        return ErrorProc(str(e), sProc)

# =============================================================================
# CLASSE LOG INGLOBATA (ex aiSys.acLog)
# =============================================================================
class acLog:
    def __init__(self): self.sLog = ""
    def Start(self, sLogfile: Optional[str] = None, sLogFolder: Optional[str] = None) -> str:
        sProc = "Start"
        try:
            if not sLogFolder or sLogFolder == "": sLogFolder = os.path.dirname(os.path.abspath(sys.argv[0]))
            sAppName = os.path.splitext(os.path.basename(sys.argv[0]))[0]
            if not sLogfile or sLogfile == "": sLogfile = sAppName
            self.sLog = os.path.join(sLogFolder, f"{sLogfile}.log")
            return ""
        except Exception as e: return ErrorProc(str(e), sProc)
    def Log(self, sType: str, sValue: str = "") -> None:
        if not self.sLog: return
        sLine = f"{Timestamp(sType)}: {sValue}"
        print(sLine)
        try:
            with open(self.sLog, 'a', encoding='utf-8') as f: f.write(sLine + '\n')
        except: pass
    def Log0(self, sResult: str, sValue: str = "") -> None:
        self.Log("ERR", f"{sResult}: {sValue}") if sResult else self.Log("INFO", sValue)
    def Log1(self, sValue: str = "") -> None: self.Log("INFO", sValue)

# =============================================================================
# CLASSE PRINCIPALE acJobsApp
# =============================================================================
class acJobsApp:
    def __init__(self):
        self.sJobIni = ""
        self.sUser = os.environ.get("NTJ_USER", "")
        sUsg = os.environ.get("NTJ_USERG", "")
        self.asUsg = [x.strip() for x in sUsg.split(",") if x.strip()] if sUsg else []
        self.bErrExit = False
        self.sName = ""
        self.tsStart = ""
        self.sType = ""
        self.sLogFile = ""
        self.sCommand = ""
        self.dictJob = {}
        self.dictJobs = {}
        self.sJobEnd = ""
        self.dictExec: Dict[str, Dict[str, str]] = {}
        self.jLog = acLog()

    def Start(self) -> str:
        sProc = "Start"
        sResult = ""
        self.tsStart = Timestamp()
        self.sJobIni = ""
        self.sJobEnd = ""
        self.dictJobs = {}
        self.dictJob = {}
        self.sCommand = ""
        # Nota: sUser/asUsg inizializzati in __init__ (variabili d'ambiente statiche)
        
        # 1. Verifica parametri
        if len(sys.argv) < 2:
            sResult = "NTJOBSAPP: Eseguire con parametro file .ini o nella forma ntjobsapp.py command parametro valore ecc."
            print(sResult)
            print("Eseguita ntjobsapp." + sProc + ": " + sResult)
            return sResult

        # 2. Controllo estensione .ini o MakeIni
        if not sys.argv[1].lower().endswith('.ini'):
            sResult = self.MakeIni()
            if sResult:
                print("Eseguita ntjobsapp." + sProc + ": " + sResult)
                return sResult
        else:
            self.sJobIni = sys.argv[1]

        # 3. Normalizzazione ed esistenza
        self.sJobIni = NormalizePath(self.sJobIni)
        if not os.path.exists(self.sJobIni):
            sResult = f"File .ini non esistente {self.sJobIni}"
            print("Eseguita ntjobsapp." + sProc + ": " + sResult)
            return sResult

        # 4. Prepara .end e leggi INI
        base, _ = os.path.splitext(self.sJobIni)
        self.sJobEnd = base + ".end"
        sResult, self.dictJobs = read_ini_to_dict(self.sJobIni)
        if sResult:
            print("Eseguita ntjobsapp." + sProc + ": " + sResult)
            return sResult
        self.dictJobs = {sec.upper(): {k.upper(): v for k, v in vals.items()} for sec, vals in self.dictJobs.items()}
        print(f"Letto {self.sJobIni}")

        # 5. Verifica chiavi riservate
        RESERVED = ["TS.START", "TS.END", "RETURN.TYPE", "RETURN.VALUE"]
        RESERVED_PREFIX = "RETURN.FILE."
        for sec, vals in self.dictJobs.items():
            for k in vals.keys():
                if k in RESERVED or k.startswith(RESERVED_PREFIX):
                    sResult = ErrorProc(f"Usate chiavi riservate {k}", sProc)
                    print("Eseguita ntjobsapp." + sProc + ": " + sResult)
                    return sResult

        print(f"Processato {self.sJobIni}, Sezioni {', '.join(self.dictJobs.keys())}")

        # 6. Verifica CONFIG
        if "CONFIG" not in self.dictJobs:
            sResult = ErrorProc(f"Sezione CONFIG non trovata in file {self.sJobIni}", sProc)
            print("Eseguita ntjobsapp." + sProc + ": " + sResult)
            return sResult

        # 7. ESPANSIONE MULTI-PASS CONFIG (2 cicli)
        # Va prima della verifica FILE.* perche' i valori possono
        # contenere $variabili definite in CONFIG.
        config_section = self.dictJobs.get("CONFIG", {})
        for _ in range(2):
            config_temp = config_section.copy()
            for k, v in config_temp.items():
                if isinstance(v, str):
                    config_section[k] = Expand(v, config_temp)

        # 8. Espansione altre sezioni
        for sKey in self.dictJobs:
            if sKey == "CONFIG": continue
            print(f"Sezione: {sKey}")
            dictTemp = self.dictJobs[sKey].copy()
            self.dictJobs[sKey] = ExpandDict(dictTemp, config_section)
            DictPrint(self.dictJobs[sKey])

        # 9. Verifica file allegati (FILE.*) su valori gia' espansi
        for sKey in self.dictJobs:
            if sKey == "CONFIG": continue
            dictTemp = self.dictJobs[sKey]
            for k, v in dictTemp.items():
                if k.startswith("FILE.") and not k.startswith("FILE.OUT."):
                    sFile = os.path.basename(str(v).strip())
                    if not os.path.isfile(sFile):
                        sResult += f"File richiesto non presente {sFile}\n"
        if sResult:
            print("Eseguita ntjobsapp." + sProc + ": " + sResult)
            return sResult

        # 10. Inizializzazione attributi da CONFIG
        self.sLogFile = self.Config("LOG") or ""
        self.sType = self.Config("TYPE")
        self.sName = self.Config("NAME")
        self.bErrExit = StringBool(self.Config("EXIT"))
        
        sResult = self.jLog.Start(self.sLogFile)
        if sResult:
            print("Eseguita ntjobsapp." + sProc + ": " + sResult)
            return sResult

        # 11. Rimozione password e verifiche finali
        if "PASSWORD" in self.dictJobs.get("CONFIG", {}):
            del self.dictJobs["CONFIG"]["PASSWORD"]

        if not self.sName:
            sResult = ErrorProc("NAME APP non precisato", sProc)
            print("Eseguita ntjobsapp." + sProc + ": " + sResult)
            return sResult
        if not self.sType.startswith("NTJOBS.APP."):
            sResult = ErrorProc("Type INI non NTJOBSAPP", sProc)
            print("Eseguita ntjobsapp." + sProc + ": " + sResult)
            return sResult

        self.Log0(sResult)
        print("Eseguita ntjobsapp." + sProc + ": " + sResult)
        return sResult

    def MakeIni(self) -> str:
        sProc = "MakeIni"
        self.sJobIni = ""
        args = sys.argv[1:]
        if len(args) < 1 or (len(args) - 1) % 2 != 0:
            sResult = ErrorProc("Errore numero parametri comando chiave=valore ecc.", sProc)
            print("Eseguita ntjobsapp." + sProc + ": " + sResult)
            return sResult

        sCommand = args[0]
        dictTemp = {}
        i = 1
        while i < len(args):
            k = args[i].strip('"').upper()
            v = args[i+1].strip('"')
            dictTemp[k] = v
            i += 2

        sFileTemp = NormalizePath(os.path.join(os.path.dirname(os.path.abspath(sys.argv[0])), "ntjobsapp.ini"))
        temp_dict = {"CONFIG": {"TYPE": "NTJOBS.APP.1"}, "JOB_01": {"COMMAND": sCommand}}
        temp_dict["JOB_01"].update(dictTemp)
        
        sResult = save_dict_to_ini(temp_dict, sFileTemp)
        print("Eseguita ntjobsapp." + sProc + ": " + sResult)
        if sResult: return sResult
        
        self.sJobIni = sFileTemp
        return sResult

    def Config(self, sKey: str) -> str:
        return DictExist(self.dictJobs.get("CONFIG", {}), sKey, "")

    def AddTimestamp(self, dictTemp: Dict) -> None:
        dictTemp["TS.START"] = self.tsStart
        dictTemp["TS.END"] = Timestamp()

    def Return(self, sResult: str, sValue: str = "", dictFiles: Optional[Dict] = None) -> str:
        sProc = "Return"
        sReturnType = "E" if sResult else "S"
        if not sValue and sReturnType == "E": sValue = sResult

        if dictFiles:
            for fid, fpath in dictFiles.items():
                if not os.path.isfile(NormalizePath(fpath)):
                    sResult = ErrorProc(f"Errore file non presente : {fpath}", sProc)
                    print("Eseguita ntjobsapp." + sProc + ": " + sResult)
                    return sResult
                dictFiles[fid] = os.path.basename(fpath)
            for k, v in dictFiles.items():
                self.dictJob[f"RETURN.FILE.{k}"] = v

        if not sReturnType: sReturnType = "S"
        self.dictJob["RETURN.TYPE"] = sReturnType
        self.dictJob["RETURN.VALUE"] = sValue
        self.AddTimestamp(self.dictJob)
        sResult = ErrorProc(sResult, sProc)
        print("Eseguita ntjobsapp." + sProc + ": " + sResult)
        return sResult

    def Run(self, cbCommands) -> str:
        sResult = ""
        for sKey in self.dictJobs:
            if sKey == "CONFIG": continue
            print(f"Esecuzione Command {sKey}")
            
            self.dictJob = self.dictJobs[sKey].copy()
            self.sCommand = DictExist(self.dictJob, "COMMAND", "")
            
            if not self.sCommand:
                sResult = f"COMMAND non trovato in {sKey}"
                self.Log(sResult)
            else:
                self.Log1(f"Eseguo il comando: {self.sCommand}, Sezione : {sKey}, TS: {Timestamp()}, Risultato: {sResult}")
                sResult = cbCommands(self.dictJob)
                self.dictJobs[sKey] = self.dictJob.copy()
                self.Log1(f"Eseguito il comando: {self.sCommand}, Sezione : {sKey}, TS: {Timestamp()}, Risultato: {sResult}")
            
            if sResult and self.bErrExit:
                break
        return sResult

    def End(self, sResult: str) -> None:
        sProc = "End"
        bIsFatalError = False
        nResult = 0
        
        if not self.dictJobs:
            nResult = 1
            self.dictJobs = {"CONFIG": {}}
            bIsFatalError = True
            
        if sResult:
            nResult = 2
            bIsFatalError = True
            
        dictTemp = {"RETURN.TYPE": "", "RETURN.VALUE": ""}
        if bIsFatalError:
            dictTemp["RETURN.TYPE"] = "E"
            dictTemp["RETURN.VALUE"] = sResult
            
        self.AddTimestamp(dictTemp)
        DictMerge(self.dictJobs.get("CONFIG", {}), dictTemp)
        
        sWriteRes = save_dict_to_ini(self.dictJobs, self.sJobEnd)
        if not sWriteRes: print(f"Creato file {self.sJobEnd}")
        
        self.Log(sResult, f"Fine applicazione {self.sName}")
        print("Eseguita ntjobsapp." + sProc + ": " + sResult)
        if nResult != 0: sys.exit(nResult)

    def Exec(self, sScript: str, dictConfig: Optional[Dict] = None, dictJobs: Optional[Dict] = None, sID: str = "") -> str:
        """Avvia una ntjobsapp esterna in background.

        Crea il file ntjobsapp_[sID].ini nella cartella dello script e lo
        lancia come: <python> <sScript> <ntjobsapp_[sID].ini>.
        L'esito si raccoglie poi con ExecReturn(sID).
        """
        sProc = "Exec"
        sResult = ""
        try:
            if not sID or not re.match(r"^[A-Za-z0-9_-]+$", str(sID)):
                sResult = ErrorProc(f"sID non valido {sID}", sProc)
                print("Eseguita ntjobsapp." + sProc + ": " + sResult)
                return sResult
            if not sScript:
                sResult = ErrorProc("Script da eseguire non precisato", sProc)
                print("Eseguita ntjobsapp." + sProc + ": " + sResult)
                return sResult
            sScriptNorm = NormalizePath(str(sScript))
            if not os.path.isfile(sScriptNorm):
                sResult = ErrorProc(f"Script non esistente {sScriptNorm}", sProc)
                print("Eseguita ntjobsapp." + sProc + ": " + sResult)
                return sResult
            sFolder = os.path.dirname(os.path.abspath(sScriptNorm)) or os.getcwd()
            sIni = NormalizePath(os.path.join(sFolder, f"ntjobsapp_{sID}.ini"))
            sEnd = NormalizePath(os.path.join(sFolder, f"ntjobsapp_{sID}.end"))
            data: Dict[str, Dict[str, str]] = {}
            cfg: Dict[str, str] = {"TYPE": "NTJOBS.APP.1"}
            if isinstance(dictConfig, dict):
                for k, v in dictConfig.items():
                    cfg[str(k).upper()] = "" if v is None else str(v)
            data["CONFIG"] = cfg
            if isinstance(dictJobs, dict):
                for sec, jobs in dictJobs.items():
                    sSec = str(sec).upper()
                    if isinstance(jobs, dict):
                        data[sSec] = {str(k).upper(): ("" if v is None else str(v)) for k, v in jobs.items()}
                    elif isinstance(jobs, (list, tuple)):
                        for idx, job in enumerate(jobs, start=1):
                            if not isinstance(job, dict):
                                continue
                            sSecN = f"{sSec}_{idx:02d}"
                            data[sSecN] = {str(k).upper(): ("" if v is None else str(v)) for k, v in job.items()}
            sResult = save_dict_to_ini(data, sIni)
            if sResult:
                sResult = ErrorProc(sResult, sProc)
                print("Eseguita ntjobsapp." + sProc + ": " + sResult)
                return sResult
            try:
                if os.path.isfile(sEnd):
                    os.remove(sEnd)
            except Exception:
                pass
            try:
                subprocess.Popen(
                    [sys.executable, os.path.abspath(sScriptNorm), os.path.abspath(sIni)],
                    cwd=sFolder,
                    stdin=subprocess.DEVNULL,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    close_fds=(os.name != "nt"),
                )
            except Exception as e:
                sResult = ErrorProc(f"Errore avvio script {sScriptNorm}: {e}", sProc)
                print("Eseguita ntjobsapp." + sProc + ": " + sResult)
                return sResult
            self.dictExec[str(sID)] = {"INI": sIni, "END": sEnd, "SCRIPT": os.path.abspath(sScriptNorm), "TS": Timestamp()}
            self.Log1(f"Eseguita ntjobsapp esterna: {sScriptNorm}, ID: {sID}, File: {sIni}")
        except Exception as e:
            sResult = ErrorProc(str(e), sProc)
        print("Eseguita ntjobsapp." + sProc + ": " + sResult)
        return sResult

    def ExecReturn(self, sID: str = "", nTimeout: int = 30) -> Dict:
        """Attende il file ntjobsapp_[sID].end e lo restituisce come dizionario.

        Ritorna {} se entro nTimeout secondi il file non esiste ancora
        (lavoro non finito). Se finito, ritorna il contenuto del file .end
        letto e cancella i file .end e .ini.
        """
        sProc = "ExecReturn"
        try:
            try:
                nTimeout = int(nTimeout)
            except Exception:
                nTimeout = 30
            if not sID or not re.match(r"^[A-Za-z0-9_-]+$", str(sID)):
                print("Eseguita ntjobsapp." + sProc + ": " + ErrorProc(f"sID non valido {sID}", sProc))
                return {"ERROR": f"sID non valido {sID}"}
            info = self.dictExec.get(str(sID), {})
            sEnd = info.get("END") or NormalizePath(os.path.join(os.getcwd(), f"ntjobsapp_{sID}.end"))
            sIni = info.get("INI") or NormalizePath(os.path.join(os.getcwd(), f"ntjobsapp_{sID}.ini"))
            deadline = time.time() + max(0, nTimeout)
            while True:
                if os.path.isfile(sEnd):
                    break
                if time.time() >= deadline:
                    print("Eseguita ntjobsapp." + sProc + ": ")
                    return {}
                time.sleep(0.5)
            time.sleep(0.2)
            sReadErr, dictResult = read_ini_to_dict(sEnd)
            if sReadErr:
                print("Eseguita ntjobsapp." + sProc + ": " + ErrorProc(sReadErr, sProc))
                return {"ERROR": sReadErr}
            for f in (sEnd, sIni):
                try:
                    if os.path.isfile(f):
                        os.remove(f)
                except Exception:
                    pass
            self.dictExec.pop(str(sID), None)
            self.Log1(f"Letto risultato ntjobsapp esterna ID: {sID}, Sezioni: {len(dictResult)}")
            print("Eseguita ntjobsapp." + sProc + ": ")
            return dictResult
        except Exception as e:
            print("Eseguita ntjobsapp." + sProc + ": " + ErrorProc(str(e), sProc))
            return {"ERROR": str(e)}

    def Log(self, sType: str, sValue: str = ""): self.jLog.Log(sType, sValue)
    def Log0(self, sResult: str, sValue: str = ""): self.jLog.Log0(sResult, sValue)
    def Log1(self, sValue: str = ""): self.jLog.Log1(sValue)