// test_acJobsApp.java - Esempio d'uso della libreria acJobsApp.java
// Compilazione ed esecuzione (serve un JDK):
//   javac acJobsApp.java test_acJobsApp.java
//   java test_acJobsApp
// Usa il file condiviso test_acJobsApp.ini (NON lo crea, NON lo cancella):
// esegue i job con Start -> Run -> End e pulisce solo .end e .log generati.
// La callback supporta COMMAND=SHELL (esegue PARAM.CMD) e, in alternativa,
// esegue direttamente il valore di COMMAND come comando di shell
// (es. COMMAND=cmd.exe /c dir *.* /b).

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class test_acJobsApp {

    private static final int MAX_OUT = 2000;

    /** Esegue un comando di shell, ritorna {errore, output}. */
    private static String[] runShell(String sCmd, String sWorkDir) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            List<String> cmd = new ArrayList<String>();
            if (os.contains("win")) { cmd.add("cmd.exe"); cmd.add("/c"); cmd.add(sCmd); }
            else { cmd.add("/bin/sh"); cmd.add("-c"); cmd.add(sCmd); }
            Process p = new ProcessBuilder(cmd).directory(new File(sWorkDir))
                    .redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            try {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append("\n");
            } finally { r.close(); }
            int nExit = p.waitFor();
            String sOut = sb.toString();
            if (sOut.length() > MAX_OUT) sOut = sOut.substring(0, MAX_OUT);
            if (nExit != 0) return new String[]{"exit=" + nExit, sOut};
            return new String[]{"", sOut};
        } catch (Exception e) {
            return new String[]{"Errore shell: " + e.getMessage(), ""};
        }
    }

    public static void main(String[] args) {
        String sBase = System.getProperty("user.dir", ".");
        final String sWorkDir = sBase;
        String sIni = args.length > 0 ? args[0]
                : acJobsApp.normalizePath(new File(sBase, "test_acJobsApp.ini").getPath());
        if (!new File(sIni).isFile()) {
            System.out.println("File di prova non trovato: " + sIni);
            return;
        }

        final acJobsApp jData = new acJobsApp();
        String sResult = jData.start(new String[]{sIni});
        int nExit;
        if (sResult != null && !sResult.isEmpty()) {
            nExit = jData.end(sResult);
        } else {
            nExit = jData.end(jData.run(dJob -> {
                String sCmd = dJob.containsKey("COMMAND") ? dJob.get("COMMAND") : "";
                if ("SHELL".equals(sCmd)) {
                    String sShell = dJob.containsKey("PARAM.CMD") ? dJob.get("PARAM.CMD") : "";
                    if (sShell == null || sShell.isEmpty()) return jData.jobReturn("PARAM.CMD mancante", "");
                    String[] res = runShell(sShell, sWorkDir);
                    return jData.jobReturn(res[0], res[1]);
                }
                if (sCmd != null && !sCmd.isEmpty()) {
                    String[] res = runShell(sCmd, sWorkDir);
                    return jData.jobReturn(res[0], res[1]);
                }
                return jData.jobReturn("COMMAND mancante", "");
            }));
        }
        System.out.println("Exit code: " + nExit);

        // Pulizia dei soli artefatti generati (l'ini condiviso resta)
        int dot = sIni.lastIndexOf('.');
        new File(dot >= 0 ? sIni.substring(0, dot) + ".end" : sIni + ".end").delete();
        new File(sBase, "test_acJobsApp.log").delete();
        System.out.println("Test completato.");
    }
}
