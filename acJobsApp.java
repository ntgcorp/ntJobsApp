// acJobsApp.java - Orchestratore per applicazioni ntJobsApp (port Java di acJobsApp.py)
// File unico e autocontenuto. Solo libreria standard Java (nessuna dipendenza esterna).
// Uso tipico (vedi test_acJobsApp.java):
//   acJobsApp jData = new acJobsApp();
//   String sResult = jData.start(args);
//   if (!sResult.isEmpty()) { System.exit(jData.end(sResult)); }
//   else { System.exit(jData.end(jData.run(dJob -> jData.jobReturn("", "fatto!")))); }
//
// Nota sui nomi: in Java `return` e' parola riservata, quindi il metodo
// Python Return() qui si chiama jobReturn(). end() restituisce il codice
// di uscita (0/1/2) invece di chiamare System.exit, cosi' il chiamante decide.

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class acJobsApp {

    public static final String NTJOBSAPP_VER = "2024102412"; // Formato YYYYMMDDHH (statico, come da specifica)

    // =========================================================================
    // FUNZIONI DI SUPPORTO (ex-aiSys, inglobate)
    // =========================================================================

    public static String normalizePath(String sPath) {
        if (sPath == null || sPath.isEmpty()) return "";
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return sPath.replace("/", "\\");
        return sPath.replace("\\", "/");
    }

    public static String errorProc(String sResult, String sProc) {
        if (sResult != null && !sResult.isEmpty()) return sProc + ": Errore " + sResult;
        return "";
    }

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd:HHmmss");

    public static String timestamp() { return timestamp(""); }

    public static String timestamp(String sPostfix) {
        try {
            String sResult = LocalDateTime.now().format(TS_FMT);
            if (sPostfix != null && !sPostfix.isEmpty()) sResult = sResult + ":" + sPostfix.toLowerCase();
            return sResult;
        } catch (Exception e) { return ""; }
    }

    private static LocalDateTime parseTs(String sTimestamp) {
        if (sTimestamp == null || sTimestamp.isEmpty()) return null;
        String[] parts = sTimestamp.split(":");
        if (parts.length < 2 || parts[0].length() != 8 || parts[1].length() != 6) return null;
        try {
            return LocalDateTime.parse(parts[0] + ":" + parts[1], TS_FMT);
        } catch (DateTimeParseException e) { return null; }
    }

    public static boolean timestampValidate(String sTimestamp) { return parseTs(sTimestamp) != null; }

    /** Converte timestamp in secondi (sMode="s") o giorni (sMode="d") dall'epoch. Ritorna null se non valido. */
    public static Double timestampConvert(String sTimestamp, String sMode) {
        try {
            LocalDateTime dt = parseTs(sTimestamp);
            if (dt == null) return null;
            double secs = (double) (dt.toEpochSecond(ZoneOffset.UTC) - LocalDateTime.of(1970, 1, 1, 0, 0).toEpochSecond(ZoneOffset.UTC));
            if ("d".equalsIgnoreCase(sMode)) return secs / 86400.0;
            return secs;
        } catch (Exception e) { return null; }
    }

    /** Differenza assoluta tra due timestamp, in secondi ("s") o giorni ("d"). Null se non validi. */
    public static Double timestampDiff(String sTimestamp1, String sTimestamp2, String sMode) {
        Double sec1 = timestampConvert(sTimestamp1, "s");
        Double sec2 = timestampConvert(sTimestamp2, "s");
        if (sec1 == null || sec2 == null) return null;
        double diff = Math.abs(sec1 - sec2);
        if ("d".equalsIgnoreCase(sMode)) return diff / 86400.0;
        return diff;
    }

    /** Aggiunge tempo a un timestamp. sUnit: "s"=secondi, "m"=minuti, "h"=ore, "d"=giorni. "" se errore. */
    public static String timestampAdd(String sTimestamp, double nValue, String sUnit) {
        try {
            LocalDateTime dt = parseTs(sTimestamp);
            if (dt == null) return "";
            long secs;
            if ("m".equalsIgnoreCase(sUnit)) secs = (long) (nValue * 60);
            else if ("h".equalsIgnoreCase(sUnit)) secs = (long) (nValue * 3600);
            else if ("d".equalsIgnoreCase(sUnit)) secs = (long) (nValue * 86400);
            else if ("s".equalsIgnoreCase(sUnit)) secs = (long) nValue;
            else return "";
            return dt.plusSeconds(secs).format(TS_FMT);
        } catch (Exception e) { return ""; }
    }

    /** Espande variabili e sequenze di escape (stesse 3 fasi della versione Python). */
    public static String expand(String sText, Map<String, String> dictConfig) {
        String sProc = "Expand";
        try {
            if (sText == null) return "";
            if (dictConfig == null) dictConfig = new LinkedHashMap<String, String>();
            // Fase 1: escape (ordine critico)
            sText = sText.replace("%##", "#");
            sText = sText.replace("%#", "\"");
            sText = sText.replace("%%", "%");
            sText = sText.replace("%n", "\n");
            sText = sText.replace("%$", "$");
            // Fase 2: $SYS. e $ENV.
            Pattern pSysEnv = Pattern.compile("\\$(SYS\\.|ENV\\.)([A-Za-z0-9_]+)");
            Matcher m = pSysEnv.matcher(sText);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String prefix = m.group(1);
                String varname = m.group(2);
                String rep;
                if ("ENV.".equals(prefix)) {
                    String v = System.getenv(varname);
                    rep = (v == null) ? "" : v;
                } else {
                    rep = sysVar(varname);
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(rep));
            }
            m.appendTail(sb);
            sText = sb.toString();
            // Fase 3: $NOMEVAR da dictConfig (se manca resta letterale)
            final Map<String, String> cfg = dictConfig;
            Pattern pCfg = Pattern.compile("\\$([A-Za-z0-9_]+)");
            Matcher m2 = pCfg.matcher(sText);
            StringBuffer sb2 = new StringBuffer();
            while (m2.find()) {
                String varname = m2.group(1);
                String rep = cfg.containsKey(varname) ? cfg.get(varname) : m2.group(0);
                if (rep == null) rep = "";
                m2.appendReplacement(sb2, Matcher.quoteReplacement(rep));
            }
            m2.appendTail(sb2);
            return sb2.toString();
        } catch (Exception e) { return errorProc(e.getMessage(), sProc); }
    }

    private static String sysVar(String varname) {
        String osName = System.getProperty("os.name", "").toUpperCase();
        String sOS = osName.contains("WIN") ? "WINDOWS" : (osName.contains("LINUX") ? "LINUX" : osName);
        String user = System.getenv("USERNAME");
        if (user == null) user = System.getenv("USER");
        if (user == null) user = "";
        String computer = System.getenv("COMPUTERNAME");
        if (computer == null) computer = "";
        String temp = System.getenv("TEMP");
        if (temp == null) temp = System.getenv("TMP");
        if (temp == null) temp = "";
        Map<String, String> sysVars = new LinkedHashMap<String, String>();
        sysVars.put("OS", sOS);
        sysVars.put("OS2", System.getProperty("os.name", ""));
        sysVars.put("USER", user);
        sysVars.put("COMPUTER", computer);
        sysVars.put("CD", System.getProperty("user.dir", ""));
        sysVars.put("TEMP", temp);
        sysVars.put("YYYYMMDD", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        sysVars.put("NOW", timestamp());
        return sysVars.containsKey(varname) ? sysVars.get(varname) : "NOTFOUND";
    }

    /** Espande i valori di un dizionario usando expand(). Max 1 livello. */
    public static Map<String, String> expandDict(Map<String, String> dictExpand, Map<String, String> dictParam) {
        Map<String, String> result = new LinkedHashMap<String, String>();
        if (dictExpand == null) return result;
        if (dictParam == null) dictParam = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> e : dictExpand.entrySet()) {
            String v = e.getValue();
            result.put(e.getKey(), v == null ? "" : expand(v, dictParam));
        }
        return result;
    }

    public static boolean stringBool(String sText) {
        return sText != null && sText.trim().equalsIgnoreCase("true");
    }

    public static String dictExist(Map<String, String> dictParam, String sKey, String xDefault) {
        if (dictParam == null || !(dictParam instanceof Map)) return null;
        return dictParam.containsKey(sKey) ? dictParam.get(sKey) : xDefault;
    }

    public static Map<String, String> dictMerge(Map<String, String> dictSource, Map<String, String> dictAdd) {
        if (dictAdd == null || dictAdd.isEmpty()) return dictSource;
        if (dictSource == null || dictSource.isEmpty()) return new LinkedHashMap<String, String>(dictAdd);
        for (Map.Entry<String, String> e : dictAdd.entrySet()) dictSource.put(e.getKey(), e.getValue());
        return dictSource;
    }

    public static boolean fileExists(String sFile) {
        if (sFile == null || sFile.isEmpty()) return false;
        return new File(sFile).isFile();
    }

    /** Esito lettura INI: err ("" se ok) + data (sezione -> chiave -> valore). */
    public static class IniRead {
        public String err = "";
        public Map<String, Map<String, String>> data = new LinkedHashMap<String, Map<String, String>>();
    }

    /** Legge un file INI (commenti ';', nessun commento inline, case originale preservato). */
    public static IniRead readIniToDict(String iniFilePath) {
        String sProc = "read_ini_to_dict";
        IniRead out = new IniRead();
        try {
            if (!fileExists(iniFilePath)) {
                out.err = "File non esistente: " + iniFilePath;
                return out;
            }
            Map<String, Map<String, String>> result = new LinkedHashMap<String, Map<String, String>>();
            String section = null;
            List<String> lines = Files.readAllLines(Paths.get(iniFilePath), StandardCharsets.UTF_8);
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith(";")) continue;
                if (line.startsWith("[") && line.endsWith("]") && line.length() > 2) {
                    section = line.substring(1, line.length() - 1);
                    if (!result.containsKey(section)) result.put(section, new LinkedHashMap<String, String>());
                } else if (section != null) {
                    int eq = line.indexOf('=');
                    if (eq >= 0) {
                        String k = line.substring(0, eq).trim();
                        String v = line.substring(eq + 1).trim();
                        result.get(section).put(k, v);
                    }
                }
            }
            out.data = result;
            System.out.println("Letto file .ini " + iniFilePath + ", Numero Sezioni: " + result.size());
            return out;
        } catch (Exception e) {
            out.err = errorProc(e.getMessage(), sProc);
            out.data = new LinkedHashMap<String, Map<String, String>>();
            return out;
        }
    }

    /** Salva un dizionario di dizionari in formato INI (crea le cartelle se servono). Ritorna "" se ok. */
    public static String saveDictToIni(Map<String, Map<String, String>> dataDict, String iniFilePath) {
        String sProc = "save_dict_to_ini";
        try {
            if (dataDict == null) dataDict = new LinkedHashMap<String, Map<String, String>>();
            Path p = Paths.get(iniFilePath);
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            BufferedWriter w = Files.newBufferedWriter(p, StandardCharsets.UTF_8);
            try {
                for (Map.Entry<String, Map<String, String>> sec : dataDict.entrySet()) {
                    w.write("[" + sec.getKey() + "]");
                    w.newLine();
                    if (sec.getValue() != null) {
                        for (Map.Entry<String, String> kv : sec.getValue().entrySet()) {
                            String v = kv.getValue() == null ? "" : kv.getValue();
                            w.write(kv.getKey() + "=" + v);
                            w.newLine();
                        }
                    }
                }
            } finally { w.close(); }
            return "";
        } catch (Exception e) { return errorProc(String.valueOf(e.getMessage()), sProc); }
    }

    public static String dictPrint(Map<String, String> dictParam) {
        String sProc = "DictPrint";
        try {
            if (dictParam == null) dictParam = new LinkedHashMap<String, String>();
            StringBuilder sb = new StringBuilder("{\n");
            for (Map.Entry<String, String> e : dictParam.entrySet()) {
                sb.append("  \"").append(e.getKey()).append("\": \"").append(e.getValue()).append("\"\n");
            }
            sb.append("}");
            System.out.println(sb.toString());
            return "";
        } catch (Exception e) { return errorProc(String.valueOf(e.getMessage()), sProc); }
    }

    // =========================================================================
    // CLASSE LOG INGLOBATA (ex aiSys.acLog)
    // =========================================================================

    public static class AcLog {
        public String sLog = "";

        public String start(String sLogfile, String sLogFolder) {
            String sProc = "Start";
            try {
                if (sLogFolder == null || sLogFolder.isEmpty()) sLogFolder = System.getProperty("user.dir", ".");
                String sAppName = "acJobsApp";
                if (sLogfile == null || sLogfile.isEmpty()) sLogfile = sAppName;
                // Evita doppia estensione: se sLogfile termina gia' con .log non aggiungerla
                String base = sLogfile.endsWith(".log") ? sLogfile.substring(0, sLogfile.length() - 4) : sLogfile;
                this.sLog = new File(sLogFolder, base + ".log").getPath();
                return "";
            } catch (Exception e) { return errorProc(String.valueOf(e.getMessage()), sProc); }
        }

        public void log(String sType, String sValue) {
            if (this.sLog == null || this.sLog.isEmpty()) return;
            if (sValue == null) sValue = "";
            String sLine = timestamp(sType == null ? "" : sType) + ": " + sValue;
            System.out.println(sLine);
            try {
                BufferedWriter w = Files.newBufferedWriter(Paths.get(this.sLog), StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                try { w.write(sLine); w.newLine(); } finally { w.close(); }
            } catch (IOException ignored) { }
        }

        public void log0(String sResult, String sValue) {
            if (sResult != null && !sResult.isEmpty()) log("ERR", sResult + ": " + (sValue == null ? "" : sValue));
            else log("INFO", sValue == null ? "" : sValue);
        }

        public void log1(String sValue) { log("INFO", sValue == null ? "" : sValue); }
    }

    // =========================================================================
    // CLASSE PRINCIPALE acJobsApp
    // =========================================================================

    /** Callback dei job: riceve il dizionario del job corrente, ritorna "" se ok o un messaggio di errore. */
    public interface JobCallback {
        String handle(Map<String, String> dJob);
    }

    public String sJobIni = "";
    public String sUser = "";
    public List<String> asUsg = new ArrayList<String>();
    public boolean bErrExit = false;
    public String sName = "";
    public String tsStart = "";
    public String sType = "";
    public String sLogFile = "";
    public String sCommand = "";
    public Map<String, String> dictJob = new LinkedHashMap<String, String>();
    public Map<String, Map<String, String>> dictJobs = new LinkedHashMap<String, Map<String, String>>();
    public String sJobEnd = "";
    public Map<String, Map<String, String>> dictExec = new LinkedHashMap<String, Map<String, String>>();
    public AcLog jLog = new AcLog();

    public acJobsApp() {
        String u = System.getenv("NTJ_USER");
        this.sUser = (u == null) ? "" : u;
        String g = System.getenv("NTJ_USERG");
        if (g != null && !g.isEmpty()) {
            for (String x : g.split(",")) {
                String t = x.trim();
                if (!t.isEmpty()) this.asUsg.add(t);
            }
        }
    }

    /** Legge e valida il file .ini passato in args[0] (o lo crea via makeIni). Ritorna "" se ok. */
    public String start(String[] args) {
        String sProc = "Start";
        String sResult = "";
        this.tsStart = timestamp();
        this.sJobIni = "";
        this.sJobEnd = "";
        this.dictJobs = new LinkedHashMap<String, Map<String, String>>();
        this.dictJob = new LinkedHashMap<String, String>();
        this.sCommand = "";

        if (args == null || args.length < 1) {
            sResult = "NTJOBSAPP: Eseguire con parametro file .ini o nella forma ntjobsapp command parametro valore ecc.";
            System.out.println(sResult);
            System.out.println("Eseguita ntjobsapp." + sProc + ": " + sResult);
            return sResult;
        }

        if (!args[0].toLowerCase().endsWith(".ini")) {
            sResult = makeIni(args);
            if (sResult != null && !sResult.isEmpty()) {
                System.out.println("Eseguita ntjobsapp." + sProc + ": " + sResult);
                return sResult;
            }
        } else {
            this.sJobIni = args[0];
        }

        this.sJobIni = normalizePath(this.sJobIni);
        if (!new File(this.sJobIni).exists()) {
            sResult = "File .ini non esistente " + this.sJobIni;
            System.out.println("Eseguita ntjobsapp." + sProc + ": " + sResult);
            return sResult;
        }

        int dot = this.sJobIni.lastIndexOf('.');
        this.sJobEnd = (dot >= 0 ? this.sJobIni.substring(0, dot) : this.sJobIni) + ".end";
        IniRead rd = readIniToDict(this.sJobIni);
        if (rd.err != null && !rd.err.isEmpty()) {
            System.out.println("Eseguita ntjobsapp." + sProc + ": " + rd.err);
            return rd.err;
        }
        this.dictJobs = new LinkedHashMap<String, Map<String, String>>();
        for (Map.Entry<String, Map<String, String>> sec : rd.data.entrySet()) {
            Map<String, String> upper = new LinkedHashMap<String, String>();
            if (sec.getValue() != null) {
                for (Map.Entry<String, String> kv : sec.getValue().entrySet()) {
                    upper.put(kv.getKey() == null ? "" : kv.getKey().toUpperCase(), kv.getValue());
                }
            }
            this.dictJobs.put(sec.getKey() == null ? "" : sec.getKey().toUpperCase(), upper);
        }
        System.out.println("Letto " + this.sJobIni);

        String[] reserved = {"TS.START", "TS.END", "RETURN.TYPE", "RETURN.VALUE"};
        for (Map.Entry<String, Map<String, String>> sec : this.dictJobs.entrySet()) {
            if (sec.getValue() == null) continue;
            for (String k : sec.getValue().keySet()) {
                boolean hit = false;
                for (String r : reserved) { if (r.equals(k)) { hit = true; break; } }
                if (!hit && k.startsWith("RETURN.FILE.")) hit = true;
                if (hit) {
                    sResult = errorProc("Usate chiavi riservate " + k, sProc);
                    System.out.println("Eseguita ntjobsapp." + sProc + ": " + sResult);
                    return sResult;
                }
            }
        }

        System.out.println("Processato " + this.sJobIni + ", Sezioni " + String.join(", ", this.dictJobs.keySet()));

        if (!this.dictJobs.containsKey("CONFIG")) {
            sResult = errorProc("Sezione CONFIG non trovata in file " + this.sJobIni, sProc);
            System.out.println("Eseguita ntjobsapp." + sProc + ": " + sResult);
            return sResult;
        }

        // Espansione multi-pass di CONFIG (2 cicli)
        Map<String, String> configSection = this.dictJobs.get("CONFIG");
        for (int pass = 0; pass < 2; pass++) {
            Map<String, String> tmp = new LinkedHashMap<String, String>(configSection);
            for (Map.Entry<String, String> e : tmp.entrySet()) {
                if (e.getValue() != null) configSection.put(e.getKey(), expand(e.getValue(), tmp));
            }
        }

        // Espansione altre sezioni
        for (Map.Entry<String, Map<String, String>> sec : this.dictJobs.entrySet()) {
            if ("CONFIG".equals(sec.getKey())) continue;
            System.out.println("Sezione: " + sec.getKey());
            Map<String, String> expanded = expandDict(sec.getValue(), configSection);
            this.dictJobs.put(sec.getKey(), expanded);
            dictPrint(expanded);
        }

        // Verifica FILE.* sui valori gia' espansi (salta FILE.OUT.*)
        StringBuilder sbErr = new StringBuilder();
        for (Map.Entry<String, Map<String, String>> sec : this.dictJobs.entrySet()) {
            if ("CONFIG".equals(sec.getKey()) || sec.getValue() == null) continue;
            for (Map.Entry<String, String> kv : sec.getValue().entrySet()) {
                String k = kv.getKey();
                if (k.startsWith("FILE.") && !k.startsWith("FILE.OUT.")) {
                    String v = kv.getValue() == null ? "" : kv.getValue().trim();
                    String sFile = new File(v).getName();
                    if (!new File(sFile).isFile()) sbErr.append("File richiesto non presente ").append(sFile).append("\n");
                }
            }
        }
        sResult = sbErr.toString();
        if (!sResult.isEmpty()) {
            System.out.println("Eseguita ntjobsapp." + sProc + ": " + sResult);
            return sResult;
        }

        this.sLogFile = config("LOG");
        if (this.sLogFile == null) this.sLogFile = "";
        this.sType = config("TYPE");
        this.sName = config("NAME");
        this.bErrExit = stringBool(config("EXIT"));

        sResult = this.jLog.start(this.sLogFile, null);
        if (sResult != null && !sResult.isEmpty()) {
            System.out.println("Eseguita ntjobsapp." + sProc + ": " + sResult);
            return sResult;
        }

        Map<String, String> cfg = this.dictJobs.get("CONFIG");
        if (cfg != null && cfg.containsKey("PASSWORD")) cfg.remove("PASSWORD");

        if (this.sName == null || this.sName.isEmpty()) {
            sResult = errorProc("NAME APP non precisato", sProc);
            System.out.println("Eseguita ntjobsapp." + sProc + ": " + sResult);
            return sResult;
        }
        if (this.sType == null || !this.sType.startsWith("NTJOBS.APP.")) {
            sResult = errorProc("Type INI non NTJOBSAPP", sProc);
            System.out.println("Eseguita ntjobsapp." + sProc + ": " + sResult);
            return sResult;
        }

        log0("", "");
        System.out.println("Eseguita ntjobsapp." + sProc + ": " + sResult);
        return sResult == null ? "" : sResult;
    }

    /** Modalita' sviluppo: crea ntjobsapp.ini da riga comando (comando + coppie chiave valore). */
    public String makeIni(String[] args) {
        String sProc = "MakeIni";
        this.sJobIni = "";
        if (args == null || args.length < 1 || ((args.length - 1) % 2 != 0)) {
            String sResult = errorProc("Errore numero parametri comando chiave=valore ecc.", sProc);
            System.out.println("Eseguita ntjobsapp." + sProc + ": " + sResult);
            return sResult;
        }
        String sCommand = args[0];
        Map<String, String> dictTemp = new LinkedHashMap<String, String>();
        for (int i = 1; i < args.length; i += 2) {
            String k = args[i].replace("\"", "").trim().toUpperCase();
            String v = args[i + 1].replace("\"", "").trim();
            dictTemp.put(k, v);
        }
        String sFileTemp = normalizePath(new File(System.getProperty("user.dir", "."), "ntjobsapp.ini").getPath());
        Map<String, Map<String, String>> tempDict = new LinkedHashMap<String, Map<String, String>>();
        Map<String, String> cfg = new LinkedHashMap<String, String>();
        cfg.put("TYPE", "NTJOBS.APP.1");
        tempDict.put("CONFIG", cfg);
        Map<String, String> job = new LinkedHashMap<String, String>();
        job.put("COMMAND", sCommand);
        job.putAll(dictTemp);
        tempDict.put("JOB_01", job);
        String sResult = saveDictToIni(tempDict, sFileTemp);
        System.out.println("Eseguita ntjobsapp." + sProc + ": " + (sResult == null ? "" : sResult));
        if (sResult != null && !sResult.isEmpty()) return sResult;
        this.sJobIni = sFileTemp;
        return "";
    }

    public String config(String sKey) {
        Map<String, String> cfg = this.dictJobs.get("CONFIG");
        if (cfg == null) return "";
        String v = cfg.get(sKey);
        return v == null ? "" : v;
    }

    public void addTimestamp(Map<String, String> dictTemp) {
        if (dictTemp == null) return;
        dictTemp.put("TS.START", this.tsStart);
        dictTemp.put("TS.END", timestamp());
    }

    /** Registra l'esito del job corrente (da chiamare dentro la callback di run). */
    public String jobReturn(String sResult, String sValue, Map<String, String> dictFiles) {
        String sProc = "Return";
        if (sResult == null) sResult = "";
        if (sValue == null) sValue = "";
        String sReturnType = sResult.isEmpty() ? "S" : "E";
        if (sValue.isEmpty() && "E".equals(sReturnType)) sValue = sResult;
        if (dictFiles != null) {
            for (Map.Entry<String, String> e : dictFiles.entrySet()) {
                String fpath = normalizePath(e.getValue() == null ? "" : e.getValue());
                if (!new File(fpath).isFile()) {
                    String err = errorProc("Errore file non presente : " + e.getValue(), sProc);
                    System.out.println("Eseguita ntjobsapp." + sProc + ": " + err);
                    return err;
                }
                e.setValue(new File(fpath).getName());
            }
            for (Map.Entry<String, String> e : dictFiles.entrySet()) {
                this.dictJob.put("RETURN.FILE." + e.getKey(), e.getValue());
            }
        }
        this.dictJob.put("RETURN.TYPE", sReturnType);
        this.dictJob.put("RETURN.VALUE", sValue);
        addTimestamp(this.dictJob);
        String out = errorProc(sResult, sProc);
        System.out.println("Eseguita ntjobsapp." + sProc + ": " + out);
        return out;
    }

    public String jobReturn(String sResult, String sValue) { return jobReturn(sResult, sValue, null); }
    public String jobReturn(String sResult) { return jobReturn(sResult, "", null); }

    /** Esegue in sequenza ogni job chiamando cbCommands. Ritorna "" se ok. */
    public String run(JobCallback cbCommands) {
        String sResult = "";
        List<String> keys = new ArrayList<String>(this.dictJobs.keySet());
        for (String sKey : keys) {
            if ("CONFIG".equals(sKey)) continue;
            System.out.println("Esecuzione Command " + sKey);
            this.dictJob = new LinkedHashMap<String, String>(this.dictJobs.get(sKey));
            this.sCommand = this.dictJob.containsKey("COMMAND") ? this.dictJob.get("COMMAND") : "";
            if (this.sCommand == null) this.sCommand = "";
            if (this.sCommand.isEmpty()) {
                sResult = "COMMAND non trovato in " + sKey;
                log(sResult, "");
            } else {
                log1("Eseguo il comando: " + this.sCommand + ", Sezione : " + sKey + ", TS: " + timestamp() + ", Risultato: " + sResult);
                sResult = cbCommands.handle(this.dictJob);
                if (sResult == null) sResult = "";
                this.dictJobs.put(sKey, new LinkedHashMap<String, String>(this.dictJob));
                log1("Eseguito il comando: " + this.sCommand + ", Sezione : " + sKey + ", TS: " + timestamp() + ", Risultato: " + sResult);
            }
            if (!sResult.isEmpty() && this.bErrExit) break;
        }
        return sResult;
    }

    /** Scrive il file .end e ritorna il codice di uscita (0/1/2). Non chiama System.exit. */
    public int end(String sResult) {
        String sProc = "End";
        if (sResult == null) sResult = "";
        boolean bIsFatalError = false;
        int nResult = 0;
        if (this.dictJobs == null || this.dictJobs.isEmpty()) {
            nResult = 1;
            this.dictJobs = new LinkedHashMap<String, Map<String, String>>();
            this.dictJobs.put("CONFIG", new LinkedHashMap<String, String>());
            bIsFatalError = true;
        }
        if (!sResult.isEmpty()) {
            nResult = 2;
            bIsFatalError = true;
        }
        Map<String, String> dictTemp = new LinkedHashMap<String, String>();
        dictTemp.put("RETURN.TYPE", "");
        dictTemp.put("RETURN.VALUE", "");
        if (bIsFatalError) {
            dictTemp.put("RETURN.TYPE", "E");
            dictTemp.put("RETURN.VALUE", sResult);
        }
        addTimestamp(dictTemp);
        Map<String, String> cfg = this.dictJobs.get("CONFIG");
        if (cfg == null) { cfg = new LinkedHashMap<String, String>(); this.dictJobs.put("CONFIG", cfg); }
        dictMerge(cfg, dictTemp);
        String sWriteRes = saveDictToIni(this.dictJobs, this.sJobEnd);
        if (sWriteRes == null || sWriteRes.isEmpty()) System.out.println("Creato file " + this.sJobEnd);
        log(sResult, "Fine applicazione " + this.sName);
        System.out.println("Eseguita ntjobsapp." + sProc + ": " + sResult);
        return nResult;
    }

    /**
     * Avvia una ntjobsapp esterna in background.
     * Crea ntjobsapp_[sID].ini nella cartella dello script e lo lancia
     * (script .py via "python", .jar via "java -jar", altrimenti esecuzione diretta).
     * L'esito si raccoglie con execReturn(sID).
     * dictJobs: chiave=sezione, valore=Map job oppure List di Map job (-> SEZIONE_01, _02...).
     */
    public String exec(String sScript, Map<String, String> dictConfig, Map<String, Object> dictJobs, String sID) {
        String sProc = "Exec";
        String sResult = "";
        try {
            if (sID == null || !sID.matches("^[A-Za-z0-9_-]+$")) {
                sResult = errorProc("sID non valido " + sID, sProc);
                System.out.println("Eseguita ntjobsapp." + sProc + ": " + sResult);
                return sResult;
            }
            if (sScript == null || sScript.isEmpty()) {
                sResult = errorProc("Script da eseguire non precisato", sProc);
                System.out.println("Eseguita ntjobsapp." + sProc + ": " + sResult);
                return sResult;
            }
            String sScriptNorm = normalizePath(sScript);
            if (!new File(sScriptNorm).isFile()) {
                sResult = errorProc("Script non esistente " + sScriptNorm, sProc);
                System.out.println("Eseguita ntjobsapp." + sProc + ": " + sResult);
                return sResult;
            }
            String sAbsScript = new File(sScriptNorm).getAbsolutePath();
            String sFolder = new File(sAbsScript).getParent();
            if (sFolder == null || sFolder.isEmpty()) sFolder = System.getProperty("user.dir", ".");
            String sIni = normalizePath(new File(sFolder, "ntjobsapp_" + sID + ".ini").getPath());
            String sEnd = normalizePath(new File(sFolder, "ntjobsapp_" + sID + ".end").getPath());

            Map<String, Map<String, String>> data = new LinkedHashMap<String, Map<String, String>>();
            Map<String, String> cfg = new LinkedHashMap<String, String>();
            cfg.put("TYPE", "NTJOBS.APP.1");
            if (dictConfig != null) {
                for (Map.Entry<String, String> e : dictConfig.entrySet()) {
                    cfg.put(e.getKey() == null ? "" : e.getKey().toUpperCase(), e.getValue() == null ? "" : e.getValue());
                }
            }
            data.put("CONFIG", cfg);
            if (dictJobs != null) {
                for (Map.Entry<String, Object> sec : dictJobs.entrySet()) {
                    String sSec = sec.getKey() == null ? "" : sec.getKey().toUpperCase();
                    Object jobs = sec.getValue();
                    if (jobs instanceof Map) {
                        Map<String, String> one = new LinkedHashMap<String, String>();
                        for (Map.Entry<?, ?> kv : ((Map<?, ?>) jobs).entrySet()) {
                            one.put(String.valueOf(kv.getKey()).toUpperCase(), kv.getValue() == null ? "" : String.valueOf(kv.getValue()));
                        }
                        data.put(sSec, one);
                    } else if (jobs instanceof List) {
                        int idx = 1;
                        for (Object job : (List<?>) jobs) {
                            if (!(job instanceof Map)) continue;
                            Map<String, String> one = new LinkedHashMap<String, String>();
                            for (Map.Entry<?, ?> kv : ((Map<?, ?>) job).entrySet()) {
                                one.put(String.valueOf(kv.getKey()).toUpperCase(), kv.getValue() == null ? "" : String.valueOf(kv.getValue()));
                            }
                            data.put(sSec + "_" + String.format("%02d", idx), one);
                            idx++;
                        }
                    }
                }
            }
            sResult = saveDictToIni(data, sIni);
            if (sResult != null && !sResult.isEmpty()) {
                sResult = errorProc(sResult, sProc);
                System.out.println("Eseguita ntjobsapp." + sProc + ": " + sResult);
                return sResult;
            }
            try { new File(sEnd).delete(); } catch (Exception ignored) { }

            List<String> cmd = new ArrayList<String>();
            String low = sAbsScript.toLowerCase();
            if (low.endsWith(".py")) { cmd.add("python"); cmd.add(sAbsScript); }
            else if (low.endsWith(".jar")) { cmd.add("java"); cmd.add("-jar"); cmd.add(sAbsScript); }
            else { cmd.add(sAbsScript); }
            cmd.add(new File(sIni).getAbsolutePath());
            try {
                new ProcessBuilder(cmd).directory(new File(sFolder))
                        .redirectInput(ProcessBuilder.Redirect.DISCARD)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
            } catch (Exception e) {
                sResult = errorProc("Errore avvio script " + sScriptNorm + ": " + e.getMessage(), sProc);
                System.out.println("Eseguita ntjobsapp." + sProc + ": " + sResult);
                return sResult;
            }
            Map<String, String> info = new LinkedHashMap<String, String>();
            info.put("INI", sIni);
            info.put("END", sEnd);
            info.put("SCRIPT", sAbsScript);
            info.put("TS", timestamp());
            this.dictExec.put(sID, info);
            log1("Eseguita ntjobsapp esterna: " + sScriptNorm + ", ID: " + sID + ", File: " + sIni);
        } catch (Exception e) {
            sResult = errorProc(String.valueOf(e.getMessage()), sProc);
        }
        System.out.println("Eseguita ntjobsapp." + sProc + ": " + (sResult == null ? "" : sResult));
        return sResult == null ? "" : sResult;
    }

    /**
     * Attende ntjobsapp_[sID].end fino a nTimeout secondi e lo restituisce come mappa.
     * Mappa vuota (0 chiavi) = non finito. Piu' chiavi = finito (file letto e cancellato).
     * {"ERROR": "..."} = sID non valido o file illeggibile.
     */
    public Map<String, Map<String, String>> execReturn(String sID, int nTimeout) {
        String sProc = "ExecReturn";
        try {
            if (sID == null || !sID.matches("^[A-Za-z0-9_-]+$")) {
                System.out.println("Eseguita ntjobsapp." + sProc + ": " + errorProc("sID non valido " + sID, sProc));
                Map<String, Map<String, String>> err = new LinkedHashMap<String, Map<String, String>>();
                Map<String, String> one = new LinkedHashMap<String, String>();
                one.put("ERROR", "sID non valido " + sID);
                err.put("ERROR", one);
                return err;
            }
            Map<String, String> info = this.dictExec.get(sID);
            String sEnd = (info != null && info.get("END") != null) ? info.get("END")
                    : normalizePath(new File(System.getProperty("user.dir", "."), "ntjobsapp_" + sID + ".end").getPath());
            String sIni = (info != null && info.get("INI") != null) ? info.get("INI")
                    : normalizePath(new File(System.getProperty("user.dir", "."), "ntjobsapp_" + sID + ".ini").getPath());
            long deadline = System.currentTimeMillis() + Math.max(0, nTimeout) * 1000L;
            while (true) {
                if (new File(sEnd).isFile()) break;
                if (System.currentTimeMillis() >= deadline) {
                    System.out.println("Eseguita ntjobsapp." + sProc + ": ");
                    return new LinkedHashMap<String, Map<String, String>>();
                }
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
            try { Thread.sleep(200); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            IniRead rd = readIniToDict(sEnd);
            if (rd.err != null && !rd.err.isEmpty()) {
                System.out.println("Eseguita ntjobsapp." + sProc + ": " + errorProc(rd.err, sProc));
                Map<String, Map<String, String>> err = new LinkedHashMap<String, Map<String, String>>();
                Map<String, String> one = new LinkedHashMap<String, String>();
                one.put("ERROR", rd.err);
                err.put("ERROR", one);
                return err;
            }
            try { new File(sEnd).delete(); } catch (Exception ignored) { }
            try { new File(sIni).delete(); } catch (Exception ignored) { }
            this.dictExec.remove(sID);
            log1("Letto risultato ntjobsapp esterna ID: " + sID + ", Sezioni: " + rd.data.size());
            System.out.println("Eseguita ntjobsapp." + sProc + ": ");
            return rd.data;
        } catch (Exception e) {
            System.out.println("Eseguita ntjobsapp." + sProc + ": " + errorProc(String.valueOf(e.getMessage()), sProc));
            Map<String, Map<String, String>> err = new LinkedHashMap<String, Map<String, String>>();
            Map<String, String> one = new LinkedHashMap<String, String>();
            one.put("ERROR", String.valueOf(e.getMessage()));
            err.put("ERROR", one);
            return err;
        }
    }

    public Map<String, Map<String, String>> execReturn(String sID) { return execReturn(sID, 30); }

    public void log(String sType, String sValue) { this.jLog.log(sType, sValue); }
    public void log0(String sResult, String sValue) { this.jLog.log0(sResult, sValue); }
    public void log1(String sValue) { this.jLog.log1(sValue); }
}
