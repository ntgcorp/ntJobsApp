// test_acJobsApp.java - Esempio d'uso della libreria acJobsApp.java
// Compilazione ed esecuzione (serve un JDK):
//   javac acJobsApp.java test_acJobsApp.java
//   java test_acJobsApp
// L'esempio: crea un file .ini di prova, esegue 2 job (SALUTA e SOMMA),
// scrive il file .end, mostra un poll execReturn con timeout breve e ripulisce.

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public class test_acJobsApp {

    public static void main(String[] args) {
        // --- 1. Funzioni di supporto ---
        System.out.println("Timestamp : " + acJobsApp.timestamp());
        System.out.println("Expand    : " + acJobsApp.expand("Ciao $USER da $SYS.OS",
                new LinkedHashMap<String, String>() {{ put("USER", "Mario"); }}));
        System.out.println("Bool TRUE : " + acJobsApp.stringBool("TRUE"));

        // --- 2. Crea un file .ini di prova (solo PARAM.*, nessun FILE.* richiesto) ---
        String sIni = acJobsApp.normalizePath(new File(System.getProperty("user.dir", "."), "test_lavoro.ini").getPath());
        Map<String, Map<String, String>> data = new LinkedHashMap<String, Map<String, String>>();
        Map<String, String> cfg = new LinkedHashMap<String, String>();
        cfg.put("TYPE", "NTJOBS.APP.1.0");
        cfg.put("NAME", "DEMO_JAVA");
        cfg.put("EXIT", "TRUE");
        cfg.put("LOG", "test_demo.log");
        cfg.put("BASE_DIR", "C:\\dati");
        data.put("CONFIG", cfg);
        Map<String, String> job1 = new LinkedHashMap<String, String>();
        job1.put("COMMAND", "SALUTA");
        job1.put("PARAM.NAME", "Mario");
        job1.put("PARAM.DIR", "$BASE_DIR");
        data.put("JOB1", job1);
        Map<String, String> job2 = new LinkedHashMap<String, String>();
        job2.put("COMMAND", "SOMMA");
        job2.put("PARAM.A", "40");
        job2.put("PARAM.B", "2");
        data.put("JOB2", job2);
        String sErr = acJobsApp.saveDictToIni(data, sIni);
        if (sErr != null && !sErr.isEmpty()) { System.out.println("Errore scrittura ini: " + sErr); return; }

        // --- 3. Ciclo di vita: Start -> Run -> End ---
        acJobsApp jData = new acJobsApp();
        String sResult = jData.start(new String[]{sIni});
        int nExit;
        if (sResult != null && !sResult.isEmpty()) {
            nExit = jData.end(sResult);
        } else {
            nExit = jData.end(jData.run(dJob -> {
                String sCmd = dJob.containsKey("COMMAND") ? dJob.get("COMMAND") : "";
                if ("SALUTA".equals(sCmd)) {
                    String sNome = dJob.containsKey("PARAM.NAME") ? dJob.get("PARAM.NAME") : "mondo";
                    return jData.jobReturn("", "Ciao " + sNome + "!");
                }
                if ("SOMMA".equals(sCmd)) {
                    try {
                        int a = Integer.parseInt(dJob.get("PARAM.A").trim());
                        int b = Integer.parseInt(dJob.get("PARAM.B").trim());
                        return jData.jobReturn("", "Somma=" + (a + b));
                    } catch (Exception e) {
                        return jData.jobReturn("Parametri non numerici", "");
                    }
                }
                return jData.jobReturn("Comando sconosciuto: " + sCmd, "");
            }));
        }
        System.out.println("Exit code: " + nExit);

        // --- 4. Leggi il file .end e mostra l'esito globale ---
        String sEnd = sIni.substring(0, sIni.lastIndexOf('.')) + ".end";
        acJobsApp.IniRead rd = acJobsApp.readIniToDict(sEnd);
        if (rd.err == null || rd.err.isEmpty()) {
            Map<String, String> endCfg = rd.data.get("CONFIG");
            System.out.println("END CONFIG RETURN.TYPE  = " + (endCfg != null ? endCfg.get("RETURN.TYPE") : "?"));
            System.out.println("END JOB1 RETURN.VALUE   = " + rd.data.get("JOB1").get("RETURN.VALUE"));
            System.out.println("END JOB2 RETURN.VALUE   = " + rd.data.get("JOB2").get("RETURN.VALUE"));
        }

        // --- 5. execReturn con timeout breve su ID inesistente -> mappa vuota (non finito) ---
        Map<String, Map<String, String>> pending = jData.execReturn("id_inesistente_xyz", 1);
        System.out.println("execReturn su ID inesistente, chiavi=" + pending.size() + " (0 = non finito, atteso)");

        // Esempio di lancio reale (decommentare con uno script esistente):
        // String sLaunch = jData.exec("C:/apps/figlia.py",
        //         new LinkedHashMap<String, String>() {{ put("NAME", "FIGLIA"); }},
        //         new LinkedHashMap<String, Object>() {{ put("JOB1", new LinkedHashMap<String, String>() {{ put("COMMAND", "SALUTA"); }}); }},
        //         "lotto1");
        // Map<String, Map<String, String>> dictResult = jData.execReturn("lotto1", 30);

        // --- 6. Pulizia file di prova ---
        new File(sIni).delete();
        new File(sEnd).delete();
        new File("test_demo.log").delete();
        System.out.println("Test completato.");
    }
}
